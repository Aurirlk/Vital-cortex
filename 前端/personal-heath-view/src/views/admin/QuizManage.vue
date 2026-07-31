<template>
  <div class="manage-container">
    <div class="manage-header">
      <h2></h2>
      <el-button type="primary" @click="showAddQuestion = true"></el-button>
    </div>

    <div class="tabs">
      <div class="tab" :class="{ active: activeTab === 'question' }" @click="activeTab = 'question'"></div>
      <div class="tab" :class="{ active: activeTab === 'exam' }" @click="activeTab = 'exam'"></div>
    </div>

    <!--  -->
    <div v-if="activeTab === 'question'" class="tab-content">
      <el-table :data="questions" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="" show-overflow-tooltip />
        <el-table-column prop="questionType" label="" width="100">
          <template #default="{ row }">{{ ['', '', '', '', '', ''][row.questionType] }}</template>
        </el-table-column>
        <el-table-column prop="difficulty" label="" width="100">
          <template #default="{ row }">
            <el-tag :type="['', 'success', 'warning', 'danger'][row.difficulty]">{{ ['', '', '', ''][row.difficulty] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="" width="80" />
        <el-table-column label="" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="editQuestion(row)"></el-button>
            <el-button size="small" type="danger" @click="deleteQuestion(row.id)"></el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'exam'" class="tab-content">
      <el-button type="primary" @click="showAddExam = true" style="margin-bottom: 16px"></el-button>
      <el-table :data="exams" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="" />
        <el-table-column prop="durationMinutes" label="()" width="100" />
        <el-table-column prop="totalScore" label="" width="80" />
        <el-table-column prop="passScore" label="" width="80" />
        <el-table-column prop="questionCount" label="" width="80" />
        <el-table-column prop="status" label="" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '' : '' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <el-dialog v-model="showAddQuestion" title="" width="600px">
      <el-form :model="questionForm" label-width="80px">
        <el-form-item label="">
          <el-select v-model="questionForm.questionType">
            <el-option label="" :value="0" />
            <el-option label="" :value="1" />
            <el-option label="" :value="2" />
            <el-option label="" :value="3" />
            <el-option label="" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label=""><el-input v-model="questionForm.title" type="textarea" /></el-form-item>
        <el-form-item v-if="questionForm.questionType < 3" label="">
          <el-input v-model="questionForm.options" type="textarea" placeholder='JSON["A","B","C","D"]' />
        </el-form-item>
        <el-form-item label=""><el-input v-model="questionForm.answer" /></el-form-item>
        <el-form-item label=""><el-input v-model="questionForm.analysis" type="textarea" /></el-form-item>
        <el-form-item label="">
          <el-select v-model="questionForm.difficulty">
            <el-option label="" :value="1" />
            <el-option label="" :value="2" />
            <el-option label="" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label=""><el-input-number v-model="questionForm.score" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddQuestion = false"></el-button>
        <el-button type="primary" @click="saveQuestion"></el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showAddExam" title="" width="500px">
      <el-form :model="examForm" label-width="80px">
        <el-form-item label=""><el-input v-model="examForm.title" /></el-form-item>
        <el-form-item label=""><el-input v-model="examForm.description" type="textarea" /></el-form-item>
        <el-form-item label="()"><el-input-number v-model="examForm.durationMinutes" :min="10" /></el-form-item>
        <el-form-item label=""><el-input-number v-model="examForm.passScore" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddExam = false"></el-button>
        <el-button type="primary" @click="saveExam"></el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "QuizManage",
  data() {
    return {
      activeTab: "question",
      questions: [],
      exams: [],
      showAddQuestion: false,
      showAddExam: false,
      questionForm: { questionType: 0, title: "", options: "", answer: "", analysis: "", difficulty: 2, score: 1 },
      examForm: { title: "", description: "", durationMinutes: 60, passScore: 60 },
    };
  },
  created() {
    this.loadQuestions();
    this.loadExams();
  },
  methods: {
    async loadQuestions() {
      try {
        const { data } = await request.post("quiz/question/query", {});
        if (data.code === 200) this.questions = data.data;
      } catch (e) { console.error(e); }
    },
    async loadExams() {
      try {
        const { data } = await request.get("quiz/exam/list");
        if (data.code === 200) this.exams = data.data;
      } catch (e) { console.error(e); }
    },
    async saveQuestion() {
      try {
        await request.post("quiz/question/save", this.questionForm);
        this.showAddQuestion = false;
        this.loadQuestions();
        this.$message.success("");
      } catch (e) { this.$message.error(""); }
    },
    editQuestion(row) { this.questionForm = { ...row }; this.showAddQuestion = true; },
    async deleteQuestion(id) {
      await request.post("quiz/question/batchDelete", [id]);
      this.loadQuestions();
    },
    async saveExam() {
      try {
        await request.post("quiz/exam/save", this.examForm);
        this.showAddExam = false;
        this.loadExams();
        this.$message.success("");
      } catch (e) { this.$message.error(""); }
    },
  },
};
</script>

<style scoped>
.manage-container { padding: 20px; }
.manage-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.manage-header h2 { margin: 0; font-size: 20px; }
.tabs { display: flex; gap: 4px; background: #f5f5f5; padding: 4px; border-radius: 10px; margin-bottom: 20px; }
.tab { padding: 10px 20px; border-radius: 8px; cursor: pointer; font-size: 14px; transition: all 0.2s; }
.tab.active { background: #fff; color: #ff2442; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
</style>
