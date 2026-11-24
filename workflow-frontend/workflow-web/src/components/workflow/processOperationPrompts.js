import { ElMessageBox } from 'element-plus'

export const INITIATOR_TERMINATION_REASON = '发起人主动终止'

function ignoreDismissal(action) {
  if (action === 'cancel' || action === 'close') return null
  throw action
}

/** 确认后返回清理首尾空格的选填原因，取消或关闭时返回 null。 */
export async function promptProcessWithdrawal() {
  const result = await ElMessageBox.prompt(
    '撤回后本轮流程结束，未完成的待办将取消。确认撤回吗？',
    '撤回流程',
    {
      type: 'warning', inputType: 'textarea', inputPlaceholder: '请输入撤回原因（选填）',
      inputValidator: value => !value || value.length <= 2000 || '撤回原因不能超过 2000 字',
      confirmButtonText: '确认撤回', cancelButtonText: '取消'
    }
  ).catch(ignoreDismissal)
  return result === null ? null : (result.value || '').trim()
}

/** 以工作台的终止提示确认操作，取消或关闭时返回 false。 */
export async function confirmProcessTermination() {
  const result = await ElMessageBox.confirm(
    '终止后流程将直接结束，相关待办也会取消，且不能从当前节点继续。确认终止吗？',
    '终止流程',
    { type: 'warning', confirmButtonText: '确认终止', cancelButtonText: '取消' }
  ).catch(ignoreDismissal)
  return result !== null
}
