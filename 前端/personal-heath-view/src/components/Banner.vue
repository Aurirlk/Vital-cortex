<template>
  <div class="banner-wrapper">
    <img
      :src="activeData.cover"
      :style="{ width: width, height: height, borderRadius: borderRadius }"
      class="banner-img"
    />
    <h3 @click="onClick" class="tip-name">{{ activeData.name }}</h3>
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
        this.index = this.index >= this.data.length ? 0 : this.index;
        this.activeData = this.data[this.index++];
      }, this.time);
    },
  },
};
</script>

<style scoped lang="scss">
.banner-wrapper {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 30px rgba(102, 126, 234, 0.15);
  }
}

.banner-img {
  display: block;
  transition: transform 0.4s ease;

  &:hover {
    transform: scale(1.03);
  }
}

.tip-name {
  position: absolute;
  bottom: 0;
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
</style>
