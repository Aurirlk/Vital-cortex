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
          <span
            class="metric-icon"
            style="background: rgba(14, 165, 165, 0.1); color: #0ea5a5"
            >A</span
          >
          <span class="metric-label">上下文精确度</span>
        </div>
        <div class="metric-value">
          {{
            metrics.contextPrecision != null
              ? metrics.contextPrecision + "%"
              : "—"
          }}
        </div>
        <div class="metric-desc">检索结果中相关文档的比例</div>
      </div>
      <div class="metric-card">
        <div class="metric-card__header">
          <span
            class="metric-icon"
            style="background: rgba(16, 185, 129, 0.1); color: #10b981"
            >F</span
          >
          <span class="metric-label">回答忠实度</span>
        </div>
        <div class="metric-value">
          {{ metrics.faithfulness != null ? metrics.faithfulness + "%" : "—" }}
        </div>
        <div class="metric-desc">回答基于检索上下文的比例</div>
      </div>
      <div class="metric-card">
        <div class="metric-card__header">
          <span
            class="metric-icon"
            style="background: rgba(99, 44, 229, 0.1); color: #632ce5"
            >R</span
          >
          <span class="metric-label">答案相关性</span>
        </div>
        <div class="metric-value">
          {{
            metrics.answerRelevance != null
              ? metrics.answerRelevance + "%"
              : "—"
          }}
        </div>
        <div class="metric-desc">回答与问题的相关程度</div>
      </div>
      <div class="metric-card">
        <div class="metric-card__header">
          <span
            class="metric-icon"
            style="background: rgba(245, 158, 11, 0.1); color: #f59e0b"
            >Q</span
          >
          <span class="metric-label">评测总数</span>
        </div>
        <div class="metric-value">{{ metrics.totalEvaluations || 0 }}</div>
        <div class="metric-desc">累计评测次数</div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <button
        class="btn btn--primary"
        @click="runEvaluation"
        :loading="evaluating"
      >
        {{ evaluating ? "评测中..." : "手动触发评测" }}
      </button>
      <button class="btn btn--outline" @click="loadHistory">刷新记录</button>
    </div>

    <!-- ==================== 召回率 / 精确率（金标评测） ==================== -->
    <div class="section">
      <h3 class="section-title">召回率 / 精确率（金标评测）</h3>

      <div class="eval-intro">
        上方三个指标评的是<b>「回答有没有覆盖到要点」</b>；这里评的是
        <b>「检索有没有把该给的文档找出来」</b>，用的是标准信息检索指标。
        召回率 = |检索结果 ∩ 金标| / |金标|，<b>必须有人工标注的金标集</b>才能算 ——
        没有金标就只能算「返回了几条」这类与质量无关的假指标。
      </div>

      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-card__header">
            <span class="metric-icon" style="background: rgba(14, 165, 165, 0.1); color: #0ea5a5">R</span>
            <span class="metric-label">召回率</span>
          </div>
          <div class="metric-value">{{ pct(evalReport.recall) }}</div>
          <div class="metric-desc">
            金标用例 {{ evalReport.recallScored || 0 }} 条参与统计
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-card__header">
            <span class="metric-icon" style="background: rgba(16, 185, 129, 0.1); color: #10b981">P</span>
            <span class="metric-label">精确率@K</span>
          </div>
          <div class="metric-value">{{ pct(evalReport.precisionAtK) }}</div>
          <div class="metric-desc">
            有效用例 {{ evalReport.precisionScored || 0 }} 条（含仅关键词用例）
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-card__header">
            <span class="metric-icon" style="background: rgba(99, 44, 229, 0.1); color: #632ce5">M</span>
            <span class="metric-label">MRR</span>
          </div>
          <div class="metric-value">{{ num(evalReport.mrr) }}</div>
          <div class="metric-desc">首个正确结果的平均倒数排名</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__header">
            <span class="metric-icon" style="background: rgba(245, 158, 11, 0.1); color: #f59e0b">N</span>
            <span class="metric-label">nDCG</span>
          </div>
          <div class="metric-value">{{ num(evalReport.ndcg) }}</div>
          <div class="metric-desc">考虑了名次的排序质量</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__header">
            <span class="metric-icon" style="background: rgba(59, 130, 246, 0.1); color: #3b82f6">H</span>
            <span class="metric-label">命中率</span>
          </div>
          <div class="metric-value">{{ pct(evalReport.hitRate) }}</div>
          <div class="metric-desc">至少召回一条金标的用例占比</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__header">
            <span class="metric-icon" style="background: rgba(107, 114, 128, 0.1); color: #6b7280">C</span>
            <span class="metric-label">用例数</span>
          </div>
          <div class="metric-value">{{ evalReport.caseTotal || 0 }}</div>
          <div class="metric-desc">
            金标 {{ evalReport.labeledTotal || 0 }} · 仅关键词 {{ evalReport.keywordTotal || 0 }}
          </div>
        </div>
      </div>

      <div v-if="evalNote" class="eval-note">{{ evalNote }}</div>

      <div class="action-bar">
        <label class="eval-field">
          返回条数 K
          <input v-model.number="evalTopK" type="number" min="1" max="50" />
        </label>
        <label class="eval-field">
          受众
          <select v-model="evalAudience">
            <option value="">全部</option>
            <option value="user">普通用户</option>
            <option value="doctor">医生</option>
          </select>
        </label>
        <button class="btn btn--primary" :disabled="evalRunning" @click="runEval">
          {{ evalRunning ? "评测中..." : "跑一次评测" }}
        </button>
        <button class="btn btn--outline" @click="loadEvalHistory">刷新历史</button>
        <button class="btn btn--outline" @click="openCases">评测集管理</button>
      </div>

      <!-- 逐题明细 -->
      <div v-if="evalCases.length" class="chart-card">
        <div class="chart-header">
          <h3>逐题明细</h3>
          <span class="chart-subtitle">
            本次 runId：{{ evalReport.runId || "-" }} · 平均耗时
            {{ evalReport.avgLatencyMs != null ? evalReport.avgLatencyMs : "—" }} ms
          </span>
        </div>
        <div class="eval-table-wrap">
          <table class="eval-table">
            <thead>
              <tr>
                <th>问题</th>
                <th>粒度</th>
                <th>召回 / 金标</th>
                <th>命中</th>
                <th>首中名次</th>
                <th>召回率</th>
                <th>精确率</th>
                <th>路径</th>
                <th>耗时</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(c, i) in evalCases" :key="c.caseId != null ? c.caseId : i">
                <td class="eval-q" :title="c.question">{{ c.question }}</td>
                <td>{{ c.matchLevel === "doc" ? "文档" : "块" }}</td>
                <td>{{ c.retrieved }} / {{ c.golden }}</td>
                <td>{{ c.hitCount }}</td>
                <td>{{ c.firstHitRank || "—" }}</td>
                <td>{{ pct(c.recall) }}</td>
                <td>{{ pct(c.precision) }}</td>
                <td>
                  <span class="path-tag" :class="'path-' + c.path">{{ c.path }}</span>
                </td>
                <td>{{ c.latencyMs }} ms</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="eval-hint">
          「—」表示该项在本条用例上<b>无定义</b>（该用例只标了关键词，没有金标，
          缺金标就无法计算召回率），而不是 0％。
        </div>
      </div>

      <!-- 评测历史 -->
      <div class="chart-card">
        <div class="chart-header">
          <h3>评测历史</h3>
          <span class="chart-subtitle">按时间倒序</span>
        </div>
        <div class="eval-table-wrap">
          <table class="eval-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>runId</th>
                <th>用例</th>
                <th>召回率</th>
                <th>精确率</th>
                <th>MRR</th>
                <th>命中率</th>
                <th>仅词法</th>
                <th>触发</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in evalHistory" :key="r.runId">
                <td>{{ r.createTime }}</td>
                <td class="eval-mono">{{ r.runId }}</td>
                <td>{{ r.caseTotal }}</td>
                <td>{{ pct(r.recall) }}</td>
                <td>{{ pct(r.precisionAtK) }}</td>
                <td>{{ num(r.mrr) }}</td>
                <td>{{ pct(r.hitRate) }}</td>
                <td>{{ r.sparseOnly }}</td>
                <td>{{ r.triggerBy || "manual" }}</td>
              </tr>
              <tr v-if="evalHistory.length === 0">
                <td colspan="9" class="eval-empty">还没有评测记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
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
        <div
          v-for="record in evaluationRecords"
          :key="record.id"
          class="record-item"
        >
          <div class="record-item__info">
            <div class="record-item__time">{{ record.createTime }}</div>
            <div class="record-item__metrics">
              <span class="metric-tag"
                >精确度: {{ record.contextPrecision }}%</span
              >
              <span class="metric-tag">忠实度: {{ record.faithfulness }}%</span>
              <span class="metric-tag"
                >相关性: {{ record.answerRelevance }}%</span
              >
            </div>
          </div>
          <div
            class="record-item__status"
            :class="record.status === 'pass' ? 'status-pass' : 'status-fail'"
          >
            {{
              record.status === "pass"
                ? "通过"
                : record.status === "unavailable"
                ? "未接入"
                : "需改进"
            }}
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
          <input
            v-model.number="thresholds.contextPrecision"
            type="number"
            min="0"
            max="100"
          />
          <span>%</span>
        </div>
        <div class="threshold-item">
          <label>回答忠实度阈值</label>
          <input
            v-model.number="thresholds.faithfulness"
            type="number"
            min="0"
            max="100"
          />
          <span>%</span>
        </div>
        <div class="threshold-item">
          <label>答案相关性阈值</label>
          <input
            v-model.number="thresholds.answerRelevance"
            type="number"
            min="0"
            max="100"
          />
          <span>%</span>
        </div>
        <button class="btn btn--primary" @click="saveThresholds">
          保存阈值
        </button>
      </div>
    </div>

    <!-- ==================== 评测集管理 ==================== -->
    <el-dialog v-model="casesDialog" title="金标评测集" width="860px" @open="loadCases">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="eval-dialog-tip"
        title="金标与预期关键词至少要有一项"
        description="有金标（expected_refs）才能算召回率；只有关键词的用例只参与精确率。两者都空的用例无法评分，保存时会被拒绝。"
      />

      <div class="eval-dialog-bar">
        <el-input
          v-model="caseQuery.question"
          placeholder="按问题搜索"
          size="small"
          style="width: 220px"
          clearable
          @keyup.enter="loadCases"
        />
        <el-button size="small" @click="loadCases">查询</el-button>
        <el-button size="small" type="primary" @click="newCase">新增用例</el-button>
      </div>

      <el-form :inline="true" label-width="90px" class="eval-case-form">
        <el-form-item label="问题" required>
          <el-input v-model="caseForm.question" style="width: 520px" placeholder="用户会怎么问" />
        </el-form-item>
        <el-form-item label="金标引用">
          <el-input
            v-model="caseForm.expectedRefs"
            style="width: 520px"
            placeholder="留空则不参与召回率。多个 id 用逗号分隔，如 12,15"
          />
        </el-form-item>
        <el-form-item label="预期关键词">
          <el-input
            v-model="caseForm.expectedKeywords"
            style="width: 520px"
            placeholder="逗号分隔，如 用法用量,禁忌。用于计算精确率"
          />
        </el-form-item>
        <el-form-item label="匹配粒度">
          <el-select v-model="caseForm.matchLevel" style="width: 140px">
            <el-option label="块（chunk）" value="chunk" />
            <el-option label="文档（doc）" value="doc" />
          </el-select>
          <span class="eval-form-hint">金标按块标就选块，按整篇标就选文档</span>
        </el-form-item>
        <el-form-item label="知识库">
          <el-input v-model="caseForm.kbIds" style="width: 200px" placeholder="留空表示不限定" />
        </el-form-item>
        <el-form-item label="受众">
          <el-select v-model="caseForm.audience" clearable style="width: 140px">
            <el-option label="全部" value="all" />
            <el-option label="普通用户" value="user" />
            <el-option label="医生" value="doctor" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="caseForm.enabled" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="caseSaving" @click="saveCase">
            {{ caseForm.id ? "保存修改" : "新增" }}
          </el-button>
          <el-button v-if="caseForm.id" @click="newCase">取消编辑</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="caseRows" border size="small" v-loading="caseLoading" max-height="300">
        <el-table-column prop="id" label="ID" width="56" align="center" />
        <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="expectedRefs" label="金标" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.expectedRefs">{{ row.expectedRefs }}</span>
            <span v-else class="eval-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="expectedKeywords" label="关键词" width="140" show-overflow-tooltip />
        <el-table-column label="粒度" width="70" align="center">
          <template #default="{ row }">{{ row.matchLevel === "doc" ? "文档" : "块" }}</template>
        </el-table-column>
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? "是" : "否" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="editCase(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeCase(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import * as echarts from "echarts";
import request from "@/utils/request.js";

