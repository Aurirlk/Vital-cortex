<template>
  <div class="message-manage">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ============  ============ -->
      <el-tab-pane label="" name="system">
        <el-row style="padding: 10px 0">
          <el-date-picker
            size="small"
            style="margin-left: 10px; width: 220px"
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
            v-model="messageQueryDto.content"
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
              style="background-color: rgb(96, 98, 102); color: rgb(247, 248, 249); border: none"
              class="customer"
              type="info"
              @click="allMessagePush"
              ><el-icon><Plus /></el-icon></el-button
            >
          </span>
        </el-row>

        <el-table :data="tableData" style="width: 100%">
          <el-table-column prop="name" width="98" label="">
            <template #default="{ row }">
              <span>{{ row.isRead ? "" : "" }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="messageType" width="148" label="">
            <template #default="{ row }">
              <span v-if="row.messageType === 1"></span>
              <span v-else-if="row.messageType === 2"></span>
              <span v-else-if="row.messageType === 3"></span>
              <span v-else></span>
            </template>
          </el-table-column>
          <el-table-column prop="receiverName" width="108" label=""></el-table-column>
          <el-table-column prop="content" label=""></el-table-column>
          <el-table-column prop="createTime" width="168" label=""></el-table-column>
          <el-table-column label="" width="88">
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
      </el-tab-pane>

      <!-- ============ AI ============ -->
      <el-tab-pane label="AI" name="aiChat">
        <div style="padding: 10px 0">
          <el-row style="margin-bottom: 15px">
            <el-col :span="8">
              <el-date-picker
                v-model="aiDateRange"
                type="daterange"
                range-separator=""
                start-placeholder=""
                end-placeholder=""
                size="small"
                @change="loadAiChatRecords"
              >
              </el-date-picker>
            </el-col>
            <el-col :span="4">
              <el-select
                v-model="aiQueryRole"
                placeholder=""
                clearable
                size="small"
                @change="loadAiChatRecords"
              >
                <el-option label="" value=""></el-option>
                <el-option label="" value="doctor"></el-option>
                <el-option label="" value="nutritionist"></el-option>
                <el-option label="" value="psychologist"></el-option>
                <el-option label="" value="analyst"></el-option>
                <el-option label="" value="general_assistant"></el-option>
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-button size="small" style="background-color: #15559a; border: none" type="primary" @click="loadAiChatRecords">
                <el-icon><Search /></el-icon> 
              </el-button>
            </el-col>
          </el-row>

          <el-table :data="aiChatRecords" border style="width: 100%" max-height="500">
            <el-table-column prop="id" label="ID" width="80"></el-table-column>
            <el-table-column prop="role" label="" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getRoleTagType(row.agentType)">
                  {{ getRoleName(row.agentType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sender" label="" width="100"></el-table-column>
            <el-table-column prop="content" label="" show-overflow-tooltip></el-table-column>
            <el-table-column prop="createTime" label="" width="160"></el-table-column>
          </el-table>

          <el-pagination
            v-if="aiChatRecords.length > 0"
            style="margin-top: 15px; text-align: right"
            v-model:current-page="aiPagination.current"
            :page-sizes="[10, 20, 50]"
            v-model:page-size="aiPagination.size"
            layout="total, sizes, prev, pager, next"
            :total="aiPagination.total"
          >
          </el-pagination>
        </div>
      </el-tab-pane>

      <!-- ============ AI ============ -->
      <el-tab-pane label="AI" name="aiStats">
        <el-row :gutter="20" style="margin-bottom: 20px">
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ aiStats.totalChats }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ aiStats.todayChats }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ aiStats.userCount }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ aiStats.avgPerUser }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <div class="chart-card">
              <div class="chart-title"></div>
              <div style="padding: 20px">
                <el-table :data="aiRoleStats" border style="width: 100%">
                  <el-table-column prop="name" label=""></el-table-column>
                  <el-table-column prop="count" label="" width="100"></el-table-column>
                  <el-table-column prop="percent" label="" width="150">
                    <template #default="{ row }">
                      <el-progress :percentage="row.percent" :stroke-width="10" :color="'#15559a'"></el-progress>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="chart-title"></div>
              <div style="padding: 20px">
                <el-table :data="aiTrendData" border style="width: 100%">
                  <el-table-column prop="date" label=""></el-table-column>
                  <el-table-column prop="count" label="" width="100"></el-table-column>
                  <el-table-column prop="users" label="" width="100"></el-table-column>
                </el-table>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!--  -->
    <el-dialog
      :show-title="false"
      :show-close="false"
      v-model="dialogMessageOperation"
      width="24%"
    >
      <p style="padding: 20px 0 0 20px"></p>
      <div style="padding: 0 20px">
        <el-row>
          <span class="dialog-hover"></span>
          <el-input
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder=""
            v-model="messageContent"
          >
          </el-input>
        </el-row>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            size="small"
            style="background-color: rgb(43, 121, 203); border: none"
            class="customer"
            type="info"
            @click="messagePushOperation"
            ></el-button
          >
          <el-button
            class="customer"
            size="small"
            style="background-color: rgb(241, 241, 241); border: none"
            @click="dialogMessageOperation = false"
            ></el-button
          >
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: "MessageManage",
  data() {
    return {
      // 
      activeTab: "system",

      // ======  ======
      data: {},
      filterText: "",
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      tableData: [],
      searchTime: [],
      selectedRows: [],
      messageQueryDto: {},
      messsageContent: "",
      messageTypeList: [],
      dialogMessageOperation: false,
      isOperation: false,
      messageContent: "",

      // ====== AI ======
      aiChatRecords: [],
      aiDateRange: [],
      aiQueryRole: "",
      aiPagination: {
        current: 1,
        size: 10,
        total: 0,
      },

      // ====== AI ======
      aiStats: {
        totalChats: 0,
        todayChats: 0,
        userCount: 0,
        avgPerUser: 0,
      },
      aiRoleStats: [],
      aiTrendData: [],
    };
  },
  watch: {
    currentPage() {
      this.fetchFreshData();
    },
    pageSize() {
      this.fetchFreshData();
    },
    "aiPagination.current"() {
      this.loadAiChatRecords();
    },
    "aiPagination.size"() {
      this.loadAiChatRecords();
    },
  },
  created() {
    this.fetchFreshData();
    this.loadAllMessageType();
  },
  methods: {
    // ====================  ====================
    messagePushOperation() {
      const message = {
        content: this.messageContent,
      };
      this.$axios
        .post("/message/systemInfoUsersSave", message)
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.$notify({
              duration: 2000,
              title: "",
              message: "",
              type: "success",
            });
            this.dialogMessageOperation = false;
            this.messageContent = "";
          }
        });
    },
    allMessagePush() {
      this.dialogMessageOperation = true;
    },
    loadAllMessageType() {
      this.$axios.get("/message/types").then((response) => {
        const { data } = response;
        if (data.code === 200) {
          this.messageTypeList = data.data;
        }
      });
    },
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
          const response = await this.$axios.post(`/message/batchDelete`, ids);
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
          console.error(``, e);
        }
      }
    },
    resetQueryCondition() {
      this.messageQueryDto = {};
      this.searchTime = [];
      this.fetchFreshData();
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
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          startTime: startTime,
          endTime: endTime,
          ...this.messageQueryDto,
        };
        const response = await this.$axios.post("/message/query", params);
        const { data } = response;
        this.tableData = data.data;
        this.totalItems = data.total;
      } catch (error) {
        console.error(":", error);
      }
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
      this.dialogMessageOperation = true;
      this.isOperation = true;
      this.data = { ...row };
    },
    handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },

    // ==================== AI ====================
    async loadAiChatRecords() {
      try {
        const params = {
          current: (this.aiPagination.current - 1) * this.aiPagination.size,
          size: this.aiPagination.size,
          agentType: this.aiQueryRole || undefined,
        };
        if (this.aiDateRange && this.aiDateRange.length === 2) {
          params.startTime = this.aiDateRange[0];
          params.endTime = this.aiDateRange[1];
        }
        const response = await this.$axios.post("/ai/records/query", params);
        const { data } = response;
        if (data.code === 200) {
          this.aiChatRecords = data.data || [];
          this.aiPagination.total = data.total || 0;
        }
      } catch (e) {
        console.error("AI:", e);
      }
    },

    getRoleName(type) {
      const map = {
        doctor: "",
        nutritionist: "",
        psychologist: "",
        analyst: "",
        general_assistant: "",
      };
      return map[type] || type;
    },

    getRoleTagType(type) {
      const map = {
        doctor: "",
        nutritionist: "success",
        psychologist: "warning",
        analyst: "info",
        general_assistant: "danger",
      };
      return map[type] || "";
    },

    // ==================== AI ====================
    async loadAiStats() {
      try {
        const response = await this.$axios.get("/ai/stats");
        const { data } = response;
        if (data.code === 200) {
          this.aiStats = data.data.stats || this.aiStats;
          this.aiRoleStats = data.data.roleStats || [];
          this.aiTrendData = data.data.trendData || [];
        }
      } catch (e) {
        console.error("AI:", e);
      }
    },
  },
};
</script>

<style scoped>
.message-manage {
  padding: 10px;
}

/* AI */
.stat-card {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  text-align: center;
  border: 1px solid #f0f0f0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #15559a;
}

.stat-label {
  font-size: 13px;
  color: #999;
  margin-top: 5px;
}

.chart-card {
  background: #fff;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  padding: 15px 20px 0;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 10px;
}

.text-button {
  color: #409eff;
  cursor: pointer;
  font-size: 13px;
}

.text-button:hover {
  color: #66b1ff;
}
</style>
