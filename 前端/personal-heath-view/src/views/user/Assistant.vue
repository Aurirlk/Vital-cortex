<template>
  <div class="assistant-page">
    <!--  -->
    <div class="history-sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title"></span>
        <el-button size="small" type="primary" @click="newChat" :icon="Plus"></el-button>
      </div>
      <div class="history-list">
        <div v-if="conversations.length === 0" class="no-history"></div>
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['history-item', { active: currentConvId === conv.id }]"
          @click="switchConv(conv)"
        >
          <div class="history-title">{{ conv.title }}</div>
          <div class="history-meta">
            <span>{{ conv.msgCount }}</span>
            <span>{{ conv.time }}</span>
          </div>
          <el-button class="history-del" size="small" type="danger" text @click.stop="delConv(conv.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!--  -->
    <div class="chat-panel">
      <div class="chat-header">
        <span class="header-title"></span>
        <span class="header-desc"> · </span>
      </div>

      <div class="chat-messages" ref="chatMessages">
        <div v-if="messages.length === 0" class="chat-empty">
          <p class="welcome-text"></p>
          <div class="intent-cards">
            <div class="intent-card" @click="quickAsk('search')">
              <el-icon :size="20"><Search /></el-icon>
              <span></span>
              <small></small>
            </div>
            <div class="intent-card" @click="quickAsk('doctor')">
              <el-icon :size="20"><UserFilled /></el-icon>
              <span></span>
              <small></small>
            </div>
            <div class="intent-card" @click="quickAsk('drug')">
              <el-icon :size="20"><FirstAidKit /></el-icon>
              <span></span>
              <small></small>
            </div>
            <div class="intent-card" @click="quickAsk('knowledge')">
              <el-icon :size="20"><Collection /></el-icon>
              <span></span>
              <small></small>
            </div>
          </div>
          <p style="font-size:13px;color:#999;margin-top:16px;"></p>
        </div>
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
          <div class="msg-avatar"><el-icon v-if="msg.role==='user'"><User /></el-icon><el-icon v-else><ChatDotRound /></el-icon></div>
          <div class="msg-bubble">
            <div v-if="msg.intent" class="msg-intent">{{ intentLabel(msg.intent) }}</div>
            <div class="msg-content" v-html="msg.content"></div>
            <div v-if="msg.doctors" class="doctor-quick">
              <div v-for="d in msg.doctors" :key="d.key" class="doctor-quick-btn" @click="jumpToDoctor(d.key)">
                {{ d.icon }} {{ d.name }}
              </div>
            </div>
            <div class="msg-time">{{ msg.time }}</div>
          </div>
        </div>
        <div v-if="loading" class="msg assistant">
          <div class="msg-avatar"><el-icon><ChatDotRound /></el-icon></div>
          <div class="msg-bubble"><div class="typing"><span></span><span></span><span></span></div></div>
        </div>
      </div>

      <div class="chat-input">
        <el-input v-model="input" placeholder="..." @keyup.enter="send" :disabled="loading" size="large">
          <template #append>
            <el-button @click="send" :loading="loading" type="primary"></el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>
</template>

<script>
import { getToken } from "@/utils/storage.js";
import { marked } from "marked";
marked.setOptions({ breaks: true, gfm: true });

const STORAGE_KEY = "assistant_conversations";

