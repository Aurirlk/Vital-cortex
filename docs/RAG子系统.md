# RAG 检索增强子系统

> 版本：v5.1（2026-07-31）
> 代码位置：`后端/personal-health-api/src/main/java/cn/kmbeast/crm/rag/`、`.../crm/vectordb/`、`.../service/impl/RAGEvaluationServiceImpl.java`
> 配套文档：AI 智能体架构见 [`智能体架构.md`](./智能体架构.md)

---

![双路 RAG 检索与知识图谱](assets/03-rag-knowledge-graph.png)

## 1. 总览

采用**混合检索**：本地向量库（余弦相似度）+ MySQL LIKE 关键词检索，经 **RRF（Reciprocal Rank Fusion, k=60）** 融合，回答带引用溯源。整体包含三大模块：

```
知识入库（ingestion）          在线检索（retrieval）         质量评测（evaluation）
文章 → 分块 → 嵌入 → 入库      向量 + LIKE 双路 → RRF 融合    真实 RAGAS 指标
```

---

## 2. Ingestion 管线（KnowledgeIngestionService）

文章 CRUD 自动触发：发布→灌数、删除→清块，并提供 `rebuildAll()` 全量重建。

流程 `ingestArticle(articleId, title, content, tagName)`：

1. **分块** `ChunkUtil.split()`：按段落→句子切分，`CHUNK_SIZE = 200`、`CHUNK_OVERLAP = 50`（字符），保留语义边界；
2. **嵌入** `EmbeddingService`：LRU 缓存 + 批量顺序调用（避免并发打爆嵌入服务）；
3. **入库** `LocalVectorStoreImpl`：写入 `COLLECTION = "health_knowledge"`（本地文件向量库，读写锁 + compact 截断修复）。

> `deleteArticle()` 清理旧块；`rebuildAll()` 用于索引重建 / 嵌入模型切换。

---

## 3. 检索（HybridRetriever）

`HybridRetriever` 同时跑两路：

- **向量路**：`LocalVectorStoreImpl` 余弦相似度 TopN；
- **关键词路**：`NewsMapper` MySQL LIKE（标题优先）；
- **融合**：`RRF_K = 60` 重排，输出 `RetrievedDoc { articleId, title, tag, content, score, source }`，其中 `source ∈ {vector, keyword, both}` 标注命中来源；
- **缓存**：`ConcurrentHashMap` 缓存避免重复查询。

---

## 4. 向量库与嵌入

| 组件 | 位置 | 说明 |
|------|------|------|
| `LocalVectorStoreImpl` | `crm/vectordb` | 本地文件向量库、余弦相似度、读写锁、compact 截断修复 |
| `EmbeddingService` | `crm/vectordb` | LRU 缓存 + 批量顺序；配置 `EMBEDDING_API_URL`（必填）、`EMBEDDING_MODEL`（默认 text-embedding-3-small） |

---

## 5. 评测（RAGEvaluationServiceImpl）

真实 RAGAS 口径（替代原模拟数据）。`runEvaluation()` 对黄金问答集计算三项指标：

| 指标 | key | 说明 / 阈值 |
|------|-----|-------------|
| 上下文精确度 | `contextPrecision` | 检索命中是否精准 |
| 忠实度 | `faithfulness`（阈值 80） | 检索上下文对标准答案要点的覆盖比例 |
| 答案相关性 | `answerRelevance` | 回答与问题相关程度 |

底层 `coverage()`（上下文覆盖 gold answer 要点比例）、`questionRelevance()`（上下文与问题相关度）。管理端「RAG 监控」页展示评测结果。

---

## 6. 引用溯源

回答末尾附相关文章推荐，链接到 `search_knowledge` 命中的 `articleId`，用户可回溯到原文。

---

## 7. 配置与限制

