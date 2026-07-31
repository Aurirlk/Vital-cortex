<template>
  <div class="appointment-container">
    <div class="appointment-header">
      <h1 class="page-title"></h1>
      <p class="page-desc"></p>
    </div>

    <!--  -->
    <div class="steps-bar">
      <div class="step" :class="{ 'step--active': currentStep >= 1, 'step--done': currentStep > 1 }">
        <div class="step__num">1</div>
        <span></span>
      </div>
      <div class="step-line" :class="{ 'step-line--active': currentStep > 1 }"></div>
      <div class="step" :class="{ 'step--active': currentStep >= 2, 'step--done': currentStep > 2 }">
        <div class="step__num">2</div>
        <span></span>
      </div>
      <div class="step-line" :class="{ 'step-line--active': currentStep > 2 }"></div>
      <div class="step" :class="{ 'step--active': currentStep >= 3, 'step--done': currentStep > 3 }">
        <div class="step__num">3</div>
        <span></span>
      </div>
      <div class="step-line" :class="{ 'step-line--active': currentStep > 3 }"></div>
      <div class="step" :class="{ 'step--active': currentStep >= 4 }">
        <div class="step__num">4</div>
        <span></span>
      </div>
    </div>

    <!-- 1 -->
    <div v-if="currentStep === 1" class="step-content">
      <h2 class="section-title"></h2>
      <div class="department-grid">
        <div v-for="dept in departments" :key="dept.id"
             class="dept-card" :class="{ 'dept-card--selected': selectedDept?.id === dept.id }"
             @click="selectDepartment(dept)">
          <div class="dept-card__icon"></div>
          <div class="dept-card__name">{{ dept.name }}</div>
          <div class="dept-card__desc">{{ dept.description }}</div>
        </div>
      </div>
    </div>

    <!-- 2 -->
    <div v-if="currentStep === 2" class="step-content">
      <h2 class="section-title">{{ selectedDept?.name }} - </h2>
      <div class="doctor-grid">
        <div v-for="doctor in doctors" :key="doctor.id"
             class="doctor-card" :class="{ 'doctor-card--selected': selectedDoctor?.id === doctor.id }"
             @click="selectDoctor(doctor)">
          <img :src="doctor.avatar || '/default-doctor.png'" class="doctor-card__avatar" />
          <div class="doctor-card__info">
            <div class="doctor-card__name">{{ doctor.name }}</div>
            <div class="doctor-card__title">{{ doctor.title }}</div>
            <div class="doctor-card__expertise">{{ doctor.expertise }}</div>
          </div>
          <div class="doctor-card__status" :class="doctor.isOnline ? 'online' : 'offline'">
            {{ doctor.isOnline ? '' : '' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 3 -->
    <div v-if="currentStep === 3" class="step-content">
      <h2 class="section-title"></h2>
      <div class="date-picker">
        <div v-for="date in availableDates" :key="date"
             class="date-btn" :class="{ 'date-btn--selected': selectedDate === date }"
             @click="selectDate(date)">
          {{ formatDate(date) }}
        </div>
      </div>
      <div class="time-slots">
        <div v-for="slot in timeSlots" :key="slot.id"
             class="slot-card" :class="{ 'slot-card--full': slot.bookedCount >= slot.maxPatients }"
             @click="selectSlot(slot)">
          <div class="slot-card__time">{{ slot.timeSlot === 'morning' ? '' : slot.timeSlot === 'afternoon' ? '' : '' }}</div>
          <div class="slot-card__count">
             {{ slot.maxPatients - slot.bookedCount }} 
          </div>
        </div>
      </div>
    </div>

    <!-- 4 -->
    <div v-if="currentStep === 4" class="step-content">
      <h2 class="section-title"></h2>
      <div class="confirm-card">
        <div class="confirm-item">
          <span class="confirm-label"></span>
          <span class="confirm-value">{{ selectedDept?.name }}</span>
        </div>
        <div class="confirm-item">
          <span class="confirm-label"></span>
          <span class="confirm-value">{{ selectedDoctor?.name }} ({{ selectedDoctor?.title }})</span>
        </div>
        <div class="confirm-item">
          <span class="confirm-label"></span>
          <span class="confirm-value">{{ formatDate(selectedDate) }} {{ selectedSlot?.timeSlot === 'morning' ? '' : '' }}</span>
        </div>
        <div class="confirm-item">
          <span class="label"></span>
          <textarea v-model="symptomDesc" class="symptom-input" placeholder=""></textarea>
        </div>
      </div>
      <div class="confirm-actions">
        <button class="btn btn--outline" @click="currentStep = 3"></button>
        <button class="btn btn--primary" @click="submitAppointment"></button>
      </div>
    </div>

    <!--  -->
    <div class="my-appointments">
      <h2 class="section-title"></h2>
      <div v-if="myAppointments.length === 0" class="empty-state"></div>
      <div v-else class="appointment-list">
        <div v-for="apt in myAppointments" :key="apt.id" class="apt-item">
          <div class="apt-item__info">
            <div class="apt-item__doctor">{{ apt.doctorName }} ({{ apt.doctorTitle }})</div>
            <div class="apt-item__dept">{{ apt.departmentName }}</div>
            <div class="apt-item__time">{{ apt.appointmentDate }} {{ apt.timeSlot === 'morning' ? '' : '' }}</div>
          </div>
          <div class="apt-item__status" :class="'status--' + apt.status">
            {{ ['', '', '', '', ''][apt.status] }}
          </div>
          <button v-if="apt.status === 0" class="btn btn--text" @click="cancelApt(apt.id)"></button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "Appointment",
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
    };
  },
  created() {
    this.loadDepartments();
    this.loadMyAppointments();
    this.generateDates();
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
      const weekdays = ["", "", "", "", "", "", ""];
      return `${d.getMonth() + 1}/${d.getDate()} ${weekdays[d.getDay()]}`;
    },
    async loadDepartments() {
      try {
        const { data } = await request.get("appointment/departments");
        if (data.code === 200) this.departments = data.data;
      } catch (e) { console.error(e); }
    },
    async loadDoctors(deptId) {
      try {
        const { data } = await request.get("appointment/doctors", { params: { departmentId: deptId } });
        if (data.code === 200) this.doctors = data.data;
      } catch (e) { console.error(e); }
    },
    async loadMyAppointments() {
      try {
        const { data } = await request.post("appointment/query", {});
        if (data.code === 200) this.myAppointments = data.data;
      } catch (e) { console.error(e); }
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
          params: { doctorId: this.selectedDoctor.id, date: this.selectedDate }
        });
        if (data.code === 200) this.timeSlots = data.data;
      } catch (e) { console.error(e); }
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
          this.$swal.fire({ title: "", icon: "success", timer: 1500, showConfirmButton: false });
          this.currentStep = 1;
          this.loadMyAppointments();
        } else {
          this.$swal.fire({ title: "", text: data.msg, icon: "error" });
        }
      } catch (e) {
        this.$message.error("");
      }
    },
    async cancelApt(id) {
      try {
        await request.post(`appointment/cancel/${id}`);
        this.loadMyAppointments();
      } catch (e) { console.error(e); }
    },
  },
};
</script>

