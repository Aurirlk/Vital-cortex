<template>
  <div class="doc-home">
    <!-- 欢迎条 -->
    <section class="welcome">
      <div class="welcome__text">
        <h3 class="welcome__title">{{ greeting }}，{{ doctor.name || "医生" }}</h3>
        <p class="welcome__sub">
          <span v-if="doctor.departmentName">{{ doctor.departmentName }}</span>
          <span v-else>医生端</span>
          <template v-if="doctor.title"> · {{ doctor.title }}</template>
          · 今天是 {{ doctor.today }}
        </p>
      </div>
      <el-button
        type="primary"
        :icon="Refresh"
        circle
        :loading="loading"
        @click="load"
      />
    </section>

    <!-- 统计卡片 -->
    <section class="stat-row">
      <div
        v-for="card in cards"
        :key="card.key"
        class="stat-card"
        :style="{ '--accent': card.color }"
        @click="card.jump && $router.push(card.jump)"
      >
        <div class="stat-card__icon" :style="{ background: card.color + '1a', color: card.color }">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-card__body">
          <div class="stat-card__value">{{ card.value }}</div>
          <div class="stat-card__label">{{ card.label }}</div>
        </div>
      </div>
    </section>

    <!-- 今日接诊 -->
    <section class="panel">
      <div class="panel__head">
        <h4 class="panel__title">今日接诊安排</h4>
        <el-button
          type="primary"
          link
          @click="$router.push('/doctor/appointments')"
        >
          查看全部
        </el-button>
      </div>

      <el-skeleton v-if="loading && !todayList.length" :rows="4" animated />

      <el-empty
        v-else-if="!todayList.length"
        description="今天暂无接诊安排"
        :image-size="90"
      />

        <ul v-else class="appt-list">
          <li
            v-for="a in todayList"
            :key="a.id"
            class="appt-item"
            @click="goDetail(a)"
          >
            <div class="appt-serial">{{ a.serialNumber }}</div>
            <div class="appt-main">
              <div class="appt-name">
                {{ a.patientName || "匿名患者" }}
                <el-icon class="appt-arrow"><ArrowRight /></el-icon>
              </div>
              <div class="appt-meta">
                {{ a.timeSlotName || slotText(a.timeSlot) }}
                <template v-if="a.departmentName"> · {{ a.departmentName }}</template>
                <template v-if="a.symptomDescription">
                  · {{ a.symptomDescription }}
                </template>
              </div>
            </div>
            <el-tag :type="statusTag(a.status)" size="small" effect="light">
              {{ statusText(a.status) }}
            </el-tag>
          </li>
        </ul>
    </section>
  </div>
</template>

<script>
import { Refresh, Tickets, Calendar, CircleCheck, Clock, ArrowRight } from "@element-plus/icons-vue";
import * as Icons from "@element-plus/icons-vue";
import { getDoctorOverview, getMyAppointments } from "@/api/doctor.js";

export default {
  name: "DoctorHome",
  data() {
    return {
      Refresh,
      loading: false,
      doctor: {},
      todayList: [],
    };
  },
  computed: {
    greeting() {
      const h = new Date().getHours();
      if (h < 6) return "夜深了";
      if (h < 12) return "早上好";
      if (h < 14) return "中午好";
      if (h < 18) return "下午好";
      return "晚上好";
    },
    cards() {
      return [
        {
          key: "today",
          label: "今日接诊",
          value: this.doctor.todayCount ?? 0,
          icon: "Tickets",
          color: "#2f7bf6",
          jump: "/doctor/appointments",
        },
        {
          key: "week",
          label: "近 7 天",
          value: this.doctor.weekCount ?? 0,
          icon: "Calendar",
          color: "#12b886",
          jump: "/doctor/appointments",
        },
        {
          key: "pending",
          label: "待接诊",
          value: this.doctor.pendingCount ?? 0,
          icon: "Clock",
          color: "#f59f0a",
          jump: "/doctor/appointments",
        },
        {
          key: "completed",
          label: "已完成",
          value: this.doctor.completedCount ?? 0,
          icon: "CircleCheck",
          color: "#7c5cff",
        },
        {
          key: "total",
          label: "累计接诊",
          value: this.doctor.totalCount ?? 0,
          icon: "DataLine",
          color: "#0ca678",
        },
      ];
    },
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [ov, list] = await Promise.all([
          getDoctorOverview(),
          getMyAppointments({ fromDate: this.todayStr(), toDate: this.todayStr() }),
        ]);
        if (ov.data && ov.data.code === 200) {
          this.doctor = ov.data.data || {};
        }
        if (list.data && list.data.code === 200) {
          this.todayList = list.data.data || [];
        }
      } catch (e) {
        // 拦截器已提示，这里不再重复弹窗
      } finally {
        this.loading = false;
      }
    },
    todayStr() {
      const d = new Date();
      const p = (n) => String(n).padStart(2, "0");
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
    },
    /** 点今日接诊条目直接进患者详情 */
    goDetail(row) {
      if (!row.patientId) {
        this.$message.warning("该预约缺少患者信息");
        return;
      }
      this.$router.push({
        path: "/doctor/patient",
        query: { patientId: row.patientId },
      });
    },
    slotText(s) {
      return { morning: "上午", afternoon: "下午", evening: "晚间" }[s] || s || "";
    },
    statusText(s) {
      return { 0: "待接诊", 1: "已预约", 2: "已完成", 3: "已取消" }[s] ?? "未知";
    },
    statusTag(s) {
      return { 0: "warning", 1: "primary", 2: "success", 3: "info" }[s] ?? "info";
    },
  },
};
</script>

<style scoped>
.doc-home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---------- 欢迎条 ---------- */
.welcome {
  background: linear-gradient(120deg, #2f7bf6 0%, #1a4fb4 100%);
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
}
.welcome__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}
.welcome__sub {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
}

/* ---------- 统计 ---------- */
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
}
.stat-card {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  transition: all 0.18s;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(20, 45, 90, 0.08);
  border-color: var(--accent);
}
.stat-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
  flex-shrink: 0;
}
.stat-card__value {
  font-size: 25px;
  font-weight: 700;
  color: #1b2433;
  line-height: 1.15;
}
.stat-card__label {
  font-size: 12.5px;
  color: #8b95a5;
  margin-top: 2px;
}

/* ---------- 今日接诊 ---------- */
.panel {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 18px 20px;
}
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.panel__title {
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
  color: #1b2433;
}

.appt-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.appt-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid #f2f5f9;
  cursor: pointer;
  transition: background 0.16s;
  border-radius: 6px;
}
.appt-item:hover {
  background: #f7f9fc;
}
.appt-item:last-child {
  border-bottom: none;
}
.appt-serial {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: #eaf2ff;
  color: #2f7bf6;
  font-weight: 700;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.appt-main {
  flex: 1;
  min-width: 0;
}
.appt-name {
  font-size: 14.5px;
  color: #1b2433;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}
.appt-arrow {
  font-size: 12px;
  color: #c0c6d0;
  opacity: 0;
  transition: opacity 0.16s, transform 0.16s;
  transform: translateX(-3px);
}
.appt-item:hover .appt-arrow {
  opacity: 1;
  transform: translateX(0);
  color: #2f7bf6;
}
.appt-meta {
  font-size: 12.5px;
  color: #8b95a5;
  margin-top: 3px;
}
</style>