- **必填**：`EMBEDDING_API_URL`（DeepSeek 无嵌入接口，需 OpenAI 兼容服务或本地服务）；
- **限制**：本地文件向量库为全量扫描，数据量 **>10 万块** 时建议迁移 pgvector / Milvus + HNSW（见根 `../README.md`「已知缺陷与限制清单」）；
- **知识图谱**：Neo4j 消费端代码 + 图谱构建管线均已就绪。
  - **2026-10-07 状态**：图谱数据已构建（**248 节点 / 485 边**），`/graph/*` 端点齐全，已接入双路召回；Neo4j 长停时自动降级为**本地三元组库**（`LocalTripleStore`），`/graph/stats` 返回 `DEGRADED` + warning，导入不失败。
  - ⚠️ **历史更正（2026-10-06 判定，已于阶段 0-B 修复）**：当时判定为「代码就绪 + 数据为零 + 未接入」——`Neo4jClient` 只有 `readTransaction` 无法写入、无 schema 文件、`neo4j_import.sh` 调用的 5 个 `/graph/import*` 端点**全部不存在**、Neo4j 未启动时静默降级无告警。
  - 详见 `docs/项目现状基线.md` §五 与 `docs/数据资产与AI能力整改方案.md` 阶段 0-B。

---

## 8. 上下文压缩 / 注意力 / 记忆 / 缓存：代码级实测（2026-10-07）

这一节回答「项目有没有做上下文压缩、注意力机制、记忆、缓存管理」。**全部按代码判定，不看文档自述**。

### 8.1 上下文压缩 —— ⚠️ 全链路无摘要式压缩

| 链路 | 机制 | 判定 |
|------|------|------|
| **数据智能体** | `DataAgentQaService` 三道限流：`SESSION_MSG_LIMIT=200`（回看）、`MULTI_TURN_MSG_LIMIT=6`（送 prompt）、`MULTI_TURN_MAX_CHARS=1500`（字符从**尾部**保留）。SQL 层 `ORDER BY id DESC LIMIT ?` 子查询先倒序再正序 | ✅ **唯一真做了截断的** |
| **AI 问诊** | `AiServiceImpl:901` `for (int i = 0; i < history.size(); i++)` —— **无 limit、无字符截断**，全量历史进 prompt。超预算时 `TokenBudgetManager:82` **抛异常**让用户开新会话 | ❌ 不压缩，靠抛错 |
| **ReAct Agent** | 无历史压缩。`ReActAgent:136 summarizeFinalAnswer()` 是「轮次耗尽后强制收尾」，内部 `new ArrayList<>(messages)` **全量拷贝未压缩** | ❌ 不压缩 |
| **RAG 检索结果** | `AiServiceImpl:105` `RAG_ARTICLE_LIMIT=6` + `RAG_CONTENT_MAX_LENGTH=300`；`extractRelevantChunk` 按句子分块提取关键词相关片段 —— **全仓唯一像样的语义级压缩**，但只作用于单篇文档内部 | ✅ 有 |

**入库分块**（`ChunkUtil.java:16-18`）：`CHUNK_SIZE=200` / `CHUNK_OVERLAP=50`，先按段落 → 再按中文句读（`。！？；\n;.`）→ 贪心累积，重叠取尾部 50 字。

**⚠️ 两个「有字段无消费」**：
- `AiPromptConfig.contextRounds`（`:34`，后台可配 0~50，默认 10）→ **业务层零读取**
- `AiConfig.maxHistoryRounds`（`:159`，后台可配 + 持久化）→ `AiServiceImpl` **从不读**

管理员在后台把 `contextRounds` 调成 5，实际对话历史一条不少。

**⚠️ `max_tokens` 不一致**：`AiServiceImpl:220,423` 有（默认 4096）；但
`Nl2SqlLlmClient:66-70`、`BaseReActAgent:467-472`、`StreamingReActAgent:163` **都没设**，
数据智能体的报告生成这种长输出任务无法控制长度。

### 8.2 注意力机制 —— ⚠️ 代码里查无此词，但有三种语义等价物

`grep -rn "attention|注意力"` 在 `.java` 里**零命中**（全是「体重 weight」误匹配）。实际靠三样东西实现：

