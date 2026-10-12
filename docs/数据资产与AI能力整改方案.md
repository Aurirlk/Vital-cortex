# 数据资产与 AI 能力整改方案

> 制定：2026-10-06　　依据：`项目现状基线.md`  
> 覆盖问题编号：1~16、19（见基线 §十二；该节 2026-10-12 合并后由 §十一 改号）

---

## 📌 执行进度

### ✅ 阶段 0-B 已完成（2026-10-07 凌晨）

| 项      | 内容                        | 状态   | 实测证据                                                                                                                                                      |
| ------ | ------------------------- | ---- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **B1** | 图谱 Schema + 三元组模型         | ✅ 完成 | `GraphSchema`（11 标签 / 15 关系 / 幂等约束 DDL，Neo4j **5.x** 语法）+ `Triple` + `GraphNode` + `GraphPayload`                                                         |
| **B2** | `Neo4jClient` 写入能力 + 本地兜底 | ✅ 完成 | 新增 `write`/`writeBatch`/`applySchema`/`isHealthy`/`stats`；修 `isConnected()` 假阳性；`LocalTripleStore`（内存邻接表 + `triples.json` 落盘）；`Neo4jGraphHealthIndicator` |
| **B3** | 5 个缺失端点                   | ✅ 完成 | `GraphImportController`：`/graph/import`、`/import/drugs`、`/import/articles`、`/import/all`、`/stats`，另加 `/graph/health`、`/graph/clear`；**全部限管理员**            |
| **B4** | 疾病词表                      | ✅ 完成 | `resources/ai-data/seed-triples.json`：**119 节点 / 171 边**（22 疾病 + 31 症状 + 58 药品 + 8 分类），覆盖 `GraphRAG.FALLBACK_TERMS` 全部 10 词                               |
| **B5** | 规则抽取器                     | ✅ 完成 | `GraphExtractor` 7 条链路，零 LLM                                                                                                                              |
| **B6** | 统计端点 + 回归                 | ✅ 完成 | `/graph/stats` 按方案返回 `status`/`backend`/`sources`/`coverage`/`warnings`；**测试 273 → 317（+44）全绿**                                                           |

**实测图谱规模（真实数据离线核算，`tools/graph_dryrun.py` 可复跑）：节点 248 / 边 485**

| 标签        | 数量 |   | 关系              | 数量  |
| --------- | -- | - | --------------- | --- |
| Drug      | 71 |   | TREATED_BY      | 114 |
| Product   | 62 |   | HAS_MEASUREMENT | 85  |
| Symptom   | 31 |   | MENTIONS        | 74  |
| Article   | 22 |   | BELONGS_TO      | 65  |
| Disease   | 23 |   | SOLD_AS         | 62  |
| Indicator | 12 |   | HAS_SYMPTOM     | 48  |
| Patient   | 10 |   | 其余 8 类          | 37  |
| 其余 4 类    | 17 |   |                 |     |

> 原方案估"约 250 节点 / 220 关系" —— **节点数吻合（248），关系数远超（485 vs 220）**，  
> 因为药品 `TREATED_BY`（114）与文章 `MENTIONS`（74）这两条链路在原估算里没算进去。

---

### ✅ 阶段 1 & 2 已完成（2026-10-07）

> 阶段 1/2 **完全不碰 DataAgent**，把"关系型提问"与"指标型提问"落到真实数据上，是用户侧核心价值。

| 项          | 内容                               | 状态   | 实测证据                                                                                                                                                                                          |
| ---------- | -------------------------------- | ---- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **阶段 1**   | 图谱检索工具 `query_knowledge_graph`   | ✅ 完成 | `crm/agent/tool/QueryGraphTool.java`：`limit` 硬钳 50、`depth` 钳 2、`2s` 协作式超时、**降级返回 ok 不报错**；Neo4j 优先 / `LocalTripleStore` 兜底；实体解析三级递进（精确→别名→包含）                                                 |
| **阶段 2-①** | 双轨参数化查询工具 `query_health_records` | ✅ 完成 | `crm/agent/tool/QueryHealthRecordsTool.java`：指标名走 **`HealthMetric` 硬编码白名单**（12 个全局指标 + 口语别名），天数/条数代码钳制，全部 MyBatis 参数化 —— **LLM 不产出任何 SQL 片段，从源头消除注入**                                         |
| **阶段 2-②** | 双轨访问控制                           | ✅ 完成 | 用户端强制查<b>本人</b>（忽略 LLM 传入的 `patientId`，防越权）；医生端（`roleId=3`）必须传 `patientId` 且先校验 `appointment ∪ visit_record` 授权，**未授权 fail-closed**（不返回数据 + 记 `status=0` 审计）                                  |
| **阶段 2-③** | 身份跨线程透传                          | ✅ 完成 | `ToolContext` 增加 `roleId`/`doctorId`；`BaseReActAgent.executeTool` 在提交线程池前捕获、池线程里恢复（否则医生端身份在工具内丢失）；3 个调用方（`UserChatController`/`CrmChatController`/`SeaChatWorkflow`）标注 `setIdentity(2, null)` |
| **阶段 2-④** | 审计落点                             | ✅ 完成 | 复用既有 `audit_log`（14 字段），医生查询成功 `status=1` / 拒绝 `status=0`，含操作人与患者 ID                                                                                                                          |

**安全要点**：对应迁移方案"第 1 层：参数化查询"。DataAgent 的 NL2SQL 让 LLM 直出 SQL，本项目**刻意不做** —— 指标白名单 + 参数化 + 硬钳制，注入面为 0。第 2 层受控 NL2SQL（默认关闭）仍按计划留到阶段 5。

**测试 317 → 331（+14）全绿**，其中越权防护用例覆盖：用户端 `patientId` 篡改被忽略、医生端无授权拒绝、未识别指标拒绝查询、`days`/`limit` 极大值钳制、正常范围双形态解析。

---

### ✅ 阶段 3 已完成（2026-10-07）

> 阶段 3 **完全不碰 DataAgent**，用规则软路由补齐"LLM 凭记忆编造健康数据"与"LLM 全挂无数据可返"两大缺口，是用户侧数据可信任的最后一道闸。

| 项       | 内容                   | 状态         | 实测证据                                                                                                                                                                                                            |
| ------- | -------------------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **3-①** | 规则软路由 `IntentRouter` | ✅ 完成       | `crm/agent/IntentRouter.java`：零 LLM、零 DataAgent 依赖、零新依赖；五类意图 `HEALTH_RECORDS`/`GRAPH`/`DRUG`/`KNOWLEDGE`/`CHAT` + 置信度 `HIGH`/`MEDIUM`/`LOW`；`IntentHint` 携带 `hintText` + 可选离线兜底载荷（`targetTool`+`suggestedArgs`） |
| **3-②** | 意图识别链路               | ✅ 完成       | ① `HealthMetric` 别名子串宽匹配 → `HEALTH_RECORDS`(带 `metric=血压`)；② `MedicalVocabulary.matchIn` 规范名+`matchAliases` 别名 → 实体；③ 关系词+实体 → `GRAPH`；④ `DRUG_KEYWORDS`/`isDrugName` → `DRUG`；⑤ 科普 → `KNOWLEDGE`；⑥ 兜底 → `CHAT` |
| **3-③** | 软路由接入链路              | ✅ 完成       | `SeaChatWorkflow.route()` 计算 `IntentHint` 透传；`BaseReActAgent` 用 ThreadLocal 承载（统一由 `clearConversationContext()` 的 finally 释放，跨 `enhanceSystemPrompt` 与 fail-safe 两处读取不会串号）；同步/流式两链路 `runWithContext*` 全接通       |
| **3-④** | 四层 fail-safe         | ✅ 完成       | **L1** 正常 LLM 选工具（`tool_choice=auto`）→ **L2** 重路由（首轮纯文本但高置信度数据意图 + 有富余轮次 → 追加强化指令重跑）→ **L3** 工具自身 `ok()` 降级（阶段 1/2）→ **L4** 离线兜底（`CrmException` 时高置信度 + 已登录身份直接调工具返真实数据，前缀 `[离线模式]`）                            |
| **3-⑤** | 软路由 vs 硬路由           | ✅ 完成（设计决策） | 只把意图倾向**追加进 system prompt「引导」而非「强制」** LLM 选工具，避免破坏 ReAct 多轮协作；`hintText` 缺失则静默跳过                                                                                                                                |

**复用既有资产（零新依赖）**：`HealthMetric` 硬编码白名单（12 全局指标 + 口语别名）、`MedicalVocabulary`（`matchIn`→`List<GraphNode>`、`matchAliases`→`List<String>`、`drugNames()`）；`MedicalVocabulary` 注入允许 null 安全降级（词表缺失不崩）。

**测试 331 → 344（+13）全绿**，新增 `IntentRouterTest`（桩词表覆盖五类意图 + 空输入降级）与 `ReActFailSafeTest`（L2 决策边界 + L4 四种场景 + intentHint 注入/清除 + 并发隔离）。

> ⚠️ 与原方案 §「阶段 3 计划」的偏差：原计划 `DataSourceRoute{GRAPH,MYSQL,HYBRID,CHITCHAT,UNKNOWN}` 四层 fail-safe（关键词前置/LLM 超时熔断器/JSON 解析/catch Throwable）**未采用**——改走"五类意图 + 软路由引导 + L2 重路由 + L4 离线兜底"方案，更贴合 ReAct 多轮协作且不引入独立熔断器。`ChatRouting` 走 `@AllArgsConstructor` 自动生成的 4 参构造（已删手写重复构造），无原方案预警的 Lombok 编译冲突。**详见下方 §阶段 3 订正说明**。

---

### ✅ 阶段 4 已完成（2026-10-07）

> 阶段 4 是**首次真正触碰 DataAgent**（此前 0-A/0-B/1/2/3 全部零依赖）。方式是「资产摘取」而非「源码合并」：只拿它的 prompt 知识与 schema 查询逻辑，**框架全丢弃**。

