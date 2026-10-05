import axios from "axios";
import { ElMessage } from "element-plus";
import { getDoctorToken, clearDoctorToken } from "@/utils/doctorAuth.js";

/**
 * 医生端专用 HTTP 实例（2026-10-04）
 *
 * <p><b>为什么不复用 utils/request.js</b>：那个实例硬编码了用户端的
 * {@code token} 键与「401 → 跳 /login」的固定行为。医生端 token 存在
 * {@code doctor-token} 键、登录页在 {@code /doctor/login}，
 * 若混用会导致：① 请求不带医生 token → 401；② 401 后被跳到用户登录页。
 *
 * <p>因此医生端用独立实例，行为完全自洽。
 */
export const URL_API =
  process.env.VUE_APP_API_BASE ||
  "http://localhost:21090/api/personal-health/v1.0";

const doctorRequest = axios.create({
  baseURL: URL_API,
  timeout: 30000,
});

// 请求：带上医生 token
doctorRequest.interceptors.request.use(
  (config) => {
    const token = getDoctorToken();
    if (token) {
      config.headers["token"] = token;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应：区分 401（会话失效，需重新登录）与 403（越权，仅提示不登出）
doctorRequest.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error && error.response && error.response.status;
    const body = (error && error.response && error.response.data) || {};

    if (status === 401) {
      // 401 = 不知道你是谁 / 会话已失效 → 清医生登录态回医生登录页
      clearDoctorToken();
      ElMessage.error(body.msg || "登录状态已失效，请重新登录");
      if (!(window.location.hash || "").startsWith("#/doctor/login")) {
        window.location.hash = "#/doctor/login";
      }
    } else if (status === 403) {
      // 403 = 知道你是谁但没权限 → 绝不登出，只提示。
      // 常见于用用户 token 访问医生端接口的情况。
      ElMessage.warning(body.msg || "无权访问该功能");
    }
    return Promise.reject(error);
  }
);

export default doctorRequest;
