import { computed, ref } from 'vue'

export function handoverUserLabel(user) {
  if (!user) return ''
  const name = user.nickname || user.username || user.id
  return user.username && user.username !== name ? `${name}（${user.username}）` : String(name)
}

export function isDeletedUser(user) {
  return user?.deleted === true || String(user?.deleted) === '1'
}

/** 接收人必须仍可登录办理；来源用户故意不使用此规则，以支持禁用、离职交接。 */
export function isEligibleTarget(user, sourceUserId) {
  return Boolean(user?.id) && String(user.id) !== String(sourceUserId)
    && String(user.status) === '0' && !isDeletedUser(user)
}

/**
 * 根据确认的交接范围生成请求，禁止空选择被误解释为“全部”。
 * 用户状态仍由服务端提交时复核；页面校验仅负责在确认前给出可理解的提示。
 */
export function buildHandoverPayload({ sourceUserId, targetUser, taskIds, all, reason }) {
  if (!sourceUserId) throw new Error('请先选择待交接人员')
  if (!isEligibleTarget(targetUser, sourceUserId)) throw new Error('请选择状态正常且与待交接人员不同的接收人')
  const normalizedReason = String(reason || '').trim()
  if (!normalizedReason) throw new Error('请填写交接原因')
  if (normalizedReason.length > 500) throw new Error('交接原因不能超过 500 字')
  const ids = [...new Set((taskIds || []).map(id => String(id).trim()).filter(Boolean))]
  if (all !== true && !ids.length) throw new Error('请至少选择一项待办')
  return {
    sourceUserId: String(sourceUserId), targetUserId: String(targetUser.id),
    taskIds: all === true ? [] : ids, all: all === true, reason: normalizedReason
  }
}

/**
 * 管理人员待办的分页、跨页选择和请求顺序。
 * 切换来源立即清空旧任务并作废在途请求，避免将上一人的任务带入本次交接。
 */
export function useTaskHandover(api) {
  const sourceUserId = ref('')
  const tasks = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const error = ref('')
  const selectedTaskIds = ref([])
  const selectedCount = computed(() => selectedTaskIds.value.length)
  let requestVersion = 0

  function clearSelection() {
    selectedTaskIds.value = []
  }

  async function loadTasks() {
    const version = ++requestVersion
    if (!sourceUserId.value) {
      loading.value = false
      return
    }
    loading.value = true
    error.value = ''
    tasks.value = []
    try {
      const result = await api.tasks({ sourceUserId: sourceUserId.value, pageNum: pageNum.value, pageSize: pageSize.value })
      if (version !== requestVersion) return
      tasks.value = result?.list ?? result?.records ?? []
      total.value = Number(result?.total || 0)
      // 并发办理可能导致最后一页变空，自动退回有效页，避免误以为没有待办。
      const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
      if (pageNum.value > lastPage) {
        pageNum.value = lastPage
        return await loadTasks()
      }
    } catch (cause) {
      if (version !== requestVersion) return
      tasks.value = []
      total.value = 0
      error.value = cause?.message || '待办加载失败，请重试'
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  async function changeSource(id) {
    ++requestVersion
    sourceUserId.value = id ? String(id) : ''
    tasks.value = []
    total.value = 0
    pageNum.value = 1
    error.value = ''
    clearSelection()
    await loadTasks()
  }

  /** 只替换当前页的选择，翻页时保留其他页；表格程序性回显不调用此方法。 */
  function selectCurrentPage(rows) {
    const currentIds = new Set(tasks.value.map(task => String(task.taskId)))
    const retained = selectedTaskIds.value.filter(id => !currentIds.has(id))
    const selected = rows.map(task => String(task.taskId)).filter(id => currentIds.has(id))
    selectedTaskIds.value = [...new Set([...retained, ...selected])]
  }

  async function changePage(page) {
    if (pageNum.value === page) return
    pageNum.value = page
    await loadTasks()
  }

  async function changePageSize(size) {
    pageSize.value = size
    pageNum.value = 1
    await loadTasks()
  }

  async function refresh() {
    clearSelection()
    await loadTasks()
  }

  return {
    sourceUserId, tasks, total, pageNum, pageSize, loading, error,
    selectedTaskIds, selectedCount, loadTasks, changeSource, clearSelection,
    selectCurrentPage, changePage, changePageSize, refresh
  }
}
