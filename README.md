<div align="center">

# 🏥 智康云 — AI 驱动的智慧健康管理平台

**让健康数据会说话，让 AI 医生常在身边**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-green)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2-blue)](https://element-plus.org/)
[![DeepSeek](https://img.shields.io/badge/DeepSeek-API-purple)](https://www.deepseek.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

</div>

---

## 📖 项目简介

智康云是一个基于 **AI-Native** 架构的全栈健康管理平台，将传统医疗信息系统升级为集 **AI 智能问诊、健康数据追踪、药品订阅、CRM 智能客服** 于一体的智慧医疗工作台。

项目采用 **ReAct Agent + RAG** 架构，通过 DeepSeek 大模型驱动，集成药品搜索、健康数据查询、联网搜索、知识检索等工具能力，实现工具增强推理的智能对话系统。

### ✨ 核心亮点

- 🤖 **5种AI角色** — 全科医生、营养师、心理咨询师、报告分析师、全能助手
- 🔍 **联网搜索** — 博查AI + Tavily 主备方案，获取实时信息
- 🧠 **深度思考** — 支持 DeepSeek Reasoner 模型，更深入的推理分析
- 💬 **CRM智能客服** — ReAct Agent + 6个内置工具，工具增强推理
- 📊 **健康可视化** — ECharts图表 + JFreeChart，健康数据一目了然
- 📄 **PDF报告** — 一键生成健康报告，包含图表和AI建议
- 🎯 **RAG知识库** — 本地向量数据库 + Embedding，知识语义检索
- 🌙 **深色模式** — 支持深色/浅色主题切换

---

## 🚀 功能特性

### 👤 用户端

| 功能 | 说明 |
|------|------|
| 🏠 健康资讯 | 浏览、搜索、收藏健康文章，支持轮播图 |
| 📊 健康指标 | 自定义健康模型，记录血压、血糖、体重等数据 |
| 🤖 AI健康分析 | 5种AI角色，支持联网搜索和深度思考 |
| 💊 药品订阅 | 浏览药品信息，订阅关注的药品 |
| 💬 智能客服 | 右下角悬浮球，随时与AI助手对话 |
| 📄 健康报告 | 一键生成PDF健康报告，含图表和建议 |
| 🌙 深色模式 | 支持深色/浅色主题切换 |
| 🔔 消息中心 | 系统通知与提醒 |

### 👨‍💼 管理后台

| 功能 | 说明 |
|------|------|
| 📈 仪表盘 | 用户增长、健康记录等统计图表 |
| 👥 用户管理 | 用户信息CRUD |
| 📰 资讯管理 | 健康文章CRUD，支持轮播图和置顶设置 |
| 💊 药品管理 | 药品信息CRUD，支持JSON批量导入 |
| 🤖 AI配置管理 | 动态配置API Key、模型、联网搜索等 |
| 🩺 AI医生管理 | 修改AI角色提示词、温度参数 |
| 💬 评论管理 | 评论审核 |
| 📨 消息管理 | 消息推送 |

### 🤖 CRM智能客服

| 功能 | 说明 |
|------|------|
| 💊 药品推荐 | 搜索药品信息，推荐合适药品 |
| 🩺 推荐医生 | 根据症状推荐AI医生角色 |
| 📚 知识检索 | 向量语义检索健康知识 |
| 🔍 联网搜索 | 获取最新医疗资讯 |
| 📊 健康数据 | 读取用户健康档案 |
| 💬 流式对话 | SSE实时输出，打字机效果 |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3)                              │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │  Element Plus │  │   ECharts    │  │  SSE 流式对话组件       │  │
│  └──────────────┘  └──────────────┘  └───────────────────────┘  │
└────────────────────────┬────────────────────────────────────────┘
                         │ HTTP / SSE
┌────────────────────────┴────────────────────────────────────────┐
│                   后端 (Spring Boot 2.7.18)                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              CRM 模块 (ReAct Agent)                         │  │
│  │  ┌──────────┐  ┌──────────────┐  ┌─────────────────────┐  │  │
│  │  │ Agent    │  │ ToolRegistry │  │ 6个工具              │  │  │
│  │  │ (5轮推理) │  │              │  │ - search_drug       │  │  │
│  │  └──────────┘  └──────────────┘  │ - search_knowledge  │  │  │
│  │                                  │ - get_health_data   │  │  │
│  │  ┌──────────┐  ┌──────────────┐  │ - get_chat_history  │  │  │
│  │  │ SQLite   │  │  向量数据库    │  │ - execute_sql       │  │  │
│  │  │ (聊天记录)│  │  (Embedding) │  │ - web_search        │  │  │
│  │  └──────────┘  └──────────────┘  └─────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │           业务模块 (用户/资讯/健康/药品/消息)                  │  │
│  │          MySQL + MyBatis + JWT + AOP                       │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端框架** | Vue 3 + Composition API | 3.x |
| **UI组件库** | Element Plus | 2.x |
| **数据可视化** | ECharts | 5.x |
| **路由** | Vue Router | 4.x |
| **HTTP客户端** | Axios | 1.x |
| **后端框架** | Spring Boot | 2.7.18 |
| **ORM** | MyBatis | 2.3.2 |
| **数据库** | MySQL + SQLite | 5.7+ / 8.x |
| **AI模型** | DeepSeek API | Chat + Embedding |
| **向量库** | 本地文件向量数据库 | 自研 |
| **PDF生成** | iText | 7.2.5 |
| **图表生成** | JFreeChart | 1.5.4 |
| **认证** | JWT + API Key | - |
| **构建工具** | Maven + Webpack | - |

---

## 📁 项目结构

```
智康云-健康管理系统/
├── 前端/personal-heath-view/              # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   │   ├── user/                      # 用户页面（10+）
│   │   │   │   ├── Home.vue               # 首页（轮播图+资讯）
│   │   │   │   ├── AiAnalysis.vue         # AI健康分析
│   │   │   │   ├── UserHealthModel.vue    # 健康指标
│   │   │   │   ├── Drug.vue               # 药品订阅
│   │   │   │   └── ...
│   │   │   └── admin/                     # 管理页面（12+）
│   │   │       ├── Main.vue               # 仪表盘
│   │   │       ├── AiAnalysis.vue         # AI分析+配置管理
│   │   │       ├── NewsManage.vue         # 资讯管理
│   │   │       ├── DrugManage.vue         # 药品管理
│   │   │       └── ...
│   │   ├── components/                    # 公共组件
│   │   │   ├── CustomerServiceBall.vue    # 客服悬浮球
│   │   │   ├── Banner.vue                 # 轮播图
│   │   │   ├── LineChart.vue              # 折线图
│   │   │   └── ...
│   │   ├── router/                        # 路由配置
│   │   ├── utils/                         # 工具函数
│   │   └── assets/css/                    # 样式文件
│   └── dist/                              # 构建产物
│
├── 后端/personal-health-api/              # Spring Boot 后端
│   ├── src/main/java/cn/kmbeast/
│   │   ├── controller/                    # REST 接口
│   │   │   ├── AiController.java          # AI聊天接口
│   │   │   ├── AiConfigController.java    # AI配置管理
│   │   │   ├── UserChatController.java    # 用户端聊天
│   │   │   ├── ReportController.java      # PDF报告接口
│   │   │   └── ...
│   │   ├── service/                       # 业务逻辑
│   │   │   ├── ChartService.java          # JFreeChart图表
│   │   │   ├── PdfReportService.java      # PDF生成
│   │   │   └── ...
│   │   ├── crm/                           # CRM 模块
│   │   │   ├── agent/
│   │   │   │   ├── ReActAgent.java        # ReAct Agent
│   │   │   │   ├── StreamingReActAgent.java # 流式Agent
│   │   │   │   ├── ToolRegistry.java      # 工具注册
│   │   │   │   └── tool/                  # 工具实现
│   │   │   │       ├── SearchDrugTool.java
│   │   │   │       ├── SearchKnowledgeTool.java
│   │   │   │       ├── WebSearchTool.java # 联网搜索
│   │   │   │       └── ...
│   │   │   ├── vectordb/                  # 向量数据库
│   │   │   ├── sqlite/                    # SQLite管理
│   │   │   └── workflow/                  # 工作流
│   │   ├── config/                        # 配置类
│   │   │   ├── AiConfig.java              # AI配置
│   │   │   └── ...
│   │   └── pojo/                          # 实体类
│   └── src/main/resources/
│       ├── application.yml                # 配置文件
│       └── mapper/                        # MyBatis XML
│
├── sql/                                   # 数据库脚本
│   ├── personal_health_schema.sql         # 建表脚本
│   ├── personal_health_data.sql           # 数据导入
│   ├── ai_chat_upgrade_safe.sql           # AI聊天升级
│   └── README.md                          # SQL说明
│
├── 开发计划.md                             # 开发计划
├── 测试计划.md                             # 测试计划
└── README.md                              # 项目说明
```

---

## 🚀 快速启动

### 环境要求

| 工具 | 版本要求 |
|------|----------|
| JDK | 1.8+ |
| Maven | 3.6+ |
| Node.js | 16+ |
| MySQL | 5.7+ / 8.x |

### 1. 克隆项目

```bash
git clone https://github.com/your-username/health-management-system.git
cd health-management-system
```

### 2. 数据库初始化

```sql
-- 创建数据库
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

-- 使用数据库
USE personal_health;

-- 执行建表脚本
source sql/personal_health_schema.sql;

-- 导入数据（可选）
source sql/personal_health_data.sql;

-- 药品数据（可选）
source 后端/personal-health-api/sql/drug_schema.sql;
```

### 3. 配置环境变量

```bash
# Windows
set DEEPSEEK_API_KEY=your-api-key

# Linux/Mac
export DEEPSEEK_API_KEY=your-api-key
```

或在 `application.yml` 中直接配置：
```yaml
deepseek:
  api-key: your-api-key
```

### 4. 启动后端

```bash
cd 后端/personal-health-api
mvn clean spring-boot:run
```

后端运行在 `http://localhost:21090`

### 5. 启动前端

```bash
cd 前端/personal-heath-view
npm install
npm run serve
```

前端运行在 `http://localhost:21091`

### 6. 访问系统

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | 123456 |
| 普通用户 | user | 123456 |

---

## ⚙️ 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DEEPSEEK_API_KEY` | DeepSeek API Key | - |
| `DEEPSEEK_REASONER_API_KEY` | 深度思考API Key | 同上 |
| `BOCHA_API_KEY` | 博查AI搜索Key | - |
| `TAVILY_API_KEY` | Tavily搜索Key | - |
| `DB_HOST` | MySQL主机 | localhost |
| `DB_PORT` | MySQL端口 | 3306 |
| `DB_NAME` | 数据库名 | personal_health |
| `DB_USERNAME` | MySQL用户 | root |
| `DB_PASSWORD` | MySQL密码 | 1234 |
| `CRM_API_KEY` | CRM接口密钥 | crm-default-key |

### 核心配置

```yaml
# application.yml

deepseek:
  # 普通对话
  api-key: ${DEEPSEEK_API_KEY:}
  model: deepseek-chat
  
  # 深度思考
  reasoner:
    model: deepseek-reasoner
  
  # 联网搜索
  websearch:
    enabled: true
    provider: bocha  # bocha / tavily
    bocha:
      api-key: ${BOCHA_API_KEY:}
    tavily:
      api-key: ${TAVILY_API_KEY:}
  
  # Embedding
  embedding:
    model: text-embedding-3-small

# CRM 配置
crm:
  react:
    max-rounds: 5
    temperature: 0.3
    tool-timeout-seconds: 30
```

---

## 📡 API 接口

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
| AI配置 | POST | `/ai/config/update` | 更新AI配置 |
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

## 🗄️ 数据库设计

| 表名 | 说明 | 主要字段 |
|------|------|----------|
| `user` | 用户表 | id, account, name, pwd, role |
| `news` | 健康资讯 | id, name, content, cover, is_top, is_banner |
| `tags` | 资讯分类 | id, name |
| `news_save` | 收藏记录 | id, user_id, news_id |
| `evaluations` | 评论 | id, content, user_id |
| `message` | 消息通知 | id, content, user_id |
| `health_model_config` | 健康模型 | id, name, unit, value_range |
| `user_health` | 健康记录 | id, user_id, value, create_time |
| `drug` | 药品信息 | id, name, price, category |
| `drug_subscription` | 药品订阅 | id, user_id, drug_id |
| `ai_conversation` | AI会话 | id, user_id, title |
| `ai_chat_record` | AI聊天记录 | id, conversation_id, role, content |

---

## 🔧 开发指南

### 添加新的AI工具

1. 创建工具类实现 `Tool` 接口：

```java
@Component
public class MyNewTool implements Tool {
    
    @Override
    public String getName() {
        return "my_tool";
    }
    
    @Override
    public String getDescription() {
        return "工具描述";
    }
    
    @Override
    public Map<String, Object> getParametersSchema() {
        // 定义参数schema
    }
    
    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        // 实现工具逻辑
    }
}
```

2. 工具会自动注册到 `ToolRegistry`

### 添加新的页面

1. 在 `src/views/user/` 或 `src/views/admin/` 创建Vue组件
2. 在 `src/router/index.js` 添加路由配置
3. 在菜单组件中添加导航项

---

## 📝 更新日志

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

## 🤝 贡献指南

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

---

## 📄 许可证

本项目基于 MIT 许可证开源 - 详见 [LICENSE](LICENSE) 文件

---

## 🙏 致谢

- [程序员晨星](https://space.bilibili.com/1759570621) - 提供前后端项目教程支持
- [DeepSeek](https://www.deepseek.com/) - AI模型API
- [Element Plus](https://element-plus.org/) - UI组件库
- [ECharts](https://echarts.apache.org/) - 数据可视化

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star！⭐**

</div>
