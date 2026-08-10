import request from '@/utils/request'

export const uiDataSourceApi = {
  catalog() {
    return request.get('/ui-data-sources/catalog')
  },
  list(params = {}) {
    return request.get('/ui-data-sources', { params })
  },
  availableOperations(params) {
    return request.get('/ui-data-sources/available-operations', { params })
  },
  create(data) {
    return request.post('/ui-data-sources', data)
  },
  update(id, data) {
    return request.post(`/ui-data-sources/${id}/update`, data)
  },
  remove(id, expectedRevision) {
    return request.post(`/ui-data-sources/${id}/delete`, {
      expectedRevision
    })
  },
  preview(id, data) {
    return request.post(`/ui-data-sources/${id}/preview`, data)
  },
  executeOperation(data) {
    return request.post('/ui-runtime/interface-operations/execute', data)
  },
  validateBinding(id, usage) {
    return request.post(`/ui-data-sources/${id}/bindings/${usage}/validate`)
  },
  operations(id) {
    return request.get(`/ui-data-sources/${id}/operations`)
  },
  previewOperation(id, operationCode, data) {
    return request.post(
      `/ui-data-sources/${id}/operations/${operationCode}/preview`,
      data
    )
  }
}

export const uiEventBindingApi = {
  catalog() {
    return request.get('/ui-event-bindings/catalog')
  },
  list(ownerType, ownerId) {
    return request.get('/ui-event-bindings', {
      params: { ownerType, ownerId }
    })
  },
  resolveDraft(ownerType, ownerId, eventCode) {
    return request.get('/ui-event-bindings/resolved-draft', {
      params: { ownerType, ownerId, eventCode }
    })
  },
  create(data) {
    return request.post('/ui-event-bindings', data)
  },
  update(id, data) {
    return request.post(`/ui-event-bindings/${id}/update`, data)
  },
  remove(id, expectedRevision) {
    return request.post(`/ui-event-bindings/${id}/delete`, {
      expectedRevision
    })
  },
  execute(eventCode, data) {
    return request.post(`/ui-runtime/events/${eventCode}/execute`, data)
  }
}

export const formActionRuntimeApi = {
  resolve(data) {
    return request.post('/ui-runtime/form-actions/resolve', data)
  }
}

export const uiComponentTemplateApi = {
  list(params = {}) {
    return request.get('/ui-component-templates', { params })
  },
  save(data) {
    return request.post('/ui-component-templates', data)
  },
  snapshot(id) {
    return request.get(`/ui-component-templates/${id}/snapshot`)
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
