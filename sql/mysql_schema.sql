-- ============================================================
-- 个人健康管理系统 - MySQL 数据库初始化脚本
-- Database: personal-health
-- ============================================================

CREATE DATABASE IF NOT EXISTS `personal-health`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `personal-health`;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`            INT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `user_account`  VARCHAR(50)  NOT NULL COMMENT '用户账号（手机号）',
    `user_name`     VARCHAR(50)  DEFAULT NULL COMMENT '用户昵称',
    `user_pwd`      VARCHAR(255) DEFAULT NULL COMMENT '密码（BCrypt加密）',
    `user_avatar`   VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `user_email`    VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `user_role`     INT          DEFAULT 0  COMMENT '角色：0=普通用户, 1=管理员',
    `is_login`      TINYINT(1)   DEFAULT 0  COMMENT '登录状态：0=正常, 1=禁用',
    `is_word`       TINYINT(1)   DEFAULT 0  COMMENT '禁言状态：0=正常, 1=禁言',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    UNIQUE KEY `uk_user_account` (`user_account`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- ============================================================
-- 2. AI对话会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id`                INT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    `user_id`           INT NOT NULL COMMENT '用户ID',
    `title`             VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    `agent_type`        VARCHAR(50)  DEFAULT 'general_assistant' COMMENT 'AI角色：doctor/nutritionist/psychologist/analyst/general_assistant',
    `message_count`     INT          DEFAULT 0 COMMENT '消息数量',
    `last_message_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '最后消息时间',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_agent_type` (`agent_type`),
    CONSTRAINT `fk_conv_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';


-- ============================================================
-- 3. AI聊天记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_chat_record` (
    `id`              INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `conversation_id` INT NOT NULL COMMENT '会话ID',
    `user_id`         INT NOT NULL COMMENT '用户ID',
    `role`            VARCHAR(20) NOT NULL COMMENT '角色：user/assistant',
    `content`         TEXT        NOT NULL COMMENT '聊天内容',
    `agent_type`      VARCHAR(50) DEFAULT NULL COMMENT 'AI角色',
    `create_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`),
    CONSTRAINT `fk_record_conv` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天记录表';


