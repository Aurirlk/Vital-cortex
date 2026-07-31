# 智康云 - 核心技术 Q&A

> 面试/答辩用技术问答手册

---

## 一、意图识别

### Q: 意图识别是怎么实现的？

**A**: 基于关键词权重匹配 + 规则引擎。AgentCoordinator 维护了 6 个专科 Agent 的关键词词库：

| Agent | 关键词 |
|-------|--------|
| 全科医生 | 症状、疼痛、发烧、咳嗽、诊断、不适 |
| 营养师 | 饮食、减肥、卡路里、维生素、食谱 |
| 心理咨询师 | 情绪、焦虑、抑郁、失眠、压力 |
| 报告分析师 | 体检、指标、化验、血糖、血压 |

用户输入 → 分词匹配各 Agent 关键词 → 计算加权得分 → 选择最高分 Agent。同时保留 `general_assistant` 作为默认兜底。

> v5.1 起，关键词词表已**外部化到 `CrmConfig`**，调整意图路由无需改代码。

```java
public String identifyAgent(String userMessage) {
    Map<String, Integer> scores = new HashMap<>();
    for (AgentRole role : AGENT_ROLES) {
        int score = 0;
        for (String keyword : role.getKeywords()) {
            if (userMessage.contains(keyword)) score++;
        }
        if (score > 0) scores.put(role.getType(), score);
    }
    return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("general_assistant");
}
```

---

## 二、Multi-Agent 系统

### Q: 多智能体 (Multi-Agent) 是怎么协作的？

**架构**：
```
用户输入 → AgentCoordinator (意图识别)
    ├── 全科医生 Agent → 症状分析 + 分诊建议
    ├── 营养师 Agent → 饮食规划
    ├── 心理咨询师 Agent → 情绪疏导
    ├── 报告分析师 Agent → 体检报告解读
    └── 全能助手 (default)
```

**关键设计**：
1. **Supervisor 中枢模式**：AgentCoordinator 作为调度中心，根据用户意图将请求路由到最合适的专科 Agent
2. **ReAct Agent (CRM 系统)**：6 种工具增强推理（药品查询、健康数据、知识检索、联网搜索、聊天历史、SQL 查询），支持 5 轮自主决策
3. **Agent 工作空间**：每个 Agent 有独立的本地文件存储区，支持用户画像缓存、对话历史摘要、任务执行日志

### Q: ReAct Agent 和普通 LLM 调用的区别？

| 维度 | 普通 LLM | ReAct Agent |
|------|----------|-------------|
| 推理方式 | 一次调用 | Thought → Action → Observation 循环 |
| 工具使用 | 无 | 6 种本地工具 |
| 决策轮次 | 1 轮 | 最多 5 轮 |
| 适用场景 | 简单问答 | 需要检索/计算/推理的复杂任务 |

---

## 三、Neo4j 知识图谱

### Q: 为什么选择 Neo4j 而不是传统关系型数据库？

1. **图遍历效率**：医疗知识天然是图结构（疾病关联症状、药物关联禁忌），Neo4j 的 Cypher 查询比 MySQL JOIN 快 10-100 倍
2. **多跳推理**：可以查询"高血压 → 并发症 → 肾病 → 禁忌药物"，MySQL 需要多次 JOIN
3. **GraphRAG**：将知识图谱子图作为 Context 注入 LLM Prompt，从物理层面防止幻觉

### Q: Neo4j 在项目中的实际应用？

```cypher
// 查询疾病的关联症状和药物
MATCH (d:Disease {name: '高血压'})-[r]->(e)
WHERE type(r) IN ['HAS_SYMPTOM', 'TREATED_BY', 'CONTRAINDICATED']
RETURN d.name, type(r), e.name
```

在 GraphRAG 中：
1. 从用户描述中提取医疗实体（疾病名、药名、症状）
2. Cypher 查询 Neo4j 获取确切的拓扑关系
3. 将子图结构化为文本 Context 注入 Prompt
4. LLM 基于可信的图数据生成回答，而非自由发散

> 状态说明：Neo4j 相关能力（`core/graph/` 模块）代码已就绪，但**尚未接入线上主链路**，属规划项（见 `../DELIVERY.md` §8.2）。

---

## 四、知识图谱与 GraphRAG（规划项）

