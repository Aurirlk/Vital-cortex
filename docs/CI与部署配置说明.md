# CI / 部署配置说明

> 最后更新：2026-11-09（阶段 10）。本文聚焦 `GitHub Actions` 流水线、secrets 机制、依赖安全门禁，以及本仓库的部署前必配项。
> 完整部署走查见根 `README.md`「部署指南 / 配置说明 / 排障」三章；结构与表数量以 `数据表结构基线-20261003.md` 为准。

---

## 1. GitHub Actions Secrets（不是应用 env）

仓库级 secrets 在 **仓库 → Settings → Secrets and variables → Actions** 里配置，仅对 CI 运行时可见，**不进入容器、不写入镜像、不进 git**。

| Secret 名 | 用途 | 是否必填 |
|---|---|---|
| `SERVER_HOST` | 部署目标服务器 IP / 域名 | 仅「自动部署」需要 |
| `SERVER_USER` | 部署登录用户名 | 仅「自动部署」需要 |
| `SERVER_SSH_KEY` | 部署私钥（用于 `appleboy/ssh-action`） | 仅「自动部署」需要 |
| `GITHUB_TOKEN` | 推镜像到 GHCR 用 | 由 GitHub 自动注入，无需手动配 |
| `ALERT_WEBHOOK_URL` | （可选）告警外发 webhook，注入 `deploy/alertmanager.yml` | 上线前建议配 |

> ⚠️ 应用运行期需要的 `JWT_SECRET` / `CRM_API_KEY` / `AES_SECRET_KEY` / `DB_ROOT_PASSWORD` 等**不是** GitHub Actions secrets，而是 `.env`（docker-compose 读取）里的变量，详见第 4 节。两者不要混淆。

---

## 2. 流水线结构与 deploy 跳过逻辑

定义在 `.github/workflows/ci.yml`，五个 job：

| Job | 作用 | 触发 |
|---|---|---|
| `backend-build` | `mvn clean compile` + **`mvn verify`**（含 `*IT` 启动守护）+ 打 JAR | push / PR |
| `frontend-build` | Node 20 + `npm ci` + lint + `npm run build` + **`npm audit`** | push / PR |
| `docker-build` | 构建并推送 backend / frontend 镜像到 GHCR | 仅 `push` 到 `main` |
| `deploy-preflight` | 探测部署密钥是否齐全 | 仅 `push` 到 `main` |
| `deploy` | 经 SSH 拉最新代码并 `docker-compose up -d` | 仅 `push` 到 `main` 且 preflight 通过 |

### deploy 跳过（关键）

`deploy-preflight` 用前置 job 的 `outputs.ready` 判定（secrets 不能在 job 级 `if` 直接读）：

- 三个部署 secret 齐全 → `ready=true` → `deploy` 执行。
- **任一缺失 → `ready=false` → `deploy` 被 `if` 跳过，并以 `::warning::` 显式告警「未配置 SERVER_HOST / SERVER_USER / SERVER_SSH_KEY，本次不执行部署。镜像已构建并推送到 GHCR」**。

这是有意设计：避免「密钥没配 → ssh 失败 → 整条流水线红」，把「未部署」与「代码有 bug」区分开。未配置密钥时，流水线结论为 `success`，只差「部署」这一步由人工在服务器上 `docker compose pull && up` 完成。

---

## 3. 依赖安全门禁（2026-11-09 阶段 10 新增）

`frontend-build` 在 `npm run build` 之后新增一步：

```yaml
- name: Audit dependencies (CVE scan, official registry)
  working-directory: ./前端/personal-heath-view
  run: npm audit --registry=https://registry.npmjs.org --omit=dev --audit-level=high
```

- **为什么走官方源**：`npm ci` 用 `registry.npmmirror.com`（国内快），但 npmmirror **不实现 audit 端点**（返回 `404 NOT_IMPLEMENTED`）。故 audit 显式走 `registry.npmjs.org`。
- **fail-closed**：仅对**生产依赖**做 `high` 及以上漏洞扫描；命中则流水线失败，逼出修复。
- **配套修复**：`前端/personal-heath-view/package-lock.json` 已订正 `dompurify 3.4.17` / `proxy-addr 2.0.8` / `shell-quote 1.12.0` / `follow-redirects 1.16.3` / `vue 3.5.45` 等，确保无 high 漏洞。改 lock 后必须 `npm ci` 验证（严格一致，否则 `EUSAGE`）。

