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
  'releaseresolutiontoken',
  'viewcompositiontraversaltoken',
  'publishedreleaseid'
])

// 审批表单按钮会把任务和流程实例作为服务端核验身份；其他既有 UI 事件仍可
// 把同名字段作为普通业务上下文，不能扩大公共清洗器的破坏范围。
const FORM_BUTTON_TRUSTED_CONTEXT_KEYS = new Set([
  'taskid',
  'processinstanceid'
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
export function sanitizeUiEventContext(context, eventCode = '') {
  if (!context || typeof context !== 'object' || Array.isArray(context)) {
    return {}
  }
  const formButton = String(eventCode || '').toUpperCase()
    === 'FORM_BUTTON_CLICK'
  return Object.fromEntries(
    Object.entries(context).filter(
      ([key]) => {
        const normalized = normalizeContextKey(key)
        return !TRUSTED_CONTEXT_KEYS.has(normalized)
          && !(formButton
            && FORM_BUTTON_TRUSTED_CONTEXT_KEYS.has(normalized))
      }
    )
  )
}

export function buildUiEventExecutionPayload(data = {}, eventCode = '') {
  return {
    ...data,
    context: sanitizeUiEventContext(data.context, eventCode)
  }
}
