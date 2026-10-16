import request from '@/utils/request'

export interface SystemAuditQuery {
  pageNum?: number
  pageSize?: number
  startTime?: string
  endTime?: string
  module?: string
  operation?: string
  operator?: string
  result?: string
  riskLevel?: string
  targetType?: string
  targetId?: string
  traceId?: string
}

export const getSystemAuditLogs = (params: SystemAuditQuery) => {
  return request.get('/system/audit-logs', { params })
}

export const getSystemAuditLogDetail = (id: string) => {
  return request.get(`/system/audit-logs/${id}`)
}

export const exportSystemAuditLogs = (data: SystemAuditQuery) => {
  return request.post('/system/audit-logs/export', data, {
    responseType: 'blob'
  })
}
