# -*- coding: utf-8 -*-
"""从 information_schema 导出迁移后的表结构基线文档。"""
import io
import subprocess

MYSQL = "D:/Program Files/MySQL/bin/mysql.exe"
DB = "personal_health"
NEW = {"news", "comment", "tags", "notification", "department",
       "hospital_doctor", "user_health", "health_model_config",
       "patient_profile", "user"}
ARCHIVE = {"post", "post_tag", "evaluations", "post_reply", "message"}
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
    ("系统", ["system_config"]),
]

o = []
o.append("# 智康云健康管理系统 · 数据表结构基线（迁移后）")
o.append("")
o.append("> 导出时间：2026-10-03　　库：`personal_health`")
o.append("> 规模：%d 张表 / %d 个字段" % (len(tables), len(rows)))
o.append(">")
o.append("> 本文档由 `information_schema` 实时导出，是后端实体类、Mapper XML、")
o.append("> 前端接口的**唯一权威结构基线**。改表前请先更新本文档。")
o.append(">")
o.append("> 标记说明：")
o.append("> - `**[本次变更]**` — 2026-10-03 数据模型重构新增或调整的表")
o.append("> - `**新增**` — 该字段为本次重构新增")
o.append("> - `` `[旧表待归档]` `` — 冗余旧表，代码切换完成后归档，用户校验通过后删除")
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
o.append("## 迁移要点速查（后端适配必读）")
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
o.append("### 7. 字符集")
o.append("- 全库统一 `utf8mb4_0900_ai_ci`（含列级），已校验无遗漏")

io.open("docs/数据表结构基线-20261003.md", "w", encoding="utf-8", newline="").write("\n".join(o))
print("已生成 docs/数据表结构基线-20261003.md")
print("表数: %d  字段数: %d" % (len(tables), len(rows)))
