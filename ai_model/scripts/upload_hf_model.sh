#!/usr/bin/env bash
# 上传 HealthPulse-Qwen2.5-7B 合并权重到 Hugging Face
# 用法（Git Bash 任意位置执行）：
#   bash "/d/Program/智康云-健康管理系统/ai_model/scripts/upload_hf_model.sh"
set -e

# 1) 规避此前 "Xet token refresh ConnectionError" 崩溃（必须）
export HF_HUB_DISABLE_XET=1

# 2) 如需代理加速（之前用的是 7897 端口），取消下一行注释并填上你的端口
#    注意端口后面不要带 ~ 等多余字符，否则会报 "Invalid port"
# export HTTPS_PROXY="http://127.0.0.1:7897"
# export HTTP_PROXY="http://127.0.0.1:7897"

# 3) 可选：若已安装 hf_transfer，取消下一行注释可大幅加速大文件上传
# export HF_HUB_ENABLE_HF_TRANSFER=1

# 4) 切到本地合并权重目录
MODEL_DIR="/d/Program/智康云-健康管理系统/dir/merged_model/HealthPulse_Qwen2.5-7B_merged/merged_model"
cd "$MODEL_DIR" || { echo "目录不存在: $MODEL_DIR"; exit 1; }

echo "当前目录: $(pwd)"
echo "开始上传到 Weikaijie/HealthPulse-Qwen2.5-7B （约 15GB，请保持网络、勿关电脑）..."

hf upload Weikaijie/HealthPulse-Qwen2.5-7B . --repo-type model

echo "✅ 模型权重上传完成: https://huggingface.co/Weikaijie/HealthPulse-Qwen2.5-7B"
echo "下一步：模型传完后，再单独上传模型卡片 README："
echo '  cd "/d/Program/智康云-健康管理系统/ai_model" && hf upload Weikaijie/HealthPulse-Qwen2.5-7B 模型卡片-HuggingFace.md README.md --repo-type model'
