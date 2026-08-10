export const LIST_COLUMN_TEMPLATE_TYPE = 'LIST_COLUMN_GROUP'

const TEMPLATE_IDENTITY_KEYS = new Set([
  'id',
  'fieldId',
  'fieldCode',
  'fieldName',
  'sortOrder',
  'orderKey',
  'templateId',
  'templateVersion',
  'localOverridesDocument',
  'revision'
])

const DEFAULT_FIELD_CONFIG = Object.freeze({
  showInList: true,
  isQuery: false,
  queryType: 'EQ',
  width: 160,
  align: 'left',
  dataSourceType: 'ENTITY_FIELD',
  dataSourceId: null,
  dataSourceOperationCode: null,
  renderComponent: 'DefaultText',
  formatter: '',
  dataSourceConfig: '',
  queryConfig: '',
  columnConfig: JSON.stringify({
    fixed: '',
    minWidth: 100,
    showOverflowTooltip: true
  }),
  renderConfig: JSON.stringify({
    emptyText: '-'
  })
})

export function createListColumnTemplateEditor(seed = {}) {
  const field = {
    ...DEFAULT_FIELD_CONFIG,
    ...sanitizeTemplateFieldConfig(seed.field || {})
  }
  const renderConfig = {
    emptyText: '-',
    ...parseObjectConfig(field.renderConfig)
  }
  renderConfig.labelMap = normalizeMappingObject(renderConfig.labelMap)
  renderConfig.statusMap = normalizeMappingObject(renderConfig.statusMap)
  const metadata = seed.metadata && typeof seed.metadata === 'object'
    ? seed.metadata
    : {}

  return {
    id: seed.id || '',
    templateKey: seed.templateKey || '',
    templateName: seed.templateName || '',
    sampleValue: metadata.sampleValue ?? '示例值',
    showInList: field.showInList !== false,
    isQuery: field.isQuery === true,
    queryType: field.queryType || 'EQ',
    width: Number(field.width) > 0 ? Number(field.width) : 160,
    align: field.align || 'left',
    dataSourceType: field.dataSourceType || 'ENTITY_FIELD',
    dataSourceId: field.dataSourceId || '',
    dataSourceOperationCode: field.dataSourceOperationCode || '',
    renderComponent: field.renderComponent || 'DefaultText',
    formatter: field.formatter || '',
    dataSourceConfig: parseObjectConfig(field.dataSourceConfig),
    queryConfig: {
      componentType: '',
      placeholder: '',
      defaultValue: '',
      ...parseObjectConfig(field.queryConfig)
    },
    columnConfig: {
      fixed: '',
      minWidth: 100,
      showOverflowTooltip: true,
      ...parseObjectConfig(field.columnConfig)
    },
    renderConfig,
    baseField: field
  }
}

export function parseListColumnTemplateSnapshot(snapshotDocument) {
  const snapshot = parseObjectConfig(snapshotDocument)
  const field = snapshot.field && typeof snapshot.field === 'object'
    ? snapshot.field
    : snapshot
  return {
    metadata: snapshot.metadata && typeof snapshot.metadata === 'object'
      ? snapshot.metadata
      : {},
    field: sanitizeTemplateFieldConfig(field)
  }
}

export function applyListColumnTemplateSnapshot(target, snapshotDocument) {
  const parsed = parseListColumnTemplateSnapshot(snapshotDocument)
  Object.assign(target, parsed.field, {
    templateId: null,
    templateVersion: null,
    localOverridesDocument: ''
  })
  return target
}

export function buildListColumnTemplateSnapshot(editor) {
  const field = sanitizeTemplateFieldConfig({
    ...(editor.baseField || {}),
    showInList: editor.showInList === true,
    isQuery: editor.isQuery === true,
    queryType: editor.queryType || 'EQ',
    width: Number(editor.width) > 0 ? Number(editor.width) : 160,
    align: editor.align || 'left',
    dataSourceType: editor.dataSourceType || 'ENTITY_FIELD',
    dataSourceId: editor.dataSourceId || null,
    dataSourceOperationCode: editor.dataSourceId
      ? editor.dataSourceOperationCode || null
      : null,
    renderComponent: editor.renderComponent || 'DefaultText',
    formatter: editor.formatter || '',
    dataSourceConfig: stringifyObjectConfig(editor.dataSourceConfig),
    queryConfig: stringifyObjectConfig(editor.queryConfig),
    columnConfig: stringifyObjectConfig(editor.columnConfig),
    renderConfig: stringifyObjectConfig(editor.renderConfig)
  })

  return {
    schemaVersion: 1,
    metadata: {
      sampleValue: editor.sampleValue ?? ''
    },
    field
  }
}

export function sanitizeTemplateFieldConfig(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(
    Object.entries(value)
      .filter(([key]) => !TEMPLATE_IDENTITY_KEYS.has(key))
  )
}

export function mappingObjectToRows(value) {
  const source = value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : {}
  return Object.entries(source).map(([key, mappedValue]) => ({
    key,
    value: mappedValue == null ? '' : String(mappedValue)
  }))
}

export function mappingRowsToObject(rows = []) {
  return rows.reduce((result, row) => {
    const key = String(row?.key || '').trim()
    const value = row?.value
    if (key && value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
    return result
  }, {})
}

function normalizeMappingObject(value) {
  return mappingRowsToObject(mappingObjectToRows(value))
}

export function describeListColumnTemplate(editor) {
  const parts = []
  if (editor.showInList) {
    parts.push(`${editor.width || 160}px`)
    parts.push({
      left: '左对齐',
      center: '居中',
      right: '右对齐'
    }[editor.align] || '左对齐')
  } else {
    parts.push('不显示')
  }
  if (editor.isQuery) parts.push(`查询 ${editor.queryType || 'EQ'}`)
  if (editor.renderComponent) parts.push(editor.renderComponent)
  if (editor.dataSourceType && editor.dataSourceType !== 'ENTITY_FIELD') {
    parts.push(editor.dataSourceType)
  }
  return parts.join(' · ')
}

function parseObjectConfig(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) {
    return clone(value)
  }
  if (typeof value !== 'string') return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

function stringifyObjectConfig(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return ''
  const entries = Object.entries(value).filter(([, item]) =>
    item !== undefined && item !== null && item !== ''
  )
  return entries.length ? JSON.stringify(Object.fromEntries(entries)) : ''
}

function clone(value) {
  if (typeof structuredClone === 'function') return structuredClone(value)
  return JSON.parse(JSON.stringify(value))
}
