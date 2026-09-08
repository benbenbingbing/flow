export const EMBED_FIELD_TYPES = Object.freeze([
  'TEXT',
  'NUMBER',
  'BOOLEAN',
  'DATE',
  'DATETIME',
  'TIME',
  'SELECT',
  'MULTI_SELECT'
])

export const EMBED_QUERY_OPERATORS = Object.freeze([
  'EQ',
  'CONTAINS',
  'IN',
  'BETWEEN',
  'GT',
  'GTE',
  'LT',
  'LTE'
])

export const EMBED_CAPABILITIES = Object.freeze([
  'LIST_QUERY',
  'SELECTION_RETURN',
  'RECORD_VIEW',
  'RECORD_CREATE',
  'ACTION_EXECUTE'
])

const FIELD_TYPES = new Set(EMBED_FIELD_TYPES)
const QUERY_OPERATORS = new Set(EMBED_QUERY_OPERATORS)
const CAPABILITIES = new Set(EMBED_CAPABILITIES)
const FIELD_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,127}$/
const RECORD_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/
const ACTION_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_.-]{0,127}$/
const RESOURCE_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}$/
const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype'])
const MAX_FIELDS = 100
const MAX_OPTIONS = 200
const MAX_ACTIONS = 100
const MAX_RECORDS_PER_PAGE = 100
// selection.changed 的值边界必须与 OpenAPI ClientValue 完全一致。
const MAX_CLIENT_VALUE_ITEMS = 100
const MAX_STRING_LENGTH = 100000
const MAX_FILTER_STRING_LENGTH = 2048

export class EmbedSchemaError extends Error {
  constructor(message, errorCode = 'EMBED_SCHEMA_INVALID') {
    super(message)
    this.name = 'EmbedSchemaError'
    this.errorCode = errorCode
  }
}

function isPlainRecord(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

function own(record, key) {
  return Object.prototype.hasOwnProperty.call(record || {}, key)
}

function asArray(value) {
  return Array.isArray(value) ? value : []
}

function clampInteger(value, fallback, min, max) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.min(max, Math.max(min, Math.floor(number)))
}

function safeText(value, fallback = '', maxLength = 256) {
  if (value === null || value === undefined) return fallback
  const text = String(value).trim()
  return text ? text.slice(0, maxLength) : fallback
}

function normalizeCode(value, pattern = FIELD_CODE_PATTERN) {
  const code = safeText(value, '', 128)
  if (!pattern.test(code) || DANGEROUS_KEYS.has(code)) return ''
  return code
}

function normalizeFieldType(value) {
  const type = safeText(value, '', 32).toUpperCase()
  return FIELD_TYPES.has(type) ? type : ''
}

/**
 * Bootstrap 来自会话快照，但前端仍按安全默认值收敛展示方式，避免旧版本或
 * 异常响应重新露出 Dialog 遮罩。
 */
export function normalizeEmbedFormPresentation(value) {
  return safeText(value, '', 32) === 'dialog'
    ? 'dialog'
    : 'seamless'
}

function normalizeOptionValue(value) {
  if (typeof value === 'string') return value.slice(0, 256)
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'boolean') return value
  return undefined
}

function normalizeOptions(rawOptions) {
  const options = []
  const seen = new Set()
  for (const item of asArray(rawOptions).slice(0, MAX_OPTIONS)) {
    const source = isPlainRecord(item) ? item : { label: item, value: item }
    const value = normalizeOptionValue(source.value)
    if (value === undefined) continue
    const identity = `${typeof value}:${String(value)}`
    if (seen.has(identity)) continue
    seen.add(identity)
    options.push(Object.freeze({
      label: safeText(source.label ?? value, String(value), 256),
      value
    }))
  }
  return Object.freeze(options)
}

