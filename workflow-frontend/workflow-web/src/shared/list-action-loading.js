import { nextTick, onScopeDispose, reactive } from 'vue'

/** 只有显式关闭才隐藏转圈，旧配置和内置按钮不需要补写默认值。 */
export function showsListActionLoading(button) {
  return button?.type !== 'custom' || button.showLoading !== false
}

function actionKey(button, row) {
  return JSON.stringify([row ? String(row.id) : null, String(button.key)])
}

function isDeletion(button) {
  return button.type === 'built-in' && ['delete', 'batchDelete'].includes(button.key)
}

function actionResource(button) {
  // 列表复用弹窗实例，跨行连续打开也必须串行；删除确认框使用单独的互斥资源。
  return isDeletion(button) ? 'confirmation' : 'presentation'
}

/**
 * nextTick 只保证 DOM 更新，双帧等待才让浏览器先绘制反馈，再执行可能较重的同步逻辑。
 * 后台页使用计时器兜底，避免 requestAnimationFrame 暂停时动作一直不执行。
 */
export async function paintListActionFeedback() {
  await nextTick()
  if (typeof requestAnimationFrame !== 'function') return
  await new Promise(resolve => {
    let first, second
    const finish = () => {
      clearTimeout(timer)
      cancelAnimationFrame(first)
      cancelAnimationFrame(second)
      resolve()
    }
    const timer = setTimeout(finish, 100)
    first = requestAnimationFrame(() => { second = requestAnimationFrame(finish) })
  })
}

/**
 * 保存按钮执行状态而非修改行数据；展示开关不影响互斥和重复点击保护。
 * task 必须返回覆盖整个异步工作的 Promise，打开界面的动作在首屏可见后即可完成。
 */
export function useListActionLoading() {
  const pending = reactive(new Map())

  function isDisabled(button, row) {
    return [...pending.values()].some(entry =>
      (row && entry.recordId === String(row.id))
      || entry.resource === actionResource(button))
  }

  function isLoading(button, row) {
    return Boolean(pending.get(actionKey(button, row))?.loading)
  }

  async function run(button, row, task) {
    if (isDisabled(button, row)) return
    const key = actionKey(button, row)
    const entry = reactive({
      recordId: row ? String(row.id) : null,
      resource: actionResource(button),
      loading: showsListActionLoading(button) && !isDeletion(button)
    })
    pending.set(key, entry)
    const execution = {
      isCurrent: () => pending.get(key) === entry,
      // 删除先展示确认框；只有用户确认后才把入口按钮切换为执行中的状态。
      startLoading: () => {
        if (execution.isCurrent()) pending.get(key).loading = showsListActionLoading(button)
      }
    }
    try {
      // 关闭展示时保留同步点击语义，便于扩展使用 window.open 等依赖用户激活的能力。
      if (entry.loading) await paintListActionFeedback()
      if (!execution.isCurrent()) return
      return await task(execution)
    } finally {
      // 包含弹窗首次挂载的 DOM 更新和绘制，避免网络刚结束便过早撤掉点击反馈。
      await paintListActionFeedback()
      if (execution.isCurrent()) pending.delete(key)
    }
  }

  function reset() { pending.clear() }
  onScopeDispose(reset)
  return { isLoading, isDisabled, run, reset }
}
