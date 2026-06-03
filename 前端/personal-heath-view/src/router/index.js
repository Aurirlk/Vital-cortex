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
    meta: {
      requireAuth: true,
    },
    children: [
      {
        path: "adminLayout",
        name: "仪表盘",
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
        name: "资讯分类",
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
        name: "模型管理",
        icon: "Files",
        component: () => import(`@/views/admin/HealthModelConfigManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "userHealthManage",
        name: "健康记录",
        icon: "ScaleToOriginal",
        component: () => import(`@/views/admin/UserHealthManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "messageManage",
        name: "消息管理",
        icon: "Message",
        component: () => import(`@/views/admin/MessageManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "evaluationsManage",
        name: "评论管理",
        icon: "ChatDotRound",
        component: () => import(`@/views/admin/EvaluationsManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "aiAnalysis",
        name: "AI分析",
        icon: "MagicStick",
        component: () => import(`@/views/admin/AiAnalysis.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "aiDoctorManage",
        name: "AI医生管理",
        icon: "Setting",
        component: () => import(`@/views/admin/AiDoctorManage.vue`),
        meta: { requireAuth: true },
      },
      {
        path: "drugManage",
        name: "药品管理",
        icon: "FirstAidKit",
        component: () => import(`@/views/admin/DrugManage.vue`),
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
        name: "健康资讯",
        path: "news-record",
        component: () => import(`@/views/user/Home.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "我的收藏",
        path: "my-save",
        component: () => import(`@/views/user/NewsSave.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "健康指标",
        path: "user-health-model",
        component: () => import(`@/views/user/UserHealthModel.vue`),
        meta: {
          requireAuth: true,
        },
      },
      {
        name: "健康资讯详情",
        path: "news-detail",
        component: () => import(`@/views/user/NewsDetail.vue`),
        meta: {
          requireAuth: true,
        },
        isHidden: true,
      },
      {
        name: "搜索页",
        path: "search-detail",
        component: () => import(`@/views/user/Search.vue`),
        meta: {
          requireAuth: true,
        },
        isHidden: true,
      },
      {
        name: "AI健康分析",
        path: "ai-analysis",
        component: () => import(`@/views/user/AiAnalysis.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "网站小助手",
        path: "assistant",
        component: () => import(`@/views/user/Assistant.vue`),
        meta: { requireAuth: true },
      },
      {
        name: "药品订阅",
        path: "drug",
        component: () => import(`@/views/user/Drug.vue`),
        meta: {
          requireAuth: true,
        },
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
