<template>
  <div>
    <div style="padding: 0 50px">
      <div>
        <p style="font-size: 24px; padding: 10px 0; font-weight: bolder">
          <span
            @click="goBack"
            style="cursor: pointer; display: inline-block; padding: 0 20px 0 0"
          >
            <el-icon><ArrowLeft /></el-icon>
            
          </span>
          
        </p>
      </div>
    </div>
    <div style="height: 6px; background-color: rgb(248, 248, 248)"></div>
    <div style="padding: 10px 50px">
      <el-row>
        <el-col
          :span="6"
          style="
            border-right: 1px solid #f1f1f1;
            min-height: calc(100vh - 250px);
          "
        >
          <el-tabs
            v-model="activeName"
            @tab-click="handleClick"
            style="margin-right: 40px"
          >
            <el-tab-pane label="" name="first"></el-tab-pane>
            <el-tab-pane label="" name="second"></el-tab-pane>
          </el-tabs>
          <div style="padding: 20px 0 30px 0">
            <span
              @click="addModel"
              style="
                cursor: pointer;
                padding: 10px 20px;
                background-color: #000;
                border-radius: 5px;
                color: #fff;
              "
            >
              
              <el-icon><Right /></el-icon>
            </span>
          </div>
          <div>
            <span style="margin-right: 20px"></span>
            <el-input
              style="width: 148px"
              v-model="userHealthModel.name"
              placeholder=""
              clearable
              @clear="handleFilterClear"
            >
            </el-input>
            <el-button
              class="customer"
              style="
                margin-left: 20px;
                background-color: rgb(43, 121, 203);
                border: none;
              "
              type="primary"
              @click="searModel"
              ></el-button
            >
          </div>
          <div
            style="
              padding: 10px 6px;
              margin-right: 40px;
              height: 500px;
              overflow-y: scroll;
              overflow-x: hidden;
            "
          >
            <div
              @click="modelSelected(model)"
              class="item-model"
              v-for="(model, index) in modelList"
              :key="index"
            >
              <el-tooltip
                class="item"
                effect="dark"
                :content="'' + model.name + ''"
                placement="bottom"
              >
                <el-row style="padding: 20px 0">
                  <el-col :span="4">
                    <img
                      :src="model.cover"
                      style="width: 50px; height: 50px; margin-top: 5px"
                    />
                  </el-col>
                  <el-col :span="20">
                    <div style="padding: 0 10px">
                      <div style="font-size: 24px; font-weight: bolder">
                        {{ model.name }}
                      </div>
                      <div style="font-size: 14px; margin-top: 5px">
                        <span>{{ model.unit }}</span>
                        <span style="margin-left: 10px">{{
                          model.symbol
                        }}</span>
                        <span
                          @click="updateModel(model)"
                          v-if="!model.isGlobal"
                          style="margin-left: 10px; color: #333"
                          ></span
                        >
                        <span
                          @click="deleteModel(model)"
                          v-if="!model.isGlobal"
                          style="margin-left: 10px; color: red"
                          ></span
                        >
                      </div>
                    </div>
                  </el-col>
                </el-row>
              </el-tooltip>
            </div>
          </div>
        </el-col>
        <el-col :span="18">
          <div style="padding: 0 150px; box-sizing: border-box">
            <div style="padding: 15px 0; font-size: 24px">
              
              <span
                @click="clearData"
                style="font-size: 14px; margin-left: 20px"
                ></span
              >
            </div>
            <el-row>
              <el-row v-if="selectedModel.length === 0">
                <el-empty description=""></el-empty>
              </el-row>
              <el-row>
                <el-col
                  :span="12"
                  v-for="(model, index) in selectedModel"
                  :key="index"
                >
                  <h3>{{ model.name }}({{ model.unit }})</h3>
                  <input
                    type="text"
                    v-model="model.value"
                    class="input-model"
                    :placeholder="'' + model.valueRange"
                  />
                </el-col>
              </el-row>
            </el-row>
          </div>
          <div style="padding: 50px 150px">
            <span
              @click="toRecord"
              style="
                cursor: pointer;
                padding: 10px 20px;
                background-color: #000;
                border-radius: 5px;
                color: #fff;
              "
            >
              
              <el-icon><Right /></el-icon>
            </span>
          </div>
        </el-col>
      </el-row>
    </div>
    <el-dialog :show-close="false" v-model="dialogUserOperaion" width="26%">
      <template #title>
        <div>
          <p class="dialog-title">
            {{ !isOperation ? "" : "" }}
          </p>
        </div>
      </template>
      <div style="padding: 0 20px">
        <p>*</p>
        <!--  -->
        <el-row style="margin-top: 10px">
          <el-upload
            class="avatar-uploader"
            :action="$uploadUrl"
            :headers="$uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <img
              v-if="data.cover"
              :src="data.cover"
              style="height: 64px; width: 64px"
            />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-row>
        <!--  -->
        <el-row style="padding: 0 10px 0 0">
          <p>
            <span class="modelName">*</span>
          </p>
          <input class="input-title" v-model="data.name" placeholder="" />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 10px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input class="input-title" v-model="data.unit" placeholder="" />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 10px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input
            class="input-title"
            v-model="data.symbol"
            placeholder=""
          />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 20px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*,</span>
          </p>
          <input
            class="input-title"
            v-model="data.valueRange"
            placeholder=""
          />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 10px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <el-input
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 3 }"
            placeholder=""
            v-model="data.detail"
          >
          </el-input>
        </el-row>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            size="small"
            v-if="!isOperation"
            style="background-color: rgb(43, 121, 203); border: none"
            class="customer"
            type="info"
            @click="addOperation"
            ></el-button
          >
          <el-button
            size="small"
            v-else
            style="background-color: rgb(43, 121, 203); border: none"
            class="customer"
            type="info"
            @click="updateOperation"
            ></el-button
          >
          <el-button
            class="customer"
            size="small"
            style="background-color: rgb(241, 241, 241); border: none"
            @click="cannel()"
            ></el-button
          >
        </span>
      </template>
    </el-dialog>
  </div>
