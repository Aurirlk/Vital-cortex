/**
 * useEcharts — ECharts 
 *
 *  <script setup> 
 *   import { useEcharts } from '@/composables/useEcharts'
 *   const { echarts } = useEcharts()
 *   const chart = echarts.init(document.getElementById('myChart'))
 *
 *  ECharts 
 *  composable
 */
import * as echarts from 'echarts'

export function useEcharts() {
  return { echarts }
}
