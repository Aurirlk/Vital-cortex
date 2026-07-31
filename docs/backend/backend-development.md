# 智康云健康管理系统 - 后端开发文档

## 1. 技术架构

### 1.1 技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.7.18 | 应用框架 |
| MyBatis | 3.5.x | ORM 框架 |
| MySQL | 8.0 | 关系型数据库 |
| JWT | - | 身份认证 |
| Lombok | - | 代码简化 |

### 1.2 项目结构
```
personal-health-api/
├── src/main/java/cn/kmbeast/
│   ├── aop/                    # AOP 切面（鉴权、分页）
│   ├── config/                 # 配置类
│   ├── controller/             # 控制器层
│   ├── mapper/                 # Mapper 接口
│   ├── pojo/
│   │   ├── api/                # 统一响应封装
│   │   ├── dto/                # 数据传输对象
│   │   ├── entity/             # 实体类
│   │   └── vo/                 # 视图对象
│   ├── service/                # 业务逻辑接口
│   │   └── impl/               # 业务逻辑实现
│   └── utils/                  # 工具类
├── src/main/resources/
│   ├── mapper/                 # MyBatis XML 映射文件
│   └── application.yml         # 应用配置
└── pom.xml                     # Maven 配置
```

## 2. 核心设计模式

### 2.1 分层架构
```
Controller → Service → Mapper → Database
    ↓           ↓         ↓
  请求处理    业务逻辑   数据访问
```

### 2.2 统一响应封装
```java
// 成功响应
ApiResult.success()              // 无数据
ApiResult.success(data)          // 有数据
PageResult.success(list, total)  // 分页响应

// 错误响应
ApiResult.error("错误信息")
```

### 2.3 AOP 注解
```java
@Protector              // 鉴权注解（普通用户）
@Protector(role = "管理员")  // 鉴权注解（管理员）
@Pager                  // 分页注解
```

## 3. API 接口设计

### 3.1 论坛模块 `/post`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /post/save | 发帖 | 用户 |
| POST | /post/batchDelete | 删帖 | 管理员 |
| PUT | /post/update | 编辑帖子 | 用户 |
| POST | /post/query | 查询帖子列表 | 用户 |
| GET | /post/getById/{id} | 获取帖子详情 | 用户 |
| POST | /post/like/{postId} | 点赞 | 用户 |
| POST | /post/unlike/{postId} | 取消点赞 | 用户 |
| POST | /post/favorite/{postId} | 收藏 | 用户 |
| POST | /post/unfavorite/{postId} | 取消收藏 | 用户 |
| POST | /post/reply | 回复帖子 | 用户 |
| GET | /post/replies/{postId} | 获取回复列表 | 用户 |
| POST | /post/follow/{followeeId} | 关注用户 | 用户 |
| POST | /post/unfollow/{followeeId} | 取消关注 | 用户 |
| GET | /post/hotList | 热榜 | 用户 |
| GET | /post/search?keyword= | 搜索帖子 | 用户 |
| POST | /post/report | 举报 | 用户 |

### 3.2 预约模块 `/appointment`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /appointment/departments | 获取科室列表 | 用户 |
| POST | /appointment/department/save | 添加科室 | 管理员 |
| GET | /appointment/doctors | 获取医生列表 | 用户 |
| GET | /appointment/doctor/{id} | 获取医生详情 | 用户 |
| POST | /appointment/doctor/save | 添加医生 | 管理员 |
| GET | /appointment/schedules | 获取排班列表 | 用户 |
| GET | /appointment/schedules/available | 获取可用号源 | 用户 |
| POST | /appointment/schedule/save | 添加排班 | 管理员 |
| POST | /appointment/book | 预约挂号 | 用户 |
| POST | /appointment/cancel/{id} | 取消预约 | 用户 |
| POST | /appointment/query | 查询预约记录 | 用户 |
| GET | /appointment/getById/{id} | 获取预约详情 | 用户 |
| POST | /appointment/visitRecord/save | 保存就诊记录 | 医生 |
| GET | /appointment/visitRecord/{appointmentId} | 获取就诊记录 | 用户 |

