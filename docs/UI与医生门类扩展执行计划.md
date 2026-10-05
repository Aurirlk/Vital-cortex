# UI 体验与「医生/门类扩展性」完整执行计划

> 版本：v1.0 ｜ 日期：2026-10-03
> 依据：《调研与代码审查报告-20261003.md》「医生/门类扩展性」章节（现状 **52/100**）
> 原则：**前后端与数据表紧耦合设计**——先定数据契约，再定接口，最后定组件，避免三边各写各的导致二次返工

---

## 〇、当前进度（诚实汇报）

### ✅ 已完成

| 层 | 内容 |
|---|---|
| **数据库** | 迁移脚本已在正式库执行，8 步全部校验通过（news 39 行、comment 10、notification 21、指标 125→160、字典 5→12、医生账号 3 个） |
| **数据库** | 科室加 `parent_id/code/level/leader_id`；医生加账号与职称、擅长、执业证号等字段 |
| **数据库** | 全库字符集统一 `utf8mb4_0900_ai_ci`（含列级，校验为空） |
| **后端** | `News` 实体吸收 post 能力（content_type/title/统计字段/status）；`NewsMapper.xml` 同步重写 |
| **后端** | `Department` 实体支持层级；`HospitalDoctor` 实体完成解耦 |
| **后端** | `RoleEnum` 新增 `DOCTOR(3)`，`ProtectorAspect` 加固（空值防护+诊断日志） |
| **后端** | `HospitalDoctorMapper` + XML：账号方法 + **分页查询 + 远程搜索**（`searchForSelect`） |
| **后端** | 医生登录体系：`DoctorAuthService/Impl` + `DoctorController`（login / reset-password / home） |
| **后端** | `Comment` 实体 + `CommentVO` + `CommentMapper` + XML（多态+树形+软删+JOIN 补用户信息） |
| **后端** | `JwtInterceptor` 修掉「对医生也查 user 表」的无效查询 |

### ❌ 未完成（本计划覆盖）

| 层 | 缺口 |
|---|---|
| **后端** | 医生端业务接口（我的排班/我的患者/我的随访）；科室树接口；`AppointmentManage` 后端分页 |
| **后端** | `Comment` 的 Service/Controller（Mapper 已建，上层未接） |
| **前端** | **完全未动** —— 这是本次计划的主战场 |
| **验证** | 后端未编译（按约定由你在 IDEA 侧执行）；迁移后功能未端到端验证 |

---

## 一、目标架构

### 1.1 医生端导航（核心改造）

```
┌───────────────┬──────────────────────────────────────────────┐
│  科室树        │  全部科室 / 内科 / 心血管内科      [+新增医生] │
│  （可搜索）    │  [姓名搜索] [职称▾] [状态▾] [重置]            │
│               ├──────────────────────────────────────────────┤
│  内科    12  │ ┌──┬──────┬────────┬──────┬────┬──────┬────┐│
│   ▾ 心血管  4 │ │头像│姓名 │职称    │排班  │评分│状态  │操作││
│   ▾ 呼吸    3 │ ├──┼──────┼────────┼──────┼──────────┼────┤│
│ ▾ 外科     8  │ │...│ ... │ ...    │ ...  │... │ ...  │操作││
│ ▾ 儿科     6  │ └──┴──────┴────────┴──────┴──────────┴────┘│
│ ▾ 骨科     9  │        后端分页 + 固定操作列 + 空态            │
└───────────────┴──────────────────────────────────────────────┘
```

### 1.2 三端权限边界（决策 2 落地）

| 角色 | JWT role | 入口 | 可访问 |
|---|---|---|---|
| 管理员 | 1 | `/admin` | `@Protector(role="管理员")` |
| 普通用户 | 2 | `/user` | `@Protector` |
| **医生** | **3** | **`/doctor`** | **`@Protector(role="医生")`** |

医生只能进医生端；管理员与普通用户**不能**访问医生端端点（由 `RoleEnum` 严格比对保证）。

---

## 二、阶段 0：止血（P0 缺陷，1~2 天）

