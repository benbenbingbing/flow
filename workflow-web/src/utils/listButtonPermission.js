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
