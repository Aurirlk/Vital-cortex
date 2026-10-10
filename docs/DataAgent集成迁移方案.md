# DataAgent 集成迁移方案

> 制定：2026-10-06　　依据：`项目现状基线.md` §九
> 目标项目：`后端/personal-health-api`（Spring Boot **3.5.16** / Servlet MVC / JDK 17）
> 外部项目：`D:\Program\DataAgent`（阿里 spring-ai-alibaba/DataAgent，Apache 2.0，PR#629）

---

## 🔝 零、前置约束（用户明确，优先级高于一切技术选型）

### 0.1 终极目标（用户原话）
> 「项目这一阶段的最终目标是把 DataAgent 代码仓库里的数据智能体功能迁移实现到本项目。」

本文全部工作服务于这个目标。凡是偏离该目标的重构一律不做。

### 0.2 🚨 强耦合原则（用户原话）
> 「只要能在原有数据表里增加列数和标记，就不允许增加新的数据表。」

**三条取舍规则**：

| 优先级 | 做法 | 例子 |
|---|---|---|
| 1 | **在既有表加列/枚举/标记位** | 给 `patient_profile` 加 `graph_synced` 标记（阶段 0-B 溯源用） |
| 2 | **复用语义相近的既有表** | NL2SQL 的会话历史 → 复用既有 **`ai_conversation`**（25 行）而非新建 `chat_session` |
| 3 | ❌ **禁止为方便而建表** | DataAgent 的 16 张元数据表**一律不许进本项目** |

**这条原则直接决定了方案的形态**：

| DataAgent 原设计 | 违反强耦合原则？ | 本项目做法 |
|---|---|---|
| 独立元数据库 `saa_data_agent`（16 张表） | 🔴 **严重违反** | **完全不要**。配置改为落既有 `ai_config`（28 行，本身已是 key-value 配置表）；会话复用 `ai_conversation`；问答案例落既有表 + 加标记列 |
| `logical_relation` 逻辑外键表 | 🔴 违反 | 改为**代码内常量 + 配置文件**，不入库 |
| `semantic_model` 语义模型表 | 🔴 违反 | 落 `ai_config` 的 key-value，或 `system_config`（24 行） |
| `model_config` 模型配置表 | 🔴 违反 | 已有 **`ai_config`** + `AiConfigHolder`，直接复用 |
| 17 个 prompt 模板（`.txt` 文本） | ✅ 不涉及表 | **原样摘取**，落 `resources/promets/`（或复用既有 prompt 管理机制） |
| 元数据抽取（information_schema 查询） | ✅ 只读不改表 | **重写约 700 行**，零新表 |
| StateGraph 编排 | ✅ 不需要表 | 改为同步串行调用，复用既有 `SeaChatWorkflow` |

> 💡 反过来看，这条原则**大幅降低了迁移成本**：我们不需要搬运它的持久化层，
> 只需摘「prompt 文本 + 元数据读取 + 编排思路」三样，且全部能挂到既有表上。

### 0.3 已核实的既有表复用落点

| 需求 | 复用目标（不新建表） | 核实结论 |
|---|---|---|
| 医生端查询审计 | **`audit_log`**（14 字段） | ✅ 完全够用：`action`(操作类型) / `resource`(资源类型，注释已含 news/drug/ai_config) / `resource_id` / `description` / `ip_address` / `user_agent` / `request_method` / `request_url` / `status` / `error_msg` 齐全。当前 **0 行**是因为**写入路径未打通**，不是表设计问题 |
| LLM 模型配置 | **`ai_config`**（28 行） | ✅ 已是 key-value 配置表 + `AiConfigHolder` 动态读取机制，与 DataAgent 的 `model_config` 表语义等价 |
| 系统开关 | **`system_config`**（24 行） | ✅ 同上 |
| 对话会话/历史 | **`ai_conversation`**（25 行）+ `ai_chat_record`（98 行） | ✅ 替代 DataAgent 的 `chat_session` / `chat_message` |
| 指标字典 | **`health_model_config`**（12 行） | ✅ 替代 DataAgent 的 `semantic_model` |
| 医生-患者授权 | **`appointment`**（5 行）推导 | ⚠️ 数据量小，需评估；**优先推导，不新增关联表** |

---

## 一、结论先行

| 你的诉求 | DataAgent 能否提供 |
|---|---|
| 自然语言 → 查询指令转换（NL2SQL） | ✅ 能力成熟，**可摘取** |
| MySQL + Neo4j 知识图谱构建 | 🔴 **完全不支持**，须自研（见 `数据资产与AI能力整改方案.md`） |

**集成形态结论：不能「源码合并」，改为「资产摘取」。**

---

## 二、DataAgent 是什么

**Java 项目，不是 Python。**

| 项 | 值 |
|---|---|
| 坐标 | `D:\Program\DataAgent`，父 pom `com.alibaba.cloud.ai:spring-ai-alibaba-data-agent:1.0.0-SNAPSHOT` |
| 子模块 | `data-agent-management`（唯一） |
| 包名 | `com.alibaba.cloud.ai.dataagent.*` |
| 技术栈 | Spring Boot **3.4.8**、Java 17、Spring AI 1.1.2、spring-ai-alibaba 1.1.2.2、MyBatis 3.0.4 |
| 编排 | `spring-ai-alibaba-graph-core` 的 **StateGraph**（16 Node + 11 Dispatcher） |
| 端口 | 8065，WebFlux |
| 协议 | Apache 2.0，github.com/spring-ai-alibaba/DataAgent |
| 成熟度 | 提交编号至 #629，**177 个测试文件**，工作区干净，最近 2026-09-20 |
| 元数据库 | 需**独立库** `saa_data_agent`（16 张表；实测本机**不存在**，首次启动会自动创建） |

**🔴 关键事实：全仓 `neo4j` / `cypher` 零命中。** `service/graph/` 是 Agent 编排状态图，**不是图数据库**。

---

## 三、为什么不能源码合并

