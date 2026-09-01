import { useUserStore } from '@/stores/user'

/**
 * 判断当前用户是否有按钮权限
 * @param {Object} btnConfig 按钮配置
 * @param {Array<string>} permissions 显式权限集合，不传时读取当前用户权限
 * @returns {boolean}
 */
export function hasButtonPermission(btnConfig, permissions) {
  if (!btnConfig) return false
  const userPerms = permissions || useUserStore().permissions || []
  const requiredPerm = btnConfig.perm
  if (!requiredPerm) return true
  return userPerms.includes(requiredPerm)
}

export function getActionCapability(row, buttonKey) {
  return row?.actionCapabilities?.[buttonKey] || {
    visible: true,
    enabled: true,
    reason: ''
  }
}

export function isActionVisible(row, buttonKey) {
  return getActionCapability(row, buttonKey).visible !== false
}

export function canExecuteAction(row, buttonKey) {
  const capability = getActionCapability(row, buttonKey)
  return capability.visible !== false && capability.enabled !== false
}

export function getActionCapabilityReason(row, buttonKey) {
  return getActionCapability(row, buttonKey).reason || ''
}

/**
 * 获取列表动作实际要操作的流程任务 ID。
 *
 * 实体列表返回某个动作能力时，能力中的 actionableTaskId 是服务端根据当前用户权限解析出的
 * 唯一可信目标。即使旧行数据仍带有 currentTaskId，也不能回退使用它，否则会把会签任务
 * 错认成其他办理人的任务。工作台等未接入动作能力的旧入口继续兼容 taskId/currentTaskId。
 *
 * @param {Object} row 列表行或工作台任务行
 * @param {string} buttonKey 动作能力键
 * @param {Object} options 解析约束
 * @param {boolean} options.requireActionCapability 是否要求服务端必须返回对应动作能力
 * @returns {string} 可操作任务 ID；能力存在但缺少目标时返回空字符串并由调用方阻断请求
 */
export function resolveActionableTaskId(row, buttonKey, options = {}) {
  const capabilities = row?.actionCapabilities
  const hasCapability = capabilities != null
    && typeof capabilities === 'object'
    && Object.prototype.hasOwnProperty.call(capabilities, buttonKey)
  if (options.requireActionCapability === true && !hasCapability) {
    return ''
  }
  const actionableTaskId = hasCapability
    ? capabilities[buttonKey]?.actionableTaskId
    : (row?.taskId || row?.currentTaskId)
  return actionableTaskId == null ? '' : String(actionableTaskId).trim()
}

export function getSelectionActionState(rows, buttonKey) {
  if (!Array.isArray(rows) || rows.length === 0) {
    return { enabled: false, reason: '请先选择数据' }
  }
  const denied = rows.find(row => !canExecuteAction(row, buttonKey))
  return denied
    ? {
        enabled: false,
        reason: getActionCapabilityReason(denied, buttonKey) || '选中数据中存在不可操作的数据'
      }
    : { enabled: true, reason: '' }
}
