/** 表单运行接口只接受已发布坐标；编辑器草稿接口留在 PC。 */
export function createFormRuntimeApi(request) {
  return {
    getFormRuntimeRelease(id, releaseId, version, releaseResolutionToken) {
      return request.get(`/entity-forms/${id}/runtime-release`, {
        params: { releaseId: releaseId || undefined, version: version ?? undefined, releaseResolutionToken: releaseResolutionToken || undefined }
      })
    },
    precheckFormFieldUnique(formId, data) {
      return request.post(`/entity-form/${formId}/unique-precheck`, data, { silentError: true })
    },
    getEntityFields(entityId) {
      return request.get(`/entity-form/entity/${entityId}/fields`)
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
