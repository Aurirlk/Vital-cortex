<template>
  <div class="dc">
    <!-- 顶部：标题 + 会话状态 -->
    <div class="dc-head">
      <div class="dc-head__left">
        <div class="dc-head__icon"><el-icon><component :is="ChatDotRound" /></el-icon></div>
        <div>
          <div class="dc-head__title">AI 临床助手</div>
          <div class="dc-head__sub">基于接诊数据的问答与工具调用 · 身份由登录态自动识别</div>
        </div>
      </div>
      <div class="dc-head__right">
        <el-tag :type="running ? 'warning' : 'success'" effect="plain" size="small">
          {{ running ? "正在生成" : "就绪" }}
        </el-tag>
        <el-button size="small" :icon="Delete" :disabled="!messages.length" @click="newSession">
          新会话
        </el-button>
        <el-button size="small" @click="openMemory">AI 记忆</el-button>
      </div>
    </div>

    <div class="dc-body">
      <!-- 左：对话主区 -->
      <div class="dc-chat">
        <div ref="chatScroll" class="dc-chat__scroll">
          <!-- 空态引导 -->
          <div v-if="!messages.length" class="dc-empty">
            <div class="dc-empty__title">您好，医生</div>
            <div class="dc-empty__desc">
              我可以在您已接诊的患者范围内检索健康指标、解释数据口径，也可以回答一般医学咨询。
              涉及具体患者数据时，服务端会按接诊授权二次校验。
            </div>
            <div class="dc-empty__cards">
              <button
                v-for="(p, i) in presets"
                :key="i"
                class="dc-empty__card"
                type="button"
                @click="usePreset(p.text)"
              >
                <span class="dc-empty__card-title">{{ p.title }}</span>
                <span class="dc-empty__card-text">{{ p.text }}</span>
              </button>
            </div>
          </div>

          <!-- 消息流 -->
          <div v-for="m in messages" :key="m.id" class="dc-msg" :class="'dc-msg--' + m.role">
            <div class="dc-msg__avatar">{{ m.role === "user" ? "医" : "AI" }}</div>
            <div class="dc-msg__main">
              <div class="dc-msg__meta">
                <span>{{ m.role === "user" ? "我" : agentName }}</span>
                <span class="dc-msg__time">{{ m.time }}</span>
              </div>
              <div class="dc-msg__bubble">
                <div v-if="m.tools && m.tools.length" class="dc-tools">
                  <div class="dc-tools__label">本轮工具调用</div>
                  <el-tag
                    v-for="(t, ti) in m.tools"
                    :key="ti"
                    class="dc-tools__tag"
                    size="small"
                    :type="t.success === false ? 'danger' : t.success === true ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ t.name }}
                    <span v-if="t.success === true">· 完成</span>
                    <span v-else-if="t.success === false">· 失败</span>
                    <span v-else>· 执行中</span>
                  </el-tag>
                </div>
                <div v-if="m.content" class="dc-msg__text">{{ m.content }}</div>
                <div v-else-if="m.status === 'thinking'" class="dc-msg__typing">
                  <span class="dc-dot" /><span class="dc-dot" /><span class="dc-dot" />
                  <span class="dc-msg__typing-text">正在理解问题并检索数据…</span>
                </div>
                <div v-else-if="m.status === 'error'" class="dc-msg__error">
                  本轮未能生成回答，请重试或换一个更明确的问题。
                </div>
                <span v-if="m.status === 'streaming'" class="dc-caret" />
              </div>
            </div>
          </div>
        </div>

        <!-- 错误提示 -->
        <el-alert
          v-if="errorText"
          class="dc-alert"
          type="warning"
          show-icon
          :closable="false"
          title="本轮提示"
          :description="errorText"
        />

        <!-- 快捷提问 -->
        <div v-if="messages.length" class="dc-presets">
          <span class="dc-presets__label">快捷提问</span>
          <el-tag
            v-for="(p, i) in presets"
            :key="i"
            class="dc-presets__tag"
            effect="plain"
            @click="usePreset(p.text)"
          >
            {{ p.title }}
          </el-tag>
        </div>

        <!-- 输入区 -->
        <div class="dc-input">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="500"
            show-word-limit
            placeholder="输入问题，Enter 发送，Shift + Enter 换行。例如：我接诊的患者里，最近一个月血压偏高的有哪些？"
            @keydown.enter.exact.prevent="send"
          />
          <div class="dc-input__bar">
            <span class="dc-input__hint">回答由模型生成，临床决策请以原始病历与检查结果为准。</span>
            <el-button v-if="running" @click="abort">停止生成</el-button>
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="running"
              :disabled="!question.trim()"
              @click="send"
            >
              {{ running ? "生成中…" : "发送" }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 右：本轮信息 -->
      <aside class="dc-side">
        <div class="dc-side__card">
          <div class="dc-side__title">会话信息</div>
          <div class="dc-side__row"><span>当前助手</span><b>{{ agentName }}</b></div>
          <div class="dc-side__row"><span>会话编号</span><b>{{ sessionId ? "#" + sessionId : "尚未建立" }}</b></div>
          <div class="dc-side__row"><span>本轮工具</span><b>{{ toolCount }} 次</b></div>
          <div class="dc-side__row"><span>连接状态</span><b>{{ running ? "流式传输中" : "空闲" }}</b></div>
        </div>

        <div class="dc-side__card dc-side__card--note">
          <div class="dc-side__title">使用说明</div>
          <p>1. 需要查询患者明细时，助手会通过工具读取您有接诊授权的数据。</p>
          <p>2. 服务端按登录态判定医生身份，无需、也不会接受请求里手工指定的患者身份。</p>
          <p>3. 需要完整的十步数据分析与报告，请使用「数据问答」。</p>
          <el-button class="dc-side__link" size="small" @click="goDataQa">
            前往数据问答
          </el-button>
        </div>
      </aside>
    </div>
    <!-- 阶段 8：医生端结构化记忆（独立命名空间，带 doctor-token） -->
    <ai-settings-drawer
      ref="memoryDrawer"
      endpoint="/doctor/ai-memory"
      :http="doctorRequest"
      title="医生端 AI 记忆"
    />
  </div>
</template>

<script>
import { ChatDotRound, Delete, Promotion } from "@element-plus/icons-vue";
import doctorRequest, { URL_API } from "@/utils/doctorRequest.js";
import { getDoctorToken } from "@/utils/doctorAuth.js";
import AiSettingsDrawer from "@/components/AiSettingsDrawer.vue";

/**
 * 解析一帧 SSE（event: + 多行 data:）。
 *
 * 与 utils/agentStream.js 的区别：那边只回调 step/result/error 三类固定事件，
 * 而医生端 ReAct 链路会发 agent_info / answer_chunk / answer_done / tool_call /
 * tool_result / session_info / reasoning_start，需要通用 onEvent 回调，
 * 因此这里保留一份精简解析（同样是 fetch + ReadableStream，EventSource 无法 POST）。
 */
function parseFrame(frame) {
  const lines = frame.split("\n");
  let event = "message";
  const dataLines = [];
  for (const line of lines) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).replace(/^ /, ""));
    }
  }
  if (!dataLines.length) return null;
  const raw = dataLines.join("\n");
  let data = raw;
  try {
    data = JSON.parse(raw);
  } catch (e) {
    /* 非 JSON 帧按纯文本交给上层 */
  }
  return { event, data };
}

