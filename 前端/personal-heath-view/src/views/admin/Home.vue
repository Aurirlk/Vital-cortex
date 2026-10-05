<template>
  <div class="admin-layout">
    <!--  -->
    <aside class="admin-sidebar" :class="{ collapsed: flag }">
      <div class="sidebar-logo">
        <Logo sysName="" :flag="flag" :bag="colorLogo" />
      </div>
      <nav class="sidebar-nav">
        <AdminMenu
          :flag="flag"
          :routes="adminRoutes"
          :bag="bagMenu"
          @select="handleRouteSelect"
        />
      </nav>
    </aside>

    <!--  -->
    <div class="admin-main">
      <!--  header —  -->
      <header class="admin-header">
        <div class="admin-header-left">
          <span class="collapse-btn" @click="toggleSidebar">
            <el-icon :size="20"><Fold v-if="!flag" /><Expand v-else /></el-icon>
          </span>
          <span class="breadcrumb-text"> / {{ tag || "" }}</span>
        </div>
        <div class="admin-header-right">
          <el-dropdown class="user-dropdown" popper-class="admin-user-dropdown">
            <span class="dropdown-trigger">
              <el-avatar :size="32" :src="userInfo.url || '/default-avatar.svg'" />
              <span class="user-name">{{ userInfo.name }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  :icon="Back"
                  @click="eventListener('loginOut')"
                  >退出登录</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!--  —  -->
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>
<script>
import request from "@/utils/request.js";
import router from "@/router/index";
import { clearToken } from "@/utils/storage";
import { Fold, Expand, ArrowDown, Back } from "@element-plus/icons-vue";
import AdminMenu from "@/components/VerticalMenu.vue";
import Logo from "@/components/Logo.vue";
export default {
  name: "Admin",
  components: { Logo, AdminMenu, Fold, Expand, ArrowDown, Back },
  data() {
    return {
      adminRoutes: [],
      activeIndex: "",
      userInfo: {
        id: null,
        url: "",
        name: "",
        role: null,
        email: "",
      },
      flag: false,
      tag: "",
      bag: "rgb(246,246,246)",
      colorLogo: "#e2e8f0",
      bagMenu: "transparent",
    };
  },
  created() {
    let menus = router.options.routes.filter(
      (route) => route.path == "/admin"
    )[0];
    this.adminRoutes = menus.children;
    this.tokenCheckLoad();
    this.menuOperationHistory();
  },

  methods: {
    eventListener(event) {
      // 退出登录
      if (event === "loginOut") {
        this.loginOut();
      }
    },
    async loginOut() {
      const confirmed = await this.$swalConfirm({
        title: "确认退出",
        text: "您确定要退出登录吗？",
        icon: "warning",
      });
      if (confirmed) {
        this.$swal.fire({
          title: "已退出",
          text: "正在跳转登录页...",
          icon: "success",
          showConfirmButton: false,
          timer: 1000,
        });
        setTimeout(() => {
          clearToken();
          this.$router.push("/login");
        }, 1000);
      }
    },
    menuOperationHistory() {
      this.flag = sessionStorage.getItem("flag") === "true";
    },
    toggleSidebar() {
      this.flag = !this.flag;
      sessionStorage.setItem("flag", this.flag);
    },
    selectOperation(flag) {
      this.flag = flag;
    },
    handleRouteSelect(index) {
      let ary = this.adminRoutes.filter((entity) => entity.path == index);
      this.tag = ary[0].name;
      const fullPath = `/admin/${index}`;
      if (this.$router.currentRoute.fullPath === fullPath) {
        return;
      }
      this.$router.push(fullPath);
    },
    // Token
    async tokenCheckLoad() {
      try {
        const res = await request.get("user/auth");
        //
        if (res.data.code === 400) {
          this.$message.error(res.data.msg);
          this.$router.push("/login");
          return;
        }
        //
        const {
          id,
          userAvatar: url,
          userName: name,
          userRole: role,
          userEmail: email,
        } = res.data.data;
        this.userInfo = { id, url, name, role, email };
        //
        const rolePath = role === 1 ? "/admin" : "/user";
        const targetMenu = router.options.routes.find(
          (route) => route.path === rolePath
        );
        if (targetMenu) {
          this.routers = targetMenu.children;
        } else {
          console.warn(`${rolePath}`);
        }
      } catch (error) {
        console.error(":", error);
        this.$message.error(",");
      }
    },
  },
};
</script>
<style scoped lang="scss">
/* ======== flex  ======== */
.admin-layout {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #f5f7fa;
}

/* ========  ======== */
.admin-sidebar {
  width: 240px;
  min-width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #1e293b;
  border-right: 1px solid #334155;
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.25s ease, min-width 0.25s ease;
  z-index: 20;

  &.collapsed {
    width: 68px;
    min-width: 68px;
  }
}

.sidebar-logo {
  padding: 16px 20px;
  flex-shrink: 0;
}

.sidebar-nav {
  flex: 1;
  margin-top: 8px;
  overflow-y: auto;
}

/* ========  ======== */
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

/* ========  header ======== */
.admin-header {
  flex-shrink: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  z-index: 10;
}

.admin-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;

  &:hover {
    background: #f3f4f6;
    color: #111;
  }
}

.breadcrumb-text {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
}

.admin-header-right {
  display: flex;
  align-items: center;
}

.dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background 0.2s;

  &:hover {
    background: #f3f4f6;
  }
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

/* ========  ======== */
.admin-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px;
  box-sizing: border-box;
  background: #f5f7fa;

  & > * {
    max-width: 100%;
  }

  &::-webkit-scrollbar {
    width: 8px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: #c1c1c1;
    border-radius: 4px;
    &:hover {
      background: #a0a0a0;
    }
  }
}

/* ========  ======== */
.modelName {
  font-size: 12px;
  color: #718096;
}

.primary-btn {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;

  &:hover {
    opacity: 0.9;
  }
}

/*  el-dropdown  z-index */
:deep(.el-dropdown-menu) {
  z-index: 3000 !important;
}
</style>
