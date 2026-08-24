import request from '@/utils/request'

export const assigneeIncidentApi = {
  list(status) {
    return request.get('/assignee-incidents', { params: status ? { status } : {} })
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
