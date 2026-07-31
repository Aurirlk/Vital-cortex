<template>
  <div class="nav-assistant">
    <!--  -->
    <el-badge :value="unreadCount" :hidden="unreadCount === 0">
      <el-button type="primary" round @click="showPanel = !showPanel" class="assistant-btn">
        <el-icon><Service /></el-icon> 
      </el-button>
    </el-badge>

    <!--  -->
    <div v-if="showPanel" class="assistant-panel">
      <div class="panel-item" @click="openFunction('symptom')">
        <el-icon color="#e74c3c"><Search /></el-icon>
        <div class="item-text">
          <strong></strong>
          <span></span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('doctor')">
        <el-icon color="#3498db"><UserFilled /></el-icon>
        <div class="item-text">
          <strong></strong>
          <span>AI</span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('drug')">
        <el-icon color="#27ae60"><FirstAidKit /></el-icon>
        <div class="item-text">
          <strong></strong>
          <span></span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('knowledge')">
        <el-icon color="#8e44ad"><Collection /></el-icon>
        <div class="item-text">
          <strong></strong>
          <span></span>
        </div>
      </div>
    </div>

    <!--  -->
    <el-dialog v-model="showSymptom" title=" " width="600px" :append-to-body="true">
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input v-model="symptomInput" placeholder="" @keyup.enter="searchSymptom">
            <template #append>
              <el-button @click="searchSymptom" :loading="symptomLoading"></el-button>
            </template>
          </el-input>
        </div>
        <div class="dialog-result" v-if="symptomResult" v-html="symptomResult"></div>
        <div class="dialog-result" v-if="symptomLoading">...</div>
      </div>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showDoctor" title="🩺 " width="500px" :append-to-body="true">
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

    <!--  -->
    <el-dialog v-model="showDrug" title=" " width="600px" :append-to-body="true">
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input v-model="drugInput" placeholder="..." @keyup.enter="searchDrug">
            <template #append>
              <el-button @click="searchDrug" :loading="drugLoading"></el-button>
            </template>
          </el-input>
        </div>
        <div class="dialog-result" v-if="drugResult" v-html="drugResult"></div>
        <div class="dialog-result" v-if="drugLoading">...</div>
      </div>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showKnowledge" title=" " width="600px" :append-to-body="true">
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input v-model="knowledgeInput" placeholder="..." @keyup.enter="searchKnowledge">
            <template #append>
              <el-button @click="searchKnowledge" :loading="knowledgeLoading"></el-button>
            </template>
          </el-input>
        </div>
        <div class="dialog-result" v-if="knowledgeResult" v-html="knowledgeResult"></div>
        <div class="dialog-result" v-if="knowledgeLoading">...</div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getToken } from "@/utils/storage.js";
import { URL_API } from "@/utils/request.js";
import { marked } from "marked";

marked.setOptions({ breaks: true, gfm: true });

export default {
  name: "NavAssistant",
  data() {
    return {
      showPanel: false,
      unreadCount: 0,
      // 
      showSymptom: false, symptomInput: "", symptomResult: "", symptomLoading: false,
      // 
      showDoctor: false,
      // 
      showDrug: false, drugInput: "", drugResult: "", drugLoading: false,
      // 
      showKnowledge: false, knowledgeInput: "", knowledgeResult: "", knowledgeLoading: false,
      // 
      doctorRoles: {
        doctor: { name: "", icon: "🩺", desc: "", path: "/user/ai-analysis" },
        nutritionist: { name: "", icon: "", desc: "", path: "/user/ai-analysis" },
        psychologist: { name: "", icon: "", desc: "", path: "/user/ai-analysis" },
        analyst: { name: "", icon: "", desc: "", path: "/user/ai-analysis" },
        general_assistant: { name: "", icon: "", desc: "", path: "/user/ai-analysis" },
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
    //  - 
    async searchSymptom() {
      if (!this.symptomInput.trim()) return;
      this.symptomLoading = true;
      this.symptomResult = "";
      try {
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;
        const res = await fetch(URL_API + "/ai/chat", {
          method: "POST", headers,
          body: JSON.stringify({ message: this.symptomInput, role: "doctor", enableWebSearch: true, enableKnowledgeBase: false, enableHealthData: false }),
        });
        const data = await res.json();
        this.symptomResult = marked.parse(data.data?.reply || "");
      } catch (e) {
        this.symptomResult = "" + e.message;
      } finally {
        this.symptomLoading = false;
      }
    },
    //  - 
    goToDoctor(key, role) {
      this.showDoctor = false;
      sessionStorage.setItem("navAssistantRole", key);
      this.$router.push(role.path);
    },
    //  - API
    async searchDrug() {
      if (!this.drugInput.trim()) return;
      this.drugLoading = true;
      this.drugResult = "";
      try {
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;
        const res = await fetch(URL_API + "/ai/chat", {
          method: "POST", headers,
          body: JSON.stringify({ message: "" + this.drugInput, role: "consultant", enableWebSearch: false, enableKnowledgeBase: false, enableHealthData: false }),
        });
        const data = await res.json();
        this.drugResult = marked.parse(data.data?.reply || "");
      } catch (e) {
        this.drugResult = "" + e.message;
      } finally {
        this.drugLoading = false;
      }
    },
    //  - +Dify
    async searchKnowledge() {
      if (!this.knowledgeInput.trim()) return;
      this.knowledgeLoading = true;
      this.knowledgeResult = "";
      try {
        const token = getToken();
        const headers = { "Content-Type": "application/json" };
        if (token) headers["token"] = token;
        // 
        let keywords = null;
        try {
          const kwRes = await fetch(URL_API + "/ai/keywords/extract", {
            method: "POST", headers,
            body: JSON.stringify({ message: this.knowledgeInput }),
          });
          const kwData = await kwRes.json();
          if (kwData.code === 200 && kwData.data) keywords = kwData.data;
        } catch (e) {}
        // AI
        const res = await fetch(URL_API + "/ai/chat", {
          method: "POST", headers,
          body: JSON.stringify({ message: this.knowledgeInput, role: "general_assistant", enableKnowledgeBase: true, enableHealthData: false, keywords }),
        });
        const data = await res.json();
        this.knowledgeResult = marked.parse(data.data?.reply || "");
      } catch (e) {
        this.knowledgeResult = "" + e.message;
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
