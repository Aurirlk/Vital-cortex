/**
 * useApi — Composition API  Axios 
 *
 *  <script setup> 
 *   import { useApi } from '@/composables/useApi'
 *   const { api, uploadUrl } = useApi()
 *   const res = await api.post('/user/login', { ... })
 *
 * Options API 
 *   this.$axios  main.js  globalProperties 
 *    import 
 */
import request from '@/utils/request'

export function useApi() {
  const api = request
  const uploadUrl =
    (process.env.VUE_APP_API_BASE ||
      'http://localhost:21090/api/personal-health/v1.0') + '/file/upload'

  return { api, uploadUrl }
}
