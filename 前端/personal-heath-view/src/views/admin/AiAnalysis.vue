<template>
  <div style="box-sizing: border-box; padding: 10px">
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <!--  -->
      <el-tab-pane label="" name="records">
        <div style="padding: 10px 0">
          <el-row style="margin-bottom: 15px">
            <el-col :span="8">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator=""
                start-placeholder=""
                end-placeholder=""
                size="small"
                @change="loadChatRecords"
              >
              </el-date-picker>
            </el-col>
            <el-col :span="4">
              <el-select
                v-model="queryRole"
                placeholder=""
                clearable
                size="small"
                @change="loadChatRecords"
              >
                <el-option label="" value=""></el-option>
                <el-option label="" value="doctor"></el-option>
                <el-option label="" value="nutritionist"></el-option>
                <el-option label="" value="psychologist"></el-option>
                <el-option label="" value="analyst"></el-option>
                <el-option
                  label=""
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
                <el-icon><Search /></el-icon> 
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
            <el-table-column prop="role" label="" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getRoleTagType(row.agentType)">
                  {{ getRoleName(row.agentType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="sender"
              label=""
              width="100"
            ></el-table-column>
            <el-table-column
              prop="content"
              label=""
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column
              prop="createTime"
              label=""
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

      <!--  -->
      <el-tab-pane label="" name="stats">
        <el-row :gutter="20" style="margin-bottom: 20px">
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.totalChats }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.todayChats }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.userCount }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ stats.avgPerUser }}</div>
              <div class="stat-label"></div>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <div class="chart-card">
              <div class="chart-title"></div>
              <div style="padding: 20px">
                <el-table :data="roleStats" border style="width: 100%">
                  <el-table-column prop="name" label=""></el-table-column>
                  <el-table-column
                    prop="count"
                    label=""
                    width="100"
                  ></el-table-column>
                  <el-table-column prop="percent" label="" width="100">
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
              <div class="chart-title"></div>
              <div style="padding: 20px">
                <el-table :data="trendData" border style="width: 100%">
                  <el-table-column prop="date" label=""></el-table-column>
                  <el-table-column
                    prop="count"
                    label=""
                    width="100"
                  ></el-table-column>
                  <el-table-column
                    prop="users"
                    label=""
                    width="100"
                  ></el-table-column>
                </el-table>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- AI -->
      <el-tab-pane label="AI " name="config">
        <div class="config-container">
          <el-alert type="info" :closable="false" style="margin-bottom: 20px">
            <template #title>
              <div>
                <strong>AI</strong> - AIAPI
                <br/>
                <span style="font-size: 12px; color: #999">
                  API KeyKey
                </span>
              </div>
            </template>
          </el-alert>

          <el-form :model="aiConfig" label-width="160px">
            <!--  -->
            <el-divider content-position="left">
              <el-icon><OfficeBuilding /></el-icon> AI
            </el-divider>
            
            <el-form-item label="">
              <el-select 
                v-model="aiConfig.provider" 
                style="width: 100%"
                @change="onProviderChange"
              >
                <el-option 
                  v-for="(config, key) in providers" 
                  :key="key" 
                  :label="config.name" 
                  :value="key"
                />
              </el-select>
            </el-form-item>
            
            <el-form-item label="OpenAI Base URL">
              <el-input 
                v-model="currentProvider.openaiBaseUrl" 
                disabled
                style="background-color: #f5f5f5"
              />
            </el-form-item>
            
            <el-form-item label="Anthropic Base URL" v-if="currentProvider.anthropicBaseUrl">
              <el-input 
                v-model="currentProvider.anthropicBaseUrl" 
                disabled
                style="background-color: #f5f5f5"
              />
            </el-form-item>

            <!--  -->
            <el-divider content-position="left">
              <el-icon><ChatDotRound /></el-icon> 
            </el-divider>
            
            <el-form-item label="API Key">
              <el-input 
                v-model="aiConfig.chat.apiKey" 
                placeholder="API Key"
                show-password
              />
              <span class="form-tip">{{ aiConfig.chat.apiKey ? '' : '' }}</span>
            </el-form-item>
            
            <el-form-item label="API ">
              <el-input 
                v-model="aiConfig.chat.apiUrl" 
                placeholder="API"
              />
            </el-form-item>
            
            <el-form-item label="">
              <el-select v-model="aiConfig.chat.model" style="width: 100%" allow-create filterable>
                <el-option 
                  v-for="model in currentProvider.models" 
                  :key="model" 
                  :label="model" 
                  :value="model"
                />
              </el-select>
            </el-form-item>

            <!--  -->
            <el-divider content-position="left">
              <el-icon><MagicStick /></el-icon> 
            </el-divider>
            
            <el-form-item label="API Key">
              <el-input 
                v-model="aiConfig.reasoner.apiKey" 
                placeholder="API Key"
                show-password
              />
              <span class="form-tip">{{ aiConfig.reasoner.apiKey ? '' : '' }}</span>
            </el-form-item>
            
            <el-form-item label="API ">
              <el-input 
                v-model="aiConfig.reasoner.apiUrl" 
                placeholder="API"
              />
            </el-form-item>
            
            <el-form-item label="">
              <el-select v-model="aiConfig.reasoner.model" style="width: 100%" allow-create filterable>
                <el-option 
                  v-for="model in currentProvider.models" 
                  :key="model" 
                  :label="model" 
                  :value="model"
                />
              </el-select>
            </el-form-item>

            <!-- Embedding -->
            <el-divider content-position="left">
              <el-icon><Connection /></el-icon> Embedding
            </el-divider>
            
            <el-form-item label="API Key">
              <el-input 
                v-model="aiConfig.embedding.apiKey" 
                placeholder="API Key"
                show-password
              />
              <span class="form-tip">{{ aiConfig.embedding.apiKey ? '' : '' }}</span>
            </el-form-item>
            
            <el-form-item label="API ">
              <el-input 
                v-model="aiConfig.embedding.apiUrl" 
                placeholder="Embedding API"
              />
            </el-form-item>
            
            <el-form-item label="">
              <el-input 
                v-model="aiConfig.embedding.model" 
                placeholder="Embedding"
              />
            </el-form-item>

            <!--  -->
            <el-divider content-position="left">
              <el-icon><Setting /></el-icon> 
            </el-divider>
            
            <el-form-item label="(ms)">
              <el-input-number 
                v-model="aiConfig.common.connectTimeout" 
                :min="5000" 
                :max="120000"
                :step="1000"
              />
            </el-form-item>
            
            <el-form-item label="(ms)">
              <el-input-number 
                v-model="aiConfig.common.readTimeout" 
                :min="10000" 
                :max="300000"
                :step="5000"
              />
            </el-form-item>
            
            <el-form-item label="Token">
              <el-input-number 
                v-model="aiConfig.common.maxTokens" 
                :min="256" 
                :max="32768"
                :step="256"
              />
            </el-form-item>
            
            <el-form-item label="">
              <el-input-number 
                v-model="aiConfig.common.maxHistoryRounds" 
                :min="1" 
                :max="50"
                :step="1"
              />
            </el-form-item>
          </el-form>

          <!--  -->
          <div class="config-actions">
            <el-button type="primary" @click="saveConfig" :loading="configSaving">
              <el-icon><Check /></el-icon> 
            </el-button>
            <el-button @click="loadConfig">
              <el-icon><Refresh /></el-icon> 
            </el-button>
            <el-button type="warning" @click="resetConfig">
              <el-icon><RefreshLeft /></el-icon> 
            </el-button>
          </div>

          <!--  -->
          <el-card style="margin-top: 20px">
            <template #header>
              <span></span>
            </template>
            <div class="config-summary">
              <p><strong></strong> {{ currentProvider.name || '' }}</p>
              <p><strong></strong> {{ aiConfig.apiKeyValid ? ' API Key' : ' API Key' }}</p>
              <p><strong></strong> {{ aiConfig.summary }}</p>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!--  -->
      <el-tab-pane label="" name="websearch">
        <div class="config-container">
          <el-alert type="info" :closable="false" style="margin-bottom: 20px">
            <template #title>
              <div>
                <strong></strong> - APIAI
                <br/>
                <span style="font-size: 12px; color: #999">
                  AITavily
                </span>
              </div>
            </template>
          </el-alert>

          <el-form :model="aiConfig" label-width="140px">
            <!--  -->
            <el-divider content-position="left">
              <el-icon><Setting /></el-icon> 
            </el-divider>
            
            <el-form-item label="">
              <el-switch v-model="aiConfig.webSearch.enabled" />
            </el-form-item>
            
            <el-form-item label="">
              <el-select v-model="aiConfig.webSearch.provider" style="width: 100%">
                <el-option label="" value="auto" />
                <el-option label="AI" value="bocha" />
                <el-option label="Tavily" value="tavily" />
                <el-option label="DuckDuckGo" value="duckduckgo" />
                <el-option label="SerperGoogle" value="serper" />
                <el-option label="SerpAPIGoogle/Bing" value="serpapi" />
              </el-select>
            </el-form-item>

            <!-- AI -->
            <el-divider content-position="left" style="font-size: 13px">
              AI <span style="color: #999; font-size: 11px"></span>
            </el-divider>
            
            <el-form-item label=" API Key">
              <el-input 
                v-model="aiConfig.webSearch.bocha.apiKey" 
                placeholder="AIAPI Key"
                show-password
              />
              <span class="form-tip">{{ aiConfig.webSearch.bocha?.apiKey ? '' : '' }}</span>
            </el-form-item>
            
            <el-form-item label=" API ">
              <el-input 
                v-model="aiConfig.webSearch.bocha.apiUrl" 
                placeholder="https://api.bochaai.com/v1/web-search"
              />
            </el-form-item>

            <!-- Tavily -->
            <el-divider content-position="left" style="font-size: 13px">
              Tavily <span style="color: #999; font-size: 11px">1000</span>
            </el-divider>
            
            <el-form-item label="Tavily API Key">
              <el-input 
                v-model="aiConfig.webSearch.tavily.apiKey" 
                placeholder="TavilyAPI Key"
                show-password
              />
              <span class="form-tip">{{ aiConfig.webSearch.tavily?.apiKey ? '' : '' }}</span>
            </el-form-item>
            
            <el-form-item label="Tavily API ">
              <el-input 
                v-model="aiConfig.webSearch.tavily.apiUrl" 
                placeholder="https://api.tavily.com/search"
              />
            </el-form-item>

            <!-- DuckDuckGo -->
            <el-divider content-position="left" style="font-size: 13px">
              DuckDuckGo <span style="color: #999; font-size: 11px">API Key</span>
            </el-divider>
            
            <el-form-item label="API ">
              <el-input 
                v-model="aiConfig.webSearch.duckduckgo.apiUrl" 
                placeholder="https://api.duckduckgo.com/"
              />
              <span class="form-tip">: https://api.duckduckgo.com/</span>
            </el-form-item>

            <!-- Serper -->
            <el-divider content-position="left" style="font-size: 13px">
              Serper <span style="color: #999; font-size: 11px">Google100</span>
            </el-divider>
            
            <el-form-item label="Serper API Key">
              <el-input 
                v-model="aiConfig.webSearch.serper.apiKey" 
                placeholder="SerperAPI Key"
                show-password
              />
              <span class="form-tip">{{ aiConfig.webSearch.serper?.apiKey ? '' : '' }}</span>
            </el-form-item>
            
            <el-form-item label="Serper API ">
              <el-input 
                v-model="aiConfig.webSearch.serper.apiUrl" 
                placeholder="https://google.serper.dev/search"
              />
            </el-form-item>

            <!-- SerpAPI -->
            <el-divider content-position="left" style="font-size: 13px">
              SerpAPI <span style="color: #999; font-size: 11px">Google/Bing100</span>
            </el-divider>
            
            <el-form-item label="SerpAPI Key">
              <el-input 
                v-model="aiConfig.webSearch.serpapi.apiKey" 
                placeholder="SerpAPIKey"
                show-password
              />
              <span class="form-tip">{{ aiConfig.webSearch.serpapi?.apiKey ? '' : '' }}</span>
            </el-form-item>
            
            <el-form-item label="SerpAPI ">
              <el-input 
                v-model="aiConfig.webSearch.serpapi.apiUrl" 
                placeholder="https://serpapi.com/search"
              />
            </el-form-item>
          </el-form>

          <!--  -->
          <div class="config-actions">
            <el-button type="primary" @click="saveConfig" :loading="configSaving">
              <el-icon><Check /></el-icon> 
            </el-button>
            <el-button @click="loadConfig">
              <el-icon><Refresh /></el-icon> 
            </el-button>
          </div>
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
      activeTab: "records",
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
          name: "",
          icon: "🩺",
          desc: "",
          temp: 0.2,
          topP: 0.3,
        },
        nutritionist: {
          name: "",
          icon: "",
          desc: "",
          temp: 0.6,
          topP: 0.8,
        },
        psychologist: {
          name: "",
          icon: "",
          desc: "",
          temp: 0.8,
          topP: 0.9,
        },
        analyst: {
          name: "",
          icon: "",
          desc: "",
          temp: 0.1,
          topP: 0.1,
        },
        general_assistant: {
          name: "",
          icon: "",
          desc: "",
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
        provider: 'deepseek',
        chat: { apiKey: '', apiUrl: '', model: 'deepseek-v4-flash' },
        reasoner: { apiKey: '', apiUrl: '', model: 'deepseek-v4-pro' },
        webSearch: { 
          enabled: true, 
          provider: 'auto',
          bocha: { apiKey: '', apiUrl: 'https://api.bochaai.com/v1/web-search' },
          tavily: { apiKey: '', apiUrl: 'https://api.tavily.com/search' },
          duckduckgo: { apiUrl: 'https://api.duckduckgo.com/' },
          serper: { apiKey: '', apiUrl: 'https://google.serper.dev/search' },
          serpapi: { apiKey: '', apiUrl: 'https://serpapi.com/search' }
        },
        embedding: { apiKey: '', apiUrl: 'https://api.deepseek.com/v1/embeddings', model: 'text-embedding-3-small' },
        common: { connectTimeout: 30000, readTimeout: 60000, maxTokens: 4096, maxHistoryRounds: 10 },
        apiKeyValid: false,
        summary: ''
      },
      providers: {},
      currentProvider: {
        name: 'DeepSeek',
        openaiBaseUrl: 'https://api.deepseek.com/v1/chat/completions',
        anthropicBaseUrl: 'https://api.deepseek.com/anthropic',
        models: ['deepseek-v4-flash', 'deepseek-v4-pro']
      },
      configSaving: false,
      configVerified: false,
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
    this.loadProviders();
  },
  methods: {
    handleTabClick() {
      if (this.activeTab === "records") {
        this.loadChatRecords();
      } else if (this.activeTab === "stats") {
        this.loadStats();
      } else if (this.activeTab === "config") {
        if (this.configVerified) {
          this.loadConfig();
        } else {
          this.verifyPassword();
        }
      }
    },
    async verifyPassword() {
      const { value: password } = await this.$swal.fire({
        title: "",
        html: `<p style="margin-bottom:12px">AI</p>`,
        input: "password",
        inputLabel: "",
        inputPlaceholder: "",
        showCancelButton: true,
        confirmButtonText: "",
        cancelButtonText: "",
        confirmButtonColor: "#15559a",
        inputValidator: (value) => { if (!value) return ""; }
      });
      if (!password) {
        this.activeTab = "records";
        return;
      }
      try {
        // 
        const userInfo = JSON.parse(sessionStorage.getItem("userInfo") || "{}");
        const md5 = this.$md5;
        const hashedPwd = md5(md5(password));
        const res = await this.$axios.post("/user/login", {
          userAccount: userInfo.userEmail || userInfo.userAccount || "yangshu",
          userPwd: hashedPwd,
        });
        if (res.data.code === 200) {
          this.configVerified = true;
          this.loadConfig();
        } else {
          this.$swal.fire({ icon: "error", title: "", text: "" });
          this.activeTab = "records";
        }
      } catch (e) {
        this.$swal.fire({ icon: "error", title: "", text: "" });
        this.activeTab = "records";
      }
    },
    // AI
    async loadConfig() {
      try {
        const res = await this.$axios.get("/ai/config/get");
        if (res.data.code === 200) {
          this.aiConfig = res.data.data;
          this.updateCurrentProvider();
        }
      } catch (e) {
        console.error("AI:", e);
      }
    },
    async loadProviders() {
      try {
        const res = await this.$axios.get("/ai/config/providers");
        if (res.data.code === 200) {
          const providersList = res.data.data;
          this.providers = {};
          providersList.forEach(p => {
            this.providers[p.key] = p;
          });
          this.updateCurrentProvider();
        }
      } catch (e) {
        console.error(":", e);
      }
    },
    updateCurrentProvider() {
      if (this.providers[this.aiConfig.provider]) {
        this.currentProvider = this.providers[this.aiConfig.provider];
      }
    },
    async onProviderChange(provider) {
      try {
        const res = await this.$axios.post("/ai/config/switch-provider", { provider });
        if (res.data.code === 200) {
          this.$message.success("");
          this.loadConfig();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("");
        console.error(":", e);
      }
    },
    async saveConfig() {
      this.configSaving = true;
      try {
        const res = await this.$axios.post("/ai/config/update", this.aiConfig);
        if (res.data.code === 200) {
          this.$message.success("");
          this.loadConfig();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("");
        console.error("AI:", e);
      } finally {
        this.configSaving = false;
      }
    },
    async resetConfig() {
      try {
        await this.$confirm("", "", {
          type: "warning"
        });
        const res = await this.$axios.post("/ai/config/reset");
        if (res.data.code === 200) {
          this.$message.success("");
          this.loadConfig();
        }
      } catch (e) {
        if (e !== "cancel") {
          this.$message.error("");
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
            content: "AI ",
            time: this.formatTime(new Date()),
          });
        }
      } catch (e) {
        this.messages.push({
          role: "assistant",
          content: "",
          time: this.formatTime(new Date()),
        });
        console.error("AI :", e);
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
        this.$message.warning("");
        return;
      }
      const content = this.messages
        .map((m) => {
          const role =
            m.role === "user" ? "" : this.roles[this.currentRole].name;
          return `[${m.time}] ${role}:\n${m.content}\n`;
        })
        .join("\n");
      const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `AI_${this.formatDate(new Date())}.txt`;
      a.click();
      URL.revokeObjectURL(url);
    },
    handleFileUpload(res, file) {
      if (res.code === 200) {
        this.uploadFiles.push(res.data);
        this.$message.success("");
      } else {
        this.$message.error("");
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
          current: (this.pagination.current - 1) * this.pagination.size,
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
        console.error(":", e);
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
        console.error(":", e);
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
        doctor: "",
        nutritionist: "",
        psychologist: "",
        analyst: "",
        general_assistant: "",
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
