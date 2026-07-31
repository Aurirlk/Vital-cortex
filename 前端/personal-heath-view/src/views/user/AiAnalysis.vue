<template>
  <div class="ai-analysis-container">
    <div class="ai-header">
      <h2 class="ai-title">
        <el-icon><MagicStick /></el-icon>
        AI 
      </h2>
      <span class="ai-subtitle"></span>
    </div>

    <el-row :gutter="16">
      <!--  +  -->
      <el-col :span="5">
        <div class="role-panel">
          <div class="panel-title">
            <el-icon><User /></el-icon>
            
          </div>
          <div class="role-list">
            <div
              v-for="(role, key) in roles"
              :key="key"
              :class="['role-item', { 'role-active': currentRole === key }]"
              @click="switchRole(key)"
            >
              <el-icon class="role-icon-el" :size="20"><component :is="role.icon" /></el-icon>
              <div class="role-info">
                <div class="role-name">{{ role.name }}</div>
                <div class="role-desc">{{ role.desc }}</div>
              </div>
            </div>
          </div>

          <!--  &  -->
          <div class="panel-title" style="margin-top: 16px">
            <el-icon><Document /></el-icon>
             & 
          </div>
          <div class="file-actions">
            <!-- MM-04 整改：图片/文件附件入口临时禁用。
                 后端 DTO 的 files 字段全仓库零消费（根本没有视觉模型链路），
                 且前端 push 的对象结构与后端 List<String> 契约不匹配，
                 附带任意附件都会导致聊天请求 400。
                 在真正接入多模态（视觉/PDF解析）之前，保留上传按钮只会制造
                 必然失败的体验，故置灰并给出说明。 -->
            <el-tooltip content="多模态能力尚未接入，附件功能暂不可用" placement="top">
              <span>
                <el-button size="small" type="primary" plain disabled>
                  <el-icon><Upload /></el-icon> 
                </el-button>
              </span>
            </el-tooltip>
            <el-button size="small" type="success" plain @click="generateHealthReport">
              <el-icon><DataAnalysis /></el-icon> 
            </el-button>
          </div>
          <div v-if="uploadFiles.length > 0" class="file-list">
            <div v-for="(file, index) in uploadFiles" :key="index" class="file-item">
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ file.name || '' + (index + 1) }}</span>
              <el-button type="text" size="small" @click="removeFile(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </el-col>

      <!--  -->
      <el-col :span="14">
        <div class="chat-panel">
          <div class="chat-header">
            <span class="current-role-badge">
              <el-icon><component :is="roles[currentRole].icon" /></el-icon>
              {{ roles[currentRole].name }}
            </span>
            <span v-if="currentConversationId" class="conv-id-badge">
               #{{ currentConversationId }}
            </span>
            <div>
              <el-button size="small" type="warning" plain @click="exportChat">
                <el-icon><Download /></el-icon> 
              </el-button>
              <el-button size="small" type="danger" plain @click="clearChat">
                <el-icon><Delete /></el-icon> 
              </el-button>
            </div>
          </div>

          <div class="chat-messages" ref="chatMessages">
            <div v-if="messages.length === 0" class="chat-empty">
              <div class="welcome-icon">
                <el-icon :size="56"><component :is="roles[currentRole].icon" /></el-icon>
              </div>
              <p class="welcome-text">{{ roles[currentRole].welcome }}</p>
              <div class="preset-list">
                <div
                  v-for="(q, i) in roles[currentRole].presets"
                  :key="i"
                  class="preset-item"
                  @click="sendPreset(q)"
                >
                  <span class="preset-index">{{ i + 1 }}</span>
                  {{ q }}
                </div>
              </div>
            </div>
            <div
              v-for="(msg, index) in messages"
              :key="index"
              :class="[
                'message-item',
                msg.role === 'user' ? 'message-user' : 'message-ai',
              ]"
            >
              <div class="message-avatar">
                <span v-if="msg.role === 'user'">
                  <el-icon><User /></el-icon>
                </span>
                <span v-else>
                  <el-icon :size="18"><component :is="roles[currentRole].icon" /></el-icon>
                </span>
              </div>
              <div class="message-content">
                <div class="message-role">
                  {{ msg.role === "user" ? "" : roles[currentRole].name }}
                </div>
                <div v-if="msg.toolCalls && msg.toolCalls.length" style="margin-bottom: 6px">
                  <span v-for="(tc, i) in msg.toolCalls" :key="i" class="tool-call-tag">
                     {{ tc.tool }}
                  </span>
                </div>
                <div
                  class="message-text"
                  v-html="formatMessage(msg.content)"
                ></div>
                <div class="message-time">
                  {{ msg.createTime || formatTime(new Date()) }}
                </div>
              </div>
            </div>
            <div v-if="loading" class="message-item message-ai">
              <div class="message-avatar">
                <span><el-icon :size="18"><component :is="roles[currentRole].icon" /></el-icon></span>
              </div>
              <div class="message-content">
                <div class="message-role">{{ roles[currentRole].name }}</div>
                <div class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          </div>

          <!--  -  -->
          <div class="feature-bar">
            <el-tooltip content="" placement="top">
              <el-button 
                :type="enableWebSearch ? 'primary' : 'info'"
                size="small"
                round
                @click="enableWebSearch = !enableWebSearch"
              >
                <el-icon><Search /></el-icon> 
              </el-button>
            </el-tooltip>
            <el-tooltip content="" placement="top">
              <el-button 
                :type="enableDeepThink ? 'warning' : 'info'"
                size="small"
                round
                @click="enableDeepThink = !enableDeepThink"
              >
                <el-icon><MagicStick /></el-icon> 
              </el-button>
            </el-tooltip>
            <el-tooltip content="" placement="top">
              <el-button 
                :type="enableKnowledgeBase ? 'success' : 'info'"
                size="small"
                round
                @click="enableKnowledgeBase = !enableKnowledgeBase"
              >
                <el-icon><Collection /></el-icon> 
              </el-button>
            </el-tooltip>
            <el-tooltip content="" placement="top">
              <el-button 
                :type="enableHealthData ? 'danger' : 'info'"
                size="small"
                round
                @click="enableHealthData = !enableHealthData"
              >
                <el-icon><FirstAidKit /></el-icon> 
              </el-button>
            </el-tooltip>
            <el-tooltip content="" placement="top">
              <el-button 
                :type="enableStream ? '' : 'info'"
                size="small"
                round
                @click="enableStream = !enableStream"
              >
                <el-icon><VideoPlay /></el-icon> 
              </el-button>
            </el-tooltip>
          </div>
          
          <!--  -->
          <div class="chat-input-area">
            <el-input
              class="chat-input"
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="..."
              @keyup.ctrl.enter="sendMessage"
              :disabled="loading"
            ></el-input>
            <div class="input-actions">
              <el-tooltip content=" ()" placement="top">
                <el-button
                  class="voice-btn"
                  :type="isVoiceMode ? 'danger' : 'success'"
                  circle
                  @mousedown="startVoiceRecord"
                  @mouseup="stopVoiceRecord"
                  @mouseleave="cancelVoiceRecord"
                  :loading="isRecording"
                >
                  <el-icon><Microphone /></el-icon>
                </el-button>
              </el-tooltip>
              <el-button
                class="send-btn"
                type="primary"
                @click="sendMessage"
                :loading="loading"
                :disabled="!inputMessage.trim()"
              >
                <el-icon><Promotion /></el-icon> 
              </el-button>
            </div>
          </div>
        </div>
      </el-col>

      <!--  +  -->
      <el-col :span="5">
        <div class="settings-panel">
          <!--  -->
          <div class="panel-title">
            <el-icon><ChatLineRound /></el-icon>
            
            <el-button
              type="primary"
              link
              size="small"
              style="float: right"
              @click="showHistoryDialog = true"
            >
              
            </el-button>
          </div>
          <div class="recent-history">
            <div v-if="conversations.length === 0" class="no-recent">
              
            </div>
            <div
              v-for="conv in recentConversations"
              :key="conv.id"
              :class="['recent-item', { 'recent-active': currentConversationId === conv.id }]"
              @click="loadConversation(conv)"
            >
              <span class="recent-icon"><el-icon :size="16"><ChatDotRound /></el-icon></span>
              <span class="recent-title">{{ conv.title || '' }}</span>
            </div>
          </div>
          
          <!--  -->
          <div class="panel-title" style="margin-top: 16px">
            <el-icon><Setting /></el-icon>
            
          </div>
          
          <!--  -  -->
          <div class="mode-tags-grid">
            <span v-for="m in genModes" :key="m.key"
              :class="['mode-tag', { 'mode-active': genMode === m.key }]"
              @click="setGenMode(m.key)">{{ m.label }}</span>
          </div>
          
          <!-- Temperature -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label"></span>
              <span class="param-value">{{ temperature }}</span>
            </div>
            <el-slider v-model="temperature" :min="0" :max="2" :step="0.1" :show-tooltip="false" size="small" />
          </div>
          
          <!-- Top P -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">Top P</span>
              <span class="param-value">{{ topP }}</span>
            </div>
            <el-slider v-model="topP" :min="0" :max="1" :step="0.05" :show-tooltip="false" size="small" />
          </div>
          
          <!--  -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label"></span>
              <span class="param-value">{{ repetitionPenalty }}</span>
            </div>
            <el-slider v-model="repetitionPenalty" :min="1" :max="2" :step="0.1" :show-tooltip="false" size="small" />
          </div>
          
          <!--  -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label"></span>
              <span class="param-value">{{ contextRounds }}</span>
            </div>
            <el-slider v-model="contextRounds" :min="0" :max="20" :step="1" :show-tooltip="false" size="small" />
          </div>
          
          <!--  -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label"></span>
              <span class="param-value">{{ maxReplyLength === 0 ? '' : maxReplyLength }}</span>
            </div>
            <el-slider v-model="maxReplyLength" :min="0" :max="8192" :step="64" :show-tooltip="false" size="small" />
          </div>
        </div>
      </el-col>
    </el-row>
    
    <!--  -->
    <el-dialog v-model="showHistoryDialog" title="" width="600px">
      <div class="history-dialog-content">
        <div class="history-header">
          <el-input
            v-model="historySearchKey"
            placeholder="..."
            clearable
            prefix-icon="Search"
          />
          <el-button type="primary" @click="newConversation">
            <el-icon><Plus /></el-icon> 
          </el-button>
        </div>
        <div class="history-list">
          <div v-if="filteredConversations.length === 0" class="no-history">
            {{ historySearchKey ? '' : '' }}
          </div>
          <div
            v-for="conv in filteredConversations"
            :key="conv.id"
            :class="[
              'history-item',
              { 'history-active': currentConversationId === conv.id },
            ]"
            @click="loadConversation(conv); showHistoryDialog = false"
          >
            <div class="history-item-header">
              <span class="history-icon"><el-icon :size="20"><ChatDotRound /></el-icon></span>
              <span class="history-title">{{ conv.title }}</span>
              <el-button
                type="text"
                size="small"
                @click.stop="deleteConversation(conv.id)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div class="history-meta">
              <span>{{ conv.messageCount }}</span>
              <span>{{ formatConvTime(conv.lastMessageTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { getToken } from "@/utils/storage.js";
import { URL_API } from "@/utils/request.js";
import { marked } from "marked";

//  marked
marked.setOptions({
  breaks: true, //  <br>
  gfm: true,    //  GitHub Flavored Markdown
});

export default {
  name: "UserAiAnalysis",
  data() {
    return {
      currentRole: "consultant",
      inputMessage: "",
      messages: [],
      loading: false,
      fileList: [],
      uploadFiles: [],
      // 
      showHealthAssistant: false,
      healthMessages: [],
      healthInput: "",
      healthLoading: false,
      healthConversationId: null,
      currentConversationId: null,
      conversations: [],
      // 
      showHistoryDialog: false,
      historySearchKey: "",
      // 
      enableStream: false,
      enableWebSearch: false,
      enableKnowledgeBase: true,
      enableDeepThink: false,
      enableHealthData: true,
      // 
      isVoiceMode: false,
      isRecording: false,
      mediaRecorder: null,
      audioChunks: [],
      voiceCancelled: false,
      // 
      uploadUrl: URL_API + "/file/upload",
      uploadHeaders: {},
      // 
      genMode: "balanced",
      temperature: 0.8,
      topP: 1.0,
      repetitionPenalty: 1.1,
      contextRounds: 3,
      maxReplyLength: 0,
      maxReasoningLength: 4096,
      longMemory: false,
      fileBox: false,
      genModes: [
        { key: "precise", label: "", temp: 0.2, topP: 0.7 },
        { key: "balanced", label: "", temp: 0.8, topP: 1.0 },
        { key: "creative", label: "", temp: 1.2, topP: 0.95 },
        { key: "custom", label: "", temp: 0.8, topP: 1.0 },
      ],
      roles: {
        consultant: {
          name: "",
          icon: "Service",
          color: "#667eea",
          desc: "·",
          temp: 0.3,
          topP: 0.5,
          welcome: "",
          presets: ["", "", "", ""],
        },
        doctor: {
          name: "",
          icon: "FirstAidKit",
          color: "#e74c3c",
          desc: "",
          temp: 0.2,
          topP: 0.3,
          welcome: "AIAI",
          presets: ["", "38.5", "", ""],
        },
        nutritionist: {
          name: "",
          icon: "Apple",
          color: "#27ae60",
          desc: "",
          temp: 0.6,
          topP: 0.8,
          welcome: "AI",
          presets: ["", "", "", ""],
        },
        psychologist: {
          name: "",
          icon: "ChatDotRound",
          color: "#f39c12",
          desc: "",
          temp: 0.8,
          topP: 0.9,
          welcome: "AI",
          presets: ["", "", "", ""],
        },
        analyst: {
          name: "",
          icon: "DataAnalysis",
          color: "#3498db",
          desc: "",
          temp: 0.1,
          topP: 0.1,
          welcome: "AI",
          presets: ["", "", "", ""],
        },
        general_assistant: {
          name: "",
          icon: "MagicStick",
          color: "#8e44ad",
          desc: "",
          temp: 0.5,
          topP: 0.5,
          welcome: "",
          presets: ["", "", "", ""],
        },
      },
    };
  },
  computed: {
    recentConversations() {
      return this.conversations.slice(0, 3);
    },
    filteredConversations() {
      if (!this.historySearchKey) {
        return this.conversations;
      }
      const key = this.historySearchKey.toLowerCase();
      return this.conversations.filter(conv => 
        conv.title && conv.title.toLowerCase().includes(key)
      );
    },
  },
  created() {
    this.loadConversations();
    // 
    const token = getToken();
    if (token) {
      this.uploadHeaders = { token: token };
    }
  },
  beforeUnmount() {
    //  SSE 
    if (this._abortController) {
      this._abortController.abort();
    }
  },
  methods: {
    switchRole(role) {
      this.currentRole = role;
      const r = this.roles[role];
      this.temperature = r.temp;
      this.topP = r.topP;
      this.currentConversationId = null;
      this.messages = [];
      this.loadConversations();
    },

    // ====================  ====================
    async startVoiceRecord() {
      if (this.loading) return;
      
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        this.mediaRecorder = new MediaRecorder(stream);
        this.audioChunks = [];
        // MM-03 修复：取消标志位。原实现 cancel 时先清空 audioChunks 再 stop，
        // 而 stop() 是异步触发 onstop——清空发生在 onstop 执行之前，
        // 但 onstop 里仍然会用（已被清空的）audioChunks 组装 Blob 并上传，
        // 造成"用户点取消，整段医疗问诊录音仍被上传"。
        this.voiceCancelled = false;
        
        this.mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            this.audioChunks.push(event.data);
          }
        };
        
        this.mediaRecorder.onstop = async () => {
          // 取消时只释放麦克风，绝不把录音发出去
          stream.getTracks().forEach(track => track.stop());
          if (this.voiceCancelled) {
            this.audioChunks = [];
            return;
          }
          const audioBlob = new Blob(this.audioChunks, { type: 'audio/wav' });
          await this.sendVoiceToServer(audioBlob);
        };
        
        this.mediaRecorder.start();
        this.isRecording = true;
        this.$message.info('...');
      } catch (error) {
        console.error(':', error);
        this.$message.error('');
      }
    },

    stopVoiceRecord() {
      if (this.mediaRecorder && this.isRecording) {
        this.voiceCancelled = false;
        this.mediaRecorder.stop();
        this.isRecording = false;
        this.$message.info('...');
      }
    },

    cancelVoiceRecord() {
      if (this.mediaRecorder && this.isRecording) {
        // 先置取消标志，再触发 stop；onstop 中据此丢弃录音
        this.voiceCancelled = true;
        this.mediaRecorder.stop();
        this.isRecording = false;
        this.audioChunks = [];
        this.$message.info('');
      }
    },

    async sendVoiceToServer(audioBlob) {
      // MM-01 整改：后端不存在 /ai/voice/asr 端点（core/voice 为占位空壳），
      // 此前的录音上传必然 404。改为优先使用浏览器原生 Web Speech API 完成
      // 语音识别，零后端依赖；仅在浏览器不支持时提示降级。
      try {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) {
          this.$message.warning('当前浏览器不支持语音输入，请使用 Chrome / Edge');
          return;
        }
        const recognition = new SpeechRecognition();
        recognition.lang = 'zh-CN';
        recognition.interimResults = false;
        recognition.maxAlternatives = 1;

        recognition.onresult = (event) => {
          const text = event.results[0][0].transcript;
          if (text && text.trim()) {
            this.inputMessage = text;
            this.$message.success('已识别: ' + text);
            // 识别完成后自动发送，与原交互一致
            this.sendMessage();
          } else {
            this.$message.warning('未识别到有效内容，请重试');
          }
        };
        recognition.onerror = (event) => {
          console.error('语音识别失败:', event.error);
          if (event.error === 'not-allowed') {
            this.$message.error('麦克风权限被拒绝，请在浏览器设置中允许');
          } else if (event.error === 'no-speech') {
            this.$message.warning('未检测到语音，请重试');
          } else {
            this.$message.error('语音识别失败: ' + event.error);
          }
        };
        recognition.onend = () => {
          this.isRecording = false;
        };
        // 一次性识别，避免长按手势与识别时长互相干扰
        this.isRecording = true;
        recognition.start();
      } catch (error) {
        console.error('语音识别异常:', error);
        this.isRecording = false;
        this.$message.error('语音识别不可用');
      }
    },

    async playTtsAudio(text) {
      // MM-01 整改：后端 /ai/voice/tts 端点不存在，原实现必然 404。
      // 改用浏览器 SpeechSynthesis 朗读（Chrome/Edge/Safari 均支持，
      // 且无需上传任何音频数据到服务端）。
      try {
        if (!('speechSynthesis' in window)) {
          this.$message.warning('当前浏览器不支持语音朗读');
          return;
        }
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'zh-CN';
        utterance.rate = 1.0;
        window.speechSynthesis.speak(utterance);
      } catch (error) {
        console.error('TTS:', error);
        this.$message.error('语音朗读不可用');
      }
    },

    // 
    sendPreset(question) {
      this.inputMessage = question;
      this.sendMessage();
    },
    // 
    setGenMode(mode) {
      this.genMode = mode;
      const m = this.genModes.find(x => x.key === mode);
      if (m && mode !== "custom") {
        this.temperature = m.temp;
        this.topP = m.topP;
      }
    },
    // 
    async loadConversations() {
      try {
        //  agentType
        const response = await this.$axios.get("/ai/conversations");
        const { data } = response;
        if (data.code === 200) {
          this.conversations = data.data || [];
        }
      } catch (e) {
        console.error(":", e);
      }
    },
    // 
    newConversation() {
      this.currentConversationId = null;
      this.messages = [];
    },
    // 
    async loadConversation(conv) {
      this.currentConversationId = conv.id;
      this.currentRole = conv.agentType;
      this.temperature = this.roles[conv.agentType]?.temp || 0.5;
      this.topP = this.roles[conv.agentType]?.topP || 0.5;

      try {
        const response = await this.$axios.get(
          `/ai/conversations/${conv.id}/messages`
        );
        const { data } = response;
        if (data.code === 200) {
          this.messages = data.data || [];
          this.scrollToBottom();
        }
      } catch (e) {
        console.error(":", e);
      }
    },
    // 
    async deleteConversation(convId) {
      try {
        await this.$confirm("", "", {
          confirmButtonText: "",
          cancelButtonText: "",
          type: "warning",
        });
        const response = await this.$axios.delete(
          `/ai/conversations/${convId}`
        );
        const { data } = response;
        if (data.code === 200) {
          this.$message.success("");
          if (this.currentConversationId === convId) {
            this.newConversation();
          }
          this.loadConversations();
        }
      } catch (e) {
        if (e !== "cancel") {
          console.error(":", e);
        }
      }
    },
    // 
    async sendMessage() {
      const msg = this.inputMessage.trim();
      if (!msg || this.loading) return;

      this.messages.push({
        role: "user",
        content: msg,
        createTime: this.formatTime(new Date()),
      });
      this.inputMessage = "";
      this.loading = true;
      this.scrollToBottom();

      await this.sendAiMessage(msg);
    },
    // AI  - SSE 
    async sendAiMessage(msg) {
      const aiMsg = {
        role: "assistant",
        content: "",
        createTime: this.formatTime(new Date()),
      };
      this.messages.push(aiMsg);

      this._abortController = new AbortController();
      try {
        const userInfo = JSON.parse(sessionStorage.getItem("userInfo") || "{}");
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;

        // 
        let keywords = null;
        if (this.enableKnowledgeBase) {
          try {
            const kwRes = await this.$axios.post("/ai/keywords/extract", { message: msg });
            if (kwRes.data.code === 200 && kwRes.data.data && kwRes.data.data.length > 0) {
              keywords = kwRes.data.data;
              console.log("[Dify] :", keywords);
            }
          } catch (e) {
            console.warn("[Dify] :", e);
          }
        }

        const requestBody = {
          conversationId: this.currentConversationId,
          message: msg,
          role: this.currentRole,
          temperature: this.temperature,
          topP: this.topP,
          repetitionPenalty: this.repetitionPenalty,
          contextRounds: this.contextRounds,
          maxReplyLength: this.maxReplyLength,
          maxReasoningLength: this.maxReasoningLength,
          longMemory: this.longMemory,
          enableWebSearch: this.enableWebSearch,
          enableKnowledgeBase: this.enableKnowledgeBase,
          enableDeepThink: this.enableDeepThink,
          enableHealthData: this.enableHealthData,
          keywords: keywords,
          // MM-04 整改：后端不消费 files 字段，且类型不匹配会 400，一律不再发送
          files: [],
          userId: userInfo.id || null,
          context: {
            userName: userInfo.userName || "",
            requestHealthData: true,
          },
        };

        // 
        const apiUrl = this.enableStream
          ? URL_API + "/ai/chat/stream"
          : URL_API + "/ai/chat";

        if (this.enableStream) {
          // 
          const response = await fetch(apiUrl, {
            method: "POST",
            headers: headers,
            body: JSON.stringify(requestBody),
            signal: this._abortController.signal,
          });

          if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
          }

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });

            const lines = buffer.split("\n");
            buffer = lines.pop();

            let currentEvent = "";
            for (const line of lines) {
              if (line.startsWith("event: ")) {
                currentEvent = line.slice(7).trim();
              } else if (line.startsWith("data: ")) {
                const raw = line.slice(6);
                try {
                  const data = JSON.parse(raw);
                  this.handleAiSseEvent(currentEvent, data, aiMsg);
                } catch (e) { /* ignore */ }
              }
            }
            this.scrollToBottom();
          }
        } else {
          // 
          try {
            const response = await fetch(apiUrl, {
              method: "POST",
              headers: headers,
              body: JSON.stringify(requestBody),
              signal: this._abortController.signal,
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const result = await response.json();
            
            let reply = "";
            if (result.code === 200 && result.data) {
              reply = (result.data.reply || "")
                .replace(/<think>[\s\S]*?<\/think>/gi, "");
              if (result.data.conversationId) {
                this.currentConversationId = parseInt(result.data.conversationId);
              }
            } else {
              reply = "" + (result.msg || "");
            }
            const idx = this.messages.length - 1;
            this.messages.splice(idx, 1, { ...aiMsg, content: reply });
          } catch (e) {
            console.error("AI:", e);
            const idx = this.messages.length - 1;
            this.messages.splice(idx, 1, { ...aiMsg, content: "" + e.message });
          }
        }
      } catch (e) {
        if (e.name !== "AbortError") {
          aiMsg.content += "\n\n";
          console.error("AI SSE :", e);
        }
      }
      this.loading = false;
      this.scrollToBottom();
    },
    //  AI SSE 
    handleAiSseEvent(event, data, aiMsg) {
      switch (event) {
        case "answer_chunk":
          aiMsg.content += data.content;
          break;
        case "answer_done":
          if (data.conversationId) {
            this.currentConversationId = parseInt(data.conversationId);
          }
          this.loadConversations();
          break;
        case "error":
          aiMsg.content += "\n\n" + (data.message || "");
          break;
      }
    },
    // 
    clearChat() {
      this.currentConversationId = null;
      this.messages = [];
      this.uploadFiles = [];
      this.fileList = [];
    },
    // 
    exportChat() {
      if (this.messages.length === 0) {
        this.$message.warning("");
        return;
      }
      const content = this.messages
        .map((m) => {
          const role =
            m.role === "user" ? "" : this.roles[this.currentRole].name;
          return `[${m.createTime || ""}] ${role}:\n${m.content}\n`;
        })
        .join("\n");
      const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `AI_${new Date().toISOString().slice(0, 10)}.txt`;
      a.click();
      URL.revokeObjectURL(url);
    },
    handleFileUpload(res, file) {
      if (res.code === 200) {
        this.uploadFiles.push({
          url: res.data,
          name: file.name
        });
        this.$message.success("");
      } else {
        this.$message.error("");
      }
    },
    removeFile(index) {
      this.uploadFiles.splice(index, 1);
    },
    async generateHealthReport() {
      this.loading = true;
      try {
        const userInfo = JSON.parse(sessionStorage.getItem("userInfo") || "{}");
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;

        // 
        const msg = "";
        
        const aiMsg = {
          role: "assistant",
          content: "",
          createTime: this.formatTime(new Date()),
        };
        this.messages.push(aiMsg);

        const requestBody = {
          conversationId: this.currentConversationId,
          message: msg,
          role: "analyst", // 
          temperature: 0.1,
          topP: 0.1,
          enableWebSearch: false,
          enableKnowledgeBase: true,
          userId: userInfo.id || null,
          context: {
            userName: userInfo.userName || "",
            requestHealthData: true,
            generateReport: true,
          },
        };

        const response = await fetch(URL_API + "/ai/chat/stream", {
          method: "POST",
          headers: headers,
          body: JSON.stringify(requestBody),
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          const lines = buffer.split("\n");
          buffer = lines.pop();

          let currentEvent = "";
          for (const line of lines) {
            if (line.startsWith("event: ")) {
              currentEvent = line.slice(7).trim();
            } else if (line.startsWith("data: ")) {
              const raw = line.slice(6);
              try {
                const data = JSON.parse(raw);
                this.handleAiSseEvent(currentEvent, data, aiMsg);
              } catch (e) { /* ignore */ }
            }
          }
          this.scrollToBottom();
        }
      } catch (e) {
        this.$message.error("" + e.message);
      } finally {
        this.loading = false;
      }
    },
    handleFileRemove(file) {
      const index = this.uploadFiles.indexOf(file.url);
      if (index > -1) {
        this.uploadFiles.splice(index, 1);
      }
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.chatMessages;
        if (container) {
          container.scrollTop = container.scrollHeight;
        }
      });
    },
    formatTime(date) {
      const h = date.getHours().toString().padStart(2, "0");
      const m = date.getMinutes().toString().padStart(2, "0");
      return `${h}:${m}`;
    },
    formatConvTime(time) {
      if (!time) return "";
      return time.substring(5, 16).replace("T", " ");
    },
    formatMessage(content) {
      if (!content) return "";
      try {
        //  marked  Markdown
        return marked.parse(content);
      } catch (e) {
        //  HTML 
        const escaped = content
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/"/g, "&quot;")
          .replace(/'/g, "&#039;");
        return escaped.replace(/\n/g, "<br>");
      }
    },
  },
};
</script>
<style scoped lang="scss">
.ai-analysis-container {
  padding: 10px 20px;
}

