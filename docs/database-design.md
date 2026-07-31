# 智康云健康管理系统 - 数据库设计文档

## 1. 数据库概述

- **数据库**: MySQL 8.0
- **字符集**: utf8mb4
- **存储引擎**: InnoDB
- **总表数**: 30+ 张

## 2. ER 图

### 2.1 核心业务模块
```
user (用户表)
  ├── news (健康资讯)
  │     ├── news_save (收藏)
  │     └── evaluations (评论)
  ├── user_health (健康记录)
  │     └── health_model_config (健康模型)
  ├── drug_subscription (药品订阅)
  │     └── drug (药品)
  └── message (消息通知)
```

### 2.2 论坛模块
```
post (帖子)
  ├── post_reply (回复)
  ├── post_like (点赞)
  ├── post_favorite (收藏)
  └── post_report (举报)

user_follow (用户关注)
post_tag (论坛标签)
```

### 2.3 预约模块
```
department (科室)
  └── hospital_doctor (医生)
        └── doctor_schedule (排班)
              └── appointment (预约)
                    └── visit_record (就诊记录)
```

### 2.4 测验模块
```
quiz_question (题库)
  └── quiz_exam_question (试卷题目)
        └── quiz_exam (试卷)
              └── quiz_record (考试记录)
                    └── quiz_answer (答题记录)
```

### 2.5 商城模块
```
product_category (商品分类)
  └── mall_product (商品)
        ├── shopping_cart (购物车)
        └── order_item (订单商品)
              └── mall_order (订单)
                    └── shipping_address (收货地址)
```

### 2.6 随访模块
```
followup_task (随访任务)
  └── followup_record (打卡记录)
```

### 2.7 AI 与智能体模块
```
ai_usage (AI token 用量记录)
  └── user (用户ID 关联，可为空)

crm_sqlite (CRM 只读沙箱，SQLite 文件，非 MySQL)
  └── 由 SqlGuard 强制约束：仅 SELECT + 租户隔离，禁止写操作
```
> 说明：AI 相关的向量召回数据（本地向量库）以 JSON 文件持久化在后端数据目录，**不落 MySQL**；
> CRM 网关的 SQL 执行走独立的 SQLite 沙箱库，与主业务 MySQL 隔离。

## 3. 表结构详细设计

### 3.1 核心业务表

#### user (用户表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 用户ID |
| user_account | VARCHAR(50), UNIQUE | 用户账号 |
| user_name | VARCHAR(50) | 用户昵称 |
| user_pwd | VARCHAR(100) | 密码(BCrypt) |
| user_avatar | VARCHAR(255) | 头像 |
| user_email | VARCHAR(100) | 邮箱 |
| user_role | INT | 角色(1:管理员,2:用户,3:商家,4:医生) |
| is_login | TINYINT(1) | 可登录状态 |
| is_word | TINYINT(1) | 禁言状态 |
| create_time | DATETIME | 创建时间 |

#### news (健康资讯表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 资讯ID |
| name | VARCHAR(200) | 标题 |
| content | LONGTEXT | 内容 |
| tag_id | INT | 分类ID |
| cover | VARCHAR(255) | 封面图 |
| reader_ids | TEXT | 阅读者ID列表 |
| is_top | TINYINT(1) | 是否置顶 |
| is_banner | TINYINT(1) | 是否轮播图 |
| create_time | DATETIME | 创建时间 |

### 3.2 论坛模块表

#### post (帖子表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 帖子ID |
| user_id | INT | 发帖用户ID |
| title | VARCHAR(200) | 帖子标题 |
| content | LONGTEXT | 帖子内容 |
| cover | VARCHAR(500) | 封面图 |
| tag_id | INT | 分类ID |
| view_count | INT UNSIGNED | 浏览数 |
| like_count | INT UNSIGNED | 点赞数 |
| favorite_count | INT UNSIGNED | 收藏数 |
| comment_count | INT UNSIGNED | 评论数 |
| share_count | INT UNSIGNED | 分享数 |
| hot_score | DOUBLE | 热度分 |
| status | TINYINT(1) | 状态(0:草稿,1:已发布,2:已锁定) |
| is_top | TINYINT(1) | 是否置顶 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### post_reply (帖子回复表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 回复ID |
| post_id | INT | 帖子ID |
| user_id | INT | 回复用户ID |
| parent_id | INT | 父回复ID |
| content | VARCHAR(2000) | 回复内容 |
| like_count | INT UNSIGNED | 点赞数 |
| create_time | DATETIME | 创建时间 |

