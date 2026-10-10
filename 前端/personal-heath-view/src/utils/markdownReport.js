/**
 * 分析报告渲染与导出（2026-10-07）
 *
 * <p>对齐 DataAgent 前端 <code>useEchartsRenderer</code> + <code>markdown-plugin-echarts</code>
 * 的能力，并按本项目情况做了两处强化：</p>
 * <ol>
 *   <li><b>XSS 消毒</b>：DataAgent 直接把 markdown 渲染结果插入 DOM。本项目的报告来自 LLM
 *       且会落库 <code>ai_chat_record</code>，再次渲染时构成<b>存储型 XSS</b> 面，因此统一走
 *       DOMPurify 消毒；</li>
 *   <li><b>HTML 导出离线自包含</b>：DataAgent 导出的 HTML 依赖 ECharts CDN，离线打不开。
 *       这里把图表实例导出为 PNG data URL 内联进 HTML，零外链。</li>
 * </ol>
 *
 * <p>支持在报告里用围栏代码块嵌图：</p>
 * <pre>
 * ```echarts
 * {"xAxis":{"type":"category","data":["1月","2月"]},"yAxis":{"type":"value"},
 *  "series":[{"type":"bar","data":[10,20]}]}
 * ```
 * </pre>
 */

import { marked } from "marked";
import DOMPurify from "dompurify";
import * as echarts from "echarts";

/** ECharts 默认色板（与 DataAgent 保持一致，保证两端口径统一）。 */
const DEFAULT_COLORS = [
  "#5584FF", "#36CBCB", "#4ECB74", "#FAD337", "#F2637B", "#975FEE",
  "#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de", "#3ba272",
  "#fc8452", "#9a60b4", "#ea7ccc", "#0082fc", "#fdd845", "#22ed7c",
];

const ECHARTS_FENCE = "echarts";
const CHART_SELECTOR = ".md-echarts";

let configured = false;

/** HTML 属性转义（用于把 JSON 塞进 data-* 属性）。 */
export function escapeAttr(value) {
  return String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function ensureMarkedConfigured() {
  if (configured) return;
  marked.use({
    gfm: true,
    breaks: false,
    renderer: {
      // marked v5+ 传 token 对象；旧版传 (code, infostring)。两种都兼容。
      code(tokenOrCode, infostring) {
        let text = "";
        let lang = "";
        if (tokenOrCode && typeof tokenOrCode === "object") {
          text = tokenOrCode.text || "";
          lang = tokenOrCode.lang || "";
        } else {
          text = tokenOrCode || "";
          lang = infostring || "";
        }
        if (String(lang).trim().toLowerCase() !== ECHARTS_FENCE) {
          return false; // 交回 marked 默认渲染
        }
        const raw = String(text).trim();
        if (!raw) {
          return '<div class="md-echarts md-echarts--empty">图表数据为空</div>';
        }
        try {
          JSON.parse(raw);
        } catch (e) {
          // 不是合法 JSON：降级为普通代码块，绝不让坏数据打爆渲染
          return (
            '<div class="md-echarts md-echarts--invalid">图表配置无法解析</div>' +
            "<pre><code>" + escapeAttr(raw) + "</code></pre>"
          );
        }
        return '<div class="md-echarts" data-echarts-config="' + escapeAttr(raw) + '"></div>';
      },
    },
  });
  configured = true;
}

/**
 * Markdown → 已消毒的 HTML。
 * 报告内容为 LLM 产出且会落库后再次渲染，必须消毒（防存储型 XSS）。
 */
export function renderMarkdown(md) {
  if (!md) return "";
  ensureMarkedConfigured();
  let html;
  try {
    html = marked.parse(String(md));
  } catch (e) {
    html = "<p>" + escapeAttr(md) + "</p>";
  }
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ["data-echarts-config"],
    FORBID_TAGS: ["script", "iframe", "object", "embed", "form", "input", "link", "style"],
  });
}

/**
 * 渲染容器内所有未初始化的图表（幂等：渲染后移除 data-echarts-config）。
 * @param {HTMLElement|null} container
 */
export function renderChartsIn(container) {
  if (!container || !container.querySelectorAll) return 0;
  let count = 0;
  container.querySelectorAll(CHART_SELECTOR).forEach((el) => {
    const rawConfig = el.getAttribute("data-echarts-config");
    if (!rawConfig) return; // 已渲染过
    let options;
    try {
      options = JSON.parse(rawConfig);
    } catch (e) {
      el.removeAttribute("data-echarts-config");
      el.textContent = "图表配置无法解析";
      return;
    }
    el.removeAttribute("data-echarts-config");
    el.textContent = "";
    try {
      const existing = echarts.getInstanceByDom(el);
      if (existing) {
        existing.setOption(options, true);
      } else {
        const chart = echarts.init(el);
        chart.setOption(Object.assign({}, options, { color: options.color || DEFAULT_COLORS }));
      }
      count += 1;
    } catch (e) {
      el.textContent = "图表渲染失败";
    }
  });
  return count;
}

