-- ============================================================
-- 个人健康管理系统 - 完整数据库初始化脚本
-- 包含：用户表、健康模型配置表、用户健康记录表、AI聊天记录表
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------
-- 1. 用户表 (user)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  `user_account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户账号',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户昵称',
  `user_pwd` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户密码',
  `user_avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户头像',
  `user_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户邮箱',
  `user_role` int(11) NULL DEFAULT 2 COMMENT '用户角色(1:管理员;2:用户)',
  `is_login` tinyint(1) NULL DEFAULT 0 COMMENT '可登录状态(0:可用;1:不可用)',
  `is_word` tinyint(1) NULL DEFAULT 0 COMMENT '禁言状态(0:可用;1:不可用)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户注册时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_account`(`user_account`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 2. 健康模型配置表 (health_model_config)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `health_model_config` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '配置名',
  `detail` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '配置简介',
  `cover` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '图标',
  `unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '单位',
  `symbol` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '符号',
  `value_range` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '正常值范围(格式:最小值,最大值)',
  `is_global` tinyint(1) NULL DEFAULT 0 COMMENT '是否是全局模型(0:否;1:是)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 3. 用户健康记录表 (user_health)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_health` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '用户ID',
  `health_model_config_id` int(11) NULL DEFAULT NULL COMMENT '健康模型ID',
  `value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户记录的值',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_health_model_config_id`(`health_model_config_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 4. AI对话会话表 (ai_conversation)
-- -----------------------------------------------------------
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

-- -----------------------------------------------------------
-- 5. AI聊天记录表 (ai_chat_record)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_chat_record` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` int(11) NULL DEFAULT NULL COMMENT '会话ID',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '用户ID',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '发送者角色：user/assistant',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '聊天内容',
  `agent_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT 'AI角色类型：doctor/nutritionist/psychologist/analyst/general_assistant',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_id`(`conversation_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_agent_type`(`agent_type`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_user_agent`(`user_id`, `agent_type`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 6. 标签表 (tags)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tags` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '标签名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 7. 健康资讯表 (news)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `news` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '标题',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '内容',
  `tag_id` int(11) NULL DEFAULT NULL COMMENT '标签ID',
  `cover` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '封面',
  `reader_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '已读用户ID列表',
  `is_top` tinyint(1) NULL DEFAULT 0 COMMENT '是否置顶',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 8. 资讯收藏表 (news_save)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `news_save` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '用户ID',
  `news_id` int(11) NULL DEFAULT NULL COMMENT '资讯ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 9. 消息表 (message)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '消息内容',
  `message_type` int(11) NULL DEFAULT NULL COMMENT '消息类型(1:评论回复;2:评论点赞;3:指标提醒;4:系统通知)',
  `receiver_id` int(11) NULL DEFAULT NULL COMMENT '接收者ID',
  `sender_id` int(11) NULL DEFAULT NULL COMMENT '发送者ID',
  `is_read` tinyint(1) NULL DEFAULT 0 COMMENT '是否已读(0:未读;1:已读)',
  `content_id` int(11) NULL DEFAULT NULL COMMENT '关联内容ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 10. 评论表 (evaluations)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `evaluations` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` int(11) NULL DEFAULT NULL COMMENT '父级评论ID',
  `commenter_id` int(11) NULL DEFAULT NULL COMMENT '评论者ID',
  `replier_id` int(11) NULL DEFAULT NULL COMMENT '回复者ID',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '内容类型',
  `content_id` int(11) NULL DEFAULT NULL COMMENT '内容ID',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '评论内容',
  `upvote_list` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '点赞列表，以","分割',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 初始数据：默认管理员账号
-- -----------------------------------------------------------
INSERT INTO `user` (`user_account`, `user_name`, `user_pwd`, `user_role`, `is_login`, `is_word`)
VALUES ('admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', 1, 0, 0)
ON DUPLICATE KEY UPDATE `user_account` = `user_account`;

-- -----------------------------------------------------------
-- 初始数据：全局健康模型配置
-- -----------------------------------------------------------
INSERT INTO `health_model_config` (`user_id`, `name`, `detail`, `unit`, `symbol`, `value_range`, `is_global`) VALUES
(1, '身高', '记录身高数据', 'cm', 'Height', '100,220', 1),
(1, '体重', '记录体重数据', 'KG', 'Weight', '30,200', 1),
(1, '收缩压', '记录收缩压数据', 'mmHg', 'Systolic', '90,140', 1),
(1, '舒张压', '记录舒张压数据', 'mmHg', 'Diastolic', '60,90', 1),
(1, '心率', '记录心率数据', '次/分', 'Heart Rate', '60,100', 1),
(1, '体温', '记录体温数据', '℃', 'Temperature', '36.0,37.3', 1),
(1, '血糖', '记录血糖数据', 'mmol/L', 'Blood Glucose', '3.9,6.1', 1),
(1, '血氧', '记录血氧饱和度', '%', 'SpO2', '95,100', 1)
ON DUPLICATE KEY UPDATE `name` = `name`;

SET FOREIGN_KEY_CHECKS = 1;
