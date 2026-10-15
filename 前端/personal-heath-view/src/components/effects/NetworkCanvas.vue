<template>
  <div ref="wrap" class="net-wrap" :style="{ height: height }">
    <canvas
      ref="canvas"
      class="net-canvas"
      :aria-label="ariaLabel"
      role="img"
    ></canvas>

    <!-- 悬浮在画布之上的标签：只显示当前高亮的那一个，避免 30 个 label 互相压字 -->
    <transition name="net-tip">
      <div
        v-if="hovered"
        class="net-tip"
        :style="{ left: hovered.sx + 'px', top: hovered.sy + 'px' }"
      >
        <span class="net-tip__name">{{ hovered.label }}</span>
        <span class="net-tip__meta">{{ hovered.links }} 条关联</span>
      </div>
    </transition>

    <div v-if="!supported" class="net-fallback">
      <slot name="fallback">知识网络图（当前环境不支持 Canvas）</slot>
    </div>
  </div>
</template>

<script>
/**
 * 3D 知识网络图。
 *
 * 技术选择：**手写透视投影的 2D canvas**，而不是 three.js / WebGL。
 * 理由是成本与收益：本项目只需要「一堆球按深度错落旋转 + 连线」，
 * 引入 three.js 会带来 ~600KB 依赖、一套场景图与材质系统，
 * 以及「打包产物里多一个只在首页出现的大 chunk」。手写投影是 40 行数学，
 * 且完全可控 —— 出问题时是一段能读的代码，不是一个黑盒。
 *
 * 投影公式（标准针孔相机，相机在 +z 看向原点）：
 *     s = focal / (focal + z)        // 越远越小
 *     sx = cx + x * s * spread       // 屏幕坐标
 *     sy = cy + y * s * spread
 * 深度排序后先画线再画点，远处的线更淡 —— 这是「立体感」的主要来源，
 * 比任何描边或高光都有效。
 *
 * 几个必须做对的细节：
 *  1. **DPR**：canvas 的 width/height 要乘 devicePixelRatio，CSS 尺寸保持 100%，
 *     否则在 Retina 上整张图是糊的（这是 canvas 组件最常见的缺陷）。
 *  2. **暂停**：标签页隐藏或组件滚出视口时必须停掉 rAF。不停就是一个
 *     看不见的页面在后台持续吃 CPU 与电量。
 *  3. **减少动效**：系统开启 prefers-reduced-motion 时只渲染一帧静态图。
 *     这不是锦上添花，持续旋转的 3D 图对前庭敏感人群是真实的不适来源。
 *  4. **销毁**：rAF、ResizeObserver、事件监听全部要在 beforeUnmount 里解绑。
 */
