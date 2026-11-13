<template>
  <div class="tbp" :class="{ 'is-disabled': disabled }">
    <div class="tbp__head">
      <span class="tbp__title">{{ label }}</span>
      <span class="tbp__hint">{{ hintText }}</span>
    </div>

    <div class="tbp__body">
      <!-- 时间轴刻度 -->
      <div class="tbp__axis" aria-hidden="true">
        <span
          v-for="h in hours"
          :key="h.min"
          class="tbp__hour"
          :style="{ top: h.pct + '%' }"
        >
          {{ h.label }}
        </span>
      </div>

      <div ref="track" class="tbp__track" @pointerdown="onTrackDown">
        <!-- 整点参考线 -->
        <div class="tbp__grid" aria-hidden="true">
          <span
            v-for="h in hours"
            :key="h.min"
            class="tbp__line"
            :style="{ top: h.pct + '%' }"
          ></span>
        </div>

        <div
          v-for="(b, i) in blocks"
          :key="b.key"
          class="tbp__block"
          :class="{ 'is-active': i === activeIndex }"
          :style="blockStyle(b)"
          role="button"
          tabindex="0"
          :aria-label="blockAria(b)"
          @pointerdown.stop="onBlockDown($event, i)"
          @keydown="onBlockKey($event, i)"
        >
          <span class="tbp__grip tbp__grip--start" data-grip="start"></span>
          <span class="tbp__label">{{ blockText(b) }}</span>
          <span class="tbp__grip tbp__grip--end" data-grip="end"></span>
        </div>

        <!--
          绘制中的草稿。它**只存在于组件本地**，抬手才写进 modelValue ——
          「按下就提交」会让父组件在拖动过程中收到一串半成品区间，
          如果父组件据此发起查询，一次拖拽会打出几十个请求。
        -->
        <div
          v-if="draft"
          class="tbp__block tbp__block--draft"
          :style="blockStyle(draft)"
        >
          <span class="tbp__label">{{ blockText(draft) }}</span>
        </div>

        <div v-if="blocks.length === 0 && !disabled" class="tbp__empty">
          {{ emptyHint }}
        </div>
      </div>
    </div>

    <div class="tbp__foot">
      <span class="tbp__sum">{{ summary }}</span>
      <button
        type="button"
        class="tbp__clear"
        :disabled="disabled || blocks.length === 0"
        @click="clearAll"
      >
        清空
      </button>
    </div>
  </div>
</template>

<script>
/**
 * 可拖拽时间块（日程条）。
 *
 * <p>三条交互：<b>在空白处拖着画出</b>一个时间块、<b>拖块身平移</b>、<b>拖两端改长短</b>。
 * 值以「距当天 0 点的分钟数」表示，与具体日期解耦 —— 组件本身不关心是哪一天。</p>
 *
 * <p><b>为什么值得单独做一个组件：</b>
 * 「我要 14:00 到 17:00 有空」这种区间，用两个下拉或一个单值选择器都表达不了，
 * 用时间去选又会强迫用户按两次、还得自己保证先后顺序。拖是唯一直接的表达方式。</p>
 *
 * <p><b>三个关键实现决定：</b></p>
 * <ol>
 *   <li><b>拖拽用 Pointer Events + 指针捕获</b>，一套代码同时覆盖鼠标、触屏、手写笔；
 *       {@code mousedown/mousemove} 在触屏上根本不触发，分开写两套必然有一处不同步。</li>
 *   <li><b>重叠靠「顶住邻居」而不是「拒绝」</b>。拖动越界时把块贴到邻居边上，
 *       手感是连续的；直接拒绝会让块突然弹回，像卡了一下。</li>
 *   <li><b>拖动过程中以「按下那一刻的快照 + 累计位移」计算</b>，
 *       而不是逐帧累加。逐帧累加到边界被钳制后，位移就丢了 ——
 *       表现为「拖出去再拖回来，块回不到原位」。</li>
 * </ol>
 */