> 这些都是**确定性数据错误**，不是体验问题，必须最先修。

| # | 缺陷 | 位置 | 修复 |
|---|------|------|------|
| 0.1 | **删除医生无二次确认** | `AppointmentManage.vue:379,398` | 删除重复的 `deleteDoctor`，保留带 `$swalConfirm` 版本 |
| 0.2 | **医生职称永远存空** | `AppointmentManage.vue:199-202` | 4 个 `el-option` 的 `value` 改为 `"主任医师"` 等真实枚举 |
| 0.3 | **随访页锁定 doctorId=0** | `FollowupManage.vue:148` | 改真实医生选择器（见 3.2） |
| 0.4 | **手输患者/医生 ID** | `FollowupManage.vue:53,56` | 换 `<PatientSelect>` / `<DoctorSelect>` |
| 0.5 | **全站零表单校验** | 全部 admin 页 | 关键表单补 `el-form :rules` + `validate()` |
| 0.6 | 后端 P0：定时任务从未运行 | `PersonalHealthApplication.java` | 补 `@EnableScheduling` |
| 0.7 | 4 处明文密钥 | `application.yml` | 轮换并改 `${ENV_VAR}` |

---

## 三、阶段 1：后端接口补齐（2~3 天）

### 1.1 科室树

```java
@GetMapping("/department/tree")     // 管理端、医生端共用
Result<List<DepartmentTreeVO>> departmentTree();

@GetMapping("/department/list")     // 分页（科室多时）
Result<PageResult<DepartmentVO>> departmentPage(current, size);
```

`DepartmentTreeVO`：`{ id, name, code, parentId, level, leaderId, doctorCount, children[] }`
`doctorCount` 由子查询统计，前端树节点直接显示「内科 (12)」。

### 1.2 医生分页 + 远程搜索（**Mapper 已就绪，只需 Service/Controller**）

```java
@GetMapping("/doctor/list")     // name / deptId / titleLevel / status / current / size
Result<PageResult<DoctorVO>> doctorPage(...);

@GetMapping("/doctor/search")   // keyword / deptId / limit  → <DoctorSelect> 远程搜索
Result<List<DoctorVO>> searchDoctors(...);
```

> `HospitalDoctorMapper.queryDoctorPage` / `countDoctorPage` / `searchForSelect` 已在本次改造中建好，XML 也写好了 `JSON_CONTAINS(dept_ids)` 多科室匹配——Service 层直接调即可。

### 1.3 患者选择器

```java
@GetMapping("/patient/search")   // keyword（姓名/手机号后4位）/ limit
Result<List<PatientVO>> searchPatients(...);
```

`PatientVO`：`{ id, userName, userAccount, avatar, gender, age }`
⚠️ 手机号目前不在 `user` 表（全库只有 `shipping_address.receiver_phone`）。
若要做"手机号后 4 位"识别，**需先给 `user` 表加 `phone` 字段**（另见决策项）。本期可先用"姓名+账号"。

### 1.4 医生端业务接口（决策 2 配套）

```java
@Protector(role = "医生")
@GetMapping("/doctor/schedule/mine")     // 我的排班（按日期区间）
@GetMapping("/doctor/patient/mine")      // 我的患者（分页+搜索）
@GetMapping("/doctor/followup/mine")     // 我的随访任务（分页+状态筛选）
@PostMapping("/doctor/followup/checkin") // 随访打卡
```

**关键**：`HospitalDoctorMapper` 现有 `searchForSelect` 已支持多科室（`JSON_CONTAINS dept_ids`），
医生端「我的患者」应通过 `appointment`/`followup_task` 的 `doctor_id` 反查，而不是走 `user_id`。

### 1.5 Comment 上层（Mapper 已建）

```java
@GetMapping("/comment/roots")    // targetType / targetId / current / size
@GetMapping("/comment/children") // parentId / limit
@PostMapping("/comment/save")
@PostMapping("/comment/like")    // id / liked
```

Service 直接调 `CommentMapper`，注意：
- `queryRoots` 已 JOIN 用户表并统计 `child_count`，**不要再循环查用户**（规避 N+1）
- 点赞用 `increaseLikeCount` / `decreaseLikeCount`（原子 SQL，非先读后写）