function allowedOperators(type) {
  if (type === 'TEXT') return new Set(['EQ', 'CONTAINS'])
  if (type === 'SELECT' || type === 'BOOLEAN') return new Set(['EQ', 'IN'])
  if (type === 'MULTI_SELECT') return new Set(['IN'])
  return new Set(['EQ', 'BETWEEN', 'GT', 'GTE', 'LT', 'LTE'])
}

function normalizeOperator(value, type) {
  const operator = safeText(value, '', 16).toUpperCase()
  return QUERY_OPERATORS.has(operator) && allowedOperators(type).has(operator)
    ? operator
    : ''
}

/**
 * 将 External Schema 字段压缩到固定白名单。未知类型和危险字段码直接丢弃，
 * 运行时绝不根据服务端文本解析组件名、Provider 或任意请求地址。
 */
export function normalizeEmbedField(rawField, { filter = false } = {}) {
  if (!isPlainRecord(rawField)) return null
  const code = normalizeCode(rawField.code)
  const type = normalizeFieldType(rawField.type)
  if (!code || !type) return null
  const operator = filter ? normalizeOperator(rawField.operator, type) : undefined
  if (filter && !operator) return null

  const result = {
    code,
    label: safeText(rawField.label ?? code, code, 256),
    type,
    options: ['SELECT', 'MULTI_SELECT'].includes(type)
      ? normalizeOptions(rawField.options)
      : Object.freeze([])
  }
  if (filter) result.operator = operator
  else {
    result.width = clampInteger(rawField.width, 0, 0, 600)
    // V1 明确禁止客户端排序；即使 DTO 意外返回 true 也保持关闭。
    result.sortable = false
  }
  return Object.freeze(result)
}

function normalizeSelection(rawSelection, columnCodes) {
  const source = isPlainRecord(rawSelection) ? rawSelection : {}
  const candidate = safeText(source.mode, 'NONE', 24).toUpperCase()
  const returnableFields = []
  const returnableCodes = new Set()
  for (const rawCode of asArray(source.returnableFields).slice(0, MAX_FIELDS)) {
    const code = normalizeCode(rawCode)
    if (!code || !columnCodes.has(code) || returnableCodes.has(code)) continue
    returnableCodes.add(code)
    returnableFields.push(code)
  }
  const valueFieldCandidate = normalizeCode(source.valueField) || 'id'
  const valueFieldAllowed = valueFieldCandidate === 'id'
    || returnableCodes.has(valueFieldCandidate)
  const mode = valueFieldAllowed && ['NONE', 'SINGLE', 'MULTIPLE'].includes(candidate)
    ? candidate
    : 'NONE'
  return Object.freeze({
    mode,
    valueField: valueFieldAllowed ? valueFieldCandidate : 'id',
    returnableFields: Object.freeze(returnableFields)
  })
}

function normalizePagination(rawPagination) {
  const source = isPlainRecord(rawPagination) ? rawPagination : {}
  return Object.freeze({
    allowTotal: source.allowTotal === true,
    maxPageSize: clampInteger(source.maxPageSize, 100, 1, 100)
  })
}

function normalizeActionDescriptor(rawAction) {
  if (!isPlainRecord(rawAction)) return null
  const key = normalizeCode(rawAction.key, ACTION_KEY_PATTERN)
  const placement = safeText(rawAction.placement, '', 16).toUpperCase()
  const kind = safeText(rawAction.kind, '', 16).toUpperCase()
  const transport = safeText(rawAction.transport, '', 24).toUpperCase()
  const recordMode = safeText(rawAction.recordMode, '', 16).toUpperCase()
  const selectionMode = safeText(rawAction.selectionMode, '', 16).toUpperCase()
  const viewAction = key === 'view'
    && placement === 'ROW'
    && kind === 'NAVIGATION'
    && transport === 'LOCAL_FORM'
    && recordMode === 'CURRENT'
    && selectionMode === 'NONE'
  const createAction = key === 'create'
    && placement === 'TOOLBAR'
    && kind === 'NAVIGATION'
    && transport === 'LOCAL_FORM'
    && recordMode === 'NONE'
    && selectionMode === 'NONE'
  if (!viewAction && !createAction) {
    return null
  }
  return Object.freeze({
    key,
    label: safeText(rawAction.label ?? key, key, 256),
    placement,
    kind,
    transport,
    recordMode,
    selectionMode,
    // V1 只读列表不执行表单/动作 Schema，避免把任意嵌套配置带入组件层。
    dataSchema: null,
    inputSchema: null,
    requiresRecordVersion: rawAction.requiresRecordVersion === true,
    idempotencyRequired: rawAction.idempotencyRequired === true,
    enabled: rawAction.enabled !== false,
    disabledReason: safeText(rawAction.disabledReason, '', 512) || null
  })
}

