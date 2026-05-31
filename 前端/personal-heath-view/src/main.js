import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import { provinceAndCityData, regionData } from "element-china-area-data";
import VueSweetalert2 from "vue-sweetalert2";
import "sweetalert2/dist/sweetalert2.min.css";
import "./assets/css/editor.scss";
import "./assets/css/button.scss";
import "./assets/css/elementui-cover.scss";
import "./assets/css/basic.scss";
import "./assets/css/dialog.scss";
import "./assets/css/input.scss";
import "./assets/css/dark-mode.scss";
import request from "@/utils/request";
import md5 from "js-md5";
import swalPlugin from "@/utils/swalPlugin";
import * as echarts from "echarts";

const app = createApp(App);

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.config.globalProperties.$md5 = md5;
app.config.globalProperties.$axios = request;
app.config.globalProperties.$echarts = echarts;
app.config.globalProperties.$provinceAndCityData = provinceAndCityData;
app.config.globalProperties.$regionData = regionData;
app.config.globalProperties.$uploadUrl = "http://localhost:21090/api/personal-health/v1.0/file/upload";
// CRM API Key 从环境变量读取，或通过管理员配置
app.config.globalProperties.$crmApiKey = process.env.VUE_APP_CRM_API_KEY || "";

app.use(ElementPlus, { locale: zhCn });
app.use(VueSweetalert2);
app.use(swalPlugin);
app.use(router);

app.mount("#app");