| 项       | 内容                 | 状态   | 实测证据                                                                                                                                                                                                                                                                                  |
| ------- | ------------------ | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **4-①** | 5.1 复制 7 个 prompt  | ✅ 完成 | `resources/ai-prompts/` 7 个 .txt + `README-ai-prompts.md`（Apache 2.0 署名）。⚠️ **实测 409 行，非方案旧记 506**（DataAgent 仓库后续改过模板）                                                                                                                                                                |
| **4-②** | 5.2 重写 5 个 Java 文件 | ✅ 完成 | 新包 `cn.kmbeast.core.nl2sql`：`JdbcSchemaIntrospector`(复用既有 `DataSource`，丢弃自建连接池) / `Nl2SqlSchemaService`(丢弃 Spring AI 依赖，敏感表黑名单永不召回) / `Nl2SqlPromptBuilder`(自研 `{key}` 占位符替代 `PromptTemplate`) / `MySqlText2SqlService`(`Flux`→同步 `String`，走 `LLMProvider.chat`) / `Nl2SqlProperties` |
| **4-③** | §六 安全两件套           | ✅ 完成 | `MySqlAiSqlGuard`（词法：禁 DML/DDL/多语句/注释/系统库/黑名单表，**绝不改** `crm.sqlite.SqlGuard`）+ `TenantSqlRewriter`（**删除而非校验**：中和租户谓词 → WHERE 整体加括号 → 无条件 `AND 租户列 = :__tenantUserId`，规避 `OR 1=1` 优先级绕过）                                                                                               |
| **4-④** | 零新依赖               | ✅ 完成 | 7 个新类均不 import `org.springframework.ai.*`；未引 webflux/security；**`pom.xml` 未改动**                                                                                                                                                                                                       |
| **4-⑤** | 配置默认关闭             | ✅ 完成 | `application.yml` 新增 `crm.ai.nl2sql.*`：`enabled=false`、`maxRows=200`、`queryTimeout=10`（严于 DataAgent 的 1000/30）                                                                                                                                                                        |

**测试 344 → 355（+11）全绿**，新增 `Nl2SqlSecurityTest`：**20 条恶意 SQL 拦截**（满足方案 §六「开启前必须过 20 条恶意 SQL 拦截测试」门禁）+ OR 绕过 + 无 WHERE 补租户条件 + 无租户列不过滤 + 主表提取（含反引号）。

### ⚠️ 阶段 4 遗留待办（需人工，不阻塞）

1. **`ai_ro` 只读 MySQL 账号仍未建** —— 核心安全项。生产必须把 NL2SQL 的执行 DataSource 切到 `ai_ro`（当前复用主 DataSource）
2. **`ai_config.api_key` 仍为空** → 即使把 `enabled` 打开，LLM 也不可用（0-A 遗留，一直未闭环）
3. **Plan B（独立服务验证 DataAgent 准确率）未执行** —— 属于可选步骤，跳过不影响已交付能力
4. `TenantSqlRewriter` 为正则实现。**JOIN 已支持逐表租户限定**；**子查询 / CTE 仍为 fail-closed**，  
   要支持需单开阶段引入 Druid / JSqlParser 做 AST 级重写

### ✅ 数据智能体落地：医生端 + 用户端 + 管理员端（2026-10-07）

> 把阶段 4 摘取的 NL2SQL 资产，从「一堆能力类」变成「可用的产品功能」：  
> 编排成 DataAgent 式流水线并逐步事件化，落到医生端与用户端两个界面，管理侧提供开关与配置。

| 项                   | 内容                                                                                                                                     | 状态 | 落点                                              |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | -- | ----------------------------------------------- |
| **流水线**             | 10 步：意图识别 → 查询增强 → 表召回 → 表关系 → SQL 生成 → 语义一致性 → 安全守卫 → 租户重写 → 执行 → 报告                                                                  | ✅  | `core/nl2sql/DataAgentPipeline.java`            |
| **步骤事件**            | `AgentStep`（枚举，含工具名）+ `AgentStepEvent`（状态/摘要/思考/结构化载荷/耗时）+ `StepSink` 回调                                                               | ✅  | `core/nl2sql/AgentStep*.java`、`StepSink.java`   |
| **SSE 协议**          | `event: step`（每步 RUNNING→SUCCESS/FAILED/SKIPPED）、`event: result`、`event: error`；多行文本逐行加 `data:` 前缀                                     | ✅  | `core/nl2sql/DataAgentSse.java`                 |
| **医生端**             | `POST /doctor/data-agent/query(/stream)`、`GET /doctor/data-agent/history`；**必须传 `patientId`** 且先校验接诊授权，未授权 fail-closed + 审计 `status=0` | ✅  | `controller/DoctorDataAgentController.java`     |
| **用户端**             | `POST /user/data-agent/query(/stream)`、`GET /user/data-agent/history`；**强制查本人**，忽略请求体 `patientId`                                      | ✅  | `controller/DataAgentQaController.java`         |
| **报告生成**            | 使用 DataAgent `report-generator-plain.txt`（64 行）生成 Markdown，支持 `echarts` 代码块；报告作为 assistant 消息落 `ai_chat_record`                        | ✅  | `Nl2SqlPromptBuilder.buildReportPrompt`         |
| **管理端**             | `GET/POST /data-agent/admin/config`（在线开关，10s TTL）、`/keywords`、`/tables`、`/prompts[/{name}]`                                            | ✅  | `controller/DataAgentAdminController.java`      |
| **运行时开关**           | 复用 `system_config`（`config_group='data_agent'`），默认回落 `crm.ai.nl2sql.*`（默认关闭）                                                           | ✅  | `core/nl2sql/DataAgentConfigHolder.java`        |
| **前端（医生）**          | `views/doctor/DoctorDataQa.vue`：患者选择 + 工作流时间线 + SQL + 结果表 + 报告                                                                         | ✅  | 路由 `/doctor/data-qa`                            |
| **前端（用户）**          | `Assistant.vue` 新增「我的数据」模式，复用 `components/AgentWorkflow.vue` / `AgentResult.vue`                                                       | ✅  | `/user/assistant`                               |
| **前端（管理）**          | `views/admin/DataAgentManage.vue`（开关 / 关键词 / 召回范围 / 提示词段落 4 个页签）                                                                       | ✅  | 路由 `/admin/dataAgentManage`                     |
| **管理端菜单分组**         | 15 项平铺菜单 → 6 个可折叠分组（用户与权限 / 内容运营 / 健康与医疗 / 业务运营 / AI 与数据 / 系统管理）                                                                       | ✅  | `components/VerticalMenu.vue` + 路由 `meta.group` |
| **会话历史管理**（阶段 7）    | `GET/PUT/DELETE /{user,doctor}/data-agent/sessions*` 6 个端点；前端 `AgentHistory.vue` + `AgentHistoryDialog.vue`（列表/重命名/删除/清空/回看）           | ✅  | 详见 `DataAgent集成迁移方案.md` §14.2                   |
| **报告图表与导出**（阶段 7）   | 报告内 `echarts` 围栏 → ECharts 渲染；下载 MD / 下载 HTML（**离线自包含**，图表转 PNG 内联）/ 全屏查看                                                              | ✅  | `utils/markdownReport.js`                       |
| **预设问题可配**（阶段 7）    | 复用 `system_config` 存 JSON；管理员页新增页签，医生端/用户端各一份；未配置回落内置默认                                                                                | ✅  | `DataAgentConfigHolder.presetQuestions`         |
| **报告 XSS 消毒**（阶段 7） | 报告经 marked 渲染后**必须过 DOMPurify**（报告来自 LLM 且落库，回看时再次渲染 → 存储型 XSS）                                                                        | ✅  | `utils/markdownReport.renderMarkdown`           |

**安全边界（不可绕过）**：执行主体由服务端依据身份 + 授权推导，请求体无法指定；SQL 必经词法守卫；  
租户条件由 `TenantSqlRewriter` **删除模型写的谓词后逐表无条件重写**（扫描 SQL 中**所有**表，不再只看主表）；  
**任一**所涉表带租户列而无执行主体 → **fail-closed**；子查询 / CTE、隐式逗号连接、  
引用白名单外表的 SQL 一律 **fail-closed**（词法级无法保证租户列被完整覆盖，放行即等于跨租户泄漏）。

**测试 379 → 390 → 397 → 405 全绿**：新增 `DataAgentPipelineTest`（门禁 / 闲聊短路 / 守卫拦截 / 租户强制重写 /  
无主体 fail-closed / **JOIN 逐表限定** / **子查询 fail-closed** / **白名单外表 fail-closed** / **逗号连接 fail-closed** / SSE 分帧 / 事件模型）、  
`DataAgentConfigHolderTest`（默认回落 + 预设问题三态）、`Nl2SqlSecurityTest`（+7：`extractTableRefs` / `hasSubqueryOrCte` / `rewriteAll` 三态 / 库名前缀 / 逗号连接正负样本）。

**⚠️ 复核中发现并修复的跨租户泄漏缺陷（P1，2026-10-07）**

原实现只按「**第一个 FROM 的表**」判定租户列 → 主表无租户列而 JOIN 进来的表有（CTE 同路径）时，  
`tenantColumn == null` → 只中和不追加 → **租户过滤被整体跳过**；且中和正则不含表前缀，  
JOIN 场景必现的 `a.user_id = 9` 会被中和成 `a.1=1`（语法错误）。  
已修复为「**逐表扫描 + 逐表限定**」（`extractTableRefs` / `rewriteAll` / `hasSubqueryOrCte`），  
详见 `DataAgent集成迁移方案.md` §13.9。

**⚠️ 二次复核又发现的三处同类缺口（P1，2026-10-07，已修）**

| # | 缺口                                    | 处置                                                                             |
| - | ------------------------------------- | ------------------------------------------------------------------------------ |
| 1 | 租户列只对**本次召回的表**判定 → SQL 引用未召回表时静默跳过过滤 | 改用**全量白名单 schema** 判定；白名单外表 fail-closed                                        |
| 2 | 隐式逗号连接 `FROM a, b` 的第二张表不被词法识别 → 漏租户  | 新增 `hasImplicitCommaJoin()`（FROM 子句内 + 括号深度 0）→ fail-closed，提示改用显式 `JOIN … ON` |
| 3 | `FROM db.table` 库名前缀使表名被误判 → 误拦正常查询   | 表引用正则容忍 `schema.` 前缀                                                           |

并把「校验 + 重写」收敛为**唯一安全出口** `safeRewrite(…)`，初次生成与故障修复重试**两条路径都必须过它**。  
详见 `DataAgent集成迁移方案.md` §14.1。

---

### ✅ 全仓文档复核新发现：全新部署路径不可复现（P1，2026-10-07，**已修复并验证**）


