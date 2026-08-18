import { createRouter, createWebHashHistory } from "vue-router";
import { getToken } from "@/utils/storage.js";

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
        name: "药品管理",
        icon: "FirstAidKit",
        component: () => import(`@/views/admin/DrugManage.vue`),
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
        name: "商城管理",
        icon: "ShoppingCart",
        component: () => import(`@/views/admin/MallManage.vue`),
        meta: { requireAuth: true },
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
      {
        path: "agentManage",
        name: "智能体",
        icon: "Cpu",
        component: () => import(`@/views/admin/AgentManagement.vue`),
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
        name: "药品查询",
        path: "drug",
        icon: "FirstAidKit",
        component: () => import(`@/views/user/Drug.vue`),
        meta: {
          requireAuth: true,
        },
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
  if (to.meta.requireAuth) {
    const token = getToken();
    if (token !== null) {
      next();
    } else {
      next("/login");
    }
  } else {
    next();
  }
});

export default router;
