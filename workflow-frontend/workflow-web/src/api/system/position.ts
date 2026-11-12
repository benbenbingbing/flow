import request from '@/utils/request'

export interface PositionQuery {
  keyword?: string
  applicableUnitType?: 'ORG' | 'DEPT' | 'ANY' | ''
  holderMode?: 'SINGLE' | 'MULTIPLE' | ''
  status?: 'ENABLED' | 'DISABLED' | ''
  pageNum?: number
  pageSize?: number
}

export interface PositionAssignmentQuery {
  keyword?: string
  positionCode?: string
  organizationUnitId?: string
  userId?: string
  pageNum?: number
  pageSize?: number
}

/** 分页查询职务定义。 */
export const getPositionPage = (params: PositionQuery) =>
  request.get('/system/position/page', { params })

/** 读取流程设计和任职表单可用的启用职务。 */
export const getEnabledPositions = (params: Record<string, any> = {}) =>
  request.get('/system/position/enabled', { params })

export const getPositionById = (id: string) =>
  request.get(`/system/position/${encodeURIComponent(id)}`)

export const createPosition = (data: Record<string, any>) =>
  request.post('/system/position', data)

export const updatePosition = (id: string, data: Record<string, any>) =>
  request.post(`/system/position/${encodeURIComponent(id)}/update`, data)

export const updatePositionStatus = (
  id: string,
  status: 'ENABLED' | 'DISABLED',
  revision: number
) => request.post(
  `/system/position/${encodeURIComponent(id)}/status`,
  { status, revision }
)

export const deletePosition = (id: string, revision: number) =>
  request.post(`/system/position/${encodeURIComponent(id)}/delete`, { revision })

/** 分页查询当前职务人员。 */
export const getPositionAssignmentPage = (params: PositionAssignmentQuery) =>
  request.get('/system/position/assignments/page', { params })

/** 读取指定组织节点的任职矩阵。 */
export const getOrganizationPositionAssignments = (unitId: string) =>
  request.get(
    `/system/org/${encodeURIComponent(unitId)}/position-assignments`
  )

/** 直接设置当前负责人，空 userId 表示清空，使用已读取集合阻止并发覆盖。 */
export const updateOrganizationLeader = (unitId: string, data: Record<string, any>) =>
  request.post(`/system/org/${encodeURIComponent(unitId)}/leader`, data)

/** 读取指定用户的当前职务关系。 */
export const getUserPositionAssignments = (userId: string) =>
  request.get(
    `/system/user/${encodeURIComponent(userId)}/position-assignments`
  )

/** 保存完整当前人员集合，可重复提交；不存在任命批次或独立预检。 */
export const setPositionMembers = (data: Record<string, any>) =>
  request.put('/system/position/assignments', data)

export const removePositionAssignment = (id: string, revision: number) =>
  request.delete(`/system/position/assignments/${encodeURIComponent(id)}`, { data: { revision } })

/** 流程设计器专用职务选项，不要求任职管理权限。 */
export const getProcessDesignPositionOptions = (
  params: Record<string, any> = {}
) => request.get('/process-design/position-options', { params })

export const getOrganizationBusinessLevels = () =>
  request.get('/process-design/organization-business-levels')

/** 使用运行时同一权威解析器执行相对组织职务试算。 */
export const previewRelativePosition = (data: Record<string, any>) =>
  request.post('/process-design/relative-position-preview', data, {
    silentError: true
  })
