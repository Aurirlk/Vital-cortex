<template>
  <div class="appointment-container">
    <div class="appointment-header">
      <h1 class="page-title">预约挂号</h1>
      <p class="page-desc">选择科室与医生，在线预约门诊服务</p>
    </div>

    <!-- 预约步骤 -->
    <div class="steps-bar">
      <div
        class="step"
        :class="{
          'step--active': currentStep >= 1,
          'step--done': currentStep > 1,
        }"
      >
        <div class="step__num">1</div>
        <span>选择科室</span>
      </div>
      <div
        class="step-line"
        :class="{ 'step-line--active': currentStep > 1 }"
      ></div>
      <div
        class="step"
        :class="{
          'step--active': currentStep >= 2,
          'step--done': currentStep > 2,
        }"
      >
        <div class="step__num">2</div>
        <span>选择医生</span>
      </div>
      <div
        class="step-line"
        :class="{ 'step-line--active': currentStep > 2 }"
      ></div>
      <div
        class="step"
        :class="{
          'step--active': currentStep >= 3,
          'step--done': currentStep > 3,
        }"
      >
        <div class="step__num">3</div>
        <span>选择时间</span>
      </div>
      <div
        class="step-line"
        :class="{ 'step-line--active': currentStep > 3 }"
      ></div>
      <div class="step" :class="{ 'step--active': currentStep >= 4 }">
        <div class="step__num">4</div>
        <span>确认预约</span>
      </div>
    </div>

    <!-- 步骤 1：选择科室 -->
    <div v-if="currentStep === 1" class="step-content">
      <h2 class="section-title">选择科室</h2>
      <div class="department-grid">
        <div
          v-for="dept in departments"
          :key="dept.id"
          class="dept-card"
          :class="{ 'dept-card--selected': selectedDept?.id === dept.id }"
          @click="selectDepartment(dept)"
        >
          <div class="dept-card__icon">🏥</div>
          <div class="dept-card__name">{{ dept.name }}</div>
          <div class="dept-card__desc">{{ dept.description }}</div>
        </div>
      </div>
    </div>

    <!-- 步骤 2：选择医生 -->
    <div v-if="currentStep === 2" class="step-content">
      <h2 class="section-title">{{ selectedDept?.name }} - 选择医生</h2>
      <div class="doctor-grid">
        <div
          v-for="doctor in doctors"
          :key="doctor.id"
          class="doctor-card"
          :class="{ 'doctor-card--selected': selectedDoctor?.id === doctor.id }"
          @click="selectDoctor(doctor)"
        >
          <img
            :src="doctor.avatar || '/default-doctor.png'"
            class="doctor-card__avatar"
          />
          <div class="doctor-card__info">
            <div class="doctor-card__name">{{ doctor.name }}</div>
            <div class="doctor-card__title">{{ doctor.title }}</div>
            <div class="doctor-card__expertise">{{ doctor.expertise }}</div>
          </div>
          <div
            class="doctor-card__status"
            :class="doctor.isOnline ? 'online' : 'offline'"
          >
            {{ doctor.isOnline ? "在线" : "离线" }}
          </div>
        </div>
      </div>
    </div>

    <!-- 步骤 3：选择时间 -->
    <div v-if="currentStep === 3" class="step-content">
      <h2 class="section-title">选择就诊时间</h2>
      <div class="date-picker">
        <div
          v-for="date in availableDates"
          :key="date"
          class="date-btn"
          :class="{ 'date-btn--selected': selectedDate === date }"
          @click="selectDate(date)"
        >
          {{ formatDate(date) }}
        </div>
      </div>
      <!--
        阶段 4：用可拖拽的时间块表达「我有空的时段」。
        排班只到上午/下午/晚间这一档粒度，所以这里是**按钟点区间做重叠筛选**，
        而不是精确到分的预约 —— 组件本身支持 15 分钟粒度，
        后端哪天给出具体钟点，只需改 SLOT_RANGES 这一张表。
      -->
      <div class="free-ranges">
        <TimeBlockPlanner
          v-model="freeRanges"
          label="我有空的时间段"
          :max-blocks="3"
          :day-start="480"
          :day-end="1260"
          :step="30"
          empty-hint="在时间条上按住上下拖动，画出你有空的时段；不画 = 全天可约"
        />
      </div>

      <p v-if="freeRanges.length" class="filter-tip">
        已按你有空的时间段筛选：共 {{ timeSlots.length }} 个号源，符合的
        {{ visibleSlots.length }} 个
        <button class="link-btn" @click="freeRanges = []">显示全部</button>
      </p>

      <div class="time-slots">
        <div
          v-for="slot in visibleSlots"
          :key="slot.id"
          class="slot-card"
          :class="{ 'slot-card--full': slot.bookedCount >= slot.maxPatients }"
          @click="selectSlot(slot)"
        >
          <div class="slot-card__time">
            {{
              slot.timeSlot === "morning"
                ? "上午"
                : slot.timeSlot === "afternoon"
                ? "下午"
                : "晚间"
            }}
          </div>
          <div class="slot-card__count">
            剩余 {{ slot.maxPatients - slot.bookedCount }} 个号源
          </div>
        </div>
        <div v-if="timeSlots.length > 0 && visibleSlots.length === 0" class="no-slot">
          你选的时间段内没有可预约号源，试试放宽一点
        </div>
      </div>
    </div>

    <!-- 步骤 4：确认预约 -->
    <div v-if="currentStep === 4" class="step-content">
      <h2 class="section-title">确认预约信息</h2>
      <div class="confirm-card">
        <div class="confirm-item">
          <span class="confirm-label">预约科室：</span>
          <span class="confirm-value">{{ selectedDept?.name }}</span>
        </div>
        <div class="confirm-item">
          <span class="confirm-label">预约医生：</span>
          <span class="confirm-value"
            >{{ selectedDoctor?.name }} ({{ selectedDoctor?.title }})</span
          >
        </div>
        <div class="confirm-item">
          <span class="confirm-label">就诊时间：</span>
          <span class="confirm-value"
            >{{ formatDate(selectedDate) }}
            {{
              selectedSlot?.timeSlot === "morning"
                ? "上午"
                : selectedSlot?.timeSlot === "afternoon"
                ? "下午"
                : "晚间"
            }}</span
          >
        </div>
        <div class="confirm-item">
          <span class="label">症状描述：</span>
          <textarea
            v-model="symptomDesc"
            class="symptom-input"
            placeholder="请简要描述您的症状或就诊需求"
          ></textarea>
        </div>
      </div>
      <div class="confirm-actions">
        <button class="btn btn--outline" @click="currentStep = 3">
          上一步
        </button>
        <button class="btn btn--primary" @click="submitAppointment">
          确认预约
        </button>
      </div>
    </div>

    <!-- 我的预约 -->
    <div class="my-appointments">
      <h2 class="section-title">我的预约</h2>
      <div v-if="myAppointments.length === 0" class="empty-state">
        暂无预约记录
      </div>
      <div v-else class="appointment-list">
        <div v-for="apt in myAppointments" :key="apt.id" class="apt-item">
          <div class="apt-item__info">
            <div class="apt-item__doctor">
              {{ apt.doctorName }} ({{ apt.doctorTitle }})
            </div>
            <div class="apt-item__dept">{{ apt.departmentName }}</div>
            <div class="apt-item__time">
              {{ apt.appointmentDate }}
              {{
                apt.timeSlot === "morning"
                  ? "上午"
                  : apt.timeSlot === "afternoon"
                  ? "下午"
                  : "晚间"
              }}
            </div>
          </div>
          <div class="apt-item__status" :class="'status--' + apt.status">
            {{
              ["待确认", "已确认", "已完成", "已取消", "已过期"][apt.status] ||
              "未知"
            }}
          </div>
          <button
            v-if="apt.status === 0"
            class="btn btn--text"
            @click="cancelApt(apt.id)"
          >
            取消
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import request from "@/utils/request.js";
import TimeBlockPlanner from "@/components/effects/TimeBlockPlanner.vue";

