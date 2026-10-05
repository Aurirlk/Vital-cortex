<template>
  <div class="sch-page">
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
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>

      <div class="filter-bar__spacer"></div>
      <div class="summary">
        <span>总排班 <strong>{{ list.length }}</strong></span>
        <span>已满 <strong class="warn">{{ fullCount }}</strong></span>
        <span>已停 <strong class="off">{{ stoppedCount }}</strong></span>
      </div>
    </div>

    <div class="list-wrap">
      <el-skeleton v-if="loading && !list.length" :rows="6" animated />

      <el-empty
        v-else-if="!list.length"
        description="该时间段暂无排班"
        :image-size="110"
      />

      <div v-else class="grid">
        <div
          v-for="s in list"
          :key="s.id"
          class="sch-card"
          :class="{ stopped: s.status === 0 }"
        >
          <div class="sch-card__date">
            <div class="sch-card__day">{{ dayOf(s.scheduleDate) }}</div>
            <div class="sch-card__full">{{ dateOf(s.scheduleDate) }}</div>
          </div>

          <div class="sch-card__body">
            <div class="sch-card__slot">
              <el-tag
                size="small"
                effect="plain"
                :type="slotType(s.timeSlot)"
              >
                {{ slotText(s.timeSlot) }}
              </el-tag>
              <el-tag
                v-if="s.status === 0"
                type="info"
                size="small"
                effect="dark"
              >
                已停诊
              </el-tag>
            </div>

            <!-- 预约进度 -->
            <div class="progress">
              <div class="progress__bar">
                <div
                  class="progress__fill"
                  :style="{
                    width: pct(s) + '%',
                    background: pctColor(s),
                  }"
                ></div>
              </div>
              <span class="progress__text">
                {{ s.bookedCount ?? 0 }} / {{ s.maxPatients }} 人
              </span>
            </div>
          </div>

          <div class="sch-card__action">
            <el-button
              v-if="s.status === 1"
              type="danger"
              size="small"
              plain
              @click="onDisable(s)"
            >
              停诊
            </el-button>
            <span v-else class="muted">—</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { Refresh } from "@element-plus/icons-vue";
import { getMySchedules, disableSchedule } from "@/api/doctor.js";

export default {
  name: "DoctorSchedules",
  data() {
    return {
      Refresh,
      loading: false,
      list: [],
      range: null, // null = 后端默认最近 30 天
    };
  },
  computed: {
    fullCount() {
      return this.list.filter(
        (s) => s.status === 1 && (s.bookedCount ?? 0) >= (s.maxPatients ?? 0)
      ).length;
    },
    stoppedCount() {
      return this.list.filter((s) => s.status === 0).length;
    },
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const params = {};
        if (Array.isArray(this.range) && this.range.length === 2) {
          params.fromDate = this.range[0];
          params.toDate = this.range[1];
        }
        const { data } = await getMySchedules(params);
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

    async onDisable(s) {
      const text = `停诊后患者将无法再预约该时段（${this.dateOf(s.scheduleDate)} ${this.slotText(s.timeSlot)}），确认停诊？`;
      try {
        await this.$confirm(text, "停诊确认", {
          confirmButtonText: "确认停诊",
          cancelButtonText: "取消",
          type: "warning",
        });
      } catch (e) {
        return;
      }

      try {
        const { data } = await disableSchedule(s.id);
        if (data.code === 200) {
          this.$message.success("已停诊");
          this.load();
        } else {
          this.$message.error(data.msg || "操作失败");
        }
      } catch (e) {
        // 拦截器已提示
      }
    },

    pct(s) {
      const max = s.maxPatients || 1;
      return Math.min(100, Math.round(((s.bookedCount ?? 0) / max) * 100));
    },
    pctColor(s) {
      const p = this.pct(s);
      if (s.status === 0) return "#c8cfd9";
      if (p >= 100) return "#f03e3e";
      if (p >= 70) return "#f59f0a";
      return "#12b886";
    },
    dateOf(d) {
      return d || "—";
    },
    dayOf(d) {
      if (!d) return "—";
      const date = new Date(d + "T00:00:00");
      if (isNaN(date.getTime())) return "—";
      const w = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][date.getDay()];
      return `${date.getMonth() + 1}/${date.getDate()} ${w}`;
    },
    slotText(s) {
      return { morning: "上午", afternoon: "下午", evening: "晚间" }[s] || s || "—";
    },
    slotType(s) {
      return { morning: "warning", afternoon: "primary", evening: "info" }[s] || "info";
    },
  },
};
</script>

<style scoped>
.sch-page {
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
.summary {
  display: flex;
  gap: 18px;
  font-size: 13px;
  color: #8b95a5;
}
.summary strong {
  color: #1b2433;
  font-size: 15px;
  margin-left: 3px;
}
.summary strong.warn {
  color: #f59f0a;
}
.summary strong.off {
  color: #a0a8b4;
}

.list-wrap {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 16px 18px 20px;
  flex: 1;
  overflow-y: auto;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.sch-card {
  border: 1px solid #e6ecf5;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: all 0.18s;
}
.sch-card:hover {
  border-color: #2f7bf6;
  box-shadow: 0 4px 14px rgba(20, 45, 90, 0.07);
}
.sch-card.stopped {
  opacity: 0.62;
  background: #fafbfc;
}

.sch-card__date {
  flex-shrink: 0;
  text-align: center;
  min-width: 74px;
}
.sch-card__day {
  font-size: 14px;
  font-weight: 650;
  color: #1b2433;
  line-height: 1.3;
}
.sch-card__full {
  font-size: 11.5px;
  color: #a0a8b4;
  margin-top: 2px;
}

.sch-card__body {
  flex: 1;
  min-width: 0;
}
.sch-card__slot {
  display: flex;
  gap: 6px;
  margin-bottom: 9px;
}

.progress {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress__bar {
  flex: 1;
  height: 6px;
  background: #eef2f7;
  border-radius: 4px;
  overflow: hidden;
}
.progress__fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}
.progress__text {
  font-size: 12px;
  color: #8b95a5;
  white-space: nowrap;
}

.sch-card__action {
  flex-shrink: 0;
}
.muted {
  color: #c0c6d0;
}
</style>
