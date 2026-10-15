-- ============================================================
-- 11_knowledge_base.sql —— 知识库与召回链路（阶段 2）
--
-- 目标：把原先「知识库 = news 表 + 一个文件向量库」的隐性约定，
--       升级为「可配置数据源 + 多知识库 + 文档上传解析 + 可观测召回链路」。
--
--   ① 多类型知识库      上传 PDF / DOCX / XLSX / TXT → 解析 → 切块 → 落库
--   ② 本地 / 远端数据源  kb_data_source 声明知识块存本机 MySQL 还是远端 MySQL
--   ③ 召回链路优化      kb_chunk 上的 ngram 全文索引（中文可切的稀疏路）
--   ④ 召回率 / 准确率   retrieval_log 记每一次真实检索；rag_eval_case 是金标，
--                       rag_eval_run 落每次评测的 recall@k / precision@k / MRR ...
--
-- 新增 7 张表：
--   kb_data_source    知识库数据源（local / remote MySQL）
--   knowledge_base    知识库本体
--   kb_document       上传的原始文档及其解析状态
--   kb_chunk          切块（检索的最小单位，带 ngram 全文索引）
--   retrieval_log     每一次检索的轨迹（追加型事件表）
--   rag_eval_case     金标评测用例（问题 → 期望命中的文档/块）
--   rag_eval_run      每次评测的聚合指标（追加型事件表）
--
-- 为什么这些必须是表，而不是塞进 system_config 的 KV 行：
--   沿用 10_agent_architecture.sql 的理由，另加两条本组特有的：
--   · kb_chunk 要对 content 建 **全文索引** —— KV 表无法建 FULLTEXT，
--     稀疏路就只能退回 LIKE '%词%'，而中文长词（「高血压」）命中不了
--     「血压偏高」这种真实表述，召回率被 LIKE 卡死。
--   · retrieval_log / rag_eval_run 是追加型事件表，需要按时间聚合算
--     召回率趋势；KV 表无法做这种聚合。
--   对照先例：audit_log / agent_run 同为「追加型事件表 + bigint 主键」。
--
-- 与既有 RAG 资产的关系（重要，避免双真值源）：
--   · 现役的 HybridRetriever 走 `news` 表 + 文件向量库（collection
--     "health_knowledge"）。本脚本**不迁移、不删除** news 路 ——
--     新表只是多了一条「知识库路」，两条路在融合阶段并列参与，
--     存量问答行为不变。待新路验证充分后再议下线。
--   · 文件向量库（LocalVectorStoreImpl）保持不变；稠密路的每个知识库
--     对应一个 collection（命名 kb_<code>），与 news 的 collection 隔离。
--
-- 惯例对齐（照抄 10_agent_architecture.sql 与审计表）：
--   · 配置类表用 int unsigned 主键；追加型事件表（retrieval_log /
--     rag_eval_run）照 audit_log 的先例用 bigint unsigned
--   · 全库**无物理外键**，引用完整性由代码维护，故这里也不加 FOREIGN KEY
--   · utf8mb4 / utf8mb4_0900_ai_ci，每列带 COMMENT
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS；种子数据用 INSERT ... SELECT ... WHERE NOT EXISTS。
-- ⚠️ 本脚本对**已有数据**零破坏：只新增表，不 ALTER 任何存量表。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 知识库数据源
--
-- 让管理员在后台声明「知识块落在哪个 MySQL 上」：
--   local  —— 就是本应用的主库，直接复用主数据源，无需账号密码
--   remote —— 另一台 MySQL，按下方字段自建只读/读写连接池
--
-- password_cipher 存 AES-256-GCM 密文（cn.kmbeast.utils.AESUtils），
-- **绝不明文入库**。注意 AESUtils 在环境变量 AES_SECRET_KEY 缺省时
-- 会退回源码内置的默认密钥 —— 那只挡得住「拿到库文件的人」，挡不住
-- 「拿到代码的人」。生产必须显式配置 AES_SECRET_KEY。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_data_source` (
  `id`              INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `code`            VARCHAR(64)   NOT NULL                COMMENT '数据源唯一标识（英文小写下划线，程序内引用）',
  `name`            VARCHAR(64)   NOT NULL                COMMENT '展示名',
  `type`            VARCHAR(16)   NOT NULL DEFAULT 'local' COMMENT '类型：local 本应用主库 / remote 远端 MySQL',
  `jdbc_url`        VARCHAR(500)  DEFAULT NULL            COMMENT 'JDBC URL（type=remote 时必填；local 时留空表示复用主数据源）',
  `username`        VARCHAR(64)   DEFAULT NULL            COMMENT '连接账号（type=remote 时必填）',
  `password_cipher` VARCHAR(512)  DEFAULT NULL            COMMENT '连接密码的 AES-256-GCM 密文，绝不明文',
  `enabled`         TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '是否启用（0停用 1启用）',
  `remark`          VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
  `last_check_time` DATETIME      DEFAULT NULL            COMMENT '最近一次连通性探测时间',
  `last_check_ok`   TINYINT(1)    DEFAULT NULL            COMMENT '最近一次连通性探测结果（NULL 表示从未探测）',
  `last_check_msg`  VARCHAR(500)  DEFAULT NULL            COMMENT '最近一次探测的失败原因',
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kds_code` (`code`),
  KEY `idx_kds_type` (`type`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '知识库数据源（本地主库 / 远端 MySQL）';

-- ------------------------------------------------------------
-- 2. 知识库
--
-- 分块参数与检索参数**落在知识库上**而不是全局配置：不同语料的最优值
-- 差别很大（法规条文要小块精确定位，科普长文要大块保上下文），
-- 全局一个值必然有一半场景是错的。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id`              INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `code`            VARCHAR(64)   NOT NULL                COMMENT '知识库唯一标识（英文小写下划线，同时用作向量库 collection 名后缀）',
  `name`            VARCHAR(64)   NOT NULL                COMMENT '展示名',
  `description`     VARCHAR(500)  DEFAULT NULL            COMMENT '用途说明',
  `data_source_id`  INT UNSIGNED  DEFAULT NULL            COMMENT '知识块存放的数据源（kb_data_source.id）；为空视为本应用主库',
  `audience`        VARCHAR(16)   NOT NULL DEFAULT 'all'  COMMENT '适用端：user 用户端 / doctor 医生端 / admin 管理端 / all 全部（与 agent_config.audience 同口径）',
  `chunk_size`      INT           NOT NULL DEFAULT 200    COMMENT '分块目标长度（字符）',
  `chunk_overlap`   INT           NOT NULL DEFAULT 50     COMMENT '相邻块重叠长度（字符）',
  `top_k`           INT           NOT NULL DEFAULT 5      COMMENT '本库默认召回条数',
  `weight`          DECIMAL(4,3)  NOT NULL DEFAULT 1.000  COMMENT '本库在多库融合时的权重 0.000~9.999',
  `embedding_model` VARCHAR(64)   DEFAULT NULL            COMMENT '本库使用的嵌入模型（为空则用全局 crm.embedding.model）',
  `enabled`         TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '是否启用（0停用 1启用）',
  `doc_count`       INT           NOT NULL DEFAULT 0      COMMENT '已入库文档数（冗余计数，随文档状态变更维护）',
  `chunk_count`     INT           NOT NULL DEFAULT 0      COMMENT '已入库块数（冗余计数）',
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_code` (`code`),
  KEY `idx_kb_audience` (`audience`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '知识库';

-- ------------------------------------------------------------
-- 3. 知识库文档
--
-- 一张表同时承载「文件上传」与「纯文本粘贴」两种来源，靠 source_type 区分。
-- status 是解析流水线的状态机：PENDING → PARSING → INDEXED / FAILED。
-- **失败必须留错误原文**（error_msg）：上传失败最常见的原因是加密 PDF、
-- 扫描件、损坏文件，没有错误原文时管理员只能反复重传同一个坏文件。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_document` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `kb_id`        INT UNSIGNED    NOT NULL                COMMENT '所属知识库（knowledge_base.id）',
  `title`        VARCHAR(255)    NOT NULL                COMMENT '文档标题（默认取文件名去扩展名）',
  `file_name`    VARCHAR(255)    DEFAULT NULL            COMMENT '原始文件名（纯文本来源时为空）',
  `file_type`    VARCHAR(16)     DEFAULT NULL            COMMENT '扩展名（小写，无点）：pdf/docx/xlsx/txt/md/csv/json',
  `file_size`    BIGINT          NOT NULL DEFAULT 0      COMMENT '原始字节数',
  `source_type`  VARCHAR(16)     NOT NULL DEFAULT 'file' COMMENT '来源：file 上传文件 / text 直接粘贴文本',
  `source_path`  VARCHAR(500)    DEFAULT NULL            COMMENT '落盘路径（source_type=file 时）。仅作留档：解析结果已在 kb_chunk，原文件丢失不影响检索',
  `content_hash` VARCHAR(64)     DEFAULT NULL            COMMENT '正文 SHA-256 前 64 位，同一知识库内复用可跳过重复解析',
  `status`       VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING 待处理 / PARSING 解析中 / INDEXED 已入库 / FAILED 失败',
  `chunk_count`  INT             NOT NULL DEFAULT 0      COMMENT '切出的块数',
  `char_count`   INT             NOT NULL DEFAULT 0      COMMENT '解析出的正文字符数',
  `error_msg`    VARCHAR(1000)   DEFAULT NULL            COMMENT '解析失败原因（原文截断）',
  `uploader_id`  INT UNSIGNED    DEFAULT NULL            COMMENT '上传者用户 id',
  `audience`     VARCHAR(16)     NOT NULL DEFAULT 'all'  COMMENT '适用端（与所属知识库一致，冗余存放便于按端过滤）',
  `deleted`      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除（0否 1是）；删文档要联动清 kb_chunk 与向量',
  `create_time`  DATETIME        DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kbd_kb_status` (`kb_id`, `status`, `deleted`),
  KEY `idx_kbd_hash` (`kb_id`, `content_hash`),
  KEY `idx_kbd_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '知识库文档（上传文件 / 粘贴文本）';

-- ------------------------------------------------------------
-- 4. 知识块（检索的最小单位）
--
-- ⚠️ 全文索引用 **ngram 解析器**，这是中文场景的关键：
--   MySQL 默认全文解析器按空格切词，中文整句会被当成一个词，
--   MATCH ... AGAINST 对中文几乎必然零命中。
--   ngram 解析器按 N 字符滑窗切（默认 N=2），「血压偏高」会被切成
--   血压/压偏/偏高 三个 token，于是搜「高血压」能命中「血压偏高」的文本。
--
-- 索引列选 content 而不是 title：正文才是召回的主体，标题通常很短、
-- 且已包含在正文首块中，单独建索引收益低而写入成本翻倍。
--
-- 检索侧对 MATCH 失败做了回落（见 KbChunkRetriever）：若本机 MySQL
-- 未启用 ngram（社区版裁剪或 token size 配置异常），会自动退回 LIKE，
-- 功能可用但召回质量下降 —— 届时 retrieval_log.path 会显示为 sparse_like。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_chunk` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `kb_id`        INT UNSIGNED    NOT NULL                COMMENT '所属知识库（knowledge_base.id）',
  `doc_id`       BIGINT UNSIGNED NOT NULL                COMMENT '所属文档（kb_document.id）',
  `chunk_index`  INT             NOT NULL                COMMENT '块在文档内的序号，从 0 递增（用于拼回上下文时还原顺序）',
  `title`        VARCHAR(255)    DEFAULT NULL            COMMENT '块所属文档标题（冗余，检索命中后无需回表）',
  `content`      TEXT            NOT NULL                COMMENT '块正文',
  `content_len`  INT             NOT NULL DEFAULT 0      COMMENT '正文长度（字符）',
  `enabled`      TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否参与检索（0停用 1启用）',
  `create_time`  DATETIME        DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kbc_doc` (`doc_id`, `chunk_index`),
  KEY `idx_kbc_kb` (`kb_id`, `enabled`),
  FULLTEXT KEY `ft_kbc_content` (`content`) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '知识块（ngram 全文索引，中文可切）';

-- ------------------------------------------------------------
-- 5. 检索日志（追加型事件表）
--
-- 「召回率/准确率」要能回答，前提是**每一次真实检索都被记下来**。
-- 只在离线评测里算指标是不够的：真实流量里的失败（向量服务超时导致的
-- 单路退化、某个知识库没有块、查询词全是停用词）在离线集里一条都测不出来。
--
-- top_json 存本次返回的 {id, score, source} 列表（截断到 2000 字符）。
-- 不存正文：检索日志的量级远大于文档量，存正文会让这张表迅速失控。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `retrieval_log` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `query_text`   VARCHAR(500)    NOT NULL                COMMENT '检索词原文（截断存）',
  `audience`     VARCHAR(16)     DEFAULT NULL            COMMENT '调用端：user/doctor/admin',
  `user_id`      INT UNSIGNED    DEFAULT NULL            COMMENT '提问用户 id',
  `kb_ids`       VARCHAR(255)    DEFAULT NULL            COMMENT '本次参与检索的知识库 id，英文逗号分隔；为空表示只走了存量 news 路',
  `top_k`        INT             NOT NULL DEFAULT 0      COMMENT '请求的召回条数',
  `sparse_hits`  INT             NOT NULL DEFAULT 0      COMMENT '稀疏路原始命中数（去重前）',
  `dense_hits`   INT             NOT NULL DEFAULT 0      COMMENT '稠密路原始命中数（去重前）',
  `fused_hits`   INT             NOT NULL DEFAULT 0      COMMENT '融合后返回条数',
  `both_hits`    INT             NOT NULL DEFAULT 0      COMMENT '两路同时命中的条数（越高说明两路一致性越好）',
  `path`         VARCHAR(32)     NOT NULL DEFAULT 'none' COMMENT '实际走的路径：hybrid 双路 / sparse_only 仅稀疏 / dense_only 仅稠密 / sparse_like 退化（稀疏走了 LIKE 兜底且稠密零召回）/ none 两路皆空',
  `latency_ms`   INT             NOT NULL DEFAULT 0      COMMENT '检索耗时毫秒',
  `top_json`     VARCHAR(2000)   DEFAULT NULL            COMMENT '返回结果的 {id,score,source} 列表（截断存）',
  `from_cache`   TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否命中检索缓存（是则各 hits 为缓存时的原值）',
  `create_time`  DATETIME        DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rl_time` (`create_time`),
  KEY `idx_rl_path_time` (`path`, `create_time`),
  KEY `idx_rl_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '检索轨迹日志（召回链路可观测）';

-- ------------------------------------------------------------
-- 6. 金标评测用例
--
-- expected_refs 是**期望命中**的 kb_chunk.id 或 kb_document.id，逗号分隔。
-- 这是「真正的召回率」唯一可能的算法基础：
--   召回率 = |检索结果 ∩ 金标| / |金标|
-- 没有金标就只能算「返回了几条」这类与质量无关的假指标 ——
-- 旧版 RAGEvaluationServiceImpl 用硬编码 5 题 + 启发式打分，
-- 算出的 precision 其实是「结论里有几个词出现在上下文中」，与召回无关。
--
-- match_level 决定 expected_refs 被解释成文档级还是块级：
--   文档级更宽松（同一篇的不同块都算命中），块级更严格（要定位到那一段）。
--   两者不能混算，故在用例上显式声明，指标按级别分组统计。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rag_eval_case` (
  `id`                INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `question`          VARCHAR(500) NOT NULL             COMMENT '评测问题',
  `expected_refs`     VARCHAR(500) DEFAULT NULL         COMMENT '期望命中的 id 列表，英文逗号分隔（含义由 match_level 决定）；可空：只有预期关键词的用例参与不了召回率，但仍能算精确率',
  `match_level`       VARCHAR(16)  NOT NULL DEFAULT 'chunk' COMMENT '金标粒度：chunk 块级 / doc 文档级',
  `kb_ids`            VARCHAR(255) DEFAULT NULL         COMMENT '本题限定的知识库 id，逗号分隔；为空则全库检索',
  `audience`          VARCHAR(16)  NOT NULL DEFAULT 'all' COMMENT '适用端',
  `expected_keywords` VARCHAR(500) DEFAULT NULL         COMMENT '参考答案里必须出现的词，英文逗号分隔（用于答案侧指标，可空）',
  `remark`            VARCHAR(255) DEFAULT NULL         COMMENT '备注（为什么这道题该命中这几段）',
  `enabled`           TINYINT(1)   NOT NULL DEFAULT 1   COMMENT '是否参与评测（0停用 1启用）',
  `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rec_enabled` (`enabled`, `audience`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'RAG 金标评测用例';

-- ------------------------------------------------------------
-- 7. 评测运行记录（追加型事件表）
--
-- 一次评测 = 一次评测（一个 run_id 覆盖 N 道题），指标是 N 道题的**宏平均**。
-- 保留历次运行是为了看趋势：改了分块大小/权重以后，召回率是涨了还是跌了。
-- 只留最新一次的话，「优化」就成了不可证伪的说法。
--
-- 各指标定义（均为 @k 截断后计算，k = top_k）：
--   recall    = 平均 |命中 ∩ 金标| / |金标|
--   precision = 平均 |命中 ∩ 金标| / |返回|
--   mrr       = 平均 1/第一个命中项的排名（未命中记 0）
--   hit_rate  = 至少命中一条金标的题目占比
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rag_eval_run` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id`         VARCHAR(64)     NOT NULL             COMMENT '本次评测的唯一 id',
  `kb_ids`         VARCHAR(255)    DEFAULT NULL         COMMENT '参与评测的知识库，逗号分隔；为空表示全库',
  `audience`       VARCHAR(16)     DEFAULT NULL         COMMENT '评测所用端',
  `top_k`          INT             NOT NULL DEFAULT 0   COMMENT '评测截断深度 k',
  `case_total`     INT             NOT NULL DEFAULT 0   COMMENT '本次实际评测的题目数',
  `case_hit`       INT             NOT NULL DEFAULT 0   COMMENT '至少命中一条金标的题目数',
  `recall`         DECIMAL(6,4)    NOT NULL DEFAULT 0   COMMENT '召回率（宏平均）',
  `precision_at_k` DECIMAL(6,4)    NOT NULL DEFAULT 0   COMMENT '准确率 @k（宏平均）',
  `mrr`            DECIMAL(6,4)    NOT NULL DEFAULT 0   COMMENT '平均倒数排名 MRR',
  `hit_rate`       DECIMAL(6,4)    NOT NULL DEFAULT 0   COMMENT '命中率 = case_hit / case_total',
  `avg_latency_ms` INT             NOT NULL DEFAULT 0   COMMENT '单题平均检索耗时',
  `sparse_only`    INT             NOT NULL DEFAULT 0   COMMENT '本题集中退化为仅稀疏路的题数（暴露向量服务可用性）',
  `detail_json`    MEDIUMTEXT      DEFAULT NULL         COMMENT '逐题明细 JSON（问题/金标/实际命中/各项指标）',
  `trigger_by`     VARCHAR(32)     NOT NULL DEFAULT 'manual' COMMENT '触发方式：manual 管理端手动 / scheduled 定时',
  `create_time`    DATETIME        DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rer_run_id` (`run_id`),
  KEY `idx_rer_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'RAG 评测运行记录（召回率 / 准确率趋势）';

-- ------------------------------------------------------------
-- 种子数据
--
-- 只种一条 local 数据源：它的语义是「复用主数据源」，不含任何凭据，
-- 因此种下不产生安全或环境耦合问题。远端数据源必须由管理员显式添加 ——
-- 那种数据带账号密码，不可能也不该有默认值。
-- ------------------------------------------------------------
INSERT INTO `kb_data_source` (`code`, `name`, `type`, `enabled`, `remark`)
SELECT 'local_main', '本应用主库', 'local', 1, '默认数据源：知识块与应用数据同库，复用主数据源'
WHERE NOT EXISTS (SELECT 1 FROM `kb_data_source` WHERE `code` = 'local_main');

-- 提示：没有种默认知识库。空知识库会让「检索不到」看起来像功能坏了，
-- 而实际上只是还没上传任何文档 —— 管理端页面会明确提示这一点。
