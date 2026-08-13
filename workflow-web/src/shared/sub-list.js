import { safeParseConfig } from './config-runtime/index.js'
import { buildSubFormParentContext } from './subform-parameter-contract.js'

export const SUB_LIST_RUNTIME_SCENE = 'EMBEDDED'
export const SUB_LIST_PARAMETER_CONTRACT_VERSION = 1
export const SUB_LIST_ACTION_DISPLAY_VERSION = 2

const FILTER_OPERATORS = new Set([
  'EQ',
  'NE',
  'LIKE',
  'GT',
  'GE',
  'LT',
  'LE',
  'IN',
  'NOT_IN'
])

export function normalizeListScenes(value) {
  if (Array.isArray(value)) {
    return value
      .map(item => String(item || '').trim().toUpperCase())
      .filter(Boolean)
  }
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    return normalizeListScenes(JSON.parse(value))
  } catch {
    return value
      .split(',')
      .map(item => item.trim().toUpperCase())
      .filter(Boolean)
  }
}

export function supportsSubListEmbedding(list) {
  const scenes = normalizeListScenes(list?.allowedScenes)
  return scenes.length === 0 || scenes.includes(SUB_LIST_RUNTIME_SCENE)
}

export function isPublishedSubListOption(list) {
  return Boolean(
    list?.listKey
    && list.activeReleaseId
    && Number(list.publishedVersion) > 0
    && supportsSubListEmbedding(list)
  )
}

export function resolveSubListTargetSelection(
  field,
  entity,
  { resetListKey = false } = {}
) {
  const target = Array.isArray(entity) ? entity[0] : entity
  return {
    refEntityId: target?.id || field?.refEntityId || '',
    refEntityType: 'CUSTOM',
    refListKey: resetListKey ? '' : (field?.refListKey || '')
  }
}

export function isParentEntityReferenceTarget(
  targetField,
  parentEntityId
) {
  const fieldType = String(
    targetField?.fieldType || targetField?.componentType || ''
  ).trim().toUpperCase()
  const targetRefEntityId = String(
    targetField?.refEntityId || ''
  ).trim()
  const normalizedParentEntityId = String(parentEntityId || '').trim()
  return [
    'REFERENCE',
    'ENTITY',
    'ENTITY_SELECTOR'
  ].includes(fieldType)
    && Boolean(targetRefEntityId)
    && targetRefEntityId === normalizedParentEntityId
}

export function resolveDefaultSubListParameterSource(
  targetField,
  parentFields = [],
  parentEntityId = ''
) {
  if (isParentEntityReferenceTarget(targetField, parentEntityId)) {
    return 'parent.recordId'
  }
  const targetFieldCode = String(
    targetField?.fieldCode || ''
  ).trim()
  const parentField = parentFields.find(field =>
    String(field?.fieldCode || '').trim() === targetFieldCode
  ) || parentFields[0]
  return parentField?.fieldCode
    ? `parent.data.${parentField.fieldCode}`
    : 'parent.recordId'
}

export function enforceSubListParentReferenceMapping(
  mapping,
  targetField,
  parentEntityId
) {
  const source = mapping && typeof mapping === 'object'
    ? { ...mapping }
    : {}
  if (!isParentEntityReferenceTarget(targetField, parentEntityId)) {
    return source
  }
  return {
    ...source,
    source: 'parent.recordId',
    operator: 'EQ',
    required: true,
    useForQuery: true
  }
}

export function isSubListTargetFieldWritable(field) {
  const fieldCode = String(field?.fieldCode || '').trim()
  if (!fieldCode
      || field?.runtimeWritable === false
      || field?.isReadonly === true
      || field?.isReadonly === 1) {
    return false
  }
  const isSystemField =
    field?.isSystem === true || field?.isSystem === 1
  return !isSystemField || fieldCode === 'name'
}

export function enforceSubListParameterUsage(
  mapping,
  targetField
) {
  const source = mapping && typeof mapping === 'object'
    ? { ...mapping }
    : {}
  const queryable = targetField?.queryable === true
  const writable = targetField?.writable === true
  let useForQuery = queryable && source.useForQuery === true
  let useForCreate = writable && source.useForCreate === true

  if (!useForQuery && !useForCreate) {
    if (queryable) {
      useForQuery = true
    } else if (writable) {
      useForCreate = true
    }
  }

  return {
    ...source,
    required:
      useForQuery || useForCreate
        ? source.required !== false
        : false,
    useForQuery,
    useForCreate
  }
}

