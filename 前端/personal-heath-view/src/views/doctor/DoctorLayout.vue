<template>
  <div class="doctor-layout">
    <!-- 侧边栏 -->
    <aside class="dl-aside" :class="{ collapsed }">
      <div class="dl-logo">
        <div class="dl-logo__mark">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M12 4v16M4 12h16"
              stroke="#fff"
              stroke-width="2.6"
              stroke-linecap="round"
            />
          </svg>
        </div>
        <span v-show="!collapsed" class="dl-logo__text">医生工作站</span>
      </div>

      <nav class="dl-menu">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="dl-menu__item"
          :class="{ active: isActive(item.path) }"
          :title="collapsed ? item.name : ''"
        >
          <el-icon class="dl-menu__icon"><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed" class="dl-menu__label">{{ item.name }}</span>
        </router-link>
      </nav>

      <div class="dl-collapse" @click="collapsed = !collapsed">
        <el-icon>
          <component :is="collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="dl-main">
      <header class="dl-header">
        <div class="dl-header__left">
          <h2 class="dl-header__title">{{ currentTitle }}</h2>
          <span v-if="doctorInfo.title" class="dl-header__tag">
            {{ doctorInfo.title }}
          </span>
        </div>

        <div class="dl-header__right">
          <span class="dl-header__date">{{ today }}</span>
          <el-dropdown @command="onCommand">
            <div class="dl-user">
              <div class="dl-avatar">{{ avatarText }}</div>
              <span class="dl-user__name">{{ doctorInfo.name || "医生" }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" :icon="SwitchButton">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="dl-content">
        <router-view />
      </section>
    </div>
  </div>
</template>

<script>
import { ArrowDown, SwitchButton } from "@element-plus/icons-vue";
import {
  getDoctorInfo,
  clearDoctorToken,
  isDoctorLoggedIn,
} from "@/utils/doctorAuth.js";

export default {
  name: "DoctorLayout",
  components: { ArrowDown, SwitchButton },
  data() {
    return {
      collapsed: false,
      doctorInfo: getDoctorInfo() || {},
      menus: [
        { name: "工作台", path: "/doctor/home", icon: "HomeFilled" },
        { name: "我的接诊", path: "/doctor/appointments", icon: "Tickets" },
        { name: "我的排班", path: "/doctor/schedules", icon: "Calendar" },
      ],
    };
  },
  computed: {
    today() {
      const d = new Date();
      const week = ["日", "一", "二", "三", "四", "五", "六"][d.getDay()];
      return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week}`;
    },
    avatarText() {
      const n = this.doctorInfo.name || "医";
      return n.length > 1 ? n.slice(-2) : n;
    },
    currentTitle() {
      const hit = this.menus.find((m) => this.isActive(m.path));
      return hit ? hit.name : "医生工作站";
    },
  },
  created() {
    // 兜底守卫：非医生身份误入此路由时直接踢回医生登录页。
    // 真正的安全边界在后端 JwtInterceptor + DoctorIsolation。
    if (!isDoctorLoggedIn()) {
      clearDoctorToken();
      this.$router.replace("/doctor/login");
    } else {
      // 首次进入时若还没有医生档案信息，补拉一次概览
      if (!this.doctorInfo.name) {
        this.fetchProfile();
      }
    }
  },
  methods: {
    isActive(path) {
      return this.$route.path.startsWith(path);
    },
    async fetchProfile() {
      try {
        const { getDoctorOverview } = await import("@/api/doctor.js");
        const res = await getDoctorOverview();
        if (res && res.code === 200 && res.data) {
          this.doctorInfo = { ...this.doctorInfo, ...res.data };
          const { setDoctorInfo } = await import("@/utils/doctorAuth.js");
          setDoctorInfo(this.doctorInfo);
        }
      } catch (e) {
        // 概览拉取失败不阻塞页面，姓名回退为「医生」
      }
    },
    onCommand(cmd) {
      if (cmd === "logout") {
        clearDoctorToken();
        this.$message.success("已退出登录");
        this.$router.replace("/doctor/login");
      }
    },
  },
};
</script>

<style scoped>
.doctor-layout {
  display: flex;
  min-height: 100vh;
  background: #f4f7fb;
}

/* ---------- 侧边栏 ---------- */
.dl-aside {
  width: 220px;
  background: #0f2557;
  display: flex;
  flex-direction: column;
  transition: width 0.22s ease;
  flex-shrink: 0;
}
.dl-aside.collapsed {
  width: 64px;
}

.dl-logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
}
.dl-logo__mark {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 9px;
  background: linear-gradient(135deg, #2f7bf6, #1a4fb4);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dl-logo__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.dl-menu {
  flex: 1;
  padding: 12px 10px;
  overflow-y: auto;
}
.dl-menu__item {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 44px;
  padding: 0 14px;
  margin-bottom: 4px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.72);
  text-decoration: none;
  font-size: 14px;
  transition: all 0.18s;
  white-space: nowrap;
  overflow: hidden;
}
.dl-menu__item:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #fff;
}
.dl-menu__item.active {
  background: linear-gradient(90deg, #2f7bf6, #1f5fd0);
  color: #fff;
  font-weight: 600;
}
.dl-menu__icon {
  font-size: 17px;
  flex-shrink: 0;
}

.dl-collapse {
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.dl-collapse:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
}

/* ---------- 主区域 ---------- */
.dl-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.dl-header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e6ecf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}
.dl-header__left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dl-header__title {
  font-size: 16.5px;
  font-weight: 600;
  margin: 0;
  color: #1b2433;
}
.dl-header__tag {
  font-size: 12px;
  color: #1b6ef3;
  background: #eaf2ff;
  padding: 2px 9px;
  border-radius: 20px;
}
.dl-header__right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.dl-header__date {
  font-size: 13px;
  color: #8b95a5;
}
.dl-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.dl-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f7bf6, #1a4fb4);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dl-user__name {
  font-size: 14px;
  color: #333d4d;
}

.dl-content {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
  min-height: 0;
}

@media (max-width: 768px) {
  .dl-aside {
    width: 64px;
  }
  .dl-header__date {
    display: none;
  }
}
</style>
