<template>
  <div class="quiz-container">
    <div class="quiz-header">
      <h1 class="page-title"></h1>
      <p class="page-desc"></p>
    </div>

    <!--  -->
    <div v-if="!currentExam" class="exam-list">
      <div v-for="exam in exams" :key="exam.id" class="exam-card" @click="startExam(exam)">
        <div class="exam-card__header">
          <h3 class="exam-card__title">{{ exam.title }}</h3>
          <span class="exam-card__difficulty" :class="'diff--' + exam.difficulty">
            {{ ['', '', '', ''][exam.difficulty] }}
          </span>
        </div>
        <p class="exam-card__desc">{{ exam.description }}</p>
        <div class="exam-card__meta">
          <span>⏱ {{ exam.durationMinutes }}</span>
          <span> {{ exam.questionCount }}</span>
          <span> {{ exam.totalScore }}</span>
        </div>
        <button class="exam-card__btn"></button>
      </div>
    </div>

    <!--  -->
    <div v-if="currentExam && !examResult" class="quiz-panel">
      <div class="quiz-panel__header">
        <h2>{{ currentExam.title }}</h2>
        <div class="quiz-timer">⏱ {{ formatTime(remainingTime) }}</div>
      </div>

      <div class="question-card">
        <div class="question-card__header">
          <span class="question-num"> {{ currentIndex + 1 }}/{{ questions.length }} </span>
          <span class="question-type">{{ ['', '', '', '', '', ''][currentQuestion.questionType] }}</span>
        </div>
        <h3 class="question-title">{{ currentQuestion.title }}</h3>

        <!--  -->
        <div v-if="currentQuestion.questionType < 3" class="options-list">
          <div v-for="(opt, idx) in parseOptions(currentQuestion.options)" :key="idx"
               class="option-item" :class="{ 'option-item--selected': isOptionSelected(idx) }"
               @click="selectOption(idx)">
            <div class="option-radio">{{ ['A', 'B', 'C', 'D'][idx] }}</div>
            <span>{{ opt }}</span>
          </div>
        </div>

        <!-- / -->
        <div v-else class="text-answer">
          <textarea v-model="answers[currentQuestion.id]" class="answer-input" placeholder=""></textarea>
        </div>
      </div>

      <div class="quiz-nav">
        <button class="btn btn--outline" :disabled="currentIndex === 0" @click="prevQuestion"></button>
        <button v-if="currentIndex < questions.length - 1" class="btn btn--primary" @click="nextQuestion"></button>
        <button v-else class="btn btn--success" @click="submitExam"></button>
      </div>
    </div>

    <!--  -->
    <div v-if="examResult" class="result-panel">
      <div class="result-card">
        <div class="result-score">
          <div class="score-circle" :class="examResult.score >= currentExam.passScore ? 'pass' : 'fail'">
            <span class="score-num">{{ examResult.score }}</span>
            <span class="score-label"></span>
          </div>
          <div class="score-status">{{ examResult.score >= currentExam.passScore ? ' ' : ' ' }}</div>
        </div>
        <div class="result-stats">
          <div class="stat-item">
            <div class="stat-value">{{ examResult.correctCount }}</div>
            <div class="stat-label"></div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ examResult.questionCount }}</div>
            <div class="stat-label"></div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ Math.round(examResult.correctCount / examResult.questionCount * 100) }}%</div>
            <div class="stat-label"></div>
          </div>
        </div>
        <div class="result-actions">
          <button class="btn btn--outline" @click="currentExam = null; examResult = null"></button>
          <button class="btn btn--primary" @click="viewAnswers"></button>
        </div>
      </div>
    </div>

    <!--  -->
    <div class="history-section">
      <h2 class="section-title"></h2>
      <div v-if="records.length === 0" class="empty-state"></div>
      <div v-else class="record-list">
        <div v-for="record in records" :key="record.id" class="record-item">
          <div class="record-item__info">
            <div class="record-item__score">{{ record.score }}</div>
            <div class="record-item__detail">{{ record.correctCount }}/{{ record.questionCount }} </div>
          </div>
          <div class="record-item__status" :class="record.score >= 60 ? 'pass' : 'fail'">
            {{ record.score >= 60 ? '' : '' }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "Quiz",
  data() {
    return {
      exams: [],
      records: [],
      currentExam: null,
      questions: [],
      currentIndex: 0,
      answers: {},
      selectedOptions: [],
      remainingTime: 0,
      timer: null,
      examResult: null,
      recordId: null,
    };
  },
  computed: {
    currentQuestion() {
      return this.questions[this.currentIndex] || {};
    },
  },
  created() {
    this.loadExams();
    this.loadRecords();
  },
  beforeUnmount() {
    if (this.timer) clearInterval(this.timer);
  },
  methods: {
    parseOptions(options) {
      try { return JSON.parse(options); } catch { return []; }
    },
    isOptionSelected(idx) {
      return this.selectedOptions.includes(idx);
    },
    selectOption(idx) {
      if (this.currentQuestion.questionType === 0) {
        this.selectedOptions = [idx];
      } else {
        const i = this.selectedOptions.indexOf(idx);
        if (i > -1) this.selectedOptions.splice(i, 1);
        else this.selectedOptions.push(idx);
      }
      this.answers[this.currentQuestion.id] = this.selectedOptions.map(i => ['A', 'B', 'C', 'D'][i]).join(',');
    },
    formatTime(seconds) {
      const m = Math.floor(seconds / 60);
      const s = seconds % 60;
      return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    },
    async loadExams() {
      try {
        const { data } = await request.get("quiz/exam/list");
        if (data.code === 200) this.exams = data.data;
      } catch (e) { console.error(e); }
    },
    async loadRecords() {
      try {
        const { data } = await request.get("quiz/records");
        if (data.code === 200) this.records = data.data;
      } catch (e) { console.error(e); }
    },
    async startExam(exam) {
      this.currentExam = exam;
      this.currentIndex = 0;
      this.answers = {};
      this.selectedOptions = [];
      this.examResult = null;
      try {
        const { data } = await request.get(`quiz/exam/${exam.id}`);
        if (data.code === 200) {
          const qData = await request.get("quiz/question/query", { params: { examId: exam.id } });
          if (qData.data.code === 200) this.questions = qData.data.data;
        }
        const startData = await request.post(`quiz/start/${exam.id}`);
        if (startData.data.code === 200) {
          this.recordId = startData.data.data.id;
        }
      } catch (e) { console.error(e); }
      this.remainingTime = exam.durationMinutes * 60;
      this.timer = setInterval(() => {
        this.remainingTime--;
        if (this.remainingTime <= 0) {
          clearInterval(this.timer);
          this.submitExam();
        }
      }, 1000);
    },
    prevQuestion() {
      if (this.currentIndex > 0) {
        this.currentIndex--;
        this.selectedOptions = [];
      }
    },
    nextQuestion() {
      if (this.currentIndex < this.questions.length - 1) {
        this.currentIndex++;
        this.selectedOptions = [];
      }
    },
    async submitExam() {
      if (this.timer) clearInterval(this.timer);
      const answers = Object.keys(this.answers).map(qId => ({
        questionId: parseInt(qId),
        answer: this.answers[qId],
      }));
      try {
        const { data } = await request.post(`quiz/submit/${this.recordId}`, answers);
        if (data.code === 200) {
          this.examResult = {
            score: data.data?.score || 0,
            correctCount: data.data?.correctCount || 0,
            questionCount: this.questions.length,
          };
        }
      } catch (e) { console.error(e); }
    },
    viewAnswers() {
      this.$message.info("");
    },
  },
};
</script>

