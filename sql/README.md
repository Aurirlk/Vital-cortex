# SQL 脚本说明

## 执行顺序

### 1. 首次安装（必选其一）

| 脚本 | 说明 | 推荐 |
|------|------|------|
| `mysql_schema.sql` | 纯建表语句（10张表，含外键约束） | ✅ 推荐 |
| `personal_health_init.sql` | 建表 + 初始数据（管理员账号、8个健康指标） | ✅ 推荐 |

### 2. 功能升级（按需执行）

| 脚本 | 说明 |
|------|------|
| `ai_chat_upgrade.sql` | AI聊天表升级（新增 `ai_conversation` 表 + `conversation_id` 字段） |
| `drug_schema.sql` | 药品订阅功能（新增 `drug` + `drug_subscription` 表 + 示例数据） |

### 3. 数据修复（可选）

| 脚本 | 说明 |
|------|------|
| `fix_news_cover.sql` | 批量填充资讯封面图（后端已做兜底，此脚本用于彻底修复） |

## 完整安装流程

```sql
-- 1. 初始化数据库（选 personal_health_init.sql 或 mysql_schema.sql）
source sql/personal_health_init.sql;

-- 2. AI聊天升级
source sql/ai_chat_upgrade.sql;

-- 3. 药品订阅功能
source 后端/personal-health-api/sql/drug_schema.sql;

-- 4. (可选) 修复资讯封面
source sql/fix_news_cover.sql;
```

## 数据库信息

- **数据库名**: `personal-health`
- **字符集**: `utf8mb4`
- **表数量**: 13张（原10张 + 新增2张药品表 + 1张AI会话表）
