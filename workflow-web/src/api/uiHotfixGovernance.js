import request from '@/utils/request'

/** UI HOTFIX 观察状态接口。 */
export const uiHotfixGovernanceApi = {
  get(id) {
    return request.get(`/ui-hotfix-requests/${id}`)
  },

  list(configType, configId) {
    return request.get('/ui-hotfix-requests', {
      params: { configType, configId }
    })
  }
}
