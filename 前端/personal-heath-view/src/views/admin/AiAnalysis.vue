<template>
  <div style="box-sizing: border-box; padding: 10px">
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <!-- AI 对话 -->
      <el-tab-pane label="AI 对话" name="chat">
        <el-row :gutter="20">
          <!-- 左侧：角色选择与参数-->
          <el-col :span="6">
            <div class="side-panel">
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
                模型参数
              </div>
              <div class="param-item">
                <span class="param-label">Temperature: {{ temperature }}</span>
                <el-slider
                  v-model="temperature"
                  :min="0"
                  :max="1.5"
                  :step="0.1"
                ></el-slider>
              </div>
              <div class="param-item">
                <span class="param-label">Top-P: {{ topP }}</span>
                <el-slider
                  v-model="topP"
                  :min="0"
                  :max="1"
                  :step="0.1"
                ></el-slider>
              </div>

              <div class="panel-title" style="margin-top: 20px">
                <el-icon><Paperclip /></el-icon>
                附件上传
              </div>
              <el-upload
                class="upload-area"
                :action="$uploadUrl"
                :on-success="handleFileUpload"
                :on-remove="handleFileRemove"
                :file-list="fileList"
                multiple
                :limit="5"
              >
                <el-button
                  size="small"
                  type="primary"
                  style="background-color: #15559a; border: none"
                >
                  <el-icon><Upload /></el-icon> 上传文件
                </el-button>
                <template #tip>
                  <div class="el-upload__tip">支持 PDF、Word、文本文件</div>
                </template>
              </el-upload>
            </div>
          </el-col>

          <!-- 右侧：聊天区域-->
          <el-col :span="18">
            <div class="chat-panel">
              <div class="chat-header">
                <span class="current-role-badge">
                  {{ roles[currentRole].icon }} {{ roles[currentRole].name }}
                </span>
                <div>
                  <el-button
                    size="small"
                    type="warning"
                    plain
                    @click="exportChat"
                  >
                    <el-icon><Download /></el-icon> 导出记录
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    @click="clearChat"
                  >
                    <el-icon><Delete /></el-icon> 清空对话
                  </el-button>
                </div>
              </div>

              <div class="chat-messages" ref="chatMessages">
                <div v-if="messages.length === 0" class="chat-empty">
                  <el-icon style="font-size: 48px; color: #ccc"
                    ><ChatDotRound
                  /></el-icon>
                  <p>管理员 AI 健康分析工作台</p>
                  <p class="quick-tips">支持角色切换、对话记录导出与历史查询</p>
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
                      {{
                        msg.role === "user" ? "管理员" : roles[currentRole].name
                      }}
                    </div>
                    <div
                      class="message-text"
                      v-html="formatMessage(msg.content)"
                    ></div>
                    <div class="message-time">{{ msg.time }}</div>
                  </div>
                </div>
                <div v-if="loading" class="message-item message-ai">
                  <div class="message-avatar">
                    <span>{{ roles[currentRole].icon }}</span>
                  </div>
                  <div class="message-content">
                    <div class="message-role">
                      {{ roles[currentRole].name }}
                    </div>
                    <div class="typing-indicator">
                      <span></span><span></span><span></span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="chat-input-area">
                <!-- 功能开关 -->
                <div class="feature-toggles">
                  <el-tooltip content="启用联网搜索获取最新信息">
                    <el-button 
                      :type="enableWebSearch ? 'primary' : 'default'"
                      size="small"
                      @click="enableWebSearch = !enableWebSearch"
                    >
                      <el-icon><Search /></el-icon> 联网搜索
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="启用深度推理，回答更详细但耗时更长">
                    <el-button 
                      :type="enableDeepThink ? 'warning' : 'default'"
                      size="small"
                      @click="enableDeepThink = !enableDeepThink"
                    >
                      <el-icon><MagicStick /></el-icon> 深度思考
                    </el-button>
                  </el-tooltip>
                </div>
                <el-input
                  class="chat-input"
                  v-model="inputMessage"
                  type="textarea"
                  :rows="2"
                  placeholder="输入健康分析问题..."
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
      </el-tab-pane>

      <!-- 咨询记录 -->
      <el-tab-pane label="咨询记录" name="records">
        <div style="padding: 10px 0">
          <el-row style="margin-bottom: 15px">
            <el-col :span="8">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                size="small"
                @change="loadChatRecords"
              >
              </el-date-picker>
            </el-col>
            <el-col :span="4">
              <el-select
                v-model="queryRole"
                placeholder="选择角色"
                clearable
                size="small"
                @change="loadChatRecords"
              >
                <el-option label="全部角色" value=""></el-option>
                <el-option label="全科医生" value="doctor"></el-option>
                <el-option label="营养师" value="nutritionist"></el-option>
                <el-option label="心理咨询" value="psychologist"></el-option>
                <el-option label="报告分析" value="analyst"></el-option>
                <el-option
                  label="全能助手"
                  value="general_assistant"
                ></el-option>
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-button
                size="small"
                style="background-color: #15559a; border: none"
                type="primary"
                @click="loadChatRecords"
              >
                <el-icon><Search /></el-icon> 搜索
              </el-button>
            </el-col>
          </el-row>

          <el-table
            :data="chatRecords"
            border
            style="width: 100%"
            max-height="500"
          >
            <el-table-column prop="id" label="ID" width="80"></el-table-column>
            <el-table-column prop="role" label="角色" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getRoleTagType(row.agentType)">
                  {{ getRoleName(row.agentType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="sender"
              label="发送者"
              width="100"
            ></el-table-column>
            <el-table-column
              prop="content"
              label="内容"
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column
              prop="createTime"
              label="时间"
              width="160"
            ></el-table-column>
          </el-table>

          <el-pagination
            v-if="chatRecords.length > 0"
            style="margin-top: 15px; text-align: right"
            v-model:current-page="pagination.current"
            :page-sizes="[10, 20, 50]"
            v-model:page-size="pagination.size"
            layout="total, sizes, prev, pager, next"
            :total="pagination.total"
          >
          </el-pagination>
        </div>
      </el-tab-pane>

      <!-- 使用统计 -->
      <el-tab-pane label="使用统计" name="stats">
        <el-row :gutter="20" style="margin-bottom: 20px">
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.totalChats }}</div>
              <div class="stat-label">总对话数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.todayChats }}</div>
              <div class="stat-label">今日对话</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.userCount }}</div>
              <div class="stat-label">使用用户数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.avgPerUser }}</div>
              <div class="stat-label">人均对话数</div>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <div class="chart-card">
              <div class="chart-title">角色使用分布</div>
              <div style="padding: 20px">
                <el-table :data="roleStats" border style="width: 100%">
                  <el-table-column prop="name" label="角色"></el-table-column>
                  <el-table-column
                    prop="count"
                    label="对话次数"
                    width="100"
                  ></el-table-column>
                  <el-table-column prop="percent" label="占比" width="100">
                    <template #default="{ row }">
                      <el-progress
                        :percentage="row.percent"
                        :stroke-width="10"
                        :color="'#15559a'"
                      ></el-progress>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="chart-card">
              <div class="chart-title">近期趋势</div>
              <div style="padding: 20px">
                <el-table :data="trendData" border style="width: 100%">
                  <el-table-column prop="date" label="日期"></el-table-column>
                  <el-table-column
                    prop="count"
                    label="对话数"
                    width="100"
                  ></el-table-column>
                  <el-table-column
                    prop="users"
                    label="活跃用户"
                    width="100"
                  ></el-table-column>
                </el-table>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- AI配置管理 -->
      <el-tab-pane label="AI 配置" name="config">
        <div class="config-container">
          <el-alert type="info" :closable="false" style="margin-bottom: 20px">
            <template #title>
              <div>
                <strong>AI配置管理</strong> - 在此配置DeepSeek API的各项参数
                <br/>
                <span style="font-size: 12px; color: #999">
                  修改后立即生效，无需重启服务。API Key不会完整显示，修改时请输入完整Key。
                </span>
              </div>
            </template>
          </el-alert>

          <el-form :model="aiConfig" label-width="140px">
            <!-- 普通对话配置 -->
            <el-divider content-position="left">
              <el-icon><ChatDotRound /></el-icon> 普通对话配置
            </el-divider>
            
            <el-form-item label="API Key">
              <el-input 
                v-model="aiConfig.chat.apiKey" 
                placeholder="请输入DeepSeek API Key"
                show-password
              />
              <span class="form-tip">当前: {{ aiConfig.chat.apiKey || '未配置' }}</span>
            </el-form-item>
            
            <el-form-item label="API 地址">
              <el-input 
                v-model="aiConfig.chat.apiUrl" 
                placeholder="https://api.deepseek.com/v1/chat/completions"
              />
            </el-form-item>
            
            <el-form-item label="模型名称">
              <el-select v-model="aiConfig.chat.model" style="width: 100%">
                <el-option label="deepseek-chat" value="deepseek-chat" />
                <el-option label="deepseek-v4-flash" value="deepseek-v4-flash" />
              </el-select>
            </el-form-item>

            <!-- 深度思考配置 -->
            <el-divider content-position="left">
              <el-icon><MagicStick /></el-icon> 深度思考配置
            </el-divider>
            
            <el-form-item label="API Key">
              <el-input 
                v-model="aiConfig.reasoner.apiKey" 
                placeholder="留空则使用普通对话的API Key"
                show-password
              />
              <span class="form-tip">当前: {{ aiConfig.reasoner.apiKey || '未配置' }}</span>
            </el-form-item>
            
            <el-form-item label="API 地址">
              <el-input 
                v-model="aiConfig.reasoner.apiUrl" 
                placeholder="https://api.deepseek.com/v1/chat/completions"
              />
            </el-form-item>
            
            <el-form-item label="模型名称">
              <el-select v-model="aiConfig.reasoner.model" style="width: 100%">
                <el-option label="deepseek-reasoner" value="deepseek-reasoner" />
                <el-option label="deepseek-chat" value="deepseek-chat" />
              </el-select>
            </el-form-item>

            <!-- 联网搜索配置 -->
            <el-divider content-position="left">
              <el-icon><Search /></el-icon> 联网搜索配置
            </el-divider>
            
            <el-form-item label="启用联网搜索">
              <el-switch v-model="aiConfig.webSearch.enabled" />
            </el-form-item>
            
            <el-form-item label="API Key">
              <el-input 
                v-model="aiConfig.webSearch.apiKey" 
                placeholder="留空则使用普通对话的API Key"
                show-password
              />
              <span class="form-tip">当前: {{ aiConfig.webSearch.apiKey || '未配置' }}</span>
            </el-form-item>
            
            <el-form-item label="API 地址">
              <el-input 
                v-model="aiConfig.webSearch.apiUrl" 
                placeholder="https://api.deepseek.com/v1/chat/completions"
              />
            </el-form-item>
            
            <el-form-item label="模型名称">
              <el-select v-model="aiConfig.webSearch.model" style="width: 100%">
                <el-option label="deepseek-chat" value="deepseek-chat" />
                <el-option label="deepseek-v4-flash" value="deepseek-v4-flash" />
              </el-select>
            </el-form-item>

            <!-- Embedding配置 -->
            <el-divider content-position="left">
              <el-icon><Connection /></el-icon> Embedding配置
            </el-divider>
            
            <el-form-item label="API Key">
              <el-input 
                v-model="aiConfig.embedding.apiKey" 
                placeholder="留空则使用普通对话的API Key"
                show-password
              />
              <span class="form-tip">当前: {{ aiConfig.embedding.apiKey || '未配置' }}</span>
            </el-form-item>
            
            <el-form-item label="API 地址">
              <el-input 
                v-model="aiConfig.embedding.apiUrl" 
                placeholder="https://api.deepseek.com/v1/embeddings"
              />
            </el-form-item>
            
            <el-form-item label="模型名称">
              <el-input 
                v-model="aiConfig.embedding.model" 
                placeholder="text-embedding-3-small"
              />
            </el-form-item>

            <!-- 通用配置 -->
            <el-divider content-position="left">
              <el-icon><Setting /></el-icon> 通用配置
            </el-divider>
            
            <el-form-item label="连接超时(ms)">
              <el-input-number 
                v-model="aiConfig.common.connectTimeout" 
                :min="5000" 
                :max="120000"
                :step="1000"
              />
            </el-form-item>
            
            <el-form-item label="读取超时(ms)">
              <el-input-number 
                v-model="aiConfig.common.readTimeout" 
                :min="10000" 
                :max="300000"
                :step="5000"
              />
            </el-form-item>
            
            <el-form-item label="最大Token数">
              <el-input-number 
                v-model="aiConfig.common.maxTokens" 
                :min="256" 
                :max="32768"
                :step="256"
              />
            </el-form-item>
            
            <el-form-item label="历史轮数">
              <el-input-number 
                v-model="aiConfig.common.maxHistoryRounds" 
                :min="1" 
                :max="50"
                :step="1"
              />
            </el-form-item>
          </el-form>

          <!-- 操作按钮 -->
          <div class="config-actions">
            <el-button type="primary" @click="saveConfig" :loading="configSaving">
              <el-icon><Check /></el-icon> 保存配置
            </el-button>
            <el-button @click="loadConfig">
              <el-icon><Refresh /></el-icon> 刷新配置
            </el-button>
            <el-button type="warning" @click="resetConfig">
              <el-icon><RefreshLeft /></el-icon> 重置默认
            </el-button>
          </div>

          <!-- 配置摘要 -->
          <el-card style="margin-top: 20px">
            <template #header>
              <span>当前配置摘要</span>
            </template>
            <div class="config-summary">
              <p><strong>状态：</strong> {{ aiConfig.apiKeyValid ? '✅ API Key已配置' : '❌ API Key未配置' }}</p>
              <p><strong>摘要：</strong> {{ aiConfig.summary }}</p>
            </div>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script>
