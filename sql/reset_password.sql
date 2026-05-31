-- ============================================================
-- 重置用户密码为双重加密格式 (MD5 + BCrypt)
-- 原密码: 123456
-- 双重MD5: 14e1b600b1fd579f47433b88e8d85291
-- BCrypt(双重MD5): $2a$10$YourBCryptHashHere
-- ============================================================

SET NAMES utf8mb4;

-- 需要先在后端生成BCrypt哈希值
-- 方法：启动后端，在日志中查看生成的哈希值
-- 或者使用以下步骤：
-- 1. 启动后端服务
-- 2. 注册一个新用户（密码会自动BCrypt加密）
-- 3. 从数据库复制该用户的密码哈希值
-- 4. 更新此SQL文件中的哈希值并执行

-- 临时方案：使用已知的BCrypt哈希值
-- 注意：这个哈希值需要根据实际生成的值来更新

-- 更新管理员密码
UPDATE `user` SET `user_pwd` = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH' 
WHERE `user_account` = 'admin' AND `user_role` = 1;

-- 更新普通用户密码
UPDATE `user` SET `user_pwd` = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH' 
WHERE `user_account` = 'user' AND `user_role` = 2;

SELECT '密码重置完成！' AS status;
SELECT '如果登录失败，请先注册一个新用户，然后复制其密码哈希值更新到数据库' AS 提示;
