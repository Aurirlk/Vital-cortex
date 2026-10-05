<template>
  <div class="pd-page" v-loading="loading">
    <!-- 加载失败 / 无权限 -->
    <el-result
      v-if="errorMsg"
      icon="warning"
      title="无法查看该患者"
      :sub-title="errorMsg"
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/doctor/appointments')">
          返回接诊列表
        </el-button>
      </template>
    </el-result>

    <template v-else-if="data.basic">
      <!-- ============ 顶部患者名片 ============ -->
      <section class="pd-hero">
        <div class="pd-hero__avatar">
          {{ (data.basic.userName || '患').slice(0, 1) }}
        </div>
        <div class="pd-hero__info">
          <div class="pd-hero__name">
            {{ data.basic.userName || '未命名患者' }}
            <el-tag size="small" effect="plain" class="pd-hero__gender">
              {{ data.basic.gender || '未知' }}
            </el-tag>
            <el-tag
              v-if="data.basic.age"
              size="small"
              effect="plain"
              type="info"
            >
              {{ data.basic.age }} 岁
            </el-tag>
            <el-tag
              v-if="data.vitals && data.vitals.bloodPressureLevel && data.vitals.bloodPressureLevel !== '正常'"
              size="small"
              type="danger"
              effect="dark"
            >
              {{ data.vitals.bloodPressureLevel }}
            </el-tag>
          </div>
          <div class="pd-hero__meta">
            <span v-if="data.basic.phone">📞 {{ data.basic.phone }}</span>
            <span v-if="data.profile.bmi">
              BMI {{ data.profile.bmi }} · {{ data.profile.bmiLabel }}
            </span>
            <span v-if="appt.hasAppointment">
              {{ appt.appointmentDate }} {{ appt.timeSlotName || slotText(appt.timeSlot) }}
              · {{ appt.departmentName || '门诊' }}
            </span>
          </div>
        </div>
        <div class="pd-hero__action">
          <el-button
            type="primary"
            size="large"
            :icon="EditPen"
            :disabled="!canConsult"
            @click="openConsultDialog"
          >
            {{ appt.hasVisitRecord ? '已接诊' : '开始接诊' }}
          </el-button>
        </div>
      </section>

      <!-- 未建档提醒 -->
      <el-alert
        v-if="!data.profileExists"
        type="info"
        :closable="false"
        show-icon
        title="该患者尚未建立健康档案"
        description="以下体征与病史信息为空，可提示患者在用户端完成建档后复查。"
        class="pd-alert"
      />

      <el-row :gutter="14" class="pd-row">
        <!-- ============ 左列 ============ -->
        <el-col :xs="24" :lg="16">
          <!-- 体征 -->
          <div class="pd-card">
            <div class="pd-card__head">
              <h4 class="pd-card__title">体征与生化指标</h4>
              <span v-if="!data.vitals.hasData" class="pd-empty-hint">暂无数据</span>
            </div>
            <div v-if="data.vitals.hasData" class="vital-grid">
              <div class="vital-item">
                <div class="vital-item__label">血压</div>
                <div class="vital-item__value">{{ data.vitals.bloodPressure || '—' }}</div>
                <div
                  class="vital-item__tag"
                  :class="bpClass(data.vitals.bloodPressureLevel)"
                >
                  {{ data.vitals.bloodPressureLevel }}
                </div>
              </div>
              <div class="vital-item">
                <div class="vital-item__label">静息心率</div>
                <div class="vital-item__value">
                  {{ data.vitals.restingHeartRate ?? '—'
                  }}<small v-if="data.vitals.restingHeartRate"> 次/分</small>
                </div>
              </div>
              <div class="vital-item">
                <div class="vital-item__label">空腹血糖</div>
                <div class="vital-item__value">
                  {{ data.vitals.fastingBloodGlucose ?? '—'
                  }}<small v-if="data.vitals.fastingBloodGlucose"> mmol/L</small>
                </div>
              </div>
              <div class="vital-item">
                <div class="vital-item__label">餐后血糖</div>
                <div class="vital-item__value">
                  {{ data.vitals.postprandialBloodGlucose ?? '—'
                  }}<small v-if="data.vitals.postprandialBloodGlucose">
                    mmol/L</small
                  >
                </div>
              </div>
              <div class="vital-item">
                <div class="vital-item__label">总胆固醇</div>
                <div class="vital-item__value">
                  {{ data.vitals.totalCholesterol ?? '—'
                  }}<small v-if="data.vitals.totalCholesterol"> mmol/L</small>
                </div>
              </div>
              <div class="vital-item">
                <div class="vital-item__label">甘油三酯</div>
                <div class="vital-item__value">
                  {{ data.vitals.triglycerides ?? '—'
                  }}<small v-if="data.vitals.triglycerides"> mmol/L</small>
                </div>
              </div>
              <div class="vital-item">
                <div class="vital-item__label">低密度脂蛋白</div>
                <div class="vital-item__value">
                  {{ data.vitals.ldlCholesterol ?? '—'
                  }}<small v-if="data.vitals.ldlCholesterol"> mmol/L</small>
                </div>
              </div>
              <div class="vital-item">
                <div class="vital-item__label">高密度脂蛋白</div>
                <div class="vital-item__value">
                  {{ data.vitals.hdlCholesterol ?? '—'
                  }}<small v-if="data.vitals.hdlCholesterol"> mmol/L</small>
                </div>
              </div>
            </div>
          </div>

          <!-- 病史 -->
          <div class="pd-card">
            <div class="pd-card__head">
              <h4 class="pd-card__title">病史摘要</h4>
            </div>
            <div class="history-grid">
              <div class="history-block">
                <div class="history-block__label">慢性病史</div>
                <div class="history-block__body">
                  <template v-if="hasItems(profile.chronicDiseases)">
                    <el-tag
                      v-for="(d, i) in profile.chronicDiseases"
                      :key="i"
                      type="danger"
                      size="small"
                      effect="light"
                      class="history-tag"
                    >
                      {{ d }}
                    </el-tag>
                  </template>
                  <span v-else class="pd-none">无</span>
                </div>
              </div>
              <div class="history-block">
                <div class="history-block__label">过敏史</div>
                <div class="history-block__body">
                  <template v-if="hasItems(profile.allergies)">
                    <el-tag
                      v-for="(a, i) in profile.allergies"
                      :key="i"
                      type="warning"
                      size="small"
                      effect="light"
                      class="history-tag"
                    >
                      {{ a }}
                    </el-tag>
                  </template>
                  <span v-else class="pd-none">无</span>
                </div>
              </div>
              <div class="history-block">
                <div class="history-block__label">正在用药</div>
                <div class="history-block__body">
                  <template v-if="hasItems(profile.medications)">
                    <el-tag
                      v-for="(m, i) in profile.medications"
                      :key="i"
                      type="primary"
                      size="small"
                      effect="plain"
                      class="history-tag"
                    >
                      {{ m }}
                    </el-tag>
                  </template>
                  <span v-else class="pd-none">无</span>
                </div>
              </div>
              <div class="history-block">
                <div class="history-block__label">手术史</div>
                <div class="history-block__body">
                  <template v-if="hasItems(profile.surgeries)">
                    <el-tag
                      v-for="(s, i) in profile.surgeries"
                      :key="i"
                      size="small"
                      effect="plain"
                      class="history-tag"
                    >
                      {{ s }}
                    </el-tag>
                  </template>
                  <span v-else class="pd-none">无</span>
                </div>
              </div>
              <div class="history-block">
                <div class="history-block__label">家族史</div>
                <div class="history-block__body">
                  <template v-if="hasItems(profile.familyHistory)">
                    <el-tag
                      v-for="(f, i) in profile.familyHistory"
                      :key="i"
                      size="small"
                      type="info"
                      effect="plain"
                      class="history-tag"
                    >
                      {{ f }}
                    </el-tag>
                  </template>
                  <span v-else class="pd-none">无</span>
                </div>
              </div>
              <div class="history-block">
                <div class="history-block__label">健康目标</div>
                <div class="history-block__body">
                  <template v-if="hasItems(profile.healthGoals)">
                    <el-tag
                      v-for="(g, i) in profile.healthGoals"
                      :key="i"
                      size="small"
                      type="success"
                      effect="plain"
                      class="history-tag"
                    >
                      {{ g }}
                    </el-tag>
                  </template>
                  <span v-else class="pd-none">未设置</span>
                </div>
              </div>
            </div>

            <!-- 生活方式 -->
            <div v-if="hasLifestyle" class="lifestyle">
              <span class="lifestyle__label">生活方式</span>
              <el-tag size="small" effect="plain" class="history-tag">
                吸烟：{{ yesNo(lifestyle.smoking) }}
              </el-tag>
              <el-tag size="small" effect="plain" class="history-tag">
                饮酒：{{ lifestyle.drinking || '—' }}
              </el-tag>
              <el-tag size="small" effect="plain" class="history-tag">
                运动：{{ lifestyle.exercise || '—' }}
              </el-tag>
            </div>
          </div>

          <!-- 健康趋势 -->
          <div class="pd-card">
            <div class="pd-card__head">
              <h4 class="pd-card__title">健康数据趋势</h4>
              <el-radio-group
                v-model="trendDays"
                size="small"
                @change="loadTrend"
              >
                <el-radio-button :value="30">30天</el-radio-button>
                <el-radio-button :value="90">90天</el-radio-button>
                <el-radio-button :value="180">180天</el-radio-button>
              </el-radio-group>
            </div>

            <el-empty
              v-if="!data.trend || !data.trend.length"
              description="该时间段暂无健康监测数据"
              :image-size="80"
            />

            <div v-else class="trend-list">
              <div
                v-for="(series, idx) in data.trend"
                :key="series.configId"
                class="trend-item"
              >
                <div class="trend-item__head">
                  <span class="trend-item__name">
                    {{ series.name || '指标' + (idx + 1) }}
                  </span>
                  <span class="trend-item__latest">
                    最新 {{ latest(series) }}
                    <small v-if="series.unit">{{ series.unit }}</small>
                  </span>
                </div>
                <div
                  ref="chartRefs"
                  class="trend-item__chart"
                  :data-chart-idx="idx"
                ></div>
              </div>
            </div>
          </div>
        </el-col>

        <!-- ============ 右列 ============ -->
        <el-col :xs="24" :lg="8">
          <!-- 历史就诊 -->
          <div class="pd-card">
            <div class="pd-card__head">
              <h4 class="pd-card__title">历史就诊</h4>
              <span class="pd-count">{{ data.visitHistory.length }} 次</span>
            </div>

            <el-empty
              v-if="!data.visitHistory.length"
              description="暂无就诊记录"
              :image-size="70"
            />

            <el-timeline v-else class="visit-timeline">
              <el-timeline-item
                v-for="v in data.visitHistory"
                :key="v.id"
                :timestamp="(v.appointmentDate || '') + ' ' + slotText(v.timeSlot)"
                placement="top"
                size="normal"
              >
                <div class="visit-item">
                  <div class="visit-item__diagnosis">{{ v.diagnosis || '未填写诊断' }}</div>
                  <div class="visit-item__doctor">
                    {{ v.doctorName || '—' }}
                    <span v-if="v.doctorTitle"> · {{ v.doctorTitle }}</span>
                  </div>
                  <div v-if="v.chiefComplaint" class="visit-item__complaint">
                    主诉：{{ v.chiefComplaint }}
                  </div>
                  <details v-if="v.prescription" class="visit-item__more">
                    <summary>查看处方</summary>
                    <pre>{{ v.prescription }}</pre>
                  </details>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>

          <!-- 随访任务 -->
          <div class="pd-card">
            <div class="pd-card__head">
              <h4 class="pd-card__title">随访任务</h4>
              <span class="pd-count">{{ data.followups.length }} 项</span>
            </div>
            <el-empty
              v-if="!data.followups.length"
              description="暂无随访安排"
              :image-size="70"
            />
            <ul v-else class="followup-list">
              <li v-for="f in data.followups" :key="f.id" class="followup-item">
                <div class="followup-item__title">{{ f.title || '随访任务' }}</div>
                <div v-if="f.description" class="followup-item__desc">
                  {{ f.description }}
                </div>
                <div class="followup-item__meta">
                  <span>截止 {{ f.dueDate || '待定' }}</span>
                  <el-tag
                    size="small"
                    :type="f.status === 2 ? 'success' : 'warning'"
                    effect="plain"
                  >
                    {{ f.status === 2 ? '已完成' : '待随访' }}
                  </el-tag>
                </div>
              </li>
            </ul>
          </div>
        </el-col>
      </el-row>
    </template>

    <!-- ============ 接诊弹窗 ============ -->
    <el-dialog
      v-model="dialogVisible"
      title="填写接诊记录"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="consultRef" :model="consult" :rules="consultRules" label-width="92px">
        <el-form-item label="主诉" prop="chiefComplaint">
          <el-input
            v-model="consult.chiefComplaint"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="患者本次就诊的主要症状，如：头晕、血压偏高"
          />
        </el-form-item>
        <el-form-item label="现病史" prop="presentIllness">
          <el-input
            v-model="consult.presentIllness"
            type="textarea"
            :rows="3"
            placeholder="症状发生时间、诱因、伴随症状、既往处理"
          />
        </el-form-item>
        <el-form-item label="诊断结论" prop="diagnosis">
          <el-input
            v-model="consult.diagnosis"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="必填。如：原发性高血压 1 级"
          />
        </el-form-item>
        <el-form-item label="处方" prop="prescription">
          <el-input
            v-model="consult.prescription"
            type="textarea"
            :rows="4"
            placeholder="用药名称、剂量、频次；建议每行一条"
          />
        </el-form-item>
        <el-form-item label="检查结果" prop="examinationResults">
          <el-input
            v-model="consult.examinationResults"
            type="textarea"
            :rows="2"
            placeholder="诊室测量值、化验单结论等"
          />
        </el-form-item>
        <el-form-item label="随访计划" prop="followUpPlan">
          <el-input
            v-model="consult.followUpPlan"
            type="textarea"
            :rows="2"
            placeholder="复诊时间、观察指标、预警症状"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConsult">
          保存并完成接诊
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { EditPen } from "@element-plus/icons-vue";
import * as echarts from "echarts";
import { getPatientDetail, startConsultation } from "@/api/doctor.js";

