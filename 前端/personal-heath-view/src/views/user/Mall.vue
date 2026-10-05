<template>
  <div class="mall-container">
    <div class="mall-header">
      <h1 class="page-title">健康商城</h1>
      <p class="page-desc">药品 · 医疗器械 · 保健品，精选健康好物</p>
    </div>

    <!-- 类型/订单页签 -->
    <div class="mall-tabs">
      <div
        class="mall-tab"
        :class="{ active: activeTab === 'shop' }"
        @click="switchTab('shop')"
      >
        选购商品
      </div>
      <div
        class="mall-tab"
        :class="{ active: activeTab === 'orders' }"
        @click="switchTab('orders')"
      >
        我的订单
      </div>
    </div>

    <template v-if="activeTab === 'shop'">
      <!-- 商品类型 -->
      <div class="type-bar">
        <div
          class="type-item"
          :class="{ active: !selectedType }"
          @click="
            selectedType = null;
            loadProducts();
          "
        >
          全部
        </div>
        <div
          class="type-item"
          :class="{ active: selectedType === 'drug' }"
          @click="
            selectedType = 'drug';
            loadProducts();
          "
        >
          药品
        </div>
        <div
          class="type-item"
          :class="{ active: selectedType === 'device' }"
          @click="
            selectedType = 'device';
            loadProducts();
          "
        >
          医疗器械
        </div>
        <div
          class="type-item"
          :class="{ active: selectedType === 'health' }"
          @click="
            selectedType = 'health';
            loadProducts();
          "
        >
          保健品
        </div>
        <router-link to="/user/drug" class="type-link"
          >药品百科查询 →</router-link
        >
      </div>

      <!-- 分类 -->
      <div class="category-bar">
        <div
          class="category-item"
          :class="{ active: !selectedCategory }"
          @click="
            selectedCategory = null;
            loadProducts();
          "
        >
          全部分类
        </div>
        <div
          v-for="cat in categories"
          :key="cat.id"
          class="category-item"
          :class="{ active: selectedCategory === cat.id }"
          @click="
            selectedCategory = cat.id;
            loadProducts();
          "
        >
          {{ cat.name }}
        </div>
      </div>

      <!-- 搜索 -->
      <div class="search-bar">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索商品"
          @keyup.enter="loadProducts"
        />
        <button class="search-btn" @click="loadProducts">搜索</button>
      </div>

      <!-- 商品 -->
      <div class="product-grid">
        <div
          v-for="product in products"
          :key="product.id"
          class="product-card"
          @click="viewProduct(product)"
        >
          <div class="product-card__img-wrap">
            <img
              :src="product.cover || '/default-product.svg'"
              class="product-card__img"
            />
            <div v-if="product.isHot" class="product-card__tag tag--hot">热销</div>
            <div v-if="product.isNew" class="product-card__tag tag--new">新品</div>
          </div>
          <div class="product-card__body">
            <h3 class="product-card__name">{{ product.name }}</h3>
            <div class="product-card__price">
              <span class="price-current">¥{{ product.price }}</span>
              <span v-if="product.originalPrice" class="price-original"
                >¥{{ product.originalPrice }}</span
              >
            </div>
            <div class="product-card__meta">
              <span>已售 {{ product.salesCount }}</span>
              <span>库存 {{ product.stock }}</span>
            </div>
            <button class="product-card__btn" @click.stop="addToCart(product)">
              加入购物车
            </button>
          </div>
        </div>
        <div v-if="products.length === 0" class="empty-products">
          暂无相关商品
        </div>
      </div>
    </template>

    <!-- 我的订单 -->
    <div v-else class="orders-view">
      <div v-if="orders.length === 0" class="empty-state">
        暂无订单，快去选购心仪好物吧
      </div>
      <div v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-card__head">
          <span class="order-card__no">{{ order.orderNo }}</span>
          <el-tag :type="orderStatusTag(order.status)" size="small">{{
            orderStatusLabel(order.status)
          }}</el-tag>
        </div>
        <div class="order-card__items">
          <div v-if="order.items && order.items.length">
            <div
              v-for="item in order.items"
              :key="item.id"
              class="order-item-row"
            >
              <span>{{ item.productName }} × {{ item.quantity }}</span>
              <span>¥{{ item.subtotal }}</span>
            </div>
          </div>
          <div v-else class="order-item-row">商品详情</div>
        </div>
        <div class="order-card__foot">
          <span class="order-card__time">{{ order.createTime }}</span>
          <span class="order-card__amount"
            >实付 <b>¥{{ order.actualAmount || order.totalAmount }}</b></span
          >
          <el-button
            v-if="order.status === 0"
            size="small"
            type="danger"
            @click="payOrder(order)"
            >去支付</el-button
          >
        </div>
      </div>
    </div>

    <!--  -->
    <div class="cart-float" @click="showCart = true">
      <el-icon class="cart-icon"><ShoppingCart /></el-icon>
      <span v-if="cartCount > 0" class="cart-badge">{{ cartCount }}</span>
    </div>

    <!--  -->
    <div v-if="showCart" class="cart-modal" @click.self="showCart = false">
      <div class="cart-panel">
        <div class="cart-panel__header">
          <h3>购物车</h3>
          <button class="close-btn" @click="showCart = false">×</button>
        </div>
        <div v-if="cartItems.length === 0" class="empty-state">
          购物车空空如也，去挑选心仪好物吧
        </div>
        <div v-else class="cart-list">
          <div v-for="item in cartItems" :key="item.id" class="cart-item">
            <img
              :src="item.productCover || '/default-product.svg'"
              class="cart-item__img"
            />
            <div class="cart-item__info">
              <div class="cart-item__name">{{ item.productName }}</div>
              <div class="cart-item__price">¥{{ item.productPrice }}</div>
            </div>
            <div class="cart-item__qty">
              <button @click="updateQty(item, item.quantity - 1)">-</button>
              <span>{{ item.quantity }}</span>
              <button @click="updateQty(item, item.quantity + 1)">+</button>
            </div>
            <button class="cart-item__del" @click="removeFromCart(item)">
              删除
            </button>
          </div>
        </div>
        <div class="cart-panel__footer">
          <div class="cart-total">
            合计：<span class="total-price">¥{{ cartTotal }}</span>
          </div>
          <button class="checkout-btn" @click="checkout">去结算</button>
        </div>
      </div>
    </div>

    <!--  -->
    <div
      v-if="selectedProduct"
      class="product-modal"
      @click.self="selectedProduct = null"
    >
      <div class="product-panel">
        <button class="close-btn" @click="selectedProduct = null">×</button>
        <img
          :src="selectedProduct.cover || '/default-product.svg'"
          class="product-panel__img"
        />
        <h2 class="product-panel__name">{{ selectedProduct.name }}</h2>
        <div class="product-panel__price">¥{{ selectedProduct.price }}</div>
        <p class="product-panel__desc">{{ selectedProduct.description }}</p>
        <button
          class="add-cart-btn"
          @click="
            addToCart(selectedProduct);
            selectedProduct = null;
          "
        >
          加入购物车
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "Mall",
  data() {
    return {
      activeTab: "shop",
      categories: [],
      products: [],
      orders: [],
      cartItems: [],
      selectedCategory: null,
      selectedType: null,
      keyword: "",
      showCart: false,
      selectedProduct: null,
    };
  },
  computed: {
    cartCount() {
      return this.cartItems.reduce((sum, i) => sum + i.quantity, 0);
    },
    cartTotal() {
      return this.cartItems
        .reduce((sum, i) => sum + i.productPrice * i.quantity, 0)
        .toFixed(2);
    },
  },
  created() {
    // 支持从个人中心等入口带参数跳转：?tab=orders / ?type=drug
    const query = this.$route.query || {};
    if (query.tab === "orders") {
      this.activeTab = "orders";
    }
    if (query.type) {
      this.selectedType = query.type;
    }
    this.loadCategories();
    this.loadProducts();
    this.loadCart();
    this.loadOrders();
  },
  methods: {
    switchTab(tab) {
      this.activeTab = tab;
      if (tab === "orders") this.loadOrders();
    },
    orderStatusLabel(s) {
      return ["待付款", "待发货", "待收货", "已完成", "已取消", "退款中"][s] || "未知";
    },
    orderStatusTag(s) {
      return ["warning", "primary", "success", "success", "info", "danger"][s] || "info";
    },
    async loadOrders() {
      try {
        const { data } = await request.get("mall/order/my");
        if (data.code === 200) this.orders = data.data || [];
      } catch (e) {
        console.error(e);
      }
    },
    async payOrder(order) {
      try {
        const { data } = await request.post(`mall/order/pay/${order.id}`);
        if (data.code === 200) {
          this.$message.success("支付成功");
          this.loadOrders();
        }
      } catch (e) {
        this.$message.error("支付失败");
      }
    },
    async loadCategories() {
      try {
        const { data } = await request.get("mall/categories");
        if (data.code === 200) this.categories = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async loadProducts() {
      try {
        const params = {};
        if (this.selectedCategory) params.categoryId = this.selectedCategory;
        if (this.selectedType) params.productType = this.selectedType;
        if (this.keyword) params.keyword = this.keyword;
        const { data } = await request.post("mall/product/query", params);
        if (data.code === 200) this.products = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async loadCart() {
      try {
        const { data } = await request.get("mall/cart/list");
        if (data.code === 200) this.cartItems = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    viewProduct(product) {
      this.selectedProduct = product;
    },
    async addToCart(product) {
      try {
        await request.post("mall/cart/add", null, {
          params: { productId: product.id, quantity: 1 },
        });
        this.loadCart();
        this.$message.success("已加入购物车");
      } catch (e) {
        this.$message.error("加入购物车失败");
      }
    },
    async updateQty(item, qty) {
      if (qty <= 0) {
        this.removeFromCart(item);
        return;
      }
      try {
        await request.put("mall/cart/update", null, {
          params: { id: item.id, quantity: qty },
        });
        this.loadCart();
      } catch (e) {
        console.error(e);
      }
    },
    async removeFromCart(item) {
      try {
        await request.delete(`mall/cart/${item.id}`);
        this.loadCart();
      } catch (e) {
        console.error(e);
      }
    },
    async checkout() {
      if (this.cartItems.length === 0) {
        this.$message.warning("购物车为空，请先添加商品");
        return;
      }
      try {
        const { data } = await request.post("mall/order/create", null, {
          params: { addressId: 1 },
        });
        if (data.code === 200) {
          await request.post(`mall/order/pay/${data.data.id}`);
          this.$swal.fire({
            title: "下单成功",
            icon: "success",
            timer: 1500,
            showConfirmButton: false,
          });
          this.showCart = false;
          this.loadCart();
        }
      } catch (e) {
        this.$message.error("结算失败");
      }
    },
  },
};
</script>

<style scoped>
.mall-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.mall-header {
  text-align: center;
  margin-bottom: 24px;
}
.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 8px;
}
.page-desc {
  font-size: 15px;
  color: #999;
  margin: 0;
}

.mall-tabs {
  display: flex;
  gap: 4px;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 10px;
  margin-bottom: 20px;
  width: fit-content;
}
.mall-tab {
  padding: 9px 26px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #555;
  transition: all 0.2s;
}
.mall-tab.active {
  background: #fff;
  color: #ff2442;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.type-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.type-item {
  padding: 8px 20px;
  border-radius: 20px;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.type-item:hover,
.type-item.active {
  background: #ff2442;
  color: #fff;
}
.type-link {
  margin-left: auto;
  font-size: 13px;
  color: #0050cb;
  text-decoration: none;
}
.type-link:hover {
  text-decoration: underline;
}

.category-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.category-item {
  padding: 8px 20px;
  border-radius: 20px;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.category-item:hover,
.category-item.active {
  background: #ff2442;
  color: #fff;
}

.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
}
.search-input {
  flex: 1;
  height: 40px;
  padding: 0 16px;
  border: 2px solid #f0f0f0;
  border-radius: 10px;
  font-size: 14px;
}
.search-input:focus {
  outline: none;
  border-color: #ff2442;
}
.search-btn {
  padding: 0 24px;
  background: #ff2442;
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.product-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s;
  border: 1px solid #f0f0f0;
}
.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.product-card__img-wrap {
  position: relative;
  height: 180px;
  overflow: hidden;
}
.product-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-card__tag {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  color: #fff;
}
.tag--hot {
  background: #ff2442;
}
.tag--new {
  background: #07c160;
}
.product-card__body {
  padding: 12px;
}
.product-card__name {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-card__price {
  margin-bottom: 8px;
}
.price-current {
  font-size: 18px;
  font-weight: 700;
  color: #ff2442;
}
.price-original {
  font-size: 13px;
  color: #999;
  text-decoration: line-through;
  margin-left: 8px;
}
.product-card__meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
}
.product-card__btn {
  width: 100%;
  padding: 8px;
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}

.cart-float {
  position: fixed;
  bottom: 30px;
  right: 30px;
  width: 56px;
  height: 56px;
  background: #ff2442;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.4);
  z-index: 100;
}
.cart-icon {
  font-size: 24px;
}
.cart-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 20px;
  height: 20px;
  background: #07c160;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cart-modal,
.product-modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cart-panel {
  background: #fff;
  border-radius: 16px;
  width: 400px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}
.cart-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}
.cart-panel__header h3 {
  margin: 0;
  font-size: 18px;
}
.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #999;
}
.cart-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.cart-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}
.cart-item__img {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  object-fit: cover;
}
.cart-item__info {
  flex: 1;
}
.cart-item__name {
  font-size: 14px;
  font-weight: 500;
}
.cart-item__price {
  font-size: 14px;
  color: #ff2442;
  font-weight: 600;
}
.cart-item__qty {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cart-item__qty button {
  width: 24px;
  height: 24px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
.cart-item__del {
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  font-size: 12px;
}
.cart-panel__footer {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cart-total {
  font-size: 14px;
  color: #666;
}
.total-price {
  font-size: 20px;
  font-weight: 700;
  color: #ff2442;
}
.checkout-btn {
  padding: 10px 32px;
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
}

.product-panel {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  max-width: 500px;
  width: 90%;
  position: relative;
}
.product-panel__img {
  width: 100%;
  height: 300px;
  object-fit: cover;
  border-radius: 12px;
  margin-bottom: 16px;
}
.product-panel__name {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 8px;
}
.product-panel__price {
  font-size: 24px;
  font-weight: 700;
  color: #ff2442;
  margin: 0 0 12px;
}
.product-panel__desc {
  font-size: 14px;
  color: #666;
  margin: 0 0 20px;
  line-height: 1.6;
}
.add-cart-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

.empty-products {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 0;
  color: #999;
}

.orders-view {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #f0f0f0;
}
.order-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.order-card__no {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}
.order-card__items {
  padding: 10px 0;
  border-top: 1px dashed #eee;
  border-bottom: 1px dashed #eee;
  margin-bottom: 10px;
}
.order-item-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
  color: #555;
}
.order-card__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-card__time {
  font-size: 12px;
  color: #999;
}
.order-card__amount {
  font-size: 13px;
  color: #333;
}
.order-card__amount b {
  color: #ff2442;
  font-size: 16px;
}
</style>
