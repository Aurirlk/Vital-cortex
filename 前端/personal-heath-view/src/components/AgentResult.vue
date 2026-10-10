<template>
  <div class="agent-result">
    <div class="ar-head">
      <span class="ar-head__title">分析结果</span>
      <span class="ar-head__meta" v-if="result">
        共 {{ result.rowCount }} 行 ·
        {{ result.tenantApplied ? "已限定 " + result.tenantColumn : "无租户限制" }}
      </span>
      <span class="ar-head__tools" v-if="hasReport">
        <el-button link size="small" @click="downloadMd">下载 MD</el-button>
        <el-button link size="small" @click="downloadHtml">下载 HTML</el-button>
        <el-button link size="small" @click="openFullscreen">全屏</el-button>
      </span>
    </div>
    <div class="ar-body">
      <div v-if="error" class="ar-error">{{ error }}</div>
      <div v-if="!result && !error" class="ar-empty">
        查询结果、生成的 SQL 与分析报告会显示在这里。
      </div>

      <template v-if="result && result.success">
        <div v-if="result.answer && result.intent === 'CHITCHAT'" class="ar-answer">
          {{ result.answer }}
        </div>

        <div v-if="result.sql" class="ar-block">
          <div class="ar-block__title">执行的 SQL</div>
          <pre class="ar-sql">{{ result.sql }}</pre>
        </div>

        <div v-if="result.rows && result.rows.length" class="ar-block">
          <div class="ar-block__title">查询结果</div>
          <el-table :data="result.rows" border size="small" max-height="320">
            <el-table-column
              v-for="col in columns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="110"
              show-overflow-tooltip
            />
          </el-table>
        </div>
        <div v-else-if="result.intent !== 'CHITCHAT'" class="ar-empty ar-empty--sm">
          查询成功，但结果为空。
        </div>

        <div v-if="result.report" class="ar-block">
          <div class="ar-block__title">分析报告</div>
          <!-- 内容已经过 DOMPurify 消毒（报告来自 LLM 且会落库，属存储型 XSS 面） -->
          <div ref="reportBody" class="ar-report" v-html="reportHtml"></div>
        </div>
      </template>

      <div v-else-if="result && !result.success" class="ar-error">
        {{ result.message }}
      </div>
    </div>

    <el-dialog
      v-model="fullscreen"
      title="分析报告"
      fullscreen
      append-to-body
      destroy-on-close
      class="ar-fs-dialog"
      @opened="onFullscreenOpened"
      @closed="onFullscreenClosed"
    >
      <div ref="fsBody" class="ar-report ar-report--fs" v-html="reportHtml"></div>
    </el-dialog>
  </div>
</template>

<script>
import {
  renderMarkdown,
  renderChartsIn,
  disposeChartsIn,
  resizeChartsIn,
  downloadText,
  reportFileName,
  buildStandaloneHtml,
} from "@/utils/markdownReport.js";

