import { createRouter, createWebHashHistory } from "vue-router";
import { getToken } from "@/utils/storage.js";
import {
  getRole,
  canAccess,
  homeOf,
  isDoctorLoggedIn,
  clearDoctorToken,
  ROLE_DOCTOR,
} from "@/utils/doctorAuth.js";

const routes = [
  {
    path: "/:pathMatch(.*)*",
    redirect: "/login",
  },
  {
    path: "/login",
    component: () => import(`@/views/login/Login.vue`),
  },
  {
    path: "/register",
    component: () => import(`@/views/register/Register.vue`),
  },

  // ==================== 医生端（独立账号体系，与用户端隔离） ====================
  {
    path: "/doctor/login",
    component: () => import(`@/views/doctor/DoctorLogin.vue`),
  },
  {
    path: "/doctor",
    component: () => import(`@/views/doctor/DoctorLayout.vue`),
    redirect: "/doctor/home",
    children: [
      {
        name: "医生工作台",
        path: "home",
        component: () => import(`@/views/doctor/DoctorHome.vue`),
        meta: { requireAuth: true, role: "医生" },
      },
      {
        name: "我的接诊",
        path: "appointments",
        component: () => import(`@/views/doctor/DoctorAppointments.vue`),
        meta: { requireAuth: true, role: "医生" },
      },
      {
        name: "患者详情",
        path: "patient",
        component: () => import(`@/views/doctor/PatientDetail.vue`),
        meta: { requireAuth: true, role: "医生" },
      },
      {
        name: "我的排班",
        path: "schedules",
        component: () => import(`@/views/doctor/DoctorSchedules.vue`),
        meta: { requireAuth: true, role: "医生" },
      },
    ],
  },
  {
    path: "/message",
    component: () => import(`@/views/user/Message.vue`),
  },
  {
    path: "/record",
    component: () => import(`@/views/user/Record.vue`),
  },
  {
    path: "/admin",
    component: () => import(`@/views/admin/Home.vue`),
    redirect: "adminLayout",
    meta: {
      requireAuth: true,
    },
    children: [
      {
        path: "adminLayout",
        name: "数据概览",
        icon: "PieChart",
        component: () => import(`@/views/admin/Main.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "userManage",
        name: "用户管理",
        icon: "User",
        component: () => import(`@/views/admin/UserManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "tagsManage",
        name: "栏目管理",
        icon: "House",
        component: () => import(`@/views/admin/TagsManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "newsManage",
        name: "资讯管理",
        icon: "Document",
        component: () => import(`@/views/admin/NewsManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "healthModelConfigManage",
        name: "指标管理",
        icon: "Files",
        component: () => import(`@/views/admin/HealthModelConfigManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "userHealthManage",
        name: "健康数据",
        icon: "ScaleToOriginal",
        component: () => import(`@/views/admin/UserHealthManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "messageManage",
        name: "消息中心",
        icon: "Message",
        component: () => import(`@/views/admin/MessageManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "evaluationsManage",
        name: "评价管理",
        icon: "ChatDotRound",
        component: () => import(`@/views/admin/EvaluationsManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "drugManage",
        name: "商城管理",
        icon: "ShoppingCart",
        component: () => import(`@/views/admin/MallManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "systemConfig",
        name: "系统配置",
        icon: "Setting",
        component: () => import(`@/views/admin/SystemConfigManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "appointmentManage",
        name: "预约管理",
        icon: "Calendar",
        component: () => import(`@/views/admin/AppointmentManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "quizManage",
        name: "测评管理",
        icon: "EditPen",
        component: () => import(`@/views/admin/QuizManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "mallManage",
        redirect: "drugManage",
        meta: { requireAuth: true },
        isHidden: true,
      },
      {
        path: "followupManage",
        name: "随访管理",
        icon: "Check",
        component: () => import(`@/views/admin/FollowupManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "auditManage",
        name: "审计日志",
        icon: "Warning",
        component: () => import(`@/views/admin/AuditManage.vue`),
        meta: { requireAuth: true },
      },
    ],
  },
  {
    path: "/user",
    component: () => import(`@/views/user/Main.vue`),
    meta: {
      requireAuth: true,
    },
    children: [
      {
        name: "首页",
        path: "news-record",
        icon: "HomeFilled",
        component: () => import(`@/views/user/Home.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "我的收藏",
        path: "my-save",
        icon: "Star",
        component: () => import(`@/views/user/NewsSave.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "健康模型",
        path: "user-health-model",
        icon: "FirstAidKit",
        component: () => import(`@/views/user/UserHealthModel.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "",
        path: "news-detail",
        component: () => import(`@/views/user/NewsDetail.vue`),
        meta: {
          requireAuth: true,
        },
        isHidden: true,
      },
      {
        name: "",
        path: "search-detail",
        component: () => import(`@/views/user/Search.vue`),
        meta: {
          requireAuth: true,
        },
        isHidden: true,
      },
      {
        name: "AI 问诊",
        path: "ai-analysis",
        icon: "ChatDotRound",
        component: () => import(`@/views/user/AiAnalysis.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "健康助手",
        path: "assistant",
        icon: "Service",
        component: () => import(`@/views/user/Assistant.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "个人中心",
        path: "profile",
        icon: "User",
        component: () => import(`@/views/user/UserProfile.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "预约挂号",
        path: "appointment",
        icon: "Calendar",
        component: () => import(`@/views/user/Appointment.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "健康测评",
        path: "quiz",
        icon: "EditPen",
        component: () => import(`@/views/user/Quiz.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "健康商城",
        path: "mall",
        icon: "ShoppingCart",
        component: () => import(`@/views/user/Mall.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "随访管理",
        path: "followup",
        icon: "Check",
        component: () => import(`@/views/user/Followup.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "健康报告",
        path: "report",
        icon: "Document",
        component: () => import(`@/views/user/Report.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "设置",
        path: "settings",
        icon: "Setting",
        component: () => import(`@/views/user/UserSettings.vue`),
        meta: { requireAuth: true },
        isHidden: true,
      },
    ],
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

router.onError((error) => {
  console.error("[Router Error]", error);
});

router.beforeEach((to, from, next) => {
  // ============ 医生端独立守卫 ============
  // 医生走 doctor-token 与 /doctor/login，与用户端 token 互不相通。
  if (to.path.startsWith("/doctor")) {
    if (to.path === "/doctor/login") {
      // 已登录的医生不必再进登录页
      return isDoctorLoggedIn() ? next("/doctor/home") : next();
    }
    if (!isDoctorLoggedIn()) {
      clearDoctorToken();
      return next("/doctor/login");
    }
    return next();
  }

  // ============ 用户端 / 管理端守卫 ============
  if (to.meta.requireAuth) {
    const token = getToken();
    if (token === null) {
      return next("/login");
    }

    // 2026-10-04：原先只判 token 是否存在，医生/用户/管理员可以互相进对方页面。
    // 现在按路径前缀校验角色 —— 这是体验层拦截，真正的边界在后端
    // JwtInterceptor + DoctorIsolation。
    const role = getRole("user");
    if (!canAccess(to.path, role)) {
      console.warn(
        `[Router] 越权访问被拦截: path=${to.path}, role=${role}，已跳回各自首页`
      );
      return next(homeOf(role));
    }
    return next();
  }

  return next();
});

export default router;
