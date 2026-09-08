import request from '@/utils/request'
import { createGroupApi } from './groupApi.js'

const groupApi = createGroupApi(request)

// 获取组列表
export const getGroupList = () => {
  return groupApi.getGroupList()
}

// 获取启用的组列表
export const getEnabledGroups = () => {
  return groupApi.getEnabledGroups()
}

// 根据ID获取组
export const getGroupById = (id: string) => {
  return groupApi.getGroupById(id)
}

// 创建组
export const createGroup = (data: any) => {
  return groupApi.createGroup(data)
}

// 更新组
export const updateGroup = (id: string, data: any) => {
  return groupApi.updateGroup(id, data)
}

// 删除组
export const deleteGroup = (id: string) => {
  return groupApi.deleteGroup(id)
}

// 更新组状态
export const updateGroupStatus = (id: string, status: string) => {
  return groupApi.updateGroupStatus(id, status)
}

// 保存组用户
export const saveGroupUsers = (id: string, userIds: string[]) => {
  return groupApi.saveGroupUsers(id, userIds)
}

// 获取用户列表
export const getUsers = () => {
  return groupApi.getUsers()
}
