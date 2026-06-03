# 智康云 — 个人健康管理系统

> AI 驱动的全栈健康管理平台，让健康数据会说话，让 AI 医生常在身边。

基于 Vue 3 + Spring Boot 构建，集成 AI 智能问诊、药品订阅、健康数据追踪、知识库 RAG 检索、联网搜索等功能。系统支持 12 个国内 AI 厂商，通过 ReAct Agent 实现工具增强推理（药品查询、健康数据读取、知识检索、联网搜索、SQL查询）。

---

**致谢：** 非常感谢 B 站大佬 **[程序员晨星](https://space.bilibili.com/1759570621)** 提供的前后端项目教程支持！本项目是在B站大佬程序员晨星分享的代码教程的基础上，增加 AI 智能问诊、CRM ReAct Agent、药品订阅、向量知识库等一系列Agent功能迭代生成的项目，同时优化了前端和后端界面，让网页更加美观和减少了项目运行的部分冗余项。

---

## 技术栈

| 层级  | 技术                                            |
| --- | --------------------------------------------- |
| 前端  | Vue 3 + Element Plus + ECharts + Vue Router   |
| 后端  | Spring Boot 2.7.18 + MyBatis + MySQL + SQLite |
| AI  | 12 个国内厂商（DeepSeek、通义千问、Kimi、GLM 等）            |
| 向量库 | 本地文件向量数据库（余弦相似度检索）                            |
| 认证  | JWT（用户端）+ API Key（管理员端）                       |
| PDF | iText + JFreeChart（健康报告生成）                    |
| RAG | AI 关键词提取 + MySQL LIKE 搜索 + 向量语义检索             |

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

## RAG 知识库检索流程

```
用户输入 → AI意图识别 → 提取关键词（AI模型+本地降级）
                           ↓
                    MySQL LIKE 搜索（标题优先 + 内容匹配）
                           ↓
                    返回 Top6 篇文章（标题匹配优先排序）
                           ↓
                    注入 AI 上下文 → 基于文章生成回答
```

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

- JDK 1.8+
- Maven 3.6+
- Node.js 16+
- MySQL 5.7+ / 8.x

### 1. 数据库初始化

```sql
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE personal_health;

-- 基础表结构
source sql/personal_health_schema.sql;
source 后端/personal-health-api/sql/drug_schema.sql;
source 后端/personal-health-api/sql/ai_chat_schema.sql;
source 后端/personal-health-api/sql/ai_config_schema.sql;

-- 可选数据
source sql/personal_health_data.sql;
```

### 2. 启动后端

```bash
cd 后端/personal-health-api
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd 前端/personal-heath-view
npm install
npm run dev
```

### 4. 首次配置

1. 用 `yangshu/123456` 登录
2. 进入管理员后台 → AI 配置
3. 选择厂商（如 DeepSeek）并输入 API Key
4. 保存配置

---

## 数据库表结构

### 用户表 `user`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 用户ID |
| user_account | VARCHAR(50) | 用户账号（手机号） |
| user_name | VARCHAR(50) | 用户名 |
| user_pwd | VARCHAR(128) | 密码（BCrypt加密） |
| user_role | TINYINT | 角色：1=管理员, 2=用户 |
| user_avatar | VARCHAR(500) | 头像URL |
| user_email | VARCHAR(100) | 邮箱 |
| is_login | TINYINT | 登录状态 |
| is_word | TINYINT | 状态 |
| create_time | DATETIME | 创建时间 |

### 健康资讯表 `news`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 资讯ID |
| name | VARCHAR(200) | 标题 |
| content | TEXT | 内容 |
| tag_id | INT | 分类ID（关联 tags 表） |
| cover | VARCHAR(500) | 封面图URL |
| is_top | TINYINT | 是否置顶 |
| is_banner | TINYINT | 是否轮播图 |
| create_time | DATETIME | 创建时间 |

### 资讯分类表 `tags`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 分类ID |
| name | VARCHAR(50) | 分类名称 |

### 药品表 `drug`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 药品ID |
| name | VARCHAR(200) | 药品名称 |
| generic_name | VARCHAR(200) | 通用名 |
| category | VARCHAR(100) | 分类（感冒药/抗生素/维生素等） |
| description | TEXT | 药品说明 |
| price | DECIMAL(10,2) | 价格 |
| unit | VARCHAR(50) | 单位（盒/瓶/支） |
| specification | VARCHAR(200) | 规格 |
| manufacturer | VARCHAR(200) | 生产厂家 |
| is_otc | TINYINT | 是否OTC（0=处方药, 1=OTC） |
| stock | INT | 库存 |
| status | TINYINT | 状态（0=下架, 1=上架） |

### 药品订阅表 `drug_subscription`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 订阅ID |
| user_id | INT | 用户ID |
| drug_id | INT | 药品ID |
| quantity | INT | 订阅数量 |
| status | TINYINT | 状态（0=取消, 1=有效） |
| create_time | DATETIME | 订阅时间 |

### 健康模型配置表 `health_model_config`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 模型ID |
| name | VARCHAR(50) | 指标名称（如"收缩压"） |
| unit | VARCHAR(20) | 单位（如"mmHg"） |
| symbol | VARCHAR(10) | 符号 |
| value_range | VARCHAR(50) | 正常范围（格式："90,140"） |
| is_global | TINYINT | 是否全局模型 |

### 用户健康记录表 `user_health`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 记录ID |
| user_id | INT | 用户ID |
| health_model_config_id | INT | 健康模型ID |
| value | VARCHAR(50) | 记录值 |
| create_time | DATETIME | 记录时间 |

### AI 会话表 `ai_conversation`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 会话ID |
| user_id | INT | 用户ID |
| title | VARCHAR(255) | 会话标题 |
| agent_type | VARCHAR(50) | AI角色（doctor/nutritionist/consultant等） |
| message_count | INT | 消息数量 |
| last_message_time | DATETIME | 最后消息时间 |
| create_time | DATETIME | 创建时间 |

### AI 聊天记录表 `ai_chat_record`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 记录ID |
| conversation_id | INT | 关联会话ID |
| user_id | INT | 用户ID |
| role | VARCHAR(20) | 角色（user/assistant） |
| content | TEXT | 消息内容 |
| agent_type | VARCHAR(50) | AI角色类型 |
| create_time | DATETIME | 创建时间 |

### AI 配置表 `ai_config`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT, 主键 | 配置ID |
| config_key | VARCHAR(100) | 配置键（如 api_key, model） |
| config_value | TEXT | 配置值（API Key 明文存储） |
| description | VARCHAR(255) | 配置描述 |
| create_time | DATETIME | 创建时间 |

### 评论表 `evaluations` / 消息表 `message`

| 表名 | 说明 |
|------|------|
| evaluations | 用户评论/评价记录 |
| message | 系统消息通知 |

---

## 目录结构

```
智康云-健康管理系统/
├── 前端/personal-heath-view/
│   └── src/views/user/
│       ├── AiAnalysis.vue      # AI 健康分析（6角色+意图识别）
│       ├── Assistant.vue        # 网站小助手（意图分流对话）
│       ├── UserHealthModel.vue  # 健康数据管理
│       └── Drug.vue             # 药品订阅
│
├── 后端/personal-health-api/
│   └── src/main/java/cn/kmbeast/
│       ├── service/impl/
│       │   ├── AiServiceImpl.java           # AI 核心服务
│       │   ├── AiHealthDataServiceImpl.java # 健康数据 JSON 格式
│       │   ├── DataExportServiceImpl.java   # 数据导出
│       │   └── DifyWorkflowServiceImpl.java # AI 关键词提取
│       ├── crm/agent/tool/                  # 6 个工具
│       ├── config/
│       │   ├── AiConfig.java                # AI 多厂商配置
│       │   └── AiPromptConfig.java          # 角色提示词配置
│       └── sql/
│           ├── ai_chat_schema.sql
│           ├── ai_config_schema.sql
│           └── drug_schema.sql
│
├── ai_data/                    # AI 数据文件（自动导出）
│   ├── drugs.json              # 55 种药品
│   └── health/user_{id}.json   # 用户健康指标
│
├── chat_backup/                # 会话备份
│   ├── history_speak/{userId}/ # 历史会话
│   └── user_health/{userId}/   # 健康数据
│
└── .gitignore                  # 排除敏感文件
```

---

## 注意事项

1. **Java 8 兼容** — 代码不使用 `var`、`Map.of()` 等 Java 9+ 特性
2. **AI 配置** — 通过管理员后台配置 API Key，保存到 MySQL，重启不丢失
3. **数据导出** — 药品和健康数据需先调用导出接口，AI 工具才能读取
4. **敏感文件** — `.gitignore` 已排除 `application.yml`、`ai_data/`、`chat_backup/` 等
5. **关键词提取** — AI 内置医学 NLP Prompt，非医学查询返回"无"

---

## 更新日志

### v4.1 (2026-06-03)

**新功能：**

- ✅ 网站小助手：独立对话页面 + 4 类意图识别（病情查询/医生推荐/药品介绍/健康知识）
- ✅ 病情查询：联网搜索最新医疗信息
- ✅ 医生推荐：关键词智能匹配，推荐最相关的 1-3 位 AI 医生
- ✅ 药品介绍：调用 55 种药品数据库
- ✅ 健康知识：AI + 知识库 RAG 检索，强制基于文章回答
- ✅ AI 关键词提取：内置医学 NLP Prompt，优化 RAG 搜索精准度

**优化：**

- ✅ 搜索结果卡片布局优化（span=4→6，图片高度增加）
- ✅ AI 医生角色图标统一为 Element Plus 矢量图标
- ✅ RAG 文章数量从 3 篇提升到 6 篇，标题匹配优先排序
- ✅ AI 配置持久化到 MySQL（删除 AES 加密，改为明文存储）
- ✅ 历史会话用户隔离存储（chat_backup/history_speak/{userId}/）

**修复：**

- ✅ 修复 Vue 响应式丢失导致 AI 回复空白的问题（splice 替换）
- ✅ 修复分页查询偏移量重复计算
- ✅ 修复药品数据未注入 AI 上下文的问题
- ✅ 移除 Dify 外部 API 依赖，关键词提取完全本地化
- 移除了那个bug居多的悬浮球

### v4.0 (2026-06-02)

**新功能：**

- ✅ 健康助手悬浮球（可拖拽移动，快捷咨询）

- ✅ 健康数据 JSON 导入导出

- ✅ AI 配置持久化到 MySQL（AES加密，重启不丢失）

- ✅ SaaS 风格功能按钮栏（联网搜索、深度思考、知识库、健康数据）

- ✅ Markdown 渲染支持

- ✅ 数据导出服务（药品、健康指标导出为JSON供AI读取）

- ✅ 会话元数据记录（联网搜索、知识库等状态）

- 增加了dify的关键词搜索功能

**优化：**

- ✅ 数据流重构：MySQL为主存储，JSON为备份/导出

- ✅ AI工具从JSON文件读取数据（药品、健康指标）

- ✅ 对话缓存机制优化

- ✅ 用户界面布局优化（三栏布局：角色+聊天+设置）

- ✅ 生成设置移至右侧边栏

**安全：**

- ✅ API Key 加密存储到数据库

- ✅ .gitignore 排除敏感文件

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

**⭐ 如果这个项目对你有帮助，请给个 Star！⭐**

</div>
