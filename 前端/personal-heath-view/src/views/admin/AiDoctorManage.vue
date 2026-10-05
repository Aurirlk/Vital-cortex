<template>
  <div class="ai-doctor-manage">
    <div class="page-header">
      <div class="page-header-left">
        <h2>AI 医生管理</h2>
        <span class="subtitle">AI 医生诊断与配置管理</span>
      </div>
      <el-button
        type="warning"
        @click="resetAllConfigs"
        :loading="resettingAll"
      >
        <el-icon><RefreshRight /></el-icon>
        重置全部配置
      </el-button>
    </div>

    <div class="doctor-cards" v-loading="loading">
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
        </div>
      </div>
    </div>

    <!--  -->
    <div v-if="selectedDoctor" class="config-editor">
      <div class="config-editor-header">
        <h3>
          <el-icon><component :is="currentConfig.icon" /></el-icon>
          {{ currentConfig.name }} -
        </h3>
        <div class="config-editor-actions">
          <el-button @click="resetConfig" :loading="resetting">
            <el-icon><RefreshRight /></el-icon>
            重置
          </el-button>
          <el-button type="primary" @click="saveConfig" :loading="saving">
            <el-icon><Check /></el-icon>
            保存
          </el-button>
        </div>
      </div>

      <div class="config-form">
        <div class="config-section">
          <label class="config-label">系统提示词 (System Prompt)</label>
          <el-input
            v-model="editForm.systemPrompt"
            type="textarea"
            :rows="12"
            placeholder="请输入系统提示词"
          />
        </div>

        <div class="config-row">
          <div class="config-section config-section--half">
            <label class="config-label">
              Temperature
              <el-tooltip
                content="控制生成随机性，值越高输出越多样"
                placement="top"
              >
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </label>
            <el-slider
              v-model="editForm.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              show-input
            />
          </div>

          <div class="config-section config-section--half">
            <label class="config-label">
              Top-P
              <el-tooltip
                content="核采样阈值，控制候选词输出范围"
                placement="top"
              >
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </label>
            <el-slider
              v-model="editForm.topP"
              :min="0"
              :max="1"
              :step="0.05"
              show-input
            />
          </div>
        </div>

        <div class="config-row">
          <div class="config-section config-section--half">
            <label class="config-label">
              Max Tokens
              <el-tooltip
                content="单次回复最大 token 数；留空则继承全局设置"
                placement="top"
              >
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </label>
            <el-input-number
              v-model="editForm.maxTokens"
              :min="1"
              :max="8192"
              :step="128"
              controls-position="right"
              style="width: 100%"
            />
          </div>

          <div class="config-section config-section--half">
            <label class="config-label">
              Presence Penalty
              <el-tooltip
                content="存在惩罚：正值会让模型避免重复已提及的主题"
                placement="top"
              >
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </label>
            <el-slider
              v-model="editForm.presencePenalty"
              :min="-2"
              :max="2"
              :step="0.1"
              show-input
            />
          </div>
        </div>

        <div class="config-row">
          <div class="config-section config-section--half">
            <label class="config-label">
              Frequency Penalty
              <el-tooltip
                content="频率惩罚：正值会让模型减少重复用词"
                placement="top"
              >
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </label>
            <el-slider
              v-model="editForm.frequencyPenalty"
              :min="-2"
              :max="2"
              :step="0.1"
              show-input
            />
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else description="请选择 AI 医生进行配置" />
  </div>
</template>

