<template>
  <div
    class="rng"
    :class="{ 'is-disabled': disabled, 'is-dragging': activeKey !== null }"
  >
    <div v-if="label" class="rng__head">
      <span class="rng__label">{{ label }}</span>
      <span class="rng__readout">{{ readout }}</span>
    </div>

    <div class="rng__body">
      <div
        ref="track"
        class="rng__track"
        @pointerdown="onTrackDown"
      >
        <div class="rng__rail"></div>
        <!-- 选中区间 -->
        <div
          class="rng__fill"
          :style="{ left: lowPct + '%', width: highPct - lowPct + '%' }"
        ></div>

        <!-- 刻度：只在没有 step 细分时显示，避免上百个刻度把轨道压黑 -->
        <div v-if="showTicks" class="rng__ticks">
          <span
            v-for="t in ticks"
            :key="t.value"
            class="rng__tick"
            :style="{ left: t.pct + '%' }"
          ></span>
        </div>

        <div
          v-for="h in handles"
          :key="h.key"
          class="rng__thumb"
          :class="{ 'is-active': activeKey === h.key }"
          :style="{ left: h.pct + '%' }"
          role="slider"
          tabindex="0"
          :aria-label="h.ariaLabel"
          :aria-valuemin="min"
          :aria-valuemax="max"
          :aria-valuenow="h.value"
          :aria-disabled="disabled"
          @pointerdown.stop="onThumbDown($event, h.key)"
          @keydown="onKeydown($event, h.key)"
        >
          <span class="rng__thumb-knob"></span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
/**
 * 双头区间滑块（两个拖柄，选一段区间）。
 *
 * <p><b>为什么不直接用 {@code el-slider range}：</b>
 * el-slider 的 range 模式能用，但它把两个柄的交叉处理成「互相顶开」，
 * 且外观是 Element 的默认模样，跟本页的圆角/渐变体系不一路。
 * 更实际的原因是这里的区间语义特殊：价格区间的**两个端点应当允许相等**
 * （比如只看 99 元的商品），而默认 el-slider 会强制一个最小间隔。
 * 自己实现能把这条语义写清楚。</p>
 *
 * <p><b>三个必须做对的点：</b></p>
 * <ol>
 *   <li><b>指针捕获</b>：用 {@code setPointerCapture} 而不是在 document 上挂
 *       mousemove/mouseup。前者在指针划出窗口、划到 iframe 上时仍然收得到事件，
 *       后者会「拖着拖着柄卡住不放了」——这是滑块最常见的缺陷。</li>
 *   <li><b>键盘可达</b>：每个柄是 {@code role="slider"} + tabindex，
 *       方向键 ±step、PageUp/Down ±10step、Home/End 到端点。
 *       只能用鼠标拖的滑块对键盘用户等于不存在。</li>
 *   <li><b>交叉钳制而非交换</b>：拖动下柄越过上柄时，把它**顶在**上柄位置，
 *       而不是让两者交换。交换会让用户拖着拖着发现「档位反了」。</li>
 * </ol>
 */
