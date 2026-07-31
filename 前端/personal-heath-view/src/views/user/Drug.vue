<template>
  <div class="drug-container">
    <!--  -->
    <div class="drug-header">
      <div class="drug-header-left">
        <h2 class="page-title"></h2>
        <span class="page-subtitle"></span>
      </div>
      <div class="drug-header-right">
        <div class="search-box">
          <input
            class="search-input"
            placeholder="..."
            @keyup.enter="searchDrugs"
            v-model="searchKeyword"
          />
          <span class="search-btn" @click="searchDrugs"></span>
        </div>
        <el-select v-model="selectedCategory" placeholder="" clearable @change="onCategoryChange" style="width: 140px; margin-left: 12px">
          <el-option label="" value="" />
          <el-option label="" value="" />
          <el-option label="" value="" />
          <el-option label="" value="" />
          <el-option label="" value="" />
          <el-option label="" value="" />
          <el-option label="" value="" />
          <el-option label="" value="" />
        </el-select>
        <el-button type="primary" @click="showMySubscriptions" style="margin-left: 12px">
          <el-icon><Collection /></el-icon>
          
        </el-button>
      </div>
    </div>

    <!--  -->
    <div class="drug-grid" v-loading="loading">
      <el-empty v-if="drugList.length === 0" description="" />
      <div
        v-for="drug in drugList"
        :key="drug.id"
        class="drug-card"
        @click="showDrugDetail(drug)"
      >
        <div class="drug-card-img">
          <img v-if="drug.cover" :src="drug.cover" :alt="drug.name" />
          <div v-else class="drug-card-placeholder">
            <el-icon :size="40"><FirstAidKit /></el-icon>
          </div>
          <span v-if="drug.isOtc" class="otc-badge">OTC</span>
          <span v-else class="rx-badge"></span>
        </div>
        <div class="drug-card-body">
          <h3 class="drug-name">{{ drug.name }}</h3>
          <p class="drug-category">{{ drug.category }}</p>
          <p class="drug-spec">{{ drug.specification }}</p>
          <div class="drug-footer">
            <span class="drug-price">¥{{ drug.price }}</span>
            <span class="drug-unit">/{{ drug.unit }}</span>
            <el-button
              v-if="!drug.subscribed"
              type="primary"
              size="small"
              @click.stop="subscribeDrug(drug)"
              class="subscribe-btn"
            >
              
            </el-button>
            <el-button
              v-else
              type="success"
              size="small"
              @click.stop="unsubscribeDrug(drug)"
              class="subscribe-btn"
            >
              
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!--  -->
    <div class="pagination-container" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadDrugs"
      />
    </div>

    <!--  -->
    <el-dialog
      v-model="detailVisible"
      :title="currentDrug.name"
      width="600px"
      class="drug-detail-dialog"
    >
      <div v-if="currentDrug" class="drug-detail">
        <div class="drug-detail-header">
          <div class="drug-detail-img">
            <img v-if="currentDrug.cover" :src="currentDrug.cover" :alt="currentDrug.name" />
            <div v-else class="drug-detail-placeholder">
              <el-icon :size="60"><FirstAidKit /></el-icon>
            </div>
          </div>
          <div class="drug-detail-info">
            <h2>{{ currentDrug.name }}</h2>
            <p v-if="currentDrug.genericName" class="generic-name">{{ currentDrug.genericName }}</p>
            <p class="category">{{ currentDrug.category }}</p>
            <p class="manufacturer" v-if="currentDrug.manufacturer">{{ currentDrug.manufacturer }}</p>
            <p class="specification" v-if="currentDrug.specification">{{ currentDrug.specification }}</p>
            <div class="price-row">
              <span class="price">¥{{ currentDrug.price }}</span>
              <span class="unit">/{{ currentDrug.unit }}</span>
            </div>
            <div class="badge-row">
              <el-tag v-if="currentDrug.isOtc" type="success">OTC </el-tag>
              <el-tag v-else type="warning"></el-tag>
              <el-tag v-if="currentDrug.stock > 0" type="info" style="margin-left: 8px">: {{ currentDrug.stock }}{{ currentDrug.unit }}</el-tag>
              <el-tag v-else type="danger" style="margin-left: 8px"></el-tag>
            </div>
          </div>
        </div>
        <div class="drug-detail-desc">
          <h4></h4>
          <p>{{ currentDrug.description }}</p>
        </div>
        <div class="drug-detail-actions">
          <el-button
            v-if="!currentDrug.subscribed"
            type="primary"
            size="large"
            @click="subscribeDrug(currentDrug)"
          >
            <el-icon><Collection /></el-icon>
            
          </el-button>
          <el-button
            v-else
            type="success"
            size="large"
            @click="unsubscribeDrug(currentDrug)"
          >
            <el-icon><Select /></el-icon>
            
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!--  -->
    <el-dialog
      v-model="subscriptionVisible"
      title=""
      width="700px"
    >
      <el-table :data="mySubscriptions" stripe v-loading="subscriptionLoading">
        <el-table-column prop="name" label="" width="150" />
        <el-table-column prop="category" label="" width="100" />
        <el-table-column prop="specification" label="" width="150" />
        <el-table-column label="" width="100">
          <template #default="{ row }">
            <span style="color: #e74c3c; font-weight: bold">¥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column label="" width="120">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="unsubscribeDrug(row)"></el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="mySubscriptions.length === 0 && !subscriptionLoading" description="" />
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: "DrugPage",
  data() {
    return {
      drugList: [],
      loading: false,
      searchKeyword: "",
      selectedCategory: "",
      currentPage: 1,
      pageSize: 12,
      total: 0,
      detailVisible: false,
      currentDrug: {},
      subscriptionVisible: false,
      mySubscriptions: [],
      subscriptionLoading: false,
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
          status: true,
        };
        if (this.selectedCategory) {
          params.category = this.selectedCategory;
        }
        if (this.searchKeyword) {
          params.name = this.searchKeyword;
        }
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
    searchDrugs() {
      this.currentPage = 1;
      this.loadDrugs();
    },
    onCategoryChange() {
      this.currentPage = 1;
      this.loadDrugs();
    },
    showDrugDetail(drug) {
      this.currentDrug = { ...drug };
      this.detailVisible = true;
    },
    async subscribeDrug(drug) {
      try {
        const res = await this.$axios.post(`/drug/subscribe/${drug.id}?quantity=1`);
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "",
            text: ` ${drug.name}`,
            timer: 1500,
            showConfirmButton: false,
          });
          drug.subscribed = true;
          if (this.currentDrug.id === drug.id) {
            this.currentDrug.subscribed = true;
          }
        }
      } catch (e) {
        this.$swal.fire({
          icon: "error",
          title: "",
          text: e.response?.data?.message || "",
        });
      }
    },
    async unsubscribeDrug(drug) {
      try {
        const res = await this.$axios.post(`/drug/unsubscribe/${drug.id}`);
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "",
            timer: 1500,
            showConfirmButton: false,
          });
          drug.subscribed = false;
          if (this.currentDrug.id === drug.id) {
            this.currentDrug.subscribed = false;
          }
          this.mySubscriptions = this.mySubscriptions.filter(d => d.id !== drug.id);
        }
      } catch (e) {
        this.$swal.fire({
          icon: "error",
          title: "",
          text: e.response?.data?.message || "",
        });
      }
    },
    async showMySubscriptions() {
      this.subscriptionVisible = true;
      this.subscriptionLoading = true;
      try {
        const res = await this.$axios.get("/drug/my-subscriptions");
        if (res.data.code === 200) {
          this.mySubscriptions = res.data.data || [];
        }
      } catch (e) {
        console.error("", e);
      } finally {
        this.subscriptionLoading = false;
      }
    },
  },
};
</script>