.ai-header {
  margin-bottom: 20px;
}

.ai-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 5px 0;
  color: #333;

  i {
    color: #15559a;
  }
}

.ai-subtitle {
  font-size: 13px;
  color: #999;
}

.role-panel {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #f0f0f0;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;

  i {
    margin-right: 5px;
    color: #15559a;
  }
}

.role-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.role-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;

  &:hover {
    background-color: #f5f7fa;
  }
}

.role-active {
  background-color: #ecf5ff;
  border-color: #409eff;
}

.role-icon-el {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  margin-right: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.role-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.role-desc {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
}

.param-item {
  margin-bottom: 15px;
}

.param-label {
  font-size: 13px;
  color: #666;
  display: block;
  margin-bottom: 5px;
}

.upload-area {
  margin-top: 5px;
}

/*  */
.conversation-list {
  max-height: 300px;
  overflow-y: auto;
}

.no-conversation {
  text-align: center;
  color: #ccc;
  padding: 20px 0;
  font-size: 13px;
}

.conversation-item {
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid #f0f0f0;
  margin-bottom: 8px;
  position: relative;

  &:hover {
    background-color: #f5f7fa;
  }
}

.conversation-active {
  background-color: #ecf5ff;
  border-color: #409eff;
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 20px;
}

.conv-meta {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  display: flex;
  gap: 8px;
}

.conv-delete {
  position: absolute;
  right: 5px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0;
  transition: opacity 0.3s;
}

.conversation-item:hover .conv-delete {
  opacity: 1;
}

/*  */
.chat-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 600px;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.current-role-badge {
  font-size: 14px;
  font-weight: 600;
  color: #15559a;
  background: #ecf5ff;
  padding: 4px 12px;
  border-radius: 15px;
}

.conv-id-badge {
  font-size: 12px;
  color: #999;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: 10px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;

  p {
    margin: 10px 0 5px;
    font-size: 16px;
  }

  .quick-tips {
    font-size: 13px;
    color: #bbb;
  }
}

.message-item {
  display: flex;
  margin-bottom: 20px;

  &.message-user {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;
    }

    .message-text {
      background-color: #15559a;
      color: #fff;
      border-radius: 12px 12px 2px 12px;
    }
  }

  &.message-ai {
    .message-text {
      background-color: #f5f7fa;
      color: #333;
      border-radius: 12px 12px 12px 2px;
    }
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e8eaed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;

  span {
    line-height: 1;
  }
}

.message-content {
  display: flex;
  flex-direction: column;
  margin: 0 12px;
  max-width: 70%;
}

.message-role {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.message-text {
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;

  /* Markdown  */
  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    margin-top: 12px;
    margin-bottom: 8px;
    font-weight: 600;
    line-height: 1.4;
  }

  :deep(h1) { font-size: 1.5em; }
  :deep(h2) { font-size: 1.3em; }
  :deep(h3) { font-size: 1.1em; }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(ul), :deep(ol) {
    padding-left: 20px;
    margin: 8px 0;
  }

  :deep(li) {
    margin: 4px 0;
  }

  :deep(code) {
    background-color: rgba(0, 0, 0, 0.06);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Courier New', Courier, monospace;
    font-size: 0.9em;
  }

  :deep(pre) {
    background-color: #1e1e1e;
    color: #d4d4d4;
    padding: 12px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 8px 0;

    code {
      background: none;
      padding: 0;
      color: inherit;
    }
  }

  :deep(blockquote) {
    border-left: 4px solid #667eea;
    padding-left: 12px;
    margin: 8px 0;
    color: #666;
    background-color: #f9f9f9;
    padding: 8px 12px;
    border-radius: 0 4px 4px 0;
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 8px 0;
    width: 100%;
  }

  :deep(th), :deep(td) {
    border: 1px solid #ddd;
    padding: 8px;
    text-align: left;
  }

  :deep(th) {
    background-color: #f5f5f5;
    font-weight: 600;
  }

  :deep(tr:nth-child(even)) {
    background-color: #f9f9f9;
  }

  :deep(a) {
    color: #667eea;
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid #eee;
    margin: 12px 0;
  }

  :deep(strong) {
    font-weight: 600;
  }

  :deep(em) {
    font-style: italic;
  }
}

.message-time {
  font-size: 11px;
  color: #ccc;
  margin-top: 4px;
}

.typing-indicator {
  display: flex;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 12px;
  gap: 4px;

  span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #999;
    animation: typing 1.4s infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

@keyframes typing {
  0%,
  60%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

.chat-input-area {
  display: flex;
  align-items: flex-end;
  padding: 15px 20px;
  border-top: 1px solid #f0f0f0;
  gap: 10px;
}

.chat-input {
  flex: 1;

  :deep(.el-textarea__inner) {
    border-radius: 8px;
    resize: none;
    font-size: 14px;
  }
}

.send-btn {
  height: 52px;
  padding: 0 20px;
  border-radius: 8px;
  background-color: #15559a;
  border: none;
  font-size: 14px;
}

.tool-call-tag {
  display: inline-block;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #f0f9ff;
  color: #15559a;
  margin: 2px 4px 2px 0;
}

/*  */
.welcome-icon {
  font-size: 56px;
  margin-bottom: 12px;
}

.welcome-text {
  font-size: 15px;
  color: #4b5563;
  line-height: 1.7;
  max-width: 480px;
  margin: 0 auto 20px;
}

/*  */
.preset-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 480px;
  width: 100%;
}

.preset-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
  text-align: left;
  transition: all 0.2s ease;
}

.preset-item:hover {
  border-color: #667eea;
  background: #f5f3ff;
  color: #667eea;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.12);
}

.preset-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

/*  */
.mode-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.mode-tag {
  padding: 5px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid #e5e7eb;
  color: #6b7280;
  background: #fff;
  transition: all 0.2s;
}

.mode-tag:hover {
  border-color: #667eea;
  color: #667eea;
}

.mode-active {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-color: transparent;
}

/*  */
.param-item {
  margin-bottom: 12px;
}

.param-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.param-label {
  font-size: 13px;
  color: #6b7280;
}

.param-value {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  min-width: 36px;
  text-align: right;
}

/*  */
.toggle-list {
  margin-top: 14px;
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.toggle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;

  span {
    font-size: 13px;
    color: #374151;
  }
}

.toggle-desc {
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 8px;
}

/*  */
.file-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.upload-btn {
  display: inline-block;
}

.file-list {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  color: #666;
  
  .file-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  
  i {
    color: #667eea;
  }
}

/*  */
.settings-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
  height: calc(100vh - 120px);
  min-height: 600px;
  overflow-y: auto;
}