/** 销毁容器内的图表实例（组件卸载 / 弹窗关闭时调用，防内存泄漏）。 */
export function disposeChartsIn(container) {
  if (!container || !container.querySelectorAll) return;
  container.querySelectorAll(CHART_SELECTOR).forEach((el) => {
    const chart = echarts.getInstanceByDom(el);
    if (chart) chart.dispose();
  });
}

/** 容器尺寸变化时重算图表（窗口 resize / 侧栏折叠）。 */
export function resizeChartsIn(container) {
  if (!container || !container.querySelectorAll) return;
  container.querySelectorAll(CHART_SELECTOR).forEach((el) => {
    const chart = echarts.getInstanceByDom(el);
    if (chart) {
      try {
        chart.resize();
      } catch (e) {
        /* ignore */
      }
    }
  });
}

/** 触发浏览器下载。 */
export function downloadText(filename, content, mime) {
  const blob = new Blob([content], { type: mime || "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** 生成带时间戳的文件名。 */
export function reportFileName(prefix, ext) {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const stamp =
    d.getFullYear() +
    pad(d.getMonth() + 1) +
    pad(d.getDate()) +
    "_" +
    pad(d.getHours()) +
    pad(d.getMinutes());
  return (prefix || "健康数据分析报告") + "_" + stamp + "." + ext;
}

/**
 * 由已渲染的报告容器生成<b>离线自包含</b>的 HTML 文档。
 *
 * <p>图表容器替换为 PNG（data URL）内联，因此不依赖任何 CDN / 网络。</p>
 *
 * @param {HTMLElement|null} containerEl 已渲染报告正文的容器
 * @param {string} title 报告标题
 * @returns {string} 完整 HTML 文本
 */
export function buildStandaloneHtml(containerEl, title) {
  const reportTitle = title || "健康数据分析报告";
  let body = "";
  if (containerEl) {
    const clone = containerEl.cloneNode(true);
    const liveCharts = containerEl.querySelectorAll(CHART_SELECTOR);
    clone.querySelectorAll(CHART_SELECTOR).forEach((el, i) => {
      const live = liveCharts[i];
      let dataUrl = "";
      try {
        const inst = live ? echarts.getInstanceByDom(live) : null;
        if (inst) {
          dataUrl = inst.getDataURL({ type: "png", pixelRatio: 2, backgroundColor: "#ffffff" });
        }
      } catch (e) {
        dataUrl = "";
      }
      const img = document.createElement("img");
      img.setAttribute("style", "max-width:100%;height:auto;");
      if (dataUrl) {
        img.setAttribute("src", dataUrl);
      } else {
        img.setAttribute("alt", "（图表未能导出）");
      }
      if (el.parentNode) el.parentNode.replaceChild(img, el);
    });
    body = clone.innerHTML;
  }
  const stamp = new Date().toLocaleString("zh-CN", { hour12: false });
  return [
    "<!DOCTYPE html>",
    '<html lang="zh-CN"><head><meta charset="utf-8" />',
    "<title>" + escapeAttr(reportTitle) + "</title>",
    "<style>",
    "body{margin:0;padding:32px 40px;background:#f5f7fb;font-family:'PingFang SC','Microsoft YaHei',Arial,sans-serif;color:#1b2433;}",
    ".report{max-width:920px;margin:0 auto;background:#fff;border-radius:12px;padding:32px 36px;box-shadow:0 2px 12px rgba(20,40,80,.06);}",
    "h1{font-size:22px;margin:0 0 6px;}h2{font-size:17px;margin:22px 0 10px;}h3{font-size:15px;margin:16px 0 8px;}",
    "table{width:100%;border-collapse:collapse;margin:12px 0;}",
    "th,td{border:1px solid #e6ecf5;padding:7px 10px;font-size:13px;text-align:left;}",
    "th{background:#f5f8fd;}",
    "pre{background:#f7f9fc;border:1px solid #edf1f7;border-radius:8px;padding:12px;overflow:auto;font-size:12.5px;}",
    "code{background:#f2f6fc;padding:1px 5px;border-radius:4px;}",
    ".meta{color:#8b95a5;font-size:12px;margin-bottom:18px;}",
    ".foot{margin-top:26px;padding-top:14px;border-top:1px solid #eef3fa;color:#98a2b3;font-size:12px;line-height:1.7;}",
    "</style></head><body><div class='report'>",
    "<h1>" + escapeAttr(reportTitle) + "</h1>",
    '<div class="meta">导出时间：' + escapeAttr(stamp) + "　·　智康云健康管理系统 · 数据智能体</div>",
    body,
    '<div class="foot">本报告由 AI 数据智能体基于系统内数据自动生成，仅供参考，不构成医疗建议。<br/>'
      + "如需诊疗意见，请咨询执业医师。</div>",
    "</div></body></html>",
  ].join("\n");
}
