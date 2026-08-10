export const sourceTypeOptions = [
  { label: '平台字典', value: 'DICTIONARY' },
  { label: '平台静态数据', value: 'STATIC_OPTIONS' },
  { label: '平台注册能力', value: 'REGISTERED_PROVIDER' },
  { label: 'HTTP 受控连接', value: 'INTEGRATION_CONNECTOR' },
  { label: '运行时上下文', value: 'RUNTIME_CONTEXT' },
  { label: '结构化计算', value: 'STRUCTURED_COMPUTE' }
]

export const eventCodes = [
  'LIST_LOAD', 'LIST_EXPORT', 'DETAIL_LOAD',
  'DATA_CREATE', 'DATA_UPDATE', 'DATA_DELETE', 'DATA_BATCH_DELETE',
  'FORM_OPEN', 'FORM_SAVE', 'FORM_RESET',
  'FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK',
  'SUBFORM_LOAD', 'SUBFORM_SAVE',
  'TOOLBAR_BUTTON_CLICK', 'ROW_BUTTON_CLICK', 'FORM_BUTTON_CLICK'
]

export function parseJson(document, fallback = {}) {
  if (!document) return fallback
  if (typeof document !== 'string') return document
  try {
    return JSON.parse(document)
  } catch {
    return fallback
  }
}

export function parseEditorJson(text, label) {
  try {
    return text?.trim() ? JSON.parse(text) : {}
  } catch {
    throw new Error(`${label}不是合法 JSON`)
  }
}

export function serviceOperations(service = {}) {
  const operations = parseJson(service.operationsDocument, [])
  return Array.isArray(operations) ? operations : []
}

export function executionPolicy(service = {}) {
  return parseJson(service.executionPolicyDocument, {})
}

export function requiresProvider(type) {
  return ['REGISTERED_PROVIDER', 'INTEGRATION_CONNECTOR'].includes(type)
}

export function configurableEntities(entities) {
  if (!Array.isArray(entities)) return []
  return entities.filter(entity => entity?.storageMode !== 'SYSTEM')
}
