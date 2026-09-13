const OWNER_TYPE_LABELS = Object.freeze({
  ENTITY: '实体默认',
  FORM: '表单覆盖',
  LIST: '列表覆盖'
})

const TARGET_TYPE_LABELS = Object.freeze({
  OWNER: '当前配置',
  FIELD: '字段',
  BUTTON: '按钮'
})

const EVENT_LABELS = Object.freeze({
  LIST_LOAD: '加载列表',
  LIST_EXPORT: '导出列表',
  DETAIL_LOAD: '加载详情',
  DATA_CREATE: '新增数据',
  DATA_UPDATE: '修改数据',
  DATA_DELETE: '删除数据',
  DATA_BATCH_DELETE: '批量删除',
  FORM_OPEN: '打开表单',
  FORM_SAVE: '保存表单',
  FORM_RESET: '重置表单',
  FIELD_CHANGE: '字段值变化',
  ENTITY_SELECTED: '选择实体后',
  FIELD_BUTTON_CLICK: '字段按钮点击',
  SUBFORM_LOAD: '加载子表',
  SUBFORM_SAVE: '保存子表',
  TOOLBAR_BUTTON_CLICK: '工具栏按钮点击',
  ROW_BUTTON_CLICK: '行按钮点击',
  FORM_BUTTON_CLICK: '表单按钮点击'
})

const LIFECYCLE_LABELS = Object.freeze({
  DRAFT_ONLY: '仅草稿',
  PUBLISHED_MATCH: '已发布·一致',
  PUBLISHED_CHANGED: '草稿有变更',
  PUBLISHED_ONLY: '仅发布',
  UNPUBLISHED: '未发布',
  NOT_APPLICABLE: '不适用',
  NOT_PUBLISHED: '未发布',
  PARTIALLY_PUBLISHED: '部分上下文已发布',
  PUBLISH_STATE_UNAVAILABLE: '发布状态不可用'
})

const STEP_STRATEGY_LABELS = Object.freeze({
  BEFORE: '前置',
  REPLACE: '替代平台处理',
  AFTER: '后置'
})

const FORM_BUTTON_STEP_STRATEGY_LABELS = Object.freeze({
  BEFORE: '前置处理',
  REPLACE: '主处理',
  AFTER: '后置处理'
})

const INHERITANCE_MODE_LABELS = Object.freeze({
  INHERIT: '继承并追加',
  REPLACE: '替换上级',
  DISABLE: '禁用自定义'
})

const BUTTON_INHERITANCE_MODE_LABELS = Object.freeze({
  INHERIT: '继承并追加',
  REPLACE: '仅使用当前层',
  DISABLE: '清空事件链'
})

const EFFECTIVE_STATUS_LABELS = Object.freeze({
  EFFECTIVE: '已进入最终有效链',
  PARTIALLY_EFFECTIVE: '部分上下文生效',
  NOT_EFFECTIVE: '未进入最终有效链',
  NOT_APPLICABLE: '不适用',
  NOT_PUBLISHED: '未发布',
  ACTIVE: '已进入最终有效链',
  PARTIAL: '部分上下文生效',
  SHADOWED: '已被下级配置覆盖',
  INVALID_CHAIN: '最终有效链无效',
  PUBLISHED_VERSION_ACTIVE: '发布版本中已生效',
  PUBLISHED_VERSION_SHADOWED: '发布版本中被覆盖',
  DRAFT_STEP_NOT_PUBLISHED: '草稿步骤尚未发布',
  DISABLED: '绑定已停用',
  UNKNOWN: '生效状态待确认'
})

export const interfaceServiceReferenceStateOptions = Object.freeze([
  { label: '已引用', value: 'REFERENCED' },
  { label: '未引用', value: 'UNUSED' },
  { label: '全部', value: 'ALL' }
])

export const interfaceServiceReferenceOwnerOptions = Object.freeze([
  { label: '全部层级', value: '' },
  ...Object.entries(OWNER_TYPE_LABELS).map(([value, label]) => ({ label, value }))
])

