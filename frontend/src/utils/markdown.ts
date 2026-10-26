import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 将 Markdown 转换为 HTML，并使用 DOMPurify 过滤掉不安全的 HTML 标签和属性
 */

const PURIFY_CONFIG = {
    // 允许的标签（DOMPurify 默认已较严格，这里显式列出便于审阅）
    ALLOWED_TAGS: [
        'a', 'b', 'blockquote', 'br', 'code', 'del', 'div', 'em',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'i', 'img',
        'li', 'ol', 'p', 'pre', 's', 'span', 'strong', 'sub',
        'sup', 'table', 'tbody', 'td', 'th', 'thead', 'tr', 'u', 'ul'
    ],
    // 允许的属性
    ALLOWED_ATTR: [
        'href', 'src', 'alt', 'title', 'class', 'align',
        'width', 'height', 'colspan', 'rowspan'
    ],
    // 危险标签一律禁止
    FORBID_TAGS: ['style', 'form', 'input', 'textarea', 'iframe', 'object', 'embed', 'script', 'link', 'meta'],
    // 危险属性一律禁止（含 onerror / onclick 等所有内联事件）
    FORBID_ATTR: ['style', 'onerror', 'onload', 'onclick', 'onmouseover', 'srcdoc'],
    // 禁止 data: 协议（除图片外），避免 data:text/html 执行脚本
    ALLOW_DATA_ATTR: false,
    // 保留相对路径图片
    ALLOW_UNKNOWN_PROTOCOLS: false
}

/**
 * 外链安全：所有 <a> 强制加 rel，禁止通过 window.opener 反向控制原页面。
 * 这一步在 DOMPurify 内部完成，保证不依赖调用方。
 */
let hookRegistered = false
const ensureHook = () => {
    if (hookRegistered) {
        return
    }
    DOMPurify.addHook('afterSanitizeAttributes', (node) => {
        if (node.tagName === 'A' && node.getAttribute('href')) {
            node.setAttribute('target', '_blank')
            node.setAttribute('rel', 'noopener noreferrer')
        }
    })
    hookRegistered = true
}

/**
 * Markdown 转安全 HTML
 *
 * @param markdown 原始 Markdown（来自 LLM，不可信）
 * @returns 经过白名单过滤的 HTML，可安全用于 v-html
 */
export const markdownToHtml = (markdown: string): string => {
    if (!markdown) {
        return ''
    }
    ensureHook()
    const rawHtml = marked(markdown) as string
    return DOMPurify.sanitize(rawHtml, PURIFY_CONFIG)
}

/**
 * 仅净化 HTML（不经过 Markdown 解析）
 * 用于已经确认是 HTML 片段的场景。
 */
export const sanitizeHtml = (html: string): string => {
    if (!html) {
        return ''
    }
    ensureHook()
    return DOMPurify.sanitize(html, PURIFY_CONFIG)
}