---

## 四、阶段 2：公共组件（3~4 天，前端主战场）

> 这三个组件是「医生/门类变多」能否撑住的关键，抽出来后全站复用。

### 2.1 `<DoctorSelect>` — `src/components/DoctorSelect.vue`

| 能力 | 实现 |
|---|---|
| 远程搜索 | `filterable` + `remote` + `remote-method` → `GET /doctor/search` |
| 按科室分组 | `el-option-group :label="科室名"` |
| 大数据量 | 选项 > 20 时切 `el-select-v2` 虚拟滚动 |
| 级联缩减 | 支持 `dept-id` prop，先选科室再在科内选医生 |
| 展示格式 | `姓名 · 职称 · 科室`（不暴露裸 ID） |

Props：`modelValue / deptId / multiple / placeholder / disabled`
Events：`update:modelValue / change(doctor)`

### 2.2 `<PatientSelect>` — `src/components/PatientSelect.vue`

与 `DoctorSelect` 同构，数据源 `GET /patient/search`，展示「姓名 + 账号/手机后 4 位 + 性别年龄」。

### 2.3 `<DepartmentTree>` — `src/components/DepartmentTree.vue`

`el-tree` + 关键字过滤 + `node-key="id"` + 节点显示 `科室名 (医生数)`。
Events：`node-click(department)`。

### 2.4 顺带抽 `useCrudList` composable

`fetchFreshData / applyLocalPage / handleFilter / handleSizeChange / handleCurrentChange / batchDelete`
这套样板在 `UserManage`、`HealthModelConfigManage`、`TagsManage`、`NewsManage`、`EvaluationsManage`、
`UserHealthManage` 中**逐行重复 6 遍**，抽成 `useCrudList(queryApi)` 可省约 400 行。

---

## 五、阶段 3：页面改造（1 周）

| 页面 | 改造 | 优先级 |
|---|---|---|
| **AppointmentManage** | 重构为「科室树 + 医生列表」；4 个 Tab 全部补分页与筛选；修复 P0-1/P0-2 | ★★★ |
| **FollowupManage** | 换掉硬编码 `doctorId=0`；患者/医生改选择器；补状态与时间筛选 | ★★★ |
| **UserManage** | 本地分页 → 后端分页；修 `filterText` 恒空导致的搜索失效 | ★★ |
| **UserHealthManage** | 补患者选择器 + 指标下拉（`health-model-config/query`）+ 日期区间 | ★★ |
| **MallManage / QuizManage** | 补分页与搜索（当前**完全无分页**，数据量上来即不可用） | ★★ |
| **HealthModelConfigManage** | 裸 `<input>` 换 `el-form` + 校验；本地分页改后端 | ★ |
| **Home.vue（侧边栏）** | 15 项平铺 → 按域分组折叠（用户/医疗/内容/商城/AI/系统） | ★ |
| **面包屑** | 层级化（当前只有当前页名，且**刷新后为空**） | ★ |
| **NewsManage** | 适配合并后的 `news` 表（`content_type` 筛选 + `title` 字段） | ★★ |

---

## 六、阶段 4：体验与可维护性（1 周）

| # | 事项 | 说明 |
|---|---|---|
| 4.1 | 加载态全覆盖 | 当前 13 个页面**只有 4 处** `v-loading` |
| 4.2 | 空态统一 `el-empty` | 当前** 0 个**；只有 3 页有手写 `.empty-state` |
| 4.3 | 表单校验 | 当前** 0 处** `:rules`；必填星号纯装饰 |
| 4.4 | 错误提示 | 大量只 `console.error` 不提示用户（Evaluations 7:2、AiAnalysis 7:6…） |
| 4.5 | 响应式 | `admin-layout` 固定 `100vw` + 240px 侧栏，**无移动端断点**（医生查房平板刚需） |
| 4.6 | 拆巨型组件 | `SystemConfigManage.vue` 2234 行 → 按 7 个 Tab 拆子组件 |
| 4.7 | 清理死代码 | `Dashboard`(1195) / `AiDoctorManage`(650) / `RagMonitor`(477) **未注册路由，共 2322 行不可达** |
| 4.8 | 权限收敛 | 当前路由守卫只判 `token != null`，**无角色级守卫**；菜单不按角色收敛 |
| 4.9 | 修复 `handleDelete` 污染 | 5 处直接 `push(row)` 到 `selectedRows`，导致后续批量删除误删 |

