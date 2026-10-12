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
      //
      this.applyDarkModeForRoute(to.path);
    },
  },
  methods: {
    initDarkMode() {
      const settings = localStorage.getItem("userSettings");
      if (settings) {
        let parsed = {};
        try {
          parsed = JSON.parse(settings);
        } catch (e) {
          console.warn("[App] userSettings 解析失败，已忽略:", e);
        }
        if (parsed.isDarkMode) {
          //
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
        let parsed = {};
        try {
          parsed = JSON.parse(settings);
        } catch (e) {
          console.warn("[App] userSettings 解析失败，已忽略:", e);
        }
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
  background-color: var(--bg-color, #f5f7fa);
  transition: background-color 0.3s ease;
}
</style>
