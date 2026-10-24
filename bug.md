# bug.md — Claude 本轮改动的审核结论（2026-10-19）

> **审核对象**：`e530d9c` → `dbe8db4` 这 3 个提交（`24072ce`、`f6810a8`、`dbe8db4`，提交日期 2026-10-15），共 177 个文件、新增 25353 行、删除 1925 行。
> **写法约定**：每条先用大白话说"你作为使用者会看到什么"，再给技术定位（文件:行号），方便你直接把定位丢给 AI 去改。
> **我实际跑过的验证**：`mvn -o -B test` → **858 例全绿，BUILD SUCCESS**（与提交说明的 858/86 一致，我复核过计数）。
> **我没跑的**：前端 `npm run build`（改用静态编译 84 个 .vue 判断，0 报错）、真实数据库连接（本机口令被拒）、服务真实启动、浏览器点页面。

---

## 〇、2026-10-20 复核状态（本文写完后又发生的变化，先看清这个再往下读）

Claude Code 之后又做了一轮（下称阶段 6，已拆成 8 个提交并入 `main`）。我在 2026-10-20 独立复跑并逐条打开源码核对；下表标「已修（哈希）」的 5 条（含 P1-3 的同类缺陷）是我随后补的，已全部落地。

| 本文条目 | 现在的真实状态 |
|---|---|
| P0-1 十二张新表不进部署链 | **已修** —— `docker-compose.yml:55-56` 挂成 15/16 两支 initdb |
| P0-2 挂号第 3 步白屏（缺 `timeSlots`） | **已修（2026-10-20，`7925925`）** —— `data()` 补 `timeSlots: []`；前端构建复跑通过，但未在浏览器实测 |
| P1-3 问题双份 / 欢迎语消失 | **已修（`6c97e53` + `bc9c30b`）** —— 改为先 `buildInitialMessages` 再落库。同类的另外两处也一并改了（`bc9c30b`）：同步链路 `SeaChatWorkflow.processChat()` 原本先存（`:108`）后读历史（`:128`），现把读历史提到落库之前（`SeaChatWorkflow.java:107-117`），读取点仍在 `route()` 之前，所以"路由炸了也要留痕"这个原始动机没丢；`CrmChatController` 的 `/crm/chat/stream` 则移到拼装之后（`:112-119`），口径与 `/user/chat/stream` 一致。新增 `SeaChatWorkflowContextOrderTest` 3 例锁死这个顺序 |
| P1-4 流式绕过白名单与运行记录 | **已修（`197a2fa`）** —— 改调基类 `resolveAllowedTools()`，并在 `doRunStreaming` 统一包住 begin/finish（用户端与 CRM 端流式都因此补上记录） |
| P1-5 白名单保存失败即权限放大 | **已修（`ad0ff33`）** —— 先整体校验再删再插，另加 `AgentAdminToolBindingTest` 5 条用例锁死「校验失败一行都不许动」 |
| P1-8 工具口径一致性护栏 | **半修** —— 测试侧 `ToolCatalog` 已改真类路径扫描；生产侧 `ReactPromptLoader.java:257-261` 仍只算 prompt→registry，反向差集没人查，且只 `log.warn` |
| P2-10 README 指向已删脚本 | **未修** —— `Data/README.md:47,49,102` 仍列 `07_sensitive_word.sql`，该文件已不存在 |
| P2-12 表数口径 | **半修** —— `README.md:574`、`docs/项目现状基线.md:358` 已写 47；`Data/README.md:5,6,15,48` 还是"35 张 / 405 字段" |
| P2-18 死代码三处 | **已修** —— `NotificationBell.vue` 已挂进两端顶栏；`utils/permission.js` 与 `directives.js` 按"明确不做"删除 |
| P1 里的 NL2SQL 只读回落（见 `项目调查报告-2026-10-12.md`） | **已修，且是行为变更** —— 缺只读源时直接拒绝执行。因 `09_create_ai_ro_user.sql` 按决策不进部署链，新环境不手工建 `ai_ro` 就等于数据智能体不可用 |

**新增的、只能实跑定论的风险**：`11_knowledge_base.sql:177` 的 `WITH PARSER ngram` 现在进了部署链，脚本报错会让 MySQL 容器的整个 initdb 中断（`mysql:8.0` 内建 ngram，按理能过，但没实测）。

**证据口径更新**：阶段 6 后我实跑过 `mvn -o -B test`（859 例 / 0 失败 / BUILD SUCCESS）和 `npm run build`（exit 0，40.3s）；补完 4 处修复后重跑为 **864 例全绿**（新增 5 条白名单守卫用例）、前端构建再次 exit 0；再补 P1-3 的同类缺陷后为 **867 例 / 88 类全绿**（新增 3 条顺序用例）。新用例做过反向验证：把 `SeaChatWorkflow` 临时换回改前版本跑它，报 `expected <3> but was <4>`（当前这句在 prompt 里出现两次），换回修复版即通过 —— 所以它锁的是症状，不是一行位置。所以"前端能否构建""单测是否绿"已属已核实；仍只有静态推导的是**真实启动日志**与**真实库表结构**，`timeSlots` 那处也只做到"声明补上 + 构建通过"，没有浏览器实测。

