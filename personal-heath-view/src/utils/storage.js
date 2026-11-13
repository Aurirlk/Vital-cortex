const TOKEN_KEY = "token";
const REFRESH_KEY = "refresh_token";
const INFO = "health-info";
const ACTIVE_PATH = "active_key";
export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

// 阶段 11 · A1：refresh token 与 access token 分开存放，登出/失效时一并清除。
export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_KEY);
}

export function setRefreshToken(token) {
  if (!token) {
    sessionStorage.removeItem(REFRESH_KEY);
    return;
  }
  sessionStorage.setItem(REFRESH_KEY, token);
}

export function getHealthInfo() {
  return sessionStorage.getItem(INFO);
}

export function setHealthInfo(obj) {
  sessionStorage.setItem(INFO, obj);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_KEY);
}
export function getActivePath() {
  return sessionStorage.getItem(ACTIVE_PATH);
}

export function setActivePath(path) {
  sessionStorage.setItem(ACTIVE_PATH, path);
}
