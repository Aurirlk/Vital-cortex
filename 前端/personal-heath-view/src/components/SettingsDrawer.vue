<template>
  <el-drawer
    v-model="visible"
    title="偏好设置"
    direction="rtl"
    size="400px"
    :before-close="handleClose"
  >
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 个性化 Tab -->
      <el-tab-pane label="个性化" name="personalization">
        <el-form label-width="100px">
          <el-form-item label="主题风格">
            <el-select
              v-model="settings.theme"
              placeholder="请选择主题"
              @change="handleThemeChange"
            >
              <el-option label="健康绿" value="health-green" />
              <el-option label="专业蓝" value="professional-blue" />
              <el-option label="暖橙" value="warm-orange" />
              <el-option label="极简白" value="minimal-white" />
              <el-option label="深色模式" value="dark" />
              <el-option label="护眼模式" value="eye-protection" />
            </el-select>
          </el-form-item>
          <el-form-item label="字体大小">
            <el-slider
              v-model="settings.fontSize"
              :min="12"
              :max="20"
              :step="1"
              show-stops
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 网络 Tab -->
      <el-tab-pane label="网络" name="network">
        <el-form label-width="100px">
          <el-form-item label="搜索引擎">
            <el-select
              v-model="settings.searchEngine"
              placeholder="请选择搜索引擎"
            >
              <el-option label="博查 AI" value="bocha" />
              <el-option label="Tavily" value="tavily" />
              <el-option label="DuckDuckGo" value="duckduckgo" />
            </el-select>
          </el-form-item>
          <el-form-item label="代理地址">
            <el-input
              v-model="settings.proxy"
              placeholder="http://proxy:port"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- AI Tab -->
      <el-tab-pane label="AI 设置" name="ai">
        <el-form label-width="100px">
          <el-form-item label="AI 服务商">
            <el-select
              v-model="settings.aiProvider"
              placeholder="请选择 AI 服务商"
            >
              <el-option label="DeepSeek" value="deepseek" />
              <el-option label="通义千问" value="qwen" />
              <el-option label="Kimi" value="kimi" />
              <el-option label="智谱 GLM" value="glm" />
              <el-option label="豆包" value="doubao" />
              <el-option label="MiniMax" value="minimax" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="settings.aiModel" placeholder="例如：qwen-max" />
          </el-form-item>
          <el-form-item label="Temperature">
            <el-slider
              v-model="settings.temperature"
              :min="0"
              :max="2"
              :step="0.1"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 语音 Tab -->
      <el-tab-pane label="语音" name="voice">
        <el-form label-width="120px">
          <el-divider content-position="left">语音播报</el-divider>

          <el-form-item label="启用语音播报">
            <el-switch
              v-model="settings.voiceEnabled"
              active-text="开启"
              inactive-text="关闭"
            />
          </el-form-item>

          <el-form-item label="自动朗读回复" v-if="settings.voiceEnabled">
            <el-switch
              v-model="settings.autoPlayTts"
              active-text="开启"
              inactive-text="关闭"
            />
            <div class="form-tip">AI 回复生成后自动朗读</div>
          </el-form-item>

          <el-form-item label="发音人" v-if="settings.voiceEnabled">
            <el-select v-model="settings.ttsVoice" placeholder="请选择发音人">
              <el-option
                label="晓晓 (女声, 通用)"
                value="zh-CN-XiaoxiaoNeural"
              />
              <el-option label="云希 (男声, 自然)" value="zh-CN-YunxiNeural" />
              <el-option
                label="云健 (男声, 新闻)"
                value="zh-CN-YunjianNeural"
              />
              <el-option label="晓伊 (女童声)" value="zh-CN-XiaoyiNeural" />
              <el-option
                label="云扬 (男声, 客服)"
                value="zh-CN-YunyangNeural"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="语速" v-if="settings.voiceEnabled">
            <el-slider
              v-model="settings.ttsSpeed"
              :min="0.5"
              :max="2.0"
              :step="0.1"
              :format-tooltip="(val) => val.toFixed(1) + 'x'"
            />
          </el-form-item>

          <el-divider content-position="left">语音输入</el-divider>

          <el-form-item label="按住说话">
            <el-switch
              v-model="settings.pushToTalk"
              active-text="开启"
              inactive-text="关闭"
            />
            <div class="form-tip">按住麦克风按钮说话，松开自动发送</div>
          </el-form-item>

          <el-form-item label="自动识别语音" v-if="!settings.pushToTalk">
            <el-switch
              v-model="settings.autoRecognize"
              active-text="开启"
              inactive-text="关闭"
            />
            <div class="form-tip">说话结束后自动识别并发送</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 情感 Tab -->
      <el-tab-pane label="情感交互" name="emotion">
        <el-form label-width="100px">
          <el-form-item label="情绪分析">
            <el-switch v-model="settings.emotionAnalysis" />
          </el-form-item>
          <el-form-item label="语调适配">
            <el-switch v-model="settings.toneAdaptation" />
          </el-form-item>
          <el-form-item label="反灌水检测">
            <el-switch v-model="settings.antiWatering" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 高级 Tab -->
      <el-tab-pane label="高级" name="advanced">
        <el-form label-width="100px">
          <el-form-item label="日志级别">
            <el-select v-model="settings.logLevel" placeholder="请选择日志级别">
              <el-option label="DEBUG" value="debug" />
              <el-option label="INFO" value="info" />
              <el-option label="WARN" value="warn" />
              <el-option label="ERROR" value="error" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用缓存">
            <el-switch v-model="settings.cacheEnabled" />
          </el-form-item>
          <el-form-item label="启用监控">
            <el-switch v-model="settings.monitoringEnabled" />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleReset">重置</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-drawer>
