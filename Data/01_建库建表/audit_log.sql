-- ============================================
-- 智康云 - 安全审计日志表
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT NULL COMMENT '操作用户ID',
  `user_name` VARCHAR(50) DEFAULT NULL COMMENT '操作用户名',
  `action` VARCHAR(100) NOT NULL COMMENT '操作类型(login/create/update/delete/export)',
  `resource` VARCHAR(100) NOT NULL COMMENT '资源类型(user/ai_config/drug/news等)',
  `resource_id` VARCHAR(50) DEFAULT NULL COMMENT '资源ID',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
  `request_method` VARCHAR(10) DEFAULT NULL COMMENT '请求方法',
  `request_url` VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:失败;1:成功)',
  `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_action` (`action`),
  INDEX `idx_resource` (`resource`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计日志表';

SET FOREIGN_KEY_CHECKS = 1;

SELECT '审计日志表创建完成！' AS status;
