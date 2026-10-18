export const importStateLabels = {
  DRAFT: '待上传', UPLOADED: '待预检', READY: '预检通过', RUNNING: '可继续执行',
  PARTIAL_SUCCESS: '部分成功 / 待重试', FAILED: '执行失败', SUCCEEDED: '已完成',
  PENDING: '待执行', COMMITTED: '已提交'
}
export const archiveResultLabels = {
  APPROVED: '审批通过', REJECTED: '已驳回', WITHDRAWN: '已撤回',
  TERMINATED: '已终止', COMPLETED_UNKNOWN: '已结束（结果未知）'
}
export const archiveActionLabels = {
  APPROVED: '同意', REJECTED: '驳回', SUBMITTED: '提交', TRANSFERRED: '转办',
  WITHDRAWN: '撤回', TERMINATED: '终止', COMMENTED: '补充意见'
}

/** 必须按服务端冻结的依赖顺序推进，界面排序不参与执行顺序。 */
export function nextImportGroup(batch) {
  const states = new Map((batch?.groups || []).map(group => [group.key, group]))
  return (batch?.groupOrder || []).map(key => states.get(key)).find(group => group && group.state !== 'COMMITTED')
}

/** 保留同节点重复办理，TIMELINE_ONLY 只进入总时间线。 */
export function archiveNodeEvents(events, nodeId) {
  return (events || []).filter(event => !nodeId || event.nodeId === nodeId)
    .slice().sort((a, b) => a.sequence - b.sequence || String(a.id).localeCompare(String(b.id)))
}

export function referenceTargets(spec, field, plan, schemas) {
  return plan.entities.filter(target => field.referenceEntity === target.entityCode
    || (schemas.find(schema => schema.code === target.entityCode)?.relations || [])
      .some(relation => relation.childEntity === spec.entityCode && relation.childField === field.code))
}
