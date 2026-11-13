import axios from "axios";
import { ElMessage } from "element-plus";
import {
  getToken,
  clearToken,
  getRefreshToken,
  setToken,
  setRefreshToken,
} from "@/utils/storage.js";

/**
 * API  —  .env.development
 *  .env.production
 *
 */
export const URL_API =
  process.env.VUE_APP_API_BASE ||
  "http://localhost:21090/api/personal-health/v1.0";

const request = axios.create({
  baseURL: URL_API,
  timeout: 30000,
});

// 阶段 11 · A1：并发 401 只触发一次 refresh，避免多请求同时轮换把后续刷新打重放。
let refreshPromise = null;

/**
 * 调匿名刷新端点换新的 access/refresh token。
 * 必须用裸 axios：走 request 实例会再次进入响应拦截器，形成递归。
 */
async function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  try {
    const res = await axios.post(`${URL_API}/auth/refresh`, { refreshToken });
    const body = res && res.data;
    if (!body || body.code !== 200 || !body.data || !body.data.token) return false;
    setToken(body.data.token);
    setRefreshToken(body.data.refreshToken);
    return true;
  } catch (e) {
    return false;
  }
}

/** 清登录态并跳登录页；4010（账号被禁用）与刷新失败都走这里。 */
function logoutAndReject(error) {
  try {
    clearToken();
  } catch (e) {
    // 存储不可用时忽略
  }
  const current = window.location.hash || "";
  if (current.indexOf("/login") === -1) {
    window.location.hash = "#/login";
  }
  return Promise.reject(error);
}
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
  async (error) => {
    const status = error && error.response && error.response.status;
    const body = (error && error.response && error.response.data) || {};
    const config = error && error.config;

    if (status === 401) {
      // 业务码 4011 = 后端「读不出账号状态」（Redis 与 MySQL 同时不可用）时的 fail-closed 拒绝。
      // 这和 4010 是两件事：4010 是「你被封了」，4011 是「我们这边刚才没查出来」，
      // 所以提示「系统繁忙，请稍后重试」，并且**不清 token、不跳登录页** ——
      // 此刻本地 token 大概率仍然有效，把它清掉等于因为服务端抖一下就把用户踢下线。
      if (body.code === 4011) {
        ElMessage.error(body.msg || "系统繁忙，请稍后重试");
        return Promise.reject(error);
      }
      // 4010 = 账号被禁用：刷新也救不回来，提示后直接清登录态。
      if (body.code === 4010) {
        ElMessage.error(body.msg || "账号已被禁用，请联系管理员");
        return logoutAndReject(error);
      }

      // 阶段 11 · A1：普通 401（access token 过期）先尝试静默续期再重放原请求。
      // 只处理「本次确实带过 token」的请求，匿名接口 401 不该触发刷新。
      if (config && !config._retry && config.headers && config.headers["token"]) {
        const sentToken = config.headers["token"];
        // 并发场景：别的请求已经刷新过，当前 token 与发出时不同，直接用新 token 重放
        if (getToken() && getToken() !== sentToken) {
          config._retry = true;
          config.headers["token"] = getToken();
          return request(config);
        }
        if (getRefreshToken()) {
          config._retry = true;
          if (!refreshPromise) {
            refreshPromise = refreshAccessToken().finally(() => {
              refreshPromise = null;
            });
          }
          const renewed = await refreshPromise;
          if (renewed) {
            config.headers["token"] = getToken();
            return request(config);
          }
        }
      }
      return logoutAndReject(error);
    }

    if (status === 403) {
      // 403 = 知道你是谁，但没有这个权限（@Protector(role=...) 角色不匹配）。
      // 与医生端 utils/doctorRequest.js 的口径一致：只提示，绝不能清 token ——
      // 否则一次误点就把已登录的用户踢下线。
      ElMessage.warning(body.msg || "无权访问该功能");
    }
    return Promise.reject(error);
  }
);

export default request;
