const { defineConfig } = require("@vue/cli-service");
module.exports = defineConfig({
  lintOnSave: false,
  assetsDir: "static",
  parallel: false,
  publicPath: "./",
  devServer: {
    host: "localhost",
    port: 21091,
    https: false,
    proxy: {
      // 后端 context-path 本身含 /api 前缀，不能再剥掉 /api
      "/api": {
        target: "http://localhost:21090",
        changeOrigin: true,
      },
    },
    client: {
      overlay: false,
    },
  },
  transpileDependencies: ["element-plus"],
});
