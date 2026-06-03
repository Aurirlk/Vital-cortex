<template>
  <div class="nav-assistant">
    <!-- 按钮 -->
    <el-badge :value="unreadCount" :hidden="unreadCount === 0">
      <el-button type="primary" round @click="showPanel = !showPanel" class="assistant-btn">
        <el-icon><Service /></el-icon> 网站小助手
      </el-button>
    </el-badge>

    <!-- 功能面板 -->
    <div v-if="showPanel" class="assistant-panel">
      <div class="panel-item" @click="openFunction('symptom')">
        <el-icon color="#e74c3c"><Search /></el-icon>
        <div class="item-text">
          <strong>病情查询</strong>
          <span>联网搜索最新医疗信息</span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('doctor')">
        <el-icon color="#3498db"><UserFilled /></el-icon>
        <div class="item-text">
          <strong>医生推荐</strong>
          <span>快速跳转到AI医生</span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('drug')">
        <el-icon color="#27ae60"><FirstAidKit /></el-icon>
        <div class="item-text">
          <strong>药品介绍</strong>
          <span>查询药品信息与价格</span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('knowledge')">
        <el-icon color="#8e44ad"><Collection /></el-icon>
        <div class="item-text">
          <strong>健康知识</strong>
          <span>平台知识库智能搜索</span>
        </div>
      </div>
    </div>

    <!-- 病情查询对话框 -->
    <el-dialog v-model="showSymptom" title="🔍 病情查询" width="600px" :append-to-body="true">
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input v-model="symptomInput" placeholder="描述您的症状，如：头痛发烧怎么办？" @keyup.enter="searchSymptom">
            <template #append>
              <el-button @click="searchSymptom" :loading="symptomLoading">联网搜索</el-button>
            </template>
          </el-input>
        </div>
        <div class="dialog-result" v-if="symptomResult" v-html="symptomResult"></div>
        <div class="dialog-result" v-if="symptomLoading">正在联网搜索中...</div>
      </div>
    </el-dialog>

    <!-- 医生推荐对话框 -->
    <el-dialog v-model="showDoctor" title="🩺 医生推荐" width="500px" :append-to-body="true">
      <div class="dialog-body">
        <div class="doctor-list">
          <div v-for="(role, key) in doctorRoles" :key="key" class="doctor-card" @click="goToDoctor(key, role)">
            <span class="doctor-icon">{{ role.icon }}</span>
            <div class="doctor-info">
              <strong>{{ role.name }}</strong>
              <span>{{ role.desc }}</span>
            </div>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 药品介绍对话框 -->
    <el-dialog v-model="showDrug" title="💊 药品介绍" width="600px" :append-to-body="true">
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input v-model="drugInput" placeholder="输入药品名称，如：布洛芬、感冒灵..." @keyup.enter="searchDrug">
            <template #append>
              <el-button @click="searchDrug" :loading="drugLoading">查询</el-button>
            </template>
          </el-input>
        </div>
        <div class="dialog-result" v-if="drugResult" v-html="drugResult"></div>
        <div class="dialog-result" v-if="drugLoading">正在查询药品库...</div>
      </div>
    </el-dialog>

    <!-- 健康知识对话框 -->
    <el-dialog v-model="showKnowledge" title="📚 健康知识" width="600px" :append-to-body="true">
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input v-model="knowledgeInput" placeholder="搜索健康知识，如：高血压饮食调理..." @keyup.enter="searchKnowledge">
            <template #append>
              <el-button @click="searchKnowledge" :loading="knowledgeLoading">搜索</el-button>
            </template>
          </el-input>
        </div>
        <div class="dialog-result" v-if="knowledgeResult" v-html="knowledgeResult"></div>
        <div class="dialog-result" v-if="knowledgeLoading">正在搜索知识库...</div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getToken } from "@/utils/storage.js";
import { marked } from "marked";

marked.setOptions({ breaks: true, gfm: true });

