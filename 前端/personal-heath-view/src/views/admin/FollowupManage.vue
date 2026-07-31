<template>
  <div class="manage-container">
    <div class="manage-header">
      <h2></h2>
      <el-button type="primary" @click="showAddTask = true"></el-button>
    </div>

    <el-table :data="tasks" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="" />
      <el-table-column prop="patientName" label="" />
      <el-table-column prop="doctorName" label="" />
      <el-table-column prop="taskType" label="" width="100">
        <template #default="{ row }">{{ getTaskIcon(row.taskType) }} {{ getTaskLabel(row.taskType) }}</template>
      </el-table-column>
      <el-table-column prop="dueDate" label="" width="120" />
      <el-table-column prop="status" label="" width="100">
        <template #default="{ row }">
          <el-tag :type="['warning', 'primary', 'success', 'danger', 'info'][row.status]">
            {{ ['', '', '', '', ''][row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="viewRecords(row)"></el-button>
          <el-button size="small" type="danger" @click="deleteTask(row.id)"></el-button>
        </template>
      </el-table-column>
    </el-table>

    <!--  -->
    <el-dialog v-model="showAddTask" title="" width="500px">
      <el-form :model="taskForm" label-width="80px">
        <el-form-item label=""><el-input v-model="taskForm.title" /></el-form-item>
        <el-form-item label="ID"><el-input-number v-model="taskForm.patientId" :min="1" /></el-form-item>
        <el-form-item label="ID"><el-input-number v-model="taskForm.doctorId" :min="1" /></el-form-item>
        <el-form-item label="">
          <el-select v-model="taskForm.taskType">
            <el-option label="" value="medication" />
            <el-option label="" value="appointment" />
            <el-option label="" value="indicator" />
            <el-option label="" value="exercise" />
            <el-option label="" value="diet" />
          </el-select>
        </el-form-item>
        <el-form-item label=""><el-date-picker v-model="taskForm.dueDate" type="date" /></el-form-item>
        <el-form-item label=""><el-input v-model="taskForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddTask = false"></el-button>
        <el-button type="primary" @click="saveTask"></el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showRecords" title="" width="500px">
      <div v-if="taskRecords.length === 0" class="empty-state"></div>
      <el-timeline v-else>
        <el-timeline-item v-for="record in taskRecords" :key="record.id" :timestamp="record.createTime">
          {{ record.content }}
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "FollowupManage",
  data() {
    return {
      tasks: [],
      showAddTask: false,
      showRecords: false,
      taskRecords: [],
      taskForm: { title: "", patientId: null, doctorId: null, taskType: "medication", dueDate: "", description: "" },
    };
  },
  created() {
    this.loadTasks();
  },
  methods: {
    getTaskIcon(type) {
      const icons = { medication: '', appointment: '', indicator: '', exercise: '', diet: '' };
      return icons[type] || '';
    },
    getTaskLabel(type) {
      const labels = { medication: '', appointment: '', indicator: '', exercise: '', diet: '' };
      return labels[type] || '';
    },
    async loadTasks() {
      try {
        const { data } = await request.get("followup/task/doctor/0");
        if (data.code === 200) this.tasks = data.data;
      } catch (e) { console.error(e); }
    },
    async saveTask() {
      try {
        await request.post("followup/task/save", this.taskForm);
        this.showAddTask = false;
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
    async deleteTask(id) {
      await request.post("followup/task/batchDelete", [id]);
      this.loadTasks();
    },
  },
};
</script>

<style scoped>
.manage-container { padding: 20px; }
.manage-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.manage-header h2 { margin: 0; font-size: 20px; }
.empty-state { text-align: center; padding: 30px; color: #999; }
</style>
