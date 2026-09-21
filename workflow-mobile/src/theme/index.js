import { DEFAULT_MOBILE_THEME, mobileThemeVariables, normalizeMobileTheme } from '@flow/workflow-core/mobile-theme'

/** 只写入移动文档的变量，根节点与 body 同步以覆盖 teleport 弹窗和 Vant 基础变量。 */
export function applyTheme(config, documentRef = document) {
  const value = normalizeMobileTheme(config)
  const variables = mobileThemeVariables(value)
  for (const target of [documentRef.documentElement, documentRef.body]) {
    if (!target) continue
    target.classList.add('flow-mobile')
    for (const [key, color] of Object.entries(variables)) target.style.setProperty(key, color)
  }
  return value
}
/** 恢复程序默认主题，用于未配置、请求失败或配置无效时的统一兜底。 */
export function resetTheme(documentRef = document) { return applyTheme(DEFAULT_MOBILE_THEME, documentRef) }

/** 每次启动直读系统主题，不使用个人偏好或本地缓存；超时/格式异常回退默认值，保障登录可用。 */
export async function loadSystemTheme({ fetchImpl = fetch, documentRef = document, timeoutMs = 4000 } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const base = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
    const response = await fetchImpl(`${base}/system/mobile-theme`, { cache: 'no-store', credentials: 'omit', signal: controller.signal })
    if (!response.ok) throw new Error('主题读取失败')
    const result = await response.json()
    if (result.code !== 200) throw new Error('主题读取失败')
    return applyTheme(result.data, documentRef)
  } catch { return resetTheme(documentRef) }
  finally { clearTimeout(timer) }
}
