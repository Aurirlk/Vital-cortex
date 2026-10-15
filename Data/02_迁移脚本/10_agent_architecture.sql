-- ============================================================
-- 10_agent_architecture.sql —— 多 Agent 架构地基（阶段 1）
--
-- 目标：把原先硬编码在 Java 里的「Agent 角色 + 关键词打分路由」
--       升级为「DB 注册表 + 真正的编排器」，使管理员可以新增 Agent、
--       绑定工具、配置委派关系，并留下可观测的运行记录。
--
-- 新增 5 张表：
--   agent_config        Agent 注册表（定义 prompt / 模型 / 参数 / 适用端）
--   agent_tool_binding  per-Agent 工具白名单（多对多）
--   agent_handoff       允许的 Agent 间委派关系（多对多）
--   agent_run           Agent 级运行记录（token / 耗时 / 迭代数）
--   agent_run_step      运行步骤明细（llm / tool / handoff）
--
-- 为什么这 5 张必须是表，而不是塞进 system_config / ai_config 的 KV 行：
--   项目铁律是「能加列就不建新表」，但那条铁律针对的是**配置项**。
--   本组数据是两个多对多关系（Agent↔工具、Agent↔Agent）加一张追加型事件表 ——
--   用 KV 存会导致：工具绑定无法做唯一约束、委派关系无法 JOIN 查询、
--   运行记录按 token/耗时做聚合分析时要在应用层反序列化全部 JSON。
--   对照先例：audit_log 同样是「追加型事件表 + bigint 主键」，未并入任何 KV 表。
--
-- 与既有「默认 Agent」的关系（重要，避免双真值源）：
--   ai_config 表已有 `ai_default_agent`（默认 general_assistant）。
--   本脚本把该值**迁入** agent_config.is_default 作为唯一权威；
--   AgentRegistry 在「没有任何 is_default=1 的行」时会回退读 ai_config，
--   以兼容尚未执行本脚本种子的环境。两者不会同时生效。
--
-- 惯例对齐（照抄现有表）：
--   · id 用 int unsigned；agent_run / agent_run_step 是追加型事件表，
--     照 audit_log 的先例用 bigint unsigned
--   · 全库**无物理外键**，引用完整性由代码维护，故这里也不加 FOREIGN KEY
--   · utf8mb4 / utf8mb4_0900_ai_ci，每列带 COMMENT
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. Agent 注册表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_config` (
  `id`               INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `agent_key`        VARCHAR(64)   NOT NULL                COMMENT 'Agent 唯一标识（英文小写下划线，程序内引用；与 ai_conversation.agent_type 同口径）',
  `name`             VARCHAR(64)   NOT NULL                COMMENT '展示名',
  `description`      VARCHAR(255)  DEFAULT NULL            COMMENT '职责说明；同时作为路由依据之一',
  `avatar`           VARCHAR(255)  DEFAULT NULL            COMMENT '头像地址或图标名',
  `system_prompt`    MEDIUMTEXT    DEFAULT NULL            COMMENT '系统提示词（为空则用全局默认）',
  `model`            VARCHAR(64)   DEFAULT NULL            COMMENT '指定模型（为空则用全局默认模型）',
  `temperature`      DECIMAL(3,2)  DEFAULT NULL            COMMENT '采样温度 0.00~2.00（为空则用全局默认）',
  `top_p`            DECIMAL(4,3)  DEFAULT NULL            COMMENT '核采样 top_p 0.000~1.000（为空则用全局默认）',
  `max_tokens`       INT           DEFAULT NULL            COMMENT '单次回复 token 上限（为空则用全局默认）',
  `audience`         VARCHAR(16)   NOT NULL DEFAULT 'all'  COMMENT '适用端：user 用户端 / doctor 医生端 / admin 管理端 / all 全部',
  `max_iterations`   INT           NOT NULL DEFAULT 5      COMMENT 'ReAct 最大迭代数（含工具调用轮次），防止无限循环',
  `routing_keywords` VARCHAR(500)  DEFAULT NULL            COMMENT '路由关键词，英文逗号分隔；命中即优先路由到本 Agent',
  `enabled`          TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '是否启用（0停用 1启用）',
  `is_default`       TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否为该端的兜底 Agent（0否 1是）；每端至多一个，唯一性由代码保证（MySQL 无法对「按 audience 分组的唯一」建索引）',
  `sort_order`       INT           NOT NULL DEFAULT 0      COMMENT '排序权重（小在前）',
  `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_key` (`agent_key`),
  KEY `idx_agent_audience` (`audience`, `enabled`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 注册表（多 Agent 架构）';

-- ------------------------------------------------------------
-- 2. per-Agent 工具白名单
--
--    语义：**有绑定行 = 白名单生效**（只给 bind 里 enabled=1 的工具）；
--          **零绑定行 = 不限制**（给全量工具）。
--    后者是刻意保留的向后兼容：存量 Agent 不配工具时行为与改造前一致，
--    避免升级瞬间所有 Agent 集体失去工具能力。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_tool_binding` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `agent_id`    INT UNSIGNED NOT NULL          COMMENT 'agent_config.id',
  `tool_name`   VARCHAR(64)  NOT NULL          COMMENT '工具名，须与 ToolRegistry 注册的 function name 完全一致',
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用（0停用 1启用）',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序权重（小在前）',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool` (`agent_id`, `tool_name`),
  KEY `idx_atb_agent` (`agent_id`, `enabled`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 工具白名单';

-- ------------------------------------------------------------
-- 3. Agent 间委派关系
--
--    语义：from → to 允许委派。零绑定行 = 不允许任何委派（默认关闭）。
--    防环：编排器在运行时按 (from,to) 有向图做环检测，本表只存许可关系。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_handoff` (
  `id`             INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `from_agent_id`  INT UNSIGNED NOT NULL          COMMENT '发起委派的 Agent（agent_config.id）',
  `to_agent_id`    INT UNSIGNED NOT NULL          COMMENT '被委派的目标 Agent（agent_config.id）',
  `condition_desc` VARCHAR(255) DEFAULT NULL      COMMENT '何时委派的说明（给人看，供提示词参考）',
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用（0停用 1启用）',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_handoff` (`from_agent_id`, `to_agent_id`),
  KEY `idx_handoff_from` (`from_agent_id`, `enabled`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 委派关系';

-- ------------------------------------------------------------
-- 4. Agent 运行记录
--
--    run_id 用字符串而非自增主键：一次用户提问跨多个 Agent（handoff 子运行），
--    需要一个贯穿父子的关联键，自增 id 无法承担。
--    answer 只存摘要不存全文：全文已在 ai_conversation / SQLite 历史里，
--    这里存全文会让本表迅速膨胀且与既有存储重复。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_run` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id`            VARCHAR(64)     NOT NULL             COMMENT '本次运行的唯一 id（贯穿父 Agent 与其委派的子 Agent）',
  `parent_run_id`     VARCHAR(64)     DEFAULT NULL         COMMENT '父运行 run_id（仅 handoff 子运行有值）',
  `agent_id`          INT UNSIGNED    DEFAULT NULL         COMMENT '执行的 Agent（agent_config.id）',
  `agent_key`         VARCHAR(64)     DEFAULT NULL         COMMENT '冗余 agent_key：Agent 被删除后运行历史仍可读',
  `session_id`        VARCHAR(64)     DEFAULT NULL         COMMENT '会话 id（与 ai_conversation / SQLite 历史同一口径）',
  `user_id`           INT UNSIGNED    DEFAULT NULL         COMMENT '提问用户 id',
  `audience`          VARCHAR(16)     DEFAULT NULL         COMMENT '调用端：user/doctor/admin',
  `question`          TEXT            DEFAULT NULL         COMMENT '用户问题原文',
  `status`            VARCHAR(16)     NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING 进行中 / SUCCESS 成功 / FAILED 失败',
  `iterations`        INT             NOT NULL DEFAULT 0   COMMENT 'ReAct 实际迭代次数',
  `prompt_tokens`     INT             NOT NULL DEFAULT 0   COMMENT '提示 token 数（LLM 返回的 usage）',
  `completion_tokens` INT             NOT NULL DEFAULT 0   COMMENT '生成 token 数（LLM 返回的 usage）',
  `latency_ms`        INT             NOT NULL DEFAULT 0   COMMENT '端到端耗时毫秒',
  `answer_digest`     VARCHAR(500)    DEFAULT NULL         COMMENT '回答摘要（截断存，不存全文）',
  `error_msg`         VARCHAR(1000)   DEFAULT NULL         COMMENT '失败原因',
  `create_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  KEY `idx_ar_agent_time` (`agent_id`, `create_time`),
  KEY `idx_ar_session` (`session_id`, `create_time`),
  KEY `idx_ar_parent` (`parent_run_id`),
  KEY `idx_ar_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 运行记录（可观测性）';

-- ------------------------------------------------------------
-- 5. 运行步骤明细
--
--    result_digest 同样只存摘要：工具结果可能是整张表或长文档，
--    全存会让表体量失控；需要全量时按 run_id 回查各工具自己的日志。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_run_step` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id`        VARCHAR(64)     NOT NULL          COMMENT '所属运行（agent_run.run_id）',
  `step_index`    INT             NOT NULL          COMMENT '步骤序号，从 0 递增',
  `step_type`     VARCHAR(16)     NOT NULL          COMMENT '步骤类型：llm 模型调用 / tool 工具调用 / handoff 委派 / final 产出最终答案',
  `tool_name`     VARCHAR(64)     DEFAULT NULL      COMMENT '工具名（step_type=tool 时有值）',
  `target_agent`  VARCHAR(64)     DEFAULT NULL      COMMENT '目标 Agent key（step_type=handoff 时有值）',
  `args_json`     TEXT            DEFAULT NULL      COMMENT '参数（工具参数或委派问题）',
  `result_digest` VARCHAR(1000)   DEFAULT NULL      COMMENT '结果摘要（截断存）',
  `latency_ms`    INT             NOT NULL DEFAULT 0,
  `create_time`   DATETIME        DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ars_run` (`run_id`, `step_index`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 运行步骤明细';

-- ============================================================
-- 种子数据
--
-- 这 6 个 Agent 不是新造的，而是把原先硬编码在
--   core/agent/AgentCoordinator.buildDefaultRoles()
-- 里的 6 个角色**原样搬进 DB**：agent_key / name / routing_keywords /
-- description 逐字段对齐，保证迁移前后路由结果完全一致。
--
-- 三个刻意的留白，避免制造双真值源：
--   1. system_prompt 一律留 NULL —— 现有的提示词权威是
--      ReactPromptLoader（classpath ai-prompts/react/role-<key>.txt）
--      + BaseReActAgent.ROLE_INSTRUCTIONS，另有一份给 /ai/chat 用的
--      AiPromptConfig.PRESETS。这里再抄一份必然发散。
--      代码约定：NULL = 不覆盖，沿用既有链路；只有管理员显式填写才生效。
--   2. model / temperature / top_p / max_tokens 同样留 NULL —— 沿用全局默认。
--   3. 用 INSERT IGNORE 而非 ON DUPLICATE KEY UPDATE ——
--      重复执行本脚本绝不能把管理员在后台改过的配置冲掉。
--
-- 关于 audience：现有 6 个角色在改造前对所有端都可见，故统一 all，
-- 保持行为不变；分端差异化留给管理员后续按需收窄。
-- ============================================================

INSERT IGNORE INTO `agent_config`
  (`agent_key`, `name`, `description`, `audience`, `max_iterations`,
   `routing_keywords`, `enabled`, `is_default`, `sort_order`)
VALUES
  ('doctor',            '全科医生',
   '症状分析、分诊建议、用药指导', 'all', 5,
   '症状,疾病,疼痛,发烧,咳嗽,头痛,感冒,治疗,诊断,不适,难受', 1, 0, 10),

  ('nutritionist',      '营养师',
   '饮食规划、营养搭配、体重管理', 'all', 5,
   '饮食,营养,减肥,增重,卡路里,蛋白质,维生素,食谱,膳食,体重管理', 1, 0, 20),

  ('psychologist',      '心理咨询师',
   '情绪疏导、压力管理、心理支持', 'all', 5,
   '心理,情绪,焦虑,抑郁,压力,失眠,睡不着,心情,烦躁,恐惧', 1, 0, 30),

  ('analyst',           '报告分析师',
   '体检报告解读、异常指标分析', 'all', 5,
   '报告,体检,指标,化验,血糖,血压,血脂,肝功,肾功,异常', 1, 0, 40),

  ('consultant',        '健康助手',
   '综合健康咨询、健康生活方式建议', 'all', 5,
   '健康,养生,运动,锻炼,保健,预防,生活,习惯', 1, 0, 50),

  ('general_assistant', '全能助手',
   '综合健康咨询（默认角色）', 'all', 5,
   NULL, 1, 1, 999);

-- ------------------------------------------------------------
-- 工具白名单种子
--
-- 语义再强调一次：**有绑定行 = 白名单生效**。因此本段一旦执行，
-- 除 general_assistant 外的 5 个 Agent 工具集会从「全量 11 个」收窄为下表，
-- 这是**有意的一次权限收紧**，不是 bug：
--   · query_health_data 是直达业务数据层的高权工具，只留给 analyst
--     （意图路由里 HEALTH_RECORDS / DATA_QUERY 也正好指向它）
--   · execute_sql **刻意不绑定给这 5 个中的任何一个**（2026-10-13 改正：
--     原注释称它「也留给 analyst」，与下面的行对不上）。它跑的是本地 SQLite 的
--     chat_history，且由 SqlGuard 校验手机号归属，并非直达业务库；
--     不绑定的实际效果是：它只对「零绑定行 = 全量工具」的 general_assistant 可见，
--     而 general_assistant 仅在无人命中关键词时兜底 —— 于是不会被
--     「问了一句含『血压』的话」顺带带出来。若要放开给 analyst，请显式加一行，
--     那是一次权限放宽，不该由脚本的沉默来完成。
--   · general_assistant 不给任何绑定行，维持「全量」——
--     它是兜底 Agent，必须保留全部能力，否则升级即故障
--   · 只读的 search_knowledge / search_knowledge_base / query_knowledge_graph
--     / get_chat_history 作为公共底座，6 个 Agent 里给到 5 个
--
-- ⚠️ 本表是**逐个工具枚举**的白名单，因此 ToolRegistry 每新增一个工具，
--    都必须回到这里补行 —— 漏补不会报错，只会表现为「新工具对 5 个 Agent
--    永远不可用」，而模型永远不会抱怨它没被提供的工具。
--    search_knowledge_base（阶段 2 新增的管理员知识库检索）就踩过一次这个坑：
--    ToolCatalog / system prompt 都补了，唯独这里没补。
--
-- 管理员可在「智能体管理」里随时增删绑定；改坏了 reload 即生效。
-- ------------------------------------------------------------
INSERT IGNORE INTO `agent_tool_binding` (`agent_id`, `tool_name`, `sort_order`)
SELECT a.`id`, m.`tool_name`, m.`sort_order`
FROM `agent_config` a
JOIN (
  SELECT 'doctor' AS agent_key, 'search_drug' AS tool_name, 10 AS sort_order
  UNION ALL SELECT 'doctor', 'search_knowledge',        20
  UNION ALL SELECT 'doctor', 'search_knowledge_base',   25
  UNION ALL SELECT 'doctor', 'query_knowledge_graph',   30
  UNION ALL SELECT 'doctor', 'get_health_data',         40
  UNION ALL SELECT 'doctor', 'query_health_records',    50
  UNION ALL SELECT 'doctor', 'web_search',              60
  UNION ALL SELECT 'doctor', 'get_chat_history',        70

  UNION ALL SELECT 'nutritionist', 'search_knowledge',      10
  UNION ALL SELECT 'nutritionist', 'search_knowledge_base', 15
  UNION ALL SELECT 'nutritionist', 'query_knowledge_graph', 20
  UNION ALL SELECT 'nutritionist', 'get_health_data',       30
  UNION ALL SELECT 'nutritionist', 'web_search',            40
  UNION ALL SELECT 'nutritionist', 'get_chat_history',      50

  UNION ALL SELECT 'psychologist', 'search_knowledge',      10
  UNION ALL SELECT 'psychologist', 'search_knowledge_base', 15
  UNION ALL SELECT 'psychologist', 'query_knowledge_graph', 20
  UNION ALL SELECT 'psychologist', 'get_health_data',       30
  UNION ALL SELECT 'psychologist', 'web_search',            40
  UNION ALL SELECT 'psychologist', 'get_chat_history',      50

  UNION ALL SELECT 'analyst', 'get_health_data',         10
  UNION ALL SELECT 'analyst', 'query_health_records',    20
  UNION ALL SELECT 'analyst', 'query_health_data',       30
  UNION ALL SELECT 'analyst', 'query_knowledge_graph',   40
  UNION ALL SELECT 'analyst', 'search_knowledge',        50
  UNION ALL SELECT 'analyst', 'search_knowledge_base',   55
  UNION ALL SELECT 'analyst', 'get_chat_history',        60

  UNION ALL SELECT 'consultant', 'search_knowledge',      10
  UNION ALL SELECT 'consultant', 'search_knowledge_base', 15
  UNION ALL SELECT 'consultant', 'search_drug',           20
  UNION ALL SELECT 'consultant', 'query_knowledge_graph', 30
  UNION ALL SELECT 'consultant', 'get_health_data',       40
  UNION ALL SELECT 'consultant', 'web_search',            50
  UNION ALL SELECT 'consultant', 'get_chat_history',      60
) m ON m.`agent_key` = a.`agent_key`;

-- ------------------------------------------------------------
-- 委派关系种子：全能助手 → 5 个专科 Agent
--
-- 这是「总控委派」的默认拓扑：用户问一句模糊的话，general_assistant
-- 可以自己判断该由哪个专科 Agent 接手，而不是靠关键词硬打分。
-- 反向不开放（专科 Agent 不互相委派），避免一开始就出现环。
-- ------------------------------------------------------------
INSERT IGNORE INTO `agent_handoff` (`from_agent_id`, `to_agent_id`, `condition_desc`)
SELECT f.`id`, t.`id`, m.`condition_desc`
FROM `agent_config` f
JOIN (
  SELECT 'doctor'            AS to_key, '问题是症状、疾病、用药相关时委派' AS condition_desc
  UNION ALL SELECT 'nutritionist', '问题是饮食、营养、体重管理相关时委派'
  UNION ALL SELECT 'psychologist', '问题是情绪、压力、睡眠相关时委派'
  UNION ALL SELECT 'analyst',      '问题是体检报告、化验指标解读相关时委派'
  UNION ALL SELECT 'consultant',   '问题是养生、运动、生活习惯相关时委派'
) m ON 1 = 1
JOIN `agent_config` t ON t.`agent_key` = m.`to_key`
WHERE f.`agent_key` = 'general_assistant';

-- ============================================================
-- 验证：
--   SHOW TABLES LIKE 'agent%';
--   SELECT agent_key, name, audience, is_default, enabled FROM agent_config ORDER BY audience, sort_order;
--   SELECT a.agent_key, COUNT(b.id) AS tools FROM agent_config a
--     LEFT JOIN agent_tool_binding b ON b.agent_id = a.id AND b.enabled = 1
--     GROUP BY a.agent_key ORDER BY tools DESC;      -- general_assistant 应为 0（=全量）
--   SELECT f.agent_key AS from_agent, t.agent_key AS to_agent
--     FROM agent_handoff h JOIN agent_config f ON f.id = h.from_agent_id
--     JOIN agent_config t ON t.id = h.to_agent_id;
--
-- 白名单漂移自检：把工具名换成新加的那个，返回空集才算补齐了。
--   SELECT 'search_knowledge_base' AS tool, a.agent_key
--     FROM agent_config a
--     WHERE a.agent_key <> 'general_assistant'
--       AND NOT EXISTS (SELECT 1 FROM agent_tool_binding b
--                       WHERE b.agent_id = a.id AND b.tool_name = 'search_knowledge_base' AND b.enabled = 1);
-- ============================================================