| 硬冲突 | 实测证据 | 可绕过？ |
|---|---|---|
| **WebFlux vs MVC 不可共存** | DataAgent **41 个文件含 `reactor.core`**；`util/FluxUtil.java` 21 处 reactive；`StreamLlmService`、`SessionEventPublisher`、`SessionEventController`(Flux<ServerSentEvent>)、`GraphController.streamSearch()`(Flux)、`security/AgentApiKeyReactiveAuthenticationManager` | **不能**。排除 webflux 依赖后这些文件**编译即失败** |
| **改 WebFlux 会摧毁 ThreadLocal** | 本项目 **11 文件依赖 ThreadLocal**：`LocalThreadHolder`、`JwtInterceptor`、`ProtectorAspect`、`ToolContext`、`AiSessionContext`、`BaseReActAgent`、`StreamingReActAgent`、`UserChatController`、`CrmChatController`、`SeaChatWorkflow`、`DoctorWorkServiceImpl`；另有 **26 处** `@Async`/线程池 | ❌ Reactor 线程切换会丢 ThreadLocal，**直接破坏「医生端身份从 LocalThreadHolder 取」铁律**。单行道，不做 |
| **Spring Security → 全站 401** | DataAgent 引 `spring-boot-starter-security` + `AgentApiKey*` 认证；本项目用 `@Protector` + `aop/ProtectorAspect` + `JwtInterceptor` | 需全局放行，等于引入空转框架 + 5 个死类 |
| **依赖树污染** | hive-jdbc 3.1.3、ojdbc8、mssql-jdbc、DmJdbcDriver18、**commons-collections 3.2.1**、opentelemetry×4、milvus/es starter、druid、protobuf 3.25.5 | 部分可 exclusions；3.2.1 与现有 commons-lang3 冲突 |
| **Spring AI 1.1.2.2 × Boot 3.5.16** | 1.1.x baseline 是 Boot 3.4.x | 🔴 **未验证**。失败模式极差：`@ConditionalOnClass` 错配不报错而是**静默不装配**，运行期才炸 |
| **元数据库注入** | 首次启动会建 16 张表 | 需独立 DataSource 隔离 |
| **pom 顺序 XSD** | 本项目 pom.xml:8-13 有血泪注释（377 文件全红） | 引入 30+ 依赖后 IDEA 导入失败概率上升 |

### 关于「架构是否太老」

**不是我们老。** 本项目 Boot 3.5.16 > DataAgent 3.4.8。DataAgent 用 WebFlux 只因它有 2 个 SSE 端点（`GraphController:49`、`SessionEventController:37`），**是为 SSE 而选，不是为先进**。而本项目的 SSE 早已在 Servlet MVC 下跑通（`/user/chat/stream`）。

**保持 Servlet MVC 是正确决策。** 若未来真要统一为 WebFlux，那是独立的架构升级项目，不应作为引入 DataAgent 的代价。

---

## 四、资产价值盘点 —— 只摘 1.2%

| 层 | 行数 | 价值 | 处置 |
|---|---|---|---|
| `resources/prompts/*.txt`（17 个中文模板） | **920** | ★★★★★ **最高**，纯文本零耦合 | **摘取 506 行** |
| `connector/impls/mysql/*` | 362 | ★★★★ 元数据抽取逻辑 | 重写 ~180 行 |
| `prompt/PromptHelper.java` | 337 | ★★★★ | 重写 ~120 行 |
| `service/schema/*` | 871 | ★★★ | 重写 ~220 行 |
| `service/nl2sql/*` | 247 | ★★ 是 Flux + 依赖 LlmService | 重写 ~150 行 |
| `workflow/**`（16 Node + 11 Dispatcher） | 3361 | ★ 与本项目 ReAct 层重叠且更弱 | **全丢弃** |
| `service/graph/**`（StateGraph） | 822 | ★ 是编排非图数据库 | **全丢弃** |
| 41 reactive 文件 + 16 元数据表 + 沙箱 + MCP + 7 方言 + OSS | ~10000+ | ☆ | **全丢弃** |

**核心洞察**：我们要的不是 DataAgent 的**框架**，而是它的**知识**（prompt 文本）与**元数据查询**。前者是 506 行 `.txt`，复制即用；后者重写为 150 行。**两者都不需要 Spring AI。**

---

## 五、迁移实施

### 5.1 A 组：复制资源（零编译耦合）

