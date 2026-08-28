function snapshot(state) {
  return Object.freeze({ ...state, items: Object.freeze([...state.items]) })
}

/**
 * 每个动态字段独立维护请求代次并中止上一请求。即使底层 fetch 无法及时中止，旧响应也不能
 * 覆盖新关键字结果；销毁后所有完成回调均失效。
 */
export function createEmbedRemoteChoices({
  query,
  AbortControllerImpl = globalThis.AbortController
} = {}) {
  if (typeof query !== 'function') throw new TypeError('Embed choices query 无效')
  let current = { loading: false, items: [], hasMore: false, error: null, keyword: '' }
  let sequence = 0
  let controller = null
  let destroyed = false
  const listeners = new Set()

  function publish(patch) {
    current = { ...current, ...patch }
    const value = snapshot(current)
    for (const listener of listeners) {
      try { listener(value) } catch { /* 组件订阅异常不能改变请求代次。 */ }
    }
    return value
  }

  async function load(keyword = '') {
    if (destroyed) return null
    const requestSequence = ++sequence
    controller?.abort?.()
    controller = typeof AbortControllerImpl === 'function' ? new AbortControllerImpl() : null
    const normalizedKeyword = String(keyword || '').trim().slice(0, 200)
    publish({ loading: true, error: null, keyword: normalizedKeyword })
    try {
      const page = await query(normalizedKeyword, { signal: controller?.signal })
      if (destroyed || requestSequence !== sequence || !page) return null
      publish({
        loading: false,
        items: Array.isArray(page.items) ? page.items : [],
        hasMore: page.hasMore === true,
        error: null
      })
      return page
    } catch (error) {
      if (destroyed || requestSequence !== sequence || error?.name === 'AbortError'
        || error?.errorCode === 'EMBED_REQUEST_ABORTED') return null
      publish({ loading: false, items: [], hasMore: false, error })
      return null
    }
  }

  function subscribe(listener) {
    if (typeof listener !== 'function') throw new TypeError('Embed choices listener 无效')
    listeners.add(listener)
    listener(snapshot(current))
    return () => listeners.delete(listener)
  }

  function destroy() {
    if (destroyed) return
    destroyed = true
    sequence += 1
    controller?.abort?.()
    controller = null
    listeners.clear()
    current = { loading: false, items: [], hasMore: false, error: null, keyword: '' }
  }

  return Object.freeze({ destroy, getSnapshot: () => snapshot(current), load, subscribe })
}
