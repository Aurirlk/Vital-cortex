#!/usr/bin/env bash
# 生成 deploy/alertmanager.yml：将 alertmanager.yml.tpl 中的 ${ALERT_WEBHOOK_URL}
# 替换为 .env 中设置的真实 webhook（企业微信 / 钉钉 / 飞书机器人 Webhook）。
#
# 用法：
#   cp .env.example .env          # 在 .env 里填 ALERT_WEBHOOK_URL=https://...
#   ./deploy/gen-alertmanager.sh
#
# 阶段 10（2026-11-09，E3）：把告警 webhook 从硬编码占位改为「从环境变量注入」，
# 避免把运维方的真实 webhook 写死进仓库，也避免告警永远停留在本地。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TPL="$SCRIPT_DIR/alertmanager.yml.tpl"
OUT="$SCRIPT_DIR/alertmanager.yml"

if [ ! -f "$TPL" ]; then
  echo "ERROR: 模板不存在 $TPL" >&2
  exit 1
fi

# 1) 优先读环境变量；2) 否则尝试从仓库根 .env 提取
URL="${ALERT_WEBHOOK_URL:-}"
if [ -z "$URL" ] && [ -f "$SCRIPT_DIR/../.env" ]; then
  URL="$(grep -E '^[[:space:]]*ALERT_WEBHOOK_URL=' "$SCRIPT_DIR/../.env" \
        | tail -n1 | cut -d= -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
fi

# 兜底占位：不设置则告警只停留在 Alertmanager/Prometheus 页面，不会外发
DEFAULT_URL="http://host.docker.internal:9000/hook"
URL="${URL:-$DEFAULT_URL}"
export ALERT_WEBHOOK_URL="$URL"

if command -v envsubst >/dev/null 2>&1; then
  envsubst < "$TPL" > "$OUT"
else
  # envsubst 不可用时退化为 sed 替换；URL 中的 & / / 需转义
  ESCAPED_URL="$(printf '%s' "$URL" | sed 's/[&/\]/\\&/g')"
  sed "s|\${ALERT_WEBHOOK_URL}|$ESCAPED_URL|g" "$TPL" > "$OUT"
fi

echo "已生成 $OUT"
echo "  ALERT_WEBHOOK_URL = ${URL}"
if [ "$URL" = "$DEFAULT_URL" ]; then
  echo "  ⚠️ 未配置真实 webhook，告警不会外发（仅本地占位）。请在 .env 设置 ALERT_WEBHOOK_URL 后重跑本脚本。"
fi
