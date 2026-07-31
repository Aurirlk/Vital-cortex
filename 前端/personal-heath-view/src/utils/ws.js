import { getToken } from './storage'

/**
 * WebSocket 
 */

let ws = null
let reconnectTimer = null
let heartbeatTimer = null
const listeners = new Map()

/**
 *  WebSocket 
 */
export function connectWs() {
  if (ws && ws.readyState === WebSocket.OPEN) return

  const token = getToken()
  if (!token) return

  const wsUrl = `ws://localhost:21091/ws/notification/${token}`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('[WebSocket] ')
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
  reconnectTimer = setTimeout(() => {
    console.log('[WebSocket] ...')
    connectWs()
  }, 5000)
}
