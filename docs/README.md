# 智康云健康管理系统 - 项目文档

> 本目录包含项目开发过程中的所有文档，满足结课报告 7 大模块要求。

## 文档目录

| 文档           | 路径                                      | 说明                                           |
| ------------ | --------------------------------------- | -------------------------------------------- |
| 需求分析         | `requirements-analysis.md` | 功能需求、用例图、流程图                                 |
| 数据库设计        | `database-design.md`           | ER 图、建表 SQL、字段说明                             |
| 后端开发         | `backend-development.md`        | 接口设计、核心代码、架构说明                               |
| 前端开发         | `frontend-development.md`      | 页面设计、组件说明、路由设计                               |
| 测试文档         | `test-report.md`                | 测试用例（49条，含25单测）、测试报告                         |
| Linux部署      | `linux-deployment.md`        | 环境搭建、部署命令、常见问题                               |
| AI工具使用       | `ai-tool-usage-record.md`      | AI工具使用记录（≥500字）                              |
| **AI 智能体架构** | `agent-architecture.md`              | **v5.1 新增** Multi-Agent/ReAct/Provider工厂/熔断器 |
| **RAG 检索增强** | `rag-subsystem.md`                   | **v5.1 新增** ingestion管线/混合检索/RAGAS           |
| **安全加固设计**   | `security-hardening.md`        | **v5.1 新增** JWT/CRM Key/SqlGuard/XSS/脱敏      |
| 项目状态         | `project-status.md`                     | 功能清单、完成状态                                    |
| 开发指南         | `development-guidelines.md`             | 防坑指南、编码规范                                    |
| 用户交付手册       | `../DELIVERY.md`                        | 部署/配置/安全/已知限制（v1.1，架构改进权威说明）                 |

---

## 技术栈

| 层级    | 技术                                                                  |
| ----- | ------------------------------------------------------------------- |
| 前端    | Vue 3 + Element Plus + ECharts + Vue Router                         |
| 后端    | Spring Boot 2.7.18 + MyBatis + MySQL 8 + SQLite（CRM 对话历史），JDK 17 验证 |
| AI    | 12 个国内厂商（DeepSeek、通义千问、Kimi、GLM 等）+ 本地 vLLM 微调模型（可选）                |
| 智能体   | Multi-Agent 协调器 + ReAct Agent（OpenAI function calling，工具轨迹落库）       |
| 认证    | JWT（7 天，密钥外部注入）+ CRM API Key（fail-closed）+ RBAC（5 角色）               |
| 韧性    | LLM Provider 工厂 + 轻量熔断器（429/5xx 重试）                                 |
| RAG   | 本地文件向量库（余弦相似度）+ MySQL LIKE 混合检索（RRF 融合）+ 真实 RAGAS 评测                |
| 安全    | SqlGuard 只读 SQL 守卫（租户隔离）+ DOMPurify XSS 净化 + PII 出境脱敏               |
| 实时通信  | WebSocket 消息通知                                                      |
| 文档解析  | Apache PDFBox                                                       |
| 知识图谱  | Neo4j（代码模块已就绪，尚未接入主链路，属规划项）                                         |
| 向量库   | 本地文件向量数据库（混合检索）                                                     |
| 容器化   | Docker + Docker Compose                                             |
| CI/CD | GitHub Actions                                                      |

---

## 项目结构