/**
 * 排班时段 → 钟点区间（分钟数，距 0 点）。
 *
 * <p>后端 {@code doctor_schedule.time_slot} 只有 morning/afternoon/evening 三档，
 * 本身不带钟点。这张表是前端的约定，用来把「用户有空的时间段」和排班对上，
 * **不是从接口读来的**。它必须与后端时段的口径一致：
 * 若后端某天把 evening 改成 18:00-22:00，只改这里即可，其余逻辑不用动。</p>
 */
const SLOT_RANGES = {
  morning: [480, 720], // 08:00 - 12:00
  afternoon: [780, 1020], // 13:00 - 17:00
  evening: [1080, 1260], // 18:00 - 21:00
};

export default {
  name: "Appointment",
  components: { TimeBlockPlanner },
  data() {
    return {
      currentStep: 1,
      departments: [],
      doctors: [],
      myAppointments: [],
      selectedDept: null,
      selectedDoctor: null,
      selectedDate: null,
      selectedSlot: null,
      symptomDesc: "",
      availableDates: [],
      /** 用户勾出的空闲区间 [{start,end}]，空数组 = 不限 */
      freeRanges: [],
    };
  },
  computed: {
    /**
     * 按空闲区间过滤后的号源。
     *
     * <p>区间为空（用户没画）时原样返回全部 —— 「没表达偏好」和
     * 「偏好是空集」是两回事，后者会让号源列表整个消失，
     * 用户只会以为系统坏了。</p>
     *
     * <p>认不出 timeSlot 的排班**保留**而不是丢弃：过滤是收窄视图的手段，
     * 不该因为前端认不出一个新枚举值就把号源藏起来。</p>
     */
    visibleSlots() {
      if (!this.freeRanges.length) return this.timeSlots;
      return this.timeSlots.filter((slot) => {
        const range = SLOT_RANGES[slot.timeSlot];
        if (!range) return true;
        // 有重叠即可 —— 用户有空 14:00-17:00，下午档 13:00-17:00 应当留下
        return this.freeRanges.some(
          (r) => r.start < range[1] && r.end > range[0]
        );
      });
    },
  },
  created() {
    this.loadDepartments();
    this.loadMyAppointments();
    this.generateDates();
  },
  watch: {
    /**
     * 已选号源被过滤掉时要清掉选择。
     * 不清的话，「上一步」回到时间选择、把空闲时段改窄，
     * 再「下一步」带着一个已经不在列表里的号源进确认页 ——
     * 确认页还显示得好好的，提交时才失败。
     */
    visibleSlots(list) {
      if (this.selectedSlot && !list.some((s) => s.id === this.selectedSlot.id)) {
        this.selectedSlot = null;
      }
    },
  },
  methods: {
    generateDates() {
      const dates = [];
      for (let i = 0; i < 7; i++) {
        const d = new Date();
        d.setDate(d.getDate() + i);
        dates.push(d.toISOString().split("T")[0]);
      }
      this.availableDates = dates;
    },
    formatDate(date) {
      if (!date) return "";
      const d = new Date(date);
      const weekdays = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
      return `${d.getMonth() + 1}/${d.getDate()} ${weekdays[d.getDay()]}`;
    },
    async loadDepartments() {
      try {
        const { data } = await request.get("appointment/departments");
        if (data.code === 200) this.departments = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async loadDoctors(deptId) {
      try {
        const { data } = await request.get("appointment/doctors", {
          params: { departmentId: deptId },
        });
        if (data.code === 200) this.doctors = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async loadMyAppointments() {
      try {
        const { data } = await request.post("appointment/query", {});
        if (data.code === 200) this.myAppointments = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    selectDepartment(dept) {
      this.selectedDept = dept;
      this.loadDoctors(dept.id);
      this.currentStep = 2;
    },
    selectDoctor(doctor) {
      this.selectedDoctor = doctor;
      this.currentStep = 3;
    },
    selectDate(date) {
      this.selectedDate = date;
      this.loadSchedules();
    },
    async loadSchedules() {
      if (!this.selectedDoctor || !this.selectedDate) return;
      try {
        const { data } = await request.get("appointment/schedules", {
          params: { doctorId: this.selectedDoctor.id, date: this.selectedDate },
        });
        if (data.code === 200) this.timeSlots = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    selectSlot(slot) {
      if (slot.bookedCount >= slot.maxPatients) return;
      this.selectedSlot = slot;
      this.currentStep = 4;
    },
    async submitAppointment() {
      try {
        const { data } = await request.post("appointment/book", {
          scheduleId: this.selectedSlot.id,
          symptomDescription: this.symptomDesc,
        });
        if (data.code === 200) {
          this.$swal.fire({
            title: "预约成功",
            icon: "success",
            timer: 1500,
            showConfirmButton: false,
          });
          this.currentStep = 1;
          this.loadMyAppointments();
        } else {
          this.$swal.fire({ title: "预约失败", text: data.msg, icon: "error" });
        }
      } catch (e) {
        this.$message.error("预约提交失败");
      }
    },
    async cancelApt(id) {
      try {
        await request.post(`appointment/cancel/${id}`);
        this.loadMyAppointments();
      } catch (e) {
        console.error(e);
      }
    },
  },
};
</script>

<style scoped>
.appointment-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}
.appointment-header {
  text-align: center;
  margin-bottom: 32px;
}
.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 8px;
}
.page-desc {
  font-size: 15px;
  color: #999;
  margin: 0;
}

.steps-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 40px;
}
.step {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #ccc;
}
.step--active {
  color: #ff2442;
}
.step--done {
  color: #07c160;
}
.step__num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}
.step--active .step__num {
  background: #ff2442;
  color: #fff;
}
.step--done .step__num {
  background: #07c160;
  color: #fff;
}
.step-line {
  width: 60px;
  height: 2px;
  background: #f0f0f0;
  margin: 0 12px;
}
.step-line--active {
  background: #07c160;
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 20px;
}

.department-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}
.dept-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  border: 2px solid #f0f0f0;
  transition: all 0.25s;
  text-align: center;
}
.dept-card:hover {
  border-color: #ff2442;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.1);
}
.dept-card--selected {
  border-color: #ff2442;
  background: rgba(255, 36, 66, 0.04);
}
.dept-card__icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.dept-card__name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}
.dept-card__desc {
  font-size: 13px;
  color: #999;
}

.doctor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.doctor-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  border: 2px solid #f0f0f0;
  transition: all 0.25s;
  display: flex;
  align-items: center;
  gap: 12px;
}
.doctor-card:hover {
  border-color: #ff2442;
}
.doctor-card--selected {
  border-color: #ff2442;
  background: rgba(255, 36, 66, 0.04);
}
.doctor-card__avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  object-fit: cover;
}
.doctor-card__info {
  flex: 1;
}
.doctor-card__name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}
.doctor-card__title {
  font-size: 13px;
  color: #0ea5a5;
  margin: 2px 0;
}
.doctor-card__expertise {
  font-size: 12px;
  color: #999;
}
.doctor-card__status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}
.online {
  background: rgba(7, 193, 96, 0.1);
  color: #07c160;
}
.offline {
  background: #f0f0f0;
  color: #999;
}

