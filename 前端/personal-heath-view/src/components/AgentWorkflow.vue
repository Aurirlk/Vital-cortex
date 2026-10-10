<template>
  <div class="agent-workflow">
    <div class="aw-head">
      <span class="aw-head__title">工作流</span>
      <span class="aw-head__meta" v-if="steps.length">
        {{ finishedCount }}/{{ steps.length }} 步 · {{ totalElapsed }} ms
      </span>
    </div>
    <div class="aw-body">
      <div v-if="!steps.length" class="aw-empty">
        提问后这里会展示数据智能体的完整工作流、思考过程与工具调用。
      </div>

      <div
        v-for="step in steps"
        :key="step.step"
        class="aw-step"
        :class="'is-' + (step.status || '').toLowerCase()"
      >
        <div class="aw-step__head" @click="toggle(step.step)">
          <span class="aw-step__order">{{ step.order }}</span>
          <span class="aw-step__icon">
            <el-icon v-if="step.status === 'RUNNING'" class="is-spin"><Loading /></el-icon>
            <el-icon v-else-if="step.status === 'SUCCESS'"><CircleCheckFilled /></el-icon>
            <el-icon v-else-if="step.status === 'FAILED'"><CircleCloseFilled /></el-icon>
            <el-icon v-else><RemoveFilled /></el-icon>
          </span>
          <span class="aw-step__title">{{ step.title }}</span>
          <span class="aw-step__tool">工具 {{ step.tool }}</span>
          <span class="aw-step__time" v-if="step.elapsedMs">{{ step.elapsedMs }}ms</span>
        </div>

        <div class="aw-step__summary">{{ step.summary }}</div>

        <div v-if="expanded[step.step]" class="aw-step__detail">
          <div v-if="step.thinking" class="aw-detail">
            <div class="aw-detail__label">思考 / 模型输出</div>
            <pre class="aw-detail__pre">{{ step.thinking }}</pre>
          </div>
          <div v-if="step.payload && Object.keys(step.payload).length" class="aw-detail">
            <div class="aw-detail__label">结构化数据</div>
            <pre class="aw-detail__pre">{{ pretty(step.payload) }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {
  Loading,
  CircleCheckFilled,
  CircleCloseFilled,
  RemoveFilled,
} from "@element-plus/icons-vue";

export default {
  name: "AgentWorkflow",
  components: { Loading, CircleCheckFilled, CircleCloseFilled, RemoveFilled },
  props: {
    steps: { type: Array, default: () => [] },
  },
  data() {
    return { expanded: {} };
  },
  computed: {
    finishedCount() {
      return this.steps.filter((s) => s.status !== "RUNNING").length;
    },
    totalElapsed() {
      return this.steps.reduce((sum, s) => sum + (s.elapsedMs || 0), 0);
    },
  },
  methods: {
    toggle(name) {
      this.expanded = { ...this.expanded, [name]: !this.expanded[name] };
    },
    pretty(payload) {
      try {
        return JSON.stringify(payload, null, 2);
      } catch (e) {
        return String(payload);
      }
    },
  },
};
</script>

<style scoped>
.agent-workflow {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.aw-head {
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #eef3fa;
}
.aw-head__title {
  font-size: 14px;
  font-weight: 600;
  color: #1b2433;
}
.aw-head__meta {
  font-size: 12px;
  color: #8b95a5;
}
.aw-body {
  padding: 14px 16px;
  overflow-y: auto;
  max-height: calc(100vh - 340px);
  min-height: 120px;
}
.aw-empty {
  color: #9aa4b2;
  font-size: 13px;
  line-height: 1.8;
  padding: 8px 0;
}

.aw-step {
  border-left: 3px solid #dfe7f2;
  padding: 8px 0 8px 12px;
  margin-bottom: 10px;
}
.aw-step.is-running {
  border-left-color: #2f7bf6;
}
.aw-step.is-success {
  border-left-color: #34c759;
}
.aw-step.is-failed {
  border-left-color: #f56c6c;
}
.aw-step.is-skipped {
  border-left-color: #c8d0da;
  opacity: 0.72;
}
.aw-step__head {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}
.aw-step__order {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #eef3fa;
  color: #5a6675;
  font-size: 11.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.aw-step__icon {
  display: flex;
  align-items: center;
  font-size: 15px;
  color: #34c759;
}
.aw-step.is-running .aw-step__icon {
  color: #2f7bf6;
}
.aw-step.is-failed .aw-step__icon {
  color: #f56c6c;
}
.aw-step.is-skipped .aw-step__icon {
  color: #9aa4b2;
}
.aw-step__title {
  font-size: 13.5px;
  font-weight: 600;
  color: #1b2433;
}
.aw-step__tool {
  font-size: 11.5px;
  color: #6b7484;
  background: #f2f6fc;
  padding: 1px 8px;
  border-radius: 10px;
}
.aw-step__time {
  margin-left: auto;
  font-size: 11.5px;
  color: #98a2b3;
}
.aw-step__summary {
  font-size: 12.5px;
  color: #67707e;
  margin-top: 4px;
  padding-left: 28px;
  line-height: 1.6;
}
.aw-step__detail {
  margin-top: 8px;
  padding-left: 28px;
}
.aw-detail {
  margin-bottom: 8px;
}
.aw-detail__label {
  font-size: 12px;
  color: #8b95a5;
  margin-bottom: 4px;
}
.aw-detail__pre {
  margin: 0;
  background: #f7f9fc;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  line-height: 1.65;
  color: #333d4d;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow: auto;
  font-family: "JetBrains Mono", Consolas, Menlo, monospace;
}
.is-spin {
  animation: aw-spin 0.9s linear infinite;
}
@keyframes aw-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