/** 规范化第 11.4 节的 list 对象，而不是内部 EntityListSchemaDTO。 */
export function normalizeEmbedListSchema(rawList = {}) {
  const source = isPlainRecord(rawList?.list) ? rawList.list : rawList
  if (!isPlainRecord(source)) {
    throw new EmbedSchemaError('Embed 列表 Schema 无效')
  }
  const columns = []
  const columnCodes = new Set()
  for (const rawColumn of asArray(source.columns).slice(0, MAX_FIELDS)) {
    const column = normalizeEmbedField(rawColumn)
    if (!column || columnCodes.has(column.code)) continue
    columnCodes.add(column.code)
    columns.push(column)
  }
  if (!columns.length) {
    throw new EmbedSchemaError('Embed 列表没有可展示字段', 'EMBED_SCHEMA_COLUMNS_EMPTY')
  }

  const filters = []
  const filterCodes = new Set()
  for (const rawFilter of asArray(source.filters).slice(0, MAX_FIELDS)) {
    const filter = normalizeEmbedField(rawFilter, { filter: true })
    if (!filter || filterCodes.has(filter.code)) continue
    filterCodes.add(filter.code)
    filters.push(filter)
  }

  return Object.freeze({
    selection: normalizeSelection(source.selection, columnCodes),
    pagination: normalizePagination(source.pagination),
    columns: Object.freeze(columns),
    filters: Object.freeze(filters)
  })
}

