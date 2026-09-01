import {
  collectFlowConditionProperties,
  createFlowConditionConfig,
  createFlowConditionGroup,
  evaluateFlowConditionGroup,
  isFlowConditionGroupComplete,
  normalizeFlowConditionRoot
} from '../utils/flowConditionGroups.js'

export const FORM_FIELD_UNIQUENESS_MODES = Object.freeze([
  'NONE',
  'GLOBAL',
  'CONDITIONAL'
])

export const FORM_FIELD_UNIQUENESS_PRECHECK_TRIGGERS = Object.freeze([
  'CHANGE',
  'BLUR',
  'SUBMIT_ONLY'
])

const UNSUPPORTED_FIELD_TYPES = new Set([
  'FILE',
  'IMAGE',
  'RICH_TEXT',
  'MULTI_SELECT',
  'CHECKBOX',
  'MULTI_REFERENCE',
  'SUB_FORM',
  'SUBLIST',
  'SUB_LIST',
  'REPEATER',
  'SECTION'
])

const UNSUPPORTED_COMPONENT_TYPES = new Set([
  'FILE',
  'IMAGE',
  'RICH_TEXT',
  'MULTI_SELECT',
  'SELECT_MULTIPLE',
  'CHECKBOX',
  'MULTI_REFERENCE',
  'SUB_FORM',
  'SUBLIST',
  'SUB_LIST',
  'REPEATER',
  'CASCADER',
  'SECTION'
])

const DEFAULT_PRECHECK = Object.freeze({
  enabled: true,
  trigger: 'BLUR',
  debounceMs: 500,
  watchConditionFields: true
})

function asObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

function cloneValue(value) {
  if (Array.isArray(value)) return value.map(cloneValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, cloneValue(item)])
    )
  }
  return value
}

function normalizeMode(value) {
  const mode = String(value || 'NONE').trim().toUpperCase()
  return FORM_FIELD_UNIQUENESS_MODES.includes(mode) ? mode : 'NONE'
}

function normalizeTrigger(value) {
  const trigger = String(value || DEFAULT_PRECHECK.trigger).trim().toUpperCase()
  return FORM_FIELD_UNIQUENESS_PRECHECK_TRIGGERS.includes(trigger)
    ? trigger
    : DEFAULT_PRECHECK.trigger
}

function normalizeDebounceMs(value) {
  const debounceMs = Number(value)
  if (!Number.isFinite(debounceMs)) return DEFAULT_PRECHECK.debounceMs
  return Math.min(3000, Math.max(200, Math.round(debounceMs)))
}

export function resolveFormFieldKey(field = {}) {
  return String(
    field.fieldCode
    || field.fieldKey
    || field.bindingRef
    || field.nodeKey
    || ''
  ).trim()
}