export default {
  name: "NetworkCanvas",
  props: {
    /** 节点标签。空数组时用内置的健康主题词表，保证组件单独挂上去就有内容 */
    labels: {
      type: Array,
      default: () => [],
    },
    height: {
      type: String,
      default: "320px",
    },
    /** 节点数上限；传进来的标签多于它时截断 */
    count: {
      type: Number,
      default: 26,
    },
    /** 每个节点连出去几条边 */
    degree: {
      type: Number,
      default: 3,
    },
    /** 自转速度（弧度/帧）。0 = 静止 */
    speed: {
      type: Number,
      default: 0.0022,
    },
    /** 鼠标视差强度，0 = 关闭 */
    parallax: {
      type: Number,
      default: 0.35,
    },
    ariaLabel: {
      type: String,
      default: "健康知识网络图：科室、症状与知识库条目之间的关联",
    },
  },
  data() {
    return {
      supported: true,
      hovered: null,
    };
  },
  computed: {
    /** 最终参与渲染的词表 */
    vocab() {
      const base =
        this.labels && this.labels.length
          ? this.labels
          : [
              "心血管内科",
              "血糖管理",
              "营养膳食",
              "睡眠质量",
              "运动处方",
              "心理健康",
              "血压监测",
              "用药提醒",
              "孕产保健",
              "儿童发育",
              "慢病随访",
              "体检解读",
              "康复训练",
              "中医调理",
              "疫苗接种",
              "过敏管理",
              "体重控制",
              "戒烟干预",
              "视光健康",
              "口腔护理",
              "骨密度",
              "肝肾功能",
              "甲状腺",
              "呼吸系统",
            ];
      return base.slice(0, Math.max(4, this.count));
    },
  },
  mounted() {
    const canvas = this.$refs.canvas;
    if (!canvas || !canvas.getContext) {
      this.supported = false;
      return;
    }
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    if (!this.ctx) {
      this.supported = false;
      return;
    }

    this.buildScene();

    // 静态模式：只画一帧，不启动 rAF
    this.reducedMotion =
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    this.resize();
    this.frame = 0;

    if (this.reducedMotion) {
      this.draw();
    } else {
      this.start();
    }

    // 尺寸跟随容器：用 ResizeObserver 而不是 window.resize ——
    // 侧边栏折叠、父容器 flex 重排都不会触发 window.resize，图会一直错位。
    if (typeof ResizeObserver !== "undefined") {
      this.ro = new ResizeObserver(() => this.resize());
      this.ro.observe(this.$refs.wrap);
    } else {
      this.onWinResize = () => this.resize();
      window.addEventListener("resize", this.onWinResize);
    }

    this.onVisibility = () => {
      if (document.hidden) this.stop();
      else if (!this.reducedMotion) this.start();
    };
    document.addEventListener("visibilitychange", this.onVisibility);

    // 滚出视口就停：首页往下滚之后这张图不该继续燃烧 CPU
    if (typeof IntersectionObserver !== "undefined") {
      this.io = new IntersectionObserver(
        (entries) => {
          const visible = entries.some((e) => e.isIntersecting);
          if (this.reducedMotion) return;
          if (visible && !document.hidden) this.start();
          else this.stop();
        },
        { threshold: 0.01 }
      );
      this.io.observe(this.$refs.wrap);
    }

    canvas.addEventListener("mousemove", this.onMove);
    canvas.addEventListener("mouseleave", this.onLeave);
  },
  beforeUnmount() {
    this.stop();
    if (this.ro) this.ro.disconnect();
    if (this.io) this.io.disconnect();
    if (this.onWinResize) window.removeEventListener("resize", this.onWinResize);
    document.removeEventListener("visibilitychange", this.onVisibility);
    if (this.canvas) {
      this.canvas.removeEventListener("mousemove", this.onMove);
      this.canvas.removeEventListener("mouseleave", this.onLeave);
    }
  },
  methods: {
    // ------------------------------------------------------------------
    // 场景构建
    // ------------------------------------------------------------------

    /**
     * 用 Fibonacci 球面分布撒点。
     *
     * 为什么不随机：纯随机撒点会出现明显的结块与空洞，转起来一眼就能看出
     * 「有几个地方挤成一坨」。Fibonacci 球面用黄金角把点均匀铺开，
     * 视觉上是均匀的，代价只是一行 `i * 2.399963`。
     */
    buildScene() {
      const n = this.vocab.length;
      const golden = Math.PI * (3 - Math.sqrt(5)); // 黄金角 ≈ 2.399963
      const nodes = [];
      for (let i = 0; i < n; i++) {
        const y = 1 - (i / (n - 1 || 1)) * 2; // y 从 1 到 -1
        const radius = Math.sqrt(Math.max(0, 1 - y * y));
        const theta = golden * i;
        nodes.push({
          label: this.vocab[i],
          // 单位球坐标；渲染时再乘半径，方便整体缩放而不改数据
          ux: Math.cos(theta) * radius,
          uy: y,
          uz: Math.sin(theta) * radius,
          phase: i * 0.7,
          sx: 0,
          sy: 0,
          scale: 0,
          depth: 0,
          links: 0,
          hot: false,
        });
      }

      // 连线：每个点连到球面上最近的三（degree）个点。
      // 用 3D 距离而非随机配对 —— 随机配对会横穿球体拉出很长的对角线，
      // 看起来像一团乱麻；最近邻连线贴着球面走，才有「网」的形态。
      const links = [];
      const seen = new Set();
      for (let i = 0; i < n; i++) {
        const dists = [];
        for (let j = 0; j < n; j++) {
          if (i === j) continue;
          const dx = nodes[i].ux - nodes[j].ux;
          const dy = nodes[i].uy - nodes[j].uy;
          const dz = nodes[i].uz - nodes[j].uz;
          dists.push({ j, d: dx * dx + dy * dy + dz * dz });
        }
        dists.sort((a, b) => a.d - b.d);
        for (let k = 0; k < Math.min(this.degree, dists.length); k++) {
          const j = dists[k].j;
          const key = i < j ? i + "-" + j : j + "-" + i;
          if (seen.has(key)) continue;
          seen.add(key);
          links.push({ a: i, b: j });
          nodes[i].links++;
          nodes[j].links++;
        }
      }

      this.nodes = nodes;
      this.links = links;
    },

    // ------------------------------------------------------------------
    // 渲染循环
    // ------------------------------------------------------------------

    start() {
      if (this.running || this.reducedMotion) return;
      this.running = true;
      const tick = () => {
        this.frame = (this.frame || 0) + 1;
        this.draw();
        this.raf = window.requestAnimationFrame(tick);
      };
      this.raf = window.requestAnimationFrame(tick);
    },

    stop() {
      this.running = false;
      if (this.raf) {
        window.cancelAnimationFrame(this.raf);
        this.raf = null;
      }
    },

    resize() {
      const wrap = this.$refs.wrap;
      const canvas = this.canvas;
      if (!wrap || !canvas) return;
      const rect = wrap.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2); // 上限 2：再高只是徒增像素
      this.w = Math.max(1, rect.width);
      this.h = Math.max(1, rect.height);
      canvas.width = Math.round(this.w * dpr);
      canvas.height = Math.round(this.h * dpr);
      canvas.style.width = this.w + "px";
      canvas.style.height = this.h + "px";
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      if (this.reducedMotion) this.draw();
    },

    draw() {
      const ctx = this.ctx;
      if (!ctx || !this.nodes) return;

      const w = this.w;
      const h = this.h;
      const cx = w / 2;
      const cy = h / 2;
      // 球半径取短边的 34%：留出边缘余量，否则最外侧节点会贴着边框被切掉
      const radius = Math.min(w, h) * 0.34;
      const focal = 2.6; // 单位球坐标用 2.6 的焦距，透视温和

      // 绕 Y 轴自转 + 绕 X 轴轻摆；鼠标视差叠加在自转角上
      const t = this.frame || 0;
      const rotY = t * this.speed + (this.px || 0) * this.parallax;
      const rotX =
        Math.sin(t * 0.0035) * 0.22 + (this.py || 0) * this.parallax * 0.5;
      const cy_ = Math.cos(rotY);
      const sy_ = Math.sin(rotY);
      const cx_ = Math.cos(rotX);
      const sx_ = Math.sin(rotX);

      // 背景：径向渐变。深色模式下用的是令牌里的深色档，
      // 但渐变本身也让平面画布有了「光源在上方」的暗示。
      const cs = getComputedStyle(document.documentElement);
      const bgFrom = (cs.getPropertyValue("--xh-net-bg-from") || "#f4f8ff").trim();
      const bgTo = (cs.getPropertyValue("--xh-net-bg-to") || "#eef2fb").trim();
      const grad = ctx.createRadialGradient(cx, cy * 0.6, 0, cx, cy, Math.max(w, h) * 0.75);
      grad.addColorStop(0, bgFrom);
      grad.addColorStop(1, bgTo);
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);

      // ---- 1. 投影 ----
      for (const node of this.nodes) {
        // 先绕 Y 再绕 X
        const x1 = node.ux * cy_ + node.uz * sy_;
        const z1 = -node.ux * sy_ + node.uz * cy_;
        const y2 = node.uy * cx_ - z1 * sx_;
        const z2 = node.uy * sx_ + z1 * cx_;

        // 每个节点沿自己的相位做轻微脉动，让整张图「活」而不只是刚体旋转
        const breathe = 1 + Math.sin(t * 0.01 + node.phase) * 0.05;
        const px = x1 * radius * breathe;
        const py = y2 * radius * breathe;
        const pz = z2 * radius * breathe;

        const s = focal / (focal + pz / radius);
        node.sx = cx + px * s;
        node.sy = cy + py * s;
        node.scale = s;
        node.depth = pz / radius; // -1（最近）.. 1（最远）
        node.hot = this.hovered && this.hovered.label === node.label;
      }

      // ---- 2. 连线（先画，让节点压在上面） ----
      const linkColor = this.cssVar("--xh-net-link", "rgba(0,80,203,.26)");
      const hotColor = this.cssVar("--xh-net-link-hot", "rgba(99,44,229,.55)");
      for (const link of this.links) {
        const a = this.nodes[link.a];
        const b = this.nodes[link.b];
        const hot = a.hot || b.hot;
        const mid = (a.depth + b.depth) / 2;
        // 深度 → 透明度：这是立体感的主要来源
        const alpha = 0.14 + (1 - (mid + 1) / 2) * 0.6;
        ctx.beginPath();
        ctx.moveTo(a.sx, a.sy);
        ctx.lineTo(b.sx, b.sy);
        ctx.strokeStyle = hot ? hotColor : linkColor;
        ctx.globalAlpha = hot ? Math.min(1, alpha + 0.45) : alpha;
        ctx.lineWidth = hot ? 1.6 : 0.9 * ((a.scale + b.scale) / 2);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;

      // ---- 3. 节点（按深度排序，远的先画） ----
      const sorted = this.nodes.slice().sort((a, b) => b.depth - a.depth);
      const nodeColor = this.cssVar("--xh-net-node", "#0050cb");
      const altColor = this.cssVar("--xh-net-node-alt", "#632ce5");
      const haloColor = this.cssVar("--xh-net-halo", "rgba(0,80,203,.16)");
      const labelColor = this.cssVar("--xh-text-primary", "#191c1e");

      for (const node of sorted) {
        const r = (node.hot ? 6.2 : 3.6) * node.scale + (this.hovered ? 0 : 0);
        const color = node.hot ? altColor : nodeColor;

        // 光晕：给最近层的节点一圈外发光，强化「浮在前面」的观感
        if (node.hot || node.depth < 0) {
          const glow = ctx.createRadialGradient(
            node.sx,
            node.sy,
            0,
            node.sx,
            node.sy,
            r * 4
          );
          glow.addColorStop(0, hotColor);
          glow.addColorStop(0.45, haloColor);
          glow.addColorStop(1, "rgba(0,0,0,0)");
          ctx.beginPath();
          ctx.arc(node.sx, node.sy, r * 4, 0, Math.PI * 2);
          ctx.fillStyle = glow;
          ctx.globalAlpha = node.hot ? 0.9 : 0.5;
          ctx.fill();
          ctx.globalAlpha = 1;
        }

        // 球体本身：带高光的径向渐变，光心偏左上 —— 与背景光源方向一致
        const body = ctx.createRadialGradient(
          node.sx - r * 0.35,
          node.sy - r * 0.35,
          r * 0.1,
          node.sx,
          node.sy,
          r
        );
        body.addColorStop(0, "#ffffff");
        body.addColorStop(0.35, color);
        body.addColorStop(1, color);
        ctx.beginPath();
        ctx.arc(node.sx, node.sy, r, 0, Math.PI * 2);
        ctx.fillStyle = body;
        ctx.globalAlpha = 0.35 + (1 - (node.depth + 1) / 2) * 0.65;
        ctx.fill();
        ctx.globalAlpha = 1;

        // 高亮节点的文字直接画在画布上（跟着球走），普通节点靠 tooltip
        if (node.hot && !this.reducedMotion) {
          ctx.font = "600 12px " + this.cssVar("--xh-font-family", "sans-serif");
          ctx.fillStyle = labelColor;
          ctx.globalAlpha = 0.9;
          ctx.fillText(node.label, node.sx + 10, node.sy + 4);
          ctx.globalAlpha = 1;
        }
      }
    },

    cssVar(name, fallback) {
      const v = getComputedStyle(document.documentElement)
        .getPropertyValue(name)
        .trim();
      return v || fallback;
    },

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    onMove(e) {
      const rect = this.canvas.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;

      // 视差：以中心为原点归一化到 [-1, 1]
      this.px = (mx / rect.width - 0.5) * 2;
      this.py = (my / rect.height - 0.5) * 2;

      // 命中检测：取屏幕距离最近且在阈值内的节点。
      // 阈值随节点缩放走 —— 近处的球大，可点区域也该大。
      let best = null;
      let bestD = Infinity;
      for (const node of this.nodes) {
        const dx = node.sx - mx;
        const dy = node.sy - my;
        const d = Math.sqrt(dx * dx + dy * dy);
        const threshold = 22;
        if (d < threshold && d < bestD) {
          bestD = d;
          best = node;
        }
      }
      this.hovered = best;
      this.canvas.style.cursor = best ? "pointer" : "default";
      if (this.reducedMotion) this.draw();
    },

    onLeave() {
      this.hovered = null;
      this.px = 0;
      this.py = 0;
      if (this.canvas) this.canvas.style.cursor = "default";
      if (this.reducedMotion) this.draw();
    },
  },
};
</script>

