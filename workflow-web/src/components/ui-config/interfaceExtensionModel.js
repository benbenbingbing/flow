export const interfaceImplementationTypeOptions = [
  { label: '平台字典', value: 'DICTIONARY' },
  { label: '平台静态数据', value: 'STATIC_OPTIONS' },
  { label: '已注册 Provider', value: 'REGISTERED_PROVIDER' },
  { label: '运行时上下文', value: 'RUNTIME_CONTEXT' },
  { label: '结构化计算', value: 'STRUCTURED_COMPUTE' }
]

export const interfaceUsageOptions = [
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

const eventCodes = interfaceUsageOptions.slice(9).map(option => option.value)
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

export function parseInterfaceJson(value, fallback = {}) {
  if (value == null || value === '') return fallback
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export function parseInterfaceEditorJson(text, label) {
  try {
    return text?.trim() ? JSON.parse(text) : {}
  } catch {
    throw new Error(`${label}不是合法 JSON`)
  }
}

/**
 * 将管理目录、可用接口选项和历史数据源 DTO 投影为单一扩展接口。
 * 旧 service/operation 字段只用于读取已有草稿，不应被新保存载荷复制。
 */
export function normalizeInterfaceExtension(value = {}) {
  if (!value || typeof value !== 'object') value = {}
  const status = String(
    value.status || (value.enabled === false ? 'DISABLED' : 'ACTIVE')
  ).toUpperCase()
  return {
    ...value,
    id: value.id || value.extensionId || '',
    extensionId: value.extensionId || value.id || '',
    extensionType: 'INTERFACE',
    extensionKey: value.extensionKey || value.key || value.interfaceCode || value.sourceCode || '',
    displayName: value.displayName || value.interfaceName || value.sourceName || '',
    implementationType: value.implementationType || value.sourceType || '',
    interfaceKind: String(
      value.interfaceKind || value.kind || value.operationKind || 'READ'
    ).toUpperCase(),
    interfaceContextType: String(
      value.interfaceContextType || value.contextType || value.operationContextType || ''
    ).toUpperCase(),
    implementationConfig: parseInterfaceJson(
      value.implementationConfig ?? value.implementationConfigDocument
        ?? value.config ?? value.configDocument,
      {}
    ),
    executionPolicy: parseInterfaceJson(
      value.executionPolicy ?? value.executionPolicyDocument,
      {}
    ),
    inputSchema: parseInterfaceJson(
      value.inputSchema ?? value.inputSchemaDocument
        ?? value.operationInputSchemaDocument,
      {}
    ),
    outputSchema: parseInterfaceJson(
      value.outputSchema ?? value.outputSchemaDocument
        ?? value.operationOutputSchemaDocument,
      {}
    ),
    status,
    enabled: status === 'ACTIVE',
    legacyServiceId: value.legacyServiceId || value.serviceId || '',
    providerOperationCode: value.providerOperationCode || value.operationCode || ''
  }
}

export function normalizeInterfaceExtensions(values = []) {
  return (Array.isArray(values) ? values : [])
    .map(normalizeInterfaceExtension)
    .filter(item => item.extensionId)
}

/** 历史草稿绑定在页面加载时映射到迁移后的扩展 ID。 */
export function resolveInterfaceExtensionId(binding = {}, interfaces = []) {
  const direct = binding.extensionId || binding.interfaceExtensionId
  if (direct) return direct
  const legacyServiceId = String(binding.serviceId || binding.dataSourceId || '')
  const legacyOperationCode = String(
    binding.operationCode || binding.dataSourceOperationCode || ''
  )
  if (!legacyServiceId) return ''
  return normalizeInterfaceExtensions(interfaces).find(item =>
    String(item.legacyServiceId) === legacyServiceId
    && (!legacyOperationCode
      || String(item.providerOperationCode) === legacyOperationCode)
  )?.extensionId || ''
}

const mutableBindingOwnedKeys = new Set([
  'usage',
  'extensionId',
  'interfaceExtensionId',
  'inputMapping',
  'outputMapping',
  'clientPrevalidate',
  'sideEffectFree',
  // 迁移前身份字段与发布钉版信息不得进入下一次可变草稿。
  'serviceId',
  'sourceCode',
  'serviceName',
  'serviceRevision',
  'operationCode',
  'operationName',
  'dataSourceId',
  'dataSourceOperationCode',
  'legacyServiceId',
  'providerOperationCode',
  'executableSnapshot',
  'definitionHash'
])

/** 将历史接口绑定收敛为编辑态单 ID，并隔离只读身份/发布快照字段。 */
export function normalizeMutableInterfaceBinding(value = {}, interfaces = []) {
  const binding = value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : {}
  return {
    extensionId: resolveInterfaceExtensionId(binding, interfaces),
    inputMapping: binding.inputMapping || {},
    outputMapping: binding.outputMapping || {},
    clientPrevalidate: binding.clientPrevalidate === true,
    sideEffectFree: binding.sideEffectFree === true,
    extra: Object.fromEntries(Object.entries(binding)
      .filter(([key]) => !mutableBindingOwnedKeys.has(key)))
  }
}

export function isInterfaceUsageCompatible(value = {}, usage = '') {
  const item = normalizeInterfaceExtension(value)
  const normalizedUsage = String(usage || '').trim().toUpperCase()
  if (!interfaceUsageOptions.some(option => option.value === normalizedUsage)) {
    return false
  }
  if (readOnlyUsages.has(normalizedUsage) && item.interfaceKind !== 'READ') return false
  if (writeOnlyUsages.has(normalizedUsage) && item.interfaceKind !== 'WRITE') return false
  if (formDataUsages.has(normalizedUsage)) return item.interfaceContextType === 'FORM'
  if (listDataUsages.has(normalizedUsage)) return item.interfaceContextType === 'LIST'
  if (item.interfaceContextType === 'ENTITY') return eventCodes.includes(normalizedUsage)
  if (item.interfaceContextType === 'FORM') return formEventUsages.has(normalizedUsage)
  if (item.interfaceContextType === 'LIST') return listEventUsages.has(normalizedUsage)
  return false
}

export function interfacesForUsage(values = [], usage = '') {
  return normalizeInterfaceExtensions(values)
    .filter(item => item.enabled && isInterfaceUsageCompatible(item, usage))
}

/** 表单自定义按钮只允许无副作用读接口。 */
export function interfacesForEvent(values = [], eventCode = '') {
  const items = normalizeInterfaceExtensions(values)
  return String(eventCode || '').trim().toUpperCase() === 'FORM_BUTTON_CLICK'
    ? items.filter(item => item.interfaceKind === 'READ')
    : items
}

/**
 * 事件扩展统一使用 UiDataSourceProvider；随事件、宿主及已选实现说明实际约束。
 * 复用接口适用范围，避免提示把 ENTITY 默认绑定误说成 EntityInvocationContext，
 * 或要求平台内置实现也编写 Java Provider。
 */
export function eventInterfaceImplementationHelp(eventCode, ownerType, selectedInterface) {
  const event = String(eventCode || '').trim().toUpperCase()
  const owner = String(ownerType || '').trim().toUpperCase()
  const selected = selectedInterface ? normalizeInterfaceExtension(selectedInterface) : null
  const contexts = selected?.interfaceContextType
    ? [selected.interfaceContextType]
    : owner === 'ENTITY'
      ? [formEventUsages.has(event) && 'FORM', listEventUsages.has(event) && 'LIST'].filter(Boolean)
      : [owner].filter(Boolean)
  const contextNames = { FORM: 'FormInvocationContext', LIST: 'ListInvocationContext', ENTITY: 'EntityInvocationContext' }
  const kind = event === 'FORM_BUTTON_CLICK' || readOnlyUsages.has(event)
    ? '只读接口（READ）'
    : writeOnlyUsages.has(event) ? '写接口（WRITE）' : '读或写接口（READ / WRITE）'
  const implementation = interfaceImplementationTypeOptions.find(option =>
    option.value === selected?.implementationType && option.value !== 'REGISTERED_PROVIDER')
  const help = [
    event ? `当前事件 ${event} 使用${kind}。` : '选择触发事件后显示接口要求。',
    contexts.length ? `运行上下文：${contexts.map(context => contextNames[context] || context).join(' 或 ')}。` : '',
    implementation
      ? `当前选择“${implementation.label}”，由平台内置实现提供，无需编写 Java Provider。`
      : '自定义 Java 实现统一使用 com.workflow.contracts.entity.ui.spi.UiDataSourceProvider；实现 getCode()、getDisplayName() 和 execute(context, dataScopePlan, configuration, input)，加 @Component 注册。在“扩展管理”选择“已注册 Provider”，Provider 编码对应 getCode()。',
    !implementation && selected?.providerCode ? `当前 Provider 编码：${selected.providerCode}。` : '',
    !implementation && event ? `通过 context.usage() 识别 ${event}，输入参数映射传给 input，返回值用于结果回填。` : ''
  ]
  if (event === 'FORM_BUTTON_CLICK') {
    help.push('表单按钮接口只能读取、校验或计算，不得直接写库或产生外部副作用。')
  }
  help.push(event === 'ENTITY_SELECTED'
    ? '仅将所选实体属性回填到表单时可留空，直接配置“结果回填”。'
    : '留空时只执行字段映射，需配置“结果回填”。')
  return help.filter(Boolean).join(' ')
}

export function defaultInterfaceDebugUsage(value = {}) {
  const item = normalizeInterfaceExtension(value)
  const interfaceCode = String(item.extensionKey || '').split('.').at(-1).toUpperCase()
  if (isInterfaceUsageCompatible(item, interfaceCode)) return interfaceCode
  if (item.interfaceKind === 'WRITE') return 'DATA_UPDATE'
  return item.interfaceContextType === 'LIST' ? 'LIST_LOAD' : 'DETAIL_LOAD'
}

export function interfaceExecutionPolicy(value = {}) {
  return normalizeInterfaceExtension(value).executionPolicy
}

/** 编辑基础超时/缓存时保留迁移记录里的失败策略和后续扩展策略。 */
export function mergeInterfaceExecutionPolicy(value = {}, controls = {}) {
  const policy = parseInterfaceJson(value, {})
  return {
    ...policy,
    timeoutMs: controls.timeoutMs,
    cacheSeconds: controls.cacheSeconds,
    failurePolicy: policy.failurePolicy || 'FAIL'
  }
}

export function requiresInterfaceProvider(type) {
  return type === 'REGISTERED_PROVIDER'
}
