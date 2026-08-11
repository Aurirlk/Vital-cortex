# HealthPulse-Qwen2.5-7B 微调模型

基于 **Qwen2.5-7B-Instruct** 的医疗领域 LoRA 微调模型（LLaMA-Factory 训练）。

## 目录内容（已入库）

| 文件 | 说明 |
| --- | --- |
| `dataset/` | 医疗问答训练集（train/val/test，huatuo_medical 格式，约 5MB） |
| `train_config.yaml` | LoRA 训练参数（rank=8, alpha=16, lr=5e-5, sft） |
| `requirements.txt` | 训练/推理依赖 |
| `微调步骤.md` | 华为云 A800 实际微调完整步骤记录（环境/数据/训练/合并/部署 + 踩坑） |
| `排障指南.md` | 微调与部署常见问题排查 |
| `部署指南-vLLM-阿里云.md` | 阿里云 GPU 实例 + vLLM 部署微调模型并接入本项目（选型/环境/服务/接入/运维） |
| `模型卡片-ModelScope.md` | ModelScope（魔搭）模型主页 README（frontmatter + 使用示例 + 评测 + 免责声明） |
| `scripts/` | 推理/评测脚本：`api_server.py`（FastAPI OpenAI 兼容服务）、`server.py`（Flask+LangChain 对话）、`infer.py`、`eval_nlu.py`、部署/评测 shell、对话日志样本 |

## 完整模型权重

合并后的完整权重约 **29GB**，不适合直接入库，已双平台发布：

> **国内（推荐）**：[`Aulink/HealthPulse-Qwen2.5-7B`](https://www.modelscope.cn/models/Aulink/HealthPulse-Qwen2.5-7B)（ModelScope 魔搭，国内直连快）
> ```python
> from modelscope import snapshot_download
> snapshot_download('Aulink/HealthPulse-Qwen2.5-7B', local_dir='./merged_model')
> ```
>
> **海外**：[`Weikaijie/HealthPulse-Qwen2.5-7B`](https://huggingface.co/Weikaijie/HealthPulse-Qwen2.5-7B)（Hugging Face）
> ```bash
> export HF_ENDPOINT=https://hf-mirror.com
> huggingface-cli download Weikaijie/HealthPulse-Qwen2.5-7B --local-dir ./merged_model
> ```

**配套数据集**（华佗医疗问答，9,344 条）：[魔搭](https://www.modelscope.cn/datasets/Aulink/Zhikangyun-Huatuo) / [Hugging Face](https://huggingface.co/datasets/Weikaijie/Zhikangyun-Huatuo)

## 本地部署（vLLM）

```bash
pip install vllm
vllm serve /path/to/HealthPulse-Qwen2.5-7B-merged --port 8000
```

后端 `LocalVllmProvider` 已实现 OpenAI 兼容协议，在管理员后台 AI 配置中将厂商切换为本地 vLLM、地址指向 `http://localhost:8000/v1` 即可一键切换。
