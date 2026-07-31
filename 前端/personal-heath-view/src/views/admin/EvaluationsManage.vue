<template>
  <el-row
    style="background-color: #ffffff; padding: 10px 0; border-radius: 5px"
  >
    <el-row style="padding: 10px; margin: 0 10px">
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
          v-model="evalustionsQueryDto.content"
          placeholder=""
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
        style="width: 100%"
      >
        <el-table-column prop="content" label="">
          <template #default="{ row }">
            <el-tooltip
              class="item"
              effect="dark"
              :content="row.content"
              placement="bottom"
            >
              <div class="cell-name">{{ row.content }}</div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="upvoteList" width="60" label="">
          <template #default="{ row }">
            <span
              v-if="row.upvoteList !== null"
              style="font-size: 16px; font-weight: bolder"
              >{{ row.upvoteList.split(",").length }}</span
            >
            <span v-else style="font-size: 16px; font-weight: bolder">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="contentType" width="100" label="">
          <template #default="{ row }">
            <span>{{ row.contentType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" width="168" label="">
          <template #default="{ row }">
            <span>{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userName" width="120" label="">
          <template #default="{ row }">
            <span v-html="highlightKeyword(row.userName)"></span>
          </template>
        </el-table-column>
        <el-table-column prop="replierName" width="120" label="">
          <template #default="{ row }">
            <span v-html="highlightKeyword(row.replierName)"></span>
          </template>
        </el-table-column>
        <el-table-column prop="parentId" width="120" label="">
          <template #default="{ row }">
            <el-icon v-if="row.parentId === null" style="margin-right: 5px"
              ><Warning
            /></el-icon>
            <el-icon v-else style="margin-right: 5px; color: rgb(253, 199, 50)"
              ><SuccessFilled
            /></el-icon>
            <span
              v-if="row.parentId === null"
              style="text-decoration: underline; text-decoration-style: dashed"
              ></span
            >
            <span v-else></span>
          </template>
        </el-table-column>
        <el-table-column label="" width="100px" fixed="right">
          <template #default="{ row }">
            <span class="text-button" @click="handleDelete(row)"></span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin: 10px 0"
        v-model:current-page="currentPage"
        :page-sizes="[20, 50]"
        v-model:page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
      ></el-pagination>
    </el-row>
    <!--  -->
    <el-dialog title="" :show-close="false" v-model="reportDialog" width="35%">
      <template #title>
        <div style="padding: 25px 0 0 20px">
          <span style="font-size: 18px; font-weight: 800"></span>
        </div>
      </template>
      <el-row style="padding: 10px 20px 20px 20px">
        <el-col :span="12">
          <PieChart :types="types" :values="values" />
        </el-col>
        <el-col :span="12">
          <el-row class="main">
            <div v-if="!reportsDate.length">
              <span class="count"></span>
            </div>
            <el-col
              :span="6"
              class="item"
              v-for="(entity, index) in reportsDate"
              :key="index"
            >
              <div class="count" style="margin-top: 20px">
                <span>
                  {{ entity.count }}
                </span>
              </div>
              <div>{{ entity.name }}</div>
            </el-col>
          </el-row>
        </el-col>
      </el-row>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            class="customer"
            size="small"
            style="background-color: rgb(241, 241, 241); border: none"
            @click="reportDialog = false"
            ></el-button
          >
        </span>
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import PieChart from "@/components/PieChart";
export default {
  components: {
    PieChart,
  },
  data() {
    return {
      data: { cover: "" },
      reportsDate: [],
      filterText: "",
      tableData: [],
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      dialogOperaion: false,
      isOperation: false,
      searchTime: [],
      selectedRows: [],
      html: {},
      fileList: [],
      dynamicTags: ["", ""],
      inputVisible: false,
      inputValue: "",
      coverDialog: false,
      commentDialog: false,
      evaluationsList: [],
      count: 0,
      evaluationsDelete: false,
      idToDelete: null,
      contentDialog: false,
      reportDialog: false,
      types: [],
      values: [],
      evalustionsQueryDto: {},
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
    keySearch() {
      this.fetchFreshData();
    },
    reportsList(row) {
      this.reportDialog = true;
      this.reportList(row.id);
    },
    async reportList(id) {
      const response = await this.$axios(
        `/evaluations-reports/reportCount/${id}`
      );
      const { data } = response;
      this.reportsDate = data.data;
      this.types = this.reportsDate.map((entity) => entity.name);
      this.values = this.reportsDate.map((entity) => entity.count);
    },
    showContent(news) {
      this.data = news;
      this.contentDialog = true;
    },
    async removeEvaluations(id) {
      this.evaluationsDelete = true;
      this.idToDelete = id;
    },
    confirmDelete() {
      this.$axios
        .delete(`/evaluations/delete/${this.idToDelete}`)
        .then((response) => {
          this.$message[response.data.code === 200 ? "success" : "error"](
            response.data.msg
          );
          if (response.data.code === 200) {
            this.evaluationsDelete = false;
            this.loadEvaluationsList();
          }
        })
        .catch((error) => {
          console.log(error);
        });
    },
    handleEvaluationsClose() {
      this.commentDialog = false;
    },
    // 
    commentList(news) {
      this.data = news;
      this.commentDialog = true;
      this.loadEvaluationsList();
    },
    // 
    async loadEvaluationsList() {
      try {
        const response = await this.$axios.get(
          `/evaluations/list/${this.data.id}/evaluations`
        );
        this.evaluationsList = response.data.data.data;
        this.count = response.data.data.count;
      } catch (error) {
        console.error(``, error);
      }
    },
    // 
    showPic(news) {
      this.data = news;
      this.coverDialog = true;
    },
    handleClose(tag) {
      this.dynamicTags.splice(this.dynamicTags.indexOf(tag), 1);
    },
    showInput() {
      this.inputVisible = true;
      this.$nextTick((_) => {
        this.$refs.saveTagInput.$refs.input.focus();
      });
    },
    handleInputConfirm() {
      let inputValue = this.inputValue;
      if (inputValue) {
        this.dynamicTags.push(inputValue);
      }
      this.inputVisible = false;
      this.inputValue = "";
    },
    goBack() {
      this.data = {};
      this.dialogOperaion = false;
    },
    goBackPage() {
      this.data = {};
      this.commentDialog = false;
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
            `/evaluations/batchDelete`,
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
    // 
    highlightKeyword(text) {
      if (text == null) {
        return;
      }
      if (this.filterText != "") {
        const regex = new RegExp(this.filterText, "gi");
        return text.replace(
          regex,
          '<span class="highlight" style="background-color: #bec936;color:#373834;">$&</span>'
        );
      }
      return text;
    },
    resetQueryCondition() {
      this.filterText = "";
      this.searchTime = "";
      this.fetchFreshData();
    },
    async updateOperation() {
      try {
        const response = await this.$axios.put(
          "/evaluations/update",
          this.data
        );
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
    async addOperation() {
      this.data.tag = this.dynamicTags.join(",");
      try {
        const response = await this.$axios.post("/evaluations/save", this.data);
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
    // 
    closeDialog() {
      this.dialogOperaion = false;
    },
    // 
    clearFormData() {
      this.data = {};
      this.html = "";
      this.fileList = [];
    },
    async fetchFreshData() {
      try {
        let startTime = "";
        let endTime = "";
        if (this.searchTime !== null && this.searchTime.length === 2) {
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
          ...this.evalustionsQueryDto,
        };
        // await
        let response = await this.$axios.post("/evaluations/query", params);
        const { data } = response;
        this.tableData = data.data;
        this.totalItems = data.total;
      } catch (error) {
        // 
        console.error("Fetch data failed:", error);
      }
    },
    add() {
      this.dialogOperaion = true;
    },
    handleFilter() {
      this.currentPage = 1;
      this.fetchFreshData();
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
      this.dialogOperaion = true;
      this.isOperation = true;
      this.data = row;
      console.log(this.data);
    },
    async handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },
  },
};
</script>
<style scoped lang="scss">
.main {
  padding: 50px 0;
  border-radius: 3px;
  display: flex;
  justify-content: center;

  .item:last-child {
    border-right: none;
  }

  .count,
  .tag {
    text-align: center;
  }

  .count {
    height: 30px;
    line-height: 30px;
    font-size: 26px;
    font-weight: bold;
    color: rgb(120 102 102);
  }

  .tag {
    height: 30px;
    line-height: 30px;
    font-size: 12px;
    color: rgb(120 102 102);
  }
}

.tag {
  color: #1e80ff;
  background-color: #eaf2ff;
  padding: 3px 4px;
  border-radius: 3px;
  font-size: 10px;
  margin-right: 5px;
}

.tag1 {
  color: #1c1c1c;
  background-color: #d7d8b8;
  padding: 3px 4px;
  border-radius: 3px;
  font-size: 10px;
  margin-right: 5px;
}
</style>
