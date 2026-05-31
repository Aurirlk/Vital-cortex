# SQL 脚本说明

## 文件列表

| 文件 | 说明 |
|------|------|
| `personal_health_schema.sql` | ✅ 建表脚本（表结构 + 默认数据） |
| `personal_health_data.sql` | ✅ 数据导入脚本（从原数据库导出的数据） |
| `ai_chat_upgrade_safe.sql` | AI聊天表升级（安全版本，自动检测字段是否存在） |
| `fix_news_cover.sql` | 批量填充资讯封面图（可选） |

## 完整安装流程

```sql
-- 1. 创建数据库
DROP DATABASE IF EXISTS personal_health;
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE personal_health;

-- 2. 执行建表脚本（表结构 + 默认数据）
source sql/personal_health_schema.sql;

-- 3. 导入原数据库数据
source sql/personal_health_data.sql;

-- 4. (可选) 药品订阅功能
source 后端/personal-health-api/sql/drug_schema.sql;

-- 5. (可选) AI聊天升级
source sql/ai_chat_upgrade_safe.sql;

-- 6. (可选) 修复资讯封面
source sql/fix_news_cover.sql;
```

## 数据库信息

- **数据库名**: `personal-health`
- **字符集**: `utf8mb4`
- **表数量**: 12张

## 注意事项

1. 所有SQL文件均使用 **UTF-8** 编码
2. 执行前请确保数据库使用 `utf8mb4` 字符集
3. `ai_chat_upgrade_safe.sql` 会自动检测字段是否存在，避免重复创建错误
4. `personal_health_data.sql` 包含原数据库的实际数据
