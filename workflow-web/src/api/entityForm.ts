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
  return request.post(`/entity-form/${id}/update`, data)
}

// 删除表单
export const deleteForm = (id: string) => {
  return request.post(`/entity-form/${id}/delete`)
}

// 获取实体的字段列表
export const getEntityFields = (entityId: string) => {
  return request.get(`/entity-form/entity/${entityId}/fields`)
}

// 保存表单字段
export const saveFormFields = (id: string, fields: any[]) => {
  return request.post(`/entity-form/${id}/fields`, fields)
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
  return request.post(`/process-node-form/${id}`)
}

// 批量保存节点表单绑定
export const saveNodeForms = (processConfigId: string, nodeForms: any[]) => {
  return request.post(`/process-node-form/process/${processConfigId}`, nodeForms)
}

// 获取实体的表单列表（用于绑定选择）
export const getEntityFormsForBind = (entityId: string) => {
  return request.get(`/process-node-form/entity/${entityId}/forms`)
}

// 设置默认表单
export const setDefaultForm = (formId: string) => {
  return request.post(`/entity-form/${formId}/default`)
}

// 仅更新表单初始化配置
export const updateFormInitConfig = (id: string, initConfig: Record<string, any> | null) => {
  return request.post(`/entity-form/${id}/init-config`, { initConfig })
}

// 复制表单
export const copyForm = (id: string) => {
  return request.post(`/entity-form/${id}/copy`)
}

export const patchFormMetadata = (id: string, data: any) => {
  return request.post(`/entity-form/${id}/patch`, data)
}

export const getFormNodes = (id: string) => {
  return request.get(`/entity-forms/${id}/nodes`)
}

export const createFormNode = (id: string, data: any) => {
  return request.post(`/entity-forms/${id}/nodes`, data)
}

export const patchFormNode = (formId: string, nodeId: string, data: any) => {
  return request.post(`/entity-forms/${formId}/nodes/${nodeId}/patch`, data)
}

export const deleteFormNode = (
  formId: string,
  nodeId: string,
  expectedRevision: number
) => {
  return request.post(`/entity-forms/${formId}/nodes/${nodeId}/delete`, {
    params: { expectedRevision }
  })
}

export const reorderFormNode = (formId: string, nodeId: string, data: any) => {
  return request.post(`/entity-forms/${formId}/nodes/${nodeId}/order`, data)
}

export const replaceFormNodes = (formId: string, nodes: any[]) => {
  return request.post(`/entity-forms/${formId}/nodes/update`, nodes)
}

export const getFormDiff = (id: string) => {
  return request.get(`/entity-forms/${id}/diff`)
}

export const publishForm = (id: string, description = '') => {
  return request.post(`/entity-forms/${id}/publish`, { description })
}

export const getFormReleases = (id: string) => {
  return request.get(`/entity-forms/${id}/releases`)
}

export const getFormRuntimeRelease = (
  id: string,
  releaseId?: string | null,
  version?: number | null
) => {
  return request.get(`/entity-forms/${id}/runtime-release`, {
    params: {
      releaseId: releaseId || undefined,
      version: version ?? undefined
    }
  })
}

export const activateFormRelease = (id: string, releaseId: string) => {
  return request.post(`/entity-forms/${id}/releases/${releaseId}/activate`)
}