```
智康云-健康管理系统/
├── 前端/personal-heath-view/
│   ├── src/
│   │   ├── views/
│   │   │   ├── user/          # 用户端页面（16个）
│   │   │   ├── admin/         # 管理端页面（19个）
│   │   │   ├── login/         # 登录页（左右分栏）
│   │   │   └── register/      # 注册页
│   │   ├── components/        # 公共组件（19个）
│   │   │   ├── BrandLogo.vue  # 品牌Logo
│   │   │   ├── BrandDecoration.vue # 品牌装饰
│   │   │   └── NotificationBell.vue # 消息通知
│   │   ├── styles/            # 样式文件
│   │   │   ├── design-tokens.css   # 设计tokens（柔和灰蓝）
│   │   │   ├── global-overrides.css # 全局样式
│   │   │   └── brand.css           # 品牌样式
│   │   ├── utils/
│   │   │   ├── permission.js  # 权限工具
│   │   │   ├── directives.js  # v-permission指令
│   │   │   └── ws.js          # WebSocket
│   │   └── router/index.js    # 路由配置
│   ├── public/
│   │   ├── manifest.json      # PWA配置
│   │   └── service-worker.js  # Service Worker
│   ├── Dockerfile             # 前端容器化
│   └── nginx.conf             # Nginx配置
│
├── 后端/personal-health-api/
│   ├── src/main/java/cn/kmbeast/
│   │   ├── config/            # 配置类
│   │   ├── controller/        # 控制器（29个）
│   │   ├── service/           # 服务层（34个）
│   │   ├── mapper/            # MyBatis映射（46个）
│   │   ├── pojo/              # 数据模型
│   │   ├── websocket/         # WebSocket
│   │   ├── core/              # 核心模块（33个文件）
│   │   │   ├── agent/         # Agent协调器
│   │   │   ├── provider/      # 服务工厂+熔断器
│   │   │   ├── cost/          # 成本控制
│   │   │   ├── emotion/       # 情感分析
│   │   │   ├── guard/         # 防端水引擎
│   │   │   ├── rag/           # 增强RAG
│   │   │   ├── voice/         # 语音交互
│   │   │   ├── graph/         # 知识图谱
│   │   │   ├── async/         # 异步任务
│   │   │   ├── harness/       # 数据引导
│   │   │   ├── scoring/       # 量化评分
│   │   │   └── workspace/     # Agent工作空间
│   │   └── crm/               # CRM系统
│   └── src/main/resources/
│       └── mapper/            # MyBatis XML（46个）
│
├── Data/sql/
│   ├── deploy/init_database.sql      # 核心表初始化
│   ├── forum_schema.sql              # 论坛模块表
│   ├── appointment_schema.sql        # 医生预约模块表
│   ├── extra_modules_schema.sql      # 测验/商城/随访/分类模块表
│   ├── rbac_schema.sql               # RBAC权限系统表
│   ├── hot_score_algorithm.sql       # 热门算法
│   ├── mock_business_data.sql        # 模拟业务数据
│   ├── audit_log.sql                 # 审计日志表
│   └── migrate_encrypted_data.sql    # 敏感数据迁移
│
├── docs/                         # 项目文档（本目录）
├── Dockerfile.backend            # 后端容器化
├── docker-compose.yml            # 服务编排
├── .env.example                  # 环境变量模板
└── .github/workflows/ci.yml     # CI/CD流水线
```

---

## 功能模块清单

### 用户端（16个页面）

| 功能     | 路由                      | 说明                 |
| ------ | ----------------------- | ------------------ |
| 健康资讯   | /user/news-record       | 浏览、搜索、收藏健康文章       |
| 论坛社区   | 整合到资讯模块                 | 发帖、回帖、点赞、收藏、关注、热榜  |
| 医生预约   | /user/appointment       | 科室→医生→时间→确认预约      |
| 健康测验   | /user/quiz              | 题库浏览、在线答题、自动评分     |
| 健康商城   | /user/mall              | 商品浏览、购物车、下单、模拟支付   |
| 患者随访   | /user/followup          | 任务查看、打卡、记录查看       |
| AI健康分析 | /user/ai-analysis       | 6种AI角色，联网搜索、知识库RAG |
| 药品订阅   | /user/drug              | 药品信息查询与订阅          |
| 个人中心   | /user/profile           | 个人信息、统计、服务菜单       |
| 我的收藏   | /user/my-save           | 收藏的文章和帖子           |
| 健康指标   | /user/user-health-model | 健康数据记录和趋势          |
| 网站小助手  | /user/assistant         | 意图识别智能助理           |
| 消息中心   | /user/message           | 系统通知与提醒            |
| 搜索页    | /user/search-detail     | 全局搜索               |
| 资讯详情   | /user/news-detail       | 文章详情页              |

### 管理端（19个页面）

| 功能     | 路由                             | 说明            |
| ------ | ------------------------------ | ------------- |
| 仪表盘    | /admin/adminLayout             | 5模块数据看板       |
| 用户管理   | /admin/userManage              | 用户CRUD        |
| 资讯管理   | /admin/newsManage              | 健康文章管理        |
| 资讯分类   | /admin/tagsManage              | 标签管理          |
| 预约管理   | /admin/appointmentManage       | 科室/医生/排班/预约管理 |
| 测验管理   | /admin/quizManage              | 题库/试卷管理       |
| 商城管理   | /admin/mallManage              | 商品/订单/分类管理    |
| 随访管理   | /admin/followupManage          | 任务/打卡记录管理     |
| AI配置   | /admin/aiAnalysis              | AI厂商配置        |
| AI医生管理 | /admin/aiDoctorManage          | AI角色管理        |
| 健康模型   | /admin/healthModelConfigManage | 健康指标模型        |
| 健康记录   | /admin/userHealthManage        | 用户健康数据        |
| 药品管理   | /admin/drugManage              | 药品信息管理        |
| 评论管理   | /admin/evaluationsManage       | 评论审核          |
| 消息管理   | /admin/messageManage           | 消息推送          |
| 系统配置   | /admin/systemConfig            | 系统配置管理        |
| RAG监控  | /admin/ragMonitor              | RAG质量监控       |

