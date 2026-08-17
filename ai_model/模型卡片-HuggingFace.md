---
language:
- zh
license: apache-2.0
tags:
- medical
- health
- qwen
- chat
- text-generation
library_name: transformers
pipeline_tag: text-generation
base_model: Qwen/Qwen2.5-7B-Instruct
---

# 本草医疗 Qwen2.5-7B（HealthPulse-Qwen2.5-7B）

基于 **Qwen2.5-7B-Instruct** 在 Huatuo（华佗）中文医患数据集上 **SFT + LoRA** 微调的医疗问答大模型。
聚焦**医学准确性**与**安全性**，适用于症状咨询、健康科普、导诊建议、用药常识问答等场景。

## 模型信息

| 项目 | 说明 |
| --- | --- |
| 基座模型 | Qwen2.5-7B-Instruct |
| 微调方法 | SFT + LoRA（rank=8, alpha=16, lr=5e-5） |
| 训练框架 | LLaMA-Factory |
| 训练数据 | Huatuo（华佗）医患数据集 9,000+ 条（Alpaca 格式） |
| 训练平台 | 华为云 A800（80GB）GPU |
| 上下文长度 | 8K（可扩展） |
| 语言 | 中文 |

## 快速使用

### Transformers

```python
from transformers import AutoModelForCausalLM, AutoTokenizer

model = AutoModelForCausalLM.from_pretrained(
    "Weikaijie/HealthPulse-Qwen2.5-7B",
    torch_dtype="auto",
    device_map="auto",
)
tokenizer = AutoTokenizer.from_pretrained("Weikaijie/HealthPulse-Qwen2.5-7B")

messages = [{"role": "user", "content": "高血压患者日常饮食需要注意什么？"}]
text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
inputs = tokenizer(text, return_tensors="pt").to(model.device)
out = model.generate(**inputs, max_new_tokens=512)
print(tokenizer.decode(out[0][inputs.input_ids.shape[1]:], skip_special_tokens=True))
```

### vLLM（推荐，OpenAI 兼容）

```bash
pip install vllm
vllm serve Weikaijie/HealthPulse-Qwen2.5-7B --port 8000
```

```python
from openai import OpenAI
client = OpenAI(base_url="http://localhost:8000/v1", api_key="EMPTY")
resp = client.chat.completions.create(
    model="HealthPulse-Qwen2.5-7B",
    messages=[{"role": "user", "content": "最近失眠多梦，怎么调理？"}],
)
print(resp.choices[0].message.content)
```

### 下载权重

```bash
# 海外直连
hf download Weikaijie/HealthPulse-Qwen2.5-7B --local-dir ./merged_model

# 国内镜像加速
export HF_ENDPOINT=https://hf-mirror.com
hf download Weikaijie/HealthPulse-Qwen2.5-7B --local-dir ./merged_model
```

```python
from huggingface_hub import snapshot_download
snapshot_download("Weikaijie/HealthPulse-Qwen2.5-7B", local_dir="./merged_model")
```

## 评测（5 维 NLU）

| 维度 | 说明 | 相对通用模型 |
| --- | --- | --- |
| 医学准确性 | 是否符合医学共识 | **提升约 10%** |
| 安全性 | 是否不乱开药/给危险建议 | **同步提升** |
| 专业性 | 是否体现医生角色 | 持平/略优 |
| 完整性 | 是否覆盖关键信息 | 略低于最新国产大模型 |
| 清晰度 | 表达是否流畅 | 略低于最新国产大模型 |

> 评测方式：LLM 初审 + 医学论坛评分 + 争议样本医学人士复核；如实记录短板，完整性与清晰度较最新国产大模型低 10%-20%（基座模型代差所致）。

## 训练详情

- **数据管线**：华佗原始医患数据 → Hive 暂存 + PySpark 清洗 → Alpaca JSON 格式；Dify 工作流 + GPT-4o-mini 多维打分（≥6.0 分，保留 90%-95%）
- **数据划分**：7,000 train / 1,000 val / 1,000 test
- **训练参数**：LoRA rank=8, alpha=16, dropout=0.1, lr=5e-5, batch=8, epochs 3-5

## 免责声明

本模型仅供**学习与研究**及健康科普参考，**不构成医疗诊断或治疗建议**。医疗问题请咨询专业医生。模型输出可能包含错误或过时信息，使用者需自行判断。

## 数据集（配套训练数据）

- **ModelScope（国内）**：[`Aulink/Zhikangyun-Huatuo`](https://www.modelscope.cn/datasets/Aulink/Zhikangyun-Huatuo)（9,344 条华佗医疗问答，Alpaca 格式）
- **Hugging Face（海外）**：[`Weikaijie/Zhikangyun-Huatuo`](https://huggingface.co/datasets/Weikaijie/Zhikangyun-Huatuo)

## 关联项目

- 项目主页（VitalCortex 健康平台，含训练集/参数/微调步骤/评测脚本）：[github.com/Aurirlk/HealthPulse](https://github.com/Aurirlk/HealthPulse)
- 微调全流程实操记录：项目仓库 `ai_model/微调步骤.md`
- 云上部署指南（vLLM + 阿里云）：项目仓库 `ai_model/部署指南-vLLM-阿里云.md`
