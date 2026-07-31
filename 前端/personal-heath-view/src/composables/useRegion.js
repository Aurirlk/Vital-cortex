/**
 * useRegion — //
 *
 *  <script setup> 
 *   import { useRegion } from '@/composables/useRegion'
 *   const { provinceAndCityData, regionData } = useRegion()
 *
 * element-china-area-data
 */
import { provinceAndCityData, regionData } from 'element-china-area-data'

export function useRegion() {
  return { provinceAndCityData, regionData }
}
