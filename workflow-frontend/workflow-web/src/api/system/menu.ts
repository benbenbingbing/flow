import request from '@/utils/request'

// 获取菜单树
export const getMenuTree = () => {
  return request.get('/system/menu/tree')
}

/** 管理页按直属下级分页，关键词按全菜单名称、路由和权限码搜索。 */
export const getMenuManagementPage = (params: {
  parentId?: string
  pageNum?: number
  pageSize?: number
  keyword?: string
  type?: string
  excludeId?: string
  parentCandidates?: boolean
}) => request.get('/system/menu/page', { params })

/** 读取真实父链用于面包屑、深层定位及上级回显。 */
export const getMenuAncestors = (id: string) => {
  return request.get(`/system/menu/${encodeURIComponent(id)}/ancestors`)
}

/** 排序只提交目标数值，避免列表快照覆盖菜单元数据。 */
export const updateMenuSortValue = (id: string, sort: number) => {
  return request.post(`/system/menu/${encodeURIComponent(id)}/sort`, { sort })
}

// 分页获取指定父菜单下的子菜单
export const getMenuChildren = (parentId: string, pageNum = 1, pageSize = 10) => {
  return request.get('/system/menu/children', { params: { parentId, pageNum, pageSize } })
}

// 获取指定节点为根的完整子树（不分页）
export const getMenuSubtree = (parentId: string) => {
  return request.get('/system/menu/subtree', { params: { parentId } })
}

// 获取运行态侧栏菜单树
export const getSidebarMenuTree = () => {
  return request.get('/system/menu/sidebar-tree')
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
  return request.post(`/system/menu/${id}/update`, data)
}

// 删除菜单
export const deleteMenu = (id: string) => {
  return request.post(`/system/menu/${id}/delete`)
}

// 更新菜单状态
export const updateStatus = (id: string, status: string) => {
  return request.post(`/system/menu/${id}/status?status=${status}`)
}

// 更新菜单显示状态
export const updateVisible = (id: string, visible: string) => {
  return request.post(`/system/menu/${id}/visible?visible=${visible}`)
}

// 更新菜单排序
export const updateSort = (menuIds: string[]) => {
  return request.post('/system/menu/sort', menuIds)
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

// 根据实体编码获取可用的按钮权限码集合
export const getPermsByEntityCode = (entityCode: string) => {
  return request.get('/system/menu/perms', { params: { entityCode } })
}

export const getEntityPermissionOptions = (entityCode: string) => {
  return request.get('/system/menu/entity-permission-options', { params: { entityCode } })
}
