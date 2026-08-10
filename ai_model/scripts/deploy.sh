#!/bin/bash
# 智康云 - 医疗大模型微调一键部署脚本

echo "=== 1. 安装依赖 ==="
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

echo "=== 2. 验证数据集 ==="
python -c "
import json
for f in ['data/train.json','data/val.json','data/test.json']:
    d=json.load(open(f,'r',encoding='utf-8'))
    print(f'{f}: {len(d)}条')
"

echo "=== 3. 开始微调 ==="
llamafactory-cli train train_config.yaml

echo "=== 4. 微调完成 ==="
echo "模型保存在: ./output/"
echo "使用以下命令导出合并模型:"
echo "llamafactory-cli export --model_name_or_path ./ --adapter_name_or_path ./output --template qwen --finetuning_type lora --export_dir ./merged_model"
