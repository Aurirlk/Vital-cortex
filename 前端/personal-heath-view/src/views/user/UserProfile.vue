<template>
  <div class="profile-container">
    <!-- 头部 -->
    <div class="profile-header">
      <div class="profile-header__bg"></div>
      <div class="profile-header__content">
        <div class="profile-avatar">
          <el-upload
            class="avatar-uploader"
            :action="$uploadUrl"
            :headers="$uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
            :before-upload="beforeAvatarUpload"
          >
            <img
              :src="avatarUrl"
              alt="头像"
              class="profile-avatar__img"
            />
            <div class="profile-avatar__edit">
              <el-icon><Camera /></el-icon>
            </div>
          </el-upload>
        </div>
        <div class="profile-info">
          <h1 class="profile-info__name">{{ userInfo.userName || "未设置昵称" }}</h1>
          <p class="profile-info__account">账号：{{ userInfo.userAccount }}</p>
          <div class="profile-info__tags">
            <span class="profile-tag profile-tag--role">{{
              userInfo.userRole === 1 ? "管理员" : "普通用户"
            }}</span>
            <span class="profile-tag profile-tag--vip">VIP</span>
          </div>
        </div>
        <button class="profile-edit-btn" @click="editProfile">
          <el-icon style="vertical-align: -2px"><EditPen /></el-icon>
          编辑资料
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="profile-stats">
      <div class="stat-item" v-for="stat in stats" :key="stat.label" @click="stat.path && navigateTo(stat.path)">
        <div class="stat-item__number">{{ stat.value }}</div>
        <div class="stat-item__label">{{ stat.label }}</div>
      </div>
    </div>

    <!-- 常用服务 -->
    <div class="profile-menu">
      <div class="menu-section">
        <h3 class="menu-section__title">常用服务</h3>
        <div class="menu-grid">
          <div
            class="menu-item"
            v-for="item in serviceMenus"
            :key="item.label"
            @click="navigateTo(item.path)"
          >
            <div class="menu-item__icon" :style="{ background: item.bg }">
              <el-icon :size="24" :color="item.color"><component :is="item.icon" /></el-icon>
            </div>
            <span class="menu-item__label">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <!-- 我的订单 / 我的预约 -->
      <div class="menu-section">
        <h3 class="menu-section__title">交易服务</h3>
        <div class="menu-grid">
          <div
            class="menu-item"
            v-for="item in tradeMenus"
            :key="item.label"
            @click="navigateTo(item.path)"
          >
            <div class="menu-item__icon" :style="{ background: item.bg }">
              <el-icon :size="24" :color="item.color"><component :is="item.icon" /></el-icon>
            </div>
            <span class="menu-item__label">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <!-- 设置 -->
      <div class="menu-section">
        <h3 class="menu-section__title">设置</h3>
        <div class="menu-list">
          <div class="menu-list-item" @click="navigateTo('/user/settings')">
            <div class="menu-list-item__left">
              <el-icon class="menu-list-item__icon"><Setting /></el-icon>
              <span class="menu-list-item__label">系统设置</span>
            </div>
            <span class="menu-list-item__arrow">›</span>
          </div>
          <div class="menu-list-item" @click="editProfile">
            <div class="menu-list-item__left">
              <el-icon class="menu-list-item__icon"><EditPen /></el-icon>
              <span class="menu-list-item__label">编辑资料</span>
            </div>
            <span class="menu-list-item__arrow">›</span>
          </div>
          <div class="menu-list-item" @click="navigateTo('/message')">
            <div class="menu-list-item__left">
              <el-icon class="menu-list-item__icon"><Bell /></el-icon>
              <span class="menu-list-item__label">消息中心</span>
            </div>
            <span class="menu-list-item__arrow">›</span>
          </div>
          <div class="menu-list-item" @click="aboutDialog = true">
            <div class="menu-list-item__left">
              <el-icon class="menu-list-item__icon"><InfoFilled /></el-icon>
              <span class="menu-list-item__label">关于我们</span>
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

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editDialog" title="编辑资料" width="480px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="头像">
          <el-upload
            class="avatar-uploader-inline"
            :action="$uploadUrl"
            :headers="$uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
            :before-upload="beforeAvatarUpload"
          >
            <img v-if="userInfo.userAvatar" :src="avatarUrl" class="avatar-preview" />
            <el-icon v-else class="avatar-placeholder"><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="editForm.userName" placeholder="请输入昵称" maxlength="30" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.userEmail" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="changePwdDialog" title="修改密码" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPwd" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPwd" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwdForm.againPwd" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changePwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePwd">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 主题设置弹窗 -->
    <el-dialog v-model="themeDialog" title="主题设置" width="420px">
      <div class="theme-item">
        <span class="theme-item__label">深色模式</span>
        <el-switch v-model="settings.isDarkMode" @change="toggleDarkMode" />
      </div>
    </el-dialog>

    <!-- 关于我们 -->
    <el-dialog v-model="aboutDialog" title="关于我们" width="420px">
      <p class="about-text">智康云 - 智能健康管理系统</p>
      <p class="about-sub">AI 问诊 · 健康管理 · 商城服务 · 全周期健康守护</p>
      <p class="about-sub" style="margin-top: 8px">版本 v1.0.0</p>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";
