<template>
  <div class="banner-wrapper">
    <img
      :src="activeData.cover"
      :style="{ width: width, height: height, borderRadius: borderRadius }"
      class="banner-img"
    />
    <h3 @click="onClick" class="tip-name">{{ activeData.name }}</h3>
    <!-- 白点指示器 -->
    <div class="dots-container" v-if="data.length > 1">
      <span
        v-for="(item, idx) in data"
        :key="idx"
        class="dot"
        :class="{ active: idx === index - 1 || (index === 0 && idx === data.length - 1) }"
        @click.stop="goToSlide(idx)"
      ></span>
    </div>
    <!-- 左右箭头 -->
    <div class="arrow left-arrow" @click.stop="prevSlide" v-if="data.length > 1">
      <el-icon><ArrowLeft /></el-icon>
    </div>
    <div class="arrow right-arrow" @click.stop="nextSlide" v-if="data.length > 1">
      <el-icon><ArrowRight /></el-icon>
    </div>
  </div>
</template>

<script>
// 轮播图组件
export default {
  name: "Banner",
  props: {
    data: {
      type: Array,
      required: true,
    },
    width: {
      // 宽度
      type: String,
      default: "100%",
    },
    height: {
      // 高度
      type: String,
      default: "208px",
    },
    borderRadius: {
      // 图片边框曲度
      type: String,
      default: "5px",
    },
    time: {
      // 轮播时间
      type: Number,
      default: 3000,
    },
  },
  watch: {
    data: {
      handler() {
        if (this.data && this.data.length > 0) {
          this.index = 0;
          this.activeData = { ...this.data[0] };
          this.config();
        }
      },
      deep: true,
      immediate: true,
    },
  },
  data() {
    return {
      activeData: {},
      index: 0,
      timer: null,
    };
  },
  beforeUnmount() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  },
  methods: {
    onClick(data) {
      this.$emit("on-click", this.activeData);
    },
    config() {
      if (this.timer) {
        clearInterval(this.timer);
      }
      this.timer = setInterval(() => {
        this.nextSlide();
      }, this.time);
    },
    nextSlide() {
      this.index = (this.index + 1) % this.data.length;
      this.activeData = { ...this.data[this.index] };
    },
    prevSlide() {
      this.index = (this.index - 1 + this.data.length) % this.data.length;
      this.activeData = { ...this.data[this.index] };
    },
    goToSlide(idx) {
      this.index = idx;
      this.activeData = { ...this.data[this.index] };
      // 重新设置定时器
      this.config();
    },
  },
};
</script>

<style scoped lang="scss">
.banner-wrapper {
  position: relative;
  border-radius: 12px;
  overflow: visible;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  padding-bottom: 20px;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 30px rgba(102, 126, 234, 0.15);

    .arrow {
      opacity: 1;
    }
  }
}

.banner-img {
  display: block;
  transition: transform 0.4s ease;
  border-radius: 12px;

  &:hover {
    transform: scale(1.03);
  }
}

.tip-name {
  position: absolute;
  bottom: 20px;
  text-align: center;
  width: 100%;
  padding: 18px 12px;
  color: #fff;
  margin: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  border-bottom-left-radius: 12px;
  border-bottom-right-radius: 12px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}

.dots-container {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 10px;
  z-index: 10;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.2);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.4);
    transform: scale(1.2);
  }

  &.active {
    background: #667eea;
    transform: scale(1.3);
    box-shadow: 0 0 8px rgba(102, 126, 234, 0.5);
  }
}

// 深色模式下的指示器
:deep(.dark) .dots-container {
  background: rgba(0, 0, 0, 0.6);
}

:deep(.dark) .dot {
  background: rgba(255, 255, 255, 0.3);

  &:hover {
    background: rgba(255, 255, 255, 0.5);
  }

  &.active {
    background: #667eea;
    box-shadow: 0 0 8px rgba(102, 126, 234, 0.5);
  }
}

.arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 36px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: all 0.3s ease;
  color: #fff;
  z-index: 10;

  &:hover {
    background: rgba(0, 0, 0, 0.6);
    transform: translateY(-50%) scale(1.1);
  }
}

.left-arrow {
  left: 10px;
}

.right-arrow {
  right: 10px;
}
</style>
