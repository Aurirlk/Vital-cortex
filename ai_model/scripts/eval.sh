#!/bin/bash
# 智康云 - 本草微调模型 NLU 评估流程
# 在SSH服务器上运行此脚本

echo "=========================================="
echo "  本草医疗大模型 NLU 评估"
echo "=========================================="

# 1. 验证数据集
echo ""
echo "--- 1. 验证数据集 ---"
python -c "
import json
for f in ['data/train.json','data/val.json','data/test.json']:
    d=json.load(open(f,'r',encoding='utf-8'))
    print(f'{f}: {len(d)}条')
    print(f'  格式: instruction={d[0][\"instruction\"][:50]}...')
    print(f'  input示例: {d[0][\"input\"][:50]}')
"

# 2. 快速测试（100条验证集）
echo ""
echo "--- 2. 推理测试 ---"
python -c "
from transformers import AutoModelForCausalLM, AutoTokenizer
import torch

model = AutoModelForCausalLM.from_pretrained('./', torch_dtype=torch.float16, device_map='auto')
tokenizer = AutoTokenizer.from_pretrained('./')

test_prompt = '患者: 血压140/90mmHg需要吃药吗'
inputs = tokenizer(test_prompt, return_tensors='pt').to(model.device)
outputs = model.generate(**inputs, max_new_tokens=100)
print(f'测试: {test_prompt}')
print(f'回答: {tokenizer.decode(outputs[0], skip_special_tokens=True)}')
"

echo ""
echo "--- 3. 批量评估 ---"
python eval_nlu.py --test_data data/test.json --output eval_results/

echo ""
echo "=========================================="
echo "  评估完成！"
echo "  结果保存在: eval_results/"
echo "=========================================="
