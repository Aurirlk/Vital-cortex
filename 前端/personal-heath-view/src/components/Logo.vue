<template>
  <span class="logo">
    <el-image
      style="width: 30px; height: 30px"
      src="/logo.png"
      fit="fill"
    ></el-image>
    <div v-if="!flag">
      <span :style="{ color: bag, display: 'block' }">{{ sysName }}</span>
    </div>
  </span>
</template>
<script>
export default {
  name: "Logo",
  data() {
    return {};
  },
  props: {
    flag: {
      type: Boolean,
      required: false,
    },
    sysName: {
      type: String,
      required: false,
    },
    bag: {
      type: String,
      default: "#1c1c1c",
    },
  },
  created() {},
  methods: {},
};
</script>
<style scoped lang="scss">
.logo {
  color: #111827 !important;
  font-weight: 700;
  font-size: 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  user-select: none;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;

  span {
    margin-left: 8px;
    background: linear-gradient(135deg, #667eea, #764ba2);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    font-weight: 700;
  }
}
</style>
