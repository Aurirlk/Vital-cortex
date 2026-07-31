# AI 智能体子系统架构

> 版本：v5.1（2026-07-31）
> 代码位置：`后端/personal-health-api/src/main/java/cn/kmbeast/core/agent/`、`.../crm/agent/`、`.../core/provider/`
> 配套文档：RAG 检索增强见 `rag-subsystem.md`；安全加固见 `security-hardening.md`；运维配置见根目录 `../DELIVERY.md`

---

## 1. 总览

智康云的 AI 能力由三层构成：

```
┌─────────────────────────────────────────────────┐
│  Multi-Agent 协调层（core/agent/AgentCoordinator） │
│  意图识别 → 路由到 6 个专科角色之一                  │
└───────────────────────┬─────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────┐
│  ReAct 推理层（crm/agent/ReActAgent 等）           │
│  思考—调用工具—观察 循环（≤5 轮，OpenAI function calling） │
└───────────────────────┬─────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────┐
│  Provider 抽象层（core/provider/）                  │
│  LLMProviderFactory + 熔断器，屏蔽 DeepSeek / 本地 vLLM 差异 │
└─────────────────────────────────────────────────┘
```

---

## 2. Multi-Agent 协调器（AgentCoordinator）

`AgentCoordinator` 维护一个有序角色表 `AGENT_ROLES`（`LinkedHashMap`），默认 6 个角色：

| 角色 key | 展示名 | 定位 |
|----------|--------|------|
| `doctor` | 全科医生 | 症状 / 疾病 / 用药 |
| `nutritionist` | 营养师 | 膳食 / 营养 |
| `psychologist` | 心理咨询师 | 情绪 / 睡眠 / 压力 |
| `analyst` | 报告分析师 | 体检报告 / 指标解读 |
| `consultant` | 健康顾问 | 通用健康咨询 |
| `general_assistant` | 全能助手 | 兜底（默认回落） |

**路由算法**（`selectAgent`）：
1. 遍历每个角色的**外部化关键词表**，对用户问题做子串匹配，命中累加得分；
2. 取最高分角色；并列或零命中时回落 `general_assistant`。

**AG-11 整改（关键词外部化）**：关键词表不再硬编码，改为可在 `crm.intent.keywords` 配置中覆盖（`@PostConstruct` 时合并），无需改代码即可调整意图识别策略。

---

## 3. ReAct Agent（推理层）

实现方式：**基于 OpenAI function calling**（非正则解析），由 LLM 自主决定调用哪些工具。核心类：

| 类 | 职责 |
|----|------|
| `BaseReActAgent` | 通用骨架：有界线程池 + 调用超时、工具并行调用、LLM 失败重试、工具轨迹（`ToolResult`）记录 |
| `ReActAgent` | 末轮总结（基于工具结果生成自然语言回答）、去重、循环检测（防无限工具调用） |
| `StreamingReActAgent` | SSE 流式输出版本（资源随连接释放） |

**工具集**（`crm/agent/tool/`）：

| 工具类 | 名称 | 功能 |
|--------|------|------|
| `SearchDrugTool` | `search_drug` | 药品检索（drugs.json） |
| `GetHealthDataTool` | `get_health_data` | 用户健康指标 |
| `SearchKnowledgeTool` | `search_knowledge` | 知识库语义检索（RAG） |
| `WebSearchTool` | `web_search` | 联网搜索 |
| `GetChatHistoryTool` | `get_chat_history` | 会话历史 |
| `ExecuteSqlTool` | `execute_sql` | 只读 SQL（经 `SqlGuard` 守卫 + 租户隔离） |

支撑类：`ToolArgsValidator`（参数校验，含 4 个单测）、`ToolRegistry`、`Tool`/`ToolContext`、`AiSessionContext`。

**检查点 / 审计**：工具调用轨迹（参数、结果、状态）随会话落库，可回放某次对话调用了哪些工具、参数与结果。

---

## 4. Provider 抽象层（core/provider）

接口 `LLMProvider`：`chat`、`stream`、`getId`、`getCircuitBreaker`。

| 实现 | ID | 说明 |
|------|----|------|
| `DeepSeekProvider` | `deepseek` | 默认厂商 |
| `LocalVllmProvider` | `zhikangyun-local` | 本地微调模型，默认基址 `http://localhost:8000/v1`（OpenAI 兼容） |

**工厂 `LLMProviderFactory`**：
- `init()` 从 `AiConfig.getProvider()` 读取激活厂商；
- `normalize()` 归一化：`local / vllm / zhikangyun-local / zhikangyunlocal` → `zhikangyun-local`；其余回落 `deepseek`；
- 构建共享 `OkHttpClient`（连接/读超时来自配置，连接池 10 × 5min）。

**熔断器 `CircuitBreaker`**：三态 `CLOSED / OPEN / HALF_OPEN`。`defaultFor()` 默认阈值：错误率 50%、统计窗口 60s、OPEN 维持 30s、最小触发调用数 5。`beforeCall / onSuccess / onFailure` 驱动状态流转，每个 Provider 内置独立熔断器；OPEN 时抛 `CircuitBreakerOpenException` 快速失败。

> 遗留：`LLMFactory`（旧注册式工厂）仍在，新代码统一走 `LLMProviderFactory`。

---

## 5. 配置

| 配置项 | 来源 | 说明 |
|--------|------|------|
| 激活厂商 | `AiConfig.getProvider()` / 环境变量 `AI_PROVIDER` | `deepseek`（默认）或 `zhikangyun-local` |
| 本地模型基址 | `LocalVllmProvider.DEFAULT_BASE` / 环境变量 | `http://localhost:8000/v1` |
| 厂商密钥 | `AI_API_KEY` | DeepSeek / 本地服务密钥 |
| 超时 | `AiConfig` connect/read timeout | OkHttp 连接池 10 |

---

## 6. 数据流

```
用户问题
  → AgentCoordinator.selectAgent()        → 选定角色（含 system prompt）
  → ReActAgent.run() 循环（≤5 轮）:
        LLM(function calling) → 解析 tool_calls
        → ToolRegistry 执行工具（含 ToolArgsValidator 校验 / SqlGuard 守卫）
        → 工具轨迹落库
        → 观察结果回填 prompt
  → 末轮总结 → 返回 / 流式推送（SSE）
        所有 LLM 调用 → LLMProviderFactory → Provider(+熔断器) → 上游厂商
```

---

## 7. 已知限制（详见 ../DELIVERY.md §8.2）

- 意图识别仍为关键词匹配（词表已外部化，升级路径：embedding 分类 / LLM router）；
- `AiServiceImpl` 职责过载（935 行），已抽离 Provider 层，后续按「会话 / 检索 / 评测 / 用量」拆 4 个服务；
- 服务端 ASR/TTS 未实现（语音走浏览器原生 Web Speech API）。
