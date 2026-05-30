# 个人健康管理系统 - 待办任务清单

> 生成时间: 2026-05-29
> 状态说明: ✅ 已完成 | ⏳ 进行中 | ❌ 未开始 | ⚠️ 需验证

---

## 一、环境配置（需用户手动完成）

### 1.1 基础环境安装
| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 1 | 安装 JDK 8+ | ❌ | 推荐 JDK 8 或 JDK 11，下载: https://adoptium.net/ |
| 2 | 安装 Node.js 16+ | ❌ | 下载: https://nodejs.org/ |
| 3 | 安装 MySQL 5.7+ | ❌ | 下载: https://dev.mysql.com/downloads/mysql/ |
| 4 | 安装 Maven 3.6+ | ❌ | 下载: https://maven.apache.org/download.cgi |
| 5 | 安装 IntelliJ IDEA | ❌ | 推荐安装 Lombok 插件 |

### 1.2 数据库配置
| 序号 | 任务 | 状态 | 命令/说明 |
|------|------|------|----------|
| 1 | 启动 MySQL 服务 | ❌ | `net start mysql` |
| 2 | 创建数据库 | ❌ | `CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4;` |
| 3 | 执行初始化脚本 | ❌ | `SOURCE sql/personal_health_init.sql;` |
| 4 | 执行AI升级脚本 | ❌ | `SOURCE sql/ai_chat_upgrade.sql;` |

### 1.3 环境变量配置
| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 1 | 配置 DEEPSEEK_API_KEY | ❌ | DeepSeek API密钥 |
| 2 | 配置 DB_PASSWORD | ❌ | 数据库密码，默认1234 |
| 3 | 配置 JWT_SECRET | ❌ | JWT密钥（至少32字符） |
| 4 | 配置 SERVER_CORS_ORIGINS | ❌ | CORS白名单 |

---

## 二、后端任务

### 2.1 安全修复（阶段1）✅
| 序号 | 任务 | 状态 | 文件 |
|------|------|------|------|
| 1 | 移除API Key硬编码 | ✅ | AiConfig.java |
| 2 | JWT密钥外部化配置 | ✅ | JwtUtil.java |
| 3 | 密码改为BCrypt加密 | ✅ | UserServiceImpl.java |
| 4 | 修复文件路径穿越漏洞 | ✅ | FileController.java |
| 5 | CORS白名单配置 | ✅ | WebConfig.java |
| 6 | 补全@Protector注解（39个接口） | ✅ | 所有Controller |

### 2.2 代码质量（阶段2）✅
| 序号 | 任务 | 状态 | 文件 |
|------|------|------|------|
| 1 | 添加全局异常处理器 | ✅ | GlobalExceptionHandler.java |
| 2 | 合并双重鉴权为单一机制 | ✅ | ProtectorAspect.java |
| 3 | 修复ThreadLocal内存泄漏 | ✅ | ProtectorAspect.java |
| 4 | JwtUtil改为Spring Bean | ✅ | JwtUtil.java, InterceptorConfig.java |
| 5 | OkHttpClient改为单例 | ✅ | AiServiceImpl.java |

### 2.3 功能增强（阶段3）✅
| 序号 | 任务 | 状态 | 文件 |
|------|------|------|------|
| 1 | 分页最大值限制（100条） | ✅ | PagerAspect.java |
| 2 | API响应格式统一 | ✅ | FileController.java |
| 3 | AI对话会话管理 | ✅ | AiConversation相关 |
| 4 | JSON缓存机制 | ✅ | AiChatCacheServiceImpl.java |
| 5 | 用户健康数据注入AI | ✅ | AiServiceImpl.java |

### 2.4 技术升级（阶段4）✅
| 序号 | 任务 | 状态 | 文件 |
|------|------|------|------|
| 1 | Spring Boot 2.2.4 → 2.7.18 | ✅ | pom.xml |
| 2 | MyBatis 2.1.2 → 2.3.2 | ✅ | pom.xml |
| 3 | jjwt 0.9.0 → 0.12.5 | ✅ | pom.xml, JwtUtil.java |
| 4 | 新增spring-boot-starter-validation | ✅ | pom.xml |

### 2.5 待完成任务
| 序号 | 任务 | 状态 | 优先级 | 说明 |
|------|------|------|--------|------|
| 1 | 验证编译通过 | ❌ | 高 | 执行 `mvn clean compile` |
| 2 | 测试所有API接口 | ❌ | 高 | 使用Postman或Apifox |
| 3 | 添加数据导出功能 | ❌ | 中 | 用户健康数据导出CSV/JSON |
| 4 | 添加单元测试 | ❌ | 中 | 创建src/test目录 |
| 5 | 修复naming typo | ❌ | 低 | "heath" → "health" |

---

## 三、前端任务

### 3.1 环境配置
| 序号 | 任务 | 状态 | 命令 |
|------|------|------|------|
| 1 | 安装依赖 | ❌ | `npm install` |
| 2 | 验证启动 | ❌ | `npm run serve` |

