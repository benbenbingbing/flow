import request from '@/utils/request'

// 获取组织部门树
export const getOrgTree = (type?: string) => {
  return request.get('/system/org/tree', { params: { type } })
}

// 获取启用的组织部门平铺列表
export const getEnabledOrgList = () => {
  return request.get('/system/org/enabled')
}
