-- ============================================================================
-- 数据智能体 · 只读数据库账号（运维脚本 · 需 DBA/root 权限）
--
-- ⛔ 本脚本【不属于库内迁移】，不在 Data/ 自动部署链中执行，需 DBA 手工执行。
-- ⛔ 开启数据智能体（受控 NL2SQL）的【安全前置条件】：
--    NL2SQL 会把模型生成的 SQL 打到真实库上执行。虽然有
--    MySqlAiSqlGuard（词法守卫，禁 DML/DDL/黑名单表）+ TenantSqlRewriter（逐表租户覆盖）
--    两道兜底，但词法守卫是正则级的，不能作为唯一防线。
--    账号层必须再兜一层：只有 SELECT 权限，即使守卫被绕过也无法写入或删库。
--
-- 📌 执行前：把下方 <设置强密码> 替换为真实密码（不要提交到 git）
-- 📌 执行后：在 application.yml 配置 crm.ai.nl2sql.read-only.* 并重启后端
-- 📌 回滚：DROP USER 'ai_ro'@'%';（后端会自动回落主数据源）
-- ============================================================================

SET NAMES utf8mb4;

-- 1. 建账号（密码请替换，建议 ≥24 位随机串）
CREATE USER IF NOT EXISTS 'ai_ro'@'%'
  IDENTIFIED BY '<设置强密码>';

-- 2. 先清空该账号可能残留的任何权限，确保最小权限起点
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'ai_ro'@'%';

-- 3. 只授予 SELECT。
--    注意：这里授予的是整个库（personal_health），黑名单表
--    （user / role / permission / system_config / audit_log）由
--    Nl2SqlSchemaService.TABLE_BLACKLIST 与 MySqlAiSqlGuard.TABLE_BLACKLIST
--    在应用层拦截（两处必须与本脚本保持一致口径）。
--    如需在数据库层也彻底隔离，请先确认应用其他模块不依赖这些表的只读查询，
--    再改为逐表 GRANT —— 本项目未采用，因为 ai_config 等表仍需被 NL2SQL 读取。
GRANT SELECT ON `personal_health`.* TO 'ai_ro'@'%';

-- 4. 显式禁止账号被授予权限给他人
--    （MySQL 中 GRANT OPTION 默认不授予，此处为可读性保留说明）

FLUSH PRIVILEGES;

-- ============================================================================
-- 验证（执行后应返回 1 行，且 Grant 列只有 SELECT）
--   SHOW GRANTS FOR 'ai_ro'@'%';
--
-- 验证只读确实生效（应报 INSERT command denied）
--   mysql -u ai_ro -p personal_health -e "INSERT INTO notification (id) VALUES (1);"
-- ============================================================================
