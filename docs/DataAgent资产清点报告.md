# DataAgent 资产清点报告（全量）

> 清点日期：2026-10-07　　源仓库：`D:\Program\DataAgent`（spring-ai-alibaba/DataAgent，Apache 2.0）
> 目的：在阶段 4 已摘取 7 个 prompt + 5 个 Java 参考源之外，**穷尽清点**剩余资产，判断哪些可摘、哪些必须丢弃。
> 结论口径：**零新 Maven 依赖**（不引 Spring AI / WebFlux / Druid / OpenTelemetry）。

---

## 一、总规模

| 项 | 数值 |
|---|---|
| 主体模块 | `data-agent-management`（303 个 Java 文件 / **31,997 行**，已排除 test） |
| 一级包 | 22 个 |
| 其他 | `data-agent-frontend-nuxt`（Nuxt 前端）、`CI`、`docker-file`、`docs`、`img` |

### 一级包规模（按行数降序）

| 包 | 文件 | 行数 | 本项目处置 |
|---|---|---|---|
| `service/` | 84 | 10,715 | 部分摘取（nl2sql/schema） |
| `connector/` | 34 | 3,999 | 部分摘取（仅 mysql 的 information_schema 查询） |
| `workflow/` | 27 | 3,361 | **全丢弃**（与本项目 ReAct 层重叠且更弱） |
| `controller/` | 15 | 2,323 | 丢弃（WebFlux/SSE，架构冲突） |
| `util/` | 17 | 1,724 | ✅ **摘取 2 个零依赖工具类**（见 §三） |
| `dto/` | 33 | 1,652 | 丢弃（BO/VO 随 Spring AI 结构） |
| `mapper/` | 13 | 1,473 | 丢弃（对应 DataAgent 16 张元数据表，本项目走强耦合复用既有表） |
| `config/` | 7 | 955 | 丢弃 |
| `entity/` | 12 | 891 | 丢弃（16 张元数据表实体，违反强耦合原则） |
| `splitter/` | 3 | 787 | 丢弃（依赖 Spring AI `TokenTextSplitter` 等） |
| `enums/` | 10 | 754 | 丢弃（方言枚举，本项目仅 MySQL） |
| `vo/` | 12 | 674 | 丢弃 |
| `properties/` | 4 | 526 | 部分摘取（已收敛为 `Nl2SqlProperties` 48 行） |
| `prompt/` | 3 | 512 | 部分摘取（已重写为 `Nl2SqlPromptBuilder`） |
| `bo/` | 9 | 382 | 丢弃（schema BO 层，已被 `TableSchema` 内部类替代） |
| `security/` | 4 | 262 | **全丢弃**（引 `spring-boot-starter-security` → 全站 401） |
| `converter/` | 3 | 241 | 丢弃（依赖 Spring AI `Document`） |
| `constant/` | 2 | 219 | 丢弃 |
| `event/` | 3 | 190 | 丢弃（Session 事件，WebFlux） |
| `annotation/` | 3 | 139 | 丢弃 |
| `strategy/` | 1 | 74 | 丢弃 |
| `exception/` | 2 | 58 | 丢弃 |
| `aop/` | 1 | 56 | 丢弃 |

---

## 二、service/ 二级包逐一裁决

| 子包 | 文件 | 行数 | 裁决 | 理由 |
|---|---|---|---|---|
| `datasource/` | 13 | 1,249 | 丢弃 | 多数据源/连接池管理，本项目单一 MySQL + 既有 `DataSource` |
| `vectorstore/` | 5 | 901 | 丢弃 | **Spring AI `VectorStore` 核心** |
| `schema/` | 3 | 871 | ✅ 已摘取 | → `Nl2SqlSchemaService`(77) + `JdbcSchemaIntrospector`(266)，丢弃 Spring AI 依赖 |
| `graph/` | 4 | 822 | 丢弃 | 是编排 StateGraph，**不是图数据库**，与本项目 Neo4j 图谱无关 |
| `langfuse/` | 3 | 815 | 丢弃 | 依赖 **OpenTelemetry**（`io.opentelemetry.*`），引入即违背零新依赖 |
| `code/` | 10 | 806 | 丢弃 | Python 代码生成 + **Docker 沙箱**执行，本项目不做 |
| `aimodelconfig/` | 6 | 775 | 丢弃 | 对应 DataAgent `model_config` 表，本项目复用既有 `ai_config` |
| `agent/` | 5 | 698 | 丢弃 | Agent 编排，与本项目 ReAct 层重叠 |
| `file/` | 6 | 611 | 丢弃 | 文件上传/OSS，本项目走本地 `pic/` |
| `knowledge/` | 5 | 603 | 丢弃 | 深度依赖 Spring AI `VectorStore`/`Document`/`TextSplitter` |
| `chat/` | 6 | 514 | 丢弃 | 会话管理，本项目复用 `ai_conversation`/`ai_chat_record` |
| `semantic/` | 3 | 422 | 丢弃 | 语义模型（对应 `semantic_model` 表），依赖 Excel 解析 + Spring AI；本项目复用 `health_model_config` |
| `hybrid/` | 5 | 410 | 丢弃 | 混合检索（向量+ES），依赖 Milvus/ES |
| `prompt/` | 2 | 337 | ✅ 已摘取 | → `Nl2SqlPromptBuilder`(240)，自研 `{}` 替换替代 `PromptTemplate` |
| `business/` | 2 | 323 | 丢弃 | 业务术语库，依赖 `Document`/`VectorStore` |
| `nl2sql/` | 2 | 247 | ✅ 已摘取 | → `MySqlText2SqlService`(300)，`Flux` 改同步 |
| `vector/` | 1 | 125 | 丢弃 | 向量检索，Spring AI |
| `llm/` | 2 | 124 | 丢弃 | `LlmService` 封装，本项目用既有 `LLMProviderFactory` |
| `mcp/` | 1 | 62 | 丢弃 | MCP 客户端，依赖 `reactor` |