</template>
<script>
export default {
  data() {
    return {
      data: { cover: "" },
      userInfo: {},
      modelList: [],
      activeName: "first",
      userHealthModel: { isGlobal: true },
      dialogUserOperaion: false,
      isOperation: false,
      userId: null,
      selectedModel: [],
    };
  },
  created() {
    this.getUserInfo();
    this.getAllModelConfig();
    this.getUser();
  },
  methods: {
    async clearData() {
      const confirmed = await this.$swalConfirm({
        title: "",
        text: `,`,
        icon: "warning",
      });
      if (confirmed) {
        this.selectedModel = [];
      }
    },
    cannel() {
      this.data = {};
      this.dialogUserOperaion = false;
      this.isOperation = false;
      this.cover = "";
    },
    // 
    updateOperation() {
      this.$axios
        .put("/health-model-config/update", this.data)
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.dialogUserOperaion = false;
            this.isOperation = false;
            this.data = {};
            this.$swal.fire({
              title: "",
              text: "",
              icon: "success",
              showConfirmButton: false,
              timer: 1000,
            });
            // 
            this.getAllModelConfig();
          }
        });
    },
    // 
    updateModel(model) {
      this.data = model;
      this.dialogUserOperaion = true;
      this.isOperation = true;
    },
    // 
    async deleteModel(model) {
      const confirmed = await this.$swalConfirm({
        title: "" + model.name + "",
        text: ``,
        icon: "warning",
      });
      if (confirmed) {
        const ids = [];
        ids.push(model.id);
        // 
        this.$axios
          .post("/health-model-config/batchDelete", ids)
          .then((response) => {
            const { data } = response;
            if (data.code === 200) {
              this.$swal.fire({
                title: "",
                text: "",
                icon: "success",
                showConfirmButton: false,
                timer: 1000,
              });
              // 
              this.getAllModelConfig();
              // 
              this.selectedModel = this.selectedModel.filter(
                (entity) => entity.id !== model.id
              );
            }
          });
      }
    },
    goBack() {
      this.$router.push("/user/news-record");
    },
    // 
    toRecord() {
      const userHealths = this.selectedModel.map((entity) => {
        return {
          healthModelConfigId: entity.id,
          value: entity.value,
        };
      });
      this.$axios.post("/user-health/save", userHealths).then((response) => {
        const { data } = response;
        if (data.code === 200) {
          this.$notify({
            title: "",
            message: "",
            type: "success",
          });
          // 
          setTimeout(() => {
            this.$router.push("/user/news-record");
          }, 2000);
        }
      });
    },
    modelSelected(model) {
      const saveFlag = this.selectedModel.find(
        (entity) => entity.id === model.id
      );
      if (!saveFlag) {
        // 
        this.selectedModel.push(model);
      }
    },
    searModel() {
      this.getAllModelConfig();
    },
    handleFilterClear() {
      this.userHealthModel.name = "";
      this.getAllModelConfig();
    },
    handleAvatarSuccess(res, file) {
      if (res.code !== 200) {
        this.$message.error(``);
        return;
      }
      this.$message.success(``);
      this.data.cover = res.data;
    },
    getUser() {
      const userInfo = sessionStorage.getItem("userInfo");
      const entity = JSON.parse(userInfo);
      this.userId = entity.id;
    },
    async addOperation() {
      try {
        this.data.userId = this.userId;
        const response = await this.$axios.post(
          "/health-model-config/save",
          this.data
        );
        this.$message[response.data.code === 200 ? "success" : "error"](
          response.data.msg
        );
        if (response.data.code === 200) {
          this.dialogUserOperaion = false;
          this.getAllModelConfig();
          this.data = {};
        }
      } catch (error) {
        console.error(":", error);
        this.$message.error("");
      }
    },
    addModel() {
      this.dialogUserOperaion = true;
    },
    handleClick(pane) {
      // Element Plus 2.x: tab-click receives { props, paneName, index, ... }
      const tabName = pane.paneName || pane.props?.name || this.activeName;
      // 
      this.userHealthModel = {};
      if (tabName === "first") {
        this.userHealthModel.isGlobal = true;
      } else {
        const userInfo = sessionStorage.getItem("userInfo");
        const entity = JSON.parse(userInfo);
        this.userHealthModel.userId = entity.id;
      }
      this.getAllModelConfig();
    },
    getAllModelConfig() {
      this.$axios
        .post("/health-model-config/modelList")
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.modelList = data.data;
          }
        });
    },
    getUserInfo() {
      const userInfo = sessionStorage.getItem("userInfo");
      this.userInfo = JSON.parse(userInfo);
    },
  },
};
</script>
<style scoped lang="scss">
.item-model:hover {
  cursor: pointer;
  background-color: #fafafa;
  border-radius: 5px;
}

.item-model {
  padding: 8px;
  box-sizing: border-box;
}

.input-model {
  font-size: 20px;
  box-sizing: border-box;
  font-weight: bold;
  padding: 20px;
  user-select: none;
  border-radius: 5px;
  border: none;
  outline: none;
  background-color: #f1f1f1;
  height: 50px;
  width: 85%;
}
</style>
