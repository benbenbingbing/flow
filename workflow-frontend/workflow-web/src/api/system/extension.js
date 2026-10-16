import request from '@/utils/request'

export const extensionCatalogApi = {
  manage(params = {}) {
    return request.get('/extension-catalog/manage', { params })
  },
  options(params = {}) {
    return request.get('/extension-catalog/options', { params })
  }
}

export const personResolverApi = {
  list(params = {}) {
    return request.get('/person-resolvers', { params })
  },
  configs() {
    return request.get('/person-resolvers/configs')
  },
  saveConfig(resolverCode, data) {
    return request.post(
      `/person-resolvers/configs/${encodeURIComponent(resolverCode)}`,
      data
    )
  }
}
