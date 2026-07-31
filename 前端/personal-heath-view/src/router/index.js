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
        name: "",
        icon: "PieChart",
        component: () => import(`@/views/admin/Main.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "userManage",
        name: "",
        icon: "User",
        component: () => import(`@/views/admin/UserManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "tagsManage",
        name: "",
        icon: "House",
        component: () => import(`@/views/admin/TagsManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "newsManage",
        name: "",
        icon: "Document",
        component: () => import(`@/views/admin/NewsManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "healthModelConfigManage",
        name: "",
        icon: "Files",
        component: () => import(`@/views/admin/HealthModelConfigManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "userHealthManage",
        name: "",
        icon: "ScaleToOriginal",
        component: () => import(`@/views/admin/UserHealthManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "messageManage",
        name: "",
        icon: "Message",
        component: () => import(`@/views/admin/MessageManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "evaluationsManage",
        name: "",
        icon: "ChatDotRound",
        component: () => import(`@/views/admin/EvaluationsManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "drugManage",
        name: "",
        icon: "FirstAidKit",
        component: () => import(`@/views/admin/DrugManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "systemConfig",
        name: "",
        icon: "Setting",
        component: () => import(`@/views/admin/SystemConfigManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "appointmentManage",
        name: "",
        icon: "Calendar",
        component: () => import(`@/views/admin/AppointmentManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "quizManage",
        name: "",
        icon: "EditPen",
        component: () => import(`@/views/admin/QuizManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "mallManage",
        name: "",
        icon: "ShoppingCart",
        component: () => import(`@/views/admin/MallManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "followupManage",
        name: "",
        icon: "Check",
        component: () => import(`@/views/admin/FollowupManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "auditManage",
        name: "",
        icon: "Warning",
        component: () => import(`@/views/admin/AuditManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "agentManage",
        name: "",
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
        name: "",
        path: "news-record",
        component: () => import(`@/views/user/Home.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "",
        path: "my-save",
        component: () => import(`@/views/user/NewsSave.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "",
        path: "user-health-model",
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
        name: "AI",
        path: "ai-analysis",
        component: () => import(`@/views/user/AiAnalysis.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "",
        path: "assistant",
        component: () => import(`@/views/user/Assistant.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "",
        path: "drug",
        component: () => import(`@/views/user/Drug.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "",
        path: "profile",
        component: () => import(`@/views/user/UserProfile.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "",
        path: "appointment",
        component: () => import(`@/views/user/Appointment.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "",
        path: "quiz",
        component: () => import(`@/views/user/Quiz.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "",
        path: "mall",
        component: () => import(`@/views/user/Mall.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "",
        path: "followup",
        component: () => import(`@/views/user/Followup.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "",
        path: "report",
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