---

## 一、先说好消息：上一轮报告里的问题，确实修了 6 处

| 上轮问题 | 现在 |
|---|---|
| 4 个控制器的路由前缀被吞进注释（导致服务起不来） | 已修，`NotificationController.java:18` 等 4 处恢复为独立注解 |
| 前端构建失败（`Assistant.vue:829` 多一个 `}`） | 已修，样式块括号 52/52 平衡 |
| 通知表引用不存在的 `related_id` | 已修，改用 `biz_type` / `biz_id` |
| `news` 表 `update_time` 与真实列名不符 | 已修，统一为 `updated_at` |
| `user.role` 与真实列名不符 | 已修，改为 `u.user_role` |
| 敏感词功能前端在调、后端已删（3 个请求必 404） | 已修，管理页那个 Tab 整块摘除（属回避式修法，但方向合理） |

**但下面这些是新的。** 其中第 1、2 条会让新部署和核心页面直接坏掉，第 3 条是"修一个 bug 顺手引入的另一个 bug"。

---

## 二、P0：新部署会缺表、核心页面会白屏

### 1. 按 README 建出来的新库，会缺 12 张表；AI 相关页面一打开就报错

**你会看到什么**：本地或服务器上按 `Data/README.md` 的场景 A（全新部署）建库之后，进"智能体管理""知识库管理""RAG 日志/评测"这些页面 → 报错或空白；AI 对话回答完了，但服务端写"运行记录"时报错。

**为什么**：本轮新增了两个建表脚本 `Data/02_迁移脚本/10_agent_architecture.sql`（建 5 张 agent 相关表）和 `11_knowledge_base.sql`（建 7 张知识库/RAG 表），但**没有任何地方引用它们**：`docker-compose.yml` 的自动初始化清单只挂到 `14-migrate-08-user-settings.sql`（`:39-51`），全仓搜索这两个文件名，文档、脚本、Dockerfile 里零命中。项目没有 Flyway/Liquibase，所以没有第二条路把它们执行掉。

**怎么修**：给 compose 追加两条挂载（`15-migrate-10-agent.sql`、`16-migrate-11-kb.sql`），并在 `Data/README.md` 的部署步骤里补上。

**附带一个坑**：`11_knowledge_base.sql:177` 用了 `WITH PARSER ngram`。如果 MySQL 没启用 ngram 插件，这个脚本会**整段中断**，即挂上了也可能只建成一半。

### 2. 患者端"预约挂号"走到第 3 步会白屏

**你会看到什么**：选科室 → 选医生 → 选到第 3 步（选时段）时，整块内容消失变白屏；即使不白，时段列表也不会出现。

**为什么**：页面模板和计算逻辑都要读一个叫 `timeSlots` 的数据（`Appointment.vue:139`、`:165`、`:309-310`），但 `data()` 里从来没声明它（`:282-295` 声明了 10 个字段，没有 `timeSlots`）。它只在 `:399` 请求回来后直接赋值 —— Vue 3 里给未声明的字段赋值不会变成响应式数据，读的时候还是空。

**怎么修**：`data()` 里补一行 `timeSlots: []`。

---

## 三、P1：功能看着在，实际不生效或数据是错的

### 3. 每问一句，AI 都把你的问题读两遍；新用户的欢迎语永久消失

**你会看到什么**：用户端聊天里，你问的问题在上下文里出现两次（模型可能重复回应、token 白白翻倍）；第一次使用的新用户再也收不到那句开场引导语。

**为什么**：这是本轮"把用户侧流式对话补上落库"这个修复**自己引入的新问题**。`UserChatController.java:169` 先把用户这句问题存进历史，紧接着 `:173` 才去拼给模型的上下文；而拼装的 `SeaChatWorkflow.buildInitialMessages():266-285` 会（a）先读历史 —— 刚存的那条被读回来，（b）再把当前问题加一遍 → 重复；同时它用"历史是否为空"判断是否新用户（`:266`），刚存了一条之后永远判定为"老用户" → 欢迎语分支再也不进。

**怎么修**：调整顺序为"先拼上下文 → 模型答完 → 再落库"（同文件同步链路的顺序是对的，照它改）。

### 4. 用户端聊天绕过了本轮新做的两项能力

**你会看到什么**：管理端精心配的"每个智能体只能用哪几个工具"在**用户实际聊天时不生效**；"智能体运行记录"页面里看不到用户端的对话过程，只有医生端的几条。

**为什么**：用户端走的是流式入口 `StreamingReActAgent.java:355`，它下发工具用的是全局配置 `buildToolsArray(crmConfig.isReactToolStrict())`，而不是按智能体的白名单；并且整个流式方法里没有运行记录的装配（对比 `BaseReActAgent.java:616` 的 `beginRunRecording` 与 `:626` 的 `finishRunRecording`，流式侧一个都没有）。

**怎么修**：流式重载里补上与同步链路相同的白名单装配 + 运行记录。

### 5. 保存工具白名单一旦失败，那个智能体立刻拿到全部工具（安全语义是"错开方向"）

