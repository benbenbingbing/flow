import request from '@/utils/request'

export const uiDataSourceApi = {
  catalog() {
    return request.get('/ui-data-sources/catalog')
  },
  list(params = {}) {
    return request.get('/ui-data-sources', { params })
  },
  create(data) {
    return request.post('/ui-data-sources', data)
  },
  update(id, data) {
    return request.post(`/ui-data-sources/${id}/update`, data)
  },
  remove(id, expectedRevision) {
    return request.post(`/ui-data-sources/${id}/delete`, {
      params: { expectedRevision }
    })
  },
  preview(id, data) {
    return request.post(`/ui-data-sources/${id}/preview`, data)
  },
  execute(id, data) {
    return request.post(`/ui-data-sources/${id}/execute`, data)
  },
  validateBinding(id, usage) {
    return request.post(`/ui-data-sources/${id}/bindings/${usage}/validate`)
  }
}

export const uiComponentTemplateApi = {
  list(params = {}) {
    return request.get('/ui-component-templates', { params })
  },
  save(data) {
    return request.post('/ui-component-templates', data)
  },
  versions(id) {
    return request.get(`/ui-component-templates/${id}/versions`)
  },
  createVersion(id, data) {
    return request.post(`/ui-component-templates/${id}/versions`, data)
  },
  upgrade(id, data) {
    return request.post(`/ui-component-templates/${id}/upgrade`, data)
  }
}

export const uiExtensionApi = {
  list(params = {}) {
    return request.get('/ui-extensions', { params })
  },
  create(data) {
    return request.post('/ui-extensions', data)
  },
  update(id, data) {
    return request.post(`/ui-extensions/${id}`, data)
  }
}
