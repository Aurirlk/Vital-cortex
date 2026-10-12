-- ============================================================
-- 08_user_settings_columns.sql —— 把「用户设置」并入 user 表
--
-- 背景（2026-10-10 修复）：
--   用户设置页（前端 views/user/UserSettings.vue）读取的 language / timezone /
--   notification / privacy / ai 五组设置，此前在数据库里没有任何落脚点：
--   既没有 user_settings 表（这是当初有意的简化 —— 表太多，不单独建表），
--   也没有把这些字段并进 user 表。
--
--   证据：Data/04_结构基线/迁移后结构快照_20261003.sql 与
--   Data/90_归档/portable_20261004_141754.sql（最新全库快照）里的 user 表，
--   列只有 id / user_account / user_name / user_pwd / user_avatar / user_email /
--   phone / phone_verified / user_role / is_login / is_word / is_vip /
--   vip_expire_time / create_time；全仓对 user 表的 ALTER 也只有
--   phone、phone_verified、idx_user_phone 与 vip_migration.sql 的 VIP 两列。
--
--   后果：UserServiceImpl.getUserSettings 只能返回硬编码默认值，
--   updateUserSettings 只能如实报错「暂未实现」—— 设置页保存必然失败。
--
-- 做法：按「并入 user 表」的原意，直接给 user 加列，不新建表。
--   布尔项用 TINYINT(1)，与既有 is_login / is_word / is_vip 口径一致；
--   枚举项用 VARCHAR 并在应用层做白名单校验（见 UserServiceImpl#updateUserSettings）。
--
-- 幂等：⚠️ MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（那是 MariaDB 语法），
--   故与 01_schema_refactor.sql 同一套路：先查 information_schema，
--   把要执行的 DDL 拼成字符串再 PREPARE/EXECUTE。
--   ⚠️ 每条 SET 后面必须紧跟 PREPARE/EXECUTE —— 只 SET 不 EXECUTE 是空操作，
--      脚本会「跑成功但一列都没加」。
-- 注意：`user` 是 MySQL 保留字，反引号不能省。
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 基本设置
-- ----------------------------------------------------------------------------
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='language'),'DO 0','ALTER TABLE `user` ADD COLUMN language VARCHAR(20) NULL DEFAULT ''zh-CN'' COMMENT ''界面语言：zh-CN / en-US''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='timezone'),'DO 0','ALTER TABLE `user` ADD COLUMN timezone VARCHAR(50) NULL DEFAULT ''Asia/Shanghai'' COMMENT ''时区：Asia/Shanghai / America/New_York / Europe/London''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------------------------------------------
-- 通知设置（0 关 / 1 开）
-- 说明：这几列是为「通知发送方按用户偏好过滤」准备的，例如发预约提醒前
--       判断 notify_appointment = 1。写成独立列而非一个 JSON 串，正是为了
--       以后能直接 WHERE 过滤，不必解析 JSON。
-- ----------------------------------------------------------------------------
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='notify_system'),'DO 0','ALTER TABLE `user` ADD COLUMN notify_system TINYINT(1) NULL DEFAULT 1 COMMENT ''系统通知开关（0关1开）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='notify_health_remind'),'DO 0','ALTER TABLE `user` ADD COLUMN notify_health_remind TINYINT(1) NULL DEFAULT 1 COMMENT ''健康提醒开关（0关1开）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='notify_ai_chat'),'DO 0','ALTER TABLE `user` ADD COLUMN notify_ai_chat TINYINT(1) NULL DEFAULT 1 COMMENT ''AI 对话通知开关（0关1开）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='notify_appointment'),'DO 0','ALTER TABLE `user` ADD COLUMN notify_appointment TINYINT(1) NULL DEFAULT 1 COMMENT ''预约提醒开关（0关1开）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='notify_order'),'DO 0','ALTER TABLE `user` ADD COLUMN notify_order TINYINT(1) NULL DEFAULT 1 COMMENT ''订单通知开关（0关1开）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------------------------------------------
-- 隐私设置
-- ----------------------------------------------------------------------------
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='privacy_health_data_share'),'DO 0','ALTER TABLE `user` ADD COLUMN privacy_health_data_share TINYINT(1) NULL DEFAULT 1 COMMENT ''是否允许 AI 读取健康数据（0否1是）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='privacy_profile_visibility'),'DO 0','ALTER TABLE `user` ADD COLUMN privacy_profile_visibility VARCHAR(20) NULL DEFAULT ''private'' COMMENT ''资料可见性：public所有人 / private仅自己''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------------------------------------------
-- AI 助手设置
-- ----------------------------------------------------------------------------
SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_default_agent'),'DO 0','ALTER TABLE `user` ADD COLUMN ai_default_agent VARCHAR(50) NULL DEFAULT ''general_assistant'' COMMENT ''默认 AI 角色''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_chat_style'),'DO 0','ALTER TABLE `user` ADD COLUMN ai_chat_style VARCHAR(20) NULL DEFAULT ''friendly'' COMMENT ''对话风格：professional/friendly/concise''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_enable_web_search'),'DO 0','ALTER TABLE `user` ADD COLUMN ai_enable_web_search TINYINT(1) NULL DEFAULT 1 COMMENT ''允许联网搜索（0否1是）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_enable_knowledge_base'),'DO 0','ALTER TABLE `user` ADD COLUMN ai_enable_knowledge_base TINYINT(1) NULL DEFAULT 1 COMMENT ''允许检索知识库（0否1是）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @d = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_context_rounds'),'DO 0','ALTER TABLE `user` ADD COLUMN ai_context_rounds INT NULL DEFAULT 10 COMMENT ''保留上下文轮数（1~20）''');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------------------------------------------
-- 补空：理论上 ADD COLUMN ... DEFAULT 会让存量行直接取到默认值，
-- 但若历史上有人显式写过 NULL，这里统一回填，避免读到 NULL 时前端显示空白。
-- 应用层（UserServiceImpl#getUserSettings）对 NULL 也做了默认值兜底，双重保险。
-- ----------------------------------------------------------------------------
UPDATE `user` SET language = 'zh-CN' WHERE language IS NULL OR language = '';
UPDATE `user` SET timezone = 'Asia/Shanghai' WHERE timezone IS NULL OR timezone = '';
UPDATE `user` SET notify_system = 1 WHERE notify_system IS NULL;
UPDATE `user` SET notify_health_remind = 1 WHERE notify_health_remind IS NULL;
UPDATE `user` SET notify_ai_chat = 1 WHERE notify_ai_chat IS NULL;
UPDATE `user` SET notify_appointment = 1 WHERE notify_appointment IS NULL;
UPDATE `user` SET notify_order = 1 WHERE notify_order IS NULL;
UPDATE `user` SET privacy_health_data_share = 1 WHERE privacy_health_data_share IS NULL;
UPDATE `user` SET privacy_profile_visibility = 'private' WHERE privacy_profile_visibility IS NULL OR privacy_profile_visibility = '';
UPDATE `user` SET ai_default_agent = 'general_assistant' WHERE ai_default_agent IS NULL OR ai_default_agent = '';
UPDATE `user` SET ai_chat_style = 'friendly' WHERE ai_chat_style IS NULL OR ai_chat_style = '';
UPDATE `user` SET ai_enable_web_search = 1 WHERE ai_enable_web_search IS NULL;
UPDATE `user` SET ai_enable_knowledge_base = 1 WHERE ai_enable_knowledge_base IS NULL;
UPDATE `user` SET ai_context_rounds = 10 WHERE ai_context_rounds IS NULL OR ai_context_rounds < 1 OR ai_context_rounds > 20;

-- ----------------------------------------------------------------------------
-- 自检：跑完应返回 14
-- ----------------------------------------------------------------------------
SELECT COUNT(*) AS user_settings_columns_added
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
  AND COLUMN_NAME IN ('language','timezone',
                      'notify_system','notify_health_remind','notify_ai_chat',
                      'notify_appointment','notify_order',
                      'privacy_health_data_share','privacy_profile_visibility',
                      'ai_default_agent','ai_chat_style','ai_enable_web_search',
                      'ai_enable_knowledge_base','ai_context_rounds');

-- ============================================================
-- 执行后请重新生成结构文档（字段级基线会与库脱节）：
--   python Data/tools/gen_schema_doc.py
-- 并同步更新 Data/04_结构基线/迁移后结构快照_20261003.sql（或另存新快照）。
-- ============================================================
