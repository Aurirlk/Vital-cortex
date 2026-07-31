<template>
  <el-row style="background-color: #ffffff; padding: 5px 0; border-radius: 5px">
    <el-row style="padding: 10px; margin-left: 5px">
      <el-row>
        <el-input
          size="small"
          style="width: 188px; margin-left: 5px; margin-right: 6px"
          v-model="healthModelConfigQueryDto.name"
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
        row-key="id"
        @selection-change="handleSelectionChange"
        :data="tableData"
        style="width: 100%"
      >
        <el-table-column prop="cover" width="80" label="">
          <template #default="{ row }">
            <img
              :src="row.cover"
              style="width: 30px; height: 30px; border-radius: 5px"
            />
          </template>
        </el-table-column>
        <el-table-column prop="name" width="218" label=""></el-table-column>
        <el-table-column prop="isGlobal" label="" width="128">
          <template #default="{ row }">
            <span>{{ row.isGlobal ? "" : "" }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="userName"
          width="108"
          label=""
        ></el-table-column>
        <el-table-column
          prop="valueRange"
          width="128"
          label=""
        ></el-table-column>
        <el-table-column prop="unit" width="88" label=""></el-table-column>
        <el-table-column
          prop="symbol"
          width="88"
          label=""
        ></el-table-column>
        <el-table-column prop="detail" label=""></el-table-column>
        <el-table-column label="" width="120">
          <template #default="{ row }">
            <span class="text-button" @click="handleEdit(row)"></span>
            <span class="text-button" @click="handleDelete(row)"></span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin: 20px 0"
        v-model:current-page="currentPage"
        :page-sizes="[10, 20]"
        v-model:page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
      ></el-pagination>
    </el-row>
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
        <!-- -->
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
        <!-- -->
        <el-row style="padding: 0 20px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*-</span>
          </p>
          <input
            class="input-title"
            v-model="data.valueRange"
            placeholder=""
          />
        </el-row>
        <!-- -->
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
  </el-row>
</template>

<script>
export default {
  data() {
    return {
      data: { cover: "" },
      filterText: "",
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      dialogUserOperaion: false, //       isOperation: false, //       tableData: [],
      searchTime: [],
      selectedRows: [],
      status: null,
      healthModelConfigQueryDto: {}, // 
      messsageContent: "",
      tagsList: [],
      valuesRange: [10, 50],
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
    handleAvatarSuccess(res, file) {
      this.$notify({
        duration: 2000,
        title: "",
        message: res.code === 200 ? "" : "",
        type: res.code === 200 ? "success" : "error",
      });
      this.data.cover = res.data;
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
          const response = await this.$axios.post(
            `/health-model-config/batchDelete`,
            ids
          );
          if (response.data.code === 200) {
            this.$notify({
              duration: 2000,
              title: "",
              message: "",
              type: "success",
            });
            this.fetchFreshData();
            return;
          }
        } catch (e) {
          console.error(``, e);
        }
      }
    },
    resetQueryCondition() {
      this.healthModelConfigQueryDto = {};
      this.searchTime = [];
      this.fetchFreshData();
    },
    // 
    async updateOperation() {
      this.$axios
        .put("/health-model-config/update", this.data)
        .then((res) => {
          if (res.data.code === 200) {
            this.cannel();
            this.fetchFreshData();
            this.$notify({
              duration: 2000,
              title: "",
              message: "",
              type: "success",
            });
          }
        })
        .catch((error) => {
          console.log("=>", error);
        });
    },
    cannel() {
      this.dialogUserOperaion = false;
      this.isOperation = false;
      this.data = {};
      this.valueRange = null;
    },
    // 
    addOperation() {
      this.$axios
        .post("/health-model-config/config/save", this.data)
        .then((res) => {
          if (res.data.code === 200) {
            this.cannel();
            this.fetchFreshData();
            this.$notify({
              duration: 2000,
              title: "",
              message: "",
              type: "success",
            });
          }
        })
        .catch((error) => {
          console.log("=>", error);
        });
    },
    // 
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
          startTime: startTime,
          endTime: endTime,
          ...this.healthModelConfigQueryDto,
        };
        const response = await this.$axios.post(
          "/health-model-config/query",
          params
        );
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
      this.data = { ...row };
    },
    handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },
  },
};
</script>
<style scoped lang="scss"></style>