对全仓 81 份 Markdown + `Data/` 脚本做复核时，用**临时库实跑**验证了 `Data/README.md` 的「场景 A 全新部署」。  
**修复前**（临时库 `ph_verify_tmp`）：

```
01_建库建表/*.sql（当时含 8 个脚本，含未归档的 rbac_schema.sql） → 46 张表（README 原写「29 张」❌）
02_迁移脚本/01_schema_refactor.sql → ERROR 1146 (42S02): Table 'notification' doesn't exist
02_迁移脚本/06_merge_message_into_notification.sql → 同一错误（CLI 遇错即止，其后语句被静默跳过）
02_迁移脚本 复跑第二遍 → 3 个 ERROR 1146（post / post_like / message 已被本套脚本 DROP）
```

| #  | 缺陷                                                                                | 修复前证据                                                                                                                                            | 处置                                                                                                      |
| -- | --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------- |
| 1  | **`notification` 表无任何 `CREATE TABLE`**                                            | 迁移脚本 01、06 报 `ERROR 1146` **中断**                                                                                                                 | ✅ 在 `01_建库建表/init_database.sql` 补入 DDL（取自实库 `SHOW CREATE TABLE`，去掉 AUTO_INCREMENT 偏移）                   |
| 2  | **`rbac_schema.sql` 建 4 张已废弃表**                                                   | `role`/`permission`/`role_permission`/`user_role` 不在真实 35 表、Mapper 已删、无迁移 drop                                                                   | ✅ 移入 `90_归档/rbac_schema_deprecated_20261007.sql`（加废弃说明头）                                                |
| 3  | **`01_schema_refactor.sql` 步骤 6.5 三条 `SET @d = IF(...)` 漏 `PREPARE/EXECUTE`**     | `user.phone`/`phone_verified` 从未真正添加 → 紧随的 `UPDATE` 报 `ERROR 1054 Unknown column 'phone_verified'`（第二个中断点，第一轮修复后才暴露）                             | ✅ 每条补 `PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;`                                             |
| 4  | **`mall_product` 少 6 列**（`product_type` + 5 个医学字段）；`07` 硬编码 `USE personal_health` | 脚本 15 列 vs 实库 21 列；`07` 对其它库执行会**静默写进 personal_health**                                                                                          | ✅ `extra_modules_schema.sql` 补 `product_type`；`07` 去 `USE` + 改 `DATABASE()` + 补 `product_type` 幂等 ALTER |
| 5  | **「全部脚本幂等」不成立**：`01`/`04`/`06` 共 **11 条语句**引用会被本套脚本自己 `DROP` 的 legacy 表           | 复跑第二遍报 3 个 `ERROR 1146`；**mysql CLI 遇错即止 → 静默跳过该文件后续语句**                                                                                         | ✅ 每条加「存在性守卫」（`SET @_has=` + `IF(@_has>0,'<原语句>','DO 0')` + `PREPARE/EXECUTE`）                           |
| 6  | **中文种子数据依赖客户端编码**：3 个含中文 `INSERT` 的脚本缺 `SET NAMES utf8mb4;`                       | 不带 `--default-character-set=utf8mb4` 即报 `ERROR 1406 Data too long for column 'model_name'`                                                       | ✅ 为全部 **12 个**含中文但缺声明的脚本补 `SET NAMES utf8mb4;`；验证 `HEX(model_name)` 与期望 UTF-8 逐字节一致                     |
| 7  | `Data/README.md` 数字/清单错误                                                          | 「29 张」实际 **42**（01 阶段，不含 rbac）；「初始建库」误写 41；`06` 重复 3 行、`05` 顺序错位、**缺 `07`**；`01` 目录清单仍列 `rbac_schema.sql`；`03` 目录漏 `seed_model_announcement.sql` | ✅ 全部更正，置顶「已修复 + 证据」                                                                                     |
| 8  | `03_数据脚本/mock_business_data.sql` 向已删除的 `post` 表插入                                 | `ERROR 1146 Table 'post' doesn't exist`                                                                                                          | ✅ 改写入 `news`（`content_type='POST'`）                                                                     |
| 9  | `03_数据脚本/vip_migration.sql` 非幂等                                                   | `ERROR 1060 Duplicate column name 'is_vip'`                                                                                                      | ✅ 改幂等                                                                                                   |
| 10 | `03_数据脚本/hot_score_algorithm.sql` 两个存储过程指向已删 `post` 表                             | 过程可建但 **CALL 即 `ERROR 1146`**，属死代码                                                                                                               | ✅ 改指 `news`，并加 `DROP PROCEDURE IF EXISTS` 使过程体可复跑刷新                                                     |

**修复后验证（2026-10-07，临时库实跑，两种编码口径各测一遍）**：

```
A 口径 mysql <file>（不带编码参数）          第 1 遍 零 ERROR；复跑第 2 遍 零 ERROR
B 口径 mysql --default-character-set=utf8mb4 第 1 遍 零 ERROR
19 个**库内**脚本 = 01_建库建表 7 + 02_迁移脚本 7 + 03_数据脚本 5
（2026-10-07 晚新增 2 个：`02_迁移脚本/09_create_ai_ro_user.sql` 为需 root 的运维脚本、
`03_数据脚本/seed_data_agent_config.sql` 为开关种子；两者不计入「双跑零 ERROR」口径）
仅 01 时：42 张表；仅 01+02 时：表 35 = 35，字段 391 = 391，多/少表、多/少列 全为空
中文种子数据 HEX(model_name) = 期望 UTF-8 字节，逐字节一致
```

> 附带发现：跑 `03_数据脚本/migrate_encrypted_data.sql` 会按设计多出 `ai_config_backup` 表（一次性运维脚本，非缺陷，已在文件头标注）。  
> 结论：`Data/` 的**全新部署路径已可用**，无需再依赖「从备份恢复」兜底。

---

### 🔍 复查：两份报告「未实现项」现状核对（2026-10-07）

对《调研与代码审查报告-20261003》与《项目现状基线（2026-10-06）》逐条回查代码（不看文档自述）。

**A.《调研与代码审查报告-20261003》**

| #  | 报告项                                                                            | 等级 | 现状         | 证据                                                                                                       |
| -- | ------------------------------------------------------------------------------ | -- | ---------- | -------------------------------------------------------------------------------------------------------- |
| 1  | 全项目无 `@EnableScheduling`，热度分定时任务从未运行                                           | P0 | ✅ 已修       | 启动类 `@EnableScheduling` + `@EnableAsync`；`AiChatCacheServiceImpl` 增加定期兜底                                 |
| 2  | 4 处明文密钥入库（MySQL/JWT/Neo4j/硅基流动）                                                | P0 | ✅ 已修       | `application.yml` 全部改 `${ENV:}`（`DB_PASSWORD` / `JWT_SECRET` / `NEO4J_PASSWORD` / `SILICONFLOW_API_KEY`） |
| 3  | 两个无界 `ConcurrentHashMap` → OOM                                                 | P1 | ✅ 已修       | 新增 `evictOversizedCache()` 定期兜底                                                                          |
| 4  | 3 处 `supplyAsync` 未传 Executor（阻塞网络调用占满 commonPool）                             | P1 | ✅ 已修       | 新增 `VoiceSynthesisExecutor`（有界队列 + CallerRuns 降级）+ `VoiceExecutorLifecycle` 统一关停                         |
| 5  | `core/async/TaskQueue` 死代码 + 无界队列 + 无 `@PreDestroy`                            | P1 | ✅ 已删       | 全项目 0 引用                                                                                                 |
| 6  | `HotScoreServiceImpl` N+1 逐条 UPDATE                                            | P1 | ⚠️ **未改**  | 仍是全量捞取后循环单条 UPDATE（已加日志便于观察，属性能项，不是正确性缺陷）                                                                |
| 7  | `ModelAnnouncementServiceImpl` 四处 `catch → return false` 静默失败                  | P1 | ✅ 已修（部分）   | 已补 `log.error`，不再静默；但仍返回 `false`，前端需自行判断                                                                 |
| 8  | `AiChatCacheServiceImpl` 的 `persistToFile/loadFromFile/restoreAllFromJson` 空实现 | P1 | ✅ 已删       | 三个空实现移除并注释说明；`/ai/restore-from-json` 端点同步标记 `@deprecated`                                                |
| 9  | `UserServiceImpl:278` TODO 用户设置持久化                                             | P1 | ❌ **仍未实现** | 需新建 `user_settings` 表 —— 与「能加列就不建新表」铁律冲突，**需先决策承载表**再动手                                                  |
| 10 | `Neo4jClient` 连接失败只 `log.warn`，静默降级                                            | P1 | ✅ 已修       | 改 `log.error` + `degraded` 标记 + `Neo4jGraphHealthIndicator`                                              |
| 11 | 死代码 `AudioManager` / `VADDetector`                                             | P1 | ✅ 已删       | 全项目 0 引用                                                                                                 |
| 12 | `TTSFactory` 的 `cosyvoice` / `minimax` 为 TODO 占位（选了会静默回退 EdgeTTS）              | P1 | ❌ **仍未实现** | 仍是 TODO 注释                                                                                               |
| 13 | 4 个重叠工厂类（`LLMFactory` vs `LLMProviderFactory` 等）                               | P2 | ⚠️ **未合并** | 仍并存                                                                                                      |
| 14 | 敏感词库从未存在（仅 5 个硬编码词）                                                            | P1 | ❌ **仍未实现** | `core/guard/OutputValidator` 仍是 5 词硬编码（报告标注「另立项」）                                                        |

**B.《项目现状基线（2026-10-06）》**

