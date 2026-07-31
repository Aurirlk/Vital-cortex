<template>
  <div class="rag-monitor-container">
    <div class="page-header">
      <h2>RAG 检索质量监控</h2>
      <p>监控知识库检索质量，量化评估上下文精确度和回答忠实度</p>
    </div>

    <!-- 评测指标卡片 -->
    <div class="metrics-grid">
      <div class="metric-card">
        <div class="metric-card__header">
          <span class="metric-icon" style="background: rgba(14, 165, 165, 0.1); color: #0EA5A5;">A</span>
          <span class="metric-label">上下文精确度</span>
        </div>
        <div class="metric-value">{{ metrics.contextPrecision != null ? metrics.contextPrecision + '%' : '—' }}</div>
        <div class="metric-desc">检索结果中相关文档的比例</div>
      </div>
      <div class="metric-card">
        <div class="metric-card__header">
          <span class="metric-icon" style="background: rgba(16, 185, 129, 0.1); color: #10b981;">F</span>
          <span class="metric-label">回答忠实度</span>
        </div>
        <div class="metric-value">{{ metrics.faithfulness != null ? metrics.faithfulness + '%' : '—' }}</div>
        <div class="metric-desc">回答基于检索上下文的比例</div>
      </div>
      <div class="metric-card">
        <div class="metric-card__header">
          <span class="metric-icon" style="background: rgba(99, 44, 229, 0.1); color: #632ce5;">R</span>
          <span class="metric-label">答案相关性</span>
        </div>
        <div class="metric-value">{{ metrics.answerRelevance != null ? metrics.answerRelevance + '%' : '—' }}</div>
        <div class="metric-desc">回答与问题的相关程度</div>
      </div>
      <div class="metric-card">
        <div class="metric-card__header">
          <span class="metric-icon" style="background: rgba(245, 158, 11, 0.1); color: #f59e0b;">Q</span>
          <span class="metric-label">评测总数</span>
        </div>
        <div class="metric-value">{{ metrics.totalEvaluations || 0 }}</div>
        <div class="metric-desc">累计评测次数</div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <button class="btn btn--primary" @click="runEvaluation" :loading="evaluating">
        {{ evaluating ? '评测中...' : '手动触发评测' }}
      </button>
      <button class="btn btn--outline" @click="loadHistory">刷新记录</button>
    </div>

    <!-- 评测趋势图 -->
    <div class="chart-card">
      <div class="chart-header">
        <h3>评测指标趋势</h3>
        <span class="chart-subtitle">最近30天</span>
      </div>
      <div class="chart-body">
        <div ref="trendChart" class="chart-container"></div>
      </div>
    </div>

    <!-- 最近评测记录 -->
    <div class="section">
      <h3 class="section-title">最近评测记录</h3>
      <div class="record-list">
        <div v-for="record in evaluationRecords" :key="record.id" class="record-item">
          <div class="record-item__info">
            <div class="record-item__time">{{ record.createTime }}</div>
            <div class="record-item__metrics">
              <span class="metric-tag">精确度: {{ record.contextPrecision }}%</span>
              <span class="metric-tag">忠实度: {{ record.faithfulness }}%</span>
              <span class="metric-tag">相关性: {{ record.answerRelevance }}%</span>
            </div>
          </div>
          <div class="record-item__status" :class="record.status === 'pass' ? 'status-pass' : 'status-fail'">
            {{ record.status === 'pass' ? '通过' : record.status === 'unavailable' ? '未接入' : '需改进' }}
          </div>
        </div>
        <div v-if="evaluationRecords.length === 0" class="empty-state">
          暂无评测记录
        </div>
      </div>
    </div>

    <!-- 告警阈值设置 -->
    <div class="section">
      <h3 class="section-title">告警阈值设置</h3>
      <div class="threshold-grid">
        <div class="threshold-item">
          <label>上下文精确度阈值</label>
          <input v-model.number="thresholds.contextPrecision" type="number" min="0" max="100" />
          <span>%</span>
        </div>
        <div class="threshold-item">
          <label>回答忠实度阈值</label>
          <input v-model.number="thresholds.faithfulness" type="number" min="0" max="100" />
          <span>%</span>
        </div>
        <div class="threshold-item">
          <label>答案相关性阈值</label>
          <input v-model.number="thresholds.answerRelevance" type="number" min="0" max="100" />
          <span>%</span>
        </div>
        <button class="btn btn--primary" @click="saveThresholds">保存阈值</button>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts';
import request from '@/utils/request.js';

