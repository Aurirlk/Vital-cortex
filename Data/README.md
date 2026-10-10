# Data 目录说明（Navicat 使用指南）

> 整理时间：2026-10-04　　**最近复核：2026-10-07（全新部署路径已端到端验证通过）**
> 库名：`personal_health`　**库默认排序规则：`utf8mb4_bin`**（`information_schema` 实测）
> **35 张表 / 130 个字符列：`utf8mb4_0900_ai_ci`** —— 由各建表脚本在 DDL 里显式指定，与库默认值无关。

---

## 一、目录结构（按 Navicat 执行顺序编号）

```
Data/
├── 01_建库建表/        ← ① 空库时执行（全新部署）     7 个脚本 → 42 张表
├── 02_迁移脚本/        ← ② 增量升级（01~07 按序执行，全幂等）→ 35 张表
├── 03_数据脚本/        ← ③ 演示数据 + 2 个一次性运维脚本（见下注）—— 可选
├── 04_结构基线/        ← ④ 只读参考，不要执行
├── 90_归档/            ← ⑤ 历史备份 + 废弃脚本（Navicat 恢复用）
└── README.md           ← 本文件
```

> **③ 目录并不是清一色演示数据**，混了 2 个一次性运维脚本，请按需单独执行：
> `migrate_encrypted_data.sql`（API Key 加密，会建备份表 `ai_config_backup`）、
> `vip_migration.sql`（VIP 字段补列，**新库无需执行**——`init_database.sql` 已含这两列）。
>
> **⑤ 归档目录**除历史备份外，还收了一个废弃脚本
> `rbac_schema_deprecated_20261007.sql`（会建 4 张本项目从未使用的 RBAC 表，勿执行）。

---

## 二、Navicat 里怎么用

### 场景 A：全新部署（空库）

1. Navicat 新建 MySQL 连接 → 右键连接 → **新建数据库**
   - 库名 `personal_health`
   - 字符集 **utf8mb4**
   - 排序规则 **utf8mb4_bin**（与生产库默认一致；表内 DDL 已显式写死 `utf8mb4_0900_ai_ci`，所以这一项选 `utf8mb4_0900_ai_ci` 也能得到同样的表结构）
2. 右键该库 → **运行 SQL 文件** → 依次执行 `01_建库建表/` 下全部 7 个 `.sql`
   - 顺序：`init_database.sql` → `forum_schema.sql` → `appointment_schema.sql`
     → `extra_modules_schema.sql` → `ai_usage_schema.sql` → `audit_log.sql`
     → `model_announcement_schema.sql`
3. 跑完后表数应为 **42 张**（未合并前的初始结构，2026-10-07 临时库实测）
4. 再执行 `02_迁移脚本/` 下 `01`~`07`，进化为现在的 **35 张 / 391 字段**

