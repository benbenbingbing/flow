/** 页内定位和翻页不创建新标签；业务实体、列表、表单及其他入口参数仍参与身份判断。 */
const transientQueryKeys = new Set(['page', 'pageNum', 'pageSize', 'sort', 'order', 'tab', 'settings', 'section', 'targetType', 'targetKey'])

export const workspacePageKey = Symbol('workspacePage')

/** 统一路由身份，避免参数排列不同或页内切换产生重复标签。 */
export function workspaceRouteKey(route) {
  const query = Object.keys(route.query || {}).sort()
    .filter(key => !transientQueryKeys.has(key))
    .map(key => [key, route.query[key]])
  return JSON.stringify([route.path.replace(/\/$/, '') || '/', query])
}

/** 每个标签保存独立路由快照，后台缓存页面不会观察到其他标签的路由参数。 */
export function snapshotWorkspaceRoute(route) {
  return { ...route, params: { ...route.params }, query: { ...route.query }, meta: { ...route.meta }, matched: [...route.matched] }
}

export function workspaceRouteTitle(route) {
  const title = route.meta?.title || '页面'
  const identity = route.params?.id || route.params?.entityCode || route.params?.entityId || route.params?.instanceId
  return identity ? `${title} · ${identity}` : title
}
