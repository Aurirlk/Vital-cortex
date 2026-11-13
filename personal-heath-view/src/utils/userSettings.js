// 用户本地偏好设置的单一读写出口。
// 所有页面读写 localStorage 的 "userSettings" 都必须经过本模块，
// 禁止在各组件里直接 JSON.parse / JSON.stringify 整写，
// 否则会出现「单页只认识自己那几个键、写完把别人存的键抹掉」的丢设置 bug。
//
// 真实表现（2026-11-08 修复前）：
//   UserProfile.vue 只认 isDarkMode 一个键，每次切换深色模式都把 { isDarkMode }
//   整体写回，把 Main.vue 存的 showBannerDots / autoPlayBanner / enableNotification
//   三个键抹掉 —— 用户切一次深色模式，轮播与通知偏好即丢失。

export const SETTINGS_KEY = "userSettings";

export const DEFAULTS = {
  isDarkMode: false,
  showBannerDots: true,
  autoPlayBanner: true,
  enableNotification: true,
};

// 读 localStorage，与 DEFAULTS 浅合并。不删存储里多余的键（向前兼容），
// 只保证 DEFAULTS 里的键都有值。解析失败时回退 DEFAULTS。
export function loadSettings() {
  let stored = {};
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (raw) stored = JSON.parse(raw) || {};
  } catch (e) {
    console.warn("[userSettings] 解析失败，回退默认值:", e);
  }
  return { ...DEFAULTS, ...stored };
}

// 关键：patch 而非整写。传入的对象只覆盖对应键，其余键（DEFAULTS 或存储里
// 已有的）全部保留，避免单页写入抹掉其他页面存的键。
export function saveSettings(patch) {
  const next = { ...loadSettings(), ...(patch || {}) };
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(next));
  return next;
}

export function getDarkMode() {
  return !!loadSettings().isDarkMode;
}

// 深色模式的唯一 DOM 出口（阶段 8 收敛）：整站只允许通过
// document.documentElement.classList 的 "dark" 类控制主题。
// 禁止再出现第二种机制（data-theme / body class / 内联变量整写等），
// 否则 App.vue 的路由 watcher 与各页面的设置开关会互相覆盖。
export function applyDarkMode(settings) {
  const on = !!(settings || loadSettings()).isDarkMode;
  if (on) {
    document.documentElement.classList.add("dark");
  } else {
    document.documentElement.classList.remove("dark");
  }
  return on;
}

// 路由级应用：用户端按用户设置生效，其余端（管理端/医生端）一律不加深色类。
export function applyDarkModeForRoute(path) {
  return applyDarkMode({
    ...loadSettings(),
    isDarkMode: getDarkMode() && String(path || "").startsWith("/user"),
  });
}
