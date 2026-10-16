import { parseJsonConfig } from '../utils/jsonConfig.js'

export const FIXED_FILTER_OPERATORS = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'LIKE', label: '包含文本' },
  { value: 'GT', label: '大于' },
  { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LE', label: '小于等于' },
  { value: 'BETWEEN', label: '介于' },
  { value: 'IN', label: '属于任一值' },
  { value: 'NOT_IN', label: '不属于这些值' },
  { value: 'IS_NULL', label: '为空' }
]
const has = (value, key) => Object.hasOwn(value, key)
const present = value => value !== null && value !== undefined
  && !(typeof value === 'string' && value.trim() === '')
const scalar = value => ['string', 'number', 'boolean'].includes(typeof value)
const clone = value => JSON.parse(JSON.stringify(value))
const baseField = key => key.replace(/_(op|start|end)$/, '')

export function createFixedFilterRow() {
  return { field: '', operator: 'EQ', value: '', start: '', end: '' }
}

/** 用于草稿变更比较，原始文档只用于无损回写，不参与可编辑值的比较。 */
export function fixedFilterRowFingerprint(row) {
  return JSON.stringify([row.field, row.operator, row.value, row.start, row.end])
}

/**
 * 将现有扁平对象分组为条件行。保留原文，避免仅打开再保存就改变隐式 EQ、数值类型或特殊条件。
 * 无法用单行准确表示的复合/扩展条件只读保留，用户仍可明确删除该行。
 */
export function readFixedFilterRows(document, { systemEntity = false } = {}) {
  const config = typeof document === 'string'
    ? parseJsonConfig(document, { fieldName: '固定条件' }) : document ?? {}
  if (!config || Array.isArray(config) || typeof config !== 'object') {
    throw new Error('固定条件必须是对象')
  }
  const groups = new Map()
  for (const [key, value] of Object.entries(config)) {
    const field = baseField(key)
    if (!groups.has(field)) groups.set(field, {})
    Object.defineProperty(groups.get(field), key, { value: clone(value), enumerable: true })
  }
  return [...groups].map(([field, original]) => {
    const row = { ...createFixedFilterRow(), field, original }
    const op = String(original[`${field}_op`] || 'EQ').toUpperCase()
    const hasStart = has(original, `${field}_start`)
    const hasEnd = has(original, `${field}_end`)
    if ((hasStart || hasEnd) && !has(original, field) && ['EQ', 'BETWEEN'].includes(op)) {
      row.operator = hasStart && hasEnd ? 'BETWEEN' : hasStart ? 'GE' : 'LE'
      row.start = original[`${field}_start`] ?? ''
      row.end = original[`${field}_end`] ?? ''
      row.value = hasStart ? row.start : row.end
      row.readOnly = ![row.start, row.end].filter(present).every(scalar)
        || (hasStart && !present(row.start)) || (hasEnd && !present(row.end))
    } else {
      row.operator = op
      row.value = original[field]
      // GE/LE 的历史普通值在业务查询中并非范围语义，不擅自把它们转换成范围。
      row.readOnly = hasStart || hasEnd || !has(original, field)
        || !['EQ', 'NE', 'LIKE', 'GT', 'LT', 'IN', 'NOT_IN', 'IS_NULL', ...(systemEntity ? ['GE', 'LE'] : [])].includes(op)
      if (['IN', 'NOT_IN'].includes(op)) {
        row.value = Array.isArray(row.value) ? row.value
          : typeof row.value === 'string' ? row.value.split(',').map(value => value.trim()).filter(Boolean) : [row.value]
        row.readOnly ||= !row.value.length || !row.value.every(value => scalar(value) && present(value))
      } else {
        row.readOnly ||= !scalar(row.value) || !present(row.value)
      }
    }
    row.baseline = fixedFilterRowFingerprint(row)
    return row
  })
}

/**
 * 校验并还原现有存储协议；不完整条件必须阻止保存，不能静默丢弃而扩大查询范围。
 * 业务查询通过 _start/_end 表示闭区间，系统表的单侧比较则使用 GE/LE。
 */