#### post_like (帖子点赞表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 点赞ID |
| user_id | INT | 用户ID |
| post_id | INT | 帖子ID |
| create_time | DATETIME | 创建时间 |
| **UNIQUE** | (user_id, post_id) | 防重复点赞 |

### 3.3 预约模块表

#### department (科室表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 科室ID |
| name | VARCHAR(100), UNIQUE | 科室名称 |
| description | VARCHAR(500) | 科室描述 |
| cover | VARCHAR(500) | 科室图片 |
| sort_order | INT | 排序 |
| status | TINYINT(1) | 状态 |
| create_time | DATETIME | 创建时间 |

#### hospital_doctor (医生表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 医生ID |
| user_id | INT | 关联用户ID |
| name | VARCHAR(50) | 医生姓名 |
| avatar | VARCHAR(500) | 头像 |
| title | VARCHAR(50) | 职称 |
| department_id | INT | 所属科室ID |
| introduction | TEXT | 个人简介 |
| expertise | VARCHAR(500) | 擅长领域 |
| is_online | TINYINT(1) | 在线状态 |
| status | TINYINT(1) | 状态 |
| create_time | DATETIME | 创建时间 |

#### doctor_schedule (排班表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 排班ID |
| doctor_id | INT | 医生ID |
| schedule_date | DATE | 排班日期 |
| time_slot | VARCHAR(20) | 时间段(morning/afternoon/evening) |
| max_patients | INT UNSIGNED | 号源总数 |
| booked_count | INT UNSIGNED | 已预约数 |
| version | INT UNSIGNED | 乐观锁版本号 |
| status | TINYINT(1) | 状态 |
| **UNIQUE** | (doctor_id, schedule_date, time_slot) | 唯一排班 |

#### appointment (预约表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 预约ID |
| patient_id | INT | 患者ID |
| doctor_id | INT | 医生ID |
| schedule_id | INT | 排班ID |
| department_id | INT | 科室ID |
| appointment_date | DATE | 预约日期 |
| time_slot | VARCHAR(20) | 时间段 |
| serial_number | INT UNSIGNED | 就诊序号 |
| symptom_description | VARCHAR(1000) | 症状描述 |
| status | TINYINT(1) | 状态(0:待确认,1:已确认,2:已完成,3:已取消) |
| create_time | DATETIME | 创建时间 |

### 3.4 测验模块表

#### quiz_question (题库表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 题目ID |
| category_id | INT | 分类ID |
| question_type | TINYINT(1) | 题型(0:单选,1:多选,2:判断,3:填空,4:简答) |
| title | VARCHAR(1000) | 题目内容 |
| options | JSON | 选项 |
| answer | VARCHAR(2000) | 正确答案 |
| analysis | VARCHAR(2000) | 解析 |
| difficulty | TINYINT(1) | 难度(1:简单,2:中等,3:困难) |
| score | INT UNSIGNED | 分值 |
| status | TINYINT(1) | 状态 |
| create_time | DATETIME | 创建时间 |

#### quiz_exam (试卷表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 试卷ID |
| title | VARCHAR(200) | 试卷名称 |
| description | VARCHAR(500) | 描述 |
| duration_minutes | INT UNSIGNED | 考试时长(分钟) |
| total_score | INT UNSIGNED | 总分 |
| pass_score | INT UNSIGNED | 及格分 |
| difficulty | TINYINT(1) | 难度 |
| question_count | INT UNSIGNED | 题目数量 |
| status | TINYINT(1) | 状态 |
| create_time | DATETIME | 创建时间 |

### 3.5 商城模块表

#### product_category (商品分类表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 分类ID |
| name | VARCHAR(100) | 分类名称 |
| parent_id | INT | 父分类ID |
| icon | VARCHAR(500) | 分类图标 |
| sort_order | INT | 排序 |
| status | TINYINT(1) | 状态 |
| create_time | DATETIME | 创建时间 |

