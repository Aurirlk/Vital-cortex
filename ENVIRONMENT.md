# 个人健康管理系统 - 环境配置清单

## 一、基础运行环境

### 1. Java 环境
```
JDK 版本: 1.8+ (推荐 JDK 8 或 JDK 11)
检查命令: java -version
下载地址: https://adoptium.net/
```

### 2. Node.js 环境
```
Node.js 版本: 14.x 或 16.x (推荐 16.x LTS)
npm 版本: 6.x 或 8.x
检查命令: node -v && npm -v
下载地址: https://nodejs.org/
```

### 3. MySQL 数据库
```
MySQL 版本: 5.7+ 或 8.0
默认端口: 3306
检查命令: mysql --version
下载地址: https://dev.mysql.com/downloads/mysql/
```

### 4. Maven 构建工具
```
Maven 版本: 3.6+
检查命令: mvn -v
下载地址: https://maven.apache.org/download.cgi
```

---

## 二、后端环境配置

### 1. 数据库初始化
```sql
-- 登录 MySQL 后执行
mysql -u root -p

-- 创建数据库
CREATE DATABASE IF NOT EXISTS personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

-- 执行初始化脚本
SOURCE D:/Program/个人健康管理系统/sql/personal_health_init.sql;

-- 执行 AI 功能升级脚本（如果需要）
SOURCE D:/Program/个人健康管理系统/sql/ai_chat_upgrade.sql;
```

### 2. 环境变量配置
在项目根目录创建 `.env` 文件（已有模板）:
```properties
# DeepSeek AI API
DEEPSEEK_API_KEY=你的API密钥

# 数据库
DB_HOST=localhost
DB_PORT=3306
DB_NAME=personal_health
DB_USERNAME=root
DB_PASSWORD=1234

# JWT密钥
JWT_SECRET=你的JWT密钥(至少32字符)

# CORS白名单
SERVER_CORS_ORIGINS=http://localhost:8080,http://localhost:21091
```

### 3. 编译运行后端
```bash
cd D:\Program\个人健康管理系统\后端\personal-health-api

# 清理并编译
mvn clean compile

# 运行（方式一）
mvn spring-boot:run

# 运行（方式二：打包后运行）
mvn clean package -DskipTests
java -jar target/personal-health-api-1.0-SNAPSHOT.jar
```

后端启动后访问: http://localhost:21090

---

## 三、前端环境配置

### 1. 安装依赖
```bash
cd D:\Program\个人健康管理系统\前端\personal-health-view

# 安装所有依赖
npm install

# 如果安装慢，使用淘宝镜像
npm install --registry=https://registry.npmmirror.com
```

### 2. 启动开发服务器
```bash
npm run serve
```

前端启动后访问: http://localhost:8080

### 3. 构建生产版本
```bash
npm run build
```

---

## 四、默认账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | 123456 |
| 普通用户 | 注册获取 | - |

---

## 五、端口配置

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 API | 21090 | Spring Boot 服务 |
| 前端开发 | 8080 | Vue 开发服务器 |
| MySQL | 3306 | 数据库服务 |

---

## 六、常见问题

### 1. Maven 下载慢
```bash
# 使用阿里云镜像，编辑 ~/.m2/settings.xml 添加:
<mirrors>
    <mirror>
        <id>aliyun</id>
        <mirrorOf>central</mirrorOf>
        <name>Aliyun Maven</name>
        <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
</mirrors>
```

### 2. npm 安装慢
```bash
# 使用淘宝镜像
npm config set registry https://registry.npmmirror.com

# 或使用 cnpm
npm install -g cnpm --registry=https://registry.npmmirror.com
cnpm install
```

### 3. MySQL 连接失败
检查:
- MySQL 服务是否启动
- 用户名密码是否正确
- 数据库 `personal_health` 是否存在
- 端口 3306 是否被占用

### 4. 端口被占用
```bash
# Windows 查看端口占用
netstat -ano | findstr 21090

# 杀掉占用进程
taskkill /PID <进程ID> /F
```

### 5. Lombok 插件
- IntelliJ IDEA 需要安装 Lombok 插件
- Settings → Plugins → 搜索 Lombok → Install

---

## 七、项目目录结构

```
个人健康管理系统/
├── .env                          # 环境变量配置
├── sql/                          # 数据库脚本
│   ├── personal_health_init.sql  # 初始化脚本
│   └── ai_chat_upgrade.sql       # AI功能升级脚本
├── 后端/
│   └── personal-health-api/      # Spring Boot 后端
│       ├── pom.xml               # Maven 配置
│       ├── vector_cache/         # 向量缓存目录
│       └── src/main/
│           ├── java/cn/kmbeast/  # Java 源码
│           └── resources/        # 配置文件
├── 前端/
│   └── personal-health-view/      # Vue 前端
│       ├── package.json          # npm 配置
│       └── src/                  # 前端源码
└── ENVIRONMENT.md                # 本文档
```

---

## 八、技术栈总结

### 后端
- Spring Boot 2.7.18
- MyBatis 2.3.2
- MySQL 5.7+
- Java 8+
- DeepSeek AI API

### 前端
- Vue 2.6
- Element UI 2.15
- Axios 0.21
- ECharts 4.8
- Vue Router 3.2