### Q: GraphRAG 和普通 RAG 的区别？

| 维度 | 普通 RAG | GraphRAG |
|------|----------|----------|
| 数据源 | 文档向量 | 知识图谱 |
| 检索方式 | 语义相似度 | Cypher 图查询 |
| 知识结构 | 非结构化 | 结构化三元组 |
| 关系推理 | 不支持 | 多跳推理 |
| 幻觉控制 | 依赖 LLM | 图约束 + LLM |

### Q: 项目中 GraphRAG 的当前状态？

> ⚠️ **当前（v5.1）GraphRAG / Neo4j 为规划与实验性模块，尚未接入主 RAG 链路。**

- 代码侧已具备 `core/graph/` 模块（`Neo4jClient`、`Neo4jConfig`、`KnowledgeGraphService`、`GraphRAG`），可对接 Neo4j 做实体抽取与子图查询；
- 但线上问答主链路仍以**混合 RAG（本地向量库 + MySQL LIKE，RRF 融合）**为主；
- GraphRAG 真正落地依赖图谱数据建设（疾病-症状-药物三元组），属独立新项目，详见 `../DELIVERY.md` §8.2。

```
规划中的双路召回（尚未启用）：
用户输入
  ├── Vector RAG 通道：本地向量库检索 → 相关文档
  ├── Graph RAG 通道（规划）：实体提取 → Cypher 查询 → 知识图谱子图
  └── 合并 Context → 注入 LLM Prompt → 生成回答
```

---

## 五、RAG 检索增强

### Q: RAG 检索流程是怎样的？

文章在发布时经 **ingestion 管线**（分块 `ChunkUtil` → 嵌入 `EmbeddingService` → 入库 `KnowledgeIngestionService`）自动灌入向量库；检索时采用**向量 + MySQL LIKE 双路召回、RRF 融合**：

```
文章发布 → 分块 → 嵌入 → 向量库入库（ingestion 自动联动）
                        ↓
用户提问 → 向量召回（余弦相似度）+ MySQL LIKE 召回
                        ↓
              RRF 融合排序 → Top-K 文章（带引用溯源）
                        ↓
              注入 AI 上下文 → 基于文章生成回答
                        ↓
              RAGAS 评测（真实检索 + LLM 打分：精确度/忠实度/相关性）
```

### Q: 为什么要用"混合搜索"（Hybrid）？

1. **关键词搜索**（MySQL LIKE）：精确匹配，速度快，适合已知医学术语
2. **语义搜索**（本地文件向量库，余弦相似度）：理解意图，适合相似语义但不同表述
3. **RRF 融合**：对两路结果按排名倒数加权融合，兼顾召回率与准确率，回答附引用溯源

> 说明：项目当前使用**自研本地文件向量库**（余弦相似度），并非 FAISS；如需更大规模（>10 万块）再迁移 pgvector / Milvus + HNSW。

---

## 六、防幻觉机制

### Q: 项目有哪些防幻觉措施？

| 措施 | 层级 | 说明 |
|------|------|------|
| GraphRAG 约束 | Prompt 层 | 用知识图谱事实限制输出空间 |
| 防端水引擎 | 输出层 | 检测重复/无意义内容 |
| 输出验证器 | 安全层 | 检测是否包含药物剂量建议 |
| 系统提示词约束 | 指令层 | "严禁开具处方药""标注免责声明" |
| 知识库 RAG | 上下文层 | 强制基于文章回答 |

---

## 七、性能与成本

### Q: 如何降低 AI 调用成本？

1. **本地模型替换**：高频简单问答使用 LoRA 微调的本地模型（约 0 成本）
2. **熔断机制**：故障时自动切换备用厂商或本地降级
3. **CostTracker**：追踪每次调用的 Token 消耗，超出预算时告警
4. **意图分流**：简单意图走本地模型，复杂意图走云端 API

预期：通过本地模型替换部分云端 API 调用，Token 成本下降约 40%。

---

## 八、系统安全

### Q: 医疗系统的安全保障措施？