从 `D:\Program\DataAgent\data-agent-management\src\main\resources\prompts\` 复制 7 个文件到 `personal-health-api\src\main\resources\ai-prompts\`：

| 源文件 | 行数 | 用途 |
|---|---|---|
| `new-sql-generate.txt` | 79 | 核心 SQL 生成（**只读约束仅在此 prompt 内，本方案会加硬校验**） |
| `query-enhancement.txt` | 87 | 问题改写 |
| `semantic-consistency.txt` | 80 | 语义一致性校验 |
| `sql-error-fixer.txt` | 53 | SQL 错误修复 |
| `intent-recognition.txt` | 96 | 意图识别（对应本项目阶段 3 路由） |
| `evidence-query-rewrite.txt` | 66 | 证据查询改写 |
| `mix-selector.txt` | 45 | 相关表选择 |
| **合计** | **506** | |

> ⚠️ 需在 `NOTICE` 或 `README-ai-prompts.md` 标注来源与 Apache 2.0 协议（署名要求）。
> 全部提示词为**中文硬编码**，换英文模型效果会下降。

### 5.2 B 组：重写移植（5 个新文件）

| 新文件（`cn.kmbeast.core.nl2sql`） | 参考源 | 行数 | 重写要点 |
|---|---|---|---|
| `JdbcSchemaIntrospector.java` | `MysqlJdbcDdl.java`(249) + `showForeignKeys`(169) | ~180 | **丢弃** `Ddl`/`AbstractJdbcDdl`/`DdlFactory`/`AccessorFactory`/自建连接池；**改用本项目现有 `DataSource`**；保留 `information_schema` 查询、库名正则 `":\d+/([^/?&]+)"`、列注释采样 |
| `Nl2SqlSchemaService.java` | `SchemaServiceImpl.java`(521) + `TableMetadataService.java`(310) | ~220 | **丢弃** `AgentVectorStoreService`/`DynamicFilterService`/`Document`/`FilterExpressionBuilder`（**Spring AI 依赖主要来源**）；只保留 schema 组装为紧凑 prompt 文本 |
| `Nl2SqlPromptBuilder.java` | `PromptHelper.java`(337) | ~120 | 只取 `buildSqlGeneratePrompt`/`buildMixMacSqlDbPrompt`；**自实现 `{}` 占位符替换**替代 Spring AI 的 `PromptTemplate`（~25 行） |
| `MySqlText2SqlService.java` | `Nl2SqlServiceImpl.java`(206) | ~150 | **`Flux<ChatResponse>` → 同步返回 `String`**；LLM 走现有 `LLMProvider.chat()` + `DeepSeekProvider`；保留 `IS_ONLY_NL2SQL` 语义 |
| `Nl2SqlProperties.java` | `DataAgentProperties` | ~40 | 配置收敛 |

**总计：830 行新 Java + 506 行复制资源，5 个新文件，零新 Maven 依赖，`pom.xml` 不动。**

### 5.3 依赖处理：零新增

| DataAgent 依赖 | 处理 |
|---|---|
| `spring-ai-*` / `spring-ai-alibaba-*` | **不引入**。5 个新文件均不 import 任何 `org.springframework.ai.*` |
| `spring-boot-starter-webflux` / `-security` | **不引入** |
| `hive/ojdbc8/mssql/DmJdbc/protobuf/otel/milvus/es/oss` | **不引入** |
| `mysql-connector-j` | ✅ 已有 |
| `neo4j-java-driver` | ✅ 已有（若后续加 Cypher 生成用） |
| Spring AI 的 `PromptTemplate` | 用 ~25 行自研替换 |

**⚠️ 若后续想"顺手"引入 Spring AI，必须单独开阶段** —— 那是独立的兼容性赌博，不该与资产摘取混在一起。

---

## 六、安全设计（比 DataAgent 严格）

| 项 | DataAgent | 本方案 |
|---|---|---|
| 只读防护 | 🔴 **仅 prompt 软约束**（`new-sql-generate.txt:39-40` 禁 DML/DDL/多语句） | 参数化查询（无 SQL 产出）+ 硬校验 |
| 行数限制 | `setMaxRows(1000)` | `setMaxRows(200)` |
| 超时 | `setQueryTimeout(30)` | `10` |
| 租户隔离 | 🔴 **无** | `TenantSqlRewriter` 无条件覆写 + `ai_ro` 只读账号 |
| 表范围 | 库内全部 | 白名单 35 张 + 黑名单（`user`/`role`/`permission`/`system_config`/`audit_log`） |
| 元数据库 | 无防护 | 禁 `information_schema`/`mysql`/`performance_schema`/`sys` |

### 🔴 租户隔离核心：重写而非校验

**不要**去"检查 SQL 里有没有正确的 user_id" —— LLM 可以写 `WHERE user_id=3 OR 1=1`、`UNION SELECT`，检查永远追不上花样。

**正确做法是无条件覆写**：

```
LLM 生成 SQL
 → ① MySqlAiSqlGuard.validate(sql)          词法校验
 → ② TenantSqlRewriter.rewrite(sql, ...)    删掉所有 user_id/patient_id/doctor_id 谓词
                                    无条件追加 AND user_id = :__tenantUserId
 → ③ NamedParameterJdbcTemplate 绑定真值    真值从 LocalThreadHolder 取，从不来自 SQL 文本
 → ④ ai_ro 只读账号执行，setMaxRows(200) + setQueryTimeout(10)
```

**「删除而非校验」是核心**：LLM 无论写什么都物理移除，无法表达 `OR 1=1` 绕过。

### 两层设计

| 层 | 状态 | 说明 |
|---|---|---|
| **第 1 层：参数化查询**（`query_health_records`） | ✅ **默认开启** | 指标名走硬编码枚举 + 天数钳制，走 MyBatis 参数化。**LLM 不产出 SQL → 根上消除注入面**，覆盖 ~90% 真实提问。**不需要 SqlGuard** |
| **第 2 层：受控 NL2SQL** | ⚠️ **默认关闭** `crm.ai.nl2sql.enabled=false` | 跨表自由查询。需先过 20 条恶意 SQL 拦截测试 |

**新建 `MySqlAiSqlGuard`，绝不改 `SqlGuard`** —— 后者 10 个既有用例锁定的是 SQLite `chat_history` 语义（`WHERE phone_number='x'`），与 MySQL 隔离模型完全不同。

**保守档位**（第 2 层启用时的默认）：单表 SELECT、禁 JOIN/子查询/UNION、强制 WHERE + LIMIT。此档位下 `TenantSqlRewriter` 只需处理顶层 WHERE，无需 AST。

### `ai_ro` 只读账号（需人工在 MySQL 执行）

```sql
CREATE USER 'ai_ro'@'localhost' IDENTIFIED BY '<强密码>';
GRANT SELECT ON personal_health.user_health TO 'ai_ro'@'localhost';
GRANT SELECT ON personal_health.patient_profile TO 'ai_ro'@'localhost';
GRANT SELECT ON personal_health.health_model_config TO 'ai_ro'@'localhost';
GRANT SELECT ON personal_health.visit_record TO 'ai_ro'@'localhost';
-- 明确不授予 information_schema / mysql / sys
```
> ⚠️ 本机 `root/1234` 是全权限账号，**不能给 AI 用**。

---

## 七、验证

| # | 测试 | 期望 |
|---|---|---|
| 1 | `JdbcSchemaIntrospectorTest` 连**真实** `personal_health` | 读到 35 表、`user_health` 4 列、`patient_profile` 7 个 JSON 列识别为 JSON 类型 |
| 2 | `Nl2SqlPromptBuilderTest` | 输出无 Spring AI 类名残留、占位符全替换 |
| 3 | **越权测试 20 条**（仅第 2 层） | `OR 1=1` / `UNION SELECT` / `WHERE user_id=999` / `INTO OUTFILE` / `information_schema` 枚举 / `SLEEP` 盲注 / 多语句堆叠 —— **全部必须被拦截** |
| 4 | 端到端手工 | 输入「查一下我最近的血压记录」→ **人工审查生成 SQL 是否含 tenant 谓词** |
| 5 | 回归 | **229 用例全绿** + 新增用例 |

---

## 八、回滚

**无 pom.xml 改动 → 回滚绝对干净**：

```bash
rm -rf 后端/personal-health-api/src/main/java/cn/kmbeast/core/nl2sql/
rm -rf 后端/personal-health-api/src/main/resources/ai-prompts/
```

> 这是本方案相对「源码合并」路线的**最大优势**：不碰依赖、不碰配置、零残留风险。

---

## 九、Plan B：独立服务先验证（可选，0.5 天）

**触发条件**：希望在动目标项目一行代码前，就确认 DataAgent 的 NL2SQL 在医疗场景真好用。

**做法**（成本极低）：

1. DataAgent 原样启动（git 干净，2026-09-20）：`mvnw.cmd -pl data-agent-management -DskipTests spring-boot:run`
   - ⚠️ 必须 `JAVA_HOME=D:\Program Files\Java\Jdk-17`（系统 `java` 是 JDK8）
   - ⚠️ 用自带的 `mvnw.cmd`（3.9.12），本机 Maven 3.6.1 插件不够
2. **建独立元数据库** `saa_data_agent`（实测不存在）—— 🔴 **绝不让它连 `personal_health`**
3. 配置只读数据源指向 `personal_health`
4. Docker 未运行 → **只走 `nl2sqlOnly` 纯 SQL 链路**，避开所有 Python 沙箱节点（`DATAAGENT_SANDBOX_DOCKER_HOST` 也别配）
   - ⚠️ 沙箱镜像在**阿里云新加坡 registry**，国内可能拉不动
   - ⚠️ 建议部署到**纯英文路径**（作者原注释：Windows 下 `./uploads` 拼接会解析失败）
5. 用 20 条真实医疗提问评测 SQL 准确率

**决策点**：
- 准确率不满意 → **阶段 4-5 直接取消**，省下全部风险
- 满意 → 证明 prompt 资产有价值，阶段 4 照做
- **无论结果如何，`数据资产与AI能力整改方案.md` 的阶段 0-A/0-B 完全不受影响**

---

## 十、执行顺序（价值/风险排序）

```
阶段 0-A  让数据变可见（get_health_data / 重建向量库 / 清 33 条必崩 SQL / 加一致性测试）
          ← 最高价值，完全不引 DataAgent，pom.xml 不动
          ✅ 2026-10-06 已完成
    ↓