export default {
  name: "RangeSlider",
  props: {
    /** [low, high] */
    modelValue: {
      type: Array,
      default: () => [0, 100],
    },
    min: {
      type: Number,
      default: 0,
    },
    max: {
      type: Number,
      default: 100,
    },
    step: {
      type: Number,
      default: 1,
    },
    /** 左右柄的无障碍名 */
    lowLabel: {
      type: String,
      default: "区间下限",
    },
    highLabel: {
      type: String,
      default: "区间上限",
    },
    label: {
      type: String,
      default: "",
    },
    /** 读数格式化；默认直接显示数字 */
    formatter: {
      type: Function,
      default: null,
    },
    /** 是否画刻度线 */
    showTicks: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      activeKey: null,
    };
  },
  computed: {
    low() {
      return this.clamp(this.modelValue[0]);
    },
    high() {
      return this.clamp(this.modelValue[1]);
    },
    lowPct() {
      return this.toPct(this.low);
    },
    highPct() {
      return this.toPct(this.high);
    },
    handles() {
      return [
        { key: "low", value: this.low, pct: this.lowPct, ariaLabel: this.lowLabel },
        { key: "high", value: this.high, pct: this.highPct, ariaLabel: this.highLabel },
      ];
    },
    readout() {
      const { low, high } = this;
      if (high >= this.max && low <= this.min) return "不限";
      return `${this.fmt(low)} - ${this.fmt(high)}`;
    },
    ticks() {
      if (!this.showTicks) return [];
      const span = this.max - this.min || 1;
      const n = 5;
      return Array.from({ length: n + 1 }, (_, i) => {
        const value = this.min + (span * i) / n;
        return { value, pct: (i / n) * 100 };
      });
    },
  },
  methods: {
    fmt(v) {
      return this.formatter ? this.formatter(v) : String(v);
    },

    clamp(v) {
      const n = Number(v);
      if (!Number.isFinite(n)) return this.min;
      return Math.min(this.max, Math.max(this.min, n));
    },

    /** 吸附到 step 网格 */
    snap(v) {
      const s = this.step > 0 ? this.step : 1;
      const snapped = this.min + Math.round((v - this.min) / s) * s;
      // 浮点步长（如 0.1）会累出 0.30000000000000004，按 step 的小数位收敛
      const decimals = (String(s).split(".")[1] || "").length;
      return Number(snapped.toFixed(decimals));
    },

    toPct(v) {
      const span = this.max - this.min;
      if (span <= 0) return 0;
      return ((v - this.min) / span) * 100;
    },

    /** 指针 x → 数值 */
    valueFromClientX(clientX) {
      const rect = this.$refs.track.getBoundingClientRect();
      if (rect.width <= 0) return this.min;
      const ratio = (clientX - rect.left) / rect.width;
      return this.snap(this.min + ratio * (this.max - this.min));
    },

    emit(low, high) {
      this.$emit("update:modelValue", [low, high]);
    },

    // ---------------- 拖动 ----------------

    onThumbDown(e, key) {
      if (this.disabled) return;
      this.activeKey = key;
      const el = e.currentTarget;
      // 指针捕获：指针离开元素甚至离开窗口，事件仍然回到这里
      if (el.setPointerCapture) {
        try {
          el.setPointerCapture(e.pointerId);
        } catch (err) {
          /* 某些环境（含 jsdom）不支持，退回普通拖动 */
        }
      }
      const onMove = (ev) => this.dragTo(ev.clientX, key);
      const onUp = () => {
        el.removeEventListener("pointermove", onMove);
        el.removeEventListener("pointerup", onUp);
        el.removeEventListener("pointercancel", onUp);
        this.activeKey = null;
        this.$emit("change", [this.low, this.high]);
      };
      el.addEventListener("pointermove", onMove);
      el.addEventListener("pointerup", onUp);
      el.addEventListener("pointercancel", onUp);
    },

    /** 点在轨道上：把**最近的那个柄**挪过来，而不是固定挪某一个 */
    onTrackDown(e) {
      if (this.disabled) return;
      const v = this.valueFromClientX(e.clientX);
      const dLow = Math.abs(v - this.low);
      const dHigh = Math.abs(v - this.high);
      const key = dLow <= dHigh ? "low" : "high";
      this.activeKey = key;
      this.apply(key, v);
      const el = e.currentTarget;
      if (el.setPointerCapture) {
        try {
          el.setPointerCapture(e.pointerId);
        } catch (err) {
          /* 忽略 */
        }
      }
      const onMove = (ev) => this.dragTo(ev.clientX, key);
      const onUp = () => {
        el.removeEventListener("pointermove", onMove);
        el.removeEventListener("pointerup", onUp);
        el.removeEventListener("pointercancel", onUp);
        this.activeKey = null;
        this.$emit("change", [this.low, this.high]);
      };
      el.addEventListener("pointermove", onMove);
      el.addEventListener("pointerup", onUp);
      el.addEventListener("pointercancel", onUp);
    },

    dragTo(clientX, key) {
      this.apply(key, this.valueFromClientX(clientX));
    },

    /**
     * 写入某一个柄。两端允许相等（价格区间「只看 99 元」是合法需求），
     * 但**不允许交叉** —— 越过就顶住，不交换。
     */
    apply(key, value) {
      let low = this.low;
      let high = this.high;
      if (key === "low") {
        low = Math.min(value, high);
      } else {
        high = Math.max(value, low);
      }
      if (low === this.low && high === this.high) return;
      this.emit(low, high);
    },

    // ---------------- 键盘 ----------------

    onKeydown(e, key) {
      if (this.disabled) return;
      const s = this.step > 0 ? this.step : 1;
      const cur = key === "low" ? this.low : this.high;
      let next = null;

      switch (e.key) {
        case "ArrowLeft":
        case "ArrowDown":
          next = cur - s;
          break;
        case "ArrowRight":
        case "ArrowUp":
          next = cur + s;
          break;
        case "PageDown":
          next = cur - s * 10;
          break;
        case "PageUp":
          next = cur + s * 10;
          break;
        case "Home":
          next = this.min;
          break;
        case "End":
          next = this.max;
          break;
        default:
          return;
      }

      // 只有真的吃掉了按键才 preventDefault：
      // 无脑阻止会让 PageUp/PageDown 失去「滚动页面」的默认行为
      e.preventDefault();
      this.apply(key, this.snap(this.clamp(next)));
      this.$emit("change", [this.low, this.high]);
    },
  },
};
</script>

