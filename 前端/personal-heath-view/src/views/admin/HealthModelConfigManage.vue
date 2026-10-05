<template>
  <el-row style="background-color: #ffffff; padding: 5px 0; border-radius: 5px">
    <el-row style="padding: 10px; margin-left: 5px">
      <el-row>
        <el-input
          size="small"
          style="width: 188px; margin-left: 5px; margin-right: 6px"
          v-model="healthModelConfigQueryDto.name"
          placeholder="请输入指标名称"
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
            ><el-icon><Plus /></el-icon
          ></el-button>
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
        <el-table-column prop="cover" width="80" label="图标">
          <template #default="{ row }">
            <img
              :src="row.cover"
              style="width: 30px; height: 30px; border-radius: 5px"
            />
          </template>
        </el-table-column>
        <el-table-column
          prop="name"
          width="218"
          label="指标名称"
        ></el-table-column>
        <el-table-column prop="isGlobal" label="适用范围" width="128">
          <template #default="{ row }">
            <span>{{ row.isGlobal ? "全局" : "个人" }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="userName"
          width="108"
          label="创建人"
        ></el-table-column>
        <el-table-column
          prop="valueRange"
          width="128"
          label="正常范围"
        ></el-table-column>
        <el-table-column prop="unit" width="88" label="单位"></el-table-column>
        <el-table-column
          prop="symbol"
          width="88"
          label="符号"
        ></el-table-column>
        <el-table-column prop="detail" label="描述"></el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <span class="text-button" @click="handleEdit(row)">编辑</span>
            <span class="text-button" @click="handleDelete(row)">删除</span>
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
            {{ !isOperation ? "新增健康指标" : "编辑健康指标" }}
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
          <input
            class="input-title"
            v-model="data.name"
            placeholder="请输入指标名称"
          />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 10px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input
            class="input-title"
            v-model="data.unit"
            placeholder="请输入单位"
          />
        </el-row>
        <!--  -->
        <el-row style="padding: 0 10px 0 0">
          <p style="font-size: 12px; padding: 3px 0">
            <span class="modelName">*</span>
          </p>
          <input
            class="input-title"
            v-model="data.symbol"
            placeholder="请输入符号"
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
            placeholder="请输入正常范围"
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
            placeholder="请输入指标描述"
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
            >新增</el-button
          >
          <el-button
            size="small"
            v-else
            style="background-color: rgb(43, 121, 203); border: none"
            class="customer"
            type="info"
            @click="updateOperation"
            >修改</el-button
          >
          <el-button
            class="customer"
            size="small"
            style="background-color: rgb(241, 241, 241); border: none"
            @click="cannel()"
            >取消</el-button
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
      allData: [],
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
      this.applyLocalPage();
    },
    pageSize() {
      this.currentPage = 1;
      this.applyLocalPage();
    },
  },
  created() {
    this.fetchFreshData();
  },
  methods: {
    handleAvatarSuccess(res, file) {
      this.$notify({
        duration: 2000,
        title: "上传结果",
        message: res.code === 200 ? "封面上传成功" : "封面上传失败",
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
        this.$message(`请选择要删除的指标`);
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "确认删除",
        text: `您确定要删除选中的健康指标吗？`,
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
              title: "删除成功",
              message: "健康指标删除成功",
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
              title: "更新成功",
              message: "健康指标更新成功",
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
              title: "添加成功",
              message: "健康指标添加成功",
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
          current: 1,
          size: 1000,
          startTime: startTime,
          endTime: endTime,
          ...this.healthModelConfigQueryDto,
        };
        const response = await this.$axios.post(
          "/health-model-config/query",
          params
        );
        const { data } = response;
        const list = Array.isArray(data.data) ? data.data : [];
        this.allData = list;
        this.totalItems = list.length;
        this.applyLocalPage();
      } catch (error) {
        console.error("加载健康指标失败:", error);
        this.$message.error("加载健康指标失败");
      }
    },
    applyLocalPage() {
      const start = (this.currentPage - 1) * this.pageSize;
      const end = start + this.pageSize;
      this.tableData = this.allData.slice(start, end);
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
      this.applyLocalPage();
    },
    handleCurrentChange(val) {
      this.currentPage = val;
      this.applyLocalPage();
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
