const TRUSTED_CONTEXT_KEYS = new Set([
  'idempotencykey',
  'datascopeplan',
  'permissionsummary',
  'trustedruntimecontext',
  'authenticateduser',
  'userid',
  'username',
  'tenantid',
  'orgid',
  'organizationid',
  'deptid',
  'departmentid',
  'entityid',
  'entitycode',
  'formid',
  'formkey',
  'listid',
  'listkey',
  'ownertype',
  'ownerid',
  'configtype',
  'configid',
  'serviceid',
  'operationcode',
  'bindingcode',
  'requestid',
  'releaseid',
  'releaseversion',
  'publishedreleaseid'
])

function normalizeContextKey(key) {
  return String(key)
    .replaceAll('_', '')
    .replaceAll('-', '')
    .toLowerCase()
}

/**
 * UI 事件只允许客户端提交业务上下文。配置身份、用户身份和发布版本由
 * 服务端根据已发布配置重新注入，避免把同一份可信元数据当成普通输入转发。
 */
export function sanitizeUiEventContext(context) {
  if (!context || typeof context !== 'object' || Array.isArray(context)) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(context).filter(
      ([key]) => !TRUSTED_CONTEXT_KEYS.has(normalizeContextKey(key))
    )
  )
}

export function buildUiEventExecutionPayload(data = {}) {
  return {
    ...data,
    context: sanitizeUiEventContext(data.context)
  }
}
