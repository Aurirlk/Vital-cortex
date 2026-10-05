<template>
  <div class="banner-wrapper">
    <div
      class="banner-img banner-gradient"
      :style="[
        { width: width, height: height, borderRadius: borderRadius },
        bannerGradient(activeData),
      ]"
    ></div>
    <h3 @click="onClick" class="tip-name">{{ activeData.name }}</h3>
    <!--  -->
    <div class="dots-container" v-if="data.length > 1">
      <span
        v-for="(item, idx) in data"
        :key="idx"
        class="dot"
        :class="{
          active: idx === index - 1 || (index === 0 && idx === data.length - 1),
        }"
        @click.stop="goToSlide(idx)"
      ></span>
    </div>
    <!--  -->
    <div
      class="arrow left-arrow"
      @click.stop="prevSlide"
      v-if="data.length > 1"
    >
      <el-icon><ArrowLeft /></el-icon>
    </div>
    <div
      class="arrow right-arrow"
      @click.stop="nextSlide"
      v-if="data.length > 1"
    >
      <el-icon><ArrowRight /></el-icon>
    </div>
  </div>
</template>

<script>
//
export default {
  name: "Banner",
  props: {
    data: {
      type: Array,
      required: true,
    },
    width: {
      //
      type: String,
      default: "100%",
    },
    height: {
      //
      type: String,
      default: "208px",
    },
    borderRadius: {
      //
      type: String,
      default: "5px",
    },
    time: {
      //
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
    bannerGradient(data) {
      const map = {
        康复手册: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
        养生保健: "linear-gradient(135deg, #11998e 0%, #38ef7d 100%)",
        疾病预防: "linear-gradient(135deg, #ff6b6b 0%, #feca57 100%)",
        心理健康: "linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)",
        运动健身: "linear-gradient(135deg, #fa709a 0%, #fee140 100%)",
        饮食健康: "linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)",
      };
      return {
        background:
          map[data?.tagName] ||
          "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
      };
    },
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
      //
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

.banner-gradient {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    background: radial-gradient(
      circle at 30% 20%,
      rgba(255, 255, 255, 0.25) 0%,
      transparent 50%
    );
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

//
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