**你会看到什么**：管理员给某个智能体配工具白名单，只要名单里有一个工具名拼错，保存会提示"未知工具"——**但原来配好的白名单已经被清空了**，该智能体随即变成"不限制、全部工具可用"。

**为什么**：`AgentAdminServiceImpl.java:303` 先执行 `deleteByAgentId(id)` 把旧绑定删掉，然后才开始逐个校验，遇到未知名字在 `:319` 用 `return ApiResult.error(...)` 直接返回。方法上虽然有 `@Transactional`（`:292`），但**正常返回不会触发回滚**（只有抛异常才回滚），删除就被提交了。而读取侧 `:126` 的判定是 `restricted = !bindings.isEmpty()` —— 绑定为空 = 不限制。三步连起来就是"校验失败 → 权限放大"。

**怎么修**：先整体校验、校验全通过再删再插；或者把提前 return 改成抛业务异常让事务回滚。

### 6. 用户端可以搜到医生端专属知识库

**你会看到什么**：给医生用的内部知识库，患者在聊天里问一句就能被检索出来、甚至出现在回答里。

**为什么**：检索工具的 `audience`（受众）和 `kb_ids` 两个参数是**由模型自己填的**（`SearchKbTool.java:109-110`），服务端不拿当前登录身份去校正；更糟的是显式指定 `kb_ids` 时会**跳过受众过滤**（`KbSearchService.java:361` 走 `listByIds`，对应 `KnowledgeBaseMapper.xml:122-130` 里没有 audience 条件）。

**怎么修**：以当前身份/当前 Agent 为准强制改写 audience，并把请求的 `kb_ids` 与该身份可见的库取交集。

### 7. 聊天记录改存 SQLite 之后，有三件事要提前知道

**你会看到什么**：(a) 备份数据库时以为全备了，其实聊天记录不在里面，机器一坏就没了；(b) 将来起第二个实例或容器重建后，历史对话"凭空消失"，因为各存各的文件；(c) 管理端统计对不上账。

**为什么**：本轮把用户侧对话持久化接到了 `SqliteChatHistoryService`（`UserChatController.java:52,169,230`），落的是本地文件而非 MySQL 业务库；`SqliteChatHistoryService.java:91` 的查询键是 `phone_number`，但用户端传进去的其实是账号，与 MySQL 侧 `ai_conversation` 的口径不同，两套数据没法互相核对。compose 里新加的 `backend_crm_data:/app/crm_data` 卷说明作者意识到了持久化问题，但备份/多实例这件事没有任何文档说明。

**怎么修**：定一个"正本"（建议双写 MySQL，或至少把这个卷纳入每日备份），并在部署文档里写清共享卷要求。

### 8. "工具清单一致性"这道护栏，方向装反了

**你会看到什么**：以后再加一个新工具、忘了写进 AI 的提示词，858 个测试照样全绿，而那个工具模型永远调不到 —— 这正是上轮 `query_health_data` 漏掉的同一个坑，号称已闭环，实际没闭。

**为什么**：新增的 `ToolCatalog.java:19-22` 注释声称"注册了但没写进 prompt 会报 missing"，但 `ReactPromptLoader.java:244-266` 只计算 **prompt→registry** 的差集，**registry→prompt** 从未计算；`ReactPromptConsistencyRunner.java:37-48` 即使发现问题也只 `log.warn`，不影响启动。全仓没有任何测试把 `src/main` 里真实的 11 个工具与 `ToolCatalog` 手抄名单做反向比对。`ReactPromptToolConsistencyTest.java:97-108` 是"自己按名单注册假工具、再断言名单和它对得上"，按构造必然通过。

**怎么修**：加一个真扫描目录的测试（取所有 `Tool.getName()` 断言等于清单），并在生产侧补上 `undeclared` 差集。

### 9. "服务能不能启动"这件事，仍然没有任何测试守着

**你会看到什么**：像今天这种"路由注解写坏导致整个服务起不来"的问题，还会再发生，而且测试全绿骗你一次。

**为什么**：仓库里仍然 **0 个 `@SpringBootTest`**。本轮新增的 `MappingAnnotationCommentGuardTest` 只有 1 个用例，判据是"注解出现在同一行 `//` 之后"，能挡住这次的形态，但挡不住：注解被 `/* */` 或 Javadoc 吞掉、类级 `@RequestMapping` 被直接删掉、两个控制器路径撞在一起引发 Ambiguous mapping。

**怎么修**：用 `ApplicationContextRunner` 或 `@WebMvcTest` 真起一次上下文，断言 `RequestMappingHandlerMapping` 里的路由条数与路径集合。

---

## 四、P2：隐患与"看得见但没坏"的问题