| #  | 基线项                                         | 等级 | 现状         | 证据                                                                      |
| -- | ------------------------------------------- | -- | ---------- | ----------------------------------------------------------------------- |
| 1  | 33 条 SQL 引用 7 张不存在的表                        | P0 | ✅ 已清       | `DrugMapper.xml` 已改指 `mall_product`（仅留说明性注释）                            |
| 2  | `get_health_data` 只读空 JSON，AI 看不到 160 条健康数据 | P0 | ✅ 已修       | 改查 MySQL                                                                |
| 3  | 向量库 208/312 错位，104 篇永久不可召回                  | P0 | ✅ 已修       | `/news/rag/rebuild` 重建                                                  |
| 4  | **`audit_log` 代码在线但写入路径未打通**                | P1 | ✅ **本轮打通** | 数据智能体每次问答写 `audit_log`（含拒绝时的 `status=0` + 拒绝原因）                         |
| 5  | `ai_usage` 是唯一真死代码                          | P2 | ✅ 已接入      | `AiServiceImpl.recordUsage`（ENG-10）                                     |
| 6  | RBAC 5 张表从未落地（23 条 SQL 悬空）                  | P1 | ✅ 已清       | 悬空 Mapper 已移除                                                           |
| 7  | 零数据层测试（229 用例全 Mockito、零 `@SpringBootTest`） | P1 | ✅ 已加       | `MapperTableConsistencyTest`（悬空表即失败）；⚠️ 「无真集成测试」本身**仍未闭环**，最新口径见本附录 §B E1  |
| 8  | 9 张空表中 4 张只差种子数据                            | P1 | ⚠️ **未变**  | 需人工造数据                                                                  |
| 9  | `user.phone` 全表 NULL + 短信登录硬编码 `123456`     | P1 | ❌ **未变**   | **上线前必办**                                                               |
| 10 | 前端 API 层只有 `api/doctor.js`，80+ 接口散落在 .vue   | P1 | ⚠️ 部分改善    | 本轮新增 `AgentHistory` 组件统一走 `utils/request.js` / `doctorRequest.js`；其余仍散落 |
| 11 | 11 处前端 404 调用                               | P1 | ❌ **未变**   | 另立项                                                                     |
| 12 | 无疾病规范化词表（ICD/SNOMED 对齐 = 0）                 | P1 | ⚠️ 部分      | 已有 119 节点人工医学词表 + 别名归并，但**未与 ICD/SNOMED 对齐**                            |

**小结**：P0 项 **6/6 全部闭环**；P1 项已闭环 14 项，**仍未实现 5 项**  
（`UserServiceImpl` 用户设置持久化、`TTSFactory` 两个供应商、敏感词库、  
`user.phone`/短信登录、前端 11 处 404），其中后四项归属「另立项」或「上线前必办」，  
不属于 DataAgent 迁移范围。

---

### 🔧 阶段 0-B 实施偏差修正（原方案写法 → 实测处置）

| 原方案写法                                                                 | 实测 / 处置                                                                                               |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| 「`MEASURED_BY`：Indicator → Examination，来自 `health_model_config` 12 行」 | 🔴 **不存在 Examination 实体**。改为只建 `Indicator` 节点（带 unit/valueRange/symbol 属性），不造无意义边                     |
| 「用 MySQL `JSON_TABLE` 展开 7 个 JSON 列」                                  | 改为 **Java 侧解析**。MySQL 9.7 存储过程传中文参数会乱码；且实际存储形态有三种（数组 / 对象数组 / 裸串），Java 侧更好兼容                          |
| 「`GraphRAG` 回退固定医学词表」                                                 | 🔴 只匹配节点**规范名**，词表里的**别名**（"血压高"→"高血压"）根本用不上 → 新增 `MedicalVocabulary.matchAliases()` 做别名归并，否则别名写了等于没写 |
| 「`neo4j_import.sh` 直接 curl」                                           | 导入端点受 `@Protector(role="管理员")` 保护 → 脚本第 0 步增加登录取 token，并新增 `/graph/health` 探测                         |
| 未考虑 Bean 名冲突                                                          | Spring Boot 自带 `Neo4jHealthIndicator`，同名会冲突 → 自定义类改名 `Neo4jGraphHealthIndicator`                      |

### ⚠️ 0-B 遗留待办

1. **Neo4j 仍长期未启动** → 图谱数据只落在本地 `triples.json`。功能完整可用（检索走 `LocalTripleStore`），  
   但 Neo4j 的图遍历能力未验证。**Neo4j 起来后重跑一次 `/graph/import/all` 即可同步**
2. **`ai_config.api_key` / `embedding_api_key` 仍为空**（0-A 遗留）→ 向量库重建与 LLM 抽取仍未跑
3. **4 种药只在 `drugs.json`、商城无售**（多潘立酮片 / 对乙酰氨基酚片 / 小柴胡颗粒 / 连花清瘟胶囊）  
   → 已由 `/graph/stats` 的 `DRUG_PRODUCT_UNMATCHED` 告警显式暴露
4. `Neo4jConfig.java` 仍是死代码（22 行只有 `@Value`）→ 未删（避免无关改动），后续可清理

---

### ✅ 阶段 0-A 已完成（2026-10-06 晚）

| 项      | 内容                           | 状态        | 实测证据                                                                         |
| ------ | ---------------------------- | --------- | ---------------------------------------------------------------------------- |
| **A1** | `get_health_data` 改查 MySQL   | ✅ 完成      | 原读 17 个全空的 JSON（0 条）→ 现直连 `user_health`(160) + `patient_profile`(7)；新增 29 用例 |
| **A2** | 向量库 208/312 错位               | ✅ 完成（代码层） | 三者按下标严格对齐；`log.error` 暴露不一致；新增 11 用例                                         |
| **A3** | 清理 33 条必崩 SQL                | ✅ 完成      | 见下方详述                                                                        |
| **A4** | `MapperTableConsistencyTest` | ✅ 完成      | 零误报，精确锁定 5 张悬空表                                                              |
| **A5** | 清理遗留文件/死脚本                   | ✅ 完成      | `init_database.sql` 删药品建表段；`neo4j_import.sh` 加状态声明；删 `dump.rdb`              |
| **A6** | 补种子数据                        | ✅ 部分完成    | `model_announcement` 2 行（1 条 active）                                         |

**测试用例：229 → 273（+44），全部通过。**

### A3 处置结果（与方案原定不同，已按实测修正）

方案原写「B 类 5 个 RBAC Mapper 无 Service 注入」——**该前提经核实不成立**，  
它们全部有 Service 真实调用（如 `RoleServiceImpl:47/71/77`）。故改为整条链路处置：

| 层                                                                          | 处置                                                | 数量                    |
| -------------------------------------------------------------------------- | ------------------------------------------------- | --------------------- |
| `drug` / `drug_subscription`                                               | **改指向既有表**（`mall_product` + `shopping_cart`），不新建表 | 1 个 Mapper / 10 条 SQL |
| `role` / `permission` / `role_permission` / `user_role` / `sensitive_word` | **删除整条链路**（Mapper+XML+Service+Controller+Entity）  | 23 个文件                |

删除前完成三项安全确认：① 17 份历史备份中这 5 张表从未出现（从未落地）  
② 前端零调用、无相关页面 ③ `RoleService` 零引用、`ContentAuditService` 未被 AI 链路依赖。  
所有文件已在 Git 版本控制中，删除可回溯。

### 🔴 本次发现并纠正的前序误判

| 此前结论                                       | 实测                                  | 根因                                                         |
| ------------------------------------------ | ----------------------------------- | ---------------------------------------------------------- |
| 「AI 的药品知识与商城商品只有 1 条匹配」                    | **实际 92% 重合**（49/53 个药名）            | mysql CLI 在 Windows 下行尾是 CRLF，Python 按 LF 切行导致每行残留 CR，造成假象 |
| 「gen_schema_doc.py 会自动同步到 `Data/04_结构基线/`」 | **不会**，只写 `docs/`                   | 脚本无同步逻辑，已补上并加注释                                            |
| 「104 篇文档永久不可召回」                            | 属实，但**修代码只能保证不错位**，补回 embedding 需重建 | 见 A2                                                       |

### ⚠️ 遗留待办（不阻塞 0-B）

1. **`ai_config.api_key` / `embedding_api_key` 为空** → LLM 与向量化均不可用，  
   A2 的数据层修复（让 104 篇可召回）需先配好 embedding key 再跑 `/news/rag/rebuild`
2. 仍有 **4 种药**只存在于 `drugs.json` 而商城没有：多潘立酮片、对乙酰氨基酚片、  
   小柴胡颗粒、连花清瘟胶囊 → AI 会推荐商城买不到的药

---

## 核心判断：先修管道，再建图

**当前最大浪费不是"没有图谱"，而是"有数据但 AI 看不到"。**

- `user_health` 有 **160 条**真实测量记录 → AI 读到 **0 条**
- `patient_profile` 有 **7 例**完整档案（7 个 JSON 列 + 9 指标字段）→ AI 读到 **0 条**
- `visit_record` 有 **3 条**完整就诊三元组原料 → AI 读到 **0 条**
- 向量库 **104 篇**文档永久不可召回（208/312 错位）

因此**阶段 0-A（让数据可见）优先级高于一切**，且完全不引入 DataAgent。

---

## 阶段 0-A：让已有数据变可见

### A1 🔴 修复 `get_health_data`（最高价值）

**问题**：`crm/agent/tool/GetHealthDataTool.java:26-28` 读 `ai_data/health/user_{id}.json`，而 **17 个文件全部 `total:0`，合计 0 条**。而 `BaseReActAgent.java:98` 系统提示词**强制** AI 用这个工具。

**改法（二选一，推荐 A）**：

| 方案        | 做法                                                                      | 优点                               |
| --------- | ----------------------------------------------------------------------- | -------------------------------- |
| **A（推荐）** | 改为查 MySQL `user_health` + `patient_profile`                             | 实时、零同步、数据可立即使用；顺带消除"数据导出文件"这类设计债 |
| B         | 用已有 `DataExportServiceImpl`（`/data-export/health/{userId}`）回填 17 个 JSON | 改动小，但数据仍是快照，会再次过期                |

**注意**：改 A 意味着这个 Tool 要用 `LocalThreadHolder.getUserId()` 取身份（与阶段 2 的双轨 Tool 同一模式），**不可接受 LLM 传 userId 参数**。

**验证**：问 AI「我最近血压怎么样」，应返回 `user_health` 里的真实记录；`/tmp/admin_token.txt` 里的 token 不可用于测试（会顶掉 admin 会话版本）。

---

### A2 🔴 重建向量库

**问题**：`cache/crm/vector_store/` 中 `collections.json` 声明 `doc_count:312`、`dimension:1536`，但 `vectors.bin` 只有 `1277952 / 4 / 1536 = 208` 条向量。

**Bug 链**：`LocalVectorStoreImpl.java:94` 调 `readAll(312)` → `VectorBinaryStore` 内部 `Math.min(docCount, 文件可读条数)` = 208 → 但第 113 行 `for i < docCount` 构造 **312 条 documents** → 检索时 208 向量配 312 文档下标 → **下标 208~311 的 104 篇永久不可召回，且映射已错位**。


