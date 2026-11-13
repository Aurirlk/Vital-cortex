<template>
  <div class="dash-page">
    <!-- 页面头：只在 Bento 栅格之外占一行，不参与 8/4 分栏，
         这样栅格本身的列宽永远是稳定的 12 等分。 -->
    <header class="dash-head">
      <div class="dash-head__main">
        <h2 class="dash-head__title">{{ greeting }}，管理员</h2>
        <p class="dash-head__sub">
          平台运行概览 · {{ fullDateStr }}
          <span v-if="lastUpdateTime" class="dash-head__stamp"
            >更新于 {{ lastUpdateTime }}</span
          >
        </p>
      </div>
      <div class="dash-head__side">
        <button
          class="dash-live"
          :class="{ 'is-paused': !pollingActive }"
          :title="pollingActive ? '点击暂停自动刷新' : '点击恢复自动刷新'"
          @click="togglePolling"
        >
          <i class="dash-live__dot"></i>
          {{ pollingActive ? "每 30 秒刷新" : "已暂停" }}
        </button>
        <button class="dash-refresh" :disabled="refreshing" @click="refresh">
          {{ refreshing ? "刷新中…" : "刷新" }}
        </button>
      </div>
    </header>

    <!-- Bento 栅格：12 列。
         主模块 8 列 × 2 行；右侧两块各 4 列 × 1 行；
         下方 6 个指标块各 4 列，两行三列。 -->
    <div class="bento">
      <!-- ============ 主模块：8 列 × 2 行 ============ -->
      <section class="tile tile--primary">
        <div class="tile__head">
          <div class="tile__titles">
            <h3 class="tile__title">平台活跃趋势</h3>
            <p class="tile__sub">最近 30 天 · 每日新增用户与健康数据记录</p>
          </div>
          <div class="legend">
            <span class="legend__item">
              <i class="legend__dot" style="background: #0050cb"></i>
              新增用户
              <b>{{ numOrDash(lastOf(trend.users)) }}</b>
            </span>
            <span class="legend__item">
              <i class="legend__dot" style="background: #10b981"></i>
              健康记录
              <b>{{ numOrDash(lastOf(trend.health)) }}</b>
            </span>
          </div>
        </div>
        <div ref="trendChart" class="tile__chart"></div>

        <div
          v-if="!loaded.trend"
          class="tile__overlay tile__overlay--skeleton"
          aria-hidden="true"
        >
          <div class="sk-chart">
            <i
              v-for="n in 14"
              :key="n"
              :style="{ height: 25 + ((n * 37) % 65) + '%' }"
            ></i>
          </div>
        </div>
        <div v-else-if="trendFailed" class="tile__overlay">
          <p>趋势数据加载失败</p>
          <button class="tile__retry" @click="loadTrend">重试</button>
        </div>
        <div v-else-if="trendEmpty" class="tile__overlay">
          <p>最近 30 天暂无新增数据</p>
        </div>
      </section>

      <!-- ============ 次要块一：最近预约 4 列 ============ -->
      <section class="tile tile--side">
        <div class="tile__head">
          <div class="tile__titles">
            <h3 class="tile__title">最近预约</h3>
            <!-- 待确认单量：管理员每天要处理的积压。取不到时整块不显示，
                 而不是显示 0 —— 0 会被读成「没有要处理的」，那是错的安慰。 -->
            <p v-if="pendingAppointments !== null" class="tile__pending">
              待确认 <b>{{ pendingAppointments }}</b> 单
            </p>
          </div>
          <button
            class="tile__link"
            @click="$router.push('/admin/appointmentManage')"
          >
            全部
          </button>
        </div>
        <p v-if="appointmentFailed" class="tile__empty">预约数据加载失败</p>
        <p
          v-else-if="loaded.appointments && !recentAppointments.length"
          class="tile__empty"
        >
          暂无预约
        </p>
        <ul
          v-else-if="!loaded.appointments"
          class="mini-list mini-list--skeleton"
          aria-hidden="true"
        >
          <li v-for="n in 3" :key="n" class="mini-row">
            <i class="sk-line"></i>
          </li>
        </ul>
        <ul v-else class="mini-list">
          <li
            v-for="item in recentAppointments"
            :key="item.id"
            class="mini-row"
            :title="rowTitle(item)"
          >
            <span class="mini-row__flag" :class="statusTone(item.status)">{{
              statusText(item.status)
            }}</span>
            <span class="mini-row__body">
              <b class="mini-row__line1">
                {{ item.patientName || "匿名患者" }} ·
                {{ item.departmentName || "未分科" }}
              </b>
              <small class="mini-row__line2">
                {{ item.doctorName || "未指定医生" }}
                <template v-if="item.timeSlotName">
                  · {{ item.timeSlotName }}</template
                >
              </small>
            </span>
            <span class="mini-row__time">{{
              shortDate(item.scheduleDate)
            }}</span>
          </li>
        </ul>
      </section>

      <!-- ============ 次要块二：我的消息 4 列 ============ -->
      <section class="tile tile--side">
        <div class="tile__head">
          <h3 class="tile__title">我的消息</h3>
          <button
            class="tile__link"
            @click="$router.push('/admin/messageManage')"
          >
            全部
          </button>
        </div>
        <p v-if="messageFailed" class="tile__empty">消息加载失败</p>
        <p v-else-if="loaded.messages && !messages.length" class="tile__empty">
          暂无消息
        </p>
        <ul
          v-else-if="!loaded.messages"
          class="mini-list mini-list--skeleton"
          aria-hidden="true"
        >
          <li v-for="n in 3" :key="n" class="mini-row">
            <i class="sk-line"></i>
          </li>
        </ul>
        <ul v-else class="mini-list">
          <li
            v-for="item in messages"
            :key="item.id"
            class="mini-row mini-row--msg"
            :class="{ 'is-unread': !item.isRead }"
          >
            <i class="mini-row__unread"></i>
            <span class="mini-row__body">
              <b class="mini-row__line1">{{ messageTitle(item) }}</b>
              <small class="mini-row__line2">{{ item.content || "—" }}</small>
            </span>
            <span class="mini-row__time">{{
              formatDateTime(item.createTime)
            }}</span>
          </li>
        </ul>
      </section>

      <!-- ============ 指标块：4 列 × 6 ============ -->
      <article v-for="kpi in kpis" :key="kpi.key" class="tile tile--kpi">
        <div class="kpi__top">
          <span
            class="kpi__icon"
            :style="{ background: kpi.bg, color: kpi.color }"
          >
            <el-icon :size="18"><component :is="iconOf(kpi.icon)" /></el-icon>
          </span>
          <span class="kpi__label">{{ kpi.label }}</span>
        </div>
        <div class="kpi__value" :class="{ 'is-missing': kpi.value === null }">
          {{ kpi.value === null ? "—" : formatNumber(kpi.value) }}
        </div>
        <div class="kpi__foot">
          <span
            v-if="kpi.delta !== null && kpi.delta !== undefined"
            class="kpi__delta"
            :class="{ 'is-zero': !kpi.delta }"
          >
            今日 +{{ kpi.delta }}
          </span>
          <span class="kpi__hint">{{ kpi.hint }}</span>
        </div>
      </article>
    </div>
  </div>
