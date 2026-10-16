import request from '@/utils/request'

const applicationPath = applicationId =>
  `/integration-applications/${encodeURIComponent(applicationId)}`

export const integrationApplicationApi = {
  list() {
    return request.get('/integration-applications')
  },
  create(data) {
    return request.post('/integration-applications', data)
  },
  updateStatus(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/status`, data)
  },
  rotateCredential(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/credentials/rotate`, data)
  },
  revokeCredential(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/credentials/revoke`, data)
  }
}