export default {
  name: "NavAssistant",
  data() {
    return {
      showPanel: false,
      unreadCount: 0,
      // 病情查询
      showSymptom: false, symptomInput: "", symptomResult: "", symptomLoading: false,
      // 医生推荐
      showDoctor: false,
      // 药品介绍
      showDrug: false, drugInput: "", drugResult: "", drugLoading: false,
      // 健康知识
      showKnowledge: false, knowledgeInput: "", knowledgeResult: "", knowledgeLoading: false,
      // 医生列表
      doctorRoles: {
        doctor: { name: "全科医生", icon: "🩺", desc: "症状分析与就医建议", path: "/user/ai-analysis" },
        nutritionist: { name: "营养师", icon: "🥗", desc: "膳食规划与营养指导", path: "/user/ai-analysis" },
        psychologist: { name: "心理咨询", icon: "🛋️", desc: "情绪疏导与心理支持", path: "/user/ai-analysis" },
        analyst: { name: "报告分析", icon: "📊", desc: "体检报告解读与分析", path: "/user/ai-analysis" },
        general_assistant: { name: "全能助手", icon: "🧠", desc: "综合健康咨询与科普", path: "/user/ai-analysis" },
      },
    };
  },
  methods: {
    openFunction(type) {
      this.showPanel = false;
      if (type === "symptom") this.showSymptom = true;
      else if (type === "doctor") this.showDoctor = true;
      else if (type === "drug") this.showDrug = true;
      else if (type === "knowledge") this.showKnowledge = true;
    },
    // 病情查询 - 联网搜索
    async searchSymptom() {
      if (!this.symptomInput.trim()) return;
      this.symptomLoading = true;
      this.symptomResult = "";
      try {
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;
        const res = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/chat", {
          method: "POST", headers,
          body: JSON.stringify({ message: this.symptomInput, role: "doctor", enableWebSearch: true, enableKnowledgeBase: false, enableHealthData: false }),
        });
        const data = await res.json();
        this.symptomResult = marked.parse(data.data?.reply || "暂无结果");
      } catch (e) {
        this.symptomResult = "查询失败：" + e.message;
      } finally {
        this.symptomLoading = false;
      }
    },
    // 医生推荐 - 跳转
    goToDoctor(key, role) {
      this.showDoctor = false;
      sessionStorage.setItem("navAssistantRole", key);
      this.$router.push(role.path);
    },
    // 药品介绍 - 调用药品库API
    async searchDrug() {
      if (!this.drugInput.trim()) return;
      this.drugLoading = true;
      this.drugResult = "";
      try {
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;
        const res = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/chat", {
          method: "POST", headers,
          body: JSON.stringify({ message: "请查询药品：" + this.drugInput, role: "consultant", enableWebSearch: false, enableKnowledgeBase: false, enableHealthData: false }),
        });
        const data = await res.json();
        this.drugResult = marked.parse(data.data?.reply || "暂无结果");
      } catch (e) {
        this.drugResult = "查询失败：" + e.message;
      } finally {
        this.drugLoading = false;
      }
    },
    // 健康知识 - 知识库+Dify
    async searchKnowledge() {
      if (!this.knowledgeInput.trim()) return;
      this.knowledgeLoading = true;
      this.knowledgeResult = "";
      try {
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;
        // 先提取关键词
        let keywords = null;
        try {
          const kwRes = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/keywords/extract", {
            method: "POST", headers,
            body: JSON.stringify({ message: this.knowledgeInput }),
          });
          const kwData = await kwRes.json();
          if (kwData.code === 200 && kwData.data) keywords = kwData.data;
        } catch (e) {}
        // 调用AI（带知识库）
        const res = await fetch("http://localhost:21090/api/personal-health/v1.0/ai/chat", {
          method: "POST", headers,
          body: JSON.stringify({ message: this.knowledgeInput, role: "general_assistant", enableKnowledgeBase: true, enableHealthData: false, keywords }),
        });
        const data = await res.json();
        this.knowledgeResult = marked.parse(data.data?.reply || "暂无结果");
      } catch (e) {
        this.knowledgeResult = "查询失败：" + e.message;
      } finally {
        this.knowledgeLoading = false;
      }
    },
  },
};
</script>

<style scoped>
.nav-assistant { position: relative; display: inline-block; margin-right: 16px; align-self: center; }
.assistant-btn { background: linear-gradient(135deg, #667eea, #764ba2); border: none; }

.assistant-panel {
  position: absolute; top: 50px; right: 0;
  width: 300px; background: #fff; border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.15); z-index: 9999; overflow: hidden;
}
.panel-item {
  display: flex; align-items: center; gap: 12px; padding: 16px; cursor: pointer;
  transition: background 0.2s; border-bottom: 1px solid #f0f0f0;
}
.panel-item:hover { background: #f5f7fa; }
.panel-item:last-child { border-bottom: none; }
.item-text { display: flex; flex-direction: column; }
.item-text strong { font-size: 14px; color: #333; }
.item-text span { font-size: 12px; color: #999; margin-top: 2px; }

.dialog-body { min-height: 100px; }
.dialog-input { margin-bottom: 16px; }
.dialog-result { max-height: 400px; overflow-y: auto; padding: 16px; background: #f9fafb; border-radius: 8px; margin-top: 12px; font-size: 14px; line-height: 1.7; }

.doctor-list { display: flex; flex-direction: column; gap: 10px; }
.doctor-card {
  display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid #f0f0f0;
  border-radius: 10px; cursor: pointer; transition: all 0.2s;
}
.doctor-card:hover { border-color: #667eea; background: #f5f3ff; }
.doctor-icon { font-size: 24px; }
.doctor-info { flex: 1; display: flex; flex-direction: column; }
.doctor-info strong { font-size: 14px; color: #333; }
.doctor-info span { font-size: 12px; color: #999; }
</style>
