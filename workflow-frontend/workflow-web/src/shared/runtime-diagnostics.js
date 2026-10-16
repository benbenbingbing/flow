export const RUNTIME_DIAGNOSTIC_CLICK_COUNT = 3
export const RUNTIME_DIAGNOSTIC_CLICK_WINDOW_MS = 1500

/**
 * 创建隐藏诊断入口的点击状态机。
 * 只有在同一时间窗内完成三次点击才切换显隐，避免普通单击误触发排障信息。
 */
export function createRuntimeDiagnosticTrigger({
  clickCount = RUNTIME_DIAGNOSTIC_CLICK_COUNT,
  windowMs = RUNTIME_DIAGNOSTIC_CLICK_WINDOW_MS,
  now = () => Date.now()
} = {}) {
  let firstClickAt = null
  let currentClickCount = 0
  let visible = false

  function recordClick() {
    const timestamp = now()
    if (firstClickAt == null || timestamp - firstClickAt > windowMs) {
      firstClickAt = timestamp
      currentClickCount = 1
    } else {
      currentClickCount += 1
    }

    if (currentClickCount < clickCount) {
      return { toggled: false, visible }
    }

    visible = !visible
    firstClickAt = null
    currentClickCount = 0
    return { toggled: true, visible }
  }

  function reset() {
    firstClickAt = null
    currentClickCount = 0
    visible = false
  }

  return {
    recordClick,
    reset,
    isVisible: () => visible
  }
}

export function normalizeRuntimeDiagnosticEntries(entries = []) {
  return entries
    .map(entry => ({
      label: String(entry?.label || '').trim(),
      value: String(entry?.value ?? '').trim()
    }))
    .filter(entry => entry.label && entry.value)
}

/**
 * 统一生成可粘贴到工单或日志检索中的纯文本，保证界面展示和复制内容口径一致。
 */
export function buildRuntimeDiagnosticText(entries, title = '运行版本排障信息') {
  const lines = normalizeRuntimeDiagnosticEntries(entries)
    .map(entry => `${entry.label}：${entry.value}`)
  return [title, ...lines].join('\n')
}

export function formatRuntimeVersion(value) {
  const version = Number(value)
  return Number.isInteger(version) && version > 0 ? `v${version}` : '版本未记录'
}

export function formatRuntimeCodeVersion(code, version, emptyCode = '编码未记录') {
  const normalizedCode = String(code || '').trim() || emptyCode
  return `${normalizedCode} · ${formatRuntimeVersion(version)}`
}
