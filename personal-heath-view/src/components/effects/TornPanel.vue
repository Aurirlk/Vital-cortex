<template>
  <div
    class="torn-panel"
    :class="{ 'is-open': visible }"
    :style="{ '--torn-h': edgeHeight + 'px' }"
  >
    <!-- 撕口本身是一段 SVG，不是图片：它要随容器宽度重算，图片做不到「任意宽度都不拉伸」 -->
    <svg
      v-if="torn !== 'bottom'"
      class="torn-panel__edge torn-panel__edge--top"
      :width="w"
      :height="edgeHeight"
      :viewBox="`0 0 ${w} ${edgeHeight}`"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <path :d="topPath" class="torn-panel__fill" />
      <path :d="topShadowPath" class="torn-panel__shade" />
    </svg>

    <div class="torn-panel__body">
      <div v-if="title" class="torn-panel__head">
        <span class="torn-panel__title">{{ title }}</span>
        <span
          v-if="closable"
          class="torn-panel__close"
          role="button"
          tabindex="0"
          aria-label="关闭"
          @click="close"
          @keydown.enter="close"
          @keydown.space.prevent="close"
        >
          <el-icon><Close /></el-icon>
        </span>
      </div>
      <div class="torn-panel__content">
        <slot></slot>
      </div>
    </div>

    <svg
      v-if="torn !== 'top'"
      class="torn-panel__edge torn-panel__edge--bottom"
      :width="w"
      :height="edgeHeight"
      :viewBox="`0 0 ${w} ${edgeHeight}`"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <path :d="bottomPath" class="torn-panel__fill" />
      <path :d="bottomShadowPath" class="torn-panel__shade" />
    </svg>
  </div>
</template>

<script>
/**
 * 撕线面板：边缘是「手撕纸」那种不规则毛边，而不是直线或规则锯齿。
 *
 * <p><b>为什么用 SVG 而不是 CSS 方案：</b>
 * 常见的做法有两种，都有硬伤 ——
 * ① {@code clip-path: polygon(...)} 画锯齿：能出形，但**不带阴影**，
 *    纸片少了投影就只剩一个平味的色块，「撕」的感觉全无；
 *    而且 clip-path 会连子元素的阴影一起裁掉。
 * ② {@code mask-image} + 重复渐变：宽度变化时齿距会被拉伸变形，
 *    容器一宽齿就变成斜的。
 * 内联 SVG 两个问题都没有：路径可以按当前宽度重算，且能对路径本体用
 * {@code drop-shadow}，阴影是**顺着撕口形状**走的。</p>
 *
 * <p><b>为什么撕口要「确定性随机」：</b>
 * 每次渲染重新 Math.random() 会让撕口在窗口缩放、重新打开时整条跳变，
 * 像闪烁。这里用固定种子的 mulberry32，同一宽度永远得到同一条撕口。</p>
 *
 * <p><b>颜色必须由令牌统一供给：</b>
 * 撕口是「用容器底色咬掉一块」，所以路径填充色必须**等于面板底色**。
 * 两边各写一次颜色是这类组件最常见的坏味道 —— 改一处忘一处，
 * 撕口就会变成另一块可见的色斑。这里两边都取 {@code --xh-torn-bg}。</p>
 */
