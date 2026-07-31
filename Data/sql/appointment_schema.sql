-- ============================================================
-- 智康云 - 医生预约模块数据库表结构
-- 版本: v1.0
-- 真实医生排班系统（非AI医生）
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 第一部分：科室与医生
-- ============================================================

-- 1. 科室表
CREATE TABLE IF NOT EXISTS `department` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '科室名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '科室描述',
  `cover` VARCHAR(500) DEFAULT NULL COMMENT '科室图片',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:停用;1:启用)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- 2. 医生表
CREATE TABLE IF NOT EXISTS `hospital_doctor` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT NULL COMMENT '关联用户表ID(可为空)',
  `name` VARCHAR(50) NOT NULL COMMENT '医生姓名',
  `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像',
  `title` VARCHAR(50) DEFAULT NULL COMMENT '职称(主任医师/副主任医师/主治医师/住院医师)',
  `department_id` INT NOT NULL COMMENT '所属科室ID',
  `introduction` TEXT COMMENT '个人简介',
  `expertise` VARCHAR(500) DEFAULT NULL COMMENT '擅长领域',
  `qualifications` VARCHAR(500) DEFAULT NULL COMMENT '资质信息',
  `is_online` TINYINT(1) DEFAULT 0 COMMENT '在线状态(0:离线;1:在线)',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:停用;1:启用)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_department_id` (`department_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生表';

-- ============================================================
-- 第二部分：排班与预约
-- ============================================================

-- 3. 医生排班表（乐观锁：version字段）
CREATE TABLE IF NOT EXISTS `doctor_schedule` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doctor_id` INT NOT NULL COMMENT '医生ID',
  `schedule_date` DATE NOT NULL COMMENT '排班日期',
  `time_slot` VARCHAR(20) NOT NULL COMMENT '时间段(morning/afternoon/evening)',
  `max_patients` INT UNSIGNED NOT NULL DEFAULT 30 COMMENT '号源总数',
  `booked_count` INT UNSIGNED DEFAULT 0 COMMENT '已预约数',
  `version` INT UNSIGNED DEFAULT 0 COMMENT '乐观锁版本号',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:停诊;1:正常)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doctor_date_slot` (`doctor_id`, `schedule_date`, `time_slot`),
  INDEX `idx_schedule_date` (`schedule_date`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生排班表';

-- 4. 预约记录表（防重复：排班+序号联合唯一）
CREATE TABLE IF NOT EXISTS `appointment` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `patient_id` INT NOT NULL COMMENT '患者用户ID',
  `doctor_id` INT NOT NULL COMMENT '医生ID',
  `schedule_id` INT NOT NULL COMMENT '排班ID',
  `department_id` INT NOT NULL COMMENT '科室ID',
  `appointment_date` DATE NOT NULL COMMENT '预约日期',
  `time_slot` VARCHAR(20) NOT NULL COMMENT '时间段(morning/afternoon/evening)',
  `serial_number` INT UNSIGNED NOT NULL COMMENT '就诊序号',
  `symptom_description` VARCHAR(1000) DEFAULT NULL COMMENT '症状描述',
  `cancel_reason` VARCHAR(500) DEFAULT NULL COMMENT '取消原因',
  `status` TINYINT(1) DEFAULT 0 COMMENT '状态(0:待确认;1:已确认;2:已完成;3:已取消;4:已爽约)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_serial` (`schedule_id`, `serial_number`),
  INDEX `idx_patient_id` (`patient_id`),
  INDEX `idx_doctor_id` (`doctor_id`),
  INDEX `idx_appointment_date` (`appointment_date`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约记录表';

-- ============================================================
-- 第三部分：就诊记录
-- ============================================================

-- 5. 就诊记录表
CREATE TABLE IF NOT EXISTS `visit_record` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `appointment_id` INT NOT NULL COMMENT '预约ID',
  `patient_id` INT NOT NULL COMMENT '患者ID',
  `doctor_id` INT NOT NULL COMMENT '医生ID',
  `chief_complaint` VARCHAR(1000) DEFAULT NULL COMMENT '主诉',
  `present_illness` TEXT COMMENT '现病史',
  `diagnosis` VARCHAR(500) DEFAULT NULL COMMENT '诊断',
  `prescription` TEXT COMMENT '处方/医嘱',
  `examination_results` TEXT COMMENT '检查结果',
  `follow_up_plan` VARCHAR(500) DEFAULT NULL COMMENT '随访计划',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment` (`appointment_id`),
  INDEX `idx_patient_id` (`patient_id`),
  INDEX `idx_doctor_id` (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='就诊记录表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 默认科室数据
-- ============================================================
INSERT IGNORE INTO `department` (`id`, `name`, `description`, `sort_order`) VALUES
(1, '内科', '内脏疾病诊治', 1),
(2, '外科', '手术治疗', 2),
(3, '儿科', '儿童疾病', 3),
(4, '妇产科', '妇科及产科', 4),
(5, '眼科', '眼病诊治', 5),
(6, '耳鼻喉科', '耳鼻喉疾病', 6),
(7, '皮肤科', '皮肤病诊治', 7),
(8, '中医科', '中医诊疗', 8),
(9, '骨科', '骨关节疾病', 9),
(10, '神经内科', '神经系统疾病', 10);

SELECT '医生预约模块表结构创建完成！共5张表。' AS status;
