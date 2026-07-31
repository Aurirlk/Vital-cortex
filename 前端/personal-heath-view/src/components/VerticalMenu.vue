<template>
  <el-menu
    :collapse-transition="false"
    :collapse="flag"
    style="padding: 5px 12px; width: 100%"
    :default-active="activeIndex"
    background-color="transparent"
    text-color="#94a3b8"
    active-text-color="#f1f5f9"
    @select="handleSelect"
  >
    <template v-for="(item, index) in routes" :key="index">
      <el-menu-item
        v-if="!item.children || item.children.length === 0"
        style="width: 100%"
        :index="item.path"
        :class="{ 'is-active': activeIndex === item.path }"
      >
        <el-icon style="font-size: 20px"><component :is="item.icon" /></el-icon>
        <template #title
          ><span style="font-size: 14px">{{ item.name }}</span></template
        >
      </el-menu-item>
    </template>
  </el-menu>
</template>
<script>
export default {
  name: "AdminMenu",
  data() {
    return {
      activeIndex: "1",
      isCollapse: true,
      selectedMenuItem: "",
    };
  },
  props: {
    routes: {
      type: Array,
      required: true,
    },
    flag: {
      type: Boolean,
      required: true,
    },
    bag: {
      type: String,
      default: "#FFFFFF",
    },
  },
  created() {
    const saveLastPath = sessionStorage.getItem("activeMenuItem");
    if (saveLastPath === null || saveLastPath.startsWith("/")) {
      this.handleSelect("adminLayout");
    } else {
      this.handleSelect(saveLastPath);
    }
  },
  methods: {
    handleSelect(index) {
      this.activeIndex = index;
      this.$emit("select", this.activeIndex);
      sessionStorage.setItem("activeMenuItem", this.activeIndex);
    },
  },
};
</script>

<style scoped>
.is-active {
  background-color: rgba(255, 255, 255, 0.1) !important;
  color: #f1f5f9 !important;
  font-weight: 600;
  border-radius: 8px;
}

.el-menu-item,
.el-submenu__title {
  height: 44px !important;
  line-height: 44px !important;
  user-select: none;
  color: #94a3b8;
  border-radius: 8px;
  margin: 2px 0;
}

.el-menu-item:focus,
.el-menu-item:hover {
  box-sizing: border-box;
  background-color: rgba(255, 255, 255, 0.06) !important;
}

.el-menu-item {
  height: 44px !important;
  line-height: 44px !important;
}

.el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.06) !important;
}
</style>
