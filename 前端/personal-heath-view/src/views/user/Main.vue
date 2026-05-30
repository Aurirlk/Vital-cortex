<template>
  <div class="user-container">
    <div class="menus-container">
      <UserMenu
        :menus="routers"
        :userInfo="userInfo"
        @eventListener="eventListener"
      />
    </div>
    <div class="content-container">
      <router-view class="route-container"></router-view>
    </div>
    <!-- 个人中心 -->
    <el-dialog :show-close="true" v-model="dialogOperaion" width="26%" class="user-center-dialog" :style="{ marginTop: '15vh' }">
      <template #title>
        <div style="padding: 25px 0 0 20px">
          <span style="font-size: 18px; font-weight: 800; color: #fff"
            >个人中心</span
          >
        </div>
      </template>
      <el-row style="padding: 20px">
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*头像</span>
          </p>
          <el-upload
            class="avatar-uploader"
            :action="$uploadUrl"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <img v-if="data.url" :src="data.url" class="avatar" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*用户名</span>
          </p>
          <input
            class="modelInput"
            type="text"
            v-model="data.name"
            placeholder="用户名"
          />
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*个人邮箱</span>
          </p>
          <input
            class="modelInput"
            type="text"
            v-model="data.email"
            placeholder="邮箱"
          />
        </el-row>
      </el-row>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            class="customer"
            size="small"
            @click="dialogOperaion = false"
            >取 消</el-button
          >
          <el-button
            size="small"
            class="customer primary-btn"
            type="info"
            @click="updateUserInfo"
            >修改</el-button
          >
        </span>
      </template>
    </el-dialog>
    <!-- 重置密码 -->
    <el-dialog :show-close="true" v-model="dialogRetPwdOperaion" width="26%">
      <template #title>
        <div style="padding: 25px 0 0 20px">
          <span style="font-size: 18px; font-weight: 800; color: #fff"
            >重置密码</span
          >
        </div>
      </template>
      <el-row style="padding: 20px">
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*原始密码</span>
          </p>
          <input
            class="modelInput"
            type="password"
            v-model="pwdEntity.oldPwd"
            placeholder="原始密码"
          />
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*新密码</span>
          </p>
          <input
            class="modelInput"
            type="password"
            v-model="pwdEntity.newPwd"
            placeholder="新密码"
          />
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*确认密码</span>
          </p>
          <input
            class="modelInput"
            type="password"
            v-model="pwdEntity.againPwd"
            placeholder="确认密码"
          />
        </el-row>
      </el-row>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            class="customer"
            size="small"
            @click="dialogRetPwdOperaion = false"
            >取 消</el-button
          >
          <el-button
            size="small"
            class="customer primary-btn"
            type="info"
            @click="updateUserPwd"
            >修改</el-button
          >
        </span>
      </template>
    </el-dialog>
    <!-- 记录健康指标 -->
    <el-dialog v-model="healthModelConfigDialog" width="28%" :show-close="true">
      <template #title>
        <div>
          <p
            style="
              color: #fff;
              margin: 0;
              padding: 20px;
              font-size: 18px;
              font-weight: 600;
            "
          >
            记录健康指标
          </p>
        </div>
      </template>
      <div style="padding: 10px 20px">
        <el-row>
          <el-col :span="6">
            <span @click="addUserHealthHistory" class="submit-btn">
              <el-icon><CirclePlus /></el-icon>
              确认提交
            </span>
          </el-col>
          <el-col :span="18">
            <label for="nutrition-select">选择健康指标</label>
            <select
              id="nutrition-select"
              v-model="selecedHealthModelIndex"
              @change="healthModelChange"
            >
              <option
                v-for="option in healthModelConfig"
                :key="option.modelName"
                :value="option.id"
              >
                {{ option.modelName }}
              </option>
            </select>
          </el-col>
        </el-row>
      </div>
      <div style="padding: 10px 20px">
        <el-row
          v-for="(healthModel, index) in isCheckHealthModelConfig"
          :key="index"
          class="health-model-item"
        >
          <el-col :span="4">
            <img class="health-model-icon" :src="healthModel.modelIcon" />
          </el-col>
          <el-col :span="20" style="padding: 0 20px">
            <div>
              <input
                class="modelInput"
                type="text"
                v-model="healthModel.input"
                placeholder="数值"
              />
              <span class="model-unit">{{ healthModel.modelUnit }}</span>
            </div>
            <div style="margin: 10px 5px">
              <div style="font-size: 16px">
                <span
                  >{{ healthModel.modelName }} -
                  {{ healthModel.modelSymbol }}</span
                >
              </div>
              <div style="margin-top: 6px">
                <span
                  class="removeFood"
                  @click="removeHealthModel(healthModel)"
                >
                  <el-icon><CircleClose /></el-icon>
                  移除
                </span>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { clearToken } from "@/utils/storage.js";
