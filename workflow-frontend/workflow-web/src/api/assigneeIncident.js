import request from '@/utils/request'

export const assigneeIncidentApi = {
  /** 根据状态、页码和每页条数查询事件，返回 records 与 total 供分页展示。 */
  list(params = {}) {
    return request.get('/assignee-incidents', { params })
  },
  metrics() {
    return request.get('/assignee-incidents/metrics')
  },
  detail(id) {
    return request.get(`/assignee-incidents/${id}`)
  },
  handle(id, data) {
    return request.post(`/assignee-incidents/${id}/handle`, data)
  }
}