import { clearToken } from "@/utils/storage.js";
import {
  Camera,
  Plus,
  EditPen,
  Lock,
  Bell,
  Brush,
  InfoFilled,
  DataAnalysis,
  FirstAidKit,
  ChatDotRound,
  Odometer,
  Star,
  Message,
  ShoppingCart,
  Calendar,
  Check,
  Setting,
} from "@element-plus/icons-vue";

export default {
  name: "UserProfile",
  components: {
    Camera,
    Plus,
    EditPen,
    Lock,
    Bell,
    Brush,
    InfoFilled,
    DataAnalysis,
    FirstAidKit,
    ChatDotRound,
    Odometer,
    Setting,
    Star,
    Message,
    ShoppingCart,
    Calendar,
    Check,
  },
  data() {
    return {
      userInfo: {
        id: null,
        userAccount: "",
        userName: "",
        userAvatar: "",
        userRole: 2,
        userEmail: "",
      },
      stats: [
        { label: "我的收藏", value: 0, path: "/user/my-save" },
        { label: "健康记录", value: 0, path: "/record" },
        { label: "AI 对话", value: 0, path: "/user/assistant" },
        { label: "用药订阅", value: 0, path: "/user/drug" },
        { label: "我的订单", value: 0, path: "/user/mall?tab=orders" },
        { label: "我的预约", value: 0, path: "/user/appointment" },
      ],
      serviceMenus: [
        {
          icon: "DataAnalysis",
          label: "健康报告",
          path: "/user/report",
          bg: "rgba(14, 165, 165, 0.08)",
          color: "#0d9488",
        },
        {
          icon: "FirstAidKit",
          label: "药品查询",
          path: "/user/drug",
          bg: "rgba(0, 80, 203, 0.08)",
          color: "#0050cb",
        },
        {
          icon: "ChatDotRound",
          label: "AI 助手",
          path: "/user/assistant",
          bg: "rgba(168, 85, 247, 0.08)",
          color: "#a855f7",
        },
        {
          icon: "Odometer",
          label: "健康模型",
          path: "/user/user-health-model",
          bg: "rgba(255, 149, 0, 0.08)",
          color: "#ff9500",
        },
        {
          icon: "Star",
          label: "我的收藏",
          path: "/user/my-save",
          bg: "rgba(255, 107, 129, 0.08)",
          color: "#ff6b81",
        },
        {
          icon: "Message",
          label: "消息中心",
          path: "/message",
          bg: "rgba(51, 112, 255, 0.08)",
          color: "#3370ff",
        },
      ],
      tradeMenus: [
        {
          icon: "ShoppingCart",
          label: "健康商城",
          path: "/user/mall",
          bg: "rgba(0, 80, 203, 0.08)",
          color: "#0050cb",
        },
        {
          icon: "Check",
          label: "我的订单",
          path: "/user/mall?tab=orders",
          bg: "rgba(16, 185, 129, 0.08)",
          color: "#10b981",
        },
        {
          icon: "Calendar",
          label: "预约挂号",
          path: "/user/appointment",
          bg: "rgba(255, 149, 0, 0.08)",
          color: "#ff9500",
        },
        {
          icon: "FirstAidKit",
          label: "预约记录",
          path: "/user/appointment?tab=records",
          bg: "rgba(249, 115, 22, 0.08)",
          color: "#f97316",
        },
      ],
      editDialog: false,
      changePwdDialog: false,
      themeDialog: false,
      aboutDialog: false,
      saving: false,
      editForm: { userName: "", userEmail: "" },
      pwdForm: { oldPwd: "", newPwd: "", againPwd: "" },
      settings: { isDarkMode: false },
    };
  },
  computed: {
    avatarUrl() {
      if (this.userInfo.userAvatar) return this.userInfo.userAvatar;
      return "/default-avatar.svg";
    },
  },
  created() {
    this.loadUserInfo();
    this.loadStats();
    this.loadSettings();
  },
  methods: {
    async loadUserInfo() {
      try {
        const { data } = await request.get("user/info");
        if (data.code === 200 && data.data) {
          this.userInfo = data.data;
          this.editForm.userName = data.data.userName || "";
          this.editForm.userEmail = data.data.userEmail || "";
        }
      } catch (error) {
        console.error("加载用户信息失败:", error);
      }
    },
    async loadStats() {
      try {
        const { data } = await request.get("user/stats");
        if (data.code === 200 && data.data) {
          this.stats[0].value = data.data.favoriteCount || 0;
          this.stats[1].value = data.data.healthRecordCount || 0;
          this.stats[2].value = data.data.aiChatCount || 0;
          this.stats[3].value = data.data.drugSubscribeCount || 0;
          this.stats[4].value = data.data.orderCount || 0;
          this.stats[5].value = data.data.appointmentCount || 0;
        }
      } catch (error) {
        console.error("加载统计失败:", error);
      }
    },
    beforeAvatarUpload(file) {
      const isImage = file.type.startsWith("image/");
      if (!isImage) {
        this.$message.error("只能上传图片文件");
        return false;
      }
      if (file.size / 1024 / 1024 > 5) {
        this.$message.error("图片大小不能超过 5MB");
        return false;
      }
      return true;
    },
    handleAvatarSuccess(res, file) {
      if (res.code !== 200) {
        this.$message.error(`头像上传失败：${res.msg || ""}`);
        return;
      }
      this.userInfo.userAvatar = res.data;
      this.$message.success("头像已更新");
    },
    editProfile() {
      this.editForm.userName = this.userInfo.userName || "";
      this.editForm.userEmail = this.userInfo.userEmail || "";
      this.editDialog = true;
    },
    async saveProfile() {
      if (!this.editForm.userName || !this.editForm.userName.trim()) {
        this.$message.warning("昵称不能为空");
        return;
      }
      this.saving = true;
      try {
        const { data } = await request.put("user/update", {
          userAvatar: this.userInfo.userAvatar,
          userName: this.editForm.userName,
          userEmail: this.editForm.userEmail,
        });
        if (data.code === 200) {
          this.$message.success("保存成功");
          this.editDialog = false;
          this.loadUserInfo();
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
    async savePwd() {
      const { oldPwd, newPwd, againPwd } = this.pwdForm;
      if (!oldPwd || !newPwd || !againPwd) {
        this.$message.warning("请填写完整密码信息");
        return;
      }
      if (newPwd !== againPwd) {
        this.$message.warning("两次输入的新密码不一致");
        return;
      }
      this.saving = true;
      try {
        const { data } = await request.put("user/updatePwd", { oldPwd, newPwd });
        if (data.code === 200) {
          this.$message.success("修改成功，请重新登录");
          this.changePwdDialog = false;
          setTimeout(() => {
            clearToken();
            this.$router.push("/login");
          }, 1200);
        } else {
          this.$message.error(data.msg || "修改失败");
        }
      } catch (e) {
        this.$message.error(e.response?.data?.msg || "修改密码失败");
      } finally {
        this.saving = false;
      }
    },
    loadSettings() {
      const s = localStorage.getItem("userSettings");
      if (s) {
        try {
          this.settings = { isDarkMode: JSON.parse(s).isDarkMode || false };
        } catch (e) {
          this.settings = { isDarkMode: false };
        }
      }
      this.applyDarkMode();
    },
    toggleDarkMode() {
      localStorage.setItem("userSettings", JSON.stringify(this.settings));
      this.applyDarkMode();
    },
    applyDarkMode() {
      if (this.settings.isDarkMode) {
        document.documentElement.classList.add("dark");
      } else {
        document.documentElement.classList.remove("dark");
      }
    },
    navigateTo(path) {
      this.$router.push(path);
    },
    logout() {
      this.$swal
        .fire({
          title: "确认退出",
          text: "您确定要退出登录吗？",
          icon: "question",
          showCancelButton: true,
          confirmButtonText: "退出",
          cancelButtonText: "取消",
          customClass: { confirmButton: "swal2-btn-primary" },
        })
        .then((result) => {
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
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 20px;
  min-height: 100vh;
  background: var(--xh-bg, #f5f7fa);
}

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
      content: "";
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
  cursor: pointer;

  &__img {
    width: 100px;
    height: 100px;
    border-radius: 50%;
    border: 4px solid #fff;
    object-fit: cover;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    display: block;
    background: #eef1f6;
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
    color: #fff;
    font-size: 16px;

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

.profile-stats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-item {
  background: #fff;
  border-radius: 12px;
  padding: 18px 10px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  }

  &__number {
    font-size: 26px;
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
    color: #0050cb;
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

.avatar-uploader-inline {
  .avatar-preview {
    width: 88px;
    height: 88px;
    border-radius: 50%;
    object-fit: cover;
    border: 2px solid #e5e7eb;
  }

  .avatar-placeholder {
    width: 88px;
    height: 88px;
    border-radius: 50%;
    border: 1px dashed #d9d9d9;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #999;
  }
}

.theme-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;

  &__label {
    font-size: 14px;
    color: #333;
  }
}

.about-text {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.about-sub {
  font-size: 13px;
  color: #888;
  margin: 0;
}

@media (max-width: 768px) {
  .profile-stats {
    grid-template-columns: repeat(3, 1fr);
  }

  .menu-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .profile-header__content {
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 0 16px 20px;
  }

  .profile-info__tags {
    justify-content: center;
  }
}
</style>
