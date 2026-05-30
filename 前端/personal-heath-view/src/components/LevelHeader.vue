<template>
  <div class="main">
    <span>
      <span class="operation-span" @click="operation">
        <el-icon v-if="!showFlag" class="i-folder"><Fold /></el-icon>
        <el-icon v-else class="i-folder"><Expand /></el-icon>
      </span>
    </span>
    <span>
      <span class="operation-span-tag">
        后台&nbsp;&nbsp;/&nbsp;&nbsp;{{ tag == "" ? "元数据" : tag }}
      </span>
    </span>
    <span class="user-block">
      <el-dropdown class="user-dropdown">
        <span
          class="el-dropdown-link"
          style="display: flex; align-items: center"
        >
          <el-avatar
            :size="35"
            :src="userInfo.url"
            style="margin-top: 0"
          ></el-avatar>
          <span class="userName" style="margin-left: 5px; font-size: 16px">{{
            userInfo.name
          }}</span>
          <el-icon style="margin-left: 5px"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :icon="UserFilled" @click="userCenterPanel"
              >个人资料</el-dropdown-item
            >
            <el-dropdown-item :icon="Fold" @click="loginOut"
              >退出登录</el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </span>
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
    // 个人中心，传回父组件处理
    userCenterPanel() {
      this.$emit("eventListener", "center");
    },
    // 退出登录，传回父组件处理
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
  padding: 15px 26px 15px 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  width: 100%;
  position: relative;
  background-color: rgb(255, 255, 255);
  color: #666;

  .operation-span-tag {
    padding: 9px 10px;
    border-radius: 3px;
    font-size: 16px;
    user-select: none;
    margin-top: 15px;
  }

  .operation-span:hover {
    background-color: rgb(246, 246, 246);
  }

  .operation-span {
    margin-top: 20px;
    padding: 6px;
    margin-left: 10px;
    border-radius: 3px;
    user-select: none;

    .el-icon {
      margin: 5px;
      font-size: 20px;
      color: #333;
    }
  }

  span {
    color: #333;
  }

  .user-block {
    position: absolute;
    right: 35px;

    .userName {
      display: inline-block;
      vertical-align: middle;
      font-size: 14px;
      cursor: pointer;
      user-select: none;
    }
  }
}
</style>