**① ReAct：LLM 自主决策（`tool_choice=auto`）**
`ReActAgent:25-110` 循环：调 LLM → `hasToolCalls()` 则并行执行工具 → 无则结束。
`ToolRegistry:37-52 buildToolsArray()` **全量注入全部工具描述，无场景筛选、无数量上限、无描述截断**。
规则层有软路由引导：`IntentRouter`（5 意图，`IntentRouter:197`）只做**倾向推断**不命令模型；
`BaseReActAgent:124-262` 用 `roleHint`/`guidancePrompt`/`intentHint` 三个 ThreadLocal 追加提示。

> ⚠️ 值得表扬的细节：`BaseReActAgent:120-123` 注释记录过一次真实 bug —— 该类
> `@Service` 单例，若 `roleHint` 用实例字段会导致 A 用户的角色提示串到 B 用户。现修为
> ThreadLocal + `roleHint.get(); roleHint.remove();` **读一次即消费** + finally 兜底清理。

**② RAG：RRF 融合（实现完整，`crm/rag/HybridRetriever.java`）**

| 参数 | 值 | 位置 |
|------|-----|------|
| RRF 常数 k | **60** | `:27` |
| 每路召回量 | `topK * 2` = **12** | `:78` 向量路 / `:102` 关键词路 |
| 融合公式 | `score = 1.0 / (RRF_K + rank)`，两路命中求和，`source="both"` | `:92,112,114-117` |
| 最终截断 | 排序后 `subList(0, topK)` = **top 6** | `:127-131` |
| rerank | ❌ **无**（RRF 分数直接排序，无 cross-encoder） | — |

⚠️ **检索缓存有缺陷**（`:36, :133-137`）：键 = `query::keywords::topK`，容量 200 但淘汰策略是
**`cache.clear()` 整体清空而非 LRU**，**无 TTL、无失效** —— 文章更新后旧 query 仍命中过期结果，
只能靠超过 200 时一次性丢掉全部键。

**③ 数据智能体：LLM 从全表选 + Evidence 术语注入**
`DataAgentPipeline:216-228` 把**全部表结构**拼进 prompt 让 LLM 返回 JSON 数组（无向量召回、无 BM25）。
`selectTables` 解析失败时 `:567-569` **回退为全库所有表**。
Evidence（`Nl2SqlEvidenceService`，实测 918 字符 / 12 指标）只注入**SQL 生成**与**错误修复**两处，
**刻意不注入**选表与语义校验 —— 那两处不需要术语，注入会让 token 翻倍。

**❌ `core/rag/` 三个类是自我标注的死代码**：
`RAGManager.java:16` / `HybridSearcher.java:16` / `Reranker.java:15` 都写着
`@deprecated RAG-12: dead code - zero references in the codebase, not wired into business.`
它们仍带 `@Component`，**Spring 仍会实例化**，容易被误认为活跃能力。
另外 `Reranker.rerank()` 用 `document.contains(word)` 计数，**中文下 `split("\\s+")` 只得到整句，
匹配几乎恒为 0/1**，即使接线也基本无效。

### 8.3 Memory 记忆 —— ⚠️ 有真实现，但接线残缺

| 项 | 定性 | 判定 |
|---|------|------|
| `core/agent/AgentMemoryService` | **长期记忆（跨会话）**，落盘 `cache/agent_workspace/memory/user_{id}/preferences.json` | ⚠️ **未接 prompt** |
| `core/workspace/AgentWorkspace` | 工作空间（画像缓存 + 文件存储） | ⚠️ 部分死代码 |
| `LocalThreadHolder` | **身份 ThreadLocal**，不是记忆（只存 userId + userRole，生命周期 = 单次请求） | ✅ 不计入 |
| `roleHint` / `messageCache` / `userProfileCache` | 工作记忆（会话内） | ✅ / ⚠️ 无界 |