阶段 0-B  建图（词表 + Neo4j 写入能力 + 5 端点 + 规则抽取 + 本地兜底）
          ← 完全不引 DataAgent
          ✅ 2026-10-07 已完成（节点 248 / 边 485，测试 317）
    ↓
阶段 1    图谱 Tool（query_knowledge_graph）
阶段 2    MySQL 参数化查询（双轨，query_health_records）
          ✅ 2026-10-07 已完成（测试 331，指标白名单 + 双轨越权防护）
    ↓                              ← 到这里已交付全部核心用户价值
阶段 3    LLM 意图路由（软路由 + 四层 fail-safe）
          ✅ 2026-10-07 已完成（IntentRouter 五类意图 + 四层 fail-safe，
          测试 344；详见 §十一「阶段 3 实现说明」）
    ↓
【Plan B 可选：独立服务验证 DataAgent 准确率】
    ↓
阶段 4    DataAgent 资产摘取（零新依赖）
          ✅ 2026-10-07 已完成（7 个 prompt **实测 409 行** + 7 个 Java 文件，
          含 MySqlAiSqlGuard/TenantSqlRewriter；测试 355；详见 §十二「阶段 4 实现说明」）
    ↓
阶段 5    受控 NL2SQL 启用（默认关闭，管理员在线开关）
          ✅ 2026-10-07 已被「阶段 6」覆盖实现（运行时开关 + 管理端配置页）
    ↓
阶段 6    数据智能体产品化落地（2026-10-07 已完成）
          10 步流水线 + SSE 步骤事件；医生端 / 用户端 / 管理员端三处落地；
          前端工作流 · 思考 · 工具调用三视图；测试 397；详见 §十三
    ↓
阶段 7    二次复核加固 + 前端能力对齐（2026-10-07 已完成）
          修 3 处跨租户 fail-closed 缺口；补会话历史 / 报告图表导出 / 预设问题可配；
          修报告渲染存储型 XSS；测试 397 → 405；详见 §十四