### 3.2 待完成任务
| 序号 | 任务 | 状态 | 优先级 | 说明 |
|------|------|------|--------|------|
| 1 | AI对话历史显示 | ❌ | 高 | 显示历史会话列表 |
| 2 | v-html XSS防护 | ❌ | 高 | 使用DOMPurify消毒 |
| 3 | 添加Vuex状态管理 | ❌ | 中 | 替代sessionStorage |
| 4 | 移除console.log | ❌ | 低 | 生产环境清理 |
| 5 | 修复router中的ElementUI引入 | ❌ | 低 | 移到main.js |

---

## 四、部署任务

### 4.1 本地部署
| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 1 | 启动MySQL服务 | ❌ | 确保3306端口可用 |
| 2 | 初始化数据库 | ❌ | 执行SQL脚本 |
| 3 | 配置.env文件 | ❌ | 填入真实密钥 |
| 4 | 编译后端 | ❌ | `mvn clean package -DskipTests` |
| 5 | 启动后端 | ❌ | `java -jar target/*.jar` |
| 6 | 启动前端 | ❌ | `npm run serve` |

### 4.2 生产部署（可选）
| 序号 | 任务 | 状态 | 说明 |
|------|------|------|------|
| 1 | 配置Nginx反向代理 | ❌ | 前后端分离部署 |
| 2 | 配置HTTPS | ❌ | SSL证书 |
| 3 | 配置数据库连接池 | ❌ | HikariCP优化 |
| 4 | 配置日志收集 | ❌ | ELK或文件日志 |
| 5 | 配置定时备份 | ❌ | 数据库自动备份 |

---

## 五、已创建的文件清单

### 5.1 新建文件
```
后端/个人-health-api/src/main/java/cn/kmbeast/
├── config/
│   ├── AiConfig.java
│   ├── AiPromptConfig.java
│   ├── PasswordConfig.java
│   └── GlobalExceptionHandler.java
├── controller/
│   └── AiController.java
├── mapper/
│   ├── AiChatRecordMapper.java
│   └── AiConversationMapper.java
├── pojo/
│   ├── entity/AiChatRecord.java
│   ├── entity/AiConversation.java
│   ├── dto/update/AiChatRequest.java
│   ├── dto/query/extend/AiChatRecordQueryDto.java
│   └── vo/AiStatsVO.java
└── service/
    ├── AiService.java
    ├── AiHealthDataService.java
    ├── AiChatCacheService.java
    └── impl/
        ├── AiServiceImpl.java
        ├── AiHealthDataServiceImpl.java
        └── AiChatCacheServiceImpl.java

后端/个人-health-api/src/main/resources/mapper/
├── AiChatRecordMapper.xml
└── AiConversationMapper.xml

sql/
├── personal_health_init.sql
└── ai_chat_upgrade.sql

前端/personal-health-view/src/views/
├── user/AiAnalysis.vue
└── admin/AiAnalysis.vue

根目录/
├── .env
├── ENVIRONMENT.md
├── TODO.md（本文件）
├── 启动后端.bat
├── 启动前端.bat
└── 一键启动.bat
```

### 5.2 修改文件
```
后端/个人-health-api/
├── pom.xml
├── src/main/resources/application.yml
└── src/main/java/cn/kmbeast/
    ├── utils/JwtUtil.java
    ├── config/WebConfig.java
    ├── config/InterceptorConfig.java
    ├── aop/ProtectorAspect.java
    ├── aop/PagerAspect.java
    ├── Interceptor/JwtInterceptor.java
    ├── controller/UserController.java
    ├── controller/UserHealthController.java
    ├── controller/MessageController.java
    ├── controller/NewsController.java
    ├── controller/TagsController.java
    ├── controller/NewsSaveController.java
    ├── controller/EvaluationsController.java
    ├── controller/HealthModelConfigController.java
    ├── controller/ViewsController.java
    ├── controller/FileController.java
    ├── mapper/UserMapper.java
    ├── mapper/UserHealthMapper.java
    ├── mapper/HealthModelConfigMapper.java
    ├── service/impl/UserServiceImpl.java
    └── resources/mapper/*.xml（多个）

前端/personal-health-view/src/
├── router/index.js
└── views/user/AiAnalysis.vue
    views/admin/AiAnalysis.vue
```

---

## 六、快速启动命令

```bash
# 1. 进入项目根目录
cd D:\Program\个人健康管理系统

# 2. 配置环境变量（编辑.env文件）

# 3. 初始化数据库
mysql -u root -p < sql/personal_health_init.sql
mysql -u root -p personal_health < sql/ai_chat_upgrade.sql

# 4. 启动后端
cd 后端\personal-health-api
mvn clean spring-boot:run

# 5. 启动前端（新终端）
cd ..\..\前端\personal-health-view
npm install
npm run serve

# 或者直接双击"一键启动.bat"
```

---

## 七、默认账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | 123456 |

---

## 八、技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.18 |
| ORM | MyBatis | 2.3.2 |
| 数据库 | MySQL | 5.7+ |
| 密码加密 | BCrypt | Spring Security |
| JWT | jjwt | 0.12.5 |
| AI接口 | DeepSeek API | - |
| 前端框架 | Vue | 2.6 |
| UI库 | Element UI | 2.15 |
| 图表 | ECharts | 4.8 |