export const interfaceServiceReferenceLifecycleOptions = Object.freeze([
  { label: '全部发布状态', value: '' },
  ...['DRAFT_ONLY', 'PUBLISHED_MATCH', 'PUBLISHED_CHANGED', 'PUBLISHED_ONLY', 'UNPUBLISHED']
    .map(value => ({ label: LIFECYCLE_LABELS[value], value }))
])

function normalizeCode(value, fallback = '') {
  return String(value || fallback).trim().toUpperCase()
}

/** 兼容直接传事件编码或完整引用对象，用于场景化显示通用枚举。 */
function contextEventCode(context) {
  return normalizeCode(
    context && typeof context === 'object' ? context.eventCode : context
  )
}

function isFormButtonContext(context) {
  return contextEventCode(context) === 'FORM_BUTTON_CLICK'
}

function isButtonTargetContext(context) {
  return Boolean(context && typeof context === 'object'
    && isFormButtonContext(context)
    && normalizeCode(context.targetType) === 'BUTTON')
}

function normalizedText(value) {
  return String(value || '').trim().toLowerCase()
}

/**
 * 兼容直接数组和常见集合包装，避免响应外层调整影响使用情况页。
 */
export function extractInterfaceServiceReferences(payload) {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  const rows = payload.references ?? payload.items ?? payload.list ?? payload.records
  return Array.isArray(rows) ? rows : []
}

/** 请求失败时移除该服务的缓存，避免把失败误解释为“零引用”。 */
export function omitInterfaceServiceReferenceCache(cache = {}, serviceId) {
  const next = cache && typeof cache === 'object' ? { ...cache } : {}
  delete next[String(serviceId ?? '')]
  return next
}

/**
 * 将后端的事件步骤引用补全为稳定的前端视图模型。
 *
 * 同一 binding 可能多次引用同一服务，因此行身份必须包含步骤位置，
 * 不能只使用 bindingId。
 */
export function normalizeInterfaceServiceReference(reference = {}, service = {}) {
  const ownerType = normalizeCode(reference.ownerType)
  const targetType = normalizeCode(reference.targetType, 'OWNER')
  const bindingId = String(reference.bindingId || reference.referenceId || '')
  const stepIndex = Number.isInteger(Number(reference.stepIndex))
    ? Number(reference.stepIndex)
    : 0
  const ownerId = String(reference.ownerId
    || (ownerType === 'FORM' ? reference.formId : '')
    || (ownerType === 'LIST' ? reference.listId : '')
    || reference.entityId
    || '')
  const ownerName = reference.ownerName
    || (ownerType === 'FORM' ? reference.formName : '')
    || (ownerType === 'LIST' ? reference.listName : '')
    || reference.entityName
    || ownerId
  const serviceId = String(reference.serviceId || service.id || '')

  return {
    ...reference,
    referenceId: String(reference.referenceId
      || `${bindingId || 'binding'}:${reference.stepCode || stepIndex}`),
    bindingId,
    stepIndex,
    serviceId,
    serviceCode: reference.serviceCode || service.sourceCode || '',
    serviceName: reference.serviceName || service.sourceName || '',
    ownerType,
    ownerId,
    ownerName,
    targetType,
    targetKey: String(reference.targetKey || ''),
    eventCode: normalizeCode(reference.eventCode),
    lifecycleStatus: normalizeCode(
      reference.lifecycleStatus || reference.publicationStatus,
      reference.published ? 'PUBLISHED_MATCH' : 'DRAFT_ONLY'
    ),
    bindingEnabled: reference.bindingEnabled !== false && reference.enabled !== false,
    stepStrategy: normalizeCode(reference.stepStrategy || reference.strategy, 'BEFORE'),
    effective: typeof reference.effective === 'boolean' ? reference.effective : null,
    effectiveStatus: normalizeCode(
      reference.effectiveStatus,
      reference.effective === true
        ? 'EFFECTIVE'
        : reference.effective === false
          ? 'NOT_EFFECTIVE'
          : 'UNKNOWN'
    ),
    effectiveContexts: Array.isArray(reference.effectiveContexts)
      ? reference.effectiveContexts
      : reference.effectiveContexts == null
        ? []
        : [reference.effectiveContexts],
    contexts: Array.isArray(reference.contexts)
      ? reference.contexts
      : reference.contexts == null
        ? []
        : [reference.contexts],
    inheritanceMode: normalizeCode(reference.inheritanceMode, 'INHERIT')
  }
}

