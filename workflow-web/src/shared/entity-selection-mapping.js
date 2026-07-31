export const ENTITY_SELECTION_FILL_STEP_CODE = 'ENTITY_SELECTION_FILL'

const TEXT_TYPES = new Set([
  'STRING',
  'TEXT',
  'RICH_TEXT',
  'SELECT',
  'RADIO',
  'USER',
  'DEPT',
  'ROLE',
  'GROUP',
  'REFERENCE'
])
const NUMERIC_TYPES = new Set([
  'INTEGER',
  'LONG',
  'DECIMAL',
  'DOUBLE',
  'NUMBER'
])
const DATE_TYPES = new Set(['DATE', 'DATETIME'])
const COLLECTION_TYPES = new Set([
  'MULTI_SELECT',
  'CHECKBOX',
  'MULTI_REFERENCE'
])
const NON_TARGET_TYPES = new Set([
  'SECTION',
  'GRID',
  'TAB_SET',
  'TAB',
  'COLLAPSE',
  'SUB_FORM',
  'SUB_FORM_LIST',
  'REPEATER',
  'ACTION_SLOT'
])

const normalizeType = value => String(value || 'STRING').toUpperCase()

export function resolveEntitySelectionRefConfig(field = {}) {
  let componentProps = field?.componentProps || {}
  if (typeof componentProps === 'string') {
    try {
      componentProps = JSON.parse(componentProps)
    } catch {
      componentProps = {}
    }
  }
  return {
    refEntityType: String(
      field?.refEntityType
      || componentProps?.refConfig?.refEntityType
      || 'CUSTOM'
    ).toUpperCase(),
    refEntityId: String(
      field?.refEntityId
      || componentProps?.refConfig?.refEntityId
      || ''
    )
  }
}

export function resolveRuntimeEntitySelectionReference(config = {}) {
  if (String(config.entityType || '').toUpperCase() !== 'CUSTOM') {
    return {
      entityCode: '',
      refEntityId: ''
    }
  }
  const entityCode = String(
    config.runtimeEntityCode || config.entityCode || ''
  ).trim()
  return {
    entityCode,
    refEntityId: entityCode
      ? ''
      : String(config.refEntityId || '').trim()
  }
}

export function isPersistedEntitySelectionField(field) {
  if (!field) return false
  if (Number(field.revision || 0) > 0) return true
  const stableId = String(field.id || field.nodeId || '').trim()
  return Boolean(stableId)
    && !stableId.startsWith('node_')
    && !stableId.startsWith('legacy_')
}

export function parseBindingSteps(binding) {
  const source = binding?.stepsDocument || binding?.steps || []
  if (Array.isArray(source)) return source
  if (!source) return []
  try {
    const parsed = JSON.parse(source)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function entitySelectionMappings(binding) {
  const step = parseBindingSteps(binding)
    .find(item => item?.stepCode === ENTITY_SELECTION_FILL_STEP_CODE)
  return Array.isArray(step?.outputMapping)
    ? step.outputMapping.map(normalizeMapping)
    : []
}

export function mergeEntitySelectionMappings(steps, mappings) {
  const preserved = (Array.isArray(steps) ? steps : [])
    .filter(item => item?.stepCode !== ENTITY_SELECTION_FILL_STEP_CODE)
  const normalizedMappings = (mappings || [])
    .filter(item => item.sourcePath && item.targetPath)
    .map(normalizeMapping)
  if (normalizedMappings.length) {
    preserved.push({
      stepCode: ENTITY_SELECTION_FILL_STEP_CODE,
      name: '选择后回填',
      strategy: 'AFTER',
      failurePolicy: 'STOP',
      outputMapping: normalizedMappings
    })
  }
  return preserved.map((step, index) => ({
    ...step,
    order: (index + 1) * 10
  }))
}

export function buildEntitySelectionSourceFields(
  refEntityType,
  entityFields = []
) {
  const standard = [
    { fieldCode: 'id', fieldName: '数据 ID', fieldType: 'STRING' },
    { fieldCode: 'name', fieldName: '数据名称', fieldType: 'STRING' },
    { fieldCode: 'code', fieldName: '数据编码', fieldType: 'STRING' },
    { fieldCode: 'dataNo', fieldName: '数据编号', fieldType: 'STRING' },
    { fieldCode: 'title', fieldName: '数据标题', fieldType: 'STRING' },
    { fieldCode: 'status', fieldName: '状态', fieldType: 'STRING' }
  ]
  const result = standard.map(field => ({
    label: field.fieldName,
    value: `selection.${field.fieldCode}`,
    fieldCode: field.fieldCode,
    fieldType: field.fieldType,
    standard: true
  }))
  if (String(refEntityType || '').toUpperCase() !== 'CUSTOM') {
    return result.filter(item =>
      ['id', 'name', 'code', 'status'].includes(item.fieldCode))
  }
  entityFields
    .filter(field => field?.fieldCode && !field?.isSystem)
    .forEach(field => {
      result.push({
        label: field.fieldName || field.fieldCode,
        value: `selection.data.${field.fieldCode}`,
        fieldCode: field.fieldCode,
        fieldType: normalizeType(field.fieldType),
        standard: false
      })
    })
  return result
}

export function buildEntitySelectionTargetFields(
  formFields = [],
  selectedFieldCode = ''
) {
  const seen = new Set()
  return formFields
    .filter(field => {
      const fieldCode = String(field?.fieldCode || '')
      const fieldType = normalizeType(
        field?.fieldType || field?.componentType)
      const bindingType = String(
        field?.bindingType || ''
      ).toUpperCase()
      const boundToEntity = Boolean(field?.fieldId)
        || bindingType === 'ENTITY_FIELD'
      if (!fieldCode
          || fieldCode === String(selectedFieldCode)
          || NON_TARGET_TYPES.has(fieldType)
          || !boundToEntity
          || seen.has(fieldCode)) {
        return false
      }
      seen.add(fieldCode)
      return true
    })
    .map(field => ({
      label: field.fieldLabel || field.fieldName || field.fieldCode,
      value: `form.${field.fieldCode}`,
      fieldCode: field.fieldCode,
      fieldType: normalizeType(
        field.fieldType || field.componentType)
    }))
}

export function areEntitySelectionTypesCompatible(
  sourceType,
  targetType
) {
  const source = normalizeType(sourceType)
  const target = normalizeType(targetType)
  if (source === target) return true
  return TEXT_TYPES.has(source) && TEXT_TYPES.has(target)
    || NUMERIC_TYPES.has(source) && NUMERIC_TYPES.has(target)
    || DATE_TYPES.has(source) && DATE_TYPES.has(target)
    || COLLECTION_TYPES.has(source) && COLLECTION_TYPES.has(target)
}

export function normalizeMapping(row = {}) {
  return {
    sourcePath: row.sourcePath || '',
    targetPath: row.targetPath || '',
    sourceType: row.sourceType
      ? normalizeType(row.sourceType) : '',
    targetType: row.targetType
      ? normalizeType(row.targetType) : '',
    overwrite: String(row.overwrite || 'ALWAYS').toUpperCase(),
    clearOnEmpty: row.clearOnEmpty !== false,
    transform: String(row.transform || 'IDENTITY').toUpperCase(),
    separator: row.separator || ','
  }
}