<style scoped>
.rng {
  /*
   * 强调色由父级通过 --rng-accent 覆盖，默认取品牌主色。
   * 商城页的主色是 #ff2442（红），其余页面是 #0050cb（蓝）——
   * 把颜色写死在组件里，放哪都会有一处不搭。
   */
  --rng-accent: var(--xh-primary);
  width: 100%;
  box-sizing: border-box;
  font-family: var(--xh-font-family);
}

.rng.is-disabled {
  opacity: 0.55;
  pointer-events: none;
}

.rng__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--xh-space-sm);
}

.rng__label {
  font-size: var(--xh-font-size-sm);
  color: var(--xh-text-secondary);
}

.rng__readout {
  font-size: var(--xh-font-size-sm);
  font-weight: var(--xh-font-semibold);
  color: var(--rng-accent);
  font-variant-numeric: tabular-nums;
}

.rng__body {
  /* 上下留白给拖柄；不然 22px 的柄会被容器裁掉一半 */
  padding: 12px 0;
}

.rng__track {
  position: relative;
  height: 4px;
  border-radius: var(--xh-radius-full);
  cursor: pointer;
  /* 触屏上允许竖向滚动手势穿过轨道，只在按住时接管 */
  touch-action: none;
}

.rng__rail {
  position: absolute;
  inset: 0;
  border-radius: var(--xh-radius-full);
  background: var(--xh-border-soft);
}

.rng__fill {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: var(--xh-radius-full);
  background: var(--rng-accent);
  transition: left var(--xh-transition-fast), width var(--xh-transition-fast);
}

.rng__ticks {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.rng__tick {
  position: absolute;
  top: 10px;
  width: 1px;
  height: 6px;
  background: var(--xh-border);
  transform: translateX(-50%);
}

.rng__thumb {
  position: absolute;
  top: 50%;
  width: 22px;
  height: 22px;
  margin-left: -11px;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  cursor: grab;
  touch-action: none;
  transition: left var(--xh-transition-fast);
}

.rng__thumb:active,
.rng__thumb.is-active {
  cursor: grabbing;
}

.rng__thumb-knob {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--xh-bg-card);
  border: 2px solid var(--rng-accent);
  box-shadow: var(--xh-shadow-sm);
  transition: transform var(--xh-transition-fast),
    box-shadow var(--xh-transition-fast);
}

.rng__thumb:hover .rng__thumb-knob,
.rng__thumb.is-active .rng__thumb-knob {
  transform: scale(1.15);
  /* 先给一条不依赖 color-mix 的兜底，再给更贴合强调色的一条 */
  box-shadow: 0 0 0 4px var(--xh-ring-track);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--rng-accent) 18%, transparent);
}

/* 焦点环画在柄上，且必须看得见 —— 键盘用户唯一的「我在哪」提示 */
.rng__thumb:focus-visible {
  outline: none;
}

.rng__thumb:focus-visible .rng__thumb-knob {
  box-shadow: 0 0 0 4px var(--xh-ring-track), var(--xh-shadow-sm);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--rng-accent) 20%, transparent),
    var(--xh-shadow-sm);
}

/* 拖动中不要有过渡，否则柄会「追」着指针走，手感发飘 */
.rng.is-dragging .rng__thumb,
.rng.is-dragging .rng__fill {
  transition: none;
}

@media (prefers-reduced-motion: reduce) {
  .rng__fill,
  .rng__thumb,
  .rng__thumb-knob {
    transition: none;
  }
}
</style>
