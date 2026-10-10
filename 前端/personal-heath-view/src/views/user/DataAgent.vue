<template>
  <div class="uda">
    <!-- 顶部：提问 + 开关 -->
    <div class="uda-bar">
      <div class="uda-bar__row">
        <span class="uda-bar__label">当前会话</span>
        <el-tag v-if="sessionId" type="success" effect="plain" size="small">
          #{{ sessionId }} · 可继续追问
        </el-tag>
        <el-tag v-else type="info" effect="plain" size="small">新会话</el-tag>
        <el-button
          v-if="sessionId"
          size="small"
          :icon="Plus"
          @click="newSession"
        >
          新会话
        </el-button>

        <el-switch v-model="enableReport" active-text="生成分析报告" inline-prompt />

        <el-button
          type="primary"
          :icon="Promotion"
          :loading="running"
          :disabled="!question.trim()"
          @click="ask"
        >
          {{ running ? "分析中…" : "开始分析" }}
        </el-button>
        <el-button v-if="running" @click="abort">中断</el-button>
        <el-button :icon="Clock" @click="historyVisible = true">历史会话</el-button>
      </div>

      <div class="uda-bar__row uda-bar__row--input">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          show-word-limit
          placeholder="用自然语言查询你的健康数据，例如：我最近 3 个月的血压趋势如何？"
          @keydown.enter.exact.prevent="ask"
        />
      </div>

      <div v-if="presets.length" class="uda-bar__row">
        <span class="uda-bar__label">试试这样问</span>
        <el-tag
          v-for="q in presets"
          :key="q"
          class="uda-preset"
          effect="plain"
          @click="question = q"
        >
          {{ q }}
        </el-tag>
      </div>
    </div>

    <el-alert
      v-if="disabledHint"
      class="uda-alert"
      type="warning"
      show-icon
      :closable="false"
      title="数据智能体未开启"
      description="请联系管理员在「系统配置 → 数据智能体」中开启。"
    />

    <div class="uda-main">
      <agent-workflow :steps="steps" />
      <agent-result :result="result" :error="error" />
    </div>

    <agent-history-dialog
      v-model="historyVisible"
      base-url="/user/data-agent"
      @select="onHistorySelect"
    />
  </div>
</template>

<script>
import { Promotion, Clock, Plus } from "@element-plus/icons-vue";
import request, { URL_API } from "@/utils/request.js";
import { getToken } from "@/utils/storage.js";
import { streamAgent } from "@/utils/agentStream.js";
import AgentWorkflow from "@/components/AgentWorkflow.vue";
import AgentResult from "@/components/AgentResult.vue";
import AgentHistoryDialog from "@/components/AgentHistoryDialog.vue";

export default {
  name: "DataAgent",
  components: { Promotion, Clock, Plus, AgentWorkflow, AgentResult, AgentHistoryDialog },
  data() {
    return {
      Promotion,
      Clock,
      Plus,
      question: "",
      enableReport: true,
      running: false,
      controller: null,
      steps: [],
      result: null,
      error: "",
      disabledHint: false,
      historyVisible: false,
      presets: [],
      /**
       * 当前会话 id（后端 ai_conversation.id）。
       * ⚠️ 必须回传给后端，否则每轮追问都会新建一条会话，追问也拿不到上下文。
       */
      sessionId: "",
    };
  },
  created() {
    this.loadPresets();
  },
  methods: {
    /** 预设问题由管理员在「数据智能体 → 预设问题」维护，失败时保留空列表。 */
    async loadPresets() {
      try {
        const res = await request.get("/user/data-agent/presets");
        const data = (res.data && res.data.data) || [];
        this.presets = Array.isArray(data) ? data : [];
      } catch (e) {
        this.presets = [];
      }
    },

    /** 开新会话：清空上下文，下一轮提问让后端新建 ai_conversation。 */
    newSession() {
      this.sessionId = "";
      this.steps = [];
      this.result = null;
      this.error = "";
      this.$message.info("已开启新会话");
    },

    /** 从历史会话抽屉里选中一条 —— 接回该会话继续追问。 */
    onHistorySelect(payload) {
      if (!payload || !payload.id) return;
      this.sessionId = String(payload.id);
      this.historyVisible = false;
      this.steps = [];
      this.result = null;
      this.error = "";
      this.$message.success("已切换到该会话，可继续追问");
    },

    ask() {
      if (!this.question.trim() || this.running) return;
      this.running = true;
      this.steps = [];
      this.result = null;
      this.error = "";
      this.disabledHint = false;

      const controller = new AbortController();
      this.controller = controller;

      streamAgent({
        url: URL_API + "/user/data-agent/query/stream",
        token: getToken(),
        body: {
          question: this.question.trim(),
          enableReport: this.enableReport,
          // 关键：回传会话 id，让追问落在同一条会话里（后端会据此拼多轮上下文）
          sessionId: this.sessionId || undefined,
        },
        signal: controller.signal,
        onStep: (ev) => this.upsertStep(ev),
        onResult: (res) => {
          this.result = res;
          // 后端 persist() 会把（可能是新建的）会话 id 回传，必须存下来
          if (res && res.sessionId) {
            this.sessionId = String(res.sessionId);
          }
          if (res && !res.success && (res.message || "").indexOf("未开启") >= 0) {
            this.disabledHint = true;
          }
        },
        onError: (err) => {
          this.error = (err && err.message) || "数据问答失败";
        },
      })
        .catch((e) => {
          if (e && e.status === 401) {
            this.$message.error("登录已失效，请重新登录");
          } else {
            this.error = "请求失败：" + ((e && e.message) || "未知错误");
          }
        })
        .finally(() => {
          this.running = false;
          this.controller = null;
        });
    },

    abort() {
      if (this.controller) {
        this.controller.abort();
        this.running = false;
        this.$message.info("已中断分析");
      }
    },

    /** 合并同一步骤的 RUNNING → SUCCESS/FAILED 事件 */
    upsertStep(ev) {
      if (!ev || !ev.step) return;
      const idx = this.steps.findIndex((s) => s.step === ev.step);
      if (idx === -1) {
        this.steps.push({ ...ev });
      } else {
        this.steps.splice(idx, 1, { ...ev });
      }
      if (ev.status === "FAILED" && ev.summary) {
        this.error = ev.summary;
      }
    },
  },
};
</script>

<style scoped>
.uda {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.uda-bar {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.uda-bar__row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.uda-bar__row--input {
  display: block;
}
.uda-bar__label {
  font-size: 13px;
  color: #5a6675;
  flex-shrink: 0;
}
.uda-preset {
  cursor: pointer;
}
.uda-alert {
  margin: 0;
}
.uda-main {
  display: grid;
  grid-template-columns: minmax(320px, 420px) 1fr;
  gap: 14px;
  align-items: start;
}
@media (max-width: 1100px) {
  .uda-main {
    grid-template-columns: 1fr;
  }
}
</style>
