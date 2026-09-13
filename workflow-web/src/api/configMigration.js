import request from '@/utils/request'

export const configMigrationApi = {
  getAssets(params = {}) {
    return request.get('/config-migration/assets', { params })
  },

  getAssetPage(params = {}) {
    return request.get('/config-migration/assets/page', { params })
  },

  getAsset(id) {
    return request.get(`/config-migration/assets/${id}`)
  },

  updateAssetMark(id, data) {
    return request.post(`/config-migration/assets/${id}/mark`, data)
  },

  exportPackage(data) {
    return request.post('/config-migration/packages/export', data)
  },

  getExportPackages(params = {}) {
    return request.get('/config-migration/packages', { params })
  },

  getExportPackagePage(params = {}) {
    return request.get('/config-migration/packages/page', { params })
  },

  downloadPackage(id) {
    return request.get(`/config-migration/packages/${id}/download`, {
      responseType: 'blob'
    })
  },

  uploadPackage(file, sourceEnvironment) {
    const formData = new FormData()
    formData.append('file', file)
    if (sourceEnvironment) formData.append('sourceEnvironment', sourceEnvironment)
    return request.post('/config-migration/imports', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  getImports(params = {}) {
    return request.get('/config-migration/imports', { params })
  },

  getImportPage(params = {}) {
    return request.get('/config-migration/imports/page', { params })
  },

  getImportOptions() {
    return request.get('/config-migration/imports/options')
  },

  getStats() {
    return request.get('/config-migration/stats')
  },

  getImportItems(id) {
    return request.get(`/config-migration/imports/${id}/items`)
  },

  analyzeImport(id) {
    return request.post(`/config-migration/imports/${id}/analyze`)
  },

  saveMappings(id, mappings) {
    return request.post(`/config-migration/imports/${id}/mappings`, { mappings })
  },

  compareImport(id) {
    return request.get(`/config-migration/imports/${id}/compare`)
  },

  publishImport(id) {
    return request.post(`/config-migration/imports/${id}/publish`)
  },

  rollbackImport(id) {
    return request.post(`/config-migration/imports/${id}/rollback`)
  }
}
