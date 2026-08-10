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

## 完整模型权重

合并后的完整权重约 **29GB**，不适合直接入库。提供百度网盘下载：

> **下载链接**：[百度网盘]（待补充，上传后填入）

## 本地部署（vLLM）

```bash
pip install vllm
vllm serve /path/to/HealthPulse-Qwen2.5-7B-merged --port 8000
```

后端 `LocalVllmProvider` 已实现 OpenAI 兼容协议，在管理员后台 AI 配置中将厂商切换为本地 vLLM、地址指向 `http://localhost:8000/v1` 即可一键切换。
