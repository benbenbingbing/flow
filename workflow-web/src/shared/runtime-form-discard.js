/**
 * 为原生表单的关闭和重新加载共用未保存检查。
 * 快照只保留在组件内存中；并发关闭/刷新共用一次确认，取消不改变基线。
 */
export function createRuntimeFormDiscardGuard({ readValue, enabled, confirm }) {
  let baseline
  let pending

  function markSaved() {
    baseline = JSON.stringify(readValue())
  }

  function isDirty() {
    return enabled() && baseline !== undefined
      && JSON.stringify(readValue()) !== baseline
  }

  function confirmDiscard() {
    if (pending) return pending
    if (!isDirty()) return Promise.resolve(true)
    pending = Promise.resolve().then(confirm).then(
      () => true,
      () => false
    ).finally(() => { pending = undefined })
    return pending
  }

  function beforeUnload(event) {
    if (!isDirty()) return
    event.preventDefault()
    event.returnValue = ''
  }

  return { markSaved, isDirty, confirmDiscard, beforeUnload }
}