-- ============================================================
-- 4. 健康指标配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS `health_model_config` (
    `id`          INT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    `user_id`     INT          NOT NULL COMMENT '所属用户ID（全局为0）',
    `name`        VARCHAR(100) NOT NULL COMMENT '指标名称（如：收缩压、血糖）',
    `detail`      VARCHAR(500) DEFAULT NULL COMMENT '详细说明',
    `cover`       VARCHAR(500) DEFAULT NULL COMMENT '图标URL',
    `unit`        VARCHAR(50)  DEFAULT NULL COMMENT '单位（如：mmHg, mmol/L）',
    `symbol`      VARCHAR(50)  DEFAULT NULL COMMENT '比较符号（如：<, >）',
    `value_range` VARCHAR(100) DEFAULT NULL COMMENT '正常值范围（如：90-140）',
    `is_global`   TINYINT(1)   DEFAULT 0 COMMENT '是否全局指标：0=个人, 1=全局',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_is_global` (`is_global`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康指标配置表';


-- ============================================================
-- 5. 用户健康数据记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_health` (
    `id`                     INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `user_id`                INT          NOT NULL COMMENT '用户ID',
    `health_model_config_id` INT          NOT NULL COMMENT '指标配置ID',
    `value`                  VARCHAR(255) NOT NULL COMMENT '测量值',
    `create_time`            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_config_id` (`health_model_config_id`),
    INDEX `idx_create_time` (`create_time`),
    CONSTRAINT `fk_health_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_health_config` FOREIGN KEY (`health_model_config_id`) REFERENCES `health_model_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户健康数据记录表';


-- ============================================================
-- 6. 标签表
-- ============================================================
CREATE TABLE IF NOT EXISTS `tags` (
    `id`   INT AUTO_INCREMENT PRIMARY KEY COMMENT '标签ID',
    `name` VARCHAR(100) NOT NULL COMMENT '标签名称',
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';


-- ============================================================
-- 7. 资讯文章表
-- ============================================================
CREATE TABLE IF NOT EXISTS `news` (
    `id`          INT AUTO_INCREMENT PRIMARY KEY COMMENT '文章ID',
    `name`        VARCHAR(200) NOT NULL COMMENT '标题',
    `content`     TEXT         DEFAULT NULL COMMENT '正文内容',
    `tag_id`      INT          DEFAULT NULL COMMENT '标签ID',
    `cover`       VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `reader_ids`  TEXT         DEFAULT NULL COMMENT '已读用户ID列表（逗号分隔）',
    `is_top`      TINYINT(1)   DEFAULT 0 COMMENT '是否置顶：0=否, 1=是',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    INDEX `idx_tag_id` (`tag_id`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_is_top` (`is_top`),
    CONSTRAINT `fk_news_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯文章表';


-- ============================================================
-- 8. 资讯收藏表
-- ============================================================
CREATE TABLE IF NOT EXISTS `news_save` (
    `id`          INT AUTO_INCREMENT PRIMARY KEY COMMENT '收藏ID',
    `user_id`     INT NOT NULL COMMENT '用户ID',
    `news_id`     INT NOT NULL COMMENT '文章ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY `uk_user_news` (`user_id`, `news_id`),
    INDEX `idx_news_id` (`news_id`),
    CONSTRAINT `fk_save_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_save_news` FOREIGN KEY (`news_id`) REFERENCES `news`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯收藏表';


-- ============================================================
-- 9. 评论/评价表
-- ============================================================
CREATE TABLE IF NOT EXISTS `evaluations` (
    `id`           INT AUTO_INCREMENT PRIMARY KEY COMMENT '评论ID',
    `parent_id`    INT          DEFAULT NULL COMMENT '父评论ID（NULL=一级评论）',
    `commenter_id` INT          NOT NULL COMMENT '评论者用户ID',
    `replier_id`   INT          DEFAULT NULL COMMENT '被回复者用户ID',
    `content_type` VARCHAR(50)  DEFAULT NULL COMMENT '内容类型（news/health）',
    `content_id`   INT          NOT NULL COMMENT '被评论内容ID',
    `content`      TEXT         NOT NULL COMMENT '评论内容',
    `upvote_list`  TEXT         DEFAULT NULL COMMENT '点赞用户ID列表（逗号分隔）',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_commenter_id` (`commenter_id`),
    INDEX `idx_content` (`content_type`, `content_id`),
    INDEX `idx_create_time` (`create_time`),
    CONSTRAINT `fk_eval_parent` FOREIGN KEY (`parent_id`) REFERENCES `evaluations`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_eval_commenter` FOREIGN KEY (`commenter_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论/评价表';


-- ============================================================
-- 10. 消息通知表
-- ============================================================
CREATE TABLE IF NOT EXISTS `message` (
    `id`           INT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    `content`      VARCHAR(500) NOT NULL COMMENT '消息内容',
    `message_type` INT          NOT NULL COMMENT '消息类型：0=系统, 1=评论回复, 2=点赞',
    `receiver_id`  INT          NOT NULL COMMENT '接收者用户ID',
    `sender_id`    INT          DEFAULT NULL COMMENT '发送者用户ID',
    `is_read`      TINYINT(1)   DEFAULT 0 COMMENT '已读状态：0=未读, 1=已读',
    `content_id`   INT          DEFAULT NULL COMMENT '关联内容ID',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX `idx_receiver_id` (`receiver_id`),
    INDEX `idx_is_read` (`is_read`),
    INDEX `idx_create_time` (`create_time`),
    CONSTRAINT `fk_msg_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_msg_sender` FOREIGN KEY (`sender_id`) REFERENCES `user`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';
