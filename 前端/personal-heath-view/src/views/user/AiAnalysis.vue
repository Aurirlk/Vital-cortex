<template>
  <div class="ai-analysis-container">
    <div class="ai-header">
      <h2 class="ai-title">
        <el-icon><MagicStick /></el-icon>
        AI 健康分析
      </h2>
      <span class="ai-subtitle">选择智能体角色，获取专业的健康咨询建议</span>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：角色选择 + 历史会话 -->
      <el-col :span="6">
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

          <div class="panel-title" style="margin-top: 20px">
            <el-icon><Setting /></el-icon>
            生成设置
          </div>
          <template v-if="!roles[currentRole].isCrm">
            <!-- 生成模式 -->
            <div class="mode-tags">
              <span v-for="m in genModes" :key="m.key"
                :class="['mode-tag', { 'mode-active': genMode === m.key }]"
                @click="setGenMode(m.key)">{{ m.label }}</span>
            </div>
            <!-- Temperature -->
            <div class="param-item">
              <div class="param-row">
                <span class="param-label">生成随机性</span>
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
                <span class="param-label">重复语句惩罚</span>
                <span class="param-value">{{ repetitionPenalty }}</span>
              </div>
              <el-slider v-model="repetitionPenalty" :min="1" :max="2" :step="0.1" :show-tooltip="false" size="small" />
            </div>
            <!-- 上下文轮数 -->
            <div class="param-item">
              <div class="param-row">
                <span class="param-label">携带上下文轮数</span>
                <span class="param-value">{{ contextRounds }}</span>
              </div>
              <el-slider v-model="contextRounds" :min="0" :max="20" :step="1" :show-tooltip="false" size="small" />
            </div>
            <!-- 最大回复长度 -->
            <div class="param-item">
              <div class="param-row">
                <span class="param-label">最大回复长度</span>
                <span class="param-value">{{ maxReplyLength === 0 ? '不限' : maxReplyLength }}</span>
              </div>
              <el-slider v-model="maxReplyLength" :min="0" :max="8192" :step="64" :show-tooltip="false" size="small" />
            </div>
            <!-- 最大推理长度 -->
            <div class="param-item">
              <div class="param-row">
                <span class="param-label">最大推理&回答长度</span>
                <span class="param-value">{{ maxReasoningLength }}</span>
              </div>
              <el-slider v-model="maxReasoningLength" :min="512" :max="16384" :step="256" :show-tooltip="false" size="small" />
            </div>
            <!-- 开关项 -->
            <div class="toggle-list">
              <div class="toggle-item">
                <span>长期记忆</span>
                <el-switch v-model="longMemory" active-color="#667eea" />
              </div>
              <div class="toggle-desc">开启后可总结聊天内容，用于更好的响应用户消息</div>
              <div class="toggle-item">
                <span>文件盒子</span>
                <el-switch v-model="fileBox" active-color="#667eea" />
              </div>
              <div class="toggle-desc">{{ fileBox ? '文件盒子已开启，用户可保存文件' : '文件盒子已关闭' }}</div>
            </div>
          </template>
          <div v-else class="crm-info">
            <p>连接 CRM 后端智能客服</p>
            <p style="font-size: 12px; color: #999; margin-top: 4px">支持 SSE 流式输出、工具调用、历史记录</p>
          </div>

          <!-- 历史会话列表 -->
          <div class="panel-title" style="margin-top: 20px">
            <el-icon><ChatLineRound /></el-icon>
            历史会话
            <el-button
              size="small"
              type="text"
              style="float: right"
              @click="newConversation"
            >
              <el-icon><Plus /></el-icon> 新建
            </el-button>
          </div>
          <div class="conversation-list">
            <div v-if="conversations.length === 0" class="no-conversation">
              暂无历史会话
            </div>
            <div
              v-for="conv in conversations"
              :key="conv.id"
              :class="[
                'conversation-item',
                { 'conversation-active': currentConversationId === conv.id },
              ]"
              @click="loadConversation(conv)"
            >
              <div class="conv-title">{{ conv.title }}</div>
              <div class="conv-meta">
                <span>{{ roles[conv.agentType]?.icon || "🧠" }}</span>
                <span>{{ conv.messageCount }}条消息</span>
                <span>{{ formatConvTime(conv.lastMessageTime) }}</span>
              </div>
              <el-button
                class="conv-delete"
                size="small"
                type="text"
                @click.stop="deleteConversation(conv.id)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右侧：聊天区域-->
      <el-col :span="18">
        <div class="chat-panel">
          <div class="chat-header">
            <span class="current-role-badge">
              {{ roles[currentRole].icon }} {{ roles[currentRole].name }}
            </span>
            <span v-if="roles[currentRole].isCrm" class="crm-badge">CRM</span>
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

          <div class="chat-input-area">
            <el-input
              class="chat-input"
              v-model="inputMessage"
              type="textarea"
              :rows="2"
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
    </el-row>
  </div>