/** 把平铺引用组装成“一个服务一组”的管理视图。 */
export function groupInterfaceServiceReferences(services = [], referencesByService = {}) {
  return (Array.isArray(services) ? services : []).map(service => {
    const serviceId = String(service?.id || '')
    const payload = referencesByService instanceof Map
      ? referencesByService.get(serviceId)
      : referencesByService[serviceId]
    const references = extractInterfaceServiceReferences(payload)
      .map(reference => normalizeInterfaceServiceReference(reference, service))
    return {
      service,
      serviceId,
      references,
      referenceCount: references.length,
      summary: payload && !Array.isArray(payload) && typeof payload === 'object'
        ? payload.summary || {}
        : {}
    }
  })
}

function serviceSearchText(group) {
  return normalizedText([
    group.service?.sourceName,
    group.service?.sourceCode,
    group.service?.providerCode
  ].join(' '))
}

function referenceSearchText(reference) {
  return normalizedText([
    reference.ownerName,
    reference.ownerId,
    reference.entityName,
    reference.entityCode,
    reference.formName,
    reference.formKey,
    reference.listName,
    reference.listKey,
    reference.targetKey,
    reference.eventCode,
    eventLabel(reference.eventCode),
    reference.operationName,
    reference.operationCode,
    reference.stepName,
    reference.stepCode
  ].join(' '))
}

/**
 * 搜索可命中服务本身或引用行；层级和发布状态始终只筛引用行。
 */
export function filterInterfaceServiceReferenceGroups(groups = [], filters = {}) {
  const keyword = normalizedText(filters.keyword)
  const serviceId = String(filters.serviceId || '')
  const ownerType = normalizeCode(filters.ownerType)
  const lifecycleStatus = normalizeCode(filters.lifecycleStatus)
  const referenceState = normalizeCode(filters.referenceState, 'REFERENCED')

  return groups.flatMap(group => {
    if (serviceId && group.serviceId !== serviceId) return []
    const serviceMatches = !keyword || serviceSearchText(group).includes(keyword)
    const references = group.references.filter(reference => {
      if (ownerType && reference.ownerType !== ownerType) return false
      if (lifecycleStatus && reference.lifecycleStatus !== lifecycleStatus) return false
      return serviceMatches || !keyword || referenceSearchText(reference).includes(keyword)
    })
    const hasAnyReference = group.references.length > 0
    const hasVisibleReference = references.length > 0

    if (referenceState === 'UNUSED') {
      if (hasAnyReference || ownerType || lifecycleStatus || !serviceMatches) return []
    } else if (referenceState === 'REFERENCED') {
      if (!hasVisibleReference) return []
    } else if ((ownerType || lifecycleStatus) && !hasVisibleReference) {
      return []
    } else if (keyword && !serviceMatches && !hasVisibleReference) {
      return []
    }

    return [{ ...group, references, visibleReferenceCount: references.length }]
  })
}

/**
 * 构造设计器深链。目标页可逐步消费 bindingId/targetType 等定位参数；
 * 现有表单设计器已能消费 settings=data-events&section=events。
 */
export function buildInterfaceServiceReferenceRoute(reference = {}) {
  const normalized = normalizeInterfaceServiceReference(reference)
  const sharedQuery = Object.fromEntries(Object.entries({
    bindingId: normalized.bindingId,
    eventCode: normalized.eventCode,
    targetType: normalized.targetType,
    targetKey: normalized.targetKey
  }).filter(([, value]) => value !== ''))

  if (normalized.ownerType === 'FORM') {
    const formId = String(normalized.formId || normalized.ownerId || '')
    if (!formId) return null
    return {
      name: 'EntityFormDesign',
      params: { id: formId },
      query: {
        ...(normalized.entityId ? { entityId: String(normalized.entityId) } : {}),
        settings: 'data-events',
        section: 'events',
        ...sharedQuery
      }
    }
  }
  if (normalized.ownerType === 'LIST') {
    const listId = String(normalized.listId || normalized.ownerId || '')
    if (!listId) return null
    return {
      name: 'EntityListConfigDesign',
      params: { id: listId },
      query: { events: '1', ...sharedQuery }
    }
  }
  if (normalized.ownerType === 'ENTITY') {
    const entityId = String(normalized.entityId || normalized.ownerId || '')
    if (!entityId) return null
    return {
      name: 'EntityDesign',
      params: { id: entityId },
      query: { tab: 'events', ...sharedQuery }
    }
  }
  return null
}

