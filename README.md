# 智康云 — 个人健康管理系统

> AI 驱动的全栈健康管理平台，让健康数据会说话，让 AI 医生常在身边。

基于 Vue 3 + Spring Boot 构建，集成 AI 智能问诊、CRM 健康助理、药品订阅、健康数据追踪等功能。系统采用双 AI 架构——用户端 AI 医生提供专业医疗建议，CRM 助理通过 ReAct Agent 实现工具增强推理，支持药品查询、知识检索和健康数据读取。

---

**致谢：** 非常感谢 B 站大佬 **[程序员晨星](https://space.bilibili.com/1759570621)** 提供的前后端项目教程支持！本项目是在程序员晨星分享的代码教程的基础上，增加 AI 智能问诊、CRM ReAct Agent、药品订阅、向量知识库等一系列Agent功能迭代生成的项目，同时优化了前端和后端界面，让他更加美观和减少了项目运行的部分冗余项。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Element Plus + ECharts + Vue Router |
| 后端 | Spring Boot 2.7.18 + MyBatis + MySQL + SQLite |
| AI | 多厂商支持（DeepSeek、通义千问、Kimi、GLM等） |
| 向量库 | 本地文件向量数据库（余弦相似度检索） |
| 认证 | JWT（用户端）+ API Key（CRM端） |
| PDF | iText + JFreeChart（健康报告生成） |

## 功能模块

### 用户端

- **健康资讯** — 浏览、搜索、收藏健康文章，支持轮播图
- **健康指标** — 自定义健康模型，记录健康数据（血压、血糖、体重等）
- **AI 健康分析** — 5 种 AI 角色（全科医生、营养师、心理咨询师、报告分析师、全能助手），支持 SSE 流式对话、联网搜索、深度思考
- **药品订阅** — 浏览药品信息（价格、说明、分类），订阅关注的药品
- **CRM 智能客服** — 右下角悬浮球，随时与 AI 助手对话
- **健康报告** — 一键生成 PDF 健康报告，包含图表和 AI 建议
- **深色模式** — 支持深色/浅色主题切换
- **消息中心** — 系统通知与提醒

### 管理后台

- **仪表盘** — 用户增长、健康记录等统计图表
- **用户管理** — 用户信息 CRUD
- **资讯管理** — 健康文章 CRUD（富文本编辑器），支持轮播图和置顶设置
- **药品管理** — 药品信息 CRUD，支持 JSON 批量导入
- **AI 配置管理** — 支持多厂商切换，动态配置 API Key、模型等
- **联网搜索配置** — 独立配置搜索引擎（博查AI、Tavily、DuckDuckGo等）
- **AI 医生管理** — 修改 AI 角色的系统提示词、Temperature、Top-P 参数
- **AI 分析** — 管理端 AI 对话工作台
- **评论/消息管理** — 评论审核、消息推送

### CRM 智能助理

- **六大核心功能**：
  1. 药品推荐与价格查询（search_drug 工具）
  2. 推荐 AI 医生角色
  3. 推荐健康资讯文章（向量语义检索）
  4. 联网搜索获取最新信息（web_search 工具）
  5. 读取用户健康档案（get_health_data 工具）
  6. 查询聊天历史（get_chat_history 工具）
- **ReAct Agent** — 工具增强推理，支持 5 轮自主决策
- **本地向量数据库** — 文件存储 + 内存索引，余弦相似度搜索
- **SQLite 聊天记录** — 嵌入式数据库，零外部依赖

---

## AI 多厂商支持

系统支持 12 个国内主流 AI 厂商，管理员可在后台一键切换：

| 厂商 | OpenAI Base URL | 主力模型 |
|------|-----------------|----------|
| **DeepSeek** | https://api.deepseek.com | deepseek-v4-flash, deepseek-v4-pro |
| **Moonshot AI (Kimi)** | https://api.moonshot.cn/v1 | kimi-k2.6 |
| **智谱AI (GLM)** | https://open.bigmodel.cn/api/paas/v4/ | glm-5.1, glm-4.7 |
| **阿里云 (通义千问)** | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen3.7-max, qwen-max, qwen-plus |
| **MiniMax** | https://api.minimaxi.com/v1 | MiniMax-M2.7 |
| **百度 (文心一言)** | https://qianfan.baidubce.com/v2 | ernie-5.0 |
| **字节跳动 (豆包)** | https://ark.cn-beijing.volces.com/api/v3 | doubao-seed-2.0-pro |
| **腾讯 (混元)** | https://api.hunyuan.cloud.tencent.com/v1 | hunyuan-turbo |
| **零一万物 (Yi)** | https://api.lingyiwanwu.com/v1 | yi-large |
| **百川智能** | https://api.baichuan-ai.com/v1 | baichuan-4 |
| **阶跃星辰** | https://api.stepfun.com/v1 | step-2-16k |
| **小米 (MiMo)** | https://api.xiaomimimo.com/v1 | mimo-v2.5 |

---

## 联网搜索支持

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

## 项目结构

```
智康云-健康管理系统/
├── 前端/personal-heath-view/              # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   │   ├── user/                      # 用户页面
│   │   │   └── admin/                     # 管理页面
│   │   ├── components/                    # 公共组件
│   │   │   ├── CustomerServiceBall.vue    # 客服悬浮球
│   │   │   ├── Banner.vue                 # 轮播图
│   │   │   └── ...
│   │   ├── router/                        # 路由配置
│   │   └── assets/css/                    # 样式文件
│
├── 后端/personal-health-api/              # Spring Boot 后端
│   ├── src/main/java/cn/kmbeast/
│   │   ├── controller/                    # REST 接口
│   │   │   ├── AiConfigController.java    # AI配置管理
│   │   │   ├── UserChatController.java    # 用户端聊天
│   │   │   ├── ReportController.java      # PDF报告
│   │   │   └── ...
│   │   ├── service/                       # 业务逻辑
│   │   │   ├── ChartService.java          # JFreeChart图表
│   │   │   ├── PdfReportService.java      # PDF生成
│   │   │   └── ...
│   │   ├── crm/                           # CRM 模块
│   │   │   ├── agent/
│   │   │   │   ├── ReActAgent.java        # ReAct Agent
│   │   │   │   ├── StreamingReActAgent.java
│   │   │   │   ├── ToolRegistry.java      # 工具注册
│   │   │   │   └── tool/                  # 工具实现
│   │   │   │       ├── SearchDrugTool.java
│   │   │   │       ├── SearchKnowledgeTool.java
│   │   │   │       ├── WebSearchTool.java # 联网搜索
│   │   │   │       └── ...
│   │   │   ├── vectordb/                  # 向量数据库
│   │   │   ├── sqlite/                    # SQLite管理
│   │   │   └── workflow/                  # 工作流
│   │   ├── config/
│   │   │   ├── AiConfig.java              # AI多厂商配置
│   │   │   └── ...
│   │   └── pojo/                          # 实体类
│   └── src/main/resources/
│       ├── application.yml                # 配置文件
│       └── mapper/                        # MyBatis XML
│
└── sql/                                   # 数据库脚本
    ├── personal_health_schema.sql         # 建表脚本
    ├── personal_health_data.sql           # 数据导入
    ├── ai_chat_upgrade_safe.sql           # AI聊天升级
    └── README.md                          # SQL说明
```

---

## 数据库表结构

| 表名 | 说明 |
|------|------|
| user | 用户表 |
| news | 健康资讯 |
| tags | 资讯分类 |
| news_save | 收藏记录 |
| evaluations | 评论 |
| message | 消息通知 |
| health_model_config | 健康模型配置 |
| user_health | 用户健康记录 |
| ai_conversation | AI 会话 |
| ai_chat_record | AI 聊天记录 |
| drug | 药品信息 |
| drug_subscription | 药品订阅记录 |

---

## 快速启动

### 环境要求

- JDK 1.8+
- Maven 3.6+
- Node.js 16+
- MySQL 5.7+ / 8.x

### 1. 数据库初始化

```sql
-- 创建数据库
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE personal_health;

-- 执行建表脚本
source sql/personal_health_schema.sql;

-- 导入数据（可选）
source sql/personal_health_data.sql;

-- 药品数据（可选）
source 后端/personal-health-api/sql/drug_schema.sql;
```

### 2. 启动后端

```bash
cd 后端/personal-health-api
mvn spring-boot:run
```

后端运行在 `http://localhost:21090`

### 3. 启动前端

```bash
cd 前端/personal-heath-view
npm install
npm run serve
```

前端运行在 `http://localhost:21091`

---

## 默认账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | 123456 |
| 普通用户 | user | 123456 |

---

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` | MySQL 主机 | localhost |
| `DB_PORT` | MySQL 端口 | 3306 |
| `DB_NAME` | 数据库名 | personal_health |
| `DB_USERNAME` | MySQL 用户 | root |
| `DB_PASSWORD` | MySQL 密码 | 1234 |

**注意：** AI相关的API Key通过管理员后台配置，不再使用环境变量。

---

## 配置说明

核心配置文件：`后端/personal-health-api/src/main/resources/application.yml`

```yaml
# AI配置（支持多厂商）
ai:
  provider: deepseek  # 可选: deepseek, moonshot, zhipu, qwen, minimax, baidu, bytedance, tencent, 01ai, baichuan, stepfun, xiaomi
  chat:
    api-key: ${AI_CHAT_API_KEY:}
    model: deepseek-v4-flash
  reasoner:
    model: deepseek-v4-pro
  websearch:
    enabled: true
    provider: auto  # 可选: auto, bocha, tavily, duckduckgo, serper, serpapi
    bocha:
      api-key: ${BOCHA_API_KEY:}
    tavily:
      api-key: ${TAVILY_API_KEY:}

# CRM 配置
crm:
  react:
    max-rounds: 5
    temperature: 0.3
    tool-timeout-seconds: 30
```

---

## API 接口

### 用户接口（需JWT认证）

| 模块 | 方法 | 接口 | 说明 |
|------|------|------|------|
| 用户 | POST | `/user/login` | 登录 |
| 用户 | POST | `/user/register` | 注册 |
| 资讯 | POST | `/news/query` | 查询资讯 |
| 健康 | POST | `/user-health/save` | 保存健康记录 |
| 药品 | POST | `/drug/query` | 查询药品 |
| 药品 | POST | `/drug/subscribe/{id}` | 订阅药品 |
| AI | POST | `/ai/chat` | AI对话 |
| AI | POST | `/ai/chat/stream` | AI流式对话 |
| 聊天 | POST | `/user/chat/stream` | 客服流式对话 |
| 报告 | GET | `/report/health-pdf` | 下载健康报告 |

### 管理员接口（需管理员角色）

| 模块 | 方法 | 接口 | 说明 |
|------|------|------|------|
| AI配置 | GET | `/ai/config/get` | 获取AI配置 |
| AI配置 | GET | `/ai/config/providers` | 获取厂商列表 |
| AI配置 | POST | `/ai/config/update` | 更新AI配置 |
| AI配置 | POST | `/ai/config/switch-provider` | 切换厂商 |
| 药品 | POST | `/drug/save` | 新增药品 |
| 药品 | PUT | `/drug/update` | 修改药品 |
| 药品 | POST | `/drug/batchDelete` | 删除药品 |
| 资讯 | POST | `/news/save` | 新增资讯 |
| 资讯 | PUT | `/news/update` | 修改资讯 |

### CRM接口（需API Key）

| 方法 | 接口 | 说明 |
|------|------|------|
| POST | `/crm/chat` | CRM对话 |
| POST | `/crm/chat/stream` | CRM流式对话 |
| GET | `/crm/history/{phone}` | 聊天历史 |
| POST | `/crm/vectordb/upsert` | 向量导入 |
| POST | `/crm/vectordb/search` | 向量搜索 |

---

## 注意事项

1. **Java 8 兼容** — 代码不使用 `var`、`Map.of()`、`List.of()` 等 Java 9+ 特性
2. **JWT 豁免** — `/crm/**` 路径不走 JWT，使用 `X-CRM-API-Key` 头认证
3. **首次启动** — 后端会自动创建 `crm_data/` 目录（SQLite + 向量库）
4. **AI配置** — 通过管理员后台配置API Key，支持多厂商切换
5. **联网搜索** — 需要配置搜索引擎API Key（推荐博查AI）
6. **PDF报告** — 使用JFreeChart生成图表，iText生成PDF
7. **图片存储** — 图片存储在 `后端/personal-health-api/pic/` 目录

---

## 更新日志

### v3.0 (2026-06-01)

**新功能：**
- ✅ 多AI厂商支持（12个国内厂商）
- ✅ 联网搜索多引擎支持（6种搜索引擎）
- ✅ DeepSeek v4模型支持
- ✅ AI厂商一键切换
- ✅ 联网搜索配置独立界面

**优化：**
- ✅ 更新DeepSeek模型为v4版本
- ✅ 优化AI配置管理界面
- ✅ 完善厂商配置信息

### v2.0 (2026-05-31)

**新功能：**
- ✅ 联网搜索（博查AI + Tavily主备方案）
- ✅ 深度思考（DeepSeek Reasoner模型）
- ✅ CRM客服悬浮球（用户端）
- ✅ PDF健康报告生成（JFreeChart + iText）
- ✅ 管理员AI配置管理（动态配置API Key）
- ✅ 药品JSON批量导入
- ✅ 深色模式支持

**优化：**
- ✅ 轮播图白点指示器
- ✅ 健康指标图表优化
- ✅ 收藏页面布局优化
- ✅ 资讯管理界面优化

**修复：**
- ✅ 修复ECharts中文显示问题
- ✅ 修复图片URL路径问题
- ✅ 修复Java 8兼容性问题

### v1.0 (2024-07)

- ✅ 基础功能上线
- ✅ AI智能问诊
- ✅ CRM智能客服
- ✅ 健康数据追踪

---

## 贡献指南

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

---

## 许可证

本项目基于 MIT 许可证开源 - 详见 [LICENSE](LICENSE) 文件

---

## 致谢

- [程序员晨星](https://space.bilibili.com/1759570621) - 提供前后端项目教程支持
- [DeepSeek](https://www.deepseek.com/) - AI模型API
- [Element Plus](https://element-plus.org/) - UI组件库
- [ECharts](https://echarts.apache.org/) - 数据可视化

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star！⭐**

</div>
