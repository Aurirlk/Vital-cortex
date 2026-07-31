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
  // ENG-07 整改：不再重复拼 localhost，直接复用 request.js 的 URL_API（env 可配置）
  const uploadUrl = request.defaults.baseURL + '/file/upload'

  return { api, uploadUrl }
}