export default {
  name: "PatientDetail",
  data() {
    return {
      EditPen,
      loading: false,
      submitting: false,
      errorMsg: "",
      patientId: null,
      trendDays: 90,
      data: {
        basic: null,
        profileExists: false,
        profile: {},
        vitals: { hasData: false },
        trend: [],
        visitHistory: [],
        followups: [],
        currentAppointment: {},
      },
      dialogVisible: false,
      consultRef: null,
      consult: {},
      charts: [],
      consultRules: {
        diagnosis: [
          {
            required: true,
            // 与后端 DoctorAuth 无关，这里是就诊记录的核心产出，后端也强校验
            message: "请填写诊断结论",
            trigger: "blur",
          },
        ],
      },
    };
  },
  computed: {
    profile() {
      return this.data.profile || {};
    },
    lifestyle() {
      return this.profile.lifestyle || {};
    },
    appt() {
      return this.data.currentAppointment || {};
    },
    hasLifestyle() {
      const l = this.lifestyle;
      return !!(l.smoking || l.drinking || l.exercise);
    },
    /** 无预约、或已建过就诊记录时不能再建 */
    canConsult() {
      return this.appt.hasAppointment && !this.appt.hasVisitRecord;
    },
  },
  watch: {
    "$route.query.patientId": function (v) {
      if (v) {
        this.patientId = v;
        this.load();
      }
    },
  },
  created() {
    this.patientId = this.$route.query.patientId;
    if (!this.patientId) {
      this.errorMsg = "缺少患者参数，请从接诊列表进入";
      return;
    }
    this.load();
  },
  beforeUnmount() {
    this.disposeCharts();
  },
  methods: {
    async load() {
      this.loading = true;
      this.errorMsg = "";
      try {
        const { data } = await getPatientDetail(this.patientId, this.trendDays);
        if (data.code !== 200) {
          // 无医患关系 / 患者不存在 / 签名过期都走这里
          this.errorMsg = data.msg || "加载失败";
          this.data = this.emptyData();
          return;
        }
        this.data = data.data || this.emptyData();
        this.$nextTick(() => this.renderCharts());
      } catch (e) {
        // 403 走拦截器提示（可能是拿用户 token 访问医生端）
        this.errorMsg =
          e.response && e.response.data && e.response.data.msg
            ? e.response.data.msg
            : "加载失败，请稍后重试";
        this.data = this.emptyData();
      } finally {
        this.loading = false;
      }
    },

    async loadTrend() {
      // 只重拉趋势，避免整页 loading 闪烁
      try {
        const { data } = await getPatientDetail(this.patientId, this.trendDays);
        if (data.code === 200) {
          this.data.trend = data.data.trend || [];
          this.$nextTick(() => this.renderCharts());
        }
      } catch (e) {
        /* 拦截器已提示 */
      }
    },

    emptyData() {
      return {
        basic: null,
        profileExists: false,
        profile: {},
        vitals: { hasData: false },
        trend: [],
        visitHistory: [],
        followups: [],
        currentAppointment: {},
      };
    },

    // ---------------------------------------------------------- 趋势图

    disposeCharts() {
      this.charts.forEach((c) => c && c.dispose && c.dispose());
      this.charts = [];
    },

    renderCharts() {
      this.disposeCharts();
      const trend = this.data.trend || [];
      if (!trend.length) return;

      // 每次渲染前清空容器，否则 resize 后会叠加多个 canvas
      document.querySelectorAll(".trend-item__chart").forEach((el) => {
        el.innerHTML = "";
      });

      trend.forEach((series, idx) => {
        const el = document.querySelector(`.trend-item__chart[data-chart-idx="${idx}"]`);
        if (!el) return;
        const chart = echarts.init(el);
        const points = series.points || [];
        chart.setOption({
          grid: { left: 44, right: 16, top: 14, bottom: 26 },
          tooltip: {
            trigger: "axis",
            formatter: (p) => {
              const item = p[0];
              return `${item.axisValue}<br/>${series.name || '数值'}: ${item.data} ${
                series.unit || ""
              }`;
            },
          },
          xAxis: {
            type: "category",
            data: points.map((p) => (p.date || "").slice(0, 10)),
            axisLabel: { fontSize: 10, color: "#8b95a5" },
            axisLine: { lineStyle: { color: "#e6ecf5" } },
          },
          yAxis: {
            type: "value",
            scale: true,
            axisLabel: { fontSize: 10, color: "#8b95a5" },
            splitLine: { lineStyle: { color: "#f2f5f9" } },
          },
          series: [
            {
              type: "line",
              smooth: true,
              data: points.map((p) => p.value),
              showSymbol: points.length <= 30,
              symbolSize: 5,
              lineStyle: { width: 2 },
              areaStyle: { opacity: 0.08 },
            },
          ],
        });
        this.charts.push(chart);
      });

      // 侧边栏折叠会导致容器宽度变化，需要重算
      window.addEventListener("resize", this.handleResize);
    },

    handleResize() {
      this.charts.forEach((c) => c && c.resize && c.resize());
    },

    latest(series) {
      const pts = series.points || [];
      if (!pts.length) return "—";
      return pts[pts.length - 1].value;
    },

    // ---------------------------------------------------------- 接诊

    openConsultDialog() {
      if (!this.canConsult) {
        if (this.appt.hasVisitRecord) {
          this.$message.info("该预约已有就诊记录");
        } else {
          this.$message.warning("未找到有效预约，无法接诊");
        }
        return;
      }
      this.consult = {
        chiefComplaint: this.appt.symptomDescription || "",
        presentIllness: "",
        diagnosis: "",
        prescription: "",
        examinationResults: "",
        followUpPlan: "",
      };
      this.dialogVisible = true;
    },

    async submitConsult() {
      if (!this.consultRef) return;
      try {
        await this.consultRef.validate();
      } catch (e) {
        return;
      }
      this.submitting = true;
      try {
        const { data } = await startConsultation(this.appt.appointmentId, this.consult);
        if (data.code === 200) {
          this.$message.success(data.msg || "接诊记录已保存");
          this.dialogVisible = false;
          this.load(); // 刷新后按钮变「已接诊」，历史就诊出现新记录
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        if (!e.response) {
          this.$message.error("网络异常，请稍后重试");
        }
      } finally {
        this.submitting = false;
      }
    },

    // ---------------------------------------------------------- 展示工具

    hasItems(arr) {
      return Array.isArray(arr) && arr.length > 0;
    },
    yesNo(v) {
      if (v === true) return "是";
      if (v === false) return "否";
      return v || "—";
    },
    slotText(s) {
      return { morning: "上午", afternoon: "下午", evening: "晚间" }[s] || s || "";
    },
    bpClass(level) {
      if (!level || level === "正常") return "";
      if (level === "1级高血压") return "is-warn";
      return "is-danger";
    },
  },
};
</script>

<style scoped>
.pd-page {
  min-height: 100%;
}

/* ---------- 患者名片 ---------- */
.pd-hero {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 18px 22px;
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 14px;
}
.pd-hero__avatar {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f7bf6, #1a4fb4);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pd-hero__info {
  flex: 1;
  min-width: 0;
}
.pd-hero__name {
  font-size: 19px;
  font-weight: 650;
  color: #1b2433;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.pd-hero__gender {
  transform: translateY(-1px);
}
.pd-hero__meta {
  margin-top: 7px;
  font-size: 13px;
  color: #8b95a5;
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}

.pd-alert {
  margin-bottom: 14px;
}
.pd-row {
  margin: 0 !important;
}

/* ---------- 卡片 ---------- */
.pd-card {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  padding: 16px 18px 18px;
  margin-bottom: 14px;
}
.pd-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.pd-card__title {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: #1b2433;
}
.pd-count {
  font-size: 12.5px;
  color: #a0a8b4;
}
.pd-empty-hint,
.pd-none {
  font-size: 12.5px;
  color: #b0b8c4;
}

/* ---------- 体征 ---------- */
.vital-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}
.vital-item {
  border: 1px solid #eef2f7;
  border-radius: 9px;
  padding: 11px 13px;
  position: relative;
}
.vital-item__label {
  font-size: 12px;
  color: #8b95a5;
}
.vital-item__value {
  font-size: 17px;
  font-weight: 650;
  color: #1b2433;
  margin-top: 4px;
  line-height: 1.25;
}
.vital-item__value small {
  font-size: 11.5px;
  font-weight: 400;
  color: #a0a8b4;
  margin-left: 2px;
}
.vital-item__tag {
  position: absolute;
  top: 9px;
  right: 11px;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.vital-item__tag.is-warn {
  background: #fff4e6;
  color: #f59f0a;
}
.vital-item__tag.is-danger {
  background: #ffecec;
  color: #f03e3e;
}

/* ---------- 病史 ---------- */
.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 14px;
}
.history-block__label {
  font-size: 12.5px;
  color: #8b95a5;
  margin-bottom: 7px;
}
.history-block__body {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-height: 24px;
}
.history-tag {
  margin: 0;
}
.lifestyle {
  margin-top: 15px;
  padding-top: 13px;
  border-top: 1px dashed #eef2f7;
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
}
.lifestyle__label {
  font-size: 12.5px;
  color: #8b95a5;
  margin-right: 3px;
}