/* 空闲时段规划器 */
.free-ranges {
  margin-bottom: 20px;
  /* 与商城的价格滑块同一处理：强调色跟着本页主色（红）走 */
}
.free-ranges :deep(.tbp__block) {
  background: #ff2442;
  box-shadow: 0 2px 8px rgba(255, 36, 66, 0.28);
}
.filter-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  font-size: 13px;
  color: #666;
}
.link-btn {
  border: none;
  background: none;
  padding: 0;
  color: #ff2442;
  font-size: 13px;
  cursor: pointer;
  text-decoration: underline;
}
.no-slot {
  width: 100%;
  padding: 24px;
  text-align: center;
  color: #999;
  font-size: 13px;
  background: #fafafa;
  border-radius: 12px;
}

.date-picker {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.date-btn {
  padding: 10px 20px;
  border-radius: 10px;
  border: 2px solid #f0f0f0;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.date-btn:hover {
  border-color: #ff2442;
}
.date-btn--selected {
  border-color: #ff2442;
  background: rgba(255, 36, 66, 0.06);
  color: #ff2442;
  font-weight: 600;
}

.time-slots {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.slot-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  border: 2px solid #f0f0f0;
  text-align: center;
  transition: all 0.2s;
}
.slot-card:hover:not(.slot-card--full) {
  border-color: #ff2442;
}
.slot-card--full {
  opacity: 0.5;
  cursor: not-allowed;
}
.slot-card__time {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}
.slot-card__count {
  font-size: 13px;
  color: #07c160;
}

.confirm-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}
.confirm-item {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}
.confirm-item:last-child {
  border-bottom: none;
  flex-direction: column;
}
.confirm-label {
  width: 80px;
  color: #999;
  font-size: 14px;
}
.confirm-value {
  flex: 1;
  color: #1a1a1a;
  font-weight: 500;
}
.symptom-input {
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  border: 2px solid #f0f0f0;
  border-radius: 10px;
  font-size: 14px;
  resize: vertical;
  min-height: 80px;
  box-sizing: border-box;
}
.symptom-input:focus {
  outline: none;
  border-color: #ff2442;
}

.confirm-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
.btn {
  padding: 10px 24px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}
.btn--primary {
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: #fff;
}
.btn--primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.3);
}
.btn--outline {
  background: transparent;
  border: 2px solid #ff2442;
  color: #ff2442;
}
.btn--text {
  background: none;
  border: none;
  color: #ff2442;
  padding: 4px 8px;
}

.my-appointments {
  margin-top: 40px;
}
.appointment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.apt-item {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.apt-item__info {
  flex: 1;
}
.apt-item__doctor {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}
.apt-item__dept {
  font-size: 13px;
  color: #0ea5a5;
  margin: 2px 0;
}
.apt-item__time {
  font-size: 13px;
  color: #999;
}
.apt-item__status {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 20px;
}
.status--0 {
  background: rgba(255, 180, 0, 0.1);
  color: #ffb400;
}
.status--1 {
  background: rgba(51, 112, 255, 0.1);
  color: #3370ff;
}
.status--2 {
  background: rgba(7, 193, 96, 0.1);
  color: #07c160;
}
.status--3 {
  background: #f0f0f0;
  color: #999;
}
.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}
</style>
