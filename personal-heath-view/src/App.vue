<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
import { applyDarkModeForRoute } from "@/utils/userSettings.js";
export default {
  name: "App",
  created() {
    // 阶段 8：主题 DOM 操作统一收敛到 utils/userSettings.js 的唯一出口
    applyDarkModeForRoute(this.$route?.path || "");
  },
  watch: {
    $route(to) {
      applyDarkModeForRoute(to.path);
    },
  },
};
</script>

<style lang="scss">
#app {
  height: 100%;
  background-color: var(--bg-color, #f5f7fa);
  transition: background-color 0.3s ease;
}
</style>