Node 版本由 `setup-node` 的 `node-version: '20'`（此前为 18）控制；`npm run build` 需 Node 20.19+。

---

## 4. 上线前必配的环境变量（`.env`）

复制 `cp .env.example .env` 后，至少填：

| 变量 | 说明 | 缺省行为 |
|---|---|---|
| `DB_ROOT_PASSWORD` | MySQL root 密码，同时注入后端 `DB_PASS` | `${...:?}` 缺失即拒绝启动 |
| `JWT_SECRET` | 至少 32 字节随机串（`openssl rand -hex 32`） | fail-closed，弱密钥拒绝启动 |
| `CRM_API_KEY` | `/crm/**` 接口密钥 | fail-closed |
| `AES_SECRET_KEY` | AES-256-GCM 加密密钥（加密存储 `api_key` 等敏感配置） | **不设置则回落内置默认密钥并输出 warn**——仅开发用，生产必须设置，否则加密内容可被逆向 |
| `ALERT_WEBHOOK_URL` | 告警外发 webhook（企业微信 / 钉钉 / 飞书机器人） | 不设置则告警只停留在 Alertmanager / Prometheus 页面，不外发 |
| `AI_CHAT_API_KEY` 等 | AI 厂商密钥 | 留空则 AI 对话不可用，但不阻断启动 |

> 密钥回落说明：`utils/AESUtils.java` 的 `getSecretKey()` 在缺少 `AES_SECRET_KEY` 时**不 fail-fast**（保留开发可用性），仅 `log.warn`。`AiConfigEncryptionWiringTest` 锁定此 fail-soft 行为：缺密钥仍加密成功、可解密，且不抛异常。

---

## 5. 告警外发配置（E3，2026-11-09）

`deploy/alertmanager.yml` 的 webhook 不再硬编码：

1. 在 `.env` 填 `ALERT_WEBHOOK_URL=https://qyapi.weixin.qq.com/...`（企业微信 / 钉钉 / 飞书机器人）。
2. 执行 `./deploy/gen-alertmanager.sh` —— 读取 `.env` 的 `ALERT_WEBHOOK_URL`，把 `deploy/alertmanager.yml.tpl` 的 `${ALERT_WEBHOOK_URL}` 替换为真实地址，生成 `deploy/alertmanager.yml`（docker-compose 实际挂载的文件）。
3. 不执行生成脚本时沿用本地占位 `http://host.docker.internal:9000/hook`，告警不外发。

CI 部署（`deploy` job）目前**不自动跑生成脚本**——部署前请在服务器上手动执行一次，或将此步加入部署 runbook。

---

## 6. 自动备份（E4，2026-11-09）

`deploy/backup/backup.sh`：

- `mysqldump` 逻辑备份 `personal_health` 库；
- `docker run alpine` 打包 `backend_data` 卷（上传文件等）；
- 保留 7 天（`find -mtime +7 -exec rm`）。

手动：`bash deploy/backup/backup.sh`。
cron（每天 03:07）：

```cron
7 3 * * * /opt/zhikangyun/deploy/backup/backup.sh >> /var/log/zhikangyun-backup.log 2>&1
```

恢复提示见脚本尾部注释。

---

## 7. 排查速查

| 现象 | 排查 |
|---|---|
| 流水线红在 `deploy`，但代码没问题 | 多半是三个部署 secret 没配 → 应看到 preflight 的 `::warning::` 而非 ssh 报错 |
| `npm audit` 失败 | 看报告修了哪个包；改 `package.json` 版本范围后**必须重跑 `npm ci`** 验证 lock 一致 |
| `npm ci` 报 `EUSAGE` | lock 与 `package.json` 不一致；用 `npm update --package-lock-only` 或 `npm install --package-lock-only` 重生成，**禁手改 lock** |
| 告警收不到 | `ALERT_WEBHOOK_URL` 没填或没跑 `gen-alertmanager.sh`；先看 Alertmanager / Prometheus 页面是否有 alert |
| 加密配置重启后读不出 | 检查 `AES_SECRET_KEY` 在容器里是否一致（同一密钥才能解密历史密文）；见 `AiConfigEncryptionWiringTest` |
