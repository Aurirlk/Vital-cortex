---
license: Apache License 2.0
language:
- zh
task_categories:
- text-generation
- question-answering
other:
- medical
- health
---

# Zhikangyun-Huatuo 医疗问答数据集

基于 **Huatuo（华佗）中文医患问答数据**清洗构建的医疗训练集，用于大模型医疗领域微调（SFT/LoRA）。本数据集为「本草医疗 Qwen2.5-7B」微调模型的配套训练数据。

## 数据规模

| 文件 | 条数 | 说明 |
| --- | --- | --- |
| `train.json` | 7,000 | 训练集 |
| `val.json` | 1,000 | 验证集 |
| `test.json` | 1,000 | 测试集 |
| `generated_medical.json` | 344 | 补充生成样本 |
| **合计** | **9,344** | |

## 数据格式（扩展 Alpaca）

```json
{
  "instruction": "用户问题",
  "input": "可选的补充输入/背景（可为空）",
  "output": "标准回答",
  "history": "可选的多轮对话历史 [['用户', '助手'], ...]"
}
```

字段说明：
- `instruction`：主问题（必填）
- `input`：补充上下文，如检查报告、症状描述（可为空）
- `output`：医疗回答（标准答案）
- `history`：多轮对话历史（单轮样本为空数组）

## 清洗管线

1. 华佗原始医患数据体量大但质量参差 → **Hive 暂存 + PySpark** 清洗转换
2. **Dify 工作流 + GPT-4o-mini 多维打分**（医学准确性 / 回答长度 / 专业术语数量 / 是否参考经典书籍论文 / 完整性），强制 **≥ 6.0 分**，保留比例控制在 90%-95%
3. 低质样本剔除后，按 7000/1000/1000 划分，注册进 LLaMA-Factory

## 用途

- 医疗领域 **SFT + LoRA** 微调（Qwen2.5-7B 等，LLaMA-Factory 训练）
- 医疗问答 / 健康科普 / 导诊场景评估

## 下载方式

```python
# ModelScope SDK
from modelscope import MsDataset
ds = MsDataset.load('Aulink/Zhikangyun-Huatuo')
```

```bash
# Git
git clone https://www.modelscope.cn/datasets/Aulink/Zhikangyun-Huatuo.git
```

## 关联项目

- 微调模型：`Aulink/HealthPulse-Qwen2.5-7B`（本草医疗 Qwen2.5-7B）
- 项目仓库：https://github.com/Aurirlk/HealthPulse （`ai_model/` 目录：训练参数、微调步骤、评测脚本）

## 免责声明

数据用于学习与研究目的。医学信息请以专业医生意见为准。