/* ---------- 趋势 ---------- */
.trend-item {
  margin-bottom: 18px;
}
.trend-item:last-child {
  margin-bottom: 0;
}
.trend-item__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}
.trend-item__name {
  font-size: 13.5px;
  color: #1b2433;
  font-weight: 500;
}
.trend-item__latest {
  font-size: 12.5px;
  color: #2f7bf6;
  font-weight: 600;
}
.trend-item__latest small {
  color: #a0a8b4;
  font-weight: 400;
  margin-left: 2px;
}
.trend-item__chart {
  height: 168px;
  width: 100%;
}

/* ---------- 就诊时间线 ---------- */
.visit-timeline {
  padding-left: 2px;
}
.visit-item__diagnosis {
  font-size: 14px;
  font-weight: 600;
  color: #1b2433;
  margin-bottom: 4px;
}
.visit-item__doctor {
  font-size: 12.5px;
  color: #8b95a5;
}
.visit-item__complaint {
  font-size: 12.5px;
  color: #66707f;
  margin-top: 5px;
  line-height: 1.6;
}
.visit-item__more {
  margin-top: 6px;
  font-size: 12.5px;
}
.visit-item__more summary {
  cursor: pointer;
  color: #2f7bf6;
  outline: none;
}
.visit-item__more pre {
  white-space: pre-wrap;
  background: #f7f9fc;
  border-radius: 6px;
  padding: 9px 11px;
  margin: 7px 0 0;
  font-size: 12.5px;
  color: #4a5567;
  line-height: 1.65;
  font-family: inherit;
}

/* ---------- 随访 ---------- */
.followup-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.followup-item {
  padding: 10px 0;
  border-bottom: 1px solid #f2f5f9;
}
.followup-item:last-child {
  border-bottom: none;
}
.followup-item__title {
  font-size: 13.5px;
  color: #1b2433;
}
.followup-item__desc {
  font-size: 12.5px;
  color: #8b95a5;
  margin-top: 4px;
  line-height: 1.6;
}
.followup-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 5px;
  font-size: 12.5px;
  color: #8b95a5;
}
</style>
