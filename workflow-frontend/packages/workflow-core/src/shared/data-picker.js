import { safeParseConfig } from './config-runtime/index.js'
import { applyRuntimeFieldEffects } from './form-runtime/fieldEvents.js'
import { resolveListFilterParameters } from './page-parameters.js'
import { buildListRequestFilters, listQueryDefaults } from './list-query-filters.js'

export const DATA_PICKER_USAGES = Object.freeze(['FIELD_PICKER_SCHEMA', 'FIELD_PICKER_QUERY', 'FIELD_PICKER_RESOLVE'])
const identifier = /^[A-Za-z][A-Za-z0-9_]{0,99}$/
const unsafe = new Set(['__proto__', 'prototype', 'constructor'])
const types = new Set(['STRING', 'TEXT', 'INTEGER', 'LONG', 'DECIMAL', 'DOUBLE', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME', 'RICH_TEXT'])
const nativeComponents = new Map(Object.entries({ STRING: 'input', TEXT: 'textarea', INTEGER: 'number',
  LONG: 'number', DECIMAL: 'number', DATE: 'date', DATETIME: 'datetime', BOOLEAN: 'switch', RICH_TEXT: 'rich_text' }))
const integers = ['INTEGER', 'LONG']
const numbers = [...integers, 'DECIMAL', 'DOUBLE', 'NUMBER']
const integerText = /^[+-]?\d+$/
const decimalText = /^[+-]?\d+(?:\.\d+)?(?:[eE][+-]?\d{1,4})?$/
const stringFormats = new Map(Object.entries({ date: 'DATE', 'date-time': 'DATETIME', html: 'RICH_TEXT', int64: 'LONG', decimal: 'DECIMAL' }))
const fail = message => { throw new Error(`数据选择器：${message}`) }
const fieldCode = code => typeof code === 'string' && identifier.test(code) && !unsafe.has(code)
const empty = value => value == null || value === ''

export const DEFAULT_PICKER_SCHEMA = Object.freeze({
  schemaVersion: 'id-name-v1', valueField: 'id', labelField: 'name',
  fields: [{ code: 'id', label: 'ID', type: 'STRING' }, { code: 'name', label: '名称', type: 'STRING' }],
  columns: [{ field: 'name', label: '名称' }]
})

export const isEntityListPicker = config => config.pickerSource === 'ENTITY_LIST'
export const pickerSchemaSource = (config, hasDescriptionBinding = false) =>
  config.pickerSchemaSource || (config.pickerSchema || hasDescriptionBinding ? 'INTERFACE' : 'DEFAULT')

/** 键只用于定位候选。历史 STRING、SELECT 未声明模式时仍用键回显，新增类型默认写入业务值。 */
export function pickerWriteMode(config, fieldType) {
  const type = String(fieldType || '').toUpperCase()
  return config.pickerValueMode || (type !== 'STRING' && nativeComponents.has(type) ? 'FIELD' : 'KEY')
}

export function pickerNativeComponent(field) {
  return pickerWriteMode(safeParseConfig(field.componentProps), field.fieldType) === 'FIELD'
    ? nativeComponents.get(String(field.fieldType).toUpperCase()) : undefined
}

export function pickerWriteTypeCompatible(source, target) {
  const type = String(target || '').toUpperCase()
  if (!nativeComponents.has(type)) return false
  if (type === 'STRING' || type === 'TEXT') return ['STRING', 'TEXT'].includes(source)
  if (integers.includes(type)) return integers.includes(source)
  if (type === 'DECIMAL') return numbers.includes(source)
  return type === source
}

const preciseNumber = (value, type) => typeof value === 'string'
  ? value.length <= 500 && (integers.includes(type) ? integerText : decimalText).test(value)
  : typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= Number.MAX_SAFE_INTEGER

/** 长整数和小数使用字符串跨越浏览器的 Number 边界，由实体字段按原数据库类型存储。 */
export function pickerNumericValue(value, fieldType) {
  if (empty(value)) return null
  const type = String(fieldType).toUpperCase()
  if (!preciseNumber(value, type)) fail('数值格式无效或超出安全精度')
  if (type === 'DECIMAL') return String(value)
  if (!integerText.test(String(value))) fail('取值必须为整数')
  const integer = BigInt(value)
  if (type === 'LONG') {
    if (integer < -(1n << 63n) || integer >= 1n << 63n) fail('取值超出长整数范围')
    return String(integer)
  }
  if (integer < -2147483648n || integer > 2147483647n) fail('取值超出整数范围')
  return Number(integer)
}

/** 日期使用实体字段的本地日期/时间格式，ISO 分隔符与时区标记不参与时区换算。 */
function pickerDateValue(value, type) {
  const match = typeof value === 'string' && /^(\d{4}-\d{2}-\d{2})(?:[T ]([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d)(\.\d{1,9})?)?(?:Z|[+-](?:[01]\d|2[0-3]):[0-5]\d)?)?$/.exec(value)
  if (!match || Boolean(match[2]) !== (type === 'DATETIME')) fail('取值不是有效的日期或日期时间')
  const date = new Date(`${match[1]}T00:00:00Z`)
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== match[1]) fail('取值不是有效的日期')
  return type === 'DATE' ? match[1] : `${match[1]} ${match[2]}:${match[3]}:${match[4] || '00'}${match[5] || ''}`
}

function pickerSelectedValue(field, config, selection, sanitizeRichText) {
  if (!selection || pickerWriteMode(config, field.fieldType) !== 'FIELD') return selection?.value ?? null
  const type = String(field.fieldType).toUpperCase()
  const schema = !isEntityListPicker(config) && config.pickerSchemaSource === 'DEFAULT' ? DEFAULT_PICKER_SCHEMA : config.pickerSchema || DEFAULT_PICKER_SCHEMA
  const source = schema.fields?.find(item => item.code === config.pickerWriteField)
  if (!pickerWriteTypeCompatible(source?.type, type)) fail('请配置与当前属性类型兼容的取值来源字段')
  const value = selection.data?.[source.code]
  if (value == null) fail(`所选记录的「${source.label || source.code}」没有值`)
  if (numbers.includes(type)) {
    if (value === '') fail(`所选记录的「${source.label || source.code}」没有值`)
    return pickerNumericValue(value, type)
  }
  if (type === 'DATE' || type === 'DATETIME') return pickerDateValue(value, type)
  if (type === 'BOOLEAN' && typeof value === 'boolean') return value
  if (['STRING', 'TEXT', 'RICH_TEXT'].includes(type) && typeof value === 'string') {
    return type === 'RICH_TEXT' ? sanitizeRichText(value) : value
  }
  fail(`所选记录的「${source.label || source.code}」类型不正确`)
}

/** 从分页契约的 records.items.properties 提取可直接取值、显示或回填的标量字段。 */
export function pickerQuerySchemaFields(outputSchema) {
  const properties = safeParseConfig(outputSchema).properties?.records?.items?.properties || {}
  return Object.entries(properties).flatMap(([code, property]) => {
    const nonNullTypes = Array.isArray(property?.type) ? property.type.filter(type => type !== 'null') : [property?.type]
    const type = nonNullTypes.length === 1 && pickerDeclaredFieldType(nonNullTypes[0], property?.format)
    return type && fieldCode(code) ? [{ code, type, label: property.title || code }] : []
  })
}

function pickerDeclaredFieldType(type, format) {
  switch (type) {
    case 'string': return stringFormats.get(format) || 'STRING'
    case 'integer': return format === 'int32' ? 'INTEGER' : 'LONG'
    case 'number': return 'NUMBER'
    case 'boolean': return 'BOOLEAN'
    default: return ''
  }
}

function pickerEntityFieldType(type) {
  const normalized = String(type || '').toUpperCase()
  if (types.has(normalized)) return normalized
  if (['SELECT', 'RADIO', 'REFERENCE', 'USER', 'DEPT', 'ROLE', 'GROUP'].includes(normalized)) return 'STRING'
  return ''
}

/** 只有已发布的可见实体列参与回填；虚拟列可用于展示，类型由其自身的渲染定义决定。 */
export function pickerListSchema(listFields, entityFields) {
  const fields = new Map(DEFAULT_PICKER_SCHEMA.fields.map(field => [field.code, field]))
  for (const column of listFields || []) {
    if (!column.showInList || !isPickerEntityColumn(column) || !fieldCode(column.fieldCode) || fields.has(column.fieldCode)) continue
    const entityField = entityFields.find(field => field.fieldCode === column.fieldCode)
    const type = pickerEntityFieldType(entityField?.fieldType)
    if (type) fields.set(column.fieldCode, { code: column.fieldCode, label: column.fieldName || column.fieldCode, type })
    if (fields.size === 200) break
  }
  const columns = (listFields || []).filter(column => column.showInList && fields.has(column.fieldCode)).slice(0, 50)
    .map(column => ({ field: column.fieldCode, label: column.fieldName, width: column.width }))
  return normalizePickerSchema({ schemaVersion: 'entity-list-v1', valueField: 'id', labelField: 'name',
    fields: [...fields.values()], columns: columns.length ? columns : DEFAULT_PICKER_SCHEMA.columns })
}

const isPickerEntityColumn = column => (!column.dataSourceType || String(column.dataSourceType).toUpperCase() === 'ENTITY_FIELD')
  && !column.interfaceExtensionId && !String(column.fieldId || '').startsWith('virtual_')

function listPickerRow(row, columns, schema) {
  if (!(typeof row?.id === 'string' && row.id.trim()) && !Number.isSafeInteger(row?.id)) fail('实体列表记录缺少有效 ID')
  const id = String(row.id)
  const cell = (code, source) => {
    const camel = code.replace(/_([a-z0-9])/g, (_, char) => char.toUpperCase())
    for (const container of source) {
      if (Object.hasOwn(container || {}, code)) return container[code]
      if (Object.hasOwn(container || {}, camel)) return container[camel]
    }
    return null
  }
  const label = String(row.name ?? row.data?.name ?? row.code ?? id)
  // 扩展列可覆盖同名显示值，回填则始终取已发布的实体字段。
  const data = Object.fromEntries(columns.map(column => [column.field, cell(column.field, [row.extData, row.data, row])]))
  const values = Object.fromEntries(schema.fields.map(field => {
    const value = cell(field.code, [row.data, row])
    // JDBC 的布尔字段在不同方言中可能返回 0/1，实体类型已由发布列表确定。
    return [field.code, field.type === 'BOOLEAN' && [0, 1, '0', '1'].includes(value) ? String(value) === '1' : value]
  }))
  return { id, value: id, label, data, values: { ...values, id, name: row.name ?? row.data?.name ?? null }, disabled: row.disabled === true }
}

/** 查询与按 ID 解析共用列表的发布版本及参数；解析走同一个权限和隐藏筛选入口。 */
export function createEntityListPickerSource(api, config, getParameters, getContext) {
  const { entityCode, listKey } = config.pickerList || {}
  const scene = 'FORM_PICKER'
  async function load(schema, query) {
    const mapped = getParameters()
    if (mapped?.error) fail(mapped.error)
    const parameters = resolveListFilterParameters(schema.viewConfig, mapped?.parameters || {}, Object.keys(mapped?.parameters || {}))
    const result = await api.query(entityCode, listKey, {
      ...query, scene, configHash: schema.configHash, runtimeContext: getContext(), context: { parameters }
    })
    return { records: result.records || [], total: result.total }
  }
  async function getSchema() {
    if (!entityCode || !listKey) fail('请配置实体和已发布的选择列表')
    if (!api) fail('当前页面未提供实体列表查询能力')
    const published = await api.getSchema(entityCode, listKey, scene, { runtimeContext: getContext() })
    const fields = published.fields || []
    const queryFields = fields.filter(field => field.isQuery && isPickerEntityColumn(field) && fieldCode(field.fieldCode))
      .map(field => ({ ...field, ...safeParseConfig(field.queryConfig), fieldCode: field.fieldCode,
        fieldName: field.fieldName || field.fieldCode, queryType: field.queryType || 'LIKE' }))
    const columns = fields.filter(field => field.showInList && fieldCode(field.fieldCode))
      .map(field => ({ field: field.fieldCode, label: field.fieldName || field.fieldCode, width: field.width || undefined }))
    return { published, queryFields, columns: columns.length ? columns : DEFAULT_PICKER_SCHEMA.columns }
  }
  async function query(list, request) {
    const result = await load(list.published, request)
    return { ...result, records: result.records.map(row => listPickerRow(row, list.columns, config.pickerSchema)) }
  }
  async function resolve(value, list) {
    const current = list || await getSchema()
    if (list) {
      const available = new Set((current.published.fields || []).filter(field => field.showInList && isPickerEntityColumn(field))
        .map(field => field.fieldCode))
      const usedFields = (config.pickerMappings || []).map(mapping => mapping.sourcePath.slice('selection.data.'.length))
      if (config.pickerValueMode !== 'KEY' && config.pickerWriteField) usedFields.push(config.pickerWriteField)
      if (usedFields.some(code => !['id', 'name'].includes(code) && !available.has(code))) {
        fail('列表已调整，请检查取值与回填来源并重新发布表单')
      }
    }
    const { records } = await query(current, { pageNum: 1, pageSize: 1, selectionRecordId: String(value) })
    const row = records.find(item => item.value === String(value))
    return row ? { ...row, data: row.values } : null
  }
  return { getSchema, query, resolve }
}

/** 接口字段是稳定契约，展示列只是它的子集；只允许普通字段编码，避免动态路径写入原型链。 */
export function normalizePickerSchema(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail('字段描述必须为对象')
  if (typeof value.schemaVersion !== 'string' || !value.schemaVersion.trim()) fail('缺少 schemaVersion')
  if (!Array.isArray(value.fields) || !value.fields.length || value.fields.length > 200) fail('fields 必须包含 1～200 个字段')
  const seen = new Set()
  const fields = value.fields.map(field => {
    if (!fieldCode(field?.code) || seen.has(field.code)) fail('字段编码非法或重复')
    seen.add(field.code)
    const type = String(field.type || '').toUpperCase()
    if (!types.has(type)) fail(`字段 ${field.code} 的类型不受支持`)
    return { code: field.code, label: String(field.label || field.code), type }
  })
  for (const key of ['valueField', 'labelField']) if (!seen.has(value[key])) fail(`${key} 必须引用已声明字段`)
  if (fields.find(field => field.code === value.valueField).type !== 'STRING') fail('记录标识字段必须为 STRING')
  if (!Array.isArray(value.columns) || !value.columns.length || value.columns.length > 50) fail('columns 必须包含 1～50 个展示列')
  const columnsSeen = new Set()
  const columns = value.columns.map(column => {
    if (!seen.has(column?.field) || columnsSeen.has(column.field)) fail('展示列引用不存在或重复')
    columnsSeen.add(column.field)
    return { field: column.field, label: String(column.label || fields.find(field => field.code === column.field).label),
      ...(Number.isInteger(column.width) && column.width >= 60 && column.width <= 1200 ? { width: column.width } : {}),
      minWidth: Number.isInteger(column.minWidth) && column.minWidth >= 60 && column.minWidth <= 1200 ? column.minWidth : 120 }
  })
  return { schemaVersion: value.schemaVersion, valueField: value.valueField, labelField: value.labelField, fields, columns }
}

/** 发布后的字段契约不能随接口悄悄变化；列宽、标题可动态调整，字段编码、类型和主值保持固定。 */
export function assertPickerSchemaCompatible(published, actual) {
  const signature = schema => JSON.stringify([schema.schemaVersion, schema.valueField, schema.labelField,
    schema.fields.map(field => [field.code, field.type]).sort((a, b) => a[0].localeCompare(b[0]))])
  if (signature(published) !== signature(actual)) fail('接口字段结构已变化，请重新获取字段并发布表单')
}

/** 选中行只保留声明字段，额外字段不会意外进入回填；回显与查询共用相同的键和类型检查。 */
export function normalizePickerRows(rows, schema) {
  if (!Array.isArray(rows) || rows.length > 200) fail('记录必须为不超过 200 条的数组')
  const seen = new Set()
  return rows.map(row => {
    if (!row || typeof row !== 'object' || Array.isArray(row)) fail('记录必须为对象')
    const value = row[schema.valueField]
    if (typeof value !== 'string' || !value.trim() || seen.has(value)) fail('记录的主值必须为非空且唯一的字符串')
    seen.add(value)
    const data = Object.fromEntries(schema.fields.map(field => {
      const value = row[field.code]
      if (!empty(value)) {
        if (['STRING', 'TEXT', 'DATE', 'DATETIME', 'RICH_TEXT'].includes(field.type) && typeof value !== 'string') fail(`${field.code} 必须为字符串`)
        if (field.type === 'BOOLEAN' && typeof value !== 'boolean') fail(`${field.code} 必须为布尔值`)
        if (['LONG', 'DECIMAL'].includes(field.type) && typeof value === 'string') pickerNumericValue(value, field.type)
        else if (numbers.includes(field.type) && (typeof value !== 'number' || !Number.isFinite(value))) fail(`${field.code} 必须为有限数值`)
        if (integers.includes(field.type) && typeof value === 'number' && !Number.isSafeInteger(value)) fail(`${field.code} 必须为安全整数`)
      }
      return [field.code, value ?? null]
    }))
    return { id: value, value, label: String(row[schema.labelField] ?? value), data, disabled: row.disabled === true }
  })
}

/** 回填采用现有 FIELD_MAPPING 的覆盖/空值语义；先在草稿内完成所有确认，再一次返回补丁。 */
export async function preparePickerPatch(field, selection, record, confirmOverwrite, isCurrent = () => true, sanitizeRichText) {
  const config = safeParseConfig(field.componentProps)
  const code = field.fieldCode || field.fieldKey
  if (!fieldCode(code)) fail('当前属性编码无效')
  const mappings = config.pickerMappings || []
  const patch = { [code]: pickerSelectedValue(field, config, selection, sanitizeRichText) }
  const staged = { ...record }
  const data = { form: {} }
  const active = selection || config.pickerClearOnClear === true ? mappings : []
  for (const mapping of active) {
    if (!String(mapping.sourcePath || '').startsWith('selection.data.') || !String(mapping.targetPath || '').startsWith('form.')) fail('回填字段路径无效')
    const source = String(mapping.sourcePath || '').replace(/^selection\.data\./, '')
    const target = String(mapping.targetPath || '').replace(/^form\./, '')
    if (!fieldCode(source) || !fieldCode(target) || target === code) fail('回填字段路径无效')
    data.form[target] = selection?.data[source] ?? null
  }
  await applyRuntimeFieldEffects({ effects: [{ type: 'FIELD_MAPPING', mappings: active, data }] }, {
    getRecord: () => staged,
    setField: (key, value) => { staged[key] = value; patch[key] = value },
    confirmOverwrite, isCurrent
  })
  return isCurrent() ? patch : null
}

/**
 * 两端共用的选择会话。查询、回显、确认各自有修订号，关闭/换记录/改参数后旧结果不能提交。
 * load 只执行当前字段的已发布绑定；数据源异常保留原值，由宿主决定何时应用补丁。
 */
export function createDataPickerRuntime({ onChange = () => {} } = {}) {
  let generation = 0, queryRevision = 0, restoreRevision = 0, confirmRevision = 0, settings
  let state = { open: false, loading: false, resolving: false, confirming: false, error: '', restoreError: '', rows: [], total: 0, selected: null, schema: null, list: null, pageNum: 1, pageSize: 10, keyword: '', filters: {} }
  const publish = patch => { state = { ...state, ...patch }; onChange(state) }
  function configure(next) {
    generation++; queryRevision++; restoreRevision++; confirmRevision++; settings = next
    publish({ open: false, loading: false, resolving: false, confirming: false, error: '', restoreError: '', rows: [], total: 0, selected: null, schema: null, list: null })
    try { publish({ schema: normalizePickerSchema(next.schema), pageSize: next.pageSize || 10 }) }
    catch (error) { publish({ error: error.message }) }
  }
  async function restore(value) {
    const current = generation, revision = ++restoreRevision
    publish({ selected: null, restoreError: '', resolving: !empty(value) })
    if (empty(value)) return
    try {
      if (!state.schema) fail('请先配置并发布字段描述')
      const selected = settings.list ? await settings.list.resolve(value)
        : normalizePickerRows(await settings.load('FIELD_PICKER_RESOLVE', { values: [value] }), state.schema).find(row => row.value === value)
      if (current !== generation || revision !== restoreRevision) return
      publish({ selected: selected || null, restoreError: !selected || selected.disabled ? '已保存值已失效或不可访问' : '' })
    } catch (error) { if (current === generation && revision === restoreRevision) publish({ restoreError: error.message }) }
    finally { if (current === generation && revision === restoreRevision) publish({ resolving: false }) }
  }
  async function query({ pageNum = 1, pageSize = state.pageSize, keyword = state.keyword, filters = state.filters } = {}) {
    const current = generation, revision = ++queryRevision
    const configured = settings
    confirmRevision++
    // 保留旧总数直到本页完成，防止分页控件在 total=0 的中间态自动把第二页改回第一页。
    publish({ loading: true, confirming: false, error: '', rows: [], pageNum, pageSize, keyword, filters })
    try {
      if (!state.schema) fail('请先配置并发布字段描述')
      let actual = state.schema, list = state.list
      if (configured.list && !list) {
        list = await configured.list.getSchema()
        actual = { ...actual, columns: list.columns }
        pageSize = listPickerPageSize(list, pageSize)
      } else if (!configured.list && configured.dynamicSchema !== false) {
        actual = normalizePickerSchema(await configured.load('FIELD_PICKER_SCHEMA', {}))
        assertPickerSchemaCompatible(state.schema, actual)
      }
      if (current !== generation || revision !== queryRevision || !state.open) return
      if (list && filters == null) filters = buildListRequestFilters(listQueryDefaults(list.queryFields), list.queryFields)
      const result = configured.list
        ? await configured.list.query(list, { pageNum, pageSize, filters })
        : await configured.load('FIELD_PICKER_QUERY', { pageNum, pageSize, keyword })
      const rows = configured.list ? result.records : normalizePickerRows(result?.records, actual)
      if (!Number.isSafeInteger(result.total) || result.total < 0 || rows.length > pageSize || result.total < rows.length) fail('分页结果不合法')
      if (current === generation && revision === queryRevision && state.open) publish({ rows, total: result.total, schema: actual, list, pageSize, filters })
    } catch (error) { if (current === generation && revision === queryRevision) publish({ error: error.message, total: 0, pageNum: 1 }) }
    finally { if (current === generation && revision === queryRevision) publish({ loading: false }) }
  }
  function close() { queryRevision++; confirmRevision++; publish({ open: false, loading: false, confirming: false }) }
  async function open() { publish({ open: true, list: null }); await query({ pageNum: 1, keyword: '', filters: null }) }
  async function confirm(row, prepare) {
    if (!state.open || state.loading || state.confirming || !row || row.disabled || !state.rows.some(item => item.value === row.value)) return null
    const current = generation, revision = ++confirmRevision
    const isCurrent = () => current === generation && revision === confirmRevision && state.open
    publish({ confirming: true, error: '' })
    try {
      // 确认时重新按键解析，避免使用搜索页中过期或未经完整装载的数据回填。
      const selected = settings.list ? await settings.list.resolve(row.value, state.list)
        : normalizePickerRows(await settings.load('FIELD_PICKER_RESOLVE', { values: [row.value] }), state.schema).find(item => item.value === row.value)
      if (!selected || selected.disabled) fail('所选记录已失效或不可访问')
      if (!isCurrent()) return null
      const patch = await prepare(selected, isCurrent)
      if (!isCurrent()) return null
      return patch
    } catch (error) { if (isCurrent()) publish({ error: error.message }); return null }
    finally { if (isCurrent()) publish({ confirming: false }) }
  }
  return { configure, restore, open, query, close, confirm, getState: () => state }
}

function listPickerPageSize(list, fallback) {
  const size = Number(list.published.viewConfig?.pagination?.pageSize)
  return Number.isSafeInteger(size) && size > 0 && size <= 200 ? size : fallback
}
