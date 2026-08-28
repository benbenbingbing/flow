const FIELD_TYPES = new Set([
  'TEXT',
  'NUMBER',
  'DATE',
  'DATETIME',
  'TIME',
  'BOOLEAN',
  'SELECT',
  'MULTI_SELECT'
])
const MODES = new Set(['CREATE', 'VIEW'])
const CAPABILITY_BY_MODE = Object.freeze({
  CREATE: 'RECORD_CREATE',
  VIEW: 'RECORD_VIEW'
})
const POLICY_SOURCES = new Set([
  'CLIENT_WRITABLE',
  'CONTEXT',
  'CURRENT_RECORD_READONLY',
  'CONSTANT'
])
const FILTER_OPERATORS = new Set(['EQ', 'CONTAINS', 'IN', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN'])
const VALIDATION_KEYS = new Set(['minLength', 'maxLength', 'minimum', 'maximum', 'pattern', 'format'])
const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype'])
const CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,99}$/
const ACTION_PATTERN = /^[A-Za-z][A-Za-z0-9_.-]{0,127}$/
const RECORD_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/
const RECEIPT_ID_PATTERN = /^eor_[A-Za-z0-9_-]{16,64}$/
const CLIENT_MUTATION_ID_PATTERN = /^[\x20-\x7E]{1,128}$/
const MAX_FIELDS = 100
const MAX_OPTIONS = 200
const MAX_VALUES = 100
const MAX_TEXT = 100000

export class EmbedFormProjectionError extends Error {
  constructor(message, errorCode = 'EMBED_FORM_PROJECTION_INVALID') {
    super(message)
    this.name = 'EmbedFormProjectionError'
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

function safeText(value, fallback = '', maxLength = 256) {
  if (value === null || value === undefined) return fallback
  const text = String(value).trim()
  return text ? text.slice(0, maxLength) : fallback
}

function clampInteger(value, fallback, min, max) {
  const number = Number(value)
  return Number.isFinite(number)
    ? Math.min(max, Math.max(min, Math.floor(number)))
    : fallback
}

function safeCode(value, pattern = CODE_PATTERN) {
  const code = safeText(value, '', 128)
  return pattern.test(code) && !DANGEROUS_KEYS.has(code) ? code : ''
}

function sanitizeScalar(value) {
  if (typeof value === 'string') return value.slice(0, MAX_TEXT)
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'boolean') return value
  return undefined
}

function sanitizeValue(value, type) {
  if (value === null || value === undefined) return null
  if (type === 'NUMBER') {
    const number = typeof value === 'number' ? value : Number(value)
    return Number.isFinite(number) ? number : undefined
  }
  if (type === 'BOOLEAN') {
    if (typeof value === 'boolean') return value
    if (value === 'true') return true
    if (value === 'false') return false
    return undefined
  }
  if (type === 'MULTI_SELECT') {
    if (!Array.isArray(value)) return undefined
    const result = value.slice(0, MAX_VALUES).map(sanitizeScalar)
    return result.every(item => item !== undefined) ? Object.freeze(result) : undefined
  }
  return sanitizeScalar(value)
}

function normalizeOption(raw, type) {
  if (!isPlainRecord(raw)) return null
  const value = sanitizeValue(raw.value, type === 'MULTI_SELECT' ? 'TEXT' : type)
  if (value === undefined || value === null || Array.isArray(value)) return null
  return Object.freeze({
    label: safeText(raw.label ?? value, String(value), 200),
    value,
    disabled: raw.disabled === true
  })
}

function normalizeOptions(raw, type) {
  const result = []
  const seen = new Set()
  for (const candidate of (Array.isArray(raw) ? raw : []).slice(0, MAX_OPTIONS)) {
    const option = normalizeOption(candidate, type)
    if (!option) continue
    const identity = `${typeof option.value}:${String(option.value)}`
    if (seen.has(identity)) continue
    seen.add(identity)
    result.push(option)
  }
  return Object.freeze(result)
}

function normalizeValidation(raw) {
  if (!isPlainRecord(raw)) return Object.freeze({})
  const result = {}
  for (const key of VALIDATION_KEYS) {
    if (!own(raw, key)) continue
    const value = sanitizeScalar(raw[key])
    if (value === undefined) continue
    if (key === 'pattern' && String(value).length > 256) continue
    result[key] = value
  }
  return Object.freeze(result)
}

function normalizeDependencyPolicy(raw) {
  const result = []
  const seen = new Set()
  for (const item of (Array.isArray(raw) ? raw : []).slice(0, 32)) {
    if (!isPlainRecord(item)) continue
    const code = safeCode(item.code)
    const source = safeText(item.source, '', 32).toUpperCase()
    if (!code || !POLICY_SOURCES.has(source) || seen.has(code)) continue
    seen.add(code)
    result.push(Object.freeze({ code, source }))
  }
  return Object.freeze(result)
}

function normalizeFilterPolicy(raw) {
  const result = []
  const seen = new Set()
  for (const item of (Array.isArray(raw) ? raw : []).slice(0, 32)) {
    if (!isPlainRecord(item)) continue
    const code = safeCode(item.code)
    const source = safeText(item.source, '', 32).toUpperCase()
    if (!code || !POLICY_SOURCES.has(source) || seen.has(code)) continue
    const operators = (Array.isArray(item.operators) ? item.operators : [])
      .map(value => safeText(value, '', 16).toUpperCase())
      .filter((value, index, values) => FILTER_OPERATORS.has(value) && values.indexOf(value) === index)
    seen.add(code)
    result.push(Object.freeze({ code, source, operators: Object.freeze(operators) }))
  }
  return Object.freeze(result)
}

function normalizeRuntimeSource(raw, fieldCode, kind) {
  if (raw === null || raw === undefined) return null
  if (!isPlainRecord(raw) || safeText(raw.mode, '', 16).toUpperCase() !== 'RUNTIME') {
    throw new EmbedFormProjectionError('Embed 远程字段来源无效')
  }
  const suffix = kind === 'options' ? 'options/query' : 'lookups/query'
  const expectedUrl = `/api/embed/v1/runtime/form/fields/${encodeURIComponent(fieldCode)}/${suffix}`
  if (raw.queryUrl !== expectedUrl) {
    // queryUrl 只作为后端完整性断言，调用层始终使用固定路由构造器，绝不执行返回的 URL。
    throw new EmbedFormProjectionError('Embed 远程字段路由无效', 'EMBED_FORM_SOURCE_INVALID')
  }
  return Object.freeze({
    mode: 'RUNTIME',
    dependencies: kind === 'options'
      ? normalizeDependencyPolicy(raw.dependencyPolicy)
      : normalizeFilterPolicy(raw.filterPolicy)
  })
}

function normalizeField(raw, mode, capabilities) {
  if (!isPlainRecord(raw)) return null
  const code = safeCode(raw.code)
  const type = safeText(raw.type, '', 32).toUpperCase()
  if (['LOOKUP', 'MULTI_LOOKUP'].includes(type)
    || raw.lookupSource !== null && raw.lookupSource !== undefined) {
    throw new EmbedFormProjectionError(
      'Embed V1 未开放 Lookup',
      'EMBED_OPERATION_NOT_ALLOWED'
    )
  }
  if (!code || !FIELD_TYPES.has(type)) return null
  const requiredCapability = CAPABILITY_BY_MODE[mode]
  const capabilityWritable = mode !== 'VIEW' && capabilities.includes(requiredCapability)
  const visible = raw.hidden !== true && raw.fieldState?.visible === true
  const writable = visible && capabilityWritable && raw.readOnly !== true
    && raw.fieldState?.writable === true
  const defaultValue = sanitizeValue(raw.defaultValue, type)
  if (defaultValue === undefined) {
    throw new EmbedFormProjectionError('Embed 字段默认值无效')
  }
  const optionSource = normalizeRuntimeSource(raw.optionSource, code, 'options')
  if (optionSource && !['SELECT', 'MULTI_SELECT'].includes(type)) {
    throw new EmbedFormProjectionError('Embed 动态选项字段类型无效')
  }
  return Object.freeze({
    code,
    label: safeText(raw.label ?? code, code, 200),
    type,
    required: raw.required === true,
    readOnly: !writable,
    visible,
    writable,
    defaultValue,
    validation: normalizeValidation(raw.validation),
    options: optionSource ? Object.freeze([]) : normalizeOptions(raw.options, type),
    optionSource,
    lookupSource: null,
    span: clampInteger(raw.layout?.span, 24, 1, 24)
  })
}

function normalizeRecordView(raw, fields) {
  if (raw === null || raw === undefined) return null
  if (!isPlainRecord(raw) || !RECORD_ID_PATTERN.test(String(raw.id || '')) || !isPlainRecord(raw.values)) {
    throw new EmbedFormProjectionError('Embed 记录无效', 'EMBED_FORM_RECORD_INVALID')
  }
  const values = {}
  for (const field of fields) {
    if (!field.visible || !own(raw.values, field.code)) continue
    const value = sanitizeValue(raw.values[field.code], field.type)
    if (value !== undefined) values[field.code] = value
  }
  if (raw.recordVersion !== null) {
    throw new EmbedFormProjectionError(
      'Embed V1 不接受 recordVersion',
      'EMBED_RECORD_VERSION_UNSUPPORTED'
    )
  }
  const meta = isPlainRecord(raw.meta) ? raw.meta : {}
  return Object.freeze({
    id: String(raw.id),
    recordVersion: null,
    values: Object.freeze(values),
    meta: Object.freeze({
      createdAt: Number.isFinite(Date.parse(meta.createdAt)) ? String(meta.createdAt) : null,
      updatedAt: Number.isFinite(Date.parse(meta.updatedAt)) ? String(meta.updatedAt) : null
    })
  })
}

function normalizeActions(raw, mode, capabilities) {
  if (mode !== 'CREATE' || !capabilities.includes(CAPABILITY_BY_MODE[mode])) {
    return Object.freeze([])
  }
  const expectedTransport = 'RECORD_CREATE'
  const result = []
  for (const item of (Array.isArray(raw) ? raw : []).slice(0, 20)) {
    if (!isPlainRecord(item)) continue
    const key = safeCode(item.key, ACTION_PATTERN)
    if (!key || item.placement !== 'FORM' || item.kind !== 'MUTATION'
      || item.transport !== expectedTransport) continue
    result.push(Object.freeze({
      key,
      label: safeText(item.label ?? key, key, 200),
      transport: expectedTransport,
      enabled: item.enabled === true,
      disabledReason: safeText(item.disabledReason, '', 512) || null,
      requiresRecordVersion: item.requiresRecordVersion === true,
      idempotencyRequired: item.idempotencyRequired === true
    }))
  }
  return Object.freeze(result)
}

function normalizeReturnableFields(raw, fields) {
  if (!Array.isArray(raw)) {
    throw new EmbedFormProjectionError(
      'Embed 表单返回字段策略缺失',
      'EMBED_FORM_RETURN_POLICY_INVALID'
    )
  }
  const visible = new Set(fields.filter(field => field.visible).map(field => field.code))
  const result = []
  const seen = new Set()
  for (const candidate of raw.slice(0, MAX_FIELDS)) {
    const code = safeCode(candidate)
    if (!code || !visible.has(code) || seen.has(code)) {
      // 服务端承诺已经做 actual visible ∩ returnable，任何偏差都视为协议损坏。
      throw new EmbedFormProjectionError(
        'Embed 表单返回字段策略无效',
        'EMBED_FORM_RETURN_POLICY_INVALID'
      )
    }
    seen.add(code)
    result.push(code)
  }
  return Object.freeze(result)
}

function expectedFormMode(bootstrap, expectedContext) {
  const surfaceType = bootstrap?.view?.surfaceType
  const entryMode = bootstrap?.view?.entryMode
  if (surfaceType === 'FORM') return entryMode
  if (surfaceType !== 'LIST' || entryMode !== 'LIST' || !isPlainRecord(expectedContext)) {
    return ''
  }
  const mode = safeText(expectedContext.mode, '', 16).toUpperCase()
  if (!MODES.has(mode)
    || (mode === 'VIEW' && !RECORD_ID_PATTERN.test(String(expectedContext.recordId || '')))
    || (mode === 'CREATE' && expectedContext.recordId !== undefined
      && expectedContext.recordId !== null)) {
    return ''
  }
  return mode
}

/**
 * 将 FormResult 收缩为仅由基础控件消费的 External Projection。
 *
 * LIST 会话的 expectedContext 只能由 Runtime Controller 在校验已投影 LOCAL_FORM 动作后
 * 构造；这里再次校验 mode/recordId，防止响应漂移把另一条记录带入当前导航子状态。
 */
export function normalizeEmbedFormResult(raw, bootstrap, expectedContext = null) {
  if (!isPlainRecord(raw) || !isPlainRecord(raw.form)) {
    throw new EmbedFormProjectionError('Embed 表单响应无效')
  }
  const mode = safeText(raw.mode, '', 16).toUpperCase()
  const expectedMode = expectedFormMode(bootstrap, expectedContext)
  if (!MODES.has(mode) || mode !== expectedMode) {
    throw new EmbedFormProjectionError('Embed 表单入口不匹配', 'EMBED_FORM_MODE_MISMATCH')
  }
  const capabilities = Array.isArray(bootstrap.capabilities) ? bootstrap.capabilities : []
  if (!capabilities.includes(CAPABILITY_BY_MODE[mode])) {
    throw new EmbedFormProjectionError('Embed 表单能力未开放', 'EMBED_CAPABILITY_FORBIDDEN')
  }
  const fields = []
  const codes = new Set()
  for (const candidate of (Array.isArray(raw.form.fields) ? raw.form.fields : []).slice(0, MAX_FIELDS)) {
    const field = normalizeField(candidate, mode, capabilities)
    if (!field || codes.has(field.code)) continue
    codes.add(field.code)
    if (field.visible) fields.push(field)
  }
  if (!fields.length) {
    throw new EmbedFormProjectionError('Embed 表单没有可展示字段', 'EMBED_FORM_FIELDS_EMPTY')
  }
  const layoutType = safeText(raw.form.layout?.type, 'GRID', 16).toUpperCase()
  if (!['GRID', 'VERTICAL', 'HORIZONTAL'].includes(layoutType)) {
    throw new EmbedFormProjectionError('Embed 表单布局无效')
  }
  const record = normalizeRecordView(raw.record, fields)
  const expectedRecordId = expectedContext?.recordId
  if ((mode === 'CREATE' && record)
    || (mode === 'VIEW' && expectedRecordId !== undefined && expectedRecordId !== null
      && (!RECORD_ID_PATTERN.test(String(expectedRecordId))
        || record?.id !== String(expectedRecordId)))) {
    throw new EmbedFormProjectionError('Embed 表单记录坐标不匹配', 'EMBED_FORM_RECORD_MISMATCH')
  }
  return Object.freeze({
    mode,
    title: safeText(raw.form.title, bootstrap.view.name, 256),
    layout: Object.freeze({ type: layoutType }),
    fields: Object.freeze(fields),
    returnableFields: normalizeReturnableFields(raw.form.returnableFields, fields),
    actions: normalizeActions(raw.form.actions, mode, capabilities),
    record
  })
}

/** 投影独立 RecordResult，并用服务端逐记录字段状态做第二次可见/只读求交。 */
export function normalizeEmbedRecordResult(raw, form) {
  if (!isPlainRecord(raw) || !isPlainRecord(raw.fieldStates)) {
    throw new EmbedFormProjectionError('Embed 记录响应无效', 'EMBED_FORM_RECORD_INVALID')
  }
  const fields = form.fields.map(field => {
    const state = isPlainRecord(raw.fieldStates[field.code]) ? raw.fieldStates[field.code] : null
    const visible = field.visible && state?.visible === true
    // V1 的独立记录读取只服务 VIEW；即使响应被污染为可写也不得升级前端能力。
    const writable = false
    return Object.freeze({
      ...field,
      visible,
      writable,
      readOnly: !writable,
      required: field.required && state?.required === true
    })
  }).filter(field => field.visible)
  if (!fields.length) {
    throw new EmbedFormProjectionError('Embed 记录没有可展示字段', 'EMBED_FORM_FIELDS_EMPTY')
  }
  const record = normalizeRecordView(raw.record, fields)
  if (!record || form.record?.id && record.id !== form.record.id) {
    throw new EmbedFormProjectionError('Embed 记录坐标不匹配', 'EMBED_FORM_RECORD_MISMATCH')
  }
  return Object.freeze({ ...form, fields: Object.freeze(fields), record })
}

function validateCreateClientMutationId(value) {
  if (value === undefined) return undefined
  if (typeof value !== 'string' || !CLIENT_MUTATION_ID_PATTERN.test(value)) {
    throw new EmbedFormProjectionError(
      'Embed clientMutationId 无效',
      'EMBED_CREATE_REQUEST_INVALID'
    )
  }
  return value
}

/**
 * 仅从当前 CREATE 表单的可写字段构造浏览器请求。实体、表单、Release、用户、流程及
 * 任意未知字段都不能进入该协议；这些坐标只能由服务端 Session 恢复。
 */
export function buildEmbedCreateRequest(form, values, clientMutationId) {
  if (!isPlainRecord(form) || form.mode !== 'CREATE' || !isPlainRecord(values)) {
    throw new EmbedFormProjectionError(
      'Embed 创建请求无效',
      'EMBED_CREATE_REQUEST_INVALID'
    )
  }
  const action = form.actions?.find(candidate => candidate.key === 'save')
  if (!action || action.transport !== 'RECORD_CREATE' || action.enabled !== true
    || action.requiresRecordVersion === true || action.idempotencyRequired !== true) {
    throw new EmbedFormProjectionError(
      'Embed 创建能力未开放',
      'EMBED_OPERATION_NOT_ALLOWED'
    )
  }

  const fields = new Map(form.fields.map(field => [field.code, field]))
  const data = {}
  for (const key of Object.keys(values)) {
    const field = fields.get(key)
    if (!field || field.writable !== true || field.readOnly === true) {
      // 对组件状态污染或未来协议漂移 fail closed，绝不静默把未知键发送给服务端。
      throw new EmbedFormProjectionError(
        'Embed 创建请求包含不可写字段',
        'EMBED_CREATE_REQUEST_INVALID'
      )
    }
  }
  for (const field of form.fields) {
    if (field.writable !== true || field.readOnly === true || !own(values, field.code)) continue
    const value = sanitizeValue(values[field.code], field.type)
    if (value === undefined) {
      throw new EmbedFormProjectionError(
        `字段 ${field.code} 的值无效`,
        'EMBED_CREATE_REQUEST_INVALID'
      )
    }
    if (field.required && (value === null || value === ''
      || Array.isArray(value) && value.length === 0)) {
      throw new EmbedFormProjectionError(
        `字段 ${field.code} 为必填项`,
        'EMBED_CREATE_VALIDATION_FAILED'
      )
    }
    data[field.code] = value
  }

  const mutationId = validateCreateClientMutationId(clientMutationId)
  return Object.freeze({
    data: Object.freeze(data),
    ...(mutationId === undefined ? {} : { clientMutationId: mutationId })
  })
}

/**
 * 构造 CREATE 联动重算的部分草稿。与最终保存不同，这里不检查 required，且协议
 * 只允许当前 External Projection 中仍可写的字段，不携带动作或目标坐标。
 */
export function buildEmbedCreateEvaluationRequest(form, values) {
  if (!isPlainRecord(form) || form.mode !== 'CREATE' || !isPlainRecord(values)) {
    throw new EmbedFormProjectionError(
      'Embed CREATE 表单重算请求无效',
      'EMBED_CREATE_EVALUATION_INVALID'
    )
  }
  const fields = new Map(form.fields?.map(field => [field.code, field]) || [])
  const data = {}
  for (const code of Object.keys(values)) {
    const field = fields.get(code)
    if (!field || field.writable !== true || field.readOnly === true) {
      throw new EmbedFormProjectionError(
        'Embed CREATE 表单重算包含不可写字段',
        'EMBED_CREATE_EVALUATION_INVALID'
      )
    }
    const value = sanitizeValue(values[code], field.type)
    if (value === undefined) {
      throw new EmbedFormProjectionError(
        `字段 ${code} 的重算值无效`,
        'EMBED_CREATE_EVALUATION_INVALID'
      )
    }
    data[code] = value
  }
  return Object.freeze({ data: Object.freeze(data) })
}

/**
 * CREATE 联动投影变化时，只保留新投影仍存在且类型仍安全的用户值；隐藏字段会从
 * 草稿中移除，新出现字段从发布默认值初始化。
 */
export function mergeEmbedCreateDraft(form, previousForm, previousValues = {}) {
  if (!isPlainRecord(form) || form.mode !== 'CREATE') return Object.freeze({})
  const mayPreserve = previousForm?.mode === 'CREATE'
    && previousForm?.record == null && form.record == null
    && isPlainRecord(previousValues)
  const result = {}
  for (const field of form.fields || []) {
    if (mayPreserve && own(previousValues, field.code)) {
      const preserved = sanitizeValue(previousValues[field.code], field.type)
      if (preserved !== undefined) {
        result[field.code] = preserved
        continue
      }
    }
    if (own(form.record?.values, field.code)) {
      result[field.code] = form.record.values[field.code]
    } else if (field.defaultValue !== null && field.defaultValue !== undefined) {
      result[field.code] = field.defaultValue
    } else if (field.type === 'MULTI_SELECT') {
      result[field.code] = Object.freeze([])
    } else if (field.type === 'BOOLEAN') {
      result[field.code] = false
    } else {
      result[field.code] = ''
    }
  }
  return Object.freeze(result)
}

/**
 * 校验 RECORD_CREATE 返回值并再次按当前表单投影。服务端若返回未知字段、非空 effects
 * 或错误的 clientMutationId，说明协议/策略发生漂移，iframe 必须拒绝消费。
 */
export function normalizeEmbedCreateResult(raw, form, expectedClientMutationId) {
  if (!isPlainRecord(raw)
    || Object.keys(raw).some(key => !['receiptId', 'record', 'effects', 'clientMutationId'].includes(key))
    || !RECEIPT_ID_PATTERN.test(String(raw.receiptId || ''))
    || !Array.isArray(raw.effects) || raw.effects.length !== 0
    || raw.clientMutationId !== expectedClientMutationId
    || !isPlainRecord(raw.record) || !isPlainRecord(raw.record.values)) {
    throw new EmbedFormProjectionError(
      'Embed 创建响应无效',
      'EMBED_CREATE_RESPONSE_INVALID'
    )
  }

  const visibleFields = new Map(
    form.fields.filter(field => field.visible).map(field => [field.code, field])
  )
  if (Object.keys(raw.record.values).some(code => !visibleFields.has(code))) {
    throw new EmbedFormProjectionError(
      'Embed 创建响应包含未知字段',
      'EMBED_CREATE_RESPONSE_INVALID'
    )
  }
  const record = normalizeRecordView(raw.record, form.fields)
  if (!record) {
    throw new EmbedFormProjectionError(
      'Embed 创建记录无效',
      'EMBED_CREATE_RESPONSE_INVALID'
    )
  }
  return Object.freeze({
    receiptId: String(raw.receiptId),
    record,
    effects: Object.freeze([]),
    clientMutationId: expectedClientMutationId
  })
}

/** form.saved 是宿主数据边界，只投影 View 明确允许返回的字段。 */
export function projectEmbedFormSaved(result, form) {
  if (!isPlainRecord(result) || !isPlainRecord(result.record)
    || !isPlainRecord(result.record.values)
    || !RECEIPT_ID_PATTERN.test(String(result.receiptId || ''))
    || !RECORD_ID_PATTERN.test(String(result.record.id || ''))
    || !Array.isArray(form?.returnableFields)) {
    throw new EmbedFormProjectionError(
      'Embed 保存事件无效',
      'EMBED_FORM_SAVED_PROJECTION_INVALID'
    )
  }
  const visible = new Set(form.fields?.filter(field => field.visible).map(field => field.code))
  const values = {}
  for (const code of form.returnableFields) {
    if (!visible.has(code)) {
      throw new EmbedFormProjectionError(
        'Embed 保存事件返回字段策略无效',
        'EMBED_FORM_SAVED_PROJECTION_INVALID'
      )
    }
    if (own(result.record.values, code)) values[code] = result.record.values[code]
  }
  return Object.freeze({
    receiptId: String(result.receiptId),
    record: Object.freeze({
      id: String(result.record.id),
      values: Object.freeze(values)
    }),
    clientMutationId: result.clientMutationId
  })
}

function safeClientValue(value) {
  if (value === null || value === undefined || value === '') return undefined
  const scalar = sanitizeScalar(value)
  if (scalar !== undefined) return scalar
  if (!Array.isArray(value)) return undefined
  const values = value.slice(0, MAX_VALUES).map(sanitizeScalar).filter(item => item !== undefined)
  return values.length === value.slice(0, MAX_VALUES).length ? Object.freeze(values) : undefined
}

function clientPolicyValues(policy, values) {
  const source = isPlainRecord(values) ? values : {}
  const result = {}
  for (const rule of policy || []) {
    if (rule.source !== 'CLIENT_WRITABLE' || !own(source, rule.code)) continue
    const value = safeClientValue(source[rule.code])
    if (value !== undefined) result[rule.code] = value
  }
  return Object.freeze(result)
}

function normalizeKeyword(value) {
  return safeText(value, '', 200) || null
}

function choiceNavigation(options) {
  const mode = safeText(options.mode, '', 16).toUpperCase()
  if (!MODES.has(mode)) throw new EmbedFormProjectionError('动态字段查询模式无效')
  const recordId = safeCode(options.recordId, RECORD_ID_PATTERN)
  if ((mode === 'VIEW') !== Boolean(recordId)) {
    throw new EmbedFormProjectionError('动态字段查询记录坐标无效')
  }
  return mode === 'VIEW' ? { mode, recordId } : { mode }
}

export function buildEmbedOptionQuery(field, formValues, options = {}) {
  if (!field?.optionSource) throw new EmbedFormProjectionError('字段没有动态选项来源')
  return Object.freeze({
    ...choiceNavigation(options),
    keyword: normalizeKeyword(options.keyword),
    dependencies: clientPolicyValues(field.optionSource.dependencies, formValues),
    pageNum: clampInteger(options.pageNum, 1, 1, 1000000),
    pageSize: clampInteger(options.pageSize, 50, 1, 50)
  })
}

export function buildEmbedLookupQuery() {
  throw new EmbedFormProjectionError(
    'Embed V1 未开放 Lookup',
    'EMBED_OPERATION_NOT_ALLOWED'
  )
}

export function normalizeEmbedOptionPage(raw, field) {
  if (!isPlainRecord(raw)) throw new EmbedFormProjectionError('动态选项响应无效')
  return Object.freeze({
    items: normalizeOptions(raw.items, field.type),
    hasMore: raw.hasMore === true,
    pageNum: clampInteger(raw.pageNum, 1, 1, 1000000),
    pageSize: clampInteger(raw.pageSize, 20, 1, 50)
  })
}

export function normalizeEmbedLookupPage() {
  throw new EmbedFormProjectionError(
    'Embed V1 未开放 Lookup',
    'EMBED_OPERATION_NOT_ALLOWED'
  )
}

export function requiredFormCapability(mode) {
  return CAPABILITY_BY_MODE[String(mode || '').toUpperCase()] || ''
}
