<template>
  <div class="manage-container">
    <!-- 操作栏 -->
    <div class="operate-bar">
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>
        新增药品
      </el-button>
      <el-button type="success" @click="showImportDialog">
        <el-icon><Upload /></el-icon>
        JSON导入
      </el-button>
      <el-input
        v-model="searchName"
        placeholder="搜索药品名称"
        style="width: 200px; margin-left: 16px"
        @keyup.enter="loadDrugs"
        clearable
      />
      <el-select v-model="searchCategory" placeholder="分类筛选" clearable @change="loadDrugs" style="width: 140px; margin-left: 16px">
        <el-option label="全部" value="" />
        <el-option label="感冒药" value="感冒药" />
        <el-option label="消化系统" value="消化系统" />
        <el-option label="心脑血管" value="心脑血管" />
        <el-option label="维生素" value="维生素" />
        <el-option label="抗生素" value="抗生素" />
        <el-option label="外用药" value="外用药" />
        <el-option label="中成药" value="中成药" />
      </el-select>
    </div>

    <!-- JSON导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="JSON导入药品" width="700px">
      <div style="margin-bottom: 16px">
        <el-alert type="info" :closable="false">
          <template #title>
            <div>
              <p><strong>导入方式：</strong></p>
              <p>1. 上传JSON文件  2. 直接粘贴JSON内容</p>
            </div>
          </template>
        </el-alert>
      </div>
      
      <!-- 文件上传区域 -->
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
            选择JSON文件
          </el-button>
          <template #tip>
            <span style="margin-left: 12px; color: #999; font-size: 12px">
              {{ uploadedFileName || '支持 .json 格式文件' }}
            </span>
          </template>
        </el-upload>
      </div>
      
      <div style="margin-bottom: 16px; display: flex; gap: 8px">
        <el-button type="primary" size="small" @click="showTemplate">
          <el-icon><View /></el-icon>
          查看模板
        </el-button>
        <el-button type="success" size="small" @click="fillExample">
          <el-icon><DocumentCopy /></el-icon>
          填充示例
        </el-button>
        <el-button size="small" @click="downloadTemplate">
          <el-icon><Download /></el-icon>
          下载模板
        </el-button>
      </div>
      
      <el-input
        v-model="importJson"
        type="textarea"
        :rows="12"
        placeholder='或直接在此粘贴JSON内容...'
      />
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport" :loading="importing">
          导入
        </el-button>
      </template>
    </el-dialog>

    <!-- JSON模板查看弹窗 -->
    <el-dialog v-model="templateDialogVisible" title="JSON模板示例" width="700px">
      <pre class="json-template">{{ jsonTemplate }}</pre>
      <template #footer>
        <el-button @click="copyTemplate">复制模板</el-button>
        <el-button type="primary" @click="templateDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 表格 -->
    <el-table :data="drugList" stripe v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="药品名称" width="150" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">
          <span style="color: #e74c3c; font-weight: bold">¥{{ row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column prop="specification" label="规格" width="150" />
      <el-table-column prop="manufacturer" label="生产厂家" width="200" show-overflow-tooltip />
      <el-table-column prop="isOtc" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.isOtc ? 'success' : 'warning'" size="small">
            {{ row.isOtc ? 'OTC' : '处方药' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'danger'" size="small">
            {{ row.status ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="showEditDialog(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="deleteDrug(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div style="display: flex; justify-content: center; margin-top: 20px">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadDrugs"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑药品' : '新增药品'" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="药品名称" required>
          <el-input v-model="form.name" placeholder="请输入药品名称" />
        </el-form-item>
        <el-form-item label="通用名">
          <el-input v-model="form.genericName" placeholder="请输入通用名" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" placeholder="请选择分类">
            <el-option label="感冒药" value="感冒药" />
            <el-option label="消化系统" value="消化系统" />
            <el-option label="心脑血管" value="心脑血管" />
            <el-option label="维生素" value="维生素" />
            <el-option label="抗生素" value="抗生素" />
            <el-option label="外用药" value="外用药" />
            <el-option label="中成药" value="中成药" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="盒/瓶/袋" style="width: 120px" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.specification" placeholder="如：0.5g×24片" />
        </el-form-item>
        <el-form-item label="生产厂家">
          <el-input v-model="form.manufacturer" placeholder="请输入生产厂家" />
        </el-form-item>
        <el-form-item label="OTC药品">
          <el-switch v-model="form.isOtc" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="0" />
        </el-form-item>
        <el-form-item label="药品说明">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入药品说明（功效、用法用量、不良反应、禁忌等）" />
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
            class="avatar-uploader"
            :action="$uploadUrl"
            :show-file-list="false"
            :on-success="handleCoverSuccess"
          >
            <img v-if="form.cover" :src="form.cover" style="height: 80px; width: 80px" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
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
    "name": "阿莫西林胶囊",
    "genericName": "阿莫西林",
    "category": "抗生素",
    "description": "适用于敏感菌所致的感染",
    "price": 12.50,
    "unit": "盒",
    "specification": "0.25g*24粒",
    "manufacturer": "哈药集团制药总厂",
    "isOtc": true,
    "stock": 100,
    "status": true
  },
  {
    "name": "布洛芬缓释胶囊",
    "genericName": "布洛芬",
    "category": "解热镇痛",
    "description": "用于缓解轻至中度疼痛及感冒引起的发热",
    "price": 25.00,
    "unit": "盒",
    "specification": "0.3g*20粒",
    "manufacturer": "中美天津史克制药有限公司",
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
        console.error("加载药品失败", e);
      } finally {
        this.loading = false;
      }
    },
    showAddDialog() {
      this.isEdit = false;
      this.form = {
        name: "",
        genericName: "",
        category: "感冒药",
        price: 0,
        unit: "盒",
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
        this.$message.warning("请填写必填项");
        return;
      }
      this.submitting = true;
      try {
        const url = this.isEdit ? "/drug/update" : "/drug/save";
        const method = this.isEdit ? "put" : "post";
        const res = await this.$axios[method](url, this.form);
        if (res.data.code === 200) {
          this.$message.success(this.isEdit ? "修改成功" : "新增成功");
          this.dialogVisible = false;
          this.loadDrugs();
        }
      } catch (e) {
        this.$message.error("操作失败");
      } finally {
        this.submitting = false;
      }
    },
    async deleteDrug(drug) {
      try {
        const result = await this.$swal.fire({
          title: "确定删除？",
          text: `删除后将无法恢复：${drug.name}`,
          icon: "warning",
          showCancelButton: true,
          confirmButtonColor: "#e74c3c",
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
        });
        if (!result.isConfirmed) return;
        const res = await this.$axios.post("/drug/batchDelete", [drug.id]);
        if (res.data.code === 200) {
          this.$message.success("删除成功");
          this.loadDrugs();
        }
      } catch (e) {
        this.$message.error("删除失败");
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
          // 验证是否为有效JSON
          JSON.parse(content);
          this.importJson = content;
          this.$message.success("文件读取成功");
        } catch {
          this.$message.error("文件内容不是有效的JSON格式");
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
        this.$message.success("模板已复制到剪贴板");
      }).catch(() => {
        this.$message.error("复制失败，请手动复制");
      });
    },
    downloadTemplate() {
      const blob = new Blob([this.jsonTemplate], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "药品导入模板.json";
      a.click();
      URL.revokeObjectURL(url);
    },
    async handleImport() {
      if (!this.importJson.trim()) {
        this.$message.warning("请输入JSON数据");
        return;
      }
      try {
        const data = JSON.parse(this.importJson);
        if (!Array.isArray(data)) {
          this.$message.error("JSON数据必须是数组格式");
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
        this.$message.success(`导入完成：成功${successCount}条，失败${failCount}条`);
        this.importDialogVisible = false;
        this.loadDrugs();
      } catch (e) {
        this.$message.error("JSON格式错误，请检查格式");
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
