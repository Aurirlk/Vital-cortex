-- ============================================================
-- 智康云 - 健康管理系统 数据库初始化脚本
-- 版本: v5.0
-- 
-- 使用方法:
-- 1. CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
-- 2. USE personal_health;
-- 3. 执行本文件
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 第一部分：核心业务表
-- ============================================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_account` VARCHAR(50) NOT NULL COMMENT '用户账号',
  `user_name` VARCHAR(50) DEFAULT NULL COMMENT '用户昵称',
  `user_pwd` VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  `user_avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `user_email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `user_role` INT DEFAULT 2 COMMENT '角色(1:管理员;2:用户)',
  `is_login` TINYINT(1) DEFAULT 0 COMMENT '是否可登录(0:可登录;1:锁定禁止)',
  `is_word` TINYINT(1) DEFAULT 0 COMMENT '禁言(0:正常;1:禁言)',
  `is_vip` TINYINT(1) DEFAULT 0 COMMENT 'VIP(0:否;1:是)',
  `vip_expire_time` DATETIME DEFAULT NULL COMMENT 'VIP到期时间(NULL=永久)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`user_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 资讯分类表
CREATE TABLE IF NOT EXISTS `tags` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) DEFAULT NULL COMMENT '分类名称',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 健康资讯表
CREATE TABLE IF NOT EXISTS `news` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) DEFAULT NULL COMMENT '标题',
  `content` LONGTEXT COMMENT '内容',
  `tag_id` INT DEFAULT NULL COMMENT '分类ID',
  `cover` VARCHAR(255) DEFAULT NULL COMMENT '封面图',
  `reader_ids` TEXT COMMENT '阅读者ID列表',
  `is_top` TINYINT(1) DEFAULT NULL COMMENT '是否置顶',
  `is_banner` TINYINT(1) DEFAULT NULL COMMENT '是否轮播图',
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 收藏记录表
CREATE TABLE IF NOT EXISTS `news_save` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT NULL,
  `news_id` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_news_id` (`news_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 评论表
CREATE TABLE IF NOT EXISTS `evaluations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id` INT DEFAULT NULL COMMENT '父评论ID',
  `commenter_id` INT DEFAULT NULL COMMENT '评论者ID',
  `replier_id` INT DEFAULT NULL COMMENT '回复者ID',
  `content_type` VARCHAR(100) DEFAULT NULL,
  `content_id` INT DEFAULT NULL,
  `content` VARCHAR(255) DEFAULT NULL,
  `upvote_list` LONGTEXT COMMENT '点赞列表',
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 消息通知表
CREATE TABLE IF NOT EXISTS `message` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `content` VARCHAR(500) DEFAULT NULL,
  `user_id` INT DEFAULT NULL COMMENT '接收者ID',
  `send_id` INT DEFAULT NULL COMMENT '发送者ID',
  `replier_id` INT DEFAULT NULL,
  `is_read` TINYINT(1) DEFAULT 0 COMMENT '未读:0;已读:1',
  `other` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 第二部分：健康指标表
-- ============================================================

-- 7. 健康模型配置表
CREATE TABLE IF NOT EXISTS `health_model_config` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT NULL COMMENT '用户ID(NULL=全局)',
  `name` VARCHAR(100) DEFAULT NULL COMMENT '指标名称',
  `detail` VARCHAR(500) DEFAULT NULL COMMENT '指标描述',
  `cover` VARCHAR(255) DEFAULT NULL COMMENT '图标',
  `unit` VARCHAR(50) DEFAULT NULL COMMENT '单位',
  `symbol` VARCHAR(100) DEFAULT NULL COMMENT '符号',
  `value_range` VARCHAR(100) DEFAULT NULL COMMENT '正常范围',
  `is_global` TINYINT(1) DEFAULT 0 COMMENT '是否全局(0:否;1:是)',
  `category` VARCHAR(20) DEFAULT 'PERSONALIZED' COMMENT 'PUBLIC/PERSONALIZED',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 用户健康记录表
CREATE TABLE IF NOT EXISTS `user_health` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT NULL,
  `health_model_config_id` INT DEFAULT NULL,
  `value` VARCHAR(100) DEFAULT NULL COMMENT '记录值',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_health_model_config_id` (`health_model_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 用户画像表
