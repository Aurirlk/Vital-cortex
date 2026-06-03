<template>
  <div
    class="float-ball"
    :style="{ left: position.x + 'px', top: position.y + 'px' }"
    @mousedown.prevent="onMouseDown"
  >
    <el-icon :size="28"><Service /></el-icon>
    <span class="float-ball-text">健康助手</span>
  </div>
</template>

<script>
export default {
  name: "FloatBall",
  data() {
    return {
      position: { x: 60, y: window.innerHeight - 100 },
      startX: 0,
      startY: 0,
      moved: false,
    };
  },
  mounted() {
    const saved = localStorage.getItem("floatBallPosition");
    if (saved) {
      try {
        const p = JSON.parse(saved);
        this.position.x = Math.max(0, Math.min(window.innerWidth - 60, p.x));
        this.position.y = Math.max(0, Math.min(window.innerHeight - 60, p.y));
      } catch (e) {}
    }
  },
  methods: {
    onMouseDown(e) {
      this.startX = e.clientX;
      this.startY = e.clientY;
      this.moved = false;

      const onMove = (ev) => {
        const dx = ev.clientX - this.startX;
        const dy = ev.clientY - this.startY;
        if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
          this.moved = true;
          this.position.x = Math.max(0, Math.min(window.innerWidth - 60, ev.clientX - (this.startX - this.position.x)));
          this.position.y = Math.max(0, Math.min(window.innerHeight - 60, ev.clientY - (this.startY - this.position.y)));
        }
      };

      const onUp = () => {
        document.removeEventListener("mousemove", onMove);
        document.removeEventListener("mouseup", onUp);
        if (!this.moved) {
          this.$emit("click");
        } else {
          localStorage.setItem("floatBallPosition", JSON.stringify(this.position));
        }
      };

      document.addEventListener("mousemove", onMove);
      document.addEventListener("mouseup", onUp);
    },
  },
};
</script>

<style scoped>
.float-ball {
  position: fixed;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  z-index: 9999;
  user-select: none;
}
.float-ball:hover { transform: scale(1.1); }
.float-ball .el-icon { color: #fff; margin-bottom: 2px; }
.float-ball-text { font-size: 10px; color: #fff; white-space: nowrap; }
</style>
