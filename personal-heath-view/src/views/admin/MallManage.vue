<template>
  <div class="manage-container">
    <div class="manage-header">
      <h2>商城管理</h2>
      <div class="header-actions">
        <el-button type="warning" plain @click="showImport = true"
          >JSON 导入</el-button
        >
        <el-button type="primary" @click="openAddProduct">新增商品</el-button>
      </div>
    </div>

    <div class="tabs">
      <div
        class="tab"
        :class="{ active: activeTab === 'product' }"
        @click="activeTab = 'product'"
      >
        商品管理
      </div>
      <div
        class="tab"
        :class="{ active: activeTab === 'order' }"
        @click="activeTab = 'order'"
      >
        订单管理
      </div>
      <div
        class="tab"
        :class="{ active: activeTab === 'category' }"
        @click="activeTab = 'category'"
      >
        分类管理
      </div>
    </div>

    <!-- ============ 商品管理 ============ -->
    <div v-if="activeTab === 'product'" class="tab-content">
      <div class="filter-bar">
        <div
          class="filter-item"
          :class="{ active: !productFilter }"
          @click="productFilter = ''; loadProducts()"
        >
          全部
        </div>
        <div
          class="filter-item"
          :class="{ active: productFilter === 'drug' }"
          @click="productFilter = 'drug'; loadProducts()"
        >
          药品
        </div>
        <div
          class="filter-item"
          :class="{ active: productFilter === 'device' }"
          @click="productFilter = 'device'; loadProducts()"
        >
          医疗器械
        </div>
        <div
          class="filter-item"
          :class="{ active: productFilter === 'health' }"
          @click="productFilter = 'health'; loadProducts()"
        >
          保健品
        </div>
      </div>

      <el-table :data="products" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="参考图" width="90">
          <template #default="{ row }">
            <el-image
              v-if="row.cover"
              :src="row.cover"
              fit="cover"
              style="width: 56px; height: 56px; border-radius: 8px"
              :preview-src-list="[row.cover]"
              preview-teleported
            />
            <div v-else class="no-cover">无图</div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.productType)" size="small">{{
              typeLabel(row.productType)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column prop="price" label="价格" width="90" />
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="salesCount" label="销量" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{
              row.status === 1 ? "已上架" : "已下架"
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="editProduct(row)">编辑</el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleProduct(row)"
            >
              {{ row.status === 1 ? "下架" : "上架" }}
            </el-button>
            <el-button size="small" type="danger" @click="deleteProduct(row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ============ 订单管理 ============ -->
    <div v-if="activeTab === 'order'" class="tab-content">
      <el-table :data="orders" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column prop="userName" label="用户" width="110" />
        <el-table-column prop="totalAmount" label="金额" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.status)">{{
              orderStatusLabel(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="paymentMethod" label="支付方式" width="100" />
        <el-table-column prop="paymentTime" label="支付时间" width="170" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              size="small"
              type="success"
              @click="updateOrderStatus(row, 2)"
              >发货</el-button
            >
            <el-button
              v-if="row.status === 0"
              size="small"
              type="danger"
              @click="updateOrderStatus(row, 4)"
              >取消</el-button
            >
            <el-button size="small" @click="viewOrderDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ============ 分类管理 ============ -->
    <div v-if="activeTab === 'category'" class="tab-content">
      <el-button
        type="primary"
        @click="showAddCategory = true"
        style="margin-bottom: 16px"
        >新增分类</el-button
      >
      <el-table :data="categories" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
      </el-table>
    </div>

    <!-- ============ 商品新增/编辑弹窗 ============ -->
    <el-dialog
      v-model="showAddProduct"
      :title="editingId ? '编辑商品' : '新增商品'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form :model="productForm" label-width="90px">
        <el-form-item label="商品名称" required>
          <el-input v-model="productForm.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品类型" required>
          <el-radio-group v-model="productForm.productType">
            <el-radio-button value="drug">药品</el-radio-button>
            <el-radio-button value="device">医疗器械</el-radio-button>
            <el-radio-button value="health">保健品</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品分类">
          <el-select v-model="productForm.categoryId" style="width: 100%">
            <el-option
              v-for="c in categories"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="参考图">
          <div class="cover-upload">
            <el-upload
              :action="$uploadUrl"
              :headers="$uploadHeaders"
              :show-file-list="false"
              :on-success="handleCoverSuccess"
              :before-upload="beforeCoverUpload"
            >
              <img
                v-if="productForm.cover"
                :src="productForm.cover"
                class="cover-preview"
                alt=""
              />
              <el-icon v-else class="cover-placeholder"><Plus /></el-icon>
            </el-upload>
            <el-input
              v-model="productForm.cover"
              placeholder="或直接粘贴图片 URL"
              style="margin-left: 12px; flex: 1"
            />
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="售价">
              <el-input-number
                v-model="productForm.price"
                :min="0"
                :precision="2"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="原价">
              <el-input-number
                v-model="productForm.originalPrice"
                :min="0"
                :precision="2"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位">
              <el-input v-model="productForm.unit" placeholder="件/盒/台" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="库存">
              <el-input-number
                v-model="productForm.stock"
                :min="0"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="热销">
              <el-switch v-model="productForm.isHot" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="新品">
              <el-switch v-model="productForm.isNew" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商品描述">
          <el-input
            v-model="productForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入商品描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddProduct = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProduct"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <!-- ============ 分类弹窗 ============ -->
    <el-dialog v-model="showAddCategory" title="新增分类" width="400px">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item label="分类名称">
          <el-input v-model="categoryForm.name" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddCategory = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ JSON 导入弹窗 ============ -->
    <el-dialog
      v-model="showImport"
      title="JSON 批量导入商品"
      width="760px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持上传 JSON / CSV / Excel(.xlsx) / TXT 文件，系统会自动解析并可选 AI 智能映射为商品字段（名称、类型、价格、库存等）。"
        style="margin-bottom: 12px"
      />
      <div class="import-row">
        <el-upload
          :action="parseUrl"
          :headers="$uploadHeaders"
          :show-file-list="false"
          :on-success="handleParseSuccess"
          :on-error="handleParseError"
          :before-upload="beforeParseUpload"
          name="file"
          :data="{ target: 'product', ai: true }"
        >
          <el-button type="primary" :loading="parsing">选择文件解析</el-button>
        </el-upload>
        <el-checkbox v-model="importUseAi" style="margin-left: 12px"
          >AI 智能映射字段</el-checkbox
        >
        <span class="import-hint">（未勾选则按原字段导入）</span>
      </div>
      <div v-if="parsedRows.length > 0" class="import-preview">
        <div class="import-preview__head">
          <span>解析到 {{ parsedRows.length }} 条数据</span>
          <el-button size="small" type="success" :loading="importing" @click="confirmImport"
            >确认导入</el-button
          >
        </div>
        <el-table :data="parsedRows" height="320" stripe size="small">
          <el-table-column
            v-for="col in previewCols"
            :key="col"
            :prop="col"
            :label="col"
            min-width="110"
            show-overflow-tooltip
          />
        </el-table>
      </div>
    </el-dialog>

    <!-- 订单详情 -->
    <el-dialog v-model="showOrderDetail" title="订单详情" width="560px">
      <div v-if="currentOrder" class="order-detail">
        <p><b>订单号：</b>{{ currentOrder.orderNo }}</p>
        <p><b>用户：</b>{{ currentOrder.userName }}</p>
        <p><b>金额：</b>¥{{ currentOrder.totalAmount }}</p>
        <p><b>状态：</b>{{ orderStatusLabel(currentOrder.status) }}</p>
        <p><b>支付方式：</b>{{ currentOrder.paymentMethod || "—" }}</p>
        <p><b>支付时间：</b>{{ currentOrder.paymentTime || "—" }}</p>
        <p><b>创建时间：</b>{{ currentOrder.createTime }}</p>
        <el-divider />
        <div v-if="currentOrder.items && currentOrder.items.length" class="order-items">
          <div v-for="item in currentOrder.items" :key="item.id" class="order-item">
            <span>{{ item.productName }} × {{ item.quantity }}</span>
            <span>¥{{ item.subtotal }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";
import { URL_API } from "@/utils/request.js";
import { Plus } from "@element-plus/icons-vue";

export default {
  name: "MallManage",
  components: { Plus },
  data() {
    return {
      activeTab: "product",
      products: [],
      orders: [],
      categories: [],
      loading: false,
      saving: false,
      productFilter: "",
      showAddProduct: false,
      showAddCategory: false,
      editingId: null,
      productForm: this.emptyForm(),
      categoryForm: { name: "", sortOrder: 0 },
      // 导入
      showImport: false,
      parseUrl: URL_API + "/file/parse-to-json",
      parsing: false,
      importing: false,
      importUseAi: true,
      parsedRows: [],
      previewCols: [],
      // 订单详情
      showOrderDetail: false,
      currentOrder: null,
    };
  },
  created() {
    this.loadAll();
  },
  methods: {
    emptyForm() {
      return {
        name: "",
        productType: "drug",
        categoryId: null,
        cover: "",
        price: 0,
        originalPrice: null,
        unit: "件",
        stock: 0,
        description: "",
        isHot: 0,
        isNew: 0,
        status: 1,
      };
    },
    async loadAll() {
      this.loading = true;
      try {
        const [prodRes, orderRes, catRes] = await Promise.all([
          request.post("mall/product/query", {}),
          request.get("mall/order/list"),
          request.get("mall/categories"),
        ]);
        if (prodRes.data.code === 200) this.products = prodRes.data.data;
        if (orderRes.data.code === 200) this.orders = orderRes.data.data;
        if (catRes.data.code === 200) this.categories = catRes.data.data;
      } catch (e) {
        console.error(e);
      } finally {
        this.loading = false;
      }
    },
    async loadProducts() {
      this.loading = true;
      try {
        const params = {};
        if (this.productFilter) params.productType = this.productFilter;
        const { data } = await request.post("mall/product/query", params);
        if (data.code === 200) this.products = data.data;
      } catch (e) {
        console.error(e);
      } finally {
        this.loading = false;
      }
    },
    typeLabel(t) {
      return { drug: "药品", device: "医疗器械", health: "保健品" }[t] || "其他";
    },
    typeTagType(t) {
      return { drug: "danger", device: "warning", health: "success" }[t] || "info";
    },
    orderStatusLabel(s) {
      return ["待付款", "待发货", "待收货", "已完成", "已取消", "退款中"][s] || "未知";
    },
    orderStatusTag(s) {
      return ["warning", "primary", "success", "success", "info", "danger"][s] || "info";
    },
    openAddProduct() {
      this.editingId = null;
      this.productForm = this.emptyForm();
      this.showAddProduct = true;
    },
    editProduct(row) {
      this.editingId = row.id;
      this.productForm = {
        name: row.name,
        productType: row.productType || "health",
        categoryId: row.categoryId,
        cover: row.cover || "",
        price: row.price,
        originalPrice: row.originalPrice,
        unit: row.unit || "件",
        stock: row.stock,
        description: row.description || "",
        isHot: row.isHot,
        isNew: row.isNew,
        status: row.status,
      };
      this.showAddProduct = true;
    },
    async saveProduct() {
      if (!this.productForm.name || !this.productForm.name.trim()) {
        this.$message.warning("请输入商品名称");
        return;
      }
      this.saving = true;
      try {
        if (this.editingId) {
          await request.put("mall/product/update", {
            id: this.editingId,
            ...this.productForm,
          });
        } else {
          await request.post("mall/product/save", this.productForm);
        }
        this.showAddProduct = false;
        this.loadProducts();
        this.loadAll();
        this.$message.success("保存成功");
      } catch (e) {
        this.$message.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
    async toggleProduct(row) {
      await request.put("mall/product/update", {
        id: row.id,
        status: row.status === 1 ? 0 : 1,
      });
      this.loadProducts();
      this.loadAll();
    },
    async deleteProduct(id) {
      const confirmed = await this.$swalConfirm({
        title: "确认删除",
        text: "删除后不可恢复，确定删除该商品吗？",
        icon: "warning",
      });
      if (!confirmed) return;
      await request.post("mall/product/batchDelete", [id]);
      this.$message.success("删除成功");
      this.loadProducts();
      this.loadAll();
    },
    beforeCoverUpload(file) {
      if (!file.type.startsWith("image/")) {
        this.$message.error("只能上传图片");
        return false;
      }
      return true;
    },
    handleCoverSuccess(res) {
      if (res.code === 200) {
        this.productForm.cover = res.data;
        this.$message.success("图片已上传");
      } else {
        this.$message.error("图片上传失败");
      }
    },
    async saveCategory() {
      if (!this.categoryForm.name) {
        this.$message.warning("请输入分类名称");
        return;
      }
      try {
        await request.post("mall/category/save", this.categoryForm);
        this.showAddCategory = false;
        this.categoryForm = { name: "", sortOrder: 0 };
        this.loadAll();
        this.$message.success("保存成功");
      } catch (e) {
        this.$message.error("保存失败");
      }
    },
    // ===== JSON 导入 =====
    beforeParseUpload(file) {
      const name = file.name || "";
      const ext = name.includes(".") ? name.split(".").pop().toLowerCase() : "";
      if (!["json", "csv", "tsv", "xlsx", "txt"].includes(ext)) {
        this.$message.error("仅支持 json/csv/tsv/xlsx/txt 文件");
        return false;
      }
      if (file.size > 5 * 1024 * 1024) {
        this.$message.error("文件不能超过 5MB");
        return false;
      }
      this.parsing = true;
      return true;
    },
    handleParseSuccess(res) {
      this.parsing = false;
      if (res.code !== 200) {
        this.$message.error(res.msg || "解析失败");
        return;
      }
      this.parsedRows = res.data.rows || [];
      if (this.parsedRows.length > 0) {
        this.previewCols = Object.keys(this.parsedRows[0]).slice(0, 8);
        this.$message.success(`解析成功，共 ${this.parsedRows.length} 条`);
      } else {
        this.$message.warning("未解析到数据");
      }
    },
    handleParseError() {
      this.parsing = false;
      this.$message.error("文件解析失败，请检查文件内容");
    },
    async confirmImport() {
      if (this.parsedRows.length === 0) return;
      this.importing = true;
      let success = 0;
      let fail = 0;
      for (const row of this.parsedRows) {
        try {
          const payload = {
            name: row.name || row["商品名称"] || row["名称"] || "",
            productType: this.normalizeType(row.productType || row["类型"] || "health"),
            categoryId: row.categoryId || row["分类ID"] || null,
            cover: row.cover || row["图片"] || row["参考图"] || "",
            price: Number(row.price || row["价格"] || 0) || 0,
            originalPrice: Number(row.originalPrice || row["原价"] || 0) || null,
            unit: row.unit || row["单位"] || "件",
            stock: Number(row.stock || row["库存"] || 0) || 0,
            description: row.description || row["描述"] || row["说明"] || "",
            isHot: row.isHot || 0,
            isNew: row.isNew || 0,
            status: 1,
          };
          if (!payload.name) {
            fail++;
            continue;
          }
          const { data } = await request.post("mall/product/save", payload);
          if (data.code === 200) success++;
          else fail++;
        } catch (e) {
          fail++;
        }
      }
      this.importing = false;
      this.$message.success(`导入完成：成功 ${success} 条，失败 ${fail} 条`);
      this.parsedRows = [];
      this.showImport = false;
      this.loadProducts();
      this.loadAll();
    },
    normalizeType(t) {
      const v = String(t || "").toLowerCase();
      if (v.includes("药") || v === "drug") return "drug";
      if (v.includes("器械") || v === "device") return "device";
      return "health";
    },
    // ===== 订单操作 =====
    async updateOrderStatus(row, status) {
      try {
        await request.put(`mall/order/status/${row.id}`, null, {
          params: { status },
        });
        this.$message.success("操作成功");
        this.loadAll();
      } catch (e) {
        this.$message.error("操作失败");
      }
    },
    viewOrderDetail(row) {
      this.currentOrder = row;
      this.showOrderDetail = true;
    },
  },
};
</script>

<style scoped>
.manage-container {
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  min-height: 500px;
}
.manage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.manage-header h2 {
  margin: 0;
  font-size: 20px;
  color: #1a1a1a;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.tabs {
  display: flex;
  gap: 4px;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 10px;
  margin-bottom: 20px;
}
.tab {
  padding: 10px 20px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  color: #555;
}
.tab.active {
  background: #fff;
  color: #0050cb;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-item {
  padding: 7px 18px;
  border-radius: 20px;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 13px;
  color: #555;
  transition: all 0.2s;
}
.filter-item:hover,
.filter-item.active {
  background: #0050cb;
  color: #fff;
}
.no-cover {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #999;
}
.cover-upload {
  display: flex;
  align-items: center;
  width: 100%;
}
.cover-preview {
  width: 88px;
  height: 88px;
  border-radius: 10px;
  object-fit: cover;
  border: 2px solid #e5e7eb;
  cursor: pointer;
}
.cover-placeholder {
  width: 88px;
  height: 88px;
  border-radius: 10px;
  border: 1px dashed #d9d9d9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #999;
  cursor: pointer;
}
.import-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.import-hint {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}
.import-preview {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 12px;
}
.import-preview__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 14px;
  color: #333;
}
.order-detail p {
  margin: 6px 0;
  font-size: 14px;
  color: #333;
}
.order-item {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  color: #555;
}
</style>
