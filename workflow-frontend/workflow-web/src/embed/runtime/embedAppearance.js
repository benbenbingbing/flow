/**
 * 嵌入页复用主应用的中文文案与 Element Plus zhCn。尚无其它业务翻译资源时，
 * 必须明确拒绝不支持的语言，避免宿主收到 ACK 后控件仍显示另一种语言。
 */
export function resolveEmbedLocale(locale = 'zh-CN') {
  if (String(locale).toLowerCase() === 'zh-cn') return 'zh-CN'
  const error = new Error('当前嵌入页面仅支持 zh-CN 语言')
  error.errorCode = 'EMBED_OPERATION_NOT_ALLOWED'
  throw error
}

/**
 * 将嵌入页外观应用到独立 iframe 的根节点，让 Teleport 到 body 的弹窗、下拉框
 * 同样继承 Element Plus 主题变量；销毁时恢复原状态并注销系统主题监听。
 */
export function createEmbedAppearance({
  documentRef = globalThis.document,
  windowRef = globalThis.window
} = {}) {
  const root = documentRef?.documentElement
  const originalDark = root?.classList?.contains('dark') || false
  const originalColorScheme = root?.style?.colorScheme || ''
  const originalLang = root?.getAttribute?.('lang') ?? null
  const media = windowRef?.matchMedia?.('(prefers-color-scheme: dark)')
  let theme = 'light'
  let disposed = false

  function applyTheme() {
    if (disposed || !root) return
    const dark = theme === 'dark' || (theme === 'system' && media?.matches === true)
    root.classList.toggle('dark', dark)
    root.style.colorScheme = dark ? 'dark' : 'light'
  }

  // 显式 light/dark 不受系统变化影响，system 模式无需宿主再次发命令即可跟随。
  if (media?.addEventListener) media.addEventListener('change', applyTheme)
  else media?.addListener?.(applyTheme)

  return {
    apply({ theme: nextTheme, locale }) {
      if (disposed) return
      const supportedLocale = resolveEmbedLocale(locale)
      theme = String(nextTheme || 'light').toLowerCase()
      root?.setAttribute?.('lang', supportedLocale)
      applyTheme()
    },
    dispose() {
      if (disposed) return
      disposed = true
      if (media?.removeEventListener) media.removeEventListener('change', applyTheme)
      else media?.removeListener?.(applyTheme)
      if (!root) return
      root.classList.toggle('dark', originalDark)
      root.style.colorScheme = originalColorScheme
      if (originalLang === null) root.removeAttribute('lang')
      else root.setAttribute('lang', originalLang)
    }
  }
}
