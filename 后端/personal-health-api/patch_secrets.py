# -*- coding: utf-8 -*-
"""把 application.yml 中的 4 处明文凭据改为环境变量占位（2026-10-03 安全整改）。

策略：
  - application.yml（主配置，理论上会进 git）→ 一律 ${ENV_VAR:无默认}
    无默认值意味着「必须显式提供」，避免密钥被误提交。
  - 本地开发默认值挪到 application-local.yml（已被 .gitignore 忽略）。
  - 已暴露的凭据（硅基流动 API key / JWT secret）直接作废，生成新值。
"""
import io
import os
import re
import secrets

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "src", "main", "resources")
MAIN = os.path.join(RES, "application.yml")
LOCAL = os.path.join(RES, "application-local.yml")

s = io.open(MAIN, encoding="utf-8").read()
orig = s

# ---- 生成新的 JWT 密钥（旧的已在对话/仓库中暴露，作废） ----
new_jwt = secrets.token_hex(32)

# ---- 1) MySQL 密码 ----
s = s.replace(
    "    username: root\n    password: 1234\n",
    "    username: ${DB_USERNAME:root}\n    password: ${DB_PASSWORD:}\n"
    "    # ⚠️ 2026-10-03：原为明文 1234。必须通过环境变量 DB_PASSWORD 提供，\n"
    "    #    本地默认值见 application-local.yml（该文件已被 .gitignore 忽略）。\n",
    1)

# ---- 2) Neo4j 密码（两处：crm.vectordb 下与顶层 neo4j 下） ----
cnt = s.count("      password: 12345678")
s = s.replace("      password: 12345678", "      password: ${NEO4J_PASSWORD:}")
s = s.replace("  password: 12345678\n  max-connection-pool-size",
              "  password: ${NEO4J_PASSWORD:}\n  max-connection-pool-size")
print("Neo4j 密码替换处数(2 缩进):", cnt)

# ---- 3) 硅基流动 API key（已泄露，作废） ----
s = re.sub(
    r"    api-key: sk-wkmdanxb\w+\n",
    "    # ⚠️ 2026-10-03：原为明文 key，且已在协作过程中暴露，按「已泄露」作废。\n"
    "    # 请到硅基流动控制台重新生成后，通过环境变量 SILICONFLOW_API_KEY 提供。\n"
    "    api-key: ${SILICONFLOW_API_KEY:}\n",
    s, count=1)

# ---- 4) JWT secret（已泄露，作废） ----
s = re.sub(
    r"  secret: 501d8e30\w+\n",
    "  # ⚠️ 2026-10-03：原为明文密钥且已暴露，已作废。\n"
    "  # 生产必须用环境变量 JWT_SECRET（≥32 字节）；\n"
    "  # 本地开发默认值见 application-local.yml。\n"
    "  secret: ${JWT_SECRET:}\n",
    s, count=1)

io.open(MAIN, "w", encoding="utf-8", newline="").write(s)
print("application.yml 已改:", s != orig)

# ---- 写入本地默认值 ----
local = io.open(LOCAL, encoding="utf-8").read()
marker = "# ===== 2026-10-03 安全整改：本地开发用凭据（此文件已被 .gitignore 忽略） ====="
if marker not in local:
    local = local.rstrip("\n") + "\n\n" + marker + "\n"
    local += (
        "# 说明：主配置 application.yml 中所有凭据均改为 ${ENV_VAR:} 形式（无默认值，\n"
        "#       强制显式提供），本文件承载本地开发用的默认值。\n"
        "#       ⚠️ 本文件绝不可提交到 git（.gitignore 已忽略 **/application-local.yml）。\n"
        "#       生产环境请全部改由环境变量 / 配置中心注入。\n\n"
        "spring:\n"
        "  datasource:\n"
        "    username: root\n"
        "    password: '1234'\n\n"
        "jwt:\n"
        "  # 本地开发用随机密钥（32 字节）。生产环境禁止使用此值。\n"
        "  secret: " + new_jwt + "\n\n"
        "crm:\n"
        "  vectordb:\n"
        "    embedding:\n"
        "      # 硅基流动控制台申请：https://cloud.siliconflow.cn\n"
        "      # 原 key 已泄露作废，请填入自己新申请的 key\n"
        "      api-key: ${SILICONFLOW_API_KEY:}\n\n"
        "neo4j:\n"
        "  password: '12345678'\n"
    )
    io.open(LOCAL, "w", encoding="utf-8", newline="").write(local)
    print("application-local.yml 已写入本地默认值")

print("新 JWT 密钥(前16位):", new_jwt[:16] + "****")
