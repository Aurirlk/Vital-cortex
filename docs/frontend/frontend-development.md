# 智康云健康管理系统 - 前端开发文档

## 1. 技术架构

### 1.1 技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.x | 前端框架 |
| Vue Router | 4.x | 路由管理 |
| Element Plus | 2.x | UI 组件库 |
| Axios | 1.x | HTTP 请求 |
| ECharts | 5.x | 数据可视化 |
| SCSS | - | CSS 预处理器 |

### 1.2 项目结构
```
personal-heath-view/
├── src/
│   ├── assets/              # 静态资源
│   │   ├── themes.css       # 主题系统
│   │   └── styles/          # 样式文件
│   │       ├── design-tokens.css    # 设计 tokens
│   │       ├── global-overrides.css # 全局样式覆盖
│   │       └── brand.css            # 品牌样式
│   ├── components/          # 公共组件
│   │   ├── BrandLogo.vue    # 品牌 Logo
│   │   ├── BrandDecoration.vue # 品牌装饰
│   │   ├── Logo.vue         # 原有 Logo
│   │   ├── Banner.vue       # 轮播图
│   │   └── TagLine.vue      # 标签栏
│   ├── router/              # 路由配置
│   │   └── index.js
│   ├── utils/               # 工具函数
│   │   ├── request.js       # Axios 封装
│   │   ├── storage.js       # 本地存储
│   │   └── data.js          # 数据处理
│   └── views/               # 页面组件
│       ├── login/           # 登录页
│       ├── register/        # 注册页
│       ├── user/            # 用户端页面
│       └── admin/           # 管理端页面
├── public/                  # 公共静态文件
├── package.json
└── vue.config.js
```

## 2. 设计系统

### 2.1 小红书风格设计 Tokens
```css
/* 主色调 - 珊瑚红 */
--xh-primary: #ff2442;
--xh-primary-light: #ff6b81;
--xh-primary-gradient: linear-gradient(135deg, #ff2442, #ff6b81);

/* 背景色 */
--xh-bg: #f5f5f5;
--xh-bg-card: #ffffff;

/* 文字色 */
--xh-text-primary: #1a1a1a;
--xh-text-secondary: #666666;
--xh-text-muted: #999999;

/* 圆角 */
--xh-radius-sm: 8px;
--xh-radius-md: 12px;
--xh-radius-lg: 16px;

/* 阴影 */
--xh-shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.06);
--xh-shadow-md: 0 4px 16px rgba(0, 0, 0, 0.08);
```

### 2.2 品牌色彩
```css
/* 品牌主色 - 专业医疗青色 */
--brand-primary: #0EA5A5;
--brand-primary-gradient: linear-gradient(135deg, #0EA5A5, #15559a);

/* 品牌渐变 */
--brand-gradient-hero: linear-gradient(135deg, #0EA5A5 0%, #15559a 50%, #a855f7 100%);
```

## 3. 路由设计

### 3.1 用户端路由
| 路径 | 页面 | 说明 |
|------|------|------|
| /login | Login.vue | 登录页（左右分栏） |
| /register | Register.vue | 注册页 |
| /user/news-record | Home.vue | 健康资讯 |
| /user/appointment | Appointment.vue | 医生预约 |
| /user/quiz | Quiz.vue | 健康测验 |
| /user/mall | Mall.vue | 健康商城 |
| /user/followup | Followup.vue | 患者随访 |
| /user/ai-analysis | AiAnalysis.vue | AI 健康分析 |
| /user/drug | Drug.vue | 药品订阅 |
| /user/profile | UserProfile.vue | 个人中心 |

### 3.2 管理端路由
| 路径 | 页面 | 说明 |
|------|------|------|
| /admin/adminLayout | Main.vue | 仪表盘 |
| /admin/userManage | UserManage.vue | 用户管理 |
| /admin/newsManage | NewsManage.vue | 资讯管理 |
| /admin/appointmentManage | AppointmentManage.vue | 预约管理 |
| /admin/quizManage | QuizManage.vue | 测验管理 |
| /admin/mallManage | MallManage.vue | 商城管理 |
| /admin/followupManage | FollowupManage.vue | 随访管理 |

## 4. 核心组件说明

### 4.1 登录页 (Login.vue)
**设计特点**：
- 左右分栏布局（左侧产品介绍 + 右侧登录表单）
- 支持账号密码登录和手机号登录（模拟短信）
- 渐变背景 + 装饰圆圈动画
- 响应式设计（移动端隐藏左侧）

**核心代码**：
```vue
<template>
  <div class="login-container">
    <div class="login-left">
      <!-- 产品介绍、功能特性 -->
    </div>
    <div class="login-right">
      <!-- 登录方式切换 Tab -->
      <!-- 账号密码表单 / 手机号表单 -->
    </div>
  </div>
</template>
```

### 4.2 医生预约页 (Appointment.vue)
**设计特点**：
- 步骤条引导（选择科室→选择医生→选择时间→确认预约）
- 卡片式科室/医生展示
- 日历选择排班时间
- 我的预约列表

### 4.3 健康测验页 (Quiz.vue)
**设计特点**：
- 试卷列表卡片展示
- 答题页面（倒计时、题目切换）
- 成绩报告（分数、正确率、通过状态）
- 测验历史记录

### 4.4 健康商城页 (Mall.vue)
**设计特点**：
- 分类筛选栏
- 商品网格布局（图片、名称、价格、销量）
- 购物车浮窗 + 弹窗
- 商品详情弹窗
- 下单结算流程

### 4.5 患者随访页 (Followup.vue)
**设计特点**：
- 任务统计卡片（待完成、进行中、已完成、已逾期）
- 任务类型筛选（用药、复诊、指标、运动、饮食）
- 任务列表（状态颜色标识）
- 打卡弹窗 + 记录查看弹窗

## 5. API 请求封装

### 5.1 Axios 配置
```javascript
// utils/request.js
const request = axios.create({
  baseURL: "http://localhost:21090/api/personal-health/v1.0",
  timeout: 30000,
});

// 请求拦截器 - 添加 Token
request.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers["token"] = token;
  }
  return config;
});
```

### 5.2 API 调用示例
```javascript
// GET 请求
const { data } = await request.get("appointment/departments");

// POST 请求
const { data } = await request.post("appointment/book", {
  scheduleId: 1,
  symptomDescription: "头疼"
});

// PUT 请求
await request.put("mall/product/update", productData);

// DELETE 请求
await request.delete(`mall/cart/${itemId}`);
```

## 6. 样式规范

### 6.1 卡片样式
```scss
.card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.25s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }
}
```

### 6.2 按钮样式
```scss
.btn--primary {
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 10px 24px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(255, 36, 66, 0.35);
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(255, 36, 66, 0.45);
  }
}
```

### 6.3 标签样式
```scss
.tag {
  display: inline-flex;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  
  &--primary {
    background: rgba(255, 36, 66, 0.06);
    color: #ff2442;
  }
  
  &--success {
    background: rgba(7, 193, 96, 0.06);
    color: #07c160;
  }
}
```

## 7. 启动命令

```bash
cd 前端/personal-heath-view
npm install
npm run dev
```

默认端口: 8080
访问地址: http://localhost:8080