| # | 你会看到什么 | 技术定位 |
|---|---|---|
| 10 | 照 `Data/README.md` 手工部署的人会去执行一个**已经不存在的文件** | `Data/README.md:47,49,84,102` 仍把 `07_sensitive_word.sql` 列为必经步骤，该脚本本轮已移出 |
| 11 | 仓库里悄悄多了一个"本该不入库"的文件 | `Data/90_归档/sensitive_word_未落地_20261012.sql` 在 `24072ce` 被强制跟踪，与 `.gitignore:94` 的 `Data/90_归档/` 以及本项目"归档快照不入库"的既定口径冲突 |
| 12 | 文档口径全面落后：表数 35 → 实际 47 | `Data/04_结构基线/数据表结构基线-20261003.md:4` 仍写"35 张表 / 405 字段"，`Data/README.md:5-6,15`、`docs/数据库设计.md:359` 同；项目自己规定的 `python Data/tools/gen_schema_doc.py` 没跑 |
| 13 | "智能体运行记录"里"会话"这一列永远是空 | 写侧 `AgentRunRecorder.java:94-116` 没写 `session_id`，读侧 `AgentRunMapper.xml:78` 按它筛 |
| 14 | 运行/检索日志只增不减，且写失败你完全不知道 | `RetrievalLogRecorder.java:139` 无清理任务；`AgentRunRecorder.java:120-122` 写库失败只 `log.debug` |
| 15 | 并发高时客服可能"卡住不回话" | 委派子智能体时把子任务投回同一个有界线程池并阻塞等待（`CallAgentTool.java:191` + `BaseReActAgent.java:89-97` 的 2/8/队列16/AbortPolicy），父任务正占着线程，队列满即抛拒绝 |
| 16 | AI 报告页点"查看图片"，抽屉里全是裂图；多模态其实没把图片喂给模型 | `AiAnalysis.vue:721` 读 `f.url`，但上传回填处 `:1300-1307` 用的是 `res.data`（后端 `FileController.java:96,103-105` 返回 `{url:"…"}` 对象）→ `src="[object Object]"`；`:1130` 同样把对象数组当地址发出去。`UserProfile.vue:404` 头像同病 |
| 17 | 管理端"审核管理"卡片看不出审的是什么：标题空白、作者恒显示 `U`、举报原因不显示 | `AuditManage.vue:33-45` 渲染 `post.title` / `post.userName`，后端返回的是 `PostReport`（只有 `id/userId/postId/reason/status`） |
| 18 | 三处死代码：写了但用户够不着 | `components/NotificationBell.vue` 全站零引用（通知实际由 `Main.vue:83,317-320` 角标实现）；`utils/permission.js:15,31` 调不存在的接口且无人 import；后端 `/drug-recommend/by-health` 前缀修好了但前端 0 调用 |
| 19 | 以后加工具忘了补种子白名单，该工具静默不可用 | `10_agent_architecture.sql:247-289` 的工具白名单是逐个枚举的（脚本 `:239-243` 自己承认踩过） |
| 20 | 一个不再被写的死字段 | `Notification.java:58` 仍留 `relatedId` |

### 复核中推翻的一条（记下来免得又被提出来）

有一条审查结论说"管理端数据智能体的记忆统计调 `/agent/memory/stats`，后端没有这个端点，面板恒显示 `—`"。**这是误报**：`core/agent/AgentController.java:31` 的类级前缀就是 `/agent`，`:132` 有 `@GetMapping("/memory/stats")`，与 `DataAgentManage.vue:529` 的调用完全对得上，前端取值的双层解包 `res.data.data` 也和 `Result` 包装一致。

---

## 五、按 diff 内容自动判定的提交类型

本轮 3 个提交把太多不同性质的东西混在一起了（`24072ce` 一个提交 171 文件、25088 行，且 message 写的是 `docs+code`）。按实际内容重新归类如下：

### feat（新功能，主体）
1. **多智能体编排一期** —— `core/agent/`（`AgentRegistry`、`AgentOrchestrator`、`AgentRunRecorder`、`Audience`）、`crm/agent/tool/CallAgentTool`、`AgentAdminController`，配套 `agent_config` / `agent_tool_binding` / `agent_handoff` / `agent_run` / `agent_run_step` 建表。
2. **知识库与 RAG 可观测** —— `crm/rag/`（`RRFusion`、`KeywordExtractor`、`DocumentParserRegistry`、`RetrievalMetrics`、`ChunkUtil`、`KbSearchService`、`RetrievalLogRecorder`）、`KnowledgeBaseController`、`RagEvalAdminController`、`RetrievalLogMapper.xml`，配套 7 张 kb/rag 表。
3. **前端特效与两个管理页** —— `components/effects/*` 7 个 + `directives/reveal.js` + `styles/effects.css`，`admin/AgentPlatformManage.vue`、`admin/KnowledgeBaseManage.vue` 及两条路由。

> 建议拆成 `feat(agent)`、`feat(rag)`、`feat(ui-effects)` 三个提交，因为它们各自都能独立回滚。

### fix（缺陷修复）
4. 4 个控制器类级 `@RequestMapping` 前缀恢复（纯修复，前端依赖它，应独立成一个提交）。
5. 只读数据源改造成 Provider（解掉"DataSource bean 让自动配置退避"的启动故障，含 3 个消费方跟改）。
6. `Assistant.vue:829` 多余括号删除。
7. `utils/doctorAuth.js:20` jwt-decode 改默认导入（此前 token 解析恒为 null，所有需登录页被守卫弹回 —— 这条修得很实，方向正确）。
8. 三处 SQL 列名对齐：`NewsMapper` 的 `updated_at`、`NotificationMapper` 去 `related_id`、`UserMapper` 的 `u.user_role`。
9. `MallProductMapper` 价格区间条件与 `queryCount` 补齐。
10. `AuditManage.vue` 摘除敏感词 Tab（**注意**：这是回避式修复 —— 前端不喊 404 了，但"敏感词"这个能力在计划里仍是缺口）。
11. `UserChatController` 用户侧流式对话落库（**但引入了本文第 3 条的新缺陷，严格说不能算完成态的 fix**）。