export function createFormFieldUniquenessRuleId(fieldCode) {
  const safeCode = String(fieldCode || 'field')
    .trim()
    .replace(/[^A-Za-z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
  return `uq_${safeCode || 'field'}`.slice(0, 100)
}

/**
 * 标准化表单字段唯一规则。规则始终属于宿主表单，ruleId 只需在该表单内稳定，
 * 以便不同发布版本仍能共用同一逻辑规则的并发互斥范围并保留连续审计信息。
 */
export function normalizeFormFieldUniqueness(value, fieldCode = '') {
  const source = asObject(value)
  const mode = normalizeMode(source.mode)
  const precheckSource = asObject(source.precheck)
  const normalized = {
    version: 1,
    ruleId: String(
      source.ruleId || createFormFieldUniquenessRuleId(fieldCode)
    ).trim().slice(0, 100),
    mode,
    ignoreBlank: source.ignoreBlank !== false,
    precheck: {
      enabled: precheckSource.enabled !== false,
      trigger: normalizeTrigger(precheckSource.trigger),
      debounceMs: normalizeDebounceMs(precheckSource.debounceMs),
      watchConditionFields: precheckSource.watchConditionFields !== false
    }
  }

  const message = String(source.message || '').trim()
  if (message) normalized.message = message.slice(0, 200)

  if (mode === 'CONDITIONAL') {
    const conditionSource = asObject(source.condition)
    normalized.condition = createFlowConditionConfig(
      normalizeFlowConditionRoot(
        conditionSource.root || source.conditionRoot || createFlowConditionGroup()
      )
    )
  }
  return normalized
}

export function supportsFormFieldUniqueness(field = {}) {
  const fieldType = String(field.fieldType || '').trim().toUpperCase()
  const componentType = String(field.componentType || '').trim().toUpperCase()
  return Boolean(resolveFormFieldKey(field))
    && !UNSUPPORTED_FIELD_TYPES.has(fieldType)
    && !UNSUPPORTED_COMPONENT_TYPES.has(componentType)
}

/**
 * 按节点树当前实际渲染的根范围筛选唯一预检字段。外层 Tab 会为每个子树创建独立
 * FormPreview；若仍对全表字段查重，错误会落在错误的 Tab 实例并产生重复请求。
 */
export function filterFormUniquenessFieldsForNodeScope(
  fields = [],
  nodes = [],
  { rootParentId = '', excludedNodeIds = [] } = {}
) {
  if (!Array.isArray(nodes) || !nodes.length
      || (!String(rootParentId || '') && !(excludedNodeIds || []).length)) {
    return fields || []
  }
  const children = new Map()
  for (const node of nodes) {
    const parentId = String(node?.parentId || '')
    if (!children.has(parentId)) children.set(parentId, [])
    children.get(parentId).push(node)
  }
  const excluded = new Set((excludedNodeIds || []).map(value => String(value)))
  const stack = (children.get(String(rootParentId || '')) || [])
    .filter(node => !excluded.has(String(node?.id || '')))
  const bindingRefs = new Set()
  while (stack.length) {
    const node = stack.pop()
    const nodeProps = asObject(node?.propsDocument ?? node?.props)
    ;[
      node?.bindingRef,
      nodeProps.fieldCode,
      nodeProps.fieldId,
      node?.nodeKey
    ].forEach(value => {
      const normalized = String(value || '').trim()
      if (normalized) bindingRefs.add(normalized)
    })
    stack.push(...(children.get(String(node?.id || '')) || []))
  }
  return (fields || []).filter(field => [
    field?.id,
    field?.fieldId,
    field?.fieldCode,
    field?.fieldKey,
    field?.nodeKey
  ].some(value => bindingRefs.has(String(value || '').trim())))
}

export function resolveFormFieldUniqueness(field = {}) {
  const validation = asObject(field.validationRules ?? field.validateRules)
  if (!validation.uniqueness) return null
  const normalized = normalizeFormFieldUniqueness(
    validation.uniqueness,
    resolveFormFieldKey(field)
  )
  return normalized.mode === 'NONE' ? null : normalized
}

export function validateFormFieldUniqueness(value, fieldCode = '') {
  const rule = normalizeFormFieldUniqueness(value, fieldCode)
  const errors = []
  if (rule.mode === 'NONE') return { valid: true, errors, normalized: rule }
  if (!rule.ruleId) errors.push('唯一规则标识不能为空')
  if (rule.mode === 'CONDITIONAL'
      && !isFlowConditionGroupComplete(rule.condition?.root)) {
    errors.push('条件唯一至少需要一个完整条件')
  }
  return { valid: errors.length === 0, errors, normalized: rule }
}

export function collectFormUniquenessConditionFields(rule) {
  if (rule?.mode !== 'CONDITIONAL') return []
  return collectFlowConditionProperties(rule.condition?.root)
}

export function isFormUniquenessConditionActive(rule, record = {}) {
  if (!rule || rule.mode === 'NONE') return false
  if (rule.mode === 'GLOBAL') return true
  return isFlowConditionGroupComplete(rule.condition?.root)
    && evaluateFlowConditionGroup(rule.condition.root, record)
}

function hasRecordProperty(record, property) {
  const segments = String(property || '').split('.').filter(Boolean)
  if (!segments.length) return false
  let current = record
  for (const segment of segments) {
    if (!current || typeof current !== 'object'
        || !Object.prototype.hasOwnProperty.call(current, segment)) {
      return false
    }
    current = current[segment]
  }
  return true
}

/**
 * 只有客户端掌握全部条件字段时才可以本地判定“不需要预检”。编辑表单可能没有
 * 展示条件字段，此时必须交给服务端合并数据库旧记录后再判断，不能静默跳过。
 */
export function canEvaluateFormUniquenessCondition(rule, record = {}) {
  if (!rule || rule.mode !== 'CONDITIONAL') return true
  const properties = collectFormUniquenessConditionFields(rule)
  return properties.length > 0
    && properties.every(property => hasRecordProperty(record, property))
}

export function isBlankFormUniqueValue(value) {
  return value === null
    || value === undefined
    || (typeof value === 'string' && value.trim() === '')
}

export function shouldRunFormUniquePrecheck(rule, reason) {
  if (!rule?.precheck?.enabled) return false
  const normalizedReason = String(reason || '').toUpperCase()
  if (normalizedReason === 'SUBMIT') return true
  return rule.precheck.trigger === normalizedReason
}

export function resolveFormUniqueRuntimeIdentity(form = {}, context = {}) {
  const formId = String(form.id || form.formId || form.entityFormId || '').trim()
  const releaseId = String(
    form.runtimeReleaseId || form.formReleaseId || context.formReleaseId || ''
  ).trim()
  const releaseVersion = Number(
    form.runtimeReleaseVersion
    ?? form.formReleaseVersion
    ?? context.formReleaseVersion
    ?? 0
  )
  const releaseResolutionToken = String(
    form.releaseResolutionToken
    || context.releaseResolutionToken
    || context.formReleaseResolutionToken
    || ''
  ).trim()
  const recordId = String(
    context.record?.id || context.recordId || ''
  ).trim()
  const viewCompositionTraversalToken = String(
    context.viewCompositionTraversalToken || ''
  ).trim()
  return {
    formId,
    releaseId: releaseId || undefined,
    releaseVersion: releaseVersion > 0 ? releaseVersion : undefined,
    releaseResolutionToken: releaseResolutionToken || undefined,
    viewCompositionTraversalToken:
      viewCompositionTraversalToken || undefined,
    recordId: recordId || undefined,
    // 设计器草稿也有 formId；必须存在发布身份才允许发运行时预检请求。
    published: Boolean(
      formId && (releaseId || releaseVersion > 0 || releaseResolutionToken)
    )
  }
}

export function buildFormUniquePrecheckPayload(field, rule, record, identity) {
  return {
    releaseId: identity.releaseId,
    releaseVersion: identity.releaseVersion,
    releaseResolutionToken: identity.releaseResolutionToken,
    viewCompositionTraversalToken:
      identity.viewCompositionTraversalToken,
    ruleId: rule.ruleId,
    fieldCode: resolveFormFieldKey(field),
    recordId: identity.recordId,
    formData: cloneValue(record || {})
  }
}

export function normalizeFormUniquePrecheckResult(value, field, rule) {
  const source = asObject(value)
  const available = source.available !== false
  return {
    available,
    checked: source.checked === true,
    fieldCode: String(
      source.fieldCode || resolveFormFieldKey(field)
    ),
    ruleId: String(source.ruleId || rule.ruleId || ''),
    message: available
      ? ''
      : String(
          source.message
          || rule.message
          || `${field.fieldLabel || field.fieldName || '该字段'}的值已存在`
        )
  }
}

function valueFingerprint(value) {
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function changedRecordKeys(previous, current) {
  if (!previous) return []
  const keys = new Set([
    ...Object.keys(previous || {}),
    ...Object.keys(current || {})
  ])
  return [...keys].filter(key =>
    valueFingerprint(previous?.[key]) !== valueFingerprint(current?.[key])
  )
}

/**
 * 创建表单唯一预检控制器。它只读取当前运行表单字段规则，并用递增序号忽略
 * 防抖或网络竞态产生的过期响应；最终提交仍由服务端事务校验负责。
 */
export function createFormUniquePrecheckController({
  request,
  getIdentity,
  onErrorsChange = () => {}
}) {
  let previousRecord = null
  const errors = new Map()
  const results = new Map()
  const sequences = new Map()
  const pending = new Map()

  function notifyErrors() {
    onErrorsChange(Object.fromEntries(errors))
  }

  function ruleKey(field, rule) {
    return String(rule?.ruleId || resolveFormFieldKey(field))
  }

  function clearError(fieldCode) {
    if (!errors.delete(fieldCode)) return
    notifyErrors()
  }

  function applyResult(field, result) {
    const fieldCode = resolveFormFieldKey(field)
    if (result.available || !result.checked) {
      clearError(fieldCode)
      return
    }
    errors.set(fieldCode, result.message)
    notifyErrors()
  }

  function cancelPending(key) {
    const entry = pending.get(key)
    if (!entry) return
    clearTimeout(entry.timer)
    pending.delete(key)
    entry.resolve({ available: true, checked: false, stale: true })
  }

  function invalidate(key) {
    sequences.set(key, (sequences.get(key) || 0) + 1)
    results.delete(key)
    cancelPending(key)
  }

  async function check(field, record, { reason = 'SUBMIT' } = {}) {
    const rule = resolveFormFieldUniqueness(field)
    const fieldCode = resolveFormFieldKey(field)
    if (!rule || !shouldRunFormUniquePrecheck(rule, reason)) {
      clearError(fieldCode)
      return { available: true, checked: false }
    }
    const conditionKnown = canEvaluateFormUniquenessCondition(rule, record)
    if ((rule.ignoreBlank && isBlankFormUniqueValue(record?.[fieldCode]))
        || (conditionKnown
          && !isFormUniquenessConditionActive(rule, record))) {
      invalidate(ruleKey(field, rule))
      clearError(fieldCode)
      return { available: true, checked: false }
    }

    const identity = getIdentity()
    if (!identity?.published) {
      clearError(fieldCode)
      return { available: true, checked: false }
    }

    const key = ruleKey(field, rule)
    cancelPending(key)
    const sequence = (sequences.get(key) || 0) + 1
    sequences.set(key, sequence)
    const payload = buildFormUniquePrecheckPayload(
      field,
      rule,
      record,
      identity
    )
    const fingerprint = valueFingerprint({ formId: identity.formId, ...payload })
    const cached = results.get(key)
    // 提交前必须重新预检，避免之前的“重复”结果在冲突记录已删除后永久阻塞用户。
    // 最终数据一致性仍由保存事务保证。
    if (String(reason).toUpperCase() !== 'SUBMIT'
        && cached?.fingerprint === fingerprint) {
      applyResult(field, cached.result)
      return cached.result
    }

    try {
      const response = await request(identity.formId, payload)
      if (sequences.get(key) !== sequence) {
        return { available: true, checked: false, stale: true }
      }
      const result = normalizeFormUniquePrecheckResult(response, field, rule)
      results.set(key, { fingerprint, result })
      applyResult(field, result)
      return result
    } catch (error) {
      if (sequences.get(key) !== sequence) {
        return { available: true, checked: false, stale: true }
      }
      // 预检异常不冒充重复；权威保存仍会在服务端事务中执行唯一校验。
      clearError(fieldCode)
      return { available: true, checked: false, error }
    }
  }

  function schedule(field, record) {
    const rule = resolveFormFieldUniqueness(field)
    if (!rule || !shouldRunFormUniquePrecheck(rule, 'CHANGE')) {
      return Promise.resolve({ available: true, checked: false })
    }
    const key = ruleKey(field, rule)
    invalidate(key)
    // 值或条件已变化，旧的重复提示立即失效，等待本次防抖检查给出新结论。
    clearError(resolveFormFieldKey(field))
    const snapshot = cloneValue(record || {})
    return new Promise(resolve => {
      const timer = setTimeout(() => {
        pending.delete(key)
        check(field, snapshot, { reason: 'CHANGE' }).then(resolve)
      }, rule.precheck.debounceMs)
      pending.set(key, { timer, resolve })
    })
  }

  function handleRecordChange(fields, record) {
    const snapshot = cloneValue(record || {})
    const changedKeys = changedRecordKeys(previousRecord, snapshot)
    previousRecord = snapshot
    if (!changedKeys.length) return []
    const changed = new Set(changedKeys)
    return (fields || []).flatMap(field => {
      const rule = resolveFormFieldUniqueness(field)
      if (!rule?.precheck?.enabled) return []
      const fieldCode = resolveFormFieldKey(field)
      const conditionChanged = rule.precheck.watchConditionFields
        && collectFormUniquenessConditionFields(rule)
          .some(property => changed.has(String(property).split('.')[0]))
      if (!changed.has(fieldCode) && !conditionChanged) return []
      if (rule.precheck.trigger === 'CHANGE') {
        return [schedule(field, snapshot)]
      }
      // BLUR/SUBMIT_ONLY 不因输入变化发请求，但旧错误已失效，必须立即清除。
      invalidate(ruleKey(field, rule))
      clearError(fieldCode)
      return []
    })
  }

  async function checkAll(
    fields,
    recordOrGetter,
    { maxStaleRetries = 1 } = {}
  ) {
    const enabledFields = (fields || [])
      .filter(field => resolveFormFieldUniqueness(field)?.precheck?.enabled)
    if (!enabledFields.length) return { valid: true, results: [] }

    const getCurrentRecord = typeof recordOrGetter === 'function'
      ? recordOrGetter
      : () => recordOrGetter
    const retryLimit = Math.min(
      3,
      Math.max(0, Math.trunc(Number(maxStaleRetries) || 0))
    )
    let values = []

    // 表单在请求期间仍可能被输入或联动改写。每轮固定当前指纹，请求完成后
    // 再读取最新记录；发生变化或控制器判定响应过期时，按最新值有界重试。
    for (let attempt = 0; attempt <= retryLimit; attempt += 1) {
      const currentRecord = getCurrentRecord() || {}
      const fingerprint = valueFingerprint(currentRecord)
      values = await Promise.all(
        enabledFields.map(field =>
          check(field, currentRecord, { reason: 'SUBMIT' })
        )
      )
      const recordChanged = valueFingerprint(getCurrentRecord() || {})
        !== fingerprint
      const stale = recordChanged || values.some(result => result?.stale === true)
      if (!stale) {
        return {
          valid: values.every(result => result.available !== false),
          results: values
        }
      }
    }

    // 持续编辑超过重试上限时不能把 stale 当作“可用”放行；给第一个唯一字段
    // 一个可见错误，用户停止编辑后再次提交会重新执行权威预检并清除它。
    const staleMessage = '表单数据在唯一性检查期间发生变化，请重新提交'
    const staleFieldCode = resolveFormFieldKey(enabledFields[0])
    if (staleFieldCode) {
      errors.set(staleFieldCode, staleMessage)
      notifyErrors()
    }
    return {
      valid: false,
      stale: true,
      message: staleMessage,
      results: values
    }
  }

  function reset(record = null) {
    pending.forEach(entry => {
      clearTimeout(entry.timer)
      entry.resolve({ available: true, checked: false, stale: true })
    })
    pending.clear()
    sequences.clear()
    results.clear()
    errors.clear()
    previousRecord = record == null ? null : cloneValue(record)
    notifyErrors()
  }

  function errorFor(fieldCode) {
    return errors.get(String(fieldCode || '')) || ''
  }

  return {
    check,
    checkAll,
    errorFor,
    handleRecordChange,
    reset,
    schedule
  }
}
