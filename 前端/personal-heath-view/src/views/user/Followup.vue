<template>
  <div class="followup-container">
    <div class="followup-header">
      <h1 class="page-title"></h1>
      <p class="page-desc"></p>
    </div>

    <!--  -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-card__num">{{ stats.pending }}</div>
        <div class="stat-card__label"></div>
      </div>
      <div class="stat-card">
        <div class="stat-card__num">{{ stats.inProgress }}</div>
        <div class="stat-card__label"></div>
      </div>
      <div class="stat-card">
        <div class="stat-card__num">{{ stats.completed }}</div>
        <div class="stat-card__label"></div>
      </div>
      <div class="stat-card">
        <div class="stat-card__num">{{ stats.overdue }}</div>
        <div class="stat-card__label"></div>
      </div>
    </div>

    <!--  -->
    <div class="filter-bar">
      <div class="filter-item" :class="{ active: !filterType }" @click="filterType = null"></div>
      <div class="filter-item" :class="{ active: filterType === 'medication' }" @click="filterType = 'medication'"> </div>
      <div class="filter-item" :class="{ active: filterType === 'appointment' }" @click="filterType = 'appointment'"> </div>
      <div class="filter-item" :class="{ active: filterType === 'indicator' }" @click="filterType = 'indicator'"> </div>
      <div class="filter-item" :class="{ active: filterType === 'exercise' }" @click="filterType = 'exercise'"> </div>
      <div class="filter-item" :class="{ active: filterType === 'diet' }" @click="filterType = 'diet'"> </div>
    </div>

    <!--  -->
    <div class="task-list">
      <div v-for="task in filteredTasks" :key="task.id" class="task-card" :class="'task--' + task.status">
        <div class="task-card__header">
          <div class="task-card__type">{{ getTaskIcon(task.taskType) }}</div>
          <div class="task-card__info">
            <h3 class="task-card__title">{{ task.title }}</h3>
            <div class="task-card__meta">
              <span>: {{ task.dueDate }}</span>
              <span>: {{ task.doctorName }}</span>
            </div>
          </div>
          <div class="task-card__status" :class="'status--' + task.status">
            {{ ['', '', '', '', ''][task.status] }}
          </div>
        </div>
        <p class="task-card__desc">{{ task.description }}</p>
        <div class="task-card__actions">
          <button v-if="task.status < 2" class="btn btn--primary" @click="openCheckin(task)"></button>
          <button class="btn btn--outline" @click="viewRecords(task)"></button>
        </div>
      </div>
    </div>

    <!--  -->
    <div v-if="showCheckin" class="modal-overlay" @click.self="showCheckin = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3></h3>
          <button class="close-btn" @click="showCheckin = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-field">
            <label></label>
            <textarea v-model="checkinContent" class="form-textarea" placeholder="..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn--outline" @click="showCheckin = false"></button>
          <button class="btn btn--primary" @click="submitCheckin"></button>
        </div>
      </div>
    </div>

    <!--  -->
    <div v-if="showRecords" class="modal-overlay" @click.self="showRecords = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3></h3>
          <button class="close-btn" @click="showRecords = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="taskRecords.length === 0" class="empty-state"></div>
          <div v-else class="record-list">
            <div v-for="record in taskRecords" :key="record.id" class="record-item">
              <div class="record-item__time">{{ record.createTime }}</div>
              <div class="record-item__content">{{ record.content }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "Followup",
  data() {
    return {
      tasks: [],
      filterType: null,
      showCheckin: false,
      showRecords: false,
      checkinContent: "",
      currentTask: null,
      taskRecords: [],
      stats: { pending: 0, inProgress: 0, completed: 0, overdue: 0 },
    };
  },
  computed: {
    filteredTasks() {
      if (!this.filterType) return this.tasks;
      return this.tasks.filter(t => t.taskType === this.filterType);
    },
  },
  created() {
    this.loadTasks();
  },
  methods: {
    getTaskIcon(type) {
      const icons = { medication: '', appointment: '', indicator: '', exercise: '', diet: '' };
      return icons[type] || '';
    },
    async loadTasks() {
      try {
        const { data } = await request.get("followup/task/patient/0");
        if (data.code === 200) {
          this.tasks = data.data;
          this.stats = {
            pending: this.tasks.filter(t => t.status === 0).length,
            inProgress: this.tasks.filter(t => t.status === 1).length,
            completed: this.tasks.filter(t => t.status === 2).length,
            overdue: this.tasks.filter(t => t.status === 3).length,
          };
        }
      } catch (e) { console.error(e); }
    },
    openCheckin(task) {
      this.currentTask = task;
      this.checkinContent = "";
      this.showCheckin = true;
    },
    async submitCheckin() {
      if (!this.checkinContent.trim()) { this.$message.warning(""); return; }
      try {
        await request.post("followup/checkin", {
          taskId: this.currentTask.id,
          content: this.checkinContent,
        });
        this.showCheckin = false;
        this.loadTasks();
        this.$message.success("");
      } catch (e) { this.$message.error(""); }
    },
    async viewRecords(task) {
      try {
        const { data } = await request.get(`followup/task/${task.id}/records`);
        if (data.code === 200) this.taskRecords = data.data;
      } catch (e) { console.error(e); }
      this.showRecords = true;
    },
  },
};
</script>

