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
