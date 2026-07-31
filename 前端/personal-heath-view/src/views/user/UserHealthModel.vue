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
          
          <el-icon><Right /></el-icon>
        </span>
      </div>
    </div>
    <div style="padding: 30px 0">
      <div style="margin: 20px 0; display: flex; align-items: center; gap: 16px;">
        <!--  -->
        <el-select
          size="small"
          @change="modelChange"
          v-model="userHealthQueryDto.healthModelConfigId"
          placeholder=""
        >
          <el-option
            v-for="model in usersHealthModelConfig"
            :key="model.id"
            :label="model.name"
            :value="model.id"
          >
          </el-option>
        </el-select>
        <!--  -->
        <el-button
          type="primary"
          size="small"
          @click="downloadReport"
          :loading="reportLoading"
        >
          <el-icon><Document /></el-icon>
          
        </el-button>
        <!-- JSON -->
        <el-button
          type="success"
          size="small"
          @click="showImportDialog = true"
        >
          <el-icon><Upload /></el-icon>
          JSON
        </el-button>
        <!-- JSON -->
        <el-button
          type="info"
          size="small"
          @click="exportHealthData"
          :loading="exportLoading"
        >
          <el-icon><Download /></el-icon>
          JSON
        </el-button>
      </div>
      <div>
        <LineChart
          @on-selected="onSelectedTime"
          @on-date-range="onDateRange"
          height="500px"
          tag=""
          :values="values"
          :date="dates"
        />
      </div>
    </div>
    <div>
      <h2 style="padding-left: 20px; border-left: 2px solid rgb(43, 121, 203)">
        
      </h2>
      <el-row style="padding: 10px; margin-left: 10px">
        <el-row>
          <el-date-picker
            @change="fetchFreshData"
            size="small"
            style="width: 220px"
            v-model="searchTime"
            type="daterange"
            range-separator=""
            start-placeholder=""
            end-placeholder=""
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
          <el-table-column prop="name" width="88" label="">
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
          <el-table-column prop="name" label="">
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
            label=""
          ></el-table-column>
          <el-table-column
            prop="symbol"
            width="88"
            label=""
          ></el-table-column>
          <el-table-column
            prop="valueRange"
            width="128"
            label=""
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
          v-model:page-size="pageSize"
          :page-sizes="[10, 20]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="totalItems"
        ></el-pagination>
      </el-row>
    </div>

    <!-- JSON -->
    <el-dialog v-model="showImportDialog" title="JSON" width="700px">
      <div style="margin-bottom: 16px">
        <el-alert type="info" :closable="false">
          <template #title>
            <div>
              <p><strong></strong></p>
              <p>JSON</p>
              <p>- <code>healthModelConfigId</code>: ID</p>
              <p>- <code>modelName</code>: """"ID</p>
              <p>- <code>value</code>: </p>
              <p>- <code>recordTime</code>: yyyy-MM-dd HH:mm:ss</p>
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
            JSON
          </el-button>
        </el-upload>
        <el-button size="small" @click="fillExample">
          <el-icon><DocumentCopy /></el-icon>
          
        </el-button>
      </div>
      
      <el-input
        v-model="importJson"
        type="textarea"
        :rows="12"
        placeholder="JSON..."
      />
      <template #footer>
        <el-button @click="showImportDialog = false"></el-button>
        <el-button type="primary" @click="handleImport" :loading="importing">
          
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
      userHealthQueryDto: {}, // 
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
      // 
      showImportDialog: false,
      importJson: "",
      importing: false,
      exportLoading: false,
      jsonTemplate: `[
  {
    "modelName": "",
    "value": "120",
    "recordTime": "2024-01-01 10:00:00"
  },
  {
    "modelName": "",
    "value": "80",
    "recordTime": "2024-01-01 10:00:00"
  },
  {
    "modelName": "",
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
    // 
    handleFilter() {
      this.currentPage = 1;
      this.fetchFreshData();
    },
    // 
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
        console.error(":", error);
      }
    },
    // 
    handleFilterClear() {
      this.filterText = "";
      this.handleFilter();
    },
    // 
    handleSelectionChange(selection) {
      this.selectedRows = selection;
    },
    // 
    resetQueryCondition() {
      this.searchTime = [];
      this.healthModelConfigId = null;
      this.fetchFreshData();
    },
    // 
    handleSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchFreshData();
    },
    // 
    handleCurrentChange(val) {
      this.currentPage = val;
      this.fetchFreshData();
    },
    // 
    loadUserModelHavaRecord() {
      const modelId = this.userHealthQueryDto.healthModelConfigId;
      const time = this.userHealthQueryDto.time;
      console.log(', modelId:', modelId, 'time:', time);
      
      if (!modelId) {
        console.log('ID');
        return;
      }
      
      this.$axios
        .get(`/user-health/timeQuery/${modelId}/${time}`)
        .then((response) => {
          const { data } = response;
          console.log(':', data);
          
          if (data.code === 200 && data.data && data.data.length > 0) {
            const records = data.data;
            console.log(':', records.length);
            
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
            console.log('');
            this.values = [];
            this.dates = [];
          }
        })
        .catch((error) => {
          console.error(':', error);
          this.values = [];
          this.dates = [];
        });
    },
    // 
    modelChange() {
      this.loadUserModelHavaRecord();
    },
    // 
    modelUserChange() {
      // 
      this.fetchFreshData();
    },
    // 
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
    // 
    defaultLoad() {
      if (this.modelConfigList && this.modelConfigList.length > 0) {
        // isGlobalfalse0
        const userModel = this.modelConfigList.find(m => m.isGlobal === false || m.isGlobal === 0);
        if (userModel) {
          this.userHealthQueryDto.healthModelConfigId = userModel.id;
          console.log(':', userModel.id, userModel.name);
        } else {
          // 
          const globalModel = this.modelConfigList.find(m => m.isGlobal === true || m.isGlobal === 1);
          if (globalModel) {
            this.userHealthQueryDto.healthModelConfigId = globalModel.id;
            console.log(':', globalModel.id, globalModel.name);
          } else {
            this.userHealthQueryDto.healthModelConfigId = this.modelConfigList[0].id;
            console.log(':', this.modelConfigList[0].id);
          }
        }
        // -1
        this.userHealthQueryDto.time = -1;
        this.loadUserModelHavaRecord();
      }
    },
    // 
    onSelectedTime(time) {
      this.userHealthQueryDto.time = time;
      this.loadUserModelHavaRecord();
    },
    // 
    onDateRange(startDate, endDate) {
      this.loadUserModelHavaRecordByDateRange(startDate, endDate);
    },
    // 
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
    // 
    timeSelected() {},
    toRecord() {
      this.$router.push("/record");
    },
    // 
    async downloadReport() {
      this.reportLoading = true;
      try {
        const response = await this.$axios.get('/report/health-pdf', {
          responseType: 'blob'
        });

        // 
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `_${new Date().toLocaleDateString()}.pdf`);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);

        this.$message.success('');
      } catch (e) {
        this.$message.error('');
        console.error(':', e);
      } finally {
        this.reportLoading = false;
      }
    },
    // 
    handleFileChange(file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const content = e.target.result;
          JSON.parse(content); // JSON
          this.importJson = content;
          this.$message.success("");
        } catch {
          this.$message.error("JSON");
        }
      };
      reader.readAsText(file.raw);
    },
    // 
    fillExample() {
      this.importJson = this.jsonTemplate;
    },
    // 
    async handleImport() {
      if (!this.importJson.trim()) {
        this.$message.warning("JSON");
        return;
      }
      try {
        const data = JSON.parse(this.importJson);
        let records = [];
        
        //  {records: [...]}
        if (Array.isArray(data)) {
          records = data;
        } else if (data.records && Array.isArray(data.records)) {
          records = data.records;
        } else {
          this.$message.error("JSON");
          return;
        }

        this.importing = true;
        const response = await this.$axios.post("/user-health/import", { records });
        
        if (response.data.code === 200) {
          const result = response.data.data;
          let msg = `${result.success}${result.fail}`;
          if (result.errors && result.errors.length > 0) {
            msg += `\n${result.errors[0]}`;
          }
          this.$swal.fire({
            title: "",
            text: msg,
            icon: result.fail > 0 ? "warning" : "success",
          });
          this.showImportDialog = false;
          this.fetchFreshData();
        } else {
          this.$message.error(response.data.message || "");
        }
      } catch (e) {
        this.$message.error("JSON");
      } finally {
        this.importing = false;
      }
    },
    // 
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
          a.download = `_${new Date().toISOString().slice(0, 10)}.json`;
          a.click();
          URL.revokeObjectURL(url);
          this.$message.success(`${data.length}`);
        } else {
          this.$message.error("");
        }
      } catch (e) {
        this.$message.error("" + e.message);
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
