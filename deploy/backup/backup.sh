#!/usr/bin/env bash
# 智康云 - 自动备份脚本（MySQL 逻辑备份 + 后端文件卷 tar），保留 7 天。
# 依赖 docker（通过容器执行 mysqldump / 打包卷），以及 .env 中的数据库密码。
#
# 手动执行：
#   ./deploy/backup/backup.sh
#
# 加入 cron（每天 03:07 备份，保留 7 天）：
#   7 3 * * * /path/to/zhikangyun/deploy/backup/backup.sh >> /var/log/zhikangyun-backup.log 2>&1
#
# 阶段 10（2026-11-09，E4）：补齐「无自动备份脚本」缺口。
set -euo pipefail

# ---- 配置（可用环境变量覆盖） ----
COMPOSE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # 仓库根
BACKUP_ROOT="${BACKUP_ROOT:-$COMPOSE_DIR/backups}"
DB_CONTAINER="${DB_CONTAINER:-zhikangyun-db}"
DB_NAME="${DB_NAME:-personal_health}"
FILE_VOLUME="${FILE_VOLUME:-backend_data}"   # docker volume 名（对应 docker-compose.yml 的 backend_data）
RETENTION_DAYS=7

# 从仓库根 .env 读取 DB_ROOT_PASSWORD（若存在）
if [ -z "${DB_ROOT_PASSWORD:-}" ] && [ -f "$COMPOSE_DIR/.env" ]; then
  DB_ROOT_PASSWORD="$(grep -E '^[[:space:]]*DB_ROOT_PASSWORD=' "$COMPOSE_DIR/.env" \
                      | tail -n1 | cut -d= -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
fi
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:?请设置 DB_ROOT_PASSWORD 环境变量或在 .env 中配置}"

TS="$(date +%Y%m%d_%H%M%S)"
DEST="$BACKUP_ROOT/$TS"
mkdir -p "$DEST"

echo "[$(date)] 开始备份 -> $DEST"

# ---- 1. MySQL 逻辑备份 ----
echo "[$(date)] mysqldump $DB_NAME ..."
docker exec "$DB_CONTAINER" \
  mysqldump -uroot -p"$DB_ROOT_PASSWORD" --single-transaction --routines --events "$DB_NAME" \
  > "$DEST/$DB_NAME.sql" || {
    echo "ERROR: mysqldump 失败，请检查 DB 容器是否运行、DB_ROOT_PASSWORD 是否正确" >&2
    exit 1
  }
echo "[$(date)] mysqldump 完成: $(du -h "$DEST/$DB_NAME.sql" | cut -f1)"

# ---- 2. 后端文件卷打包（上传文件等） ----
echo "[$(date)] 打包 docker volume $FILE_VOLUME ..."
if docker volume inspect "$FILE_VOLUME" >/dev/null 2>&1; then
  docker run --rm -v "$FILE_VOLUME":/data -v "$DEST":/backup alpine sh -c \
    "cd /data && tar czf /backup/${FILE_VOLUME}.tar.gz ." || {
      echo "WARN: 文件卷打包失败（可能无 alpine 镜像或卷为空），跳过" >&2
    }
  echo "[$(date)] 文件卷打包完成"
else
  echo "WARN: 未找到 docker volume $FILE_VOLUME，跳过文件卷打包" >&2
fi

# ---- 3. 清理旧备份（保留 $RETENTION_DAYS 天） ----
echo "[$(date)] 清理 $RETENTION_DAYS 天前的备份 ..."
find "$BACKUP_ROOT" -maxdepth 1 -type d -name '20*' -mtime +"$RETENTION_DAYS" -exec rm -rf {} \;
echo "[$(date)] 备份完成。当前备份目录："
ls -1 "$BACKUP_ROOT"

# ---- 恢复提示（仅注释，不执行） ----
# 恢复 MySQL ：docker exec -i "$DB_CONTAINER" mysql -uroot -p<密码> "$DB_NAME" < "$DEST/$DB_NAME.sql"
# 恢复文件卷：docker run --rm -v "$FILE_VOLUME":/data -v "$DEST":/backup alpine sh -c "cd /data && tar xzf /backup/$FILE_VOLUME.tar.gz"