export function ownerTypeLabel(value) {
  const code = normalizeCode(value)
  return OWNER_TYPE_LABELS[code] || code || '-'
}

export function targetTypeLabel(reference = {}) {
  const code = normalizeCode(reference.targetType, 'OWNER')
  const label = TARGET_TYPE_LABELS[code] || code || '-'
  const target = reference.targetName || reference.targetKey
  return target ? `${label}：${target}` : label
}

export function eventLabel(value) {
  const code = normalizeCode(value)
  return EVENT_LABELS[code] || code || '-'
}

export function lifecycleStatusLabel(value) {
  const code = normalizeCode(value)
  return LIFECYCLE_LABELS[code] || code || '-'
}

export function lifecycleStatusTagType(value) {
  return {
    DRAFT_ONLY: 'warning',
    PUBLISHED_MATCH: 'success',
    PUBLISHED_CHANGED: 'warning',
    PUBLISHED_ONLY: 'info',
    UNPUBLISHED: 'warning'
  }[normalizeCode(value)] || 'info'
}

export function stepStrategyLabel(value, context = '') {
  const code = normalizeCode(value)
  const labels = isFormButtonContext(context)
    ? FORM_BUTTON_STEP_STRATEGY_LABELS
    : STEP_STRATEGY_LABELS
  return labels[code] || code || '-'
}

export function inheritanceModeLabel(value, context = {}) {
  const code = normalizeCode(value)
  const labels = isButtonTargetContext(context)
    ? BUTTON_INHERITANCE_MODE_LABELS
    : INHERITANCE_MODE_LABELS
  return labels[code] || code || '-'
}

export function inheritanceSourceLabel(value) {
  if (value && typeof value === 'object') {
    return formatEffectiveContexts(value) || '-'
  }
  const code = normalizeCode(value)
  if (!code) return '-'
  if (code === 'CURRENT' || code === 'CURRENT_OWNER') return '当前配置'
  if (code === 'INHERITED' || code === 'PARENT') return '继承上级'
  if (code.includes('ENTITY')) return '实体默认'
  if (code.includes('FORM')) return '表单覆盖'
  if (code.includes('LIST')) return '列表覆盖'
  if (code.includes('PLATFORM')) return '平台默认处理'
  return value
}

/**
 * 最终生效链来自激活发布版本；没有发布版本或发布态不可判定时，
 * 不能用设计态数据推测线上链路。
 */
export function isEffectiveChainAvailable(context = {}) {
  return context.activeReleasePresent === true
    && normalizeCode(context.publicationStatus) !== 'PUBLISH_STATE_UNAVAILABLE'
}

export function effectiveChainUnavailableReason(context = {}) {
  if (normalizeCode(context.publicationStatus) === 'PUBLISH_STATE_UNAVAILABLE') {
    return '尚无可用的已发布执行链（发布状态不可用）'
  }
  if (context.activeReleasePresent !== true) {
    return '尚无可用的已发布执行链（当前配置没有激活发布版本）'
  }
  return ''
}

/**
 * 根据 BEFORE/REPLACE/AFTER 还原可读的最终链。只有存在平台
 * 默认动作的事件才补齐平台节点；FORM_BUTTON_CLICK 的 REPLACE
 * 是唯一主处理，不虚构平台动作。
 */
