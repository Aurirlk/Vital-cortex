-- ============================================================================
-- 智能体配置种子（数据智能体 + 上下文压缩 + 长短期记忆 + 沙箱）
--
-- 落点：system_config 表，config_group = 'data_agent'
--       （复用既有分组，不新建表 —— 铁律「能加列就不建新表」）
--
-- 覆盖范围：
--   1. 全局开关与限流        —— 已于 2026-10-07 首批写入，这里保持不变（幂等）
--   2. 按使用者分层          —— 键后缀 -user / -doctor（复用 preset-questions-* 的既有范式）
--   3. 上下文压缩            —— 接此前「配了不生效」的装饰性配置项
--   4. 长短期记忆            —— 打开用户偏好注入 prompt
--   5. 沙箱                  —— 结果字节上限、守卫开关、只读源标记
--
-- 执行前置：必须先完成 02_迁移脚本/09_create_ai_ro_user.sql（建 ai_ro 只读账号），
--           否则等于给登录用户开放主账号的 SQL 执行面。
--
-- 幂等：全部 INSERT IGNORE + uk_group_key 唯一键，可反复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 全局开关与限流（首批已写，此处保留以便全新部署时一次跑全）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `value_type`, `default_value`) VALUES
('data_agent', 'enabled',                'true',  '数据智能体总开关（未分层时的默认值）',                                  'boolean', 'false'),
('data_agent', 'report-enabled',         'true',  '查询结果是否自动生成 Markdown 报告',                                     'boolean', 'true'),
('data_agent', 'max-rows',               '200',   '单次查询最大返回行数（未分层时的默认值）',                                'number',  '200'),
('data_agent', 'query-timeout',          '10',    '单条 SQL 执行超时秒数（未分层时的默认值）',                              'number',  '10');

-- ---------------------------------------------------------------------------
-- 2. 按使用者分层
--
--    读取规则（DataAgentConfigHolder.rawForAudience）：
--      先查 `键-user` / `键-doctor`，未配置则回落无后缀的全局键，再回落 default_value。
--    ⇒ 这几个键【不配置也完全兼容】原有行为，只有显式配置才会覆盖全局值。
--
--    默认策略：医生端查的是【患者】数据、且多表 JOIN 更多，故给更宽的行数与更长超时；
--              用户端只查本人，收得更紧。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `value_type`, `default_value`) VALUES
-- 开关：留空（''）表示「不覆盖，用全局值」。若只想给某一端开放/关闭，改成 true / false。
('data_agent', 'enabled-user',           '',      '【用户端】是否开放数据智能体（空=用全局值）',                          'boolean', ''),
('data_agent', 'enabled-doctor',         '',      '【医生端】是否开放数据智能体（空=用全局值）',                          'boolean', ''),
-- 报告：同样留空表示跟随全局
('data_agent', 'report-enabled-user',    '',      '【用户端】是否自动生成报告（空=用全局值）',                            'boolean', ''),
('data_agent', 'report-enabled-doctor',  '',      '【医生端】是否自动生成报告（空=用全局值）',                            'boolean', ''),
-- 行数：医生端放宽到 500（需要看趋势），用户端收紧到 200
('data_agent', 'max-rows-user',          '200',   '【用户端】单次查询最大返回行数',                                        'number',  '200'),
('data_agent', 'max-rows-doctor',        '500',   '【医生端】单次查询最大返回行数（需看趋势，默认更宽）',                'number',  '500'),
-- 超时：医生端 15 秒（多表 JOIN），用户端 10 秒
('data_agent', 'query-timeout-user',     '10',    '【用户端】单条 SQL 超时秒数',                                           'number',  '10'),
('data_agent', 'query-timeout-doctor',   '15',    '【医生端】单条 SQL 超时秒数（多表 JOIN 场景）',                       'number',  '15');

-- ---------------------------------------------------------------------------
-- 3. 上下文压缩（AI 问诊链路，此前这些配置项「配了不生效」）
--
--    接线位置：AiServiceImpl.appendCompressedHistory()
--      · context-rounds            保留最近 N 轮（一轮 = user + assistant 两条），0 = 不携带历史
--      · context-max-chars         历史正文字符总预算，超出从【最旧】整条丢弃
--      · context-summary-threshold 丢弃条数 ≥ 该值时，生成一条滚动摘要（关键词提取，零 LLM 调用）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `value_type`, `default_value`) VALUES
('data_agent', 'context-rounds',             '10',    '上下文压缩：送入 prompt 的历史轮数上限（0=不携带历史）',             'number',  '10'),
('data_agent', 'context-max-chars',          '4000',  '上下文压缩：历史正文字符总预算（超出从最旧整条丢弃）',            'number',  '4000'),
('data_agent', 'context-summary-threshold',  '12',    '上下文压缩：丢弃条数达该值时生成滚动摘要（0=不摘要）',            'number',  '12');

-- ---------------------------------------------------------------------------
-- 4. 长短期记忆（用户偏好跨会话）
--
--    接线位置：BaseReActAgent.buildMemorySection() 与 DataAgentPipeline.safeMemory()
--    ⚠️ 接线前：AgentMemoryService 只被 3 个 REST 端点调用，偏好可通过 API 读写
--       但从不进入 prompt → 对用户完全不可见。现已注入。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `value_type`, `default_value`) VALUES
('data_agent', 'memory-enabled',       'true', '长短期记忆：是否把用户偏好注入 prompt',                                'boolean', 'true'),
('data_agent', 'memory-max-chars',     '800',  '长短期记忆：注入 prompt 的偏好段字符上限（ReAct 链路）',             'number',  '800'),
('data_agent', 'memory-max-users',    '1000', '长短期记忆：偏好缓存用户数上限（LRU 淘汰）',                        'number',  '1000');

-- ---------------------------------------------------------------------------
-- 5. 沙箱
--
--    ⚠️ sandbox-readonly 是【只读标记】，由程序在启动自检时回写，管理员不要手改：
--       前端据此展示「只读账号是否已配置」，避免误以为已隔离而实际还在用主账号。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `value_type`, `default_value`) VALUES
('data_agent', 'sandbox-max-bytes',     '524288', '沙箱：单次查询结果字节上限（512KB，防止宽表挤爆 token）',           'number',  '524288'),
('data_agent', 'sandbox-guard-enabled', 'true',   '沙箱：是否启用 SQL 词法守卫（生产环境强烈建议开启）',               'boolean', 'true'),
('data_agent', 'sandbox-readonly',      'false',  '沙箱：只读数据源是否已配置（只读标记，由程序回写，勿手改）',       'boolean', 'false');

-- ---------------------------------------------------------------------------
-- 验证：应看到 24 行
--   SELECT config_key, config_value FROM system_config WHERE config_group='data_agent' ORDER BY config_key;
-- ---------------------------------------------------------------------------