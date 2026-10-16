const DEFAULT_REDIRECT = '/inbox/todo'
const LOCAL_ORIGIN = 'https://mobile.invalid'

/**
 * 登录回跳只接收移动路由内部的绝对路径，保留查询参数和锚点供原单据继续使用。
 * URL 参数可被手工修改，因此外链、歧义路径和登录页自身均回退待办，避免外跳或登录循环。
 */
export function resolveLoginRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(value)) return DEFAULT_REDIRECT
  try {
    const target = new URL(value, LOCAL_ORIGIN)
    // 先归一化点路径，再解码路径检查；查询参数内的 URL 只是业务数据，不参与跳转判断。
    const pathname = decodeURIComponent(target.pathname)
    if (target.origin !== LOCAL_ORIGIN || pathname.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(pathname) || /^\/(?:m\/)?login\/?$/i.test(pathname)) return DEFAULT_REDIRECT
    return value
  } catch {
    return DEFAULT_REDIRECT
  }
}

/**
 * 会话失效通过整页导航重新登录时，将浏览器的 /m/ 地址转回路由内部路径。
 * 已处于登录页则不再导航，确保已有的回跳参数不会被登录地址覆盖。
 */
export function buildMobileLoginUrl({ pathname = '', search = '', hash = '' } = {}) {
  if (/^\/m\/login\/?$/i.test(pathname)) return null
  const fullPath = pathname.startsWith('/m/') ? `${pathname.slice(2)}${search}${hash}` : DEFAULT_REDIRECT
  const query = new URLSearchParams({ redirect: resolveLoginRedirect(fullPath) })
  return `/m/login?${query}`
}
