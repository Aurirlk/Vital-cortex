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
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        >
        </el-date-picker>
        <el-input
          size="small"
          style="width: 188px; margin-left: 5px; margin-right: 6px"
          v-model="evalustionsQueryDto.content"
          placeholder="搜索评价内容"
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
        <el-table-column prop="content" label="评价内容">
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
        <!-- 2026-10-10 修复：原先按 upvoteList（逗号串）长度算赞数，而该字段
             从无写入，列表恒显示 0。改读后端聚合出来的 upvoteCount。 -->
        <el-table-column prop="upvoteCount" width="60" label="点赞数">
          <template #default="{ row }">
            <span style="font-size: 16px; font-weight: bolder">{{
              row.upvoteCount || 0
            }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="contentType" width="100" label="类型">
          <template #default="{ row }">
            <span>{{ row.contentType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" width="168" label="创建时间">
          <template #default="{ row }">
            <span>{{ row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userName" width="120" label="用户名">
          <template #default="{ row }">
            <span v-html="highlightKeyword(row.userName)"></span>
          </template>
        </el-table-column>
        <el-table-column prop="replierName" width="120" label="回复人">
          <template #default="{ row }">
            <span v-html="highlightKeyword(row.replierName)"></span>
          </template>
        </el-table-column>
        <el-table-column prop="parentId" width="120" label="回复状态">
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
              >主评论</span
            >
            <span v-else>回复评论</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100px" fixed="right">
          <template #default="{ row }">
            <span class="text-button" @click="handleDelete(row)">删除</span>
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
    <!-- 2026-10-10 后端已无 evaluations-reports/reportCount 举报统计接口，举报统计入口/弹窗一并移除 -->
  </el-row>
</template>

<script>
export default {
  data() {
    return {
      data: { cover: "" },
      // 2026-10-10 举报统计接口不存在，reportsDate/types/values/reportDialog 已随功能移除
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
    // 2026-10-10 后端已无 evaluations-reports/reportCount 接口，举报统计方法（reportsList/reportList）整体移除
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
        this.$message(`请选择要删除的评价`);
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "确认删除",
        text: `确定要删除选中的评价吗？`,
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
              title: "删除成功",
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
            title: "删除失败",
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
    escapeHtml(text) {
      return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
    },
    escapeRegExp(text) {
      return String(text).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    },
    highlightKeyword(text) {
      if (text == null) {
        return "";
      }
      // 先转义用户数据，杜绝 v-html 存储型 XSS；再对转义后的文本做高亮包裹
      const safeText = this.escapeHtml(text);
      if (this.filterText != "") {
        const regex = new RegExp(this.escapeRegExp(this.filterText), "gi");
        return safeText.replace(
          regex,
          '<span class="highlight" style="background-color: #bec936;color:#373834;">$&</span>'
        );
      }
      return safeText;
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
        this.$message.error("操作失败");
      }
    },
    async addOperation() {
      this.data.tag = this.dynamicTags.join(",");
      try {
        const response = await this.$axios.post("/evaluations/insert", this.data);
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
        this.$message.error("操作失败");
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
