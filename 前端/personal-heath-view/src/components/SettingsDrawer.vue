<template>
  <el-drawer
    v-model="visible"
    title=""
    direction="rtl"
    size="400px"
    :before-close="handleClose"
  >
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab -->
      <el-tab-pane label="" name="personalization">
        <el-form label-width="100px">
          <el-form-item label="">
            <el-select v-model="settings.theme" placeholder="" @change="handleThemeChange">
              <el-option label="" value="health-green" />
              <el-option label="" value="professional-blue" />
              <el-option label="" value="warm-orange" />
              <el-option label="" value="minimal-white" />
              <el-option label="" value="dark" />
              <el-option label="" value="eye-protection" />
            </el-select>
          </el-form-item>
          <el-form-item label="">
            <el-slider v-model="settings.fontSize" :min="12" :max="20" :step="1" show-stops />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Tab -->
      <el-tab-pane label="" name="network">
        <el-form label-width="100px">
          <el-form-item label="">
            <el-select v-model="settings.searchEngine" placeholder="">
              <el-option label="AI" value="bocha" />
              <el-option label="Tavily" value="tavily" />
              <el-option label="DuckDuckGo" value="duckduckgo" />
            </el-select>
          </el-form-item>
          <el-form-item label="">
            <el-input v-model="settings.proxy" placeholder="http://proxy:port" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- AI Tab -->
      <el-tab-pane label="AI" name="ai">
        <el-form label-width="100px">
          <el-form-item label="AI">
            <el-select v-model="settings.aiProvider" placeholder="AI">
              <el-option label="DeepSeek" value="deepseek" />
              <el-option label="" value="qwen" />
              <el-option label="Kimi" value="kimi" />
              <el-option label="GLM" value="glm" />
              <el-option label="" value="doubao" />
              <el-option label="MiniMax" value="minimax" />
            </el-select>
          </el-form-item>
          <el-form-item label="">
            <el-input v-model="settings.aiModel" placeholder="" />
          </el-form-item>
          <el-form-item label="">
            <el-slider v-model="settings.temperature" :min="0" :max="2" :step="0.1" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Tab -->
      <el-tab-pane label="" name="voice">
        <el-form label-width="120px">
          <el-divider content-position="left"></el-divider>
          
          <el-form-item label="">
            <el-switch 
              v-model="settings.voiceEnabled" 
              active-text=""
              inactive-text=""
            />
          </el-form-item>

          <el-form-item label="" v-if="settings.voiceEnabled">
            <el-switch 
              v-model="settings.autoPlayTts" 
              active-text=""
              inactive-text=""
            />
            <div class="form-tip">AI</div>
          </el-form-item>

          <el-form-item label="" v-if="settings.voiceEnabled">
            <el-select v-model="settings.ttsVoice" placeholder="">
              <el-option label=" (, )" value="zh-CN-XiaoxiaoNeural" />
              <el-option label=" ()" value="zh-CN-YunxiNeural" />
              <el-option label=" ()" value="zh-CN-YunjianNeural" />
              <el-option label=" ()" value="zh-CN-XiaoyiNeural" />
              <el-option label=" ()" value="zh-CN-YunyangNeural" />
            </el-select>
          </el-form-item>

          <el-form-item label="" v-if="settings.voiceEnabled">
            <el-slider 
              v-model="settings.ttsSpeed" 
              :min="0.5" 
              :max="2.0" 
              :step="0.1"
              :format-tooltip="val => val.toFixed(1) + 'x'"
            />
          </el-form-item>

          <el-divider content-position="left"></el-divider>
          
          <el-form-item label="">
            <el-switch 
              v-model="settings.pushToTalk" 
              active-text=""
              inactive-text=""
            />
            <div class="form-tip"></div>
          </el-form-item>

          <el-form-item label="" v-if="!settings.pushToTalk">
            <el-switch 
              v-model="settings.autoRecognize" 
              active-text=""
              inactive-text=""
            />
            <div class="form-tip"></div>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Tab -->
      <el-tab-pane label="" name="emotion">
        <el-form label-width="100px">
          <el-form-item label="">
            <el-switch v-model="settings.emotionAnalysis" />
          </el-form-item>
          <el-form-item label="">
            <el-switch v-model="settings.toneAdaptation" />
          </el-form-item>
          <el-form-item label="">
            <el-switch v-model="settings.antiWatering" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Tab -->
      <el-tab-pane label="" name="advanced">
        <el-form label-width="100px">
          <el-form-item label="">
            <el-select v-model="settings.logLevel" placeholder="">
              <el-option label="DEBUG" value="debug" />
              <el-option label="INFO" value="info" />
              <el-option label="WARN" value="warn" />
              <el-option label="ERROR" value="error" />
            </el-select>
          </el-form-item>
          <el-form-item label="">
            <el-switch v-model="settings.cacheEnabled" />
          </el-form-item>
          <el-form-item label="">
            <el-switch v-model="settings.monitoringEnabled" />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleReset"></el-button>
      <el-button type="primary" @click="handleSave"></el-button>
    </template>
  </el-drawer>
</template>

<script>
export default {
  name: 'SettingsDrawer',
  data() {
    return {
      visible: false,
      activeTab: 'personalization',
      settings: {
        theme: 'professional-blue',
        fontSize: 14,
        searchEngine: 'bocha',
        proxy: '',
        aiProvider: 'deepseek',
        aiModel: '',
        temperature: 0.7,
        voiceEnabled: true,
        autoPlayTts: true,
        ttsVoice: 'zh-CN-XiaoxiaoNeural',
        ttsSpeed: 1.0,
        pushToTalk: false,
        autoRecognize: true,
        emotionAnalysis: true,
        toneAdaptation: true,
        antiWatering: true,
        logLevel: 'info',
        cacheEnabled: true,
        monitoringEnabled: true
      }
    }
  },
  methods: {
    open() {
      this.visible = true
      this.loadSettings()
    },
    handleClose() {
      this.visible = false
    },
    handleThemeChange(theme) {
      document.documentElement.setAttribute('data-theme', theme)
    },
    handleSave() {
      localStorage.setItem('settings', JSON.stringify(this.settings))
      this.$message.success('')
      this.visible = false
    },
    handleReset() {
      this.settings = {
        theme: 'professional-blue',
        fontSize: 14,
        searchEngine: 'bocha',
        proxy: '',
        aiProvider: 'deepseek',
        aiModel: '',
        temperature: 0.7,
        voiceEnabled: true,
        autoPlayTts: true,
        ttsVoice: 'zh-CN-XiaoxiaoNeural',
        ttsSpeed: 1.0,
        pushToTalk: false,
        autoRecognize: true,
        emotionAnalysis: true,
        toneAdaptation: true,
        antiWatering: true,
        logLevel: 'info',
        cacheEnabled: true,
        monitoringEnabled: true
      }
      this.$message.info('')
    },
    loadSettings() {
      const saved = localStorage.getItem('settings')
      if (saved) {
        this.settings = { ...this.settings, ...JSON.parse(saved) }
      }
    }
  }
}
</script>

<style scoped>
.settings-drawer {
  padding: 20px;
}

.el-form-item {
  margin-bottom: 20px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.el-divider__text {
  font-size: 13px;
  color: #606266;
}
</style>