</template>
<script>
import { getToken } from "@/utils/storage.js";

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
      currentConversationId: null,
      conversations: [],
      crmSessionId: null,
      crmPhoneNumber: "",
      crmToolCalls: [],
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
          name: "咨询助理",
          icon: "\uD83D\uDCBC",
          desc: "智能健康咨询·CRM对接",
          temp: 0,
          topP: 0,
          isCrm: true,
          welcome: "您好！我是您的健康咨询助理，可以帮您解答健康相关问题、查询历史对话、分析体检报告等。请问有什么可以帮您？",
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
  created() {
    this.loadConversations();
    // 获取用户手机号用于 CRM
    const userInfo = JSON.parse(sessionStorage.getItem("userInfo") || "{}");
    this.crmPhoneNumber = userInfo.userEmail || userInfo.phone || "guest";
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
      if (!r.isCrm) {
        this.temperature = r.temp;
        this.topP = r.topP;
      }
      this.crmSessionId = null;
      this.currentConversationId = null;
      this.messages = [];
      // CRM 角色：从后端加载历史
      if (r.isCrm) {
        this.loadCrmHistory();
      } else {
        this.loadConversations();
      }
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
    // 加载 CRM 历史对话
    async loadCrmHistory() {
      try {
        const response = await this.$axios.get(
          `/crm/history/${this.crmPhoneNumber}`
        );
        const { data } = response;
        if (data.code === 200 && data.data) {
          this.messages = data.data.map((item) => ({
            role: item.role === "assistant" ? "assistant" : "user",
            content: item.content,
            createTime: item.created_at
              ? item.created_at.substring(11, 16)
              : "",
          }));
          this.scrollToBottom();
        }
      } catch (e) {
        console.error("CRM 历史加载异常:", e);
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
      this.crmSessionId = null;
      this.crmToolCalls = [];
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

      const isCrm = this.roles[this.currentRole].isCrm;
      if (isCrm) {
        await this.sendCrmMessage(msg);
      } else {
        await this.sendAiMessage(msg);
      }
    },
    // CRM SSE 流式对话
    async sendCrmMessage(msg) {
      // 添加一条空的 assistant 消息，后续逐字填充
      const aiMsg = {
        role: "assistant",
        content: "",
        toolCalls: [],
        createTime: this.formatTime(new Date()),
      };
      this.messages.push(aiMsg);
      this.crmToolCalls = [];

      this._abortController = new AbortController();
      try {
        const token = getToken();
        const response = await fetch("http://localhost:21090/api/personal-health/v1.0/crm/chat/stream", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-CRM-API-Key": this.$crmApiKey
          },
          body: JSON.stringify({
            phoneNumber: this.crmPhoneNumber,
            query: msg,
            sessionId: this.crmSessionId,
          }),
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
          buffer = lines.pop(); // 保留未完成的行

          let currentEvent = "";
          for (const line of lines) {
            if (line.startsWith("event: ")) {
              currentEvent = line.slice(7).trim();
            } else if (line.startsWith("data: ")) {
              const raw = line.slice(6);
              try {
                const data = JSON.parse(raw);
                this.handleSseEvent(currentEvent, data, aiMsg);
              } catch (e) {
                // 非 JSON 行，忽略
              }
            }
          }
          this.scrollToBottom();
        }
      } catch (e) {
        if (e.name !== "AbortError") {
          aiMsg.content += "\n\n网络异常，请检查后重试！";
          console.error("CRM SSE 异常:", e);
        }
      }
      this.loading = false;
      this.scrollToBottom();
    },
    // 处理 SSE 事件
    handleSseEvent(event, data, aiMsg) {
      switch (event) {
        case "reasoning_start":
          aiMsg.content += `🤔 正在思考（第${data.round}轮）...\n`;
          break;
        case "tool_call":
          aiMsg.content += `🔧 正在调用: ${data.tool}\n`;
          aiMsg.toolCalls.push({ tool: data.tool, args: data.arguments, round: data.round });
          break;
        case "tool_result":
          aiMsg.content += `✅ ${data.tool} 完成 (${data.elapsed_ms}ms)\n`;
          break;
        case "answer_chunk":
          // 移除之前的思考/工具提示行，拼接正式回答
          if (!aiMsg._answerStarted) {
            aiMsg._answerStarted = true;
            // 保留工具调用信息，添加分隔
            const toolInfo = aiMsg.content;
            aiMsg.content = toolInfo ? toolInfo + "\n---\n" : "";
          }
          aiMsg.content += data.content;
          break;
        case "answer_done":
          // 保存 sessionId
          if (data.sessionId) {
            this.crmSessionId = data.sessionId;
          }
          break;
        case "error":
          aiMsg.content += `\n\n❌ 错误: ${data.message}`;
          break;
      }
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

        const response = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/chat/stream", {
          method: "POST",
          headers: headers,
          body: JSON.stringify({
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
            files: this.uploadFiles,
            userId: userInfo.id || null,
            context: {
              userName: userInfo.userName || "",
              requestHealthData: true,
            },
          }),
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
      this.crmSessionId = null;
      this.crmToolCalls = [];
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
        this.uploadFiles.push(res.data);
        this.$message.success("文件上传成功");
      } else {
        this.$message.error("文件上传失败");
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
      const escaped = content
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
      return escaped.replace(/\n/g, "<br>");
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
  height: calc(100vh - 200px);
  min-height: 500px;
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
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
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

.crm-info {
  padding: 12px 0;
  color: #38a169;
  font-size: 14px;
  font-weight: 500;

  p {
    margin: 0;
  }
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

.crm-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  background: linear-gradient(135deg, #38a169, #2f855a);
  color: #fff;
  margin-left: 8px;
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
</style>
