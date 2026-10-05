-- ============================================================
-- 智康云 - 扩展模块数据库表结构
-- 版本: v1.0
-- 包含：商品分类、健康测验、健康商城、患者随访
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 第一部分：商品分类（参考资讯标签分类）
-- ============================================================

-- 1. 商品分类表（支持层级）
CREATE TABLE IF NOT EXISTS `product_category` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
  `parent_id` INT DEFAULT NULL COMMENT '父分类ID(NULL=顶级)',
  `icon` VARCHAR(500) DEFAULT NULL COMMENT '分类图标',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:停用;1:启用)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ============================================================
-- 第二部分：健康测验（在线考试系统）
-- ============================================================

-- 2. 题库表
CREATE TABLE IF NOT EXISTS `quiz_question` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category_id` INT DEFAULT NULL COMMENT '分类ID',
  `question_type` TINYINT(1) NOT NULL COMMENT '题型(0:单选;1:多选;2:判断;3:填空;4:简答)',
  `title` VARCHAR(1000) NOT NULL COMMENT '题目内容',
  `options` JSON DEFAULT NULL COMMENT '选项(JSON数组，仅选择题)',
  `answer` VARCHAR(2000) NOT NULL COMMENT '正确答案',
  `analysis` VARCHAR(2000) DEFAULT NULL COMMENT '解析',
  `difficulty` TINYINT(1) DEFAULT 2 COMMENT '难度(1:简单;2:中等;3:困难)',
  `score` INT UNSIGNED DEFAULT 1 COMMENT '分值',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:草稿;1:发布)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_category` (`category_id`),
  INDEX `idx_question_type` (`question_type`),
  INDEX `idx_difficulty` (`difficulty`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库表';

-- 3. 试卷表
CREATE TABLE IF NOT EXISTS `quiz_exam` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL COMMENT '试卷名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '试卷描述',
  `duration_minutes` INT UNSIGNED DEFAULT 60 COMMENT '考试时长(分钟)',
  `total_score` INT UNSIGNED DEFAULT 100 COMMENT '总分',
  `pass_score` INT UNSIGNED DEFAULT 60 COMMENT '及格分',
  `difficulty` TINYINT(1) DEFAULT 2 COMMENT '难度(1:简单;2:中等;3:困难)',
  `question_count` INT UNSIGNED DEFAULT 0 COMMENT '题目数量',
  `status` TINYINT(1) DEFAULT 0 COMMENT '状态(0:草稿;1:已发布)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷表';

-- 4. 试卷-题目关联表
CREATE TABLE IF NOT EXISTS `quiz_exam_question` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `exam_id` INT NOT NULL COMMENT '试卷ID',
  `question_id` INT NOT NULL COMMENT '题目ID',
  `score` INT UNSIGNED DEFAULT 1 COMMENT '该题分值',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_question` (`exam_id`, `question_id`),
  INDEX `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷题目关联表';

-- 5. 考试记录表
CREATE TABLE IF NOT EXISTS `quiz_record` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `exam_id` INT NOT NULL COMMENT '试卷ID',
  `user_id` INT NOT NULL COMMENT '用户ID',
  `start_time` DATETIME COMMENT '开始时间',
  `submit_time` DATETIME COMMENT '提交时间',
  `score` INT UNSIGNED DEFAULT 0 COMMENT '得分',
  `total_score` INT UNSIGNED DEFAULT 0 COMMENT '试卷总分',
  `correct_count` INT UNSIGNED DEFAULT 0 COMMENT '正确题数',
  `question_count` INT UNSIGNED DEFAULT 0 COMMENT '题目总数',
  `status` TINYINT(1) DEFAULT 0 COMMENT '状态(0:进行中;1:已提交;2:已批改)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_exam_id` (`exam_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试记录表';

-- 6. 答题记录表
CREATE TABLE IF NOT EXISTS `quiz_answer` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `record_id` INT NOT NULL COMMENT '考试记录ID',
  `question_id` INT NOT NULL COMMENT '题目ID',
  `answer` VARCHAR(2000) DEFAULT NULL COMMENT '学生答案',
  `score` INT UNSIGNED DEFAULT 0 COMMENT '得分',
  `is_correct` TINYINT(1) DEFAULT 0 COMMENT '是否正确(0:错;1:对)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_record_id` (`record_id`),
  INDEX `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录表';

-- ============================================================
-- 第三部分：健康商城
-- ============================================================

-- 7. 商城商品表
CREATE TABLE IF NOT EXISTS `mall_product` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category_id` INT DEFAULT NULL COMMENT '分类ID',
  `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
  `description` TEXT COMMENT '商品描述',
  `cover` VARCHAR(500) DEFAULT NULL COMMENT '商品图片',
  `price` DECIMAL(10,2) NOT NULL COMMENT '售价',
  `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原价',
  `stock` INT UNSIGNED DEFAULT 0 COMMENT '库存',
  `sales_count` INT UNSIGNED DEFAULT 0 COMMENT '销量',
  `unit` VARCHAR(50) DEFAULT '件' COMMENT '单位',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:下架;1:上架)',
  `is_hot` TINYINT(1) DEFAULT 0 COMMENT '是否热销',
  `is_new` TINYINT(1) DEFAULT 0 COMMENT '是否新品',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_category_id` (`category_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_is_hot` (`is_hot`),
  INDEX `idx_is_new` (`is_new`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城商品表';

-- 8. 购物车表（防重复：用户+商品联合唯一）
CREATE TABLE IF NOT EXISTS `shopping_cart` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '用户ID',
  `product_id` INT NOT NULL COMMENT '商品ID',
  `quantity` INT UNSIGNED DEFAULT 1 COMMENT '数量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- 9. 商城订单表
CREATE TABLE IF NOT EXISTS `mall_order` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
  `user_id` INT NOT NULL COMMENT '用户ID',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总额',
  `actual_amount` DECIMAL(10,2) NOT NULL COMMENT '实付金额',
  `status` TINYINT(1) DEFAULT 0 COMMENT '状态(0:待付款;1:已付款;2:已发货;3:已收货;4:已完成;5:已取消)',
  `payment_method` VARCHAR(50) DEFAULT NULL COMMENT '支付方式(模拟)',
  `payment_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `shipping_address_id` INT DEFAULT NULL COMMENT '收货地址ID',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '订单备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城订单表';