`AgentMemoryService` 治理质量在项目中属上乘：`:38` `MAX_CACHED_USERS=1000` 有界 LRU、
`:149` `@Scheduled` 30 分钟定时清理、返回 `Collections.unmodifiableMap` 不可变副本、写时清缓存。

**🔴 但全仓仅 3 处调用，全在 REST 层**：`AgentController:72,85,96`。
`ReActAgent` / `BaseReActAgent` / `DataAgentPipeline` / `AiServiceImpl` **无一读取**
（实测 `grep AgentMemoryService crm/ core/nl2sql/` 零命中）。
→ **「用户偏好记忆」存在且可通过 API 读写，但永远不会进入 LLM prompt，对用户不可见。**

**实库无任何 memory / long_term / preference / persona 表**。跨会话记忆全靠
`cache/agent_workspace/` 下的 JSON 文件（`AgentWorkspace:43 WORKSPACE_ROOT`，**硬编码，不受 `CacheConfig` 管辖**）。

⚠️ `AgentWorkspace.userProfileCache:46` 与 `agentStates:49` 是 `ConcurrentHashMap`，
**无上限、无 TTL、无定时清理** —— 与 `AgentMemoryService` 注释里批评过的
「AI 对话缓存必然 OOM」是同类隐患，此处未修。
且 `getUserProfile:83` 直接 `return userProfileCache.computeIfAbsent(...)`，
**返回可变 JSONObject**，调用方可改动缓存内容。

前端 localStorage 记忆：`Assistant.vue:328` 存**完整会话内容**（非仅 id），
**无容量上限、无过期清理**，且是**明文存储健康对话**（XSS 可直接读取）。

### 8.4 缓存管理体系 —— ⚠️ 内存层治理完善，磁盘层零治理

| 层 | 键 | 容量 | TTL | 清理 | 判定 |
|----|----|------|-----|------|------|
| `DataAgentConfigHolder` | `system_config` 的 `data_agent` 分组 key | 8 个（`ConcurrentHashMap` 天然有界） | **10s** | TTL 到期整体 `reload()`；`update()` 落库后立即 reload ✅ | ✅ 但无击穿保护（`loadedAt` 非原子） |
| `AiChatCacheServiceImpl` | `conversationId` | **500 会话 / 100 消息** | 单条无 TTL | **10 分钟** `@Scheduled`，LRU 按 `lastMessageTime` | ✅ 治理最完整，三级回源 MySQL→JSON |
| `AgentMemoryService` | `userId` | **1000** | 30 分钟 | `@Scheduled` 压回 80% | ✅ |
| `EmbeddingService` | 文本 | 有界 | ❌ | `LinkedHashMap(accessOrder=true)` 真 LRU | ✅ 已从「满了就永久停止缓存」修复 |
| `HybridRetriever` | `query::keywords::topK` | 200 | ❌ | **`clear()` 整体清空** | ⚠️ 非 LRU、无 TTL、无失效 |
| `LocalVectorStoreImpl` | collection | ❌ 无界 | ❌ | 仅**手动** `POST /vector-db/compact` | ⚠️ 向量全常驻内存 |
| `LocalTripleStore` | 三元组 | ❌ 无界 | — | — | ⚠️ 且 `cache/knowledge_graph/` **实测为空目录**，真实图谱在 Neo4j |
| `chat_history/` 磁盘 JSON | `{userId}/conversation_{id}.json` | ❌ **无上限** | ❌ **无 TTL** | 🔴 **完全没有清理** | 🔴 见下 |

**🔴 磁盘层两个真实缺陷**

1. **`chat_history/` 无任何清理机制**（`HistoryStorageServiceImpl` 共 6 个 public 方法，
   **零 `@Scheduled`**）。用户不删会话 → JSON 永久累积（实测已 32 文件 176K）。
   且 `AiChatCacheServiceImpl:184` **每次 `addMessage` 全量重写整个会话文件** → 大对话是 O(n²) 写放大。