export default {
  name: "TimeBlockPlanner",
  props: {
    /** [{ start, end }]，单位：距 0 点的分钟数 */
    modelValue: {
      type: Array,
      default: () => [],
    },
    label: {
      type: String,
      default: "可用时间段",
    },
    /** 日程起点（分钟），默认 8:00 */
    dayStart: {
      type: Number,
      default: 8 * 60,
    },
    /** 日程终点（分钟），默认 20:00 */
    dayEnd: {
      type: Number,
      default: 20 * 60,
    },
    /** 吸附粒度（分钟） */
    step: {
      type: Number,
      default: 15,
    },
    /** 单个块最短时长（分钟） */
    minSpan: {
      type: Number,
      default: 30,
    },
    maxBlocks: {
      type: Number,
      default: 4,
    },
    emptyHint: {
      type: String,
      default: "在时间条上按住拖动，画出你有空的时间段",
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    /** 分钟 → 展示文案；默认 HH:mm */
    formatter: {
      type: Function,
      default: null,
    },
  },
  emits: ["update:modelValue", "change"],
  data() {
    return {
      activeIndex: -1,
      /* 拖动会话：按下那一刻的现场，之后所有计算都基于它 + 累计位移 */
      session: null,
      /* 正在画出来的新块；抬手才写进 modelValue */
      draft: null,
    };
  },
  computed: {
    /** 带稳定 key 的块。用内容生成 key 而非下标 —— 下标会在删除后错位，导致过渡错乱 */
    blocks() {
      return (this.modelValue || [])
        .map((b, i) => ({
          ...b,
          key: `b-${b.start}-${b.end}-${i}`,
        }))
        .sort((a, b) => a.start - b.start);
    },
    span() {
      return Math.max(1, this.dayEnd - this.dayStart);
    },
    hours() {
      const out = [];
      const from = Math.ceil(this.dayStart / 60) * 60;
      for (let m = from; m <= this.dayEnd; m += 60) {
        out.push({
          min: m,
          pct: this.toPct(m),
          label: this.fmt(m),
        });
      }
      return out;
    },
    summary() {
      if (!this.blocks.length) return "未设置（视为全天可约）";
      return this.blocks.map((b) => this.blockText(b)).join(" · ");
    },
    hintText() {
      if (this.disabled) return "";
      return `最多 ${this.maxBlocks} 段 · 吸附 ${this.step} 分钟`;
    },
  },
  methods: {
    // ---------------- 坐标换算 ----------------

    toPct(min) {
      return ((min - this.dayStart) / this.span) * 100;
    },

    /** 指针 y → 分钟，并对齐到 step 网格 */
    minFromClientY(clientY) {
      const rect = this.$refs.track.getBoundingClientRect();
      if (rect.height <= 0) return this.dayStart;
      const ratio = (clientY - rect.top) / rect.height;
      const raw = this.dayStart + ratio * this.span;
      return this.snap(raw);
    },

    snap(min) {
      const s = this.step > 0 ? this.step : 1;
      const v = this.dayStart + Math.round((min - this.dayStart) / s) * s;
      return Math.min(this.dayEnd, Math.max(this.dayStart, v));
    },

    fmt(min) {
      if (this.formatter) return this.formatter(min);
      const h = Math.floor(min / 60);
      const m = min % 60;
      return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
    },

    blockText(b) {
      return `${this.fmt(b.start)} – ${this.fmt(b.end)}`;
    },

    blockAria(b) {
      return `时间段 ${this.blockText(b)}，${Math.round(
        (b.end - b.start) / 60 * 10
      ) / 10} 小时`;
    },

    blockStyle(b) {
      return {
        top: this.toPct(b.start) + "%",
        height: (this.toPct(b.end) - this.toPct(b.start)) + "%",
      };
    },

    emit(list) {
      // 始终按时间升序输出：下游（过滤、提交）不该依赖「调用方碰巧拖的顺序」
      const sorted = list
        .slice()
        .sort((a, b) => a.start - b.start)
        .map(({ start, end }) => ({ start, end }));
      this.$emit("update:modelValue", sorted);
      this.$emit("change", sorted);
    },

    // ---------------- 交互：新建 ----------------

    onTrackDown(e) {
      if (this.disabled) return;
      // 点在已存在的块上时，由块自己的 handler 处理，这里只处理空白
      if (e.target.closest && e.target.closest(".tbp__block")) return;
      if (this.blocks.length >= this.maxBlocks) return;

      const anchor = this.minFromClientY(e.clientY);
      const el = e.currentTarget || this.$refs.track;
      this.draft = { start: anchor, end: anchor };

      if (el.setPointerCapture) {
        try {
          el.setPointerCapture(e.pointerId);
        } catch (err) {
          /* 环境不支持时退回普通拖动 */
        }
      }

      const move = (ev) => {
        const cur = this.minFromClientY(ev.clientY);
        // 允许上下任意方向画：区间永远归一化成 start <= end
        this.draft = {
          start: Math.min(anchor, cur),
          end: Math.max(anchor, cur),
        };
      };
      const up = () => {
        el.removeEventListener("pointermove", move);
        el.removeEventListener("pointerup", up);
        el.removeEventListener("pointercancel", up);
        if (el.releasePointerCapture) {
          try {
            el.releasePointerCapture(e.pointerId);
          } catch (err) {
            /* 忽略 */
          }
        }
        this.commitDraft();
      };
      el.addEventListener("pointermove", move);
      el.addEventListener("pointerup", up);
      el.addEventListener("pointercancel", up);
    },

    /**
     * 抬手提交草稿。
     * 只点一下没拖（或拖出来短于 minSpan）→ 直接丢弃，
     * 否则会留下一个高度为 0、看不见又删不掉的块。
     */
    commitDraft() {
      const d = this.draft;
      this.draft = null;
      if (!d || d.end - d.start < this.minSpan) return;

      const list = this.blocks.map((b) => ({ start: b.start, end: b.end }));
      // 与已有块重叠则不新建：静默丢弃比创建一个重叠块更好，
      // 重叠块在后续的过滤逻辑里会产出「同一时段算两次」这种诡异结果
      const overlaps = list.some((b) => d.start < b.end && d.end > b.start);
      if (overlaps) return;

      list.push({ start: d.start, end: d.end });
      this.activeIndex = list.length - 1;
      this.emit(list);
    },

    // ---------------- 交互：移动 / 缩放 ----------------

    onBlockDown(e, index) {
      if (this.disabled) return;
      this.activeIndex = index;
      const blocks = this.blocks.map((b) => ({ start: b.start, end: b.end }));
      const b = blocks[index];
      const grip = e.target && e.target.dataset ? e.target.dataset.grip : null;
      const mode = grip === "start" ? "resize-start" : grip === "end" ? "resize-end" : "move";

      // 快照 + 起点：后面全部用「快照 + 累计位移」重算，不做逐帧累加
      this.session = {
        mode,
        index,
        blocks,
        originStart: b.start,
        originEnd: b.end,
        anchorY: e.clientY,
      };

      const el = e.currentTarget;
      const move = (ev) => this.applySession(ev.clientY);
      const up = () => {
        el.removeEventListener("pointermove", move);
        el.removeEventListener("pointerup", up);
        el.removeEventListener("pointercancel", up);
        if (el.releasePointerCapture) {
          try {
            el.releasePointerCapture(e.pointerId);
          } catch (err) {
            /* 忽略 */
          }
        }
        this.session = null;
        this.emit(this.blocks.map((x) => ({ start: x.start, end: x.end })));
      };
      if (el.setPointerCapture) {
        try {
          el.setPointerCapture(e.pointerId);
        } catch (err) {
          /* 忽略 */
        }
      }
      el.addEventListener("pointermove", move);
      el.addEventListener("pointerup", up);
      el.addEventListener("pointercancel", up);
    },

    applySession(clientY) {
      const s = this.session;
      if (!s) return;

      // 指针位移 → 分钟位移
      const rect = this.$refs.track.getBoundingClientRect();
      if (rect.height <= 0) return;
      const deltaMin = this.snap(
        ((clientY - s.anchorY) / rect.height) * this.span
      ) - this.dayStart;

      const list = s.blocks.map((b) => ({ start: b.start, end: b.end }));
      const self = list[s.index];

      if (s.mode === "move") {
        const dur = s.originEnd - s.originStart;
        let start = this.snap(s.originStart + deltaMin);
        let end = start + dur;
        // 撞到日程边界就整体贴边，保持时长不变
        if (start < this.dayStart) {
          start = this.dayStart;
          end = start + dur;
        }
        if (end > this.dayEnd) {
          end = this.dayEnd;
          start = end - dur;
        }
        // 撞到邻居：贴住但不重叠
        for (let i = 0; i < list.length; i++) {
          if (i === s.index) continue;
          const o = list[i];
          if (start < o.end && end > o.start) {
            if (s.originStart < o.start) {
              end = o.start;
              start = end - dur;
            } else {
              start = o.end;
              end = start + dur;
            }
          }
        }
        self.start = this.snap(Math.max(this.dayStart, start));
        self.end = this.snap(Math.min(this.dayEnd, end));
      } else if (s.mode === "resize-start") {
        let start = this.snap(Math.min(s.originStart + deltaMin, s.originEnd - this.minSpan));
        start = Math.max(this.dayStart, start);
        for (let i = 0; i < list.length; i++) {
          if (i === s.index) continue;
          const o = list[i];
          if (o.end <= s.originStart && start < o.end) start = o.end;
        }
        self.start = this.snap(start);
        self.end = s.originEnd;
      } else {
        let end = this.snap(Math.max(s.originEnd + deltaMin, s.originStart + this.minSpan));
        end = Math.min(this.dayEnd, end);
        for (let i = 0; i < list.length; i++) {
          if (i === s.index) continue;
          const o = list[i];
          if (o.start >= s.originEnd && end > o.start) end = o.start;
        }
        self.end = this.snap(end);
        self.start = s.originStart;
      }

      this.emit(list);
    },

    // ---------------- 键盘 ----------------

    onBlockKey(e, index) {
      if (this.disabled) return;
      const s = this.step > 0 ? this.step : 1;
      const dir =
        e.key === "ArrowUp" ? -1 : e.key === "ArrowDown" ? 1 : 0;

      if (e.key === "Delete" || e.key === "Backspace") {
        e.preventDefault();
        const list = this.blocks
          .filter((_, i) => i !== index)
          .map((x) => ({ start: x.start, end: x.end }));
        this.activeIndex = -1;
        this.emit(list);
        return;
      }

      // ← / → 也在平移时可用：竖排时间条上方向键是上下，
      // 但很多人会条件反射按左右，两个都接住成本很低
      if (!dir && e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
      e.preventDefault();

      const list = this.blocks.map((x) => ({ start: x.start, end: x.end }));
      const self = list[index];
      const grow = e.shiftKey; // Shift + 方向键 = 改长短

      if (grow) {
        const end = this.clampToNeighbor(index, self.start, self.end + dir * s, list);
        self.end = end;
      } else {
        const dur = self.end - self.start;
        let start = self.start + dir * s;
        if (start < this.dayStart) start = this.dayStart;
        if (start + dur > this.dayEnd) start = this.dayEnd - dur;
        // 平移也要避让邻居：往哪个方向走就贴哪边的边
        for (let i = 0; i < list.length; i++) {
          if (i === index) continue;
          const o = list[i];
          if (start < o.end && start + dur > o.start) {
            start = dir < 0 ? o.start - dur : o.end;
          }
        }
        start = Math.max(this.dayStart, Math.min(this.dayEnd - dur, start));
        self.start = start;
        self.end = start + dur;
      }
      this.emit(list);
    },

    clampToNeighbor(index, start, end, list) {
      let out = Math.max(start + this.minSpan, Math.min(this.dayEnd, this.snap(end)));
      for (let i = 0; i < list.length; i++) {
        if (i === index) continue;
        const o = list[i];
        if (o.start >= start && out > o.start) out = o.start;
      }
      return out;
    },

    clearAll() {
      this.activeIndex = -1;
      this.emit([]);
    },
  },
};
</script>

<style scoped>
.tbp {
  --tbp-axis: 44px;
  box-sizing: border-box;
  font-family: var(--xh-font-family);
  background: var(--xh-bg-card);
  border-radius: var(--xh-radius-lg);
  padding: var(--xh-space-base);
  box-shadow: var(--xh-3d-shadow-rest);
}

.tbp.is-disabled {
  opacity: 0.6;
  pointer-events: none;
}

.tbp__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--xh-space-sm);
  margin-bottom: var(--xh-space-sm);
}

