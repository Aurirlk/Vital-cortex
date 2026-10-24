import axios from "axios";
import { ElMessage } from "element-plus";
import { getToken, clearToken } from "@/utils/storage.js";

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
      const body = error.response.data || {};
      // roadmap §1.3：业务码 4010 = 账号被禁用（管理员锁定），给出明确提示；
      // 其余 401（token 失效/会话过期）静默清 token 跳登录。
      if (body.code === 4010) {
        ElMessage.error(body.msg || "账号已被禁用，请联系管理员");
      }
      // 业务码 4011 = 后端「读不出账号状态」（Redis 与 MySQL 同时不可用）时的 fail-closed 拒绝。
      // 这和 4010 是两件事：4010 是「你被封了」，4011 是「我们这边刚才没查出来」，
      // 所以提示「系统繁忙，请稍后重试」，并且**不清 token、不跳登录页** ——
      // 此刻本地 token 大概率仍然有效，把它清掉等于因为服务端抖一下就把用户踢下线，
      // 用户重试也还得重新登录。请求本身仍被拒（走 reject 返回给各页面）。
      if (body.code === 4011) {
        ElMessage.error(body.msg || "系统繁忙，请稍后重试");
        return Promise.reject(error);
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
    } else if (status === 403) {
      // 403 = 知道你是谁，但没有这个权限（@Protector(role=...) 角色不匹配）。
      // 与医生端 utils/doctorRequest.js 的口径一致：只提示，绝不能清 token ——
      // 否则一次误点就把已登录的用户踢下线。
      // 后端改真状态码之前这里是 HTTP 200 + 业务码，各页面自己 res.data.code === 400
      // 弹提示；改成 403 后那些代码走不到了，提示统一放在这里。
      const body = (error.response && error.response.data) || {};
      ElMessage.warning(body.msg || "无权访问该功能");
    }
    return Promise.reject(error);
  }
);

export default request;
