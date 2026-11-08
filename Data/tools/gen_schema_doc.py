# -*- coding: utf-8 -*-
"""从 information_schema 导出迁移后的表结构基线文档。

⚠️ 2026-11-08 修正：原脚本把导出日期写死为 2026-10-03、字符集结论写死为
「全库统一 utf8mb4_0900_ai_ci」（与实测相反），且「迁移要点速查」里的一堆
行数是 10-03 的手工快照。直接重跑会把已经人工修正过的文档倒退回去。
现在：日期取当天、字符集按 information_schema 实测生成、历史快照段明确标注时点。
"""
import datetime
import io
import subprocess

MYSQL = "D:/Program Files/MySQL/bin/mysql.exe"
DB = "personal_health"
EXPORT_DATE = datetime.date.today().isoformat()

# 2026-10-03 数据模型重构中新建/改造的表
NEW = {"news", "comment", "tags", "notification", "department",
       "hospital_doctor", "user_health", "health_model_config",
       "patient_profile", "user"}

# 2026-10-04 表合并新增的统一交互表
# （post_like + post_favorite + post_report + news_save 四表合并而来）
NEW |= {"content_interaction"}

# 待归档的旧表：2026-10-04 已全部完成合并并 DROP，故此集合为空。
# 保留定义仅为将来若再引入新表时复用。
# 历史：post / post_tag / evaluations / post_reply / message
#      post_like / post_favorite / post_report / news_save
ARCHIVE: set[str] = set()
NEW_COLS = {
    "content_type", "user_id", "title", "summary", "view_count", "like_count",
    "favorite_count", "comment_count", "share_count", "hot_score", "status",
    "published_at", "updated_at", "tag_name_snapshot", "sort_order", "type",
    "parent_id", "code", "level", "leader_id", "location", "intro",
    "legacy_user_id", "username", "password", "salt", "title_level",
    "specialties", "visit_count", "rating", "gender", "email", "phone",
    "reg_no", "dept_ids", "need_init_password", "source", "sender_id",
    "link_url", "biz_type", "biz_id", "target_type", "target_id",
    "reply_to_id", "upvote_list", "column_name",
}


def q(sql):
    r = subprocess.run(
        [MYSQL, "-uroot", "-p1234", "-D", DB, "--default-character-set=utf8mb4",
         "-N", "-B", "-e", sql],
        capture_output=True, text=True, timeout=30)
    return r.stdout


rows = []
for line in q("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,"
              "IF(COLUMN_KEY='PRI','PK',IFNULL(COLUMN_KEY,'')),"
              "IFNULL(COLUMN_DEFAULT,'-'),IFNULL(COLUMN_COMMENT,'') "
              "FROM information_schema.COLUMNS "
              "WHERE TABLE_SCHEMA='" + DB + "' ORDER BY TABLE_NAME,ORDINAL_POSITION;"
              ).splitlines():
    p = line.split("\t")
    if len(p) >= 7:
        rows.append((p[0], p[1], p[2], p[3], p[4], p[5], "\t".join(p[6:])))

tables = sorted({r[0] for r in rows})
counts = {}
for t in tables:
    v = q("SELECT COUNT(*) FROM `" + t + "`;").strip()
    counts[t] = int(v) if v.isdigit() else -1

# 字符集/排序规则按实库实测生成，不再写死「全库统一 0900」这句错误结论
SCHEMA_COLLATION = q(
    "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA "
    "WHERE SCHEMA_NAME='" + DB + "';").strip() or "(读不到)"
COLLATION_DIST = []
for line in q(
        "SELECT IFNULL(COLLATION_NAME,'(非字符列)'),COUNT(*) "
        "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + DB + "' "
        "GROUP BY COLLATION_NAME ORDER BY 2 DESC;").splitlines():
    parts = line.split("\t")
    if len(parts) >= 2:
        COLLATION_DIST.append((parts[0], parts[1]))
CHAR_COLUMNS = sum(int(n) for name, n in COLLATION_DIST if name != "(非字符列)")

