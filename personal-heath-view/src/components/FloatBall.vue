<template>
  <div
    ref="ball"
    class="float-ball"
    :class="[
      `is-docked-${side}`,
      { 'is-docked': docked, 'is-dragging': dragging, 'is-busy': busy },
    ]"
    :style="style"
    role="button"
    tabindex="0"
    :aria-label="ariaLabel"
    @pointerdown="onPointerDown"
    @keydown="onKeydown"
    @mouseenter="hover = true"
    @mouseleave="hover = false"
    @focus="hover = true"
    @blur="hover = false"
  >
    <span class="float-ball__ring" aria-hidden="true"></span>
    <span class="float-ball__icon">
      <el-icon :size="24"><Service /></el-icon>
    </span>
    <span class="float-ball__text"></span>
    <span v-if="badge > 0" class="float-ball__badge">{{
      badge > 99 ? "99+" : badge
    }}</span>
  </div>
</template>

<script>
/**
 * 边缘吸附悬浮球。
 *
 * <p>相比改造前，这一版把四件事补上了 —— 每一件都是「不补就真的不好用」：</p>
 *
 * <ol>
 *   <li><b>松手吸附到最近的一条竖直边</b>。原版松手停在哪儿就是哪儿，
 *       结果球常常停在一屏正中间压住内容。吸附之后它总是贴着边，
 *       且贴住时会**缩进屏幕一半**，只露半个球 —— 不挡内容，鼠标一碰就整个滑出来。</li>
 *   <li><b>用 Pointer Events</b>。原版只监听 {@code mousedown/mousemove}，
 *       手机上完全拖不动（触屏不触发鼠标事件）。Pointer Events 一套覆盖鼠标/触屏/笔。</li>
 *   <li><b>区分「点」与「拖」</b>。位移超过阈值才算拖，否则算点。
 *       没有这条的话，手抖 2px 就会被判成拖拽，点击永远不生效。</li>
 *   <li><b>键盘可用</b>。{@code role="button"} + tabindex，方向键把球沿边移动、
 *       Enter/Space 触发点击。原来它是个纯 div，键盘用户完全够不着。</li>
 * </ol>
 *
 * <p>另外两处细节：位置会写进 localStorage，且**读回来的时候要按当前视口重新钳制** ——
 * 换一台小屏设备登录，存储里的坐标可能远在屏幕外，球就再也找不到了。</p>
 */
