<template>
  <div
    class="dots"
    :class="[`is-${variant}`, `is-${position}`]"
    role="tablist"
    :aria-label="ariaLabel"
    @keydown="onKeydown"
  >
    <button
      v-for="(_, i) in count"
      :key="i"
      type="button"
      class="dots__dot"
      :class="{ 'is-active': i === modelValue }"
      role="tab"
      :tabindex="i === modelValue ? 0 : -1"
      :aria-selected="i === modelValue ? 'true' : 'false'"
      :aria-label="`第 ${i + 1} 张，共 ${count} 张`"
      @click="$emit('update:modelValue', i)"
    >
      <span class="dots__inner"></span>
      <!--
        自动播放进度：只在当前这张上挂一条。
        :key 带上 index 是**必须的** —— 没有它，切换下标时 Vue 会复用同一个
        元素，CSS 动画不会重头播，进度条会「只跑第一轮」。这是进度点最常见的坑。
      -->
      <span
        v-if="i === modelValue && autoplayMs > 0"
        :key="`fill-${i}-${cycle}`"
        class="dots__fill"
        :style="{ animationDuration: autoplayMs + 'ms' }"
      ></span>
    </button>
  </div>
</template>

<script>
/**
 * 轮播指示点。
 *
 * <p>它比看上去更需要认真做 —— 指示点其实同时承担两个信息：
 * 「现在第几张」和「还要等多久」。只做前者的话，用户在自动播放的轮播前
 * 永远不知道下一次跳转什么时候发生，只能干等。</p>
 *
 * <p><b>两个容易错的点：</b></p>
 * <ol>
 *   <li><b>进度条必须靠 :key 强制重挂</b>。用同一个 DOM 元素改 style 是
 *       重启不了 CSS 动画的，切换后进度条会一直停在上一次的终态。</li>
 *   <li><b>轮播暂停时进度要停</b>。父组件把它自己的 autoplayMs 传 0 即可，
 *       于是进度条整个不渲染 —— 停着的进度条比没有进度条更让人误解。</li>
 * </ol>
 *
 * <p><b>无障碍</b>：整体是 {@code role="tablist"}，每个点是 {@code role="tab"}，
 * 并且用 roving tabindex（只有当前项 tabindex=0），左右方向键在点之间移动。
 * 这样键盘用户 Tab 进来一次就能在轮播里选片，而不是按 N 次 Tab。</p>
 */
export default {
  name: "CarouselDots",
  props: {
    count: {
      type: Number,
      required: true,
    },
    modelValue: {
      type: Number,
      default: 0,
    },
    /** 'dot' 圆点 / 'pill' 长条 / 'bar' 细线 */
    variant: {
      type: String,
      default: "pill",
      validator: (v) => ["dot", "pill", "bar"].includes(v),
    },
    /** 'center' | 'left' | 'right' */
    position: {
      type: String,
      default: "center",
      validator: (v) => ["center", "left", "right"].includes(v),
    },
    /** 自动播放一轮的毫秒数；0 = 暂停，不显示进度 */
    autoplayMs: {
      type: Number,
      default: 0,
    },
    /** 每轮播放次数变化时递增，用于在「暂停再恢复」时也重启动画 */
    cycle: {
      type: Number,
      default: 0,
    },
    ariaLabel: {
      type: String,
      default: "轮播导航",
    },
  },
  emits: ["update:modelValue"],
  computed: {
    items() {
      return Array.from({ length: this.count }, (_, i) => i);
    },
  },
  methods: {
    onKeydown(e) {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
      e.preventDefault();
      const dir = e.key === "ArrowRight" ? 1 : -1;
      const next = (this.modelValue + dir + this.count) % this.count;
      this.$emit("update:modelValue", next);
      // 焦点跟着选中项走，否则方向键按第二次时焦点已经不在当前点上了
      this.$nextTick(() => {
        const list = this.$el.querySelectorAll(".dots__dot");
        if (list[next]) list[next].focus();
      });
    },
  },
};
</script>

<style scoped>
.dots {
  /* 强调色可由父级覆盖，默认取品牌主色（轮播底色是任意渐变，
     固定色值在部分背景上会糊掉） */
  --dots-accent: var(--xh-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  pointer-events: auto;
}

.dots.is-center {
  justify-content: center;
}

.dots.is-left {
  justify-content: flex-start;
}

.dots.is-right {
  justify-content: flex-end;
}

.dots__dot {
  position: relative;
  /* 去掉浏览器默认按钮样式，但保留焦点能力 */
  appearance: none;
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  /* 触点做大有讲究：视觉上只有 8px，但可点区域必须 ≥24px，
     否则触屏上基本点不中（WCAG 2.5.8 的最低要求是 24×24） */
  min-width: 24px;
  min-height: 24px;
  display: grid;
  place-items: center;
}

.dots__dot:focus-visible {
  outline: 2px solid var(--dots-accent);
  outline-offset: 2px;
  border-radius: var(--xh-radius-sm);
}

/* ---- 视觉本体 ---- */
.dots__inner {
  display: block;
  position: relative;
  overflow: hidden;
  /* 双层描边：内层实心 + 外圈浅色环。
     轮播图底色可能是任意照片，单一颜色的点在某些图上会「消失」 */
  background: rgba(255, 255, 255, 0.55);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.7),
    0 1px 3px rgba(15, 23, 42, 0.25);
  transition: width var(--xh-morph-dur) var(--xh-morph-ease),
    background var(--xh-transition-base),
    transform var(--xh-transition-base);
}

.dots.is-dot .dots__inner {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dots.is-pill .dots__inner {
  width: 8px;
  height: 8px;
  border-radius: var(--xh-radius-full);
}

.dots.is-bar .dots__inner {
  width: 14px;
  height: 3px;
  border-radius: var(--xh-radius-full);
}

.dots__dot.is-active .dots__inner {
  background: var(--dots-accent);
  box-shadow: 0 0 0 1.5px rgba(255, 255, 255, 0.9),
    0 2px 8px rgba(15, 23, 42, 0.3);
  transform: scale(1.05);
}

/* 当前项拉长成胶囊；这是「哪个是当前」最强的一层信号，
   比只靠颜色（色觉障碍用户可能分辨不出）可靠 */
.dots.is-pill .dots__dot.is-active .dots__inner {
  width: 26px;
}

.dots.is-bar .dots__dot.is-active .dots__inner {
  width: 32px;
  background: var(--dots-accent);
}

/* ---- 自动播放进度 ---- */
.dots__fill {
  position: absolute;
  inset: 0;
  background: var(--dots-accent);
  opacity: 0.75;
  transform-origin: left center;
  transform: scaleX(0);
  animation-name: dotFill;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

.dots.is-dot .dots__fill,
.dots.is-pill .dots__fill {
  border-radius: 50%;
}

@keyframes dotFill {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .dots__inner {
    transition: none;
  }

  /* 减少动效：进度条不再「扫」，直接静态显示当前张 */
  .dots__fill {
    animation: none;
    transform: scaleX(1);
  }
}
</style>
