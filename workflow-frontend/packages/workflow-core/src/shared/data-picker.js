import { safeParseConfig } from './config-runtime/index.js'
import { applyRuntimeFieldEffects } from './form-runtime/fieldEvents.js'

export const DATA_PICKER_USAGES = Object.freeze(['FIELD_PICKER_SCHEMA', 'FIELD_PICKER_QUERY', 'FIELD_PICKER_RESOLVE'])
const identifier = /^[A-Za-z][A-Za-z0-9_]{0,99}$/
const unsafe = new Set(['__proto__', 'prototype', 'constructor'])
const types = new Set(['STRING', 'TEXT', 'INTEGER', 'LONG', 'DECIMAL', 'DOUBLE', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME'])
const fail = message => { throw new Error(`数据选择器：${message}`) }
const fieldCode = code => typeof code === 'string' && identifier.test(code) && !unsafe.has(code)
const empty = value => value == null || value === ''

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
  if (fields.find(field => field.code === value.valueField).type !== 'STRING') fail('主值字段必须为 STRING，业务编码不会转换为数字')
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
        if (['STRING', 'TEXT', 'DATE', 'DATETIME'].includes(field.type) && typeof value !== 'string') fail(`${field.code} 必须为字符串`)
        if (field.type === 'BOOLEAN' && typeof value !== 'boolean') fail(`${field.code} 必须为布尔值`)
        if (['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE', 'NUMBER'].includes(field.type) && (typeof value !== 'number' || !Number.isFinite(value))) fail(`${field.code} 必须为有限数值`)
        if (['INTEGER', 'LONG'].includes(field.type) && !Number.isSafeInteger(value)) fail(`${field.code} 必须为安全整数`)
      }
      return [field.code, value ?? null]
    }))
    return { id: value, value, label: String(row[schema.labelField] ?? value), data, disabled: row.disabled === true }
  })
}

/** 回填采用现有 FIELD_MAPPING 的覆盖/空值语义；先在草稿内完成所有确认，再一次返回补丁。 */
export async function preparePickerPatch(field, selection, record, confirmOverwrite, isCurrent = () => true) {
  const config = safeParseConfig(field.componentProps)
  const code = field.fieldCode || field.fieldKey
  if (!fieldCode(code)) fail('当前属性编码无效')
  const mappings = config.pickerMappings || []
  const patch = { [code]: selection?.value ?? null }
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
  let state = { open: false, loading: false, resolving: false, confirming: false, error: '', restoreError: '', rows: [], total: 0, selected: null, schema: null, pageNum: 1, pageSize: 10, keyword: '' }
  const publish = patch => { state = { ...state, ...patch }; onChange(state) }
  function configure(next) {
    generation++; queryRevision++; restoreRevision++; confirmRevision++; settings = next
    publish({ open: false, loading: false, resolving: false, confirming: false, error: '', restoreError: '', rows: [], total: 0, selected: null, schema: null })
    try { publish({ schema: normalizePickerSchema(next.schema), pageSize: next.pageSize || 10 }) }
    catch (error) { publish({ error: error.message }) }
  }
  async function restore(value) {
    const current = generation, revision = ++restoreRevision
    publish({ selected: null, restoreError: '', resolving: !empty(value) })
    if (empty(value)) return
    try {
      if (!state.schema) fail('请先配置并发布字段描述')
      const rows = normalizePickerRows(await settings.load('FIELD_PICKER_RESOLVE', { values: [value] }), state.schema)
      if (current !== generation || revision !== restoreRevision) return
      const selected = rows.find(row => row.value === value)
      publish({ selected: selected || null, restoreError: !selected || selected.disabled ? '已保存值已失效或不可访问' : '' })
    } catch (error) { if (current === generation && revision === restoreRevision) publish({ restoreError: error.message }) }
    finally { if (current === generation && revision === restoreRevision) publish({ resolving: false }) }
  }
  async function query({ pageNum = 1, pageSize = state.pageSize, keyword = state.keyword } = {}) {
    const current = generation, revision = ++queryRevision
    confirmRevision++
    // 保留旧总数直到本页完成，防止分页控件在 total=0 的中间态自动把第二页改回第一页。
    publish({ loading: true, confirming: false, error: '', rows: [], pageNum, pageSize, keyword })
    try {
      if (!state.schema) fail('请先配置并发布字段描述')
      const actual = normalizePickerSchema(await settings.load('FIELD_PICKER_SCHEMA', {}))
      assertPickerSchemaCompatible(state.schema, actual)
      if (current !== generation || revision !== queryRevision || !state.open) return
      const result = await settings.load('FIELD_PICKER_QUERY', { pageNum, pageSize, keyword })
      const rows = normalizePickerRows(result?.records, actual)
      if (!Number.isSafeInteger(result.total) || result.total < 0 || rows.length > pageSize || result.total < rows.length) fail('分页结果不合法')
      if (current === generation && revision === queryRevision && state.open) publish({ rows, total: result.total, schema: actual })
    } catch (error) { if (current === generation && revision === queryRevision) publish({ error: error.message, total: 0, pageNum: 1 }) }
    finally { if (current === generation && revision === queryRevision) publish({ loading: false }) }
  }
  function close() { queryRevision++; confirmRevision++; publish({ open: false, loading: false, confirming: false }) }
  async function open() { publish({ open: true }); await query({ pageNum: 1, keyword: '' }) }
  async function confirm(row, prepare) {
    if (!state.open || state.loading || state.confirming || !row || row.disabled || !state.rows.some(item => item.value === row.value)) return null
    const current = generation, revision = ++confirmRevision
    const isCurrent = () => current === generation && revision === confirmRevision && state.open
    publish({ confirming: true, error: '' })
    try {
      // 确认时重新按键解析，避免使用搜索页中过期或未经完整装载的数据回填。
      const rows = normalizePickerRows(await settings.load('FIELD_PICKER_RESOLVE', { values: [row.value] }), state.schema)
      const selected = rows.find(item => item.value === row.value)
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
