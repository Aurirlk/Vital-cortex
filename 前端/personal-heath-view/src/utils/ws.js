import { getToken } from './storage'
import { URL_API } from './request'

/**
 * WebSocket 
 */

let ws = null
let reconnectTimer = null
let heartbeatTimer = null
let reconnectDelay = 5000
const listeners = new Map()

/**
 *  WebSocket 
 */
export function connectWs() {
  if (ws && ws.readyState === WebSocket.OPEN) return

  const token = getToken()
  if (!token) return

  // ENG-07 整改：从 URL_API 推导 WS 地址，支持 VUE_APP_WS_BASE 覆盖。
  // 原硬编码 ws://localhost:21091 在生产环境（https 域名 + 反代）必然失败。
  const wsBase = process.env.VUE_APP_WS_BASE || deriveWsBase()
  const wsUrl = `${wsBase}/ws/notification/${token}`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('[WebSocket] 连接已建立')
    reconnectDelay = 5000
    startHeartbeat()
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'heartbeat') return
      notifyListeners(data)
    } catch (e) {
      console.error('[WebSocket] :', e)
    }
  }

  ws.onclose = () => {
    console.log('[WebSocket] ')
    stopHeartbeat()
    scheduleReconnect()
  }

  ws.onerror = (error) => {
    console.error('[WebSocket] :', error)
  }
}

/**
 *  WebSocket 
 */
export function closeWs() {
  if (ws) {
    ws.close()
    ws = null
  }
  stopHeartbeat()
  clearTimeout(reconnectTimer)
}

/**
 * 
 */
export function sendWsMessage(data) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data))
  }
}

/**
 * 
 */
export function addWsListener(type, callback) {
  if (!listeners.has(type)) {
    listeners.set(type, [])
  }
  listeners.get(type).push(callback)
}

/**
 * 
 */
export function removeWsListener(type, callback) {
  if (listeners.has(type)) {
    const cbs = listeners.get(type).filter(cb => cb !== callback)
    listeners.set(type, cbs)
  }
}

function notifyListeners(data) {
  const type = data.type || 'message'
  if (listeners.has(type)) {
    listeners.get(type).forEach(cb => cb(data))
  }
}

function startHeartbeat() {
  heartbeatTimer = setInterval(() => {
    sendWsMessage({ type: 'heartbeat' })
  }, 30000)
}

function stopHeartbeat() {
  clearInterval(heartbeatTimer)
}

function scheduleReconnect() {
  clearTimeout(reconnectTimer)
  reconnectDelay = Math.min(reconnectDelay * 2, 60000)
  reconnectTimer = setTimeout(() => {
    console.log('[WebSocket] 尝试重连...')
    connectWs()
  }, reconnectDelay)
}

/**
 * 从 URL_API（http(s)://host:port/api/personal-health/v1.0）推导 WebSocket 基础地址。
 * 端口规则：http→ws、https→wss，端口默认 80/443 时省略。
 * 关键点：必须保留 URL_API 的 pathname（即 context-path /api/personal-health/v1.0），
 * 否则拼接出的 WS 地址会缺少 context-path，被 nginx/后端返回 404（D-004 整改）。
 * 仅当后端 WebSocket 端点部署在非常规端口时，才用 VUE_APP_WS_BASE 显式覆盖。
 */
function deriveWsBase() {
  try {
    const url = new URL(URL_API)
    const isHttps = url.protocol === 'https:'
    const scheme = isHttps ? 'wss' : 'ws'
    return `${scheme}://${url.host}${url.pathname}`
  } catch (e) {
    return 'ws://localhost:21090'
  }
}
