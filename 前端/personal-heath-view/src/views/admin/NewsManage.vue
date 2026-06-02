<template>
  <el-row style="background-color: #ffffff; padding: 20px; border-radius: 12px">
    <el-row style="padding: 15px 20px; margin-left: 10px">
      <el-row :gutter="16" align="middle">
        <el-col :span="4">
          <el-select
            @change="changeNewsTag"
            size="default"
            v-model="newsQueryDto.tagId"
            placeholder="资讯分类"
            style="width: 100%"
          >
            <el-option
              v-for="tag in tagsList"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            >
            </el-option>
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-date-picker
            size="default"
            style="width: 100%"
            v-model="searchTime"
            type="daterange"
            range-separator="至"
            start-placeholder="发布开始"
            end-placeholder="发布结束"
          >
          </el-date-picker>
        </el-col>
        <el-col :span="5">
          <el-input
            size="default"
            style="width: 100%"
            v-model="newsQueryDto.name"
            placeholder="资讯标题"
            clearable
            @clear="handleFilterClear"
          >
            <template #append
              ><el-button @click="handleFilter"
                ><el-icon><Search /></el-icon></el-button
            ></template>
          </el-input>
        </el-col>
        <el-col :span="4">
          <el-button
            size="default"
            style="
              background: linear-gradient(135deg, #667eea, #764ba2);
              color: #fff;
              border: none;
              height: 40px;
              padding: 0 24px;
              font-size: 14px;
              font-weight: 600;
              border-radius: 8px;
              box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
            "
            class="customer"
            type="info"
            @click="add()"
            ><el-icon style="margin-right: 4px"><Plus /></el-icon>新增资讯</el-button
          >
        </el-col>
      </el-row>
    </el-row>
    <el-row style="margin: 0 20px; border-top: 1px solid rgb(245, 245, 245)">
      <el-table
        row-key="id"
        @selection-change="handleSelectionChange"
        :data="tableData"
        style="width: 100%"
      >
        <el-table-column prop="cover" width="80" label="首图">
          <template #default="{ row }">
            <img
              :src="row.cover"
              style="width: 48px; height: 34px; border-radius: 5px"
            />
          </template>
        </el-table-column>
        <el-table-column prop="tagName" width="138" label="所属分类">
          <template #default="{ row }">
            <span
              ><el-icon style="margin-right: 3px"><Discount /></el-icon>
              {{ row.tagName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="isTop" width="128" label="是否推荐">
          <template #default="{ row }">
            <el-icon v-if="!row.isTop" style="margin-right: 5px"
              ><Warning
            /></el-icon>
            <el-icon v-else style="margin-right: 5px; color: rgb(253, 199, 50)"
              ><SuccessFilled
            /></el-icon>
            <el-tooltip
              v-if="!row.isTop"
              class="item"
              effect="dark"
              content="未推荐资讯，不做推荐展示"
              placement="bottom-end"
            >
              <span
                style="
                  cursor: pointer;
                  text-decoration: underline;
                  text-decoration-style: dashed;
                "
                >未推送</span
              >
            </el-tooltip>
            <span v-else>已推送</span>
          </template>
        </el-table-column>
        <el-table-column prop="isBanner" width="128" label="轮播图">
          <template #default="{ row }">
            <el-tag :type="row.isBanner ? 'success' : 'info'" size="small">
              {{ row.isBanner ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          width="168"
          label="发布时间"
        ></el-table-column>
        <el-table-column prop="name" label="标题"></el-table-column>
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
    <el-dialog :show-close="false" v-model="dialogUserOperaion" width="50%">
      <template #title>
        <div>
          <p class="dialog-title">
            {{ !isOperation ? "新增资讯" : "修改资讯" }}
          </p>
        </div>
      </template>
      <div style="padding: 0 20px">
        <!-- 封面 -->
        <el-row style="margin-top: 10px">
          <p>*封面</p>
          <div style="display: flex; align-items: flex-start; gap: 12px">
            <el-upload
              class="avatar-uploader"
              :action="$uploadUrl"
              :show-file-list="false"
              :on-success="handleAvatarSuccess"
            >
              <img
                v-if="data.cover"
                :src="data.cover"
                style="height: 120px; width: 188px"
              />
              <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
            </el-upload>
            <el-button
              size="small"
              style="margin-top: 90px"
              @click="showDefaultCoverDialog = true"
            >选择默认图片</el-button>
          </div>
        </el-row>
        <!-- 标题 -->
        <el-row>
          <p>*标题</p>
          <input
            style="font-size: 32px; line-height: 45px"
            class="dialog-input"
            v-model="data.name"
            placeholder="标题"
          />
        </el-row>
        <!-- 标签 -->
        <el-row style="margin: 12px 0">
          <el-row>
            <span class="dialog-hover">所属分类</span>
          </el-row>
          <el-radio-group style="margin-top: 10px" v-model="data.tagId">
            <el-radio
              :key="index"
              :label="tag.id"
              v-for="(tag, index) in tagsList"
              >{{ tag.name }}</el-radio
            >
          </el-radio-group>
        </el-row>
        <!-- 推荐 -->
        <el-row style="margin: 12px 0">
          <el-row>
            <span class="dialog-hover">是否推荐</span>
          </el-row>
          <el-switch
            style="user-select: none; padding: 0 6px"
            v-model="data.isTop"
            active-color="#13ce66"
            inactive-color="rgb(226, 226, 226)"
          >
          </el-switch>
        </el-row>
        <!-- 轮播图 -->
        <el-row style="margin: 12px 0">
          <el-row>
            <span class="dialog-hover">是否轮播图</span>
          </el-row>
          <el-switch
            style="user-select: none; padding: 0 6px"
            v-model="data.isBanner"
            active-color="#13ce66"
            inactive-color="rgb(226, 226, 226)"
          >
          </el-switch>
        </el-row>
        <el-row>
          <p>*正文描述</p>
          <Editor
            height="calc(100vh - 500px)"
            :receiveContent="data.content"
            @on-receive="onReceiveContent"
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
            >新增</el-button
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
            >修改</el-button
          >
          <el-button
            class="customer"
            size="small"
            style="background-color: rgb(241, 241, 241); border: none"
            @click="dialogUserOperaion = false"
            >取消</el-button
          >
        </span>
      </template>
    </el-dialog>
    <!-- 默认图片选择对话框 -->
    <el-dialog v-model="showDefaultCoverDialog" title="选择默认封面" width="600px">
      <div style="display: flex; flex-wrap: wrap; gap: 12px; padding: 10px">
        <div
          v-for="(cover, index) in defaultCovers"
          :key="index"
          style="cursor: pointer; border: 2px solid transparent; border-radius: 8px; overflow: hidden; transition: border-color 0.3s"
          :style="{ borderColor: data.cover === cover.url ? '#409eff' : 'transparent' }"
          @click="selectDefaultCover(cover.url)"
        >
          <img :src="cover.url" :alt="cover.name" style="width: 160px; height: 100px; object-fit: cover; display: block" />
        </div>
      </div>
      <template #footer>
        <el-button @click="showDefaultCoverDialog = false">取消</el-button>
        <el-button type="primary" @click="showDefaultCoverDialog = false">确定</el-button>
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import Editor from "@/components/Editor";
export default {
  components: { Editor },
  data() {
    return {
      userPwd: "",
      data: { cover: "" },
      filterText: "",
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      dialogUserOperaion: false,
      isOperation: false,
      tableData: [],
      searchTime: [],
      selectedRows: [],
      status: null,
      newsQueryDto: {}, // 搜索条件
      messsageContent: "",
      tagsList: [],
      showDefaultCoverDialog: false,
      defaultCovers: [
        { name: "健康饮食", url: "https://picsum.photos/seed/health1/400/250" },
        { name: "运动健身", url: "https://picsum.photos/seed/health2/400/250" },
        { name: "医疗健康", url: "https://picsum.photos/seed/health3/400/250" },
        { name: "心理健康", url: "https://picsum.photos/seed/health4/400/250" },
        { name: "养生保健", url: "https://picsum.photos/seed/health5/400/250" },
        { name: "健康生活", url: "https://picsum.photos/seed/health6/400/250" },
      ],
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
    this.loadAllTags();
  },
  methods: {
    changeNewsTag(tagId) {
      this.newsQueryDto.tagId = tagId;
      this.fetchFreshData();
    },
    onReceiveContent(html) {
      this.data.content = html;
    },
    // 加载全部的资讯数据
    loadAllTags() {
      this.$axios.post(`/tags/query`, {}).then((response) => {
        const { data } = response;
        if (data.code === 200) {
          this.tagsList = data.data;
          this.tagsList.unshift({ name: "全部", id: null });
        }
      });
    },
    handleAvatarSuccess(res, file) {
      if (res.code !== 200) {
        this.$message.error(`资讯封面上传异常`);
        return;
      }
      this.data.cover = "";
      this.$message.success(`资讯封面上传成功`);
      this.data.cover = res.data;
    },
    // 多选框选中
    handleSelectionChange(selection) {
      this.selectedRows = selection;
    },
    // 批量删除数据
    async batchDelete() {
      if (!this.selectedRows.length) {
        this.$message(`未选中任何数据`);
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "删除资讯数据",
        text: `删除后不可恢复，是否继续？`,
        icon: "warning",
      });
      if (confirmed) {
        try {
          let ids = this.selectedRows.map((entity) => entity.id);
          const response = await this.$axios.post(`/news/batchDelete`, ids);
          if (response.data.code === 200) {
            this.$swal.fire({
              title: "删除提示",
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
            title: "错误提示",
            text: e,
            icon: "error",
            showConfirmButton: false,
            timer: 2000,
          });
          console.error(`资讯信息删除异常：`, e);
        }
      }
    },
    resetQueryCondition() {
      this.newsQueryDto = {};
      this.searchTime = [];
      this.fetchFreshData();
    },
    // 修改信息
    async updateOperation() {
      try {
        const response = await this.$axios.put("/news/update", this.data);
        this.$swal.fire({
          title: "资讯信息修改",
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
        console.error("提交表单时出错", error);
        this.$message.error("提交失败，请稍后再试！");
      }
    },
    // 信息新增
    async addOperation() {
      try {
        const response = await this.$axios.post("/news/save", this.data);
        this.$message[response.data.code === 200 ? "success" : "error"](
          response.data.msg
        );
        if (response.data.code === 200) {
          this.closeDialog();
          this.fetchFreshData();
          this.clearFormData();
        }
      } catch (error) {
        console.error("提交表单时出错", error);
        this.$message.error("提交失败，请稍后再试！");
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
        // 请求参数
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          startTime: startTime,
          endTime: endTime,
          ...this.newsQueryDto,
        };
        const response = await this.$axios.post("/news/query", params);
        const { data } = response;
        this.tableData = data.data;
        this.totalItems = data.total;
      } catch (error) {
        console.error("查询资讯信息异常:", error);
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
      this.data = { ...row };
    },
    handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },
    selectDefaultCover(url) {
      this.data.cover = url;
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
  /* 使按钮水平居中*/
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 如果需要调整按钮之间的间距 */
.customer {
  margin: 0 8px;
  /* 根据需要调整间距*/
}
</style>
