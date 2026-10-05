<template>
  <div class="manage-container">
    <div class="manage-header">
      <h2>随访任务管理</h2>
      <el-button type="primary" @click="showAddTask = true"
        >新增随访任务</el-button
      >
    </div>

    <!-- 2026-10-03 新增：原先只查 doctorId=0，所有管理员看到同一份数据 -->
    <el-form :inline="true" class="manage-filter">
      <el-form-item label="负责医生">
        <DoctorSelect
          v-model="filterDoctorId"
          clearable
          width="220px"
          placeholder="全部医生"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filterStatus" clearable placeholder="全部" style="width: 130px">
          <el-option label="待开始" :value="0" />
          <el-option label="进行中" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="已逾期" :value="3" />
          <el-option label="已取消" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadTasks">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tasks" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="任务名称" min-width="160" />
      <el-table-column prop="patientName" label="患者" width="110" />
      <el-table-column prop="doctorName" label="负责医生" width="110" />
      <el-table-column label="任务类型" width="110">
        <template #default="{ row }">
          {{ getTaskLabel(row.taskType) }}
        </template>
      </el-table-column>
      <el-table-column prop="dueDate" label="截止日期" width="170" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="
              ['warning', 'primary', 'success', 'danger', 'info'][row.status] ||
              'info'
            "
          >
            {{ statusLabels[row.status] || "未知" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="viewRecords(row)">记录</el-button>
          <el-button
            size="small"
            type="danger"
            @click="deleteTask(row.id)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增随访任务 -->
    <el-dialog v-model="showAddTask" title="新增随访任务" width="520px">
      <el-form :model="taskForm" label-width="90px">
        <el-form-item label="任务名称">
          <el-input v-model="taskForm.title" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="患者">
          <!-- 2026-10-03 修复：原先是 el-input-number 手输患者ID，
               操作员无从得知某个 ID 对应谁，数据极易录错。 -->
          <PatientSelect v-model="taskForm.patientId" />
        </el-form-item>
        <el-form-item label="负责医生">
          <!-- 2026-10-03 修复：原先手输医生ID，现改为可搜索的医生选择器 -->
          <DoctorSelect v-model="taskForm.doctorId" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="taskForm.taskType" style="width: 100%">
            <el-option label="用药提醒" value="medication" />
            <el-option label="复诊提醒" value="appointment" />
            <el-option label="体征监测" value="indicator" />
            <el-option label="运动指导" value="exercise" />
            <el-option label="饮食管理" value="diet" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="taskForm.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input
            v-model="taskForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入任务描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddTask = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- 随访记录 -->
    <el-dialog v-model="showRecords" title="随访打卡记录" width="520px">
      <div v-if="taskRecords.length === 0" class="empty-state">
        暂无打卡记录
      </div>
      <el-timeline v-else>
        <el-timeline-item
          v-for="record in taskRecords"
          :key="record.id"
          :timestamp="record.createTime"
        >
          {{ record.content }}
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";
import DoctorSelect from "@/components/DoctorSelect.vue";
import PatientSelect from "@/components/PatientSelect.vue";

export default {
  name: "FollowupManage",
  components: { DoctorSelect, PatientSelect },
  data() {
    return {
      tasks: [],
      loading: false,
      showAddTask: false,
      showRecords: false,
      taskRecords: [],
      // 2026-10-03 新增筛选条件（原实现无筛选，且医生ID硬编码为 0）
      filterDoctorId: null,
      filterStatus: null,
      statusLabels: ["待开始", "进行中", "已完成", "已逾期", "已取消"],
      taskTypeLabels: {
        medication: "用药提醒",
        appointment: "复诊提醒",
        indicator: "体征监测",
        exercise: "运动指导",
        diet: "饮食管理",
      },
      taskForm: {
        title: "",
        patientId: null,
        doctorId: null,
        taskType: "medication",
        dueDate: "",
        description: "",
      },
    };
  },
  created() {
    this.loadTasks();
  },
  methods: {
    getTaskLabel(type) {
      return this.taskTypeLabels[type] || type || "—";
    },
    resetFilter() {
      this.filterDoctorId = null;
      this.filterStatus = null;
      this.loadTasks();
    },
    async loadTasks() {
      this.loading = true;
      try {
        // 2026-10-03 修复：原实现固定查 doctorId=0，导致所有管理员看到同一份数据，
        // 其他医生的随访任务根本不可见、不可管。现按筛选条件动态查询。
        // doctorId=0 在后端表示「不限医生」，未选医生时仍传 0 保持原有行为。
        const { data } = await request.get(
          `followup/task/doctor/${this.filterDoctorId || 0}`
        );
        let list = data.code === 200 ? data.data || [] : [];
        // 状态筛选在前端做（后端接口暂不支持状态参数，数据量可控）
        if (this.filterStatus !== null && this.filterStatus !== "") {
          list = list.filter((t) => t.status === this.filterStatus);
        }
        this.tasks = list;
      } catch (e) {
        console.error(e);
      } finally {
        this.loading = false;
      }
    },
    async saveTask() {
      if (!this.taskForm.title || !this.taskForm.patientId) {
        this.$message.warning("请填写任务名称和患者ID");
        return;
      }
      try {
        const { data } = await request.post("followup/task/save", this.taskForm);
        if (data.code === 200) {
          this.showAddTask = false;
          this.taskForm = {
            title: "",
            patientId: null,
            doctorId: null,
            taskType: "medication",
            dueDate: "",
            description: "",
          };
          this.loadTasks();
          this.$message.success("新增成功");
        } else {
          this.$message.error(data.msg || "新增失败");
        }
      } catch (e) {
        this.$message.error("新增失败，请稍后重试");
      }
    },
    async deleteTask(id) {
      const confirmed = await this.$swalConfirm({
        title: "确认删除",
        text: "删除后不可恢复，确定删除该随访任务吗？",
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        const { data } = await request.post("followup/task/batchDelete", [id]);
        if (data.code === 200) {
          this.$message.success("删除成功");
          this.loadTasks();
        }
      } catch (e) {
        this.$message.error("删除失败");
      }
    },
    async viewRecords(task) {
      this.taskRecords = [];
      try {
        const { data } = await request.get(`followup/task/${task.id}/records`);
        if (data.code === 200) this.taskRecords = data.data || [];
      } catch (e) {
        console.error(e);
      }
      this.showRecords = true;
    },
  },
};
</script>

<style scoped>
.manage-container {
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  min-height: 400px;
}

.manage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.manage-header h2 {
  margin: 0;
  font-size: 18px;
  color: #1a1a1a;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}
</style>