const EMPTY_CASE = () => ({
  id: null,
  question: "",
  expectedRefs: "",
  expectedKeywords: "",
  matchLevel: "chunk",
  kbIds: "",
  audience: "",
  enabled: true,
});

export default {
  name: "RagMonitor",
  data() {
    return {
      metrics: {},
      evaluationRecords: [],
      evaluating: false,
      thresholds: {
        contextPrecision: 70,
        faithfulness: 80,
        answerRelevance: 70,
      },
      trendChart: null,

      // 召回率 / 精确率（金标评测）
      evalReport: {},
      evalCases: [],
      evalHistory: [],
      evalRunning: false,
      evalTopK: 5,
      evalAudience: "",
      casesDialog: false,
      caseRows: [],
      caseLoading: false,
      caseSaving: false,
      caseQuery: { question: "" },
      caseForm: EMPTY_CASE(),
    };
  },
  computed: {
    evalNote() {
      const total = this.evalReport.caseTotal;
      if (!total) return "";
      if (this.evalReport.recall == null) {
        return (
          "本次评测集里没有任何带金标（expected_refs）的用例，因此召回率 / MRR / nDCG / 命中率" +
          "都无法计算 —— 这几项只统计金标用例，其召回率无定义时不会按 0 计入平均。" +
          "请到「评测集管理」为用例补上金标。"
        );
      }
      return this.evalReport.note || "";
    },
  },
  mounted() {
    this.loadMetrics();
    this.loadHistory();
    this.initTrendChart();
    this.loadEvalLatest();
    this.loadEvalHistory();
  },
  beforeUnmount() {
    if (this.trendChart) this.trendChart.dispose();
  },
  methods: {
    async loadMetrics() {
      try {
        const { data } = await request.get("rag/metrics");
        if (data.code === 200) this.metrics = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async loadHistory() {
      try {
        const { data } = await request.get("rag/evaluations");
        if (data.code === 200) this.evaluationRecords = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async runEvaluation() {
      this.evaluating = true;
      try {
        const { data } = await request.post("rag/evaluate");
        if (data.code === 200) {
          this.metrics = data.data;
          this.loadHistory();
          if (data.data && data.data.status === "unavailable") {
            this.$message.warning(
              "真实评测管线未接入，指标暂不可用（已停止返回模拟数据）"
            );
          } else {
            this.$message.success("评测完成");
          }
        }
      } catch (e) {
        this.$message.error("评测失败");
      } finally {
        this.evaluating = false;
      }
    },
    async saveThresholds() {
      try {
        await request.post("rag/thresholds", this.thresholds);
        this.$message.success("阈值已保存");
      } catch (e) {
        this.$message.error("保存失败");
      }
    },
    initTrendChart() {
      this.trendChart = echarts.init(this.$refs.trendChart);
      this.trendChart.setOption({
        tooltip: { trigger: "axis" },
        legend: { data: ["上下文精确度", "回答忠实度", "答案相关性"] },
        xAxis: { type: "category", data: [] },
        yAxis: { type: "value", max: 100 },
        series: [
          { name: "上下文精确度", type: "line", smooth: true, data: [] },
          { name: "回答忠实度", type: "line", smooth: true, data: [] },
          { name: "答案相关性", type: "line", smooth: true, data: [] },
        ],
      });
    },

    // ---------------------------------------------------------------- 召回率 / 精确率
    /** 0~1 的比率转百分比文案；null / undefined 表示「无定义」，显示 — 而不是 0%。 */
    pct(v) {
      if (v === null || v === undefined || v === "") return "—";
      const n = Number(v);
      return Number.isNaN(n) ? "—" : (n * 100).toFixed(1) + "%";
    },
    /** 纯数值指标（MRR / nDCG）保留 3 位；无定义同样显示 —。 */
    num(v) {
      if (v === null || v === undefined || v === "") return "—";
      const n = Number(v);
      return Number.isNaN(n) ? "—" : n.toFixed(3);
    },

    async loadEvalLatest() {
      try {
        const res = await request.get("rag/admin/eval/latest", {
          params: { audience: this.evalAudience || undefined },
        });
        if (res.data && res.data.code === 200 && res.data.data) {
          this.applyEvalDetail(res.data.data);
        }
      } catch (e) {
        // 还没跑过评测、或表未建立：保持空态，不弹错误打扰
      }
    },
    applyEvalDetail(detail) {
      const run = detail.run || {};
      const inner = detail.detail || {};
      this.evalReport = {
        runId: run.runId,
        topK: run.topK,
        caseTotal: run.caseTotal,
        caseHit: run.caseHit,
        recall: run.recall,
        precisionAtK: run.precisionAtK,
        mrr: run.mrr,
        hitRate: run.hitRate,
        sparseOnly: run.sparseOnly,
        avgLatencyMs: run.avgLatencyMs,
        triggerBy: run.triggerBy,
        labeledTotal: inner.labeledTotal,
        keywordTotal: inner.keywordTotal,
        recallScored: inner.recallScored,
        precisionScored: inner.precisionScored,
        ndcg: inner.ndcg,
        note: inner.note,
      };
      this.evalCases = inner.cases || [];
    },
    async runEval() {
      this.evalRunning = true;
      try {
        const res = await request.post("rag/admin/eval/run", {
          audience: this.evalAudience || null,
          topK: this.evalTopK,
          triggerBy: "manual",
        });
        if (res.data && res.data.code === 200) {
          const report = res.data.data || {};
          this.evalReport = report;
          this.evalCases = report.cases || [];
          this.$message.success(
            `评测完成：${report.caseTotal || 0} 条用例，${report.labeledTotal || 0} 条带金标`
          );
          this.loadEvalHistory();
        } else {
          // 「评测集没准备好」是可操作的配置问题，后端已给出可照做的文案
          this.$message.warning((res.data && res.data.msg) || "评测未能执行");
        }
      } catch (e) {
        this.$message.error("评测执行失败");
      } finally {
        this.evalRunning = false;
      }
    },
    async loadEvalHistory() {
      try {
        const res = await request.post("rag/admin/eval/runs", { current: 1, size: 10 });
        if (res.data && res.data.code === 200) {
          this.evalHistory = res.data.data || [];
        }
      } catch (e) {
        console.error(e);
      }
    },

    // ---------------------------------------------------------------- 评测集
    openCases() {
      this.casesDialog = true;
    },
    newCase() {
      this.caseForm = EMPTY_CASE();
    },
    editCase(row) {
      this.caseForm = { ...EMPTY_CASE(), ...row, enabled: row.enabled !== false };
    },
    async loadCases() {
      this.caseLoading = true;
      try {
        const res = await request.post("rag/admin/eval/cases/query", {
          question: this.caseQuery.question || null,
          current: 1,
          size: 100,
        });
        if (res.data && res.data.code === 200) {
          this.caseRows = res.data.data || [];
        }
      } catch (e) {
        this.$message.error("加载评测集失败");
      } finally {
        this.caseLoading = false;
      }
    },
    async saveCase() {
      if (!this.caseForm.question || !this.caseForm.question.trim()) {
        this.$message.warning("问题不能为空");
        return;
      }
      this.caseSaving = true;
      try {
        const body = { ...this.caseForm };
        const res = this.caseForm.id
          ? await request.put("rag/admin/eval/cases/update", body)
          : await request.post("rag/admin/eval/cases/save", body);
        if (res.data && res.data.code === 200) {
          this.$message.success("已保存");
          this.newCase();
          this.loadCases();
        } else {
          // 校验规则由后端统一把守（金标与关键词至少一项），错误文案直接展示
          this.$message.error((res.data && res.data.msg) || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败");
      } finally {
        this.caseSaving = false;
      }
    },
    async removeCase(row) {
      try {
        await this.$confirm(`删除用例「${row.question}」？`, "确认删除", { type: "warning" });
      } catch (e) {
        return;
      }
      try {
        const res = await request.delete(`rag/admin/eval/cases/${row.id}`);
        if (res.data && res.data.code === 200) {
          this.$message.success("已删除");
          this.loadCases();
        } else {
          this.$message.error((res.data && res.data.msg) || "删除失败");
        }
      } catch (e) {
        this.$message.error("删除失败");
      }
    },
  },
};
</script>

<style scoped>
.rag-monitor-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 8px;
}
.page-header p {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.metric-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #f0f0f0;
}
.metric-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.metric-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}
.metric-label {
  font-size: 14px;
  color: #666;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 4px;
}
.metric-desc {
  font-size: 12px;
  color: #999;
}

.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}
.btn {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
}
.btn--primary {
  background: linear-gradient(135deg, #0050cb, #0066ff);
  color: #fff;
}
.btn--outline {
  background: transparent;
  border: 2px solid #0050cb;
  color: #0050cb;
}

.chart-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  margin-bottom: 24px;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}
.chart-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.chart-subtitle {
  font-size: 13px;
  color: #999;
}
.chart-body {
  padding: 20px;
}
.chart-container {
  width: 100%;
  height: 300px;
}

.section {
  margin-bottom: 24px;
}
.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 16px;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.record-item {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid #f0f0f0;
}
.record-item__time {
  font-size: 13px;
  color: #999;
  margin-bottom: 4px;
}
.record-item__metrics {
  display: flex;
  gap: 8px;
}
.metric-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(0, 80, 203, 0.06);
  color: #0050cb;
}
.record-item__status {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 10px;
}
.status-pass {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
.status-fail {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.threshold-grid {
  display: flex;
  gap: 16px;
  align-items: center;
  background: #fff;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}
.threshold-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.threshold-item label {
  font-size: 14px;
  color: #666;
}
.threshold-item input {
  width: 60px;
  height: 36px;
  border: 2px solid #f0f0f0;
  border-radius: 8px;
  text-align: center;
  font-size: 14px;
}
.threshold-item input:focus {
  outline: none;
  border-color: #0050cb;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

/* ==================== 召回率 / 精确率（金标评测） ==================== */
.eval-intro {
  background: #f7f9fc;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13px;
  color: #67707e;
  line-height: 1.8;
  margin-bottom: 16px;
}
.eval-intro b {
  color: #3d4859;
}
.eval-note {
  background: #fffaf3;
  border: 1px solid #fdd9b5;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12.5px;
  color: #b45309;
  line-height: 1.7;
  margin-bottom: 16px;
}
.eval-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #67707e;
}
.eval-field input,
.eval-field select {
  height: 34px;
  border: 2px solid #f0f0f0;
  border-radius: 8px;
  padding: 0 8px;
  font-size: 13px;
  color: #3d4859;
}
.eval-field input {
  width: 70px;
  text-align: center;
}
.eval-field input:focus,
.eval-field select:focus {
  outline: none;
  border-color: #0050cb;
}
.eval-table-wrap {
  padding: 0 0 4px;
  overflow-x: auto;
}
.eval-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.eval-table th,
.eval-table td {
  border-bottom: 1px solid #f0f0f0;
  padding: 8px 10px;
  text-align: left;
  color: #3d4859;
  font-variant-numeric: tabular-nums;
}
.eval-table th {
  color: #8b95a5;
  font-weight: 600;
  background: #fafbfd;
  white-space: nowrap;
}
.eval-table tbody tr:hover {
  background: #fafbfd;
}
.eval-q {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.eval-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  color: #67707e;
}
.eval-empty {
  text-align: center;
  color: #98a2b3;
  padding: 24px;
}
.eval-muted {
  color: #cbd2dc;
}
.eval-hint {
  padding: 10px 20px 16px;
  font-size: 12px;
  color: #98a2b3;
  line-height: 1.7;
}
.eval-hint b {
  color: #67707e;
}
.eval-dialog-tip {
  margin-bottom: 12px;
}
.eval-dialog-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.eval-case-form {
  padding: 12px 14px;
  background: #fafbfd;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  margin-bottom: 12px;
}
.eval-case-form :deep(.el-form-item) {
  margin-bottom: 10px;
}
.eval-form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #98a2b3;
}

/* 路径标签：sparse_like 是退化信号，必须一眼可辨 */
.path-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11.5px;
  background: rgba(107, 114, 128, 0.1);
  color: #6b7280;
  white-space: nowrap;
}
.path-hybrid {
  background: rgba(16, 185, 129, 0.12);
  color: #0f9d76;
}
.path-dense_only {
  background: rgba(99, 44, 229, 0.1);
  color: #632ce5;
}
.path-sparse_only {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}
.path-sparse_like {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}
</style>