```

**阶段 0-A / 0-B / 1 / 2 / 3 完全不碰 DataAgent**，即便后续全部回滚，核心价值完整保留。
**0-A/0-B/1/2/3/4/6/7 已全部完成**，阶段 5（受控 NL2SQL）以「运行时开关 + 管理端配置」形态交付，
**默认仍为关闭**：生产启用前必须配好只读账号并跑通恶意 SQL 拦截测试。

---

## 十一、阶段 3 实现说明（LLM 意图路由 + 四层 fail-safe，2026-10-07）

### 11.1 为什么是「软路由」而非「硬路由」
本项目的工具选择最终仍由 LLM 的 `tool_choice=auto` 决定（见 `BaseReActAgent`）。硬路由（强制改写
LLM 的工具调用）会破坏 ReAct 的多轮协作、且可能与模型判断冲突。阶段 3 只做**意图倾向推断**，把结论
以一段「本轮意图倾向」追加进 system prompt，**引导**而非**命令**模型选对工具。零 LLM 调用、零 DataAgent 依赖。

### 11.2 新增 / 改动文件
| 文件 | 性质 | 说明 |
|---|---|---|
| `crm/agent/IntentRouter.java` | 新建 | 五类意图规则分类（复用 `HealthMetric` + `MedicalVocabulary`），携带离线兜底参数 |
| `core/agent/ChatRouting.java` | 改 | 新增 `intentHint` 字段，承载意图结果 |
| `crm/agent/BaseReActAgent.java` | 改 | 新增 `intentHint` ThreadLocal + `enhanceSystemPrompt` 追加意图段落 + 四层 fail-safe 帮助方法（`shouldRetryForRouting` / `buildRoutingReinforceNote` / `tryOfflineFallback` / `extractLatestUserQuery`） |
| `crm/agent/ReActAgent.java` | 改 | doRun 落地 L2 重路由 + L4 离线兜底 |
| `crm/agent/StreamingReActAgent.java` | 改 | doRunStreaming 落地 L2 + L4（流式事件） |
| `crm/workflow/SeaChatWorkflow.java` | 改 | `route()` 计算并写入 IntentHint；`runWithContext` 4 参透传 |
| `controller/UserChatController.java` / `CrmChatController.java` | 改 | 流式链路 5 参 `runWithContextStreaming` 透传意图 |

### 11.3 五类意图
| 意图 | 触发 | 离线落点 |
|---|---|---|
| `HEALTH_RECORDS` | 指标口语（血压/血糖/体重…） | `query_health_records`（高置信度） |
| `GRAPH` | 关系型 + 医学实体（高血压吃什么药） | `query_knowledge_graph`（高置信度，需实体） |
| `DRUG` | 药品关键词 / 药品规范名 | 无（依赖 LLM + search_drug） |
| `KNOWLEDGE` | 科普关键词 | 无 |
| `CHAT` | 兜底 | 无 |

### 11.4 四层 fail-safe
1. **L1 正常**：LLM 选对工具（既有主链路）。
2. **L2 重路由**：首轮返回纯文本但意图为「高置信度数据意图」且仍有富余轮次 → 追加强化指令重跑一轮，避免模型凭记忆编造健康数据。
3. **L3 工具降级**：工具自身 `ok()` 静默降级（阶段 1/2 已做）。
4. **L4 离线兜底**：LLM 完全故障（`CrmException`）时，高置信度 + 已登录身份 → 直接调对应工具返回真实数据（`[离线模式]` 前缀），不依赖 LLM；无身份 / 无工具落点 → 安全退回「服务不可用」。

### 11.5 测试
- `IntentRouterTest`：五类意图识别 + 离线参数（`targetTool` / `suggestedArgs`）。
- `ReActFailSafeTest`：L2 判定（仅首轮+富余轮次+高置信度才重路由）、L4 兜底成功 / 无身份 / 非数据意图 / 工具缺失 四类安全降级。
- 回归：344 用例全绿（331 + 13）。

---

## 十二、阶段 4 实现说明（DataAgent 资产摘取，2026-10-07）

### 12.1 A 组：复制资源（5.1，零编译耦合）

从 `D:\Program\DataAgent\data-agent-management\src\main\resources\prompts\` 复制 7 个文件到
`personal-health-api\src\main\resources\ai-prompts\`，并新增 `README-ai-prompts.md` 标注来源与 Apache 2.0 署名。

| 源文件 | 方案原记 | **实测行数** | 用途 |
|---|---|---|---|
| `new-sql-generate.txt` | 79 | **58** | 核心 SQL 生成 |
| `query-enhancement.txt` | 87 | **65** | 问题改写 |
| `semantic-consistency.txt` | 80 | **66** | 语义一致性校验 |
| `sql-error-fixer.txt` | 53 | **53** | SQL 错误修复 |
| `intent-recognition.txt` | 96 | **77** | 意图识别 |
| `evidence-query-rewrite.txt` | 66 | **47** | 证据查询改写 |
| `mix-selector.txt` | 45 | **43** | 相关表选择 |
| **合计** | **506** | **409** | |

> ⚠️ **行数更正**：方案初版记录 506 行系早期 commit 计数，DataAgent 仓库后续修订过这些模板，
> 实际摘取为 **409 行**。以实测为准（可用 `wc -l` 复核）。模板为 `{param}` 占位符语法（部分示例含 `\{ \}` 转义）。

### 12.2 B 组：重写移植（5.2，新包 `cn.kmbeast.core.nl2sql`）

| 新文件 | 参考源 | 行数 | 重写要点 |
|---|---|---|---|
| `JdbcSchemaIntrospector.java` | `MysqlJdbcDdl.java`(249) | ~180 | **丢弃** `Ddl`/`AbstractJdbcDdl`/`DdlFactory`/`AccessorFactory`/自建连接池 → **复用项目既有 `DataSource`**；保留 information_schema 表/列/外键查询、库名正则 `":\d+/([^/?&]+)"`、列注释采样；DTO 精简为 `TableSchema`/`ColumnSchema`/`ForeignKey` 内部类 |
| `Nl2SqlSchemaService.java` | `SchemaServiceImpl.java`(521) + `TableMetadataService.java`(310) | ~220 | **丢弃** `AgentVectorStoreService`/`DynamicFilterService`/`Document`/`FilterExpressionBuilder`（Spring AI 依赖主要来源）；只保留 DB→`List<TableSchema>`，并施加敏感表黑名单（永不召回） |
| `Nl2SqlPromptBuilder.java` | `PromptHelper.java`(337) | ~120 | 只取 `buildNewSqlGeneratorPrompt`/`buildMixMacSqlDbPrompt`/`buildSqlErrorFixerPrompt` 等；**自研 `{key}` 占位符替换替代 Spring AI `PromptTemplate`**（先解 `\{ \}` 转义再替换，约 25 行），另承载 `# Table:` schema 文本拼装 |
| `MySqlText2SqlService.java` | `Nl2SqlServiceImpl.java`(206) | ~150 | **`Flux<ChatResponse>` → 同步 `String`**；LLM 走 `LLMProviderFactory.getProvider().chat(...)`（OpenAI 兼容 JSON 用 fastjson2 拼装，解析 `choices[0].message.content`）；保留 `IS_ONLY_NL2SQL` 语义：选表→生成→校验→重写→执行→故障修复重试 |
| `Nl2SqlProperties.java` | `DataAgentProperties` | ~40 | `@Value` 收敛，总开关 `crm.ai.nl2sql.enabled` **默认 false**；`maxRows=200`/`queryTimeout=10`（严于 DataAgent 的 1000/30） |

### 12.3 §六 安全两件套（另新增 2 个类）

| 类 | 职责 |
|---|---|
| `MySqlAiSqlGuard.java` | 词法校验：禁 DML/DDL/多语句/注释绕过/系统库（`information_schema`/`mysql`/`performance_schema`/`sys`）/黑名单表（`user`/`role`/`permission`/`system_config`/`audit_log`）。**绝不改** 既有 `crm.sqlite.SqlGuard`（10 个既有用例锁定 SQLite `chat_history` 语义） |
| `TenantSqlRewriter.java` | **删除而非校验**：中和 LLM 写的 `user_id`/`patient_id`/`doctor_id` 谓词 → 把整个 WHERE 括起来 → 无条件 `AND 租户列 = :__tenantUserId`（`NamedParameterJdbcTemplate` 绑定真值，真值取自 `LocalThreadHolder`，永不来自 SQL 文本）。**括号是关键**：避免 `WHERE X OR Y AND 租户条件` 被 OR 优先级短路 |

### 12.4 零新依赖验证
5 个 B 组文件 + 2 个安全类**均不 import 任何 `org.springframework.ai.*`**；未引入 webflux/security；
`pom.xml` 未改动；新增依赖仅复用既有：fastjson2、`spring-jdbc`（`NamedParameterJdbcTemplate`）、`javax.sql.DataSource`、Lombok。

### 12.5 测试与遗留
- `Nl2SqlSecurityTest`（新增 11 例）：**20 条恶意 SQL 拦截**（DML/DDL/多语句/注释/系统库/黑名单表/UNION/写文件/危险函数）、
  合法 SELECT 与 `WITH...SELECT` 放行、`user_id` 列前缀不误伤、OR 绕过被括号中和、无 WHERE 时补租户条件、
  无租户列时不过滤、主表提取（含反引号）。
