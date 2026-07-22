import request from '@/utils/request'

export const configMigrationApi = {
  getAssets(params = {}) {
    return request.get('/config-migration/assets', { params })
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

  getExportPackages() {
    return request.get('/config-migration/packages')
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

  getImports() {
    return request.get('/config-migration/imports')
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

  publishImport(id, itemIds) {
    return request.post(`/config-migration/imports/${id}/publish`, {
      itemIds: itemIds?.length ? itemIds : undefined
    })
  },

  rollbackImport(id) {
    return request.post(`/config-migration/imports/${id}/rollback`)
  }
}
