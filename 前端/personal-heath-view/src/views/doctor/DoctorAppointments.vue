<template>
  <div class="appt-page">
    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-date-picker
        v-model="range"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 260px"
        @change="load"
      />

      <el-select
        v-model="status"
        placeholder="全部状态"
        clearable
        style="width: 130px"
        @change="load"
      >
        <el-option label="待接诊" :value="0" />
        <el-option label="已预约" :value="1" />
        <el-option label="已完成" :value="2" />
        <el-option label="已取消" :value="3" />
      </el-select>

      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>

      <div class="filter-bar__spacer"></div>
      <span class="filter-bar__total">
        共 <strong>{{ list.length }}</strong> 条
      </span>
    </div>

    <div class="list-wrap">
      <el-skeleton v-if="loading && !list.length" :rows="8" animated />

      <el-empty
        v-else-if="!list.length"
        description="该时间段暂无接诊记录"
        :image-size="110"
      />

      <template v-else>
        <!-- 按日期分组 -->
        <div
          v-for="group in grouped"
          :key="group.date"
          class="day-group"
        >
          <div class="day-head">
            <span class="day-head__date">{{ group.date }}</span>
            <span class="day-head__week">{{ group.week }}</span>
            <span class="day-head__count">{{ group.items.length }} 位患者</span>
            <el-tag
              v-if="isToday(group.date)"
              type="primary"
              size="small"
              effect="dark"
            >
              今天
            </el-tag>
          </div>

          <el-table
            :data="group.items"
            class="appt-table"
            :row-style="{ height: '54px' }"
          >
            <el-table-column label="序号" width="80" align="center">
              <template #default="{ row }">
                <span class="serial">{{ row.serialNumber }}</span>
              </template>
            </el-table-column>

            <el-table-column label="时段" width="90">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="slotType(row.timeSlot)">
                  {{ row.timeSlotName || slotText(row.timeSlot) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="patientName" label="患者" min-width="110">
              <template #default="{ row }">
                <el-link
                  type="primary"
                  :underline="false"
                  @click="goDetail(row)"
                >
                  {{ row.patientName || "匿名患者" }}
                </el-link>
              </template>
            </el-table-column>

            <el-table-column prop="patientPhone" label="联系电话" width="130">
              <template #default="{ row }">
                <span class="muted">{{ row.patientPhone || "—" }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="departmentName" label="科室" width="130">
              <template #default="{ row }">
                <span class="muted">{{ row.departmentName || "—" }}</span>
              </template>
            </el-table-column>

            <el-table-column label="主诉" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="muted">{{ row.symptomDescription || "未填写" }}</span>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="96" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small" effect="light">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="150" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  link
                  @click="goDetail(row)"
                >
                  {{ row.status === 0 || row.status === 1 ? "接诊" : "查看" }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { Refresh } from "@element-plus/icons-vue";
import { getMyAppointments } from "@/api/doctor.js";

export default {
  name: "DoctorAppointments",
  data() {
    return {
      Refresh,
      loading: false,
      list: [],
      // 默认看最近 7 天，医生最常用的视图
      range: this.defaultRange(),
      status: null,
    };
  },
  computed: {
    /** 按日期分组，便于医生按天巡诊 */
    grouped() {
      const map = new Map();
      this.list.forEach((a) => {
        const d = a.appointmentDate;
        if (!d) return;
        if (!map.has(d)) map.set(d, []);
        map.get(d).push(a);
      });
      return Array.from(map.entries())
        .sort((a, b) => a[0].localeCompare(b[0]))
        .map(([date, items]) => ({
          date,
          week: this.weekText(date),
          items,
        }));
    },
  },
  created() {
    this.load();
  },
  methods: {
    defaultRange() {
      const p = (n) => String(n).padStart(2, "0");
      const fmt = (d) => `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 6);
      return [fmt(start), fmt(end)];
    },

    async load() {
      this.loading = true;
      try {
        const params = {};
        if (Array.isArray(this.range) && this.range.length === 2) {
          params.fromDate = this.range[0];
          params.toDate = this.range[1];
        }
        if (this.status !== null && this.status !== undefined) {
          params.status = this.status;
        }
        const { data } = await getMyAppointments(params);
        if (data.code === 200) {
          this.list = data.data || [];
        } else {
          this.list = [];
          this.$message.error(data.msg || "加载失败");
        }
      } catch (e) {
        this.list = [];
      } finally {
        this.loading = false;
      }
    },

    /**
     * 进入患者详情。
     *
     * <p>不再在列表里直接「完成接诊」—— 那样只改预约状态，
     * 主诉/诊断/处方全丢，等于接诊没做。改为跳详情页填写完整就诊记录。
     * 已完成的（status=2）也允许进去查看历史档案。
     */
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

    isToday(date) {
      return date === new Date().toISOString().slice(0, 10);
    },
    weekText(date) {
      const d = new Date(date + "T00:00:00");
      if (isNaN(d.getTime())) return "";
      const w = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][d.getDay()];
      return w;
    },
    slotText(s) {
      return { morning: "上午", afternoon: "下午", evening: "晚间" }[s] || s || "—";
    },
    slotType(s) {
      return { morning: "warning", afternoon: "primary", evening: "info" }[s] || "info";
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
.appt-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}

.filter-bar {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-bar__spacer {
  flex: 1;
}
.filter-bar__total {
  font-size: 13px;
  color: #8b95a5;
}
.filter-bar__total strong {
  color: #2f7bf6;
  font-size: 15px;
}

.list-wrap {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 16px 18px 20px;
  flex: 1;
  overflow-y: auto;
}

.day-group {
  margin-bottom: 22px;
}
.day-group:last-child {
  margin-bottom: 0;
}
.day-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 10px;
  margin-bottom: 4px;
  border-bottom: 2px solid #eef2f8;
}
.day-head__date {
  font-size: 15px;
  font-weight: 650;
  color: #1b2433;
}
.day-head__week {
  font-size: 12.5px;
  color: #8b95a5;
}
.day-head__count {
  font-size: 12.5px;
  color: #b0b8c4;
  margin-left: auto;
}

.appt-table {
  --el-table-border-color: #f2f5f9;
}
.serial {
  display: inline-flex;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #eaf2ff;
  color: #2f7bf6;
  font-weight: 700;
  align-items: center;
  justify-content: center;
}
.patient {
  font-weight: 500;
  color: #1b2433;
}
.muted {
  color: #8b95a5;
  font-size: 13px;
}
</style>
