<template>
  <div class="model-banner" v-if="bannerVisible && announcement"
       :style="{ backgroundColor: bannerBgColor, color: bannerTextColor }">
    <div class="banner-content" @click="goToAiChat">
      <el-icon class="banner-icon" :size="20"><MagicStick /></el-icon>
      <div class="banner-text">
        <span class="banner-title">{{ announcement.title }}</span>
        <span class="banner-desc" v-if="announcement.content">{{ announcement.content }}</span>
      </div>
    </div>
    <el-button class="banner-close" text size="small" @click="closeBanner"
               :style="{ color: bannerTextColor }">
      <el-icon :size="16"><Close /></el-icon>
    </el-button>
  </div>
</template>

<script>
export default {
  name: "ModelBanner",
  data() {
    return {
      announcement: null,
      bannerVisible: false
    }
  },
  computed: {
    bannerBgColor() {
      return this.announcement?.bgColor || '#409EFF';
    },
    bannerTextColor() {
      const color = this.bannerBgColor;
      if (!color) return '#fff';
      const hex = color.replace('#', '');
      const r = parseInt(hex.substr(0, 2), 16);
      const g = parseInt(hex.substr(2, 2), 16);
      const b = parseInt(hex.substr(4, 2), 16);
      const brightness = (r * 299 + g * 587 + b * 114) / 1000;
      return brightness > 160 ? '#333' : '#fff';
    }
  },
  created() {
    this.loadBanner();
  },
  methods: {
    async loadBanner() {
      // 检查 localStorage 是否已关闭
      const closed = localStorage.getItem('modelBannerClosed');
      if (closed) {
        const closedDate = new Date(closed);
        const today = new Date();
        if (closedDate.toDateString() === today.toDateString()) {
          return; // 当天不再显示
        }
        localStorage.removeItem('modelBannerClosed');
      }

      try {
        const res = await this.$axios.get("/ai/announcement/active");
        if (res.data.code === 200 && res.data.data) {
          this.announcement = res.data.data;
          this.bannerVisible = true;
        }
      } catch (e) {
        console.error("加载横幅失败:", e);
      }
    },

    closeBanner() {
      this.bannerVisible = false;
      localStorage.setItem('modelBannerClosed', new Date().toISOString());
    },

    goToAiChat() {
      this.$router.push('/user/ai-analysis');
    }
  }
}
</script>

<style scoped>
.model-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  min-height: 40px;
  box-sizing: border-box;
}

html.dark .model-banner {
  opacity: 0.9;
}

.model-banner:hover {
  opacity: 0.92;
}

.banner-content {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.banner-icon {
  flex-shrink: 0;
}

.banner-text {
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
}

.banner-title {
  font-weight: 600;
  white-space: nowrap;
}

.banner-desc {
  font-size: 13px;
  opacity: 0.9;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-close {
  color: rgba(255, 255, 255, 0.8) !important;
  flex-shrink: 0;
  margin-left: 12px;
}

.banner-close:hover {
  color: #fff !important;
}
</style>
