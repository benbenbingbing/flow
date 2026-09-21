import {
  buildUiEventExecutionPayload
} from '@flow/workflow-core/ui-event-request'

/** 为指定 transport 创建接口集合，不在包内持有应用单例。 */
export function createUiConfigApi(request) {

const UI_CONFIG_DRAFT_DISCARD_ENDPOINTS = Object.freeze({
  FORM: id => `/entity-forms/${id}/discard-draft`,
  LIST: id => `/entity-list-config/${id}/discard-draft`
})

const uiConfigDraftApi = {
  /**
   * 以 revision、草稿哈希和当前发布版本三重校验，原子恢复到当前发布版本。
   */
  discard(configType, configId, preconditions) {
    const endpoint = UI_CONFIG_DRAFT_DISCARD_ENDPOINTS[
      String(configType || '').toUpperCase()
    ]
    if (!endpoint) {
      throw new Error(`不支持撤销 ${configType || '未知'} 配置草稿`)
    }
    return request.post(endpoint(configId), preconditions)
  }
}

const uiExtensionApi = {
  catalog() {
    return request.get('/ui-extensions/catalog')
  },
  list(params = {}) {
    return request.get('/ui-extensions', { params })
  },
  availableInterfaces(params) {
    return request.get('/ui-extensions/available-interfaces', { params })
  },
  create(data) {
    return request.post('/ui-extensions', data)
  },
  update(id, data) {
    return request.post(`/ui-extensions/${id}`, data)
  },
  remove(id, expectedRevision) {
    return request.post(`/ui-extensions/${id}/delete`, {
      expectedRevision
    })
  },
  preview(id, data) {
    return request.post(`/ui-extensions/${id}/preview`, data)
  }
}

/** 运行态只提交发布时固定的扩展接口 ID，不再透出后端方法。 */
const uiExtensionRuntimeApi = {
  execute(data) {
    return request.post('/ui-runtime/extensions/execute', data)
  }
}

const uiEventBindingApi = {
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
    return request.post(
      `/ui-runtime/events/${eventCode}/execute`,
      buildUiEventExecutionPayload(data, eventCode)
    )
  }
}

const formActionRuntimeApi = {
  resolve(data) {
    return request.post('/ui-runtime/form-actions/resolve', data)
  }
}

const uiComponentTemplateApi = {
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

return { uiConfigDraftApi, uiExtensionApi, uiExtensionRuntimeApi, uiEventBindingApi, formActionRuntimeApi, uiComponentTemplateApi }
}