</template>

<script>
import * as echarts from "echarts";
import request from "@/utils/request.js";
import {
  User,
  Document,
  Cpu,
  DataLine,
  ShoppingCart,
  Calendar,
} from "@element-plus/icons-vue";

/*
 * 图标用「模块级常量 + 方法」而不是放进 data/computed：
 * 组件定义一旦被 Vue 的响应式系统代理，控制台会不断警告
 * "Vue received a Component that was made a reactive object"。
 * iconOf() 在渲染时按名字取回原始对象，不进入响应式状态。
 */
const ICONS = { User, Document, Cpu, DataLine, ShoppingCart, Calendar };

/** 预约状态文案。与 admin/AppointmentManage.vue 的表头映射保持一致。 */
const APPOINTMENT_STATUS = ["待确认", "已确认", "已完成", "已取消", "已过期"];
const STATUS_TONE = ["warn", "info", "ok", "muted", "muted"];

const TREND_DAYS = 30;
const POLL_INTERVAL = 30000;

export default {
  name: "AdminDashboard",
  data() {
    return {
      refreshing: false,
      lastUpdateTime: "",
      pollingActive: true,
      pollTimer: null,

      /*
       * 三块数据各自独立成败：某个接口挂了只影响自己那一块，
       * 不能因为「消息接口 500」把整张看板变成一片 0。
       * 因此失败时保持 null / []，由模板显示「—」或「加载失败」。
       */
      staticStats: null, // { 存量用户: n, ... }
      mallTotal: null,
      appointmentTotal: null,
      recentAppointments: null, // null = 请求失败（不是「没有数据」，空数据是 []）
      pendingAppointments: null, // /dashboard/stats 的待确认单量，null = 没读到
      messages: null,

      /*
       * 首次请求回来之前 null 既可能是「还没请求」也可能是「请求失败」。
       * 不区分的话，mounted 的第一帧就会闪一下「加载失败」——
       * 所以用独立的 loaded 标记把「未开始」与「失败」分开。
       */
      loaded: { trend: false, appointments: false, messages: false },

      trend: { labels: [], users: null, health: null },
    };
  },
  computed: {
    greeting() {
      const h = new Date().getHours();
      if (h < 6) return "凌晨好";
      if (h < 12) return "上午好";
      if (h < 14) return "中午好";
      if (h < 18) return "下午好";
      return "晚上好";
    },
    fullDateStr() {
      const d = new Date();
      const weeks = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
      return `${d.getFullYear()} 年 ${
        d.getMonth() + 1
      } 月 ${d.getDate()} 日 · ${weeks[d.getDay()]}`;
    },
    trendFailed() {
      return (
        this.loaded.trend &&
        this.trend.users === null &&
        this.trend.health === null
      );
    },
    trendEmpty() {
      const sum = (arr) =>
        Array.isArray(arr) ? arr.reduce((s, v) => s + (v || 0), 0) : 0;
      /*
       * 只有两条序列都拿到了才敢说「暂无数据」——
       * 其中一条请求失败时数组是 null，把它也算成 0 会把
       * 「接口挂了」伪装成「确实没有新增」。
       */
      return (
        this.loaded.trend &&
        Array.isArray(this.trend.users) &&
        Array.isArray(this.trend.health) &&
        sum(this.trend.users) === 0 &&
        sum(this.trend.health) === 0
      );
    },
    appointmentFailed() {
      return this.loaded.appointments && this.recentAppointments === null;
    },
    messageFailed() {
      return this.loaded.messages && this.messages === null;
    },
    /** 今天这一桶的增量：轴的最后一项就是后端返回的「今天」 */
    kpis() {
      const stats = this.staticStats || {};
      const usersToday = this.lastOf(this.trend.users);
      const healthToday = this.lastOf(this.trend.health);
      return [
        {
          key: "users",
          label: "存量用户",
          icon: "User",
          color: "#0050cb",
          bg: "rgba(0, 80, 203, 0.1)",
          value: this.pick(stats["存量用户"]),
          delta: usersToday,
          hint: "累计注册账号",
        },
        {
          key: "news",
          label: "收录资讯",
          icon: "Document",
          color: "#632ce5",
          bg: "rgba(99, 44, 229, 0.1)",
          value: this.pick(stats["收录资讯"]),
          delta: null,
          hint: "已发布内容",
        },
        {
          key: "models",
          label: "收录模型",
          icon: "Cpu",
          color: "#0ea5e9",
          bg: "rgba(14, 165, 233, 0.1)",
          value: this.pick(stats["收录模型"]),
          delta: null,
          hint: "健康指标模型",
        },
        {
          key: "health",
          label: "健康数据",
          icon: "DataLine",
          color: "#10b981",
          bg: "rgba(16, 185, 129, 0.1)",
          value: this.pick(stats["健康数据"]),
          delta: healthToday,
          hint: "累计记录条数",
        },
        {
          key: "products",
          label: "商品总数",
          icon: "ShoppingCart",
          color: "#f59e0b",
          bg: "rgba(245, 158, 11, 0.1)",
          value: this.pick(this.mallTotal),
          delta: null,
          hint: "商城商品档案",
        },
        {
          key: "appointments",
          label: "预约总数",
          icon: "Calendar",
          color: "#ef4444",
          bg: "rgba(239, 68, 68, 0.1)",
          value: this.pick(this.appointmentTotal),
          delta: null,
          hint: "累计预约单",
        },
      ];
    },
  },
  mounted() {
    this.$nextTick(() => {
      this.initChart();
      this.loadAll();
    });
    this.startPolling();
    window.addEventListener("resize", this.resizeChart);
  },
  beforeUnmount() {
    this.stopPolling();
    window.removeEventListener("resize", this.resizeChart);
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    if (this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
  },
  methods: {
    iconOf(name) {
      return ICONS[name];
    },
    /* ---------------- 图表 ---------------- */
    initChart() {
      const el = this.$refs.trendChart;
      if (!el) return;
      this.chart = echarts.init(el);
      this.renderTrend();
      /*
       * 管理端侧栏可以折叠，内容区宽度会变，但窗口本身不变 ——
       * 只听 window.resize 的话，折叠侧栏后图表会留一块空白。
       * ResizeObserver 直接盯容器尺寸，两种变化都能接住。
       */
      if (typeof ResizeObserver !== "undefined") {
        this.resizeObserver = new ResizeObserver(() => this.resizeChart());
        this.resizeObserver.observe(el);
      }
    },
    resizeChart() {
      if (this.chart) this.chart.resize();
    },
    renderTrend() {
      if (!this.chart) return;
      const { labels, users, health } = this.trend;
      this.chart.setOption(
        {
          color: ["#0050cb", "#10b981"],
          tooltip: {
            trigger: "axis",
            backgroundColor: "rgba(15, 23, 42, 0.92)",
            borderWidth: 0,
            padding: [8, 12],
            textStyle: { color: "#fff", fontSize: 12 },
            axisPointer: { type: "line", lineStyle: { color: "#c8d8f3" } },
          },
          grid: { left: 48, right: 52, top: 18, bottom: 30 },
          xAxis: {
            type: "category",
            boundaryGap: false,
            data: labels,
            axisLine: { lineStyle: { color: "#e6ebf2" } },
            axisTick: { show: false },
            axisLabel: { color: "#647084", fontSize: 11, interval: 4 },
          },
          yAxis: [
            {
              type: "value",
              name: "用户",
              nameTextStyle: { color: "#94a3b8", fontSize: 11 },
              minInterval: 1,
              axisLine: { show: false },
              axisTick: { show: false },
              axisLabel: { color: "#647084", fontSize: 11 },
              splitLine: { lineStyle: { color: "#f1f5f9" } },
            },
            {
              type: "value",
              name: "记录",
              nameTextStyle: { color: "#94a3b8", fontSize: 11 },
              minInterval: 1,
              axisLine: { show: false },
              axisTick: { show: false },
              axisLabel: { color: "#647084", fontSize: 11 },
              splitLine: { show: false },
            },
          ],
          series: [
            {
              name: "新增用户",
              type: "line",
              smooth: true,
              showSymbol: false,
              symbolSize: 6,
              data: Array.isArray(users) ? users : [],
              lineStyle: { width: 2.5 },
              itemStyle: { color: "#0050cb" },
              areaStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: "rgba(0, 80, 203, 0.22)" },
                  { offset: 1, color: "rgba(0, 80, 203, 0)" },
                ]),
              },
            },
            {
              name: "健康记录",
              type: "line",
              smooth: true,
              showSymbol: false,
              symbolSize: 6,
              yAxisIndex: 1,
              data: Array.isArray(health) ? health : [],
              lineStyle: { width: 2.5 },
              itemStyle: { color: "#10b981" },
              areaStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: "rgba(16, 185, 129, 0.18)" },
                  { offset: 1, color: "rgba(16, 185, 129, 0)" },
                ]),
              },
            },
          ],
        },
        { notMerge: true }
      );
    },

    /* ---------------- 数据加载 ---------------- */
    loadAll() {
      return Promise.all([
        this.loadTrend(),
        this.loadStaticStats(),
        this.loadTotals(),
        this.loadDashboardStats(),
        this.loadMessages(),
      ]).then(() => {
        this.refreshing = false;
        this.lastUpdateTime = new Date().toLocaleTimeString("zh-CN", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        });
      });
    },
    refresh() {
      if (this.refreshing) return;
      this.refreshing = true;
      this.loadAll();
    },

    /**
     * 后端 /user/daysQuery/{n} 的口径（DateUtil.countDatesWithinRange）：
     *   - 从 today-n 到 today 共 n+1 个桶
     *   - key 是 "MM-dd"，**没有年份**
     *   - 计数为 0 的日期直接跳过（稀疏数组）
     * 所以横轴必须由前端补齐，否则「没数据的日期」会被 echarts 当成不存在，
     * 一段本应平坦的空档会被画成直线连过去，视觉上像是天天都有新增。
     */
    async loadTrend() {
      const axis = this.buildAxis(TREND_DAYS);
      const sparseToSeries = (list) => {
        const map = new Map();
        (Array.isArray(list) ? list : []).forEach((item) => {
          map.set(item.name, item.count || 0);
        });
        return axis.map((day) => map.get(day.key) || 0);
      };

      const [userRes, healthRes] = await Promise.all([
        this.safeGet(`user/daysQuery/${TREND_DAYS}`),
        this.safeGet(`user-health/daysQuery/${TREND_DAYS}`),
      ]);

      this.trend = {
        labels: axis.map((day) => day.key),
        users: userRes ? sparseToSeries(userRes) : null,
        health: healthRes ? sparseToSeries(healthRes) : null,
      };
      this.loaded.trend = true;
      this.$nextTick(this.renderTrend);
    },

    /** 生成与后端完全同口径的日期轴：today-days … today，共 days+1 项 */
    buildAxis(days) {
      const now = new Date();
      const axis = [];
      for (let offset = 0; offset <= days; offset++) {
        const d = new Date(
          now.getFullYear(),
          now.getMonth(),
          now.getDate() - (days - offset)
        );
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        axis.push({ key: `${mm}-${dd}` });
      }
      return axis;
    },

    async loadStaticStats() {
      const data = await this.safeGet("views/staticControls");
      if (!data) {
        this.staticStats = null;
        return;
      }
      const map = {};
      data.forEach((item) => {
        map[item.name] = item.count;
      });
      this.staticStats = map;
    },

    async loadTotals() {
      /*
       * 取总数必须读响应体的 total，而不是 data.length ——
       * data 只是当前这一页（3 条），把它当总数会显示「预约总数 3」。
       */
      const [mall, appt] = await Promise.all([
        this.safePostBody("mall/product/query", { current: 0, size: 1 }),
        this.safePostBody("appointment/query", { current: 1, size: 3 }),
      ]);

      this.mallTotal = this.totalOf(mall);
      this.appointmentTotal = this.totalOf(appt);
      this.recentAppointments =
        appt && Array.isArray(appt.data) ? appt.data : null;
      this.loaded.appointments = true;
    },

    /**
     * 待确认预约数：来自管理端专用的 /dashboard/stats（一次 GROUP BY，不拉列表）。
     *
     * 只认数字 —— 接口挂了要保持 null 让模板整行不显示，
     * 把「没读到」显示成「待确认 0 单」是反向误导，管理员会以为没有积压要处理。
     */
    async loadDashboardStats() {
      const data = await this.safeGet("dashboard/stats");
      const pending = data && data.appointment ? data.appointment.pending : null;
      this.pendingAppointments = typeof pending === "number" ? pending : null;
    },

    async loadMessages() {
      const list = await this.safePost("message/query", {
        current: 0,
        size: 3,
      });
      this.messages = Array.isArray(list) ? list : null;
      this.loaded.messages = true;
    },

    /* ---------------- 请求包装 ---------------- */

    /** GET 成功返回 data 数组，失败返回 null（调用方按 null 显示降级态） */
    async safeGet(url) {
      try {
        const res = await request.get(url);
        const body = res && res.data;
        if (body && body.code === 200) return body.data;
        return null;
      } catch (e) {
        return null;
      }
    },

    /**
     * POST 分页接口。注意后端各接口的 current 语义并不统一：
     *   - 带 @Pager 的（appointment/query）current 是 1 起的页码
     *   - 不带的（message/query、mall/product/query）current 就是 SQL 偏移量
     * 这里由调用方按各自口径传参。
     */
    async safePost(url, payload) {
      const body = await this.safePostBody(url, payload);
      return body ? body.data : null;
    },

    /** 与 safePost 相同，但保留整个响应体 —— 分页总数在 body.total 上 */
    async safePostBody(url, payload) {
      try {
        const res = await request.post(url, payload);
        const body = res && res.data;
        if (body && body.code === 200) return body;
        return null;
      } catch (e) {
        return null;
      }
    },

    /** 只认真正的数字；后端 total 缺失时返回 null 而不是 0 */
    totalOf(body) {
      return body && typeof body.total === "number" ? body.total : null;
    },

    /* ---------------- 轮询 ---------------- */
    startPolling() {
      this.stopPolling();
      this.pollTimer = setInterval(() => {
        if (this.pollingActive) this.loadAll();
      }, POLL_INTERVAL);
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },
    togglePolling() {
      this.pollingActive = !this.pollingActive;
    },

    /* ---------------- 展示辅助 ---------------- */
    pick(v) {
      return typeof v === "number" && Number.isFinite(v) ? v : null;
    },
    lastOf(arr) {
      if (!Array.isArray(arr) || arr.length === 0) return null;
      return arr[arr.length - 1];
    },
    formatNumber(n) {
      return Number(n).toLocaleString("zh-CN");
    },
    numOrDash(v) {
      return typeof v === "number" ? v : "—";
    },
    statusText(status) {
      return APPOINTMENT_STATUS[status] || "未知";
    },
    statusTone(status) {
      return STATUS_TONE[status] || "muted";
    },
    shortDate(value) {
      if (!value) return "";
      const s = String(value).replace("T", " ");
      return s.length >= 10 ? s.slice(5, 10) : s;
    },
    formatDateTime(value) {
      if (!value) return "";
      const s = String(value).replace("T", " ");
      return s.length >= 16 ? s.slice(5, 16) : s;
    },
    rowTitle(item) {
      return [
        item.patientName,
        item.departmentName,
        item.doctorName,
        item.scheduleDate,
        item.timeSlotName,
      ]
        .filter(Boolean)
        .join(" · ");
    },
    messageTitle(item) {
      return item.title || "通知";
    },
  },
};
</script>