function normalizeView(rawView, { requireName = false, requireRevision = true } = {}) {
  const source = isPlainRecord(rawView) ? rawView : {}
  const key = safeText(source.key, '', 256)
  const surfaceType = safeText(source.surfaceType, '', 16).toUpperCase()
  if (!key || /[/?#\\]/.test(key) || !['LIST', 'FORM'].includes(surfaceType)) {
    throw new EmbedSchemaError('Embed View 无效', 'EMBED_SCHEMA_VIEW_INVALID')
  }
  const revision = requireRevision ? Number(source.revision) : undefined
  if (requireRevision && (!Number.isSafeInteger(revision) || revision < 1)) {
    throw new EmbedSchemaError('Embed Schema revision 无效', 'EMBED_SCHEMA_VIEW_INVALID')
  }
  const entryMode = requireName
    ? safeText(source.entryMode, '', 16).toUpperCase()
    : undefined
  if (requireName && !['LIST', 'CREATE', 'VIEW'].includes(entryMode)) {
    throw new EmbedSchemaError('Embed View entryMode 无效', 'EMBED_SCHEMA_VIEW_INVALID')
  }
  return Object.freeze({
    key,
    name: requireName ? safeText(source.name ?? key, key, 256) : undefined,
    surfaceType,
    ...(requireRevision ? { revision } : {}),
    entryMode
  })
}

function normalizeDateTime(value) {
  const text = safeText(value, '', 64)
  return text && Number.isFinite(Date.parse(text)) ? text : ''
}

function normalizeResourceId(value, required = false) {
  const id = safeText(value, '', 256)
  if (!id) return required ? null : ''
  return RESOURCE_ID_PATTERN.test(id) ? id : null
}

function normalizeReleaseVersion(value, required = false) {
  if (value === null || value === undefined || value === '') {
    return required ? null : undefined
  }
  const version = Number(value)
  return Number.isSafeInteger(version) && version > 0 ? version : null
}

function normalizeOpaqueRuntimeCoordinate(value, maxLength = 4096) {
  const text = safeText(value, '', maxLength)
  return text && !/[\u0000-\u001F\u007F\s]/.test(text) ? text : ''
}

function normalizeNativeRuntimeContext(value) {
  if (!isPlainRecord(value)
    || Object.keys(value).some(key => DANGEROUS_KEYS.has(key))) {
    return Object.freeze({})
  }
  // 内容已经由服务端从 Session 上下文和发布绑定恢复；这里只隔离顶层对象，
  // 不按字段/组件重新投影，避免未来新增组件的合法结构被旧 Embed 客户端截断。
  return Object.freeze({ ...value })
}

function normalizeRuntimeIdentityValues(values, maxItems = 1000) {
  const result = []
  for (const value of asArray(values).slice(0, maxItems)) {
    const candidate = typeof value === 'string'
      ? value
      : value?.roleCode ?? value?.permissionCode ?? value?.code
    const normalized = safeText(candidate, '', 256)
    if (!normalized || result.includes(normalized)) continue
    result.push(normalized)
  }
  return Object.freeze(result)
}

/**
 * Bootstrap 只提供服务端从固定 Session 快照恢复出的原生 Flow 坐标。
 * 坐标不是页面结构；iframe 随后通过 Flow 标准 runtime URL 读取同一份
 * LIST/FORM 发布快照，因而新增字段、渲染器或数据源无需再修改 Embed。
 */
export function normalizeEmbedNativeRuntimeTarget(rawTarget, view) {
  if (!isPlainRecord(rawTarget)) {
    throw new EmbedSchemaError('Embed 原生运行时坐标缺失', 'EMBED_BOOTSTRAP_TARGET_INVALID')
  }
  const rawForm = isPlainRecord(rawTarget.form) ? rawTarget.form : rawTarget
  const rawFormRelease = isPlainRecord(rawTarget.formRelease)
    ? rawTarget.formRelease
    : rawForm
  const rawListRelease = isPlainRecord(rawTarget.listRelease)
    ? rawTarget.listRelease
    : rawTarget
  const entityCode = normalizeCode(rawTarget.entityCode ?? rawForm.entityCode)
  const listKey = normalizeResourceId(
    rawTarget.listKey ?? rawListRelease.listKey,
    view.surfaceType === 'LIST'
  )
  const listReleaseId = normalizeResourceId(
    rawListRelease.listReleaseId ?? rawListRelease.releaseId,
    view.surfaceType === 'LIST'
  )
  const listReleaseVersion = normalizeReleaseVersion(
    rawListRelease.listReleaseVersion
      ?? rawListRelease.releaseVersion
      ?? rawListRelease.version,
    view.surfaceType === 'LIST'
  )
  const listReleaseResolutionToken = normalizeOpaqueRuntimeCoordinate(
    rawListRelease.listReleaseResolutionToken
      ?? rawListRelease.releaseResolutionToken
  )

  if (!entityCode) {
    throw new EmbedSchemaError('Embed 原生运行时实体坐标无效', 'EMBED_BOOTSTRAP_TARGET_INVALID')
  }

  if (view.surfaceType === 'LIST') {
    // LIST 必须使用 Session 启动时固定的 exact Release；任何坐标
    // 缺失都不允许退回当前 ACTIVE，避免已打开页面中途漂移。
    if (!listKey || !listReleaseId || !listReleaseVersion
      || !listReleaseResolutionToken) {
      throw new EmbedSchemaError('Embed 原生列表坐标无效', 'EMBED_BOOTSTRAP_TARGET_INVALID')
    }
    return Object.freeze({
      entityCode,
      listKey,
      listReleaseId,
      listReleaseVersion,
      listReleaseResolutionToken,
      mode: 'LIST',
      nativeRuntimeUrl: normalizeOpaqueRuntimeCoordinate(
        rawTarget.nativeRuntimeUrl,
        2048
      ) || null,
      initialData: normalizeNativeRuntimeContext(rawTarget.initialData),
      parameters: normalizeNativeRuntimeContext(rawTarget.parameters),
      runtimeContext: normalizeNativeRuntimeContext(
        rawTarget.runtimeContext ?? rawTarget.context
      ),
      // true 表示服务端已在 Launch 时完成一次默认表单解析；即使结果为空，
      // iframe 也不得再按当前 ACTIVE 动态回退。
      defaultFormResolved: rawTarget.defaultFormResolved === true,
      // 列表中的“新增/查看”仍使用启动时固定的默认表单。
      formId: normalizeResourceId(rawForm.formId ?? rawForm.id) || null,
      formReleaseId: normalizeResourceId(
        rawFormRelease.formReleaseId ?? rawFormRelease.releaseId
      ) || null,
      formReleaseVersion: normalizeReleaseVersion(
        rawFormRelease.formReleaseVersion
          ?? rawFormRelease.releaseVersion
          ?? rawFormRelease.version
      ) || null,
      formReleaseResolutionToken: normalizeOpaqueRuntimeCoordinate(
        rawFormRelease.formReleaseResolutionToken
          ?? rawFormRelease.releaseResolutionToken
      ) || null
    })
  }

  const formId = normalizeResourceId(rawForm.formId ?? rawForm.id, true)
  const formReleaseId = normalizeResourceId(
    rawFormRelease.formReleaseId ?? rawFormRelease.releaseId,
    true
  )
  const formReleaseVersion = normalizeReleaseVersion(
    rawFormRelease.formReleaseVersion ?? rawFormRelease.releaseVersion ?? rawFormRelease.version,
    true
  )
  const formReleaseResolutionToken = normalizeOpaqueRuntimeCoordinate(
    rawFormRelease.formReleaseResolutionToken
      ?? rawFormRelease.releaseResolutionToken
  )
  const recordId = normalizeResourceId(rawTarget.recordId ?? rawForm.recordId)
  // exact Release token 是历史发布快照读取凭据；缺失时不能退回当前 ACTIVE，
  // 否则管理员后续发布表单会让既有第三方会话发生不可见漂移。
  if (!formId || !formReleaseId || !formReleaseVersion
    || !formReleaseResolutionToken
    || (view.entryMode === 'VIEW' && !recordId)) {
    throw new EmbedSchemaError('Embed 原生表单坐标无效', 'EMBED_BOOTSTRAP_TARGET_INVALID')
  }

  const target = {
    entityCode,
    formId,
    formReleaseId,
    formReleaseVersion,
    formReleaseResolutionToken,
    mode: view.entryMode,
    recordId: recordId || null,
    processInstanceId: normalizeResourceId(
      rawTarget.processInstanceId ?? rawForm.processInstanceId
    ) || null,
    listKey: listKey || null,
    listReleaseId: listReleaseId || null,
    listReleaseVersion,
    listReleaseResolutionToken: listReleaseResolutionToken || null,
    nativeRuntimeUrl: normalizeOpaqueRuntimeCoordinate(
      rawTarget.nativeRuntimeUrl ?? rawForm.nativeRuntimeUrl,
      2048
    ) || null,
    initialData: normalizeNativeRuntimeContext(rawTarget.initialData),
    parameters: normalizeNativeRuntimeContext(rawTarget.parameters),
    runtimeContext: normalizeNativeRuntimeContext(
      rawTarget.runtimeContext ?? rawTarget.context
    )
  }
  return Object.freeze(target)
}

/** 将 Bootstrap DTO 投影为 Shell 所需的最小会话、视图和 UI 配置。 */
export function normalizeEmbedBootstrap(rawBootstrap = {}) {
  if (!isPlainRecord(rawBootstrap)) {
    throw new EmbedSchemaError('Embed Bootstrap 无效', 'EMBED_BOOTSTRAP_INVALID')
  }
  const session = isPlainRecord(rawBootstrap.session) ? rawBootstrap.session : {}
  const actor = isPlainRecord(rawBootstrap.actor) ? rawBootstrap.actor : {}
  const ui = isPlainRecord(rawBootstrap.ui) ? rawBootstrap.ui : {}
  const limits = isPlainRecord(rawBootstrap.limits) ? rawBootstrap.limits : {}
  const expiresAt = normalizeDateTime(session.expiresAt ?? session.absoluteExpiresAt)
  const idleExpiresAt = normalizeDateTime(session.idleExpiresAt)
  if (!safeText(session.id, '', 256) || !expiresAt || !idleExpiresAt) {
    throw new EmbedSchemaError('Embed Session 摘要无效', 'EMBED_BOOTSTRAP_SESSION_INVALID')
  }
  const capabilities = asArray(rawBootstrap.capabilities)
    .map(value => safeText(value, '', 32).toUpperCase())
    .filter((value, index, values) => CAPABILITIES.has(value) && values.indexOf(value) === index)

  const view = normalizeView(rawBootstrap.view, {
    requireName: true,
    requireRevision: false
  })
  return Object.freeze({
    session: Object.freeze({
      id: safeText(session.id, '', 256),
      expiresAt,
      idleExpiresAt
    }),
    actor: Object.freeze({
      displayName: safeText(
        actor.displayName ?? actor.nickname ?? actor.username,
        '当前用户',
        256
      ),
      username: safeText(actor.username, '', 128),
      nickname: safeText(actor.nickname ?? actor.displayName, '', 256),
      roles: normalizeRuntimeIdentityValues(actor.roles, 200),
      isSuperAdmin: actor.isSuperAdmin === true,
      permissions: normalizeRuntimeIdentityValues(actor.permissions)
    }),
    view,
    target: normalizeEmbedNativeRuntimeTarget(rawBootstrap.target, view),
    capabilities: Object.freeze(capabilities),
    ui: Object.freeze({
      locale: safeText(ui.locale, 'zh-CN', 32),
      theme: ['light', 'dark', 'system'].includes(String(ui.theme || '').toLowerCase())
        ? String(ui.theme).toLowerCase()
        : 'light',
      showSearch: ui.showSearch !== false,
      showPagination: ui.showPagination !== false,
      showToolbar: ui.showToolbar !== false,
      pageSize: clampInteger(ui.pageSize, 20, 1, 100),
      formPresentation: normalizeEmbedFormPresentation(ui.formPresentation),
      heightMode: safeText(ui.heightMode, 'AUTO', 16).toUpperCase() === 'FIXED'
        ? 'FIXED'
        : 'AUTO'
    }),
    limits: Object.freeze({
      maxPageSize: clampInteger(limits.maxPageSize, 100, 1, 100),
      maxPayloadBytes: clampInteger(limits.maxPayloadBytes, 1024 * 1024, 1024, 10 * 1024 * 1024),
      maxSelectionSize: clampInteger(limits.maxSelectionSize, 100, 1, 1000)
    })
  })
}

/** 规范化第 11.4 节完整 External Schema DTO，并移除内部扩展面。 */
export function normalizeEmbedExternalSchema(rawSchema = {}) {
  if (!isPlainRecord(rawSchema)) {
    throw new EmbedSchemaError('Embed Schema 无效')
  }
  const entity = isPlainRecord(rawSchema.entity) ? rawSchema.entity : {}
  const code = normalizeCode(entity.code)
  if (!code) {
    throw new EmbedSchemaError('Embed Entity 无效', 'EMBED_SCHEMA_ENTITY_INVALID')
  }
  const actions = []
  const actionKeys = new Set()
  for (const rawAction of asArray(rawSchema.actions).slice(0, MAX_ACTIONS)) {
    const action = normalizeActionDescriptor(rawAction)
    if (!action || actionKeys.has(action.key)) continue
    actionKeys.add(action.key)
    actions.push(action)
  }

  return Object.freeze({
    view: normalizeView(rawSchema.view),
    entity: Object.freeze({
      code,
      name: safeText(entity.name ?? code, code, 256)
    }),
    list: normalizeEmbedListSchema(rawSchema.list),
    form: null,
    actions: Object.freeze(actions)
  })
}

/** 兼容调用点使用的短名称。 */
export const normalizeEmbedSchema = normalizeEmbedExternalSchema

function sanitizeScalar(value) {
  if (typeof value === 'string') return value.slice(0, MAX_STRING_LENGTH)
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'boolean') return value
  return undefined
}

