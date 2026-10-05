<template>
  <div class="system-config-manage">
    <!--  -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="安全验证"
      width="400px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <el-form :model="passwordForm" label-width="80px">
        <el-form-item label="管理员密码">
          <el-input
            v-model="passwordForm.password"
            type="password"
            placeholder="请输入管理员密码"
            show-password
            @keyup.enter="verifyPassword"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelPasswordDialog">取消</el-button>
        <el-button type="primary" @click="verifyPassword" :loading="verifying">
          验证
        </el-button>
      </template>
    </el-dialog>

    <!--  -->
    <div class="page-header">
      <div class="page-header-left">
        <h2>系统配置</h2>
        <span class="subtitle">管理系统各项运行参数与 AI 能力</span>
      </div>
      <div class="page-header-actions">
        <el-button
          type="warning"
          @click="resetAllConfigs"
          :loading="resettingAll"
        >
          <el-icon><RefreshRight /></el-icon>
          重置全部配置
        </el-button>
      </div>
    </div>

    <!--  -->
    <el-tabs
      v-model="mainTab"
      type="border-card"
      @tab-change="handleMainTabChange"
    >
      <!-- ============  ============ -->
      <el-tab-pane label="系统参数" name="system">
        <el-tabs
          v-model="systemGroup"
          tab-position="left"
          @tab-change="handleSystemGroupChange"
        >
          <el-tab-pane
            v-for="(configs, group) in systemConfigGroups"
            :key="group"
            :label="getGroupLabel(group)"
            :name="group"
          >
            <div class="config-group-header">
              <span class="group-title">{{ getGroupLabel(group) }}</span>
              <el-button
                type="primary"
                size="small"
                @click="saveSystemConfig(group)"
                :loading="saving"
              >
                <el-icon><Check /></el-icon>
                保存配置
              </el-button>
            </div>

            <el-form
              :model="editSystemConfigs[group]"
              label-width="140px"
              class="config-form"
            >
              <el-form-item
                v-for="config in configs"
                :key="config.key"
                :label="config.description"
              >
                <!--  -->
                <el-switch
                  v-if="config.valueType === 'boolean'"
                  v-model="editSystemConfigs[group][config.key]"
                  :active-value="'true'"
                  :inactive-value="'false'"
                />

                <!--  -->
                <el-input-number
                  v-else-if="config.valueType === 'number'"
                  v-model.number="editSystemConfigs[group][config.key]"
                  :min="0"
                  controls-position="right"
                  style="width: 200px"
                />

                <!-- / -->
                <div v-else-if="config.sensitive" class="sensitive-input">
                  <el-input
                    v-model="editSystemConfigs[group][config.key]"
                    :type="showPasswordMap[config.key] ? 'text' : 'password'"
                    placeholder="请输入敏感配置值"
                    style="width: 400px"
                  >
                    <template #append>
                      <el-button
                        :icon="showPasswordMap[config.key] ? 'Hide' : 'View'"
                        @click="togglePasswordVisibility(config.key)"
                      />
                    </template>
                  </el-input>
                  <span class="sensitive-tip">
                    <el-icon><Warning /></el-icon>
                    敏感信息，请妥善保管
                  </span>
                </div>

                <!--  -->
                <el-input
                  v-else
                  v-model="editSystemConfigs[group][config.key]"
                  :placeholder="config.defaultValue || ''"
                  style="width: 400px"
                />

                <span class="config-default" v-if="config.defaultValue">
                  : {{ config.defaultValue }}
                </span>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>

      <!-- ============ LLM (合并 AI 服务商 + 模型管理 + 智能体管理) ============ -->
      <el-tab-pane label="LLM" name="ai">
        <el-tabs v-model="aiTab" @tab-change="handleAiTabChange">
          <!-- AI 服务商配置 -->
          <el-tab-pane label="AI 服务商" name="provider">
            <el-form :model="aiConfig" label-width="140px" class="config-form">
              <el-divider content-position="left">AI</el-divider>

              <el-form-item label="服务商">
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
                  placeholder="https://api.deepseek.com/v1/chat/completions"
                />
              </el-form-item>

              <el-form-item
                label="Anthropic Base URL"
                v-if="currentProvider.anthropicBaseUrl !== null"
              >
                <el-input
                  v-model="currentProvider.anthropicBaseUrl"
                  placeholder="https://api.deepseek.com/anthropic"
                />
              </el-form-item>

              <el-divider content-position="left">模型配置</el-divider>

              <el-form-item label="API Key">
                <el-input
                  v-model="aiConfig.chat.apiKey"
                  placeholder="API Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="API 地址">
                <el-input v-model="aiConfig.chat.apiUrl" placeholder="请输入 Chat API 地址" />
              </el-form-item>

              <el-form-item label="聊天模型">
                <el-select
                  v-model="aiConfig.chat.model"
                  style="width: 100%"
                  allow-create
                  filterable
                >
                  <el-option
                    v-for="model in currentProvider.models"
                    :key="model"
                    :label="model"
                    :value="model"
                  />
                </el-select>
              </el-form-item>

              <el-divider content-position="left">推理模型配置</el-divider>

              <el-form-item label="API Key">
                <el-input
                  v-model="aiConfig.reasoner.apiKey"
                  placeholder="API Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="推理模型">
                <el-select
                  v-model="aiConfig.reasoner.model"
                  style="width: 100%"
                  allow-create
                  filterable
                >
                  <el-option
                    v-for="model in currentProvider.models"
                    :key="model"
                    :label="model"
                    :value="model"
                  />
                </el-select>
              </el-form-item>

              <el-divider content-position="left">Embedding</el-divider>

              <el-form-item label="API Key">
                <el-input
                  v-model="aiConfig.embedding.apiKey"
                  placeholder="API Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="Embedding 模型">
                <el-input
                  v-model="aiConfig.embedding.model"
                  placeholder="Embedding"
                />
              </el-form-item>

              <el-divider content-position="left">超时配置</el-divider>

              <el-form-item label="超时时间 (ms)">
                <el-input-number
                  v-model="aiConfig.common.connectTimeout"
                  :min="5000"
                  :max="120000"
                  :step="1000"
                />
              </el-form-item>

              <el-form-item label="超时时间 (ms)">
                <el-input-number
                  v-model="aiConfig.common.readTimeout"
                  :min="10000"
                  :max="300000"
                  :step="5000"
                />
              </el-form-item>

              <el-form-item label="最大 Token 数">
                <el-input-number
                  v-model="aiConfig.common.maxTokens"
                  :min="256"
                  :max="32768"
                  :step="256"
                />
              </el-form-item>

              <el-form-item label="最大历史轮数">
                <el-input-number
                  v-model="aiConfig.common.maxHistoryRounds"
                  :min="1"
                  :max="50"
                  :step="1"
                />
              </el-form-item>
            </el-form>

            <div class="config-actions">
              <el-button
                type="primary"
                @click="saveAiConfig"
                :loading="aiSaving"
              >
                <el-icon><Check /></el-icon>
              </el-button>
              <el-button @click="loadAiConfig">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </el-tab-pane>

          <!-- 联网搜索 -->
          <el-tab-pane label="联网搜索" name="websearch">
            <el-form :model="aiConfig" label-width="140px" class="config-form">
              <el-divider content-position="left">搜索服务配置</el-divider>

              <el-form-item label="启用联网搜索">
                <el-switch v-model="aiConfig.webSearch.enabled" />
              </el-form-item>

              <el-form-item label="搜索服务">
                <el-select
                  v-model="aiConfig.webSearch.provider"
                  style="width: 100%"
                >
                  <el-option label="自动" value="auto" />
                  <el-option label="AI" value="bocha" />
                  <el-option label="Tavily" value="tavily" />
                  <el-option label="DuckDuckGo" value="duckduckgo" />
                  <el-option label="SerperGoogle" value="serper" />
                  <el-option label="SerpAPIGoogle/Bing" value="serpapi" />
                </el-select>
              </el-form-item>

              <el-divider content-position="left">AI</el-divider>

              <el-form-item label="API Key">
                <el-input
                  v-model="aiConfig.webSearch.bocha.apiKey"
                  placeholder="AIAPI Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="API 地址">
                <el-input
                  v-model="aiConfig.webSearch.bocha.apiUrl"
                  placeholder="https://api.bochaai.com/v1/web-search"
                />
              </el-form-item>

              <el-divider content-position="left">Tavily</el-divider>

              <el-form-item label="Tavily API Key">
                <el-input
                  v-model="aiConfig.webSearch.tavily.apiKey"
                  placeholder="TavilyAPI Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="Tavily API ">
                <el-input
                  v-model="aiConfig.webSearch.tavily.apiUrl"
                  placeholder="https://api.tavily.com/search"
                />
              </el-form-item>

              <el-divider content-position="left">DuckDuckGo</el-divider>

              <el-form-item label="API ">
                <el-input
                  v-model="aiConfig.webSearch.duckduckgo.apiUrl"
                  placeholder="https://api.duckduckgo.com/"
                />
              </el-form-item>

              <el-divider content-position="left">Serper</el-divider>

              <el-form-item label="Serper API Key">
                <el-input
                  v-model="aiConfig.webSearch.serper.apiKey"
                  placeholder="SerperAPI Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="Serper API ">
                <el-input
                  v-model="aiConfig.webSearch.serper.apiUrl"
                  placeholder="https://google.serper.dev/search"
                />
              </el-form-item>

              <el-divider content-position="left">SerpAPI</el-divider>

              <el-form-item label="SerpAPI Key">
                <el-input
                  v-model="aiConfig.webSearch.serpapi.apiKey"
                  placeholder="SerpAPIKey"
                  show-password
                />
              </el-form-item>

              <el-form-item label="SerpAPI ">
                <el-input
                  v-model="aiConfig.webSearch.serpapi.apiUrl"
                  placeholder="https://serpapi.com/search"
                />
              </el-form-item>
            </el-form>

            <div class="config-actions">
              <el-button
                type="primary"
                @click="saveAiConfig"
                :loading="aiSaving"
              >
                <el-icon><Check /></el-icon>
              </el-button>
              <el-button @click="loadAiConfig">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </el-tab-pane>

          <!-- 模型管理（合并自原独立标签页） -->
          <el-tab-pane label="模型管理" name="model">
            <div class="model-manage">
              <el-alert
                title="配置可用的 AI 模型，设置优先级决定默认使用顺序"
                description="点击「添加模型」按钮配置新模型，拖拽或修改优先级数字调整顺序"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />

              <!-- 操作栏 -->
              <div class="model-toolbar">
                <el-button type="primary" @click="showAddModelDialog">
                  <el-icon><Plus /></el-icon>
                  添加模型
                </el-button>
                <el-button @click="loadModelList">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>

              <!-- 模型列表 -->
              <el-table
                :data="modelList"
                style="width: 100%"
                v-loading="modelLoading"
                row-key="providerKey"
              >
                <el-table-column label="优先级" width="100">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.priority"
                      :min="1"
                      :max="999"
                      size="small"
                      controls-position="right"
                      style="width: 80px"
                      @change="updateModelPriority(row)"
                    />
                  </template>
                </el-table-column>
                <el-table-column prop="providerName" label="厂商名称" width="200" />
                <el-table-column prop="providerKey" label="标识" width="160" />
                <el-table-column label="可用模型">
                  <template #default="{ row }">
                    <el-tag
                      v-for="model in row.models"
                      :key="model"
                      size="small"
                      style="margin-right: 4px; margin-bottom: 4px"
                    >
                      {{ model }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="当前状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.current ? 'success' : 'info'" size="small">
                      {{ row.current ? "在线中" : "离线" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button
                      v-if="!row.current"
                      type="primary"
                      size="small"
                      @click="switchModel(row)"
                      :loading="modelSwitching"
                    >
                      切换使用
                    </el-button>
                    <el-tag v-else type="success" size="small">当前使用</el-tag>
                    <el-button
                      size="small"
                      type="danger"
                      @click="removeModel(row)"
                    >
                      移除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 横幅通知配置 -->
              <el-divider content-position="left">横幅通知配置</el-divider>
              <el-form
                :model="announcementForm"
                label-width="120px"
                class="config-form"
                style="max-width: 600px"
              >
                <el-form-item label="关联模型">
                  <el-select
                    v-model="announcementForm.modelKey"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in modelList"
                      :key="item.providerKey"
                      :label="item.providerName"
                      :value="item.providerKey"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="横幅标题">
                  <el-input
                    v-model="announcementForm.title"
                    placeholder="如：本草大模型已上线"
                  />
                </el-form-item>
                <el-form-item label="横幅描述">
                  <el-input
                    v-model="announcementForm.content"
                    type="textarea"
                    :rows="3"
                    placeholder="如：基于Qwen2.5-7B微调的医疗领域模型"
                  />
                </el-form-item>
                <el-form-item label="背景颜色">
                  <el-color-picker v-model="announcementForm.bgColor" />
                </el-form-item>
                <el-form-item label="立即启用">
                  <el-switch
                    v-model="announcementForm.isActive"
                    :active-value="1"
                    :inactive-value="0"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button
                    type="primary"
                    @click="saveAnnouncement"
                    :loading="announcementSaving"
                  >
                    保存横幅
                  </el-button>
                </el-form-item>
              </el-form>

              <!-- 现有横幅列表 -->
              <el-divider content-position="left">已保存的横幅</el-divider>
              <el-table
                :data="announcementList"
                style="width: 100%"
                v-loading="announcementLoading"
              >
                <el-table-column prop="modelKey" label="关联模型" width="160" />
                <el-table-column prop="title" label="标题" />
                <el-table-column
                  prop="content"
                  label="描述"
                  show-overflow-tooltip
                />
                <el-table-column label="颜色" width="80">
                  <template #default="{ row }">
                    <div
                      :style="{
                        width: '24px',
                        height: '24px',
                        borderRadius: '4px',
                        backgroundColor: row.bgColor,
                      }"
                    ></div>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag
                      :type="row.isActive === 1 ? 'success' : 'info'"
                      size="small"
                    >
                      {{ row.isActive === 1 ? "展示中" : "已关闭" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="160">
                  <template #default="{ row }">
                    <el-button size="small" @click="editAnnouncement(row)"
                      >编辑</el-button
                    >
                    <el-button
                      size="small"
                      type="danger"
                      @click="deleteAnnouncement(row)"
                      >删除</el-button
                    >
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 智能体管理（合并自原独立标签页） -->
          <el-tab-pane label="智能体管理" name="doctor">
            <div class="doctor-cards" v-loading="doctorLoading">
              <div
                v-for="doctor in doctorList"
                :key="doctor.key"
                class="doctor-card"
                :class="{ 'doctor-card--active': selectedDoctor === doctor.key }"
                @click="selectDoctor(doctor)"
              >
                <div class="doctor-card-header">
                  <el-icon :size="28" color="#667eea"
                    ><component :is="doctor.icon"
                  /></el-icon>
                  <div class="doctor-card-title">
                    <h3>{{ doctor.name }}</h3>
                    <p>{{ doctor.description }}</p>
                  </div>
                </div>
                <div class="doctor-card-params">
                  <span class="param-item">Temp: {{ doctor.temperature }}</span>
                  <span class="param-item">Top-P: {{ doctor.topP }}</span>
                  <span class="param-item">上下文: {{ doctor.contextRounds }}轮</span>
                  <span class="param-item">回复长度: {{ doctor.maxReplyLength }}</span>
                </div>
              </div>
            </div>

            <div v-if="selectedDoctor" class="doctor-editor">
              <div class="doctor-editor-header">
                <h3>
                  <el-icon><component :is="currentDoctorConfig.icon" /></el-icon>
                  {{ currentDoctorConfig.name }} - 智能体配置
                </h3>
                <div class="doctor-editor-actions">
                  <el-button @click="resetDoctorConfig" :loading="doctorResetting">
                    <el-icon><RefreshRight /></el-icon>
                    重置
                  </el-button>
                  <el-button
                    type="primary"
                    @click="saveDoctorConfig"
                    :loading="doctorSaving"
                  >
                    <el-icon><Check /></el-icon>
                    保存配置
                  </el-button>
                </div>
              </div>

              <el-form
                :model="doctorEditForm"
                label-width="140px"
                class="config-form"
              >
                <el-form-item label="系统提示词">
                  <el-input
                    v-model="doctorEditForm.systemPrompt"
                    type="textarea"
                    :rows="12"
                    placeholder="请输入该智能体的系统提示词"
                  />
                </el-form-item>

                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="Temperature">
                      <el-slider
                        v-model="doctorEditForm.temperature"
                        :min="0"
                        :max="2"
                        :step="0.1"
                        show-input
                      />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="Top-P">
                      <el-slider
                        v-model="doctorEditForm.topP"
                        :min="0"
                        :max="1"
                        :step="0.05"
                        show-input
                      />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="Max Tokens">
                      <el-tooltip
                        content="单次回复最大 token 数；留空则继承全局设置"
                        placement="top"
                      >
                        <el-input-number
                          v-model="doctorEditForm.maxTokens"
                          :min="1"
                          :max="8192"
                          :step="128"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-tooltip>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="Presence Penalty">
                      <el-tooltip
                        content="存在惩罚：正值会让模型避免重复已提及的主题"
                        placement="top"
                      >
                        <el-slider
                          v-model="doctorEditForm.presencePenalty"
                          :min="-2"
                          :max="2"
                          :step="0.1"
                          show-input
                        />
                      </el-tooltip>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="Frequency Penalty">
                      <el-tooltip
                        content="频率惩罚：正值会让模型减少重复用词"
                        placement="top"
                      >
                        <el-slider
                          v-model="doctorEditForm.frequencyPenalty"
                          :min="-2"
                          :max="2"
                          :step="0.1"
                          show-input
                        />
                      </el-tooltip>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="重复惩罚">
                      <el-tooltip content="重复惩罚：正值抑制重复生成的内容（0~2）" placement="top">
                        <el-slider
                          v-model="doctorEditForm.repetitionPenalty"
                          :min="0"
                          :max="2"
                          :step="0.1"
                          show-input
                        />
                      </el-tooltip>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="上下文轮数">
                      <el-tooltip content="携带的历史对话轮数（0~50），0 表示不携带历史" placement="top">
                        <el-input-number
                          v-model="doctorEditForm.contextRounds"
                          :min="0"
                          :max="50"
                          :step="1"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-tooltip>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="最大回复长度">
                      <el-tooltip content="单次回复的最大长度上限（0~32768），0 表示不限" placement="top">
                        <el-input-number
                          v-model="doctorEditForm.maxReplyLength"
                          :min="0"
                          :max="32768"
                          :step="256"
                          controls-position="right"
                          style="width: 100%"
                        />
                      </el-tooltip>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </div>

            <el-empty v-else description="请选择一个 AI 智能体进行配置" />
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>

      <!-- ============  ============ -->
      <el-tab-pane label="语音配置" name="voice">
        <el-tabs v-model="voiceTab" @tab-change="handleVoiceTabChange">
          <!-- ASR  -->
          <el-tab-pane label="语音识别 (ASR)" name="asr">
            <el-form
              :model="voiceConfig.asr"
              label-width="140px"
              class="config-form"
            >
              <el-divider content-position="left">ASR Provider 选择</el-divider>

              <el-form-item label="ASR 服务商">
                <el-select
                  v-model="voiceConfig.asr.provider"
                  style="width: 100%"
                  @change="onAsrProviderChange"
                >
                  <el-option label="FunASR (阿里云 DashScope)" value="funasr" />
                  <el-option label="OpenAI Whisper" value="whisper" />
                  <el-option label="通义千问 ASR" value="qwen" />
                </el-select>
              </el-form-item>

              <el-form-item label="API Key">
                <el-input
                  v-model="voiceConfig.asr.apiKey"
                  placeholder="请输入 DashScope / OpenAI API Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="API 地址">
                <el-input
                  v-model="voiceConfig.asr.apiUrl"
                  placeholder="https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition"
                />
              </el-form-item>

              <el-form-item
                label="端点基址"
                v-if="voiceConfig.asr.provider === 'whisper'"
              >
                <el-input
                  v-model="voiceConfig.asr.baseUrl"
                  placeholder="留空则用 https://api.openai.com/v1（后端会自动拼 /audio/transcriptions）"
                />
                <div class="slider-tip">
                  <span>兼容 Azure OpenAI / 本地 vLLM 等任意 OpenAI 协议端点</span>
                </div>
              </el-form-item>

              <el-form-item label="模型">
                <el-select
                  v-model="voiceConfig.asr.model"
                  style="width: 100%"
                  allow-create
                  filterable
                >
                  <el-option label="paraformer-zh (中文)" value="paraformer-zh" />
                  <el-option label="paraformer-v2" value="paraformer-v2" />
                  <el-option label="whisper-1" value="whisper-1" />
                </el-select>
              </el-form-item>

              <el-form-item label="语言">
                <el-select
                  v-model="voiceConfig.asr.language"
                  style="width: 100%"
                >
                  <el-option label="中文 (zh-CN)" value="zh-CN" />
                  <el-option label="英文 (en-US)" value="en-US" />
                  <el-option label="自动检测" value="auto" />
                </el-select>
              </el-form-item>

              <el-form-item label="超时时间 (ms)">
                <el-input-number
                  v-model="voiceConfig.asr.timeout"
                  :min="5000"
                  :max="120000"
                  :step="5000"
                />
              </el-form-item>

              <el-divider content-position="left">Provider 说明</el-divider>
              <el-alert
                v-if="voiceConfig.asr.provider === 'funasr'"
                title="FunASR (阿里云 DashScope)"
                description="使用阿里云 DashScope 的 Paraformer 模型，需填写 DashScope API Key。异步提交 + 轮询，单次请求上限 500 秒，适合较长音频。"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else-if="voiceConfig.asr.provider === 'whisper'"
                title="OpenAI Whisper"
                description="走 OpenAI 兼容协议（POST {baseUrl}/audio/transcriptions），需填写 OpenAI API Key。同步返回结果，适合短语音输入。端点基址留空则使用官方地址。"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else
                title="通义千问 ASR"
                description="使用阿里云通义千问语音识别，需填写 DashScope API Key。"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
            </el-form>
          </el-tab-pane>

          <!-- TTS  -->
          <el-tab-pane label="语音合成 (TTS)" name="tts">
            <el-form
              :model="voiceConfig.tts"
              label-width="140px"
              class="config-form"
            >
              <el-divider content-position="left">TTS Provider 选择</el-divider>

              <el-form-item label="TTS 服务商">
                <el-select
                  v-model="voiceConfig.tts.provider"
                  style="width: 100%"
                  @change="onTtsProviderChange"
                >
                  <el-option label="EdgeTTS (免费, 微软)" value="edgetts" />
                  <el-option label="CosyVoice (阿里云)" value="cosyvoice" />
                  <el-option label="MiniMax TTS" value="minimax" />
                </el-select>
              </el-form-item>

              <el-form-item
                label="音色"
                v-if="voiceConfig.tts.provider === 'edgetts'"
              >
                <el-select v-model="voiceConfig.tts.voice" style="width: 100%">
                  <el-option label="晓晓 (女声, 通用)" value="zh-CN-XiaoxiaoNeural" />
                  <el-option label="云希 (男声, 通用)" value="zh-CN-YunxiNeural" />
                  <el-option label="云健 (男声, 新闻)" value="zh-CN-YunjianNeural" />
                  <el-option label="晓伊 (女声, 童声)" value="zh-CN-XiaoyiNeural" />
                  <el-option label="云扬 (男声, 新闻)" value="zh-CN-YunyangNeural" />
                </el-select>
              </el-form-item>

              <el-form-item label="API Key" v-else>
                <el-input
                  v-model="voiceConfig.tts.apiKey"
                  placeholder="请输入 TTS API Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="语速">
                <el-slider
                  v-model="voiceConfig.tts.speed"
                  :min="0.5"
                  :max="2.0"
                  :step="0.1"
                  show-stops
                  :format-tooltip="(val) => val.toFixed(1) + 'x'"
                />
              </el-form-item>

              <el-form-item label="音量">
                <el-slider
                  v-model="voiceConfig.tts.volume"
                  :min="0"
                  :max="100"
                  :step="10"
                />
              </el-form-item>

              <el-form-item label="输出格式">
                <el-radio-group v-model="voiceConfig.tts.format">
                  <el-radio label="mp3">MP3</el-radio>
                  <el-radio label="wav">WAV</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="超时时间 (ms)">
                <el-input-number
                  v-model="voiceConfig.tts.timeout"
                  :min="10000"
                  :max="300000"
                  :step="10000"
                />
              </el-form-item>

              <el-divider content-position="left">Provider 说明</el-divider>
              <el-alert
                v-if="voiceConfig.tts.provider === 'edgetts'"
                title="EdgeTTS (免费, 微软)"
                description="调用微软 Edge 浏览器的语音合成服务，无需 API Key。音质优于浏览器原生 speechSynthesis，但要求服务端能出网到 speech.platform.bing.com。"
                type="success"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else-if="voiceConfig.tts.provider === 'cosyvoice'"
                title="CosyVoice (阿里云)"
                description="使用阿里云 DashScope 的 CosyVoice 服务，需填写 DashScope API Key。"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else
                title="MiniMax TTS"
                description="使用 MiniMax 语音合成服务，需填写 MiniMax API Key。"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
            </el-form>
          </el-tab-pane>

          <!-- VAD  -->
          <el-tab-pane label="语音活动检测 (VAD)" name="vad">
            <el-form
              :model="voiceConfig.vad"
              label-width="140px"
              class="config-form"
            >
              <el-divider content-position="left">VAD 参数</el-divider>

              <el-form-item label="启用 VAD">
                <el-switch
                  v-model="voiceConfig.vad.enabled"
                  active-text="开启"
                  inactive-text="关闭"
                />
              </el-form-item>

              <el-form-item label="灵敏度" v-if="voiceConfig.vad.enabled">
                <el-slider
                  v-model="voiceConfig.vad.sensitivity"
                  :min="0.1"
                  :max="1.0"
                  :step="0.1"
                  show-stops
                  :format-tooltip="(val) => (val * 100).toFixed(0) + '%'"
                />
                <div class="slider-tip">
                  <span>低</span>
                  <span>高</span>
                </div>
              </el-form-item>

              <el-divider content-position="left">VAD 说明</el-divider>
              <el-alert
                title="语音活动检测 (Voice Activity Detection)"
                description="VAD 用于在录音过程中自动检测用户是否还在说话：1. 开启后可在静音时自动结束录音；2. 灵敏度越高越容易判定为「说话结束」；3. 当前对话页录音为「按住说话」模式，松开即提交，VAD 参数暂未接入录音流程，仅作预留。"
                type="info"
                show-icon
                :closable="false"
              />

              <el-divider content-position="left">连通性自测</el-divider>
              <el-alert
                title="服务端出网探测"
                description="TTS 默认走微软 Edge TTS（免费免 Key）；ASR 的 funasr=阿里云 DashScope、whisper=OpenAI 兼容端点，两者都需 API Key 且服务端能出到对应域名。保存配置后可点下方按钮验证服务端是否真的连得上（与浏览器是否能出网无关）。"
                type="warning"
                show-icon
                :closable="false"
              />
              <el-form-item>
                <el-space>
                  <el-button size="small" :loading="voiceTesting" @click="testVoiceConnection('tts')">
                    测试 TTS 合成
                  </el-button>
                  <el-button size="small" :loading="voiceTesting" @click="testVoiceConnection('asr')">
                    测试 ASR 识别
                  </el-button>
                  <span
                    v-if="voiceTestResult"
                    class="voice-test-result"
                    :class="voiceTestResult.success ? 'is-ok' : 'is-fail'"
                  >
                    {{ voiceTestResult.success ? "✓" : "✗" }}
                    {{ voiceTestResult.provider }} ·
                    {{ voiceTestResult.durationMs }}ms ·
                    {{ voiceTestResult.message }}
                  </span>
                </el-space>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <!-- 语音配置操作区 -->
        <div class="config-actions">
          <el-button type="primary" :loading="voiceSaving" @click="saveVoiceConfig">
            <el-icon><Check /></el-icon>保存配置
          </el-button>
          <el-button @click="loadVoiceConfig">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </el-tab-pane>

    </el-tabs>

    <!--  -->
    <div class="config-status" v-if="lastSaveTime">
      <el-tag type="success">
        <el-icon><Check /></el-icon>
        最后保存：{{ lastSaveTime }}
      </el-tag>
    </div>
  </div>
</template>

<script>
export default {
  name: "SystemConfigManage",
  data() {
    return {
      //
      passwordDialogVisible: false,
      passwordForm: { password: "" },
      verifying: false,
      passwordVerified: false,
      passwordCallback: null,

      //
      mainTab: "system",

      // ======  ======
      systemConfigGroups: {},
      editSystemConfigs: {},
      systemGroup: "mysql",
      saving: false,
      resettingAll: false,
      lastSaveTime: null,
      showPasswordMap: {},

      // ====== LLM ======
      aiTab: "provider",
      aiConfig: {
        provider: "deepseek",
        chat: { apiKey: "", apiUrl: "", model: "deepseek-v4-flash" },
        reasoner: { apiKey: "", apiUrl: "", model: "deepseek-v4-pro" },
        webSearch: {
          enabled: true,
          provider: "auto",
          bocha: {
            apiKey: "",
            apiUrl: "https://api.bochaai.com/v1/web-search",
          },
          tavily: { apiKey: "", apiUrl: "https://api.tavily.com/search" },
          duckduckgo: { apiUrl: "https://api.duckduckgo.com/" },
          serper: { apiKey: "", apiUrl: "https://google.serper.dev/search" },
          serpapi: { apiKey: "", apiUrl: "https://serpapi.com/search" },
        },
        embedding: {
          apiKey: "",
          apiUrl: "https://api.deepseek.com/v1/embeddings",
          model: "text-embedding-3-small",
        },
        common: {
          connectTimeout: 30000,
          readTimeout: 60000,
          maxTokens: 4096,
          maxHistoryRounds: 10,
        },
        apiKeyValid: false,
        summary: "",
      },
      providers: {},
      currentProvider: {
        name: "DeepSeek",
        openaiBaseUrl: "https://api.deepseek.com/v1/chat/completions",
        anthropicBaseUrl: "https://api.deepseek.com/anthropic",
        models: ["deepseek-v4-flash", "deepseek-v4-pro"],
      },
      aiSaving: false,

      // ======  ======
      voiceTab: "asr",

      // ======  ======
      modelList: [],
      modelLoading: false,
      modelSwitching: false,
      announcementList: [],
      announcementForm: {
        modelKey: "zhikangyun-local",
        title: "",
        content: "",
        bgColor: "#67C23A",
        isActive: 1,
      },
      announcementLoading: false,
      announcementSaving: false,

      voiceConfig: {
        asr: {
          provider: "funasr",
          apiKey: "",
          apiUrl:
            "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition",
          // OpenAI 兼容 ASR 端点基址：Whisper 走 baseUrl+/audio/transcriptions；留空则用官方
          baseUrl: "",
          model: "paraformer-zh",
          language: "zh-CN",
          timeout: 30000,
        },
        tts: {
          provider: "edgetts",
          apiKey: "",
          apiUrl: "",
          voice: "zh-CN-XiaoxiaoNeural",
          speed: 1.0,
          volume: 100,
          format: "mp3",
          timeout: 60000,
        },
        vad: {
          enabled: true,
          sensitivity: 0.5,
        },
      },
      voiceSaving: false,
      voiceTesting: false,
      voiceTestResult: null,

      // ====== AI ======
      doctorLoading: false,
      doctorList: [],
      selectedDoctor: null,
      currentDoctorConfig: {},
      doctorEditForm: {
        systemPrompt: "",
        temperature: 0.5,
        topP: 0.5,
        maxTokens: 2048,
        presencePenalty: 0,
        frequencyPenalty: 0,
        repetitionPenalty: 1.0,
        contextRounds: 5,
        maxReplyLength: 2048,
      },
      doctorSaving: false,
      doctorResetting: false,

      //
      groupLabels: {
        mysql: "MySQL",
        server: "服务器",
        websocket: "WebSocket",
        ota: "OTA",
        sqlite: "SQLite",
        jwt: "JWT",
        admin: "后台管理",
      },
    };
  },
  created() {
    this.loadSystemConfigs();
    this.loadAiConfig();
    this.loadProviders();
    this.loadDoctorConfigs();
    this.loadVoiceConfig();
    this.loadModelList();
    this.loadAnnouncements();
  },
  methods: {
    // ====================  ====================
    getGroupLabel(group) {
      return this.groupLabels[group] || group;
    },

    handleMainTabChange(tab) {
      if (tab === "model") {
        this.loadModelList();
        this.loadAnnouncements();
      }
    },

    async loadSystemConfigs() {
      try {
        const res = await this.$axios.get("/system/config/all");
        if (res.data.code === 200) {
          // 后端直接返回 { group: [...], ... } 格式，不嵌套在 groups 下
          const groups = res.data.data || {};
          this.systemConfigGroups = groups;

          this.editSystemConfigs = {};
          for (const group in groups) {
            this.editSystemConfigs[group] = {};
            groups[group].forEach((config) => {
              this.editSystemConfigs[group][config.key] = config.value;
              if (config.sensitive) {
                this.showPasswordMap[config.key] = false;
              }
            });
          }
        }
      } catch (e) {
        console.error("加载系统配置失败", e);
        this.$message.error("加载系统配置失败");
      }
    },

    handleSystemGroupChange(group) {
      this.systemGroup = group;
    },

    togglePasswordVisibility(key) {
      this.showPasswordMap[key] = !this.showPasswordMap[key];
    },

    async saveSystemConfig(group) {
      const configs = this.systemConfigGroups[group] || [];
      const hasSensitive = configs.some((c) => c.sensitive);

      if (hasSensitive && !this.passwordVerified) {
        this.showPasswordDialog(() => {
          this.doSaveSystemConfig(group);
        });
        return;
      }

      await this.doSaveSystemConfig(group);
    },

    async doSaveSystemConfig(group) {
      this.saving = true;
      try {
        const configs = this.systemConfigGroups[group] || [];
        const updateList = configs.map((config) => ({
          configGroup: group,
          configKey: config.key,
          configValue: this.editSystemConfigs[group][config.key],
          description: config.description,
          sensitive: config.sensitive,
          valueType: config.valueType,
          defaultValue: config.defaultValue,
        }));

        const res = await this.$axios.post("/system/config/batch-update", {
          configs: updateList,
        });

        if (res.data.code === 200) {
          this.$message.success("配置保存成功");
          this.lastSaveTime = new Date().toLocaleString();
          this.passwordVerified = false;
        } else {
          this.$message.error(res.data.message || "");
        }
      } catch (e) {
        this.$message.error(": " + (e.response?.data?.message || e.message));
      } finally {
        this.saving = false;
      }
    },

    async resetAllConfigs() {
      const { value: password } = await this.$swal.fire({
        title: "重置配置确认",
        html: `<p style="margin-bottom:12px"> 将把系统配置恢复为默认（MySQL）值 </p>
               <p style="color:#e6a23c;font-size:13px"> 此操作不可撤销，请谨慎操作 </p>`,
        input: "password",
        inputLabel: "管理员密码",
        inputPlaceholder: "请输入管理员密码",
        inputAttributes: { autocapitalize: "off", autocorrect: "off" },
        showCancelButton: true,
        confirmButtonText: "确认重置",
        cancelButtonText: "取消",
        customClass: { confirmButton: "swal2-btn-warning" },
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        },
      });

      if (!password) return;

      try {
        const verifyRes = await this.$axios.post(
          "/system/config/verify-password",
          { password }
        );
        if (verifyRes.data.code !== 200) {
          this.$swal.fire({
            icon: "error",
            title: "验证失败",
            text: "密码错误，无法重置配置",
          });
          return;
        }
      } catch (e) {
        this.$swal.fire({
          icon: "error",
          title: "验证失败",
          text: "验证过程发生错误",
        });
        return;
      }

      this.resettingAll = true;
      try {
        const res = await this.$axios.post("/system/config/reset/mysql");
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "重置成功",
            text: "系统配置已恢复默认",
            timer: 2000,
            showConfirmButton: false,
          });
          await this.loadSystemConfigs();
        } else {
          this.$swal.fire({
            icon: "error",
            title: "重置失败",
            text: res.data.message || "",
          });
        }
      } catch (e) {
        this.$swal.fire({
          icon: "error",
          title: "重置失败",
          text: e.response?.data?.message || "",
        });
      } finally {
        this.resettingAll = false;
      }
    },

    showPasswordDialog(callback) {
      this.passwordForm.password = "";
      this.passwordDialogVisible = true;
      this.passwordCallback = callback;
    },

    cancelPasswordDialog() {
      this.passwordDialogVisible = false;
      this.passwordForm.password = "";
      this.passwordCallback = null;
    },

    async verifyPassword() {
      if (!this.passwordForm.password) {
        this.$message.warning("请输入管理员密码");
        return;
      }
      this.verifying = true;
      try {
        const res = await this.$axios.post("/system/config/verify-password", {
          password: this.passwordForm.password,
        });
        if (res.data.code === 200) {
          this.passwordVerified = true;
          this.passwordDialogVisible = false;
          this.$message.success("验证成功");
          if (this.passwordCallback) {
            this.passwordCallback();
            this.passwordCallback = null;
          }
        } else {
          this.$message.error("密码错误，验证失败");
        }
      } catch (e) {
        this.$message.error(": " + (e.response?.data?.message || e.message));
      } finally {
        this.verifying = false;
      }
    },

    // ==================== AI ====================
    handleAiTabChange(tab) {
      if (tab === "provider") {
        this.loadAiConfig();
      }
    },

    async loadAiConfig() {
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
          providersList.forEach((p) => {
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
        const res = await this.$axios.post("/ai/config/switch-provider", {
          provider,
        });
        if (res.data.code === 200) {
          this.$message.success("切换服务商成功");
          this.loadAiConfig();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("切换服务商失败");
      }
    },

    async saveAiConfig() {
      this.aiSaving = true;
      try {
        const res = await this.$axios.post("/ai/config/update", this.aiConfig);
        if (res.data.code === 200) {
          this.$message.success("AI 配置保存成功");
          this.lastSaveTime = new Date().toLocaleString();
          this.loadAiConfig();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("AI 配置保存失败");
      } finally {
        this.aiSaving = false;
      }
    },

    // ====================  ====================
    handleVoiceTabChange(tab) {
      //  tab
      console.log(" tab:", tab);
    },

    async loadVoiceConfig() {
      try {
        const res = await this.$axios.get("/voice/config");
        if (res.data.code === 200) {
          const data = res.data.data;
          if (data.asr) this.voiceConfig.asr = data.asr;
          if (data.tts) this.voiceConfig.tts = data.tts;
          if (data.vad) this.voiceConfig.vad = data.vad;
        }
      } catch (e) {
        console.error("加载语音配置失败:", e);
      }
    },

    async saveVoiceConfig() {
      this.voiceSaving = true;
      try {
        const res = await this.$axios.post(
          "/voice/config",
          this.voiceConfig
        );
        if (res.data.code === 200) {
          this.$message.success("语音配置保存成功");
          this.lastSaveTime = new Date().toLocaleString();
          // 保存后重新加载，确保回显值与后端一致（避免脱敏串残留）
          await this.loadVoiceConfig();
        } else {
          this.$message.error(res.data.msg || "保存失败");
        }
      } catch (e) {
        this.$message.error("语音配置保存失败");
      } finally {
        this.voiceSaving = false;
      }
    },

    /**
     * 服务端语音链路连通性自测。
     * 探测发生在后端（服务端出网），与浏览器能否访问外网无关——
     * 因此即使浏览器能上网、服务端被防火墙拦掉，这里也会如实报失败。
     */
    async testVoiceConnection(type) {
      this.voiceTesting = true;
      this.voiceTestResult = null;
      try {
        const res = await this.$axios.post("/voice/test", null, {
          params: { type },
        });
        const data = res.data && res.data.data;
        if (data) {
          this.voiceTestResult = data;
          if (data.success) {
            this.$message.success(
              `${data.provider} 链路正常（${data.durationMs}ms）`
            );
          } else {
            this.$message.error(`${data.provider} 链路失败：${data.message}`);
          }
        } else {
          this.$message.error(res.data.msg || "自测请求失败");
        }
      } catch (e) {
        this.$message.error("自测请求异常，请检查后端服务");
      } finally {
        this.voiceTesting = false;
      }
    },

    onAsrProviderChange(provider) {
      //  provider
      if (provider === "funasr") {
        this.voiceConfig.asr.apiUrl =
          "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition";
        this.voiceConfig.asr.model = "paraformer-zh";
      } else if (provider === "whisper") {
        // 后端按 baseUrl + /audio/transcriptions 拼接，这里只给基址（留空则用 OpenAI 官方）
        this.voiceConfig.asr.baseUrl = "https://api.openai.com/v1";
        this.voiceConfig.asr.apiUrl =
          "https://api.openai.com/v1/audio/transcriptions";
        this.voiceConfig.asr.model = "whisper-1";
      } else if (provider === "qwen") {
        this.voiceConfig.asr.apiUrl =
          "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition";
        this.voiceConfig.asr.model = "paraformer-zh";
      }
    },

    onTtsProviderChange(provider) {
      //  provider
      if (provider === "edgetts") {
        this.voiceConfig.tts.voice = "zh-CN-XiaoxiaoNeural";
        this.voiceConfig.tts.apiUrl = "";
      } else if (provider === "cosyvoice") {
        this.voiceConfig.tts.voice = "cosyvoice-v1";
        this.voiceConfig.tts.apiUrl =
          "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2audio/generation";
      } else if (provider === "minimax") {
        this.voiceConfig.tts.voice = "male-qn-qingse";
        this.voiceConfig.tts.apiUrl = "https://api.minimax.chat/v1/t2a_v2";
      }
    },

    // ==================== AI ====================
    async loadDoctorConfigs() {
      this.doctorLoading = true;
      try {
        const res = await this.$axios.get("/ai/config/list");
        if (res.data.code === 200) {
          this.doctorList = res.data.data || [];
        }
      } catch (e) {
        console.error("AI", e);
      } finally {
        this.doctorLoading = false;
      }
    },

    selectDoctor(doctor) {
      this.selectedDoctor = doctor.key;
      this.currentDoctorConfig = doctor;
      // 从后端拉取完整运行时参数（maxTokens / presencePenalty / frequencyPenalty）
      this.$axios
        .get(`/ai/config/${doctor.key}`)
        .then((res) => {
          if (res.data.code === 200 && res.data.data) {
            const d = res.data.data;
            this.currentDoctorConfig = { ...this.currentDoctorConfig, ...d };
            this.doctorEditForm = {
              systemPrompt: d.systemPrompt || "",
              temperature: d.temperature != null ? Number(d.temperature) : 0.5,
              topP: d.topP != null ? Number(d.topP) : 0.5,
              maxTokens: d.maxTokens != null ? Number(d.maxTokens) : 2048,
              presencePenalty:
                d.presencePenalty != null ? Number(d.presencePenalty) : 0,
              frequencyPenalty:
                d.frequencyPenalty != null ? Number(d.frequencyPenalty) : 0,
              repetitionPenalty:
                d.repetitionPenalty != null ? Number(d.repetitionPenalty) : 1.0,
              contextRounds:
                d.contextRounds != null ? Number(d.contextRounds) : 5,
              maxReplyLength:
                d.maxReplyLength != null ? Number(d.maxReplyLength) : 2048,
            };
            return;
          }
          this.doctorEditForm = this.makeFallbackDoctorForm(doctor);
        })
        .catch(() => {
          this.doctorEditForm = this.makeFallbackDoctorForm(doctor);
        });
    },
    makeFallbackDoctorForm(doctor) {
      return {
        systemPrompt: doctor.systemPrompt || "",
        temperature: doctor.temperature != null ? Number(doctor.temperature) : 0.5,
        topP: doctor.topP != null ? Number(doctor.topP) : 0.5,
        maxTokens: doctor.maxTokens != null ? Number(doctor.maxTokens) : 2048,
        presencePenalty:
          doctor.presencePenalty != null ? Number(doctor.presencePenalty) : 0,
        frequencyPenalty:
          doctor.frequencyPenalty != null
            ? Number(doctor.frequencyPenalty)
            : 0,
        repetitionPenalty:
          doctor.repetitionPenalty != null ? Number(doctor.repetitionPenalty) : 1.0,
        contextRounds:
          doctor.contextRounds != null ? Number(doctor.contextRounds) : 5,
        maxReplyLength:
          doctor.maxReplyLength != null ? Number(doctor.maxReplyLength) : 2048,
      };
    },

    async saveDoctorConfig() {
      if (!this.doctorEditForm.systemPrompt.trim()) {
        this.$message.warning("请填写系统提示词");
        return;
      }
      // 后端 /ai/config/{role} PUT 需要管理员密码验证
      const { value: password } = await this.$swal.fire({
        title: "保存智能体配置",
        html: `<p style="margin-bottom:12px">将保存 <b>${this.currentDoctorConfig.name}</b> 的配置</p>`,
        input: "password",
        inputLabel: "管理员密码",
        inputPlaceholder: "请输入管理员密码",
        inputAttributes: { autocapitalize: "off", autocorrect: "off" },
        showCancelButton: true,
        confirmButtonText: "确认保存",
        cancelButtonText: "取消",
        customClass: { confirmButton: "swal2-btn-primary" },
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        },
      });
      if (!password) return;

      this.doctorSaving = true;
      try {
        const res = await this.$axios.put(
          `/ai/config/${this.selectedDoctor}`,
          { ...this.doctorEditForm, password }
        );
        if (res.data.code === 200) {
          this.$message.success("智能体配置保存成功");
          this.lastSaveTime = new Date().toLocaleString();
          await this.loadDoctorConfigs();
          const doctor = this.doctorList.find(
            (d) => d.key === this.selectedDoctor
          );
          if (doctor) this.selectDoctor(doctor);
        } else {
          this.$message.error(res.data.msg || res.data.message || "保存失败");
        }
      } catch (e) {
        this.$message.error(
          "保存失败: " + (e.response?.data?.msg || e.response?.data?.message || e.message)
        );
      } finally {
        this.doctorSaving = false;
      }
    },

    async resetDoctorConfig() {
      const { value: password } = await this.$swal.fire({
        title: "重置智能体配置",
        html: `<p style="margin-bottom:12px"> 将把 <b>${this.currentDoctorConfig.name}</b> 恢复为默认配置 </p>`,
        input: "password",
        inputLabel: "管理员密码",
        inputPlaceholder: "请输入管理员密码",
        inputAttributes: { autocapitalize: "off", autocorrect: "off" },
        showCancelButton: true,
        confirmButtonText: "确认重置",
        cancelButtonText: "取消",
        customClass: { confirmButton: "swal2-btn-primary" },
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        },
      });

      if (!password) return;

      this.doctorResetting = true;
      try {
        const res = await this.$axios.post(
          `/ai/config/${this.selectedDoctor}/reset`,
          { password }
        );
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "重置成功",
            text: `${this.currentDoctorConfig.name} 已恢复默认配置`,
            timer: 1500,
            showConfirmButton: false,
          });
          await this.loadDoctorConfigs();
          const doctor = this.doctorList.find(
            (d) => d.key === this.selectedDoctor
          );
          if (doctor) this.selectDoctor(doctor);
        } else {
          this.$swal.fire({
            icon: "error",
            title: "重置失败",
            text: res.data.message || "",
          });
        }
      } catch (e) {
        this.$swal.fire({
          icon: "error",
          title: "重置失败",
          text: e.response?.data?.message || "",
        });
      } finally {
        this.doctorResetting = false;
      }
    },

    // ==================== 模型管理 ====================
    async loadModelList() {
      this.modelLoading = true;
      try {
        const res = await this.$axios.get("/ai/config/models");
        if (res.data.code === 200) {
          // 后端返回的列表，给每个模型加上优先级字段（默认按原始顺序）
          this.modelList = (res.data.data || []).map((item, index) => ({
            ...item,
            priority: item.priority || (index + 1) * 10,
          }));
        }
      } catch (e) {
        console.error("加载模型列表失败", e);
        this.$message.error("加载模型列表失败");
      } finally {
        this.modelLoading = false;
      }
    },

    async switchModel(row) {
      this.modelSwitching = true;
      try {
        const res = await this.$axios.post("/ai/config/switch-model", {
          providerKey: row.providerKey,
          model: row.models?.[0] || "",
        });
        if (res.data.code === 200) {
          this.$message.success(`已切换到 ${row.providerName}`);
          await this.loadModelList();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("切换失败: " + (e.response?.data?.message || e.message));
      } finally {
        this.modelSwitching = false;
      }
    },

    showAddModelDialog() {
      // 弹出添加模型对话框
      this.$prompt("请输入模型标识（如 deepseek）", "添加模型", {
        confirmButtonText: "添加",
        cancelButtonText: "取消",
        inputPlaceholder: "模型标识",
        inputValidator: (val) => {
          if (!val) return "请输入模型标识";
          if (this.modelList.some((m) => m.providerKey === val)) {
            return "该模型标识已存在";
          }
          return true;
        },
      }).then(({ value }) => {
        if (value) {
          // 检查是否在已知厂商列表中
          if (this.providers[value]) {
            const provider = this.providers[value];
            this.modelList.push({
              providerKey: value,
              providerName: provider.name,
              models: provider.models || [],
              current: false,
              priority: this.modelList.length + 1,
            });
            this.$message.success(`已添加 ${provider.name}`);
          } else {
            this.$message.warning("未找到该厂商配置，请先在「AI 服务商」中配置");
          }
        }
      }).catch(() => {});
    },

    removeModel(row) {
      this.$confirm(`确定要移除 ${row.providerName} 吗？`, "移除确认", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(() => {
        this.modelList = this.modelList.filter(
          (m) => m.providerKey !== row.providerKey
        );
        this.$message.success(`已移除 ${row.providerName}`);
      }).catch(() => {});
    },

    updateModelPriority(row) {
      // 优先级修改后自动排序
      this.modelList.sort((a, b) => (a.priority || 999) - (b.priority || 999));
    },

    async loadAnnouncements() {
      this.announcementLoading = true;
      try {
        const res = await this.$axios.get("/ai/announcement/list");
        if (res.data.code === 200) {
          this.announcementList = res.data.data || [];
        }
      } catch (e) {
        console.error("", e);
      } finally {
        this.announcementLoading = false;
      }
    },

    async saveAnnouncement() {
      if (!this.announcementForm.title) {
        this.$message.warning("请填写横幅标题");
        return;
      }
      this.announcementSaving = true;
      try {
        const res = await this.$axios.post(
          "/ai/announcement/save",
          this.announcementForm
        );
        if (res.data.code === 200) {
          this.$message.success("横幅保存成功");
          this.announcementForm = {
            modelKey: "zhikangyun-local",
            title: "",
            content: "",
            bgColor: "#67C23A",
            isActive: 0,
          };
          await this.loadAnnouncements();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error(": " + (e.response?.data?.message || e.message));
      } finally {
        this.announcementSaving = false;
      }
    },

    editAnnouncement(row) {
      this.announcementForm = {
        id: row.id,
        modelKey: row.modelKey,
        title: row.title,
        content: row.content,
        bgColor: row.bgColor || "#67C23A",
        isActive: row.isActive,
      };
    },

    async deleteAnnouncement(row) {
      try {
        const res = await this.$axios.post("/ai/announcement/delete", {
          id: row.id,
        });
        if (res.data.code === 200) {
          this.$message.success("横幅删除成功");
          await this.loadAnnouncements();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error(": " + (e.response?.data?.message || e.message));
      }
    },
  },
};
</script>

<style scoped>
.system-config-manage {
  padding: 20px;
}

/* 语音连通性自测结果 */
.voice-test-result {
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.voice-test-result.is-ok {
  color: #67c23a;
}

.voice-test-result.is-fail {
  color: #f56c6c;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 4px;
}

.page-header .subtitle {
  font-size: 14px;
  color: #8c8c8c;
}

.page-header-actions {
  display: flex;
  gap: 8px;
}

.config-group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
}

.group-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
}

.config-form {
  max-width: 800px;
}

.sensitive-input {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sensitive-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #e6a23c;
  font-size: 12px;
  white-space: nowrap;
}

.config-default {
  margin-left: 12px;
  font-size: 12px;
  color: #8c8c8c;
}

.config-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #f0f2f5;
}

.config-status {
  margin-top: 20px;
  text-align: right;
}

.config-status .el-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* AI */
.doctor-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.doctor-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  border: 2px solid transparent;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.2s;
}

.doctor-card:hover {
  border-color: #667eea;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.15);
}

.doctor-card--active {
  border-color: #667eea;
  background: linear-gradient(
    135deg,
    rgba(102, 126, 234, 0.05),
    rgba(118, 75, 162, 0.05)
  );
}

.doctor-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.doctor-card-title h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0;
}

.doctor-card-title p {
  font-size: 13px;
  color: #8c8c8c;
  margin: 4px 0 0;
}

.doctor-card-params {
  display: flex;
  gap: 12px;
}

.param-item {
  font-size: 12px;
  color: #667eea;
  background: rgba(102, 126, 234, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.doctor-editor {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.doctor-editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f2f5;
}

.doctor-editor-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.doctor-editor-actions {
  display: flex;
  gap: 8px;
}

/* 模型管理工具栏 */
.model-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.model-manage {
  padding: 0;
}
</style>
