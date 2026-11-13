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
// D-003/D-008 整改：全局加载设计令牌（--xh-*），打通 token 体系（仅定义变量，不含有问题的 EP 覆写）。
// 主品牌色 #0050cb 已在 assets/themes.css 的 --primary-color 中生效。
import "@/styles/design-tokens.css";
// 阶段 4：视觉特效令牌层（3D / 玻璃 / 光晕 / 撕线 / 形变）。
// 必须排在 design-tokens.css 之后 —— 它引用了 --xh-bg-card 等基线令牌（--xh-torn-bg）。
// 与 dark-mode.scss 的先后无关：本文件自带的暗色覆盖写在 html.dark 里，
// 特异性高于 :root，且 dark-mode.scss 并不定义任何 --xh-* 令牌（不争同一批变量）。
import "@/styles/effects.css";
// SweetAlert2 弹窗定制：必须放在 sweetalert2.min.css 之后，否则会被官方默认样式覆盖。
import "./assets/css/sweetalert.scss";
import request from "@/utils/request";
import md5 from "js-md5";
import swalPlugin from "@/utils/swalPlugin";
import { URL_API } from "@/utils/request";
import { getToken } from "@/utils/storage.js";
import { connectWs } from "@/utils/ws.js";
// 阶段 4：v-reveal 入场指令（官网风格页面的「滚动浮现」）。
// 做成全局指令而不是页面局部注册：它天然是跨页复用的展示层能力，
// 而且局部注册要在每个用到的页面里重复三行 directives/import。
import reveal from "@/directives/reveal.js";

// ---- Element Plus  ----
//  300+
//  import  registerUsedIcons
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Bell,
  Calendar,
  ChatDotRound,
  ChatLineRound,
  Check,
  CircleCheck,
  CircleClose,
  CirclePlus,
  Clock,
  Close,
  Collection,
  Connection,
  CopyDocument,
  Cpu,
  DataAnalysis,
  DataLine,
  Delete,
  Discount,
  Document,
  DocumentCopy,
  Download,
  EditPen,
  Expand,
  Files,
  FirstAidKit,
  Fold,
  HomeFilled,
  House,
  Lock,
  MagicStick,
  Message,
  Microphone,
  Moon,
  OfficeBuilding,
  Open,
  PieChart,
  Plus,
  Promotion,
  QuestionFilled,
  Refresh,
  RefreshLeft,
  RefreshRight,
  Right,
  ScaleToOriginal,
  Search,
  Select,
  Service,
  Setting,
  ShoppingCart,
  Star,
  SuccessFilled,
  SwitchButton,
  Tickets,
  Timer,
  Upload,
  User,
  UserFilled,
  VideoPlay,
  View,
  Warning,
} from "@element-plus/icons-vue";

const usedIcons = [
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Bell,
  Calendar,
  ChatDotRound,
  ChatLineRound,
  Check,
  CircleCheck,
  CircleClose,
  CirclePlus,
  Clock,
  Close,
  Collection,
  Connection,
  CopyDocument,
  Cpu,
  DataAnalysis,
  DataLine,
  Delete,
  Discount,
  Document,
  DocumentCopy,
  Download,
  EditPen,
  Expand,
  Files,
  FirstAidKit,
  Fold,
  HomeFilled,
  House,
  Lock,
  MagicStick,
  Message,
  Microphone,
  Moon,
  OfficeBuilding,
  Open,
  PieChart,
  Plus,
  Promotion,
  QuestionFilled,
  Refresh,
  RefreshLeft,
  RefreshRight,
  Right,
  ScaleToOriginal,
  Search,
  Select,
  Service,
  Setting,
  ShoppingCart,
  Star,
  SuccessFilled,
  SwitchButton,
  Tickets,
  Timer,
  Upload,
  User,
  UserFilled,
  VideoPlay,
  View,
  Warning,
];

const app = createApp(App);

//  icon  <component :is="doctor.icon" />
usedIcons.forEach((icon) => {
  if (icon && icon.name) {
    app.component(icon.name, icon);
  }
});

// ---- 全局指令 ----
app.directive("reveal", reveal);

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

// D-004 整改：应用启动且本地已存在 token（如刷新页面后保留登录态）时，主动建立 WebSocket 连接，
// 以便即时接收消息通知。登录成功分支（Login.vue）也会在写入 token 后调用 connectWs()。
if (getToken()) {
  connectWs();
}

app.mount("#app");