<style scoped lang="scss">
.dash-page {
  box-sizing: border-box;
  /* 视觉居中：内容区再宽，看板也只长到 1560px 并水平居中 */
  max-width: 1560px;
  margin: 0 auto;
  padding-bottom: 8px;
  color: var(--xh-text-primary);
}

/* ---------------- 页头 ---------------- */
.dash-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 18px;
}

.dash-head__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.dash-head__sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--xh-text-muted);
}

.dash-head__stamp {
  margin-left: 10px;
  color: var(--xh-text-muted);
  opacity: 0.8;
}

.dash-head__side {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dash-live,
.dash-refresh {
  box-sizing: border-box;
  height: 32px;
  padding: 0 14px;
  border-radius: 9999px;
  border: 1px solid var(--xh-border-soft);
  background: #fff;
  color: var(--xh-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.dash-live {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.dash-live:hover,
.dash-refresh:hover {
  background: var(--xh-bg-hover);
  border-color: var(--xh-border);
}

.dash-live__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--xh-success);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
  animation: dash-pulse 2s ease-in-out infinite;
}

.dash-live.is-paused .dash-live__dot {
  background: #94a3b8;
  box-shadow: none;
  animation: none;
}

.dash-refresh:disabled {
  cursor: default;
  opacity: 0.6;
}

@keyframes dash-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

/* ---------------- Bento 栅格 ---------------- */
.bento {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  /*
   * 前两行给主模块（8 列 × 2 行）与右侧两个小块；
   * 后两行给 6 个指标块（每行 3 个 × 4 列）。
   * 指标块矮一些，避免一排「大数字」占掉与主图同等的高度。
   */
  grid-template-rows: 196px 196px 152px 152px;
  gap: 18px;
}

.tile {
  box-sizing: border-box;
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 18px 20px;
  background: var(--xh-bg-card);
  border: 1px solid var(--xh-border-soft);
  border-radius: 16px;
  box-shadow: var(--xh-shadow-sm);
  overflow: hidden;
}

.tile--primary {
  grid-column: span 8;
  grid-row: span 2;
}

.tile--primary::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--xh-gradient-primary);
}