export default {
  name: "AgentResult",
  props: {
    result: { type: Object, default: null },
    error: { type: String, default: "" },
  },
  data() {
    return { fullscreen: false, resizeTimer: null };
  },
  computed: {
    columns() {
      const rows = (this.result && this.result.rows) || [];
      return rows.length ? Object.keys(rows[0]) : [];
    },
    hasReport() {
      return !!(this.result && this.result.report);
    },
    reportHtml() {
      const md = (this.result && this.result.report) || "";
      return md ? renderMarkdown(md) : "";
    },
  },
  watch: {
    // 报告到达后立刻把 ```echarts 围栏渲染成图表
    reportHtml() {
      this.$nextTick(() => renderChartsIn(this.$refs.reportBody));
    },
  },
  mounted() {
    window.addEventListener("resize", this.onResize);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.onResize);
    if (this.resizeTimer) clearTimeout(this.resizeTimer);
    disposeChartsIn(this.$refs.reportBody);
    disposeChartsIn(this.$refs.fsBody);
  },
  methods: {
    onResize() {
      if (this.resizeTimer) clearTimeout(this.resizeTimer);
      this.resizeTimer = setTimeout(() => {
        resizeChartsIn(this.$refs.reportBody);
        resizeChartsIn(this.$refs.fsBody);
      }, 200);
    },
    openFullscreen() {
      this.fullscreen = true;
    },
    onFullscreenOpened() {
      // 弹窗真正可见后才渲染，否则容器尺寸为 0 会画出空白图
      renderChartsIn(this.$refs.fsBody);
    },
    onFullscreenClosed() {
      disposeChartsIn(this.$refs.fsBody);
    },
    downloadMd() {
      const md = (this.result && this.result.report) || "";
      if (!md) return;
      downloadText(reportFileName("健康数据分析报告", "md"), md, "text/markdown;charset=utf-8");
    },
    downloadHtml() {
      if (!this.hasReport) return;
      const html = buildStandaloneHtml(this.$refs.reportBody, "健康数据分析报告");
      downloadText(reportFileName("健康数据分析报告", "html"), html, "text/html;charset=utf-8");
    },
  },
};
</script>

<style scoped>
.agent-result {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ar-head {
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #eef3fa;
  gap: 10px;
}
.ar-head__title {
  font-size: 14px;
  font-weight: 600;
  color: #1b2433;
}
.ar-head__meta {
  font-size: 12px;
  color: #8b95a5;
  margin-right: auto;
  padding-left: 12px;
}
.ar-head__tools {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.ar-body {
  padding: 14px 16px;
  overflow-y: auto;
  max-height: calc(100vh - 340px);
  min-height: 120px;
}
.ar-empty {
  color: #9aa4b2;
  font-size: 13px;
  line-height: 1.8;
  padding: 8px 0;
}
.ar-empty--sm {
  padding: 4px 0;
}
.ar-block {
  margin-bottom: 18px;
}
.ar-block__title {
  font-size: 13px;
  font-weight: 600;
  color: #1b2433;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #2f7bf6;
}
.ar-sql {
  margin: 0;
  background: #0f2557;
  color: #dbe7ff;
  border-radius: 8px;
  padding: 12px;
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "JetBrains Mono", Consolas, Menlo, monospace;
}
.ar-answer {
  font-size: 14px;
  color: #333d4d;
  line-height: 1.8;
  margin-bottom: 12px;
}
.ar-error {
  background: #fef0f0;
  border: 1px solid #fde2e2;
  color: #d9534f;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
}
.ar-report {
  font-size: 13.5px;
  line-height: 1.85;
  color: #333d4d;
  word-break: break-word;
}
.ar-report--fs {
  max-width: 1000px;
  margin: 0 auto;
  padding: 8px 4px 40px;
  font-size: 14.5px;
}
.ar-report :deep(h1),
.ar-report :deep(h2),
.ar-report :deep(h3) {
  font-size: 15px;
  margin: 14px 0 8px;
  color: #1b2433;
}
.ar-report :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
}
.ar-report :deep(th),
.ar-report :deep(td) {
  border: 1px solid #e6ecf5;
  padding: 6px 10px;
  font-size: 12.5px;
}
.ar-report :deep(th) {
  background: #f5f8fd;
}
.ar-report :deep(code) {
  background: #f2f6fc;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12.5px;
}
.ar-report :deep(pre) {
  background: #f7f9fc;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 10px;
  overflow: auto;
}
.ar-report :deep(.md-echarts) {
  width: 100%;
  height: 360px;
  margin: 12px 0;
}
.ar-report :deep(.md-echarts--empty),
.ar-report :deep(.md-echarts--invalid) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 90px;
  color: #9aa4b2;
  font-size: 13px;
  background: #f7f9fc;
  border: 1px dashed #dfe7f2;
  border-radius: 8px;
}
</style>