- 回归：**344 → 355 全绿**（+11）。
- ⚠️ **遗留（需人工，不阻塞）**：
  1. **`ai_ro` 只读 MySQL 账号仍未建**（核心安全项）——生产必须把执行 DataSource 切到 `ai_ro`；
  2. `ai_config.api_key` 仍为空 → 即使开启开关，LLM 也不可用；
  3. Plan B（独立服务验证 DataAgent 准确率）未执行；
  4. 保守档位下 `TenantSqlRewriter` 为正则实现，若未来放开 JOIN/子查询需升级为 AST 方案
     （**最佳解药已定位**：DataAgent `util/SqlUtil.java`(124) 基于 Druid 做真正的 AST 解析，
     但项目未引入 Druid，需单开阶段评估）。

### 12.6 资产清点补充（2026-10-07 二次发掘）

在 12.1~12.5 之后又做了**穷尽清点**（源仓库 303 文件 / 31,997 行，一级包 22 个），
完整裁决见 `docs/DataAgent资产清点报告.md`。本轮**新增摘取 2 个零依赖工具类**：

| 新文件 | 源 | 行数 | 价值 |
|---|---|---|---|
| `core/nl2sql/MarkdownParserUtil.java` | `util/MarkdownParserUtil.java` | 70 | **替换自研 `stripFence()`**：支持可变长度围栏（4+ 反引号）与 CRLF/CR/LF；自研版本只处理固定 3 反引号 |
| `core/nl2sql/ColumnTypeUtil.java` | `util/ColumnTypeUtil.java` | 32 | 列类型归一（`int/varchar` → `number/text`） |

**一处上游修正**：`ColumnTypeUtil.wrapType` 精确匹配用 `equalsIgnoreCase` 但前缀匹配用区分大小写的
`startsWith`，导致大写 `VARCHAR(255)` 漏判；摘取时统一为忽略大小写。

**结论：可摘资产已基本摘尽。** 剩余部分（workflow/graph/vectorstore/reactive/security/langfuse）
全部因架构冲突或需引入新依赖（Druid/OpenTelemetry/Docker 沙箱）而不可摘，这不是遗漏。

---

## 十三、阶段 6 实现说明（数据智能体产品化落地，2026-10-07）

### 13.1 为什么需要这一阶段

阶段 4 交付的是「一堆能力类」（`Nl2SqlSchemaService` / `MySqlText2SqlService` / 守卫 / 重写器），
但它们是**同步黑盒**：一次调用进去、一个结果出来，中间过程不可见，且没有任何界面入口。
用户的目标是「像 DataAgent 一样完整展示工作流和思考过程以及调用工具过程」——
这要求把黑盒拆成**可逐步观测的流水线**。

### 13.2 流水线与步骤事件

`DataAgentPipeline` 顺序编排 10 步，每步通过 `StepSink` 回调推事件：

| # | 步骤 | 工具名 | 失败后果 |
|---|---|---|---|
| 1 | 意图识别 | `intent_recognizer` | 闲聊则短路返回，不进后续步骤 |
| 2 | 查询增强 | `query_enhancer` | 失败回落原问题 |
| 3 | 数据表召回 | `schema_recaller` | 无候选表 → 终止 |
| 4 | 表关系分析 | `table_relation_analyzer` | 无外键 → 按单表查询继续 |
| 5 | SQL 生成 | `sql_generator` | — |
| 6 | 语义一致性校验 | `semantic_checker` | 未通过 → 带意见重生成一轮 |
| 7 | 安全守卫 | `sql_guard` | **终止**（恶意 SQL） |
| 8 | 租户隔离重写 | `tenant_rewriter` | **终止**（有租户列却无主体） |
| 9 | 执行查询 | `sql_executor` | 失败 → `sql-error-fixer` 修复重试 ≤2 次后终止 |
| 10 | 生成报告 | `report_generator` | 失败不影响查询结果 |

> **与 DataAgent 的差异**：不引 Spring AI Alibaba Graph，不做 `planner` / Python 沙箱 /
> `HumanFeedback` 中断；改用「顺序编排 + 事件回调」。对本项目的查询场景，
> 多步 planner 的收益远小于其复杂度与不可控性。

### 13.3 SSE 事件协议

```
event: step
data: {"order":7,"step":"SECURITY_GUARD","title":"安全守卫","tool":"sql_guard",
       "status":"SUCCESS","summary":"词法校验通过（只读）","thinking":null,
       "payload":{"primaryTable":"user_health"},"elapsedMs":0,"ts":...}

event: result
data: {"success":true,"sql":"...","rows":[...],"rowCount":12,"tenantApplied":true, ...}
```

`DataAgentSse.send` 对多行文本**逐行加 `data:` 前缀**（SSE 规范要求），否则前端 `JSON.parse` 会失败。

### 13.4 三端落地

| 端 | 路由 | 身份与授权 |
|---|---|---|
| 医生端 | `/doctor/data-agent/{query,query/stream,history}` | `@Protector(role="医生")` + 必传 `patientId` + 接诊授权校验 |
| 用户端 | `/user/data-agent/{query,query/stream,history}` | `@Protector` + **强制查本人**（忽略请求体 `patientId`） |
| 管理端 | `/data-agent/admin/{config,keywords,tables,prompts}` | `@Protector(role="管理员")` |

**强耦合原则落地**：不新建任何表 ——
审计 → `audit_log`；会话/消息 → `ai_conversation` + `ai_chat_record`（`agent_type` 区分
`DATA_AGENT_USER` / `DATA_AGENT_DOCTOR` 两个主体空间）；运行时开关 → `system_config`
（`config_group='data_agent'`，10s TTL，管理员改完无需重启）。

### 13.5 前端三视图

`components/AgentWorkflow.vue`（工作流时间线：状态 / 工具名 / 耗时 / 展开看「思考」与「结构化载荷」）
与 `components/AgentResult.vue`（SQL / 动态列表格 / Markdown 报告）在医生端与用户端**共用**，
避免两套实现漂移；`utils/agentStream.js` 用 `fetch` + `ReadableStream` 手工解 SSE 帧
（原生 `EventSource` 不支持 POST 请求体）。

### 13.6 管理端菜单分组

管理端原为 15 项平铺菜单，改为 6 个可折叠分组
（用户与权限 / 内容运营 / 健康与医疗 / 业务运营 / AI 与数据 / 系统管理），
由路由 `meta.group` 驱动，`VerticalMenu.vue` 用 `el-sub-menu` 渲染，侧栏折叠时自动退化为图标。