CREATE TABLE IF NOT EXISTS `patient_profile` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `gender` VARCHAR(10) DEFAULT NULL,
  `age` INT DEFAULT NULL,
  `birth_date` DATE DEFAULT NULL,
  `height` DECIMAL(5,2) DEFAULT NULL COMMENT '身高cm',
  `weight` DECIMAL(5,2) DEFAULT NULL COMMENT '体重kg',
  `bmi` DECIMAL(5,2) DEFAULT NULL,
  `chronic_diseases` JSON COMMENT '基础疾病',
  `allergies` JSON COMMENT '过敏史',
  `medications` JSON COMMENT '用药史',
  `surgeries` JSON COMMENT '手术史',
  `family_history` JSON COMMENT '家族病史',
  `lifestyle` JSON COMMENT '生活习惯',
  `health_goals` JSON COMMENT '健康目标',
  `fasting_blood_glucose` DECIMAL(5,2) COMMENT '空腹血糖',
  `postprandial_blood_glucose` DECIMAL(5,2) COMMENT '餐后血糖',
  `total_cholesterol` DECIMAL(5,2) COMMENT '总胆固醇',
  `triglycerides` DECIMAL(5,2) COMMENT '甘油三酯',
  `hdl_cholesterol` DECIMAL(5,2) COMMENT '高密度脂蛋白',
  `ldl_cholesterol` DECIMAL(5,2) COMMENT '低密度脂蛋白',
  `systolic_pressure` INT COMMENT '收缩压',
  `diastolic_pressure` INT COMMENT '舒张压',
  `resting_heart_rate` INT COMMENT '静息心率',
  `last_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 第三部分：药品表
-- ============================================================

-- 10. 药品信息表
CREATE TABLE IF NOT EXISTS `drug` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL COMMENT '药品名称',
  `generic_name` VARCHAR(200) DEFAULT NULL COMMENT '通用名',
  `category` VARCHAR(100) DEFAULT NULL COMMENT '分类',
  `description` TEXT COMMENT '说明',
  `price` DECIMAL(10,2) DEFAULT NULL,
  `unit` VARCHAR(50) DEFAULT NULL,
  `specification` VARCHAR(200) DEFAULT NULL COMMENT '规格',
  `manufacturer` VARCHAR(200) DEFAULT NULL COMMENT '厂家',
  `cover` VARCHAR(500) DEFAULT NULL,
  `is_otc` TINYINT(1) DEFAULT 1 COMMENT '0:处方药;1:OTC',
  `stock` INT DEFAULT 0,
  `status` TINYINT(1) DEFAULT 1 COMMENT '0:下架;1:上架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_name` (`name`),
  INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. 药品订阅表
CREATE TABLE IF NOT EXISTS `drug_subscription` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `drug_id` INT NOT NULL,
  `quantity` INT DEFAULT 1,
  `status` TINYINT(1) DEFAULT 1 COMMENT '0:取消;1:有效',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_drug` (`user_id`, `drug_id`),
  INDEX `idx_drug_id` (`drug_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 第四部分：AI相关表
-- ============================================================

-- 12. AI会话表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `title` VARCHAR(255) DEFAULT '',
  `agent_type` VARCHAR(50) DEFAULT '',
  `message_count` INT DEFAULT 0,
  `last_message_time` DATETIME,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_agent_type` (`agent_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. AI聊天记录表
