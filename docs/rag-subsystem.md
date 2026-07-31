# RAG 检索增强子系统

> 版本：v5.1（2026-07-31）
> 代码位置：`后端/personal-health-api/src/main/java/cn/kmbeast/crm/rag/`、`.../crm/vectordb/`、`.../service/impl/RAGEvaluationServiceImpl.java`
> 配套文档：AI 智能体架构见 `agent-architecture.md`

---

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
- **限制**：本地文件向量库为全量扫描，数据量 **>10 万块** 时建议迁移 pgvector / Milvus + HNSW（见 ../DELIVERY.md §8.2）；
- **知识图谱**：Neo4j 代码模块已就绪，但**未接入**主 RAG 链路（GraphRAG 属新项目，规划中）。
