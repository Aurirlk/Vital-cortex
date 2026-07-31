<template>
  <div class="main">
    <div class="header-left">
      <span class="operation-span" @click="operation">
        <el-icon v-if="!showFlag" class="i-folder"><Fold /></el-icon>
        <el-icon v-else class="i-folder"><Expand /></el-icon>
      </span>
      <span class="operation-span-tag">
        &nbsp;&nbsp;/&nbsp;&nbsp;{{ tag == "" ? "" : tag }}
      </span>
    </div>
    <div class="header-right">
      <el-dropdown class="user-dropdown">
        <span class="el-dropdown-link">
          <el-avatar :size="35" :src="userInfo.url"></el-avatar>
          <span class="userName">{{ userInfo.name }}</span>
          <el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :icon="UserFilled" @click="userCenterPanel"></el-dropdown-item>
            <el-dropdown-item :icon="Fold" @click="loginOut"></el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>
<script>
import { Fold, Expand, ArrowDown, UserFilled } from "@element-plus/icons-vue";
export default {
  name: "LevelHeader",
  components: { Fold, Expand, ArrowDown, UserFilled },
  data() {
    return {
      showFlag: sessionStorage.getItem("flag") === "true",
      Fold,
      Expand,
      ArrowDown,
      UserFilled,
    };
  },
  props: {
    tag: {
      type: String,
      required: true,
      default: "",
    },
    userInfo: {
      type: Object,
      required: true,
      default: () => ({}),
    },
    bag: {
      type: String,
      default: "",
    },
  },
  methods: {
    // 
    userCenterPanel() {
      this.$emit("eventListener", "center");
    },
    // 
    loginOut() {
      this.$emit("eventListener", "loginOut");
    },
    operation() {
      this.showFlag = !this.showFlag;
      sessionStorage.setItem("flag", this.showFlag);
      this.$emit("selectOperation", this.showFlag);
    },
  },
};
</script>
<style scoped lang="scss">
.main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 100%;
  height: 56px;
  padding: 0 24px;
  box-sizing: border-box;
  background-color: #fff;
  color: #333;
  border-bottom: 1px solid #e8e8e8;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
}

.operation-span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  color: #555;
  transition: all 0.2s;

  &:hover {
    background-color: #f3f4f6;
    color: #111;
  }

  .el-icon {
    font-size: 18px;
  }
}

.operation-span-tag {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  user-select: none;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.user-dropdown {
  .el-dropdown-link {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 6px 12px;
    border-radius: 8px;
    transition: background 0.2s;

    &:hover {
      background: #f3f4f6;
    }
  }

  .userName {
    font-size: 14px;
    font-weight: 500;
    color: #374151;
    user-select: none;
  }
}
</style>