**修法**：`NewsController.java:94` 已有 `/news/rag/rebuild` 端点，跑一次。

**验证**：重建后 `vectors.bin` 大小应为 `312 × 4 × 1536 = 1,916,928` 字节；抽 3 个原本 208 之后的 docId 应能被检索到。

---

### A3 🔴 清理 33 条必崩 SQL

**A 类：drug / drug_subscription**（用户已定：改指向 `mall_product`）

| 文件                               | 动作                                                                                                                     |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `mapper/DrugMapper.xml`          | 10 条 SQL 的 `FROM drug` → `FROM mall_product WHERE product_type='drug'`；`FROM drug_subscription` → `mall_order`（购药记录语义） |
| `mapper/DrugMapper.java`         | 实体 `Drug` → `MallProduct`；`DrugVO` 字段对齐 `mall_product` 17 列                                                            |
| `controller/DrugController.java` | 9 端点核对；前端零调用（死链），可考虑标 `@Deprecated` 或删除                                                                                |
| 前端                               | `Mall.vue:69`、`UserProfile.vue:252,267` 的 `/user/drug` **死链**（Vue Router 无此 path）→ 删除入口                                |

**B 类：RBAC 5 张表**（用户已定：分步清理）

| 步骤 | 文件                                                                                                      | 动作                                                 |
| -- | ------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| 1  | `RolePermissionMapper.xml` + `.java`、`UserRoleMapper.xml` + `.java`、`SensitiveWordMapper.xml` + `.java` | **直接删除**（无 Service 注入、无调用方）                        |
| 2  | `RoleMapper.xml`、`PermissionMapper.xml`                                                                 | **暂留 + 加 `@Deprecated` + TODO 注释**，待确认 RBAC 是否需要落地 |

> 前端 `permission/user/0` 与 `role/user/0` 均 404（后者后端连基路径都没有）。清理时需同步。

---

### A4 🔴 新增数据层一致性测试

**问题**：6 个悬空 Mapper **全部零测试**；229 个用例全是 Mockito 单测，**零 `@SpringBootTest`**，从不连库 → 所以"删表不改代码"在历史上从未被测试发现。（**2026-10-12 更新**：句中「229」是阶段 0-A 的时点数，此后用例量已增至 claude 线 **364**（2026-10-10）/ main 侧 **541**（2026-10-11，commit `9baa4ea`）—— 单测**数量**已不是瓶颈；但「全是 Mockito 纯对象测试、零 `@SpringBootTest`」的定性**至今未变**，最新口径见本附录 §B E1。）

**做法**：新增 `src/test/java/cn/kmbeast/mapper/MapperTableConsistencyTest.java`

```
1. 扫描 classpath:mapper/*.xml
2. 正则提取 FROM|JOIN|INTO|UPDATE 后的表名
3. 逐个查 information_schema.tables（需一个可用的 DataSource，或读 Data/README.md 里的表清单）
4. 断言：所有提取到的表名都存在于实际库
5. 失败信息列出：XML 文件 + 行号 + 表名
```

**意义**：这是防止"删表不改代码"重演的**唯一自动化手段**，也是本次整改最有长期价值的产出。

---

### A5 🟡 清理遗留文件

| 文件                                   | 动作                                                                                                                                                                             |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `Data/01_建库建表/init_database.sql:164` | 删 `CREATE TABLE drug`                                                                                                                                                          |
| 同文件 `:185`                           | 删 `CREATE TABLE drug_subscription`                                                                                                                                             |
| 根目录 `neo4j_import.sh`                | 注释写"10 药物"实际 55 种；5 个端点全不存在 → 阶段 0-B 补齐后修复，或删除                                                                                                                                 |
| 根目录 `dump.rdb`（112 B）                | Redis 快照，位置不当 → 移到 `deploy/` 或删除                                                                                                                                               |
| `Data_整理前快照_20261004_135823/`        | ✅ 2026-10-07 确认 `Data/` 完整后 `git mv` 至 `Data/90_归档/`，随后确认内容冗余（dump 与 legacy 导出 md5 相同、脚本已被取代）并清理；`Data/90_归档/` 由 42 文件 / 3.9 MB 精简为 **4 文件 / 485 KB**（保留最近两代完整备份 + 废弃 RBAC 脚本） |

---

### A6 🟠 补种子数据（4 张表只差数据）

| 表                             | 说明                                                                   |
| ----------------------------- | -------------------------------------------------------------------- |
| `model_announcement`          | 代码完整在线，前端 `ModelBanner.vue` 在调 `ai/announcement/active` → **只差种子数据** |
| `quiz_exam_question`          | 试卷 2 行存在，未配题                                                         |
| `quiz_record` / `quiz_answer` | 上游为空                                                                 |
| `audit_log`                   | 代码在线但**写入路径未打通**（`/audit/*` 4 端点全是敏感词 CRUD，无"记录审计"调用）                |

⚠️ **空表 ≠ 无用表**。`shopping_cart`/`shipping_address`/`user_follow` 功能在线，绝不能删。唯一真死代码是 `ai_usage`（1 条 SQL，无 Service 注入）。

---

## 阶段 0-B：知识图谱从零到有

> **✅ 已于 2026-10-07 实施完成**，进度与实测数据见本文顶部「执行进度」。
>
> 落地位置（全部在 `后端/personal-health-api/src/main/java/cn/kmbeast/core/graph/`）：
>
> | 文件                                                               | 职责                                  |
> | ---------------------------------------------------------------- | ----------------------------------- |
> | `GraphSchema.java`                                               | 11 个节点标签 + 15 个关系类型 + 幂等约束 DDL（B1）  |
> | `Triple.java` / `GraphNode.java` / `GraphPayload.java`           | 三元组与节点的唯一中间表示（B1）                   |
> | `Neo4jClient.java`（改）                                            | 写能力 + 修复 `isConnected()` 假阳性（B2）    |
> | `Neo4jGraphWriter.java`                                          | 按标签分组的 MERGE 批量写入（B2）               |
> | `LocalTripleStore.java`                                          | 本地兜底图：内存邻接表 + `triples.json` 落盘（B2） |
> | `Neo4jGraphHealthIndicator.java`                                 | `/actuator/health/neo4jGraph`（B2）   |
> | `MedicalVocabulary.java` + `resources/ai-data/seed-triples.json` | 人工词表（B4）                            |
> | `GraphExtractor.java`                                            | 7 条规则抽取链路，零 LLM（B5）                 |
> | `GraphImportService.java` / `GraphImportController.java`         | 导入编排 + 5 个端点（B3/B6）                 |
> | `GraphRAG.java`（改）                                               | 接入本地兜底 + 别名归并（B2/B4）                |
>
> 另有 `tools/graph_dryrun.py` —— 离线核算脚本，不启动 Spring 即可用真实数据验证抽取规模。

### B0 前置认知

| 事实                                        | 影响                         |
| ----------------------------------------- | -------------------------- |
| `Neo4jClient.java:54` 用 `readTransaction` | **物理上无法写入**，必须新增 write 方法  |
| 无节点标签/关系类型/schema 文件                      | 需从零设计                      |
| `cache/knowledge_graph/` 是空目录             | 图谱里 **0 个节点**              |
| Neo4j 实例**长期未启动**（无 docker 镜像、本机无安装）      | 必须有本地兜底                    |
| 原料仅演示级：疾病 9 个提及、症状 **3 条**、就诊 3 条         | **撑不起医学知识图谱**，只能建"患者健康画像图" |

> **诚实结论**：当前数据能支撑「患者-指标-数值-就诊」的关系型图谱（约 250 节点/220 关系，确定性、零 LLM），**撑不起**「疾病-症状-药物」医学知识图谱。后者需要外部知识源或人工录入词表。

---

### B1 图谱 Schema（面向患者画像 + 疾病知识双层）

```cypher
// 唯一性约束 —— 幂等导入基础（MERGE by name）
CREATE CONSTRAINT disease_name  IF NOT EXISTS FOR (d:Disease)      REQUIRE d.name IS UNIQUE;
CREATE CONSTRAINT symptom_name  IF NOT EXISTS FOR (s:Symptom)      REQUIRE s.name IS UNIQUE;
CREATE CONSTRAINT drug_name     IF NOT EXISTS FOR (d:Drug)         REQUIRE d.name IS UNIQUE;
CREATE CONSTRAINT indicator_name IF NOT EXISTS FOR (i:Indicator)    REQUIRE i.name IS UNIQUE;
CREATE CONSTRAINT user_name     IF NOT EXISTS FOR (u:User)         REQUIRE u.name IS UNIQUE;

// 索引（GraphRAG 的 WHERE n.name=$name 走这个）
CREATE INDEX disease_name_idx  IF NOT EXISTS FOR (d:Disease)   ON (d.name);
CREATE INDEX indicator_name_idx IF NOT EXISTS FOR (i:Indicator) ON (i.name);
```

> ⚠️ 按 **Neo4j 5.x** 语法（实际驱动 5.28.x）。所有节点必须带 `name` —— 这是 `GraphRAG.loadGraphEntities()` 的 `MATCH (n) RETURN n.name` 与 `WHERE n.name=$name` 的**隐式硬契约**。

**节点属性**：`name`(必填) / `alias[]` / `description` / `source`（溯源）/ `importBatch`（批次回滚用）

**关系类型**：

| 关系                | 方向                      | 数据源                                       | 抽取方式                   | 预期规模 |
| ----------------- | ----------------------- | ----------------------------------------- | ---------------------- | ---- |
| `HAS_MEASUREMENT` | User → Indicator        | `user_health` 160 条 EAV                   | **确定性**                | 160  |
| `HAS_DIAGNOSIS`   | User → Disease          | `patient_profile.chronic_diseases` JSON   | **`JSON_TABLE` 零 LLM** | ~9   |
| `ALLERGIC_TO`     | User → Allergen         | `patient_profile.allergies` JSON          | JSON_TABLE             | ~5   |
| `TAKES_DRUG`      | User → Drug             | `patient_profile.medications` JSON        | JSON_TABLE             | ~8   |
| `TREATED_BY`      | Disease → Drug          | `drugs.json` + `mall_product.description` | 规则解析                   | ~100 |
| `SOLD_AS`         | Drug → Product          | 名称匹配两线                                    | 规则                     | ~50  |
| `MEASURED_BY`     | Indicator → Examination | `health_model_config` 12 行                | 规则                     | 12   |
| `DIAGNOSED_WITH`  | VisitRecord → Disease   | `visit_record.diagnosis` 3 条              | 规则                     | 3    |
| `PRESCRIBED`      | VisitRecord → Drug      | `visit_record.prescription` 3 条           | 规则                     | 3    |