function sanitizeValue(value, field) {
  if (value === null) return null
  if (field.type === 'NUMBER') {
    const number = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(number)) return undefined
    return number
  }
  if (field.type === 'BOOLEAN') {
    if (typeof value === 'boolean') return value
    if (value === 'true') return true
    if (value === 'false') return false
    return undefined
  }
  if (field.type === 'MULTI_SELECT') {
    if (!Array.isArray(value)) return undefined
    return value.slice(0, MAX_CLIENT_VALUE_ITEMS)
      .map(sanitizeScalar)
      .filter(item => item !== undefined)
  }
  return sanitizeScalar(value)
}

function normalizeRecordId(value) {
  // ID 是身份字段，不能通过 trim/slice 把非法 ID 改造成另一个合法 ID。
  if (typeof value !== 'string'
    && !(typeof value === 'number' && Number.isFinite(value))) return ''
  const normalized = String(value)
  return RECORD_ID_PATTERN.test(normalized) ? normalized : ''
}

function normalizeRecordActions(rawActions, allowedActionKeys) {
  if (!isPlainRecord(rawActions)) return Object.freeze({})
  const result = {}
  for (const actionKey of allowedActionKeys) {
    const capability = rawActions[actionKey]
    if (!isPlainRecord(capability)) continue
    result[actionKey] = Object.freeze({
      visible: capability.visible === true,
      enabled: capability.enabled === true,
      reason: safeText(capability.reason, '', 512) || null
    })
  }
  return Object.freeze(result)
}

