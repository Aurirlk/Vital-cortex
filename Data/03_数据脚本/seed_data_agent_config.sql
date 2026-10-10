-- ============================================================================
-- 数据智能体（受控 NL2SQL）开关种子配置
--
-- ⛔ 安全前置：本脚本会【开启】受控 NL2SQL 执行面。执行前必须先完成
--    Data/02_迁移脚本/09_create_ai_ro_user.sql（创建 ai_ro 只读账号）
--    并在 application-local.yml 配好 crm.ai.nl2sql.read-only.*。
--    否则模型生成的 SQL 会以【主账号】权限执行，等于给每个登录用户开放写权限。
--
-- ✅ 幂等：system_config 有唯一键 uk_group_key(config_group, config_key)，
--    用 INSERT IGNORE，重复执行不会产生重复行，也不会覆盖已有值。
--    （如需强制改值，用 UPDATE，见文件末尾「回滚 / 调整」）
--
-- 📌 运行时以本表为准，优先级高于 application.yml 的 crm.ai.nl2sql.*；
--    管理员也可在「系统配置 → 数据智能体」在线改，改完 10s 内生效、无需重启
--    （DataAgentConfigHolder 的 TTL 为 10 秒）。
--
-- 📌 回滚 / 关闭（无需重启，10s 内生效）：
--    UPDATE system_config SET config_value='false'
--      WHERE config_group='data_agent' AND config_key='enabled';
-- ============================================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO `system_config`
    (`config_group`, `config_key`, `config_value`, `description`,
     `sensitive`, `value_type`, `default_value`, `create_time`, `update_time`)
VALUES
    ('data_agent', 'enabled', 'true',
     '是否开启数据智能体（受控 NL2SQL）', 0, 'boolean', 'false', NOW(), NOW()),
    ('data_agent', 'report-enabled', 'true',
     '查询结果是否自动生成 Markdown 分析报告', 0, 'boolean', 'true', NOW(), NOW()),
    ('data_agent', 'max-rows', '200',
     '单次查询最大返回行数（SQL 层硬限，防全表拖库）', 0, 'number', '200', NOW(), NOW()),
    ('data_agent', 'query-timeout', '10',
     '单条 SQL 执行超时（秒）', 0, 'number', '10', NOW(), NOW());

-- ============================================================================
-- 验证（应返回 4 行，enabled=true）：
--   SELECT config_key, config_value FROM system_config
--     WHERE config_group='data_agent' ORDER BY config_key;
-- ============================================================================
