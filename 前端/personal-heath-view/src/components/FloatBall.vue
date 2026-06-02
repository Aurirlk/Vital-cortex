<template>
  <div
    class="float-ball"
    :style="{ left: position.x + 'px', top: position.y + 'px' }"
    @mousedown="startDrag"
    @click="handleClick"
    @mouseenter="showTooltip = true"
    @mouseleave="showTooltip = false"
  >
    <el-icon :size="28"><Service /></el-icon>
    <span class="float-ball-text">健康助手</span>
    
    <!-- 提示框 -->
    <div v-if="showTooltip && !isDragging" class="float-ball-tooltip">
      点击打开健康助手
    </div>
  </div>
</template>

<script>
export default {
  name: "FloatBall",
  data() {
    return {
      position: { x: 20, y: window.innerHeight - 100 },
      isDragging: false,
      dragOffset: { x: 0, y: 0 },
      showTooltip: false,
      dragStartTime: 0,
    };
  },
  mounted() {
    // 从本地存储恢复位置
    const savedPos = localStorage.getItem("floatBallPosition");
    if (savedPos) {
      try {
        const pos = JSON.parse(savedPos);
        this.position = {
          x: Math.max(0, Math.min(window.innerWidth - 60, pos.x)),
          y: Math.max(0, Math.min(window.innerHeight - 60, pos.y)),
        };
      } catch (e) {
        // 忽略解析错误
      }
    }
    
    // 监听窗口大小变化
    window.addEventListener("resize", this.handleResize);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.handleResize);
    document.removeEventListener("mousemove", this.onDrag);
    document.removeEventListener("mouseup", this.stopDrag);
  },
  methods: {
    startDrag(e) {
      this.isDragging = false;
      this.dragStartTime = Date.now();
      this.dragOffset = {
        x: e.clientX - this.position.x,
        y: e.clientY - this.position.y,
      };
      document.addEventListener("mousemove", this.onDrag);
      document.addEventListener("mouseup", this.stopDrag);
    },
    onDrag(e) {
      const dx = e.clientX - this.dragOffset.x - this.position.x;
      const dy = e.clientY - this.dragOffset.y - this.position.y;
      
      // 移动超过5px才算拖拽
      if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
        this.isDragging = true;
      }
      
      if (this.isDragging) {
        this.position = {
          x: Math.max(0, Math.min(window.innerWidth - 60, e.clientX - this.dragOffset.x)),
          y: Math.max(0, Math.min(window.innerHeight - 60, e.clientY - this.dragOffset.y)),
        };
      }
    },
    stopDrag() {
      document.removeEventListener("mousemove", this.onDrag);
      document.removeEventListener("mouseup", this.stopDrag);
      
      if (this.isDragging) {
        // 保存位置到本地存储
        localStorage.setItem("floatBallPosition", JSON.stringify(this.position));
      }
      
      // 延迟重置 isDragging，以便 click 事件判断
      setTimeout(() => {
        this.isDragging = false;
      }, 100);
    },
    handleClick() {
      // 只有在非拖拽状态下才触发点击
      if (!this.isDragging && Date.now() - this.dragStartTime < 300) {
        this.$emit("click");
      }
    },
    handleResize() {
      // 窗口大小变化时，确保悬浮球在可视区域内
      this.position = {
        x: Math.max(0, Math.min(window.innerWidth - 60, this.position.x)),
        y: Math.max(0, Math.min(window.innerHeight - 60, this.position.y)),
      };
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
  transition: transform 0.2s, box-shadow 0.2s;
  user-select: none;
}

.float-ball:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.6);
}

.float-ball:active {
  transform: scale(0.95);
}

.float-ball .el-icon {
  color: #fff;
  margin-bottom: 2px;
}

.float-ball-text {
  font-size: 10px;
  color: #fff;
  white-space: nowrap;
}

.float-ball-tooltip {
  position: absolute;
  right: 70px;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  pointer-events: none;
}

.float-ball-tooltip::after {
  content: "";
  position: absolute;
  right: -6px;
  top: 50%;
  transform: translateY(-50%);
  border-left: 6px solid rgba(0, 0, 0, 0.8);
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
}
</style>