GROUPS = [
    ("身份与用户", ["user", "patient_profile", "user_follow", "audit_log"]),
    ("医疗业务（本次重点：科室层级 + 医生解耦）",
     ["department", "hospital_doctor", "doctor_schedule", "appointment",
      "visit_record", "followup_task", "followup_record"]),
    ("健康数据（本次重点：指标去重）", ["health_model_config", "user_health"]),
    ("内容与互动（本次重点：news 合并）",
     ["news", "post", "tags", "post_tag", "evaluations", "post_reply",
      "comment", "post_favorite", "post_like", "post_report",
      "model_announcement"]),
    ("消息（本次重点：notification 合并）", ["message", "notification"]),
    ("商城", ["mall_product", "mall_order", "order_item", "product_category",
             "shopping_cart", "shipping_address"]),
    ("问答", ["quiz_question", "quiz_exam", "quiz_exam_question",
             "quiz_record", "quiz_answer"]),
    ("AI", ["ai_config", "ai_conversation", "ai_chat_record", "ai_usage"]),
    ("RAG 知识库与检索",
     ["knowledge_base", "kb_document", "kb_chunk", "kb_data_source",
      "retrieval_log", "rag_eval_case", "rag_eval_run"]),
    ("智能体平台",
     ["agent_config", "agent_run", "agent_tool_binding", "agent_memory",
      "agent_state", "agent_workspace"]),
    ("系统", ["system_config", "audit_log"]),
]

o = []
o.append("# 智康云健康管理系统 · 数据表结构基线（迁移后）")
o.append("")
o.append("> 导出时间：%s　　库：`personal_health`" % EXPORT_DATE)
o.append("> 规模：%d 张表 / %d 个字段（`information_schema` 实时导出）" % (len(tables), len(rows)))
o.append(">")
o.append("> ⚠️ 文件名里的 `20261003` 是**首次基线日期**，不是本文件内容的时间；")
o.append("> 本文件内容按上面的导出时间从实库机器重新生成。改文件名会牵动")
o.append("> `MapperTableConsistencyTest` 与多处文档引用，故保留原名。")
o.append(">")
o.append("> 本文档由 `information_schema` 实时导出，是后端实体类、Mapper XML、")
o.append("> 前端接口的**唯一权威结构基线**。改表前请先更新本文档。")
o.append(">")
o.append("> **口径提醒**：这里导出的是**本机实库**。`Data/01_建库建表` + `02_迁移脚本`")
o.append("> 全量跑完的部署链终态另含 5 张 `agent_*` 表（47 表 / 561 字段 / 196 字符列），")
o.append("> 两者不同，引用时务必写明是哪一个口径。")
o.append(">")
o.append("> 标记说明（以下两种均为 2026-10-03/04 重构的**历史标记**，不是本次导出新增）：")
o.append("> - `**[本次变更]**` — 2026-10-03/04 数据模型重构新增或调整的表")
o.append("> - `**新增**` — 该字段为 2026-10-03/04 重构新增")
if ARCHIVE:
    o.append("> - `` `[旧表待归档]` `` — 冗余旧表，代码切换完成后归档，用户校验通过后删除")
else:
    o.append("> - （2026-10-04 表合并已全部完成，无待归档旧表）")
o.append("")
o.append("---")
o.append("")

seen = set()
for gname, ts in GROUPS:
    hit = [t for t in ts if t in tables]
    if not hit:
        continue
    o.append("## " + gname)
    o.append("")
    for t in hit:
        seen.add(t)
        mark = " **[本次变更]**" if t in NEW else ""
        arch = " `[旧表待归档]`" if t in ARCHIVE else ""
        o.append("### `%s`%s%s — %d 行" % (t, mark, arch, counts[t]))
        o.append("")
        o.append("| 字段 | 类型 | 可空 | 键 | 默认 | 说明 |")
        o.append("|---|---|---|---|---|---|")
        for tb, col, ty, nul, key, dflt, cm in rows:
            if tb != t:
                continue
            flag = "**新增**" if (col in NEW_COLS and t in NEW) else ""
            o.append("| `%s`%s | %s | %s | %s | %s | %s |"
                     % (col, flag, ty, nul, key, dflt, cm))
        o.append("")

rest = [t for t in tables if t not in seen]
if rest:
    o.append("## 其他表")
    o.append("")
    for t in rest:
        cols = ", ".join("`%s`" % r[1] for r in rows if r[0] == t)
        o.append("### `%s` — %d 行" % (t, counts[t]))
        o.append("")
        o.append("字段：%s" % cols)
        o.append("")

