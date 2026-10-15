<template>
  <teleport to="body">
    <transition name="pd-fade">
      <!-- Esc 统一由 document 上的 keydown 处理；这里不再挂 @keydown.esc，
           否则同一个按键会同时经过冒泡和 document 监听，close() 触发两次 -->
      <div v-if="visible" class="pd-mask" @click.self="close">
        <transition name="pd-slide" appear>
          <aside
            ref="panel"
            class="pd"
            role="dialog"
            aria-modal="true"
            :aria-label="title || '照片'"
            tabindex="-1"
          >
            <header class="pd__head">
              <div class="pd__titles">
                <h3 class="pd__title">{{ title }}</h3>
                <span class="pd__count">{{ photos.length }} 张</span>
              </div>
              <button
                type="button"
                class="pd__close"
                aria-label="关闭照片面板"
                @click="close"
              >
                <el-icon><Close /></el-icon>
              </button>
            </header>

            <div v-if="photos.length === 0" class="pd__empty">
              <slot name="empty">还没有照片</slot>
            </div>

            <div v-else class="pd__grid">
              <button
                v-for="(p, i) in photos"
                :key="p.url + '-' + i"
                type="button"
                class="pd__cell"
                :aria-label="`查看第 ${i + 1} 张：${p.title || ''}`"
                @click="openViewer(i)"
              >
                <img
                  class="pd__thumb"
                  :src="p.thumb || p.url"
                  :alt="p.title || `照片 ${i + 1}`"
                  loading="lazy"
                />
                <span v-if="p.title" class="pd__caption">{{ p.title }}</span>
              </button>

              <slot name="extra"></slot>
            </div>

            <footer v-if="$slots.footer" class="pd__foot">
              <slot name="footer"></slot>
            </footer>

            <!-- 放大查看：覆盖在抽屉之上，先关它再关抽屉 -->
            <transition name="pd-fade">
              <div
                v-if="viewer >= 0"
                class="pd-viewer"
                @click.self="viewer = -1"
              >
                <button
                  type="button"
                  class="pd-viewer__close"
                  aria-label="关闭大图"
                  @click="viewer = -1"
                >
                  <el-icon><Close /></el-icon>
                </button>
                <button
                  v-if="photos.length > 1"
                  type="button"
                  class="pd-viewer__nav pd-viewer__nav--prev"
                  aria-label="上一张"
                  @click.stop="step(-1)"
                >
                  <el-icon><ArrowLeft /></el-icon>
                </button>
                <img
                  class="pd-viewer__img"
                  :src="photos[viewer].url"
                  :alt="photos[viewer].title || '照片'"
                />
                <button
                  v-if="photos.length > 1"
                  type="button"
                  class="pd-viewer__nav pd-viewer__nav--next"
                  aria-label="下一张"
                  @click.stop="step(1)"
                >
                  <el-icon><ArrowRight /></el-icon>
                </button>
                <div class="pd-viewer__bar">
                  <span class="pd-viewer__cap">{{
                    photos[viewer].title || ""
                  }}</span>
                  <span class="pd-viewer__idx"
                    >{{ viewer + 1 }} / {{ photos.length }}</span
                  >
                </div>
              </div>
            </transition>
          </aside>
        </transition>
      </div>
    </transition>
  </teleport>
</template>

<script>
/**
 * 照片抽屉：从右侧滑出的图片面板，点缩略图在面板内放大查看。
 *
 * <p><b>为什么用 teleport 到 body：</b>
 * 抽屉是 {@code position: fixed}，但只要祖先元素上有 {@code transform} /
 * {@code filter} / {@code perspective}（本项目的页面外壳就用到了 transform 做转场），
 * fixed 的包含块就会变成那个祖先 —— 抽屉会被裁在页面容器里、跟着页面滚。
 * teleport 到 body 是唯一稳定的解法。</p>
 *
 * <p><b>四个必须自己处理、浏览器不会替你做的事：</b>
 * <ol>
 *   <li><b>锁滚动</b>：抽屉打开时给 body 加 overflow:hidden，关闭时**还原成原来的值**
 *       （不是无脑设成空字符串 —— 打开前若本来就是 hidden，关了会被错误地放开）。</li>
 *   <li><b>焦点收回来</b>：打开时记住「是谁打开的我」，关闭时把焦点还回去，
 *       否则键盘用户关掉抽屉后焦点会掉回 body，Tab 一下从头开始。</li>
 *   <li><b>Esc 分层</b>：大图开着时 Esc 只关大图，不关抽屉。</li>
 *   <li><b>左右键</b>：大图下用方向键翻页，这是看图时最自然的操作。</li>
 * </ol>
 */