### 13.7 测试

`DataAgentPipelineTest`（10）+ `DataAgentConfigHolderTest`（6）+ `Nl2SqlSecurityTest`（含后述加固用例），
回归 **379 → 390 → 397 → 405 全绿**。

其中最关键的三条：
- `tenantRewriteAlwaysApplied`：即使模型写的 SQL **完全没有 WHERE**，最终 SQL 也必须含
  `user_id = :__tenantUserId`；
- `failClosedWhenNoOwner`：检测到租户列却拿不到执行主体时，流程在 `TENANT_REWRITE` 步失败，
  **绝不执行**；
- `joinToTenantTableStillEnforced`（2026-10-07 加固）：**主表无租户列、JOIN 进来的表有**时，
  仍必须逐表限定（`h.user_id = :__tenantUserId`）—— 这条锁死了下文 §13.9 的跨租户泄漏缺陷。

### 13.8 仍未闭环（需人工）

1. `ai_ro` 只读账号未建 —— 生产启用前必须建，并把执行 DataSource 切过去；
2. `ai_config.api_key` 需重新填写（配置存储问题已修复，见 `MEMORY.md`）；
3. **子查询 / CTE、隐式逗号连接、白名单外表的 SQL 当前一律 fail-closed**（见 §13.9 / §14.1）。
   要支持子查询必须单开阶段引入 Druid（或 JSqlParser）做 **AST 级重写**；
   逗号连接与越权表**不建议**放开（词法级无法保证租户列被完整覆盖）。
   > 注：`JOIN` 已于 2026-10-07 由「正则单表兜底」升级为「**逐表租户限定**」，可安全使用。

### 13.9 复核发现的跨租户泄漏缺陷及修复（2026-10-07）

复核「是否真的支持 JOIN / 子查询」时发现**真实安全缺陷**（P1）。

**根因**：`DataAgentPipeline` 只调用 `MySqlAiSqlGuard.extractPrimaryTable()`（**第一个 FROM 的表**）
判定租户列。当主表无租户列、而 JOIN 进来的表有时，`tenantColumn == null` →
`TenantSqlRewriter.rewrite(sql, null)` 只做「中和」不追加 → **租户过滤被整体跳过**，返回全表数据。
CTE（`WITH ... SELECT`）因 CTE 别名不在 schema 里，同样命中该路径。

**附带的必现 bug**：中和正则不含表前缀，JOIN 场景模型必然写 `a.user_id = 9`，
被中和后残留 `a.1=1` → **语法错误**。

**修复（5 处，零新依赖）**

| # | 改动 | 说明 |
|---|---|---|
| 1 | `MySqlAiSqlGuard.extractTableRefs()` | 解析所有 `FROM / JOIN` 表引用 + 别名；SQL 关键字不得被当作别名 |
| 2 | `MySqlAiSqlGuard.hasSubqueryOrCte()` | 出现 `( select` 即判定；保守档位拒绝（正则无法定位其内部租户列，追加条件会落到顶层 WHERE） |
| 3 | `TenantSqlRewriter.rewriteAll()` | **逐表限定**；单表保持原输出形态（向后兼容），多表带表名/别名前缀（避免 JOIN 两侧同名列 ambiguous） |
| 4 | `TenantSqlRewriter.PREDICATE_PATTERN` | 增加 `(?:`?\w+`?\.)?` 前缀捕获，前缀整体吃掉 |
| 5 | `DataAgentPipeline` 第 8 步 | 子查询/CTE → fail-closed；`resolveTenantColumns()` 扫描 SQL 中**所有**表；**任一**表有租户列而 `dataOwnerId == null` → fail-closed |

**测试**：`Nl2SqlSecurityTest` +5、`DataAgentPipelineTest` +2。

**结论**：JOIN 现在**安全可用**；子查询 / CTE 明确降级为「拒绝 + 提示」，不再存在静默泄漏路径。

---

## 十四、阶段 7：二次复核加固与前端能力对齐（2026-10-07）

> 本节回应「复核代码 → 回查 DataAgent 仓库是否遗漏前端用户可见功能 → 复查两份报告未实现项 → 更新文档交付」。

### 14.1 二次复核发现的三处租户隔离缺口（已修）

§13.9 修掉了「只看第一个 FROM 的表」这条主路径。本轮把**同一类问题的剩余分支**一次清干净：

| # | 缺口 | 为什么危险 | 处置 |
|---|---|---|---|
| 1 | 租户列只对**本次召回的表**判定（`resolveTenantColumns(schemas, …)`） | SQL 写出了未被召回的表时，`tenantColumnOf` 查不到 → **静默跳过租户过滤** → 跨租户泄漏 | 改用**全量白名单 schema** 判定；白名单外表 **fail-closed** |
| 2 | 隐式逗号连接 `FROM a, b` | 表引用正则只在 `FROM/JOIN` 之后识别表名，逗号后的表**不被识别** → 该表的租户列不会被限定；「是否多表」判定也会错 | 新增 `hasImplicitCommaJoin()`（限 **FROM 子句内 + 括号深度 0**，不误伤 `IN (1,2)`）→ fail-closed 并提示改用显式 `JOIN … ON` |
| 3 | `FROM personal_health.user_health` 库名前缀 | 表名被误判为库名 → 被判「未授权表」而**误拦正常查询** | 表引用正则容忍可选 `schema.` 前缀，只取最后一段 |

**收敛方式**：把「校验 + 重写」抽成**唯一安全出口** `safeRewrite(…)`，
初次生成与「故障修复重试」两条路径**都**必须过它 —— 避免只堵一条路。
返回 `null` 即统一 fail-closed，并把原因透传到前端提示。

### 14.2 前端用户可见能力：与 DataAgent 的差距比对

按「先看 DataAgent 前端用户能做什么 → 本项目没有的 → 回查其后端实现 → 迁移」的顺序逐项核对：