---

## 数据库表清单（30+张表）

| 模块   | 表名                                                                                      | 说明                |
| ---- | --------------------------------------------------------------------------------------- | ----------------- |
| 核心   | user, tags, news, news_save, evaluations, message                                       | 用户、资讯、评论、消息       |
| 健康   | health_model_config, user_health, patient_profile                                       | 健康指标、用户画像         |
| 药品   | drug, drug_subscription                                                                 | 药品信息、订阅           |
| AI   | ai_conversation, ai_chat_record, ai_config                                              | AI会话、配置           |
| 系统   | system_config, notification                                                             | 系统配置、消息通知         |
| RBAC | role, permission, role_permission, user_role                                            | 角色权限系统            |
| 论坛   | post, post_reply, post_like, post_favorite, user_follow, post_report, post_tag          | 帖子、回复、点赞、收藏、关注、举报 |
| 预约   | department, hospital_doctor, doctor_schedule, appointment, visit_record                 | 科室、医生、排班、预约、就诊    |
| 测验   | quiz_question, quiz_exam, quiz_exam_question, quiz_record, quiz_answer                  | 题库、试卷、考试记录        |
| 商城   | product_category, mall_product, shopping_cart, mall_order, order_item, shipping_address | 分类、商品、购物车、订单      |
| 随访   | followup_task, followup_record                                                          | 随访任务、打卡记录         |
| 审核   | sensitive_word, audit_log                                                               | 敏感词、审计日志          |

---

## API 接口汇总（100+个）

| 模块    | 接口前缀               | 接口数 |
| ----- | ------------------ | --- |
| 用户    | /user              | 5   |
| 论坛    | /post              | 15  |
| 预约    | /appointment       | 16  |
| 测验    | /quiz              | 12  |
| 商城    | /mall              | 14  |
| 随访    | /followup          | 7   |
| 通知    | /notification      | 5   |
| 权限    | /role, /permission | 10  |
| 审核    | /audit             | 4   |
| 热门    | /hot               | 2   |
| 仪表盘   | /dashboard         | 6   |
| PDF   | /pdf               | 1   |
| 药品推荐  | /drug-recommend    | 1   |
| RAG监控 | /rag               | 5   |

---

## 编译状态

- ✅ 后端编译：BUILD SUCCESS
- ✅ 前端构建：BUILD SUCCESS
- ✅ 无编码损坏

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



---

## 近期架构改进（v5.1，2026-07-31）

本轮对 AI 能力做了架构级重构与加固，核心交付如下（详见根目录 `../DELIVERY.md`）：

- **Multi-Agent**：`AgentCoordinator` 意图词表外部化，6 专科角色路由
- **ReAct Agent**：改用 OpenAI function calling，工具调用轨迹落库（检查点/审计）
- **Provider 工厂 + 熔断器**：`DeepSeekProvider` / `LocalVllmProvider` / `CircuitBreaker`，运行时切换厂商、故障快速失败
- **混合 RAG**：ingestion 管线（分块→嵌入→入库）+ 向量/MySQL LIKE 的 RRF 融合 + 真实 RAGAS 评测 + 引用溯源
- **安全**：JWT 外部密钥 + 启动强校验、CRM API Key fail-closed、`SqlGuard` 只读守卫 + 租户隔离、DOMPurify、PII 脱敏、token 成本落库
- **测试**：25 个单元测试通过（原缺单测）

---

## 已知缺陷与限制清单

> 本表为历史缺陷台账，状态随版本推进更新。架构级改进详情见根目录 `../DELIVERY.md` §8。

### 已解决（v5.1 完成）