<style scoped>
.drug-container {
  padding: 20px 30px;
  min-height: calc(100vh - 80px);
  background: #f5f7fa;
}

.drug-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 20px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.drug-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.page-subtitle {
  font-size: 14px;
  color: #8c8c8c;
}

.drug-header-right {
  display: flex;
  align-items: center;
}

.search-box {
  display: flex;
  align-items: center;
  background: #f0f2f5;
  border-radius: 8px;
  overflow: hidden;
}

.search-input {
  border: none;
  outline: none;
  padding: 8px 14px;
  font-size: 14px;
  background: transparent;
  width: 200px;
}

.search-btn {
  padding: 8px 16px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.search-btn:hover {
  opacity: 0.9;
}

.drug-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.drug-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.drug-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.drug-card-img {
  height: 160px;
  background: linear-gradient(135deg, #f0f4ff, #e8ecf8);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.drug-card-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.drug-card-placeholder {
  color: #667eea;
}

.otc-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: #52c41a;
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.rx-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: #faad14;
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.drug-card-body {
  padding: 16px;
}

.drug-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.drug-category {
  font-size: 12px;
  color: #667eea;
  margin: 0 0 4px;
  background: #f0f4ff;
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
}

.drug-spec {
  font-size: 13px;
  color: #8c8c8c;
  margin: 0 0 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.drug-footer {
  display: flex;
  align-items: center;
}

.drug-price {
  font-size: 20px;
  font-weight: 700;
  color: #e74c3c;
}

.drug-unit {
  font-size: 13px;
  color: #8c8c8c;
  margin-left: 2px;
}

.subscribe-btn {
  margin-left: auto;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 30px;
  padding: 20px 0;
}

/*  */
.drug-detail-header {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
}

.drug-detail-img {
  width: 180px;
  height: 180px;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(135deg, #f0f4ff, #e8ecf8);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.drug-detail-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.drug-detail-placeholder {
  color: #667eea;
}

.drug-detail-info h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px;
}

.drug-detail-info p {
  font-size: 14px;
  color: #595959;
  margin: 0 0 6px;
}

.drug-detail-info .price-row {
  margin: 12px 0;
}

.drug-detail-info .price {
  font-size: 28px;
  font-weight: 700;
  color: #e74c3c;
}

.drug-detail-info .unit {
  font-size: 14px;
  color: #8c8c8c;
}

.badge-row {
  display: flex;
  align-items: center;
  margin-top: 8px;
}

.drug-detail-desc {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.drug-detail-desc h4 {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 10px;
}

.drug-detail-desc p {
  font-size: 14px;
  color: #595959;
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
}

.drug-detail-actions {
  display: flex;
  justify-content: center;
}
</style>