export function writeFixedFilterRows(rows, { systemEntity = false, fields: fieldDefinitions = [] } = {}) {
  const result = {}
  const fields = new Set()
  for (const [index, row] of rows.entries()) {
    const fail = message => { throw new Error(`固定条件第 ${index + 1} 行：${message}`) }
    if (!row.field) fail('请选择字段')
    if (fields.has(row.field)) fail('同一字段只能配置一次，请使用“介于”设置范围')
    fields.add(row.field)
    if (row.original && row.baseline === fixedFilterRowFingerprint(row)) {
      // 使用 defineProperty 保留历史对象的键，避免特殊键触发 Object 原型 setter。
      for (const [key, value] of Object.entries(row.original)) {
        Object.defineProperty(result, key, { value: clone(value), enumerable: true, configurable: true })
      }
      continue
    }
    if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(row.field) || /_(op|start|end)$/.test(row.field)) fail('字段编码不支持')
    if (row.readOnly) fail('该历史条件只能保留或删除')
    if (!FIXED_FILTER_OPERATORS.some(option => option.value === row.operator)
        || (systemEntity && row.operator === 'NOT_IN')) fail('不支持该比较方式')
    const valueType = fixedFilterValueType(fieldDefinitions.find(field => field.fieldCode === row.field))
    const check = value => {
      if (!scalar(value) || !present(value) || (typeof value === 'number' && !Number.isFinite(value))) {
        fail('请填写完整的条件值')
      }
      // 旧配置可能用字符串存数字；允许有限数值字符串，避免只改比较方式就被迫改写值类型。
      if (valueType === 'number' && !['number', 'string'].includes(typeof value)) fail('请填写有效数值')
      if (valueType === 'number' && !Number.isFinite(Number(value))) fail('请填写有效数值')
      return value
    }
    const field = row.field
    if (row.operator === 'BETWEEN') {
      const start = check(row.start)
      const end = check(row.end)
      if ((typeof start === 'number' && typeof end === 'number' || ['date', 'datetime'].includes(valueType)) && start > end) fail('起始值不能大于结束值')
      result[`${field}_start`] = start
      result[`${field}_end`] = end
      result[`${field}_op`] = 'BETWEEN'
    } else if (['GE', 'LE'].includes(row.operator) && !systemEntity) {
      result[`${field}_${row.operator === 'GE' ? 'start' : 'end'}`] = check(row.value)
    } else {
      if (['IN', 'NOT_IN'].includes(row.operator)) {
        if (!Array.isArray(row.value) || !row.value.length) fail('请至少填写一个值')
        result[field] = row.value.map(check)
      } else {
        // IS_NULL 需要非空占位值进入现有查询器，实际 SQL 不使用该值。
        result[field] = row.operator === 'IS_NULL' ? true : check(row.value)
      }
      result[`${field}_op`] = row.operator
    }
  }
  return result
}

export function fixedFilterValueType(field = {}) {
  const type = String(field.fieldType || '').toUpperCase()
  if (['NUMBER', 'INTEGER', 'DECIMAL', 'LONG', 'DOUBLE', 'FLOAT'].includes(type)) return 'number'
  if (type === 'BOOLEAN') return 'boolean'
  if (['DATE', 'DATETIME'].includes(type)) return type.toLowerCase()
  return 'text'
}

/** 静态选项允许直接选择；无选项或历史选项已下线时仍可录入真实存储值。 */
export function fixedFilterFieldOptions(field = {}) {
  for (let source of [field.options, field.optionsJson]) {
    if (typeof source === 'string') {
      try { source = JSON.parse(source) } catch { continue }
    }
    if (!Array.isArray(source)) continue
    return source.map(option => typeof option === 'object' && option !== null
      ? { label: String(option.label ?? option.name ?? option.value), value: option.value }
      : { label: String(option), value: option }).filter(option => scalar(option.value))
  }
  return []
}
