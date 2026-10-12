# Data 目录说明（Navicat 使用指南）

> 整理时间：2026-10-04　｜　最近更新：2026-10-10（归档目录移出版本库、通知开关说明）
> 库名：`personal_health`　字符集：`utf8mb4 / utf8mb4_0900_ai_ci`

---

## 一、目录结构（按 Navicat 执行顺序编号）

```
Data/
├── 01_建库建表/        ← ① 空库时执行（全新部署）
├── 02_迁移脚本/        ← ② 增量升级（01~08 按序执行，全幂等）
├── 03_数据脚本/        ← ③ 演示/测试数据
├── 04_结构基线/        ← ④ 只读参考，不要执行
├── 90_归档/            ← ⑤ 历史备份（Navicat 恢复用）
└── README.md           ← 本文件
```

---

## 二、Navicat 里怎么用

### 场景 A：全新部署（空库）

1. Navicat 新建 MySQL 连接 → 右键连接 → **新建数据库**
   - 库名 `personal_health`
   - 字符集 **utf8mb4**
   - 排序规则 **utf8mb4_0900_ai_ci**
2. 右键该库 → **运行 SQL 文件** → 依次执行 `01_建库建表/` 下所有 `.sql`
   - 建议顺序：`init_database.sql` → `rbac_schema.sql` → `forum_schema.sql`
     → `appointment_schema.sql` → `extra_modules_schema.sql`
     → `ai_usage_schema.sql` → `audit_log.sql` → `model_announcement_schema.sql`
3. 跑完后表数应为 **29 张**（未合并前的初始结构）
4. 再执行 `02_迁移脚本/` 下 01~08，进化为现在的 **35 张**

### 场景 B：已有库升级（当前你的情况）

直接执行 `02_迁移脚本/`，按序号：

| 序号 | 文件 | 作用 | 幂等 |
|---|---|---|---|
| 01 | `01_schema_refactor.sql` | news 合并 post、医生解耦、科室层级、user.phone、字符集统一 | ✅ |
| 02 | `02_patient_profile_normalize.sql` | 患者档案脏值归一化（性别中英混杂、`["无"]`、lifestyle 键名） | ✅ |
| 03 | `03_align_demo_appointments.sql` | 演示数据对齐（**仅本地演示用，生产勿执行**） | ✅ |
| 04 | `04_consolidate_tables.sql` | 表合并：post_like+post_favorite+post_report+news_save → content_interaction | ✅ |
| 05 | `05_drop_unused_tables.sql` | 空表清理（当前无可删项，全部注释保留） | ✅ |
| 06 | `06_merge_message_into_notification.sql` | 消息表双轨合并：`message` → `notification` | ✅ |
| 07 | `07_sensitive_word.sql` | 敏感词表 | ✅ |
| 08 | `08_user_settings_columns.sql` | `user` 表补 14 个设置列（语言/时区/通知/隐私/AI），设置页据此落库 | ✅ |

全部脚本**幂等**，重复执行结果一致，不会丢数据。

> 08 是 2026-10-10 新增的。`user_settings` 表当初被有意简化并入 `user` 表，
> 但列一直没建 —— 设置页读的是硬编码默认值、保存直接报「暂未实现」。
> 本脚本补齐列并把历史上已有的行回填成默认值，无需改代码即可上线。

> ⚠️ **08 必跑**：其中的 `notify_appointment` / `notify_order` / `notify_ai_chat` 三列
> 在 2026-10-10 之后已被后端**真正读取**（发送通知前查用户开关，见 `docs/通知系统.md`）。
> 漏跑 08 时应用**不会报错**（读不到开关按「开启」处理），表现是「设置页怎么点都没用」——
> 这是个静默失效，排查时先确认这三列存在。
>
> 通知功能**不需要任何额外的表或列**：`notification` 的 `title` / `link_url` / `biz_type` /
> `biz_id` 等字段在 06（`message` 并表）时就已经建好了。

执行完 08 之后，`04_结构基线/` 的文档会落后于实际结构，重新生成一次：

```bash
python Data/tools/gen_schema_doc.py
```

### 场景 C：从备份恢复

> ⚠️ **2026-10-10 起 `90_归档/` 不进仓库**（见 §四）。
> 从 GitHub 克隆下来的仓库里**没有备份文件**，`LATEST_PORTABLE.txt` 也不在。
> 恢复前请先自行准备一份 dump（你自己机器上的 MySQL 用 `mysqldump` 导出的即可）。
> 本机磁盘上若还有历史备份，可直接用。

1. Navicat 右键 `personal_health` 库 → **运行 SQL 文件**
2. 选 `90_归档/` 下 `LATEST_PORTABLE.txt` 指向的那个文件（**前提：该文件存在**）
3. 恢复后校验：表数应为 **35**

