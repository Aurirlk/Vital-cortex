<template>
  <div class="nav-assistant">
    <!--  -->
    <el-badge :value="unreadCount" :hidden="unreadCount === 0">
      <el-button
        type="primary"
        round
        @click="showPanel = !showPanel"
        class="assistant-btn"
      >
        <el-icon><Service /></el-icon> 健康助手
      </el-button>
    </el-badge>

    <!--  -->
    <div v-if="showPanel" class="assistant-panel">
      <div class="panel-item" @click="openFunction('symptom')">
        <el-icon color="#e74c3c"><Search /></el-icon>
        <div class="item-text">
          <strong>症状自查</strong>
          <span>输入症状，AI 初步分析</span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('doctor')">
        <el-icon color="#3498db"><UserFilled /></el-icon>
        <div class="item-text">
          <strong>AI 医生</strong>
          <span>多科室 AI 问诊入口</span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('drug')">
        <el-icon color="#27ae60"><FirstAidKit /></el-icon>
        <div class="item-text">
          <strong>药品查询</strong>
          <span>药品用法、禁忌与副作用</span>
        </div>
      </div>
      <div class="panel-item" @click="openFunction('knowledge')">
        <el-icon color="#8e44ad"><Collection /></el-icon>
        <div class="item-text">
          <strong>健康知识</strong>
          <span>疾病、养生、康复知识库</span>
        </div>
      </div>
    </div>

    <!--  -->
    <el-dialog
      v-model="showSymptom"
      title="症状自查"
      width="600px"
      :append-to-body="true"
    >
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input
            v-model="symptomInput"
            placeholder="请输入症状，例如：头痛、发热、咳嗽"
            @keyup.enter="searchSymptom"
          >
            <template #append>
              <el-button @click="searchSymptom" :loading="symptomLoading"
                >查询</el-button
              >
            </template>
          </el-input>
        </div>
        <div
          class="dialog-result"
          v-if="symptomResult"
          v-html="safeHtml(symptomResult)"
        ></div>
        <div class="dialog-result" v-if="symptomLoading">
          AI 正在分析中，请稍候…
        </div>
      </div>
    </el-dialog>

    <!--  -->
    <el-dialog
      v-model="showDoctor"
      title="AI 医生"
      width="500px"
      :append-to-body="true"
    >
      <div class="dialog-body">
        <div class="doctor-list">
          <div
            v-for="(role, key) in doctorRoles"
            :key="key"
            class="doctor-card"
            @click="goToDoctor(key, role)"
          >
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
    <el-dialog
      v-model="showDrug"
      title="药品查询"
      width="600px"
      :append-to-body="true"
    >
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input
            v-model="drugInput"
            placeholder="请输入药品名称，例如：阿莫西林"
            @keyup.enter="searchDrug"
          >
            <template #append>
              <el-button @click="searchDrug" :loading="drugLoading"
                >查询</el-button
              >
            </template>
          </el-input>
        </div>
        <div
          class="dialog-result"
          v-if="drugResult"
          v-html="safeHtml(drugResult)"
        ></div>
        <div class="dialog-result" v-if="drugLoading">
          AI 正在查询中，请稍候…
        </div>
      </div>
    </el-dialog>

    <!--  -->
    <el-dialog
      v-model="showKnowledge"
      title="健康知识问答"
      width="600px"
      :append-to-body="true"
    >
      <div class="dialog-body">
        <div class="dialog-input">
          <el-input
            v-model="knowledgeInput"
            placeholder="请输入健康相关问题"
            @keyup.enter="searchKnowledge"
          >
            <template #append>
              <el-button @click="searchKnowledge" :loading="knowledgeLoading"
                >查询</el-button
              >
            </template>
          </el-input>
        </div>
        <div
          class="dialog-result"
          v-if="knowledgeResult"
          v-html="safeHtml(knowledgeResult)"
        ></div>
        <div class="dialog-result" v-if="knowledgeLoading">
          AI 正在检索知识库，请稍候…
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getToken } from "@/utils/storage.js";
import { sanitizeHtml } from "@/utils/sanitize.js";
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
      showSymptom: false,
      symptomInput: "",
      symptomResult: "",
      symptomLoading: false,
      // AI 医生弹窗
      showDoctor: false,
      // 药品查询弹窗
      showDrug: false,
      drugInput: "",
      drugResult: "",
      drugLoading: false,
      // 健康知识弹窗
      showKnowledge: false,
      knowledgeInput: "",
      knowledgeResult: "",
      knowledgeLoading: false,
      // 医生角色列表
      doctorRoles: {
        doctor: {
          name: "全科医生",
          icon: "🩺",
          desc: "常见症状与疾病咨询",
          path: "/user/ai-analysis",
        },
        nutritionist: {
          name: "营养师",
          icon: "🥗",
          desc: "饮食搭配与营养方案",
          path: "/user/ai-analysis",
        },
        psychologist: {
          name: "心理顾问",
          icon: "🧠",
          desc: "情绪疏导与心理支持",
          path: "/user/ai-analysis",
        },
        analyst: {
          name: "报告分析师",
          icon: "📊",
          desc: "体检报告与健康数据解读",
          path: "/user/ai-analysis",
        },
        general_assistant: {
          name: "通用助手",
          icon: "💡",
          desc: "不限主题的智能问答",
          path: "/user/ai-analysis",
        },
      },
    };
  },
  methods: {
    safeHtml(html) {
      return sanitizeHtml(html);
    },

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
          method: "POST",
          headers,
          body: JSON.stringify({
            message: this.symptomInput,
            role: "doctor",
            enableWebSearch: true,
            enableKnowledgeBase: false,
            enableHealthData: false,
          }),
        });
        const data = await res.json();
        this.symptomResult = marked.parse(data.data?.reply || "");
      } catch (e) {
        this.symptomResult = "分析失败：" + e.message;
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
          method: "POST",
          headers,
          body: JSON.stringify({
            message: "" + this.drugInput,
            role: "consultant",
            enableWebSearch: false,
            enableKnowledgeBase: false,
            enableHealthData: false,
          }),
        });
        const data = await res.json();
        this.drugResult = marked.parse(data.data?.reply || "");
      } catch (e) {
        this.drugResult = "查询失败：" + e.message;
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
            method: "POST",
            headers,
            body: JSON.stringify({ message: this.knowledgeInput }),
          });
          const kwData = await kwRes.json();
          if (kwData.code === 200 && kwData.data) keywords = kwData.data;
        } catch (e) {}
        // AI
        const res = await fetch(URL_API + "/ai/chat", {
          method: "POST",
          headers,
          body: JSON.stringify({
            message: this.knowledgeInput,
            role: "general_assistant",
            enableKnowledgeBase: true,
            enableHealthData: false,
            keywords,
          }),
        });
        const data = await res.json();
        this.knowledgeResult = marked.parse(data.data?.reply || "");
      } catch (e) {
        this.knowledgeResult = "检索失败：" + e.message;
      } finally {
        this.knowledgeLoading = false;
      }
    },
  },
};
</script>

<style scoped>
.nav-assistant {
  position: relative;
  display: inline-block;
  margin-right: 16px;
  align-self: center;
}
.assistant-btn {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
}

.assistant-panel {
  position: absolute;
  top: 50px;
  right: 0;
  width: 300px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
  z-index: 9999;
  overflow: hidden;
}
.panel-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid #f0f0f0;
}
.panel-item:hover {
  background: #f5f7fa;
}
.panel-item:last-child {
  border-bottom: none;
}
.item-text {
  display: flex;
  flex-direction: column;
}
.item-text strong {
  font-size: 14px;
  color: #333;
}
.item-text span {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.dialog-body {
  min-height: 100px;
}
.dialog-input {
  margin-bottom: 16px;
}
.dialog-result {
  max-height: 400px;
  overflow-y: auto;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.7;
}

.doctor-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.doctor-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.doctor-card:hover {
  border-color: #667eea;
  background: #f5f3ff;
}
.doctor-icon {
  font-size: 24px;
}
.doctor-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.doctor-info strong {
  font-size: 14px;
  color: #333;
}
.doctor-info span {
  font-size: 12px;
  color: #999;
}
</style>
