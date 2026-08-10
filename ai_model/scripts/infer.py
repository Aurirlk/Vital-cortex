from transformers import AutoModelForCausalLM, AutoTokenizer
import torch

model_path = "/root/zhikangyun/model/models/Qwen--Qwen2.5-7B-Instruct/snapshots/master"
tokenizer = AutoTokenizer.from_pretrained(model_path)
model = AutoModelForCausalLM.from_pretrained(model_path, torch_dtype=torch.float16, device_map="auto")

messages = [
    {"role": "system", "content": "你是一位三甲医院资深全科医生"},
    {"role": "user", "content": "血压140/90需要吃药吗"}
]

text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
inputs = tokenizer(text, return_tensors="pt").to(model.device)
outputs = model.generate(**inputs, max_new_tokens=256)
response = tokenizer.decode(outputs[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)
print(f"回答: {response}")
