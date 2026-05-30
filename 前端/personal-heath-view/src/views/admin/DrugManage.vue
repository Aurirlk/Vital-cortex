<template>
  <div class="manage-container">
    <!-- 操作栏 -->
    <div class="operate-bar">
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>
        新增药品
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
          current: (this.currentPage - 1) * this.pageSize,
          size: this.pageSize,
        };
        if (this.searchName) params.name = this.searchName;
        if (this.searchCategory) params.category = this.searchCategory;
        const res = await this.$axios.post("/drug/query", params);
        if (res.data.code === 200) {
          this.drugList = res.data.data || [];
          this.total = res.data.totalCount || 0;
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
</style>