### refactor（不改行为的重构）
12. `SeaChatWorkflow` / `BaseReActAgent` 的 audience、agentKey、`ToolContext` 透传与方法签名收敛。
13. `router/index.js` 去掉未使用的 `ROLE_DOCTOR`。
14. `ToolCatalog` 从测试里抽成公共类（但见 P1-8，它没起到声称的作用）。

### test（新增测试，本轮 684 → 858 例）
15. 真护栏：`MapperTableConsistencyTest`（列校验部分含 planted-bug 自检，是本轮质量最高的守护）、`Nl2SqlReadOnlyDataSourceWiringTest`（用 `ApplicationContextRunner` 真起容器）、`MallProductPriceFilterTest`、`DocumentParserRegistryTest`、`RRFusionTest`、`RetrievalMetricsTest`。
16. 自证式/需返工：`ReactPromptToolConsistencyTest:97-108`、`ToolWhitelistSeedTest:75`（找不到 SQL 就 `assumeTrue` 整类静默跳过）、`RagPropertiesTest:24-31`（只 `new` 对象测钳位，yml 绑定从未被测）、`DocumentParserRegistryTest:95-99`（手抄 4 个 parser 名单）。
17. `ReadOnlyDataSourceConfigTest`（-78 行）被 `Nl2SqlReadOnlyDataSourceWiringTest` 替换，方向是升级，但丢了两条覆盖：消费方的 `@Resource(name=…)`/`@Qualifier` 写错没人测、"注入侧必须 `required=false`"无替代者。

### docs
18. `f6810a8`（新增功能文档 + README 更新日志 + 清死链）、`dbe8db4`（测试数 684→858）、`Data/README.md` 与结构基线的字符集说明订正。
> **未完成的 docs**：表数 35→47 的口径没同步（P2-12），README 部署步骤仍指向已删脚本（P2-10）。

### chore
19. `docker-compose.yml` 新增 `backend_crm_data` 卷；`pom.xml` 依赖/插件同步；`07_sensitive_word.sql` 移出并归档。

---

## 六、建议动手顺序

1. **~~今天就该补的 4 行~~（已于 2026-10-20 补完并提交）**：`Appointment.vue` 的 `timeSlots: []`（`7925925`）、compose 追加两条 initdb 挂载（阶段 6 的 `d36048c`）、`UserChatController` 落库与拼装换序（`6c97e53`）、`AgentAdminServiceImpl` 先校验后删（`ad0ff33`）。
   **~~补的时候新发现一处同类缺陷，未修~~（已于 2026-10-20 点头后修完，`bc9c30b`）**：同步链路 `SeaChatWorkflow.processChat()` 原本先存消息（改前 `:108`）再读历史（改前 `:128`），当前问题进提示词两遍；已按「先读后存」改，并顺带把 `/crm/chat/stream` 那处同病灶一起换序。
2. **补真护栏**：registry→prompt 反向差集测试（P1-8）+ 一个能起上下文的启动类测试（P1-9）。这两条不做，同类问题还会再来。
3. **口径回写**：跑 `gen_schema_doc.py`，同步 4 处表数；修 `Data/README.md` 的部署步骤；决定 `90_归档` 到底入不入库（P2-10/11/12）。
4. **产品决策项**（需要你先定方向，不适合直接改代码）：聊天历史正本放哪（P1-7）、知识库受众隔离策略（P1-6）、敏感词是补后端还是正式取消（见 §五 fix 第 10 条）。

---

## 七、2026-10-24 二轮代码级扫描（本文 §〇 之后新发现的那些）

上面那份台账收口后，我又扫了一轮**运行期代码**（先让子代理按「出错时往哪个方向降级」
筛候选，再逐条打开源码复核，共 11 个候选，推翻或降级了其中若干）。这一轮里
6 条当场改掉，7 条只报不动 —— 它们的正确改法需要你先定方向，或者会改变对外可见行为。
**同日你逐条定了方向，7 条已全部修完（含 §7.2 第 3 条的二次升级），补修记录见 §7.3；
下面 §7.2 的原文保留，只把标题划掉并标上你的选择，便于对照「当初为什么不敢擅动」。**

### 7.1 本轮已修（6 条，`bc4a45a..be9b995`）

