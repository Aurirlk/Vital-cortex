<template>
  <div class="ai-analysis-container">
    <div class="ai-header">
      <h2 class="ai-title">
        <el-icon><MagicStick /></el-icon>
        AI 健康问诊
      </h2>
      <span class="ai-subtitle"
        >多角色智能健康助手，支持症状咨询、报告解读与营养建议</span
      >
    </div>

    <el-row :gutter="16">
      <!--  +  -->
      <el-col :span="5">
        <div class="role-panel">
          <div class="panel-title">
            <el-icon><User /></el-icon>
            选择问诊角色
          </div>
          <div class="role-list">
            <div
              v-for="(role, key) in roles"
              :key="key"
              :class="['role-item', { 'role-active': currentRole === key }]"
              @click="switchRole(key)"
            >
              <el-icon class="role-icon-el" :size="20"
                ><component :is="role.icon"
              /></el-icon>
              <div class="role-info">
                <div class="role-name">{{ role.name }}</div>
                <div class="role-desc">{{ role.desc }}</div>
              </div>
            </div>
          </div>

          <!--  &  -->
          <div class="panel-title" style="margin-top: 16px">
            <el-icon><Document /></el-icon>
            文件与健康报告
          </div>
          <div class="file-actions">
            <!-- Phase A（docs/multimodal-design.md §4）：图片多模态已接入——
                 后端 /ai/chat 的 files 字段已消费（OpenAI 兼容 image_url），
                 入口可用性由 /ai/config/capabilities 的 visionEnabled 控制：
                 管理端未配置多模态模型时保持置灰，避免 400/无效请求。 -->
            <el-tooltip
              :content="
                capabilities.visionEnabled
                  ? '上传图片（单次最多 ' +
                    capabilities.maxImages +
                    ' 张，支持体检单/化验单/症状照片）'
                  : '当前模型不支持图片，请在管理端「AI配置」选择多模态模型后使用'
              "
              placement="top"
            >
              <span>
                <el-upload
                  :show-file-list="false"
                  :action="uploadUrl"
                  :headers="uploadHeaders"
                  :on-success="handleFileUpload"
                  :before-upload="beforeImageUpload"
                  :disabled="
                    !capabilities.visionEnabled ||
                    uploadFiles.length >= capabilities.maxImages
                  "
                  accept="image/*"
                  style="display: inline-block"
                >
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    :disabled="
                      !capabilities.visionEnabled ||
                      uploadFiles.length >= capabilities.maxImages
                    "
                  >
                    <el-icon><Upload /></el-icon> 上传图片
                  </el-button>
                </el-upload>
              </span>
            </el-tooltip>
            <el-button
              size="small"
              type="success"
              plain
              @click="generateHealthReport"
            >
              <el-icon><DataAnalysis /></el-icon> 生成健康报告
            </el-button>
          </div>
          <div v-if="uploadFiles.length > 0" class="file-list">
            <div
              v-for="(file, index) in uploadFiles"
              :key="index"
              class="file-item"
            >
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ file.name || "" + (index + 1) }}</span>
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
              <el-icon><component :is="currentRoleConfig.icon" /></el-icon>
              {{ currentRoleConfig.name }}
            </span>
            <span v-if="currentConversationId" class="conv-id-badge">
              #{{ currentConversationId }}
            </span>
            <div>
              <el-button size="small" type="warning" plain @click="exportChat">
                <el-icon><Download /></el-icon> 导出对话
              </el-button>
              <el-button size="small" type="danger" plain @click="clearChat">
                <el-icon><Delete /></el-icon> 清空对话
              </el-button>
            </div>
          </div>

          <div class="chat-messages" ref="chatMessages">
            <div v-if="messages.length === 0" class="chat-empty">
              <div class="welcome-icon">
                <el-icon :size="56"
                  ><component :is="currentRoleConfig.icon"
                /></el-icon>
              </div>
              <p class="welcome-text">{{ currentRoleConfig.welcome }}</p>
              <div class="preset-list">
                <div
                  v-for="(q, i) in currentRoleConfig.presets"
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
                  <el-icon :size="18"
                    ><component :is="currentRoleConfig.icon"
                  /></el-icon>
                </span>
              </div>
              <div class="message-content">
                <div class="message-role">
                  {{ msg.role === "user" ? "我" : currentRoleConfig.name }}
                </div>
                <div
                  v-if="msg.toolCalls && msg.toolCalls.length"
                  style="margin-bottom: 6px"
                >
                  <span
                    v-for="(tc, i) in msg.toolCalls"
                    :key="i"
                    class="tool-call-tag"
                  >
                    {{ tc.tool }}
                  </span>
                </div>
                <div class="message-text" v-html="safeHtml(msg.content)"></div>
                <div class="message-time">
                  {{ msg.createTime || formatTime(new Date()) }}
                  <el-button
                    v-if="msg.role === 'ai'"
                    link
                    type="primary"
                    size="small"
                    :loading="speakingIndex === index"
                    @click="playTtsAudio(msg.content, index)"
                    style="padding: 0 4px"
                  >
                    <el-icon><Service /></el-icon>朗读
                  </el-button>
                </div>
              </div>
            </div>
            <div v-if="loading" class="message-item message-ai">
              <div class="message-avatar">
                <span
                  ><el-icon :size="18"
                    ><component :is="currentRoleConfig.icon" /></el-icon
                ></span>
              </div>
              <div class="message-content">
                <div class="message-role">{{ currentRoleConfig.name }}</div>
                <div class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          </div>

          <!--  功能开关 -  -->
          <div class="feature-bar">
            <el-tooltip
              content="联网搜索：开启后 AI 可检索最新健康资讯"
              placement="top"
            >
              <el-button
                :type="enableWebSearch ? 'primary' : 'info'"
                size="small"
                round
                @click="enableWebSearch = !enableWebSearch"
              >
                <el-icon><Search /></el-icon> 联网
              </el-button>
            </el-tooltip>
            <el-tooltip
              content="深度思考：让 AI 进行更严谨的多步推理"
              placement="top"
            >
              <el-button
                :type="enableDeepThink ? 'warning' : 'info'"
                size="small"
                round
                @click="enableDeepThink = !enableDeepThink"
              >
                <el-icon><MagicStick /></el-icon> 深度
              </el-button>
            </el-tooltip>
            <el-tooltip
              content="知识库：开启后优先引用平台健康资料"
              placement="top"
            >
              <el-button
                :type="enableKnowledgeBase ? 'success' : 'info'"
                size="small"
                round
                @click="enableKnowledgeBase = !enableKnowledgeBase"
              >
                <el-icon><Collection /></el-icon> 知识库
              </el-button>
            </el-tooltip>
            <el-tooltip
              content="健康档案：允许 AI 读取您的健康记录"
              placement="top"
            >
              <el-button
                :type="enableHealthData ? 'danger' : 'info'"
                size="small"
                round
                @click="enableHealthData = !enableHealthData"
              >
                <el-icon><FirstAidKit /></el-icon> 健康档案
              </el-button>
            </el-tooltip>
            <el-tooltip
              content="流式输出：开启后 AI 回复逐字显示"
              placement="top"
            >
              <el-button
                :type="enableStream ? '' : 'info'"
                size="small"
                round
                @click="enableStream = !enableStream"
              >
                <el-icon><VideoPlay /></el-icon> 流式
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
              placeholder="请输入您的健康问题，按 Ctrl+Enter 快速发送..."
              @keyup.ctrl.enter="sendMessage"
              :disabled="loading"
            ></el-input>
            <div class="input-actions">
              <el-tooltip content="按住说话（松开结束）" placement="top">
                <el-button
                  class="voice-btn"
                  :type="isVoiceMode ? 'danger' : 'success'"
                  circle
                  @pointerdown="startVoiceRecord"
                  @pointerup="stopVoiceRecord"
                  @pointercancel="cancelVoiceRecord"
                  @pointerleave="cancelVoiceRecord"
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
                <el-icon><Promotion /></el-icon> 发送
              </el-button>
            </div>
          </div>
        </div>
      </el-col>

      <!--  +  -->
      <el-col :span="5">
        <div class="settings-panel">
          <!--  最近对话 -->
          <div class="panel-title">
            <el-icon><ChatLineRound /></el-icon>
            最近对话
            <el-button
              type="primary"
              link
              size="small"
              style="float: right"
              @click="showHistoryDialog = true"
            >
              查看全部
            </el-button>
          </div>
          <div class="recent-history">
            <div v-if="conversations.length === 0" class="no-recent">
              暂无历史对话
            </div>
            <div
              v-for="conv in recentConversations"
              :key="conv.id"
              :class="[
                'recent-item',
                { 'recent-active': currentConversationId === conv.id },
              ]"
              @click="loadConversation(conv)"
            >
              <span class="recent-icon"
                ><el-icon :size="16"><ChatDotRound /></el-icon
              ></span>
              <span class="recent-title">{{ conv.title || "" }}</span>
            </div>
          </div>

          <!--  参数设置 -->
          <div class="panel-title" style="margin-top: 16px">
            <el-icon><Setting /></el-icon>
            生成参数
          </div>

          <!--  -  -->
          <div class="mode-tags-grid">
            <span
              v-for="m in genModes"
              :key="m.key"
              :class="['mode-tag', { 'mode-active': genMode === m.key }]"
              @click="setGenMode(m.key)"
              >{{ m.label }}</span
            >
          </div>

          <!-- Temperature -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">Temperature（创造性）</span>
              <span class="param-value">{{ temperature }}</span>
            </div>
            <el-slider
              v-model="temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :show-tooltip="false"
              size="small"
            />
          </div>

          <!-- Top P -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">Top P</span>
              <span class="param-value">{{ topP }}</span>
            </div>
            <el-slider
              v-model="topP"
              :min="0"
              :max="1"
              :step="0.05"
              :show-tooltip="false"
              size="small"
            />
          </div>

          <!-- 重复惩罚 -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">重复惩罚</span>
              <span class="param-value">{{ repetitionPenalty }}</span>
            </div>
            <el-slider
              v-model="repetitionPenalty"
              :min="1"
              :max="2"
              :step="0.1"
              :show-tooltip="false"
              size="small"
            />
          </div>

          <!-- 上下文轮数 -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">上下文轮数</span>
              <span class="param-value">{{ contextRounds }}</span>
            </div>
            <el-slider
              v-model="contextRounds"
              :min="0"
              :max="20"
              :step="1"
              :show-tooltip="false"
              size="small"
            />
          </div>

          <!-- 最大回复长度 -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">最大回复长度（0 为不限制）</span>
              <span class="param-value">{{
                maxReplyLength === 0 ? "不限制" : maxReplyLength
              }}</span>
            </div>
            <el-slider
              v-model="maxReplyLength"
              :min="0"
              :max="8192"
              :step="64"
              :show-tooltip="false"
              size="small"
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <!--  历史对话弹窗 -->
    <el-dialog v-model="showHistoryDialog" title="历史对话" width="600px">
      <div class="history-dialog-content">
        <div class="history-header">
          <el-input
            v-model="historySearchKey"
            placeholder="搜索历史对话..."
            clearable
            prefix-icon="Search"
          />
          <el-button type="primary" @click="newConversation">
            <el-icon><Plus /></el-icon> 新建对话
          </el-button>
        </div>
        <div class="history-list">
          <div v-if="filteredConversations.length === 0" class="no-history">
            {{ historySearchKey ? "未找到匹配对话" : "暂无历史对话" }}
          </div>
          <div
            v-for="conv in filteredConversations"
            :key="conv.id"
            :class="[
              'history-item',
              { 'history-active': currentConversationId === conv.id },
            ]"
            @click="
              loadConversation(conv);
              showHistoryDialog = false;
            "
          >
            <div class="history-item-header">
              <span class="history-icon"
                ><el-icon :size="20"><ChatDotRound /></el-icon
              ></span>
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
import { sanitizeHtml } from "@/utils/sanitize.js";
import { URL_API } from "@/utils/request.js";
import { marked } from "marked";

