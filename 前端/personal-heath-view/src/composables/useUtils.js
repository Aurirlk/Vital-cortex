/**
 * useUtils — md5 
 *
 *  <script setup> 
 *   import { useUtils } from '@/composables/useUtils'
 *   const { md5 } = useUtils()
 *   const hash = md5(value)
 *
 * Options API 
 *   this.$md5  main.js  globalProperties 
 *    import 
 */
import md5 from 'js-md5'

export function useUtils() {
  return { md5 }
}
