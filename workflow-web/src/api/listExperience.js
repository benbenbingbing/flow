import request from '@/utils/request'

const base = '/api/list-experience'

export const listExperienceApi = {
  analyzeIndexes(entityCode, listKey, data = {}) {
    return request.post(`${base}/${entityCode}/${listKey}/index-advice/analyze`, data)
  },
  indexes(entityCode, listKey) {
    return request.get(`${base}/${entityCode}/${listKey}/index-advice`)
  },
  applyIndex(id, expectedRevision) {
    return request.post(`${base}/index-advice/${id}/apply`, {
      expectedRevision,
      confirmed: true
    })
  },
  rejectIndex(id, expectedRevision, reason = '') {
    return request.post(`${base}/index-advice/${id}/reject`, {
      expectedRevision,
      reason
    })
  }
}
