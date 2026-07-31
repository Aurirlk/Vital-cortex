<template>
  <div class="report-container">
    <div class="page-header">
      <h2>健康报告</h2>
      <p>查看和管理您的健康数据报告</p>
    </div>

    <div class="report-grid">
      <div class="report-card card-blue">
        <div class="card-stripe"></div>
        <div class="card-icon" style="background: rgba(0, 80, 203, 0.08); color: #0050cb;">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/></svg>
        </div>
        <div class="card-body">
          <h3>个人健康报告</h3>
          <p>包含健康指标数据、趋势图和 AI 建议</p>
          <div class="card-foot">
            <span class="tag tag-blue">PDF</span>
            <button class="card-btn btn-blue" @click="downloadReport">生成报告</button>
          </div>
        </div>
        <div class="card-glow"></div>
      </div>

      <div class="report-card card-green">
        <div class="card-stripe"></div>
        <div class="card-icon" style="background: rgba(16, 185, 129, 0.08); color: #10b981;">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
        </div>
        <div class="card-body">
          <h3>健康数据趋势</h3>
          <p>查看血压、血糖、体重等指标的历史变化</p>
          <div class="card-foot">
            <span class="tag tag-green">实时</span>
            <button class="card-btn btn-green" @click="viewHealthData">查看趋势</button>
          </div>
        </div>
        <div class="card-glow"></div>
      </div>

      <div class="report-card card-orange">
        <div class="card-stripe"></div>
        <div class="card-icon" style="background: rgba(245, 158, 11, 0.08); color: #f59e0b;">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
        </div>
        <div class="card-body">
          <h3>上传体检报告</h3>
          <p>上传 PDF 体检报告，AI 自动解析数据</p>
          <div class="card-foot">
            <span class="tag tag-orange">PDF</span>
            <button class="card-btn btn-orange" @click="uploadPdf">上传报告</button>
          </div>
        </div>
        <div class="card-glow"></div>
      </div>
    </div>

    <div class="particles-bg"></div>
  </div>
</template>

<script>
import request from '@/utils/request.js';

export default {
  name: 'UserReport',
  data() {
    return {};
  },
  mounted() {
    const bg = this.$el.querySelector('.particles-bg');
    if (bg) {
      for (let i = 0; i < 15; i++) {
        const p = document.createElement('div');
        p.className = 'particle';
        p.style.left = Math.random() * 100 + '%';
        p.style.top = Math.random() * 100 + '%';
        p.style.animationDelay = Math.random() * 5 + 's';
        p.style.animationDuration = (3 + Math.random() * 5) + 's';
        bg.appendChild(p);
      }
    }
  },
  methods: {
    downloadReport() {
      const apiBase = process.env.VUE_APP_API_BASE || '/api/personal-health/v1.0';
      window.open(apiBase + '/report/health-pdf', '_blank');
      this.$message.success('报告生成中...');
    },
    viewHealthData() {
      this.$router.push('/user/user-health-model');
    },
    uploadPdf() {
      this.$message.info('PDF 上传功能开发中');
    }
  }
};
</script>

<style scoped>
.report-container { max-width: 800px; margin: 0 auto; padding: 24px; position: relative; }
.page-header { text-align: center; margin-bottom: 32px; }
.page-header h2 { font-size: 28px; font-weight: 700; color: #191c1e; margin: 0 0 8px; }
.page-header p { font-size: 15px; color: #647084; margin: 0; }

.report-grid { display: flex; flex-direction: column; gap: 20px; position: relative; z-index: 2; }
.report-card { background: #fff; border-radius: 16px; padding: 28px; display: flex; align-items: center; gap: 24px; cursor: pointer; border: 1px solid #e6ebf2; box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05); transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); position: relative; overflow: hidden; }
.report-card:hover { transform: translateY(-4px); box-shadow: 0 16px 48px rgba(15, 23, 42, 0.1); }
.card-stripe { position: absolute; top: 0; left: 0; right: 0; height: 4px; }
.card-blue .card-stripe { background: linear-gradient(90deg, #0050cb, #0066ff, #632ce5); }
.card-green .card-stripe { background: linear-gradient(90deg, #10b981, #34d399, #6ee7b7); }
.card-orange .card-stripe { background: linear-gradient(90deg, #f59e0b, #fbbf24, #fcd34d); }

.card-icon { width: 60px; height: 60px; border-radius: 16px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: transform 0.3s; }
.report-card:hover .card-icon { transform: scale(1.05); }

.card-body { flex: 1; }
.card-body h3 { font-size: 18px; font-weight: 700; color: #191c1e; margin: 0 0 8px; }
.card-body p { font-size: 14px; color: #647084; margin: 0 0 16px; }

.card-foot { display: flex; align-items: center; gap: 12px; justify-content: space-between; }
.tag { font-size: 12px; font-weight: 600; padding: 4px 12px; border-radius: 20px; }
.tag-blue { background: rgba(0, 80, 203, 0.08); color: #0050cb; }
.tag-green { background: rgba(16, 185, 129, 0.08); color: #10b981; }
.tag-orange { background: rgba(245, 158, 11, 0.08); color: #f59e0b; }

.card-btn { padding: 8px 20px; border-radius: 8px; font-size: 13px; font-weight: 600; color: #fff; border: none; cursor: pointer; transition: all 0.2s; }
.card-btn:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0, 80, 203, 0.3); }
.btn-blue { background: linear-gradient(135deg, #0050cb, #0066ff); }
.btn-green { background: linear-gradient(135deg, #10b981, #34d399); }
.btn-orange { background: linear-gradient(135deg, #f59e0b, #fbbf24); }

.card-glow { position: absolute; right: -60px; top: -60px; width: 180px; height: 180px; border-radius: 50%; opacity: 0.4; }
.card-blue .card-glow { background: radial-gradient(circle, rgba(0, 80, 203, 0.06) 0%, transparent 70%); }
.card-green .card-glow { background: radial-gradient(circle, rgba(16, 185, 129, 0.06) 0%, transparent 70%); }
.card-orange .card-glow { background: radial-gradient(circle, rgba(245, 158, 11, 0.06) 0%, transparent 70%); }

.particles-bg { position: absolute; inset: 0; overflow: hidden; pointer-events: none; z-index: 1; }
.particle { position: absolute; width: 4px; height: 4px; border-radius: 50%; background: rgba(0, 80, 203, 0.08); animation: floatUp linear infinite; }
@keyframes floatUp {
  0% { transform: translateY(0) scale(1); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(-100px) scale(0); opacity: 0; }
}
</style>