CREATE TABLE IF NOT EXISTS `ai_chat_record` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` INT NOT NULL,
  `user_id` INT NOT NULL,
  `role` VARCHAR(20) NOT NULL COMMENT 'user/assistant',
  `content` TEXT,
  `agent_type` VARCHAR(50) DEFAULT '',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_conversation_id` (`conversation_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. AI配置表
CREATE TABLE IF NOT EXISTS `ai_config` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
  `config_value` TEXT COMMENT '配置值',
  `description` VARCHAR(255) DEFAULT '',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. 系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `config_group` VARCHAR(50) NOT NULL COMMENT '配置分组',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键名',
  `config_value` TEXT COMMENT '配置值',
  `description` VARCHAR(255) DEFAULT '',
  `sensitive` TINYINT(1) DEFAULT 0 COMMENT '是否敏感',
  `value_type` VARCHAR(20) DEFAULT 'string' COMMENT '值类型',
  `default_value` VARCHAR(500) DEFAULT '',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 第五部分：默认数据
-- ============================================================

-- 默认管理员 (密码: 123456)
INSERT IGNORE INTO `user` (`id`, `user_account`, `user_name`, `user_pwd`, `user_role`, `is_login`, `is_word`) VALUES
(1, 'admin', '管理员', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, 0, 0),
(2, 'user', '普通用户', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 2, 0, 0);

-- 默认健康模型
INSERT IGNORE INTO `health_model_config` (`id`, `user_id`, `name`, `detail`, `cover`, `unit`, `symbol`, `value_range`, `is_global`, `category`) VALUES
(1, NULL, '收缩压', '心脏收缩时动脉血压最高值', 'blood-pressure', 'mmHg', 'SBP', '90,140', 1, 'PUBLIC'),
(2, NULL, '舒张压', '心脏舒张时动脉血压最低值', 'blood-pressure', 'mmHg', 'DBP', '60,90', 1, 'PUBLIC'),
(3, NULL, '空腹血糖', '空腹时血液中的葡萄糖浓度', 'blood-sugar', 'mmol/L', 'FPG', '3.9,6.1', 1, 'PUBLIC'),
(4, NULL, '体重指数', '体重与身高的平方之比', 'bmi', 'kg/m²', 'BMI', '18.5,24.9', 1, 'PERSONALIZED'),
(5, NULL, '心率', '每分钟心跳次数', 'heart-rate', '次/分', 'HR', '60,100', 1, 'PERSONALIZED');

-- 默认标签
INSERT IGNORE INTO `tags` (`id`, `name`) VALUES
(1, '饮食健康'), (2, '运动健身'), (3, '心理健康'), (4, '疾病预防'), (5, '养生保健');

-- AI配置
INSERT IGNORE INTO `ai_config` (`config_key`, `config_value`, `description`) VALUES
('provider', 'deepseek', 'AI厂商'),
('api_key', '', '普通对话API Key'),
('api_url', 'https://api.deepseek.com/v1/chat/completions', 'API地址'),
('model', 'deepseek-v4-flash', '模型'),
('reasoner_api_key', '', '深度思考API Key'),
('reasoner_api_url', 'https://api.deepseek.com/v1/chat/completions', '深度思考API地址'),
('reasoner_model', 'deepseek-v4-pro', '深度思考模型'),
('embedding_api_key', '', 'Embedding API Key'),
('embedding_api_url', 'https://api.deepseek.com/v1/embeddings', 'Embedding API地址'),
('embedding_model', 'text-embedding-3-small', 'Embedding模型'),
('web_search_enabled', 'true', '联网搜索启用'),
('web_search_provider', 'auto', '搜索引擎'),
('connect_timeout', '30000', '连接超时'),
('read_timeout', '60000', '读取超时'),
('max_tokens', '4096', '最大Token数'),
('max_history_rounds', '10', '最大历史轮数');

-- 系统配置
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `sensitive`, `value_type`, `default_value`) VALUES
('mysql', 'url', 'jdbc:mysql://localhost:3306/personal_health?characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true', '数据库URL', 1, 'string', ''),
('mysql', 'username', 'root', '数据库用户名', 1, 'string', 'root'),
('mysql', 'password', '1234', '数据库密码', 1, 'string', ''),
('mysql', 'driver', 'com.mysql.cj.jdbc.Driver', '驱动类', 0, 'string', 'com.mysql.cj.jdbc.Driver'),
('mysql', 'pool-min-idle', '5', '最小空闲连接', 0, 'number', '5'),
('mysql', 'pool-max-size', '20', '最大连接数', 0, 'number', '20'),
('server', 'port', '21090', '服务端口', 0, 'number', '21090'),
('server', 'context-path', '/api/personal-health/v1.0', '上下文路径', 0, 'string', '/api/personal-health/v1.0'),
('websocket', 'enabled', 'true', 'WebSocket启用', 0, 'boolean', 'true'),
('websocket', 'port', '21091', 'WebSocket端口', 0, 'number', '21091'),
('websocket', 'max-connections', '1000', '最大连接数', 0, 'number', '1000'),
('ota', 'enabled', 'false', 'OTA启用', 0, 'boolean', 'false'),
('ota', 'server-url', '', 'OTA服务器', 0, 'string', ''),
('ota', 'check-interval', '3600', '检查间隔', 0, 'number', '3600'),
('sqlite', 'enabled', 'false', 'SQLite启用', 0, 'boolean', 'false'),
('sqlite', 'db-path', './data/local.db', 'SQLite路径', 0, 'string', './data/local.db'),
('ai', 'provider', 'deepseek', 'AI厂商', 0, 'string', 'deepseek'),
('ai', 'api-key', '', 'AI API Key', 1, 'string', ''),
('ai', 'reasoner-api-key', '', '深度思考Key', 1, 'string', ''),
('ai', 'bocha-api-key', '', '博查搜索Key', 1, 'string', ''),
('ai', 'max-tokens', '4096', '最大Token数', 0, 'number', '4096'),
('jwt', 'secret', '***REMOVED***', 'JWT密钥', 1, 'string', ''),
('jwt', 'expiration', '604800000', 'JWT过期时间(ms)', 0, 'number', '604800000'),
('admin', 'password', 'admin123', '管理员密码', 1, 'string', 'admin123');

SELECT '数据库初始化完成！共15张表。' AS status;
