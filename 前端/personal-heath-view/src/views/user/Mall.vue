<template>
  <div class="mall-container">
    <div class="mall-header">
      <h1 class="page-title"></h1>
      <p class="page-desc"></p>
    </div>

    <!--  -->
    <div class="category-bar">
      <div class="category-item" :class="{ active: !selectedCategory }" @click="selectedCategory = null; loadProducts()">
        
      </div>
      <div v-for="cat in categories" :key="cat.id"
           class="category-item" :class="{ active: selectedCategory === cat.id }"
           @click="selectedCategory = cat.id; loadProducts()">
        {{ cat.name }}
      </div>
    </div>

    <!--  -->
    <div class="search-bar">
      <input v-model="keyword" class="search-input" placeholder="..." @keyup.enter="loadProducts" />
      <button class="search-btn" @click="loadProducts"></button>
    </div>

    <!--  -->
    <div class="product-grid">
      <div v-for="product in products" :key="product.id" class="product-card" @click="viewProduct(product)">
        <div class="product-card__img-wrap">
          <img :src="product.cover || '/default-product.png'" class="product-card__img" />
          <div v-if="product.isHot" class="product-card__tag tag--hot"></div>
          <div v-if="product.isNew" class="product-card__tag tag--new"></div>
        </div>
        <div class="product-card__body">
          <h3 class="product-card__name">{{ product.name }}</h3>
          <div class="product-card__price">
            <span class="price-current">¥{{ product.price }}</span>
            <span v-if="product.originalPrice" class="price-original">¥{{ product.originalPrice }}</span>
          </div>
          <div class="product-card__meta">
            <span> {{ product.salesCount }}</span>
            <span> {{ product.stock }}</span>
          </div>
          <button class="product-card__btn" @click.stop="addToCart(product)"></button>
        </div>
      </div>
    </div>

    <!--  -->
    <div class="cart-float" @click="showCart = true">
      <span class="cart-icon"></span>
      <span v-if="cartCount > 0" class="cart-badge">{{ cartCount }}</span>
    </div>

    <!--  -->
    <div v-if="showCart" class="cart-modal" @click.self="showCart = false">
      <div class="cart-panel">
        <div class="cart-panel__header">
          <h3></h3>
          <button class="close-btn" @click="showCart = false">×</button>
        </div>
        <div v-if="cartItems.length === 0" class="empty-state"></div>
        <div v-else class="cart-list">
          <div v-for="item in cartItems" :key="item.id" class="cart-item">
            <img :src="item.productCover || '/default-product.png'" class="cart-item__img" />
            <div class="cart-item__info">
              <div class="cart-item__name">{{ item.productName }}</div>
              <div class="cart-item__price">¥{{ item.productPrice }}</div>
            </div>
            <div class="cart-item__qty">
              <button @click="updateQty(item, item.quantity - 1)">-</button>
              <span>{{ item.quantity }}</span>
              <button @click="updateQty(item, item.quantity + 1)">+</button>
            </div>
            <button class="cart-item__del" @click="removeFromCart(item)"></button>
          </div>
        </div>
        <div class="cart-panel__footer">
          <div class="cart-total">: <span class="total-price">¥{{ cartTotal }}</span></div>
          <button class="checkout-btn" @click="checkout"></button>
        </div>
      </div>
    </div>

    <!--  -->
    <div v-if="selectedProduct" class="product-modal" @click.self="selectedProduct = null">
      <div class="product-panel">
        <button class="close-btn" @click="selectedProduct = null">×</button>
        <img :src="selectedProduct.cover || '/default-product.png'" class="product-panel__img" />
        <h2 class="product-panel__name">{{ selectedProduct.name }}</h2>
        <div class="product-panel__price">¥{{ selectedProduct.price }}</div>
        <p class="product-panel__desc">{{ selectedProduct.description }}</p>
        <button class="add-cart-btn" @click="addToCart(selectedProduct); selectedProduct = null"></button>
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
      categories: [],
      products: [],
      cartItems: [],
      selectedCategory: null,
      keyword: "",
      showCart: false,
      selectedProduct: null,
    };
  },
  computed: {
    cartCount() { return this.cartItems.reduce((sum, i) => sum + i.quantity, 0); },
    cartTotal() { return this.cartItems.reduce((sum, i) => sum + i.productPrice * i.quantity, 0).toFixed(2); },
  },
  created() {
    this.loadCategories();
    this.loadProducts();
    this.loadCart();
  },
  methods: {
    async loadCategories() {
      try {
        const { data } = await request.get("mall/categories");
        if (data.code === 200) this.categories = data.data;
      } catch (e) { console.error(e); }
    },
    async loadProducts() {
      try {
        const params = {};
        if (this.selectedCategory) params.categoryId = this.selectedCategory;
        if (this.keyword) params.keyword = this.keyword;
        const { data } = await request.post("mall/product/query", params);
        if (data.code === 200) this.products = data.data;
      } catch (e) { console.error(e); }
    },
    async loadCart() {
      try {
        const { data } = await request.get("mall/cart/list");
        if (data.code === 200) this.cartItems = data.data;
      } catch (e) { console.error(e); }
    },
    viewProduct(product) { this.selectedProduct = product; },
    async addToCart(product) {
      try {
        await request.post("mall/cart/add", null, { params: { productId: product.id, quantity: 1 } });
        this.loadCart();
        this.$message.success("");
      } catch (e) { this.$message.error(""); }
    },
    async updateQty(item, qty) {
      if (qty <= 0) { this.removeFromCart(item); return; }
      try {
        await request.put("mall/cart/update", null, { params: { id: item.id, quantity: qty } });
        this.loadCart();
      } catch (e) { console.error(e); }
    },
    async removeFromCart(item) {
      try {
        await request.delete(`mall/cart/${item.id}`);
        this.loadCart();
      } catch (e) { console.error(e); }
    },
    async checkout() {
      if (this.cartItems.length === 0) { this.$message.warning(""); return; }
      try {
        const { data } = await request.post("mall/order/create", null, { params: { addressId: 1 } });
        if (data.code === 200) {
          await request.post(`mall/order/pay/${data.data.id}`);
          this.$swal.fire({ title: "", icon: "success", timer: 1500, showConfirmButton: false });
          this.showCart = false;
          this.loadCart();
        }
      } catch (e) { this.$message.error(""); }
    },
  },
};
</script>