| 你会看到什么 | 定位 | 改法 / 提交 |
|---|---|---|
| 库一抖动，老用户的上下文被清空，AI 还像第一次见面那样打招呼 | `crm/sqlite/SqliteChatHistoryService.java:90-106`（`isNewUser` 的 catch 原先 `return true`） | 失败按「老用户」降级；`bc4a45a` |
| 逐条删完 AI 会话后，被删的那批（标题+全部消息）整批复活 | `service/impl/AiChatCacheServiceImpl.java:234-253`（只删 MySQL 与缓存，不删磁盘 JSON；而 `getConversationList:137-146` 在 MySQL 空时回落到磁盘） | 补磁盘副本清理，属主在删除之前取；`b5286c1` |
| 健康偏好「保存成功」，重启后其实一条都没留下 | `core/workspace/AgentWorkspace.java:59-70`（吞 `IOException` 返回 void）→ `core/agent/AgentMemoryService.java:119`、`core/agent/AgentController.java:123-126` | 逐级返回 boolean，写失败不刷缓存、接口报错；`08ec6ab` |
| 通知铃某天起不再更新，日志只有一行 error | `websocket/WebSocketServer.java`（心跳/单发/广播三处并发调 `getBasicRemote()`，JSR-356 明确非线程安全） | 收敛为按 session 串行的唯一发送出口；`139c595` |
| 提问为空时流式接口「像是卡住了」：200 + 事件流头 + 0 字节 | `crm/controller/CrmChatController.java:73-90`、`controller/UserChatController.java:131-148`（先设 SSE 头再校验，不合格 `return`） | 校验前置，回 400 JSON；`bd258f2` |
| 新工具上线忘了写进提示词 → 模型永远调不到，而没有任何一处会报 | `crm/agent/ReactPromptLoader.java:245-275`（原先只算 prompt→registry 单向差集） | 补 `unmentioned` 反向差集 + 启动横幅 + T7.10 活护栏；`be9b995`（台账 P1-8 收口） |

### 7.2 当时只报不动的 7 条 —— 你已逐条点头，**7 条全部修完**（修法见 §7.3）

原文保留，便于对照「当初为什么不敢擅动」。你的选择依次是：① 全部拒绝；② 读不到就不发；
③ 把假功能做成真功能、默认未校验；④ 给管理员看板加功能；⑤ 统一 401；⑥ 槽位回填接上去；
⑦ 区分「读失败」与「文件不存在」。

1. ~~**账号被封还能照常使用（数据库报错的那一刻）**~~ → **已修，你选的是「改成拒绝」，见 §7.3**
   —— `core/auth/AuthSessionManager.java:151-155`：
   `queryLockedFromDb` 查库失败时 `return false`（=「没被锁定」）。它是 Redis 失败后的最后一道，
   所以 MySQL 一抖，被停用的账号（JWT 还没过期）照样畅通。
   **为什么不直接改**：反过来 fail-closed 会让数据库抖动的几分钟里**所有用户都被锁在外面**，
   在健康管理系统里这个代价未必更小。三个选项：① 改成拒绝（最安全，最伤可用性）；
   ② 保留放行但把「查不出状态」计数告警出去；③ 给锁定状态加一个短的强制回源窗口。
   **需要你选一个。**
2. ~~**通知开关读不到就按「开启」发**~~ → **已修，你选的是「读不到就不发」，见 §7.3**
   —— `service/impl/NotificationPreferenceServiceImpl.java:35-36` 与 `:60`：
   明确写了注释、也论证过（通知默认想收到），但效果是：用户关掉「预约提醒」后，一旦读开关失败
   （例如 `08_*.sql` 迁移没跑）就会重新开始被打扰。要不要改成「读不到就不发」，属产品取向。
3. ~~**NL2SQL 的「语义一致」绿灯可能是假的**~~ → **已修两步：先止住假绿灯 `26fc054`，再按你选的「做成真功能」补追问与复核，见 §7.3**
   —— `core/nl2sql/DataAgentPipeline.java:422`：
   模型返回的不是 JSON、或压根没返回 `passed` 字段时，判定按「通过」处理，界面显示
   「SQL 与问题语义一致」。安全守卫（`:443`）和租户改写（`:462`）是真 fail-closed，我复核过，
   所以这只是质量步骤虚绿，不是越权。改法：判空视为不通过并标注「未校验」。
4. ~~**`/dashboard/stats` 任何登录用户都能读平台用户总数**~~ → **已修，你选的是「给管理员看板加功能」，见 §7.3**
   —— `controller/DashboardController.java:25-26`
   只有 `@Protector`（=「登录即可」），没写 `role`。敏感度低（只有两个计数），
   且我在前端**找不到任何调用点** —— 它要么是死接口（该删），要么本就打算给管理员看板（该加 `role="管理员"`）。
5. ~~**鉴权失败回 HTTP 200**~~ → **已修，你选的是「统一 401」（越权另归 403），见 §7.3**
   —— `aop/ProtectorAspect.java:44/49/59/71`：token 过期时返回
   `ApiResult.error("身份认证失败…")` 但状态码 200，而 `JwtInterceptor:173` 走的是真 401。
   前端按 401 跳转登录的拦截器对前者不触发 → 用户停在原页面看到「请先登录」而不跳转。
   **要统一成 401 就得同时核对前端的响应拦截器**，改动跨前后端，没点头我不动。
6. ~~**`saveUserPreferences`（批量写偏好）注释说「供 SlotFilling 使用」，实际生产代码里没人调**~~ → **已修，你选的是「把槽位回填接上去」，见 §7.3** ——
   `core/agent/AgentMemoryService.java:133`（唯一调用方在测试里）。属「文档写着有这能力、链路没接」，
   与 §五 那几条同类。要么把槽位回填接上去，要么删掉；删掉是行为收敛，我不擅自做。
