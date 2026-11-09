<template>
  <el-drawer v-model="visible" :title="title" size="540px" direction="rtl" :append-to-body="true">
    <el-tabs v-model="activeTab" class="ais-tabs">
      <el-tab-pane label="AI 记忆" name="memory">
        <memory-panel ref="memory" :endpoint="endpoint" :http="http" />
      </el-tab-pane>
      <el-tab-pane label="显示与通知" name="display">
        <div class="ais-display">
          <div class="ais-item">
            <div class="ais-item__text">
              <strong>深色模式</strong>
              <span>只对用户端生效，管理端与医生端保持浅色</span>
            </div>
            <el-switch v-model="settings.isDarkMode" active-text="开" inactive-text="关" @change="persist" />
          </div>
          <div class="ais-item">
            <div class="ais-item__text">
              <strong>首页 Banner 指示点</strong>
              <span>轮播图下方是否显示分页圆点</span>
            </div>
            <el-switch v-model="settings.showBannerDots" @change="persist" />
          </div>
          <div class="ais-item">
            <div class="ais-item__text">
              <strong>首页 Banner 自动轮播</strong>
              <span>关闭后需手动切换图片</span>
            </div>
            <el-switch v-model="settings.autoPlayBanner" @change="persist" />
          </div>
          <div class="ais-item">
            <div class="ais-item__text">
              <strong>消息通知</strong>
              <span>控制站内通知提醒的展示</span>
            </div>
            <el-switch v-model="settings.enableNotification" @change="persist" />
          </div>
          <p class="ais-tip">
            显示项保存在本机的 userSettings 中，使用 patch 方式写入，切换任意一项都不会抹掉其它设置。
          </p>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script>
import MemoryPanel from "@/components/MemoryPanel.vue";
import { loadSettings, saveSettings, applyDarkMode } from "@/utils/userSettings.js";

/**
 * 用户端统一设置抽屉（阶段 8 / D3 改写）。
 *
 * <p>替代原先 Main.vue 里那个只包含 4 个本地开关的偏好设置弹窗：
 * 现在「设置」下拉只有一个出口 $refs.aiDrawer.open()，
 * 抽屉内同时承载结构化 AI 记忆与本地显示项，且不含任何
 * initial_prompt / persona 自由文本输入区。</p>
 *
 * <p>医生端可复用：传 endpoint="/doctor/ai-memory" 与 http=doctorRequest。</p>
 */
export default {
  name: "AiSettingsDrawer",
  components: { MemoryPanel },
  props: {
    endpoint: { type: String, default: "/user/ai-memory" },
    http: { type: [Object, Function], default: null },
    title: { type: String, default: "偏好设置" },
  },
  data() {
    return {
      visible: false,
      activeTab: "memory",
      settings: loadSettings(),
    };
  },
  methods: {
    open() {
      this.visible = true;
      this.settings = loadSettings();
      this.$nextTick(() => {
        if (this.$refs.memory) this.$refs.memory.load();
      });
    },
    persist() {
      // patch 写入，保留其它键；主题 DOM 只走 userSettings 的唯一出口
      this.settings = saveSettings(this.settings);
      applyDarkMode(this.settings);
    },
  },
};
</script>

<style scoped>
.ais-tabs {
  padding: 0 4px;
}
.ais-display {
  padding: 4px 2px;
}
.ais-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e5eaf2);
}
.ais-item__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ais-item__text span {
  font-size: 12px;
  color: var(--el-text-color-secondary, #7b8798);
}
.ais-tip {
  margin: 16px 4px 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary, #7b8798);
}
</style>