| DataAgent 前端能力 | 上游实现 | 本项目本轮前 | 本轮处置 |
|---|---|---|---|
| 工作流时间线（节点/思考/工具） | `ChatWorkflowTimeline.vue` | ✅ 已有 `AgentWorkflow.vue` | 保持 |
| 结果集表格 | `ChatResultSet.vue` | ✅ 已有 `AgentResult.vue` | 保持 |
| Markdown 报告 | `ChatMarkdownReport.vue` | ✅ 已有（marked） | **补消毒 + 工具栏** |
| 报告内嵌 ECharts 图表 | `useEchartsRenderer.ts` + `markdown-plugin-echarts.ts` | ❌ 缺失 | ✅ **已迁移**（echarts 围栏块 → 图表） |
| 下载 MD / 下载 HTML / 全屏查看 | `ChatMarkdownReport.vue` | ❌ 缺失 | ✅ **已迁移**，且导出为**离线自包含**（图表转 PNG 内联，优于上游的 CDN 依赖） |
| 会话历史侧边栏（列表/重命名/删除/清空） | `ChatSidebar.vue` | ⚠️ 只有扁平 `history()` | ✅ **已迁移**：`AgentHistory.vue` + `AgentHistoryDialog.vue`，后端补 6 个端点 |
| 预设问题 | `ChatWelcome.vue` + `AgentPresetQuestionController` | ⚠️ 医生端硬编码 4 条 | ✅ **已迁移**：管理员可配（复用 `system_config`），医生/用户各一份 |
| 流式报告打字机 | `ChatStreamingReport.vue` + `useTypewriter` | ❌ 无 | ⏸ **不迁移**：本实现报告**一次性产出后整包推送**，没有增量流，打字机无意义 |
| 人工反馈 / 计划审核（HITL） | `HUMAN_FEEDBACK_REQUIRED` + `submitFeedback` | ❌ 无 | ⏸ **不迁移**：依赖 DataAgent 的图检查点（threadId 续跑），本项目是线性流水线，引入等于自建状态机 —— 收益低、风险高 |
| 多数据源 / 多模型切换 | `switchDatasource` / `switchModel` | N/A | ⏸ 场景不符：单库 `personal_health` + 单一 `ai_config` |

### 14.3 本轮新增/改动落点

**后端**

| 文件 | 改动 |
|---|---|
| `core/nl2sql/MySqlAiSqlGuard.java` | +`hasImplicitCommaJoin()`；`TableRef` 容忍库名前缀 |
| `core/nl2sql/DataAgentPipeline.java` | +`safeRewrite()`（唯一安全出口）与 `SafeRewrite` 内部类；第 8 步与重试路径统一走它 |
| `core/nl2sql/DataAgentConfigHolder.java` | +`preset-questions-user` / `preset-questions-doctor` 与 `presetQuestions(boolean)` |
| `core/nl2sql/DataAgentQaService.java` | +`sessions()` / `sessionMessages()` / `renameSession()` / `deleteSession()` / `clearSessions()` / `presetQuestions()` |
| `controller/DataAgentQaController.java`、`DoctorDataAgentController.java` | +`GET /sessions`、`GET /sessions/{id}/messages`、`PUT /sessions/{id}/rename`、`DELETE /sessions/{id}`、`DELETE /sessions`、`GET /presets` |

**前端**

| 文件 | 说明 |
|---|---|
| `utils/markdownReport.js`（新增） | marked + **DOMPurify 消毒** + ECharts 围栏插件 + 渲染/销毁/resize + **离线自包含 HTML 导出** |
| `components/AgentHistory.vue`（新增） | 会话列表：选择 / 重命名 / 删除 / 清空 / 刷新，医生端与用户端共用 |
| `components/AgentHistoryDialog.vue`（新增） | 抽屉式历史回看（左列表 + 右消息流），助手消息走 Markdown 消毒渲染 |
| `components/AgentResult.vue`（改） | 报告 `v-html` 前**必须经 `renderMarkdown()` 消毒**；工具栏（下载 MD / 下载 HTML / 全屏）+ 图表渲染与销毁 |
| `views/doctor/DoctorDataQa.vue`（改） | +「历史会话」入口；快捷提问改为**从接口读取**（管理员可配） |
| `views/user/Assistant.vue`（改） | 「我的数据」模式 +「历史记录」入口 + 预设问题标签 |
| `views/admin/DataAgentManage.vue`（改） | +「预设问题」页签（医生端 / 用户端各一个多行输入，每行一条） |
| `selfcheck.py`（新增） | 前端静态自检：提取 `<script>` 交 `node --check` + `<template>` 栈式配平 |

### 14.4 前端 XSS 修复（复核中发现）

`AgentResult.vue` 原实现为 `v-html="marked.parse(report)"` —— **未消毒**。
报告来自 LLM **且会落库** `ai_chat_record`，下次回看时再次渲染，
构成**存储型 XSS**（提示注入后即可在医生端/管理端执行脚本）。

处置：统一收敛到 `renderMarkdown()`（marked → DOMPurify），
`FORBID_TAGS` 屏蔽 `script / iframe / object / embed / form / input / link / style`，
仅额外放行 `data-echarts-config`，且其内容仍须通过 `JSON.parse` 校验才会渲染。

### 14.5 测试

回归 **397 → 405（+8）全绿**：

- `Nl2SqlSecurityTest` +2：库名前缀解析、`hasImplicitCommaJoin` 正/负样本（`IN (1,2)`、`SELECT a,b`、`GROUP BY a,b` 均不误伤）
- `DataAgentPipelineTest` +3：白名单外表 fail-closed、逗号连接 fail-closed、**显式 JOIN 仍放行**（防止加固误伤正常查询）
- `DataAgentConfigHolderTest` +3：预设问题回落默认 / 配置生效且过滤空串 / 坏 JSON 不抛异常

前端：`selfcheck.py` 全量校验 **74 个 .vue + 19 个 .js，0 失败**。

### 14.6 能力对照结论

| 维度 | DataAgent | 本项目（阶段 7 后） |
|---|---|---|
| 工作流可视化 | 节点流（LangGraph 状态图） | 顺序 10 步 + SSE 逐步事件（等价可观测性） |
| 报告图表 | ✅ ECharts（前端渲染） | ✅ ECharts（**消毒后**渲染） |
| 报告导出 | MD / HTML（依赖 CDN） | MD / HTML（**离线自包含**） |
| 会话管理 | ✅ CRUD + 置顶 | ✅ 列表/重命名/删除/清空（未做置顶：`last_message_time` 倒序已足够） |
| 预设问题 | ✅ 按 Agent 配置 | ✅ 按端（医生 / 用户）配置 |
| 租户隔离 | 🔴 无 | ✅ 逐表限定 + 三重 fail-closed |
| 只读防护 | 🔴 仅 prompt 软约束 | ✅ 词法硬校验 + 表白名单 + 只读账号（待建） |
| HITL 计划审核 | ✅ | ⏸ 不做（架构不符，非能力缺失） |