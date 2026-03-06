import request from '@/utils/request'

// 获取菜单树
export const getMenuTree = () => {
  return request.get('/system/menu/tree')
}

// 根据ID获取菜单
export const getMenuById = (id: string) => {
  return request.get(`/system/menu/${id}`)
}

// 创建菜单
export const createMenu = (data: any) => {
  return request.post('/system/menu', data)
}

// 更新菜单
export const updateMenu = (id: string, data: any) => {
  return request.put(`/system/menu/${id}`, data)
}

// 删除菜单
export const deleteMenu = (id: string) => {
  return request.delete(`/system/menu/${id}`)
}

// 更新菜单状态
export const updateStatus = (id: string, status: string) => {
  return request.put(`/system/menu/${id}/status?status=${status}`)
}

// 更新菜单显示状态
export const updateVisible = (id: string, visible: string) => {
  return request.put(`/system/menu/${id}/visible?visible=${visible}`)
}

// 更新菜单排序
export const updateSort = (menuIds: string[]) => {
  return request.put('/system/menu/sort', menuIds)
}

// 导出菜单
export const exportMenus = () => {
  return request.get('/system/menu/export')
}

// 导入菜单
export const importMenus = (data: any[]) => {
  return request.post('/system/menu/import', data)
}

// 获取菜单类型选项
export const getMenuTypeOptions = () => {
  return request.get('/system/menu/type-options')
}
