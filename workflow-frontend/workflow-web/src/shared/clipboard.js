/**
 * 将任意单元格值转换为适合写入剪贴板的文本。
 * 对象和数组优先保留结构，避免复制后只得到 "[object Object]"。
 */
export function normalizeClipboardText(value) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

/**
 * 优先使用现代 Clipboard API；在非安全上下文或旧浏览器中回退到隐藏文本框。
 * environment 参数用于测试注入浏览器能力，业务调用时无需传入。
 */
export async function writeClipboardText(value, environment = globalThis) {
  const text = normalizeClipboardText(value)
  let clipboardError

  if (typeof environment?.navigator?.clipboard?.writeText === 'function') {
    try {
      await environment.navigator.clipboard.writeText(text)
      return
    } catch (error) {
      clipboardError = error
    }
  }

  const documentRef = environment?.document
  if (!documentRef?.body || typeof documentRef.execCommand !== 'function') {
    throw clipboardError || new Error('当前浏览器不支持剪贴板写入')
  }

  const textarea = documentRef.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  documentRef.body.appendChild(textarea)

  let copied = false
  try {
    textarea.select()
    copied = documentRef.execCommand('copy')
  } finally {
    textarea.remove()
  }
  if (!copied) throw clipboardError || new Error('浏览器拒绝剪贴板写入')
}
