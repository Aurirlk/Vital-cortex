/**
 * 数据智能体 SSE 流式客户端（2026-10-07）
 *
 * <p><b>为什么不用 EventSource</b>：原生 {@code EventSource} 只能发 GET，
 * 而数据问答需要 POST 请求体（问题、患者 id、是否出报告）。因此改用
 * {@code fetch} + {@code ReadableStream} 手工解析 SSE 帧。</p>
 *
 * <p>帧格式与后端 {@code DataAgentSse} 一致：</p>
 * <pre>
 * event: step
 * data: {json}
 * （空行）
 * </pre>
 */

/** 解析单个 SSE 帧（event + 多行 data）。 */
function parseFrame(frame) {
  const lines = frame.split("\n");
  let event = "message";
  const dataLines = [];
  for (const line of lines) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).replace(/^ /, ""));
    }
  }
  if (!dataLines.length) return null;
  const raw = dataLines.join("\n");
  let data = raw;
  try {
    data = JSON.parse(raw);
  } catch (e) {
    /* 非 JSON 时按纯文本传递 */
  }
  return { event, data };
}

/**
 * 发起一次流式数据问答。
 *
 * @param {object}   opts
 * @param {string}   opts.url      完整接口地址
 * @param {string}   opts.token    身份 token（医生端为 doctor-token）
 * @param {object}   opts.body     请求体
 * @param {Function} [opts.onStep]   每步事件回调（RUNNING / SUCCESS / FAILED / SKIPPED）
 * @param {Function} [opts.onResult] 最终结果回调
 * @param {Function} [opts.onError]  错误回调
 * @param {AbortSignal} [opts.signal] 中断信号
 * @returns {Promise<void>}
 */
export async function streamAgent({
  url,
  token,
  body,
  onStep,
  onResult,
  onError,
  signal,
}) {
  let response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        token: token || "",
      },
      body: JSON.stringify(body || {}),
      signal,
    });
  } catch (e) {
    if (onError) onError({ message: "网络异常，无法连接数据智能体" });
    return;
  }

  if (!response.ok) {
    // 把 HTTP 状态交给调用方处理（401 需清 token 跳登录，403 只提示）
    const err = new Error("HTTP " + response.status);
    err.status = response.status;
    throw err;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let sep;
    while ((sep = buffer.indexOf("\n\n")) >= 0) {
      const frame = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      const parsed = parseFrame(frame);
      if (!parsed) continue;
      if (parsed.event === "step" && onStep) onStep(parsed.data);
      else if (parsed.event === "result" && onResult) onResult(parsed.data);
      else if (parsed.event === "error" && onError) onError(parsed.data);
    }
  }
}
