import { safeParseConfig } from './config-runtime/index.js'

export const SUBFORM_PARAMETER_CONTRACT_VERSION = 1

const PARAMETER_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,99}$/

export function normalizeInputParameterSchema(value) {
  const source = safeParseConfig(value)
  const properties = source?.properties && typeof source.properties === 'object'
    && !Array.isArray(source.properties)
    ? source.properties
    : {}
  const required = Array.isArray(source?.required)
    ? source.required.map(item => String(item || '').trim()).filter(Boolean)
    : []
  return {
    type: 'object',
    properties: { ...properties },
    ...(required.length ? { required: [...new Set(required)] } : {})
  }
}

export function getInputParameterDefinitions(value) {
  const schema = normalizeInputParameterSchema(value)
  const required = new Set(schema.required || [])
  return Object.entries(schema.properties).map(([code, definition]) => {
    const item = definition && typeof definition === 'object'
      ? definition
      : {}
    return {
      code,
      name: item.title || code,
      type: String(item.type || 'string').toLowerCase(),
      required: required.has(code),
      defaultValue: editableDefaultValue(
        item.default,
        item.type
      ),
      description: item.description || ''
    }
  })
}

export function buildInputParameterSchema(definitions = []) {
  const properties = {}
  const required = []
  definitions.forEach(item => {
    const code = String(item?.code || '').trim()
    if (!PARAMETER_CODE_PATTERN.test(code)) return
    const definition = {
      type: String(item?.type || 'string').toLowerCase(),
      title: String(item?.name || code).trim() || code
    }
    if (item?.description) {
      definition.description = String(item.description).trim()
    }
    if (hasConfiguredDefault(item?.defaultValue)) {
      definition.default = normalizeInputParameterDefaultValue(
        item.defaultValue,
        definition.type
      )
    }
    properties[code] = definition
    if (item?.required === true) required.push(code)
  })
  return {
    type: 'object',
    properties,
    ...(required.length ? { required } : {})
  }
}

export function normalizeInputParameterDefaultValue(value, type) {
  const normalizedType = String(type || 'string').toLowerCase()
  if (!hasConfiguredDefault(value)) return undefined
  if (normalizedType === 'boolean') {
    if (value === true || value === false) return value
    if (String(value).toLowerCase() === 'true') return true
    if (String(value).toLowerCase() === 'false') return false
    return value
  }
  if (normalizedType === 'number' || normalizedType === 'integer') {
    if (typeof value === 'number') return value
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : value
  }
  if (normalizedType === 'object' || normalizedType === 'array') {
    if (typeof value !== 'string') return cloneValue(value)
    try {
      return JSON.parse(value)
    } catch {
      return value
    }
  }
  return String(value)
}

export function normalizeSubFormParameterContract(value) {
  const source = value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : {}
  return {
    version: Number(source.version || SUBFORM_PARAMETER_CONTRACT_VERSION),
    parameterMapping: normalizeMapping(source.parameterMapping),
    fieldInitializationMapping: normalizeMapping(
      source.fieldInitializationMapping
    )
  }
}

export function hasSubFormParameterContract(value) {
  const contract = normalizeSubFormParameterContract(value)
  return contract.version === SUBFORM_PARAMETER_CONTRACT_VERSION
    && (
      Object.keys(contract.parameterMapping).length > 0
      || Object.keys(contract.fieldInitializationMapping).length > 0
    )
}

export function resolveSubFormMapping(mapping, source) {
  const result = {}
  Object.entries(normalizeMapping(mapping)).forEach(([target, selector]) => {
    result[target] = resolveSelector(source, selector)
  })
  return result
}

export function resolveSubFormParameters(contractValue, source, schemaValue) {
  const contract = normalizeSubFormParameterContract(contractValue)
  if (contract.version !== SUBFORM_PARAMETER_CONTRACT_VERSION) return {}
  const schema = normalizeInputParameterSchema(schemaValue)
  const result = resolveSubFormMapping(contract.parameterMapping, source)
  Object.entries(schema.properties).forEach(([code, definition]) => {
    if (result[code] === undefined && definition
        && Object.prototype.hasOwnProperty.call(definition, 'default')) {
      result[code] = cloneValue(definition.default)
    }
  })
  return result
}

export function validateSubFormParameters(value, schemaValue) {
  const schema = normalizeInputParameterSchema(schemaValue)
  const required = new Set(schema.required || [])
  const errors = []
  Object.entries(schema.properties).forEach(([code, definition]) => {
    const current = value?.[code]
    if (required.has(code) && isEmptySubFormValue(current)) {
      errors.push(`${definition?.title || code}不能为空`)
      return
    }
    if (current === undefined || current === null || current === '') return
    if (!matchesSchemaType(current, definition?.type)) {
      errors.push(
        `${definition?.title || code}类型不正确，应为${schemaTypeLabel(definition?.type)}`
      )
    }
  })
  return errors
}

