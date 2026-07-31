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
      <ModelBanner />
      <router-view class="route-container"></router-view>
    </div>
    <!--  -->
    <el-dialog :show-close="true" v-model="dialogOperaion" width="26%" class="user-center-dialog" :style="{ marginTop: '15vh' }">
      <template #title>
        <div style="padding: 25px 0 0 20px">
          <span style="font-size: 18px; font-weight: 800; color: #fff"
            ></span
          >
        </div>
      </template>
      <el-row style="padding: 20px">
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*</span>
          </p>
          <el-upload
            class="avatar-uploader"
            :action="$uploadUrl"
            :headers="$uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <img v-if="data.url" :src="data.url" class="avatar" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input
            class="modelInput"
            type="text"
            v-model="data.name"
            placeholder=""
          />
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input
            class="modelInput"
            type="text"
            v-model="data.email"
            placeholder=""
          />
        </el-row>
      </el-row>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            class="customer"
            size="small"
            @click="dialogOperaion = false"
            > </el-button
          >
          <el-button
            size="small"
            class="customer primary-btn"
            type="info"
            @click="updateUserInfo"
            ></el-button
          >
        </span>
      </template>
    </el-dialog>
    <!--  -->
    <el-dialog :show-close="true" v-model="dialogRetPwdOperaion" width="26%">
      <template #title>
        <div style="padding: 25px 0 0 20px">
          <span style="font-size: 18px; font-weight: 800; color: #fff"
            ></span
          >
        </div>
      </template>
      <el-row style="padding: 20px">
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*</span>
          </p>
          <input
            class="modelInput"
            type="password"
            v-model="pwdEntity.oldPwd"
            placeholder=""
          />
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*</span>
          </p>
          <input
            class="modelInput"
            type="password"
            v-model="pwdEntity.newPwd"
            placeholder=""
          />
        </el-row>
        <el-row style="width: 100%">
          <p style="font-size: 12px; padding: 3px 0; margin-bottom: 10px">
            <span class="modelName">*</span>
          </p>
          <input
            class="modelInput"
            type="password"
            v-model="pwdEntity.againPwd"
            placeholder=""
          />
        </el-row>
      </el-row>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            class="customer"
            size="small"
            @click="dialogRetPwdOperaion = false"
            > </el-button
          >
          <el-button
            size="small"
            class="customer primary-btn"
            type="info"
            @click="updateUserPwd"
            ></el-button
          >
        </span>
      </template>
    </el-dialog>
    <!--  -->
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
            
          </p>
        </div>
      </template>
      <div style="padding: 10px 20px">
        <el-row>
          <el-col :span="6">
            <span @click="addUserHealthHistory" class="submit-btn">
              <el-icon><CirclePlus /></el-icon>
              
            </span>
          </el-col>
          <el-col :span="18">
            <label for="nutrition-select"></label>
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
                placeholder=""
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
                  
                </span>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-dialog>
    <!--  -->
    <el-dialog v-model="settingsDialog" width="40%" :show-close="true">
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
            
          </p>
        </div>
      </template>
      <div style="padding: 20px">
        <div class="settings-section">
          <h3></h3>
          <div class="setting-item">
            <div class="setting-info">
              <el-icon><Moon /></el-icon>
              <span></span>
            </div>
            <el-switch
              v-model="settings.isDarkMode"
              @change="toggleDarkMode"
              active-text=""
              inactive-text=""
            />
          </div>
        </div>
        <div class="settings-section">
          <h3></h3>
          <div class="setting-item">
            <div class="setting-info">
              <el-icon><View /></el-icon>
              <span></span>
            </div>
            <el-switch
              v-model="settings.showBannerDots"
              @change="saveSettings"
              active-text=""
              inactive-text=""
            />
          </div>
          <div class="setting-item">
            <div class="setting-info">
              <el-icon><Timer /></el-icon>
              <span></span>
            </div>
            <el-switch
              v-model="settings.autoPlayBanner"
              @change="saveSettings"
              active-text=""
              inactive-text=""
            />
          </div>
        </div>
        <div class="settings-section">
          <h3></h3>
          <div class="setting-item">
            <div class="setting-info">
              <el-icon><Bell /></el-icon>
              <span></span>
            </div>
            <el-switch
              v-model="settings.enableNotification"
              @change="saveSettings"
              active-text=""
              inactive-text=""
            />
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { clearToken } from "@/utils/storage.js";
import router from "@/router";
import UserMenu from "@/components/LevelMenu.vue";
import ModelBanner from "@/components/ModelBanner.vue";
export default {
  name: "UserMain",
  components: {
    UserMenu,
    ModelBanner,
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
      settingsDialog: false,
      settings: {
        isDarkMode: false,
        showBannerDots: true,
        autoPlayBanner: true,
        enableNotification: true,
      },
    };
  },
  created() {
    console.log("[Main.vue] created, route:", this.$route.path);
    this.tokenCheckLoad();
    this.loadSettings();
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
      // 
      if (!exists) {
        this.isCheckHealthModelConfig.unshift(healthModel);
      } else {
        console.log("");
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
            title: "",
            text: data.msg,
            icon: "success",
            showConfirmButton: false,
            timer: 1000,
          });
        }
      } catch (e) {
        this.dialogOperaion = false;
        this.$swal.fire({
          title: "",
          text: e,
          icon: "error",
          showConfirmButton: false,
          timer: 2000,
        });
        console.error(`:${e}`);
      }
    },
    async resetPwd() {
      try {
        const { oldPwd, newPwd, againPwd } = this.pwdEntity;
        if (!oldPwd || !newPwd || !againPwd) {
          this.$message(``);
          return;
        }
        if (newPwd !== againPwd) {
          this.$message(``);
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
            title: "",
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
        this.$message.error(e.response?.data?.msg || "");
        console.error(`:${e}`);
      }
    },
    handleAvatarSuccess(res, file) {
      if (res.code !== 200) {
        this.$message.error(``);
        return;
      }
      this.$message.success(``);
      this.data.url = res.data;
    },
    // 
    eventListener(event) {
      // 
      if (event === "center") {
        this.dialogOperaion = !this.dialogOperaion;
      }
      // 
      else if (event === "resetPwd") {
        this.dialogRetPwdOperaion = true;
        // 
      } else if (event === "search-detail") {
        this.$router.push("/user/search-detail");
      }
      // 
      else if (event === "loginOut") {
        this.loginOutOperation();
      }
      // 
      else if (event === "healthDataRecord") {
        this.$router.push("/record");
      }
      // 
      else if (event === "settings") {
        this.settingsDialog = true;
      }
    },
    removeFood(food) {
      // 
      food.mgValue = "";
      this.isCheckFood = this.isCheckFood.filter((item) => item.id !== food.id);
    },
    removeHealthModel(healthModel) {
      // 
      healthModel.input = "";
      this.isCheckHealthModelConfig = this.isCheckHealthModelConfig.filter(
        (item) => item.id !== healthModel.id
      );
    },
    foodChange() {
      const food = this.foodList[this.selecedFoodIndex - 1];
      const exists = this.isCheckFood.some((item) => item.id === food.id);
      // 
      if (!exists) {
        this.isCheckFood.unshift(food);
      } else {
        console.log("");
      }
    },
    // 
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
            title: "",
            text: "",
            icon: "success",
            showConfirmButton: false,
            timer: 1500,
          });
        }
      } catch (e) {
        console.error(``, e);
      }
    },
    // 
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
            title: "",
            text: data.msg,
            icon: "success",
            showConfirmButton: false,
            timer: 1500,
          });
        }
      } catch (e) {
        console.error(``, e);
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
        console.error(``, e);
      }
    },
    async loadFoodList() {
      try {
        const response = await this.$axios.post(`/food/list`, {});
        const { data } = response;
        this.foodList = data.data;
      } catch (e) {
        console.error(``, e);
      }
    },
    async loginOutOperation() {
      const confirmed = await this.$swalConfirm({
        title: "",
        text: `!`,
        icon: "warning",
      });
      if (confirmed) {
        // Token
        clearToken();
        this.$router.push("/login");
      }
    },
    // 
    loadSettings() {
      const settings = localStorage.getItem("userSettings");
      if (settings) {
        const parsed = JSON.parse(settings);
        this.settings = {
          isDarkMode: parsed.isDarkMode || false,
          showBannerDots: parsed.showBannerDots !== false,
          autoPlayBanner: parsed.autoPlayBanner !== false,
          enableNotification: parsed.enableNotification !== false,
        };
      }
      this.applyDarkMode();
    },
    saveSettings() {
      localStorage.setItem("userSettings", JSON.stringify(this.settings));
      this.applyDarkMode();
    },
    toggleDarkMode() {
      this.saveSettings();
    },
    applyDarkMode() {
      //  CSS  body 
      if (this.settings.isDarkMode) {
        document.documentElement.classList.add("dark");
      } else {
        document.documentElement.classList.remove("dark");
      }
    },
    // Token
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
          console.error('');
        }
      } catch (error) {
        console.error('Token:', error);
      }
    },
  },
};
</script>
<style lang="scss">
/*  */
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

.settings-section {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;

  &:last-child {
    border-bottom: none;
    margin-bottom: 0;
  }

  h3 {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 15px;
    color: #2d3748;
  }
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
}

.setting-info {
  display: flex;
  align-items: center;
  gap: 12px;

  .el-icon {
    font-size: 18px;
    color: #667eea;
  }

  span {
    font-size: 14px;
    color: #4a5568;
  }
}
</style>