| 措施 | 实现 |
|------|------|
| 身份认证 | JWT，**密钥外部注入**（`JWT_SECRET`），启动强校验（空/弱密钥拒绝启动），默认 **7 天**有效期 |
| 机器接口 | `/crm/**` 由 `CrmApiKeyInterceptor` 做 API Key 认证，**fail-closed**（缺密钥即拒绝） |
| 权限控制 | RBAC 5 角色（菜单/按钮/API 三级） |
| SQL 注入防护 | `SqlGuard` 只读词法校验 + 关键词黑名单 + 禁子查询 + **租户隔离**（仅查本会话手机号数据） |
| 越权防护 | AI 会话 / 文件上传均有归属校验（IDOR 已修复） |
| XSS 防护 | AI 输出经 **DOMPurify** 白名单净化 |
| 数据出境 | 健康档案发送第三方 AI 前**自动脱敏**（剔除姓名/手机号等 PII） |
| 凭证管理 | 厂商密钥经环境变量注入；管理端配置项建议信封加密（KMS），详见 `../DELIVERY.md` §8.2 |
| LLM 韧性 | Provider 工厂 + 熔断器，429/5xx 重试与故障快速失败 |
| 成本可观测 | 每次调用 token 用量落库（`ai_usage` 表） |
| 内容审核 | 敏感词过滤 + 帖子审核 |
| 合规 | 涉及敏感健康信息，商用前建议等保三级测评 + 渗透测试 |

---

## 九、大模型微调

### Q: LoRA 微调的原理和参数？

**原理**：在原模型旁添加低秩矩阵（rank=8），只训练新增参数，不改变原模型权重。

**参数配置**：
- 基座模型：Qwen2.5-7B-Instruct
- 训练框架：LLaMA-Factory
- LoRA rank=8, alpha=16, dropout=0.1
- 学习率：5e-5，batch_size=4，epochs=3-5
- 训练数据：9000 条（7000 train + 1000 val + 1000 test）

**成本**：RTX 4090 单卡约 2-4 小时，显存 24GB，LoRA 权重仅几十 MB。

---

## 十、v5.1 架构改进要点（2026-07-31）

### Q: ReAct Agent 是怎么实现的？基于正则解析吗？

**不是正则解析。** 采用 **OpenAI function calling**：模型在回复中直接输出结构化工具调用（函数名 + 参数 JSON），由 SDK/运行时解析并执行，避免正则从自然语言里抽取参数的脆弱性。每轮工具调用的参数、结果与状态都会落库，形成可审计的**工具轨迹（检查点）**。

```
用户消息 → LLM（带工具定义）
   ← function_call: {name, arguments}
工具执行 → Observation
   → 再次 LLM → …（最多 5 轮）
   → 末轮总结 + 去重 + 循环检测 → 最终回答
```

### Q: 如何支持多厂商且保证韧性？

通过 **`LLMProviderFactory`** 在运行时按配置切换：
- `DeepSeekProvider` — 云端 DeepSeek（OpenAI 兼容）
- `LocalVllmProvider` — 自部署微调模型 `HealthPulse-Qwen2.5-7B`（vLLM，`:8000/v1`）
- `CircuitBreaker` — 轻量熔断器：429/5xx 自动重试，连续失败则快速失败（fail-fast），半开状态探测恢复

### Q: 本地大模型怎么接入？

在 `application.yml` / 环境变量中设置 `AI_PROVIDER=zhikangyun-local`，并指向 `http://localhost:8000/v1`（vLLM OpenAI 兼容端点）。`LocalVllmProvider` 复用统一 `LLMProvider` 接口，业务层无感切换。

### Q: 单元测试覆盖情况？

v5.1 新增 **25 个用例**（`mvn test` 通过），覆盖关键安全/算法路径：
- `SqlGuardTest`（10）— 只读守卫与租户隔离（真实抓出 3 个绕过漏洞并修复）
- `ChunkUtilTest`（4）— 文本分块
- `ToolArgsValidatorTest`（4）— 工具参数校验
- `DrugServiceImplTest`（7）— 药品业务逻辑

### Q: RAG 评测用的是什么？

**真实 RAGAS 评测管线**（`RAGEvaluationServiceImpl`）：基于真实检索结果 + LLM 打分，计算 `contextPrecision`（上下文精确度）、`faithfulness`（忠实度）、`relevance`（相关性），阈值不满足时报警。已**移除原先的模拟数据**。

---

**文档位置**: `tech-qa.md`（配套见根目录 `../DELIVERY.md`）
