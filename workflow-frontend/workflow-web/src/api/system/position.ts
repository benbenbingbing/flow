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
  activeOnly?: boolean
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

/** 分页查询任职事实，包含历史及撤销记录。 */
export const getPositionAssignmentPage = (params: PositionAssignmentQuery) =>
  request.get('/system/position/assignments/page', { params })

/** 读取指定组织节点的任职矩阵。 */
export const getOrganizationPositionAssignments = (unitId: string) =>
  request.get(
    `/system/org/${encodeURIComponent(unitId)}/position-assignments`
  )

/**
 * 组织负责人是 UNIT_LEADER 任职的快捷投影；不能再随组织 DTO 直写 leaderId。
 * 设置负责人时幂等键必填，清空 userId 表示撤销当前有效负责人。
 */
export const updateOrganizationLeader = (
  unitId: string,
  data: Record<string, any>,
  idempotencyKey: string
) => request.post(
  `/system/org/${encodeURIComponent(unitId)}/leader`,
  data,
  { headers: { 'Idempotency-Key': idempotencyKey } }
)

/** 读取指定用户的全部任职记录。 */
export const getUserPositionAssignments = (userId: string) =>
  request.get(
    `/system/user/${encodeURIComponent(userId)}/position-assignments`
  )

/**
 * 使用与正式提交相同的领域校验器执行只读预检。
 * 预检通过不替代提交事务内的再次校验。
 */
export const precheckPositionAssignments = (data: Record<string, any>) =>
  request.post('/system/position/assignments/precheck', data, {
    silentError: true
  })

/** 原子批量任命或转任，幂等键用于防止用户重复点击产生重复任职事实。 */
export const batchAssignPositions = (
  data: Record<string, any>,
  idempotencyKey: string
) => request.post('/system/position/assignments/batch', data, {
  headers: { 'Idempotency-Key': idempotencyKey }
})

export const revokePositionAssignment = (
  id: string,
  data: Record<string, any>
) => request.post(
  `/system/position/assignments/${encodeURIComponent(id)}/revoke`,
  data
)

export const updatePositionAssignmentPeriod = (
  id: string,
  data: Record<string, any>
) => request.post(
  `/system/position/assignments/${encodeURIComponent(id)}/update-period`,
  data
)

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