let seq = 0;
function nextId() {
  seq += 1;
  return "m" + Date.now() + "-" + seq;
}
function clock() {
  return new Date().toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
}

export default {
  name: "DoctorChat",
  components: { AiSettingsDrawer },
  data() {
    return {
      doctorRequest,
      ChatDotRound,
      Delete,
      Promotion,
      question: "",
      messages: [],
      running: false,
      controller: null,
      sessionId: "",
      agentName: "AI 临床助手",
      agentType: "",
      toolCount: 0,
      errorText: "",
      presets: [
        { title: "患者概览", text: "帮我概括一下最近接诊患者的整体情况。" },
        { title: "指标解读", text: "血压持续偏高的患者，通常需要重点复核哪些指标？" },
        { title: "随访建议", text: "对血糖波动较大的患者，随访时应该关注哪些数据变化？" },
        { title: "术语口径", text: "系统里「血压偏高」这个指标口径是怎么定义的？" },
      ],
    };
  },
  methods: {
    openMemory() {
      if (this.$refs.memoryDrawer) this.$refs.memoryDrawer.open();
    },
    usePreset(text) {
      this.question = text;
    },
    newSession() {
      if (this.running) this.abort();
      this.messages = [];
      this.sessionId = "";
      this.toolCount = 0;
      this.errorText = "";
      this.agentName = "AI 临床助手";
      this.agentType = "";
      this.$message.info("已开启新会话");
    },
    goDataQa() {
      this.$router.push("/doctor/data-qa");
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.chatScroll;
        if (el) el.scrollTop = el.scrollHeight;
      });
    },
    abort() {
      if (this.controller) {
        this.controller.abort();
        this.controller = null;
        this.running = false;
        this.$message.info("已停止生成");
      }
    },
    async send() {
      const text = this.question.trim();
      if (!text || this.running) return;
      this.errorText = "";
      this.question = "";
      this.toolCount = 0;
      const aiMsg = {
        id: nextId(),
        role: "assistant",
        content: "",
        time: clock(),
        tools: [],
        status: "thinking",
      };
      this.messages.push(
        { id: nextId(), role: "user", content: text, time: clock(), tools: [] },
        aiMsg
      );
      this.scrollToBottom();
      this.running = true;
      const controller = new AbortController();
      this.controller = controller;
      try {
        await this.stream(text, aiMsg, controller.signal);
        aiMsg.status = aiMsg.content ? "done" : "error";
      } catch (e) {
        if (e && e.name === "AbortError") {
          aiMsg.status = aiMsg.content ? "done" : "error";
        } else {
          this.errorText = (e && e.message) || "连接 AI 助手失败，请稍后重试";
          aiMsg.status = aiMsg.content ? "done" : "error";
        }
      } finally {
        this.running = false;
        this.controller = null;
        this.scrollToBottom();
      }
    },
    async stream(text, aiMsg, signal) {
      const response = await fetch(URL_API + "/doctor/chat/stream", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          token: getDoctorToken() || "",
        },
        body: JSON.stringify({ message: text, sessionId: this.sessionId || undefined }),
        signal,
      });
      if (!response.ok) {
        if (response.status === 401) {
          throw new Error("登录状态已失效，请重新登录");
        }
        if (response.status === 403) {
          throw new Error("无权使用医生端 AI 助手");
        }
        throw new Error("服务暂不可用（HTTP " + response.status + "）");
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let sep;
        while ((sep = buffer.indexOf("\n\n")) >= 0) {
          const frame = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          const parsed = parseFrame(frame);
          if (parsed) this.handleEvent(parsed.event, parsed.data, aiMsg);
        }
      }
    },
    handleEvent(event, data, aiMsg) {
      const d = data && typeof data === "object" ? data : {};
      switch (event) {
        case "agent_info":
          this.agentType = d.agentType || "";
          this.agentName = d.agentName || "AI 临床助手";
          break;
        case "reasoning_start":
          aiMsg.status = "thinking";
          break;
        case "answer_chunk":
          aiMsg.content += d.content || (typeof data === "string" ? data : "");
          aiMsg.status = "streaming";
          this.scrollToBottom();
          break;
        case "answer_done":
          aiMsg.status = "done";
          break;
        case "tool_call": {
          const name = d.tool || "工具";
          aiMsg.tools.push({ name, success: null });
          this.toolCount += 1;
          this.scrollToBottom();
          break;
        }
        case "tool_result": {
          const name = d.tool || "工具";
          for (let i = aiMsg.tools.length - 1; i >= 0; i -= 1) {
            if (aiMsg.tools[i].name === name && aiMsg.tools[i].success === null) {
              aiMsg.tools[i].success = d.success !== false;
              break;
            }
          }
          break;
        }
        case "session_info":
          if (d.sessionId) this.sessionId = String(d.sessionId);
          break;
        case "error":
          this.errorText = d.message || "AI 助手暂时不可用，请稍后重试";
          aiMsg.status = aiMsg.content ? "done" : "error";
          break;
        default:
          break;
      }
    },
  },
};
</script>

