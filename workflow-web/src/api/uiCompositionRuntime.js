import request from '@/utils/request'

/**
 * “关联内容”运行时只接受宿主发布身份和来源记录标识。
 * 目标记录、固定筛选及发布版本都由服务端重新解析，前端不能自行拼接。
 */
export const uiCompositionRuntimeApi = {
  resolve(data = {}) {
    return request.post('/ui-runtime/view-compositions/resolve', data)
  },
  capabilities(actionContextToken) {
    return request.post('/ui-runtime/view-compositions/actions/capabilities', {
      actionContextToken
    })
  },
  linkCandidates(actionContextToken, action) {
    return request.post('/ui-runtime/view-compositions/actions/link-candidates', {
      actionContextToken,
      action
    })
  },
  executeAction(data = {}) {
    const operationId = String(data?.operationId || '').trim()
    return request.post(
      '/ui-runtime/view-compositions/actions',
      data,
      operationId
        ? { headers: { 'X-Trace-Id': operationId } }
        : undefined
    )
  }
}
