import { webBase } from './deployment-path.js'

const DEFAULT_REDIRECT = '/'
const LOCAL_ORIGIN = 'https://workflow.invalid'
const AUTH_PAGE = /^\/(?:login|change-password)\/?$/i

/**
 * 登录回跳参数可被手工修改，只接受本站路由绝对路径，并保留单据查询参数和锚点。
 * 登录和改密页不能成为最终目标，避免完成认证后重新进入认证流程。
 */
export function resolveLoginRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(value)) return DEFAULT_REDIRECT
  try {
    const target = new URL(value, LOCAL_ORIGIN)
    const pathname = decodeURIComponent(target.pathname)
    if (target.origin !== LOCAL_ORIGIN || pathname.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(pathname) || AUTH_PAGE.test(pathname)) return DEFAULT_REDIRECT
    // 消除点路径，避免浏览器在回跳时将路由拼接的部署前缀一并消除。
    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return DEFAULT_REDIRECT
  }
}

/** 认证流程中的下一次跳转继续携带原单据，不把登录页或改密页包成新的回跳目标。 */
export function authReturnTarget(route) {
  return resolveLoginRedirect(AUTH_PAGE.test(route.path)
    ? route.query?.redirect : route.fullPath || route.path)
}

/** 有具体业务目标时将其放入 URL，刷新页面和改密后的再次登录均可恢复。 */
export function authRouteLocation(path, redirect) {
  const target = resolveLoginRedirect(redirect)
  return target === DEFAULT_REDIRECT ? path : { path, query: { redirect: target } }
}

/**
 * 请求失效与跨标签页退出使用整页导航；已在目标认证页时不重载或覆盖已有回跳参数。
 * 从改密页返回登录页时提取原目标，确保撤销会话后仍能完成重新登录再回单据的链路。
 * 浏览器地址包含部署前缀，回跳参数保留应用内部路径，由路由器在登录后补回前缀。
 */
export function buildAuthRedirectUrl(location, path = '/login', base = webBase) {
  if (!location) return null
  const prefix = base.replace(/\/$/, '')
  if (!location.pathname.startsWith(`${prefix}/`)) return `${prefix}${path}`
  const routePath = location.pathname.slice(prefix.length)
  if (routePath.toLowerCase().replace(/\/$/, '') === path) return null
  const redirects = new URLSearchParams(location.search || '').getAll('redirect')
  const target = authReturnTarget({
    path: routePath,
    fullPath: `${routePath}${location.search || ''}${location.hash || ''}`,
    query: { redirect: redirects.length === 1 ? redirects[0] : undefined }
  })
  const destination = authRouteLocation(path, target)
  return typeof destination === 'string' ? `${prefix}${destination}`
    : `${prefix}${destination.path}?${new URLSearchParams(destination.query)}`
}
