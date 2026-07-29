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
  updateAccess(applicationId, data) {
    return request.put(`${applicationPath(applicationId)}/access`, data)
  },
  updateStatus(applicationId, data) {
    return request.patch(`${applicationPath(applicationId)}/status`, data)
  },
  listProcessContracts(applicationId) {
    return request.get(`${applicationPath(applicationId)}/process-contracts`)
  },
  updateProcessContracts(applicationId, data) {
    return request.put(`${applicationPath(applicationId)}/process-contracts`, data)
  },
  rotateCredential(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/credentials/rotate`, data)
  },
  revokeCredential(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/credentials/revoke`, data)
  }
}

export const integrationWebhookApi = {
  list(applicationId) {
    return request.get(`${applicationPath(applicationId)}/webhooks`)
  },
  create(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/webhooks`, data)
  },
  update(applicationId, endpointId, data) {
    return request.put(
      `${applicationPath(applicationId)}/webhooks/${encodeURIComponent(endpointId)}`,
      data
    )
  },
  rotateSecret(applicationId, endpointId, data) {
    return request.post(
      `${applicationPath(applicationId)}/webhooks/${encodeURIComponent(endpointId)}/secret/rotate`,
      data
    )
  },
  validate(applicationId, endpointId) {
    return request.post(
      `${applicationPath(applicationId)}/webhooks/${encodeURIComponent(endpointId)}/validate`
    )
  },
  listDeliveries(applicationId) {
    return request.get(`${applicationPath(applicationId)}/webhooks/deliveries`)
  },
  replay(applicationId, deliveryId, data) {
    return request.post(
      `${applicationPath(applicationId)}/webhooks/deliveries/${encodeURIComponent(deliveryId)}/replay`,
      data
    )
  }
}

export const integrationSecretApi = {
  list(applicationId) {
    return request.get(`${applicationPath(applicationId)}/secrets`)
  },
  create(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/secrets`, data)
  },
  rotate(applicationId, secretName, data) {
    return request.post(
      `${applicationPath(applicationId)}/secrets/${encodeURIComponent(secretName)}/rotate`,
      data
    )
  },
  revoke(applicationId, secretName, data) {
    return request.post(
      `${applicationPath(applicationId)}/secrets/${encodeURIComponent(secretName)}/revoke`,
      data
    )
  },
  destroy(applicationId, secretId) {
    return request.delete(
      `${applicationPath(applicationId)}/secrets/${encodeURIComponent(secretId)}`
    )
  }
}

export const integrationConnectorApi = {
  list(applicationId) {
    return request.get(`${applicationPath(applicationId)}/connectors`)
  },
  create(applicationId, data) {
    return request.post(`${applicationPath(applicationId)}/connectors`, data)
  },
  update(applicationId, configId, data) {
    return request.put(
      `${applicationPath(applicationId)}/connectors/${encodeURIComponent(configId)}`,
      data
    )
  },
  test(applicationId, configId, data) {
    return request.post(
      `${applicationPath(applicationId)}/connectors/${encodeURIComponent(configId)}/test`,
      data
    )
  }
}