> **不要建 RBAC/敏感词节点** —— 那 5 张表从未落地，无数据可抽。

---

### B2 `Neo4jClient` 新增能力

```java
// 新增（query() 保持不动 → GraphRAG 零回归）
public void write(String cypher, Map<String, Object> params);        // writeTransaction
public int  writeBatch(String cypher, Map<String, Object> params);    // UNWIND $rows
public void applySchema(List<String> statements);                     // DDL，IF NOT EXISTS 幂等
public boolean isHealthy();   // 🔴 修复 isConnected() 假阳性：driver!=null 在宕机后仍为 true
public Map<String,Object> stats();   // 支撑 /graph/stats
```

**配套修 `init()`**：连接失败由 `log.warn` 改 `log.error` + 置 `degraded` 标记（静默降级 → 可见降级）。注册 `HealthIndicator("neo4jGraph")`。

**新增 `LocalTripleStore`**（~150 行）：从 `cache/knowledge_graph/triples.json` 构建内存邻接表。**这是 Neo4j 长期未启动时能立即工作的关键**，也是成果不丢的保险。

---

### B3 补齐 5 个不存在的端点

`neo4j_import.sh` 调用的 5 个端点，`grep "graph/import" src/` → **0 命中**。

| 端点                       | 职责           | 数据源                                      | 幂等                   |
| ------------------------ | ------------ | ---------------------------------------- | -------------------- |
| `/graph/import`          | 基础三元组 + 疾病词表 | 🆕 `resources/ai-data/seed-triples.json` | MERGE + ON MATCH SET |
| `/graph/import/drugs`    | 药品 + 分类      | `ai_data/drugs.json`(55)                 | 同上                   |
| `/graph/import/articles` | 从知识文章抽三元组    | `news` content_type='NEWS'(29)           | 按三元组 MERGE           |
| `/graph/import/all`      | 编排上述全部       | —                                        | 全部幂等                 |
| `/graph/stats`           | 统计           | Neo4j                                    | 只读                   |

**统一前置校验**：Neo4j 不可用返回 `503 + {status:"GRAPH_UNAVAILABLE"}`，**不返回 200 空结果**。

**幂等验收**：连跑两次 `/graph/import/all`，`/graph/stats` 的 nodeCount/relationshipCount **必须完全不变**。

---

### B4 建最小疾病词表（🔴 唯一无法自动完成的部分）

从 4 处汇总去重，人工对齐 ICD-10：

- `patient_profile.chronic_diseases`（9 个提及：高血压×2、骨质疏松、2型糖尿病、高尿酸、轻度脂肪肝、高血脂、甲状腺功能减退）
- `visit_record.diagnosis`（3 条：原发性高血压1级、空腹血糖受损(IFG)、轻度脂肪肝）
- `mall_product.description` 适应症提及（疼痛33/感冒20/高血压6/腹泻5/糖尿病3/甲状腺2）
- `hospital_doctor.expertise`（9 个词：高血压、糖尿病、冠心病、普外科、骨科、微创手术、小儿感冒、肺炎、腹泻）

目标 20-50 条，覆盖 `GraphRAG.FALLBACK_TERMS` 那 10 个词（高血压/糖尿病/心脏病/感冒/发烧/咳嗽/头痛/哮喘/胃炎/失眠）。

> **这是后续一切图谱工作的地基，也是唯一必须人工的部分。**

---

### B5 抽取策略：规则优先，LLM 离线补充

| 层          | 做法                                                                                                                            | 覆盖             |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **层1 规则**  | `JSON_TABLE` 展开 7 个 JSON 列；`user_health` EAV 建边；`health_model_config` 建指标关系；`drugs.json` + `mall_product.description` 规则解析适应症 | **~85%，零 LLM** |
| **层2 LLM** | `news` 29 篇 + `visit_record` 3 条 → 走现有 `DeepSeekProvider`                                                                     | 剩余             |
| **层3 种子**  | `seed-triples.json` 保证首次非空                                                                                                    | 兜底             |

🔴 **LLM 抽取必须离线**（`@Scheduled` 或手动端点），**绝不在 `/user/chat` 同步执行**（N 次调用会让首字延迟从秒级变分钟级）。产出先落 `cache/knowledge_graph/triples.json` 再灌 Neo4j。

⚠️ MySQL 9.7 实测**不支持 `MD5()`**（`ERROR 1305`）；存储过程传中文参数乱码 → **抽取逻辑全放 Java 侧，不写 MySQL 存储过程**。

---

### B6 `/graph/stats` 返回结构

```json
{
  "status": "OK",
  "backend": "neo4j",
  "neo4j": { "connected": true, "uri": "bolt://localhost:7687", "driverVersion": "5.28.x", "degraded": false },
  "graph": {
    "nodeCount": 142, "relationshipCount": 287,
    "labels": [ { "label": "User", "count": 10 }, { "label": "Indicator", "count": 12 }, { "label": "Disease", "count": 18 } ],
    "relationshipTypes": [ { "type": "HAS_MEASUREMENT", "count": 160 } ]
  },
  "sources": [ { "source": "patient_profile_json", "nodes": 24, "lastImport": "2026-10-06T13:40:00" } ],
  "coverage": {
    "importEndpointsUsed": ["/graph/import", "/graph/import/all"],
    "pendingEndpoints": ["/graph/import/articles"],
    "llmExtractionJobStatus": "NEVER_RUN"
  },
  "warnings": [ { "type": "DRUG_PRODUCT_UNMATCHED", "detail": "5 个 drugs.json 药名在 mall_product 无匹配" } ],
  "elapsedMs": 23
}
```

`status` 与 `backend` 分离 → 前端能区分"图谱为空"与"图谱挂了"；`warnings` 暴露药品两条数据线的不一致。

---

## 阶段 1-3：AI 能力接入

### 阶段 1：图谱 Tool

`crm/agent/tool/QueryGraphTool.java` —— 第 7 个 Tool，`limit` 硬钳制上限 50（LLM 传 9999 也压到 50），超时 2s，**降级返回 `ToolResult.ok()` 而非 `error`**（error 会让 ReAct 重试）。

`ToolRegistry` **零改动** —— 它靠 `@Resource List<Tool>` + `@PostConstruct` 自动收集。

### 阶段 2：MySQL 参数化查询（双轨制）

**🔴 关键事实**：`JwtInterceptor:113` 对医生也调 `setUserId(hospital_doctor.id, 3)` —— **医生 id 是 3001+，用户 id 是 1~8，两套不同 ID 空间**。必须先判 `RoleEnum`。

| 维度   | 用户端（role=2）                               | 医生端（role=3）                          |
| ---- | ----------------------------------------- | ------------------------------------ |
| 身份   | `getUserId()` = `user.id`                 | `getUserId()` = `hospital_doctor.id` |
| 可查范围 | **仅本人**，SQL 强制 `user_id = :currentUserId` | **全库患者**（含指定 `patientId`）            |
| 审计   | 不需要                                       | 🔴 **先写 `audit_log` 再查**             |

**设计要点**：

- `metric` 用**代码内硬编码枚举**（12 项指标），LLM 不能自由发挥
- Schema 里**根本没有 userId 参数**；用户端传 `patientId` **被忽略**
- SQL 全参数化 → **不需要 SqlGuard**，因为 LLM 不产出 SQL
- ⚠️ `user_health.value` 是 `varchar(100)` → **必须 `CAST(... AS DECIMAL(10,2))`**，否则字符串序排序

**越权测试（必须全过）**：LLM 传 `userId=999` 被拒 / 无 ThreadLocal 拒绝执行 / 医生端查 `patientId` 先审计 / 用户端传 patientId 被忽略 / 非白名单指标拒绝 / `days=999999` 钳制到 3650

### 阶段 3：LLM 意图路由（软路由）✅ 已实施（2026-10-07）

> 原计划 `DataSourceRoute{GRAPH,MYSQL,HYBRID,CHITCHAT,UNKNOWN}` + 独立熔断器方案**未采用**，下方为实际落地形态。

**软路由 vs 硬路由**：只把意图倾向**追加进 system prompt「引导」而非「强制」** LLM 选工具，保留 ReAct 多轮协作能力。


```java
public enum Intent { HEALTH_RECORDS, GRAPH, DRUG, KNOWLEDGE, CHAT }
public enum Confidence { HIGH, MEDIUM, LOW }

// IntentHint：路由结果 + 可选离线兜底载荷
//   hintText      → 追加进 system prompt 的引导语
//   targetTool    → L4 离线兜底直接调的工具名
//   suggestedArgs → L4 离线兜底入参
public boolean isHighConfidenceDataIntent() {  // HEALTH_RECORDS|GRAPH 且 HIGH
    return confidence == Confidence.HIGH
        && (intent == Intent.HEALTH_RECORDS || intent == Intent.GRAPH);
}
```

**意图识别链路**（规则、零 LLM）：`HealthMetric` 别名子串宽匹配 → `HEALTH_RECORDS`；`MedicalVocabulary` 规范名+别名 → 实体；关系词+实体 → `GRAPH`；`DRUG_KEYWORDS`/`isDrugName` → `DRUG`；科普 → `KNOWLEDGE`；兜底 → `CHAT`。

**四层 fail-safe（实际）**：

1. **L1** 正常 LLM 选工具（`tool_choice=auto`，`ToolRegistry` 自动收集）
2. **L2** 重路由：仅首轮纯文本但高置信度数据意图 + 仍有富余轮次 → 追加强化指令 system 消息重跑（`shouldRetryForRouting` 守卫）
3. **L3** 工具自身 `ok()` 降级（阶段 1/2 的 `QueryGraphTool`/`QueryHealthRecordsTool` 已具备）
4. **L4** 离线兜底：`catch(CrmException)` 时若高置信度数据意图 + 已登录身份 → 直接 `tool.execute` 返真实数据，前缀 `[离线模式]`（`tryOfflineFallback`，含 `toolRegistry==null` 守卫、userId 判空）

