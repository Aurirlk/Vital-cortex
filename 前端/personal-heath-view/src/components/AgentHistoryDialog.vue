<template>
  <el-drawer
    v-model="visible"
    :title="isDoctor ? '数据问答历史' : '我的数据 · 历史记录'"
    size="760px"
    append-to-body
    @open="onOpen"
  >
    <div class="ahd">
      <agent-history
        ref="history"
        class="ahd__side"
        :is-doctor="isDoctor"
        :base-url="baseUrl"
        @select="onSelect"
      />

      <div class="ahd__main" ref="main">
        <div v-if="!current" class="ahd__empty">
          左侧选择一个会话，即可回看当时的提问与结果。
        </div>
        <template v-else>
          <div class="ahd__title">{{ current.title || "未命名会话" }}</div>
          <div v-if="!messages.length" class="ahd__empty">该会话没有可显示的消息。</div>
          <div
            v-for="(m, i) in messages"
            :key="i"
            class="ahd__msg"
            :class="'is-' + (m.role === 'user' ? 'user' : 'assistant')"
          >
            <div class="ahd__role">{{ m.role === "user" ? "我" : "数据智能体" }}</div>
            <!-- 助手消息可能是 Markdown 报告，需消毒后渲染（防存储型 XSS） -->
            <div
              v-if="m.role === 'user'"
              class="ahd__text"
            >{{ m.content }}</div>
            <div v-else class="ahd__text ahd__text--md" v-html="renderMd(m.content)"></div>
          </div>
        </template>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import AgentHistory from "@/components/AgentHistory.vue";
import { renderMarkdown, renderChartsIn, disposeChartsIn } from "@/utils/markdownReport.js";

export default {
  name: "AgentHistoryDialog",
  components: { AgentHistory },
  props: {
    modelValue: { type: Boolean, default: false },
    isDoctor: { type: Boolean, default: false },
    baseUrl: { type: String, required: true },
  },
  emits: ["update:modelValue", "select"],
  data() {
    return { current: null, messages: [] };
  },
  computed: {
    visible: {
      get() {
        return this.modelValue;
      },
      set(v) {
        this.$emit("update:modelValue", v);
      },
    },
  },
  methods: {
    onOpen() {
      if (this.$refs.history) this.$refs.history.reload();
    },
    onSelect(payload) {
      this.current = { id: payload.id, title: payload.title };
      this.messages = payload.messages || [];
      // 历史报告里可能含 ```echarts 围栏，渲染成图表
      this.$nextTick(() => renderChartsIn(this.$refs.main));
      // 向上冒泡：宿主页面拿到 id 后可「接回该会话继续追问」。
      // 不监听即无副作用（医生端当前未监听）。
      this.$emit("select", {
        id: payload.id,
        title: payload.title,
        messages: this.messages,
      });
    },
    renderMd(content) {
      return renderMarkdown(content);
    },
  },
  beforeUnmount() {
    disposeChartsIn(this.$refs.main);
  },
};
</script>

<style scoped>
.ahd {
  display: grid;
  grid-template-columns: 268px 1fr;
  gap: 14px;
  height: 100%;
}
.ahd__side {
  min-width: 0;
}
.ahd__main {
  min-width: 0;
  overflow-y: auto;
  padding-right: 4px;
}
.ahd__title {
  font-size: 14px;
  font-weight: 600;
  color: #1b2433;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eef3fa;
}
.ahd__empty {
  color: #9aa4b2;
  font-size: 13px;
  line-height: 1.9;
  padding: 12px 0;
}
.ahd__msg {
  margin-bottom: 14px;
}
.ahd__role {
  font-size: 12px;
  color: #8b95a5;
  margin-bottom: 4px;
}
.ahd__text {
  font-size: 13.5px;
  line-height: 1.8;
  color: #333d4d;
  border-radius: 10px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.ahd__msg.is-user .ahd__text {
  background: #f0f6ff;
}
.ahd__msg.is-assistant .ahd__text {
  background: #f7f9fc;
}
.ahd__text--md {
  white-space: normal;
}
.ahd__text--md :deep(h1),
.ahd__text--md :deep(h2),
.ahd__text--md :deep(h3) {
  font-size: 14px;
  margin: 10px 0 6px;
  color: #1b2433;
}
.ahd__text--md :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}
.ahd__text--md :deep(th),
.ahd__text--md :deep(td) {
  border: 1px solid #e6ecf5;
  padding: 5px 8px;
  font-size: 12.5px;
}
.ahd__text--md :deep(th) {
  background: #f5f8fd;
}
.ahd__text--md :deep(pre) {
  background: #fff;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 10px;
  overflow: auto;
}
.ahd__text--md :deep(.md-echarts) {
  width: 100%;
  height: 240px;
}
</style>