<style scoped>
.net-wrap {
  position: relative;
  width: 100%;
  border-radius: var(--xh-radius-lg);
  overflow: hidden;
  box-shadow: var(--xh-3d-shadow-rest);
}

.net-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.net-tip {
  position: absolute;
  transform: translate(-50%, calc(-100% - 14px));
  padding: 6px 10px;
  border-radius: var(--xh-radius-sm);
  background: var(--xh-glass-bg-strong);
  border: 1px solid var(--xh-glass-border);
  backdrop-filter: blur(var(--xh-glass-blur));
  -webkit-backdrop-filter: blur(var(--xh-glass-blur));
  box-shadow: var(--xh-glass-shadow);
  pointer-events: none;
  white-space: nowrap;
  display: flex;
  flex-direction: column;
  gap: 1px;
  line-height: 1.3;
}

.net-tip__name {
  font-size: var(--xh-font-size-sm);
  font-weight: var(--xh-font-semibold);
  color: var(--xh-text-primary);
}

.net-tip__meta {
  font-size: var(--xh-font-size-xs);
  color: var(--xh-text-muted);
}

.net-tip-enter-active,
.net-tip-leave-active {
  transition: opacity 0.14s ease;
}

.net-tip-enter-from,
.net-tip-leave-to {
  opacity: 0;
}

.net-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--xh-font-size-sm);
  color: var(--xh-text-muted);
}
</style>
