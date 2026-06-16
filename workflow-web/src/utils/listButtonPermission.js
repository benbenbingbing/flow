import { useUserStore } from '@/stores/user'

/**
 * 判断当前用户是否有按钮权限
 * @param {Object} btnConfig 按钮配置
 * @returns {boolean}
 */
export function hasButtonPermission(btnConfig) {
  if (!btnConfig) return false
  const userStore = useUserStore()
  const userPerms = userStore.permissions || []
  const requiredPerm = btnConfig.perm
  if (!requiredPerm) return true
  return userPerms.includes(requiredPerm)
}