.tbp__title {
  font-size: var(--xh-font-size-base);
  font-weight: var(--xh-font-semibold);
  color: var(--xh-text-primary);
}

.tbp__hint {
  font-size: var(--xh-font-size-xs);
  color: var(--xh-text-muted);
}

.tbp__body {
  display: flex;
  gap: var(--xh-space-sm);
}

.tbp__axis {
  position: relative;
  width: var(--tbp-axis);
  /* 与轨道等高：用同一个高度变量保证刻度与块严格对齐 */
  height: 260px;
  flex: none;
}

.tbp__hour {
  position: absolute;
  right: 0;
  transform: translateY(-50%);
  font-size: var(--xh-font-size-xs);
  color: var(--xh-text-muted);
  font-variant-numeric: tabular-nums;
}

.tbp__track {
  position: relative;
  flex: 1;
  height: 260px;
  border-radius: var(--xh-radius-md);
  background: var(--xh-bg-hover);
  border: 1px solid var(--xh-border-soft);
  cursor: crosshair;
  overflow: hidden;
  touch-action: none;
}

.tbp__grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.tbp__line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--xh-border-soft);
}

.tbp__block {
  position: absolute;
  left: 6px;
  right: 6px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--xh-radius-sm);
  background: var(--xh-primary-gradient);
  color: #fff;
  font-size: var(--xh-font-size-xs);
  font-weight: var(--xh-font-semibold);
  cursor: grab;
  user-select: none;
  touch-action: none;
  box-shadow: 0 2px 8px rgba(0, 80, 203, 0.28);
  transition: box-shadow var(--xh-transition-fast);
}

