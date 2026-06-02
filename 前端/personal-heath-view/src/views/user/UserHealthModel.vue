<template>
  <div>
    <div
      style="
        border-radius: 5px;
        padding: 20px 0 60px 0;
        width: 100%;
        background-color: #fafafa;
      "
    >
      <div
        style="
          height: 100px;
          line-height: 100px;
          text-align: center;
          font-size: 24px;
        "
      >
        健康生活，健康人生，从此刻开始！
      </div>
      <div
        style="
          height: 50px;
          line-height: 50px;
          text-align: center;
          font-size: 30px;
          font-weight: bolder;
        "
      >
        每一点改变，都值得被记录。
        <span
          @click="toRecord"
          style="
            cursor: pointer;
            padding: 5px 10px;
            background-color: #000;
            border-radius: 5px;
            color: #fff;
          "
        >
          前去记录
          <el-icon><Right /></el-icon>
        </span>
      </div>
    </div>
    <div style="padding: 30px 0">
      <div style="margin: 20px 0; display: flex; align-items: center; gap: 16px;">
        <!-- 选择具体的指标模型 -->
        <el-select
          size="small"
          @change="modelChange"
          v-model="userHealthQueryDto.healthModelConfigId"
          placeholder="请选择"
        >
          <el-option
            v-for="model in usersHealthModelConfig"
            :key="model.id"
            :label="model.name"
            :value="model.id"
          >
          </el-option>
        </el-select>
        <!-- 下载报告按钮 -->
        <el-button
          type="primary"
          size="small"
          @click="downloadReport"
          :loading="reportLoading"
        >
          <el-icon><Document /></el-icon>
          生成健康报告
        </el-button>
        <!-- JSON导入按钮 -->
        <el-button
          type="success"
          size="small"
          @click="showImportDialog = true"
        >
          <el-icon><Upload /></el-icon>
          JSON导入
        </el-button>
        <!-- JSON导出按钮 -->
        <el-button
          type="info"
          size="small"
          @click="exportHealthData"
          :loading="exportLoading"
        >
          <el-icon><Download /></el-icon>
          JSON导出
        </el-button>
      </div>
      <div>
        <LineChart
          @on-selected="onSelectedTime"
          @on-date-range="onDateRange"
          height="500px"
          tag="健康趋势图"
          :values="values"
          :date="dates"
        />
      </div>
    </div>
    <div>
      <h2 style="padding-left: 20px; border-left: 2px solid rgb(43, 121, 203)">
        健康指标数据
      </h2>
      <el-row style="padding: 10px; margin-left: 10px">
        <el-row>
          <el-date-picker
            @change="fetchFreshData"
            size="small"
            style="width: 220px"
            v-model="searchTime"
            type="daterange"
            range-separator="至"
            start-placeholder="记录开始"
            end-placeholder="记录结束"
          >
          </el-date-picker>
        </el-row>
      </el-row>
      <el-row style="margin: 0 20px; border-top: 1px solid rgb(245, 245, 245)">
        <el-table
          row-key="id"
          @selection-change="handleSelectionChange"
          :data="tableData"
        >
          <el-table-column prop="name" width="88" label="状态">
            <template #default="{ row }">
              <el-icon v-if="!statusCheck(row)" style="margin-right: 5px"
                ><Warning
              /></el-icon>
              <el-icon
                v-else
                style="margin-right: 5px; color: rgb(253, 199, 50)"
                ><SuccessFilled
              /></el-icon>
              <el-tooltip
                v-if="!statusCheck(row)"
                class="item"
                effect="dark"
                content="异常指标，提醒用户及时处理"
                placement="bottom-end"
              >
                <span
                  style="
                    text-decoration: underline;
                    text-decoration-style: dashed;
                  "
                  >异常</span
                >
              </el-tooltip>
              <span v-else>正常</span>
            </template>
          </el-table-column>
          <el-table-column prop="value" width="148" label="记录值" sortable>
            <template #default="{ row }">
              <span>{{ row.value }}({{ row.unit }})</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="模型名">
            <template #default="{ row }">
              <span
                ><el-icon style="margin-right: 3px"><Receiving /></el-icon
                >{{ row.name }}</span
              >
            </template>
          </el-table-column>
          <el-table-column
            prop="unit"
            width="88"
            label="单位"
          ></el-table-column>
          <el-table-column
            prop="symbol"
            width="88"
            label="符号"
          ></el-table-column>
          <el-table-column
            prop="valueRange"
            width="128"
            label="阈值"
          ></el-table-column>
          <el-table-column
            prop="createTime"
            width="178"
            label="记录时间"
            sortable
          ></el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <span class="text-button" @click="handleDelete(row)">删除</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin: 20px 0"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="totalItems"
        ></el-pagination>
      </el-row>
    </div>

    <!-- JSON导入弹窗 -->
    <el-dialog v-model="showImportDialog" title="JSON导入健康记录" width="700px">
      <div style="margin-bottom: 16px">
        <el-alert type="info" :closable="false">
          <template #title>
            <div>
              <p><strong>导入格式说明：</strong></p>
              <p>支持JSON格式，每条记录包含：</p>
              <p>- <code>healthModelConfigId</code>: 健康模型ID（优先）</p>
              <p>- <code>modelName</code>: 模型名称（如"收缩压"、"血糖"等，当没有ID时使用）</p>
              <p>- <code>value</code>: 记录值</p>
              <p>- <code>recordTime</code>: 记录时间（可选，格式：yyyy-MM-dd HH:mm:ss）</p>
            </div>
          </template>
        </el-alert>
      </div>
      
      <div style="margin-bottom: 16px; display: flex; gap: 8px">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".json"
          :on-change="handleFileChange"
        >
          <el-button size="small" type="primary">
            <el-icon><Upload /></el-icon>
            选择JSON文件
          </el-button>
        </el-upload>
        <el-button size="small" @click="fillExample">
          <el-icon><DocumentCopy /></el-icon>
          填充示例
        </el-button>
      </div>
      
      <el-input
        v-model="importJson"
        type="textarea"
        :rows="12"
        placeholder="或直接在此粘贴JSON内容..."
      />
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleImport" :loading="importing">
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script>
import LineChart from "@/components/LineChart.vue";
export default {
  components: { LineChart },
  data() {
    return {
      usersHealthModelConfig: [],
      modelConfigList: [],
      userHealthQueryDto: {}, // 查询的参数
      values: [],
      dates: [],
      tableData: [],
      selectedRows: [],
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      searchTime: [],
      healthModelConfigId: null,
      reportLoading: false,
      // 导入导出相关
      showImportDialog: false,
      importJson: "",
      importing: false,
      exportLoading: false,
      jsonTemplate: `[
  {
    "modelName": "收缩压",
    "value": "120",
    "recordTime": "2024-01-01 10:00:00"
  },
  {
    "modelName": "舒张压",
    "value": "80",
    "recordTime": "2024-01-01 10:00:00"
  },
  {
    "modelName": "血糖",
    "value": "5.6",
    "recordTime": "2024-01-01 10:00:00"
  }
]`,
    };
  },
  created() {
    this.loadHealthModelConfig();
    this.fetchFreshData();
  },
  watch: {
    currentPage() {
      this.fetchFreshData();
    },
    pageSize() {
      this.currentPage = 1;
      this.fetchFreshData();
    },
  },
  methods: {
    handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },
    // 处理用户输入的值，是正常的还是异常的，给个状态
    statusCheck(data) {
      // 用户输入的值
      const inputValue = data.value;
      // 正常值范围
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
    // 批量删除数据
    async batchDelete() {
      if (!this.selectedRows.length) {
        this.$message(`未选中任何数据`);
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "删除健康记录数据",
        text: `删除后不可恢复，是否继续？`,
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
          console.error(`用户健康记录信息删除异常：`, e);
        }
      }
    },
    // 点击查询之后，执行的一个函数
    handleFilter() {
      this.currentPage = 1;
      this.fetchFreshData();
    },
    // 加载用户自己的健康记录数据
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
        };
        console.log(JSON.stringify(params));
        const response = await this.$axios.post(
          "/user-health/queryUser",
          params
        );
        const { data } = response;
        this.tableData = data.data;
        this.totalItems = data.total;
      } catch (error) {
        console.error("查询用户健康记录信息异常:", error);
      }
    },
    // 点击输入框里面的清除按钮
    handleFilterClear() {
      this.filterText = "";
      this.handleFilter();
    },
    // 多选框选中
    handleSelectionChange(selection) {
      this.selectedRows = selection;
    },
    // 条件重置
    resetQueryCondition() {
      this.searchTime = [];
      this.healthModelConfigId = null;
      this.fetchFreshData();
    },
    // 当前页切换
    handleSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchFreshData();
    },
    // 当前页跳转
    handleCurrentChange(val) {
      this.currentPage = val;
      this.fetchFreshData();
    },
    // 查询用户具体记录的值，指定时间范围内
    loadUserModelHavaRecord() {
      const modelId = this.userHealthQueryDto.healthModelConfigId;
      const time = this.userHealthQueryDto.time;
      console.log('加载健康数据, modelId:', modelId, 'time:', time);
      
      if (!modelId) {
        console.log('模型ID为空，跳过加载');
        return;
      }
      
      this.$axios
        .get(`/user-health/timeQuery/${modelId}/${time}`)
        .then((response) => {
          const { data } = response;
          console.log('健康数据响应:', data);
          
          if (data.code === 200 && data.data && data.data.length > 0) {
            const records = data.data;
            console.log('记录数量:', records.length);
            
            this.values = records.map((entity) => parseFloat(entity.value)).reverse();
            this.dates = records.map((entity) => {
              if (entity.createTime) {
                const dateStr = entity.createTime.replace('T', ' ').substring(0, 10);
                const parts = dateStr.split('-');
                return `${parts[1]}-${parts[2]}`;
              }
              return '';
            }).reverse();
            
            console.log('values:', this.values);
            console.log('dates:', this.dates);
          } else {
            console.log('无数据或返回错误');
            this.values = [];
            this.dates = [];
          }
        })
        .catch((error) => {
          console.error('加载健康数据失败:', error);
          this.values = [];
          this.dates = [];
        });
    },
    // 模型选中方法
    modelChange() {
      this.loadUserModelHavaRecord();
    },
    // 表格里面的具体模型选中
    modelUserChange() {
      // 如果想用户直接选中，数据直接回来，就要用到这一个方法
      this.fetchFreshData();
    },
    // 查询用户自己配置的模型以及全局模型
    loadHealthModelConfig() {
      this.$axios.post("/health-model-config/modelList").then((response) => {
        const { data } = response;
        if (data.code === 200) {
          this.usersHealthModelConfig = data.data;
          this.modelConfigList = data.data;
          this.defaultLoad();
        }
      });
    },
    // 默认加载
    defaultLoad() {
      if (this.modelConfigList && this.modelConfigList.length > 0) {
        // 优先找用户自定义模型（isGlobal为false或0）
        const userModel = this.modelConfigList.find(m => m.isGlobal === false || m.isGlobal === 0);
        if (userModel) {
          this.userHealthQueryDto.healthModelConfigId = userModel.id;
          console.log('使用用户模型:', userModel.id, userModel.name);
        } else {
          // 如果没有用户模型，使用第一个全局模型
          const globalModel = this.modelConfigList.find(m => m.isGlobal === true || m.isGlobal === 1);
          if (globalModel) {
            this.userHealthQueryDto.healthModelConfigId = globalModel.id;
            console.log('使用全局模型:', globalModel.id, globalModel.name);
          } else {
            this.userHealthQueryDto.healthModelConfigId = this.modelConfigList[0].id;
            console.log('使用第一个模型:', this.modelConfigList[0].id);
          }
        }
        // 使用-1表示查询所有数据，不受时间限制
        this.userHealthQueryDto.time = -1;
        this.loadUserModelHavaRecord();
      }
    },
    // 折线图选择指定事件范围之后，返回的一个回调
    onSelectedTime(time) {
      this.userHealthQueryDto.time = time;
      this.loadUserModelHavaRecord();
    },
    // 自定义日期范围查询
    onDateRange(startDate, endDate) {
      this.loadUserModelHavaRecordByDateRange(startDate, endDate);
    },
    // 按日期范围查询数据
    loadUserModelHavaRecordByDateRange(startDate, endDate) {
      const modelId = this.userHealthQueryDto.healthModelConfigId;
      if (!modelId) return;
      
      const start = startDate.toISOString().split('T')[0] + 'T00:00:00';
      const end = endDate.toISOString().split('T')[0] + 'T23:59:59';
      
      this.$axios
        .get(`/user-health/queryByDateRange?modelId=${modelId}&startTime=${start}&endTime=${end}`)
        .then((response) => {
          const { data } = response;
          if (data.code === 200 && data.data && data.data.length > 0) {
            const records = data.data;
            this.values = records.map((entity) => parseFloat(entity.value)).reverse();
            this.dates = records.map((entity) => {
              if (entity.createTime) {
                const dateStr = entity.createTime.replace('T', ' ').substring(0, 10);
                const parts = dateStr.split('-');
                return `${parts[1]}-${parts[2]}`;
              }
              return '';
            }).reverse();
          } else {
            this.values = [];
            this.dates = [];
          }
        })
        .catch(() => {
          this.values = [];
          this.dates = [];
        });
    },
    // 组件里面返回的数据
    timeSelected() {},
    toRecord() {
      this.$router.push("/record");
    },
    // 下载健康报告
    async downloadReport() {
      this.reportLoading = true;
      try {
        const response = await this.$axios.get('/report/health-pdf', {
          responseType: 'blob'
        });

        // 创建下载链接
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `健康报告_${new Date().toLocaleDateString()}.pdf`);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);

        this.$message.success('报告生成成功');
      } catch (e) {
        this.$message.error('报告生成失败，请稍后再试');
        console.error('下载报告失败:', e);
      } finally {
        this.reportLoading = false;
      }
    },
    // 处理文件选择
    handleFileChange(file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const content = e.target.result;
          JSON.parse(content); // 验证JSON格式
          this.importJson = content;
          this.$message.success("文件读取成功");
        } catch {
          this.$message.error("文件内容不是有效的JSON格式");
        }
      };
      reader.readAsText(file.raw);
    },
    // 填充示例
    fillExample() {
      this.importJson = this.jsonTemplate;
    },
    // 处理导入
    async handleImport() {
      if (!this.importJson.trim()) {
        this.$message.warning("请输入JSON数据");
        return;
      }
      try {
        const data = JSON.parse(this.importJson);
        let records = [];
        
        // 支持两种格式：直接数组或 {records: [...]}
        if (Array.isArray(data)) {
          records = data;
        } else if (data.records && Array.isArray(data.records)) {
          records = data.records;
        } else {
          this.$message.error("JSON格式错误，需要数组格式");
          return;
        }

        this.importing = true;
        const response = await this.$axios.post("/user-health/import", { records });
        
        if (response.data.code === 200) {
          const result = response.data.data;
          let msg = `导入完成：成功${result.success}条，失败${result.fail}条`;
          if (result.errors && result.errors.length > 0) {
            msg += `\n错误示例：${result.errors[0]}`;
          }
          this.$swal.fire({
            title: "导入结果",
            text: msg,
            icon: result.fail > 0 ? "warning" : "success",
          });
          this.showImportDialog = false;
          this.fetchFreshData();
        } else {
          this.$message.error(response.data.message || "导入失败");
        }
      } catch (e) {
        this.$message.error("JSON格式错误，请检查格式");
      } finally {
        this.importing = false;
      }
    },
    // 导出健康数据
    async exportHealthData() {
      this.exportLoading = true;
      try {
        const response = await this.$axios.get("/user-health/export");
        
        if (response.data.code === 200) {
          const data = response.data.data;
          const json = JSON.stringify(data, null, 2);
          const blob = new Blob([json], { type: "application/json" });
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `健康数据_${new Date().toISOString().slice(0, 10)}.json`;
          a.click();
          URL.revokeObjectURL(url);
          this.$message.success(`导出成功，共${data.length}条记录`);
        } else {
          this.$message.error("导出失败");
        }
      } catch (e) {
        this.$message.error("导出失败：" + e.message);
      } finally {
        this.exportLoading = false;
      }
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
</style>
