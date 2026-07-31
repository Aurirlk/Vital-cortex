import axios from "axios";
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
