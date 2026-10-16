export const INBOX_KEYS = ['todo', 'done', 'started', 'cc']

/** 每个页签独立持有状态，其他页签结束请求不能提前关闭当前页签的 loading。 */
export function createInboxPageState() {
  return Object.fromEntries(INBOX_KEYS.map(key => [key, {
    rows: [], total: 0, loading: false, error: '', loaded: false, generation: 0
  }]))
}

/**
 * PC 分页以最新请求替换列表。prepareRows 可异步补齐任务能力，但必须全部完成
 * 后再提交同一代数据；旧请求成功、失败和 finally 都不得修改新一代状态。
 * 返回的 dispose 在页面卸载时使所有在途请求失效，不取消已有服务端操作。
 */
export function createInboxPageLoader(state, api, { prepareRows = async (_key, rows) => rows } = {}) {
  const labels = { todo: '待办任务', done: '已办任务', started: '我发起的流程', cc: '知会记录' }
  async function load(key, params) {
    const entry = state[key]
    if (!entry || !api[key]) return
    const generation = ++entry.generation
    const isCurrent = () => generation === entry.generation
    entry.loading = true
    entry.error = ''
    try {
      const result = await api[key](params)
      if (!isCurrent()) return
      const rows = Array.isArray(result) ? result : result?.records || result?.list || []
      const prepared = await prepareRows(key, rows)
      if (!isCurrent()) return
      entry.rows = prepared
      entry.total = Array.isArray(result) ? rows.length : result?.total || 0
      entry.loaded = true
    } catch (error) {
      if (!isCurrent()) return
      entry.error = error?.message || `无法读取${labels[key]}，请重试`
      // 保留原我发起的入口失败时清空数据的行为；其他入口保留上次结果供重试。
      if (key === 'started') { entry.rows = []; entry.total = 0 }
    } finally {
      if (isCurrent()) entry.loading = false
    }
  }
  function dispose() {
    for (const entry of Object.values(state)) { entry.generation++; entry.loading = false }
  }
  return { load, dispose }
}
