-- ============================================================
-- AI对话功能升级迁移脚本（安全版本）
-- 使用前请先备份数据库！
-- ============================================================

-- 1. 创建AI对话会话表（如果不存在）
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

-- 2. 给ai_chat_record表添加conversation_id字段（如果不存在）
-- 使用存储过程来安全添加字段
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS add_column_if_not_exists()
BEGIN
    DECLARE column_count INT DEFAULT 0;
    
    SELECT COUNT(*) INTO column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_chat_record'
    AND COLUMN_NAME = 'conversation_id';
    
    IF column_count = 0 THEN
        ALTER TABLE `ai_chat_record`
        ADD COLUMN `conversation_id` int(11) NULL DEFAULT NULL COMMENT '会话ID' AFTER `id`;
        
        SELECT 'conversation_id 字段添加成功' AS status;
    ELSE
        SELECT 'conversation_id 字段已存在，跳过' AS status;
    END IF;
END //
DELIMITER ;

CALL add_column_if_not_exists();
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- 3. 添加conversation_id索引（如果不存在）
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS add_index_if_not_exists()
BEGIN
    DECLARE index_count INT DEFAULT 0;
    
    SELECT COUNT(*) INTO index_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_chat_record'
    AND INDEX_NAME = 'idx_conversation_id';
    
    IF index_count = 0 THEN
        ALTER TABLE `ai_chat_record`
        ADD INDEX `idx_conversation_id`(`conversation_id`) USING BTREE;
        
        SELECT 'idx_conversation_id 索引添加成功' AS status;
    ELSE
        SELECT 'idx_conversation_id 索引已存在，跳过' AS status;
    END IF;
END //
DELIMITER ;

CALL add_index_if_not_exists();
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

SELECT '升级完成！' AS status;
