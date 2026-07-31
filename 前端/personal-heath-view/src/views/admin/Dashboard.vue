<template>
  <div class="dashboard-container">
    <!-- 欢迎横幅（参考 tianlang） -->
    <div class="welcome-banner">
      <div class="welcome-text">
        <h2>{{ greeting }}，管理员</h2>
        <p>管理系统运行概览，及时处理待办事项</p>
      </div>
      <div class="welcome-date">
        <div class="date-day">{{ dayStr }}</div>
        <div class="date-full">{{ fullDateStr }}</div>
      </div>
    </div>

    <!-- 总览统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card" v-for="stat in overviewStats" :key="stat.label">
        <div class="stat-card__icon" :style="{ background: stat.bg }">
          <span class="stat-icon">{{ stat.icon }}</span>
        </div>
        <div class="stat-card__info">
          <div class="stat-label">{{ stat.label }}</div>
          <div class="stat-value">{{ stat.value }}</div>
        </div>
        <div class="stat-card__decoration"></div>
      </div>
    </div>

    <!-- 快捷操作（参考 tianlang） -->
    <div class="section">
      <div class="section-header">
        <h3 class="section-title">快捷操作</h3>
      </div>
      <div class="quick-actions">
        <div class="quick-card" @click="$router.push('/admin/newsManage')">
          <div class="quick-icon" style="background: linear-gradient(135deg, #06b6d4, #0ea5e9)">
            <span>N</span>
          </div>
          <span class="quick-label">资讯管理</span>
        </div>
        <div class="quick-card" @click="$router.push('/admin/appointmentManage')">
          <div class="quick-icon" style="background: linear-gradient(135deg, #10b981, #34d399)">
            <span>A</span>
          </div>
          <span class="quick-label">预约管理</span>
        </div>
        <div class="quick-card" @click="$router.push('/admin/mallManage')">
          <div class="quick-icon" style="background: linear-gradient(135deg, #8b5cf6, #a78bfa)">
            <span>M</span>
          </div>
          <span class="quick-label">商城管理</span>
        </div>
        <div class="quick-card" @click="$router.push('/admin/quizManage')">
          <div class="quick-icon" style="background: linear-gradient(135deg, #f59e0b, #fbbf24)">
            <span>Q</span>
          </div>
          <span class="quick-label">测验管理</span>
        </div>
        <div class="quick-card" @click="$router.push('/admin/userManage')">
          <div class="quick-icon" style="background: linear-gradient(135deg, #3b82f6, #6366f1)">
            <span>U</span>
          </div>
          <span class="quick-label">用户管理</span>
        </div>
      </div>
    </div>

    <!-- 5个模块标签页 -->
    <div class="section">
      <div class="section-header">
        <h3 class="section-title">数据看板</h3>
        <div class="realtime-indicator" @click="togglePolling">
          <span :class="['pulse-dot', { paused: !pollingActive }]"></span>
          <span class="realtime-text">{{ pollingActive ? '实时同步中' : '已暂停' }}</span>
          <span v-if="lastUpdateTime" class="realtime-time">{{ lastUpdateTime }}</span>
        </div>
      </div>

      <div class="module-tabs">
        <button v-for="tab in tabs" :key="tab.key"
                :class="{ active: activeTab === tab.key }"
                @click="activeTab = tab.key">
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- 模块1: AI使用情况 -->
    <div v-if="activeTab === 'ai'" class="module-content">
      <div class="charts-grid">
        <div class="chart-card">
          <div class="chart-header">
            <h3>AI会话趋势</h3>
            <span class="chart-subtitle">最近7天</span>
          </div>
          <div class="chart-body">
            <div ref="aiTrendChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>角色使用分布</h3>
          </div>
          <div class="chart-body">
            <div ref="aiRoleChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card full-width">
          <div class="chart-header">
            <h3>AI使用统计</h3>
          </div>
          <div class="chart-body">
            <div class="stat-list">
              <div class="stat-item">
                <div class="stat-item__label">总会话数</div>
                <div class="stat-item__value">{{ aiStats.conversationCount || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">总消息数</div>
                <div class="stat-item__value">{{ aiStats.messageCount || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">今日新增会话</div>
                <div class="stat-item__value">{{ aiStats.todayConversations || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">今日新增消息</div>
                <div class="stat-item__value">{{ aiStats.todayMessages || 0 }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 模块2: 医生预约情况 -->
    <div v-if="activeTab === 'appointment'" class="module-content">
      <div class="charts-grid">
        <div class="chart-card">
          <div class="chart-header">
            <h3>预约趋势</h3>
            <span class="chart-subtitle">最近7天</span>
          </div>
          <div class="chart-body">
            <div ref="appointmentTrendChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>预约状态分布</h3>
          </div>
          <div class="chart-body">
            <div ref="appointmentStatusChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>科室预约分布</h3>
          </div>
          <div class="chart-body">
            <div ref="appointmentDeptChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>热门医生排行</h3>
          </div>
          <div class="chart-body">
            <div class="rank-list">
              <div v-for="(item, index) in appointmentStats.topDoctors" :key="index" class="rank-item">
                <span class="rank-num" :class="'rank-' + (index + 1)">{{ index + 1 }}</span>
                <span class="rank-name">{{ item.doctor }}</span>
                <span class="rank-value">{{ item.count }}次</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 模块3: 资讯论坛情况 -->
    <div v-if="activeTab === 'news'" class="module-content">
      <div class="charts-grid">
        <div class="chart-card">
          <div class="chart-header">
            <h3>发帖趋势</h3>
            <span class="chart-subtitle">最近7天</span>
          </div>
          <div class="chart-body">
            <div ref="newsTrendChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>分类统计</h3>
          </div>
          <div class="chart-body">
            <div ref="newsTagChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card full-width">
          <div class="chart-header">
            <h3>热门帖子排行</h3>
          </div>
          <div class="chart-body">
            <div class="rank-list">
              <div v-for="(item, index) in newsStats.hotPosts" :key="index" class="rank-item">
                <span class="rank-num" :class="'rank-' + (index + 1)">{{ index + 1 }}</span>
                <span class="rank-name">{{ item.title }}</span>
                <span class="rank-value">{{ item.hotScore }}分</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 模块4: 药品销售情况 -->
    <div v-if="activeTab === 'mall'" class="module-content">
      <div class="charts-grid">
        <div class="chart-card">
          <div class="chart-header">
            <h3>销售趋势</h3>
            <span class="chart-subtitle">最近7天</span>
          </div>
          <div class="chart-body">
            <div ref="mallTrendChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>订单状态分布</h3>
          </div>
          <div class="chart-body">
            <div ref="mallStatusChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>热销商品排行</h3>
          </div>
          <div class="chart-body">
            <div class="rank-list">
              <div v-for="(item, index) in mallStats.topProducts" :key="index" class="rank-item">
                <span class="rank-num" :class="'rank-' + (index + 1)">{{ index + 1 }}</span>
                <span class="rank-name">{{ item.name }}</span>
                <span class="rank-value">{{ item.salesCount }}件</span>
              </div>
            </div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>销售统计</h3>
          </div>
          <div class="chart-body">
            <div class="stat-list">
              <div class="stat-item">
                <div class="stat-item__label">总订单数</div>
                <div class="stat-item__value">{{ mallStats.orderCount || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">今日订单</div>
                <div class="stat-item__value">{{ mallStats.todayOrders || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">总销售额</div>
                <div class="stat-item__value">{{ mallStats.totalSales || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">今日销售额</div>
                <div class="stat-item__value">{{ mallStats.todaySales || 0 }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 模块5: 健康模型与指标监控 -->
    <div v-if="activeTab === 'health'" class="module-content">
      <div class="charts-grid">
        <div class="chart-card">
          <div class="chart-header">
            <h3>记录趋势</h3>
            <span class="chart-subtitle">最近7天</span>
          </div>
          <div class="chart-body">
            <div ref="healthTrendChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>指标记录分布</h3>
          </div>
          <div class="chart-body">
            <div ref="healthIndicatorChart" class="chart-container"></div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>异常指标统计</h3>
          </div>
          <div class="chart-body">
            <div class="rank-list">
              <div v-for="(item, index) in healthStats.abnormalStats" :key="index" class="rank-item">
                <span class="rank-num rank-warning">{{ index + 1 }}</span>
                <span class="rank-name">{{ item.indicator }}</span>
                <span class="rank-value rank-warning">{{ item.count }}次异常</span>
              </div>
            </div>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-header">
            <h3>健康统计</h3>
          </div>
          <div class="chart-body">
            <div class="stat-list">
              <div class="stat-item">
                <div class="stat-item__label">健康模型数</div>
                <div class="stat-item__value">{{ healthStats.modelCount || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">健康记录总数</div>
                <div class="stat-item__value">{{ healthStats.recordCount || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-item__label">今日新增记录</div>
                <div class="stat-item__value">{{ healthStats.todayRecords || 0 }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts';
import request from '@/utils/request.js';

export default {
  name: 'AdminDashboard',
  data() {
    return {
      activeTab: 'ai',
      tabs: [
        { key: 'ai', label: 'AI使用情况' },
        { key: 'appointment', label: '医生预约' },
        { key: 'news', label: '资讯论坛' },
        { key: 'mall', label: '药品销售' },
        { key: 'health', label: '健康指标监控' }
      ],
      overviewStats: [],
      aiStats: {},
      appointmentStats: {},
      newsStats: {},
      mallStats: {},
      healthStats: {},
      charts: {},
      // 实时刷新
      pollingActive: true,
      lastUpdateTime: '',
      pollTimer: null
    };
  },
  computed: {
    greeting() {
      const h = new Date().getHours();
      if (h < 6) return '凌晨好';
      if (h < 12) return '上午好';
      if (h < 14) return '中午好';
      if (h < 18) return '下午好';
      return '晚上好';
    },
    dayStr() {
      return new Date().getDate();
    },
    fullDateStr() {
      const d = new Date();
      const weeks = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
      return `${d.getFullYear()}年${d.getMonth() + 1}月 · ${weeks[d.getDay()]}`;
    }
  },
  watch: {
    activeTab() {
      this.$nextTick(() => this.initCurrentTabCharts());
    }
  },
  mounted() {
    this.loadAllStats();
    this.startPolling();
  },
  beforeUnmount() {
    this.stopPolling();
    Object.values(this.charts).forEach(chart => chart?.dispose());
  },
  methods: {
    startPolling() {
      if (this.pollTimer) clearInterval(this.pollTimer);
      this.pollTimer = setInterval(() => this.loadAllStats(true), 30000);
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },
    togglePolling() {
      this.pollingActive = !this.pollingActive;
      if (this.pollingActive) {
        this.startPolling();
        this.loadAllStats();
      } else {
        this.stopPolling();
      }
    },
    async loadAllStats(silent = false) {
      try {
        const [overview, ai, appointment, news, mall, health] = await Promise.all([
          request.get('dashboard/overview'),
          request.get('dashboard/ai/stats'),
          request.get('dashboard/appointment/stats'),
          request.get('dashboard/news/stats'),
          request.get('dashboard/mall/stats'),
          request.get('dashboard/health/stats')
        ]);
        if (overview.data.code === 200) {
          const d = overview.data.data;
          this.overviewStats = [
            { label: '用户总数', value: d.userCount || 0, icon: 'U', bg: 'rgba(0, 80, 203, 0.1)' },
            { label: '帖子总数', value: d.postCount || 0, icon: 'P', bg: 'rgba(16, 185, 129, 0.1)' },
            { label: '预约总数', value: d.appointmentCount || 0, icon: 'A', bg: 'rgba(245, 158, 11, 0.1)' },
            { label: '订单总数', value: d.orderCount || 0, icon: 'O', bg: 'rgba(99, 44, 229, 0.1)' },
            { label: 'AI会话', value: d.aiConversationCount || 0, icon: 'AI', bg: 'rgba(239, 68, 68, 0.1)' },
            { label: '资讯总数', value: d.newsCount || 0, icon: 'N', bg: 'rgba(59, 130, 246, 0.1)' }
          ];
        }
        if (ai.data.code === 200) this.aiStats = ai.data.data;
        if (appointment.data.code === 200) this.appointmentStats = appointment.data.data;
        if (news.data.code === 200) this.newsStats = news.data.data;
        if (mall.data.code === 200) this.mallStats = mall.data.data;
        if (health.data.code === 200) this.healthStats = health.data.data;
        this.lastUpdateTime = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        if (!silent) {
          this.$nextTick(() => this.initCurrentTabCharts());
        }
      } catch (e) { if (!silent) console.error(e); }
    },
    initCurrentTabCharts() {
      switch (this.activeTab) {
        case 'ai': this.initAiCharts(); break;
        case 'appointment': this.initAppointmentCharts(); break;
        case 'news': this.initNewsCharts(); break;
        case 'mall': this.initMallCharts(); break;
        case 'health': this.initHealthCharts(); break;
      }
    },
    initChart(refName) {
      if (this.charts[refName]) this.charts[refName].dispose();
      const el = this.$refs[refName];
      if (!el) return null;
      this.charts[refName] = echarts.init(el);
      return this.charts[refName];
    },
    initAiCharts() {
      const trend = this.initChart('aiTrendChart');
      if (trend) {
        const data = this.aiStats.trend || [];
        trend.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.map(d => d.date) },
          yAxis: { type: 'value' },
          series: [{ data: data.map(d => d.count), type: 'line', smooth: true, areaStyle: { color: 'rgba(0, 80, 203, 0.1)' }, lineStyle: { color: '#0050cb' }, itemStyle: { color: '#0050cb' } }]
        });
      }
      const role = this.initChart('aiRoleChart');
      if (role) {
        const data = this.aiStats.roleStats || [];
        role.setOption({
          tooltip: { trigger: 'item' },
          series: [{ type: 'pie', radius: '60%', data: data.map(d => ({ value: d.count, name: d.role })) }]
        });
      }
    },
    initAppointmentCharts() {
      const trend = this.initChart('appointmentTrendChart');
      if (trend) {
        const data = this.appointmentStats.trend || [];
        trend.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.map(d => d.date) },
          yAxis: { type: 'value' },
          series: [{ data: data.map(d => d.count), type: 'bar', itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] } }]
        });
      }
      const status = this.initChart('appointmentStatusChart');
      if (status) {
        const data = this.appointmentStats.statusStats || [];
        const statusMap = { 0: '待确认', 1: '已确认', 2: '已完成', 3: '已取消', 4: '已爽约' };
        status.setOption({
          tooltip: { trigger: 'item' },
          series: [{ type: 'pie', radius: '60%', data: data.map(d => ({ value: d.count, name: statusMap[d.status] || d.status })) }]
        });
      }
      const dept = this.initChart('appointmentDeptChart');
      if (dept) {
        const data = this.appointmentStats.departmentStats || [];
        dept.setOption({
          tooltip: { trigger: 'item' },
          series: [{ type: 'pie', radius: ['40%', '70%'], data: data.map(d => ({ value: d.count, name: d.department })) }]
        });
      }
    },
    initNewsCharts() {
      const trend = this.initChart('newsTrendChart');
      if (trend) {
        const data = this.newsStats.trend || [];
        trend.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.map(d => d.date) },
          yAxis: { type: 'value' },
          series: [{ data: data.map(d => d.count), type: 'line', smooth: true, areaStyle: { color: 'rgba(16, 185, 129, 0.1)' }, lineStyle: { color: '#10b981' }, itemStyle: { color: '#10b981' } }]
        });
      }
      const tag = this.initChart('newsTagChart');
      if (tag) {
        const data = this.newsStats.tagStats || [];
        tag.setOption({
          tooltip: { trigger: 'item' },
          series: [{ type: 'pie', radius: '60%', data: data.map(d => ({ value: d.count, name: d.tag })) }]
        });
      }
    },
    initMallCharts() {
      const trend = this.initChart('mallTrendChart');
      if (trend) {
        const data = this.mallStats.trend || [];
        trend.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.map(d => d.date) },
          yAxis: { type: 'value' },
          series: [{ data: data.map(d => d.count), type: 'bar', itemStyle: { color: '#632ce5', borderRadius: [4, 4, 0, 0] } }]
        });
      }
      const status = this.initChart('mallStatusChart');
      if (status) {
        const data = this.mallStats.statusStats || [];
        const statusMap = { 0: '待付款', 1: '已付款', 2: '已发货', 3: '已收货', 4: '已完成', 5: '已取消' };
        status.setOption({
          tooltip: { trigger: 'item' },
          series: [{ type: 'pie', radius: '60%', data: data.map(d => ({ value: d.count, name: statusMap[d.status] || d.status })) }]
        });
      }
    },
    initHealthCharts() {
      const trend = this.initChart('healthTrendChart');
      if (trend) {
        const data = this.healthStats.trend || [];
        trend.setOption({
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.map(d => d.date) },
          yAxis: { type: 'value' },
          series: [{ data: data.map(d => d.count), type: 'line', smooth: true, areaStyle: { color: 'rgba(245, 158, 11, 0.1)' }, lineStyle: { color: '#f59e0b' }, itemStyle: { color: '#f59e0b' } }]
        });
      }
      const indicator = this.initChart('healthIndicatorChart');
      if (indicator) {
        const data = this.healthStats.indicatorStats || [];
        indicator.setOption({
          tooltip: { trigger: 'item' },
          series: [{ type: 'pie', radius: '60%', data: data.map(d => ({ value: d.count, name: d.indicator })) }]
        });
      }
    }
  }
};
</script>

<style scoped>
.dashboard-container {
  padding: 24px;
  background: #f7f9fc;
  min-height: 100vh;
}

/* 欢迎横幅（参考 tianlang） */
.welcome-banner {
  background: linear-gradient(135deg, #e0f2fe 0%, #f0f9ff 50%, #eff6ff 100%);
  border-radius: 16px;
  padding: 28px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  border: 1px solid rgba(14, 165, 233, 0.1);
  position: relative;
  overflow: hidden;
}

.welcome-banner::before {
  content: '';
  position: absolute;
  top: -40px;
  right: -40px;
  width: 160px;
  height: 160px;
  background: radial-gradient(circle, rgba(14, 165, 233, 0.08) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-text h2 {
  font-size: 22px;
  font-weight: 700;
  color: #191c1e;
  margin-bottom: 6px;
}

.welcome-text p {
  font-size: 13px;
  color: #647084;
  margin: 0;
}

.welcome-date {
  text-align: center;
}

.date-day {
  font-size: 42px;
  font-weight: 800;
  background: linear-gradient(135deg, #0050cb, #632ce5);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1;
}

.date-full {
  font-size: 12px;
  color: #647084;
  margin-top: 4px;
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  overflow: hidden;
  border: 1px solid #e6ebf2;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 800;
  color: #0050cb;
}

.stat-card__info {
  flex: 1;
}

.stat-label {
  font-size: 12px;
  color: #647084;
  font-weight: 600;
}

.stat-value {
  font-size: 24px;
  font-weight: 800;
  color: #191c1e;
  line-height: 1.2;
  margin-top: 4px;
}

.stat-card__decoration {
  position: absolute;
  right: -16px;
  top: -16px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(0, 80, 203, 0.04);
}

/* 区块 */
.section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #191c1e;
  margin: 0;
}

/* 实时刷新指示器（参考 tianlang） */
.realtime-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 12px;
  color: #647084;
  padding: 4px 10px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid #e6ebf2;
  transition: all 0.2s ease;
}

.realtime-indicator:hover {
  background: #f8fafc;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  animation: pulse-ring 1.5s ease-out infinite;
}

.pulse-dot.paused {
  background: #9ca3af;
  animation: none;
}

@keyframes pulse-ring {
  0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.5); }
  100% { box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
}

.realtime-text {
  font-weight: 500;
}

.realtime-time {
  color: #647084;
  font-size: 11px;
}

/* 快捷操作（参考 tianlang） */
.quick-actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px;
  background: #fff;
  border-radius: 14px;
  border: 1px solid #e6ebf2;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  min-width: 100px;
}

.quick-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
  border-color: #0050cb;
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
  font-weight: 700;
}

.quick-label {
  font-size: 13px;
  font-weight: 500;
  color: #647084;
}

/* 模块标签页 */
.module-tabs {
  display: flex;
  gap: 4px;
  background: #fff;
  padding: 4px;
  border-radius: 10px;
  border: 1px solid #e6ebf2;
}

.module-tabs button {
  padding: 10px 20px;
  border-radius: 8px;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  color: #647084;
  cursor: pointer;
  transition: all 0.2s;
}

.module-tabs button.active {
  background: linear-gradient(135deg, #0050cb, #0066ff);
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 80, 203, 0.3);
}

/* 图表区域 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.chart-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e6ebf2;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e6ebf2;
}

.chart-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #191c1e;
}

.chart-subtitle {
  font-size: 12px;
  color: #647084;
}

.chart-body {
  padding: 16px;
}

.chart-container {
  width: 100%;
  height: 280px;
}

/* 统计列表 */
.stat-list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-item {
  text-align: center;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.stat-item__label {
  font-size: 12px;
  color: #647084;
  margin-bottom: 8px;
}

.stat-item__value {
  font-size: 24px;
  font-weight: 700;
  color: #191c1e;
}

/* 排行榜 */
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-num {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: #f0f0f0;
  color: #647084;
}

.rank-1 { background: #ff2442; color: #fff; }
.rank-2 { background: #ff9500; color: #fff; }
.rank-3 { background: #ffb400; color: #fff; }

.rank-name {
  flex: 1;
  font-size: 14px;
  color: #191c1e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-value {
  font-size: 14px;
  font-weight: 600;
  color: #0050cb;
}

.rank-warning {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
</style>
