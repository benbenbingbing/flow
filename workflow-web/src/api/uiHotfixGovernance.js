import request from '@/utils/request'

/** UI HOTFIX 申请、独立复核、取消与观察状态接口。 */
export const uiHotfixGovernanceApi = {
  apply(data) {
    return request.post('/ui-hotfix-requests', data)
  },

  review(id, approved, comment) {
    return request.post(`/ui-hotfix-requests/${id}/review`, {
      approved,
      comment
    })
  },

  cancel(id, reason) {
    return request.post(`/ui-hotfix-requests/${id}/cancel`, { reason })
  },

  get(id) {
    return request.get(`/ui-hotfix-requests/${id}`)
  },

  list(configType, configId) {
    return request.get('/ui-hotfix-requests', {
      params: { configType, configId }
    })
  }
}
