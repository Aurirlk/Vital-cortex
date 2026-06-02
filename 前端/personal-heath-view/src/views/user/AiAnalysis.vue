<template>
  <div class="ai-analysis-container">
    <div class="ai-header">
      <h2 class="ai-title">
        <el-icon><MagicStick /></el-icon>
        AI 健康分析
      </h2>
      <span class="ai-subtitle">选择智能体角色，获取专业的健康咨询建议</span>
    </div>

    <el-row :gutter="16">
      <!-- 左侧：角色选择 + 文件报告 -->
      <el-col :span="5">
        <div class="role-panel">
          <div class="panel-title">
            <el-icon><User /></el-icon>
            智能体角色
          </div>
          <div class="role-list">
            <div
              v-for="(role, key) in roles"
              :key="key"
              :class="['role-item', { 'role-active': currentRole === key }]"
              @click="switchRole(key)"
            >
              <span class="role-icon">{{ role.icon }}</span>
              <div class="role-info">
                <div class="role-name">{{ role.name }}</div>
                <div class="role-desc">{{ role.desc }}</div>
              </div>
            </div>
          </div>

          <!-- 文件上传 & 健康报告 -->
          <div class="panel-title" style="margin-top: 16px">
            <el-icon><Document /></el-icon>
            文件 & 报告
          </div>
          <div class="file-actions">
            <el-upload
              class="upload-btn"
              :action="uploadUrl"
              :show-file-list="false"
              :on-success="handleFileUpload"
              :headers="uploadHeaders"
              accept=".pdf,.doc,.docx,.txt,.jpg,.png"
            >
              <el-button size="small" type="primary" plain>
                <el-icon><Upload /></el-icon> 上传文件
              </el-button>
            </el-upload>
            <el-button size="small" type="success" plain @click="generateHealthReport">
              <el-icon><DataAnalysis /></el-icon> 生成报告
            </el-button>
          </div>
          <div v-if="uploadFiles.length > 0" class="file-list">
            <div v-for="(file, index) in uploadFiles" :key="index" class="file-item">
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ file.name || '文件' + (index + 1) }}</span>
              <el-button type="text" size="small" @click="removeFile(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 中间：聊天区域 -->
      <el-col :span="14">
        <div class="chat-panel">
          <div class="chat-header">
            <span class="current-role-badge">
              {{ roles[currentRole].icon }} {{ roles[currentRole].name }}
            </span>
            <span v-if="currentConversationId" class="conv-id-badge">
              会话 #{{ currentConversationId }}
            </span>
            <div>
              <el-button size="small" type="warning" plain @click="exportChat">
                <el-icon><Download /></el-icon> 导出
              </el-button>
              <el-button size="small" type="danger" plain @click="clearChat">
                <el-icon><Delete /></el-icon> 清空
              </el-button>
            </div>
          </div>

          <div class="chat-messages" ref="chatMessages">
            <div v-if="messages.length === 0" class="chat-empty">
              <div class="welcome-icon">{{ roles[currentRole].icon }}</div>
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
                  {{ roles[currentRole].icon }}
                </span>
              </div>
              <div class="message-content">
                <div class="message-role">
                  {{ msg.role === "user" ? "用户" : roles[currentRole].name }}
                </div>
                <div v-if="msg.toolCalls && msg.toolCalls.length" style="margin-bottom: 6px">
                  <span v-for="(tc, i) in msg.toolCalls" :key="i" class="tool-call-tag">
                    🔧 {{ tc.tool }}
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
                <span>{{ roles[currentRole].icon }}</span>
              </div>
              <div class="message-content">
                <div class="message-role">{{ roles[currentRole].name }}</div>
                <div class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          </div>

          <!-- 功能按钮区域 - 单行全称 -->
          <div class="feature-bar">
            <el-tooltip content="联网搜索获取最新信息" placement="top">
              <el-button 
                :type="enableWebSearch ? 'primary' : 'info'"
                size="small"
                round
                @click="enableWebSearch = !enableWebSearch"
              >
                <el-icon><Search /></el-icon> 联网搜索
              </el-button>
            </el-tooltip>
            <el-tooltip content="启用深度推理，回答更详细但耗时更长" placement="top">
              <el-button 
                :type="enableDeepThink ? 'warning' : 'info'"
                size="small"
                round
                @click="enableDeepThink = !enableDeepThink"
              >
                <el-icon><MagicStick /></el-icon> 深度思考
              </el-button>
            </el-tooltip>
            <el-tooltip content="参考平台健康知识库文章" placement="top">
              <el-button 
                :type="enableKnowledgeBase ? 'success' : 'info'"
                size="small"
                round
                @click="enableKnowledgeBase = !enableKnowledgeBase"
              >
                <el-icon><Collection /></el-icon> 知识库
              </el-button>
            </el-tooltip>
            <el-tooltip content="读取个人健康指标数据" placement="top">
              <el-button 
                :type="enableHealthData ? 'danger' : 'info'"
                size="small"
                round
                @click="enableHealthData = !enableHealthData"
              >
                <el-icon><FirstAidKit /></el-icon> 健康数据
              </el-button>
            </el-tooltip>
            <el-tooltip content="流式输出，逐字显示回答" placement="top">
              <el-button 
                :type="enableStream ? '' : 'info'"
                size="small"
                round
                @click="enableStream = !enableStream"
              >
                <el-icon><VideoPlay /></el-icon> 流式输出
              </el-button>
            </el-tooltip>
          </div>
          
          <!-- 输入区域 -->
          <div class="chat-input-area">
            <el-input
              class="chat-input"
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="描述您的症状、饮食需求或上传报告..."
              @keyup.ctrl.enter="sendMessage"
              :disabled="loading"
            ></el-input>
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
      </el-col>

      <!-- 右侧：历史会话 + 生成设置边栏 -->
      <el-col :span="5">
        <div class="settings-panel">
          <!-- 历史会话 -->
          <div class="panel-title">
            <el-icon><ChatLineRound /></el-icon>
            历史会话
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
              暂无会话
            </div>
            <div
              v-for="conv in recentConversations"
              :key="conv.id"
              :class="['recent-item', { 'recent-active': currentConversationId === conv.id }]"
              @click="loadConversation(conv)"
            >
              <span class="recent-icon">{{ roles[conv.agentType]?.icon || "🧠" }}</span>
              <span class="recent-title">{{ conv.title || '新会话' }}</span>
            </div>
          </div>
          
          <!-- 生成设置 -->
          <div class="panel-title" style="margin-top: 16px">
            <el-icon><Setting /></el-icon>
            生成设置
          </div>
          
          <!-- 生成模式 - 两列 -->
          <div class="mode-tags-grid">
            <span v-for="m in genModes" :key="m.key"
              :class="['mode-tag', { 'mode-active': genMode === m.key }]"
              @click="setGenMode(m.key)">{{ m.label }}</span>
          </div>
          
          <!-- Temperature -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">随机性</span>
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
          
          <!-- 重复惩罚 -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">重复惩罚</span>
              <span class="param-value">{{ repetitionPenalty }}</span>
            </div>
            <el-slider v-model="repetitionPenalty" :min="1" :max="2" :step="0.1" :show-tooltip="false" size="small" />
          </div>
          
          <!-- 上下文轮数 -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">上下文轮数</span>
              <span class="param-value">{{ contextRounds }}</span>
            </div>
            <el-slider v-model="contextRounds" :min="0" :max="20" :step="1" :show-tooltip="false" size="small" />
          </div>
          
          <!-- 最大回复长度 -->
          <div class="param-item">
            <div class="param-row">
              <span class="param-label">最大回复</span>
              <span class="param-value">{{ maxReplyLength === 0 ? '不限' : maxReplyLength }}</span>
            </div>
            <el-slider v-model="maxReplyLength" :min="0" :max="8192" :step="64" :show-tooltip="false" size="small" />
          </div>
        </div>
      </el-col>
    </el-row>
    
    <!-- 历史会话弹出框 -->
    <el-dialog v-model="showHistoryDialog" title="历史会话" width="600px">
      <div class="history-dialog-content">
        <div class="history-header">
          <el-input
            v-model="historySearchKey"
            placeholder="搜索会话标题..."
            clearable
            prefix-icon="Search"
          />
          <el-button type="primary" @click="newConversation">
            <el-icon><Plus /></el-icon> 新建会话
          </el-button>
        </div>
        <div class="history-list">
          <div v-if="filteredConversations.length === 0" class="no-history">
            {{ historySearchKey ? '没有找到匹配的会话' : '暂无历史会话' }}
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
              <span class="history-icon">{{ roles[conv.agentType]?.icon || "🧠" }}</span>
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
              <span>{{ conv.messageCount }}条消息</span>
              <span>{{ formatConvTime(conv.lastMessageTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 健康助手悬浮球 -->
    <FloatBall @click="openHealthAssistant" />
    
    <!-- 健康助手快捷对话框 -->
    <el-dialog v-model="showHealthAssistant" title="健康助手" width="500px" :append-to-body="true">
      <div class="health-assistant-container">
        <div class="health-assistant-messages" ref="healthMessages">
          <div v-if="healthMessages.length === 0" class="health-assistant-empty">
            <div class="health-assistant-icon">🩺</div>
            <p>您好！我是您的健康助手</p>
            <p class="health-assistant-tip">可以随时向我咨询健康问题</p>
          </div>
          <div
            v-for="(msg, index) in healthMessages"
            :key="index"
            :class="['health-msg', msg.role]"
          >
            <div class="health-msg-content" v-html="formatMessage(msg.content)"></div>
          </div>
          <div v-if="healthLoading" class="health-msg assistant">
            <div class="health-msg-content">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>
        <div class="health-assistant-input">
          <el-input
            v-model="healthInput"
            placeholder="输入您的健康问题..."
            @keyup.enter="sendHealthMessage"
            :disabled="healthLoading"
          >
            <template #append>
              <el-button @click="sendHealthMessage" :loading="healthLoading">
                <el-icon><Promotion /></el-icon>
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { getToken } from "@/utils/storage.js";
import { marked } from "marked";
import FloatBall from "@/components/FloatBall.vue";

// 配置 marked
marked.setOptions({
  breaks: true, // 支持换行符转换为 <br>
  gfm: true,    // 支持 GitHub Flavored Markdown
});

export default {
  name: "UserAiAnalysis",
  components: { FloatBall },
  data() {
    return {
      currentRole: "consultant",
      inputMessage: "",
      messages: [],
      loading: false,
      fileList: [],
      uploadFiles: [],
      // 健康助手悬浮球相关
      showHealthAssistant: false,
      healthMessages: [],
      healthInput: "",
      healthLoading: false,
      healthConversationId: null,
      currentConversationId: null,
      conversations: [],
      // 历史会话弹出框
      showHistoryDialog: false,
      historySearchKey: "",
      // 用户设置
      enableStream: true,
      enableWebSearch: false,
      enableKnowledgeBase: true,
      enableDeepThink: false,
      enableHealthData: true,
      // 文件上传
      uploadUrl: "http://localhost:21090/api/personal-health/v1.0/file/upload",
      uploadHeaders: {},
      // 高级参数
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
        { key: "precise", label: "精确模式", temp: 0.2, topP: 0.7 },
        { key: "balanced", label: "平衡模式", temp: 0.8, topP: 1.0 },
        { key: "creative", label: "创意模式", temp: 1.2, topP: 0.95 },
        { key: "custom", label: "自定义", temp: 0.8, topP: 1.0 },
      ],
      roles: {
        consultant: {
          name: "健康助手",
          icon: "\uD83D\uDCBC",
          desc: "智能健康咨询·综合服务",
          temp: 0.3,
          topP: 0.5,
          welcome: "您好！我是您的健康助手，可以帮您解答健康相关问题、查询药品信息、分析体检报告等。请问有什么可以帮您？",
          presets: ["我最近经常失眠怎么办？", "帮我解读一下体检报告", "高血压患者饮食要注意什么？", "推荐适合中老年人的运动方式"],
        },
        doctor: {
          name: "全科医生",
          icon: "\uD83E\uDE7A",
          desc: "症状分析与就医建议",
          temp: 0.2,
          topP: 0.3,
          welcome: "您好，我是全科医生AI助手。请描述您的症状或不适，我会为您提供初步分析和就医建议。请注意，AI建议仅供参考，不能替代专业医生的诊断。",
          presets: ["最近总是头疼，可能是什么原因？", "孩子发烧38.5度怎么处理？", "胸口偶尔闷痛需要就医吗？", "血压偏高该如何调理？"],
        },
        nutritionist: {
          name: "营养师",
          icon: "\uD83E\uDD57",
          desc: "膳食规划与营养指导",
          temp: 0.6,
          topP: 0.8,
          welcome: "您好！我是您的AI营养师。无论是日常膳食搭配、减脂增肌饮食，还是特殊人群（孕妇、糖尿病等）的营养方案，我都可以为您提供专业建议。",
          presets: ["减肥期间一天应该吃多少热量？", "糖尿病患者能吃水果吗？", "健身增肌的饮食计划", "孩子挑食不爱吃蔬菜怎么办？"],
        },
        psychologist: {
          name: "心理咨询",
          icon: "\uD83D\uDECB\uFE0F",
          desc: "情绪疏导与心理支持",
          temp: 0.8,
          topP: 0.9,
          welcome: "您好，这里是AI心理咨询室。我将为您提供一个安全、无评判的空间，倾听您的困扰并给予支持。请放心倾诉，我会认真倾听。",
          presets: ["最近工作压力很大，总是焦虑", "失眠严重，脑子里停不下来", "和家人关系紧张怎么办？", "感觉生活没有动力，情绪低落"],
        },
        analyst: {
          name: "报告分析",
          icon: "\uD83D\uDCCA",
          desc: "体检报告解读与分析",
          temp: 0.1,
          topP: 0.1,
          welcome: "您好，我是AI体检报告分析师。请上传或描述您的体检指标，我会为您逐项解读，标注异常值并给出健康建议。",
          presets: ["帮我分析血常规报告", "肝功能指标偏高意味着什么？", "血脂异常需要怎么调理？", "肿瘤标志物升高一定是癌症吗？"],
        },
        general_assistant: {
          name: "全能助手",
          icon: "\uD83E\uDDE0",
          desc: "综合健康咨询与科普",
          temp: 0.5,
          topP: 0.5,
          welcome: "您好！我是全能健康助手，可以回答各类健康知识、医疗常识、养生保健等问题。有什么想了解的，尽管问我！",
          presets: ["每天喝多少水合适？", "久坐办公室怎么缓解腰椎压力？", "不同季节养生有什么讲究？", "如何提高免疫力？"],
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
    // 初始化上传头
    const token = getToken();
    if (token) {
      this.uploadHeaders = { token: token };
    }
  },
  beforeUnmount() {
    // 终止进行中的 SSE 连接
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
    // 发送预置问题
    sendPreset(question) {
      this.inputMessage = question;
      this.sendMessage();
    },
    // 设置生成模式
    setGenMode(mode) {
      this.genMode = mode;
      const m = this.genModes.find(x => x.key === mode);
      if (m && mode !== "custom") {
        this.temperature = m.temp;
        this.topP = m.topP;
      }
    },
    // 加载会话列表
    async loadConversations() {
      try {
        const response = await this.$axios.get("/ai/conversations", {
          params: { agentType: this.currentRole },
        });
        const { data } = response;
        if (data.code === 200) {
          this.conversations = data.data || [];
        }
      } catch (e) {
        console.error("加载会话列表异常:", e);
      }
    },
    // 新建会话
    newConversation() {
      this.currentConversationId = null;
      this.messages = [];
    },
    // 加载历史会话
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
        console.error("加载会话消息异常:", e);
      }
    },
    // 删除会话
    async deleteConversation(convId) {
      try {
        await this.$confirm("确定删除该会话？", "提示", {
          confirmButtonText: "确定",
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
          console.error("删除会话异常:", e);
        }
      }
    },
    // 发送消息
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
    // AI 对话 - SSE 流式输出
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
          files: this.uploadFiles,
          userId: userInfo.id || null,
          context: {
            userName: userInfo.userName || "",
            requestHealthData: true,
          },
        };

        // 根据是否流式输出选择接口
        const apiUrl = this.enableStream
          ? "http://localhost:21090/api/personal-health/v1.0/ai/chat/stream"
          : "http://localhost:21090/api/personal-health/v1.0/ai/chat";

        if (this.enableStream) {
          // 流式输出
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
          // 非流式输出
          const response = await this.$axios.post("/ai/chat", requestBody);
          if (response.data.code === 200) {
            aiMsg.content = response.data.data.reply || "暂无回复";
            if (response.data.data.conversationId) {
              this.currentConversationId = parseInt(response.data.data.conversationId);
            }
          } else {
            aiMsg.content = "请求失败：" + (response.data.msg || "未知错误");
          }
        }
      } catch (e) {
        if (e.name !== "AbortError") {
          aiMsg.content += "\n\n网络异常，请检查后重试！";
          console.error("AI SSE 异常:", e);
        }
      }
      this.loading = false;
      this.scrollToBottom();
    },
    // 处理 AI SSE 事件
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
          aiMsg.content += "\n\n" + (data.message || "服务异常");
          break;
      }
    },
    // 清空当前对话
    clearChat() {
      this.currentConversationId = null;
      this.messages = [];
      this.uploadFiles = [];
      this.fileList = [];
    },
    // 导出对话
    exportChat() {
      if (this.messages.length === 0) {
        this.$message.warning("暂无对话记录");
        return;
      }
      const content = this.messages
        .map((m) => {
          const role =
            m.role === "user" ? "用户" : this.roles[this.currentRole].name;
          return `[${m.createTime || ""}] ${role}:\n${m.content}\n`;
        })
        .join("\n");
      const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `AI对话记录_${new Date().toISOString().slice(0, 10)}.txt`;
      a.click();
      URL.revokeObjectURL(url);
    },
    handleFileUpload(res, file) {
      if (res.code === 200) {
        this.uploadFiles.push({
          url: res.data,
          name: file.name
        });
        this.$message.success("文件上传成功");
      } else {
        this.$message.error("文件上传失败");
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

        // 发送生成健康报告请求
        const msg = "请根据我的健康数据生成一份详细的健康报告，包含健康指标分析、异常提示和改善建议。";
        
        const aiMsg = {
          role: "assistant",
          content: "",
          createTime: this.formatTime(new Date()),
        };
        this.messages.push(aiMsg);

        const requestBody = {
          conversationId: this.currentConversationId,
          message: msg,
          role: "analyst", // 使用报告分析角色
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

        const response = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/chat/stream", {
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
        this.$message.error("生成健康报告失败：" + e.message);
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
        // 使用 marked 渲染 Markdown
        return marked.parse(content);
      } catch (e) {
        // 降级处理：转义 HTML 并替换换行符
        const escaped = content
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/"/g, "&quot;")
          .replace(/'/g, "&#039;");
        return escaped.replace(/\n/g, "<br>");
      }
    },
    // 打开健康助手
    openHealthAssistant() {
      this.showHealthAssistant = true;
    },
    // 发送健康助手消息
    async sendHealthMessage() {
      const msg = this.healthInput.trim();
      if (!msg || this.healthLoading) return;

      this.healthMessages.push({
        role: "user",
        content: msg,
      });
      this.healthInput = "";
      this.healthLoading = true;

      this.$nextTick(() => {
        const container = this.$refs.healthMessages;
        if (container) container.scrollTop = container.scrollHeight;
      });

      try {
        const userInfo = JSON.parse(sessionStorage.getItem("userInfo") || "{}");
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;

        const aiMsg = {
          role: "assistant",
          content: "",
        };
        this.healthMessages.push(aiMsg);

        const response = await fetch(
          "http://localhost:21090/api/personal-health/v1.0/ai/chat/stream",
          {
            method: "POST",
            headers: headers,
            body: JSON.stringify({
              conversationId: this.healthConversationId,
              message: msg,
              role: "consultant",
              temperature: 0.3,
              topP: 0.5,
              enableWebSearch: false,
              enableKnowledgeBase: true,
              enableHealthData: true,
              userId: userInfo.id || null,
            }),
          }
        );

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
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
                if (currentEvent === "answer_chunk") {
                  aiMsg.content += data.content;
                } else if (currentEvent === "answer_done") {
                  if (data.conversationId) {
                    this.healthConversationId = parseInt(data.conversationId);
                  }
                }
              } catch (e) {
                // ignore
              }
            }
          }

          this.$nextTick(() => {
            const container = this.$refs.healthMessages;
            if (container) container.scrollTop = container.scrollHeight;
          });
        }
      } catch (e) {
        console.error("健康助手对话异常:", e);
        this.healthMessages.push({
          role: "assistant",
          content: "抱歉，服务暂时不可用，请稍后再试。",
        });
      } finally {
        this.healthLoading = false;
        this.$nextTick(() => {
          const container = this.$refs.healthMessages;
          if (container) container.scrollTop = container.scrollHeight;
        });
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

.role-icon {
  font-size: 28px;
  margin-right: 12px;
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

/* 会话列表样式 */
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

/* 聊天区域样式 */
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

  /* Markdown 样式 */
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

/* 欢迎信息 */
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

/* 预置问题 */
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

/* 生成模式标签 */
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

/* 参数行 */
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

/* 开关列表 */
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

/* 文件操作区域 */
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

/* 右侧设置边栏 */
.settings-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
  height: calc(100vh - 120px);
  min-height: 600px;
  overflow-y: auto;
}

/* 功能按钮栏 - 单行 */
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

/* 聊天输入区域 */
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

/* 右侧历史会话区域 */
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

/* 历史会话弹出框 */
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

/* 生成模式两列网格 */
.mode-tags-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-bottom: 12px;
}

/* 健康助手对话框 */
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