/** 只保留 Schema columns 已公开字段和 External Action Capability Map。 */
export function projectEmbedRecord(rawRecord, schema) {
  if (!isPlainRecord(rawRecord) || !schema?.list?.columns) return null
  if (rawRecord.recordVersion !== null) {
    throw new EmbedSchemaError(
      'Embed V1 不接受 recordVersion',
      'EMBED_RECORD_VERSION_UNSUPPORTED'
    )
  }
  const id = normalizeRecordId(rawRecord.id)
  if (!id || !isPlainRecord(rawRecord.values)) return null
  const values = {}
  for (const column of schema.list.columns) {
    if (!own(rawRecord.values, column.code)) continue
    const value = sanitizeValue(rawRecord.values[column.code], column)
    if (value !== undefined) values[column.code] = value
  }
  const meta = isPlainRecord(rawRecord.meta) ? rawRecord.meta : {}
  const updatedAt = normalizeDateTime(meta.updatedAt)
  return Object.freeze({
    id,
    recordVersion: null,
    values: Object.freeze(values),
    meta: Object.freeze({ updatedAt: updatedAt || null }),
    actions: normalizeRecordActions(
      rawRecord.actions,
      (schema.actions || []).map(action => action.key)
    )
  })
}

/** 将第 11.5 节分页响应压缩为列表组件唯一可消费的安全行集合。 */
export function projectEmbedPage(rawPage, schema) {
  const page = isPlainRecord(rawPage) ? rawPage : {}
  const pageSize = clampInteger(
    page.pageSize,
    20,
    1,
    schema?.list?.pagination?.maxPageSize || 100
  )
  const items = asArray(page.items)
    .slice(0, MAX_RECORDS_PER_PAGE)
    .map(record => projectEmbedRecord(record, schema))
    .filter(Boolean)
  const result = {
    items: Object.freeze(items),
    hasMore: page.hasMore === true,
    pageNum: clampInteger(page.pageNum, 1, 1, 1000000),
    pageSize
  }
  if (schema?.list?.pagination?.allowTotal === true && own(page, 'total')) {
    result.total = clampInteger(page.total, items.length, 0, 100000000)
  }
  return Object.freeze(result)
}

