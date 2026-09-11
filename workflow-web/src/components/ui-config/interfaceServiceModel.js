export const sourceTypeOptions = [
  { label: '平台字典', value: 'DICTIONARY' },
  { label: '平台静态数据', value: 'STATIC_OPTIONS' },
  { label: '平台注册能力', value: 'REGISTERED_PROVIDER' },
  { label: '运行时上下文', value: 'RUNTIME_CONTEXT' },
  { label: '结构化计算', value: 'STRUCTURED_COMPUTE' }
]

export const interfaceServiceUsageOptions = [
  { label: '表单初始化（FORM_INIT）', value: 'FORM_INIT' },
  { label: '字段选项（FIELD_OPTIONS）', value: 'FIELD_OPTIONS' },
  { label: '字段默认值（FIELD_DEFAULT）', value: 'FIELD_DEFAULT' },
  { label: '字段计算（FIELD_COMPUTE）', value: 'FIELD_COMPUTE' },
  { label: '子表行加载（SUBFORM_ROWS）', value: 'SUBFORM_ROWS' },
  { label: '列表查询（LIST_QUERY）', value: 'LIST_QUERY' },
  { label: '列表扩展列（LIST_COLUMN）', value: 'LIST_COLUMN' },
  { label: '加载后处理（AFTER_LOAD）', value: 'AFTER_LOAD' },
  { label: '提交前处理（BEFORE_SUBMIT）', value: 'BEFORE_SUBMIT' },
  { label: '列表加载（LIST_LOAD）', value: 'LIST_LOAD' },
  { label: '列表导出（LIST_EXPORT）', value: 'LIST_EXPORT' },
  { label: '详情加载（DETAIL_LOAD）', value: 'DETAIL_LOAD' },
  { label: '数据新增（DATA_CREATE）', value: 'DATA_CREATE' },
  { label: '数据更新（DATA_UPDATE）', value: 'DATA_UPDATE' },
  { label: '数据删除（DATA_DELETE）', value: 'DATA_DELETE' },
  { label: '批量删除（DATA_BATCH_DELETE）', value: 'DATA_BATCH_DELETE' },
  { label: '表单打开（FORM_OPEN）', value: 'FORM_OPEN' },
  { label: '表单保存（FORM_SAVE）', value: 'FORM_SAVE' },
  { label: '表单重置（FORM_RESET）', value: 'FORM_RESET' },
  { label: '字段变化（FIELD_CHANGE）', value: 'FIELD_CHANGE' },
  { label: '实体选择（ENTITY_SELECTED）', value: 'ENTITY_SELECTED' },
  { label: '字段按钮（FIELD_BUTTON_CLICK）', value: 'FIELD_BUTTON_CLICK' },
  { label: '子表加载（SUBFORM_LOAD）', value: 'SUBFORM_LOAD' },
  { label: '子表保存（SUBFORM_SAVE）', value: 'SUBFORM_SAVE' },
  { label: '工具栏按钮（TOOLBAR_BUTTON_CLICK）', value: 'TOOLBAR_BUTTON_CLICK' },
  { label: '行按钮（ROW_BUTTON_CLICK）', value: 'ROW_BUTTON_CLICK' },
  { label: '表单按钮（FORM_BUTTON_CLICK）', value: 'FORM_BUTTON_CLICK' }
]

// 保留纯页面事件目录，供仍按事件维度消费该模型的旧入口使用。
export const eventCodes = interfaceServiceUsageOptions
  .slice(9)
  .map(option => option.value)

const readOnlyUsages = new Set([
  'FORM_INIT', 'FIELD_OPTIONS', 'FIELD_DEFAULT', 'FIELD_COMPUTE',
  'SUBFORM_ROWS', 'LIST_QUERY', 'LIST_COLUMN', 'AFTER_LOAD',
  'LIST_LOAD', 'LIST_EXPORT', 'DETAIL_LOAD', 'FORM_OPEN',
  'SUBFORM_LOAD', 'ENTITY_SELECTED'
])
const writeOnlyUsages = new Set([
  'BEFORE_SUBMIT', 'DATA_CREATE', 'DATA_UPDATE', 'DATA_DELETE',
  'DATA_BATCH_DELETE', 'FORM_SAVE', 'SUBFORM_SAVE'
])
const formDataUsages = new Set([
  'FORM_INIT', 'FIELD_OPTIONS', 'FIELD_DEFAULT', 'FIELD_COMPUTE',
  'SUBFORM_ROWS', 'AFTER_LOAD', 'BEFORE_SUBMIT'
])
const listDataUsages = new Set(['LIST_QUERY', 'LIST_COLUMN'])
const formEventUsages = new Set([
  'DETAIL_LOAD', 'DATA_CREATE', 'DATA_UPDATE', 'FORM_OPEN', 'FORM_SAVE',
  'FORM_RESET', 'FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK',
  'SUBFORM_LOAD', 'SUBFORM_SAVE', 'FORM_BUTTON_CLICK'
])
const listEventUsages = new Set([
  'LIST_LOAD', 'LIST_EXPORT', 'DETAIL_LOAD', 'DATA_CREATE', 'DATA_UPDATE',
  'DATA_DELETE', 'DATA_BATCH_DELETE', 'TOOLBAR_BUTTON_CLICK',
  'ROW_BUTTON_CLICK'
])

/** 判断调用用途是否与操作的数据影响和业务上下文一致。 */
export function isInterfaceServiceUsageCompatible(operation = {}, usage = '') {
  const normalizedUsage = String(usage || '').trim().toUpperCase()
  if (!interfaceServiceUsageOptions.some(option => option.value === normalizedUsage)) {
    return false
  }

  const kind = String(operation.kind || 'READ').trim().toUpperCase()
  if (readOnlyUsages.has(normalizedUsage) && kind !== 'READ') return false
  if (writeOnlyUsages.has(normalizedUsage) && kind !== 'WRITE') return false

  const contextType = String(operation.contextType || '').trim().toUpperCase()
  if (formDataUsages.has(normalizedUsage)) return contextType === 'FORM'
  if (listDataUsages.has(normalizedUsage)) return contextType === 'LIST'
  if (contextType === 'ENTITY') return eventCodes.includes(normalizedUsage)
  if (contextType === 'FORM') return formEventUsages.has(normalizedUsage)
  if (contextType === 'LIST') return listEventUsages.has(normalizedUsage)
  return false
}

/**
 * 为管理端调试选择最接近真实绑定的默认用途。
 *
 * 操作编码与 usage 是两个独立维度，但平台内置操作通常复用同名编码；优先同名
 * 匹配可避免 FIELD_OPTIONS 等操作被错误地按 DETAIL_LOAD 执行。自定义操作再按
 * READ/WRITE 语义和业务上下文回退到常用用途。
 */
export function defaultInterfaceServiceDebugUsage(operation = {}) {
  const operationCode = String(operation.code || '').trim().toUpperCase()
  if (isInterfaceServiceUsageCompatible(operation, operationCode)) {
    return operationCode
  }
  if (String(operation.kind || '').toUpperCase() === 'WRITE') {
    return 'DATA_UPDATE'
  }
  return String(operation.contextType || '').toUpperCase() === 'LIST'
    ? 'LIST_LOAD'
    : 'DETAIL_LOAD'
}

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
  return type === 'REGISTERED_PROVIDER'
}

export function configurableEntities(entities) {
  if (!Array.isArray(entities)) return []
  return entities.filter(entity => entity?.storageMode !== 'SYSTEM')
}
