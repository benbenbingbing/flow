import request from '@/utils/request'

// 获取实体的表单列表
export const getFormsByEntity = (entityId: string) => {
  return request.get(`/entity-form/entity/${entityId}`)
}

// 根据ID获取表单
export const getFormById = (id: string) => {
  return request.get(`/entity-form/${id}`)
}

// 创建表单
export const createForm = (data: any) => {
  return request.post('/entity-form', data)
}

// 更新表单
export const updateForm = (id: string, data: any) => {
  return request.put(`/entity-form/${id}`, data)
}

// 删除表单
export const deleteForm = (id: string) => {
  return request.delete(`/entity-form/${id}`)
}

// 获取实体的字段列表
export const getEntityFields = (entityId: string) => {
  return request.get(`/entity-form/entity/${entityId}/fields`)
}

// 保存表单字段
export const saveFormFields = (id: string, fields: any[]) => {
  return request.put(`/entity-form/${id}/fields`, fields)
}

// 获取表单字段
export const getFormFields = (id: string) => {
  return request.get(`/entity-form/${id}/fields`)
}

// ==================== 流程节点表单绑定 ====================

// 获取流程的节点表单绑定
export const getNodeFormsByProcess = (processConfigId: string) => {
  return request.get(`/process-node-form/process/${processConfigId}`)
}

// 获取节点的表单绑定
export const getNodeFormByNodeId = (processConfigId: string, nodeId: string) => {
  return request.get(`/process-node-form/process/${processConfigId}/node/${nodeId}`)
}

// 保存节点表单绑定
export const saveNodeForm = (data: any) => {
  return request.post('/process-node-form', data)
}

// 删除节点表单绑定
export const deleteNodeForm = (id: string) => {
  return request.delete(`/process-node-form/${id}`)
}

// 批量保存节点表单绑定
export const saveNodeForms = (processConfigId: string, nodeForms: any[]) => {
  return request.put(`/process-node-form/process/${processConfigId}`, nodeForms)
}

// 获取实体的表单列表（用于绑定选择）
export const getEntityFormsForBind = (entityId: string) => {
  return request.get(`/process-node-form/entity/${entityId}/forms`)
}

// 设置默认表单
export const setDefaultForm = (formId: string) => {
  return request.put(`/entity-form/${formId}/default`)
}

// 复制表单
export const copyForm = (id: string) => {
  return request.post(`/entity-form/${id}/copy`)
}