<script>
export default {
  name: "AiDoctorManage",
  data() {
    return {
      doctorList: [],
      loading: false,
      selectedDoctor: null,
      currentConfig: {},
      defaultDoctorList: [
        {
          key: "doctor",
          name: "AI医生",
          description: "全科医疗咨询，解答症状、用药和就诊建议",
          icon: "FirstAidKit",
          temperature: 0.3,
          topP: 0.5,
          systemPrompt: "你是一位经验丰富的全科医生，能够根据患者描述的症状提供初步医疗建议、用药指导和就诊建议。严重情况请建议及时就医。",
        },
        {
          key: "nutritionist",
          name: "营养师",
          description: "饮食营养、慢病膳食调理与减重建议",
          icon: "Apple",
          temperature: 0.5,
          topP: 0.6,
          systemPrompt: "你是一位专业营养师，擅长根据用户的健康状况、饮食习惯和目标提供个性化的营养建议、膳食搭配和减重方案。",
        },
        {
          key: "psychologist",
          name: "心理顾问",
          description: "情绪疏导、压力管理与心理健康支持",
          icon: "Sunny",
          temperature: 0.6,
          topP: 0.7,
          systemPrompt: "你是一位温和的心理咨询师，善于倾听和情绪疏导，能够为用户提供压力管理、情绪调节和心理健康方面的支持性建议。",
        },
        {
          key: "analyst",
          name: "报告解读师",
          description: "体检报告、检查指标解读与健康趋势分析",
          icon: "DataAnalysis",
          temperature: 0.3,
          topP: 0.5,
          systemPrompt: "你是一位医学数据解读专家，能够帮助用户理解体检报告、化验指标和健康数据，给出通俗易懂的分析和改善建议。",
        },
        {
          key: "general_assistant",
          name: "健康助手",
          description: "通用健康问答与系统功能引导",
          icon: "Service",
          temperature: 0.7,
          topP: 0.8,
          systemPrompt: "你是智康云健康管理系统的智能助手，能够回答用户关于健康、系统功能、药品和养生方面的常见问题，并引导用户使用系统功能。",
        },
      ],
      editForm: {
        systemPrompt: "",
        temperature: 0.5,
        topP: 0.5,
        enableSearchDrug: true,
        enableSearchKnowledge: true,
        enableWebSearch: true,
        enableGetHealthData: true,
        enableGetChatHistory: true,
        enableExecuteSql: true,
        knowledgeCollections: [
          "health_knowledge",
          "report_templates",
          "nutrition_knowledge",
        ],
        maxRounds: 5,
      },
      saving: false,
      resetting: false,
      resettingAll: false,
    };
  },
  created() {
    this.loadConfigs();
  },
  methods: {
    async loadConfigs() {
      this.loading = true;
      try {
        const res = await this.$axios.get("/ai/config/list");
        if (res.data.code === 200 && Array.isArray(res.data.data) && res.data.data.length > 0) {
          this.doctorList = res.data.data;
        } else {
          // 接口为空或未实现 → 使用本地默认医生，保证页面有内容
          this.doctorList = JSON.parse(JSON.stringify(this.defaultDoctorList));
          if (res.data.code !== 200) {
            console.warn("AI 配置接口返回非 200，使用本地默认医生:", res.data.msg);
          }
        }
      } catch (e) {
        console.error("加载 AI 配置失败, 使用本地默认医生:", e);
        this.doctorList = JSON.parse(JSON.stringify(this.defaultDoctorList));
      } finally {
        this.loading = false;
      }
    },
    selectDoctor(doctor) {
      this.selectedDoctor = doctor.key;
      this.currentConfig = doctor;
      // 切换角色时调一次 /ai/config/{role} 拉取最新参数（含 presence/frequencyPenalty、maxTokens）
      this.$axios
        .get(`/ai/config/${doctor.key}`)
        .then((res) => {
          if (res.data.code === 200 && res.data.data) {
            const d = res.data.data;
            this.currentConfig = { ...this.currentConfig, ...d };
            this.editForm = {
              systemPrompt: d.systemPrompt || "",
              temperature: d.temperature != null ? Number(d.temperature) : 0.5,
              topP: d.topP != null ? Number(d.topP) : 0.5,
              maxTokens: d.maxTokens != null ? Number(d.maxTokens) : 2048,
              presencePenalty:
                d.presencePenalty != null ? Number(d.presencePenalty) : 0,
              frequencyPenalty:
                d.frequencyPenalty != null ? Number(d.frequencyPenalty) : 0,
            };
            return;
          }
          this.editForm = this.makeFallbackForm(doctor);
        })
        .catch(() => {
          this.editForm = this.makeFallbackForm(doctor);
        });
    },
    makeFallbackForm(doctor) {
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
      };
    },
    async saveConfig() {
      if (!this.editForm.systemPrompt.trim()) {
        this.$message.warning("请输入系统提示词");
        return;
      }
      const { value: password } = await this.$swal.fire({
        title: "保存配置",
        html: `<p style="margin-bottom:12px">保存 <b>${this.currentConfig.name}</b> 的配置</p>`,
        input: "password",
        inputLabel: "管理员密码",
        inputPlaceholder: "请输入管理员密码",
        inputAttributes: {
          autocapitalize: "off",
          autocorrect: "off",
        },
        showCancelButton: true,
        confirmButtonText: "确认保存",
        cancelButtonText: "取消",
        customClass: { confirmButton: "swal2-btn-primary" },
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        },
      });
      if (!password) return;

      this.saving = true;
      try {
        const res = await this.$axios.put(
          `/ai/config/${this.selectedDoctor}`,
          { ...this.editForm, password }
        );
        if (res.data.code === 200) {
          this.$message.success("保存成功");
          //
          const idx = this.doctorList.findIndex(
            (d) => d.key === this.selectedDoctor
          );
          if (idx !== -1) {
            this.doctorList[idx].systemPrompt = this.editForm.systemPrompt;
            this.doctorList[idx].temperature = this.editForm.temperature;
            this.doctorList[idx].topP = this.editForm.topP;
            this.currentConfig = this.doctorList[idx];
          }
        } else {
          this.$message.error(res.data.msg || res.data.message || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败: " + (e.response?.data?.msg || e.response?.data?.message || e.message));
      } finally {
        this.saving = false;
      }
    },
    async resetConfig() {
      const { value: password } = await this.$swal.fire({
        title: "确认重置配置",
        html: `<p style="margin-bottom:12px"> <b>${this.currentConfig.name}</b> </p>`,
        input: "password",
        inputLabel: "管理员密码",
        inputPlaceholder: "请输入管理员密码",
        inputAttributes: {
          autocapitalize: "off",
          autocorrect: "off",
        },
        showCancelButton: true,
        confirmButtonText: "确认重置",
        cancelButtonText: "取消",
        customClass: { confirmButton: "swal2-btn-primary" },
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        },
      });

      if (!password) return;

      this.resetting = true;
      try {
        const res = await this.$axios.post(
          `/ai/config/${this.selectedDoctor}/reset`,
          { password }
        );
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "重置成功",
            text: `${this.currentConfig.name} 已重置为默认配置`,
            timer: 1500,
            showConfirmButton: false,
          });
          await this.loadConfigs();
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
        this.resetting = false;
      }
    },
    async resetAllConfigs() {
      const { value: password } = await this.$swal.fire({
        title: "确认重置全部配置",
        html: `<p style="margin-bottom:12px"> <b>AI</b> </p>`,
        input: "password",
        inputLabel: "管理员密码",
        inputPlaceholder: "请输入管理员密码",
        inputAttributes: {
          autocapitalize: "off",
          autocorrect: "off",
        },
        showCancelButton: true,
        confirmButtonText: "确认重置",
        cancelButtonText: "取消",
        customClass: { confirmButton: "swal2-btn-warning" },
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        },
      });

      if (!password) return;

      this.resettingAll = true;
      try {
        const res = await this.$axios.post("/ai/config/reset-all", {
          password,
        });
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "重置成功",
            text: "已全部重置为默认配置",
            timer: 1500,
            showConfirmButton: false,
          });
          await this.loadConfigs();
          if (this.selectedDoctor) {
            const doctor = this.doctorList.find(
              (d) => d.key === this.selectedDoctor
            );
            if (doctor) this.selectDoctor(doctor);
          }
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
  },
};
</script>

<style scoped>
.ai-doctor-manage {
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

.config-editor {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.config-editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f2f5;
}

.config-editor-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-editor-actions {
  display: flex;
  gap: 8px;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-section--half {
  flex: 1;
}

.config-row {
  display: flex;
  gap: 24px;
}

.config-label {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  display: flex;
  align-items: center;
  gap: 4px;
}

.config-label .el-icon {
  color: #8c8c8c;
  cursor: help;
}
</style>
