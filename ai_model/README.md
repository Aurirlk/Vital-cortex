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
| `scripts/` | 推理/评测脚本：`api_server.py`（FastAPI OpenAI 兼容服务）、`server.py`（Flask+LangChain 对话）、`infer.py`、`eval_nlu.py`、部署/评测 shell、对话日志样本 |

## 完整模型权重

合并后的完整权重约 **29GB**，不适合直接入库，已发布到 Hugging Face：

> **模型仓库**：[`Weikaijie/HealthPulse-Qwen2.5-7B`](https://huggingface.co/Weikaijie/HealthPulse-Qwen2.5-7B)
>
> 国内拉取加速：`export HF_ENDPOINT=https://hf-mirror.com`，然后
> `huggingface-cli download Weikaijie/HealthPulse-Qwen2.5-7B --local-dir ./merged_model`

## 本地部署（vLLM）

```bash
pip install vllm
vllm serve /path/to/HealthPulse-Qwen2.5-7B-merged --port 8000
```

后端 `LocalVllmProvider` 已实现 OpenAI 兼容协议，在管理员后台 AI 配置中将厂商切换为本地 vLLM、地址指向 `http://localhost:8000/v1` 即可一键切换。
