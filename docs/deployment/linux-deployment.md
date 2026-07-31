# 智康云健康管理系统 - Linux 部署文档

## 1. 服务器环境要求

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| CentOS/Ubuntu | 7+ / 18+ | 操作系统 |
| JDK | 1.8+ | Java 运行环境 |
| MySQL | 5.7+ / 8.x | 数据库 |
| Nginx | 1.18+ | 反向代理 |
| Node.js | 16+ | 前端构建（可选） |

## 2. 环境搭建

### 2.1 安装 JDK
```bash
# CentOS
yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel

# Ubuntu
apt install -y openjdk-8-jdk

# 验证
java -version
```

### 2.2 安装 MySQL
```bash
# CentOS
yum install -y mysql-server
systemctl start mysqld
systemctl enable mysqld

# Ubuntu
apt install -y mysql-server
systemctl start mysql
systemctl enable mysql

# 安全初始化
mysql_secure_installation

# 创建数据库
mysql -u root -p
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
```

### 2.3 安装 Nginx
```bash
# CentOS
yum install -y nginx
systemctl start nginx
systemctl enable nginx

# Ubuntu
apt install -y nginx
systemctl start nginx
systemctl enable nginx
```

## 3. 项目部署

### 3.1 上传项目文件
```bash
# 创建目录
mkdir -p /opt/zhikangyun

# 上传文件（使用 scp 或 FTP）
scp -r 后端/personal-health-api root@server:/opt/zhikangyun/backend
scp -r 前端/personal-heath-view/dist root@server:/opt/zhikangyun/frontend
scp -r Data/sql root@server:/opt/zhikangyun/sql
```

### 3.2 初始化数据库
```bash
mysql -u root -p personal_health < /opt/zhikangyun/sql/deploy/init_database.sql
mysql -u root -p personal_health < /opt/zhikangyun/sql/forum_schema.sql
mysql -u root -p personal_health < /opt/zhikangyun/sql/appointment_schema.sql
mysql -u root -p personal_health < /opt/zhikangyun/sql/extra_modules_schema.sql
```

### 3.3 配置后端
```bash
# 编辑配置文件
vi /opt/zhikangyun/backend/src/main/resources/application.yml

# 修改数据库连接
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/personal_health?characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8
    username: root
    password: your_password
```

### 3.4 启动后端
```bash
cd /opt/zhikangyun/backend

# 方式1：直接运行
nohup java -jar target/personal-health-api.jar > app.log 2>&1 &

# 方式2：使用 Maven
nohup mvn spring-boot:run > app.log 2>&1 &

# 查看日志
tail -f app.log

# 验证启动
curl http://localhost:21090/api/personal-health/v1.0/user/login
```

### 3.5 配置 Nginx
```nginx
# /etc/nginx/conf.d/zhikangyun.conf
server {
    listen 80;
    server_name your-domain.com;  # 或 IP 地址

    # 前端静态文件
    location / {
        root /opt/zhikangyun/frontend;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://localhost:21090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        root /opt/zhikangyun/frontend;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}
```

```bash
# 测试配置
nginx -t

# 重载配置
nginx -s reload
```

## 4. 防火墙配置

```bash
# 开放端口
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --permanent --add-port=21090/tcp
firewall-cmd --reload

# 或使用 iptables
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
iptables -A INPUT -p tcp --dport 21090 -j ACCEPT
```

## 5. 系统服务配置

### 5.1 创建后端服务
```bash
# /etc/systemd/system/zhikangyun.service
[Unit]
Description=ZhiKangYun Health Management System
After=syslog.target network.target mysql.service

[Service]
User=root
WorkingDirectory=/opt/zhikangyun/backend
ExecStart=/usr/bin/java -jar target/personal-health-api.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 启用服务
systemctl daemon-reload
systemctl enable zhikangyun
systemctl start zhikangyun
systemctl status zhikangyun
```

## 6. 访问验证

### 6.1 访问地址
- **前端**: http://your-domain.com 或 http://your-ip
- **后端 API**: http://your-domain.com/api/personal-health/v1.0

### 6.2 默认账号
- **管理员**: admin / 123456
- **普通用户**: user / 123456

### 6.3 功能验证
1. 访问登录页，使用管理员账号登录
2. 进入管理后台，检查各功能模块
3. 切换到用户端，测试论坛、预约、测验等功能

## 7. 常见问题

### 7.1 端口被占用
```bash
# 查看端口占用
netstat -tlnp | grep 21090
lsof -i :21090

# 杀死进程
kill -9 PID
```

### 7.2 数据库连接失败
```bash
# 检查 MySQL 状态
systemctl status mysqld

# 检查防火墙
firewall-cmd --list-all

# 测试连接
mysql -u root -p -h localhost
```

### 7.3 前端白屏
```bash
# 检查 Nginx 配置
nginx -t

# 检查文件权限
ls -la /opt/zhikangyun/frontend

# 查看 Nginx 日志
tail -f /var/log/nginx/error.log
```

### 7.4 内存不足
```bash
# 查看内存
free -h

# 增加 Swap
dd if=/dev/zero of=/swapfile bs=1G count=2
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

## 8. 备份策略

### 8.1 数据库备份
```bash
# 每日备份脚本
#!/bin/bash
DATE=$(date +%Y%m%d)
mysqldump -u root -p personal_health > /backup/db_$DATE.sql
find /backup -name "db_*.sql" -mtime +7 -delete
```

### 8.2 文件备份
```bash
# 备份项目文件
tar -czf /backup/zhikangyun_$DATE.tar.gz /opt/zhikangyun
```

## 9. 监控命令

```bash
# 查看后端日志
tail -f /opt/zhikangyun/backend/app.log

# 查看系统资源
top
df -h
free -h

# 查看网络连接
netstat -tlnp
ss -tlnp
```
