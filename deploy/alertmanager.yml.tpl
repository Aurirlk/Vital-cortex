# 智康云健康管理系统 - Alertmanager 告警通知配置（模板）
# 本文件由 deploy/gen-alertmanager.sh 读取 .env 的 ALERT_WEBHOOK_URL 替换 ${ALERT_WEBHOOK_URL}
# 后生成 deploy/alertmanager.yml（docker-compose 实际挂载的文件）。
# 不要手动编辑生成的 alertmanager.yml，改这里并重跑生成脚本。
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'instance']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'zhikangyun-webhook'

receivers:
  - name: 'zhikangyun-webhook'
    webhook_configs:
      # 占位符由 gen-alertmanager.sh 用 .env 中的 ALERT_WEBHOOK_URL 替换。
      # 不设置则回落为本地占位地址（告警只停留在 Alertmanager/Prometheus 页面，不会外发）。
      - url: '${ALERT_WEBHOOK_URL}'
        send_resolved: true