---

## 七、数据库与前后端紧耦合对照表

> 改动任何一侧，必须同步更新此表与 `docs/数据表结构基线-20261003.md`。

| 业务概念 | 表 | 关键字段 | 后端接口 | 前端组件/页面 |
|---|---|---|---|---|
| 科室（层级） | `department` | `parent_id/code/level/leader_id` | `/department/tree` `/department/list` | `<DepartmentTree>` / AppointmentManage |
| 医生（独立身份） | `hospital_doctor` | `username/password/need_init_password/title_level/dept_ids/specialties` | `/doctor/login` `/doctor/list` `/doctor/search` `/doctor/reset-password` | `<DoctorSelect>` / 医生管理页 / 医生端 |
| 患者 | `user`(+`patient_profile`) | `user_account/user_name` ⚠️**无 phone** | `/patient/search` | `<PatientSelect>` / FollowupManage |
| 内容（资讯+帖子） | `news` | `content_type/title/user_id/统计字段/status` | `/news/query`（支持 content_type 筛选） | NewsManage |
| 评论（多态） | `comment` | `target_type/target_id/parent_id/reply_to_id` | `/comment/roots` `/comment/children` | 内容详情页 |
| 消息 | `notification` | `type`(tinyint 0~4) `source/biz_type/biz_id` | `/notification/*` | 消息中心 |
| 健康指标 | `user_health` + `health_model_config` | EAV：`config_id + value` | `/user-health/query` | UserHealthManage |
| 随访 | `followup_task` / `followup_record` | `doctor_id/patient_id`（**不再硬编码 0**） | `/followup/task/doctor/{doctorId}` | FollowupManage |

---

## 八、执行顺序与依赖

```
阶段0 止血（P0 缺陷，独立可先做）
   ↓
阶段1 后端接口（Mapper 已就绪的部分可先接：医生分页/搜索、科室树）
   ↓                                    ↘
阶段2 公共组件         ← 依赖 1.2/1.3 接口      阶段1.4 医生端业务接口
   ↓
阶段3 页面改造（AppointmentManage / FollowupManage 优先）
   ↓
阶段4 体验与可维护性（可与阶段3 并行推进）
```

**并行建议**：阶段 2 的组件开发与阶段 1.4 的医生端接口可并行；
阶段 4 的重构（拆组件、清死代码）不依赖任何接口，随时可做。

---

## 九、需要你确认的两个决策

| # | 问题 | 影响 |
|---|------|------|
| 1 | **`user` 表要不要加 `phone` 字段？** | 不做：`<PatientSelect>` 只能显示"姓名+账号"，短信登录仍是死功能<br>做：可显示"姓名+手机后4位"，并为短信登录铺路 |
| 2 | **医生端界面从零建，还是复用用户端布局？** | 从零建：工作量大但语义清晰（医生视角：排班/患者/随访）<br>复用：快，但会混入用户端无关模块 |

---

## 十、验收标准

- [ ] 科室树可展开到多级，节点显示医生数
- [ ] 医生列表支持「按科室筛选 + 姓名搜索 + 职称筛选 + 后端分页」
- [ ] `<DoctorSelect>` 输入关键词可远程搜索，>20 项自动虚拟滚动
- [ ] 随访任务不再硬编码医生，可按医生筛选
- [ ] 删除医生有二次确认；职称可正确保存
- [ ] 管理端新增 100 名医生后，页面仍流畅（分页+虚拟滚动生效）
- [ ] 医生账号可独立登录，登录后**不能**跳到用户端或管理端
- [ ] 全站列表页有 loading 与空态
- [ ] 2322 行未注册路由死代码清理完毕