> ✅ **2026-10-07 已修复：本路径原先跑不通（P1），现已端到端验证通过**
> 复核时在临时库 `ph_verify_tmp` 实跑，发现 6 处缺陷并全部修掉：
>
> | # | 缺陷 | 证据（修复前） | 修复 |
> |---|---|---|---|
> | 1 | `notification` 表**无任何 `CREATE TABLE`** | `01_schema_refactor` / `06` 双双报 `ERROR 1146 (42S02): Table 'notification' doesn't exist` 并中断 | 在 `01_建库建表/init_database.sql` 补入该表 DDL（取自实库 `SHOW CREATE TABLE`） |
> | 2 | `rbac_schema.sql` 建 4 张**已废弃**表 | `role`/`permission`/`role_permission`/`user_role` 不在真实 35 表中、Mapper 已删、无迁移 drop | 移入 `90_归档/rbac_schema_deprecated_20261007.sql` |
> | 3 | `01_schema_refactor.sql` 步骤 6.5 三条 `SET @d = IF(...)` **漏 `PREPARE/EXECUTE`**，`user.phone` / `phone_verified` 从未真正添加 | 紧随其后的 `UPDATE` 报 `ERROR 1054 Unknown column 'phone_verified'` | 每条补 `PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;` |
> | 4 | `mall_product` 少 6 列（`product_type` + 5 个医学字段）；`07` 硬编码 `USE personal_health` | 脚本产物 15 列 vs 实库 21 列；`07` 针对其它库执行会**静默写进 personal_health** | `extra_modules_schema.sql` 补 `product_type`；`07` 去掉 `USE`、改用 `DATABASE()`、补 `product_type` 的幂等 ALTER |
> | 5 | **"全部幂等"不成立**：`01`/`04`/`06` 共 **11 条语句**引用了会被本套脚本自己 `DROP` 的 legacy 表（`post`/`post_tag`/`evaluations`/`post_reply`/`message`/`post_like`/`post_favorite`/`post_report`/`news_save`） | 复跑第二遍报 `ERROR 1146`：`01` 停在 `Table 'post' doesn't exist`、`04` 停在 `'post_like'`、`06` 停在 `'message'`。**且 mysql CLI 遇错即止，会静默跳过该文件其后所有语句**（原文档称"复跑结果一致"与实测不符） | 每条加**存在性守卫**：`SET @_has=(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='x'); SET @_sql=IF(@_has>0,'<原语句>','DO 0'); PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;` |
> | 6 | **中文种子数据依赖客户端编码**：`model_announcement_schema.sql` / `seed_model_announcement.sql` / `03_align_demo_appointments.sql` 含中文 `INSERT` 却**没有 `SET NAMES utf8mb4;`** | 不带 `--default-character-set=utf8mb4` 执行即报 `ERROR 1406 (22001): Data too long for column 'model_name'`（客户端按非 utf8 编码解释文件字节，中文被当成多字节撑爆列宽） | 为**全部 12 个**含中文但缺声明的脚本补 `SET NAMES utf8mb4;`；验证：不带任何编码参数也零 ERROR，且 `HEX(model_name)` = `E699BA…` 与期望 UTF-8 完全一致 |
>
> 另修 3 处「可选脚本」缺陷：`mock_business_data.sql` 仍向已合并删除的 `post` 表插入（改写入 `news` 且标注 `content_type='POST'`）；`vip_migration.sql` 非幂等（`ERROR 1060 Duplicate column 'is_vip'`）→ 改为幂等；`hot_score_algorithm.sql` 两个存储过程指向已删 `post` 表（调用即 `ERROR 1146` 的死代码）→ 改指 `news`，并加 `DROP PROCEDURE IF EXISTS` 使过程体可被复跑刷新。
>
> **验证结果（2026-10-07，临时库 `ph_verify_tmp` 连跑两遍，两种编码口径各测一次）**：
> ```
> A 口径：mysql <file>                     （不带编码参数，考验脚本自带 SET NAMES）
>        第 1 遍 零 ERROR；第 2 遍（复跑）零 ERROR
> B 口径：mysql --default-character-set=utf8mb4 <file>
>        第 1 遍 零 ERROR；终态与实库逐表比对：多表/少表 = 空
> 20 个脚本 = 01_建库建表 7 + 02_迁移脚本 8 + 03_数据脚本 6
> ⚠️ 其中 `02_迁移脚本/09_create_ai_ro_user.sql` 是【需 DBA 权限的运维脚本】，不在自动部署链中，
>   「双跑零 ERROR」的口径只覆盖其余 19 个库内脚本。详见该文件头说明。
> 仅 01 时：42 张表；仅 01+02 时：表 35 = 35，字段 391 = 391
> 含 03 时：多出 ai_config_backup（一次性运维脚本，设计如此，非缺陷）
> ```
> 注：`03_数据脚本/hot_score_algorithm.sql` 只创建存储过程、**不自动执行**，不会覆盖已有 `hot_score`。

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
| 07 | `07_mall_product_medical_fields.sql` | `mall_product` 补医学字段（isOtc / genericName / specification / manufacturer） | ✅ |
| 08 | —（无新增） | — | — |
| 09 | `09_create_ai_ro_user.sql` | 🔴 **运维脚本（需 root）**：创建 `ai_ro` 只读账号，供数据智能体的受控 NL2SQL 使用。**不在自动部署链**，需 DBA 手工执行；密码占位符须替换 | — |

除 `09` 外的全部脚本**幂等**，重复执行结果一致，不会丢数据。
`01` 与 `06` 依赖 `notification` 表存在——该表已补入 `01_建库建表/init_database.sql`，**全新部署与升级两条路径均已验证通过**。
`07` 自 2026-10-07 起**不再硬编码 `USE personal_health`**，可安全用于任意库名的环境（含临时库/测试库）。

### 场景 C：从备份恢复

1. Navicat 右键 `personal_health` 库 → **运行 SQL 文件**
2. 选 `90_归档/` 下 `LATEST_PORTABLE.txt` 指向的那个文件
3. 恢复后校验：表数应为 **35**

⚠️ **备份有两种格式，用错会报 `ERROR 1046 No database selected`**：

| 格式 | 文件特征 | Navicat 里怎么用 |
|---|---|---|
| **完整版**（推荐） | 开头含 `CREATE DATABASE` + `USE` | 直接「运行 SQL 文件」，无需先选库 |
| **纯净版** | 无 `USE` 语句 | 必须**先右键目标库**再运行，否则 MySQL 不知道往哪写 |

**当前 `LATEST_PORTABLE.txt` 指向的是完整版**，可直接跑。

> 2026-10-07 已清理历史备份：现仅保留**最近两代完整版**（`portable_20261007_050202.sql` 为当前、`portable_20261006_233220.sql` 为上一代），
> 10-04 迁移过程中的中间快照（`portable_20261004_*` / `*_premigrate_*` / `*_emergency_*` / `premerge_*`）与 8-19/8-28 的 legacy 导出已删除。

