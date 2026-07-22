import request from '@/utils/request'

// 获取用户列表
export const getUserList = () => {
  return request.get('/system/user/list')
}

// 根据ID获取用户
export const getUserById = (id: string) => {
  return request.get(`/system/user/${id}`)
}

// 创建用户
export const createUser = (data: any) => {
  return request.post('/system/user', data)
}

// 更新用户
export const updateUser = (id: string, data: any) => {
  return request.post(`/system/user/${id}/update`, data)
}

// 删除用户
export const deleteUser = (id: string) => {
  return request.post(`/system/user/${id}/delete`)
}

// 更新用户状态
export const updateUserStatus = (id: string, status: string) => {
  return request.post(`/system/user/${id}/status?status=${status}`)
}

// 重置密码
export const resetPassword = (id: string) => {
  return request.post(`/system/user/${id}/reset-password`)
}

// 获取角色列表
export const getRoles = () => {
  return request.get('/system/user/roles')
}
