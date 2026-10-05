-- ============================================================
-- 智康云健康管理系统 - VIP 字段迁移脚本（Phase B，1.2 VIP 分级窗口）
-- 适用：已在运行的既有数据库（init_database.sql 已含这些列，新装环境无需执行）
-- 执行：mysql -u root -p personal_health < Data/sql/vip_migration.sql
-- ============================================================

ALTER TABLE `user`
  ADD COLUMN `is_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'VIP(0:否;1:是)' AFTER `is_word`,
  ADD COLUMN `vip_expire_time` DATETIME DEFAULT NULL COMMENT 'VIP到期时间(NULL=永久)' AFTER `is_vip`;
