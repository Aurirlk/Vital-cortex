<template>
  <div class="line-main">
    <div>
      <span class="tag">{{ tag }}</span>
    </div>
    <div ref="chart" :style="{ width: '100%', height: height }"></div>
  </div>
</template>
<script>
// 柱状图组件
import * as echarts from "echarts";
export default {
  name: "BarChart",
  props: {
    height: {
      type: String,
      default: "300px",
    },
    tag: {
      type: String,
      default: "柱状图",
    },
    values: {
      type: Array,
      required: true,
    },
    date: {
      type: Array,
      required: true,
    },
  },
  data() {
    return {
      chart: null,
    };
  },
  watch: {
    values: {
      handler() {
        this.$nextTick(() => {
          this.init();
        });
      },
      immediate: true,
    },
    date: {
      handler() {
        this.$nextTick(() => {
          this.init();
        });
      },
      immediate: true,
    },
  },
  mounted() {
    this.$nextTick(() => {
      this.init();
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
    handleResize() {
      if (this.chart) {
        this.chart.resize();
      }
    },
    // 柱状图加载
    init() {
      if (!this.$refs.chart) return;
      if (!this.values.length || !this.date.length) return;
      if (this.chart) {
        this.chart.dispose();
      }
      this.chart = echarts.init(this.$refs.chart);
      let option = {
        grid: {
          left: 30,
          right: 5,
          top: 10,
          borderWidth: 5,
        },
        title: { text: "" },
        tooltip: {},
        xAxis: {
          data: this.date,
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: "rgb(102, 102, 102)",
            interval: 1,
            rotate: 0,
          },
        },
        yAxis: {
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: "rgb(102, 102, 102)",
            fontSize: "12",
          },
        },
        series: [
          {
            name: "",
            type: "bar",
            data: this.values,
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: {
              color: "rgb(102, 102, 102)",
            },
            itemStyle: {
              normal: {
                color: function (params) {
                  const colorList = [
                    "#e2e1e4",
                    "#bc84a8",
                    "#5e616d",
                    "#57c3c2",
                    "#87CEEB",
                    "#ADD8E6",
                  ];
                  return colorList[params.dataIndex % colorList.length];
                },
              },
            },
          },
        ],
      };
      this.chart.setOption(option);
    },
  },
};
</script>
<style scoped lang="scss">
.line-main {
  margin-bottom: 5px;
  border-radius: 3px;

  .tag {
    font-size: 16px;
    padding: 15px 6px;
    display: inline-block;
    color: #333;
    font-weight: bold;
  }
}
</style>