.tile--side {
  grid-column: span 4;
  padding: 16px 18px;
}

.tile--kpi {
  grid-column: span 4;
  justify-content: space-between;
  padding: 16px 18px;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.tile--kpi:hover {
  transform: translateY(-2px);
  box-shadow: var(--xh-shadow-md);
}

/* ---------------- 标题区 ---------------- */
.tile__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
}

.tile__titles {
  min-width: 0;
}

.tile__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--xh-text-primary);
}

.tile__sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--xh-text-muted);
}

/* 待确认单量：紧凑的一行小字，只在有数据时出现，不抢列表的视觉重心 */
.tile__pending {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: #b45309;
  font-variant-numeric: tabular-nums;
}

.tile__pending b {
  font-weight: 700;
}

.tile__link {
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--xh-text-link);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.tile__link:hover {
  text-decoration: underline;
}

.legend {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--xh-text-muted);
}

.legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.legend__item b {
  color: var(--xh-text-primary);
  font-variant-numeric: tabular-nums;
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* ---------------- 图表 ---------------- */
.tile__chart {
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  margin-top: 8px;
}

.tile__overlay {
  position: absolute;
  inset: 56px 20px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--xh-text-muted);
  font-size: 13px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(2px);
}

.tile__overlay p {
  margin: 0;
}