o.append("---")
o.append("")
o.append("## 迁移要点速查（2026-10-03 迁移当时的历史记录）")
o.append("")
o.append("> ⚠️ 本节是 2026-10-03/04 迁移时人工写下的**历史快照**：下面的行数、指标数、")
o.append("> 科室数、医生账号数都只代表当时，**不代表本次导出的实况**。")
o.append("> 当前实况以本文件上面的字段表与「### 7 字符集（本次导出实测）」为准。")
o.append("")
o.append("### 1. news 已吸收 post")
o.append("- 旧 `post` 表 10 行已迁入 `news`，`content_type='POST'`")
o.append("- 旧 `news` 29 行为 `content_type='NEWS'`，`title` 由 `name` 回填")
o.append("- 统一字段：`title` / `content_type` / `user_id` / `summary` / 四个统计字段 / `status` / `published_at`")
o.append("- ⚠️ `name` 与 `title` 迁移期并存，代码统一用 `title`；旧 `name` 待代码切换完成后废弃")
o.append("")
o.append("### 2. 医生已与 user 解耦")
o.append("- 账号字段：`username` / `password`(BCrypt) / `need_init_password`")
o.append("- 现有 3 个医生账号：`doctor3001` / `doctor3002` / `doctor3003`（密码均未设置，首次登录须初始化）")
o.append("- `legacy_user_id` 存的是迁移前的 `user_id`，**仅历史追溯，代码不得再使用**")
o.append("- 职称枚举 `title_level`：`TITLE` 主任 / `ASSOC` 副主任 / `ATTENDING` 主治 / `RESIDENT` 住院医")
o.append("- 现有数据职称：3001=TITLE，3002=ASSOC，3003=ATTENDING")
o.append("")
o.append("### 3. comment 统一表")
o.append("- 多态设计：`target_type`(NEWS/QUIZ) + `target_id`")
o.append("- 字段：`user_id` / `reply_to_id` / `parent_id` / `content` / `like_count` / `upvote_list`")
o.append("- 旧 `evaluations`(10) 与 `post_reply`(0) 已迁入")
o.append("")
o.append("### 4. notification 合并 message")
o.append("- `type` 是 **tinyint**：0系统公告 1预约 2随访 3订单 **4私信**")
o.append("- 新增 `source`(SYSTEM/USER/AI/ORDER) / `sender_id` / `biz_type` / `biz_id` / `link_url`")
o.append("- 旧 `message` 16 行已迁入（type=4）")
o.append("")
o.append("### 5. 科室支持层级")
o.append("- `parent_id`（0=顶级）/ `level` / `code` / `leader_id`")
o.append("- 现有 10 个科室均为一级（parent_id=0），编码 `DEPT_001` ~ `DEPT_010`")
o.append("")
o.append("### 6. 指标去重")
o.append("- `user_health` 从 125 → 160 行（回填 35 条来自 `patient_profile` 硬编码字段）")
o.append("- `health_model_config` 从 5 → 12 个指标（补齐血脂四项、餐后血糖、身高、体重）")
o.append("- ⚠️ `patient_profile` 的 9 个指标字段仍在（代码不再使用，待后续 DROP）")
o.append("")
o.append("### 7. 字符集（本次导出实测）")
o.append("- 库默认排序规则：`%s`（`information_schema.SCHEMATA` 实测）" % SCHEMA_COLLATION)
o.append("- 列级排序规则分布：%s" % "、".join(
    "`%s` %s 列" % (name, n) for name, n in COLLATION_DIST))
o.append("- 字符列合计 **%d** 个。" % CHAR_COLUMNS)
o.append("- ⚠️ **不要写「全库统一 `utf8mb4_0900_ai_ci`」**：库默认值并未改动，")
o.append("  只是建表 DDL 逐表/逐列显式覆盖了字符列；两种库默认值下终态结构相同。")

DOC = "docs/数据表结构基线-20261003.md"
io.open(DOC, "w", encoding="utf-8", newline="").write("\n".join(o))
print("已生成 %s" % DOC)
print("表数: %d  字段数: %d" % (len(tables), len(rows)))

# ---------------------------------------------------------------------------
# 同步到 Data/04_结构基线/
#
# ⚠️ 2026-10-06 补：原脚本只写 docs/ 一处。而 MapperTableConsistencyTest
#    （防「Mapper 引用不存在的表」的一致性测试）读的是 Data/04_结构基线/，
#    两处不同步会导致改表后基线失真、测试误报。故在此强制同步。
# ---------------------------------------------------------------------------
import os
import shutil as _shutil
_MIRROR = os.path.join("Data", "04_结构基线", "数据表结构基线-20261003.md")
try:
    os.makedirs(os.path.dirname(_MIRROR), exist_ok=True)
    _shutil.copyfile(DOC, _MIRROR)
    print("已同步 -> %s" % _MIRROR)
except Exception as e:  # 同步失败不应让整次生成失败
    print("[警告] 同步到 %s 失败: %s" % (_MIRROR, e))
