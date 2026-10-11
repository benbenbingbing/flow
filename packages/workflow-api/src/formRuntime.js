/** 表单运行接口读取当前配置；参数中的旧发布坐标仅兼容内容指纹检查。 */
export function createFormRuntimeApi(request) {
  return {
    getFormRuntimeRelease(id, releaseId, version, releaseResolutionToken) {
      return request.get(`/entity-forms/${id}/runtime`, {
        params: { releaseId: releaseId || undefined, version: version ?? undefined, releaseResolutionToken: releaseResolutionToken || undefined }
      })
    },
    precheckFormFieldUnique(formId, data) {
      return request.post(`/entity-form/${formId}/unique-precheck`, data, { silentError: true })
    },
    getEntityFields(entityId) {
      return request.get(`/entity-form/entity/${entityId}/fields`)
    },
    /** 读取字段绑定的代码表；保存值使用 itemCode，展示使用 itemLabel。 */
    getDictionaryItems(dictCode) {
      return request.get(`/system/dict/item/tree/code/${encodeURIComponent(dictCode)}`)
    },
    getProgress(instanceId, taskId) {
      return request.get(`/process-instance/${encodeURIComponent(instanceId)}/progress`, { params: taskId ? { taskId } : {} })
    },
    getUserPage(params) {
      return request.get('/system/user/page', { params })
    },
    getEntityOptions(entityType, params) {
      return request.get(`/entity-selector/${encodeURIComponent(entityType)}`, { params })
    }
  }
}