</template>

<script>
export default {
  name: "SettingsDrawer",
  data() {
    return {
      visible: false,
      activeTab: "personalization",
      settings: {
        theme: "professional-blue",
        fontSize: 14,
        searchEngine: "bocha",
        proxy: "",
        aiProvider: "deepseek",
        aiModel: "",
        temperature: 0.7,
        voiceEnabled: true,
        autoPlayTts: true,
        ttsVoice: "zh-CN-XiaoxiaoNeural",
        ttsSpeed: 1.0,
        pushToTalk: false,
        autoRecognize: true,
        emotionAnalysis: true,
        toneAdaptation: true,
        antiWatering: true,
        logLevel: "info",
        cacheEnabled: true,
        monitoringEnabled: true,
      },
    };
  },
  methods: {
    open() {
      this.visible = true;
      this.loadSettings();
    },
    handleClose() {
      this.visible = false;
    },
    handleThemeChange(theme) {
      document.documentElement.setAttribute("data-theme", theme);
    },
    handleSave() {
      localStorage.setItem("settings", JSON.stringify(this.settings));
      this.$message.success("设置已保存");
      this.visible = false;
    },
    handleReset() {
      this.settings = {
        theme: "professional-blue",
        fontSize: 14,
        searchEngine: "bocha",
        proxy: "",
        aiProvider: "deepseek",
        aiModel: "",
        temperature: 0.7,
        voiceEnabled: true,
        autoPlayTts: true,
        ttsVoice: "zh-CN-XiaoxiaoNeural",
        ttsSpeed: 1.0,
        pushToTalk: false,
        autoRecognize: true,
        emotionAnalysis: true,
        toneAdaptation: true,
        antiWatering: true,
        logLevel: "info",
        cacheEnabled: true,
        monitoringEnabled: true,
      };
      this.$message.info("已恢复默认设置");
    },
    loadSettings() {
      const saved = localStorage.getItem("settings");
      if (saved) {
        this.settings = { ...this.settings, ...JSON.parse(saved) };
      }
    },
  },
};
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