<style scoped>
.dc {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.dc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 2px 10px rgba(16, 42, 80, 0.03);
}
.dc-head__left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.dc-head__icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  background: linear-gradient(135deg, #0050cb, #4b8ff5);
}
.dc-head__title {
  font-size: 17px;
  font-weight: 600;
  color: #1c2b3f;
}
.dc-head__sub {
  margin-top: 3px;
  font-size: 12px;
  color: #7a8798;
}
.dc-head__right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dc-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 14px;
  align-items: start;
}
.dc-chat {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(16, 42, 80, 0.03);
}
.dc-chat__scroll {
  height: 520px;
  overflow-y: auto;
  padding: 22px;
  background: #f8fafd;
}
.dc-empty {
  max-width: 660px;
  margin: 48px auto 0;
  text-align: center;
}
.dc-empty__title {
  font-size: 22px;
  font-weight: 600;
  color: #1c2b3f;
}
.dc-empty__desc {
  margin: 10px auto 24px;
  max-width: 560px;
  line-height: 1.8;
  font-size: 13px;
  color: #5a6675;
}
.dc-empty__cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  text-align: left;
}
.dc-empty__card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid #e6ecf5;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}
.dc-empty__card:hover {
  border-color: #0050cb;
  box-shadow: 0 6px 18px rgba(0, 80, 203, 0.09);
  transform: translateY(-2px);
}
.dc-empty__card-title {
  font-size: 13px;
  font-weight: 600;
  color: #0050cb;
}
.dc-empty__card-text {
  font-size: 12px;
  line-height: 1.6;
  color: #5a6675;
}
.dc-msg {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.dc-msg--user {
  flex-direction: row-reverse;
}
.dc-msg__avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #0050cb;
}
.dc-msg--assistant .dc-msg__avatar {
  background: linear-gradient(135deg, #4b8ff5, #00a6a6);
}
.dc-msg__main {
  max-width: 78%;
}
.dc-msg__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #7a8798;
}
.dc-msg--user .dc-msg__meta {
  justify-content: flex-end;
}
.dc-msg__time {
  color: #a3adba;
}
.dc-msg__bubble {
  padding: 12px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e6ecf5;
  font-size: 14px;
  line-height: 1.75;
  color: #243247;
  box-shadow: 0 2px 8px rgba(16, 42, 80, 0.04);
}
.dc-msg--user .dc-msg__bubble {
  background: #0050cb;
  border-color: #0050cb;
  color: #fff;
}
.dc-msg__text {
  white-space: pre-wrap;
  word-break: break-word;
}
.dc-msg__typing {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #7a8798;
  font-size: 13px;
}
.dc-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #0050cb;
  animation: dc-blink 1.2s infinite ease-in-out;
}
.dc-dot:nth-child(2) { animation-delay: 0.2s; }
.dc-dot:nth-child(3) { animation-delay: 0.4s; }
.dc-msg__typing-text { margin-left: 6px; }
.dc-msg__error { color: #c0392b; font-size: 13px; }
.dc-caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #0050cb;
  animation: dc-blink 0.8s infinite;
}
@keyframes dc-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}
.dc-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e6ecf5;
}
.dc-tools__label {
  width: 100%;
  font-size: 11px;
  color: #7a8798;
}
.dc-alert { margin: 0 18px 12px; }
.dc-presets {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 18px 0;
  border-top: 1px solid #f0f3f8;
}
.dc-presets__label { font-size: 12px; color: #7a8798; }
.dc-presets__tag { cursor: pointer; }
.dc-input {
  padding: 12px 18px 16px;
}
.dc-input__bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}
.dc-input__hint {
  margin-right: auto;
  font-size: 11px;
  color: #98a3b2;
}
.dc-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.dc-side__card {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 2px 10px rgba(16, 42, 80, 0.03);
}
.dc-side__title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1c2b3f;
}
.dc-side__row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed #f0f3f8;
  font-size: 12px;
  color: #7a8798;
}
.dc-side__row:last-child { border-bottom: none; }
.dc-side__row b {
  font-weight: 500;
  color: #243247;
  text-align: right;
  word-break: break-all;
}
.dc-side__card--note p {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #5a6675;
}
.dc-side__link { margin-top: 4px; }
@media (max-width: 1100px) {
  .dc-body { grid-template-columns: 1fr; }
  .dc-side { flex-direction: row; }
  .dc-side__card { flex: 1; }
}
@media (max-width: 700px) {
  .dc-empty__cards { grid-template-columns: 1fr; }
  .dc-side { flex-direction: column; }
  .dc-head__sub { display: none; }
  .dc-chat__scroll { height: 440px; }
}
</style>
