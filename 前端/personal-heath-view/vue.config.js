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
      "/api": {
        target: "http://localhost:21090",
        changeOrigin: true,
        pathRewrite: { "^/api": "" },
      },
    },
    client: {
      overlay: false,
    },
  },
  transpileDependencies: ["element-plus"],
});
