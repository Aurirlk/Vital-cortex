-- ============================================
-- 智康云 - 敏感数据迁移脚本
-- 将现有明文 API Key 加密存储
-- 使用前请确保已设置 AES_SECRET_KEY 环境变量
-- ============================================

-- 备份原始数据
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
