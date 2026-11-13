# -*- coding: utf-8 -*-
"""撤销 application-local.yml 中重复追加的段，并更新原有问题项的取值。"""
import io
import os
import re
import secrets

P = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 "src", "main", "resources", "application-local.yml")
s = io.open(P, encoding="utf-8").read()

# ---- 1. 截断掉我追加的整段（从标记行开始到文件末尾）----
marker = "# ===== 2026-10-03 安全整改：本地开发用凭据（此文件已被 .gitignore 忽略） ====="
if marker in s:
    s = s[:s.index(marker)].rstrip("\n") + "\n"
    print("已移除重复追加段")

# ---- 2. 轮换 JWT 密钥（旧的 501d8e30… 已在协作过程中暴露）----
new_jwt = secrets.token_hex(32)
s = re.sub(r"(jwt:\s*\n(?:.*\n)*?\s*secret:\s*)\\$\{JWT_SECRET:[^}]*\}",
           lambda m: m.group(1) + new_jwt + "\n# 2026-10-03 轮换：原密钥已暴露作废。生产环境务必用环境变量 JWT_SECRET 注入。",
           s, count=1)
if new_jwt not in s:
    s = re.sub(r"(\n\s*secret:\s*).*", lambda m: m.group(1) + new_jwt, s, count=1)
print("JWT 密钥已轮换:", new_jwt[:12] + "****")

# ---- 3. 确保 datasource 密码存在（主配置已改为 ${DB_PASSWORD:}，这里给默认值）----
if re.search(r"^\s{4}password:\s*$", s, re.M) is None and "datasource:" in s:
    s = re.sub(r"(datasource:[\s\S]*?username:\s*root\s*\n)",
               r"\1    # 2026-10-03：主配置已改为 ${DB_PASSWORD:}，本地默认值在此提供\n"
               r"    password: '1234'\n", s, count=1)
    print("datasource 密码已补充")

# ---- 4. 确保 neo4j 密码存在 ----
if "neo4j:" not in s:
    s = s.rstrip("\n") + "\n\n# 2026-10-03：主配置已改为 ${NEO4J_PASSWORD:}，本地默认值在此提供\nneo4j:\n  password: '12345678'\n"
    print("neo4j 密码段已补充")

io.open(P, "w", encoding="utf-8", newline="").write(s)

# ---- 5. 校验：无重复顶层键 ----
top = re.findall(r"^([a-zA-Z][\w-]*):", s, re.M)
dup = {k for k in top if top.count(k) > 1}
print("顶层键:", ", ".join(top))
print("重复键:", ("无 ✓" if not dup else "❌ " + ", ".join(dup)))
print("新 JWT 密钥(前12):", new_jwt[:12] + "****")
