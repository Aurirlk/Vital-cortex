/**
 * 安全 HTML 渲染工具（MM-18 整改）。
 *
 * AI 聊天/助手输出经 marked 渲染成 HTML 后直接 v-html 注入，若知识库或
 * 联网搜索结果被注入恶意内容，AI 回显即可形成存储型 XSS。
 * 统一走 DOMPurify 白名单净化后再注入。
 */
import DOMPurify from "dompurify";

/**
 * 净化 AI 输出的 HTML。
 * @param {string} html 原始 HTML（可含 <script>、onerror 等危险内容）
 * @returns {string} 净化后的安全 HTML
 */
export function sanitizeHtml(html) {
  if (!html) return "";
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ["style", "iframe", "form", "input", "button"],
    FORBID_ATTR: ["style"],
  });
}
