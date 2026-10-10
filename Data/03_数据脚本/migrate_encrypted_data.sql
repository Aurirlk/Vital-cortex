-- ============================================
-- 智康云 - 敏感数据迁移脚本
-- 将现有明文 API Key 加密存储
-- 使用前请确保已设置 AES_SECRET_KEY 环境变量
--
-- ⚠️ 2026-10-07 标注：本文件**不是演示数据**，而是**一次性运维脚本**，
--    与同目录的 mock/seed 脚本性质不同，请**单独、按需**执行。
--    执行后会多出一张备份表 `ai_config_backup`（设计如此，非缺陷）；
--    确认加密无误后可 DROP 掉它。
--    README「场景 A 全新部署」只跑 01 + 02，**不含本目录**。
-- ============================================

-- 备份原始数据
-- 显式声明连接编码，避免依赖客户端默认值（否则中文 INSERT/列注释会报 ERROR 1406 或乱码）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_config_backup` AS SELECT * FROM `ai_config`;

-- 注意：实际加密需要在应用层执行（Java AES加密）
-- 此脚本仅创建备份表，加密操作通过应用接口完成

-- 验证备份
SELECT '备份完成，共' AS status, COUNT(*) AS count FROM `ai_config_backup`;

-- 加密操作说明：
-- 1. 启动应用后调用 POST /api/personal-health/v1.0/admin/encrypt-configs
-- 2. 应用会自动将明文 API Key 加密
-- 3. 验证加密后功能正常
-- 4. 确认无误后可删除备份表：DROP TABLE IF EXISTS `ai_config_backup`;
