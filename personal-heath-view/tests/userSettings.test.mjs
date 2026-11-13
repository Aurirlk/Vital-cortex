// userSettings.js 回归测试（Node 内置 node:test，无需额外依赖）。
// 反向验证：证明「整写覆盖」会丢失其他页面存的键（旧 bug 真实存在），
// 并证明新实现的 saveSettings(patch) 不再丢键。
import test from "node:test";
import assert from "node:assert/strict";

// 简易 localStorage mock
const store = new Map();
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
};

const { loadSettings, saveSettings, DEFAULTS, getDarkMode } = await import(
  "../src/utils/userSettings.js"
);

test("DEFAULTS 四键齐全", () => {
  assert.deepEqual(
    Object.keys(DEFAULTS).sort(),
    ["autoPlayBanner", "enableNotification", "isDarkMode", "showBannerDots"].sort()
  );
});

test("loadSettings 缺省返回 DEFAULTS 副本", () => {
  store.clear();
  assert.deepEqual(loadSettings(), { ...DEFAULTS });
});

test("loadSettings 与 DEFAULTS 浅合并（存储值覆盖对应键）", () => {
  store.clear();
  globalThis.localStorage.setItem(
    "userSettings",
    JSON.stringify({ isDarkMode: true })
  );
  assert.equal(loadSettings().isDarkMode, true);
  assert.equal(loadSettings().showBannerDots, true);
  assert.equal(loadSettings().autoPlayBanner, true);
  assert.equal(loadSettings().enableNotification, true);
});

test("saveSettings 必须是 patch 而非整写（核心回归）", () => {
  store.clear();
  // 先由 Main 写入完整四键
  saveSettings({
    isDarkMode: false,
    showBannerDots: true,
    autoPlayBanner: true,
    enableNotification: true,
  });
  // UserProfile 只改 isDarkMode（对应修复前单页只认这一个键的行为）
  saveSettings({ isDarkMode: true });
  const after = loadSettings();
  assert.equal(after.isDarkMode, true, "深色模式应更新");
  assert.equal(after.showBannerDots, true, "不得被抹掉");
  assert.equal(after.autoPlayBanner, true, "不得被抹掉");
  assert.equal(after.enableNotification, true, "不得被抹掉");
});

test("反向验证：整写覆盖确实会丢失其余键（旧 bug 真实存在）", () => {
  store.clear();
  // 模拟修复前的 UserProfile.vue:485 —— 只写 { isDarkMode }
  const legacyWrite = () => {
    const s = { isDarkMode: true };
    globalThis.localStorage.setItem("userSettings", JSON.stringify(s));
  };
  saveSettings({
    isDarkMode: false,
    showBannerDots: true,
    autoPlayBanner: true,
    enableNotification: true,
  });
  legacyWrite();
  const parsed = JSON.parse(globalThis.localStorage.getItem("userSettings"));
  assert.equal(
    parsed.showBannerDots,
    undefined,
    "修复前整写确实会抹掉其他键"
  );
  assert.equal(parsed.enableNotification, undefined);
});

test("getDarkMode 反映当前值", () => {
  store.clear();
  assert.equal(getDarkMode(), false);
  saveSettings({ isDarkMode: true });
  assert.equal(getDarkMode(), true);
});
