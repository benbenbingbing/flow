/**
 * 布尔偏好的串行保存器。快速操作只保留最后一次待保存意图，
 * 首次读取失败时必须重新读取版本后才能写入，避免默认值覆盖已保存的偏好。
 */
export function createBooleanPreference({ read, write, remove, onChange, onError = () => {} }) {
  let active = true
  let snapshot = null
  let queued = null
  let running = null
  let loading = null
  let state = { value: false, loaded: false, loading: false, saving: false, source: 'DEFAULT', overridden: false, error: '' }

  function publish(patch) {
    if (!active) return
    state = { ...state, ...patch }
    onChange({ ...state })
  }

  function accept(view) {
    if (typeof view?.value !== 'boolean') throw new Error('面板偏好返回了无效值')
    snapshot = view
    publish({ value: queued?.kind === 'save' ? queued.value : view.value,
      loaded: true, source: view.source, overridden: Boolean(view.override), error: '' })
  }

  /** 读取共享一个请求；处于保存队列时不从外部刷新，以免旧响应覆盖新意图。 */
  function load() {
    if (!active) return Promise.resolve(false)
    if (loading) return loading
    publish({ loading: true })
    loading = (async () => {
      try {
        const view = await Promise.resolve().then(() => active ? read() : null)
        if (active) accept(view)
        return active
      } catch (error) {
        publish({ loaded: false, error: '偏好读取失败，点击面板时将重试' })
        return false
      } finally {
        publish({ loading: false })
        loading = null
      }
    })()
    return loading
  }

  /** 同一账号跨页面复用此队列；失败回退并提示，下一次操作重新读取服务端状态。 */
  function drain() {
    if (running) return running
    publish({ saving: true, error: '' })
    running = (async () => {
      try {
        if (loading) await loading
        if (!active) return
        if (!state.loaded && !(await load())) throw new Error('无法读取偏好，状态尚未保存')
        while (active && queued) {
          const intent = queued
          queued = null
          const version = { expectedId: snapshot?.override?.id ?? null, expectedVersion: snapshot?.override?.version ?? null }
          const view = intent.kind === 'reset'
            ? await remove(version)
            : await write({ ...version, settingValue: JSON.stringify(intent.value) })
          if (active) accept(view)
        }
      } catch (error) {
        queued = null
        publish({ value: snapshot?.value ?? false, loaded: false,
          error: error?.status === 409 ? '偏好已在其他页面修改，请重试' : '偏好保存失败，请重试' })
        if (active) onError(state.error)
      } finally {
        publish({ saving: false })
        running = null
      }
    })()
    return running
  }

  return {
    refresh: () => active && !running ? load() : Promise.resolve(false),
    setValue(value) {
      if (!active || typeof value !== 'boolean') return Promise.resolve()
      queued = { kind: 'save', value }
      publish({ value })
      return drain()
    },
    reset() {
      if (!active) return Promise.resolve()
      queued = { kind: 'reset' }
      return drain()
    },
    /** 账号切换后丢弃旧响应和待写意图，不允许用新账号继续旧账号队列。 */
    dispose() { active = false; queued = null }
  }
}
