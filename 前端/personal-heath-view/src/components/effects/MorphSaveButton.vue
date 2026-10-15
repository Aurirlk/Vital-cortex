<template>
  <button
    type="button"
    class="morph-btn"
    :class="[`is-${state}`, `is-${variant}`]"
    :disabled="disabled || state === 'loading'"
    :aria-busy="state === 'loading' ? 'true' : 'false'"
    @click="onClick"
  >
    <!-- 文字部分在形变时收拢（max-width → 0），按钮宽度是「自然缩」而不是硬切 -->
    <span class="morph-btn__label">
      <slot>{{ state === "success" ? successLabel : label }}</slot>
    </span>

    <span v-if="state !== 'idle'" class="morph-btn__stage">
      <svg class="morph-btn__svg" viewBox="0 0 44 44" aria-hidden="true">
        <!-- 轨道 -->
        <circle
          class="morph-ring__track"
          cx="22"
          cy="22"
          :r="R"
          fill="none"
          stroke-width="2.5"
        />
        <!--
          进度弧。
          determinate（传了 progress）：dasharray 按百分比收，弧长即进度。
          indeterminate（没传）：只画 1/4 圈并整体旋转 —— 不知道进度时不假装知道。
        -->
        <circle
          class="morph-ring__arc"
          cx="22"
          cy="22"
          :r="R"
          fill="none"
          stroke-width="2.5"
          stroke-linecap="round"
          :stroke-dasharray="arcDash"
          :stroke-dashoffset="arcOffset"
          :style="spinStyle"
        />
        <!-- 成功勾 / 失败叉，都用 pathLength 归一化后描边 -->
        <path
          v-if="state === 'success'"
          class="morph-btn__mark morph-btn__mark--ok"
          :d="CHECK_PATH"
          pathLength="1"
          fill="none"
          stroke-width="3"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          v-if="state === 'error'"
          class="morph-btn__mark morph-btn__mark--err"
          :d="CROSS_PATH"
          pathLength="1"
          fill="none"
          stroke-width="3"
          stroke-linecap="round"
        />
      </svg>
    </span>

    <!-- 屏幕阅读器只需要听结果，不需要听「正在转圈」 -->
    <span class="morph-btn__sr" role="status" aria-live="polite">{{
      srStatus
    }}</span>
  </button>
</template>

<script>
/**
 * 会形变的保存按钮：点击后按钮收成圆形 + 进度环，成功后环内浮出对勾，再变回按钮。
 *
 * <p><b>为什么需要一个专门的组件、而不直接用 {@code el-button :loading}：</b>
 * el-button 的 loading 只是把文字换成一个转圈图标，按钮**形状不变**。
 * 「形变」的要点在于按钮本体的几何在变（矩形 → 圆 → 矩形），
 * 这是一段要自己控制的过渡，Element Plus 没有对应能力。</p>
 *
 * <p><b>三个刻意的设计决定：</b></p>
 * <ol>
 *   <li><b>宽度靠「收拢文字」自然缩，不写死 width。</b>
 *       {@code width: auto → 44px} 是**不能过渡**的（auto 无法插值），
 *       硬写就成了瞬间跳变。这里让 label 的 {@code max-width} 从 120px 收到 0、
 *       padding 收到 0，盒子自然缩成 44px 的圆 —— 全程可插值。</li>
 *   <li><b>未知进度就画未知的环。</b>
 *       没传 {@code progress} 时渲染一段 1/4 圈的弧并整体旋转，
 *       而不是假装画一条「走到 80% 停住」的假进度。
 *       传了 {@code progress} 才画确定弧。</li>
 *   <li><b>{@code minMs}：操作太快也要让环转满。</b>
 *       本地保存可能 30ms 就回来了，环一闪而过，
 *       用户看到的是「按了没反应」。默认至少转 320ms。</li>
 * </ol>
 *
 * <p><b>用法：</b></p>
 * <pre>
 * &lt;MorphSaveButton ref="save" label="保存" @click="saveProfile" /&gt;
 *
 * async saveProfile () {
 *   this.$refs.save.begin()
 *   try { await api(); this.$refs.save.succeed() }
 *   catch (e) { this.$refs.save.fail() }
 * }
 * </pre>
 */
