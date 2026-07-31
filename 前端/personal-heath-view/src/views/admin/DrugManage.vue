<template>
  <div class="manage-container">
    <!--  -->
    <div class="operate-bar">
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>
        
      </el-button>
      <el-button type="success" @click="showImportDialog">
        <el-icon><Upload /></el-icon>
        JSON
      </el-button>
      <el-input
        v-model="searchName"
        placeholder=""
        style="width: 200px; margin-left: 16px"
        @keyup.enter="loadDrugs"
        clearable
      />
      <el-select v-model="searchCategory" placeholder="" clearable @change="loadDrugs" style="width: 140px; margin-left: 16px">
        <el-option label="" value="" />
        <el-option label="" value="" />
        <el-option label="" value="" />
        <el-option label="" value="" />
        <el-option label="" value="" />
        <el-option label="" value="" />
        <el-option label="" value="" />
        <el-option label="" value="" />
      </el-select>
    </div>

    <!-- JSON -->
    <el-dialog v-model="importDialogVisible" title="JSON" width="700px">
      <div style="margin-bottom: 16px">
        <el-alert type="info" :closable="false">
          <template #title>
            <div>
              <p><strong></strong></p>
              <p>1. JSON  2. JSON</p>
            </div>
          </template>
        </el-alert>
      </div>
      
      <!--  -->
      <div style="margin-bottom: 16px">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="false"
          accept=".json"
          :on-change="handleFileChange"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon>
            JSON
          </el-button>
          <template #tip>
            <span style="margin-left: 12px; color: #999; font-size: 12px">
              {{ uploadedFileName || ' .json ' }}
            </span>
          </template>
        </el-upload>
      </div>
      
      <div style="margin-bottom: 16px; display: flex; gap: 8px">
        <el-button type="primary" size="small" @click="showTemplate">
          <el-icon><View /></el-icon>
          
        </el-button>
        <el-button type="success" size="small" @click="fillExample">
          <el-icon><DocumentCopy /></el-icon>
          
        </el-button>
        <el-button size="small" @click="downloadTemplate">
          <el-icon><Download /></el-icon>
          
        </el-button>
      </div>
      
      <el-input
        v-model="importJson"
        type="textarea"
        :rows="12"
        placeholder='JSON...'
      />
      <template #footer>
        <el-button @click="importDialogVisible = false"></el-button>
        <el-button type="primary" @click="handleImport" :loading="importing">
          
        </el-button>
      </template>
    </el-dialog>

    <!-- JSON -->
    <el-dialog v-model="templateDialogVisible" title="JSON" width="700px">
      <pre class="json-template">{{ jsonTemplate }}</pre>
      <template #footer>
        <el-button @click="copyTemplate"></el-button>
        <el-button type="primary" @click="templateDialogVisible = false"></el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-table :data="drugList" stripe v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="" width="150" />
      <el-table-column prop="category" label="" width="100" />
      <el-table-column prop="price" label="" width="100">
        <template #default="{ row }">
          <span style="color: #e74c3c; font-weight: bold">¥{{ row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="" width="60" />
      <el-table-column prop="specification" label="" width="150" />
      <el-table-column prop="manufacturer" label="" width="200" show-overflow-tooltip />
      <el-table-column prop="isOtc" label="" width="80">
        <template #default="{ row }">
          <el-tag :type="row.isOtc ? 'success' : 'warning'" size="small">
            {{ row.isOtc ? 'OTC' : '' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="" width="80" />
      <el-table-column prop="status" label="" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'danger'" size="small">
            {{ row.status ? '' : '' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="showEditDialog(row)"></el-button>
          <el-button type="danger" size="small" @click="deleteDrug(row)"></el-button>
        </template>
      </el-table-column>
    </el-table>

    <!--  -->
    <div style="display: flex; justify-content: center; margin-top: 20px">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadDrugs"
      />
    </div>

    <!-- / -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '' : ''" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="" required>
          <el-input v-model="form.name" placeholder="" />
        </el-form-item>
        <el-form-item label="">
          <el-input v-model="form.genericName" placeholder="" />
        </el-form-item>
        <el-form-item label="" required>
          <el-select v-model="form.category" placeholder="">
            <el-option label="" value="" />
            <el-option label="" value="" />
            <el-option label="" value="" />
            <el-option label="" value="" />
            <el-option label="" value="" />
            <el-option label="" value="" />
            <el-option label="" value="" />
          </el-select>
        </el-form-item>
        <el-form-item label="" required>
          <el-input-number v-model="form.price" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="">
          <el-input v-model="form.unit" placeholder="//" style="width: 120px" />
        </el-form-item>
        <el-form-item label="">
          <el-input v-model="form.specification" placeholder="0.5g×24" />
        </el-form-item>
        <el-form-item label="">
          <el-input v-model="form.manufacturer" placeholder="" />
        </el-form-item>
        <el-form-item label="OTC">
          <el-switch v-model="form.isOtc" />
        </el-form-item>
        <el-form-item label="">
          <el-input-number v-model="form.stock" :min="0" />
        </el-form-item>
        <el-form-item label="">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="" />
        </el-form-item>
        <el-form-item label="">
          <el-upload
            class="avatar-uploader"
            :action="$uploadUrl"
            :headers="$uploadHeaders"
            :show-file-list="false"
            :on-success="handleCoverSuccess"
          >
            <img v-if="form.cover" :src="form.cover" style="height: 80px; width: 80px" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false"></el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting"></el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: "DrugManage",
  data() {
    return {
      drugList: [],
      loading: false,
      searchName: "",
      searchCategory: "",
      currentPage: 1,
      pageSize: 15,
      total: 0,
      dialogVisible: false,
      isEdit: false,
      form: {},
      submitting: false,
      importDialogVisible: false,
      templateDialogVisible: false,
      importJson: "",
      importing: false,
      uploadedFileName: "",
      jsonTemplate: `[
  {
    "name": "",
    "genericName": "",
    "category": "",
    "description": "",
    "price": 12.50,
    "unit": "",
    "specification": "0.25g*24",
    "manufacturer": "",
    "isOtc": true,
    "stock": 100,
    "status": true
  },
  {
    "name": "",
    "genericName": "",
    "category": "",
    "description": "",
    "price": 25.00,
    "unit": "",
    "specification": "0.3g*20",
    "manufacturer": "",
    "isOtc": true,
    "stock": 200,
    "status": true
  }
]`,
    };
  },
  created() {
    this.loadDrugs();
  },
  methods: {
    async loadDrugs() {
      this.loading = true;
      try {
        const params = {
          current: this.currentPage,
          size: this.pageSize,
        };
        if (this.searchName) params.name = this.searchName;
        if (this.searchCategory) params.category = this.searchCategory;
        const res = await this.$axios.post("/drug/query", params);
        if (res.data.code === 200) {
          this.drugList = res.data.data || [];
          this.total = res.data.total || 0;
        }
      } catch (e) {
        console.error("", e);
      } finally {
        this.loading = false;
      }
    },
    showAddDialog() {
      this.isEdit = false;
      this.form = {
        name: "",
        genericName: "",
        category: "",
        price: 0,
        unit: "",
        specification: "",
        manufacturer: "",
        isOtc: true,
        stock: 100,
        description: "",
        cover: "",
      };
      this.dialogVisible = true;
    },
    showEditDialog(drug) {
      this.isEdit = true;
      this.form = { ...drug };
      this.dialogVisible = true;
    },
    handleCoverSuccess(res) {
      if (res.code === 200) {
        this.form.cover = res.data;
      }
    },
    async submitForm() {
      if (!this.form.name || !this.form.category || this.form.price === undefined) {
        this.$message.warning("");
        return;
      }
      this.submitting = true;
      try {
        const url = this.isEdit ? "/drug/update" : "/drug/save";
        const method = this.isEdit ? "put" : "post";
        const res = await this.$axios[method](url, this.form);
        if (res.data.code === 200) {
          this.$message.success(this.isEdit ? "" : "");
          this.dialogVisible = false;
          this.loadDrugs();
        }
      } catch (e) {
        this.$message.error("");
      } finally {
        this.submitting = false;
      }
    },
    async deleteDrug(drug) {
      try {
        const result = await this.$swal.fire({
          title: "",
          text: `${drug.name}`,
          icon: "warning",
          showCancelButton: true,
          confirmButtonColor: "#e74c3c",
          confirmButtonText: "",
          cancelButtonText: "",
        });
        if (!result.isConfirmed) return;
        const res = await this.$axios.post("/drug/batchDelete", [drug.id]);
        if (res.data.code === 200) {
          this.$message.success("");
          this.loadDrugs();
        }
      } catch (e) {
        this.$message.error("");
      }
    },
    showImportDialog() {
      this.importJson = "";
      this.uploadedFileName = "";
      this.importDialogVisible = true;
    },
    handleFileChange(file) {
      this.uploadedFileName = file.name;
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const content = e.target.result;
          // JSON
          JSON.parse(content);
          this.importJson = content;
          this.$message.success("");
        } catch {
          this.$message.error("JSON");
        }
      };
      reader.readAsText(file.raw);
    },
    showTemplate() {
      this.templateDialogVisible = true;
    },
    fillExample() {
      this.importJson = this.jsonTemplate;
    },
    copyTemplate() {
      navigator.clipboard.writeText(this.jsonTemplate).then(() => {
        this.$message.success("");
      }).catch(() => {
        this.$message.error("");
      });
    },
    downloadTemplate() {
      const blob = new Blob([this.jsonTemplate], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = ".json";
      a.click();
      URL.revokeObjectURL(url);
    },
    async handleImport() {
      if (!this.importJson.trim()) {
        this.$message.warning("JSON");
        return;
      }
      try {
        const data = JSON.parse(this.importJson);
        if (!Array.isArray(data)) {
          this.$message.error("JSON");
          return;
        }
        this.importing = true;
        let successCount = 0;
        let failCount = 0;
        for (const drug of data) {
          try {
            const res = await this.$axios.post("/drug/save", drug);
            if (res.data.code === 200) {
              successCount++;
            } else {
              failCount++;
            }
          } catch {
            failCount++;
          }
        }
        this.$message.success(`${successCount}${failCount}`);
        this.importDialogVisible = false;
        this.loadDrugs();
      } catch (e) {
        this.$message.error("JSON");
      } finally {
        this.importing = false;
      }
    },
  },
};
</script>

<style scoped>
.manage-container {
  padding: 20px;
}
.operate-bar {
  display: flex;
  align-items: center;
}
.json-template {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
}
</style>
