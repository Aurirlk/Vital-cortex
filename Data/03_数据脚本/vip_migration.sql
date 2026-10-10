-- ============================================================
-- 智康云健康管理系统 - VIP 字段迁移脚本（Phase B，1.2 VIP 分级窗口）
-- 适用：已在运行的既有数据库（init_database.sql 已含这些列，新装环境无需执行）
-- 执行：mysql -u root -p <库名> < Data/03_数据脚本/vip_migration.sql
--
-- 2026-10-07 修正：改为**幂等**。原脚本直接 `ALTER TABLE ... ADD COLUMN`，
--   在 init_database.sql 建出的库上重跑会报
--   `ERROR 1060 (42S21): Duplicate column name 'is_vip'`，
--   导致「按 README 顺序跑完 03_数据脚本」中断。
--   MySQL 9.7 无 ADD COLUMN IF NOT EXISTS，故用 information_schema 判定 + 动态 DDL。
-- ============================================================

-- 显式声明连接编码，避免依赖客户端默认值（否则中文 INSERT/列注释会报 ERROR 1406 或乱码）
SET NAMES utf8mb4;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='is_vip'),'DO 0','ALTER TABLE `user` ADD COLUMN `is_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''VIP(0:否;1:是)'' AFTER `is_word`');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='vip_expire_time'),'DO 0','ALTER TABLE `user` ADD COLUMN `vip_expire_time` DATETIME DEFAULT NULL COMMENT ''VIP到期时间(NULL=永久)'' AFTER `is_vip`');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;
