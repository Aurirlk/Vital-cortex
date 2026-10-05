import { getToken } from "@/utils/storage.js";
import { jwtDecode } from "jwt-decode";

/**
 * 医生端身份工具（2026-10-04）
 *
 * 医生与用户是两套独立账号体系：
 *   - 医生登录接口 /doctor/login，token 里 role=3，且**不在 user 表中**
 *   - 用户/管理员登录 /user/login，role=1|2
 *
 * 因此前端不能用 /permission/user/0 那套查 user 表的接口来判断医生权限，
 * 必须直接从 token 解出 role。
 */

export const ROLE_ADMIN = 1;
export const ROLE_USER = 2;
export const ROLE_DOCTOR = 3;

/** 医生端 token 的独立存储键 —— 与用户端 token 互不覆盖 */
const DOCTOR_TOKEN_KEY = "doctor-token";
const DOCTOR_INFO_KEY = "doctor-info";

export function getDoctorToken() {
  return sessionStorage.getItem(DOCTOR_TOKEN_KEY);
}

export function setDoctorToken(token) {
  sessionStorage.setItem(DOCTOR_TOKEN_KEY, token);
}

export function clearDoctorToken() {
  sessionStorage.removeItem(DOCTOR_TOKEN_KEY);
  sessionStorage.removeItem(DOCTOR_INFO_KEY);
}

export function setDoctorInfo(obj) {
  sessionStorage.setItem(DOCTOR_INFO_KEY, JSON.stringify(obj || {}));
}

export function getDoctorInfo() {
  try {
    return JSON.parse(sessionStorage.getItem(DOCTOR_INFO_KEY) || "null");
  } catch (e) {
    return null;
  }
}

/**
 * 从 token 解出 payload。失败返回 null（token 过期/被篡改/格式不对）。
 * @param {string} token
 */
export function decodeToken(token) {
  if (!token) return null;
  try {
    return jwtDecode(token);
  } catch (e) {
    return null;
  }
}

/**
 * 取当前登录者的角色编码。
 *
 * 医生优先读 doctor-token，用户端读 token。
 * 这样即使两个 token 同时存在（比如同一浏览器开两个标签），
 * 也能靠当前所在端判断该用哪一个。
 *
 * @param {string} [which] "doctor" | "user"，不传则先查医生端
 * @returns {number|null} 1管理员 2用户 3医生；无法解析时 null
 */
export function getRole(which) {
  if (which === "user") {
    return decodeToken(getToken())?.role ?? null;
  }
  const doctorPayload = decodeToken(getDoctorToken());
  if (doctorPayload) {
    return doctorPayload.role ?? null;
  }
  return decodeToken(getToken())?.role ?? null;
}

/**
 * 医生端的登录态判定：必须是 role=3 才算。
 * @returns {boolean}
 */
export function isDoctorLoggedIn() {
  return getRole("doctor") === ROLE_DOCTOR;
}

/**
 * 各端允许访问的路径前缀。与后端 DoctorIsolation 保持一致。
 * 这里是「体验层」的拦截，真正的安全边界在后端 —— 前端只负责不让人白跑一趟。
 */
export const ACCESS = {
  doctor: ["/doctor"],
  user: ["/user"],
  admin: ["/admin"],
};

/**
 * 判断角色能否进入某个路径。
 * @param {string} path
 * @param {number|null} role
 * @returns {boolean}
 */
export function canAccess(path, role) {
  if (!path || role == null) return false;
  if (path.startsWith("/doctor")) return role === ROLE_DOCTOR;
  if (path.startsWith("/user")) return role === ROLE_USER || role === ROLE_ADMIN;
  if (path.startsWith("/admin")) return role === ROLE_ADMIN;
  // 登录/注册等公共页
  return true;
}

/**
 * 角色对应的首页 —— 登录后落地页。
 * @param {number|null} role
 * @returns {string}
 */
export function homeOf(role) {
  if (role === ROLE_DOCTOR) return "/doctor/home";
  if (role === ROLE_ADMIN) return "/admin/adminLayout";
  return "/user/news-record";
}
