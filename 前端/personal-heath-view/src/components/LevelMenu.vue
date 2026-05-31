<template>
  <header class="header">
    <!-- 左侧：Logo + 导航菜单 -->
    <div class="header-left">
      <div class="logo-area">
        <Logo sysName="健康资讯" />
      </div>
      <nav class="nav-menu">
        <template v-for="(item, index) in menus" :key="index">
          <div
            v-if="!item.isHidden"
            class="nav-item"
            :class="{ 'nav-item--active': selectedIndex === index }"
            @click="menuClick(item.path, index)"
          >
            <span class="nav-item-text">
              <el-icon><component :is="item.icon" /></el-icon>
              {{ item.name }}
            </span>
            <div v-if="selectedIndex === index" class="nav-indicator"></div>
          </div>
        </template>
      </nav>
    </div>

    <!-- 右侧：搜索 + 指标记录 + 铃铛 + 个人主页 -->
    <div class="header-right">
      <!-- 搜索框 -->
      <div class="search-box">
        <input
          class="search-input"
          placeholder="搜索..."
          @keyup.enter="search"
          v-model="filterText"
        />
        <span class="search-btn" @click="search">搜索</span>
      </div>

      <!-- 指标记录按钮 -->
      <button class="record-btn" @click="healthDataRecord">
        <svg class="record-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
        </svg>
        <span>指标记录</span>
      </button>

      <!-- 铃铛 -->
      <div class="bell-area" @click="messageCenter">
        <el-badge :hidden="noReadMsg === 0" :value="noReadMsg">
          <el-icon class="bell-icon"><Bell /></el-icon>
        </el-badge>
      </div>

      <!-- 个人主页 -->
      <div class="user-area">
        <el-dropdown trigger="click">
          <div class="user-trigger">
            <el-avatar :size="32" :src="userInfo.url"></el-avatar>
            <span class="user-name">{{ userInfo.name }}</span>
            <el-icon class="arrow-icon"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="User" @click="userCenterPanel">个人中心</el-dropdown-item>
              <el-dropdown-item :icon="WarningFilled" @click="resetPwd">修改密码</el-dropdown-item>
              <el-dropdown-item :icon="Setting" @click="openSettings">设置</el-dropdown-item>
              <el-dropdown-item :icon="Back" @click="loginOut">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </header>
</template>

<script>
import { clearToken } from "@/utils/storage.js";
import Logo from "@/components/Logo.vue";
import { Upload, Bell, ArrowDown, User, WarningFilled, Back, Setting } from "@element-plus/icons-vue";

export default {
  name: "UserMenu",
  components: { Logo, Upload, Bell, ArrowDown, User, WarningFilled, Back, Setting },
  data() {
    return {
      selectedIndex: 0,
      messagePath: "/message",
      defaultPath: "/user/news-record",
      filterText: "",
      noReadMsg: 0,
      User, WarningFilled, Back, Setting,
    };
  },
  props: {
    menus: { type: Array, required: true },
    userInfo: { type: Object, required: true },
  },
  mounted() {
    this.pathToDo(this.defaultPath);
    this.loadMsgCount();
  },
  methods: {
    search() {
      if (this.$route.path === "/search-detail") {
        sessionStorage.setItem("keyWord", this.filterText);
        return;
      }
      sessionStorage.setItem("keyWord", this.filterText);
      this.$emit("eventListener", "search-detail");
    },
    userCenterPanel() { this.$emit("eventListener", "center"); },
    resetPwd() { this.$emit("eventListener", "resetPwd"); },
    openSettings() { this.$emit("eventListener", "settings"); },
    loginOut() { this.$emit("eventListener", "loginOut"); },
    healthDataRecord() { this.$emit("eventListener", "healthDataRecord"); },
    async loadMsgCount() {
      try {
        const userInfo = sessionStorage.getItem("userInfo");
        if (!userInfo) return;
        const userInfoEntity = JSON.parse(userInfo);
        const messageQueryDto = { userId: userInfoEntity.id, isRead: false };
        const response = await this.$axios.post(`/message/query`, messageQueryDto);
        const { data } = response;
        if (data.code === 200) {
          this.noReadMsg = data.data.length;
        }
      } catch (e) { /* ignore */ }
    },
    pathToDo(path) {
      if (this.$route.path !== path) {
        this.$router.push(path);
      }
    },
    menuClick(path, index) {
      this.selectedIndex = index;
      this.pathToDo(`/user/${path}`);
    },
    messageCenter() {
      this.selectedIndex = null;
      this.pathToDo(this.messagePath);
    },
  },
};
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-menu {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-item {
  position: relative;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.25s ease;
}

.nav-item:hover {
  background: rgba(102, 126, 234, 0.06);
}

.nav-item-text {
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-weight: 500;
  font-size: 15px;
  color: #6B7280;
  transition: color 0.25s ease;
}

.nav-item--active .nav-item-text {
  font-weight: 700;
  color: #111827;
}

.nav-item:hover .nav-item-text {
  color: #111827;
}

.nav-indicator {
  position: absolute;
  left: 50%;
  bottom: -2px;
  transform: translateX(-50%);
  height: 3px;
  width: 24px;
  border-radius: 999px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  box-shadow: 0 0 8px rgba(102, 126, 234, 0.4);
  animation: indicator-in 0.25s ease;
}

@keyframes indicator-in {
  from { width: 0; opacity: 0; }
  to { width: 24px; opacity: 1; }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  display: flex;
  align-items: center;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  transition: border-color 0.2s;
}

.search-box:focus-within {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

.search-input {
  outline: none;
  border: none;
  padding: 6px 12px;
  font-size: 14px;
  width: 160px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.search-input::placeholder {
  color: #9CA3AF;
}

.search-btn {
  padding: 6px 14px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.search-btn:hover {
  opacity: 0.9;
}

.record-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  background: #10B981;
  color: #fff;
  border: none;
  padding: 7px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.record-btn:hover {
  background: #059669;
}

.record-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.bell-area {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.bell-area:hover {
  background: #f3f4f6;
}

.bell-icon {
  font-size: 20px;
  color: #10B981;
}

.user-area {
  display: flex;
  align-items: center;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-trigger:hover {
  background: #f3f4f6;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.arrow-icon {
  font-size: 12px;
  color: #9CA3AF;
}
</style>
