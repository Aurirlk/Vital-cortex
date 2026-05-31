<template>
  <div class="settings-container" :class="{ 'dark-mode': isDarkMode }">
    <div class="settings-header">
      <h2>设置</h2>
    </div>
    
    <div class="settings-section">
      <h3>外观设置</h3>
      <div class="setting-item">
        <div class="setting-info">
          <el-icon><Moon /></el-icon>
          <span>深色模式</span>
        </div>
        <el-switch
          v-model="isDarkMode"
          @change="toggleDarkMode"
          active-text="深色"
          inactive-text="浅色"
        />
      </div>
    </div>

    <div class="settings-section">
      <h3>显示设置</h3>
      <div class="setting-item">
        <div class="setting-info">
          <el-icon><View /></el-icon>
          <span>显示轮播图白点</span>
        </div>
        <el-switch
          v-model="showBannerDots"
          @change="saveSettings"
          active-text="显示"
          inactive-text="隐藏"
        />
      </div>
      <div class="setting-item">
        <div class="setting-info">
          <el-icon><Timer /></el-icon>
          <span>轮播图自动播放</span>
        </div>
        <el-switch
          v-model="autoPlayBanner"
          @change="saveSettings"
          active-text="开启"
          inactive-text="关闭"
        />
      </div>
    </div>

    <div class="settings-section">
      <h3>通知设置</h3>
      <div class="setting-item">
        <div class="setting-info">
          <el-icon><Bell /></el-icon>
          <span>消息通知</span>
        </div>
        <el-switch
          v-model="enableNotification"
          @change="saveSettings"
          active-text="开启"
          inactive-text="关闭"
        />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "UserSettings",
  data() {
    return {
      isDarkMode: false,
      showBannerDots: true,
      autoPlayBanner: true,
      enableNotification: true,
    };
  },
  created() {
    this.loadSettings();
  },
  methods: {
    loadSettings() {
      const settings = localStorage.getItem("userSettings");
      if (settings) {
        const parsed = JSON.parse(settings);
        this.isDarkMode = parsed.isDarkMode || false;
        this.showBannerDots = parsed.showBannerDots !== false;
        this.autoPlayBanner = parsed.autoPlayBanner !== false;
        this.enableNotification = parsed.enableNotification !== false;
      }
      this.applyDarkMode();
    },
    saveSettings() {
      const settings = {
        isDarkMode: this.isDarkMode,
        showBannerDots: this.showBannerDots,
        autoPlayBanner: this.autoPlayBanner,
        enableNotification: this.enableNotification,
      };
      localStorage.setItem("userSettings", JSON.stringify(settings));
      this.$emit("settings-changed", settings);
    },
    toggleDarkMode() {
      this.saveSettings();
      this.applyDarkMode();
    },
    applyDarkMode() {
      if (this.isDarkMode) {
        document.documentElement.classList.add("dark");
        document.body.style.backgroundColor = "#1a1a2e";
        document.body.style.color = "#eee";
      } else {
        document.documentElement.classList.remove("dark");
        document.body.style.backgroundColor = "#f5f7fa";
        document.body.style.color = "#333";
      }
    },
  },
};
</script>

<style scoped lang="scss">
.settings-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
  transition: all 0.3s ease;

  &.dark-mode {
    background-color: #1a1a2e;
    color: #eee;
  }
}

.settings-header {
  margin-bottom: 30px;
  
  h2 {
    font-size: 24px;
    font-weight: 600;
    color: inherit;
  }
}

.settings-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;

  .dark-mode & {
    background: #16213e;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.2);
  }

  h3 {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 15px;
    color: inherit;
  }
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;

  .dark-mode & {
    border-bottom-color: #2a2a4a;
  }

  &:last-child {
    border-bottom: none;
  }
}

.setting-info {
  display: flex;
  align-items: center;
  gap: 12px;

  .el-icon {
    font-size: 18px;
    color: #667eea;
  }

  span {
    font-size: 14px;
  }
}
</style>
