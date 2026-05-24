import request from '@/utils/request'

/**
 * 实体定义管理API
 */
export const entityApi = {
  /**
   * 获取所有实体定义
   */
  getList() {
    return request.get('/entity')
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
    return request.put(`/entity/${id}`, data)
  },

  /**
   * 删除实体定义
   */
  delete(id) {
    return request.delete(`/entity/${id}`)
  },

  /**
   * 发布实体定义
   */
  publish(id) {
    return request.post(`/entity/${id}/publish`)
  },

  /**
   * 绑定流程
   */
  bindProcess(entityId, processId) {
    return request.post(`/entity/${entityId}/bind-process/${processId}`)
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
  update(entityCode, id, data) {
    return request.put(`/entity-data/entity/${entityCode}/detail/${id}`, data)
  },

  /**
   * 删除数据
   */
  delete(id) {
    return request.delete(`/entity-data/${id}`)
  }
}
