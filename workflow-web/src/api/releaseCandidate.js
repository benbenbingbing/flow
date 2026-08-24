import request from '@/utils/request'

export const releaseCandidateApi = {
  list() {
    return request.get('/release-candidates')
  },
  sources() {
    return request.get('/release-candidates/sources')
  },
  detail(id) {
    return request.get(`/release-candidates/${id}`)
  },
  create(data) {
    return request.post('/release-candidates', data)
  },
  preflight(id, expectedRevision) {
    return request.post(`/release-candidates/${id}/preflight`, { expectedRevision })
  },
  publish(id, data) {
    return request.post(`/release-candidates/${id}/publish`, data)
  },
  resume(id, data) {
    return request.post(`/release-candidates/${id}/resume`, data)
  },
  compensate(id, reason) {
    return request.post(`/release-candidates/${id}/compensate`, { reason })
  },
  report(id) {
    return request.get(`/release-candidates/${id}/report`, { responseType: 'blob' })
  }
}
