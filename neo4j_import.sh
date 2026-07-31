#!/bin/bash
# Neo4j 知识图谱数据导入脚本
# 在后端服务运行后执行
# 用法: bash neo4j_import.sh [base_url]
# 默认 base_url=http://localhost:21090/api/personal-health/v1.0

BASE_URL=${1:-http://localhost:21090/api/personal-health/v1.0}
echo "导入到: $BASE_URL"
echo ""

# 1. 导入基础健康数据（15疾病+15症状+10药物+10部位）
echo "=== 1. 导入基础健康数据 ==="
curl -s -X POST "$BASE_URL/graph/import" | python -m json.tool
echo ""

# 2. 导入药品数据（55种药品）
echo "=== 2. 导入药品数据 ==="
curl -s -X POST "$BASE_URL/graph/import/drugs" | python -m json.tool
echo ""

# 3. 从知识库文章提取实体
echo "=== 3. 从文章提取实体 ==="
curl -s -X POST "$BASE_URL/graph/import/articles" | python -m json.tool
echo ""

# 4. 一键完整导入
echo "=== 4. 一键完整导入（推荐） ==="
curl -s -X POST "$BASE_URL/graph/import/all" | python -m json.tool
echo ""

# 5. 查看统计
echo "=== 5. 图谱统计 ==="
curl -s "$BASE_URL/graph/stats" | python -m json.tool
echo ""

echo "完成！"
