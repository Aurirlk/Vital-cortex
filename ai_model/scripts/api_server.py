"""轻量级 OpenAI 兼容 API 服务器（用于部署微调后的 Qwen2.5-7B 医疗模型）"""
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Dict
from transformers import AutoModelForCausalLM, AutoTokenizer
import torch
import time
import os

app = FastAPI(title="智康云医疗模型 API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 全局模型/分词器
model = None
tokenizer = None

class Message(BaseModel):
    role: str
    content: str

class ChatCompletionRequest(BaseModel):
    model: Optional[str] = "default"
    messages: List[Message]
    temperature: Optional[float] = 0.7
    max_tokens: Optional[int] = 512
    stream: Optional[bool] = False

class ChatCompletionResponse(BaseModel):
    id: str
    object: str = "chat.completion"
    created: int
    model: str
    choices: List[Dict]

@app.on_event("startup")
def load_model():
    global model, tokenizer
    model_path = os.environ.get("MODEL_PATH", "/root/zhikangyun/merged_model")
    print(f"[加载模型] {model_path} ...")
    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        torch_dtype=torch.float16,
        device_map="auto",
        use_cache=True,
    )
    tokenizer = AutoTokenizer.from_pretrained(model_path)
    model.eval()
    print(f"[完成] 模型已加载，设备: {model.device}")

@app.post("/v1/chat/completions")
async def chat_completion(req: ChatCompletionRequest):
    if model is None or tokenizer is None:
        raise HTTPException(503, "模型未加载")
    
    prompt = tokenizer.apply_chat_template(
        req.messages, tokenize=False, add_generation_prompt=True
    )
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    
    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=req.max_tokens,
            temperature=req.temperature,
            do_sample=True,
            pad_token_id=tokenizer.pad_token_id or tokenizer.eos_token_id,
        )
    
    response = tokenizer.decode(
        outputs[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True
    )
    
    return ChatCompletionResponse(
        id=f"chatcmpl-{int(time.time())}",
        created=int(time.time()),
        model=req.model or "zhikangyun-medical",
        choices=[{
            "index": 0,
            "message": {"role": "assistant", "content": response},
            "finish_reason": "stop"
        }]
    )

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
