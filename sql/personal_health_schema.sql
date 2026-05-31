-- ============================================================
-- 个人健康管理系统 - 数据库初始化脚本（安全版本）
-- 执行顺序：先执行此文件创建表结构，再执行 personal_health_data.sql 导入数据
-- 注意：请确保数据库使用 utf8mb4 字符集
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------
-- 1. 用户表 (user)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户账号',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户昵称',
  `user_pwd` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户密码',
  `user_avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户头像',
  `user_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '用户邮箱',
  `user_role` int(11) NULL DEFAULT 2 COMMENT '用户角色(1:管理员;2:用户)',
  `is_login` tinyint(1) NULL DEFAULT 0 COMMENT '可登录状态(0:禁止;1:允许)',
  `is_word` tinyint(1) NULL DEFAULT 0 COMMENT '禁言状态(0:正常;1:禁言)',
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
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '指标名称',
  `detail` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '指标描述',
  `cover` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '图标',
  `unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '单位',
  `symbol` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '符号',
  `value_range` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '参考值范围(格式:最小值,最大值)',
  `is_global` tinyint(1) NULL DEFAULT 0 COMMENT '是否为全局模型(0:否;1:是)',
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
  INDEX `idx_health_model_config_id`(`health_model_config_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 4. 标签表 (tags)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tags` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '标签名称',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 5. 健康资讯表 (news)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `news` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '健康资讯ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '标题',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '内容',
  `tag_id` int(11) NULL DEFAULT NULL COMMENT '标签ID',
  `cover` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '图片封面',
  `reader_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '阅读者ID列表，用逗号分割',
  `is_top` tinyint(1) NULL DEFAULT NULL COMMENT '是否推荐',
  `create_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '健康资讯表' ROW_FORMAT = Dynamic;

-- -----------------------------------------------------------
-- 6. 收藏记录表 (news_save)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `news_save` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '用户ID',
  `news_id` int(11) NULL DEFAULT NULL COMMENT '资讯ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_news_id`(`news_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 7. 评论表 (evaluations)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `evaluations` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` int(11) NULL DEFAULT NULL COMMENT '父级评论ID',
  `commenter_id` int(11) NULL DEFAULT NULL COMMENT '评论者ID',
  `replier_id` int(11) NULL DEFAULT NULL COMMENT '回复者ID',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '内容类型',
  `content_id` int(11) NULL DEFAULT NULL COMMENT '内容ID',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '评论内容',
  `upvote_list` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '点赞列表，以逗号分割',
  `create_time` datetime NULL DEFAULT NULL COMMENT '评论时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 126 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 8. 消息通知表 (message)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '消息内容',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '接收用户ID',
  `send_id` int(11) NULL DEFAULT NULL COMMENT '发送者ID',
  `replier_id` int(11) NULL DEFAULT NULL COMMENT '回复者ID',
  `is_read` tinyint(1) NULL DEFAULT 0 COMMENT '是否已读(0:未读;1:已读)',
  `other` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '其他信息',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 9. AI会话表 (ai_conversation)
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
-- 10. AI聊天记录表 (ai_chat_record)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_chat_record` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `conversation_id` int(11) NULL DEFAULT NULL COMMENT '会话ID',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '用户ID',
  `agent_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT 'AI角色类型',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '角色(user/assistant)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '消息内容',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_conversation_id`(`conversation_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 11. 药品信息表 (drug)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `drug` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '药品ID',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '药品名称',
  `generic_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '通用名',
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '药品分类',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '药品说明',
  `price` decimal(10,2) NULL DEFAULT NULL COMMENT '价格',
  `unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '单位',
  `specification` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '规格',
  `manufacturer` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '生产厂家',
  `cover` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '药品图片',
  `is_otc` tinyint(1) NULL DEFAULT 1 COMMENT '是否OTC(0:处方药;1:OTC)',
  `stock` int(11) NULL DEFAULT 0 COMMENT '库存',
  `status` tinyint(1) NULL DEFAULT 1 COMMENT '状态(0:下架;1:上架)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_name`(`name`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 12. 药品订阅表 (drug_subscription)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `drug_subscription` (
  `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订阅ID',
  `user_id` int(11) NOT NULL COMMENT '用户ID',
  `drug_id` int(11) NOT NULL COMMENT '药品ID',
  `quantity` int(11) NULL DEFAULT 1 COMMENT '订阅数量',
  `status` tinyint(1) NULL DEFAULT 1 COMMENT '状态(0:取消;1:有效)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_drug`(`user_id`, `drug_id`) USING BTREE,
  INDEX `idx_drug_id`(`drug_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------
-- 插入默认数据
-- -----------------------------------------------------------

-- 插入默认管理员账号 (密码: 123456)
INSERT INTO `user` (`id`, `user_account`, `user_name`, `user_pwd`, `user_role`, `is_login`, `is_word`, `create_time`) VALUES
(1, 'admin', '管理员', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, 1, 0, '2024-01-01 00:00:00'),
(2, 'user', '普通用户', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 2, 1, 0, '2024-01-01 00:00:00');

-- 插入默认健康模型配置
INSERT INTO `health_model_config` (`id`, `user_id`, `name`, `detail`, `cover`, `unit`, `symbol`, `value_range`, `is_global`) VALUES
(1, NULL, '收缩压', '心脏收缩时动脉血压的最高值', 'blood-pressure', 'mmHg', 'SBP', '90,140', 1),
(2, NULL, '舒张压', '心脏舒张时动脉血压的最低值', 'blood-pressure', 'mmHg', 'DBP', '60,90', 1),
(3, NULL, '空腹血糖', '空腹时血液中的葡萄糖浓度', 'blood-sugar', 'mmol/L', 'FPG', '3.9,6.1', 1),
(4, NULL, '体重指数', '体重与身高的平方之比', 'bmi', 'kg/m²', 'BMI', '18.5,24.9', 1),
(5, NULL, '心率', '每分钟心跳次数', 'heart-rate', '次/分', 'HR', '60,100', 1);

-- 插入标签数据
INSERT INTO `tags` (`id`, `name`, `create_time`) VALUES
(1, '饮食健康', '2024-01-01 00:00:00'),
(2, '运动健身', '2024-01-01 00:00:00'),
(3, '心理健康', '2024-01-01 00:00:00'),
(4, '疾病预防', '2024-01-01 00:00:00'),
(5, '养生保健', '2024-01-01 00:00:00');

SELECT '数据库初始化完成！' AS status;