7. ~~**文件读失败一律回空**~~ → **已修，你选的就是「区分这两种情况分别报」，见 §7.3**
   —— `core/workspace/AgentWorkspace.java:41-49/75-85`：
   `createDirectories` 失败只打日志继续往下走，`readFromFile` 把「读失败」和「文件不存在」
   都返回 `null`。第 1 条修完之后写侧已经能如实报失败，读侧仍然分不出这两种情况；
   影响是磁盘故障时偏好会「看起来像从没设置过」。

### 7.3 你点头后补修的全部 7 条（`5dd92a7` … `c40d25d`）

| 你会看到什么 | 定位 | 改法 / 提交 |
|---|---|---|
| 数据库报错的那几分钟，被停用的账号还能照常查数据、发通知 | `core/auth/AuthSessionManager.java:79-121`（原 `queryLockedFromDb` 的 catch `return false`） | 按你选的**拒绝（fail-closed）**：状态改三态 `LOCKED/NORMAL/UNKNOWN`，`UNKNOWN` 一律当已锁定拒绝；`5dd92a7` |
| 同上，但更隐蔽的一侧：一次查库失败被缓存成 60 秒的「正常」 | `AuthSessionManager.java:88-89`（改前把降级出来的 `false` 直接 `setAccountState` 写进 Redis） | `UNKNOWN` **不写缓存**，下个请求重新回源；否则抖动被放大成整分钟，且刚禁用的账号还要等 TTL 到期才生效；`5dd92a7` |
| 数据问答的报告写着「SQL 与问题语义一致」，其实模型压根没给判定 | `core/nl2sql/DataAgentPipeline.java:422`（`checkJson == null \|\| !Boolean.FALSE.equals(...)`） | 判定为读不出时改发 `SKIPPED` 事件「未完成语义校验：本报告结论未经语义核对」，`payload` 带 `checked=false`；仍照常执行（这是质量步骤，不是安全门）；`26fc054` |
| 补上的坑：`{"passed":"maybe"}` 这种含糊回复 | `DataAgentPipeline.java` 新增 `readSemanticVerdict` | 不能用 `JSONObject.getBoolean` —— 实测它会把字符串强转出布尔值，绿灯照样假；改成只认 `Boolean` / `0-1` / `true-false-yes-no`，其它一律算未判定；`26fc054` |
| 上一行的收尾太早：模型偶尔只是不守格式，问一次就要不回判定就等于这个环节没做 | `DataAgentPipeline.java:846-862`（新 `semanticCheck`） | 按你选的「做成真功能」：第一遍读不出就**带上输出格式要求追问一次**，两轮都要不回才认「未校验」，措辞相应改成「模型连续两轮均未返回判定」；`payload` 加 `attempts`；`c40d25d` |
| 更实在的一个洞：校验判「不通过」→ 重生成 → 执行的是**新版** SQL，可绿灯是给旧版点的，新版从头到尾没人核对过 | `DataAgentPipeline.java:417-441`（第 6 步）+ `emitSemanticCheck` | 重生成后对新版**再核一次**（`afterRegeneration=true`，只核一轮不打转）；复核读不出同样照实标未校验，不通过的措辞改「按现状执行」；追问与复核都不改安全守卫、租户改写那两道真 fail-closed 的门；`c40d25d` |
| 用户明明关掉了「预约提醒」，却在大库抖动 / `08_*.sql` 没跑期间重新开始被打扰 | `service/impl/NotificationPreferenceServiceImpl.java:35-36/60`（`allows()` 的 catch `return true`） | 按你选的**读不到就不发**：查询失败不投递并把日志升到 error（这条持续命中=全站静默停发，留 warn 看不见）；`NULL` 仍按开启解释 —— 「从没设置过」不等于「读不到」，一律当关闭会让从没进过设置页的人静默收不到服药/复诊提醒；`10a93c7` |
| 健康偏好读不出来时，下一次保存会把盘上既有的内容当成「本来就是空的」覆盖掉 | `core/workspace/AgentWorkspace.java:41-49/75-85` → `core/agent/AgentMemoryService.java` | 按你选的**分三种情况报**：`ReadStatus{FOUND,MISSING,IO_FAILED,PARSE_FAILED}` + `ReadOutcome`，人话分别是「文件不存在 / 磁盘读取失败 / 文件内容无法解析」；只有 `MISSING` 才当空档案起步，`IO_FAILED`/`PARSE_FAILED` 时两个 save 都**放弃本次保存**并 warn，接口如实报错；`e6771eb` |
| AI 问了身高体重年龄，下一轮又问一遍 —— 采集到的槽位只进内存对象，没进 Agent 记忆 | `core/agent/AgentMemoryService.java:133`（`saveUserPreferences` 生产零调用）→ `core/harness/HarnessEngine.java:persistSlots` | 按你选的**接上去**：只把真正写进档案的槽位（`applied`）以 `profile.*` 镜像进 Agent 记忆 —— 全量镜像会把被否掉的口误（如 175 覆盖已确认的 180）写进去，而记忆是覆盖写的；镜像失败只 warn，不影响本轮回答（档案已落库）；`bc0a152` |
| token 过期后停在原页面看到「请先登录」而不跳登录；越权访问也回 200，前端按 401 的拦截器不触发 | `aop/ProtectorAspect.java:44/49/59/71`（五处拒绝点全部 `ApiResult.error` + HTTP 200） | 按你选的**统一 401**，并把两类问题分开：身份类（无 token/过期/解不开/role 读不出）→ 真 401，前端照旧清 token 跳登录；**越权类（role 不符）→ 403**，绝不清 token（清了等于把管理员踢成登出）；新增 `reject()` 统一写状态码，注解读不出时按未认证拒绝并 warn（不再静默放行）；`a37f4d6` |
| 同上，前端侧：403 落到通用分支会弹「请求失败」并可能洗掉登录态 | `前端/personal-heath-view/src/utils/request.js`（响应拦截器） | 补 403 分支：只 `ElMessage.warning(msg \|\| "无权访问该功能")`，不动 token、不跳转；`a37f4d6` |
| 管理员看板只有四个存量计数，`/dashboard/stats` 这个接口在前端找不到任何调用点，谁都能读 | `controller/DashboardController.java:25-26`（只有 `@Protector`）、`mapper/AppointmentMapper.countByStatus()`（写好但没人调） | 按你选的**给管理员看板加功能**：接口收归 `@Protector(role="管理员")`，返回体补 `appointment.total/pending`（顺带给那个死方法一个调用方）；`views/admin/Dashboard.vue` 最近预约卡片表头显示「待确认 N 单」，取不到数就不渲染；`4efa874` |