#### mall_product (商品表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 商品ID |
| category_id | INT | 分类ID |
| name | VARCHAR(200) | 商品名称 |
| description | TEXT | 商品描述 |
| cover | VARCHAR(500) | 商品图片 |
| price | DECIMAL(10,2) | 售价 |
| original_price | DECIMAL(10,2) | 原价 |
| stock | INT UNSIGNED | 库存 |
| sales_count | INT UNSIGNED | 销量 |
| unit | VARCHAR(50) | 单位 |
| status | TINYINT(1) | 状态 |
| is_hot | TINYINT(1) | 是否热销 |
| is_new | TINYINT(1) | 是否新品 |
| create_time | DATETIME | 创建时间 |

#### mall_order (订单表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 订单ID |
| order_no | VARCHAR(50), UNIQUE | 订单号 |
| user_id | INT | 用户ID |
| total_amount | DECIMAL(10,2) | 订单总额 |
| actual_amount | DECIMAL(10,2) | 实付金额 |
| status | TINYINT(1) | 状态(0:待付款,1:已付款,2:已发货,3:已收货,4:已完成,5:已取消) |
| payment_method | VARCHAR(50) | 支付方式 |
| payment_time | DATETIME | 支付时间 |
| shipping_address_id | INT | 收货地址ID |
| create_time | DATETIME | 创建时间 |

### 3.6 随访模块表

#### followup_task (随访任务表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT UNSIGNED, PK | 任务ID |
| patient_id | INT | 患者ID |
| doctor_id | INT | 医生ID |
| title | VARCHAR(200) | 任务标题 |
| description | VARCHAR(1000) | 任务描述 |
| task_type | VARCHAR(50) | 任务类型 |
| due_date | DATE | 截止日期 |
| status | TINYINT(1) | 状态(0:待完成,1:进行中,2:已完成,3:已逾期) |
| create_time | DATETIME | 创建时间 |

### 3.7 AI 与智能体表

#### ai_usage (AI Token 用量记录表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT, PK, AUTO_INCREMENT | 主键 |
| user_id | INT | 用户ID（可为空，非登录态调用） |
| scene | VARCHAR(50) | 场景：chat/stream/keyword/websearch/rag_eval |
| model | VARCHAR(100) | 模型名 |
| prompt_tokens | INT | 输入 token 数 |
| completion_tokens | INT | 输出 token 数 |
| total_tokens | INT | 总 token 数 |
| create_time | DATETIME | 创建时间 |

> 初始化脚本：`Data/sql/ai_usage_schema.sql`

## 4. 索引设计

### 4.1 主键索引
所有表使用 `INT UNSIGNED AUTO_INCREMENT` 作为主键。

### 4.2 唯一索引
- `user.user_account` - 用户账号唯一
- `post_like.(user_id, post_id)` - 防重复点赞
- `post_favorite.(user_id, post_id)` - 防重复收藏
- `user_follow.(follower_id, followee_id)` - 防重复关注
- `doctor_schedule.(doctor_id, schedule_date, time_slot)` - 唯一排班
- `appointment.(schedule_id, serial_number)` - 防重复预约
- `shopping_cart.(user_id, product_id)` - 防重复加购
- `mall_order.order_no` - 订单号唯一

### 4.3 普通索引
- 外键字段索引（user_id, post_id, doctor_id 等）
- 查询频繁字段索引（status, create_time 等）
- 热度分排序索引（hot_score DESC）

## 5. 数据库初始化

### 5.1 创建数据库
```sql
CREATE DATABASE personal_health DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE personal_health;
```

### 5.2 执行初始化脚本
```sql
source Data/sql/deploy/init_database.sql;
source Data/sql/forum_schema.sql;
source Data/sql/appointment_schema.sql;
source Data/sql/extra_modules_schema.sql;
```

### 5.3 默认数据
- 默认管理员: admin/123456
- 默认健康模型: 收缩压、舒张压、空腹血糖、BMI、心率
- 默认标签: 饮食健康、运动健身、心理健康、疾病预防、养生保健
- 默认科室: 内科、外科、儿科、妇产科、眼科、耳鼻喉科、皮肤科、中医科、骨科、神经内科
- 默认商品分类: 药品、医疗器械、保健品、健康食品、健身器材

### 5.4 AI 模块初始化
```sql
-- AI token 用量表（v5.1 新增）
source Data/sql/ai_usage_schema.sql;
```
> 向量库与 SQLite 沙箱为文件型存储，无需 SQL 初始化；首次启动由后端自动建库。