export default {
  name: 'RagMonitor',
  data() {
    return {
      metrics: {},
      evaluationRecords: [],
      evaluating: false,
      thresholds: {
        contextPrecision: 70,
        faithfulness: 80,
        answerRelevance: 70
      },
      trendChart: null
    };
  },
  mounted() {
    this.loadMetrics();
    this.loadHistory();
    this.initTrendChart();
  },
  beforeUnmount() {
    if (this.trendChart) this.trendChart.dispose();
  },
  methods: {
    async loadMetrics() {
      try {
        const { data } = await request.get('rag/metrics');
        if (data.code === 200) this.metrics = data.data;
      } catch (e) { console.error(e); }
    },
    async loadHistory() {
      try {
        const { data } = await request.get('rag/evaluations');
        if (data.code === 200) this.evaluationRecords = data.data;
      } catch (e) { console.error(e); }
    },
    async runEvaluation() {
      this.evaluating = true;
      try {
        const { data } = await request.post('rag/evaluate');
        if (data.code === 200) {
          this.metrics = data.data;
          this.loadHistory();
          if (data.data && data.data.status === 'unavailable') {
            this.$message.warning('真实评测管线未接入，指标暂不可用（已停止返回模拟数据）');
          } else {
            this.$message.success('评测完成');
          }
        }
      } catch (e) {
        this.$message.error('评测失败');
      } finally {
        this.evaluating = false;
      }
    },
    async saveThresholds() {
      try {
        await request.post('rag/thresholds', this.thresholds);
        this.$message.success('阈值已保存');
      } catch (e) {
        this.$message.error('保存失败');
      }
    },
    initTrendChart() {
      this.trendChart = echarts.init(this.$refs.trendChart);
      this.trendChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['上下文精确度', '回答忠实度', '答案相关性'] },
        xAxis: { type: 'category', data: [] },
        yAxis: { type: 'value', max: 100 },
        series: [
          { name: '上下文精确度', type: 'line', smooth: true, data: [] },
          { name: '回答忠实度', type: 'line', smooth: true, data: [] },
          { name: '答案相关性', type: 'line', smooth: true, data: [] }
        ]
      });
    }
  }
};
</script>

<style scoped>
.rag-monitor-container { max-width: 1200px; margin: 0 auto; padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: #1a1a1a; margin: 0 0 8px; }
.page-header p { font-size: 14px; color: #666; margin: 0; }

.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.metric-card { background: #fff; border-radius: 12px; padding: 20px; border: 1px solid #f0f0f0; }
.metric-card__header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.metric-icon { width: 36px; height: 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 16px; }
.metric-label { font-size: 14px; color: #666; }
.metric-value { font-size: 28px; font-weight: 700; color: #1a1a1a; margin-bottom: 4px; }
.metric-desc { font-size: 12px; color: #999; }

.action-bar { display: flex; gap: 12px; margin-bottom: 24px; }
.btn { padding: 10px 20px; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; border: none; }
.btn--primary { background: linear-gradient(135deg, #0050cb, #0066ff); color: #fff; }
.btn--outline { background: transparent; border: 2px solid #0050cb; color: #0050cb; }

.chart-card { background: #fff; border-radius: 12px; border: 1px solid #f0f0f0; margin-bottom: 24px; }
.chart-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #f0f0f0; }
.chart-header h3 { margin: 0; font-size: 16px; font-weight: 600; }
.chart-subtitle { font-size: 13px; color: #999; }
.chart-body { padding: 20px; }
.chart-container { width: 100%; height: 300px; }

.section { margin-bottom: 24px; }
.section-title { font-size: 18px; font-weight: 600; color: #1a1a1a; margin: 0 0 16px; }

.record-list { display: flex; flex-direction: column; gap: 12px; }
.record-item { background: #fff; border-radius: 10px; padding: 16px; display: flex; justify-content: space-between; align-items: center; border: 1px solid #f0f0f0; }
.record-item__time { font-size: 13px; color: #999; margin-bottom: 4px; }
.record-item__metrics { display: flex; gap: 8px; }
.metric-tag { font-size: 12px; padding: 2px 8px; border-radius: 10px; background: rgba(0, 80, 203, 0.06); color: #0050cb; }
.record-item__status { font-size: 12px; padding: 4px 12px; border-radius: 10px; }
.status-pass { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.status-fail { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.threshold-grid { display: flex; gap: 16px; align-items: center; background: #fff; padding: 20px; border-radius: 12px; border: 1px solid #f0f0f0; }
.threshold-item { display: flex; align-items: center; gap: 8px; }
.threshold-item label { font-size: 14px; color: #666; }
.threshold-item input { width: 60px; height: 36px; border: 2px solid #f0f0f0; border-radius: 8px; text-align: center; font-size: 14px; }
.threshold-item input:focus { outline: none; border-color: #0050cb; }

.empty-state { text-align: center; padding: 40px; color: #999; }
</style>
