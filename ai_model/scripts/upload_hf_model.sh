#!/usr/bin/env bash
# 上传 HealthPulse-Qwen2.5-7B 合并权重到 Hugging Face
# 用法（Git Bash 任意位置执行）：
#   bash "/d/Program/智康云-健康管理系统/ai_model/scripts/upload_hf_model.sh"
set -e

# 1) 上传通道：hf-mirror 国内镜像，无需代理（已验证可连，比反复崩溃的代理稳）
export HF_ENDPOINT=https://hf-mirror.com
unset HTTPS_PROXY HTTP_PROXY   # 用镜像时务必清掉代理变量，否则会绕回代理

# 2) 高性能传输（基于 Xet 的分块 + 断点续传），专治「大文件传一半断连」。
#    ⚠️ 重要：hf_transfer 在当前 huggingface_hub 版本已废弃，改用 HF_XET_HIGH_PERFORMANCE。
#    不要再设 HF_HUB_DISABLE_XET=1 —— 那会关掉续传能力，退回易断的普通单请求上传。
export HF_XET_HIGH_PERFORMANCE=1

# 4) 切到本地合并权重目录
MODEL_DIR="/d/Program/智康云-健康管理系统/dir/merged_model/HealthPulse_Qwen2.5-7B_merged/merged_model"
cd "$MODEL_DIR" || { echo "目录不存在: $MODEL_DIR"; exit 1; }

echo "当前目录: $(pwd)"
echo "开始上传到 Weikaijie/HealthPulse-Qwen2.5-7B （约 15GB，请保持网络、勿关电脑）..."

hf upload Weikaijie/HealthPulse-Qwen2.5-7B . --repo-type model

echo "✅ 模型权重上传完成: https://huggingface.co/Weikaijie/HealthPulse-Qwen2.5-7B"
echo "下一步：模型传完后，再单独上传模型卡片 README："
echo '  cd "/d/Program/智康云-健康管理系统/ai_model" && hf upload Weikaijie/HealthPulse-Qwen2.5-7B 模型卡片-HuggingFace.md README.md --repo-type model'