⚠️ **备份有两种格式，用错会报 `ERROR 1046 No database selected`**：

| 格式 | 文件特征 | Navicat 里怎么用 |
|---|---|---|
| **完整版**（推荐） | 开头含 `CREATE DATABASE` + `USE` | 直接「运行 SQL 文件」，无需先选库 |
| **纯净版** | 无 `USE` 语句 | 必须**先右键目标库**再运行，否则 MySQL 不知道往哪写 |

**历史上 `LATEST_PORTABLE.txt` 指向的是完整版**，可直接跑。
旧的 `portable_20261003_*.sql` / `portable_20261004_132132.sql` 是纯净版，仅作历史留档。

> 生成命令差异：
> 完整版 `mysqldump --databases personal_health`；纯净版 `mysqldump personal_health`

自己生成备份：

```bash
# 完整版（含 CREATE DATABASE + USE，推荐）
mysqldump --databases personal_health > portable_$(date +%Y%m%d_%H%M%S).sql
```

---

## 三、⚠️ 重要提醒

### 1. 迁移前务必先备份

**任何迁移脚本执行前，先用 Navicat 右键库 → 备份 → 生成 .nbak**，
或用 mysqldump 导出。不要只备份单表——2026-10-04 就发生过整库被清空的事故。

### 2. 恢复备份的坑

误用纯净版会报 `ERROR 1046 No database selected`。详见上文「场景 C」的格式对照表。

本机若有历史备份，最新一份的路径记在 `90_归档/LATEST_PORTABLE.txt`。
**注意该目录已不进仓库**（2026-10-10 起），克隆下来的仓库里没有它，需要自己出 dump。

### 3. 表数量说明

- 初始建库：**29 张**
- 2026-10-03 迁移后：43 张
- 2026-10-04 表合并后：**35 张**

35 张是当前合理下限。剩余空表（`shopping_cart`/`shipping_address`/`ai_usage` 等）
**功能都在线**，0 行只代表「还没人用」，删表等于删功能 —— 不要删。

### 4. 整理前快照

根目录的 `Data_整理前快照_20261004_135823/` 是 2026-10-04 目录重排**之前**的完整副本，
已于后续提交中删除（重排验证无误）。需要回溯时用 `90_归档/` 里的 dump。

---

## 四、目录明细

### `01_建库建表/`（空库部署用）

| 文件 | 内容 |
|---|---|
| `init_database.sql` | 主库初始化（用户/角色/菜单等核心表） |
| `rbac_schema.sql` | 权限相关表 |
| `forum_schema.sql` | 社区帖子相关表（**已被合并，仅供参考**） |
| `appointment_schema.sql` | 预约排班相关表 |
| `extra_modules_schema.sql` | 扩展模块（药品、随访、评价等） |
| `ai_usage_schema.sql` | AI token 用量 |
| `audit_log.sql` | 操作审计 |
| `model_announcement_schema.sql` | 公告弹窗 |

### `02_迁移脚本/`（增量升级，见上方表格）

### `03_数据脚本/`

| 文件 | 内容 |
|---|---|
| `mock_business_data.sql` | 业务演示数据 |
| `vip_migration.sql` | VIP 相关处理 |
| `migrate_encrypted_data.sql` | 历史加密数据迁移 |
| `hot_score_algorithm.sql` | 热度分算法说明 |

### `04_结构基线/`（只读）

| 文件 | 说明 |
|---|---|
| `数据表结构基线-20261003.md` | 权威结构文档（字段级），与 `docs/` 下同名文件一致 |
| `迁移后结构快照_20261003.sql` | 迁移后的建表语句快照，可用于比对差异 |

改表后重新生成文档：
```bash
python Data/tools/gen_schema_doc.py
```

### `tools/`

- `gen_schema_doc.py` —— 从 `information_schema` 读取真实结构，生成结构基线文档。
  改了表结构后跑一次，避免文档与实际脱节。

### `90_归档/`

历史备份，按时间戳命名。**2026-10-10 起本目录不进仓库**（已 `git rm --cached` 并加入 `.gitignore`）：
里面是 37 个 mysqldump 导出（约 3MB），没有版本管理价值（二进制式大文本、diff 全红），
且含全库数据快照。**本机磁盘上仍然保留**，需要时直接取用；但因为不在仓库里，
从 GitHub 克隆到别的机器后这个目录是空的。

最新完整备份路径记在 `LATEST_PORTABLE.txt`（仅本机有效）。
旧备份仅作回溯用，日常可定期清理（保留最近 3~5 份即可）。
