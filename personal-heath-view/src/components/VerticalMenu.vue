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
    <!-- 未分组项：直接铺开 -->
    <el-menu-item
      v-for="item in ungrouped"
      :key="item.path"
      style="width: 100%"
      :index="item.path"
      :class="{ 'is-active': activeIndex === item.path }"
    >
      <el-icon style="font-size: 20px"><component :is="item.icon" /></el-icon>
      <template #title
        ><span style="font-size: 14px">{{ item.name }}</span></template
      >
    </el-menu-item>

    <!-- 分组项：可折叠 -->
    <el-sub-menu
      v-for="group in groups"
      :key="group.name"
      :index="'group:' + group.name"
      popper-class="admin-menu-popper"
    >
      <template #title>
        <el-icon style="font-size: 18px"><component :is="group.icon" /></el-icon>
        <span style="font-size: 13.5px">{{ group.name }}</span>
      </template>

      <el-menu-item
        v-for="item in group.items"
        :key="item.path"
        style="width: 100%"
        :index="item.path"
        :class="{ 'is-active': activeIndex === item.path }"
      >
        <el-icon style="font-size: 18px"><component :is="item.icon" /></el-icon>
        <template #title
          ><span style="font-size: 14px">{{ item.name }}</span></template
        >
      </el-menu-item>
    </el-sub-menu>
  </el-menu>
</template>
<script>
export default {
  name: "AdminMenu",
  data() {
    return {
      activeIndex: "1",
      selectedMenuItem: "",
    };
  },
  computed: {
    visibleRoutes() {
      return (this.routes || []).filter(
        (item) => item.name && item.name.trim() && !item.isHidden
      );
    },
    ungrouped() {
      return this.visibleRoutes.filter((r) => !(r.meta && r.meta.group));
    },
    groups() {
      const order = [
        "用户与权限",
        "内容运营",
        "健康与医疗",
        "业务运营",
        "AI 与数据",
        "系统管理",
      ];
      const icons = {
        用户与权限: "UserFilled",
        内容运营: "Document",
        健康与医疗: "FirstAidKit",
        业务运营: "Briefcase",
        "AI 与数据": "DataAnalysis",
        系统管理: "Setting",
      };
      const map = new Map();
      this.visibleRoutes.forEach((r) => {
        const g = r.meta && r.meta.group;
        if (!g) return;
        if (!map.has(g)) map.set(g, []);
        map.get(g).push(r);
      });
      return Array.from(map.keys())
        .sort((a, b) => {
          const ia = order.indexOf(a);
          const ib = order.indexOf(b);
          return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
        })
        .map((name) => ({
          name,
          icon: icons[name] || "Menu",
          items: map.get(name),
        }));
    },
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
:deep(.el-sub-menu__title) {
  height: 42px !important;
  line-height: 42px !important;
  user-select: none;
  color: #94a3b8;
  border-radius: 8px;
  margin: 2px 0;
  font-size: 13.5px;
}

:deep(.el-sub-menu__title:hover),
.el-menu-item:hover,
.el-menu-item:focus {
  box-sizing: border-box;
  background-color: rgba(255, 255, 255, 0.06) !important;
}

/* 子菜单容器缩进与浅色底，避免层级混淆 */
:deep(.el-sub-menu .el-menu) {
  background-color: rgba(255, 255, 255, 0.03) !important;
  border-radius: 8px;
}
</style>