/* 首次加载的骨架屏：主模块画一排柱、侧块画三条线，
   形状与真实内容一致，数据到达时不会出现整块跳变。 */
.tile__overlay--skeleton {
  background: #fff;
  backdrop-filter: none;
  padding: 16px 0 8px;
  align-items: stretch;
  justify-content: flex-end;
}

.sk-chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
  height: 100%;
}

.sk-chart i,
.sk-line {
  display: block;
  background: linear-gradient(90deg, #eef2f7 25%, #f7f9fc 37%, #eef2f7 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.4s ease infinite;
}

.sk-chart i {
  flex: 1 1 0;
  border-radius: 6px 6px 2px 2px;
}

.sk-line {
  width: 100%;
  height: 12px;
  border-radius: 6px;
}

.mini-list--skeleton .mini-row {
  justify-content: center;
}

@keyframes sk-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sk-chart i,
  .sk-line {
    animation: none;
  }
  .dash-live__dot {
    animation: none;
  }
}

.tile__retry {
  height: 28px;
  padding: 0 14px;
  border: 1px solid var(--xh-border);
  border-radius: 9999px;
  background: #fff;
  color: var(--xh-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.tile__retry:hover {
  border-color: var(--xh-primary);
  color: var(--xh-primary);
}

.tile__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  font-size: 12px;
  color: var(--xh-text-muted);
}

/* ---------------- 侧栏迷你列表 ---------------- */
.mini-list {
  flex: 1 1 auto;
  min-height: 0;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}

.mini-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 0;
  padding: 6px 0;
  box-sizing: border-box;
}

