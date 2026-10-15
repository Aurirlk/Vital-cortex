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
    <!--
      阶段 4：换成 CarouselDots。
      顺带修掉一个真实的错位 —— 原来判定高亮用的是
      `idx === index - 1 || (index === 0 && idx === data.length - 1)`，
      也就是**永远高亮上一张**（index=0 时高亮最后一个点）。
      改成直接比较 `idx === index`（由 CarouselDots 内部完成），
      并顺带获得：自动播放进度、方向键导航、以及「当前项拉长成胶囊」
      这层不依赖颜色的状态信号。
    -->
    <div class="dots-container" v-if="data.length > 1">
      <CarouselDots
        variant="pill"
        :count="data.length"
        :model-value="index"
        :autoplay-ms="time"
        aria-label="资讯轮播导航"
        @update:model-value="goToSlide"
      />
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
import CarouselDots from "@/components/effects/CarouselDots.vue";

//
export default {
  name: "Banner",
  components: { CarouselDots },
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
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

//
:deep(.dark) .dots-container {
  background: rgba(0, 0, 0, 0.6);
}

// 轮播图底色是任意渐变色，指示点的强调色跟着 banner 自己的紫色走
:deep(.dots) {
  --dots-accent: #667eea;
  gap: 4px;
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