import router from "@/router";
import UserMenu from "@/components/LevelMenu.vue";
export default {
  name: "UserMain",
  components: {
    UserMenu,
  },
  data() {
    return {
      userInfo: {},
      data: {},
      pwdEntity: { oldPwd: "", newPwd: "", againPwd: "" },
      dialogOperaion: false,
      dialogRetPwdOperaion: false,
      foodList: [],
      routers: [],
      isCheckFood: [],
      isCheckHealthModelConfig: [],
      healthModelConfig: [],
      selecedFoodIndex: 0,
      selecedHealthModelIndex: 0,
      dietDialog: false,
      healthModelConfigDialog: false,
    };
  },
  created() {
    console.log("[Main.vue] created, route:", this.$route.path);
    this.tokenCheckLoad();
  },
  mounted() {
    console.log("[Main.vue] mounted, DOM ready");
  },
  methods: {
    healthModelChange() {
      const healthModel =
        this.healthModelConfig[this.selecedHealthModelIndex - 2];
      const exists = this.isCheckHealthModelConfig.some(
        (item) => item.id === healthModel.id
      );
      // 如果不存在，则添加新选的健康配置项
      if (!exists) {
        this.isCheckHealthModelConfig.unshift(healthModel);
      } else {
        console.log("指标项已经添加");
      }
    },
    updateUserPwd() {
      this.resetPwd();
    },
    async updateUserInfo() {
      try {
        const userUpdateDTO = {
          userAvatar: this.data.url,
          userName: this.data.name,
          userEmail: this.data.email,
        };
        const resposne = await this.$axios.put(`/user/update`, userUpdateDTO);
        const { data } = resposne;
        if (data.code === 200) {
          this.dialogOperaion = false;
          this.tokenCheckLoad();
          this.$swal.fire({
            title: "修改个人信息",
            text: data.msg,
            icon: "success",
            showConfirmButton: false,
            timer: 1000,
          });
        }
      } catch (e) {
        this.dialogOperaion = false;
        this.$swal.fire({
          title: "修改个人信息异常",
          text: e,
          icon: "error",
          showConfirmButton: false,
          timer: 2000,
        });
        console.error(`修改个人信息异常:${e}`);
      }
    },
    async resetPwd() {
      try {
        const { oldPwd, newPwd, againPwd } = this.pwdEntity;
        if (!oldPwd || !newPwd || !againPwd) {
          this.$message(`任意项不为空`);
          return;
        }
        if (newPwd !== againPwd) {
          this.$message(`前后密码输入不一致`);
          return;
        }
        const pwdDTO = {
          oldPwd: this.$md5(this.$md5(oldPwd)),
          newPwd: this.$md5(this.$md5(newPwd)),
        };
        const resposne = await this.$axios.put(`/user/updatePwd`, pwdDTO);
        const { data } = resposne;
        if (data.code === 200) {
          this.dialogRetPwdOperaion = false;
          this.$swal.fire({
            title: "修改密码",
            text: data.msg,
            icon: "success",
            showConfirmButton: false,
            timer: 1000,
          });
          setTimeout(() => {
            clearToken();
            this.$router.push(`/login`);
          }, 1200);
        } else {
          this.$message.error(data.msg);
        }
      } catch (e) {
        this.dialogOperaion = false;
        this.$message.error(e.response?.data?.msg || "修改密码异常");
        console.error(`修改密码异常:${e}`);
      }
    },
    handleAvatarSuccess(res, file) {
      if (res.code !== 200) {
        this.$message.error(`头像上传异常`);
        return;
      }
      this.$message.success(`头像上传成功`);
      this.data.url = res.data;
    },
    // 监听菜单点击事件
    eventListener(event) {
      // 个人中心
      if (event === "center") {
        this.dialogOperaion = !this.dialogOperaion;
      }
      // 密码重置
      else if (event === "resetPwd") {
        this.dialogRetPwdOperaion = true;
        // 搜索页搜索
      } else if (event === "search-detail") {
        this.$router.push("/user/search-detail");
      }
      // 退出登录
      else if (event === "loginOut") {
        this.loginOutOperation();
      }
      // 健康指标记录
      else if (event === "healthDataRecord") {
        this.$router.push("/record");
      }
    },
    removeFood(food) {
      // 清空输入项
      food.mgValue = "";
      this.isCheckFood = this.isCheckFood.filter((item) => item.id !== food.id);
    },
    removeHealthModel(healthModel) {
      // 清空输入项
      healthModel.input = "";
      this.isCheckHealthModelConfig = this.isCheckHealthModelConfig.filter(
        (item) => item.id !== healthModel.id
      );
    },
    foodChange() {
      const food = this.foodList[this.selecedFoodIndex - 1];
      const exists = this.isCheckFood.some((item) => item.id === food.id);
      // 如果不存在，则添加新选的菜单
      if (!exists) {
        this.isCheckFood.unshift(food);
      } else {
        console.log("菜谱已经添加");
      }
    },
    // 提交饮食记录
    async addUserHealthHistory() {
      const healthModels = this.isCheckHealthModelConfig.map((entity) => {
        return {
          healthModelId: entity.id,
          inputValue: entity.input,
        };
      });
      try {
        const response = await this.$axios.post(
          `/user-health/save`,
          healthModels
        );
        const { data } = response;
        if (data.code === 200) {
          this.healthModelConfigDialog = false;
          this.isCheckHealthModelConfig = [];
          this.$swal.fire({
            title: "记录健康指标",
            text: "记录成功",
            icon: "success",
            showConfirmButton: false,
            timer: 1500,
          });
        }
      } catch (e) {
        console.error(`记录健康指标异常`, e);
      }
    },
    // 提交饮食记录
    async addDietHistory() {
      const foodIds = this.isCheckFood.map((entity) => entity.id).join(",");
      const foodNum = this.isCheckFood
        .map((entity) => entity.mgValue)
        .join(",");
      const diet = {
        foodIds: foodIds,
        foodNum: foodNum,
      };
      try {
        const response = await this.$axios.post(`/diet/save`, diet);
        const { data } = response;
        if (data.code === 200) {
          this.dietDialog = false;
          this.isCheckFood = [];
          this.$swal.fire({
            title: "记录饮食，拥抱生活",
            text: data.msg,
            icon: "success",
            showConfirmButton: false,
            timer: 1500,
          });
        }
      } catch (e) {
        console.error(`饮食新增异常：`, e);
      }
    },
    async loadHealthModelConfigList() {
      try {
        const response = await this.$axios.post(
          `/health-model-config/list`,
          {}
        );
        const { data } = response;
        this.healthModelConfig = data.data;
      } catch (e) {
        console.error(`查询健康配置异常：`, e);
      }
    },
    async loadFoodList() {
      try {
        const response = await this.$axios.post(`/food/list`, {});
        const { data } = response;
        this.foodList = data.data;
      } catch (e) {
        console.error(`查询饮食配置异常：`, e);
      }
    },
    async loginOutOperation() {
      const confirmed = await this.$swalConfirm({
        title: "退出登录",
        text: `退出登录后，需重新登录才能使用系统功能!`,
        icon: "warning",
      });
      if (confirmed) {
        // 清除Token，路由至登录页
        clearToken();
        this.$router.push("/login");
      }
    },
    // Token检验
    async tokenCheckLoad() {
      try {
        const res = await this.$axios.get('user/auth');
        if (res.data.code === 400) {
          clearToken();
          this.$message.error(res.data.msg);
          this.$router.push('/login');
          return;
        }
        const { id: userId, userAvatar, userName, userRole, userEmail } = res.data.data;
        sessionStorage.setItem('userInfo', JSON.stringify(res.data.data));
        this.userInfo = {
          url: userAvatar,
          name: userName,
          role: userRole,
          email: userEmail
        };
        this.data = { ...this.userInfo };
        const roleRouteKey = userRole === 1 ? 'admin' : 'user';
        const roleRoute = router.options.routes.find(route => route.path.startsWith(`/${roleRouteKey}`));
        if (roleRoute) {
          this.routers = roleRoute.children;
        } else {
          console.error('未能找到对应角色的路由配置');
        }
      } catch (error) {
        console.error('Token检验时发生错误:', error);
      }
    },
  },
};
</script>
<style lang="scss">
/* 个人中心弹窗下移 */
.user-center-dialog.el-dialog {
  margin-top: 18vh !important;
}
</style>