> 生成命令差异：
> 完整版 `mysqldump --databases personal_health`；纯净版 `mysqldump personal_health`

---

## 三、⚠️ 重要提醒

### 1. 迁移前务必先备份

**任何迁移脚本执行前，先用 Navicat 右键库 → 备份 → 生成 .nbak**，
或用 mysqldump 导出。不要只备份单表——2026-10-04 就发生过整库被清空的事故。

### 2. 恢复备份的坑

误用纯净版会报 `ERROR 1046 No database selected`。详见上文「场景 C」的格式对照表。

最新备份路径见 `90_归档/LATEST_PORTABLE.txt`。

### 3. 表数量说明

- 初始建库：**42 张**（2026-10-07 临时库实跑 `01_建库建表/` 7 个脚本后 `information_schema` 实测；
  原写「29 张」是 10-04 目录重排前的旧数、「41 张」是漏算，均已更正。
  这 42 张**不含** `rbac_schema.sql` 的 4 张已废弃 RBAC 表——该脚本已移入 `90_归档/`）
- 2026-10-03 迁移后：43 张
- 2026-10-04 表合并后：**35 张**

35 张是当前合理下限。剩余空表（`shopping_cart`/`shipping_address`/`ai_usage` 等）
**功能都在线**，0 行只代表「还没人用」，删表等于删功能 —— 不要删。

### 4. 备份保留策略

`90_归档/` 采用**「保留最近两代完整备份」**策略（2026-10-07 起）：

| 文件 | 说明 |
|---|---|
| `LATEST_PORTABLE.txt` | 指针，指向当前完整备份 |
| `portable_20261007_050202.sql` | **当前**完整备份（35 表，含 `CREATE DATABASE` + `USE`） |
| `portable_20261006_233220.sql` | 上一代完整备份（保留一代作为回滚冗余） |
| `rbac_schema_deprecated_20261007.sql` | 已废弃的 RBAC 建表脚本（仅作决策留档，**勿执行**） |

> 历史做法是把每次迁移前的快照都留在这里，导致 `90_归档/` 积累 40+ 个文件 / 3.9 MB。
> 2026-10-07 在确认 `Data/` 全部脚本可复现（35 表 / 391 字段）后清理为上述 4 个文件（485 KB）。
> 新增备份时：先 `mysqldump --databases personal_health`，再更新 `LATEST_PORTABLE.txt`，并删除更早的一代。

---

## 四、目录明细

### `01_建库建表/`（空库部署用，共 7 个脚本）

| 文件 | 内容 |
|---|---|
| `init_database.sql` | 主库初始化（用户/角色/菜单等核心表，**含 `notification`**） |
| `forum_schema.sql` | 社区帖子相关表（**已被合并，仅供参考**） |
| `appointment_schema.sql` | 预约排班相关表 |
| `extra_modules_schema.sql` | 扩展模块（药品、随访、评价等） |
| `ai_usage_schema.sql` | AI token 用量 |
| `audit_log.sql` | 操作审计 |
| `model_announcement_schema.sql` | 公告弹窗 |

> ⚠️ `rbac_schema.sql` 已**不在本目录**——2026-10-07 移入 `90_归档/rbac_schema_deprecated_20261007.sql`（它会建 4 张本项目从未使用的表），**不要执行**。

### `02_迁移脚本/`（增量升级，见上方表格）

### `03_数据脚本/`（演示数据 + 种子配置 + 一次性运维脚本，共 6 个）

| 文件 | 内容 |
|---|---|
| `mock_business_data.sql` | 业务演示数据（写入 `news`，帖子以 `content_type='POST'` 标注） |
| `seed_data_agent_config.sql` | 🔴 **数据智能体开关种子**：向 `system_config` 写 4 行 `config_group='data_agent'`（`enabled=true` 等）。**执行前必须先完成 `09_create_ai_ro_user.sql`**，否则等于给登录用户开放主账号写权限 |
| `seed_model_announcement.sql` | 公告弹窗种子数据 |
| `vip_migration.sql` | VIP 字段补列（**幂等；新库无需执行**，`init_database.sql` 已含这两列） |
| `migrate_encrypted_data.sql` | 历史 API Key 加密迁移（一次性运维，会建备份表 `ai_config_backup`） |
| `hot_score_algorithm.sql` | 热度分存储过程（作用于 `news`，**只创建、不自动执行**） |

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

历史备份与废弃脚本。当前只保留**最近两代完整备份**（`LATEST_PORTABLE.txt` 指向最新的那份）+ 1 个废弃 RBAC 脚本，共 4 个文件。
`Data_整理前快照_*`（10-04 目录重排前的副本）与 10-04 迁移中间快照已于 2026-10-07 清理。
