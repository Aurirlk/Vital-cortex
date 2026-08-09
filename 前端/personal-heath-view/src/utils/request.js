import axios from "axios";
import { ElMessage } from "element-plus";
import { getToken, clearToken } from "@/utils/storage.js";

/**
 * API  —  .env.development 
 *  .env.production 
 * 
 */
export const URL_API = process.env.VUE_APP_API_BASE || "http://localhost:21090/api/personal-health/v1.0";

const request = axios.create({
  baseURL: URL_API,
  timeout: 30000,
});
//
request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token !== null) {
      config.headers["token"] = token;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器：后端鉴权失败后由 HTTP 200 + 业务码改为标准 401，
// 这里统一接住，清理本地登录态并跳转登录页，避免各页面出现未处理的 Promise reject。
request.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error && error.response && error.response.status;
    if (status === 401) {
      // roadmap §1.3：业务码 4010 = 账号被禁用（管理员锁定），给出明确提示；
      // 其余 401（token 失效/会话过期）静默清 token 跳登录。
      const body = error.response.data || {};
      if (body.code === 4010) {
        ElMessage.error(body.msg || "账号已被禁用，请联系管理员");
      }
      try {
        clearToken();
      } catch (e) {
        // 存储不可用时忽略
      }
      const current = window.location.hash || "";
      if (current.indexOf("/login") === -1) {
        window.location.hash = "#/login";
      }
    }
    return Promise.reject(error);
  }
);

export default request;
