/**
 * Embed 运行时与后台运行时的启动边界。
 *
 * 该模块必须保持为无副作用的纯函数：未来的 main.js 可在导入任何后台
 * router、store 或扩展之前调用它，从而确保 iframe 页面不会意外恢复普通登录态。
 */

export const EMBED_ROUTE_PREFIX = '/embed/v1/launches/'
const EMBED_ENTRY_PATH_PATTERN = /^\/embed\/v1\/launches\/[A-Za-z0-9_-]{3,256}$/

function normalizeHost(value) {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/\.$/, '')
}

/** 将部署配置的逗号分隔 host 列表转换为精确匹配集合。 */
export function normalizeEmbedHosts(hosts = []) {
  const values = Array.isArray(hosts)
    ? hosts
    : String(hosts || '').split(',')

  return new Set(
    values
      .map(normalizeHost)
      .filter(Boolean)
  )
}

/** 仅识别约定的 Embed 路径，避免普通业务路径被误判为 iframe 运行时。 */
export function isEmbedPath(pathname = '') {
  const path = String(pathname || '/')
  return EMBED_ENTRY_PATH_PATTERN.test(path)
}

/**
 * 决定应启动哪个前端运行时。
 *
 * 生产环境应只配置 embedHosts；allowEmbedPath 仅供本地开发或由单独入口
 * 已经完成隔离的场景使用，不能作为开放任意后台路径的权限判断。
 * 返回 blocked 时调用方必须渲染静态拒绝页，绝不能继续启动后台应用。
 */
export function resolveRuntimeMode(
  locationLike = globalThis.location,
  {
    embedHosts = [],
    allowEmbedPath = false
  } = {}
) {
  const host = normalizeHost(locationLike?.hostname || locationLike?.host)
  const pathname = String(locationLike?.pathname || '/')
  const trustedEmbedHosts = normalizeEmbedHosts(embedHosts)
  const embedPath = isEmbedPath(pathname)

  if (trustedEmbedHosts.has(host)) return embedPath ? 'embed' : 'blocked'
  if (embedPath) return allowEmbedPath ? 'embed' : 'blocked'
  return 'admin'
}

export function isEmbedRuntimeLocation(locationLike, options) {
  return resolveRuntimeMode(locationLike, options) === 'embed'
}
