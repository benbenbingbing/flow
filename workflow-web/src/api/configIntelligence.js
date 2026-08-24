import request from '@/utils/request'

const baseUrl = '/api/config-intelligence'

export function listBlueprints(params) {
  return request({ url: `${baseUrl}/blueprints`, method: 'get', params })
}

export function getBlueprint(id) {
  return request({ url: `${baseUrl}/blueprints/${id}`, method: 'get' })
}

export function saveBlueprint(data) {
  return request({ url: `${baseUrl}/blueprints`, method: 'post', data })
}

export function publishBlueprint(id, expectedVersion) {
  return request({
    url: `${baseUrl}/blueprints/${id}/publish`,
    method: 'post',
    data: { expectedVersion }
  })
}

export function instantiateBlueprint(id, data) {
  return request({ url: `${baseUrl}/blueprints/${id}/instantiate`, method: 'post', data })
}

export function exportBlueprintPackage(id) {
  return request({ url: `${baseUrl}/blueprints/${id}/package`, method: 'get' })
}

export function verifyConfigurationPackage(data) {
  return request({ url: `${baseUrl}/packages/verify`, method: 'post', data })
}

export function getDependencies(params) {
  return request({ url: `${baseUrl}/dependencies`, method: 'get', params })
}

export function replaceDependencies(data) {
  return request({ url: `${baseUrl}/dependencies`, method: 'put', data })
}

export function analyzeConfigurationImpact(data) {
  return request({ url: `${baseUrl}/impact`, method: 'post', data })
}

export function analyzeConfigurationQuality(data) {
  return request({ url: `${baseUrl}/quality/analyze`, method: 'post', data })
}

export function getQualityHistory(params) {
  return request({ url: `${baseUrl}/quality/history`, method: 'get', params })
}