| 编号    | 原缺陷          | 现状                                                                                    |
| ----- | ------------ | ------------------------------------------------------------------------------------- |
| D-005 | 缺少单元测试       | ✅ 已补 25 个用例（SqlGuard / ChunkUtil / ToolArgsValidator / DrugServiceImpl），`mvn test` 通过 |
| D-009 | 部分接口缺少输入验证   | ✅ CRM 接口加 `CrmApiKeyInterceptor`；`SqlGuard` 只读守卫 + 租户隔离；AI 输出 DOMPurify 净化            |
| D-001 | 部分 SQL 脚本未执行 | ⚠️ 已补充 `Data/sql/ai_usage_schema.sql`（token 成本表），其余业务表脚本仍建议部署时执行                      |

### 仍待处理（保留项）

| 编号    | 缺陷                 | 影响        | 位置              |
| ----- | ------------------ | --------- | --------------- |
| D-002 | admin 账号可能被锁定      | 无法登录管理后台  | `user` 表        |
| D-003 | 旧页面 UI 风格不统一       | 视觉不一致     | `views/`        |
| D-004 | WebSocket 未实际部署测试  | 消息通知可能不可用 | `websocket/`    |
| D-006 | Redis 缓存未完全集成      | 热点数据无缓存   | `service/impl/` |
| D-007 | Prometheus 监控未完全集成 | 无系统健康监控   | `config/`       |
| D-008 | 前端样式不完全统一          | 部分页面样式不一致 | `views/`        |
| D-010 | 错误日志不够详细           | 调试困难      | `service/impl/` |

### 架构级遗留项（见 `../DELIVERY.md` §8.2）

- **Spring Boot 3 迁移**：当前 2.7.18（2023-11 EOL），需独立改造周期（javax→jakarta 等）
- **等保三级测评 / 渗透测试**：未开展，商用前置
- **API Key 存储**：环境变量注入；管理端配置项建议信封加密
- **God Class 拆分**：`AiServiceImpl` 职责过载，已抽离 Provider 层，后续按会话/检索/评测/用量拆 4 服务
- **服务端 ASR/TTS**：未实现（语音走浏览器原生 Web Speech API）
- **向量库规模**：本地文件实现全量扫描，>10 万块时建议迁移 pgvector/Milvus + HNSW
- **知识图谱**：Neo4j 代码模块已就绪，但尚未接入主 RAG 链路（GraphRAG 属新项目）

---

## 改进目标

### 短期（1-2 周）

| 优先级 | 目标              | 说明        |
| --- | --------------- | --------- |
| P0  | 执行所有 SQL 脚本     | 确保扩展模块表存在 |
| P0  | 测试 WebSocket 功能 | 确保消息通知可用  |
| P1  | 统一前端样式          | 小红书风格 UI  |

### 中期（1-2 月）

| 优先级 | 目标               | 说明          |
| --- | ---------------- | ----------- |
| P1  | 旧页面 UI 重构        | 首页、AI分析、药品等 |
| P1  | 完善单元测试           | 覆盖率 ≥ 70%   |
| P2  | 集成 Redis 缓存      | 热点数据缓存      |
| P2  | 集成 Prometheus 监控 | 系统健康监控      |

### 长期（3-6 月）

| 优先级 | 目标                 | 说明           |
| --- | ------------------ | ------------ |
| P2  | Spring Boot 3.x 升级 | 使用 Spring AI |
| P3  | 移动端适配              | 响应式设计完善      |
| P3  | 微信小程序              | 核心功能移植       |

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
| `views/admin/QuizManage.vue`        | 测验管理页面    |
| `views/admin/MallManage.vue`        | 商城管理页面    |

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

## AI 厂商支持

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

## AI 工具列表

| 工具                 | 文件                         | 功能               |
| ------------------ | -------------------------- | ---------------- |
| `search_drug`      | `SearchDrugTool.java`      | 药品搜索，从 JSON 文件读取 |
| `get_health_data`  | `GetHealthDataTool.java`   | 用户健康指标查询         |
| `search_knowledge` | `SearchKnowledgeTool.java` | 知识库语义检索          |
| `web_search`       | `WebSearchTool.java`       | 联网搜索最新信息         |
| `get_chat_history` | `GetChatHistoryTool.java`  | 查询聊天历史           |
| `execute_sql`      | `ExecuteSqlTool.java`      | 只读 SQL 查询        |

---

**总计**: 30+ 张表 | 100+ 个API | 35 个前端页面 | 33+ 个 core 模块 | 14 份 docs 文档 + 根目录 `../DELIVERY.md`（用户交付手册）

> 文档版本：v5.1（2026-07-31）。架构级改进以 `../DELIVERY.md` 为权威说明，本索引仅作导航。其中 `agent-architecture.md`、`rag-subsystem.md`、`security-hardening.md` 为 v5.1 新增子系统深度文档。
