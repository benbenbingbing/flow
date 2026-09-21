export const INBOXES = Object.freeze([
  { key: 'todo', label: '待办', icon: 'todo-list-o', method: 'getTodoList' },
  { key: 'done', label: '已办', icon: 'passed', method: 'getDoneList' },
  { key: 'started', label: '我发起的', icon: 'bookmark-o', method: 'getMyStartedList' },
  { key: 'cc', label: '知会', icon: 'envelop-o', method: 'getMyCcList' }
])

export function createInboxState() {
  return Object.fromEntries(INBOXES.map(({ key }) => [key, {
    rows: [], total: 0, page: 0, loading: false, refreshing: false, finished: false,
    initialized: false, error: '', keyword: '', startUserName: '', startDate: '', endDate: '', scroll: 0, generation: 0
  }]))
}

/** 请求按列表隔离并带代次；筛选、刷新或退出后，迟到响应不能覆盖新列表。 */
export function createInboxLoader(state, api, pageSize = 15) {
  return async function load(kind, refresh = false) {
    const entry = state[kind], definition = INBOXES.find(item => item.key === kind)
    if (!entry || !definition || (!refresh && (entry.loading || entry.finished))) return
    const generation = refresh ? ++entry.generation : entry.generation
    const page = refresh ? 1 : entry.page + 1
    entry.loading = true; entry.error = ''
    try {
      const result = await api[definition.method]({
        pageNum: page, pageSize, keyword: entry.keyword || undefined, processName: entry.keyword || undefined,
        startUserName: entry.startUserName || undefined, startDate: entry.startDate || undefined, endDate: entry.endDate || undefined
      })
      if (entry.generation !== generation) return
      const rows = Array.isArray(result) ? result : result?.list || result?.records || []
      const total = Array.isArray(result) ? rows.length : Number(result?.total ?? rows.length)
      const combined = refresh ? rows : [...entry.rows, ...rows]
      const seen = new Set()
      entry.rows = combined.filter(row => {
        const id = String(row.id || row.taskId || row.processInstanceId)
        if (seen.has(id)) return false
        seen.add(id); return true
      })
      Object.assign(entry, { total, page, initialized: true, finished: Array.isArray(result) || rows.length < pageSize || entry.rows.length >= total })
    } catch (error) {
      if (entry.generation === generation) entry.error = error.message || '加载失败，请重试'
    } finally {
      if (entry.generation === generation) { entry.loading = false; entry.refreshing = false }
    }
  }
}

const invalidationListeners = new Set()
/** 办理与已读事件使受影响列表失效，返回时保留筛选及滚动位置。 */
export function invalidateInboxes(kinds = INBOXES.map(item => item.key)) { invalidationListeners.forEach(listener => listener(kinds)) }
export function onInboxesInvalidated(listener) { invalidationListeners.add(listener); return () => invalidationListeners.delete(listener) }