.mini-row + .mini-row {
  border-top: 1px solid var(--xh-border-soft);
}

.mini-row__flag {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 9999px;
  font-size: 11px;
  line-height: 1.5;
  white-space: nowrap;
}

.mini-row__flag.warn {
  color: #b45309;
  background: rgba(245, 158, 11, 0.12);
}

.mini-row__flag.info {
  color: #0050cb;
  background: rgba(0, 80, 203, 0.1);
}

.mini-row__flag.ok {
  color: #047857;
  background: rgba(16, 185, 129, 0.12);
}

.mini-row__flag.muted {
  color: #64748b;
  background: rgba(100, 116, 139, 0.1);
}

.mini-row__body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mini-row__line1,
.mini-row__line2 {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mini-row__line1 {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--xh-text-primary);
}

.mini-row__line2 {
  font-size: 11.5px;
  color: var(--xh-text-muted);
}

.mini-row__time {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--xh-text-muted);
  font-variant-numeric: tabular-nums;
}

.mini-row__unread {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: transparent;
}

.mini-row--msg.is-unread .mini-row__unread {
  background: var(--xh-primary);
}

.mini-row--msg.is-unread .mini-row__line1 {
  color: var(--xh-primary);
}

/* ---------------- 指标块 ---------------- */
.kpi__top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kpi__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex: 0 0 auto;
}