<style scoped>
.followup-container { max-width: 900px; margin: 0 auto; padding: 24px; }
.followup-header { text-align: center; margin-bottom: 32px; }
.page-title { font-size: 28px; font-weight: 700; color: #1a1a1a; margin: 0 0 8px; }
.page-desc { font-size: 15px; color: #999; margin: 0; }

.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
.stat-card { background: #fff; border-radius: 12px; padding: 16px; text-align: center; }
.stat-card__num { font-size: 28px; font-weight: 700; color: #ff2442; }
.stat-card__label { font-size: 13px; color: #999; margin-top: 4px; }

.filter-bar { display: flex; gap: 10px; margin-bottom: 24px; flex-wrap: wrap; }
.filter-item { padding: 8px 16px; border-radius: 20px; background: #f5f5f5; cursor: pointer; font-size: 13px; transition: all 0.2s; }
.filter-item:hover, .filter-item.active { background: #ff2442; color: #fff; }

.task-list { display: flex; flex-direction: column; gap: 12px; }
.task-card { background: #fff; border-radius: 12px; padding: 16px; border-left: 4px solid #f0f0f0; }
.task--0 { border-left-color: #ffb400; }
.task--1 { border-left-color: #3370ff; }
.task--2 { border-left-color: #07c160; }
.task--3 { border-left-color: #ff2442; }
.task-card__header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.task-card__type { font-size: 28px; }
.task-card__info { flex: 1; }
.task-card__title { font-size: 16px; font-weight: 600; color: #1a1a1a; margin: 0 0 4px; }
.task-card__meta { display: flex; gap: 16px; font-size: 12px; color: #999; }
.task-card__status { font-size: 12px; padding: 2px 10px; border-radius: 10px; }
.status--0 { background: rgba(255,180,0,0.1); color: #ffb400; }
.status--1 { background: rgba(51,112,255,0.1); color: #3370ff; }
.status--2 { background: rgba(7,193,96,0.1); color: #07c160; }
.status--3 { background: rgba(255,36,66,0.1); color: #ff2442; }
.task-card__desc { font-size: 14px; color: #666; margin: 0 0 12px; }
.task-card__actions { display: flex; gap: 8px; }

.btn { padding: 8px 16px; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer; border: none; transition: all 0.2s; }
.btn--primary { background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; }
.btn--outline { background: transparent; border: 1px solid #ff2442; color: #ff2442; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 200; display: flex; align-items: center; justify-content: center; }
.modal-panel { background: #fff; border-radius: 16px; width: 400px; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 16px; border-bottom: 1px solid #f0f0f0; }
.modal-header h3 { margin: 0; }
.close-btn { background: none; border: none; font-size: 24px; cursor: pointer; color: #999; }
.modal-body { padding: 16px; }
.form-field label { display: block; font-size: 14px; font-weight: 500; margin-bottom: 8px; }
.form-textarea { width: 100%; padding: 10px; border: 2px solid #f0f0f0; border-radius: 10px; font-size: 14px; resize: vertical; min-height: 80px; box-sizing: border-box; }
.form-textarea:focus { outline: none; border-color: #ff2442; }
.modal-footer { padding: 16px; border-top: 1px solid #f0f0f0; display: flex; gap: 8px; justify-content: flex-end; }
.record-list { display: flex; flex-direction: column; gap: 10px; }
.record-item { padding: 10px; background: #f8f8f8; border-radius: 8px; }
.record-item__time { font-size: 12px; color: #999; margin-bottom: 4px; }
.record-item__content { font-size: 14px; color: #333; }
.empty-state { text-align: center; padding: 30px; color: #999; }
</style>
