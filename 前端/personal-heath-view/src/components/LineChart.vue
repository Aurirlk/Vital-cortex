<template>
  <div class="line-main">
    <div class="chart-header">
      <span class="tag">{{ tag }}</span>
      <div class="time-controls">
        <el-select
          size="small"
          style="width: 100px"
          v-model="selectedValue"
          placeholder="时间范围"
          @change="handleTimeChange"
        >
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
          </el-option>
        </el-select>
        <el-date-picker
          v-if="selectedValue === 'custom'"
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
          style="margin-left: 8px; width: 240px"
          @change="handleDateRangeChange"
        />
      </div>
    </div>
    <div ref="chart" :style="{ width: '100%', height: height }"></div>
  </div>
</template>
<script>
// 折线图组件
import * as echarts from "echarts";
export default {
  name: "DialogLine",
  props: {
    tag: {
      type: String,
      default: "折线图",
    },
    values: {
      type: Array,
      required: true,
    },
    date: {
      type: Array,
      required: true,
    },
    height: {
      type: String,
      default: "220px",
    },
  },
  watch: {
    values: {
      handler(newVal) {
        console.log('LineChart values变化:', newVal);
        this.$nextTick(() => {
          this.initChart();
        });
      },
      deep: true,
    },
    date: {
      handler(newVal) {
        console.log('LineChart date变化:', newVal);
        this.$nextTick(() => {
          this.initChart();
        });
      },
      deep: true,
    },
  },
  data() {
    return {
      chart: null,
      options: [
        { value: 7, label: "最近7天" },
        { value: 30, label: "最近30天" },
        { value: 90, label: "最近3个月" },
        { value: 180, label: "最近半年" },
        { value: 365, label: "最近一年" },
        { value: -1, label: "全部数据" },
        { value: "custom", label: "自定义范围" },
      ],
      selectedValue: 365,
      dateRange: null,
    };
  },
  mounted() {
    this.$nextTick(() => {
      this.initChart();
    });
    window.addEventListener("resize", this.handleResize);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.handleResize);
    if (this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
  },
  methods: {
    handleTimeChange(val) {
      if (val !== "custom") {
        this.$emit("on-selected", val);
      }
    },
    handleDateRangeChange(val) {
      if (val && val.length === 2) {
        this.$emit("on-date-range", val[0], val[1]);
      }
    },
    handleResize() {
      if (this.chart) {
        this.chart.resize();
      }
    },
    // 图表初始化
    initChart() {
      if (!this.$refs.chart) return;
      
      if (this.chart) {
        this.chart.dispose();
      }
      this.chart = echarts.init(this.$refs.chart);
      
      // 即使无数据也显示空图表
      const xData = this.date && this.date.length > 0 ? this.date : ['暂无数据'];
      const yData = this.values && this.values.length > 0 ? this.values : [0];
      
      let option = {
        grid: {
          left: 60,
          right: 20,
          top: 40,
          bottom: 30,
          borderWidth: 0,
        },
        tooltip: {
          trigger: "axis",
          formatter: function(params) {
            if (params[0].name === '暂无数据') return '暂无数据';
            return params[0].name + ': ' + params[0].value;
          },
        },
        xAxis: {
          type: 'category',
          data: xData,
          axisLine: { show: true, lineStyle: { color: '#E5E6EB' } },
          axisTick: { show: false },
          axisLabel: {
            color: "#666",
            fontSize: 12,
            rotate: xData.length > 10 ? 30 : 0,
          },
        },
        yAxis: {
          type: 'value',
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: "#666",
            fontSize: 12,
          },
          splitLine: {
            lineStyle: { color: '#F0F0F0' }
          }
        },
        series: [
          {
            name: "",
            type: "line",
            smooth: true,
            data: yData,
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(102, 126, 234, 0.3)' },
                { offset: 1, color: 'rgba(102, 126, 234, 0.05)' }
              ]),
            },
            lineStyle: {
              color: "#667eea",
              width: 2,
            },
            itemStyle: {
              color: "#667eea",
              borderColor: "#fff",
              borderWidth: 2,
            },
            symbol: 'circle',
            symbolSize: 6,
            label: {
              show: xData.length <= 20 && xData[0] !== '暂无数据',
              position: "top",
              color: "#666",
              fontSize: 11,
            },
          },
        ],
      };
      
      // 无数据时显示提示
      if (!this.values || this.values.length === 0) {
        option.graphic = {
          type: 'text',
          left: 'center',
          top: 'middle',
          style: {
            text: '暂无数据，请先记录健康指标',
            fontSize: 14,
            fill: '#999',
          }
        };
      }
      
      this.chart.setOption(option);
    },
  },
};
</script>
<style scoped lang="scss">
.line-main {
  margin-bottom: 16px;
  border-radius: 12px;
  background: #fff;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);

  .chart-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  .tag {
    font-size: 16px;
    color: #333;
    font-weight: 600;
  }

  .time-controls {
    display: flex;
    align-items: center;
  }
}
</style>