.kpi__label {
  font-size: 13px;
  color: var(--xh-text-secondary);
}

.kpi__value {
  font-size: 34px;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums;
  color: var(--xh-text-primary);
}

.kpi__value.is-missing {
  color: var(--xh-text-muted);
  font-weight: 500;
}

.kpi__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 11.5px;
  color: var(--xh-text-muted);
}

.kpi__delta {
  color: #047857;
  background: rgba(16, 185, 129, 0.12);
  border-radius: 9999px;
  padding: 2px 8px;
  font-variant-numeric: tabular-nums;
}

.kpi__delta.is-zero {
  color: var(--xh-text-muted);
  background: rgba(100, 116, 139, 0.1);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1200px) {
  .bento {
    grid-template-columns: repeat(6, minmax(0, 1fr));
    grid-template-rows: 196px 196px 150px 150px 150px;
  }

  .tile--primary {
    grid-column: span 6;
  }

  .tile--side {
    grid-column: span 3;
  }

  .tile--kpi {
    grid-column: span 2;
  }
}

@media (max-width: 760px) {
  .bento {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: none;
    grid-auto-rows: auto;
  }

  .tile--primary,
  .tile--side,
  .tile--kpi {
    grid-column: auto;
    grid-row: auto;
  }

  .tile--primary {
    min-height: 340px;
  }

  .tile--side {
    min-height: 170px;
  }

  .tile--kpi {
    min-height: 132px;
  }
}
</style>