export default {
  name: "MorphSaveButton",
  props: {
    label: {
      type: String,
      default: "保存",
    },
    successLabel: {
      type: String,
      default: "已保存",
    },
    /** 'primary' | 'ghost' | 'danger' */
    variant: {
      type: String,
      default: "primary",
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    /** 0..100；null = 进度未知，画不确定弧 */
    progress: {
      type: Number,
      default: null,
    },
    /** 成功后自动变回按钮的延时（ms）；0 = 不自动回退，由父组件 reset() */
    autoRevert: {
      type: Number,
      default: 1600,
    },
    /** 形变后的停留时长下限（ms），防止环一闪而过 */
    minMs: {
      type: Number,
      default: 320,
    },
  },
  data() {
    return {
      state: "idle",
      R: 18,
      CHECK_PATH: "M14 22.5 L19.5 28 L30 16",
      CROSS_PATH: "M16 16 L28 28 M28 16 L16 28",
      begunAt: 0,
      timers: [],
    };
  },
  computed: {
    circumference() {
      return 2 * Math.PI * this.R;
    },
    /** 确定进度：弧长按百分比；不确定：固定 1/4 圈 */
    arcDash() {
      const c = this.circumference;
      if (this.progress === null || this.progress === undefined) {
        return `${(c / 4).toFixed(2)} ${(c * 3) / 4}`;
      }
      const p = Math.max(0, Math.min(100, this.progress)) / 100;
      return `${(c * p).toFixed(2)} ${c}`;
    },
    arcOffset() {
      if (this.progress === null || this.progress === undefined) return 0;
      // 确定弧从 12 点方向顺时针长出来
      return -this.circumference / 4;
    },
    spinStyle() {
      return this.progress === null || this.progress === undefined
        ? { transformOrigin: "22px 22px", animation: "xhSpin 0.9s linear infinite" }
        : {};
    },
    srStatus() {
      if (this.state === "success") return this.successLabel;
      if (this.state === "error") return "保存失败";
      return "";
    },
  },
  beforeUnmount() {
    this.clearTimers();
  },
  methods: {
    // ---------------- 对外状态机 ----------------

    begin() {
      this.clearTimers();
      this.begunAt = Date.now();
      this.state = "loading";
      this.$emit("state-change", "loading");
    },

    succeed() {
      this.finish("success");
    },

    fail() {
      this.finish("error");
    },

    reset() {
      this.clearTimers();
      this.state = "idle";
      this.$emit("state-change", "idle");
    },

    /**
     * 收敛到终态。若本次形变还没转满 minMs，先把剩余时间补上 ——
     * 否则「接口太快」会让整个动效看起来像没生效。
     */
    finish(next) {
      const elapsed = Date.now() - this.begunAt;
      const wait = Math.max(0, this.minMs - elapsed);
      this.clearTimers();
      this.timer(
        () => {
          this.state = next;
          this.$emit("state-change", next);
          this.$emit(next === "success" ? "success" : "error");
          if (this.autoRevert > 0) {
            this.timer(() => this.reset(), this.autoRevert);
          }
        },
        wait
      );
    },

    timer(fn, ms) {
      this.timers.push(window.setTimeout(fn, ms));
    },

    clearTimers() {
      this.timers.forEach((t) => window.clearTimeout(t));
      this.timers = [];
    },

    onClick(e) {
      // 形变中不接受重复点击：第二次点击会让两段动画叠在一起
      if (this.state === "loading" || this.disabled) return;
      this.$emit("click", e);
    },
  },
};
</script>

<style scoped>
.morph-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  /* 本项目没有全局 box-sizing 重置，这里必须自己声明：
     否则 width:44px 只是内容宽，加上 padding 后圆形会变成椭圆 */
  box-sizing: border-box;
  height: 44px;
  padding: 0 var(--xh-space-lg);
  border: none;
  border-radius: var(--xh-radius-full);
  font-family: inherit;
  font-size: var(--xh-font-size-base);
  font-weight: var(--xh-font-semibold);
  cursor: pointer;
  overflow: hidden;
  /* 形变的三条属性一起走同一条缓动，形状才不会「分家」 */
  transition: padding var(--xh-morph-dur) var(--xh-morph-ease),
    background var(--xh-transition-base),
    box-shadow var(--xh-transition-base),
    transform var(--xh-transition-fast);
}