export default {
  name: "FloatBall",
  props: {
    /** 距边缘的留白 */
    margin: {
      type: Number,
      default: 16,
    },
    /** 触发「拖」的最小位移（px） */
    dragThreshold: {
      type: Number,
      default: 4,
    },
    /** 角标数字，0 表示不显示 */
    badge: {
      type: Number,
      default: 0,
    },
    /** 是否显示「处理中」的呼吸光晕 */
    busy: {
      type: Boolean,
      default: false,
    },
    ariaLabel: {
      type: String,
      default: "打开智能助手",
    },
    storageKey: {
      type: String,
      default: "floatBallPosition",
    },
  },
  emits: ["click"],
  data() {
    return {
      size: 56,
      /** 未吸附时的实时坐标（左上角） */
      x: 0,
      y: 0,
      side: "right",
      docked: true,
      dragging: false,
      hover: false,
      moving: false,
      ready: false,
    };
  },
  computed: {
    style() {
      return {
        left: this.ready ? this.x + "px" : "-9999px",
        top: this.ready ? this.y + "px" : "0px",
      };
    },
  },
  mounted() {
    this.restore();
    // resize 时重新钳制：把窗口缩小后，原先合法的坐标会跑到视口外
    this.onResize = () => this.reclamp();
    window.addEventListener("resize", this.onResize);
    this.ready = true;
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.onResize);
    this.detach();
  },
  methods: {
    viewport() {
      return { w: window.innerWidth, h: window.innerHeight };
    },

    /** 安全区（刘海屏底部横条）也要让开，否则球会被系统手势条盖住 */
    safeBottom() {
      const probe = getComputedStyle(document.documentElement);
      const raw = probe.getPropertyValue("--xh-safe-bottom").trim();
      const v = parseInt(raw, 10);
      return Number.isFinite(v) ? v : 0;
    },

    clampY(y) {
      const { h } = this.viewport();
      const bottom = h - this.size - this.margin - this.safeBottom();
      return Math.max(this.margin, Math.min(bottom, y));
    },

    xFor(side) {
      const { w } = this.viewport();
      return side === "left"
        ? this.margin
        : Math.max(this.margin, w - this.size - this.margin);
    },

    /** 靠哪边更近就吸附到哪边 */
    nearestSide(cx) {
      const { w } = this.viewport();
      return cx < w / 2 ? "left" : "right";
    },

    restore() {
      const { w, h } = this.viewport();
      let x = null;
      let y = null;
      try {
        const saved = localStorage.getItem(this.storageKey);
        if (saved) {
          const p = JSON.parse(saved);
          if (Number.isFinite(p.x) && Number.isFinite(p.y)) {
            x = p.x;
            y = p.y;
          }
        }
      } catch (e) {
        // 存储里是坏数据：当作没存过，不要因此让悬浮球消失
      }

      if (x === null) {
        // 默认位置：右下，避开大多数页面主内容与底部按钮
        x = this.xFor("right");
        y = Math.round(h * 0.62);
      }

      this.x = Math.max(this.margin, Math.min(w - this.size - this.margin, x));
      this.y = this.clampY(y);
      this.side = this.nearestSide(this.x + this.size / 2);
      // 恢复时一律先吸附好，避免出现「半个球悬在中间」的中间态
      this.x = this.xFor(this.side);
      this.docked = true;
    },

    reclamp() {
      this.y = this.clampY(this.y);
      this.x = this.xFor(this.side);
    },

    // ---------------- 拖动 ----------------

    onPointerDown(e) {
      if (e.button !== undefined && e.button !== 0) return; // 只响应主键
      const el = this.$refs.ball;
      const startX = e.clientX;
      const startY = e.clientY;
      const originX = this.x;
      const originY = this.y;
      let moved = false;

      this.dragging = false;

      if (el.setPointerCapture) {
        try {
          el.setPointerCapture(e.pointerId);
        } catch (err) {
          /* 环境不支持时退回普通拖动 */
        }
      }

      const onMove = (ev) => {
        const dx = ev.clientX - startX;
        const dy = ev.clientY - startY;
        if (!moved && Math.hypot(dx, dy) > this.dragThreshold) {
          moved = true;
          this.dragging = true;
          this.docked = false; // 开始拖就展开成完整的球，方便看清位置
        }
        if (!moved) return;

        const { w, h } = this.viewport();
        // 拖动时允许略微超出边缘（回弹感），松手才吸附
        this.x = Math.max(-this.size * 0.4, Math.min(w - this.size * 0.6, originX + dx));
        this.y = Math.max(0, Math.min(h - this.size, originY + dy));
        this.side = this.nearestSide(this.x + this.size / 2);
      };

      const onUp = () => {
        this.detach();
        if (!moved) {
          this.$emit("click");
          return;
        }
        // 松手吸附：竖直方向钳回可视区，水平方向滑到最近的边
        this.y = this.clampY(this.y);
        this.x = this.xFor(this.side);
        this.dragging = false;
        this.docked = true;
        this.persist();
      };

      this.cleanup = () => {
        el.removeEventListener("pointermove", onMove);
        el.removeEventListener("pointerup", onUp);
        el.removeEventListener("pointercancel", onUp);
      };
      el.addEventListener("pointermove", onMove);
      el.addEventListener("pointerup", onUp);
      el.addEventListener("pointercancel", onUp);
    },

    detach() {
      if (this.cleanup) {
        this.cleanup();
        this.cleanup = null;
      }
      this.dragging = false;
    },

    persist() {
      try {
        localStorage.setItem(
          this.storageKey,
          JSON.stringify({ x: this.x, y: this.y })
        );
      } catch (e) {
        // 隐私模式下 localStorage 可能抛异常；位置存不下不该影响使用
      }
    },

    // ---------------- 键盘 ----------------

    onKeydown(e) {
      const step = e.shiftKey ? 40 : 12;
      const { h } = this.viewport();
      let handled = true;

      if (e.key === "ArrowUp") {
        this.y = this.clampY(this.y - step);
      } else if (e.key === "ArrowDown") {
        this.y = this.clampY(this.y + step);
      } else if (e.key === "ArrowLeft") {
        this.side = "left";
        this.x = this.xFor("left");
      } else if (e.key === "ArrowRight") {
        this.side = "right";
        this.x = this.xFor("right");
      } else if (e.key === "Home") {
        this.y = this.margin;
      } else if (e.key === "End") {
        this.y = this.clampY(h - this.size - this.margin);
      } else if (e.key === "Enter" || e.key === " ") {
        this.$emit("click");
      } else {
        handled = false;
      }

      if (!handled) return;
      e.preventDefault();
      this.persist();
      // 键盘移动时也保证球完全可见并贴边
      this.x = this.xFor(this.side);
      this.docked = true;
    },
  },
};
</script>

