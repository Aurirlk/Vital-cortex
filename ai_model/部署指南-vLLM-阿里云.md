# vLLM + 阿里云 + 本项目部署指南

> 将微调模型 `HealthPulse-Qwen2.5-7B` 部署到阿里云 GPU 服务器，并接入本项目的完整指南。
> 面向对象：想在云端自部署医疗大模型、替代云端 API 的开发者。

## 前置条件

- 阿里云账号 + 一台 **GPU 实例**（选型见下）
- 已获取微调模型权重（Hugging Face 仓库或网盘，见 `README.md`）
- 本项目后端可运行（Spring Boot 3.5.16 + Java 17）

## 一、实例选型（阿里云 ECS GPU）

| 模型规模 | 显存需求 | 推荐实例 | 参考价位 |
| --- | --- | --- | --- |
| Qwen2.5-7B（fp16，约 15GB） | ≥ 24GB | `gn7i`（A10 24GB）/ `ebmgn7ix` | 按量 ~¥6-10/时 |
| 需更大并发/更长上下文 | ≥ 40GB | `gn7e`（A100 40GB）/ `ebmgn7e` | 按量 ~¥20-30/时 |

> 本模型 fp16 权重约 15GB，**A10 24GB 单卡即可流畅部署**；如需更高并发可升 A100 或双卡。

## 二、环境准备（驱动 + 容器）

```bash
# 1. 安装 NVIDIA 驱动（阿里云控制台可选的公共镜像自带，或手动安装 550+）
nvidia-smi   # 验证 GPU 可见

# 2. 安装 NVIDIA Container Toolkit（推荐容器方式，环境隔离）
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | \
  sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \
  sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
sudo apt-get update && sudo apt-get install -y nvidia-container-toolkit
sudo nvidia-ctk runtime configure --runtime=docker && sudo systemctl restart docker
```

## 三、获取模型权重（Hugging Face）

```bash
# 国内网络建议走 HF 镜像
export HF_ENDPOINT=https://hf-mirror.com

# 方式 A：从 HF 仓库拉取（推荐）
pip install -U huggingface_hub
huggingface-cli download Weikaijie/HealthPulse-Qwen2.5-7B --local-dir /data/models/HealthPulse-Qwen2.5-7B

# 方式 B：从网盘下载后解压到同一目录（目录内需含 config.json、model.safetensors 等）
```

## 四、启动 vLLM 服务

```bash
pip install vllm
nohup vllm serve /data/models/HealthPulse-Qwen2.5-7B \
  --port 8000 \
  --max-model-len 8192 \
  --gpu-memory-utilization 0.9 > /data/logs/vllm.log 2>&1 &

# 验证服务
curl http://localhost:8000/v1/models
curl http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"HealthPulse-Qwen2.5-7B","messages":[{"role":"user","content":"高血压患者饮食注意什么？"}]}'
```

## 五、接入本项目后端

1. 登录系统 → 管理员后台 → **AI 配置**
2. 厂商选择 **本地 vLLM**（对应代码 `LocalVllmProvider`，OpenAI 兼容协议）
3. API 地址填 `http://<阿里云服务器公网IP>:8000/v1`（生产建议走内网/网关，勿直接暴露公网）
4. 保存后即可在前端 AI 对话中使用自部署模型

**代码侧说明**：`LocalVllmProvider` 已实现 OpenAI 兼容调用（`/chat/completions`），与云端厂商同构，配置热切换无需改代码。

## 六、运维建议

- **守护进程**：用 `systemd` 管理 vllm 进程（`Restart=always`）
- **安全组**：8000 端口仅对后端服务器内网开放，避免公网裸奔
- **监控**：`nvidia-smi -l` 或 Prometheus `nvidia_gpu` exporter 看显存/利用率
- **性能**：QPS 低时调 `--gpu-memory-utilization` 预留显存；并发高考虑多卡或升级实例

## 常见问题

- **OOM**：显存不足 → 降 `--max-model-len` 或升实例规格
- **模型加载慢**：首次加载需读盘 15GB，建议 SSD 盘
- **与云端厂商切换**：后端 AI 配置热切换即可，无需重启