function sanitizeQueryValue(value, field) {
  if (field.operator === 'BETWEEN') {
    if (!Array.isArray(value) || value.length !== 2) return undefined
    const range = value.map(item => sanitizeQueryAtom(item, field))
    return range.every(item => item !== undefined && item !== null && item !== '')
      ? Object.freeze(range)
      : undefined
  }
  if (field.operator === 'IN') {
    const source = Array.isArray(value) ? value : [value]
    const values = source
      .slice(0, MAX_OPTIONS)
      .map(item => sanitizeQueryAtom(item, field))
      .filter(item => item !== undefined && item !== null && item !== '')
    return values.length ? Object.freeze(values) : undefined
  }
  const normalized = sanitizeQueryAtom(value, field)
  return normalized === undefined || normalized === null || normalized === ''
    ? undefined
    : normalized
}

/** Query filters always carry atoms; MULTI_SELECT storage shape must not leak into an IN item. */
function sanitizeQueryAtom(value, field) {
  const normalized = field.type === 'MULTI_SELECT'
    ? sanitizeScalar(value)
    : sanitizeValue(value, field)
  return typeof normalized === 'string'
    ? normalized.slice(0, MAX_FILTER_STRING_LENGTH)
    : normalized
}

/**
 * Build the discriminated external filter DTO without exposing an operator choice to the browser.
 * The server resolves the operator again from the immutable published field descriptor.
 */
