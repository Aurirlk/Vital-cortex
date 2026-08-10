# 安全加固设计

> 版本：v5.1（2026-07-31）
> 代码位置：`后端/.../utils/JwtUtil.java`、`后端/.../Interceptor/CrmApiKeyInterceptor.java`、`后端/.../crm/sqlite/SqlGuard.java`、`前端/.../utils/sanitize.js`、`后端/.../service/impl/AiServiceImpl.java`
> 部署与配置基线详见根目录 `../DELIVERY.md` §5 / §7

---

## 1. 认证与鉴权

### 1.1 JWT（用户端）

- `JwtUtil`：签名密钥**必须外部注入**（`JWT_SECRET` 环境变量或 `jwt.secret` 配置）；
- **启动强校验**：密钥缺失或强度不足（<32 字节）直接拒绝启动，杜绝默认/弱密钥上线；
- 默认有效期 7 天；用户密码采用 BCrypt 加盐哈希。

### 1.2 CRM API Key（机器接口 `/crm/**`）

`CrmApiKeyInterceptor`（`Interceptor/`）统一 **fail-closed** 拦截（原校验散落在各 Controller 方法，曾漏写导致匿名遍历手机号拖走全平台问诊记录——SEC-02）：

- 读取请求头 `X-CRM-API-Key`，与 `CrmConfig.getCrmApiKey()` 做**定长时间比较**（防时序爆破）；
- 服务端未配置密钥 → 一律拒绝，绝不退化为「无需认证」；
- 仅白名单放行 `/crm/health`（注册于 `config/InterceptorConfig`）；
- 设计原则：**新增接口默认受保护**，除非显式加入白名单。

---

## 2. 数据访问守卫

### 2.1 SqlGuard（只读 SQL 守卫）

`crm/sqlite/SqlGuard` 对 `execute_sql` 工具做词法校验：

| 规则 | 说明 |
|------|------|
| 单语句 | 仅允许单条 `SELECT` / `WITH`，分号即拒（阻断多语句注入） |
| 关键词黑名单 | 拦截 INSERT / UPDATE / DELETE / DROP / ATTACH 等写操作 |
| 禁子查询嵌套 | 含嵌套 `SELECT` / `WITH` 视为注入风险，拒绝 |
| **租户隔离兜底** | 查询 `chat_history` 时，顶层 `WHERE` 必须 `phone_number = '当前会话手机号'`，否则拒绝——LLM 只能看到本用户记录 |

覆盖 10 个单元测试，真实抓出 3 个绕过漏洞并已修复。

### 2.2 IDOR 会话归属

`AiChatCacheServiceImpl.isOwnedBy()`（SEC-03）：以 MySQL `user_id` 为准校验会话归属，缓存命中时也校验缓存对象自带正确 `userId`，防止越权访问他人对话。

---

## 3. 内容安全

### 3.1 XSS 防护

AI 输出经前端 `utils/sanitize.js`（`DOMPurify` 封装，`sanitizeHtml`）白名单净化后再注入 DOM。`AiAnalysis.vue`、`Assistant.vue`、`NewsDetail.vue`、`CustomerServiceBall.vue`、`NavAssistant.vue` 均已接入。

### 3.2 PII 出境脱敏

`AiServiceImpl.maskProfileForExternal()`（SEC-12）：健康档案发送到第三方 LLM 前，
- 剥离联系方式类字段：`phone` / `phoneNumber` / `idCard` / `idCardNo` / `email` / `address`；
- 姓名打码（保留首字，其余替换 `*`）；
- 仅保留健康指标，避免敏感个人信息出境。

### 3.3 输出校验

`core/guard/OutputValidator`：`containsSensitiveInfo()`（敏感信息检测）、`needDisclaimer()`（医疗免责声明提示）。

---

## 4. LLM 韧性

Provider 工厂 + 轻量熔断器（`CircuitBreaker`）：上游 429 / 5xx 自动重试，OPEN 状态快速失败，避免上游故障拖垮主链路。

---

## 5. 安全配置基线（摘要）

| 项 | 现状 |
|----|------|
| 密码存储 | BCrypt 加盐哈希 |
| 登录令牌 | JWT（密钥外部注入 + 启动强校验，默认 7 天） |
| 会话越权 | AI 会话 / 文件上传归属校验（IDOR 已修复） |
| CRM 接口 | API Key 认证 + 强制租户隔离 |
| SQL 注入 | 只读 `SqlGuard`（词法校验 + 黑名单 + 禁子查询 + 租户谓词） |
| 文件上传 | 登录态 + 扩展名白名单 + 魔数校验 + 随机文件名（不可枚举） |
| XSS | AI 输出 DOMPurify 白名单净化 |
| 数据出境 | 健康档案发送第三方前自动 PII 脱敏 |
| 成本可观测 | 每次调用 token 用量落库（`ai_usage` 表） |
| 容器 | 非 root 运行、去默认密码、healthcheck |
| 日志 | 滚动归档，异常不回传前端 |

---

## 6. 合规提醒

平台处理**敏感个人信息**（健康数据），正式商用前建议：

1. 完成**等保三级**测评；
2. 开展**个人信息保护影响评估（PIA）**；
3. 对第三方 AI 厂商的数据出境做合规确认；
4. 上线前由第三方执行渗透测试（OWASP Top10 + 越权专项）。
