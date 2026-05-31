<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
export default {
  name: "App",
  created() {
    this.initDarkMode();
  },
  watch: {
    $route(to) {
      // 路由变化时检查是否需要应用深色模式
      this.applyDarkModeForRoute(to.path);
    },
  },
  methods: {
    initDarkMode() {
      const settings = localStorage.getItem("userSettings");
      if (settings) {
        const parsed = JSON.parse(settings);
        if (parsed.isDarkMode) {
          // 只在用户端应用深色模式
          const currentPath = this.$route?.path || "";
          if (currentPath.startsWith("/user")) {
            document.documentElement.classList.add("dark");
          }
        }
      }
    },
    applyDarkModeForRoute(path) {
      const settings = localStorage.getItem("userSettings");
      if (settings) {
        const parsed = JSON.parse(settings);
        if (parsed.isDarkMode && path.startsWith("/user")) {
          document.documentElement.classList.add("dark");
        } else {
          document.documentElement.classList.remove("dark");
        }
      } else {
        document.documentElement.classList.remove("dark");
      }
    },
  },
};
</script>

<style lang="scss">
#app {
  height: 100%;
  background-color: #f5f7fa;
  transition: background-color 0.3s ease;
}

html.dark #app {
  background-color: #1a1a2e;
}
</style>