.tbp__block:active {
  cursor: grabbing;
}

.tbp__block.is-active,
.tbp__block:focus-visible {
  outline: none;
  box-shadow: var(--xh-glow-primary), 0 6px 18px rgba(0, 80, 203, 0.35);
}

.tbp__label {
  pointer-events: none;
  padding: 0 8px;
  white-space: nowrap;
}

/* 草稿：虚线描边 + 半透明，与已提交的块在视觉上分得开 */
.tbp__block--draft {
  background: var(--xh-primary-bg);
  color: var(--xh-primary);
  border: 1px dashed var(--xh-primary);
  box-shadow: none;
  pointer-events: none;
}

/* 两端的抓手。视觉上只有 6px，但可拖区域给足 12px ——
   太窄的缩放手柄在触屏上根本按不准 */
.tbp__grip {
  position: absolute;
  left: 0;
  right: 0;
  height: 12px;
  cursor: ns-resize;
}

.tbp__grip--start {
  top: -2px;
}

.tbp__grip--end {
  bottom: -2px;
}

.tbp__grip::after {
  content: "";
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 26px;
  height: 3px;
  border-radius: var(--xh-radius-full);
  background: rgba(255, 255, 255, 0.85);
  opacity: 0;
  transition: opacity var(--xh-transition-fast);
}

.tbp__block:hover .tbp__grip::after,
.tbp__block.is-active .tbp__grip::after {
  opacity: 1;
}

.tbp__grip--start::after {
  top: 3px;
}

.tbp__grip--end::after {
  bottom: 3px;
}

.tbp__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 0 24px;
  text-align: center;
  font-size: var(--xh-font-size-xs);
  color: var(--xh-text-muted);
  pointer-events: none;
}

.tbp__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xh-space-sm);
  margin-top: var(--xh-space-sm);
}

.tbp__sum {
  font-size: var(--xh-font-size-xs);
  color: var(--xh-text-secondary);
}

.tbp__clear {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--xh-primary);
  font-size: var(--xh-font-size-xs);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--xh-radius-sm);
  transition: background var(--xh-transition-fast);
}

.tbp__clear:hover:not(:disabled) {
  background: var(--xh-primary-bg);
}

.tbp__clear:disabled {
  color: var(--xh-text-muted);
  cursor: default;
}

@media (max-width: 640px) {
  .tbp__body {
    flex-direction: column;
  }

  .tbp__axis {
    width: 100%;
    height: 20px;
  }

  .tbp__hour {
    top: auto !important;
    right: auto;
    transform: none;
    padding-left: 2px;
  }
}
</style>