function externalFilter(field, value) {
  if (field.operator === 'IN') {
    return Object.freeze({ field: field.code, values: value })
  }
  if (field.operator === 'BETWEEN') {
    return Object.freeze({
      field: field.code,
      range: Object.freeze({ start: value[0], end: value[1] })
    })
  }
  return Object.freeze({ field: field.code, value })
}

/**
 * 严格生成第 11.5 节请求体。客户端只能提交 Schema filters 已声明的字段和值形状，
 * 不接受 scene、releaseId、context、fixedFilters 或自定义排序表达式。
 */
export function buildEmbedListQuery(queryValues, schema, {
  pageNum = 1,
  pageSize = 20
} = {}) {
  const source = isPlainRecord(queryValues) ? queryValues : {}
  const filters = []
  for (const field of schema?.list?.filters || []) {
    if (!own(source, field.code)) continue
    const value = sanitizeQueryValue(source[field.code], field)
    if (value !== undefined) filters.push(externalFilter(field, value))
  }
  const maxPageSize = schema?.list?.pagination?.maxPageSize || 100
  return Object.freeze({
    pageNum: clampInteger(pageNum, 1, 1, 1000000),
    pageSize: clampInteger(pageSize, 20, 1, maxPageSize),
    filters: Object.freeze(filters)
  })
}

/**
 * 列表选择事件只回传发布快照授权的 returnableFields。展示字段与宿主返回字段
 * 必须分离，避免仅用于 iframe 展示的数据经 selection.changed 外泄。
 */
export function projectEmbedSelection(records, schema, maxSelectionSize = 100) {
  const returnableFields = schema?.list?.selection?.returnableFields || []
  return Object.freeze(asArray(records)
    .slice(0, clampInteger(maxSelectionSize, 100, 1, 1000))
    .map(record => projectEmbedRecord(record, schema))
    .filter(Boolean)
    .map(record => {
      const values = {}
      for (const code of returnableFields) {
        if (own(record.values, code)) values[code] = record.values[code]
      }
      return Object.freeze({ id: record.id, values: Object.freeze(values) })
    }))
}
