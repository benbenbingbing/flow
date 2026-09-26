export const USER_INTERFACE_PREFERENCES_KEY = 'ui.user_preferences'
export const DEFAULT_USER_INTERFACE_PREFERENCES = Object.freeze({
  fieldTypesCollapsed: false, sidebarCollapsed: false, tabsEnabled: false
})
export const USER_INTERFACE_PREFERENCE_FIELDS = Object.freeze([
  { key: 'fieldTypesCollapsed', label: '实体设计字段类型面板收起状态', description: '开启后收起字段类型面板，切换不影响实体设计的保存与发布。' },
  { key: 'sidebarCollapsed', label: '左侧主菜单收起状态', description: '开启后收起桌面主菜单，窄屏导航抽屉不受影响。' },
  { key: 'tabsEnabled', label: '启用顶部多标签页', description: '开启后在顶部显示页面选项卡；切换时保留页面状态，刷新后不恢复已打开的标签。' }
])
const fields = Object.keys(DEFAULT_USER_INTERFACE_PREFERENCES)

export function initialUserInterfacePreferencesState() {
  return { value: { ...DEFAULT_USER_INTERFACE_PREFERENCES }, overrideValue: {}, loaded: false,
    loading: false, saving: false, source: 'DEFAULT', error: '' }
}

/** 校验服务端协议，不允许将有效值误当成个人覆盖后写回。缺失字段继续继承系统默认。 */
function validateView(view) {
  const isObject = value => value && typeof value === 'object' && !Array.isArray(value)
  if (!isObject(view?.value) || fields.some(key => typeof view.value[key] !== 'boolean')
      || (view.override && !isObject(view.overrideValue))) throw new Error('偏好返回了无效值')
  if (view.overrideValue != null && (!isObject(view.overrideValue)
      || Object.entries(view.overrideValue).some(([key, value]) => !fields.includes(key) || typeof value !== 'boolean'))) {
    throw new Error('偏好返回了无效覆盖')
  }
}

/**
 * 三项偏好共用一份版本和串行保存队列，按字段合并用户操作。
 * 本地选择立即生效；失败后保留选择，下一次操作重新读取并只重试明确修改的字段。
 */
export function createUserInterfacePreferences({ read, write, remove, onChange }) {
  let active = true, snapshot = null, running = null, loading = null
  let queued = new Map()
  const localIntents = new Map()
  let state = initialUserInterfacePreferencesState()

  function publish(patch = {}) {
    if (!active) return
    const value = { ...state.value, ...patch.value }
    for (const [key, intent] of localIntents) value[key] = intent.value
    state = { ...state, ...patch, value }
    onChange({ ...state, value: { ...value }, overrideValue: { ...state.overrideValue } })
  }

  function accept(view, savedIntents = new Map()) {
    validateView(view)
    snapshot = view
    // 旧保存响应只能确认当时提交的意图，不能清除随后对同一字段的新操作。
    for (const [key, intent] of savedIntents) {
      if (localIntents.get(key) === intent) localIntents.delete(key)
    }
    publish({ value: view.value, overrideValue: view.overrideValue || {}, loaded: true,
      source: view.source, error: localIntents.size ? state.error : '' })
  }

  function load() {
    if (!active) return Promise.resolve(false)
    if (loading) return loading
    publish({ loading: true })
    loading = (async () => {
      try {
        const view = await Promise.resolve().then(() => active ? read() : null)
        if (active) accept(view)
        return active
      } catch {
        publish({ loaded: false, error: '偏好读取失败，再次操作时将重试' })
        return false
      } finally {
        publish({ loading: false })
        loading = null
      }
    })()
    return loading
  }

  /** 只修改稀疏覆盖中的目标字段；清空最后一个覆盖时删除整行，恢复完整继承。 */
  function drain() {
    if (running) return running
    publish({ saving: true, error: '' })
    running = (async () => {
      try {
        if (loading) await loading
        if (!active) return
        if (!state.loaded && !(await load())) throw new Error('无法读取偏好')
        while (active && queued.size) {
          const intents = queued
          queued = new Map()
          const overrides = { ...snapshot.overrideValue }
          for (const [key, intent] of intents) {
            if (intent.kind === 'reset') delete overrides[key]
            else overrides[key] = intent.value
          }
          const version = { expectedId: snapshot.override?.id ?? null, expectedVersion: snapshot.override?.version ?? null }
          const view = Object.keys(overrides).length
            ? await write({ ...version, settingValue: JSON.stringify(overrides) })
            : await remove(version)
          if (active) accept(view, intents)
        }
      } catch (error) {
        queued.clear()
        publish({ loaded: false, error: error?.status === 409
          ? '偏好已在其他页面修改，本次选择仅在当前会话生效' : '偏好未保存，本次选择仅在当前会话生效' })
      } finally {
        publish({ saving: false })
        running = null
      }
    })()
    return running
  }

  function update(key, intent) {
    if (!active || !fields.includes(key)) return Promise.resolve()
    localIntents.set(key, intent)
    // 失败后仍保留所有明确的用户选择；重试不会把未操作字段的系统值写成个人值。
    queued = new Map(localIntents)
    publish()
    return drain()
  }

  return {
    refresh: () => active && !running ? load() : Promise.resolve(false),
    setValue: (key, value) => typeof value === 'boolean' ? update(key, { kind: 'save', value }) : Promise.resolve(),
    reset: key => update(key, { kind: 'reset', value: state.value[key] }),
    // 账号切换后停止旧队列；已发请求可以完成，但其响应不得进入新账号状态。
    dispose() { active = false; queued.clear(); localIntents.clear() }
  }
}