<style scoped>
.quiz-container { max-width: 800px; margin: 0 auto; padding: 24px; }
.quiz-header { text-align: center; margin-bottom: 32px; }
.page-title { font-size: 28px; font-weight: 700; color: #1a1a1a; margin: 0 0 8px; }
.page-desc { font-size: 15px; color: #999; margin: 0; }

.exam-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.exam-card { background: #fff; border-radius: 12px; padding: 20px; cursor: pointer; border: 2px solid #f0f0f0; transition: all 0.25s; }
.exam-card:hover { border-color: #ff2442; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(255,36,66,0.1); }
.exam-card__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.exam-card__title { font-size: 16px; font-weight: 600; color: #1a1a1a; margin: 0; }
.exam-card__difficulty { font-size: 12px; padding: 2px 8px; border-radius: 10px; }
.diff--1 { background: rgba(7,193,96,0.1); color: #07c160; }
.diff--2 { background: rgba(255,180,0,0.1); color: #ffb400; }
.diff--3 { background: rgba(255,36,66,0.1); color: #ff2442; }
.exam-card__desc { font-size: 13px; color: #666; margin: 0 0 12px; }
.exam-card__meta { display: flex; gap: 16px; font-size: 12px; color: #999; margin-bottom: 12px; }
.exam-card__btn { width: 100%; padding: 8px; background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; border: none; border-radius: 8px; font-weight: 500; cursor: pointer; }

.quiz-panel__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.quiz-timer { font-size: 18px; font-weight: 600; color: #ff2442; }

.question-card { background: #fff; border-radius: 12px; padding: 24px; margin-bottom: 24px; }
.question-card__header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.question-num { font-size: 14px; color: #999; }
.question-type { font-size: 12px; padding: 2px 8px; border-radius: 10px; background: rgba(14,165,165,0.1); color: #0EA5A5; }
.question-title { font-size: 16px; font-weight: 500; color: #1a1a1a; margin: 0 0 16px; }

.options-list { display: flex; flex-direction: column; gap: 10px; }
.option-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border: 2px solid #f0f0f0; border-radius: 10px; cursor: pointer; transition: all 0.2s; }
.option-item:hover { border-color: #ff2442; }
.option-item--selected { border-color: #ff2442; background: rgba(255,36,66,0.04); }
.option-radio { width: 28px; height: 28px; border-radius: 50%; border: 2px solid #ddd; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; }
.option-item--selected .option-radio { border-color: #ff2442; background: #ff2442; color: #fff; }

.answer-input { width: 100%; padding: 12px; border: 2px solid #f0f0f0; border-radius: 10px; font-size: 14px; resize: vertical; min-height: 100px; box-sizing: border-box; }
.answer-input:focus { outline: none; border-color: #ff2442; }

.quiz-nav { display: flex; justify-content: space-between; }
.btn { padding: 10px 24px; border-radius: 10px; font-size: 14px; font-weight: 500; cursor: pointer; border: none; transition: all 0.2s; }
.btn--primary { background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; }
.btn--outline { background: transparent; border: 2px solid #ff2442; color: #ff2442; }
.btn--success { background: #07c160; color: #fff; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.result-panel { margin-bottom: 40px; }
.result-card { background: #fff; border-radius: 16px; padding: 32px; text-align: center; }
.result-score { margin-bottom: 24px; }
.score-circle { width: 120px; height: 120px; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; margin: 0 auto 12px; }
.score-circle.pass { background: rgba(7,193,96,0.1); border: 3px solid #07c160; }
.score-circle.fail { background: rgba(255,36,66,0.1); border: 3px solid #ff2442; }
.score-num { font-size: 36px; font-weight: 700; color: #1a1a1a; }
.score-label { font-size: 14px; color: #999; }
.score-status { font-size: 18px; font-weight: 600; }

.result-stats { display: flex; justify-content: center; gap: 40px; margin-bottom: 24px; }
.stat-item { text-align: center; }
.stat-value { font-size: 24px; font-weight: 700; color: #1a1a1a; }
.stat-label { font-size: 13px; color: #999; }

.result-actions { display: flex; gap: 12px; justify-content: center; }

.history-section { margin-top: 40px; }
.section-title { font-size: 20px; font-weight: 600; color: #1a1a1a; margin: 0 0 16px; }
.record-list { display: flex; flex-direction: column; gap: 10px; }
.record-item { background: #fff; border-radius: 10px; padding: 12px 16px; display: flex; align-items: center; gap: 12px; }
.record-item__info { flex: 1; }
.record-item__score { font-size: 18px; font-weight: 700; color: #1a1a1a; }
.record-item__detail { font-size: 13px; color: #999; }
.record-item__status { font-size: 12px; padding: 2px 10px; border-radius: 10px; }
.pass { background: rgba(7,193,96,0.1); color: #07c160; }
.fail { background: rgba(255,36,66,0.1); color: #ff2442; }
.empty-state { text-align: center; padding: 30px; color: #999; }
</style>
