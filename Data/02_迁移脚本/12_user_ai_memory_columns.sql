-- ============================================================
-- 12_user_ai_memory_columns.sql —— 结构化 AI 记忆列（阶段 8 / D3 改写）
--
-- 背景（2026-11-09）：
--   旧 P4 方案打算给 user 加 8 个手工画像列、另建 hospital_doctor_ai_profile
--   表、再配 8 个 REST，并把 initial_prompt 自由文本拼进 system prompt。
--   2026 技术重估后判定为安全反模式（提示词注入面 + 无按事实删除 + 无命名空间），
--   正式改写为「结构化 preference facts」：不建任何新表，只给现有两张身份表各加 3 列，
--   守住项目铁律「能加列就不建新表」。
--
-- 三列含义：
--   ai_memory_facts   JSON NULL   事实数组，元素形如
--                                 {"key":"diet","value":"素食","kind":"preference",
--                                  "confidence":0.9,"source":"explicit",
--                                  "createdAt":"...","updatedAt":"...","expiresAt":"..."}
--   ai_memory_enabled TINYINT(1)  是否允许把记忆注入 prompt（用户可关）
--   ai_memory_opt_out TINYINT(1)  是否退出记忆抽取（关掉后连 extract 都不跑）
--
-- 幂等：MySQL 8/9 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 才有），沿用
--   08_user_settings_columns.sql 的 information_schema + PREPARE/EXECUTE 套路。
--   ⚠️ 每条 SET 后必须紧跟 PREPARE/EXECUTE/DEALLOCATE，漏 EXECUTE 是空操作。
--   ⚠️ `user` 是保留字，反引号不能省。
--   ⚠️ 含中文 COMMENT，SET NAMES utf8mb4 不能少。
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- user 表（用户端记忆，命名空间 = user.id）
-- ----------------------------------------------------------------------------
SET @_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_memory_facts');
SET @_sql = IF(@_has > 0, 'DO 0', 'ALTER TABLE `user` ADD COLUMN ai_memory_facts JSON NULL COMMENT ''结构化AI记忆事实数组(JSON)''');
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

SET @_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_memory_enabled');
SET @_sql = IF(@_has > 0, 'DO 0', 'ALTER TABLE `user` ADD COLUMN ai_memory_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否允许AI记忆注入prompt(0关/1开)''');
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

SET @_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='ai_memory_opt_out');
SET @_sql = IF(@_has > 0, 'DO 0', 'ALTER TABLE `user` ADD COLUMN ai_memory_opt_out TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否退出AI记忆抽取(0否/1是)''');
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

-- ----------------------------------------------------------------------------
-- hospital_doctor 表（医生端记忆，命名空间 = hospital_doctor.id）
-- 医生与 user 表已解耦，两套 id 空间独立；分别存列即天然隔离，不建关联表。
-- ----------------------------------------------------------------------------
SET @_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='ai_memory_facts');
SET @_sql = IF(@_has > 0, 'DO 0', 'ALTER TABLE `hospital_doctor` ADD COLUMN ai_memory_facts JSON NULL COMMENT ''结构化AI记忆事实数组(JSON)''');
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

SET @_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='ai_memory_enabled');
SET @_sql = IF(@_has > 0, 'DO 0', 'ALTER TABLE `hospital_doctor` ADD COLUMN ai_memory_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否允许AI记忆注入prompt(0关/1开)''');
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

SET @_has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME='ai_memory_opt_out');
SET @_sql = IF(@_has > 0, 'DO 0', 'ALTER TABLE `hospital_doctor` ADD COLUMN ai_memory_opt_out TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否退出AI记忆抽取(0否/1是)''');
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

-- ----------------------------------------------------------------------------
-- 结果自检（不写业务数据，只回报列数，便于执行者核对）
-- ----------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME LIKE 'ai_memory_%') AS user_ai_memory_columns,
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_doctor' AND COLUMN_NAME LIKE 'ai_memory_%') AS doctor_ai_memory_columns;