export default {
  name: "UserAssistant",
  data() {
    return {
      input: "", messages: [], loading: false,
      currentConvId: null,
      conversations: [],
      intents: {
        search: ["","","","","","","","","","",""],
        drug: ["","","","","","","","","",""],
        doctor: ["","","","","","",""],
        knowledge: ["","","","","","","","",""],
      },
      // 
      doctorKeywords: {
        doctor: { keywords: ["","","","","","","","","","","","","","","","",""], name: "", icon: "🩺", desc: "" },
        nutritionist: { keywords: ["","","","","","","","","","","","","","","","","",""], name: "", icon: "", desc: "" },
        psychologist: { keywords: ["","","","","","","","","","","","","","","","","",""], name: "", icon: "", desc: "" },
        analyst: { keywords: ["","","","","","","","","","","","","","","",""], name: "", icon: "", desc: "" },
        general_assistant: { keywords: ["","","","","","","","",""], name: "", icon: "", desc: "" },
      },
    };
  },
  created() {
    this.loadConversations();
  },
  methods: {
    // =====  =====
    loadConversations() {
      try {
        const saved = localStorage.getItem(STORAGE_KEY);
        this.conversations = saved ? JSON.parse(saved) : [];
      } catch { this.conversations = []; }
    },
    saveConversations() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.conversations));
    },
    newChat() {
      if (this.currentConvId) {
        this.saveCurrentConv();
      }
      this.currentConvId = Date.now();
      this.messages = [];
      const conv = { id: this.currentConvId, title: "", msgCount: 0, time: this.now() };
      this.conversations.unshift(conv);
      this.saveConversations();
    },
    switchConv(conv) {
      if (this.currentConvId) this.saveCurrentConv();
      this.currentConvId = conv.id;
      this.messages = conv.messages || [];
      this.scrollDown();
    },
    delConv(id) {
      this.conversations = this.conversations.filter(c => c.id !== id);
      if (this.currentConvId === id) {
        this.currentConvId = null;
        this.messages = [];
      }
      this.saveConversations();
    },
    saveCurrentConv() {
      const conv = this.conversations.find(c => c.id === this.currentConvId);
      if (conv && this.messages.length > 0) {
        conv.messages = [...this.messages];
        conv.msgCount = this.messages.length;
        const firstUser = this.messages.find(m => m.role === "user");
        if (firstUser) conv.title = firstUser.content.substring(0, 20) + (firstUser.content.length > 20 ? "..." : "");
        conv.time = this.now();
      }
      this.saveConversations();
    },

    // =====  =====
    quickAsk(type) {
      const prompts = {
        search: "",
        doctor: "AI",
        drug: "",
        knowledge: "",
      };
      this.input = prompts[type] || "";
      this.send();
    },
    async send() {
      const msg = this.input.trim();
      if (!msg || this.loading) return;
      this.input = "";
      this.loading = true;

      if (!this.currentConvId) this.newChat();

      const intent = this.recognizeIntent(msg);
      this.messages.push({ role: "user", content: msg, time: this.now(), intent });
      const aiMsg = { role: "assistant", content: "", intent, time: this.now() };
      this.messages.push(aiMsg);
      this.scrollDown();
      this.saveCurrentConv();

      const token = getToken();
      const headers = { "Content-Type": "application/json" };
      if (token) headers["token"] = token;

      try {
        if (intent === "doctor") {
          const matched = this.matchDoctors(msg);
          aiMsg.doctors = matched.map(d => ({ key: d.key, name: d.name, icon: d.icon }));
          if (matched.length === 1) {
            aiMsg.content = `<strong>${matched[0].name}</strong><br>${matched[0].desc}<br> `;
          } else {
            aiMsg.content = `<strong> ${matched.length} AI</strong><br> `;
          }
        } else {
          const body = this.buildRequestBody(msg, intent);
          // Dify
          if (intent === "knowledge") {
            try {
              const kwRes = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/keywords/extract", {
                method: "POST", headers, body: JSON.stringify({ message: msg }),
              });
              const kwData = await kwRes.json();
              if (kwData.code === 200 && kwData.data?.length > 0) body.keywords = kwData.data;
            } catch (e) { console.warn("Dify:", e); }
          }
          const res = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/chat", {
            method: "POST", headers, body: JSON.stringify(body),
          });
          const data = await res.json();
          aiMsg.content = marked.parse(data.data?.reply || "");
        }
      } catch (e) {
        aiMsg.content = "" + e.message;
      } finally {
        this.loading = false;
        this.scrollDown();
        this.saveCurrentConv();
      }
    },
    buildRequestBody(msg, intent) {
      const body = { message: msg, enableHealthData: false };
      if (intent === "search") { body.role = "doctor"; body.enableWebSearch = true; body.enableKnowledgeBase = false; }
      else if (intent === "drug") { body.role = "consultant"; body.enableWebSearch = false; body.enableKnowledgeBase = false; }
      else { body.role = "general_assistant"; body.enableKnowledgeBase = true; body.enableWebSearch = false; }
      return body;
    },
    recognizeIntent(msg) {
      const lower = msg.toLowerCase();
      const scores = {};
      for (const [intent, keywords] of Object.entries(this.intents)) {
        scores[intent] = keywords.filter(k => lower.includes(k.toLowerCase())).length;
      }
      const best = Object.entries(scores).sort((a, b) => b[1] - a[1])[0];
      return best[1] > 0 ? best[0] : "knowledge";
    },
    matchDoctors(msg) {
      const lower = msg.toLowerCase();
      const scored = Object.entries(this.doctorKeywords).map(([key, d]) => {
        const hits = d.keywords.filter(k => lower.includes(k.toLowerCase())).length;
        return { key, ...d, score: hits };
      });
      const matched = scored.filter(d => d.score > 0).sort((a, b) => b.score - a.score);
      // 3
      return matched.length > 0 ? matched.slice(0, 3) : [scored.find(d => d.key === "doctor")];
    },
    intentLabel(intent) {
      return { search: "", drug: "", doctor: "", knowledge: "" }[intent] || intent;
    },
    jumpToDoctor(key) {
      sessionStorage.setItem("navAssistantRole", key);
      this.$router.push("/user/ai-analysis");
    },
    now() {
      const d = new Date();
      return `${d.getHours().toString().padStart(2,"0")}:${d.getMinutes().toString().padStart(2,"0")}`;
    },
    scrollDown() {
      this.$nextTick(() => {
        const c = this.$refs.chatMessages;
        if (c) c.scrollTop = c.scrollHeight;
      });
    },
  },
};
</script>

