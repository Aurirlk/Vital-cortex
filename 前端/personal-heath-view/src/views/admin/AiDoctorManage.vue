<template>
  <div class="ai-doctor-manage">
    <div class="page-header">
      <div class="page-header-left">
        <h2>AI医生管理</h2>
        <span class="subtitle">管理AI医生角色的提示词和参数配置</span>
      </div>
      <el-button type="warning" @click="resetAllConfigs" :loading="resettingAll">
        <el-icon><RefreshRight /></el-icon>
        恢复所有默认
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

    <!-- 配置编辑区 -->
    <div v-if="selectedDoctor" class="config-editor">
      <div class="config-editor-header">
        <h3>
          <el-icon><component :is="currentConfig.icon" /></el-icon>
          {{ currentConfig.name }} - 配置编辑
        </h3>
        <div class="config-editor-actions">
          <el-button @click="resetConfig" :loading="resetting">
            <el-icon><RefreshRight /></el-icon>
            重置默认
          </el-button>
          <el-button type="primary" @click="saveConfig" :loading="saving">
            <el-icon><Check /></el-icon>
            保存配置
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
            placeholder="请输入系统提示词..."
          />
        </div>

        <div class="config-row">
          <div class="config-section config-section--half">
            <label class="config-label">
              Temperature
              <el-tooltip content="控制回复的随机性，值越高回复越多样" placement="top">
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
              <el-tooltip content="控制词汇选择的范围，值越高词汇越丰富" placement="top">
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
      </div>
    </div>

    <el-empty v-else description="请先选择一个AI医生角色进行配置" />
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
        knowledgeCollections: ["health_knowledge", "report_templates", "nutrition_knowledge"],
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
        if (res.data.code === 200) {
          this.doctorList = res.data.data || [];
        }
      } catch (e) {
        console.error("加载AI医生配置失败", e);
        this.$message.error("加载配置失败");
      } finally {
        this.loading = false;
      }
    },
    selectDoctor(doctor) {
      this.selectedDoctor = doctor.key;
      this.currentConfig = doctor;
      this.editForm = {
        systemPrompt: doctor.systemPrompt || "",
        temperature: doctor.temperature || 0.5,
        topP: doctor.topP || 0.5,
      };
    },
    async saveConfig() {
      if (!this.editForm.systemPrompt.trim()) {
        this.$message.warning("系统提示词不能为空");
        return;
      }
      this.saving = true;
      try {
        const res = await this.$axios.put(`/ai/config/${this.selectedDoctor}`, this.editForm);
        if (res.data.code === 200) {
          this.$message.success("配置保存成功");
          // 更新本地数据
          const idx = this.doctorList.findIndex(d => d.key === this.selectedDoctor);
          if (idx !== -1) {
            this.doctorList[idx].systemPrompt = this.editForm.systemPrompt;
            this.doctorList[idx].temperature = this.editForm.temperature;
            this.doctorList[idx].topP = this.editForm.topP;
            this.currentConfig = this.doctorList[idx];
          }
        } else {
          this.$message.error(res.data.message || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败: " + (e.response?.data?.message || e.message));
      } finally {
        this.saving = false;
      }
    },
    async resetConfig() {
      const { value: password } = await this.$swal.fire({
        title: "恢复默认提示词",
        html: `<p style="margin-bottom:12px">将把 <b>${this.currentConfig.name}</b> 的配置恢复为默认值</p>`,
        input: "password",
        inputLabel: "请输入管理员密码",
        inputPlaceholder: "密码",
        inputAttributes: {
          autocapitalize: "off",
          autocorrect: "off"
        },
        showCancelButton: true,
        confirmButtonText: "确认恢复",
        cancelButtonText: "取消",
        confirmButtonColor: "#667eea",
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        }
      });

      if (!password) return;

      this.resetting = true;
      try {
        const res = await this.$axios.post(`/ai/config/${this.selectedDoctor}/reset`, { password });
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "恢复成功",
            text: `${this.currentConfig.name} 已恢复默认提示词`,
            timer: 1500,
            showConfirmButton: false
          });
          await this.loadConfigs();
          const doctor = this.doctorList.find(d => d.key === this.selectedDoctor);
          if (doctor) this.selectDoctor(doctor);
        } else {
          this.$swal.fire({
            icon: "error",
            title: "恢复失败",
            text: res.data.message || "请检查密码是否正确"
          });
        }
      } catch (e) {
        this.$swal.fire({
          icon: "error",
          title: "恢复失败",
          text: e.response?.data?.message || "请检查密码是否正确"
        });
      } finally {
        this.resetting = false;
      }
    },
    async resetAllConfigs() {
      const { value: password } = await this.$swal.fire({
        title: "恢复所有默认提示词",
        html: `<p style="margin-bottom:12px">将把 <b>所有AI医生角色</b> 的配置恢复为默认值</p>`,
        input: "password",
        inputLabel: "请输入管理员密码",
        inputPlaceholder: "密码",
        inputAttributes: {
          autocapitalize: "off",
          autocorrect: "off"
        },
        showCancelButton: true,
        confirmButtonText: "确认恢复",
        cancelButtonText: "取消",
        confirmButtonColor: "#e6a23c",
        inputValidator: (value) => {
          if (!value) return "请输入密码";
        }
      });

      if (!password) return;

      this.resettingAll = true;
      try {
        const res = await this.$axios.post("/ai/config/reset-all", { password });
        if (res.data.code === 200) {
          this.$swal.fire({
            icon: "success",
            title: "恢复成功",
            text: "所有角色已恢复默认提示词",
            timer: 1500,
            showConfirmButton: false
          });
          await this.loadConfigs();
          if (this.selectedDoctor) {
            const doctor = this.doctorList.find(d => d.key === this.selectedDoctor);
            if (doctor) this.selectDoctor(doctor);
          }
        } else {
          this.$swal.fire({
            icon: "error",
            title: "恢复失败",
            text: res.data.message || "请检查密码是否正确"
          });
        }
      } catch (e) {
        this.$swal.fire({
          icon: "error",
          title: "恢复失败",
          text: e.response?.data?.message || "请检查密码是否正确"
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
