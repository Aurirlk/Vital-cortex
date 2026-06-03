<template>
  <div class="assistant-page">
    <!-- 左侧历史记录 -->
    <div class="history-sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title">对话历史</span>
        <el-button size="small" type="primary" @click="newChat" :icon="Plus">新对话</el-button>
      </div>
      <div class="history-list">
        <div v-if="conversations.length === 0" class="no-history">暂无历史记录</div>
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['history-item', { active: currentConvId === conv.id }]"
          @click="switchConv(conv)"
        >
          <div class="history-title">{{ conv.title }}</div>
          <div class="history-meta">
            <span>{{ conv.msgCount }}条</span>
            <span>{{ conv.time }}</span>
          </div>
          <el-button class="history-del" size="small" type="danger" text @click.stop="delConv(conv.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- 右侧聊天区 -->
    <div class="chat-panel">
      <div class="chat-header">
        <span class="header-title">网站小助手</span>
        <span class="header-desc">意图识别 · 智能分流</span>
      </div>

      <div class="chat-messages" ref="chatMessages">
        <div v-if="messages.length === 0" class="chat-empty">
          <p class="welcome-text">您好！我是网站小助手，可以帮您：</p>
          <div class="intent-cards">
            <div class="intent-card" @click="quickAsk('search')">
              <el-icon :size="20"><Search /></el-icon>
              <span>病情查询</span>
              <small>联网搜索</small>
            </div>
            <div class="intent-card" @click="quickAsk('doctor')">
              <el-icon :size="20"><UserFilled /></el-icon>
              <span>医生推荐</span>
              <small>快速跳转</small>
            </div>
            <div class="intent-card" @click="quickAsk('drug')">
              <el-icon :size="20"><FirstAidKit /></el-icon>
              <span>药品介绍</span>
              <small>药品数据库</small>
            </div>
            <div class="intent-card" @click="quickAsk('knowledge')">
              <el-icon :size="20"><Collection /></el-icon>
              <span>健康知识</span>
              <small>知识库搜索</small>
            </div>
          </div>
          <p style="font-size:13px;color:#999;margin-top:16px;">或直接输入问题，系统会自动识别意图</p>
        </div>
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
          <div class="msg-avatar"><el-icon v-if="msg.role==='user'"><User /></el-icon><el-icon v-else><ChatDotRound /></el-icon></div>
          <div class="msg-bubble">
            <div v-if="msg.intent" class="msg-intent">识别为：{{ intentLabel(msg.intent) }}</div>
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
        <el-input v-model="input" placeholder="描述您的问题..." @keyup.enter="send" :disabled="loading" size="large">
          <template #append>
            <el-button @click="send" :loading="loading" type="primary">发送</el-button>
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
        search: ["发烧","头痛","咳嗽","胸闷","肚子痛","过敏","失眠","怎么治","症状","病因","检查"],
        drug: ["药","胶囊","片","颗粒","价格","功效","说明书","布洛芬","阿莫西林","感冒灵"],
        doctor: ["医生","挂号","看什么科","挂什么科","推荐医生","选哪个","选什么"],
        knowledge: ["知识","科普","预防","养生","饮食","运动","调理","保健","营养"],
      },
      // 医生关键词匹配
      doctorKeywords: {
        doctor: { keywords: ["发烧","咳嗽","感冒","头痛","头晕","胸闷","肚子","疼痛","腹泻","失眠","乏力","检查","诊断","吃什么","怎么缓解","多久","传染"], name: "全科医生", icon: "🩺", desc: "症状分析、用药指导" },
        nutritionist: { keywords: ["减肥","胖","瘦","营养","维生素","热量","卡路里","蛋白质","脂肪","碳水","糖尿病","血糖","降糖","食谱","增肌","减脂","饮食","吃什"], name: "营养师", icon: "🥗", desc: "膳食规划、营养指导" },
        psychologist: { keywords: ["心情","焦虑","抑郁","失眠","压力","烦躁","紧张","害怕","孤独","难过","崩溃","不开心","心理咨询","情绪","想不开","睡不着","精神","崩溃"], name: "心理咨询", icon: "🛋️", desc: "情绪疏导、心理支持" },
        analyst: { keywords: ["体检","报告","化验","指标","血常规","尿常规","肝功能","肾功能","血脂","尿酸","转氨酶","胆固醇","报告解读","指标偏高","指标偏低","异常"], name: "报告分析", icon: "📊", desc: "体检报告解读" },
        general_assistant: { keywords: ["养生","保健","预防","科普","知识","健康","季节","疫苗","免疫力"], name: "全能助手", icon: "🧠", desc: "综合健康咨询" },
      },
    };
  },
  created() {
    this.loadConversations();
  },
  methods: {
    // ===== 历史记录 =====
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
      const conv = { id: this.currentConvId, title: "新对话", msgCount: 0, time: this.now() };
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

    // ===== 对话功能 =====
    quickAsk(type) {
      const prompts = {
        search: "我发烧咳嗽应该怎么办？",
        doctor: "推荐合适的AI医生",
        drug: "布洛芬缓释胶囊的价格和功效",
        knowledge: "高血压日常饮食应该怎么调理？",
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
            aiMsg.content = `<strong>根据您的情况，推荐：${matched[0].name}</strong><br>${matched[0].desc}<br>点击下方按钮直接跳转 👇`;
          } else {
            aiMsg.content = `<strong>为您推荐 ${matched.length} 位最相关的AI医生：</strong><br>点击下方按钮直接跳转 👇`;
          }
        } else {
          const body = this.buildRequestBody(msg, intent);
          // 知识库查询先提取Dify关键词
          if (intent === "knowledge") {
            try {
              const kwRes = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/keywords/extract", {
                method: "POST", headers, body: JSON.stringify({ message: msg }),
              });
              const kwData = await kwRes.json();
              if (kwData.code === 200 && kwData.data?.length > 0) body.keywords = kwData.data;
            } catch (e) { console.warn("Dify提取失败:", e); }
          }
          const res = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/chat", {
            method: "POST", headers, body: JSON.stringify(body),
          });
          const data = await res.json();
          aiMsg.content = marked.parse(data.data?.reply || "暂无回复");
        }
      } catch (e) {
        aiMsg.content = "抱歉，服务异常：" + e.message;
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
      // 有匹配返回前3，无匹配兜底返回全科医生
      return matched.length > 0 ? matched.slice(0, 3) : [scored.find(d => d.key === "doctor")];
    },
    intentLabel(intent) {
      return { search: "病情查询（联网搜索）", drug: "药品查询", doctor: "医生推荐", knowledge: "健康知识" }[intent] || intent;
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

/* 左侧历史 */
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

/* 右侧聊天 */
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
