import request from '@/utils/request'

// 获取组织部门树
export const getOrgTree = (type?: string) => {
  return request.get('/system/org/tree', { params: { type } })
}

// 获取启用的组织部门平铺列表
export const getEnabledOrgList = () => {
  return request.get('/system/org/enabled')
}

/** 读取组织管理专用的启用业务层级，不要求通用字典查看权限。 */
export const getOrganizationBusinessLevelOptions = () => {
  return request.get('/system/org/business-level-options')
}

/** 管理页及上级选择器使用有界分页，关键词搜索包含指定节点及其后代。 */
export const getOrganizationPage = (params: {
  parentId?: string
  pageNum?: number
  pageSize?: number
  keyword?: string
  type?: string
  excludeId?: string
}) => request.get('/system/org/page', { params })

/** 按真实父链回显路径，支持定位尚未展开或尚未加载到分页中的节点。 */
export const getOrganizationAncestors = (id: string) => {
  return request.get(`/system/org/${encodeURIComponent(id)}/ancestors`)
}

/** 树展开读取当前节点的全部直属下级，不分页、不递归读取后代。 */
export const getOrganizationChildren = (params: { parentId?: string, type?: string, excludeId?: string }) =>
  request.get('/system/org/children', { params })

/** 选择器搜索结果按列表分页，只返回启用且符合类型的候选。 */
export const getOrganizationSelectionPage = (params: {
  parentId?: string, pageNum?: number, pageSize?: number, keyword?: string, type?: string
}) => request.get('/system/org/options/page', { params })

/** 已选值按需回显，单次最多 100 个；流程定义使用 code，管理表单使用 id。 */
export const resolveOrganizationSelection = (values: string[], valueKey = 'id') =>
  request.get('/system/org/options/resolve', { params: { values: values.join(','), valueKey } })
