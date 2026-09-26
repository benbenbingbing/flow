/**
 * 节点可能在回退后再次激活，因此历史完成记录不能覆盖当前活跃状态。
 * PC 和移动端统一采用 active > terminated > completed > pending；兼容节点 ID 与节点对象。
 */
export function getProcessNodeStatus(progress, nodeId) {
  const contains = key => (progress?.[key] || []).some(node =>
    String(typeof node === 'object' ? node?.nodeId || node?.id : node) === String(nodeId))
  if (contains('activeNodes')) return 'active'
  if (contains('terminatedNodes') || contains('cancelledNodes')) return 'terminated'
  if (contains('completedNodes')) return 'completed'
  return 'pending'
}

/** 流程图与执行历史共用耗时格式；空耗时沿用既有占位展示。 */
export function formatProcessDuration(ms) {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000), minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60), days = Math.floor(hours / 24)
  if (days > 0) return `${days}天${hours % 24}小时`
  if (hours > 0) return `${hours}小时${minutes % 60}分钟`
  if (minutes > 0) return `${minutes}分钟${seconds % 60}秒`
  return `${seconds}秒`
}
