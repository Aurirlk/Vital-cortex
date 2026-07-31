# 智康云健康管理系统 - 项目状态报告

> 更新时间：2026-07-31
> 版本：v5.1

---

## 项目概述

智康云是一个 AI 驱动的全栈健康管理平台，被医院收购后进行了全面升级。v5.1 完成了 AI 智能体（Multi-Agent / ReAct）、混合 RAG、LLM Provider 工厂与韧性、以及安全合规的架构级重构，详见根目录 `../DELIVERY.md`。

## 功能模块清单

### 用户端功能（16 个页面）

| 功能 | 页面文件 | 后端接口 | 状态 |
|------|----------|----------|------|
| 健康资讯 | Home.vue | /news | ✅ |
| 论坛社区 | 整合到资讯 | /post | ✅ 新增 |
| 医生预约 | Appointment.vue | /appointment | ✅ 新增 |
| 健康测验 | Quiz.vue | /quiz | ✅ 新增 |
| 健康商城 | Mall.vue | /mall | ✅ 新增 |
| 患者随访 | Followup.vue | /followup | ✅ 新增 |
| AI 健康分析 | AiAnalysis.vue | /ai | ✅ |
| 药品订阅 | Drug.vue | /drug | ✅ |
| 个人中心 | UserProfile.vue | /user | ✅ 新增 |
| 网站小助手 | Assistant.vue | /ai | ✅ |
| 健康指标 | UserHealthModel.vue | /user-health | ✅ |
| 消息中心 | Message.vue | /message | ✅ |
| 搜索 | Search.vue | /news | ✅ |
| 我的收藏 | NewsSave.vue | /news-save | ✅ |
| 资讯详情 | NewsDetail.vue | /news | ✅ |
| 健康报告 | Report.vue | /report | ✅ |

### 管理端功能（19 个页面）

| 功能 | 页面文件 | 状态 |
|------|----------|------|
| 仪表盘（5模块） | Dashboard.vue | ✅ 重构 |
| 用户管理 | UserManage.vue | ✅ |
| 资讯管理 | NewsManage.vue | ✅ |
| 预约管理 | AppointmentManage.vue | ✅ 新增 |
| 测验管理 | QuizManage.vue | ✅ 新增 |
| 商城管理 | MallManage.vue | ✅ 新增 |
| 随访管理 | FollowupManage.vue | ✅ 新增 |
| AI 配置 | AiAnalysis.vue | ✅ |
| AI 医生管理 | AiDoctorManage.vue | ✅ |
| 药品管理 | DrugManage.vue | ✅ |
| 评论管理 | EvaluationsManage.vue | ✅ |
| 消息管理 | MessageManage.vue | ✅ |
| 资讯分类 | TagsManage.vue | ✅ |
| 健康模型 | HealthModelConfigManage.vue | ✅ |
| 健康记录 | UserHealthManage.vue | ✅ |
| 系统配置 | SystemConfigManage.vue | ✅ |
| RAG 监控 | RagMonitor.vue | ✅ 新增 |
| 审核管理 | 待添加 | ⏳ 待开发 |

### 后端 API（29 个 Controller）

| 模块 | Controller | 接口数 |
|------|------------|--------|
| 用户 | UserController | 5 |
| 论坛 | PostController | 15 |
| 预约 | AppointmentController | 16 |
| 测验 | QuizController | 12 |
| 商城 | MallController | 14 |
| 随访 | FollowupController | 7 |
| 通知 | NotificationController | 5 |
| 权限 | RoleController + PermissionController | 10 |
| 审核 | ContentAuditController | 4 |
| 热门 | HotScoreController | 2 |
| 仪表盘 | DashboardController | 6 |
| PDF | PdfParseController | 1 |
| 药品推荐 | DrugRecommendController | 1 |
| RAG 监控 | RAGEvaluationController | 5 |
| AI | AiController | 8 |
| 其他 | 14 个原有 Controller | ~50 |

### 数据库（30+ 张表）

| 模块 | 表数 | 状态 |
|------|------|------|
| 核心业务 | 15 | ✅ |
| 论坛 | 7 | ✅ 新增 |
| 预约 | 5 | ✅ 新增 |
| 测验 | 6 | ✅ 新增 |
| 商城 | 6 | ✅ 新增 |
| 随访 | 2 | ✅ 新增 |
| RBAC | 4 | ✅ 新增 |
| 审核 | 1 | ✅ 新增 |
| 通知 | 1 | ✅ 新增 |

### core/ 模块（33 个文件）

