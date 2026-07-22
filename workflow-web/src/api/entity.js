import request from '@/utils/request'

/**
 * 实体定义管理API
 */
export const entityApi = {
  /**
   * 获取实体定义分页列表
   */
  getList(params = {}) {
    return request.get('/entity', { params })
  },

  /**
   * 获取全部实体定义，用于配置项下拉选择。
   */
  async getAll(params = {}) {
    const response = await request.get('/entity', {
      params: { pageNum: 1, pageSize: 1000, ...params }
    })
    if (Array.isArray(response)) return response
    if (Array.isArray(response?.records)) return response.records
    if (Array.isArray(response?.list)) return response.list
    if (Array.isArray(response?.data)) return response.data
    return []
  },

  /**
   * 根据ID获取实体定义
   */
  getById(id) {
    return request.get(`/entity/${id}`)
  },

  /**
   * 根据编码获取实体定义
   */
  getByCode(code) {
    return request.get(`/entity/code/${code}`)
  },

  /**
   * 创建实体定义
   */
  create(data) {
    return request.post('/entity', data)
  },

  /**
   * 更新实体定义
   */
  update(id, data) {
    return request.post(`/entity/${id}/update`, data)
  },

  /**
   * 删除实体定义
   */
  delete(id) {
    return request.post(`/entity/${id}/delete`)
  },

  /**
   * 发布实体定义
   */
  publish(id, data = {}) {
    return request.post(`/entity/${id}/publish`, data)
  },

  bindWorkflow(entityId, processDefinitionId) {
    return request.post(`/entity/${entityId}/workflow-binding/update`, { processDefinitionId })
  },

  unbindWorkflow(entityId) {
    return request.post(`/entity/${entityId}/workflow-binding/delete`)
  },

  updateLifecycleMode(entityId, lifecycleMode) {
    return request.post(`/entity/${entityId}/lifecycle-mode`, { lifecycleMode })
  },

  /**
   * 获取实体的表单列表
   */
  getEntityForms(entityId) {
    return request.get(`/entity-form/entity/${entityId}`)
  },

  /**
   * 保存实体表单配置
   */
  saveEntityForm(entityId, data) {
    return request.post('/entity-form', data)
  },

  /**
   * 获取表单字段
   */
  getFormFields(formId) {
    return request.get(`/entity-form/${formId}/fields`)
  }
}

/**
 * 实体数据管理API
 */
export const entityDataApi = {
  /**
   * 获取某实体的所有数据
   */
  getList(entityCode, params = {}) {
    return request.get(`/entity-data/entity/${entityCode}`, { params })
  },

  /**
   * 获取某实体的数据列表（带列表配置扩展字段）
   */
  getListWithConfig(entityCode, listKey, params = {}) {
    const queryParams = { ...params }
    if (listKey) queryParams.listKey = listKey
    return request.get(`/entity-data/entity/${entityCode}/list-with-config`, { params: queryParams })
  },

  /**
   * 根据ID获取数据
   */
  getById(id) {
    return request.get(`/entity-data/${id}`)
  },

  /**
   * 获取实体数据详情
   */
  getDetail(entityCode, id, listKey) {
    return request.get(`/entity-data/entity/${entityCode}/detail/${id}`, {
      params: listKey ? { listKey } : {}
    })
  },

  /**
   * 保存数据
   * @param data 数据对象
   * @param startProcess 是否同时发起流程
   */
  save(data, startProcess = false) {
    return request.post('/entity-data', { ...data, startProcess })
  },

  /**
   * 更新数据
   */
  update(entityCode, id, data, startProcess = false, listKey) {
    return request.post(`/entity-data/entity/${entityCode}/detail/${id}/update`,
      { ...data, startProcess },
      { params: listKey ? { listKey } : {} }
    )
  },

  /**
   * 删除数据
   */
  delete(entityCode, id, listKey) {
    return request.post(`/entity-data/entity/${entityCode}/detail/${id}/delete`, {
      params: listKey ? { listKey } : {}
    })
  },

  batchDelete(entityCode, ids, listKey) {
    return request.post(`/entity-data/entity/${entityCode}/batch-delete`, {
      ids,
      listKey
    })
  },

  /**
   * 导出实体数据（选中或全部）
   */
  exportData(entityCode, data) {
    return request.post(`/entity-data/entity/${entityCode}/export`, data, {
      responseType: 'blob'
    })
  }
}
