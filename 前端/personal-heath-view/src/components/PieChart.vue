<template>
  <div class="line-main" :style="{ backgroundColor: bag }">
    <div ref="chart" :style="{ width: width, height: height }"></div>
  </div>
</template>
<script>
import * as echarts from "echarts";
export default {
  name: "PieChart",
  props: {
    types: {
      type: Array,
      default: () => [],
    },
    values: {
      type: Array,
      default: () => [],
    },
    width: {
      type: String,
      default: "100%",
    },
    height: {
      type: String,
      default: "243px",
    },
    bag: {
      type: String,
      default: "#fff",
    },
    fontColor: {
      type: String,
      default: "#333",
    },
  },
  data() {
    return {
      chart: null,
    };
  },
  watch: {
    types: {
      handler() {
        this.$nextTick(() => {
          this.initChart();
        });
      },
      immediate: true,
    },
    values: {
      handler() {
        this.$nextTick(() => {
          this.initChart();
        });
      },
      immediate: true,
    },
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
    handleResize() {
      if (this.chart) {
        this.chart.resize();
      }
    },
    initChart() {
      if (!this.$refs.chart) return;
      if (!this.types.length || !this.values.length) return;
      if (this.chart) {
        this.chart.dispose();
      }
      this.chart = echarts.init(this.$refs.chart);
      let option = {
        title: {
          text: "",
          subtext: "",
          left: "center",
        },
        tooltip: {
          trigger: "item",
        },
        legend: {
          orient: "vertical",
          left: "left",
          show: false,
        },
        series: [
          {
            name: "",
            type: "pie",
            radius: "70%",
            avoidLabelOverlap: false,
            emphasis: {
              label: {
                show: false,
                fontSize: "24",
                fontWeight: "600",
              },
            },
            label: {
              show: true,
              position: "outer",
              formatter: "{d}%",
            },
            labelLine: {
              show: true,
            },
            data: this.values.map((value, index) => ({
              name: this.types[index],
              value: value,
            })),
            itemStyle: {
              color: function (params) {
                const colorList = [
                  "#FF6347",
                  "#FFD700",
                  "#0885f9",
                  "#FF69B4",
                  "#9370DB",
                  "#00FFFF",
                  "#b84031",
                ];
                return colorList[params.dataIndex % colorList.length];
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
  padding-top: 40px;
  margin-bottom: 5px;
  border-radius: 3px;
  background-color: #000000;

  .tag {
    font-size: 14px;
    text-align: center;
    padding: 15px 6px;
    display: block;
  }
}
</style>
