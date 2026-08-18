<template>
  <div class="profile-container">
    <!--  -->
    <div class="profile-header">
      <div class="profile-header__bg"></div>
      <div class="profile-header__content">
        <div class="profile-avatar">
          <img :src="userInfo.avatar || '/default-avatar.png'" alt="" class="profile-avatar__img" />
          <div class="profile-avatar__edit" @click="editAvatar">
            <span>编辑</span>
          </div>
        </div>
        <div class="profile-info">
          <h1 class="profile-info__name">{{ userInfo.name || '' }}</h1>
          <p class="profile-info__account">{{ userInfo.account }}</p>
          <div class="profile-info__tags">
            <span class="profile-tag profile-tag--role">{{ userInfo.role === 1 ? '管理员' : '普通用户' }}</span>
            <span class="profile-tag profile-tag--vip">VIP</span>
          </div>
        </div>
        <button class="profile-edit-btn" @click="editProfile">
          编辑资料
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="profile-stats">
      <div class="stat-item" v-for="stat in stats" :key="stat.label">
        <div class="stat-item__number">{{ stat.value }}</div>
        <div class="stat-item__label">{{ stat.label }}</div>
      </div>
    </div>

    <!-- 功能菜单 -->
    <div class="profile-menu">
      <div class="menu-section">
        <h3 class="menu-section__title">常用服务</h3>
        <div class="menu-grid">
          <div class="menu-item" v-for="item in serviceMenus" :key="item.label" @click="navigateTo(item.path)">
            <div class="menu-item__icon" :style="{ background: item.bg }">{{ item.icon }}</div>
            <span class="menu-item__label">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <div class="menu-section">
        <h3 class="menu-section__title">设置</h3>
        <div class="menu-list">
          <div class="menu-list-item" v-for="item in settingMenus" :key="item.label" @click="navigateTo(item.path)">
            <div class="menu-list-item__left">
              <span class="menu-list-item__icon">{{ item.icon }}</span>
              <span class="menu-list-item__label">{{ item.label }}</span>
            </div>
            <span class="menu-list-item__arrow">›</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 退出登录 -->
    <div class="profile-footer">
      <button class="logout-btn" @click="logout">退出登录</button>
    </div>
  </div>
</template>

<script>
import request from "@/utils/request.js";
import { getToken, clearToken } from "@/utils/storage.js";