**生命周期**：`intentHint` 用 ThreadLocal 承载，`setIntentHint` 后统一由 `clearConversationContext()` 的 finally 释放（增强系统提示与 fail-safe 两处读取不会串号）。`ChatRouting` 走 `@AllArgsConstructor` 自动生成 4 参构造，无原预警的 Lombok 冲突。

**实测**：新增 `IntentRouterTest` + `ReActFailSafeTest`；**测试 331 → 344（+13）全绿**。

---

## 验证方式

### 阶段 0-B 验收（2026-10-07 已执行）

**1. 单元测试**：273 → **317**，全部通过（新增 4 个测试类：  
`GraphExtractorTest` / `GraphExtractorDbTest` / `LocalTripleStoreTest` /  
`GraphSchemaTest` / `GraphImportServiceTest` / `GraphRagFallbackTest`）

**2. 离线规模核算**（不启动 Spring，只读 MySQL）：

```bash
cd 后端/personal-health-api && python tools/graph_dryrun.py
# → 节点 248 / 边 485，并列出两线药品不一致清单
```

**3. 端到端幂等验收**（需后端在跑 + 管理员 token）：

```bash
# 用专用管理员账号，不要用 admin（会顶掉在用会话）
bash neo4j_import.sh http://localhost:21090/api/personal-health/v1.0 <账号> <密码>
# 脚本第 6 步会连跑两次 /graph/import/all，
# 请比对两次 /graph/stats 的 nodeCount / relationshipCount —— 必须完全一致
```

**4. 降级可见性验收**：Neo4j 未启动时，`/graph/health` 返回  
`backend=local`；`/graph/stats` 返回 `status=DEGRADED` + `warnings[NEO4J_UNAVAILABLE]`；  
`/actuator/health/neo4jGraph` 显示 `DEGRADED`。**导入不失败，本地仍有数据。**

---

### 常规回归

编译与测试由用户在 IDEA 侧执行 `mvn test`（本机 Maven 3.6.1 < 插件要求 3.6.3；  
用户已确认 2026-10-07 mvn 构建正常）。

AI 侧回归：

```bash
cd /d/Program/智康云-健康管理系统/后端/personal-health-api
find src/main/java -name "*.java" > /tmp/srcs.txt
javac -encoding UTF-8 -nowarn -cp "$(cat /tmp/cp_main.txt)" -d target/classes @/tmp/srcs.txt
find src/test/java -name "*.java" > /tmp/tsrcs.txt
javac -encoding UTF-8 -nowarn -cp "$(cat /tmp/cp_test.txt)" -d target/test-classes @/tmp/tsrcs.txt RunAll.java
java -Dfile.encoding=UTF-8 -cp "$(cat /tmp/cp_test.txt)" RunAll
```

⚠️ **测试接口禁忌**：不要用 curl 反复登录 admin —— 每次登录都会 increment Redis `auth:user:{id}:ver`，把正在使用系统的用户踢下线。用 `lily` 账号或复用 `/tmp/admin_token.txt`。测试完 `taskkill` 掉自己拉起的 java 进程，别占着 21090。

---

## 附录：旧文档整合归档（2026-10-07）

仓库文档一度达 27 份、多份互相矛盾。本次把**已被取代的旧文档**的价值并入权威文档后删除，结果如下。

### A. 处置清单

| 原文档                                            | 处置      | 去向                                                                 |
| ---------------------------------------------- | ------- | ------------------------------------------------------------------ |
| `DELIVERY.md`（根，v1.2 交付手册）                     | ❌ 删除    | 「部署指南 / 配置说明 / 使用指南 / 故障排查」4 章并入根 `README.md`；其余 5 章与 README 重复故丢弃 |
| `docs/项目状态.md`（v5.1 快照）                        | ❌ 删除    | 表数 / 单测 / 页面数 / Controller 数全部与基线冲突，**无独有内容**；2026-10-10~12 claude 线写回该旧文档的增量（通知开关接线 + 服务启动阻断缺陷）已移植至 `项目现状基线.md` §十一 |
| `docs/测试报告.md`（2026-08-09 快照） + `功能测试方案.md`（根） | 🔄 合并重写 | 合并为**当前版** `docs/测试报告.md`（405 用例 / 逐层跑法 / 回归范围清单）                  |
| `docs/缺陷与改进路线图.md`                             | ❌ 删除    | 待排期项并入本附录 §B（本文档即缺陷台账的现行版本）；2026-10-10~12 claude 线改写的 **E1** 口径同样并入 §B，删除决定维持不变 |
| `docs/UI与医生门类扩展执行计划.md`                        | ❌ 删除    | 已执行产出与决策并入本附录 §D                                                   |
| `docs/数据模型重构与项目扩展方案.md`                        | ❌ 删除    | 关键结论与判断修正并入本附录 §C                                                  |

> 删除的文件均留在 git 历史中（`git log --all -- <path>` 可取回）。

### B. 仍待排期清单（并入自《缺陷与改进路线图》，状态截至 2026-10-07；E1 口径更新至 2026-10-10）

**安全**

| #   | 问题                                                | 现状                   | 建议                                         |
| --- | ------------------------------------------------- | -------------------- | ------------------------------------------ |
| S2  | 手机号登录为假实现（验证码写死）                                  | 未修                   | 上线前下线该入口，或接真实短信 + 服务端校验 + 频次限制             |
| S5  | AI 密钥在 `ai_config` 中明文存储                          | 未修                   | KMS / 信封加密                                 |
| S6  | WebSocket token 走 URL（`/ws/notification/{token}`） | 未核实                  | 改 header 或短期一次性 token，避免进 nginx access log |
| S7  | `/file/getFile` 匿名可读                              | 设计权衡（capability URL） | 确认文件名随机源强度；日志脱敏                            |
| S10 | 登录无防爆破                                            | 未修                   | 失败次数锁定（Redis 计数）+ 验证码                      |
| S9  | 等保三级 / 渗透测试                                       | 未开展                  | 商用合规前置，需外部机构                               |

**工程化 / 运维**

| #  | 问题                                             | 现状 | 建议                               |
| -- | ---------------------------------------------- | -- | -------------------------------- |
| E1 | 无集成 / E2E 测试                                   | **单测数量已不是问题**（2026-10-10 `origin/claude` 线 364 例、2026-10-11 main 侧 commit `9baa4ea` 541/541 全绿；合并后总数见 `docs/测试报告.md`），问题是这些用例**全部是 Mockito 纯对象测试，没有一个 `@SpringBootTest`** —— 容器与 Mapper XML 从未被真正加载。该盲区由 `MapperXmlParseTest`（静态解析，不需数据库）**兜底**，并借此揪出启动阻断缺陷 BUG-002（`DepartmentMapper.xml` 两处裸写 `<>` 令服务无法启动） | 至少补登录 / 通知推送 / 上传权限 / RAG 关键链路冒烟；**未跑 `mvn compile` / `npm run build` 前不要下发**；长期需补一个**真能启动容器**的集成测试，静态兜底只是止血 |
| E2 | CI 门禁薄弱（仅 `mvn test`）                          | 未修 | 加 lint / 依赖 CVE 扫描 / 构建产物校验      |
| E3 | 告警外发未配置（`deploy/alertmanager.yml` 为占位 webhook） | 未修 | **上线前**替换为企业微信 / 钉钉 / 飞书 webhook |
| E4 | 无自动备份脚本                                        | 未做 | 补 cron 自动备份（MySQL dump + 文件卷）    |
| E5 | 容器 healthcheck 用 `/user/login`                 | 未改 | 改 `/actuator/health`；补优雅停机       |

> **E1 口径演进（避免后来人误读）**：旧《缺陷与改进路线图》写作「E1 **测试覆盖 <2%** —— 25 单测 / 336 Java 文件」（2026-08-01 口径）；
> 2026-10-07 该文档删除、待排期项并入本 §B 时**未带入 E1 行**（其「<2% / 25 单测」的量级口径当时已被 405 例的现实作废，只剩「无集成/E2E」这半句仍然成立）。
> 2026-10-10 `origin/claude` 线把 E1 改写为「无集成/E2E 测试」，2026-10-12 合并时移植回本表 ——
> **单测数量早已不是瓶颈，缺的是能真正启动容器的那类测试**；`MapperXmlParseTest` 只是静态止血。
> claude 原文里指向的 `docs/测试报告.md` §3.2，合并后该 Bug 清单编号为 **§6.2**（BUG-002 编号不变）。

**架构演进**

| #  | 方向                                         | 状态                                     |
| -- | ------------------------------------------ | -------------------------------------- |
| A1 | 鉴权模型重构（会话版本号 / refresh token / 账号状态校验一体设计） | 会话版本号 + 账号状态校验**已落地**；refresh token 未做 |
| A4 | 向量库选型（pgvector vs Milvus）                  | 待做（承接「本地文件全量扫描」限制）                     |
| A5 | 多租户（主业务健康数据为单租户模型）                         | 未做；CRM 已有租户隔离                          |
| A6 | 审计日志体系（等保要求）                               | `audit_log` 表已存在并被数据智能体写入；体系化未做        |
| A7 | 异步化（短信 / 通知 / 报表无 MQ）                      | 未做                                     |
| A8 | 密钥托管（KMS / Vault / 信封加密）                   | 未做（与 S5 同源）                            |

> 已闭环：S3 接口限流（Sentinel 登录 10QPS / AI 5QPS / 上传 20QPS）、S8 Spring Boot 3 迁移、F2 图片进上下文、F3 向量库规模（规划中）、F4 GraphRAG（**判定已修正**：图谱数据已构建 248 节点 / 485 边）、F6 登出服务端感知（会话版本号）。  
> F5（全站约 40 页硬编码色值收敛为品牌蓝 `#0050cb`）仍为**待排期**项。

### C. 数据模型改造的关键结论（并入自《数据模型重构与项目扩展方案》）

**表数量轨迹**：`42（初次审计）→ 43（迁移新增）→ 36（表合并）→ 35（message 合并）`。  
比最初规划的 28 张多 7 张——初版把 `post_favorite` / `post_report` / `news_save` 列为「保留」，实际已进一步合并为 `content_interaction`；剩余 0 行的表（`shopping_cart` / `shipping_address` / `user_follow` / `ai_usage` 等）**功能均在线**，删表等于删功能，故保留。

