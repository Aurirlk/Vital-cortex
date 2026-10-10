#!/bin/bash
# ============================================================================
# Neo4j 知识图谱数据导入脚本
#
# ✅ 状态（2026-10-07 更新）：本脚本调用的 5 个端点【已全部实现】
#    阶段 0-B 交付内容见 docs/数据资产与AI能力整改方案.md → 阶段 0-B
#
# 实现位置：
#   后端/personal-health-api/src/main/java/cn/kmbeast/core/graph/
#     GraphImportController.java  POST /graph/import | /import/drugs | /import/articles | /import/all
#                                 GET  /graph/stats | /graph/health
#                                 POST /graph/clear
#     GraphImportService.java     导入编排（双写：Neo4j + 本地兜底）
#     GraphExtractor.java         规则抽取（零 LLM）
#     MedicalVocabulary.java      疾病词表（resources/ai-data/seed-triples.json）
#
# ⚠️ 全部导入端点需要【管理员 token】。脚本第 0 步会要求登录并自动带上 token。
#    不要用 admin 反复登录（会顶掉正在使用系统的会话），建议用专用管理员账号。
#
# ⚠️ Neo4j 未启动时：导入【不会失败】，会降级写入本地三元组
#    cache/knowledge_graph/triples.json，图谱检索走 LocalTripleStore 兜底。
#    Neo4j 恢复后重跑一次 /graph/import/all 即可同步进图库。
#    导入响应里的 backend 字段会告诉你这次实际写到了哪里（neo4j / local）。
#
# 规模参考（2026-10-07 离线核算，真实数据）：节点 248 / 边 485
# ============================================================================
#
# 用法: bash neo4j_import.sh [base_url] [username] [password]
# 默认 base_url=http://localhost:21090/api/personal-health/v1.0

set -uo pipefail

BASE_URL=${1:-http://localhost:21090/api/personal-health/v1.0}
USERNAME=${2:-}
PASSWORD=${3:-}

echo "导入到: $BASE_URL"
echo ""

# 若 python3 不可用则降级为原始输出，避免脚本整体失效
fmt() {
    if command -v python3 >/dev/null 2>&1; then
        python3 -m json.tool
    elif command -v python >/dev/null 2>&1; then
        python -m json.tool
    else
        cat
    fi
}

# ----------------------------------------------------------------------------
# 0. 登录取 token（导入端点受 @Protector(role="管理员") 保护）
# ----------------------------------------------------------------------------
TOKEN=""
if [ -n "$USERNAME" ] && [ -n "$PASSWORD" ]; then
    echo "=== 0. 登录管理员账号 ==="
    LOGIN_RESP=$(curl -s -X POST "$BASE_URL/user/login" \
        -H "Content-Type: application/json" \
        -d "{\"userAccount\":\"$USERNAME\",\"userPwd\":\"$PASSWORD\"}")
    # 兼容两种返回结构：data 是对象（取 .token）或 data 直接是字符串
    TOKEN=$(printf '%s' "$LOGIN_RESP" | python3 -c '
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get("data")
    if isinstance(data, dict):
        print(data.get("token") or "")
    else:
        print(data or "")
except Exception:
    print("")
' 2>/dev/null || echo "")
    if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then
        echo "登录失败，无法获取管理员 token。请确认账号密码。"
        echo "   响应: $LOGIN_RESP"
        exit 1
    fi
    echo "已获取 token（长度 ${#TOKEN}）"
    echo ""
else
    echo "未提供账号密码，将以匿名方式请求 —— 导入端点会返回 401。"
    echo "   用法: bash neo4j_import.sh <base_url> <管理员账号> <密码>"
    echo ""
fi

AUTH=()
if [ -n "$TOKEN" ]; then
    AUTH=(-H "token: $TOKEN")
fi

# ----------------------------------------------------------------------------
# 0.5 探测图谱后端（看清这次导入会写到哪）
# ----------------------------------------------------------------------------
echo "=== 0.5 图谱可用性探测 ==="
curl -s "${AUTH[@]}" "$BASE_URL/graph/health" | fmt
echo ""
echo ""

# 1. 导入基础健康数据（词表 + 指标 + 档案 + 测量 + 就诊）
echo "=== 1. 导入基础健康数据 ==="
curl -s -X POST "${AUTH[@]}" "$BASE_URL/graph/import" | fmt
echo ""

# 2. 导入药品数据
echo "=== 2. 导入药品数据 ==="
curl -s -X POST "${AUTH[@]}" "$BASE_URL/graph/import/drugs" | fmt
echo ""

# 3. 从知识库文章提取实体
echo "=== 3. 从文章提取实体 ==="
curl -s -X POST "${AUTH[@]}" "$BASE_URL/graph/import/articles" | fmt
echo ""

# 4. 一键完整导入
echo "=== 4. 一键完整导入（推荐） ==="
curl -s -X POST "${AUTH[@]}" "$BASE_URL/graph/import/all" | fmt
echo ""

# 5. 查看统计
echo "=== 5. 图谱统计 ==="
curl -s "${AUTH[@]}" "$BASE_URL/graph/stats" | fmt
echo ""

# 6. 幂等验收：再跑一次全量导入，nodeCount / relationshipCount 必须完全不变
echo "=== 6. 幂等验收：再跑一次 import/all 后重新统计 ==="
curl -s -X POST "${AUTH[@]}" "$BASE_URL/graph/import/all" | fmt
echo ""
echo "请把下面的 nodeCount / relationshipCount 与第 5 步比对（必须完全一致）:"
curl -s "${AUTH[@]}" "$BASE_URL/graph/stats" | fmt
echo ""

echo "完成！"
