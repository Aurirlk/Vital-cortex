-- ============================================================
-- AI对话功能升级迁移脚本
-- 添加会话管理和对话缓存功能
-- ============================================================

-- 1. 创建AI对话会话表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '用户ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '会话标题',
  `agent_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT 'AI角色类型',
  `message_count` int(11) NULL DEFAULT 0 COMMENT '消息数量',
  `last_message_time` datetime NULL DEFAULT NULL COMMENT '最后消息时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_user_agent`(`user_id`, `agent_type`) USING BTREE,
  INDEX `idx_last_message_time`(`last_message_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- 2. 给ai_chat_record表添加conversation_id字段
ALTER TABLE `ai_chat_record`
ADD COLUMN `conversation_id` int(11) NULL DEFAULT NULL COMMENT '会话ID' AFTER `id`;

-- 3. 添加conversation_id索引
ALTER TABLE `ai_chat_record`
ADD INDEX `idx_conversation_id`(`conversation_id`) USING BTREE;

-- 4. 为已有的聊天记录创建默认会话（可选，按需执行）
-- 注意：以下SQL会将已有的聊天记录按用户和角色分组创建会话
-- 如果不需要迁移旧数据，可以注释掉以下SQL

/*
INSERT INTO ai_conversation (user_id, title, agent_type, message_count, last_message_time, create_time, update_time)
SELECT
    user_id,
    CONCAT(
        CASE agent_type
            WHEN 'doctor' THEN '全科医生'
            WHEN 'nutritionist' THEN '营养师'
            WHEN 'psychologist' THEN '心理咨询'
            WHEN 'analyst' THEN '报告分析'
            WHEN 'general_assistant' THEN '全能助手'
            ELSE 'AI助手'
        END,
        ' - 迁移会话'
    ) as title,
    agent_type,
    COUNT(*) as message_count,
    MAX(create_time) as last_message_time,
    MIN(create_time) as create_time,
    MAX(create_time) as update_time
FROM ai_chat_record
WHERE conversation_id IS NULL
GROUP BY user_id, agent_type;

-- 为旧记录关联会话ID
UPDATE ai_chat_record acr
INNER JOIN ai_conversation acv ON acr.user_id = acv.user_id AND acr.agent_type = acv.agent_type
SET acr.conversation_id = acv.id
WHERE acr.conversation_id IS NULL;
*/

SELECT '迁移完成！' AS status;
