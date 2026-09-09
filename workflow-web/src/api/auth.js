import request from '@/utils/request'

/**
 * 登录
 * @param {Object} data - 登录参数 {username, password}
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 获取当前登录用户信息
 */
export function getCurrentUser() {
  return request.get('/auth/current')
}

/**
 * 退出登录
 */
export function logout() {
  return request.post('/auth/logout', null, {
    skipAuthRefresh: true,
    silentError: true
  })
}

/**
 * 修改当前登录用户密码。成功后服务端撤销全部会话并清除刷新 Cookie，
 * 不返回新登录会话；调用方需清理本地登录态并引导用户使用新密码重新登录。
 */
export function changePassword(data) {
  return request.post('/auth/change-password', data)
}

/**
 * 获取当前登录用户权限码集合
 */
export function getPermissions() {
  return request.get('/auth/permissions')
}
