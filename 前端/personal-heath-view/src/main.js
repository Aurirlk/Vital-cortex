import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
import VueSweetalert2 from "vue-sweetalert2";
import "sweetalert2/dist/sweetalert2.min.css";
import "./assets/css/editor.scss";
import "./assets/css/button.scss";
import "./assets/css/elementui-cover.scss";
import "./assets/css/basic.scss";
import "./assets/css/dialog.scss";
import "./assets/css/input.scss";
import "./assets/css/dark-mode.scss";
import "./assets/themes.css";
import request from "@/utils/request";
import md5 from "js-md5";
import swalPlugin from "@/utils/swalPlugin";
import { URL_API } from "@/utils/request";

// ---- Element Plus  ----
//  300+ 
//  import  registerUsedIcons 
import {
  ArrowDown, ArrowLeft, ArrowRight,
  Bell,
  ChatDotRound, ChatLineRound, Check, CircleClose, CirclePlus, Close, Collection, Connection,
  CopyDocument, DataAnalysis, Delete, Discount, Document, Download,
  Expand,
  Files, FirstAidKit, Fold,
  House,
  MagicStick, Message, Microphone, Moon,
  OfficeBuilding, Open,
  PieChart, Plus, Promotion,
  QuestionFilled,
  Refresh, RefreshLeft, RefreshRight, Right,
  ScaleToOriginal, Search, Select, Service, Setting,
  Timer,
  Upload, User, UserFilled,
  VideoPlay, View,
  Warning,
} from "@element-plus/icons-vue";

const usedIcons = [
  ArrowDown, ArrowLeft, ArrowRight,
  Bell,
  ChatDotRound, ChatLineRound, Check, CircleClose, CirclePlus, Close, Collection, Connection,
  CopyDocument, DataAnalysis, Delete, Discount, Document, Download,
  Expand,
  Files, FirstAidKit, Fold,
  House,
  MagicStick, Message, Microphone, Moon,
  OfficeBuilding, Open,
  PieChart, Plus, Promotion,
  QuestionFilled,
  Refresh, RefreshLeft, RefreshRight, Right,
  ScaleToOriginal, Search, Select, Service, Setting,
  Timer,
  Upload, User, UserFilled,
  VideoPlay, View,
  Warning,
];

const app = createApp(App);

//  icon  <component :is="doctor.icon" /> 
usedIcons.forEach((icon) => {
  if (icon && icon.name) {
    app.component(icon.name, icon);
  }
});

// ----  composables ----
app.config.globalProperties.$md5 = md5;
app.config.globalProperties.$axios = request;
app.config.globalProperties.$uploadUrl = URL_API + "/file/upload";
// MM-05 整改：/file/upload 已恢复登录态校验，所有 el-upload 必须携带 token。
// 用 getter 而非快照值，保证登录之后再发起的上传也能取到最新 token。
app.config.globalProperties.$uploadHeaders = {
  get token() {
    return sessionStorage.getItem("token");
  },
};

app.use(ElementPlus, { locale: zhCn });
app.use(VueSweetalert2);
app.use(swalPlugin);
app.use(router);

app.mount("#app");