<style scoped>
.assistant-page { display: flex; height: calc(100vh - 130px); gap: 0; }

/*  */
.history-sidebar { width: 260px; background: #fff; border-right: 1px solid #f0f0f0; display: flex; flex-direction: column; flex-shrink: 0; }
.sidebar-header { padding: 16px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
.sidebar-title { font-size: 15px; font-weight: 600; color: #333; }
.history-list { flex: 1; overflow-y: auto; padding: 10px; }
.no-history { text-align: center; color: #ccc; padding: 30px 0; font-size: 13px; }
.history-item { padding: 12px; border-radius: 8px; cursor: pointer; margin-bottom: 6px; position: relative; border: 1px solid transparent; transition: all 0.2s; }
.history-item:hover { background: #f5f7fa; }
.history-item.active { background: #ecf5ff; border-color: #409eff; }
.history-title { font-size: 13px; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding-right: 24px; }
.history-meta { font-size: 11px; color: #999; margin-top: 4px; display: flex; gap: 8px; }
.history-del { position: absolute; right: 8px; top: 8px; opacity: 0; }
.history-item:hover .history-del { opacity: 1; }

/*  */
.chat-panel { flex: 1; background: #fff; display: flex; flex-direction: column; min-width: 0; }
.chat-header { padding: 16px 24px; border-bottom: 1px solid #f0f0f0; display: flex; align-items: center; gap: 12px; }
.header-title { font-size: 18px; font-weight: 700; color: #333; }
.header-desc { font-size: 13px; color: #999; }
.chat-messages { flex: 1; overflow-y: auto; padding: 24px; }
.chat-empty { text-align: center; padding: 60px 20px; }
.welcome-text { font-size: 16px; color: #333; margin-bottom: 24px; }

.intent-cards { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
.intent-card { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px 16px; border: 1px solid #e5e7eb; border-radius: 12px; cursor: pointer; width: 130px; transition: all 0.2s; }
.intent-card:hover { border-color: #667eea; background: #f5f3ff; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102,126,234,0.15); }
.intent-card span { font-size: 14px; font-weight: 600; color: #333; }
.intent-card small { font-size: 11px; color: #999; }

.msg { display: flex; gap: 10px; margin-bottom: 20px; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%; background: #f0f0f0; display: flex; align-items: center; justify-content: center; flex-shrink: 0; color: #666; }
.msg-bubble { max-width: 75%; }
.msg-intent { font-size: 11px; color: #667eea; margin-bottom: 4px; padding: 2px 8px; background: #f0f4ff; border-radius: 10px; display: inline-block; }
.msg-content { padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; word-break: break-word; }
.msg.user .msg-content { background: #15559a; color: #fff; border-radius: 12px 12px 2px 12px; }
.msg.assistant .msg-content { background: #f5f7fa; color: #333; border-radius: 12px 12px 12px 2px; }
.msg-time { font-size: 11px; color: #ccc; margin-top: 4px; }
.msg.user .msg-time { text-align: right; }

.doctor-quick { display: flex; gap: 8px; flex-wrap: wrap; margin: 10px 0; }
.doctor-quick-btn { padding: 8px 14px; background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; border-radius: 8px; cursor: pointer; font-size: 13px; transition: transform 0.2s; }
.doctor-quick-btn:hover { transform: scale(1.05); }

.chat-input { padding: 16px 24px; border-top: 1px solid #f0f0f0; }

.typing { display: flex; gap: 4px; padding: 8px 0; }
.typing span { width: 6px; height: 6px; border-radius: 50%; background: #999; animation: typing 1.4s infinite; }
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing { 0%,60%,100%{opacity:.3;transform:translateY(0)} 30%{opacity:1;transform:translateY(-4px)} }
</style>
