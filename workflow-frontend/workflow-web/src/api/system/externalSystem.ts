import request from '@/utils/request'

export type ExternalSystemStatus = '0' | '1'

export interface ExternalSystemParameter {
  id?: string
  nameZh: string
  nameEn: string
  value: string
  sortOrder?: number
}

export interface ExternalSystemPayload {
  systemName: string
  systemCode: string
  status: ExternalSystemStatus
  address: string
  description?: string
  parameters: ExternalSystemParameter[]
}

export type ExternalSystemUpdatePayload = Omit<ExternalSystemPayload, 'systemCode'> & {
  expectedVersion: number
}

export interface ExternalSystemRecord extends Omit<ExternalSystemPayload, 'parameters'> {
  id: string
  version: number
  parameters?: ExternalSystemParameter[]
  createTime?: string
  updateTime?: string
}

export interface ExternalSystemQuery {
  systemName?: string
  systemCode?: string
  status?: ExternalSystemStatus
  pageNum?: number
  pageSize?: number
}

/** 分页查询外部系统，响应由共享 request 层统一解包。 */
export const getExternalSystemPage = (params: ExternalSystemQuery) =>
  request.get('/system/external-system/page', { params })

/** 读取外部系统及其全部参数，用于原子编辑。 */
export const getExternalSystemById = (id: string) =>
  request.get(`/system/external-system/${encodeURIComponent(id)}`)

/** 原子创建外部系统和参数。 */
export const createExternalSystem = (data: ExternalSystemPayload) =>
  request.post('/system/external-system', data)

/** 原子更新外部系统和参数。 */
export const updateExternalSystem = (id: string, data: ExternalSystemUpdatePayload) =>
  request.post(`/system/external-system/${encodeURIComponent(id)}/update`, {
    systemName: data.systemName,
    status: data.status,
    address: data.address,
    description: data.description,
    parameters: data.parameters,
    expectedVersion: data.expectedVersion
  })

export const updateExternalSystemStatus = (
  id: string,
  status: ExternalSystemStatus,
  expectedVersion: number
) => request.post(
  `/system/external-system/${encodeURIComponent(id)}/status`,
  { status, expectedVersion }
)

export const deleteExternalSystem = (id: string, expectedVersion: number) =>
  request.post(`/system/external-system/${encodeURIComponent(id)}/delete`, {
    expectedVersion
  })
