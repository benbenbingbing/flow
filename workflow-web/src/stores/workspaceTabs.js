import { defineStore } from 'pinia'
import { ref, shallowReactive, shallowRef, markRaw } from 'vue'
import { snapshotWorkspaceRoute, workspaceRouteKey, workspaceRouteTitle } from '../shared/workspace-tabs.js'

/** 会话内页面目录；不把路由参数、未提交表单或打开记录写入个人偏好表。 */
export const useWorkspaceTabsStore = defineStore('workspaceTabs', () => {
  const tabs = shallowRef([])
  const enabled = ref(false)
  const activeKey = ref('')
  const generation = ref(0)
  const busy = ref(false)
  const guards = new Map()
  let sequence = 0
  let confirmDiscard = async () => false

  function configureConfirm(confirm) { confirmDiscard = confirm }

  /** 只在导航成功后调用；单页模式淘汰旧页面，多页模式保留各自实例。 */
  function visit(route, title = workspaceRouteTitle(route)) {
    const key = workspaceRouteKey(route)
    let tab = tabs.value.find(item => item.key === key)
    if (!tab) {
      tab = shallowReactive({ key, cacheName: `WorkspacePage${++sequence}`, title,
        route: markRaw(snapshotWorkspaceRoute(route)), scrollTop: 0, scrollLeft: 0 })
    } else {
      tab.route = markRaw(snapshotWorkspaceRoute(route))
      tab.title = title
    }
    tabs.value = enabled.value ? (tabs.value.includes(tab) ? tabs.value : [...tabs.value, tab]) : [tab]
    activeKey.value = key
    return tab
  }

  /** 页面和内部表单可注册多个未保存检查；关闭后台标签也能统一检查。 */
  function registerGuard(key, guard) {
    const entries = guards.get(key) || new Set()
    entries.add(guard)
    guards.set(key, entries)
    return () => {
      entries.delete(guard)
      // 账号切换后，同一路由可能已注册新检查；旧实例卸载不能删除新账号的检查。
      if (!entries.size && guards.get(key) === entries) guards.delete(key)
    }
  }

  async function canDiscard(keys) {
    const dirty = keys.flatMap(key => [...(guards.get(key) || [])]
      .filter(guard => guard.isDirty()).map(guard => ({ title: tabs.value.find(tab => tab.key === key)?.title || '页面', message: guard.message })))
    return !dirty.length || await confirmDiscard(dirty)
  }

  function hasUnsavedChanges() {
    return tabs.value.some(tab => [...(guards.get(tab.key) || [])].some(guard => guard.isDirty()))
  }

  /** 关闭模式仅销毁其他标签，保留当前页面实例；确认取消时不改变任何状态。 */
  async function setEnabled(value) {
    if (enabled.value === value) return true
    if (busy.value) return false
    const epoch = generation.value
    const current = activeKey.value
    busy.value = true
    try {
      if (!value && !(await canDiscard(tabs.value.filter(tab => tab.key !== activeKey.value).map(tab => tab.key)))) return false
      if (epoch !== generation.value || current !== activeKey.value) return false
      enabled.value = value
      if (!value) tabs.value = tabs.value.filter(tab => tab.key === activeKey.value)
      return true
    } finally {
      if (epoch === generation.value) busy.value = false
    }
  }

  /** 先检查再导航，导航成功后才移除当前标签；取消或权限拒绝都保留原页。 */
  async function close(key, navigate) {
    if (busy.value) return false
    const index = tabs.value.findIndex(tab => tab.key === key)
    if (index < 0 || tabs.value[index].route.path === '/home') return false
    const epoch = generation.value
    busy.value = true
    try {
      if (!(await canDiscard([key])) || epoch !== generation.value) return false
      if (activeKey.value === key) {
        const neighbour = tabs.value[index + 1] || tabs.value[index - 1]
        if (!(await navigate(neighbour?.route.fullPath || '/home'))) return false
      }
      if (epoch !== generation.value) return false
      tabs.value = tabs.value.filter(tab => tab.key !== key)
      return true
    } finally {
      if (epoch === generation.value) busy.value = false
    }
  }

  /** 退出/切换账号直接销毁页面缓存，不能把前一账号的内容留给新账号。 */
  function clear() {
    generation.value++
    tabs.value = []
    activeKey.value = ''
    enabled.value = false
    busy.value = false
    guards.clear()
  }

  return { tabs, enabled, activeKey, generation, busy, visit, registerGuard, canDiscard, hasUnsavedChanges, configureConfirm, setEnabled, close, clear }
})