export function applySubFormFieldInitialization(
  row,
  contractValue,
  source,
  blockedFields = []
) {
  if (!row || typeof row !== 'object' || Array.isArray(row)) return false
  const contract = normalizeSubFormParameterContract(contractValue)
  if (contract.version !== SUBFORM_PARAMETER_CONTRACT_VERSION) return false
  const blocked = new Set(
    (blockedFields || []).map(item => String(item || '').trim()).filter(Boolean)
  )
  const values = resolveSubFormMapping(
    contract.fieldInitializationMapping,
    source
  )
  let changed = false
  Object.entries(values).forEach(([fieldCode, value]) => {
    if (blocked.has(fieldCode) || !isEmptySubFormValue(row[fieldCode])
        || value === undefined) {
      return
    }
    row[fieldCode] = cloneValue(value)
    changed = true
  })
  return changed
}

export function buildSubFormParentContext(context = {}) {
  const record = context?.record
  if (record?.data && typeof record.data === 'object') {
    return {
      recordId: context.recordId || record.id || record.data?.id || null,
      entityId: context.entityDefinition?.id
        || context.entityId
        || context.form?.entityId
        || null,
      formId: context.form?.id || context.formId || null,
      data: record.data
    }
  }
  const explicitParent = context?.parent
  if (explicitParent?.data && typeof explicitParent.data === 'object') {
    return {
      recordId: explicitParent.recordId || explicitParent.data?.id || null,
      entityId: explicitParent.entityId || context.entityDefinition?.id || null,
      formId: explicitParent.formId || context.form?.id || null,
      data: explicitParent.data
    }
  }
  const data = record && typeof record === 'object' ? record : {}
  return {
    recordId: context.recordId || record?.id || data?.id || null,
    entityId: context.entityDefinition?.id
      || context.entityId
      || context.form?.entityId
      || null,
    formId: context.form?.id || context.formId || null,
    data
  }
}

export function getPublishedFormParameterSchema(snapshotDocument) {
  const snapshot = safeParseConfig(snapshotDocument)
  const viewConfig = safeParseConfig(snapshot?.form?.viewConfig)
  return normalizeInputParameterSchema(viewConfig.inputParameterSchema)
}

export function getPublishedFormFields(snapshotDocument) {
  const snapshot = safeParseConfig(snapshotDocument)
  if (Array.isArray(snapshot.legacyFields)) {
    return snapshot.legacyFields.map(normalizePublishedField)
  }
  if (!Array.isArray(snapshot.nodes)) return []
  return snapshot.nodes
    .filter(node => ['FIELD', 'SUB_FORM', 'REPEATER'].includes(
      String(node?.nodeType || '').toUpperCase()
    ))
    .map(node => {
      const props = safeParseConfig(node.propsDocument || node.props)
      return normalizePublishedField({
        id: node.id,
        fieldId: props.fieldId,
        fieldCode: props.fieldCode || node.nodeKey,
        fieldName: props.label || props.fieldName || node.nodeKey,
        fieldType: props.fieldType || node.nodeType,
        componentType: props.componentType,
        isReadonly: props.readonly,
        isSystem: props.isSystem
      })
    })
}

export function isEmptySubFormValue(value) {
  return value === undefined
    || value === null
    || value === ''
    || (Array.isArray(value) && value.length === 0)
}

function normalizePublishedField(field) {
  return {
    ...field,
    fieldCode: field.fieldCode || field.fieldKey || '',
    fieldName: field.fieldLabel || field.fieldName || field.fieldCode || '',
    fieldType: String(field.fieldType || field.componentType || 'STRING')
      .toUpperCase(),
    readonly: field.isReadonly === true || field.isReadonly === 1,
    system: field.isSystem === true || field.isSystem === 1
  }
}

function normalizeMapping(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(
    Object.entries(value)
      .map(([key, selector]) => [String(key || '').trim(), selector])
      .filter(([key, selector]) => key && selector !== undefined)
  )
}

function resolveSelector(source, selector) {
  if (selector && typeof selector === 'object' && !Array.isArray(selector)
      && Object.prototype.hasOwnProperty.call(selector, 'literal')) {
    return cloneValue(selector.literal)
  }
  return String(selector || '').split('.').filter(Boolean)
    .reduce((current, key) => current?.[key], source)
}

function matchesSchemaType(value, type) {
  switch (String(type || 'string').toLowerCase()) {
    case 'number':
      return typeof value === 'number' && Number.isFinite(value)
    case 'integer':
      return Number.isInteger(value)
    case 'boolean':
      return typeof value === 'boolean'
    case 'object':
      return value && typeof value === 'object' && !Array.isArray(value)
    case 'array':
      return Array.isArray(value)
    default:
      return typeof value === 'string'
  }
}

function schemaTypeLabel(type) {
  return {
    string: '文本',
    number: '数字',
    integer: '整数',
    boolean: '布尔值',
    object: '对象',
    array: '数组'
  }[String(type || 'string').toLowerCase()] || '文本'
}

function editableDefaultValue(value, type) {
  if (value === undefined || value === null) return ''
  const normalizedType = String(type || 'string').toLowerCase()
  if ((normalizedType === 'object' || normalizedType === 'array')
      && typeof value === 'object') {
    return JSON.stringify(value)
  }
  return value
}

function hasConfiguredDefault(value) {
  return value !== undefined && value !== null && value !== ''
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