<style scoped>
.mall-container { max-width: 1200px; margin: 0 auto; padding: 24px; }
.mall-header { text-align: center; margin-bottom: 24px; }
.page-title { font-size: 28px; font-weight: 700; color: #1a1a1a; margin: 0 0 8px; }
.page-desc { font-size: 15px; color: #999; margin: 0; }

.category-bar { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.category-item { padding: 8px 20px; border-radius: 20px; background: #f5f5f5; cursor: pointer; font-size: 14px; transition: all 0.2s; }
.category-item:hover, .category-item.active { background: #ff2442; color: #fff; }

.search-bar { display: flex; gap: 8px; margin-bottom: 24px; }
.search-input { flex: 1; height: 40px; padding: 0 16px; border: 2px solid #f0f0f0; border-radius: 10px; font-size: 14px; }
.search-input:focus { outline: none; border-color: #ff2442; }
.search-btn { padding: 0 24px; background: #ff2442; color: #fff; border: none; border-radius: 10px; cursor: pointer; }

.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.product-card { background: #fff; border-radius: 12px; overflow: hidden; cursor: pointer; transition: all 0.25s; border: 1px solid #f0f0f0; }
.product-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.08); }
.product-card__img-wrap { position: relative; height: 180px; overflow: hidden; }
.product-card__img { width: 100%; height: 100%; object-fit: cover; }
.product-card__tag { position: absolute; top: 8px; left: 8px; padding: 2px 8px; border-radius: 10px; font-size: 11px; color: #fff; }
.tag--hot { background: #ff2442; }
.tag--new { background: #07c160; }
.product-card__body { padding: 12px; }
.product-card__name { font-size: 14px; font-weight: 500; color: #1a1a1a; margin: 0 0 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-card__price { margin-bottom: 8px; }
.price-current { font-size: 18px; font-weight: 700; color: #ff2442; }
.price-original { font-size: 13px; color: #999; text-decoration: line-through; margin-left: 8px; }
.product-card__meta { display: flex; gap: 12px; font-size: 12px; color: #999; margin-bottom: 12px; }
.product-card__btn { width: 100%; padding: 8px; background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; border: none; border-radius: 8px; font-size: 13px; cursor: pointer; }

.cart-float { position: fixed; bottom: 30px; right: 30px; width: 56px; height: 56px; background: #ff2442; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 4px 16px rgba(255,36,66,0.4); z-index: 100; }
.cart-icon { font-size: 24px; }
.cart-badge { position: absolute; top: -4px; right: -4px; width: 20px; height: 20px; background: #07c160; color: #fff; border-radius: 50%; font-size: 11px; display: flex; align-items: center; justify-content: center; }

.cart-modal, .product-modal { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 200; display: flex; align-items: center; justify-content: center; }
.cart-panel { background: #fff; border-radius: 16px; width: 400px; max-height: 80vh; display: flex; flex-direction: column; }
.cart-panel__header { display: flex; justify-content: space-between; align-items: center; padding: 16px; border-bottom: 1px solid #f0f0f0; }
.cart-panel__header h3 { margin: 0; font-size: 18px; }
.close-btn { background: none; border: none; font-size: 24px; cursor: pointer; color: #999; }
.cart-list { flex: 1; overflow-y: auto; padding: 16px; }
.cart-item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.cart-item__img { width: 50px; height: 50px; border-radius: 8px; object-fit: cover; }
.cart-item__info { flex: 1; }
.cart-item__name { font-size: 14px; font-weight: 500; }
.cart-item__price { font-size: 14px; color: #ff2442; font-weight: 600; }
.cart-item__qty { display: flex; align-items: center; gap: 8px; }
.cart-item__qty button { width: 24px; height: 24px; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
.cart-item__del { background: none; border: none; color: #999; cursor: pointer; font-size: 12px; }
.cart-panel__footer { padding: 16px; border-top: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
.cart-total { font-size: 14px; color: #666; }
.total-price { font-size: 20px; font-weight: 700; color: #ff2442; }
.checkout-btn { padding: 10px 32px; background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; border: none; border-radius: 10px; font-weight: 600; cursor: pointer; }

.product-panel { background: #fff; border-radius: 16px; padding: 24px; max-width: 500px; width: 90%; position: relative; }
.product-panel__img { width: 100%; height: 300px; object-fit: cover; border-radius: 12px; margin-bottom: 16px; }
.product-panel__name { font-size: 20px; font-weight: 600; margin: 0 0 8px; }
.product-panel__price { font-size: 24px; font-weight: 700; color: #ff2442; margin: 0 0 12px; }
.product-panel__desc { font-size: 14px; color: #666; margin: 0 0 20px; line-height: 1.6; }
.add-cart-btn { width: 100%; padding: 12px; background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; border: none; border-radius: 10px; font-size: 16px; font-weight: 600; cursor: pointer; }
.empty-state { text-align: center; padding: 40px; color: #999; }
</style>
