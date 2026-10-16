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
  'SUB_LIST',
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
    .find(isQuickFillStep)
  return Array.isArray(step?.outputMapping)
    ? step.outputMapping.map(normalizeMapping)
    : []
}

/** 高级编辑中改为接口调用或条件步骤后，已不属于快捷编辑器能够完整表达的配置。 */
function isQuickFillStep(step) {
  return step?.stepCode === ENTITY_SELECTION_FILL_STEP_CODE
    && !step.extensionId && !step.serviceId
    && String(step.strategy || 'AFTER').toUpperCase() === 'AFTER'
    && Object.keys(step.condition || {}).length === 0
    && Array.isArray(step.outputMapping)
    && step.outputMapping.every(row => String(row.sourcePath || '').startsWith('selection.'))
}

export function mergeEntitySelectionMappings(steps, mappings) {
  const source = Array.isArray(steps) ? steps : []
  const normalizedMappings = (mappings || [])
    .filter(item => item.sourcePath && item.targetPath)
    .map(normalizeMapping)
  const fillStep = normalizedMappings.length ? {
    stepCode: ENTITY_SELECTION_FILL_STEP_CODE,
    name: '选择后回填',
    strategy: 'AFTER',
    failurePolicy: 'STOP',
    outputMapping: normalizedMappings
  } : null
  // 在原位置更新快捷步骤，保留接口步骤及相对顺序，避免每次快捷保存都改变执行结果。
  const preserved = []
  let replaced = false
  source.forEach(step => {
    if (step?.stepCode !== ENTITY_SELECTION_FILL_STEP_CODE) {
      preserved.push(step)
    } else if (!isQuickFillStep(step)) {
      // 用户可能已把快捷步骤改成接口调用。仅解除快捷管理标记，完整保留高级配置，
      // 防止重新配置/清空快捷回填时悄悄删除接口、执行条件或 data.* 返回值映射。
      const { stepCode, ...customStep } = step
      preserved.push(customStep)
    } else if (!replaced) {
      if (fillStep) preserved.push(fillStep)
      replaced = true
    }
  })
  if (fillStep && !replaced) preserved.push(fillStep)
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