<style scoped lang="scss">
#nutrition-select {
  margin: 10px;
  padding: 8px 12px;
  font-size: 14px;
  border: 2px solid #e2e8f0;
  outline: none;
  width: 60%;
  color: #4a5568;
  border-radius: 8px;
  background: #f8fafc;
  transition: all 0.3s;

  &:focus {
    border-color: #667eea;
    background: #fff;
  }
}

.removeFood {
  color: #a0aec0;
  user-select: none;
  cursor: pointer;
  display: inline-block;
  margin-top: 10px;
  font-size: 13px;
  transition: color 0.2s;

  &:hover {
    color: #e53e3e;
  }
}

label {
  font-size: 13px;
  color: #718096;
  font-weight: 500;
}

.content-container {
  padding: 20px 120px;
  box-sizing: border-box;
  min-height: calc(100vh - 200px);
  overflow-x: hidden;
  background: #f5f7fa;
  margin: 0;
}

.modelInput {
  outline: none;
  border: none;
  border-bottom: 2px solid #e2e8f0;
  font-size: 16px;
  width: 70%;
  font-weight: 600;
  padding: 8px 4px;
  color: #2d3748;
  transition: border-color 0.3s;

  &:focus {
    border-bottom-color: #667eea;
  }
}

.modelName {
  font-size: 13px;
  color: #718096;
  font-weight: 500;
}

.model-unit {
  font-size: 13px;
  color: #718096;
  margin-left: 8px;
}

.avatar {
  width: 88px;
  height: 88px;
  border-radius: 12px;
  object-fit: cover;
  border: 3px solid #e2e8f0;
}

.submit-btn {
  margin-top: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: white;
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  }
}

.health-model-item {
  border-bottom: 1px solid #f0f2f5;
  padding: 16px 0;
  transition: background 0.2s;

  &:hover {
    background: #f8f9ff;
    border-radius: 8px;
  }
}

.health-model-icon {
  width: 90%;
  height: 56px;
  border-radius: 8px;
  margin-top: 20px;
  object-fit: cover;
}

.primary-btn {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);

  &:hover {
    opacity: 0.9;
  }
}
</style>
