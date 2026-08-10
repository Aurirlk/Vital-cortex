# VitalCortex — AI-Native 个人健康智能体平台

> 从 0 到 1 落地的 AI 应用工程实践：Multi-Agent 健康问诊 × 双路 RAG 检索 × 多模态交互 × 企业级安全加固。
> 让健康数据会说话，让 AI 医生常在身边。

基于 **Spring Boot 3.5 + Java 17 + Vue 3** 构建的 AI 健康平台，集成 AI 智能问诊、药品订阅、健康数据追踪、知识库双路 RAG 检索、联网搜索、社区互动等功能。支持 12 个国内 AI 厂商，通过 ReAct Agent 实现工具增强推理（药品查询、健康数据读取、知识检索、联网搜索、SQL 查询）。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| **Multi-Agent 健康问诊** | 医生/营养师/药师等角色协调器 + ReAct Agent（OpenAI function calling），工具轨迹落库，可观测可回放 |
| **双路 RAG 检索** | 向量语义（bge-m3）× Neo4j 知识图谱双路召回 + RRF 融合，回答带 `[ID]` 引用溯源 + RAGAS 评测 |
| **三层防幻觉** | `SynthesisGuard` 质量门（端水/重复检测）+ `OutputValidator` 合规门（剂量敏感/免责声明）+ `SignalDetector` 安全门（紧急就医提示） |
| **多模态交互** | 图片进上下文（VIP 512K / 普通 128K 分级窗口 + 预算管理）；语音 ASR/TTS Provider 化（设计就绪） |
| **企业级安全** | 会话版本号（锁定/登出/改密即旧 Token 失效）、JWT 外部注入、fail-closed 接口、SqlGuard 防注入、Sentinel 限流熔断 |
| **全链路可观测** | MDC traceId/userId 日志关联 + Prometheus/Grafana 指标看板 + Loki 日志聚合 + Alertmanager 告警 |
| **多厂商 AI 接入** | DeepSeek/通义/Kimi/GLM/豆包/MiniMax 等 12 家，Provider 工厂 + 熔断 + 自动重试，管理员界面热切换 |

---