<style scoped>
.float-ball {
  --fb-size: 56px;
  position: fixed;
  z-index: 2100;
  width: var(--fb-size);
  height: var(--fb-size);
  border-radius: 50%;
  background: var(--xh-primary-gradient);
  box-shadow: 0 6px 20px rgba(0, 80, 203, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
  touch-action: none;
  /* 只在「松手吸附」和「贴边缩进」时过渡；拖动中关掉，否则球会追着指针跑 */
  transition: transform var(--xh-morph-dur) var(--xh-morph-ease),
    left var(--xh-morph-dur) var(--xh-morph-ease),
    top var(--xh-morph-dur) var(--xh-morph-ease),
    box-shadow var(--xh-transition-base);
  outline: none;
}

.float-ball.is-dragging {
  transition: box-shadow var(--xh-transition-fast);
  cursor: grabbing;
  box-shadow: 0 14px 36px rgba(0, 80, 203, 0.5);
  transform: scale(1.06);
}

.float-ball:focus-visible {
  box-shadow: 0 0 0 4px var(--xh-ring-track), 0 6px 20px rgba(0, 80, 203, 0.4);
}

/*
 * 贴边缩进：只露出约 60%。
 * 这是悬浮球能不能长期留在屏幕上的关键 —— 一个完整覆盖 56px 的球
 * 永远会挡住下面某个按钮，用户最后只能把它拖到角落不用。
 * 半藏之后鼠标一碰就整个滑出来，两者兼得。
 */
.float-ball.is-docked.is-docked-left {
  transform: translateX(calc(-1 * var(--fb-size) * 0.4));
}

.float-ball.is-docked.is-docked-right {
  transform: translateX(calc(var(--fb-size) * 0.4));
}

/* 悬停/聚焦时滑回完整可见。这里必须覆盖上面的位移，所以选择器要更长 */
.float-ball.is-docked.is-docked-left:hover,
.float-ball.is-docked.is-docked-left:focus-visible {
  transform: translateX(0) scale(1.06);
}

.float-ball.is-docked.is-docked-right:hover,
.float-ball.is-docked.is-docked-right:focus-visible {
  transform: translateX(0) scale(1.06);
}

.float-ball__icon {
  display: flex;
  color: #fff;
  line-height: 1;
}

.float-ball__text {
  font-size: 10px;
  color: #fff;
  white-space: nowrap;
  margin-top: 2px;
}

/* 呼吸光晕：提醒球「还活着」，也给 busy 状态一个不喧闹的表达 */
.float-ball__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(0, 80, 203, 0.35);
  opacity: 0;
  pointer-events: none;
}

.float-ball.is-busy .float-ball__ring {
  animation: xhRipple 1.8s ease-out infinite;
}

.float-ball__badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  box-sizing: border-box;
  border-radius: var(--xh-radius-full);
  background: var(--xh-error);
  color: #fff;
  font-size: 11px;
  font-weight: var(--xh-font-bold);
  line-height: 18px;
  text-align: center;
  box-shadow: 0 0 0 2px var(--xh-bg-card);
}

@media (prefers-reduced-motion: reduce) {
  .float-ball {
    transition: none;
  }

  .float-ball.is-dragging,
  .float-ball.is-docked.is-docked-left:hover,
  .float-ball.is-docked.is-docked-right:hover {
    transform: none;
  }

  .float-ball.is-busy .float-ball__ring {
    animation: none;
    opacity: 0.6;
  }
}
</style>