---

## 三、本次新增摘取（此前方案未覆盖）

清点 `util/` 时发现 2 个**零依赖、Apache 2.0、逻辑优于自研实现**的工具类，已原样摘取（仅改包名）：

| 新文件 | 源 | 行数 | 价值 |
|---|---|---|---|
| `core/nl2sql/MarkdownParserUtil.java` | `util/MarkdownParserUtil.java` | 70 | ✅ **替换自研 `stripFence()`**。支持可变长度围栏（4+ 反引号）、语言标注、CRLF/CR/LF 三种换行；自研版本只处理固定 3 反引号，模型输出嵌套代码块时会解析错误 |
| `core/nl2sql/ColumnTypeUtil.java` | `util/ColumnTypeUtil.java` | 32 | 列类型归一（`int/varchar/...` → `number/text`），减少 LLM 误用类型 |

**一处上游修正**：`ColumnTypeUtil.wrapType` 对精确匹配用 `equalsIgnoreCase`，但前缀匹配用**区分大小写**的 `startsWith`，导致大写 `VARCHAR(255)`（MySQL 常见）漏判。摘取时统一为忽略大小写，并在代码注释标明与上游的差异。

---

## 四、评估后**明确不摘取**的高价值候选（附理由）

这几处看着有价值，但引入即违背「零新依赖」或与本项目既有能力冲突：

| 候选 | 行数 | 不摘原因 |
|---|---|---|
| `util/SqlUtil.java` | 124 | 🔴 依赖 **Druid**（`com.alibaba.druid.sql.*`）做真正的 SQL AST 解析。**它是 `TenantSqlRewriter` 升级为 AST 方案的最佳解药**，但项目未引入 Druid，引入它需单独开阶段评估 |
| `util/FluxUtil.java` | 164 | 21 处 reactive，本项目是 Servlet MVC |
| `util/ReportTemplateUtil.java` | 279 | 报告模板渲染（Marked/ECharts CDN），本项目不做报告生成 |
| `util/StateUtil/PlanProcessUtil` | 271 | planner 状态机，与本项目 ReAct 无关 |
| `splitter/*`（3 类） | 787 | 依赖 Spring AI `TokenTextSplitter`/`SemanticTextSplitter` |
| `connector/SqlExecutor` + `ResultSetBuilder` | 237 | 依赖 `ResultSetBO` + commons-lang3，耦合深；本项目 `NamedParameterJdbcTemplate.queryForList` 更简洁且是 Spring 原生 |
| `service/langfuse/` | 815 | 依赖 OpenTelemetry |
| `bo/schema/*`（8 类） | 339 | schema BO 层，已被 `JdbcSchemaIntrospector` 的内部类精简替代 |

---

## 五、结论

- **可摘资产已基本摘尽**：零依赖、无 Spring AI、无 WebFlux 的高价值部分已全部收入本项目。
- **剩余资产全部因架构冲突或新依赖而不可摘**，且这不是遗漏：DataAgent 的核心价值（prompt 知识 + schema 查询）已完整迁移，其框架部分（workflow/graph/vectorstore/reactive/security）与本项目 Servlet MVC + ReAct + Neo4j 架构**本质冲突**。
- **唯一值得后续单独评估的**：`SqlUtil`（Druid AST）—— 若阶段 5 要放开 NL2SQL 的 JOIN/子查询，`TenantSqlRewriter` 需从正则升级为 AST，届时可评估引入 Druid（需单开阶段，不与资产摘取混做）。

### 摘取资产累计（本项目）

| 类别 | 数量 | 规模 |
|---|---|---|
| prompt 模板（`resources/ai-prompts/`） | 7 个 | 409 行 |
| Java 资产（`core/nl2sql/`） | 9 个 | 1,285 行 |
| 测试 | 3 个类 | 22 例 |