### 3.3 测验模块 `/quiz`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /quiz/question/save | 添加题目 | 管理员 |
| PUT | /quiz/question/update | 编辑题目 | 管理员 |
| POST | /quiz/question/batchDelete | 删除题目 | 管理员 |
| POST | /quiz/question/query | 查询题目列表 | 用户 |
| GET | /quiz/question/{id} | 获取题目详情 | 用户 |
| POST | /quiz/exam/save | 创建试卷 | 管理员 |
| PUT | /quiz/exam/update | 编辑试卷 | 管理员 |
| POST | /quiz/exam/batchDelete | 删除试卷 | 管理员 |
| GET | /quiz/exam/list | 获取试卷列表 | 用户 |
| GET | /quiz/exam/{id} | 获取试卷详情 | 用户 |
| POST | /quiz/exam/{examId}/questions | 组卷 | 管理员 |
| POST | /quiz/start/{examId} | 开始考试 | 用户 |
| POST | /quiz/submit/{recordId} | 提交试卷 | 用户 |
| GET | /quiz/records | 获取考试记录 | 用户 |
| GET | /quiz/record/{recordId}/answers | 获取答题详情 | 用户 |

### 3.4 商城模块 `/mall`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /mall/categories | 获取分类列表 | 用户 |
| POST | /mall/category/save | 添加分类 | 管理员 |
| POST | /mall/product/save | 添加商品 | 管理员 |
| PUT | /mall/product/update | 编辑商品 | 管理员 |
| POST | /mall/product/batchDelete | 删除商品 | 管理员 |
| POST | /mall/product/query | 查询商品列表 | 用户 |
| GET | /mall/product/{id} | 获取商品详情 | 用户 |
| POST | /mall/cart/add | 加入购物车 | 用户 |
| PUT | /mall/cart/update | 更新购物车 | 用户 |
| DELETE | /mall/cart/{id} | 删除购物车项 | 用户 |
| GET | /mall/cart/list | 获取购物车列表 | 用户 |
| POST | /mall/order/create | 创建订单 | 用户 |
| POST | /mall/order/pay/{orderId} | 支付订单 | 用户 |
| GET | /mall/order/list | 获取订单列表 | 用户 |
| GET | /mall/order/{id} | 获取订单详情 | 用户 |
| GET | /mall/address/list | 获取地址列表 | 用户 |
| POST | /mall/address/save | 添加地址 | 用户 |
| PUT | /mall/address/update | 编辑地址 | 用户 |
| DELETE | /mall/address/{id} | 删除地址 | 用户 |

### 3.5 随访模块 `/followup`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /followup/task/save | 创建任务 | 管理员 |
| PUT | /followup/task/update | 编辑任务 | 管理员 |
| POST | /followup/task/batchDelete | 删除任务 | 管理员 |
| GET | /followup/task/patient/{patientId} | 获取患者任务 | 用户 |
| GET | /followup/task/doctor/{doctorId} | 获取医生任务 | 医生 |
| GET | /followup/task/{id} | 获取任务详情 | 用户 |
| POST | /followup/checkin | 任务打卡 | 用户 |
| GET | /followup/task/{taskId}/records | 获取打卡记录 | 用户 |

## 4. 核心代码说明

### 4.1 预约挂号（并发安全）
```java
@Transactional
public Result<Void> bookAppointment(Appointment appointment) {
    DoctorSchedule schedule = scheduleMapper.getById(appointment.getScheduleId());
    if (schedule.getBookedCount() >= schedule.getMaxPatients()) {
        return ApiResult.error("号源已满");
    }
    // 生成序号
    Integer currentCount = appointmentMapper.countByScheduleId(schedule.getId());
    appointment.setSerialNumber(currentCount + 1);
    appointmentMapper.save(appointment);
    // 更新已预约数（乐观锁）
    scheduleMapper.incrementBookedCount(schedule.getId());
    return ApiResult.success();
}
```

### 4.2 热度算法
```sql
UPDATE post SET hot_score = (
    view_count * 1 + 
    like_count * 3 + 
    favorite_count * 2 + 
    comment_count * 4 + 
    share_count * 5
) WHERE id = #{id}
```

### 4.3 自动评分
```java
// 客观题自动评分
if (question.getQuestionType() < 3) {
    boolean correct = question.getAnswer().trim()
        .equalsIgnoreCase(answer.getAnswer().trim());
    answer.setIsCorrect(correct ? 1 : 0);
    answer.setScore(correct ? question.getScore() : 0);
}
```

## 5. 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/personal_health?characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8
    username: root
    password: 1234
    driver-class-name: com.mysql.cj.jdbc.Driver
```

## 6. 启动命令

```bash
cd 后端/personal-health-api
mvn spring-boot:run
```

默认端口: 21090
API 基础路径: /api/personal-health/v1.0
