-- AI会话表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `title` VARCHAR(255) DEFAULT '',
  `agent_type` VARCHAR(50) DEFAULT '',
  `message_count` INT DEFAULT 0,
  `last_message_time` DATETIME,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_agent_type` (`agent_type`),
  INDEX `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话表';

-- AI聊天记录表
CREATE TABLE IF NOT EXISTS `ai_chat_record` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `conversation_id` INT NOT NULL,
  `user_id` INT NOT NULL,
  `role` VARCHAR(20) NOT NULL COMMENT 'user/assistant',
  `content` TEXT,
  `agent_type` VARCHAR(50) DEFAULT '',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_conversation_id` (`conversation_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天记录表';
