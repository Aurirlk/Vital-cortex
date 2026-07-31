<template>
  <div class="manage-container">
    <div class="manage-header">
      <h2></h2>
      <button class="btn btn--primary" @click="showAddDept = true"></button>
    </div>

    <!-- Tab -->
    <div class="tabs">
      <div class="tab" :class="{ active: activeTab === 'dept' }" @click="activeTab = 'dept'"></div>
      <div class="tab" :class="{ active: activeTab === 'doctor' }" @click="activeTab = 'doctor'"></div>
      <div class="tab" :class="{ active: activeTab === 'schedule' }" @click="activeTab = 'schedule'"></div>
      <div class="tab" :class="{ active: activeTab === 'appointment' }" @click="activeTab = 'appointment'"></div>
    </div>

    <!--  -->
    <div v-if="activeTab === 'dept'" class="tab-content">
      <el-table :data="departments" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="" />
        <el-table-column prop="description" label="" />
        <el-table-column prop="status" label="" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '' : '' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="editDept(row)"></el-button>
            <el-button size="small" type="danger" @click="deleteDept(row.id)"></el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'doctor'" class="tab-content">
      <div class="toolbar">
        <el-button type="primary" @click="showAddDoctor = true"></el-button>
      </div>
      <el-table :data="doctors" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="" />
        <el-table-column prop="title" label="" />
        <el-table-column prop="departmentName" label="" />
        <el-table-column prop="isOnline" label="" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isOnline === 1 ? 'success' : 'info'">{{ row.isOnline === 1 ? '' : '' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="editDoctor(row)"></el-button>
            <el-button size="small" type="danger" @click="deleteDoctor(row.id)"></el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'schedule'" class="tab-content">
      <div class="toolbar">
        <el-button type="primary" @click="showAddSchedule = true"></el-button>
      </div>
      <el-table :data="schedules" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="doctorId" label="ID" />
        <el-table-column prop="scheduleDate" label="" />
        <el-table-column prop="timeSlot" label="">
          <template #default="{ row }">{{ { morning: '', afternoon: '', evening: '' }[row.timeSlot] }}</template>
        </el-table-column>
        <el-table-column prop="maxPatients" label="" />
        <el-table-column prop="bookedCount" label="" />
        <el-table-column prop="status" label="" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '' : '' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'appointment'" class="tab-content">
      <el-table :data="appointments" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="patientName" label="" />
        <el-table-column prop="doctorName" label="" />
        <el-table-column prop="departmentName" label="" />
        <el-table-column prop="appointmentDate" label="" />
        <el-table-column prop="timeSlot" label="">
          <template #default="{ row }">{{ { morning: '', afternoon: '', evening: '' }[row.timeSlot] }}</template>
        </el-table-column>
        <el-table-column prop="status" label="" width="100">
          <template #default="{ row }">
            <el-tag :type="['warning', 'primary', 'success', 'info', 'danger'][row.status]">
              {{ ['', '', '', '', ''][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <el-dialog v-model="showAddDept" title="" width="400px">
      <el-form :model="deptForm" label-width="80px">
        <el-form-item label=""><el-input v-model="deptForm.name" /></el-form-item>
        <el-form-item label=""><el-input v-model="deptForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDept = false"></el-button>
        <el-button type="primary" @click="saveDept"></el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showAddDoctor" title="" width="500px">
      <el-form :model="doctorForm" label-width="80px">
        <el-form-item label=""><el-input v-model="doctorForm.name" /></el-form-item>
        <el-form-item label="">
          <el-select v-model="doctorForm.title">
            <el-option label="" value="" />
            <el-option label="" value="" />
            <el-option label="" value="" />
            <el-option label="" value="" />
          </el-select>
        </el-form-item>
        <el-form-item label="">
          <el-select v-model="doctorForm.departmentId">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label=""><el-input v-model="doctorForm.expertise" /></el-form-item>
        <el-form-item label=""><el-input v-model="doctorForm.introduction" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDoctor = false"></el-button>
        <el-button type="primary" @click="saveDoctor"></el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showAddSchedule" title="" width="400px">
      <el-form :model="scheduleForm" label-width="80px">
        <el-form-item label="">
          <el-select v-model="scheduleForm.doctorId">
            <el-option v-for="d in doctors" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label=""><el-date-picker v-model="scheduleForm.scheduleDate" type="date" /></el-form-item>
        <el-form-item label="">
          <el-select v-model="scheduleForm.timeSlot">
            <el-option label="" value="morning" />
            <el-option label="" value="afternoon" />
            <el-option label="" value="evening" />
          </el-select>
        </el-form-item>
        <el-form-item label=""><el-input-number v-model="scheduleForm.maxPatients" :min="1" :max="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddSchedule = false"></el-button>
        <el-button type="primary" @click="saveSchedule"></el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "AppointmentManage",
  data() {
    return {
      activeTab: "dept",
      departments: [],
      doctors: [],
      schedules: [],
      appointments: [],
      showAddDept: false,
      showAddDoctor: false,
      showAddSchedule: false,
      deptForm: { name: "", description: "" },
      doctorForm: { name: "", title: "", departmentId: null, expertise: "", introduction: "" },
      scheduleForm: { doctorId: null, scheduleDate: "", timeSlot: "morning", maxPatients: 30 },
    };
  },
  created() {
    this.loadAll();
  },
  methods: {
    async loadAll() {
      try {
        const [deptRes, docRes, aptRes] = await Promise.all([
          request.get("appointment/departments"),
          request.get("appointment/doctors"),
          request.post("appointment/query", {}),
        ]);
        if (deptRes.data.code === 200) this.departments = deptRes.data.data;
        if (docRes.data.code === 200) this.doctors = docRes.data.data;
        if (aptRes.data.code === 200) this.appointments = aptRes.data.data;
      } catch (e) { console.error(e); }
    },
    async saveDept() {
      try {
        await request.post("appointment/department/save", this.deptForm);
        this.showAddDept = false;
        this.loadAll();
        this.$message.success("");
      } catch (e) { this.$message.error(""); }
    },
    editDept(row) { this.deptForm = { ...row }; this.showAddDept = true; },
    async deleteDept(id) {
      await request.post("appointment/department/batchDelete", [id]);
      this.loadAll();
    },
    async saveDoctor() {
      try {
        await request.post("appointment/doctor/save", this.doctorForm);
        this.showAddDoctor = false;
        this.loadAll();
        this.$message.success("");
      } catch (e) { this.$message.error(""); }
    },
    editDoctor(row) { this.doctorForm = { ...row }; this.showAddDoctor = true; },
    async deleteDoctor(id) {
      await request.post("appointment/doctor/batchDelete", [id]);
      this.loadAll();
    },
    async saveSchedule() {
      try {
        await request.post("appointment/schedule/save", this.scheduleForm);
        this.showAddSchedule = false;
        this.loadAll();
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
.toolbar { margin-bottom: 16px; }
.btn { padding: 8px 16px; border-radius: 8px; font-size: 14px; cursor: pointer; border: none; }
.btn--primary { background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; }
</style>