守卫测试（新增 25 例 / 2 个测试类）：`NotificationPreferenceServiceImplTest` 改 1 例断言 + 新增 1 例、
`AgentWorkspaceReadOutcomeTest` 5 例（真实文件系统）+ `AgentMemoryServiceTest` 4 例回归、
`HarnessEngineTest` +3 例（28 例）、`ProtectorAspectTest` +4 例（16 例）、
`DashboardControllerStatsTest` 6 例、`DataAgentPipelineTest` 语义校验段 8 例（26 例）、
另有 `AuthSessionLockFailClosedTest` 8 例（属 `5dd92a7`）。

反向验证逐条记（换回改前代码，确认对应用例报红）—— 共 18 例：
`10a93c7` 2 例；`bc0a152` 1 例（`rejectedSlotMustNotReachMemory`）；`a37f4d6` 4 例
（`expected <401/403> but was <200>`）；`4efa874` 4 例（`expected: <管理员> but was: <>` 1 例 +
待确认桶错位 3 例）；`c40d25d` 7 例（去掉追问 → 6 例报红，含 `expected: <SUCCESS> but was: <SKIPPED>`；
去掉复核 → 2 例 `expected: <2> but was: <1>`；两组重叠 1 例）。
**`e6771eb` 只补了用例、没做反向验证**，写在这里是为了不含糊过去。

`mvn -o -B test` 现值 = **918 例 / 93 个测试类全绿**（2026-10-25 实测；§7 起于 893/91）。
仍要提醒：这套用例里没有一个 `@SpringBootTest`，全绿只证明方法级逻辑，**不证明应用能启动**；
`Dashboard.vue` 只做了 `vue-cli-service build` 通过 + 脚本段语法检查，
本机连不上 MySQL/后端，未做浏览器实测。

### 7.4 §七 收口后仍开着的两条

1. **AI 每轮都问「既往病史」，用户的回答既抽不出来也不落库** ——
   `core/harness/HarnessEngine.java:55` 把 `chronicDiseases` 列进 `REQUIRED_SLOTS`，
   但 `:226-239` 的 `EXTRACT_RULES` 只有身高/体重/年龄三条正则（性别与病史都不在抽取范围内），
   `persistSlots` 也只写性别/年龄/身高/体重这四样；全项目唯一写 `chronicDiseases` 的地方是档案表单
   `service/impl/PatientProfileServiceImpl.java:118`。
   后果：没在表单里填过病史的人，`checkDataCompleteness` 永远判定它缺失 →
   追问话术每轮注入 → 用户答了「有高血压，长期吃络活喜」，系统照旧不懂，下一轮再问一遍。
   **要改得先定方向**：① 把病史也纳入抽取并落库（需归一化成 JSON 数组 —— `HarnessEngine.java:94`
   注明迁移脚本把「无慢性病」写成 `[]`，判「没病」和「没答」得分开）；
   ② 把这条从 `REQUIRED_SLOTS` 摘掉，对话里不再追问，只在档案表单填。
   我倾向 ②（对话里抽自由文本属于 LLM 的活，而本类的注释明确写着「不做 LLM 调用」），但这是产品取向，等你说。
2. **被禁用与数据库抖动共用一句提示** —— 拒绝时接口回的是 401 +「账号已被禁用，请联系管理员」，
   数据库抖动期间正常用户也会看到这句（措辞不准确，但状态码与前端跳转是对的）。
   要改成「系统繁忙，请稍后重试」就得给拦截器加一条区分 `UNKNOWN` 的分支，
   那是对外可见行为变化，等你说。