//  marked
marked.setOptions({
  breaks: true, //  <br>
  gfm: true, //  GitHub Flavored Markdown
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
      uploadFiles: [], //
      // Phase A：AI 能力开关（/ai/config/capabilities，图片多模态；语音待 roadmap §1.1）
      capabilities: {
        visionEnabled: false,
        visionModel: "",
        maxContext: 131072,
        maxImages: 3,
        asrEnabled: false,
        ttsEnabled: false,
      },
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
      speakingIndex: null,
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
        { key: "precise", label: "精准", temp: 0.2, topP: 0.7 },
        { key: "balanced", label: "均衡", temp: 0.8, topP: 1.0 },
        { key: "creative", label: "创意", temp: 1.2, topP: 0.95 },
        { key: "custom", label: "自定义", temp: 0.8, topP: 1.0 },
      ],
      roles: {
        consultant: {
          name: "健康顾问",
          icon: "Service",
          color: "#667eea",
          desc: "日常健康问题咨询",
          temp: 0.3,
          topP: 0.5,
          welcome: "您好，我是您的健康顾问，有什么可以帮您？",
          presets: [
            "我最近总是失眠，怎么办？",
            "帮我分析一下体检报告",
            "高血压患者日常注意事项",
            "每天应该喝多少水",
          ],
        },
        doctor: {
          name: "AI 医生",
          icon: "FirstAidKit",
          color: "#e74c3c",
          desc: "症状分析与就医建议",
          temp: 0.2,
          topP: 0.3,
          welcome: "请描述您的症状，我会帮您分析并给出就医建议。",
          presets: [
            "发烧 38.5℃ 需要去医院吗？",
            "咳嗽一周不好，什么原因",
            "头疼伴随恶心是怎么回事",
            "皮肤过敏起红疹怎么处理",
          ],
        },
        nutritionist: {
          name: "营养师",
          icon: "Apple",
          color: "#27ae60",
          desc: "饮食搭配与营养建议",
          temp: 0.6,
          topP: 0.8,
          welcome: "我是您的营养顾问，请告诉我您的饮食目标和偏好。",
          presets: [
            "帮我制定一周减脂餐",
            "糖尿病患者应该怎么吃",
            "早餐吃什么比较有营养",
            "运动后怎么补充蛋白质",
          ],
        },
        psychologist: {
          name: "心理顾问",
          icon: "ChatDotRound",
          color: "#f39c12",
          desc: "情绪疏导与心理支持",
          temp: 0.8,
          topP: 0.9,
          welcome: "我是您的心理顾问，愿意倾听您的烦恼。",
          presets: [
            "最近压力很大，怎么缓解",
            "焦虑睡不着怎么办",
            "如何改善人际关系",
            "情绪低落时怎么自我调节",
          ],
        },
        analyst: {
          name: "报告分析师",
          icon: "DataAnalysis",
          color: "#3498db",
          desc: "体检报告与健康数据解读",
          temp: 0.1,
          topP: 0.1,
          welcome: "请上传体检单或化验单，我会帮您解读关键指标。",
          presets: [
            "帮我看看这份血常规报告",
            "肝功能指标偏高说明什么",
            "血脂报告怎么解读",
            "尿酸高要注意什么",
          ],
        },
        general_assistant: {
          name: "通用助手",
          icon: "MagicStick",
          color: "#8e44ad",
          desc: "不限主题的智能问答",
          temp: 0.5,
          topP: 0.5,
          welcome: "有什么我可以帮您的吗？",
          presets: [
            "介绍一下这个平台的功能",
            "如何记录健康数据",
            "怎么预约医生",
            "平台的 AI 模型有哪些",
          ],
        },
      },
    };
  },
  computed: {
    safeHtml(html) {
      return sanitizeHtml(html);
    },

    // 当前角色配置的安全访问：历史会话的 agentType 可能不在 roles 中，避免渲染崩溃
    currentRoleConfig() {
      return (
        this.roles[this.currentRole] ||
        this.roles.consultant ||
        this.roles.doctor ||
        {}
      );
    },

    recentConversations() {
      return this.conversations.slice(0, 3);
    },
    filteredConversations() {
      if (!this.historySearchKey) {
        return this.conversations;
      }
      const key = this.historySearchKey.toLowerCase();
      return this.conversations.filter(
        (conv) => conv.title && conv.title.toLowerCase().includes(key)
      );
    },
  },
  created() {
    this.loadConversations();
    // Phase A：拉取 AI 能力开关（visionEnabled 决定图片入口是否可用）
    this.loadCapabilities();
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

    // ==================== 语音输入（后端 ASR） ====================
    // 路线 A：浏览器采集 PCM → 编码为 WAV → POST /voice/asr（DashScope Paraformer）。
    // 不采用 MediaRecorder 默认 webm/opus（DashScope 不接受），改用 Web Audio 原生采集，
    // 兼容性更好且格式确定。
    async startVoiceRecord() {
      if (this.loading || this.isRecording) return;
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        this.$message.warning("当前浏览器不支持录音，请使用 Chrome / Edge");
        return;
      }
      try {
        this.voiceCancelled = false;
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        this._mediaStream = stream;
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        const ctx = new AudioCtx();
        this._audioCtx = ctx;
        const source = ctx.createMediaStreamSource(stream);
        const processor = ctx.createScriptProcessor(4096, 1, 1);
        const gain = ctx.createGain();
        gain.gain.value = 0; // 静音监听，避免回声
        this._audioBuffers = [];
        processor.onaudioprocess = (e) => {
          if (this.voiceCancelled) return;
          const ch = e.inputBuffer.getChannelData(0);
          this._audioBuffers.push(new Float32Array(ch));
        };
        source.connect(processor);
        processor.connect(gain);
        gain.connect(ctx.destination);
        this._audioSource = source;
        this._audioProcessor = processor;
        this.isRecording = true;
        this.$message.info("请说话，松开结束");
      } catch (e) {
        this.isRecording = false;
        if (e && e.name === "NotAllowedError") {
          this.$message.error("麦克风权限被拒绝，请在浏览器地址栏允许使用麦克风");
        } else {
          this.$message.error("录音启动失败");
        }
        this._cleanupAudio();
      }
    },

    async stopVoiceRecord() {
      if (!this.isRecording) return;
      this.isRecording = false;
      const buffers = this._audioBuffers || [];
      const ctx = this._audioCtx;
      const sampleRate = ctx ? ctx.sampleRate : 16000;
      this._cleanupAudio();
      if (this.voiceCancelled || !buffers.length) return;

      // 拼接 PCM
      let total = 0;
      buffers.forEach((b) => (total += b.length));
      const samples = new Float32Array(total);
      let offset = 0;
      buffers.forEach((b) => {
        samples.set(b, offset);
        offset += b.length;
      });
      const wavBlob = this._encodeWav(samples, sampleRate);

      try {
        const form = new FormData();
        form.append("file", wavBlob, "recording.wav");
        this.$message.info("识别中...");
        const res = await this.$axios.post("/voice/asr", form);
        if (res.data && res.data.code === 200 && res.data.data) {
          const text = res.data.data;
          if (text && text.trim()) {
            this.inputMessage = text;
            this.$message.success("已识别: " + text);
            this.sendMessage();
          } else {
            this.$message.warning("未识别到有效内容，请重试");
          }
        } else {
          this.$message.error((res.data && res.data.msg) || "语音识别失败");
        }
      } catch (e) {
        console.error("ASR:", e);
        this.$message.error("语音识别请求失败");
      }
    },

    cancelVoiceRecord() {
      if (this.isRecording) {
        this.voiceCancelled = true;
        this.isRecording = false;
        this._cleanupAudio();
      }
    },

    _cleanupAudio() {
      if (this._audioProcessor) {
        try { this._audioProcessor.disconnect(); } catch (e) { /* ignore */ }
      }
      if (this._audioSource) {
        try { this._audioSource.disconnect(); } catch (e) { /* ignore */ }
      }
      if (this._audioCtx) {
        try { this._audioCtx.close(); } catch (e) { /* ignore */ }
      }
      if (this._mediaStream) {
        this._mediaStream.getTracks().forEach((t) => t.stop());
      }
      this._audioProcessor = null;
      this._audioSource = null;
      this._audioCtx = null;
      this._mediaStream = null;
      this._audioBuffers = null;
    },

    // 将 Float32 PCM 编码为 16-bit 单声道 WAV Blob
    _encodeWav(samples, sampleRate) {
      const dataSize = samples.length * 2;
      const buffer = new ArrayBuffer(44 + dataSize);
      const view = new DataView(buffer);
      const writeStr = (off, s) => {
        for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i));
      };
      writeStr(0, "RIFF");
      view.setUint32(4, 36 + dataSize, true);
      writeStr(8, "WAVE");
      writeStr(12, "fmt ");
      view.setUint32(16, 16, true);
      view.setUint16(20, 1, true); // PCM
      view.setUint16(22, 1, true); // 单声道
      view.setUint32(24, sampleRate, true);
      view.setUint32(28, sampleRate * 2, true);
      view.setUint16(32, 2, true);
      view.setUint16(34, 16, true); // 16-bit
      writeStr(36, "data");
      view.setUint32(40, dataSize, true);
      let off = 44;
      for (let i = 0; i < samples.length; i++) {
        let s = Math.max(-1, Math.min(1, samples[i]));
        view.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true);
        off += 2;
      }
      return new Blob([view], { type: "audio/wav" });
    },

    // ==================== 语音输出（后端 TTS） ====================
    // 路线 A：调用后端 /voice/tts 合成 mp3 并播放（Edge TTS，音质优于浏览器原生）。
    async playTtsAudio(text, index) {
      if (!text || !text.trim()) return;
      try {
        this.speakingIndex = index !== undefined ? index : null;
        const form = new URLSearchParams();
        form.append("text", text);
        const res = await this.$axios.post("/voice/tts", form, {
          responseType: "blob",
        });
        if (res.status !== 200) {
          let msg = "语音合成失败";
          try {
            msg = (await res.data.text()) || msg;
          } catch (e) {
            /* ignore */
          }
          throw new Error(msg);
        }
        const blob = res.data;
        const url = URL.createObjectURL(blob);
        const audio = new Audio(url);
        audio.onended = () => {
          URL.revokeObjectURL(url);
          this.speakingIndex = null;
        };
        audio.onerror = () => {
          URL.revokeObjectURL(url);
          this.speakingIndex = null;
          this.$message.error("语音播放失败");
        };
        await audio.play();
      } catch (error) {
        this.speakingIndex = null;
        console.error("TTS:", error);
        this.$message.error(
          "语音朗读不可用：" + (error && error.message ? error.message : "")
        );
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
      const m = this.genModes.find((x) => x.key === mode);
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
      if (!conv || !conv.id) return;
      this.currentConversationId = conv.id;
      // 历史会话的 agentType 可能为 null 或不在前端 roles 中，做安全回退
      this.currentRole = this.roles[conv.agentType] ? conv.agentType : "consultant";
      // 从该角色的后端配置拉取最新参数（temperature / topP / maxRounds / 惩罚系数等），
      // 让右侧"生成参数"面板同步刷新；如果接口失败则用前端兜底。
      try {
        const cfgRes = await this.$axios.get(`/ai/config/${this.currentRole}`);
        if (cfgRes.data.code === 200 && cfgRes.data.data) {
          const cfg = cfgRes.data.data;
          if (cfg.temperature != null) this.temperature = Number(cfg.temperature);
          if (cfg.topP != null) this.topP = Number(cfg.topP);
          if (cfg.repetitionPenalty != null)
            this.repetitionPenalty = Number(cfg.repetitionPenalty);
          if (cfg.contextRounds != null)
            this.contextRounds = Number(cfg.contextRounds);
          if (cfg.maxReplyLength != null)
            this.maxReplyLength = Number(cfg.maxReplyLength);
        }
      } catch (e) {
        this.temperature = this.roles[this.currentRole]?.temp || 0.5;
        this.topP = this.roles[this.currentRole]?.topP || 0.5;
      }

      try {
        const response = await this.$axios.get(
          `/ai/conversations/${conv.id}/messages`
        );
        const { data } = response;
        if (data.code === 200) {
          this.messages = data.data || [];
          this.$nextTick(() => this.scrollToBottom());
        } else {
          this.$message.warning(data.msg || "加载消息失败");
        }
      } catch (e) {
        console.error("加载历史对话失败:", e);
        this.$message.error("加载历史对话失败");
      }
    },
    //
    async deleteConversation(convId) {
      try {
        await this.$confirm("删除后不可恢复，是否继续？", "删除对话", {
          confirmButtonText: "删除",
          cancelButtonText: "取消",
          type: "warning",
        });
        const response = await this.$axios.delete(
          `/ai/conversations/${convId}`
        );
        const { data } = response;
        if (data.code === 200) {
          this.$message.success("删除成功");
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
            const kwRes = await this.$axios.post("/ai/keywords/extract", {
              message: msg,
            });
            if (
              kwRes.data.code === 200 &&
              kwRes.data.data &&
              kwRes.data.data.length > 0
            ) {
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
          // Phase A：后端已消费 files（多模态 image_url），传已上传图片的 capability URL
          files: this.uploadFiles.map((f) => f.url),
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
                } catch (e) {
                  /* ignore */
                }
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
              reply = (result.data.reply || "").replace(
                /<think>[\s\S]*?<\/think>/gi,
                ""
              );
              if (result.data.conversationId) {
                this.currentConversationId = parseInt(
                  result.data.conversationId
                );
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
            m.role === "user" ? "" : this.currentRoleConfig.name;
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
    // Phase A：拉取 AI 能力开关（visionEnabled 决定图片入口；语音开关留待 roadmap §1.1）
    async loadCapabilities() {
      try {
        const { data } = await this.$axios.get("/ai/config/capabilities");
        if (data.code === 200 && data.data) {
          this.capabilities = { ...this.capabilities, ...data.data };
        }
      } catch (e) {
        console.warn("[Capabilities] 获取能力开关失败:", e);
      }
    },
    // Phase A：上传前校验（仅图片、≤5MB、不超过单次上限）
    beforeImageUpload(file) {
      const isImage = file.type && file.type.startsWith("image/");
      if (!isImage) {
        this.$message.error("仅支持上传图片");
        return false;
      }
      if (file.size > 5 * 1024 * 1024) {
        this.$message.error("图片大小不能超过 5MB");
        return false;
      }
      if (this.uploadFiles.length >= this.capabilities.maxImages) {
        this.$message.warning(
          "单次最多上传 " + this.capabilities.maxImages + " 张图片"
        );
        return false;
      }
      return true;
    },
    handleFileUpload(res, file) {
      if (res.code === 200) {
        this.uploadFiles.push({
          url: res.data,
          name: file.name,
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

        // MM-24 整改：报告流复用同一 abortController，组件卸载时一并中断
        this._abortController = new AbortController();
        const response = await fetch(URL_API + "/ai/chat/stream", {
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
              } catch (e) {
                /* ignore */
              }
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
  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin-top: 12px;
    margin-bottom: 8px;
    font-weight: 600;
    line-height: 1.4;
  }

  :deep(h1) {
    font-size: 1.5em;
  }
  :deep(h2) {
    font-size: 1.3em;
  }
  :deep(h3) {
    font-size: 1.1em;
  }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(ul),
  :deep(ol) {
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
    font-family: "Courier New", Courier, monospace;
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

  :deep(th),
  :deep(td) {
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
</style>