export default {
  name: "UserProfile",
  data() {
    return {
      userInfo: {
        id: null,
        account: "",
        name: "",
        avatar: "",
        role: 2,
        email: "",
      },
      stats: [
        { label: "我的收藏", value: 0 },
        { label: "健康记录", value: 0 },
        { label: "AI 对话", value: 0 },
        { label: "用药提醒", value: 0 },
      ],
      serviceMenus: [
        { icon: "📊", label: "健康报告", path: "/user/health-report", bg: "rgba(14, 165, 165, 0.08)" },
        { icon: "💊", label: "药品查询", path: "/user/drug", bg: "rgba(0, 80, 203, 0.08)" },
        { icon: "🤖", label: "AI 助手", path: "/user/assistant", bg: "rgba(168, 85, 247, 0.08)" },
        { icon: "🫀", label: "健康模型", path: "/user/user-health-model", bg: "rgba(255, 149, 0, 0.08)" },
        { icon: "⭐", label: "我的收藏", path: "/user/my-save", bg: "rgba(255, 107, 129, 0.08)" },
        { icon: "🔔", label: "消息中心", path: "/user/message-center", bg: "rgba(51, 112, 255, 0.08)" },
      ],
      settingMenus: [
        { icon: "✏️", label: "编辑资料", path: "/user/profile-edit" },
        { icon: "🔐", label: "修改密码", path: "/user/change-password" },
        { icon: "🔔", label: "通知设置", path: "/user/notification-settings" },
        { icon: "🎨", label: "主题设置", path: "/user/theme-settings" },
        { icon: "❓", label: "帮助中心", path: "/user/help" },
        { icon: "ℹ️", label: "关于我们", path: "/user/about" },
      ],
    };
  },
  created() {
    this.loadUserInfo();
    this.loadStats();
  },
  methods: {
    async loadUserInfo() {
      try {
        const { data } = await request.get("user/info");
        if (data.code === 200) {
          this.userInfo = data.data;
        }
      } catch (error) {
        console.error(":", error);
      }
    },
    async loadStats() {
      try {
        const { data } = await request.get("user/stats");
        if (data.code === 200) {
          this.stats[0].value = data.data.favoriteCount || 0;
          this.stats[1].value = data.data.healthRecordCount || 0;
          this.stats[2].value = data.data.aiChatCount || 0;
          this.stats[3].value = data.data.drugSubscribeCount || 0;
        }
      } catch (error) {
        console.error(":", error);
      }
    },
    editAvatar() {
      // TODO: 实现头像上传
      this.$message.info("头像编辑功能开发中");
    },
    editProfile() {
      this.$router.push("/user/profile-edit");
    },
    navigateTo(path) {
      this.$router.push(path);
    },
    logout() {
      this.$swal.fire({
        title: "确认退出",
        text: "您确定要退出登录吗？",
        icon: "question",
        showCancelButton: true,
        confirmButtonText: "退出",
        cancelButtonText: "取消",
        confirmButtonColor: "#0050cb",
      }).then((result) => {
        if (result.isConfirmed) {
          clearToken();
          this.$router.push("/login");
        }
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.profile-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 20px;
  min-height: 100vh;
  background: var(--xh-bg);
}

/*  */
.profile-header {
  position: relative;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);

  &__bg {
    height: 120px;
    background: linear-gradient(135deg, #0050cb, #0066ff, #632ce5);
    position: relative;

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 40px;
      background: linear-gradient(transparent, rgba(0, 0, 0, 0.1));
    }
  }

  &__content {
    display: flex;
    align-items: flex-end;
    padding: 0 24px 24px;
    margin-top: -50px;
    position: relative;
    z-index: 2;
  }
}

.profile-avatar {
  position: relative;
  margin-right: 20px;

  &__img {
    width: 100px;
    height: 100px;
    border-radius: 50%;
    border: 4px solid #fff;
    object-fit: cover;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }

  &__edit {
    position: absolute;
    bottom: 4px;
    right: 4px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: #0050cb;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0, 80, 203, 0.4);
    transition: transform 0.2s;

    span {
      font-size: 14px;
    }

    &:hover {
      transform: scale(1.1);
    }
  }
}

.profile-info {
  flex: 1;

  &__name {
    font-size: 24px;
    font-weight: 700;
    color: #1a1a1a;
    margin: 0 0 4px 0;
  }

  &__account {
    font-size: 14px;
    color: #999;
    margin: 0 0 12px 0;
  }

  &__tags {
    display: flex;
    gap: 8px;
  }
}

.profile-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;

  &--role {
    background: rgba(14, 165, 165, 0.1);
    color: #0050cb;
  }

  &--vip {
    background: linear-gradient(135deg, #ff9500, #ffb340);
    color: #fff;
  }
}

.profile-edit-btn {
  padding: 8px 24px;
  border: 2px solid #0050cb;
  border-radius: 10px;
  background: transparent;
  color: #0050cb;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s ease;
  align-self: flex-end;

  &:hover {
    background: rgba(0, 80, 203, 0.06);
  }
}

/*  */
.profile-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-item {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  }

  &__number {
    font-size: 28px;
    font-weight: 700;
    background: linear-gradient(135deg, #0050cb, #0066ff);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  &__label {
    font-size: 13px;
    color: #999;
    margin-top: 4px;
  }
}

/*  */
.profile-menu {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.menu-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: #1a1a1a;
    margin: 0 0 16px 0;
  }
}

.menu-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.menu-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s ease;

  &:hover {
    background: #f8f8f8;
    transform: translateY(-2px);
  }

  &__icon {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
  }

  &__label {
    font-size: 13px;
    color: #333;
    font-weight: 500;
  }
}

.menu-list {
  display: flex;
  flex-direction: column;
}

.menu-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.2s;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #fafafa;
    margin: 0 -8px;
    padding: 14px 8px;
    border-radius: 8px;
  }

  &__left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__icon {
    font-size: 20px;
  }

  &__label {
    font-size: 14px;
    color: #333;
  }

  &__arrow {
    font-size: 18px;
    color: #ccc;
    transition: transform 0.2s;
  }

  &:hover &__arrow {
    transform: translateX(4px);
    color: #0050cb;
  }
}

/*  */
.profile-footer {
  margin-top: 24px;
  padding-bottom: 40px;
}

.logout-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 12px;
  background: #fff;
  color: #0050cb;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  &:hover {
    background: rgba(0, 80, 203, 0.04);
    box-shadow: 0 4px 16px rgba(0, 80, 203, 0.1);
  }
}

/*  */
@media (max-width: 640px) {
  .profile-header__content {
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 0 16px 20px;
  }

  .profile-info__tags {
    justify-content: center;
  }

  .profile-stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .menu-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
