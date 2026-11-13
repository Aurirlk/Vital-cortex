<template>
  <div class="manage-container">
    <div class="manage-header">
      <h2>预约管理</h2>
      <button class="btn btn--primary" @click="showAddDept = true">
        新增科室
      </button>
    </div>

    <!-- Tab -->
    <div class="tabs">
      <div
        class="tab"
        :class="{ active: activeTab === 'dept' }"
        @click="activeTab = 'dept'"
      >
        科室管理
      </div>
      <div
        class="tab"
        :class="{ active: activeTab === 'doctor' }"
        @click="activeTab = 'doctor'"
      >
        医生管理
      </div>
      <div
        class="tab"
        :class="{ active: activeTab === 'schedule' }"
        @click="activeTab = 'schedule'"
      >
        排班管理
      </div>
      <div
        class="tab"
        :class="{ active: activeTab === 'appointment' }"
        @click="activeTab = 'appointment'"
      >
        预约管理
      </div>
    </div>

    <!--  -->
    <div v-if="activeTab === 'dept'" class="tab-content">
      <el-table :data="departments" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="科室名称" />
        <el-table-column prop="description" label="科室描述" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? "启用" : "停用"
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="editDept(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteDept(row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'doctor'" class="tab-content">
      <div class="toolbar">
        <el-button type="primary" @click="showAddDoctor = true"
          >新增医生</el-button
        >
      </div>
      <el-table :data="doctors" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="医生姓名" />
        <el-table-column prop="title" label="职称" />
        <el-table-column prop="departmentName" label="所属科室" />
        <el-table-column prop="isOnline" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isOnline === 1 ? 'success' : 'info'">{{
              row.isOnline === 1 ? "在线" : "离线"
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="editDoctor(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteDoctor(row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'schedule'" class="tab-content">
      <div class="toolbar">
        <el-button type="primary" @click="showAddSchedule = true"
          >新增排班</el-button
        >
      </div>
      <el-table :data="schedules" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="doctorId" label="医生ID" />
        <el-table-column prop="scheduleDate" label="排班日期" />
        <el-table-column prop="timeSlot" label="时间段">
          <template #default="{ row }">{{
            { morning: "上午", afternoon: "下午", evening: "晚上" }[
              row.timeSlot
            ]
          }}</template>
        </el-table-column>
        <el-table-column prop="maxPatients" label="最大接诊数" />
        <el-table-column prop="bookedCount" label="已预约数" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? "启用" : "停用"
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="deleteSchedule(row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <div v-if="activeTab === 'appointment'" class="tab-content">
      <el-table :data="appointments" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="patientName" label="患者姓名" />
        <el-table-column prop="doctorName" label="医生姓名" />
        <el-table-column prop="departmentName" label="科室" />
        <el-table-column prop="appointmentDate" label="预约日期" />
        <el-table-column prop="timeSlot" label="时间段">
          <template #default="{ row }">{{
            { morning: "上午", afternoon: "下午", evening: "晚上" }[
              row.timeSlot
            ]
          }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="
                ['warning', 'primary', 'success', 'info', 'danger'][row.status]
              "
            >
              {{
                ["待确认", "已确认", "已完成", "已取消", "已过期"][row.status]
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              v-if="row.status <= 1"
              size="small"
              type="danger"
              @click="cancelAppointment(row)"
              >取消预约</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!--  -->
    <el-dialog v-model="showAddDept" title="新增科室" width="400px">
      <el-form :model="deptForm" label-width="80px">
        <el-form-item label="科室名称"
          ><el-input v-model="deptForm.name"
        /></el-form-item>
        <el-form-item label="科室描述"
          ><el-input v-model="deptForm.description" type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDept = false">取消</el-button>
        <el-button type="primary" @click="saveDept">保存</el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showAddDoctor" title="新增医生" width="500px">
      <el-form :model="doctorForm" label-width="80px">
        <el-form-item label="医生姓名"
          ><el-input v-model="doctorForm.name"
        /></el-form-item>
        <el-form-item label="职称">
          <!-- 2026-10-03 修复：4 个 option 的 value 原本都是空字符串，
               导致选了职称也存不进去，doctor.title 永远为空。 -->
          <el-select v-model="doctorForm.title" placeholder="请选择职称">
            <el-option label="主任医师" value="主任医师" />
            <el-option label="副主任医师" value="副主任医师" />
            <el-option label="主治医师" value="主治医师" />
            <el-option label="住院医师" value="住院医师" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属科室">
          <el-select v-model="doctorForm.departmentId">
            <el-option
              v-for="d in departments"
              :key="d.id"
              :label="d.name"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="擅长领域"
          ><el-input v-model="doctorForm.expertise"
        /></el-form-item>
        <el-form-item label="医生简介"
          ><el-input v-model="doctorForm.introduction" type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDoctor = false">取消</el-button>
        <el-button type="primary" @click="saveDoctor">保存</el-button>
      </template>
    </el-dialog>

    <!--  -->
    <el-dialog v-model="showAddSchedule" title="新增排班" width="400px">
      <el-form :model="scheduleForm" label-width="80px">
        <el-form-item label="选择医生">
          <el-select v-model="scheduleForm.doctorId">
            <el-option
              v-for="d in doctors"
              :key="d.id"
              :label="d.name"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排班日期"
          ><el-date-picker v-model="scheduleForm.scheduleDate" type="date"
        /></el-form-item>
        <el-form-item label="时间段">
          <el-select v-model="scheduleForm.timeSlot">
            <el-option label="上午" value="morning" />
            <el-option label="下午" value="afternoon" />
            <el-option label="晚上" value="evening" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大接诊数"
          ><el-input-number
            v-model="scheduleForm.maxPatients"
            :min="1"
            :max="100"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddSchedule = false">取消</el-button>
        <el-button type="primary" @click="saveSchedule">保存</el-button>
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
      deptForm: { id: null, name: "", description: "", status: 1 },
      doctorForm: {
        name: "",
        title: "",
        departmentId: null,
        expertise: "",
        introduction: "",
      },
      scheduleForm: {
        doctorId: null,
        scheduleDate: "",
        timeSlot: "morning",
        maxPatients: 30,
      },
    };
  },
  created() {
    this.loadAll();
  },
  methods: {
    async loadAll() {
      try {
        const [deptRes, docRes, schRes, aptRes] = await Promise.all([
          request.get("appointment/departments"),
          request.get("appointment/doctors"),
          request.get("appointment/schedules"),
          request.post("appointment/query", {}),
        ]);
        if (deptRes.data.code === 200) this.departments = deptRes.data.data;
        if (docRes.data.code === 200) this.doctors = docRes.data.data;
        if (schRes.data.code === 200) this.schedules = schRes.data.data;
        if (aptRes.data.code === 200) this.appointments = aptRes.data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async saveDept() {
      try {
        const url = this.deptForm.id
          ? "appointment/department/update"
          : "appointment/department/save";
        await request.post(url, this.deptForm);
        this.showAddDept = false;
        this.deptForm = { name: "", description: "" };
        this.loadAll();
        this.$message.success("科室保存成功");
      } catch (e) {
        this.$message.error("科室保存失败");
      }
    },
    async cancelAppointment(row) {
      const confirmed = await this.$swalConfirm({
        title: "确认取消",
        text: "取消该预约后将释放该时段名额，确定继续？",
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        await request.post("appointment/cancel/" + row.id, {});
        this.$message.success("已取消");
        this.loadAll();
      } catch (e) {
        this.$message.error("操作失败");
      }
    },
    editDept(row) {
      this.deptForm = { ...row };
      this.showAddDept = true;
    },
    async deleteDept(id) {
      const confirmed = await this.$swalConfirm({
        title: "确认删除",
        text: "删除科室将影响关联医生和排班，确定继续？",
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        await request.post("appointment/department/batchDelete", [id]);
        this.$message.success("科室删除成功");
        this.loadAll();
      } catch (e) {
        this.$message.error("科室删除失败");
      }
    },
    async saveDoctor() {
      try {
        const url = this.doctorForm.id
          ? "appointment/doctor/update"
          : "appointment/doctor/save";
        await request.post(url, this.doctorForm);
        this.showAddDoctor = false;
        this.doctorForm = { name: "", title: "", departmentId: null, expertise: "", introduction: "" };
        this.loadAll();
        this.$message.success("医生保存成功");
      } catch (e) {
        this.$message.error("医生保存失败");
      }
    },
    async deleteDoctor(id) {
      const confirmed = await this.$swalConfirm({
        title: "确认删除",
        text: "删除医生将影响关联排班和预约，确定继续？",
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        await request.post("appointment/doctor/batchDelete", [id]);
        this.$message.success("医生删除成功");
        this.loadAll();
      } catch (e) {
        this.$message.error("医生删除失败");
      }
    },
    editDoctor(row) {
      this.doctorForm = { ...row };
      this.showAddDoctor = true;
    },
    async saveSchedule() {
      try {
        const url = this.scheduleForm.id
          ? "appointment/schedule/update"
          : "appointment/schedule/save";
        const payload = {
          ...this.scheduleForm,
          scheduleDate: this.scheduleForm.scheduleDate instanceof Date
            ? this.formatDate(this.scheduleForm.scheduleDate)
            : this.scheduleForm.scheduleDate,
        };
        await request.post(url, payload);
        this.showAddSchedule = false;
        this.scheduleForm = { doctorId: null, scheduleDate: "", timeSlot: "morning", maxPatients: 30 };
        this.loadAll();
        this.$message.success("排班保存成功");
      } catch (e) {
        this.$message.error("排班保存失败");
      }
    },
    async deleteSchedule(id) {
      const confirmed = await this.$swalConfirm({
        title: "确认删除",
        text: "删除排班将影响用户预约，确定继续？",
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        await request.post("appointment/schedule/batchDelete", [id]);
        this.$message.success("排班删除成功");
        this.loadAll();
      } catch (e) {
        this.$message.error("排班删除失败");
      }
    },
    formatDate(d) {
      const pad = (n) => (n < 10 ? "0" + n : "" + n);
      return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
    },
  },
};
</script>

<style scoped>
.manage-container {
  padding: 20px;
}
.manage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.manage-header h2 {
  margin: 0;
  font-size: 20px;
}
.tabs {
  display: flex;
  gap: 4px;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 10px;
  margin-bottom: 20px;
}
.tab {
  padding: 10px 20px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.tab.active {
  background: #fff;
  color: #ff2442;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.toolbar {
  margin-bottom: 16px;
}
.btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  border: none;
}
.btn--primary {
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: #fff;
}
</style>
