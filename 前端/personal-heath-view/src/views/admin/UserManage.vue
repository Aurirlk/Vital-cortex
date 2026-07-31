<template>
  <el-row style="background-color: #ffffff; padding: 5px 0; border-radius: 5px">
    <el-row style="padding: 10px; margin-left: 10px">
      <el-row>
        <el-date-picker
          size="small"
          style="width: 220px"
          v-model="searchTime"
          type="daterange"
          range-separator=""
          start-placeholder=""
          end-placeholder=""
        >
        </el-date-picker>
        <el-input
          size="small"
          style="width: 188px; margin-left: 5px; margin-right: 6px"
          v-model="userQueryDto.userName"
          placeholder=""
          clearable
          @clear="handleFilterClear"
        >
          <template #append
            ><el-button @click="handleFilter"
              ><el-icon><Search /></el-icon></el-button
          ></template>
        </el-input>
        <span style="float: right">
          <el-button
            size="small"
            style="
              background-color: rgb(96, 98, 102);
              color: rgb(247, 248, 249);
              border: none;
            "
            class="customer"
            type="info"
            @click="add()"
            ><el-icon><Plus /></el-icon></el-button
          >
        </span>
      </el-row>
    </el-row>
    <el-row style="margin: 0 20px; border-top: 1px solid rgb(245, 245, 245)">
      <el-table
        @selection-change="handleSelectionChange"
        :data="tableData"
        style="width: 100%"
        :header-cell-style="{ fontWeight: 600, color: '#606266' }"
      >
        <el-table-column prop="userAvatar" width="60" label="" align="center">
          <template #default="{ row }">
            <el-avatar
              :size="32"
              :src="row.userAvatar"
            ></el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="" min-width="100"></el-table-column>
        <el-table-column
          prop="userAccount"
          min-width="110"
          label=""
        ></el-table-column>
        <el-table-column
          prop="userEmail"
          min-width="160"
          label=""
          show-overflow-tooltip
        ></el-table-column>
        <el-table-column prop="userRole" width="72" label="" align="center">
          <template #default="{ row }">
            <span>{{ row.userRole === 1 ? "" : "" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isLogin" width="80" label="" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.isLogin" style="margin-right: 3px; color: #e6a23c"
              ><Warning
            /></el-icon>
            <el-icon v-else style="margin-right: 3px; color: #67c23a"
              ><SuccessFilled
            /></el-icon>
            <el-tooltip
              v-if="row.isLogin"
              class="item"
              effect="dark"
              content=""
              placement="bottom-end"
            >
              <span style="text-decoration: underline; text-decoration-style: dashed; font-size: 13px"></span>
            </el-tooltip>
            <span v-else style="font-size: 13px"></span>
          </template>
        </el-table-column>
        <el-table-column prop="isWord" width="80" label="" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.isWord" style="margin-right: 3px; color: #e6a23c"
              ><Warning
            /></el-icon>
            <el-icon v-else style="margin-right: 3px; color: #67c23a"
              ><SuccessFilled
            /></el-icon>
            <el-tooltip
              v-if="row.isWord"
              class="item"
              effect="dark"
              content=""
              placement="bottom-end"
            >
              <span style="text-decoration: underline; text-decoration-style: dashed; font-size: 13px"></span>
            </el-tooltip>
            <span v-else style="font-size: 13px"></span>
          </template>
        </el-table-column>
        <el-table-column
          :sortable="true"
          prop="createTime"
          min-width="155"
          label=""
        ></el-table-column>
        <el-table-column label="" min-width="160" fixed="right">
          <template #default="{ row }">
            <span class="text-button" @click="handleStatus(row)"></span>
            <span class="text-button" @click="handleEdit(row)"></span>
            <span class="text-button" @click="handleDelete(row)"></span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin: 10px 0"
        v-model:current-page="currentPage"
        :page-sizes="[10, 20]"
        v-model:page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
      ></el-pagination>
    </el-row>
    <!--  -->
    <el-dialog :show-close="false" v-model="dialogUserOperaion" width="25%">
      <template #title>
        <div>
          <p class="dialog-title">
            {{ !isOperation ? "" : "" }}
          </p>
        </div>
      </template>
      <div style="padding: 0 20px">
        <el-row>
          <el-upload
            class="avatar-uploader"
            :action="$uploadUrl"
            :headers="$uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <img v-if="userAvatar" :src="userAvatar" class="dialog-avatar" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-row>
        <el-row>
          <span class="dialog-hover"></span>
          <input
            class="dialog-input"
            v-model="data.userName"
            placeholder=""
          />
          <span class="dialog-hover"></span>
          <input
            class="dialog-input"
            v-model="data.userAccount"
            placeholder=""
          />
          <span class="dialog-hover"></span>
          <input
            class="dialog-input"
            v-model="data.userEmail"
            placeholder=""
          />
          <span class="dialog-hover"></span>
          <input
            class="dialog-input"
            v-model="userPwd"
            type="password"
            placeholder=""
          />
        </el-row>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            size="small"
            v-if="!isOperation"
            style="
              background-color: rgb(96, 98, 102);
              color: rgb(247, 248, 249);
              border: none;
            "
            class="customer"
            type="info"
            @click="addOperation()"
            ></el-button
          >
          <el-button
            size="small"
            v-else
            style="
              background-color: rgb(96, 98, 102);
              color: rgb(247, 248, 249);
              border: none;
            "
            class="customer"
            type="info"
            @click="updateOperation()"
            ></el-button
          >
          <el-button
            class="customer"
            size="small"
            style="background-color: rgb(211, 241, 241); border: none"
            @click="cannel"
            ></el-button
          >
        </span>
      </template>
    </el-dialog>
    <el-dialog :show-close="false" v-model="dialogStatusOperation" width="25%">
      <template #title>
        <div>
          <p class="dialog-title"></p>
        </div>
      </template>
      <div style="padding: 0 20px">
        <el-row>
          <el-switch
            active-color="rgb(230, 62, 49)"
            inactive-color="rgb(246,246,246)"
            v-model="data.isLogin"
            active-text=""
            inactive-text=""
          >
          </el-switch>
        </el-row>
        <el-row style="margin: 20px 0">
          <el-switch
            active-color="rgb(230, 62, 49)"
            inactive-color="rgb(246,246,246)"
            v-model="data.isWord"
            active-text=""
            inactive-text=""
          >
          </el-switch>
        </el-row>
        <span class="dialog-hover"></span>
        <el-switch
          v-model="roleStatus"
          active-color="rgb(230, 62, 49)"
          inactive-color="rgb(246,246,246)"
        >
        </el-switch>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            size="small"
            style="
              background-color: rgb(96, 98, 102);
              color: rgb(247, 248, 249);
              border: none;
            "
            class="customer"
            type="info"
            @click="comfirmStatus"
            ></el-button
          >
          <el-button
            class="customer"
            size="small"
            style="background-color: rgb(241, 241, 241); border: none"
            @click="cannel"
            ></el-button
          >
        </span>
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
export default {
  data() {
    return {
      roleStatus: false,
      userPwd: "",
      userAvatar: "",
      data: {},
      filterText: "",
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      dialogStatusOperation: false,
      dialogUserOperaion: false,
      isOperation: false,
      tableData: [],
      searchTime: [],
      selectedRows: [],
      status: null,
      userQueryDto: {}, // 
      messsageContent: "",
    };
  },
  watch: {
    currentPage() {
      this.fetchFreshData();
    },
    pageSize() {
      this.fetchFreshData();
    },
  },
  created() {
    this.fetchFreshData();
  },
  methods: {
    comfirmStatus() {
      const userUpdateDto = {
        id: this.data.id,
        isLogin: this.data.isLogin,
        isWord: this.data.isWord,
        userRole: this.roleStatus ? 1 : 2,
      };
      this.$axios
        .put(`/user/backUpdate`, userUpdateDto)
        .then((res) => {
          if (res.data.code === 200) {
            this.$notify({
              duration: 2000,
              title: "",
              message: "",
              type: "success",
            });
            this.dialogStatusOperation = false;
            this.fetchFreshData();
          }
        })
        .catch((error) => {
          console.log("" + error);
        });
    },
    handleStatus(data) {
      this.dialogStatusOperation = true;
      this.roleStatus = data.userRole === 1;
      this.data = data;
    },
    handleAvatarSuccess(res, file) {
      if (res.code !== 200) {
        this.$notify({
          duration: 2000,
          title: "",
          message: "",
          type: "error",
        });
        return;
      }
      this.$notify({
        duration: 2000,
        title: "",
        message: "",
        type: "success",
      });
      this.userAvatar = res.data;
    },
    switchChange() {
      this.fetchFreshData();
    },
    async handleSwitchChange(id, status, operation) {
      try {
        let param = { id: id };
        // 
        if (operation) {
          param.isLogin = status;
        } else {
          // 
          param.isWord = status;
        }
        const response = await this.$axios.put(`/user/backUpdate`, param);
        if (response.data.code === 200) {
          this.$notify({
            duration: 2000,
            title: "",
            message: "",
            type: "success",
          });
          this.cannel();
        }
      } catch (e) {
        console.error(`${e}`);
      }
    },
    // 
    handleSelectionChange(selection) {
      this.selectedRows = selection;
    },
    // 
    async batchDelete() {
      if (!this.selectedRows.length) {
        this.$message(``);
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "",
        text: ``,
        icon: "warning",
      });
      if (confirmed) {
        try {
          let ids = this.selectedRows.map((entity) => entity.id);
          const response = await this.$axios.post(`/user/batchDelete`, ids);
          if (response.data.code === 200) {
            this.$notify({
              duration: 2000,
              title: "",
              message: "",
              type: "success",
            });
            this.cannel();
            this.fetchFreshData();
            return;
          }
        } catch (e) {
          console.error(``, e);
        }
      }
    },
    resetQueryCondition() {
      this.userQueryDto = {};
      this.searchTime = [];
      this.fetchFreshData();
    },
    // 
    async updateOperation() {
      if (this.userPwd !== "") {
        const pwd = this.$md5(this.$md5(this.userPwd));
        this.data.userPwd = pwd;
      } else {
        this.data.userPwd = null;
      }
      this.data.userAvatar = this.userAvatar;
      try {
        const response = await this.$axios.put("/user/backUpdate", this.data);
        if (response.data.code === 200) {
          this.fetchFreshData();
          this.cannel();
          this.$notify({
            duration: 2000,
            title: "",
            message: "",
            type: "success",
          });
        }
      } catch (error) {
        console.error("", error);
        this.$message.error("");
      }
    },
    // 
    async addOperation() {
      if (this.userPwd !== "") {
        this.data.userPwd = this.$md5(this.$md5(this.userPwd));
      } else {
        this.data.userPwd = null;
      }
      this.data.userAvatar = this.userAvatar;
      try {
        const response = await this.$axios.post("/user/insert", this.data);
        this.$message[response.data.code === 200 ? "success" : "error"](
          response.data.msg
        );
        if (response.data.code === 200) {
          this.fetchFreshData();
          this.cannel();
          this.$notify({
            duration: 2000,
            title: "",
            message: "",
            type: "success",
          });
        }
      } catch (error) {
        console.error("", error);
        this.$message.error("");
      }
    },
    cannel() {
      this.userAvatar = "";
      this.userPwd = "";
      this.data = {};
      this.isOperation = false;
      this.dialogStatusOperation = false;
      this.dialogUserOperaion = false;
    },
    async fetchFreshData() {
      try {
        this.tableData = [];
        let startTime = null;
        let endTime = null;
        if (this.searchTime != null && this.searchTime.length === 2) {
          const [startDate, endDate] = await Promise.all(
            this.searchTime.map((date) => date.toISOString())
          );
          startTime = `${startDate.split("T")[0]}T00:00:00`;
          endTime = `${endDate.split("T")[0]}T23:59:59`;
        }
        // 
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          key: this.filterText,
          startTime: startTime,
          endTime: endTime,
          ...this.userQueryDto,
        };
        const response = await this.$axios.post("/user/query", params);
        const { data } = response;
        this.tableData = data.data;
        this.totalItems = data.total;
      } catch (error) {
        console.error(":", error);
      }
    },
    add() {
      this.dialogUserOperaion = true;
    },
    handleFilter() {
      this.currentPage = 1;
      this.fetchFreshData();
    },
    handleFilterClear() {
      this.filterText = "";
      this.handleFilter();
    },
    handleSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchFreshData();
    },
    handleCurrentChange(val) {
      this.currentPage = val;
      this.fetchFreshData();
    },
    handleEdit(row) {
      this.dialogUserOperaion = true;
      this.isOperation = true;
      row.userPwd = null;
      this.userAvatar = row.userAvatar;
      this.data = { ...row };
    },
    handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },
  },
};
</script>
<style scoped lang="scss">
.tag-tip {
  display: inline-block;
  padding: 5px 10px;
  border-radius: 5px;
  background-color: rgb(245, 245, 245);
  color: rgb(104, 118, 130);
}

.input-def {
  height: 40px;
  line-height: 40px;
  outline: none;
  border: none;
  font-size: 20px;
  color: rgb(102, 102, 102);
  font-weight: 900;
  width: 100%;
}

.dialog-footer {
  /* */
  display: flex;
  justify-content: center;
  align-items: center;
}

/*  */
.customer {
  margin: 0 8px;
  /* */
}
</style>
