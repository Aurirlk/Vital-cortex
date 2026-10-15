/**
 * v-reveal：滚动进入视口时的入场动画指令。
 *
 * <p>官网风格的页面要靠「内容随滚动逐段浮现」来建立节奏感，否则一屏静态文字
 * 读起来像文档。但入场动画有一类必须防住的失败模式：<b>内容永远不出现</b>。
 * 只要 JS 出错、IntersectionObserver 没触发、或者元素初始就在视口外被判成
 * 「还没进来」，用户看到的就是一片空白 —— 而且这是**内容丢失**，
 * 不是「少了个动效」。所以本指令带三重兜底，任何一条命中都会直接显示：</p>
 *
 * <ol>
 *   <li><b>没有 IntersectionObserver</b>（老浏览器）→ 立刻显示，不做动画。</li>
 *   <li><b>系统开启「减少动态效果」</b> → 立刻显示。前庭敏感的用户看到的是内容，
 *       而不是一段位移。</li>
 *   <li><b>3.5 秒超时</b> → 无论观察器有没有回调，到点就显示。
 *       这条兜住的是「观察器被别处的布局抖动卡住」这类说不清的情况。</li>
 * </ol>
 *
 * <p>用法：</p>
 * <pre>
 * &lt;div v-reveal&gt;默认向上浮现&lt;/div&gt;
 * &lt;div v-reveal="{ delay: 120, y: 40 }"&gt;延迟 120ms、位移 40px&lt;/div&gt;
 * </pre>
 */

const SHOW_TIMEOUT_MS = 3500;

/** 是否要求减少动效 */
function prefersReducedMotion() {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

function show(el) {
  el.style.opacity = "";
  el.style.transform = "";
  el.style.transition = "";
  el.classList.add("is-revealed");
}

export default {
  mounted(el, binding) {
    const opts = binding.value || {};
    const { delay = 0, y = 24, x = 0, scale = 1, duration = 560 } = opts;

    // 兜底 2：减少动效 → 直接显示
    if (prefersReducedMotion()) {
      el.classList.add("is-revealed");
      return;
    }

    // 初始态直接写在行内样式上，保证「先隐藏再出现」不会闪一下原样
    el.style.opacity = "0";
    el.style.transform = `translate3d(${x}px, ${y}px, 0) scale(${scale})`;
    el.style.transition = `opacity ${duration}ms cubic-bezier(0.22,1,0.36,1) ${delay}ms,
      transform ${duration}ms cubic-bezier(0.22,1,0.36,1) ${delay}ms`;
    el.style.willChange = "opacity, transform";

    // 兜底 1：没有 IntersectionObserver
    if (typeof IntersectionObserver === "undefined") {
      show(el);
      return;
    }

    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      show(el);
      if (io) io.disconnect();
      if (timer) window.clearTimeout(timer);
      el.removeEventListener("transitionend", onEnd);
    };

    const onEnd = (e) => {
      if (e.target === el && e.propertyName === "opacity") finish();
    };

    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) finish();
      },
      // 提前 6% 触发：等元素完全进来才开始动，用户已经看到它「卡」在那儿了
      { threshold: 0.01, rootMargin: "0px 0px -6% 0px" }
    );

    // 兜底 3：定时显示
    const timer = window.setTimeout(finish, SHOW_TIMEOUT_MS + delay);

    el.addEventListener("transitionend", onEnd);
    el.__revealFinish = finish;
    io.observe(el);
  },

  // 列表重排 / keep-alive 复用时，已经显示过的元素不要再被隐藏一次
  updated(el) {
    if (el.classList.contains("is-revealed")) {
      el.style.opacity = "";
      el.style.transform = "";
    }
  },

  beforeUnmount(el) {
    el.removeEventListener("transitionend", () => {});
    if (el.__revealFinish) el.__revealFinish();
    delete el.__revealFinish;
  },
};
