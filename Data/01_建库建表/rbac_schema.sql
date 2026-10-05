-- ============================================================
-- 智康云 - RBAC 权限系统数据库表结构
-- 版本: v1.0
-- 基于现有 user 表扩展，添加角色权限管理
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 第一部分：角色表
-- ============================================================

-- 1. 角色表
CREATE TABLE IF NOT EXISTS `role` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:禁用;1:启用)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 2. 权限表
CREATE TABLE IF NOT EXISTS `permission` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '权限名称',
  `code` VARCHAR(100) NOT NULL COMMENT '权限编码(如:user:create, post:delete)',
  `type` TINYINT(1) NOT NULL COMMENT '类型(1:菜单;2:按钮;3:API)',
  `parent_id` INT DEFAULT NULL COMMENT '父权限ID',
  `path` VARCHAR(200) DEFAULT NULL COMMENT '路由路径(菜单权限)',
  `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标(菜单权限)',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0:禁用;1:启用)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 3. 角色-权限关联表
CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_id` INT NOT NULL COMMENT '角色ID',
  `permission_id` INT NOT NULL COMMENT '权限ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 4. 用户-角色关联表
CREATE TABLE IF NOT EXISTS `user_role` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '用户ID',
  `role_id` INT NOT NULL COMMENT '角色ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 第二部分：默认数据
-- ============================================================

-- 默认角色
INSERT IGNORE INTO `role` (`id`, `name`, `code`, `description`) VALUES
(1, '超级管理员', 'super_admin', '拥有所有权限'),
(2, '管理员', 'admin', '除系统配置外的所有权限'),
(3, '医生', 'doctor', '排班管理、预约管理、就诊记录、随访任务'),
(4, '商家', 'merchant', '商品管理、订单管理、数据统计'),
(5, '用户', 'user', '基础功能');

-- 默认权限（菜单权限）
INSERT IGNORE INTO `permission` (`id`, `name`, `code`, `type`, `parent_id`, `path`, `icon`, `sort_order`) VALUES
-- 用户端菜单
(1, '健康资讯', 'news:view', 1, NULL, '/user/news-record', 'Document', 1),
(2, '医生预约', 'appointment:view', 1, NULL, '/user/appointment', 'Calendar', 2),
(3, '健康测验', 'quiz:view', 1, NULL, '/user/quiz', 'EditPen', 3),
(4, '健康商城', 'mall:view', 1, NULL, '/user/mall', 'ShoppingCart', 4),
(5, '患者随访', 'followup:view', 1, NULL, '/user/followup', 'Check', 5),
(6, 'AI健康分析', 'ai:view', 1, NULL, '/user/ai-analysis', 'ChatDotRound', 6),
(7, '药品订阅', 'drug:view', 1, NULL, '/user/drug', 'FirstAidKit', 7),
(8, '个人中心', 'profile:view', 1, NULL, '/user/profile', 'User', 8),

-- 管理端菜单
(100, '仪表盘', 'dashboard:view', 1, NULL, '/admin/adminLayout', 'PieChart', 100),
(101, '用户管理', 'user:manage', 1, NULL, '/admin/userManage', 'User', 101),
(102, '资讯管理', 'news:manage', 1, NULL, '/admin/newsManage', 'Document', 102),
(103, '预约管理', 'appointment:manage', 1, NULL, '/admin/appointmentManage', 'Calendar', 103),
(104, '测验管理', 'quiz:manage', 1, NULL, '/admin/quizManage', 'EditPen', 104),
(105, '商城管理', 'mall:manage', 1, NULL, '/admin/mallManage', 'ShoppingCart', 105),
(106, '随访管理', 'followup:manage', 1, NULL, '/admin/followupManage', 'Check', 106),
(107, 'AI配置', 'ai:config', 1, NULL, '/admin/aiAnalysis', 'Setting', 107),
(108, '系统配置', 'system:config', 1, NULL, '/admin/systemConfig', 'Setting', 108),

-- 医生端菜单
(200, '排班管理', 'schedule:manage', 1, NULL, '/doctor/schedule', 'Calendar', 200),
(201, '预约管理', 'appointment:doctor', 1, NULL, '/doctor/appointments', 'Document', 201),
(202, '就诊记录', 'visit:manage', 1, NULL, '/doctor/visits', 'Notebook', 202),

-- 商家端菜单
(300, '商品管理', 'product:manage', 1, NULL, '/merchant/products', 'ShoppingCart', 300),
(301, '订单管理', 'order:manage', 1, NULL, '/merchant/orders', 'Document', 301),
(302, '数据统计', 'stats:view', 1, NULL, '/merchant/stats', 'DataLine', 302),

-- 按钮权限
(1001, '创建帖子', 'post:create', 2, 1, NULL, NULL, 1001),
(1002, '编辑帖子', 'post:update', 2, 1, NULL, NULL, 1002),
(1003, '删除帖子', 'post:delete', 2, 1, NULL, NULL, 1003),
(1004, '预约挂号', 'appointment:create', 2, 2, NULL, NULL, 1004),
(1005, '取消预约', 'appointment:cancel', 2, 2, NULL, NULL, 1005),
(1006, '答题', 'quiz:answer', 2, 3, NULL, NULL, 1006),
(1007, '下单', 'order:create', 2, 4, NULL, NULL, 1007),
(1008, '打卡', 'followup:checkin', 2, 5, NULL, NULL, 1008),

-- 管理员按钮权限
(2001, '创建用户', 'user:create', 2, 101, NULL, NULL, 2001),
(2002, '编辑用户', 'user:update', 2, 101, NULL, NULL, 2002),
(2003, '删除用户', 'user:delete', 2, 101, NULL, NULL, 2003),
(2004, '创建资讯', 'news:create', 2, 102, NULL, NULL, 2004),
(2005, '编辑资讯', 'news:update', 2, 102, NULL, NULL, 2005),
(2006, '删除资讯', 'news:delete', 2, 102, NULL, NULL, 2006),
(2007, '创建题目', 'question:create', 2, 104, NULL, NULL, 2007),
(2008, '创建商品', 'product:create', 2, 105, NULL, NULL, 2008),
(2009, '创建任务', 'task:create', 2, 106, NULL, NULL, 2009);

-- 角色-权限关联（超级管理员拥有所有权限）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `permission`;

-- 管理员权限（除系统配置外）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, `id` FROM `permission` WHERE `code` NOT LIKE 'system:%' AND `id` < 1000;

-- 医生权限
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`) VALUES
(3, 1), (3, 2), (3, 5), (3, 8),  -- 基础用户功能
(3, 200), (3, 201), (3, 202);    -- 医生端功能

-- 商家权限
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`) VALUES
(4, 1), (4, 4), (4, 8),          -- 基础用户功能
(4, 300), (4, 301), (4, 302);    -- 商家端功能

-- 用户权限
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`) VALUES
(5, 1), (5, 2), (5, 3), (5, 4), (5, 5), (5, 6), (5, 7), (5, 8),  -- 用户端菜单
(5, 1001), (5, 1002), (5, 1004), (5, 1005), (5, 1006), (5, 1007), (5, 1008);  -- 用户端按钮

-- 为现有用户分配默认角色（用户角色）
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT `id`, 2 FROM `user` WHERE `user_role` = 1;  -- 现有管理员分配管理员角色

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT `id`, 5 FROM `user` WHERE `user_role` = 2;  -- 现有用户分配用户角色

SELECT 'RBAC 权限系统表结构创建完成！共4张表。' AS status;