export default {
  name: "PhotoDrawer",
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      default: "照片",
    },
    /** [{ url, thumb?, title? }] */
    photos: {
      type: Array,
      default: () => [],
    },
  },
  emits: ["update:visible", "close"],
  data() {
    return {
      viewer: -1,
    };
  },
  watch: {
    visible: {
      handler(open) {
        if (open) {
          this.prevOverflow = document.body.style.overflow;
          document.body.style.overflow = "hidden";
          this.opener = document.activeElement;
          this.$nextTick(() => {
            if (this.$refs.panel) this.$refs.panel.focus();
          });
          document.addEventListener("keydown", this.onKey);
        } else {
          this.viewer = -1;
          this.unlock();
          document.removeEventListener("keydown", this.onKey);
          // 焦点还给打开它的那个元素；元素已从 DOM 摘掉时安全跳过
          if (this.opener && document.contains(this.opener)) {
            this.opener.focus();
          }
        }
      },
      immediate: true,
    },
  },
  beforeUnmount() {
    this.unlock();
    document.removeEventListener("keydown", this.onKey);
  },
  methods: {
    unlock() {
      if (this.prevOverflow !== undefined) {
        document.body.style.overflow = this.prevOverflow;
        this.prevOverflow = undefined;
      }
    },

    close() {
      this.$emit("update:visible", false);
      this.$emit("close");
    },

    openViewer(i) {
      this.viewer = i;
    },

    step(dir) {
      if (this.viewer < 0 || this.photos.length === 0) return;
      const n = this.photos.length;
      this.viewer = (this.viewer + dir + n) % n;
    },

    onKey(e) {
      if (e.key === "Escape") {
        // 分层：大图优先
        if (this.viewer >= 0) this.viewer = -1;
        else this.close();
        return;
      }
      if (this.viewer < 0) return;
      if (e.key === "ArrowLeft") {
        e.preventDefault();
        this.step(-1);
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        this.step(1);
      }
    },
  },
};
</script>

<style scoped>
.pd-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: var(--xh-bg-mask);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
  display: flex;
  justify-content: flex-end;
}

.pd {
  position: relative;
  width: min(420px, 92vw);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--xh-bg-card);
  box-shadow: -18px 0 48px rgba(15, 23, 42, 0.22);
  outline: none;
  overflow: hidden;
}

.pd__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xh-space-base);
  padding: var(--xh-space-lg);
  border-bottom: 1px solid var(--xh-border-soft);
}

.pd__titles {
  display: flex;
  align-items: baseline;
  gap: var(--xh-space-sm);
}

.pd__title {
  margin: 0;
  font-size: var(--xh-font-size-lg);
  font-weight: var(--xh-font-semibold);
  color: var(--xh-text-primary);
}

.pd__count {
  font-size: var(--xh-font-size-sm);
  color: var(--xh-text-muted);
}

.pd__close,
.pd-viewer__close {
  appearance: none;
  border: none;
  background: transparent;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: var(--xh-radius-full);
  color: var(--xh-text-secondary);
  cursor: pointer;
  transition: background var(--xh-transition-fast),
    color var(--xh-transition-fast);
}

.pd__close:hover,
.pd-viewer__close:hover {
  background: var(--xh-bg-hover);
  color: var(--xh-text-primary);
}

.pd__empty {
  padding: var(--xh-space-3xl) var(--xh-space-lg);
  text-align: center;
  color: var(--xh-text-muted);
  font-size: var(--xh-font-size-sm);
}

.pd__grid {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--xh-space-sm);
  padding: var(--xh-space-base) var(--xh-space-lg);
  align-content: start;
}

.pd__cell {
  position: relative;
  appearance: none;
  border: none;
  padding: 0;
  aspect-ratio: 1 / 1;
  border-radius: var(--xh-radius-md);
  overflow: hidden;
  cursor: pointer;
  background: var(--xh-bg-hover);
  box-shadow: var(--xh-shadow-xs);
  transition: transform var(--xh-transition-base),
    box-shadow var(--xh-transition-base);
}

.pd__cell:hover,
.pd__cell:focus-visible {
  transform: translateY(-2px) scale(1.02);
  box-shadow: var(--xh-3d-shadow-lift);
  outline: none;
}

.pd__thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.pd__caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 4px 6px;
  font-size: var(--xh-font-size-xs);
  color: #fff;
  background: linear-gradient(to top, rgba(15, 23, 42, 0.72), transparent);
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pd__foot {
  padding: var(--xh-space-base) var(--xh-space-lg);
  border-top: 1px solid var(--xh-border-soft);
}

/* ---------------- 大图 ---------------- */
.pd-viewer {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: rgba(9, 12, 20, 0.92);
  display: grid;
  place-items: center;
  padding: var(--xh-space-xl) var(--xh-space-base) 56px;
}

.pd-viewer__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: var(--xh-radius-sm);
  box-shadow: var(--xh-shadow-xl);
}

.pd-viewer__close {
  position: absolute;
  top: var(--xh-space-base);
  right: var(--xh-space-base);
  color: #fff;
  z-index: 3;
}

.pd-viewer__close:hover {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.pd-viewer__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  cursor: pointer;
  transition: background var(--xh-transition-fast);
}

.pd-viewer__nav:hover {
  background: rgba(255, 255, 255, 0.26);
}

.pd-viewer__nav--prev {
  left: var(--xh-space-base);
}

.pd-viewer__nav--next {
  right: var(--xh-space-base);
}

.pd-viewer__bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: var(--xh-space-base) var(--xh-space-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xh-space-base);
  color: rgba(255, 255, 255, 0.86);
  font-size: var(--xh-font-size-sm);
  background: linear-gradient(to top, rgba(9, 12, 20, 0.9), transparent);
}

.pd-viewer__cap {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pd-viewer__idx {
  font-variant-numeric: tabular-nums;
  flex: none;
}

/* ---------------- 转场 ---------------- */
.pd-fade-enter-active,
.pd-fade-leave-active {
  transition: opacity 0.22s ease;
}

.pd-fade-enter-from,
.pd-fade-leave-to {
  opacity: 0;
}

.pd-slide-enter-active {
  animation: xhSlideInRight var(--xh-morph-dur) var(--xh-morph-ease);
}

.pd-slide-leave-active {
  transition: transform 0.2s ease;
}

.pd-slide-leave-to {
  transform: translateX(100%);
}

@media (prefers-reduced-motion: reduce) {
  .pd-slide-enter-active {
    animation: none;
  }

  .pd__cell {
    transition: none;
  }
}
</style>
