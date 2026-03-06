import request from '@/utils/request'

// 获取组列表
export const getGroupList = () => {
  return request.get('/system/group/list')
}

// 获取启用的组列表
export const getEnabledGroups = () => {
  return request.get('/system/group/enabled')
}

// 根据ID获取组
export const getGroupById = (id: string) => {
  return request.get(`/system/group/${id}`)
}

// 创建组
export const createGroup = (data: any) => {
  return request.post('/system/group', data)
}

// 更新组
export const updateGroup = (id: string, data: any) => {
  return request.put(`/system/group/${id}`, data)
}

// 删除组
export const deleteGroup = (id: string) => {
  return request.delete(`/system/group/${id}`)
}

// 更新组状态
export const updateGroupStatus = (id: string, status: string) => {
  return request.put(`/system/group/${id}/status?status=${status}`)
}

// 保存组用户
export const saveGroupUsers = (id: string, userIds: string[]) => {
  return request.put(`/system/group/${id}/users`, userIds)
}

// 获取用户列表
export const getUsers = () => {
  return request.get('/system/group/users')
}
