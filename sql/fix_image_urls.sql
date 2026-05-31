-- ============================================================
-- 修复图片URL路径
-- 将 personal-heath 改为 personal-health
-- ============================================================

SET NAMES utf8mb4;

-- 修复 user 表的 user_avatar 字段
UPDATE user SET user_avatar = REPLACE(user_avatar, 'personal-heath', 'personal-health') 
WHERE user_avatar LIKE '%personal-heath%';

-- 验证修复结果
SELECT id, user_account, user_avatar FROM user WHERE user_avatar IS NOT NULL LIMIT 5;

SELECT '图片URL修复完成！' AS status;
