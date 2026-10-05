import { getToken } from "./storage";
import { URL_API } from "./request";

/**
 * WebSocket 客户端
 *
 * 负责与后端 /ws/notification/{token} 保持长连接，用于接收站内消息通知。
 *
 * 关键设计（修复"连接反复断开重连"问题）：
 * 1. 防重入必须覆盖 CONNECTING 状态。原实现只判断 OPEN，若在连接建立过程中再次调用
 *    connectWs()，会重复创建连接；新连接会顶掉旧连接（后端 SESSIONS.put + close），
 *    旧连接的 onclose 又触发重连，形成"建→顶→关→再建"的连接风暴。
 * 2. 后台标签页心跳节流。浏览器会把后台标签页的 setInterval 节流到最低 1 分钟，
 *    页面隐藏时甚至完全暂停，导致心跳停发、服务端空闲超时断开。
 *    对策：监听 visibilitychange，切回前台时立即补发心跳并检查连接状态。
 * 3. 主动健康检查。定时器可能因节流失效，除了被动等 onclose，
 *    还要在心跳时顺带确认连接是否仍处于 OPEN。
 */

let ws = null;
let reconnectTimer = null;
let heartbeatTimer = null;
let reconnectDelay = 5000;
let visibilityBound = false;
const listeners = new Map();

/** 心跳发送间隔。需小于服务端（Tomcat）默认 60s 的空闲超时。 */
const HEARTBEAT_INTERVAL = 25000;

/**
 * 建立 WebSocket 连接（幂等）
 */
export function connectWs() {
  const token = getToken();
  if (!token) return;

  // 防重入：CONNECTING 与 OPEN 都要拦截，否则会重复建连接
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return;
  }

  // ENG-07 整改：从 URL_API 推导 WS 地址，支持 VUE_APP_WS_BASE 覆盖。
  // 原硬编码 ws://localhost:21091 在生产环境（https 域名 + 反代）必然失败。
  const wsBase = process.env.VUE_APP_WS_BASE || deriveWsBase();
  const wsUrl = `${wsBase}/ws/notification/${token}`;
  ws = new WebSocket(wsUrl);

  ws.onopen = () => {
    console.log("[WebSocket] 连接已建立");
    reconnectDelay = 5000;
    startHeartbeat();
    bindVisibility();
  };

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      if (data.type === "heartbeat") return;
      notifyListeners(data);
    } catch (e) {
      console.error("[WebSocket] 消息解析失败:", e);
    }
  };

  ws.onclose = () => {
    console.log("[WebSocket] 连接已关闭");
    stopHeartbeat();
    scheduleReconnect();
  };

  ws.onerror = (error) => {
    console.error("[WebSocket] 连接错误:", error);
  };
}

/**
 * 关闭连接并取消一切重连/心跳
 */
export function closeWs() {
  if (ws) {
    ws.close();
    ws = null;
  }
  stopHeartbeat();
  unbindVisibility();
  clearTimeout(reconnectTimer);
  reconnectTimer = null;
  reconnectDelay = 5000;
}

/**
 * 发送消息
 */
export function sendWsMessage(data) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  }
}

/**
 * 注册消息监听
 */
export function addWsListener(type, callback) {
  if (!listeners.has(type)) {
    listeners.set(type, []);
  }
  listeners.get(type).push(callback);
}

/**
 * 取消消息监听
 */
export function removeWsListener(type, callback) {
  if (listeners.has(type)) {
    const cbs = listeners.get(type).filter((cb) => cb !== callback);
    listeners.set(type, cbs);
  }
}

function notifyListeners(data) {
  const type = data.type || "message";
  if (listeners.has(type)) {
    listeners.get(type).forEach((cb) => cb(data));
  }
}

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = setInterval(() => {
    // 定时器可能因浏览器节流而长时间未执行，顺带确认连接是否还活着；
    // 若已断开则立刻走重连，而不是干等 onclose（后台标签页下 onclose 可能延迟很久）。
    if (!ws || ws.readyState === WebSocket.CLOSED || ws.readyState === WebSocket.CLOSING) {
      stopHeartbeat();
      scheduleReconnect();
      return;
    }
    sendWsMessage({ type: "heartbeat" });
  }, HEARTBEAT_INTERVAL);
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return; // 已有重连计划在排队，避免重复排期
  reconnectDelay = Math.min(reconnectDelay * 2, 60000);
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    console.log(`[WebSocket] ${reconnectDelay / 1000}s 后尝试重连...`);
    connectWs();
  }, reconnectDelay);
}

/**
 * 页面可见性监听：解决后台标签页定时器被节流导致的心跳中断
 */
function bindVisibility() {
  if (visibilityBound || typeof document === "undefined") return;
  document.addEventListener("visibilitychange", onVisibilityChange);
  visibilityBound = true;
}

function unbindVisibility() {
  if (visibilityBound && typeof document !== "undefined") {
    document.removeEventListener("visibilitychange", onVisibilityChange);
  }
  visibilityBound = false;
}

function onVisibilityChange() {
  if (document.visibilityState !== "visible") return;

  // 切回前台：补发心跳（此时 onclose 可能还没触发），并确认连接状态
  if (!ws || ws.readyState === WebSocket.CLOSED || ws.readyState === WebSocket.CLOSING) {
    stopHeartbeat();
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
    reconnectDelay = 5000;
    console.log("[WebSocket] 页面恢复可见，重新建立连接");
    connectWs();
    return;
  }
  if (ws.readyState === WebSocket.OPEN) {
    sendWsMessage({ type: "heartbeat" });
  }
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
    const url = new URL(URL_API);
    const isHttps = url.protocol === "https:";
    const scheme = isHttps ? "wss" : "ws";
    return `${scheme}://${url.host}${url.pathname}`;
  } catch (e) {
    return "ws://localhost:21090";
  }
}