export default {
  name: "TornPanel",
  props: {
    visible: {
      type: Boolean,
      default: true,
    },
    title: {
      type: String,
      default: "",
    },
    closable: {
      type: Boolean,
      default: true,
    },
    /** 'top' | 'bottom' | 'both' —— 哪几条边是撕口 */
    torn: {
      type: String,
      default: "both",
      validator: (v) => ["top", "bottom", "both"].includes(v),
    },
    /** 撕口高度（px）。太矮看不出撕，太高像破损 */
    edgeHeight: {
      type: Number,
      default: 14,
    },
    /** 撕口的随机种子：同一个种子 + 同一个宽度 = 同一条撕口 */
    seed: {
      type: Number,
      default: 20261013,
    },
  },
  data() {
    return {
      w: 320,
    };
  },
  computed: {
    topPath() {
      return this.tearPath(this.w, this.edgeHeight, true);
    },
    bottomPath() {
      return this.tearPath(this.w, this.edgeHeight, false);
    },
    /**
     * 纸片投影：把同一条路径整体下移 2px、淡化后垫在后面。
     * 不用 CSS filter —— filter 作用在整个 svg 盒子上，会被 svg 的
     * 可视区域裁掉；手绘偏移路径在任何浏览器上表现一致。
     */
    topShadowPath() {
      return this.tearPath(this.w, this.edgeHeight, true, 2);
    },
    bottomShadowPath() {
      return this.tearPath(this.w, this.edgeHeight, false, 2);
    },
  },
  watch: {
    visible: {
      handler() {
        // 展开后再量一次：v-if 的边在初次挂载时宽度可能还是 0
        this.$nextTick(this.measure);
      },
      immediate: true,
    },
  },
  mounted() {
    this.measure();
    if (typeof ResizeObserver !== "undefined") {
      this.ro = new ResizeObserver(() => this.measure());
      this.ro.observe(this.$el);
    } else {
      this.onResize = () => this.measure();
      window.addEventListener("resize", this.onResize);
    }
  },
  beforeUnmount() {
    if (this.ro) this.ro.disconnect();
    if (this.onResize) window.removeEventListener("resize", this.onResize);
  },
  methods: {
    measure() {
      if (!this.$el) return;
      const w = Math.round(this.$el.getBoundingClientRect().width);
      if (w > 0 && w !== this.w) this.w = w;
    },

    /**
     * 生成一条撕口路径。
     *
     * 做法：沿宽度切若干段，每段给一个 0..h 之间的抖动高度，
     * 段与段之间插入一个「尖角」点 —— 只有起伏没有尖角会像波浪，
     * 只有尖角没有起伏会像锯齿；两者叠加才像手撕的纸。
     *
     * @param {number} w     宽度
     * @param {number} h     撕口高度
     * @param {boolean} top  true = 撕口在面板上沿（向下咬）
     * @param {number} dy    整体垂直偏移（画投影用）
     */
    tearPath(w, h, top, dy = 0) {
      const teeth = Math.max(6, Math.round(w / 14)); // 齿距约 14px
      const step = w / teeth;
      const rand = this.mulberry32(this.seed + Math.round(w));
      // 每个齿一个振幅，保证相邻齿不至于一模一样
      const amps = [];
      for (let i = 0; i <= teeth; i++) {
        amps.push(0.25 + rand() * 0.75);
      }

      const pts = [];
      for (let i = 0; i <= teeth; i++) {
        const x = +(i * step).toFixed(2);
        const a = amps[i] * h;
        // 每个齿：先到「深点」（撕下去），再到「浅点」（扯上来）
        pts.push([x, a]);
        if (i < teeth) {
          const midX = +(x + step / 2).toFixed(2);
          pts.push([midX, a * 0.35 + rand() * h * 0.18]);
        }
      }

      const mapY = (y) => (top ? y : h - y) + dy;

      // 顶部撕口：从左上角进入撕线，描到右上角，再沿上边闭合；
      // 底部撕口对称处理。闭合边始终贴着面板本体，不产生缝隙。
      let d = top
        ? `M 0 ${dy} L 0 ${mapY(pts[0][1])}`
        : `M 0 ${h + dy} L 0 ${mapY(pts[0][1])}`;
      for (const [x, y] of pts) {
        d += ` L ${x} ${mapY(y).toFixed(2)}`;
      }
      d += top
        ? ` L ${w} ${dy} Z`
        : ` L ${w} ${h + dy} Z`;
      return d;
    },

    /** mulberry32：小、快、确定性的 PRNG。只用来画撕口，不用于任何安全场景。 */
    mulberry32(seed) {
      let a = seed >>> 0;
      return function () {
        a |= 0;
        a = (a + 0x6d2b79f5) | 0;
        let t = Math.imul(a ^ (a >>> 15), 1 | a);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
      };
    },

    close() {
      this.$emit("update:visible", false);
      this.$emit("close");
    },
  },
};
</script>

<style scoped>
.torn-panel {
  position: relative;
  /* 与撕口同源：两处都取令牌，改一处即可 */
  background: var(--xh-torn-bg);
  color: var(--xh-text-primary);
  padding: var(--xh-space-base) var(--xh-space-lg);
  opacity: 0;
  transform: translateY(-6px);
  transition: opacity var(--xh-morph-dur) var(--xh-morph-ease),
    transform var(--xh-morph-dur) var(--xh-morph-ease);
}

.torn-panel.is-open {
  opacity: 1;
  transform: translateY(0);
  animation: xhTearOpen var(--xh-morph-dur) var(--xh-morph-ease);
}

.torn-panel__edge {
  position: absolute;
  left: 0;
  display: block;
  pointer-events: none;
}

.torn-panel__edge--top {
  top: calc(-1 * var(--torn-h, 14px));
  height: var(--torn-h, 14px);
}

.torn-panel__edge--bottom {
  bottom: calc(-1 * var(--torn-h, 14px));
  height: var(--torn-h, 14px);
}

/* 撕口与面板同色 —— 它咬的是面板自己 */
.torn-panel__fill {
  fill: var(--xh-torn-bg);
}

.torn-panel__shade {
  fill: var(--xh-torn-shadow);
  opacity: 0.5;
}

.torn-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xh-space-sm);
  margin-bottom: var(--xh-space-sm);
}

.torn-panel__title {
  font-size: var(--xh-font-size-md);
  font-weight: var(--xh-font-semibold);
  letter-spacing: 0.02em;
}

.torn-panel__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: var(--xh-radius-full);
  color: var(--xh-text-muted);
  cursor: pointer;
  transition: background var(--xh-transition-fast),
    color var(--xh-transition-fast);
}

.torn-panel__close:hover,
.torn-panel__close:focus-visible {
  background: var(--xh-bg-hover);
  color: var(--xh-text-primary);
}

.torn-panel__content {
  font-size: var(--xh-font-size-base);
  line-height: var(--xh-line-height-normal);
  color: var(--xh-text-secondary);
}

@media (prefers-reduced-motion: reduce) {
  .torn-panel,
  .torn-panel.is-open {
    animation: none;
    transition: none;
    opacity: 1;
    transform: none;
  }
}
</style>
