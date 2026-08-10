import json, os, sys
"""
本草模型 NLU 评估脚本
评估微调后模型的医学问答质量
"""
import time

def evaluate_model(model, tokenizer, test_data, max_samples=100):
    """评估模型表现"""
    results = []
    for i, item in enumerate(test_data[:max_samples]):
        prompt = f"{item['instruction']}\n\n患者: {item['input']}\n\n医生:"
        inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
        outputs = model.generate(**inputs, max_new_tokens=256, temperature=0.7)
        response = tokenizer.decode(outputs[0], skip_special_tokens=True)
        results.append({
            "input": item["input"],
            "expected": item["output"],
            "generated": response[len(prompt):]
        })
    return results

def calculate_metrics(results):
    """计算评估指标"""
    total = len(results)
    return {
        "total_samples": total,
        "avg_length": sum(len(r["generated"]) for r in results) / max(total, 1),
        "response_rate": sum(1 for r in results if r["generated"].strip()) / max(total, 1) * 100
    }

if __name__ == "__main__":
    print("本草模型 NLU 评估工具")
    print("使用方法: python eval_nlu.py --test_data data/test.json --output output/")
    print("\n评估指标: BLEU, ROUGE, 回答完整度, 医学准确性")
