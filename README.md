# 智康云 — 个人健康管理系统

> AI 驱动的全栈健康管理平台，让健康数据会说话，让 AI 医生常在身边。

基于 Vue 3 + Spring AI 构建，集成 AI 智能问诊、CRM 健康助理、药品订阅、健康数据追踪等功能。系统采用双 AI 架构——用户端 AI 医生提供专业医疗建议，CRM 助理通过 ReAct Agent 实现工具增强推理，支持药品查询、知识检索和健康数据读取。

---

**致谢：** 非常感谢 B 站大佬 **[程序员晨星](https://space.bilibili.com/1759570621)** 提供的前后端项目教程支持！本项目是在程序员晨星分享的代码教程的基础上，增加 AI 智能问诊、CRM ReAct Agent、药品订阅、向量知识库等一系列Agent功能迭代生成的项目，同时优化了前端和后端界面，让他更加美观和减少了项目运行的部分冗余项。

---

## 技术栈

| 层级  | 技术                                            |
| --- | --------------------------------------------- |
| 前端  | Vue 3 + Element Plus + ECharts + Vue Router   |
| 后端  | Spring Boot 2.7.18 + MyBatis + MySQL + SQLite |
| AI  | DeepSeek API（Chat + Embedding）                |
| 向量库 | 本地文件向量数据库（余弦相似度检索）                            |
| 认证  | JWT（用户端）+ API Key（CRM端）                       |

## 功能模块

### 用户端

- **健康资讯** — 浏览、搜索、收藏健康文章
- **健康指标** — 自定义健康模型，记录健康数据（血压、血糖、体重等）
- **AI 健康分析** — 5 种 AI 角色（全科医生、营养师、心理咨询师、报告分析师、全能助手），支持 SSE 流式对话
- **药品订阅** — 浏览药品信息（价格、说明、分类），订阅关注的药品
- **消息中心** — 系统通知与提醒

### 管理后台

- **仪表盘** — 用户增长、健康记录等统计图表
- **用户管理** — 用户信息 CRUD
- **资讯管理** — 健康文章 CRUD（富文本编辑器）
- **药品管理** — 药品信息 CRUD（名称、价格、说明、分类）
- **AI 医生管理** — 修改 AI 角色的系统提示词、Temperature、Top-P 参数，支持密码验证恢复默认
- **AI 分析** — 管理端 AI 对话工作台
- **评论/消息管理** — 评论审核、消息推送

### CRM 智能助理

- **三大核心功能**：
  1. 药品推荐与价格查询（search_drug 工具）
  2. 推荐 AI 医生角色
  3. 推荐健康资讯文章（向量语义检索）
- **ReAct Agent** — 工具增强推理，支持 5 轮自主决策
- **本地向量数据库** — 文件存储 + 内存索引，余弦相似度搜索
- **SQLite 聊天记录** — 嵌入式数据库，零外部依赖

## 项目结构

```
个人健康管理系统/
├── 前端/personal-heath-view/          # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   │   ├── user/                  # 用户页面（9个）
│   │   │   └── admin/                 # 管理页面（11个）
│   │   ├── components/                # 公共组件（11个）
│   │   ├── router/                    # 路由配置
│   │   ├── utils/                     # 工具函数
│   │   └── assets/css/                # 全局样式
│   └── dist/                          # 构建产物
│
├── 后端/personal-health-api/          # Spring Boot 后端
│   ├── src/main/java/cn/kmbeast/
│   │   ├── controller/                # REST 接口（12个）
│   │   ├── service/                   # 业务逻辑
│   │   ├── mapper/                    # MyBatis Mapper（10个）
│   │   ├── pojo/                      # Entity/VO/DTO
│   │   ├── config/                    # 配置类
│   │   ├── crm/                       # CRM 模块（30+文件）
│   │   │   ├── agent/                 # ReAct Agent + 工具
│   │   │   ├── vectordb/              # 本地向量数据库
│   │   │   ├── sqlite/                # SQLite 管理
│   │   │   └── workflow/              # 工作流编排
│   │   └── aop/                       # 切面（分页/权限）
│   └── src/main/resources/
│       ├── application.yml            # 配置文件
│       └── mapper/                    # MyBatis XML
│
└── sql/                               # 数据库脚本
    ├── personal_health_init.sql       # 初始化（建表+数据）
    ├── mysql_schema.sql               # 纯建表语句
    ├── ai_chat_upgrade.sql            # AI聊天表升级
    ├── drug_schema.sql                # 药品表（新增）
    └── README.md                      # SQL脚本说明
```

## 快速启动

### 环境要求

- JDK 1.8+
- Maven 3.6+
- Node.js 16+
- MySQL 5.7+ / 8.x

### 1. 数据库初始化

```sql
-- 创建数据库并初始化
source sql/personal_health_init.sql;
source sql/ai_chat_upgrade.sql;
source 后端/personal-health-api/sql/drug_schema.sql;
```

### 2. 启动后端

```bash
cd 后端/personal-health-api
mvn spring-boot:run
```

后端运行在 `http://localhost:21090/api/personal-health/v1.0`

### 3. 启动前端

```bash
cd 前端/personal-heath-view
npm install
npm run serve
```

前端运行在 `http://localhost:21091`

## 环境变量

| 变量                 | 说明               | 默认值             |
| ------------------ | ---------------- | --------------- |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | 内置默认值           |
| `DB_HOST`          | MySQL 主机         | localhost       |
| `DB_PORT`          | MySQL 端口         | 3306            |
| `DB_NAME`          | 数据库名             | personal-health |
| `DB_USERNAME`      | MySQL 用户         | root            |
| `DB_PASSWORD`      | MySQL 密码         | 1234            |
| `CRM_API_KEY`      | CRM 接口密钥         | crm-default-key |

## API 接口

### 用户接口（需 JWT）

| 模块  | 接口                          | 说明      |
| --- | --------------------------- | ------- |
| 用户  | POST `/user/login`          | 登录      |
| 资讯  | POST `/news/query`          | 查询资讯    |
| 健康  | POST `/user-health/save`    | 保存健康记录  |
| 药品  | POST `/drug/query`          | 查询药品    |
| 药品  | POST `/drug/subscribe/{id}` | 订阅药品    |
| AI  | POST `/ai/chat/stream`      | AI 流式对话 |

### 管理员接口（需管理员角色）

| 模块  | 接口                             | 说明         |
| --- | ------------------------------ | ---------- |
| 药品  | POST `/drug/save`              | 新增药品       |
| 药品  | PUT `/drug/update`             | 修改药品       |
| 药品  | POST `/drug/batchDelete`       | 删除药品       |
| AI  | GET `/ai/config/list`          | 获取 AI 角色配置 |
| AI  | PUT `/ai/config/{role}`        | 修改 AI 角色配置 |
| AI  | POST `/ai/config/{role}/reset` | 重置为默认（需密码） |

### CRM 接口（需 API Key）

| 接口                          | 说明       |
| --------------------------- | -------- |
| POST `/crm/chat/stream`     | CRM 流式对话 |
| GET `/crm/history/{phone}`  | 聊天历史     |
| POST `/crm/vectordb/upsert` | 向量导入     |

## 数据库表结构

| 表名                  | 说明            |
| ------------------- | ------------- |
| user                | 用户表           |
| news                | 健康资讯          |
| tags                | 资讯分类          |
| news_save           | 收藏记录          |
| evaluations         | 评论            |
| message             | 消息通知          |
| health_model_config | 健康模型配置        |
| user_health         | 用户健康记录        |
| ai_conversation     | AI 会话         |
| ai_chat_record      | AI 聊天记录       |
| drug                | 药品信息（15条初始数据） |
| drug_subscription   | 药品订阅记录        |

## 配置说明

核心配置文件：`后端/personal-health-api/src/main/resources/application.yml`

```yaml
# AI 配置
deepseek:
  api-key: ${DEEPSEEK_API_KEY:sk-xxx}
  model: ${DEEPSEEK_MODEL:deepseek-v4-flash}

# CRM 配置
crm:
  react:
    max-rounds: 5          # ReAct 最大推理轮数
    temperature: 0.3        # 默认温度
    tool-timeout-seconds: 30 # 工具执行超时
```

## 默认账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | 123456 |
| 普通用户 | user | 123456 |

## 注意事项

1. **API Key** — DeepSeek API Key 已内置默认值，生产环境请通过环境变量覆盖
2. **JWT 豁免** — `/crm/**` 路径不走 JWT，使用 `X-CRM-API-Key` 头认证
3. **首次启动** — 后端会自动创建 `crm_data/` 目录（SQLite + 向量库）
4. **Java 8 兼容** — 代码不使用 `var`、`Map.of()`、`List.of()` 等 Java 9+ 特性
5. **AI 医生管理** — 管理员可在后台修改 AI 角色提示词，支持密码验证恢复默认
6. **药品功能** — 需先执行 `drug_schema.sql` 创建药品表