/*  -  */
.feature-bar {
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: nowrap;
  overflow-x: auto;
}

.feature-bar .el-button {
  transition: all 0.2s;
  flex-shrink: 0;
}

.feature-bar .el-button:hover {
  transform: translateY(-1px);
}

/*  */
.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid #f0f0f0;
}

.send-btn {
  width: 100%;
  height: 36px;
  border-radius: 8px;
  background-color: #15559a;
  border: none;
  font-size: 14px;
}

.input-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.voice-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  font-size: 18px;
  transition: all 0.3s ease;
}

.voice-btn:active {
  transform: scale(0.95);
}

/*  */
.recent-history {
  margin-bottom: 8px;
}

.no-recent {
  text-align: center;
  color: #ccc;
  padding: 12px 0;
  font-size: 12px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.recent-item:hover {
  background-color: #f5f7fa;
}

.recent-active {
  background-color: #ecf5ff;
}

.recent-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.recent-title {
  font-size: 12px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/*  */
.history-dialog-content {
  max-height: 500px;
}

.history-header {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.history-header .el-input {
  flex: 1;
}

.history-list {
  max-height: 400px;
  overflow-y: auto;
}

.no-history {
  text-align: center;
  color: #999;
  padding: 40px 0;
}

.history-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid #f0f0f0;
  margin-bottom: 8px;
}

.history-item:hover {
  background-color: #f5f7fa;
  border-color: #667eea;
}

.history-active {
  background-color: #ecf5ff;
  border-color: #409eff;
}

.history-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-icon {
  font-size: 20px;
}

.history-title {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}

/*  */
.mode-tags-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-bottom: 12px;
}

/*  */
.health-assistant-container {
  height: 450px;
  display: flex;
  flex-direction: column;
}

.health-assistant-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.health-assistant-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
}

.health-assistant-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.health-assistant-tip {
  font-size: 13px;
  color: #ccc;
  margin-top: 4px;
}

.health-msg {
  margin-bottom: 16px;
}

.health-msg.user {
  text-align: right;
}

.health-msg .health-msg-content {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 12px;
  max-width: 80%;
  font-size: 14px;
  line-height: 1.6;
  text-align: left;
}

.health-msg.user .health-msg-content {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 12px 12px 2px 12px;
}

.health-msg.assistant .health-msg-content {
  background: #f5f7fa;
  color: #333;
  border-radius: 12px 12px 12px 2px;
}

.health-assistant-input {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #999;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-4px);
  }
}
</style>