2. **批量删除产生磁盘孤儿**：`AiChatCacheServiceImpl:244-253 batchDeleteConversations`
   只删 MySQL + 内存缓存，**不删磁盘 JSON**（`HistoryStorageService:210 deleteConversation` 才会删）。
   → 批量删会话后，`chat_history/` 留下永不回收的孤儿文件。

**Redis 实际只用 3 个键**（`AuthSessionManager:31-35`，全在认证）：

| 键 | 用途 | TTL | 降级 |
|----|------|-----|------|
| `auth:user:{id}:ver` | 会话版本号（登出/改密递增，使旧 JWT 全失效） | **无 TTL**（设计必需） | ❌ 异常返回 0 → **跳出版本校验** |
| `auth:user:{id}:state` | 账号锁定 | 60s | ✅ 降级查库 |
| `auth:user:{id}:vip` | VIP 标记（供上下文窗口分级） | 60s | ✅ 降级查库 |

⚠️ **三处需注意**：
- `RedisConfig:25-26` 注释声称「热点查询通过 `@Cacheable` 注解接入」——**全仓零 `@Cacheable`**，
  `RedisCacheManager` bean 建了却空转
- 击穿保护 ❌ 无：`isAccountLocked` / `isVip` 都是 `get → miss → 查库 → set`，无分布式锁
- `RedisHealthIndicator` **类不存在**（实际只有 `Neo4jGraphHealthIndicator`）

**多实例风险**：所有内存缓存（`AiChatCacheServiceImpl` / `HybridRetriever` /
`AgentMemoryService` / `LocalVectorStoreImpl`）都是 JVM 内存，docker-compose 若多副本，
用户 A 在实例 1 的检索结果实例 2 读不到。`AgentMemoryService` 靠落盘兜底，其余无。

**路径分裂**（三个存储根互不管辖）：
`CacheConfig` 的 `cache.root=./cache` 只管得到 `./cache/*`；
而 `HistoryStorageServiceImpl:33` 用 `chat_history`、`AgentWorkspace:43` 用 `./cache/agent_workspace`
是硬编码，`AiConfig:161` 声明的 `./vector_cache` 会 `mkdirs()` 建目录但**没有任何代码往里写**（实测 0 文件）。

### 8.5 运行时数据目录实测（2026-10-07）

| 目录 | 文件数 | 说明 |
|------|-------|------|
| `chat_history/` | 32 | ✅ 有代码写；🔴 无清理 |
| `ai_data/` | 18 | ✅ 有代码写（`DataExportServiceImpl` / `SearchDrugTool`） |
| `chat_cache/history_speak/` | 7 | ⚠️ 语音 Web Speech 降级路径；**无代码引用**，靠 `ai.chat-history-dir` 配置项写入 |
| `crm_data/` | 1 | ✅ `CrmConfig` 的 SQLite 默认路径 |
| `cache/crm/vector_store/health_knowledge/` | 4 | ✅ 向量库落盘 |
| `vector_cache/` | **0** | ⚠️ 建了目录但没人写 |
| `chat_backup/` | **0** | ⚠️ 空目录；README 曾误写此处（已修正为 `chat_history/`） |
| `cache/knowledge_graph/` | **0** | ⚠️ `LocalTripleStore` 未落盘，真实图谱在 Neo4j |

### 8.6 `LocalVectorStoreImpl` 一个值得表扬的防御性设计

`:99-105` 的 `effectiveCount` 取「声明数 / 可用向量数 / 可用文档数」三者最小值：

```java
if (declaredCount <= 0 || availableVectors < 0 || availableDocs < 0) return 0;  // fail-safe
return Math.min(declaredCount, Math.min(availableVectors, availableDocs));
```

`:83-89` 注释记录了真实事故：**曾实测 `health_knowledge` 元数据声明 312 条，
`vectors.bin` 只够 208 条 → 下标 208~311 的 104 篇文档永久不可召回且不报错**。
现 `:158-164` 会 `log.error` 显式告警。⚠️ 但只能保证**不错位**，补不回丢失的 embedding（需运维 rebuild）。
