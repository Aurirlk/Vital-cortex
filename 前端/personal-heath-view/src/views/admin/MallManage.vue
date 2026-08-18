<template>
  <div class="manage-container">
    <div class="manage-header">
      <h2>商城管理</h2>
      <el-button type="primary" @click="showAddProduct = true">新增商品</el-button>
    </div>

    <div class="tabs">
      <div class="tab" :class="{ active: activeTab === 'product' }" @click="activeTab = 'product'">商品管理</div>
      <div class="tab" :class="{ active: activeTab === 'order' }" @click="activeTab = 'order'">订单管理</div>
      <div class="tab" :class="{ active: activeTab === 'category' }" @click="activeTab = 'category'">分类管理</div>
    </div>

    <!--  -->
    <div v-if="activeTab === 'product'" class="tab-content">
      <el-table :data="products" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="商品名称" />
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column prop="price" label="价格" width="100" />
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="salesCount" label="销量" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已上架' : '已下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="editProduct(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleProduct(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" type="danger" @click="deleteProduct(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'order'" class="tab-content">
      <el-table :data="orders" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="userName" label="用户" />
        <el-table-column prop="totalAmount" label="金额" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="['warning', 'primary', 'success', 'info', '', 'danger'][row.status]">
              {{ ['待付款', '待发货', '待收货', '已完成', '已取消', '退款中'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'category'" class="tab-content">
      <el-button type="primary" @click="showAddCategory = true" style="margin-bottom: 16px">新增分类</el-button>
      <el-table :data="categories" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
      </el-table>
    </div>

    <!--  -->
    <el-dialog v-model="showAddProduct" title="新增商品" width="600px">
      <el-form :model="productForm" label-width="80px">
        <el-form-item label="商品名称"><el-input v-model="productForm.name" /></el-form-item>
        <el-form-item label="商品分类">
          <el-select v-model="productForm.categoryId">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格"><el-input-number v-model="productForm.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="productForm.stock" :min="0" /></el-form-item>
        <el-form-item label="商品描述"><el-input v-model="productForm.description" type="textarea" /></el-form-item>
        <el-form-item label="URL"><el-input v-model="productForm.cover" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddProduct = false">取消</el-button>
        <el-button type="primary" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showAddCategory" title="新增分类" width="400px">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item label="分类名称"><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddCategory = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "MallManage",
  data() {
    return {
      activeTab: "product",
      products: [],
      orders: [],
      categories: [],
      showAddProduct: false,
      showAddCategory: false,
      productForm: { name: "", categoryId: null, price: 0, stock: 0, description: "", cover: "" },
      categoryForm: { name: "", sortOrder: 0 },
    };
  },
  created() {
    this.loadAll();
  },
  methods: {
    async loadAll() {
      try {
        const [prodRes, orderRes, catRes] = await Promise.all([
          request.post("mall/product/query", {}),
          request.get("mall/order/list"),
          request.get("mall/categories"),
        ]);
        if (prodRes.data.code === 200) this.products = prodRes.data.data;
        if (orderRes.data.code === 200) this.orders = orderRes.data.data;
        if (catRes.data.code === 200) this.categories = catRes.data.data;
      } catch (e) { console.error(e); }
    },
    async saveProduct() {
      try {
        await request.post("mall/product/save", this.productForm);
        this.showAddProduct = false;
        this.loadAll();
        this.$message.success("保存成功");
      } catch (e) { this.$message.error("保存失败"); }
    },
    editProduct(row) { this.productForm = { ...row }; this.showAddProduct = true; },
    async toggleProduct(row) {
      await request.put("mall/product/update", { ...row, status: row.status === 1 ? 0 : 1 });
      this.loadAll();
    },
    async deleteProduct(id) {
      await request.post("mall/product/batchDelete", [id]);
      this.loadAll();
    },
    async saveCategory() {
      try {
        await request.post("mall/category/save", this.categoryForm);
        this.showAddCategory = false;
        this.loadAll();
        this.$message.success("保存成功");
      } catch (e) { this.$message.error("保存失败"); }
    },
  },
};
</script>

<style scoped>
.manage-container { padding: 20px; }
.manage-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.manage-header h2 { margin: 0; font-size: 20px; }
.tabs { display: flex; gap: 4px; background: #f5f5f5; padding: 4px; border-radius: 10px; margin-bottom: 20px; }
.tab { padding: 10px 20px; border-radius: 8px; cursor: pointer; font-size: 14px; transition: all 0.2s; }
.tab.active { background: #fff; color: #ff2442; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
</style>
