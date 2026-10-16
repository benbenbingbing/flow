/**
 * 生成用户组保存请求体，只允许提交后端公开的可编辑字段。
 * 服务端生成字段和页面状态（尤其是空 id）不得进入新增请求。
 */
export function buildGroupPayload(source = {}) {
  return {
    groupName: normalizeText(source.groupName),
    groupCode: normalizeText(source.groupCode),
    description: normalizeText(source.description),
    sort: source.sort ?? 0,
    status: source.status ?? '0'
  }
}

/**
 * 按是否存在组 ID 明确选择新增或更新接口，避免两个不同签名的函数被统一调用。
 */
export async function submitGroupForm(
  formData,
  { createGroup, updateGroup }
) {
  const payload = buildGroupPayload(formData)
  const groupId = normalizeGroupId(formData?.id)
  if (groupId) {
    return updateGroup(groupId, payload)
  }
  return createGroup(payload)
}

/**
 * 规范化成员 ID：统一为去除首尾空白的字符串，并按首次出现顺序去重。
 */
export function normalizeGroupMemberIds(userIds) {
  if (!Array.isArray(userIds)) return []

  const normalized = []
  const seen = new Set()
  for (const userId of userIds) {
    const value = normalizeGroupId(userId)
    if (!value || seen.has(value)) continue
    seen.add(value)
    normalized.push(value)
  }
  return normalized
}

/** 成员顺序不代表业务差异，因此使用规范化后的集合判断是否发生变更。 */
export function hasGroupMemberChanges(currentUserIds, selectedUserIds) {
  const current = normalizeGroupMemberIds(currentUserIds)
  const selected = normalizeGroupMemberIds(selectedUserIds)
  if (current.length !== selected.length) return true

  const currentSet = new Set(current)
  return selected.some(userId => !currentSet.has(userId))
}

/**
 * 准备成员保存，并按成员 ID 集合计算新增/移除数量，使同人数换人也能准确提示。
 * 确认框取消会转换为普通结果，避免点击事件产生未处理的 Promise 拒绝。
 */
export async function prepareGroupMemberChange({
  currentUserIds,
  selectedUserIds,
  confirmChange
}) {
  const current = normalizeGroupMemberIds(currentUserIds)
  const selected = normalizeGroupMemberIds(selectedUserIds)
  const currentSet = new Set(current)
  const selectedSet = new Set(selected)
  const summary = {
    beforeCount: current.length,
    afterCount: selected.length,
    addedCount: selected.filter(userId => !currentSet.has(userId)).length,
    removedCount: current.filter(userId => !selectedSet.has(userId)).length
  }
  if (!hasGroupMemberChanges(current, selected)) {
    return {
      shouldSave: false,
      reason: 'unchanged',
      currentUserIds: current,
      userIds: selected,
      ...summary
    }
  }

  try {
    await confirmChange(summary)
  } catch (error) {
    return {
      shouldSave: false,
      reason: 'cancelled',
      error,
      currentUserIds: current,
      userIds: selected,
      ...summary
    }
  }

  return {
    shouldSave: true,
    reason: 'confirmed',
    currentUserIds: current,
    userIds: selected,
    ...summary
  }
}

/**
 * 串行处理单个用户组的状态切换。确认取消或接口失败都会恢复界面状态，
 * pending 集合则保证同一行在请求完成前不会重复提交。
 */
export async function runGroupStatusChange({
  row,
  pendingIds,
  confirmChange,
  updateStatus
}) {
  const groupId = normalizeGroupId(row?.id)
  const nextStatus = String(row?.status ?? '')
  const previousStatus = nextStatus === '0'
    ? '1'
    : nextStatus === '1'
      ? '0'
      : ''

  if (!groupId || !previousStatus) {
    if (previousStatus && row) row.status = previousStatus
    return { updated: false, reason: 'invalid' }
  }
  if (pendingIds.has(groupId)) {
    return { updated: false, reason: 'pending' }
  }

  pendingIds.add(groupId)
  try {
    await confirmChange({ groupId, nextStatus, previousStatus })
    await updateStatus(groupId, nextStatus)
    return { updated: true, reason: 'updated', status: nextStatus }
  } catch (error) {
    row.status = previousStatus
    return {
      updated: false,
      reason: 'reverted',
      error,
      status: previousStatus
    }
  } finally {
    pendingIds.delete(groupId)
  }
}

function normalizeGroupId(value) {
  return value == null ? '' : String(value).trim()
}

function normalizeText(value) {
  return value == null ? '' : String(value).trim()
}