*项目源于 [程序员晨星](https://space.bilibili.com/1759570621) 的前后端教程，在其基础上迭代了 AI 智能问诊、CRM ReAct Agent、双路 RAG、多模态、安全加固等 Agent 能力，并持续重构为 AI-Native 架构。*

---

## 项目文档导航

> 项目开发过程中的全部文档（满足结课报告 7 大模块要求），已扁平化至 `docs/` 根目录。架构级改进以 `DELIVERY.md` 为权威说明。

| 文档           | 路径                                      | 说明                                           |
| ------------ | --------------------------------------- | -------------------------------------------- |
| 需求分析         | `docs/需求分析.md` | 功能需求、用例图、流程图                                 |
| 数据库设计        | `docs/数据库设计.md`           | ER 图、建表 SQL、字段说明                             |
| 后端开发         | `docs/后端开发.md`        | 接口设计、核心代码、架构说明                               |
| 前端开发         | `docs/前端开发.md`      | 页面设计、组件说明、路由设计                               |
| 测试文档         | `docs/测试报告.md`                | 测试用例（49条，含25单测）、测试报告                         |
| Linux部署      | `docs/Linux部署.md`        | 环境搭建、部署命令、常见问题                               |
| AI工具使用       | `docs/AI工具使用记录.md`      | AI工具使用记录（≥500字）                              |
| **AI 智能体架构** | `docs/智能体架构.md`              | **v5.1 新增** Multi-Agent/ReAct/Provider工厂/熔断器 |
| **RAG 检索增强** | `docs/RAG子系统.md`                   | **v5.1 新增** ingestion管线/混合检索/RAGAS           |
| **安全加固设计**   | `docs/安全加固设计.md`        | **v5.1 新增** JWT/CRM Key/SqlGuard/XSS/脱敏      |
| 项目状态         | `docs/项目状态.md`                     | 功能清单、完成状态                                    |
| 开发指南         | `docs/开发指南.md`             | 防坑指南、编码规范                                    |
| **缺陷与改进路线图** | `docs/缺陷与改进路线图.md`                | **v5.2 新增** 缺陷/漏洞/改进点决策与排期（已定方案 3 项）      |
| **多模态能力设计** | `docs/多模态设计.md`            | **v5.2 新增** 图片进上下文 + 语音 ASR/TTS Provider 化详细设计 |
| 用户交付手册       | `DELIVERY.md`                        | 部署/配置/安全/已知限制（v1.2，架构改进权威说明）                 |

---

## 技术栈

| 层级  | 技术                                            |
| --- | --------------------------------------------- |
| 前端  | Vue 3 + Element Plus + ECharts + Vue Router   |
| 后端  | **Spring Boot 3.5.16 + Java 17** + MyBatis + MySQL 8 + Redis + SQLite（CRM 对话历史） |
| AI  | 12 个国内厂商（DeepSeek、通义千问、Kimi、GLM 等）+ 本地 vLLM 微调模型（可选，训练集与参数见 `ai_model/`，完整权重发布 Hugging Face：`Weikaijie/HealthPulse-Qwen2.5-7B`） |
| 向量库 | 本地向量库（余弦相似度，规划迁移 pgvector HNSW）+ **bge-m3 嵌入（硅基流动）** + MySQL LIKE，**RRF 混合检索** |
| 知识图谱 | **Neo4j GraphRAG**（实体抽取 → 关系查询 → 注入上下文，双路召回已接入主链路） |
| 认证  | JWT（会话版本号，锁定/登出/改密即失效）+ CRM API Key（机器接口，fail-closed） |
| 智能体 | Multi-Agent 协调器 + ReAct Agent（OpenAI function calling + 工具轨迹落库） |
| RAG | 文章 ingestion 管线（分块→嵌入→入库）+ 向量/图谱/MySQL 混合检索 + RAGAS 评测 + 引用溯源 |
| 防幻觉 | **SynthesisGuard 三层防线**：质量门 + 合规门 + 安全门（已接入 chat/chatStream 输出端） |
| 多模态 | 图片进上下文（VIP 512K/普通 128K 分级窗口，TokenBudgetManager 预算管控） |
| 韧性  | LLM Provider 工厂 + 轻量熔断器（429/5xx 重试与快速失败）+ **Sentinel 限流**（登录/AI/上传） |
| 安全  | SqlGuard 只读 SQL 守卫（词法校验 + 租户隔离）+ DOMPurify XSS 净化 + PII 出境脱敏 + 上传魔数校验 |
| 可观测 | MDC traceId/userId + **Prometheus/Grafana/Loki/Alertmanager 监控告警栈**（docker-compose 一键起） |
| PDF | iText + JFreeChart（健康报告生成）                    |

---

## 功能模块

### 用户端

- **健康资讯** — 浏览、搜索、收藏健康文章，支持轮播图

- **健康指标** — 自定义健康模型，记录健康数据（血压、血糖、体重等），支持 JSON 导入导出

- **AI 健康分析** — 6 种 AI 角色（健康助手、全科医生、营养师、心理咨询师、报告分析师、全能助手），支持 Markdown 渲染、联网搜索、深度思考、知识库 RAG、健康数据读取

- **网站小助手** — 独立对话页面，内置意图识别（病情查询/医生推荐/药品介绍/健康知识），自动分流

- **药品订阅** — 浏览药品信息（价格、说明、分类），订阅关注的药品

- **健康报告** — 一键生成 PDF 健康报告，包含图表和 AI 建议

- **深色模式** — 支持深色/浅色主题切换

- **消息中心** — 系统通知与提醒

### 管理后台

- **仪表盘** — 用户增长、健康记录等统计图表

- **用户管理** — 用户信息 CRUD

- **资讯管理** — 健康文章 CRUD（富文本编辑器），支持轮播图和置顶设置

- **AI 配置管理** — 支持多厂商切换，配置持久化到 MySQL（重启不丢失）

- **AI 医生管理** — 修改各 AI 角色的系统提示词、Temperature、Top-P 参数

- **联网搜索配置** — 独立配置搜索引擎（博查AI、Tavily、DuckDuckGo等）

- **资讯管理** — 健康文章 CRUD（富文本编辑器），支持轮播图和置顶

- **药品管理** — 药品信息 CRUD，支持 JSON 批量导入

- **健康数据管理** — 查看和管理用户健康记录，支持 JSON 导入导出

- **评论/消息管理** — 评论审核、消息推送

### AI 工具增强

| 工具                 | 数据来源                            | 说明              |
| ------------------ | ------------------------------- | --------------- |
| `search_drug`      | `ai_data/drugs.json`            | 药品搜索，返回名称/价格/厂商 |
| `get_health_data`  | `ai_data/health/user_{id}.json` | 用户健康指标查询        |
| `search_knowledge` | 向量数据库 + MySQL                   | 知识库语义检索         |
| `web_search`       | 博查/Tavily/DuckDuckGo            | 联网搜索最新信息        |
| `get_chat_history` | SQLite                          | 查询聊天历史          |
| `execute_sql`      | SQLite                          | 只读 SQL 查询       |

---

## 网站小助手（基于CRM意图识别的智能助理）

- **五大核心功能**：
1. 药品推荐与价格查询（search_drug 工具，从JSON文件读取）

2. 推荐 AI 医生角色

3. 推荐健康资讯文章（向量语义检索）

4. 联网搜索获取最新信息（web_search 工具）

5. SQL 查询执行（execute_sql 工具）

内置意图识别引擎，根据用户输入自动分流：

| 用户输入        | 识别意图 | 处理方式         |
| ----------- | ---- | ------------ |
| "我发烧咳嗽怎么办？" | 病情查询 | 联网搜索         |
| "推荐合适的医生"   | 医生推荐 | 关键词匹配 → 快捷跳转 |
| "布洛芬价格多少"   | 药品介绍 | 调用药品数据库      |
| "高血压怎么调理"   | 健康知识 | AI + 知识库 RAG |

- **ReAct Agent** — 工具增强推理，支持 5 轮自主决策

- **本地向量数据库** — 文件存储 + 内存索引，余弦相似度搜索

- **SQLite 聊天记录** — 嵌入式数据库，零外部依赖

---

## AI 智能体架构（Multi-Agent + ReAct + 混合 RAG）

系统 AI 能力由三层构成，覆盖从意图路由到工具增强推理再到知识检索的完整链路。

### 1. Multi-Agent 协调（AgentCoordinator）

`AgentCoordinator` 作为 Supervisor 中枢，基于**外部化意图词表**将用户问题路由到最合适的专科 Agent：

```
用户输入 → AgentCoordinator（意图识别）
   ├── 全科医生 Agent  → 症状分析 + 分诊建议
   ├── 营养师 Agent    → 饮食规划
   ├── 心理咨询师 Agent → 情绪疏导
   ├── 报告分析师 Agent → 体检报告解读
   ├── 健康助手 Agent   → 通用健康咨询
   └── 全能助手 (default) → 兜底
```

> 意图词表已外部化到 `CrmConfig`，便于运维调整而不改代码。

### 2. ReAct Agent（工具增强推理）

对话中通过 **OpenAI function calling**（非正则解析）驱动 ReAct 循环，支持最多 5 轮自主决策，调用 6 个本地工具：

| 工具                 | 数据来源                            | 说明              |
| ------------------ | ------------------------------- | --------------- |
| `search_drug`      | `ai_data/drugs.json`            | 药品搜索，返回名称/价格/厂商 |
| `get_health_data`  | `ai_data/health/user_{id}.json` | 用户健康指标查询        |
| `search_knowledge` | 向量数据库 + MySQL                   | 知识库语义检索（混合）     |
| `web_search`       | 博查/Tavily/DuckDuckGo            | 联网搜索最新信息        |
| `get_chat_history` | SQLite                          | 查询聊天历史          |
| `execute_sql`      | SQLite（只读 + 租户隔离）               | 只读 SQL 查询       |

每一轮工具调用的**参数、结果、状态**都会随会话落库，支持审计回放（检查点能力已落地）。

### 3. 混合 RAG 检索

文章在发布时经 **ingestion 管线**（分块 `ChunkUtil` → 嵌入 `EmbeddingService` → 入库 `KnowledgeIngestionService`）自动灌入向量库；检索时采用**向量（余弦相似度）+ MySQL LIKE 双路召回，RRF 融合排序**，回答附引用溯源：

```
文章发布 → 分块 → 嵌入 → 向量库入库（ingestion 自动联动）
                          ↓
用户提问 → 向量召回 + MySQL LIKE 召回 → RRF 融合 Top-K
                          ↓
              注入 AI 上下文（带引用）→ 基于文章生成回答
                          ↓
              RAGAS 评测（真实检索 + LLM 打分：精确度/忠实度/相关性）
```

> RAG 质量看板（`/admin/ragMonitor`）展示真实 RAGAS 指标，替代了原先的模拟数据。

### 4. LLM Provider 工厂与韧性

通过 `LLMProviderFactory` 在运行时切换厂商，自带轻量熔断器与 429/5xx 重试：

- `DeepSeekProvider` — 云端 DeepSeek
- `LocalVllmProvider` — 自部署微调模型 `HealthPulse-Qwen2.5-7B`（vLLM，OpenAI 兼容，默认 `:8000`）；训练集/训练参数见 `ai_model/`，完整权重发布 Hugging Face：`Weikaijie/HealthPulse-Qwen2.5-7B`（见 `ai_model/README.md`）
- `CircuitBreaker` — 故障快速失败、半开探测恢复

### 5. 安全加固（本轮完成）

| 项 | 实现 |
|----|------|
| 登录令牌 | JWT 密钥**外部注入**，启动强校验（空密钥/弱密钥直接拒绝启动），默认 7 天有效期 |
| 机器接口 | `/crm/**` 由 `CrmApiKeyInterceptor` 做 API Key 认证，fail-closed |
| SQL 注入 | `SqlGuard` 只读词法校验 + 关键词黑名单 + 禁子查询 + **租户隔离**（只能查本会话手机号数据） |
| XSS | AI 输出经 DOMPurify 白名单净化 |
| 数据出境 | 健康档案发送第三方 AI 前自动**脱敏**（剔除姓名/手机号等 PII） |
| 成本可观测 | 每次调用 token 用量落库（`ai_usage` 表） |
| 单元测试 | 25 个用例（SqlGuard / ChunkUtil / ToolArgsValidator / DrugServiceImpl），`mvn test` 可跑 |

---

## RAG 知识库检索流程（双路召回）

```
用户提问 → 向量语义召回（bge-m3）+ Neo4j 图谱召回（实体关系）+ MySQL LIKE 召回
                    ↓
              RRF 融合排序 → Top-K 上下文
                    ↓
      注入 AI 上下文（文章引用溯源 + 图谱实体关系）→ 基于知识生成回答
```

> 图谱路未配置/未启动 Neo4j 时自动降级为纯向量 + LIKE，不抛错（`GraphRAG.isConnected()` 兜底）。

---

## 数据流设计

```
对话历史 → MySQL（主存储）
AI调用数据 → MySQL → 导出JSON → AI工具读取
AI配置 → MySQL（明文存储，管理员后台管理）
历史会话 → chat_backup/history_speak/{userId}/ （备份）
健康数据 → chat_backup/user_health/{userId}/ （备份）
```

---

## API 接口

### 用户接口（需 JWT 认证）

| 方法   | 接口                                | 说明           |
| ---- | --------------------------------- | ------------ |
| POST | `/user/login`                     | 登录           |
| POST | `/user/register`                  | 注册           |
| POST | `/user-health/save`               | 保存健康记录       |
| POST | `/user-health/import`             | JSON 导入健康记录  |
| GET  | `/user-health/export`             | JSON 导出健康记录  |
| POST | `/drug/query`                     | 查询药品         |
| POST | `/drug/subscribe/{id}`            | 订阅药品         |
| POST | `/ai/chat`                        | AI 对话（非流式）   |
| POST | `/ai/chat/stream`                 | AI 流式对话（SSE） |
| GET  | `/ai/conversations`               | 获取会话列表       |
| GET  | `/ai/conversations/{id}/messages` | 获取会话消息       |
| POST | `/ai/keywords/extract`            | 提取关键词（RAG）   |
| GET  | `/report/health-pdf`              | 下载健康报告       |

### 管理员接口

| 方法   | 接口                           | 说明              |
| ---- | ---------------------------- | --------------- |
| POST | `/ai/config/update`          | 更新 AI 配置        |
| POST | `/ai/config/switch-provider` | 切换 AI 厂商        |
| POST | `/data-export/all`           | 导出药品+健康数据到 JSON |
| POST | `/drug/save`                 | 新增药品            |
| PUT  | `/drug/update`               | 修改药品            |

### 数据导出接口（管理员）

| 接口                             | 说明                         |
| ------------------------------ | -------------------------- |
| `/data-export/drugs`           | 导出药品到 `ai_data/drugs.json` |
| `/data-export/health/{userId}` | 导出指定用户健康数据                 |
| `/data-export/health/all`      | 导出所有用户健康数据                 |

---

## AI 多厂商支持

| 厂商                     | 主力模型                               |
| ---------------------- | ---------------------------------- |
| **DeepSeek**           | deepseek-v4-flash, deepseek-v4-pro |
| **Moonshot AI (Kimi)** | kimi-k2.6                          |
| **智谱AI (GLM)**         | glm-5.1, glm-4.7                   |
| **阿里云 (通义千问)**         | qwen3.7-max, qwen-plus             |
| **MiniMax**            | MiniMax-M2.7                       |
| **百度 (文心一言)**          | ernie-5.0                          |
| **字节跳动 (豆包)**          | doubao-seed-2.0-pro                |
| **腾讯 (混元)**            | hunyuan-turbo                      |
| **零一万物 (Yi)**          | yi-large                           |
| **百川智能**               | baichuan-4                         |
| **阶跃星辰**               | step-2-16k                         |
| **小米 (MiMo)**          | mimo-v2.5                          |

---

### 联网搜索支持

支持 6 种搜索引擎，管理员可独立配置：

| 搜索引擎 | 费用 | 说明 |

|----------|------|------|

| **自动** | - | 优先博查→Tavily→DuckDuckGo |

| **博查AI** | 免费额度 | 国内医疗优化，推荐 |

| **Tavily** | 1000次/月免费 | 国际搜索，专为AI设计 |

| **DuckDuckGo** | 完全免费 | 无需API Key |

| **Serper** | 100次/月免费 | Google搜索API |

| **SerpAPI** | 100次/月免费 | Google/Bing搜索 |

---

## 快速启动

### 环境要求

- JDK 17（构建/运行目标，pom `source/target=17`）
- Maven 3.6+
- Node.js 16+
- MySQL 8.x（主库，HikariCP 连接池）
- Redis 6+（缓存 + 会话版本号，必需）
- Neo4j（可选，GraphRAG 双路召回；未启动时自动降级为纯向量检索）
- Docker（可选，一键起监控告警栈：Prometheus/Grafana/Loki/Alertmanager，见 `DELIVERY.md` §4.4）

### 1. 数据库初始化

```sql
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE personal_health;

-- 基础表结构 + 种子数据
source Data/sql/deploy/init_database.sql;
```

扩展模块脚本按需执行（`Data/sql/` 下）：`ai_usage_schema.sql`（token 成本）、`appointment_schema.sql`（预约）、`forum_schema.sql`（社区）、`rbac_schema.sql`（权限）、`audit_log.sql`（审计）等。

### 2. 配置密钥（本地开发）

```bash
# 复制配置模板（application.yml 已被 gitignore，不会误提交真实密钥）
cp 后端/personal-health-api/src/main/resources/application-example.yml \
   后端/personal-health-api/src/main/resources/application.yml

export JWT_SECRET=$(openssl rand -hex 48)      # JWT 签名密钥（≥32 字节，启动强校验）
export CRM_API_KEY=$(openssl rand -hex 32)     # CRM 机器接口密钥（≥24 字符）
export EMBEDDING_API_KEY=sk-xxx                # 嵌入服务密钥（硅基流动 bge-m3 等）
```

> 本地开发可设置 `CRM_STRICT_STARTUP=false`（或配置 `crm.strict-startup: false`）跳过启动强校验；
> 生产必须保持默认严格校验（未配置密钥拒绝启动）。

### 3. 启动后端

```bash
cd 后端/personal-health-api
mvn spring-boot:run
```

### 4. 启动前端

```bash
cd 前端/personal-heath-view
npm install
npm run dev
```

### 5. 首次配置

1. 用种子管理员账号 `admin` 登录（密码见 `Data/sql/deploy/init_database.sql` 注释，首次登录后请立即修改）
2. 进入管理员后台 → AI 配置：选择厂商（如 DeepSeek）、输入 API Key、配置嵌入服务（Embedding API URL + Key）
3. 保存配置后即可开始 AI 对话

---

## 数据库表结构

### 用户表 `user`

| 字段           | 类型           | 说明             |
| ------------ | ------------ | -------------- |
| id           | INT, 主键      | 用户ID           |
| user_account | VARCHAR(50)  | 用户账号（手机号）      |
| user_name    | VARCHAR(50)  | 用户名            |
| user_pwd     | VARCHAR(128) | 密码（BCrypt加密）   |
| user_role    | TINYINT      | 角色：1=管理员, 2=用户 |
| user_avatar  | VARCHAR(500) | 头像URL          |
| user_email   | VARCHAR(100) | 邮箱             |
| is_login     | TINYINT      | 登录状态           |
| is_word      | TINYINT      | 状态             |
| create_time  | DATETIME     | 创建时间           |

### 健康资讯表 `news`

| 字段          | 类型           | 说明              |
| ----------- | ------------ | --------------- |
| id          | INT, 主键      | 资讯ID            |
| name        | VARCHAR(200) | 标题              |
| content     | TEXT         | 内容              |
| tag_id      | INT          | 分类ID（关联 tags 表） |
| cover       | VARCHAR(500) | 封面图URL          |
| is_top      | TINYINT      | 是否置顶            |
| is_banner   | TINYINT      | 是否轮播图           |
| create_time | DATETIME     | 创建时间            |

### 资讯分类表 `tags`

| 字段   | 类型          | 说明   |
| ---- | ----------- | ---- |
| id   | INT, 主键     | 分类ID |
| name | VARCHAR(50) | 分类名称 |

### 药品表 `drug`

| 字段            | 类型            | 说明                  |
| ------------- | ------------- | ------------------- |
| id            | INT, 主键       | 药品ID                |
| name          | VARCHAR(200)  | 药品名称                |
| generic_name  | VARCHAR(200)  | 通用名                 |
| category      | VARCHAR(100)  | 分类（感冒药/抗生素/维生素等）    |
| description   | TEXT          | 药品说明                |
| price         | DECIMAL(10,2) | 价格                  |
| unit          | VARCHAR(50)   | 单位（盒/瓶/支）           |
| specification | VARCHAR(200)  | 规格                  |
| manufacturer  | VARCHAR(200)  | 生产厂家                |
| is_otc        | TINYINT       | 是否OTC（0=处方药, 1=OTC） |
| stock         | INT           | 库存                  |
| status        | TINYINT       | 状态（0=下架, 1=上架）      |

### 药品订阅表 `drug_subscription`

| 字段          | 类型       | 说明             |
| ----------- | -------- | -------------- |
| id          | INT, 主键  | 订阅ID           |
| user_id     | INT      | 用户ID           |
| drug_id     | INT      | 药品ID           |
| quantity    | INT      | 订阅数量           |
| status      | TINYINT  | 状态（0=取消, 1=有效） |
| create_time | DATETIME | 订阅时间           |

### 健康模型配置表 `health_model_config`

| 字段          | 类型          | 说明                |
| ----------- | ----------- | ----------------- |
| id          | INT, 主键     | 模型ID              |
| name        | VARCHAR(50) | 指标名称（如"收缩压"）      |
| unit        | VARCHAR(20) | 单位（如"mmHg"）       |
| symbol      | VARCHAR(10) | 符号                |
| value_range | VARCHAR(50) | 正常范围（格式："90,140"） |
| is_global   | TINYINT     | 是否全局模型            |

### 用户健康记录表 `user_health`

| 字段                     | 类型          | 说明     |
| ---------------------- | ----------- | ------ |
| id                     | INT, 主键     | 记录ID   |
| user_id                | INT         | 用户ID   |
| health_model_config_id | INT         | 健康模型ID |
| value                  | VARCHAR(50) | 记录值    |
| create_time            | DATETIME    | 记录时间   |

### AI 会话表 `ai_conversation`

| 字段                | 类型           | 说明                                    |
| ----------------- | ------------ | ------------------------------------- |
| id                | INT, 主键      | 会话ID                                  |
| user_id           | INT          | 用户ID                                  |
| title             | VARCHAR(255) | 会话标题                                  |
| agent_type        | VARCHAR(50)  | AI角色（doctor/nutritionist/consultant等） |
| message_count     | INT          | 消息数量                                  |
| last_message_time | DATETIME     | 最后消息时间                                |
| create_time       | DATETIME     | 创建时间                                  |

### AI 聊天记录表 `ai_chat_record`

| 字段              | 类型          | 说明                 |
| --------------- | ----------- | ------------------ |
| id              | INT, 主键     | 记录ID               |
| conversation_id | INT         | 关联会话ID             |
| user_id         | INT         | 用户ID               |
| role            | VARCHAR(20) | 角色（user/assistant） |
| content         | TEXT        | 消息内容               |
| agent_type      | VARCHAR(50) | AI角色类型             |
| create_time     | DATETIME    | 创建时间               |

### AI 配置表 `ai_config`

| 字段           | 类型           | 说明                    |
| ------------ | ------------ | --------------------- |
| id           | INT, 主键      | 配置ID                  |
| config_key   | VARCHAR(100) | 配置键（如 api_key, model） |
| config_value | TEXT         | 配置值（API Key 明文存储）     |
| description  | VARCHAR(255) | 配置描述                  |
| create_time  | DATETIME     | 创建时间                  |

### 评论表 `evaluations` / 消息表 `message`

| 表名          | 说明        |
| ----------- | --------- |
| evaluations | 用户评论/评价记录 |
| message     | 系统消息通知    |

---

## 目录结构

```
VitalCortex/
├── 前端/personal-heath-view/        # Vue 3 + Element Plus
│   └── src/views/                    # 页面（AI分析/助手/健康数据/药品等）
│
├── 后端/personal-health-api/        # Spring Boot 3.5.16 + Java 17
│   └── src/main/java/cn/kmbeast/
│       ├── controller/               # REST 接口
│       ├── service/impl/             # 业务实现（AiServiceImpl 等）
│       ├── core/                     # GraphRAG / Multi-Agent / LLM Provider
│       ├── crm/                      # 网站小助手（ReAct Agent / SQLite / 向量库）
│       └── config/                   # 配置（Sentinel / Redis / AI / 缓存）
│
├── ai_model/                        # 微调模型全套（已入库）
│   ├── dataset/                      # 医疗问答训练集（train/val/test）
│   ├── scripts/                      # 推理/评测脚本（FastAPI / Flask）
│   ├── train_config.yaml             # LoRA 训练参数
│   ├── 微调步骤.md / 排障指南.md
│   ├── 部署指南-vLLM-阿里云.md
│   └── README.md                     # 含 HF 权重仓库链接
│
├── docs/                            # 项目文档（中文名）
│   ├── 需求分析.md / 数据库设计.md / 后端开发.md / 前端开发.md
│   ├── 测试报告.md / 智能体架构.md / RAG子系统.md / 安全加固设计.md
│   ├── 多模态设计.md / 技术问答.md / 缺陷与改进路线图.md / ...
│
├── Data/sql/                        # SQL 脚本（建表 / 种子 / 扩展模块）
├── deploy/                          # 部署配置（Prometheus 等）
├── docker-compose.yml               # MySQL / Redis / 监控告警栈
├── DELIVERY.md                      # 交付与运维手册
└── README.md                        # 本项目文件
```

> 注：`dir/`（完整模型权重 29GB 等大文件）已被 `.gitignore` 排除，仅存本地；完整权重发布至 Hugging Face，训练集/参数/文档在 `ai_model/`。

---

## 注意事项

1. **运行环境** — 开发与验证环境为 **JDK 17 + Spring Boot 3.5.16**（pom `source/target=17`），与 IDEA 语言级别、JAVA_HOME 三者对齐；升级 Boot 3 时已通过 OpenRewrite 完成 javax→jakarta 全量迁移。
2. **密钥必须外部注入** — `JWT_SECRET`、`CRM_API_KEY`、`EMBEDDING_API_KEY` 等缺失或强度不足时后端**拒绝启动**，请勿使用硬编码密钥。
3. **AI 配置** — 通过管理员后台配置 API Key，持久化到 MySQL，重启不丢失（生产建议信封加密）。
4. **数据导出** — 药品和健康数据需先调用导出接口，AI 工具才能读取。
5. **敏感文件** — `.gitignore` 已排除 `application.yml`、`ai_data/`、`chat_backup/`、`dir/`（完整模型权重）等；微调训练集与训练参数已入库至 `ai_model/`，完整权重通过 Hugging Face 分发（`Weikaijie/HealthPulse-Qwen2.5-7B`）。
6. **合规提醒** — 平台涉及敏感健康信息，商用前建议完成等保三级测评与个人信息保护影响评估。详见 `DELIVERY.md`。

---

## 更新日志

### v5.4 (2026-08-10) — 作品集门面完善 + 微调模型发布

**文档体系**
- docs/ 15 个文档改中文名（需求分析/数据库设计/后端开发等），全部交叉引用同步
- README 作品集化：**VitalCortex** 品牌 + 核心能力矩阵 + 全站去 emoji
- 路线图/缺陷台账同步完成状态（Boot 3 升级、GraphRAG 接入、Sentinel 限流）
- 技术问答新增 4 道 v5.3 实战面试题（Boot3 升级坑 / Sentinel / embedding 选型 / GraphRAG 落地）

**微调模型发布**
- `ai_model/` 入库：训练集（5MB）+ LoRA 训练参数 + 微调步骤/排障指南/部署指南（vLLM+阿里云）+ 推理评测脚本（FastAPI/Flask）
- 完整权重（29GB）发布 **Hugging Face**：[`Weikaijie/HealthPulse-Qwen2.5-7B`](https://huggingface.co/Weikaijie/HealthPulse-Qwen2.5-7B)

### v5.3 (2026-08-09) — Spring Boot 3 升级 + 双路 RAG 打通 + 限流

**框架升级**
- **Spring Boot 2.7.18 → 3.5.16**（OpenRewrite 自动化迁移：javax→jakarta 全量、mybatis 3.0.3、mysql-connector-j），Java 17 编译目标对齐
- 修复升级隐藏坑：`spring.data.redis` 前缀迁移、`logging.file.path` 迁移瑕疵、SQLite + HikariCP 6 的 `setReadOnly` 兼容问题

**AI 能力**
- **GraphRAG 接入主检索链路**：Neo4j 实体抽取（真实图谱实体匹配 + 缓存）→ 关系查询 → 注入上下文，与向量路构成双路召回
- **SynthesisGuard 三层防幻觉接线**：质量门 + 合规门（免责声明/遵医嘱）+ 安全门（紧急就医），chat/chatStream 输出端统一生效
- **图片多模态 Phase A/B**：OpenAI 视觉格式消息组装、TokenBudgetManager 预算管控（VIP 512K/普通 128K 分级 + 模型上限取 min）
- **Embedding 接入**：硅基流动 bge-m3（独立密钥，与 LLM 厂商分离），向量检索链路可用

**工程化**
- **Sentinel 限流**：登录 10QPS / AI 对话 5QPS / 上传 20QPS，blockHandler 兜底（含 SSE 流式）
- 补齐历史欠账：重建 `PostServiceImpl`（19 方法）/ `SystemConfigServiceImpl`（7 方法）

### v5.2 (2026-08-01) — 多模态设计与账号锁定

**方案定稿（docs/缺陷与改进路线图.md）**
- 1.1 语音 ASR/TTS Provider 化设计（与 AiConfig 同构，未配置回退 Web Speech）
- 1.2 图片进上下文 + VIP 分级窗口设计（128K/512K）
- 1.3 账号锁定即失效 + 会话版本号设计

**v5.2 已实现**
- 1.3 会话版本号机制：锁定/登出/改密 → 旧 Token 立即失效；账号禁用返回 4010，前端强制退出
- 1.2 VIP 分级：`is_vip`/`vip_expire_time` 字段 + 128K/512K 预算档位 + 图片数量分级

### v5.1 (2026-07-31) — 架构级改进与加固

**AI 智能体**
- Multi-Agent 协调器（`AgentCoordinator`）意图词表外部化，6 专科角色路由
- ReAct Agent 改为 OpenAI function calling 驱动，工具调用轨迹落库（检查点/审计）
- LLM Provider 工厂 + 熔断器（`DeepSeekProvider` / `LocalVllmProvider` / `CircuitBreaker`），429/5xx 重试与快速失败
- 本地微调模型 `HealthPulse-Qwen2.5-7B`（vLLM :8000，OpenAI 兼容）可一键切换；训练集/参数入库 `ai_model/`，完整权重 Hugging Face 分发（`Weikaijie/HealthPulse-Qwen2.5-7B`，见 `ai_model/README.md`）

**RAG**
- 文章 ingestion 管线（分块→嵌入→入库），发布自动联动灌数
- 向量（余弦）+ MySQL LIKE 双路召回，RRF 融合，回答带引用溯源
- 真实 RAGAS 评测管线（精确度/忠实度/相关性），替代原模拟数据

**安全**
- JWT 密钥外部注入 + 启动强校验（空/弱密钥拒绝启动），7 天有效期
- CRM 接口 API Key 认证（`CrmApiKeyInterceptor`，fail-closed）
- `SqlGuard` 只读 SQL 守卫 + 租户隔离（仅查本会话数据）
- AI 输出 DOMPurify 净化、出境前 PII 脱敏、token 用量落库（`ai_usage`）

**工程**
- 25 个单元测试（SqlGuard / ChunkUtil / ToolArgsValidator / DrugServiceImpl）通过
- Docker 镜像去默认密码、非 root 运行、healthcheck；CI 去 `continue-on-error`
- 后端 `pom.xml` 编码属性修正、测试依赖补全；规范化损坏换行符

> 完整交付与后续规划见根目录 `DELIVERY.md`。

### v4.1 (2026-06-03)

**新功能：**

- 网站小助手：独立对话页面 + 4 类意图识别（病情查询/医生推荐/药品介绍/健康知识）
- 病情查询：联网搜索最新医疗信息
- 医生推荐：关键词智能匹配，推荐最相关的 1-3 位 AI 医生
- 药品介绍：调用 55 种药品数据库
- 健康知识：AI + 知识库 RAG 检索，强制基于文章回答
- AI 关键词提取：内置医学 NLP Prompt，优化 RAG 搜索精准度

**优化：**

- 搜索结果卡片布局优化（span=4→6，图片高度增加）
- AI 医生角色图标统一为 Element Plus 矢量图标
- RAG 文章数量从 3 篇提升到 6 篇，标题匹配优先排序
- AI 配置持久化到 MySQL（删除 AES 加密，改为明文存储）
- 历史会话用户隔离存储（chat_backup/history_speak/{userId}/）

**修复：**

- 修复 Vue 响应式丢失导致 AI 回复空白的问题（splice 替换）
- 修复分页查询偏移量重复计算
- 修复药品数据未注入 AI 上下文的问题
- 移除 Dify 外部 API 依赖，关键词提取完全本地化
- 移除了那个bug居多的悬浮球

### v4.0 (2026-06-02)

**新功能：**

- 健康助手悬浮球（可拖拽移动，快捷咨询）

- 健康数据 JSON 导入导出

- AI 配置持久化到 MySQL（AES加密，重启不丢失）

- SaaS 风格功能按钮栏（联网搜索、深度思考、知识库、健康数据）

- Markdown 渲染支持

- 数据导出服务（药品、健康指标导出为JSON供AI读取）

- 会话元数据记录（联网搜索、知识库等状态）

- 增加了dify的关键词搜索功能

**优化：**

- 数据流重构：MySQL为主存储，JSON为备份/导出

- AI工具从JSON文件读取数据（药品、健康指标）

- 对话缓存机制优化

- 用户界面布局优化（三栏布局：角色+聊天+设置）

- 生成设置移至右侧边栏

**安全：**

- API Key 加密存储到数据库

- .gitignore 排除敏感文件

### v3.0 (2026-06-01)

**新功能：**

- 多AI厂商支持（12个国内厂商）

- 联网搜索多引擎支持（6种搜索引擎）

- DeepSeek v4模型支持

- AI厂商一键切换

- 联网搜索配置独立界面

**优化：**

- 更新DeepSeek模型为v4版本

- 优化AI配置管理界面

- 完善厂商配置信息

---

## 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3)                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ 用户端   │  │ 管理端   │  │ 商家端   │  │ 医生端   │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    后端 (Spring Boot)                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                   Controller 层                       │  │
│  │  User | Drug | News | AI | Appointment | Quiz | ...  │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    Service 层                         │  │
│  │  Business Logic | AI Service | Export | Cache        │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    CRM Agent 系统                     │  │
│  │  ReAct Agent | Tools | VectorDB | SQLite | Workflow  │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    Core 核心模块                      │  │
│  │  Agent | Provider | RAG | Emotion | Guard | Voice    │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      数据层                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │  MySQL   │  │  SQLite  │  │  JSON    │  │  Redis   │  │
│  │ 主数据库 │  │ 聊天记录 │  │ AI数据   │  │  缓存    │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    外部 AI 服务                              │
│  DeepSeek | 通义千问 | Kimi | GLM | 文心一言 | 豆包 | ...  │
└─────────────────────────────────────────────────────────────┘
```

## 编译状态

- 后端编译：**BUILD SUCCESS**（Spring Boot 3.5.16 + Java 17，352 源文件）
- 前端构建：BUILD SUCCESS
- 无编码损坏

---

## 已知缺陷与限制清单

> 本表为历史缺陷台账，状态随版本推进更新。架构级改进详情见 `DELIVERY.md` §8。

### 已解决（v5.1 / v5.2 完成）

| 编号    | 原缺陷          | 现状                                                                                    |
| ----- | ------------ | ------------------------------------------------------------------------------------- |
| D-005 | 缺少单元测试       | 已补 25 个用例（SqlGuard / ChunkUtil / ToolArgsValidator / DrugServiceImpl），`mvn test` 通过 |
| D-009 | 部分接口缺少输入验证   | CRM 接口加 `CrmApiKeyInterceptor`；`SqlGuard` 只读守卫 + 租户隔离；AI 输出 DOMPurify 净化            |
| D-001 | 部分 SQL 脚本未执行 | 已补充 `Data/sql/ai_usage_schema.sql`（token 成本表），其余业务表脚本仍建议部署时执行                      |
| D-002 | admin 账号可能被锁定 | 修复 `init_database.sql`/`mock_business_data.sql` 种子账号 `is_login 1→0`；`UserServiceImpl.backUpdate` 增加防锁死守卫（保护当前用户 & 最后一名可登录管理员） |
| D-003 | 旧页面 UI 风格不统一 | 主品牌色统一为 `#0050cb`（品牌蓝），激活 `design-tokens.css` 令牌体系；登录页/个人页硬编码色收敛为品牌蓝（其余页面分阶收敛，见路线图） |
| D-004 | WebSocket 未实际部署测试 | 全链路打通：`ws.js` 保留 context-path 推导 + 指数退避；`Login.vue`/`main.js` 登录态自动建连；后端 `WebSocketServer` 补 `@OnMessage` 心跳回包；`NotificationServiceImpl.save` 入库后 `sendToUser` 实时推送（`type=notification` 对齐 `NotificationBell`） |
| D-006 | Redis 缓存未完全集成 | 引入 `RedisConfig`（Jackson2Json + 10min TTL）；`TagsServiceImpl`/`DrugServiceImpl` 加 `@Cacheable`/`@CacheEvict`；`docker-compose` 增 `redis` 服务 |
| D-007 | Prometheus 监控未完全集成 | `InterceptorConfig` 放行 `/actuator/**`；`application.yml` 去掉 `roles: ADMIN` 且 Redis health 默认关闭；新增 `deploy/prometheus.yml` 抓取 `backend:21090`，`docker-compose` 增 `prometheus` 服务 |
| D-008 | 前端样式不完全统一 | 主色收敛品牌蓝（见 D-003）；`main.js` 全局加载 `design-tokens.css`；移除 `Login.vue`/`UserProfile.vue` 冗余 `@import` |
| D-010 | 错误日志不够详细 | 新增 `TraceIdFilter`（MDC `traceId`/`userId` + 响应头 `X-Trace-Id`）；`JwtInterceptor` 注入 `userId`；`logback` 模式含 `[traceId=%X{traceId} userId=%X{userId}]`；`GlobalExceptionHandler` 返回追踪 ID 便于定位 |
| D-011 | **PostService/SystemConfigService 实现缺失** | Boot 3 升级后启动暴露：接口长期存在但实现类从未编写。重建 `PostServiceImpl`（19 方法：发帖/点赞/收藏/回复/关注/举报/热门/搜索）、`SystemConfigServiceImpl`（7 方法：分组配置/敏感掩码/管理员密码验证） |
| D-012 | **SQLite + HikariCP 兼容** | Boot 3（HikariCP 6）升级后 `config.setReadOnly(true)` 与 sqlite-jdbc 冲突导致启动失败；移除 JDBC 只读标志（SQLite 只读需在创建连接时指定） |
| D-013 | **Embedding 服务缺失** | 接入硅基流动 bge-m3（独立密钥，与 LLM 厂商 DeepSeek 分离）；DeepSeek 无 embeddings API 的历史坑闭环 |

### 仍待处理（保留项）

| 编号 | 缺陷 | 影响 | 位置 |
| --- | --- | --- | --- |
| D-003/008（续） | 全站样式令牌收敛 | 约 40 个页面仍存在硬编码色值，需分阶段视觉回归后统一为品牌蓝 | `views/` |

### 架构级遗留项（见 `DELIVERY.md` §8.2）

- **Spring Boot 3 迁移**：v5.3 已完成（3.5.16 + Java 17，javax→jakarta 全量）
- **等保三级测评 / 渗透测试**：未开展，商用前置
- **API Key 存储**：环境变量注入；管理端配置项建议信封加密
- **God Class 拆分**：`AiServiceImpl` 职责过载，已抽离 Provider 层，后续按会话/检索/评测/用量拆 4 服务
- **服务端 ASR/TTS**：未实现（语音走浏览器原生 Web Speech API；Provider 化设计已定稿，见 `docs/多模态设计.md`）
- **向量库规模**：本地文件实现全量扫描，已规划迁移 pgvector + HNSW（10 万级甜蜜区）
- **知识图谱**：GraphRAG 已接入主检索链路（Neo4j 实体抽取 → 关系查询 → 双路召回，见 v5.3）

---

## 改进目标（路线图）

### 已完成（v5.1 ~ v5.3 迭代落地）

| 目标 | 状态 |
| --- | --- |
| WebSocket 通知打通 | v5.1（D-004：全链路 + 心跳 + context-path） |
| Redis 缓存 | v5.1（D-006：Jackson2Json + 10min TTL） |
| Prometheus 监控 | v5.1（D-007：/actuator/prometheus + Grafana） |
| Spring Boot 3 升级 | v5.3（3.5.16 + Java 17，javax→jakarta 全量） |
| Sentinel 限流 | v5.3（登录 10QPS / AI 5QPS / 上传 20QPS） |
| GraphRAG 双路召回 | v5.3（Neo4j 实体抽取 → 双路召回接入主链路） |

### 短期（1-2 周）

| 优先级 | 目标 | 说明 |
| --- | --- | --- |
| P0 | 执行所有 SQL 脚本 | 扩展模块表（部署时执行，见 D-001） |
| P1 | 统一前端样式 | 约 40 页硬编码色值收敛为品牌蓝（D-003/008 续） |

### 中期（1-2 月）

| 优先级 | 目标 | 说明 |
| --- | --- | --- |
| P1 | 旧页面 UI 重构 | 首页、AI分析、药品等 |
| P1 | 完善单元测试 | 覆盖率 ≥ 70% |
| P2 | 向量库迁移 pgvector + HNSW | 本地全量扫描 → pgvector（方案已定稿） |

### 长期（3-6 月）

| 优先级 | 目标 | 说明 |
| --- | --- | --- |
| P2 | Spring AI demo | 单独实验项目（不重构主线，展示主流框架集成能力） |
| P3 | 移动端适配 | 响应式设计完善 |
| P3 | 微信小程序 | 核心功能移植 |

---

## FAQ 常见问题

### Q1: 无法登录怎么办？

A: 检查 `user` 表的 `is_login` 字段，确保为 0。如果是 1，执行：

```sql
UPDATE user SET is_login = 0 WHERE user_account = 'admin';
```

### Q2: AI 对话无响应怎么办？

A: 检查以下配置：

1. `ai_config` 表是否有正确的 API Key
2. AI 厂商服务是否可用
3. 网络是否正常

### Q3: 药品搜索无结果怎么办？

A: 检查 `ai_data/drugs.json` 文件是否存在且有数据。如果不存在，执行数据导出：

```bash
POST /data-export/drugs
```

### Q4: 扩展模块功能不可用怎么办？

A: 执行对应的 SQL 脚本：

```sql
source Data/sql/forum_schema.sql;
source Data/sql/appointment_schema.sql;
source Data/sql/extra_modules_schema.sql;
source Data/sql/rbac_schema.sql;
```

### Q5: 前端页面空白怎么办？

A: 检查以下几点：

1. 后端是否启动（默认端口 21090）
2. 前端 API 地址是否正确（`utils/request.js`）
3. 浏览器控制台是否有错误

### Q6: 如何重置 admin 密码？

A: 在 MySQL 命令行执行：

```sql
UPDATE user SET user_pwd = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH' WHERE user_account = 'admin';
UPDATE user SET is_login = 0 WHERE user_account = 'admin';
```

---

## 关键文件索引

### 后端核心文件

| 文件                                        | 说明         |
| ----------------------------------------- | ---------- |
| `controller/AiController.java`            | AI 对话接口    |
| `controller/UserController.java`          | 用户接口       |
| `controller/DrugController.java`          | 药品接口       |
| `controller/NewsController.java`          | 资讯接口       |
| `controller/AppointmentController.java`   | 医生预约接口     |
| `controller/QuizController.java`          | 健康测验接口     |
| `controller/MallController.java`          | 健康商城接口     |
| `controller/FollowupController.java`      | 患者随访接口     |
| `service/impl/AiServiceImpl.java`         | AI 核心服务    |
| `config/AiConfig.java`                    | AI 多厂商配置   |
| `crm/agent/tool/SearchDrugTool.java`      | AI 药品搜索工具  |
| `crm/agent/tool/SearchKnowledgeTool.java` | AI 知识库检索工具 |
| `crm/agent/tool/WebSearchTool.java`       | AI 联网搜索工具  |

### 前端核心文件

| 文件                                  | 说明        |
| ----------------------------------- | --------- |
| `views/user/AiAnalysis.vue`         | AI 健康分析页面 |
| `views/user/Assistant.vue`          | 网站小助手页面   |
| `views/user/Appointment.vue`        | 医生预约页面    |
| `views/user/Quiz.vue`               | 健康测验页面    |
| `views/user/Mall.vue`               | 健康商城页面    |
| `views/admin/Dashboard.vue`         | 管理端仪表盘    |
| `views/admin/AppointmentManage.vue` | 预约管理页面    |
| `views/admin/QuizManage.vue`         | 测验管理页面    |
| `views/admin/MallManage.vue`         | 商城管理页面    |

### 配置文件

| 文件                         | 说明          |
| -------------------------- | ----------- |
| `application.yml`          | 后端配置文件      |
| `pom.xml`                  | Maven 依赖配置  |
| `package.json`             | 前端依赖配置      |
| `router/index.js`          | 前端路由配置      |
| `styles/design-tokens.css` | 设计系统 tokens |

### 数据文件

| 文件                   | 说明           |
| -------------------- | ------------ |
| `ai_data/drugs.json` | AI 药品数据（55条） |
| `chat_backup/`       | 会话备份目录       |
| `Data/sql/`          | SQL 脚本目录     |

---

## 许可证

MIT License

---

## 致谢

- [程序员晨星](https://space.bilibili.com/1759570621) - 提供前后端项目教程支持

- [DeepSeek](https://www.deepseek.com/) - AI模型API

- [Element Plus](https://element-plus.org/) - UI组件库

- [ECharts](https://echarts.apache.org/) - 数据可视化

---

<div align="center">

**如果这个项目对你有帮助，请给个 Star！**

</div>