-- 10. 订单商品表
CREATE TABLE IF NOT EXISTS `order_item` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` INT NOT NULL COMMENT '订单ID',
  `product_id` INT NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称(快照)',
  `product_price` DECIMAL(10,2) NOT NULL COMMENT '商品单价(快照)',
  `product_cover` VARCHAR(500) DEFAULT NULL COMMENT '商品图片(快照)',
  `quantity` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '数量',
  `subtotal` DECIMAL(10,2) NOT NULL COMMENT '小计',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品表';

-- 11. 收货地址表
CREATE TABLE IF NOT EXISTS `shipping_address` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '用户ID',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收件人姓名',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收件人电话',
  `province` VARCHAR(50) DEFAULT NULL COMMENT '省',
  `city` VARCHAR(50) DEFAULT NULL COMMENT '市',
  `district` VARCHAR(50) DEFAULT NULL COMMENT '区',
  `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
  `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否默认(0:否;1:是)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- ============================================================
-- 第四部分：患者随访
-- ============================================================

-- 12. 随访任务表
CREATE TABLE IF NOT EXISTS `followup_task` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `patient_id` INT NOT NULL COMMENT '患者用户ID',
  `doctor_id` INT NOT NULL COMMENT '医生ID',
  `title` VARCHAR(200) NOT NULL COMMENT '任务标题',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT '任务描述',
  `task_type` VARCHAR(50) NOT NULL COMMENT '任务类型(medication/appointment/indicator/exercise/diet)',
  `due_date` DATE NOT NULL COMMENT '截止日期',
  `status` TINYINT(1) DEFAULT 0 COMMENT '状态(0:待完成;1:进行中;2:已完成;3:已逾期;4:已取消)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_patient_id` (`patient_id`),
  INDEX `idx_doctor_id` (`doctor_id`),
  INDEX `idx_due_date` (`due_date`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随访任务表';

-- 13. 随访打卡记录表
CREATE TABLE IF NOT EXISTS `followup_record` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` INT NOT NULL COMMENT '任务ID',
  `patient_id` INT NOT NULL COMMENT '患者ID',
  `content` VARCHAR(1000) DEFAULT NULL COMMENT '打卡内容/备注',
  `proof` JSON DEFAULT NULL COMMENT '证明材料(图片/视频JSON)',
  `status` TINYINT(1) DEFAULT 0 COMMENT '状态(0:正常;1:异常)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_task_id` (`task_id`),
  INDEX `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随访打卡记录表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 默认商品分类数据
-- ============================================================
INSERT IGNORE INTO `product_category` (`id`, `name`, `sort_order`) VALUES
(1, '药品', 1), (2, '医疗器械', 2), (3, '保健品', 3), (4, '健康食品', 4), (5, '健身器材', 5);

SELECT '扩展模块表结构创建完成！共13张表。' AS status;
