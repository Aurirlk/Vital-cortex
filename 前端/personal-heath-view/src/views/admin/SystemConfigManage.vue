<template>
  <div class="system-config-manage">
    <!--  -->
    <el-dialog
      v-model="passwordDialogVisible"
      title=""
      width="400px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <el-form :model="passwordForm" label-width="80px">
        <el-form-item label="">
          <el-input
            v-model="passwordForm.password"
            type="password"
            placeholder=""
            show-password
            @keyup.enter="verifyPassword"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelPasswordDialog"></el-button>
        <el-button type="primary" @click="verifyPassword" :loading="verifying">
          
        </el-button>
      </template>
    </el-dialog>

    <!--  -->
    <div class="page-header">
      <div class="page-header-left">
        <h2></h2>
        <span class="subtitle"></span>
      </div>
      <div class="page-header-actions">
        <el-button type="warning" @click="resetAllConfigs" :loading="resettingAll">
          <el-icon><RefreshRight /></el-icon>
          
        </el-button>
      </div>
    </div>

    <!--  -->
    <el-tabs v-model="mainTab" type="border-card" @tab-change="handleMainTabChange">
      <!-- ============  ============ -->
      <el-tab-pane label="" name="system">
        <el-tabs v-model="systemGroup" tab-position="left" @tab-change="handleSystemGroupChange">
          <el-tab-pane
            v-for="(configs, group) in systemConfigGroups"
            :key="group"
            :label="getGroupLabel(group)"
            :name="group"
          >
            <div class="config-group-header">
              <span class="group-title">{{ getGroupLabel(group) }}</span>
              <el-button type="primary" size="small" @click="saveSystemConfig(group)" :loading="saving">
                <el-icon><Check /></el-icon>
                
              </el-button>
            </div>

            <el-form :model="editSystemConfigs[group]" label-width="140px" class="config-form">
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
                    placeholder=""
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

      <!-- ============ LLM ============ -->
      <el-tab-pane label="LLM" name="ai">
        <el-tabs v-model="aiTab" @tab-change="handleAiTabChange">
          <!-- AI -->
          <el-tab-pane label="" name="provider">
            <el-form :model="aiConfig" label-width="140px" class="config-form">
              <el-divider content-position="left">AI</el-divider>
              
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

              <el-divider content-position="left"></el-divider>
              
              <el-form-item label="API Key">
                <el-input 
                  v-model="aiConfig.chat.apiKey" 
                  placeholder="API Key"
                  show-password
                />
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

              <el-divider content-position="left"></el-divider>
              
              <el-form-item label="API Key">
                <el-input 
                  v-model="aiConfig.reasoner.apiKey" 
                  placeholder="API Key"
                  show-password
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

              <el-divider content-position="left">Embedding</el-divider>
              
              <el-form-item label="API Key">
                <el-input 
                  v-model="aiConfig.embedding.apiKey" 
                  placeholder="API Key"
                  show-password
                />
              </el-form-item>
              
              <el-form-item label="">
                <el-input 
                  v-model="aiConfig.embedding.model" 
                  placeholder="Embedding"
                />
              </el-form-item>

              <el-divider content-position="left"></el-divider>
              
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

            <div class="config-actions">
              <el-button type="primary" @click="saveAiConfig" :loading="aiSaving">
                <el-icon><Check /></el-icon> 
              </el-button>
              <el-button @click="loadAiConfig">
                <el-icon><Refresh /></el-icon> 
              </el-button>
            </div>
          </el-tab-pane>

          <!--  -->
          <el-tab-pane label="" name="websearch">
            <el-form :model="aiConfig" label-width="140px" class="config-form">
              <el-divider content-position="left"></el-divider>
              
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

              <el-divider content-position="left">AI</el-divider>
              
              <el-form-item label=" API Key">
                <el-input 
                  v-model="aiConfig.webSearch.bocha.apiKey" 
                  placeholder="AIAPI Key"
                  show-password
                />
              </el-form-item>
              
              <el-form-item label=" API ">
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
              <el-button type="primary" @click="saveAiConfig" :loading="aiSaving">
                <el-icon><Check /></el-icon> 
              </el-button>
              <el-button @click="loadAiConfig">
                <el-icon><Refresh /></el-icon> 
              </el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>

      <!-- ============  ============ -->
      <el-tab-pane label="" name="voice">
        <el-tabs v-model="voiceTab" @tab-change="handleVoiceTabChange">
          <!-- ASR  -->
          <el-tab-pane label=" (ASR)" name="asr">
            <el-form :model="voiceConfig.asr" label-width="140px" class="config-form">
              <el-divider content-position="left">ASR Provider </el-divider>
              
              <el-form-item label="">
                <el-select 
                  v-model="voiceConfig.asr.provider" 
                  style="width: 100%"
                  @change="onAsrProviderChange"
                >
                  <el-option label="FunASR ()" value="funasr" />
                  <el-option label="OpenAI Whisper" value="whisper" />
                  <el-option label=" ASR" value="qwen" />
                </el-select>
              </el-form-item>

              <el-form-item label="API Key">
                <el-input 
                  v-model="voiceConfig.asr.apiKey" 
                  placeholder=" DashScope API Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="API ">
                <el-input 
                  v-model="voiceConfig.asr.apiUrl" 
                  placeholder="https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition"
                />
              </el-form-item>

              <el-form-item label="">
                <el-select v-model="voiceConfig.asr.model" style="width: 100%" allow-create filterable>
                  <el-option label="paraformer-zh ()" value="paraformer-zh" />
                  <el-option label="paraformer-v2" value="paraformer-v2" />
                  <el-option label="whisper-1" value="whisper-1" />
                </el-select>
              </el-form-item>

              <el-form-item label="">
                <el-select v-model="voiceConfig.asr.language" style="width: 100%">
                  <el-option label=" (zh-CN)" value="zh-CN" />
                  <el-option label=" (en-US)" value="en-US" />
                  <el-option label="" value="auto" />
                </el-select>
              </el-form-item>

              <el-form-item label=" (ms)">
                <el-input-number 
                  v-model="voiceConfig.asr.timeout" 
                  :min="5000" 
                  :max="120000" 
                  :step="5000"
                />
              </el-form-item>

              <el-divider content-position="left">Provider </el-divider>
              <el-alert
                v-if="voiceConfig.asr.provider === 'funasr'"
                title="FunASR ( DashScope)"
                description=" DashScope API Key 500 "
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else-if="voiceConfig.asr.provider === 'whisper'"
                title="OpenAI Whisper"
                description="OpenAI  OpenAI API Key"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else
                title=" ASR"
                description=" DashScope API Key"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
            </el-form>
          </el-tab-pane>

          <!-- TTS  -->
          <el-tab-pane label=" (TTS)" name="tts">
            <el-form :model="voiceConfig.tts" label-width="140px" class="config-form">
              <el-divider content-position="left">TTS Provider </el-divider>
              
              <el-form-item label="">
                <el-select 
                  v-model="voiceConfig.tts.provider" 
                  style="width: 100%"
                  @change="onTtsProviderChange"
                >
                  <el-option label="EdgeTTS (, )" value="edgetts" />
                  <el-option label="CosyVoice ()" value="cosyvoice" />
                  <el-option label="MiniMax TTS" value="minimax" />
                </el-select>
              </el-form-item>

              <el-form-item label="/" v-if="voiceConfig.tts.provider === 'edgetts'">
                <el-select v-model="voiceConfig.tts.voice" style="width: 100%">
                  <el-option label=" (, )" value="zh-CN-XiaoxiaoNeural" />
                  <el-option label=" ()" value="zh-CN-YunxiNeural" />
                  <el-option label=" ()" value="zh-CN-YunjianNeural" />
                  <el-option label=" ()" value="zh-CN-XiaoyiNeural" />
                  <el-option label=" ()" value="zh-CN-YunyangNeural" />
                </el-select>
              </el-form-item>

              <el-form-item label="API Key" v-else>
                <el-input 
                  v-model="voiceConfig.tts.apiKey" 
                  placeholder=" TTS API Key"
                  show-password
                />
              </el-form-item>

              <el-form-item label="">
                <el-slider 
                  v-model="voiceConfig.tts.speed" 
                  :min="0.5" 
                  :max="2.0" 
                  :step="0.1"
                  show-stops
                  :format-tooltip="val => val.toFixed(1) + 'x'"
                />
              </el-form-item>

              <el-form-item label="">
                <el-slider 
                  v-model="voiceConfig.tts.volume" 
                  :min="0" 
                  :max="100" 
                  :step="10"
                />
              </el-form-item>

              <el-form-item label="">
                <el-radio-group v-model="voiceConfig.tts.format">
                  <el-radio label="mp3">MP3</el-radio>
                  <el-radio label="wav">WAV</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item label=" (ms)">
                <el-input-number 
                  v-model="voiceConfig.tts.timeout" 
                  :min="10000" 
                  :max="300000" 
                  :step="10000"
                />
              </el-form-item>

              <el-divider content-position="left">Provider </el-divider>
              <el-alert
                v-if="voiceConfig.tts.provider === 'edgetts'"
                title="EdgeTTS (, )"
                description=" Edge TTS  edge-ttspip install edge-tts"
                type="success"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else-if="voiceConfig.tts.provider === 'cosyvoice'"
                title="CosyVoice ()"
                description=" DashScope API Key"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
              <el-alert
                v-else
                title="MiniMax TTS"
                description="MiniMax  MiniMax API Key"
                type="info"
                show-icon
                :closable="false"
                style="margin-bottom: 20px"
              />
            </el-form>
          </el-tab-pane>

          <!-- VAD  -->
          <el-tab-pane label="VAD " name="vad">
            <el-form :model="voiceConfig.vad" label-width="140px" class="config-form">
              <el-divider content-position="left"> (VAD)</el-divider>
              
              <el-form-item label=" VAD">
                <el-switch 
                  v-model="voiceConfig.vad.enabled"
                  active-text=""
                  inactive-text=""
                />
              </el-form-item>

              <el-form-item label="" v-if="voiceConfig.vad.enabled">
                <el-slider 
                  v-model="voiceConfig.vad.sensitivity" 
                  :min="0.1" 
                  :max="1.0" 
                  :step="0.1"
                  show-stops
                  :format-tooltip="val => (val * 100).toFixed(0) + '%'"
                />
                <div class="slider-tip">
                  <span> ()</span>
                  <span> ()</span>
                </div>
              </el-form-item>

              <el-divider content-position="left">VAD </el-divider>
              <el-alert
                title=" (Voice Activity Detection)"
                description="VAD 1.  2.  3.  TTS "
                type="info"
                show-icon
                :closable="false"
              />
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <!-- / -->
        <div class="config-actions">
          <!-- MM-01 整改：后端不存在 /ai/voice/config/get|update 端点（语音模块为空壳），
               保存必然失败。在语音后端落地前禁用保存，避免管理员填写大量配置后
               被 404 静默吞掉。 -->
          <el-tooltip content="语音后端尚未实现，配置暂无法保存" placement="top">
            <span>
              <el-button type="primary" disabled :loading="saving">
                <el-icon><Check /></el-icon> 
              </el-button>
            </span>
          </el-tooltip>
          <el-button @click="loadVoiceConfig">
            <el-icon><Refresh /></el-icon> 
          </el-button>
        </div>
      </el-tab-pane>

      <!-- ============ 模型管理 & 横幅通知 ============ -->
      <el-tab-pane label="模型管理" name="model">
        <div class="model-manage">
          <el-alert
            title="切换模型后 C 端用户刷新页面即可看到效果"
            description="从 13 个可用模型中选择一个上线，并可配置 C 端通知横幅"
            type="info"
            show-icon
            :closable="false"
            style="margin-bottom: 20px"
          />

          <!-- 模型列表 -->
          <el-table :data="modelList" style="width: 100%" v-loading="modelLoading">
            <el-table-column prop="providerName" label="厂商名称" width="200" />
            <el-table-column prop="providerKey" label="标识" width="160" />
            <el-table-column label="可用模型">
              <template #default="{ row }">
                <el-tag
                  v-for="model in row.models"
                  :key="model"
                  size="small"
                  style="margin-right: 4px"
                >
                  {{ model }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.current ? 'success' : 'info'" size="small">
                  {{ row.current ? '在线中' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
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
              </template>
            </el-table-column>
          </el-table>

          <!-- 横幅通知配置 -->
          <el-divider content-position="left">横幅通知配置</el-divider>
          <el-form :model="announcementForm" label-width="120px" class="config-form" style="max-width: 600px">
            <el-form-item label="关联模型">
              <el-select v-model="announcementForm.modelKey" style="width: 100%">
                <el-option
                  v-for="item in modelList"
                  :key="item.providerKey"
                  :label="item.providerName"
                  :value="item.providerKey"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="横幅标题">
              <el-input v-model="announcementForm.title" placeholder="如：本草大模型已上线" />
            </el-form-item>
            <el-form-item label="横幅描述">
              <el-input v-model="announcementForm.content" type="textarea" :rows="3" placeholder="如：基于Qwen2.5-7B微调的医疗领域模型" />
            </el-form-item>
            <el-form-item label="背景颜色">
              <el-color-picker v-model="announcementForm.bgColor" />
            </el-form-item>
            <el-form-item label="立即启用">
              <el-switch v-model="announcementForm.isActive" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveAnnouncement" :loading="announcementSaving">
                保存横幅
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 现有横幅列表 -->
          <el-divider content-position="left">已保存的横幅</el-divider>
          <el-table :data="announcementList" style="width: 100%" v-loading="announcementLoading">
            <el-table-column prop="modelKey" label="关联模型" width="160" />
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="content" label="描述" show-overflow-tooltip />
            <el-table-column label="颜色" width="80">
              <template #default="{ row }">
                <div :style="{ width: '24px', height: '24px', borderRadius: '4px', backgroundColor: row.bgColor }"></div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isActive === 1 ? 'success' : 'info'" size="small">
                  {{ row.isActive === 1 ? '展示中' : '已关闭' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="editAnnouncement(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteAnnouncement(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ============ AI ============ -->
      <el-tab-pane label="AI" name="doctor">
        <div class="doctor-cards" v-loading="doctorLoading">
          <div
            v-for="doctor in doctorList"
            :key="doctor.key"
            class="doctor-card"
            :class="{ 'doctor-card--active': selectedDoctor === doctor.key }"
            @click="selectDoctor(doctor)"
          >
            <div class="doctor-card-header">
              <el-icon :size="28" color="#667eea"><component :is="doctor.icon" /></el-icon>
              <div class="doctor-card-title">
                <h3>{{ doctor.name }}</h3>
                <p>{{ doctor.description }}</p>
              </div>
            </div>
            <div class="doctor-card-params">
              <span class="param-item">Temp: {{ doctor.temperature }}</span>
              <span class="param-item">Top-P: {{ doctor.topP }}</span>
            </div>
          </div>
        </div>

        <div v-if="selectedDoctor" class="doctor-editor">
          <div class="doctor-editor-header">
            <h3>
              <el-icon><component :is="currentDoctorConfig.icon" /></el-icon>
              {{ currentDoctorConfig.name }} - 
            </h3>
            <div class="doctor-editor-actions">
              <el-button @click="resetDoctorConfig" :loading="doctorResetting">
                <el-icon><RefreshRight /></el-icon>
                
              </el-button>
              <el-button type="primary" @click="saveDoctorConfig" :loading="doctorSaving">
                <el-icon><Check /></el-icon>
                
              </el-button>
            </div>
          </div>

          <el-form :model="doctorEditForm" label-width="140px" class="config-form">
            <el-form-item label="">
              <el-input
                v-model="doctorEditForm.systemPrompt"
                type="textarea"
                :rows="12"
                placeholder="..."
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
          </el-form>
        </div>

        <el-empty v-else description="AI" />
      </el-tab-pane>
    </el-tabs>

    <!--  -->
    <div class="config-status" v-if="lastSaveTime">
      <el-tag type="success">
        <el-icon><Check /></el-icon>
        : {{ lastSaveTime }}
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
      aiSaving: false,

      // ======  ======
      voiceTab: "asr",

      // ======  ======
      modelList: [],
      modelLoading: false,
      modelSwitching: false,
      announcementList: [],
      announcementForm: {
        modelKey: 'zhikangyun-local',
        title: '',
        content: '',
        bgColor: '#67C23A',
        isActive: 1
      },
      announcementLoading: false,
      announcementSaving: false,

      voiceConfig: {
        asr: {
          provider: 'funasr',
          apiKey: '',
          apiUrl: 'https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition',
          model: 'paraformer-zh',
          language: 'zh-CN',
          timeout: 30000
        },
        tts: {
          provider: 'edgetts',
          apiKey: '',
          apiUrl: '',
          voice: 'zh-CN-XiaoxiaoNeural',
          speed: 1.0,
          volume: 100,
          format: 'mp3',
          timeout: 60000
        },
        vad: {
          enabled: true,
          sensitivity: 0.5
        }
      },
      voiceSaving: false,

      // ====== AI ======
      doctorLoading: false,
      doctorList: [],
      selectedDoctor: null,
      currentDoctorConfig: {},
      doctorEditForm: {
        systemPrompt: "",
        temperature: 0.5,
        topP: 0.5,
      },
      doctorSaving: false,
      doctorResetting: false,

      // 
      groupLabels: {
        mysql: "MySQL",
        server: "",
        websocket: "WebSocket",
        ota: "OTA",
        sqlite: "SQLite",
        jwt: "JWT",
        admin: "",
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
      if (tab === 'model') {
        this.loadModelList();
        this.loadAnnouncements();
      }
    },

    async loadSystemConfigs() {
      try {
        const res = await this.$axios.get("/system/config/all");
        if (res.data.code === 200) {
          const groups = res.data.data.groups || {};
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
        console.error("", e);
        this.$message.error("");
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
          this.$message.success("");
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
        title: "",
        html: `<p style="margin-bottom:12px"> <b></b> </p>
               <p style="color:#e6a23c;font-size:13px"> </p>`,
        input: "password",
        inputLabel: "",
        inputPlaceholder: "",
        inputAttributes: { autocapitalize: "off", autocorrect: "off" },
        showCancelButton: true,
        confirmButtonText: "",
        cancelButtonText: "",
        confirmButtonColor: "#e6a23c",
        inputValidator: (value) => { if (!value) return ""; },
      });

      if (!password) return;

      try {
        const verifyRes = await this.$axios.post("/system/config/verify-password", { password });
        if (verifyRes.data.code !== 200) {
          this.$swal.fire({ icon: "error", title: "", text: "" });
          return;
        }
      } catch (e) {
        this.$swal.fire({ icon: "error", title: "", text: "" });
        return;
      }

      this.resettingAll = true;
      try {
        const res = await this.$axios.post("/system/config/reset/mysql");
        if (res.data.code === 200) {
          this.$swal.fire({ icon: "success", title: "", text: "", timer: 2000, showConfirmButton: false });
          await this.loadSystemConfigs();
        } else {
          this.$swal.fire({ icon: "error", title: "", text: res.data.message || "" });
        }
      } catch (e) {
        this.$swal.fire({ icon: "error", title: "", text: e.response?.data?.message || "" });
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
        this.$message.warning("");
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
          this.$message.success("");
          if (this.passwordCallback) {
            this.passwordCallback();
            this.passwordCallback = null;
          }
        } else {
          this.$message.error("");
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
          this.loadAiConfig();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("");
      }
    },

    async saveAiConfig() {
      this.aiSaving = true;
      try {
        const res = await this.$axios.post("/ai/config/update", this.aiConfig);
        if (res.data.code === 200) {
          this.$message.success("AI");
          this.lastSaveTime = new Date().toLocaleString();
          this.loadAiConfig();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("AI");
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
        const res = await this.$axios.get("/ai/voice/config/get");
        if (res.data.code === 200) {
          const data = res.data.data;
          if (data.asr) this.voiceConfig.asr = data.asr;
          if (data.tts) this.voiceConfig.tts = data.tts;
          if (data.vad) this.voiceConfig.vad = data.vad;
        }
      } catch (e) {
        console.error(":", e);
      }
    },

    async saveVoiceConfig() {
      this.voiceSaving = true;
      try {
        const res = await this.$axios.post("/ai/voice/config/update", this.voiceConfig);
        if (res.data.code === 200) {
          this.$message.success("");
          this.lastSaveTime = new Date().toLocaleString();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error("");
      } finally {
        this.voiceSaving = false;
      }
    },

    onAsrProviderChange(provider) {
      //  provider 
      if (provider === 'funasr') {
        this.voiceConfig.asr.apiUrl = 'https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition';
        this.voiceConfig.asr.model = 'paraformer-zh';
      } else if (provider === 'whisper') {
        this.voiceConfig.asr.apiUrl = 'https://api.openai.com/v1/audio/transcriptions';
        this.voiceConfig.asr.model = 'whisper-1';
      } else if (provider === 'qwen') {
        this.voiceConfig.asr.apiUrl = 'https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition';
        this.voiceConfig.asr.model = 'paraformer-zh';
      }
    },

    onTtsProviderChange(provider) {
      //  provider 
      if (provider === 'edgetts') {
        this.voiceConfig.tts.voice = 'zh-CN-XiaoxiaoNeural';
        this.voiceConfig.tts.apiUrl = '';
      } else if (provider === 'cosyvoice') {
        this.voiceConfig.tts.voice = 'cosyvoice-v1';
        this.voiceConfig.tts.apiUrl = 'https://dashscope.aliyuncs.com/api/v1/services/aigc/text2audio/generation';
      } else if (provider === 'minimax') {
        this.voiceConfig.tts.voice = 'male-qn-qingse';
        this.voiceConfig.tts.apiUrl = 'https://api.minimax.chat/v1/t2a_v2';
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
      this.doctorEditForm = {
        systemPrompt: doctor.systemPrompt || "",
        temperature: doctor.temperature || 0.5,
        topP: doctor.topP || 0.5,
      };
    },

    async saveDoctorConfig() {
      if (!this.doctorEditForm.systemPrompt.trim()) {
        this.$message.warning("");
        return;
      }
      this.doctorSaving = true;
      try {
        const res = await this.$axios.put(`/ai/config/${this.selectedDoctor}`, this.doctorEditForm);
        if (res.data.code === 200) {
          this.$message.success("AI");
          this.lastSaveTime = new Date().toLocaleString();
          await this.loadDoctorConfigs();
          const doctor = this.doctorList.find(d => d.key === this.selectedDoctor);
          if (doctor) this.selectDoctor(doctor);
        } else {
          this.$message.error(res.data.message || "");
        }
      } catch (e) {
        this.$message.error(": " + (e.response?.data?.message || e.message));
      } finally {
        this.doctorSaving = false;
      }
    },

    async resetDoctorConfig() {
      const { value: password } = await this.$swal.fire({
        title: "",
        html: `<p style="margin-bottom:12px"> <b>${this.currentDoctorConfig.name}</b> </p>`,
        input: "password",
        inputLabel: "",
        inputPlaceholder: "",
        inputAttributes: { autocapitalize: "off", autocorrect: "off" },
        showCancelButton: true,
        confirmButtonText: "",
        cancelButtonText: "",
        confirmButtonColor: "#667eea",
        inputValidator: (value) => { if (!value) return ""; },
      });

      if (!password) return;

      this.doctorResetting = true;
      try {
        const res = await this.$axios.post(`/ai/config/${this.selectedDoctor}/reset`, { password });
        if (res.data.code === 200) {
          this.$swal.fire({ icon: "success", title: "", text: `${this.currentDoctorConfig.name} `, timer: 1500, showConfirmButton: false });
          await this.loadDoctorConfigs();
          const doctor = this.doctorList.find(d => d.key === this.selectedDoctor);
          if (doctor) this.selectDoctor(doctor);
        } else {
          this.$swal.fire({ icon: "error", title: "", text: res.data.message || "" });
        }
      } catch (e) {
        this.$swal.fire({ icon: "error", title: "", text: e.response?.data?.message || "" });
      } finally {
        this.doctorResetting = false;
      }
    },

    // ====================  ====================
    async loadModelList() {
      this.modelLoading = true;
      try {
        const res = await this.$axios.get("/ai/config/models");
        if (res.data.code === 200) {
          this.modelList = res.data.data || [];
        }
      } catch (e) {
        console.error("", e);
        this.$message.error("");
      } finally {
        this.modelLoading = false;
      }
    },

    async switchModel(row) {
      this.modelSwitching = true;
      try {
        const res = await this.$axios.post("/ai/config/switch-model", {
          providerKey: row.providerKey,
          model: row.models?.[0] || ''
        });
        if (res.data.code === 200) {
          this.$message.success(` ${row.providerName}`);
          await this.loadModelList();
        } else {
          this.$message.error(res.data.msg || "");
        }
      } catch (e) {
        this.$message.error(": " + (e.response?.data?.message || e.message));
      } finally {
        this.modelSwitching = false;
      }
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
        this.$message.warning("");
        return;
      }
      this.announcementSaving = true;
      try {
        const res = await this.$axios.post("/ai/announcement/save", this.announcementForm);
        if (res.data.code === 200) {
          this.$message.success("");
          this.$message.success("");
          this.announcementForm = {
            modelKey: 'zhikangyun-local',
            title: '',
            content: '',
            bgColor: '#67C23A',
            isActive: 0
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
        bgColor: row.bgColor || '#67C23A',
        isActive: row.isActive
      };
    },

    async deleteAnnouncement(row) {
      try {
        const res = await this.$axios.post("/ai/announcement/delete", { id: row.id });
        if (res.data.code === 200) {
          this.$message.success("");
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
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05), rgba(118, 75, 162, 0.05));
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
</style>