export default {
  name: "AdminAiAnalysis",
  data() {
    return {
      activeTab: "chat",
      currentRole: "doctor",
      temperature: 0.2,
      topP: 0.3,
      inputMessage: "",
      messages: [],
      enableWebSearch: false,
      enableDeepThink: false,
      loading: false,
      fileList: [],
      uploadFiles: [],
      roles: {
        doctor: {
          name: "全科医生",
          icon: "🩺",
          desc: "症状分析与就医建议",
          temp: 0.2,
          topP: 0.3,
        },
        nutritionist: {
          name: "营养师",
          icon: "🥗",
          desc: "膳食规划与营养指导",
          temp: 0.6,
          topP: 0.8,
        },
        psychologist: {
          name: "心理咨询",
          icon: "🛋️",
          desc: "情绪疏导与心理支持",
          temp: 0.8,
          topP: 0.9,
        },
        analyst: {
          name: "报告分析",
          icon: "📊",
          desc: "体检报告解读与分析",
          temp: 0.1,
          topP: 0.1,
        },
        general_assistant: {
          name: "全能助手",
          icon: "🧠",
          desc: "综合健康咨询与科普",
          temp: 0.5,
          topP: 0.5,
        },
      },
      chatRecords: [],
      dateRange: [],
      queryRole: "",
      pagination: {
        current: 1,
        size: 10,
        total: 0,
      },
      stats: {
        totalChats: 0,
        todayChats: 0,
        userCount: 0,
        avgPerUser: 0,
      },
      roleStats: [],
      trendData: [],
      aiConfig: {
        chat: { apiKey: '', apiUrl: '', model: 'deepseek-chat' },
        reasoner: { apiKey: '', apiUrl: '', model: 'deepseek-reasoner' },
        webSearch: { apiKey: '', apiUrl: '', model: 'deepseek-chat', enabled: true },
        embedding: { apiKey: '', apiUrl: '', model: 'text-embedding-3-small' },
        common: { connectTimeout: 30000, readTimeout: 60000, maxTokens: 4096, maxHistoryRounds: 10 },
        apiKeyValid: false,
        summary: ''
      },
      configSaving: false,
    };
  },
  watch: {
    "pagination.current"() {
      this.loadChatRecords();
    },
    "pagination.size"() {
      this.loadChatRecords();
    },
  },
  created() {
    this.loadChatRecords();
    this.loadStats();
    this.loadConfig();
  },
  methods: {
    handleTabClick() {
      if (this.activeTab === "records") {
        this.loadChatRecords();
      } else if (this.activeTab === "stats") {
        this.loadStats();
      } else if (this.activeTab === "config") {
        this.loadConfig();
      }
    },
    // AI配置相关方法
    async loadConfig() {
      try {
        const res = await this.$axios.get("/ai/config/get");
        if (res.data.code === 200) {
          this.aiConfig = res.data.data;
        }
      } catch (e) {
        console.error("加载AI配置失败:", e);
      }
    },
    async saveConfig() {
      this.configSaving = true;
      try {
        const res = await this.$axios.post("/ai/config/update", this.aiConfig);
        if (res.data.code === 200) {
          this.$message.success("配置保存成功");
          this.loadConfig();
        } else {
          this.$message.error(res.data.msg || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存配置失败");
        console.error("保存AI配置失败:", e);
      } finally {
        this.configSaving = false;
      }
    },
    async resetConfig() {
      try {
        await this.$confirm("确定要重置为默认配置吗？", "提示", {
          type: "warning"
        });
        const res = await this.$axios.post("/ai/config/reset");
        if (res.data.code === 200) {
          this.$message.success("配置已重置");
          this.loadConfig();
        }
      } catch (e) {
        if (e !== "cancel") {
          this.$message.error("重置配置失败");
        }
      }
    },
    switchRole(role) {
      this.currentRole = role;
      this.temperature = this.roles[role].temp;
      this.topP = this.roles[role].topP;
    },
    async sendMessage() {
      const msg = this.inputMessage.trim();
      if (!msg || this.loading) return;

      this.messages.push({
        role: "user",
        content: msg,
        time: this.formatTime(new Date()),
      });
      this.inputMessage = "";
      this.loading = true;
      this.scrollToBottom();

      try {
        const response = await this.$axios.post("/ai/chat", {
          message: msg,
          role: this.currentRole,
          temperature: this.temperature,
          topP: this.topP,
          files: this.uploadFiles,
          enableWebSearch: this.enableWebSearch,
          enableDeepThink: this.enableDeepThink,
        });
        const { data } = response;
        if (data.code === 200) {
          this.messages.push({
            role: "assistant",
            content: data.data.reply,
            time: this.formatTime(new Date()),
          });
        } else {
          this.messages.push({
            role: "assistant",
            content: "AI 服务暂时不可用，请稍后再试！",
            time: this.formatTime(new Date()),
          });
        }
      } catch (e) {
        this.messages.push({
          role: "assistant",
          content: "网络异常，请检查后重试！",
          time: this.formatTime(new Date()),
        });
        console.error("AI 分析请求异常:", e);
      }

      this.loading = false;
      this.scrollToBottom();
    },
    clearChat() {
      this.messages = [];
      this.uploadFiles = [];
      this.fileList = [];
    },
    exportChat() {
      if (this.messages.length === 0) {
        this.$message.warning("暂无对话记录可导出");
        return;
      }
      const content = this.messages
        .map((m) => {
          const role =
            m.role === "user" ? "管理员" : this.roles[this.currentRole].name;
          return `[${m.time}] ${role}:\n${m.content}\n`;
        })
        .join("\n");
      const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `AI对话记录_${this.formatDate(new Date())}.txt`;
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
    async loadChatRecords() {
      try {
        const params = {
          current: this.pagination.current,
          size: this.pagination.size,
          agentType: this.queryRole || undefined,
        };
        if (this.dateRange && this.dateRange.length === 2) {
          params.startTime = this.dateRange[0];
          params.endTime = this.dateRange[1];
        }
        const response = await this.$axios.post("/ai/records/query", params);
        const { data } = response;
        if (data.code === 200) {
          this.chatRecords = data.data || [];
          this.pagination.total = data.total || 0;
        }
      } catch (e) {
        console.error("加载咨询记录异常:", e);
      }
    },
    async loadStats() {
      try {
        const response = await this.$axios.get("/ai/stats");
        const { data } = response;
        if (data.code === 200) {
          this.stats = data.data.stats || this.stats;
          this.roleStats = data.data.roleStats || [];
          this.trendData = data.data.trendData || [];
        }
      } catch (e) {
        console.error("加载统计异常:", e);
      }
    },
    handleSizeChange(val) {
      this.pagination.size = val;
      this.loadChatRecords();
    },
    handleCurrentChange(val) {
      this.pagination.current = val;
      this.loadChatRecords();
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
    formatDate(date) {
      const y = date.getFullYear();
      const m = (date.getMonth() + 1).toString().padStart(2, "0");
      const d = date.getDate().toString().padStart(2, "0");
      return `${y}${m}${d}`;
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
    getRoleName(type) {
      const map = {
        doctor: "全科医生",
        nutritionist: "营养师",
        psychologist: "心理咨询",
        analyst: "报告分析",
        general_assistant: "全能助手",
      };
      return map[type] || type;
    },
    getRoleTagType(type) {
      const map = {
        doctor: "",
        nutritionist: "success",
        psychologist: "warning",
        analyst: "info",
        general_assistant: "danger",
      };
      return map[type] || "";
    },
  },
};
</script>
<style scoped lang="scss">
.side-panel {
  background: #fff;
  border-radius: 6px;
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
  gap: 8px;
}

.role-item {
  display: flex;
  align-items: center;
  padding: 10px;
  border-radius: 6px;
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
  font-size: 24px;
  margin-right: 10px;
}

.role-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.role-desc {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.param-item {
  margin-bottom: 12px;
}

.param-label {
  font-size: 12px;
  color: #666;
  display: block;
  margin-bottom: 3px;
}

.chat-panel {
  background: #fff;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 220px);
  min-height: 450px;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
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
  margin-bottom: 18px;

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
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #e8eaed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}

.message-content {
  display: flex;
  flex-direction: column;
  margin: 0 10px;
  max-width: 70%;
}

.message-role {
  font-size: 12px;
  color: #999;
  margin-bottom: 3px;
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
  margin-top: 3px;
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
  padding: 12px 20px;
  border-top: 1px solid #f0f0f0;
  gap: 10px;
}

.chat-input {
  flex: 1;

  :deep(.el-textarea__inner) {
    border-radius: 6px;
    resize: none;
    font-size: 14px;
  }
}

.send-btn {
  height: 48px;
  padding: 0 20px;
  border-radius: 6px;
  background-color: #15559a;
  border: none;
}

.stat-card {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  text-align: center;
  border: 1px solid #f0f0f0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #15559a;
}

.stat-label {
  font-size: 13px;
  color: #999;
  margin-top: 5px;
}

.chart-card {
  background: #fff;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  padding: 15px 20px 0;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 10px;
}

.config-container {
  padding: 20px;
}

.config-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-left: 10px;
}

.config-summary {
  font-size: 14px;
  line-height: 2;
}

.config-summary p {
  margin: 0;
}

.feature-toggles {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.feature-toggles .el-button {
  border-radius: 20px;
}
</style>