<style scoped>
.appointment-container { max-width: 1000px; margin: 0 auto; padding: 24px; }
.appointment-header { text-align: center; margin-bottom: 32px; }
.page-title { font-size: 28px; font-weight: 700; color: #1a1a1a; margin: 0 0 8px; }
.page-desc { font-size: 15px; color: #999; margin: 0; }

.steps-bar { display: flex; align-items: center; justify-content: center; margin-bottom: 40px; }
.step { display: flex; align-items: center; gap: 8px; color: #ccc; }
.step--active { color: #ff2442; }
.step--done { color: #07c160; }
.step__num { width: 28px; height: 28px; border-radius: 50%; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 600; }
.step--active .step__num { background: #ff2442; color: #fff; }
.step--done .step__num { background: #07c160; color: #fff; }
.step-line { width: 60px; height: 2px; background: #f0f0f0; margin: 0 12px; }
.step-line--active { background: #07c160; }

.section-title { font-size: 20px; font-weight: 600; color: #1a1a1a; margin: 0 0 20px; }

.department-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.dept-card { background: #fff; border-radius: 12px; padding: 20px; cursor: pointer; border: 2px solid #f0f0f0; transition: all 0.25s; text-align: center; }
.dept-card:hover { border-color: #ff2442; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(255,36,66,0.1); }
.dept-card--selected { border-color: #ff2442; background: rgba(255,36,66,0.04); }
.dept-card__icon { font-size: 32px; margin-bottom: 8px; }
.dept-card__name { font-size: 16px; font-weight: 600; color: #1a1a1a; margin-bottom: 4px; }
.dept-card__desc { font-size: 13px; color: #999; }

.doctor-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.doctor-card { background: #fff; border-radius: 12px; padding: 16px; cursor: pointer; border: 2px solid #f0f0f0; transition: all 0.25s; display: flex; align-items: center; gap: 12px; }
.doctor-card:hover { border-color: #ff2442; }
.doctor-card--selected { border-color: #ff2442; background: rgba(255,36,66,0.04); }
.doctor-card__avatar { width: 60px; height: 60px; border-radius: 50%; object-fit: cover; }
.doctor-card__info { flex: 1; }
.doctor-card__name { font-size: 16px; font-weight: 600; color: #1a1a1a; }
.doctor-card__title { font-size: 13px; color: #0EA5A5; margin: 2px 0; }
.doctor-card__expertise { font-size: 12px; color: #999; }
.doctor-card__status { font-size: 12px; padding: 2px 8px; border-radius: 10px; }
.online { background: rgba(7,193,96,0.1); color: #07c160; }
.offline { background: #f0f0f0; color: #999; }

.date-picker { display: flex; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
.date-btn { padding: 10px 20px; border-radius: 10px; border: 2px solid #f0f0f0; cursor: pointer; font-size: 14px; transition: all 0.2s; }
.date-btn:hover { border-color: #ff2442; }
.date-btn--selected { border-color: #ff2442; background: rgba(255,36,66,0.06); color: #ff2442; font-weight: 600; }

.time-slots { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.slot-card { background: #fff; border-radius: 12px; padding: 16px; cursor: pointer; border: 2px solid #f0f0f0; text-align: center; transition: all 0.2s; }
.slot-card:hover:not(.slot-card--full) { border-color: #ff2442; }
.slot-card--full { opacity: 0.5; cursor: not-allowed; }
.slot-card__time { font-size: 16px; font-weight: 600; color: #1a1a1a; margin-bottom: 4px; }
.slot-card__count { font-size: 13px; color: #07c160; }

.confirm-card { background: #fff; border-radius: 12px; padding: 24px; margin-bottom: 24px; }
.confirm-item { display: flex; padding: 12px 0; border-bottom: 1px solid #f5f5f5; }
.confirm-item:last-child { border-bottom: none; flex-direction: column; }
.confirm-label { width: 80px; color: #999; font-size: 14px; }
.confirm-value { flex: 1; color: #1a1a1a; font-weight: 500; }
.symptom-input { width: 100%; margin-top: 8px; padding: 12px; border: 2px solid #f0f0f0; border-radius: 10px; font-size: 14px; resize: vertical; min-height: 80px; box-sizing: border-box; }
.symptom-input:focus { outline: none; border-color: #ff2442; }

.confirm-actions { display: flex; gap: 12px; justify-content: flex-end; }
.btn { padding: 10px 24px; border-radius: 10px; font-size: 14px; font-weight: 500; cursor: pointer; border: none; transition: all 0.2s; }
.btn--primary { background: linear-gradient(135deg, #ff2442, #ff6b81); color: #fff; }
.btn--primary:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(255,36,66,0.3); }
.btn--outline { background: transparent; border: 2px solid #ff2442; color: #ff2442; }
.btn--text { background: none; border: none; color: #ff2442; padding: 4px 8px; }

.my-appointments { margin-top: 40px; }
.appointment-list { display: flex; flex-direction: column; gap: 12px; }
.apt-item { background: #fff; border-radius: 12px; padding: 16px; display: flex; align-items: center; gap: 16px; }
.apt-item__info { flex: 1; }
.apt-item__doctor { font-size: 15px; font-weight: 600; color: #1a1a1a; }
.apt-item__dept { font-size: 13px; color: #0EA5A5; margin: 2px 0; }
.apt-item__time { font-size: 13px; color: #999; }
.apt-item__status { font-size: 12px; padding: 4px 12px; border-radius: 20px; }
.status--0 { background: rgba(255,180,0,0.1); color: #ffb400; }
.status--1 { background: rgba(51,112,255,0.1); color: #3370ff; }
.status--2 { background: rgba(7,193,96,0.1); color: #07c160; }
.status--3 { background: #f0f0f0; color: #999; }
.empty-state { text-align: center; padding: 40px; color: #999; }
</style>
