import { resolveAllowedAddSignTypes } from './workflow-operation-guards.js'

/**
 * 认领是候选任务的可选操作，不再决定能否进入审批。
 * 优先使用新版 canClaim；仅在字段缺失时兼容旧服务端，显式 false 不得被旧字段放开。
 */
export function isTaskClaimable(task) {
  if (!task?.taskId || task.nodeType === 'ADD_SIGN') return false
  return Object.prototype.hasOwnProperty.call(task, 'canClaim')
    ? task.canClaim === true
    : task.claimRequired === true
}

/** 构造待办的次要操作；候选任务可主动接手，其他管理操作延续已分配任务的能力约束。 */
export function getTodoTaskMoreActions(task = {}) {
  const actions = []
  if (isTaskClaimable(task)) {
    actions.push({ command: 'claim', label: '认领任务' })
  } else if (task.nodeType !== 'ADD_SIGN') {
    if (task.taskOperations?.transfer === true) {
      actions.push({ command: 'transfer', label: '转办' })
    }
    if (task.taskOperations?.addSign === true
        && resolveAllowedAddSignTypes(task.taskOperations).length > 0) {
      actions.push({ command: 'addSign', label: '加签' })
    } else if (task.taskOperations?.activeAddSign?.id) {
      actions.push({ command: 'cancelAddSign', label: '撤销加签' })
    }
    if (task.taskOperations?.manualCc !== false) {
      actions.push({ command: 'cc', label: '知会' })
    }
  }
  if (task.slaStatus) {
    actions.push({ command: 'sla', label: 'SLA', divided: actions.length > 0 })
  }
  return actions
}

/** 仅解释任务竞争的业务错误码，其他权限/校验错误继续展示服务端原消息。 */
export function taskApprovalConflictMessage(error) {
  const codes = [
    error?.errorCode,
    error?.source?.errorCode,
    error?.response?.data?.errorCode
  ]
  const reasons = {
    TASK_ALREADY_CLAIMED: '任务已被其他办理人接手，无法提交审批',
    TASK_ALREADY_COMPLETED: '任务已处理或不再存在，无法重复提交审批',
    TASK_STATE_CHANGED: '任务状态已变化，请确认最新任务状态后再处理'
  }
  const code = codes.find(value => Object.prototype.hasOwnProperty.call(reasons, value))
  return code ? `${reasons[code]}。你填写的内容仍保留在当前弹窗。` : ''
}
