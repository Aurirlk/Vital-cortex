# 数据库文件说明

## 目录结构

```
sql/
├── deploy/              ← 新项目部署（空表+默认数据）
│   └── init_database.sql
├── legacy/              ← 旧数据迁移（完整备份）
│   ├── full_backup.sql
│   └── personal_health_data.sql
└── README.md
```

## 使用方法

### 新项目部署
```sql
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE personal_health;
source sql/deploy/init_database.sql;
```

### 迁移旧数据
```sql
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE personal_health;
source sql/legacy/full_backup.sql
```

## 表结构（共15张表）

| 序号 | 表名 | 说明 |
|------|------|------|
| 1 | `user` | 用户表 |
| 2 | `tags` | 资讯分类表 |
| 3 | `news` | 健康资讯表 |
| 4 | `news_save` | 收藏记录表 |
| 5 | `evaluations` | 评论表 |
| 6 | `message` | 消息通知表 |
| 7 | `health_model_config` | 健康模型配置表 |
| 8 | `user_health` | 用户健康记录表 |
| 9 | `patient_profile` | 用户画像表 |
| 10 | `drug` | 药品信息表 |
| 11 | `drug_subscription` | 药品订阅表 |
| 12 | `ai_conversation` | AI会话表 |
| 13 | `ai_chat_record` | AI聊天记录表 |
| 14 | `ai_config` | AI配置表 |
| 15 | `system_config` | 系统配置表 |

## 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员 |
| user | 123456 | 普通用户 |
