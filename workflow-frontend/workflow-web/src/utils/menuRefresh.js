export const SIDEBAR_MENU_REFRESH_EVENT = 'workflow:sidebar-menu-refresh'
export const SIDEBAR_MENU_REVISION_KEY = 'workflow:sidebar-menu-revision'

export const notifySidebarMenuChanged = () => {
  if (typeof window === 'undefined') {
    return
  }
  const revision = `${Date.now()}-${Math.random()}`
  try {
    window.localStorage.setItem(SIDEBAR_MENU_REVISION_KEY, revision)
  } catch {
    // 同窗口刷新依赖自定义事件，本地存储仅用于通知其他标签页。
  }
  window.dispatchEvent(new CustomEvent(SIDEBAR_MENU_REFRESH_EVENT, {
    detail: { revision }
  }))
}