.morph-btn:focus-visible {
  outline: 2px solid var(--xh-primary);
  outline-offset: 3px;
}

.morph-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.morph-btn:disabled {
  cursor: default;
}

/* ---- 分色 ---- */
.morph-btn.is-primary {
  background: var(--xh-primary-gradient);
  color: var(--xh-text-white);
  box-shadow: var(--xh-glow-primary);
}

.morph-btn.is-ghost {
  background: transparent;
  color: var(--xh-primary);
  box-shadow: inset 0 0 0 1px var(--xh-border-dark);
}

.morph-btn.is-danger {
  background: var(--xh-error);
  color: var(--xh-text-white);
}

/* ---- 文字收拢：这才是形状变化的来源 ---- */
.morph-btn__label {
  max-width: 140px;
  overflow: hidden;
  white-space: nowrap;
  transition: max-width var(--xh-morph-dur) var(--xh-morph-ease),
    opacity 0.26s ease;
}

/* ---- 形变中 / 已结束：收成圆 ---- */
.morph-btn.is-loading,
.morph-btn.is-success,
.morph-btn.is-error {
  padding: 0;
}

.morph-btn.is-loading .morph-btn__label,
.morph-btn.is-success .morph-btn__label,
.morph-btn.is-error .morph-btn__label {
  max-width: 0;
  opacity: 0;
}

/* 圆的外径 = 高度 44px；border-radius 同步切换让矩形→圆是连续的 */
.morph-btn.is-loading,
.morph-btn.is-success,
.morph-btn.is-error {
  border-radius: 50%;
  width: 44px;
  min-width: 44px;
}

.morph-btn.is-success {
  background: var(--xh-success);
  box-shadow: 0 0 0 1px rgba(16, 185, 129, 0.2),
    0 8px 30px rgba(16, 185, 129, 0.25);
}

.morph-btn.is-error {
  background: var(--xh-error);
  box-shadow: 0 0 0 1px rgba(239, 68, 68, 0.2),
    0 8px 30px rgba(239, 68, 68, 0.25);
}

/* ---- 环 ---- */
.morph-btn__stage {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  pointer-events: none;
}

.morph-btn__svg {
  width: 44px;
  height: 44px;
  display: block;
}

.morph-ring__track {
  stroke: rgba(255, 255, 255, 0.28);
}

.morph-btn.is-ghost .morph-ring__track {
  stroke: var(--xh-ring-track);
}

.morph-ring__arc {
  stroke: #fff;
  transition: stroke-dasharray 0.3s ease;
}

.morph-btn.is-ghost .morph-ring__arc {
  stroke: var(--xh-primary);
}

/* ---- 勾与叉：用 dashoffset 描出来，而不是直接显示 ---- */
.morph-btn__mark {
  stroke: #fff;
  /* pathLength="1" 把路径长度归一化，于是这两个数字与图形实际尺寸无关 */
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: markDraw 0.32s var(--xh-morph-ease) forwards;
}

.morph-btn.is-ghost .morph-btn__mark {
  stroke: var(--xh-primary);
}

@keyframes markDraw {
  to {
    stroke-dashoffset: 0;
  }
}

.morph-btn__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

@media (prefers-reduced-motion: reduce) {
  .morph-btn__label,
  .morph-btn {
    transition: none;
  }

  .morph-btn__mark {
    animation: none;
    stroke-dashoffset: 0;
  }
}
</style>