| 模块 | 文件数 | 说明 |
|------|--------|------|
| agent | 3 | Agent 协调器、记忆服务、控制器 |
| provider | 5 | 服务工厂、熔断器、重试机制 |
| cost | 2 | 成本追踪、定价表 |
| emotion | 2 | 情感分析 |
| guard | 3 | 防端水、信号检测、输出验证 |
| rag | 3 | RAG 管理、混合搜索、重排序 |
| voice | 6 | 语音识别、合成、VAD |
| graph | 5 | 知识图谱 |
| harness | 1 | 数据引导 |
| scoring | 1 | 健康评分 |
| async | 1 | 异步任务 |
| workspace | 1 | Agent 工作空间 |

### 基础设施

| 组件 | 文件 | 状态 |
|------|------|------|
| Docker | Dockerfile.backend, 前端/Dockerfile | ✅ |
| Nginx | 前端/nginx.conf | ✅ |
| Compose | docker-compose.yml | ✅ |
| CI/CD | .github/workflows/ci.yml | ✅ |
| 环境变量 | .env.example | ✅ |

### 文档（8 份 + 根目录交付手册）

| 文档 | 文件 | 状态 |
|------|------|------|
| 需求分析 | requirements-analysis.md | ✅ |
| 数据库设计 | database-design.md | ✅ |
| 后端开发 | backend-development.md | ✅ |
| 前端开发 | frontend-development.md | ✅ |
| 测试文档 | test-report.md | ✅ |
| Linux 部署 | linux-deployment.md | ✅ |
| AI 工具使用 | ai-tool-usage-record.md | ✅ |
| 开发指南 | development-guidelines.md | ✅ |
| 用户交付手册 | `../DELIVERY.md`（根目录） | ✅ v1.1 新增 |

---

## v5.1 架构级改进（2026-07-31）

| 领域 | 交付 |
|------|------|
| Multi-Agent | `AgentCoordinator` 意图词表外部化，6 专科角色路由 |
| ReAct Agent | OpenAI function calling 驱动，工具调用轨迹落库（检查点/审计） |
| Provider 工厂 | `LLMProviderFactory` + `DeepSeekProvider` / `LocalVllmProvider`，运行时切换厂商 |
| 熔断器 | `CircuitBreaker` 429/5xx 重试与快速失败 |
| 混合 RAG | ingestion 管线 + 向量/MySQL LIKE 的 RRF 融合 + 真实 RAGAS 评测 + 引用溯源 |
| 安全 | JWT 外部密钥+启动强校验、CRM API Key fail-closed、`SqlGuard` 只读守卫+租户隔离、DOMPurify、PII 脱敏、token 成本落库 |
| 测试 | 25 个单元测试通过（SqlGuard / ChunkUtil / ToolArgsValidator / DrugServiceImpl） |

## 编译状态

- ✅ 后端编译：BUILD SUCCESS
- ✅ 前端构建：BUILD SUCCESS
- ✅ 无编码损坏

---

## 已知限制

| 限制 | 说明 | 状态 |
|------|------|------|
| Spring Boot 3.x 迁移 | 2.7.18 已于 2023-11 EOL，需独立改造周期（javax→jakarta、Spring Security 6、MyBatis 适配） | 待排期 |
| Spring AI | 依赖 Spring Boot 3.x | 待迁移后 |
| 等保三级测评 | 系统含敏感健康信息，商用前置 | 未开展 |
| 渗透测试 | OWASP Top10 + 越权专项，建议第三方执行 | 未开展 |
| API Key 存储 | 环境变量注入；管理端配置项建议信封加密（KMS） | 已部分 |
| 服务端 ASR/TTS | 语音走浏览器原生 Web Speech API，未实现服务端 | 规划中 |
| 知识图谱 | Neo4j 代码模块就绪，未接入主 RAG 链路（GraphRAG 属新项目） | 规划中 |
| 单元测试 | 已补 25 用例；覆盖率仍偏低，目标 ≥70% | ✅ 已启动 |
| WebSocket 测试 | 代码已写但未实际部署测试 | 待验证 |
| PWA 测试 | manifest.json 已创建但未测试安装 | 待验证 |
| 旧页面 UI | 部分旧页面未按统一风格重构 | 待优化 |

---

## 统计数据

- **后端文件**: 200+ 个 Java 文件
- **前端文件**: 50+ 个 Vue 文件
- **SQL 文件**: 12 个（`Data/sql/` 下，含新增 `ai_usage_schema.sql`）
- **文档**: 15 份（docs/ 下 14 份 + 根目录 `../DELIVERY.md` 用户交付手册）
- **总代码行数**: 约 30,000+ 行
- **单元测试**: 25 个用例（v5.1 新增）