**判断修正 1：字符集问题被高估。** 初版称「`user` 表是 `utf8mb4_bin`、排序异常」——经 `information_schema` 逐列核实为**误判**：42 张表中 41 张本就是 `utf8mb4_0900_ai_ci`，真正遗留 5.7 旧规则的只有 `model_announcement` 的 6 个字段。统一目标因此定为 `utf8mb4_0900_ai_ci`（**不是** `general_ci`，后者改过去反而是降级）。

**判断修正 2：健康指标「双存储」不构成冲突。** `patient_profile.systolic_pressure` 是**最近一次快照**（医生看诊的当前值），`user_health` 的三条记录是**不同日期的测量序列**（趋势分析）——语义不同。故**未删** `patient_profile` 的 9 个指标字段；指标字典已从 5 个补到 12 个。若未来需单源，做法应是「保留快照 + 写入时同步 `user_health`」，而非删字段。

**遗留决策：外键约束仍为 0 个（有意为之）。** MySQL 外键会建索引带来性能开销，且 ORM 层已保证引用完整性 → 改为在 Service 层做存在性校验。

**归档表提醒已失效**：初版要求「观察 1~2 周后再 DROP 归档表」，实际未采用 RENAME 归档方案，而是「全库备份 → 迁移 → 校验通过 → 直接 DROP」（本项目零外键、无 DBA 归档流程，每次变更前都有完整 `mysqldump`）。

> 本轮实施踩的 6 个 MySQL 坑（无 `ADD COLUMN IF NOT EXISTS`、存储过程传中文乱码、无 `MD5()`、`LPAD` 截断长 id、`notification.type` 是 tinyint、`TABLE_ROWS` 是估算值）已收录进 [`.workbuddy/memory/MEMORY.md`](../.workbuddy/memory/MEMORY.md) 与 `开发指南.md`。

### D. UI 与医生/门类扩展的已执行产出（并入自《UI与医生门类扩展执行计划》）

**已落地**：科室加 `parent_id` / `code` / `level` / `leader_id` 支持多级层级；医生与 `user` **解耦**（医生作为执业身份独立登录）；`News` 吸收原 `post` 能力（`content_type` / `title` / 统计字段 / `status`）；`Comment` 多态 + 树形 + 软删；`RoleEnum` 新增 `DOCTOR(3)`；医生登录体系（`DoctorAuthService` + `DoctorController`）；`HospitalDoctorMapper` 分页与远程搜索。

**两个决策**：① `user` 表**加** `phone` 字段（已落地，为短信登录铺路）；② 医生端界面**从零建**（已落地：独立 `doctor-token` + `doctorRequest.js`，登录后不能跳用户端/管理端）。

---

## 附录 B：数据智能体三端落地（2026-10-07 晚，P0 打通 + P1 增强 + P2 清理）

> 起因：复盘 8 项「行业通行能力」清单时发现，本项目**用户端数据智能体实际处于关闭状态**，  
> 且多轮对话完全没接线。本节记录真实缺口、真因、处置与能力边界。

### B.1 三个缺口的真因（其中一条是此前复盘的误判）

| 缺口                  | 此前认知                           | **实测真因**                                                                                                                                        | 处置                                                                                                                                                                                                    |
| ------------------- | ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **多表 JOIN 不可用**     | 「`MySqlAiSqlGuard` 保守档位禁 JOIN」 | ❌ **误判**。守卫只有 5 条词法规则，禁用词表里**没有 `join`**，显式 `JOIN ... ON` 是放行的（3 条测试锁死）。真因是**实库外键数 = 0** → `buildSchemaText` 的【Foreign keys】段恒空 → 模型不敢猜关联而退化为单表 | `JdbcSchemaIntrospector.inferRelations()`：按列名约定（`x_id → x/xs/x_info`）+ 4 条人工核实过的提示（`*.doctor_id → hospital_doctor`）补关系。**零新增 DB 查询**（列名本就在第二条 SQL 里），下游 `collectRelations()` 与 `buildSchemaText` 自动生效 |
| **多轮对话为零**          | `multiTurn` 字段无人赋值             | 正确，但**还有第二处断点**：前端从不回传 `sessionId`，导致每轮追问都新建会话、追问也拿不到上下文                                                                                        | 后端在 `query()` 内**自取**历史回填 `multiTurn`（前端零改动也生效）；前端补 `sessionId` 收发 + 历史点击续聊；`sessionMessages` 补 `LIMIT`（原为无上限，长会话会撑爆 prompt）                                                                          |
| **RAG 与 NL2SQL 分离** | 术语不进向量库                        | 正确。`HybridRetriever` 以 `articleId` 为融合 key 且丢弃 id ≤ 0，术语文档没有合法 articleId                                                                        | 走 **Evidence 注入**（方案③）：新建 `Nl2SqlEvidenceService`，从 `health_model_config` 读 12 条全局指标 + `HealthMetric` 口语别名，只注入 SQL 生成与错误修复两处                                                                          |

### B.2 顺带修掉的 4 个真实缺陷

| # | 缺陷                                   | 证据                                                                                                                                                                                               | 处置                                                                                                                                                                  |
| - | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | 🔴 **数据智能体处于关闭状态**                   | `application.yml` 的 `crm.ai.nl2sql.enabled: false`，且 `Data/` 全部脚本中**无任何种子 SQL** 写 `system_config(config_group='data_agent')`；实库查询该分组 **0 行**                                                     | 新建 `Data/03_数据脚本/seed_data_agent_config.sql`（4 行 `INSERT IGNORE`，幂等），已执行并复核                                                                                         |
| 2 | 🔴 **主数据源是 root，NL2SQL 面无账号级兜底**     | `application.yml:26` 为 `username: ${DB_USERNAME:root}`。而 `application.yml:97` 自己写着「生产开启前必须配只读账号」                                                                                                 | 新建**独立**只读数据源 `Nl2SqlReadOnlyDataSourceConfig`（bean 名 `nl2sqlReadOnlyDataSource`），账号 `ai_ro` 只有 `SELECT`（实测 INSERT 报 `ERROR 1142`）。⛔ **不动主数据源** —— 切成只读会让登录/下单/发帖全挂 |
| 3 | 🔴 **5 个配置项「看起来能配、实际不生效」**           | `@Value("${crm.ai.nl2sql.maxSqlRetryCount:10}")` 等用的是**驼峰 key**，而 yml 里是 **kebab-case**（`max-sql-retry-count`）。`@Value` **不支持 relaxed binding**（那是 `@ConfigurationProperties` 的能力）→ 5 项全部取硬编码默认值 | 统一改为 kebab-case；并新增真正被流水线使用的 `max-fix-retry`（原为硬编码 `MAX_FIX_RETRY=2`，与配置的 10 差 5 倍）                                                                                 |
| 4 | 🟠 **黑名单两处重复定义 + `history()` 少一层过滤** | `Nl2SqlSchemaService` 与 `MySqlAiSqlGuard` 各写一份 `TABLE_BLACKLIST`，改一边忘另一边就会出现「schema 允许召回、守卫却拦死」；`history()` 只过滤了 `ai_conversation` 侧，未过滤 `ai_chat_record` 侧                                      | 前者提为唯一真源（`public static final`，另一处直接引用）；后者补齐双端过滤（已实测脏数据 0 行，加过滤不会让历史消失）                                                                                             |

### B.3 用户端入口

新增独立页面 `views/user/DataAgent.vue` + 路由 `/user/data-agent`（菜单由 `Main.vue` 从 router children 自动生成，加一条即出菜单）。  
原先它只是 `Assistant.vue` 里一个**默认不展开的隐藏 tab**（`mode` 默认 `chat`），用户根本发现不了；现已删除 `Assistant.vue` 内的重复实现，改为跳转按钮，避免两份逻辑漂移。

### B.4 ⚠️ 能力边界（如实记录，不得夸大）

| 项                                          | 状态                                                                                                                                         |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| JOIN 到 `user` 表（如「就诊记录 + 患者姓名」）            | ❌ **不支持**。`user` 在 `TABLE_BLACKLIST` 内，任何引用它的 SQL 会被词法守卫整条拦死。这是**既有安全边界**，不是本次退化                                                           |
| 推断出的关系是否 100% 可靠                           | ⚠️ 由命名约定 + 人工提示推断，**非数据库外键约束**。推断错会让模型写出错误 JOIN 且**结果错但不报错**（语义校验在 JSON 解析失败时默认判通过）。已在 prompt 的关系段标注「由列名约定推断，不确定时优先单表」，并锁了 7 条单测           |
| 多表 JOIN 的租户隔离                              | ✅ 已有逐表覆盖（`TenantSqlRewriter.rewriteAll`），非本次新增                                                                                             |
| Evidence 注入范围                              | 仅 SQL 生成（`new-sql-generate`）与错误修复（`sql-error-fixer`）两处。**刻意不注入** `mix-selector`（选表）与 `semantic-consistency`（语义校验）—— 那两处不需要术语，注入会让 token 翻倍 |
| StateGraph / Python 沙箱 / HITL / MCP / 多数据源 | ⏸ **有意不迁移**，理由见 `DataAgent集成迁移方案.md`，不是未完成                                                                                                 |

### B.5 验证证据（全部实测，非推断）

```
后端测试    405 → 433 用例 / 38 → 41 类，433 successful / 0 failed
新增单测    JdbcSchemaIntrospectorTest(7) + Nl2SqlEvidenceServiceTest(6)
            + DataAgentQaServiceTest(10) + DataAgentPipelineTest 扩充(4)
            首次编译通过即 412/412；补齐 DataAgentQaService 测试时一度 9 failed，
            根因是 Mockito varargs 匹配歧义 + 反射未沿类链找字段，均已修正
前端自检    75 个 .vue + 19 个 .js，0 失败（新增 DataAgent.vue）
Evidence    实跑输出 918 字符 / 27 行，12 个指标双格式（90~140 与 0~11.1）全部正确解析
只读账号    SHOW GRANTS → USAGE + SELECT ON personal_health.*
            INSERT notification → ERROR 1142 command denied（只读确实生效）
JOIN 可行性 user_health×health_model_config 160 行 / visit_record×hospital_doctor 3 行
            / appointment×hospital_doctor 5 行 —— 均能关联出真实数据
种子幂等    seed_data_agent_config.sql 连跑两遍，system_config 恒为 4 行
脏数据      ai_chat_record 与 ai_conversation 归属不一致 0 行
```
