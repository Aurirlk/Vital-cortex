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
          v-model="userHealthQueryDto.userId"
          placeholder="ID"
          clearable
          @clear="handleFilterClear"
        >
          <template #append
            ><el-button @click="handleFilter"
              ><el-icon><Search /></el-icon></el-button
          ></template>
        </el-input>
      </el-row>
    </el-row>
    <el-row style="margin: 0 20px; border-top: 1px solid rgb(245, 245, 245)">
      <el-table
        row-key="id"
        @selection-change="handleSelectionChange"
        :data="tableData"
      >
        <el-table-column prop="name" width="88" label="">
          <template #default="{ row }">
            <el-icon v-if="!statusCheck(row)" style="margin-right: 5px"
              ><Warning
            /></el-icon>
            <el-icon v-else style="margin-right: 5px; color: rgb(253, 199, 50)"
              ><SuccessFilled
            /></el-icon>
            <el-tooltip
              v-if="!statusCheck(row)"
              class="item"
              effect="dark"
              content=""
              placement="bottom-end"
            >
              <span
                style="
                  text-decoration: underline;
                  text-decoration-style: dashed;
                "
                ></span
              >
            </el-tooltip>
            <span v-else></span>
          </template>
        </el-table-column>
        <el-table-column prop="value" width="148" label="" sortable>
          <template #default="{ row }">
            <span>{{ row.value }}({{ row.unit }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label=""></el-table-column>
        <el-table-column
          prop="valueRange"
          width="88"
          label=""
        ></el-table-column>
        <el-table-column prop="name" width="140" label="">
          <template #default="{ row }">
            <span
              ><el-icon style="margin-right: 3px"><Document /></el-icon
              >{{ row.name }}</span
            >
          </template>
        </el-table-column>
        <el-table-column prop="unit" width="88" label=""></el-table-column>
        <el-table-column
          prop="symbol"
          width="88"
          label=""
        ></el-table-column>
        <el-table-column
          prop="userId"
          width="108"
          label="ID"
          sortable
        ></el-table-column>
        <el-table-column
          prop="createTime"
          width="178"
          label=""
          sortable
        ></el-table-column>
        <el-table-column label="" width="80">
          <template #default="{ row }">
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
        <!--  -->
        <el-row style="margin-top: 20px">
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
              style="height: 44px; width: 44px"
            />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-row>
        <!-- -->
        <el-row style="padding: 0 20px 0 0">
          <p>
            <span class="modelName">*</span>
          </p>
          <input
            class="input-title"
            v-model="data.name"
            style="border-radius: 5px; background-color: #f1f1f1"
          />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 20px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input
            class="input-title"
            v-model="data.unit"
            style="border-radius: 5px; background-color: #f1f1f1"
          />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 20px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input
            class="input-title"
            v-model="data.symbol"
            style="border-radius: 5px; background-color: #f1f1f1"
          />
        </el-row>
        <!-- -->
        <el-row style="padding: 0 20px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <el-input
            style="border-radius: 5px; background-color: #f1f1f1"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 3 }"
            placeholder=""
            v-model="data.detail"
          >
          </el-input>
        </el-row>
        <!-- -->
        <el-row style="padding: 0 20px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <el-slider v-model="valuesRange" range show-stops :max="1000">
          </el-slider>
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
            @click="dialogUserOperaion = false"
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
      data: {},
      filterText: "",
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      dialogUserOperaion: false, //       isOperation: false, //       tableData: [],
      searchTime: [],
      selectedRows: [],
      status: null,
      userHealthQueryDto: {}, // 
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
    dialogUserOperaion(v1, v2) {
      if (!v1) {
        this.isOperation = !this.isOperation;
      }
      if (!v1 && v2) {
        this.data = {};
      }
    },
  },
  created() {
    this.fetchFreshData();
  },
  methods: {
    // 
    statusCheck(data) {
      // 
      const inputValue = data.value;
      // 
      const valueRange = data.valueRange;
      if (valueRange !== null && inputValue !== null) {
        const aryValueRange = valueRange.split(",");
        const minValue = aryValueRange[0];
        const maxValue = aryValueRange[1];
        return (
          Number(inputValue) > Number(minValue) &&
          Number(inputValue) < Number(maxValue)
        );
      }
    },
    handleAvatarSuccess(res, file) {
      if (res.code !== 200) {
        this.$message.error(``);
        return;
      }
      this.$message.success(``);
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
            `/user-health/batchDelete`,
            ids
          );
          if (response.data.code === 200) {
            this.$swal.fire({
              title: "",
              text: response.data.msg,
              icon: "success",
              showConfirmButton: false,
              timer: 2000,
            });
            this.fetchFreshData();
            return;
          }
        } catch (e) {
          this.$swal.fire({
            title: "",
            text: e,
            icon: "error",
            showConfirmButton: false,
            timer: 2000,
          });
          console.error(``, e);
        }
      }
    },
    resetQueryCondition() {
      this.userHealthQueryDto = {};
      this.searchTime = [];
      this.fetchFreshData();
    },
    // 
    async updateOperation() {
      try {
        this.data.valueRange = this.valuesRange.join(",");
        const response = await this.$axios.put(
          "/user-health/update",
          this.data
        );
        this.$swal.fire({
          title: "",
          text: response.data.msg,
          icon: response.data.code === 200 ? "success" : "error",
          showConfirmButton: false,
          timer: 1000,
        });
        if (response.data.code === 200) {
          this.closeDialog();
          this.fetchFreshData();
          this.clearFormData();
        }
      } catch (error) {
        console.error("", error);
        this.$message.error("");
      }
    },
    // 
    async addOperation() {
      try {
        // [20,252] ---> 20,252
        this.data.valueRange = this.valuesRange.join(",");
        const response = await this.$axios.post("/user-health/save", this.data);
        this.$message[response.data.code === 200 ? "success" : "error"](
          response.data.msg
        );
        if (response.data.code === 200) {
          this.closeDialog();
          this.fetchFreshData();
          this.clearFormData();
        }
      } catch (error) {
        console.error("", error);
        this.$message.error("");
      }
    },
    closeDialog() {
      this.dialogUserOperaion = false;
    },
    clearFormData() {
      this.data = {};
    },
    async fetchFreshData() {
      try {
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
          ...this.userHealthQueryDto,
        };
        const response = await this.$axios.post("/user-health/query", params);
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
      if (row.valueRange !== null) {
        this.valuesRange = row.valueRange.split(",");
      }
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
.status-success {
  display: inline-block;
  padding: 1px 5px;
  border-radius: 2px;
  background-color: rgb(201, 237, 249);
  color: rgb(111, 106, 196);
  font-size: 12px;
}

.status-error {
  display: inline-block;
  padding: 1px 5px;
  border-radius: 2px;
  background-color: rgb(233, 226, 134);
  color: rgb(131, 138, 142);
  color: rgb(111, 106, 196);
  font-size: 12px;
}

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
