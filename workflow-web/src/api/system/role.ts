import request from '@/utils/request'

// 获取角色列表
export const getRoleList = () => {
  return request.get('/system/role/list')
}

// 获取启用的角色列表
export const getEnabledRoles = () => {
  return request.get('/system/role/enabled')
}

// 根据ID获取角色
export const getRoleById = (id: string) => {
  return request.get(`/system/role/${id}`)
}

// 创建角色
export const createRole = (data: any) => {
  return request.post('/system/role', data)
}

// 更新角色
export const updateRole = (id: string, data: any) => {
  return request.post(`/system/role/${id}/update`, data)
}

// 删除角色
export const deleteRole = (id: string) => {
  return request.post(`/system/role/${id}/delete`)
}

// 更新角色状态
export const updateRoleStatus = (id: string, status: string) => {
  return request.post(`/system/role/${id}/status?status=${status}`)
}

// 获取菜单树
export const getMenuTree = () => {
  return request.get('/system/role/menu-tree')
}

// 获取角色的菜单权限
export const getRoleMenus = (id: string) => {
  return request.get(`/system/role/${id}/menus`)
}

// 保存角色菜单权限
export const saveRoleMenus = (id: string, menuIds: string[]) => {
  return request.post(`/system/role/${id}/menus`, menuIds)
}