export function normalizeSubListDisplayConfig(value) {
  const source = safeParseConfig(value)
  const actionDisplayVersion = Number(source?.actionDisplayVersion || 0)
  const usesLegacyActionDisplay =
    actionDisplayVersion < SUB_LIST_ACTION_DISPLAY_VERSION
  return {
    ...source,
    actionDisplayVersion,
    showSearch: source?.showSearch !== false,
    showPagination: source?.showPagination !== false,
    showToolbar: usesLegacyActionDisplay
      ? true
      : source?.showToolbar !== false,
    showRowActions: usesLegacyActionDisplay
      ? true
      : source?.showRowActions !== false,
    pageSize: positiveNumber(source?.pageSize, 10),
    maxHeight: positiveNumber(source?.maxHeight, 420)
  }
}

export function normalizeSubListParameterContract(value) {
  const source = safeParseConfig(value)
  const mappings = Array.isArray(source?.mappings)
    ? source.mappings
    : []
  return {
    version: Number(
      source?.version || SUB_LIST_PARAMETER_CONTRACT_VERSION
    ),
    mappings: mappings
      .map(normalizeSubListParameterMapping)
      .filter(Boolean)
  }
}

export function resolveSubListParameterContract(
  contractValue,
  runtimeContext = {}
) {
  const contract = normalizeSubListParameterContract(contractValue)
  if (contract.version !== SUB_LIST_PARAMETER_CONTRACT_VERSION) {
    return emptySubListParameterResolution()
  }

  const parent = buildSubFormParentContext(runtimeContext)
  const source = {
    parent,
    context: runtimeContext,
    params: runtimeContext?.params
      || runtimeContext?.parameters
      || {}
  }
  const parameters = {}
  const queryFilters = {}
  const createValues = {}
  const missingRequired = []

  contract.mappings.forEach(mapping => {
    const value = resolveSelector(source, mapping.source)
    if (isEmptySubListParameterValue(value)) {
      if (mapping.required
          && (mapping.useForQuery || mapping.useForCreate)) {
        missingRequired.push({
          targetField: mapping.targetField,
          targetFieldName:
            mapping.targetFieldName || mapping.targetField
        })
      }
      return
    }

    parameters[mapping.targetField] = cloneValue(value)
    if (mapping.useForQuery) {
      queryFilters[mapping.targetField] = cloneValue(value)
      queryFilters[`${mapping.targetField}_op`] = mapping.operator
    }
    if (mapping.useForCreate) {
      createValues[mapping.targetField] = cloneValue(value)
    }
  })

  return {
    parameters,
    queryFilters,
    createValues,
    missingRequired
  }
}

export function isEmptySubListParameterValue(value) {
  return value === undefined
    || value === null
    || value === ''
    || (Array.isArray(value) && value.length === 0)
}

function normalizeSubListParameterMapping(mapping) {
  if (!mapping || typeof mapping !== 'object' || Array.isArray(mapping)) {
    return null
  }
  const targetField = String(
    mapping.targetField || mapping.targetFieldCode || ''
  ).trim()
  if (!targetField) return null
  const operator = String(mapping.operator || 'EQ').toUpperCase()
  return {
    targetField,
    targetFieldName: String(
      mapping.targetFieldName || mapping.targetFieldLabel || ''
    ).trim(),
    source: normalizeSource(mapping.source),
    operator: FILTER_OPERATORS.has(operator) ? operator : 'EQ',
    required: mapping.required !== false,
    useForQuery: mapping.useForQuery !== false,
    useForCreate: mapping.useForCreate !== false
  }
}

function normalizeSource(source) {
  if (source && typeof source === 'object' && !Array.isArray(source)
      && Object.prototype.hasOwnProperty.call(source, 'literal')) {
    return { literal: cloneValue(source.literal) }
  }
  return String(source || '').trim()
}

function resolveSelector(source, selector) {
  if (selector && typeof selector === 'object' && !Array.isArray(selector)
      && Object.prototype.hasOwnProperty.call(selector, 'literal')) {
    return cloneValue(selector.literal)
  }
  return String(selector || '')
    .split('.')
    .filter(Boolean)
    .reduce((current, key) => current?.[key], source)
}

function emptySubListParameterResolution() {
  return {
    parameters: {},
    queryFilters: {},
    createValues: {},
    missingRequired: []
  }
}

function positiveNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : fallback
}

function cloneValue(value) {
  if (value === undefined) return undefined
  if (typeof structuredClone === 'function') {
    try {
      return structuredClone(value)
    } catch {
      // Fall through for values unsupported by structuredClone.
    }
  }
  if (value && typeof value === 'object') {
    return JSON.parse(JSON.stringify(value))
  }
  return value
}