export function buildEffectiveChainItems(context = {}, eventCode = '') {
  if (!isEffectiveChainAvailable(context)) return []
  const formButton = isFormButtonContext(eventCode || context)
  const steps = (Array.isArray(context.effectiveChain) ? context.effectiveChain : [])
    .map((step, index) => ({
      ...step,
      stepStrategy: normalizeCode(step.stepStrategy || step.strategy, 'BEFORE'),
      sourceLabel: inheritanceSourceLabel(step.inheritanceSource || step.ownerType),
      sortOrder: Number.isFinite(Number(step.stepOrder))
        ? Number(step.stepOrder)
        : index * 10
    }))
    .sort((left, right) => left.sortOrder - right.sortOrder)
  const before = steps.filter(step => step.stepStrategy === 'BEFORE')
  const replacements = steps.filter(step => step.stepStrategy === 'REPLACE')
  const after = steps.filter(step => step.stepStrategy === 'AFTER')
  const unclassified = steps.filter(step =>
    !['BEFORE', 'REPLACE', 'AFTER'].includes(step.stepStrategy))
  const toItem = step => ({
    ...step,
    kind: step.stepStrategy === 'REPLACE' ? 'REPLACE' : 'STEP',
    label: step.stepName || step.operationCode || step.stepCode || '未命名步骤',
    strategyLabel: stepStrategyLabel(step.stepStrategy, eventCode || context)
  })
  return [
    ...before.map(toItem),
    ...(replacements.length
      ? replacements.map(toItem)
      : (formButton
          ? []
          : [{ kind: 'PLATFORM', label: '平台默认处理', stepStrategy: 'PLATFORM' }])),
    ...after.map(toItem),
    ...unclassified.map(toItem)
  ]
}

/** 把有效上下文压缩成可读文本，同时容错字符串和对象两种后端表达。 */
export function formatEffectiveContexts(contexts) {
  const rows = Array.isArray(contexts)
    ? contexts
    : contexts == null
      ? []
      : [contexts]
  return rows.map(context => {
    if (typeof context === 'string' || typeof context === 'number') {
      return String(context)
    }
    if (!context || typeof context !== 'object') return ''
    const type = ownerTypeLabel(context.ownerType || context.configType)
    const name = context.ownerName
      || context.configName
      || context.formName
      || context.listName
      || context.entityName
      || context.ownerId
      || context.configId
      || ''
    const releaseVersion = context.releaseVersion == null
      ? ''
      : `v${context.releaseVersion}`
    const publicationStatus = lifecycleStatusLabel(context.publicationStatus)
    const effectiveStatus = effectiveStatusLabel(context.effectiveStatus)
    return [
      type === '-' ? '' : type,
      name,
      releaseVersion,
      publicationStatus === '-' ? '' : publicationStatus,
      effectiveStatus === EFFECTIVE_STATUS_LABELS.UNKNOWN ? '' : effectiveStatus
    ].filter(Boolean).join('·')
  }).filter(Boolean).join('、')
}

export function effectiveStateLabel(reference = {}) {
  const code = normalizeCode(reference.effectiveStatus, 'UNKNOWN')
  const statusLabel = effectiveStatusLabel(code)
  if (reference.bindingEnabled !== false) return statusLabel
  if (code === 'UNKNOWN' || code === 'DISABLED') return '草稿绑定已停用'
  return `${statusLabel}（草稿绑定已停用）`
}

export function effectiveStateTagType(reference = {}) {
  const code = normalizeCode(reference.effectiveStatus, 'UNKNOWN')
  const statusType = {
    EFFECTIVE: 'success',
    PARTIALLY_EFFECTIVE: 'warning',
    NOT_EFFECTIVE: 'info',
    NOT_APPLICABLE: 'info',
    NOT_PUBLISHED: 'info',
    ACTIVE: 'success',
    PARTIAL: 'warning',
    SHADOWED: 'info',
    INVALID_CHAIN: 'danger',
    PUBLISHED_VERSION_ACTIVE: 'success',
    PUBLISHED_VERSION_SHADOWED: 'info',
    DRAFT_STEP_NOT_PUBLISHED: 'warning',
    DISABLED: 'info',
    UNKNOWN: 'warning'
  }[code]
  if (statusType) return statusType
  return reference.bindingEnabled === false ? 'info' : 'warning'
}

export function effectiveStatusLabel(value) {
  const code = normalizeCode(value, 'UNKNOWN')
  return EFFECTIVE_STATUS_LABELS[code] || code
}
