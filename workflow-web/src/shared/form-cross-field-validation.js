const NUMBER_TYPES = new Set(['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'])
const FIELD_CODE = /^[A-Za-z][A-Za-z0-9_]{0,99}$/
const RULE_KEYS = new Set(['id', 'operator', 'targetFieldCode', 'message'])
const CONFIG_KEYS = new Set(['version', 'rules'])

export const CROSS_FIELD_OPERATORS = Object.freeze([
  { value: 'EQ', label: '等于', symbol: '=' },
  { value: 'NE', label: '不等于', symbol: '≠' },
  { value: 'GT', label: '大于', symbol: '>' },
  { value: 'GE', label: '大于等于', symbol: '≥' },
  { value: 'LT', label: '小于', symbol: '<' },
  { value: 'LE', label: '小于等于', symbol: '≤' }
])
export const CROSS_FIELD_MAX_RULES = 20
export const CROSS_FIELD_ERROR_CODE = 'FORM_CROSS_FIELD_VALIDATION_FAILED'

const typeOf = field => String(field?.fieldType || '').toUpperCase()
const codeOf = field => String(field?.fieldCode || field?.bindingRef || '')
const labelOf = field => field?.fieldLabel || field?.fieldName || codeOf(field) || '当前字段'
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const isBlank = value => value === null || value === undefined || value === ''

/** 日期只比较同种类型，数值可以跨数值类型比较；不对文本进行隐式转换。 */
export function supportsCrossFieldValidation(fieldType) {
  const type = String(fieldType || '').toUpperCase()
  return NUMBER_TYPES.has(type) || type === 'DATE' || type === 'DATETIME'
}

export function areCrossFieldTypesCompatible(left, right) {
  const a = String(left || '').toUpperCase()
  const b = String(right || '').toUpperCase()
  return NUMBER_TYPES.has(a) ? NUMBER_TYPES.has(b) : (supportsCrossFieldValidation(a) && a === b)
}

/** 只读取所属表单的 validationRules；不从实体 validateRules 继承跨字段规则。 */
export function getCrossFieldConfig(field) {
  const source = field?.validationRules
  const validation = typeof source === 'string' ? JSON.parse(source || '{}') : source
  return validation?.crossField
}

/**
 * 检查结构和字段引用。fields 未传时仅检查结构，供节点 PATCH/编辑中间态使用。
 * 返回可展示的错误列表，不修改或丢弃失效引用，以便用户修复草稿。
 */
export function validateCrossFieldConfiguration(config, field, fields) {
  if (config === null || config === undefined) return []
  if (!isObject(config) || config.version !== 1 || !Array.isArray(config.rules)) {
    return ['跨字段校验配置必须包含 version=1 和 rules 数组']
  }
  if (Object.keys(config).some(key => !CONFIG_KEYS.has(key))) return ['跨字段校验包含不支持的配置项']
  if (config.rules.length > CROSS_FIELD_MAX_RULES) return [`每个字段最多配置 ${CROSS_FIELD_MAX_RULES} 条跨字段规则`]
  if (config.rules.length && !supportsCrossFieldValidation(typeOf(field))) return ['当前字段类型不支持跨字段校验']
  const ids = new Set()
  const comparisons = new Set()
  const errors = []
  for (const [index, rule] of config.rules.entries()) {
    const prefix = `跨字段规则 ${index + 1}：`
    if (!isObject(rule) || Object.keys(rule).some(key => !RULE_KEYS.has(key))) {
      errors.push(prefix + '规则结构无效或包含不支持的配置项')
      continue
    }
    if (typeof rule.id !== 'string' || !/^[A-Za-z0-9_-]{1,100}$/.test(rule.id) || ids.has(rule.id)) {
      errors.push(prefix + '规则标识无效或重复')
    }
    ids.add(rule.id)
    if (!CROSS_FIELD_OPERATORS.some(item => item.value === rule.operator)) errors.push(prefix + '请选择有效的比较方式')
    if (typeof rule.targetFieldCode !== 'string' || !FIELD_CODE.test(rule.targetFieldCode)) errors.push(prefix + '请选择有效的比较字段')
    if (rule.targetFieldCode === codeOf(field)) errors.push(prefix + '不能与当前字段自身比较')
    if (rule.message !== undefined && (typeof rule.message !== 'string' || [...rule.message].length > 200)) {
      errors.push(prefix + '错误提示必须为不超过 200 字的文本')
    }
    const comparison = `${rule.operator}:${rule.targetFieldCode}`
    if (comparisons.has(comparison)) errors.push(prefix + '比较方式和目标字段重复')
    comparisons.add(comparison)
    if (fields) {
      const target = fields.find(candidate => codeOf(candidate) === rule.targetFieldCode)
      if (!target) errors.push(prefix + `比较字段已失效：${rule.targetFieldCode || '未选择'}`)
      else if (!areCrossFieldTypesCompatible(typeOf(field), typeOf(target))) errors.push(prefix + '关联字段类型不兼容')
    }
  }
  return errors
}

/** 使用稳定字段编码创建反向依赖；仅重查受变化影响的规则，不递归触发比较。 */
export function buildCrossFieldDependencyIndex(fields) {
  const index = new Map()
  for (const field of fields) {
    const owner = codeOf(field)
    const config = getCrossFieldConfig(field)
    if (!Array.isArray(config?.rules) || !config.rules.length) continue
    for (const key of new Set([owner, ...config.rules.map(rule => rule?.targetFieldCode).filter(Boolean)])) {
      if (!index.has(key)) index.set(key, new Set())
      index.get(key).add(owner)
    }
  }
  return index
}

/** 严格校验日历组成，避免 Date 构造器滚动非法日期以及浏览器时区差异。 */
function dateValue(value, type) {
  if (value instanceof Date) {
    if (!Number.isFinite(value.getTime()) || value.getMilliseconds() !== 0) throw new Error('不是有效的日期时间')
    const pad = part => String(part).padStart(2, '0')
    const day = `${String(value.getFullYear()).padStart(4, '0')}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
    value = type === 'DATE' ? day : `${day} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
  }
  const pattern = type === 'DATE'
    ? /^(\d{4})-(\d{2})-(\d{2})$/
    : /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.0{1,9})?$/
  const match = typeof value === 'string' ? pattern.exec(value) : null
  const error = type === 'DATE' ? '不是有效的日期' : '不是有效的日期时间（精确到秒且不含时区偏移）'
  if (!match) throw new Error(error)
  const [, year, month, day, hour = '00', minute = '00', second = '00'] = match
  const y = Number(year)
  const m = Number(month)
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (y < 1 || m < 1 || m > 12 || Number(day) < 1 || Number(day) > days[m - 1]
      || Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59) throw new Error(error)
  return `${year}${month}${day}${hour}${minute}${second}`
}

/**
 * 将十进制化为符号、有效数字和指数。比较不经过 Number，保留 LONG/DECIMAL 字符串精度。
 * 限制输入长度与指数，避免异常数据引发大字符串分配或两端实现范围不一致。
 */
function decimalValue(value, type) {
  if (typeof value === 'number' && (!Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value)))) {
    throw new Error('数值超出安全精度，请使用精确数值输入')
  }
  if (!['string', 'number'].includes(typeof value)) throw new Error('不是有效的数字')
  const text = String(value)
  const match = text.length <= 500 ? /^([+-]?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d{1,5}))?$/.exec(text) : null
  if (!match) throw new Error('不是有效的数字')
  let digits = (match[2] + (match[3] || '')).replace(/^0+/, '')
  let exponent = Number(match[4] || 0) - (match[3]?.length || 0)
  if (Math.abs(Number(match[4] || 0)) > 10000) throw new Error('数值指数超出支持范围')
  if (!digits) return { sign: 0, digits: '0', exponent: 0 }
  const trailing = /0+$/.exec(digits)?.[0].length || 0
  digits = digits.slice(0, digits.length - trailing)
  exponent += trailing
  if ((type === 'INTEGER' || type === 'LONG') && exponent < 0) throw new Error('必须为整数')
  return { sign: match[1] === '-' ? -1 : 1, digits, exponent }
}

/** 返回 -1/0/1；非法的非空值抛出可展示的类型错误，不能作为空值跳过。 */
export function compareCrossFieldValues(left, right, leftType, rightType = leftType) {
  if (!areCrossFieldTypesCompatible(leftType, rightType)) throw new Error('比较字段类型不兼容')
  if (!NUMBER_TYPES.has(leftType)) {
    const a = dateValue(left, leftType)
    const b = dateValue(right, rightType)
    return a === b ? 0 : a < b ? -1 : 1
  }
  const a = decimalValue(left, leftType)
  const b = decimalValue(right, rightType)
  if (a.sign !== b.sign) return a.sign < b.sign ? -1 : 1
  if (!a.sign) return 0
  const magnitudeA = a.digits.length + a.exponent
  const magnitudeB = b.digits.length + b.exponent
  if (magnitudeA !== magnitudeB) return (magnitudeA < magnitudeB ? -1 : 1) * a.sign
  const size = Math.max(a.digits.length, b.digits.length)
  const digitsA = a.digits.padEnd(size, '0')
  const digitsB = b.digits.padEnd(size, '0')
  return digitsA === digitsB ? 0 : (digitsA < digitsB ? -1 : 1) * a.sign
}

/** 校验单个所属字段；state 必须由运行时可见性、编辑权限计算，不能来自提交数据。 */
export function evaluateCrossField(field, record, fields, state = {}) {
  if (state.visible !== true || state.editable !== true) return { error: null, deferred: false }
  const config = getCrossFieldConfig(field)
  const fieldCode = codeOf(field)
  const fail = (rule, message) => ({ error: { fieldCode, ruleId: rule?.id || '', targetFieldCode: rule?.targetFieldCode || '', message }, deferred: false })
  const errors = validateCrossFieldConfiguration(config, field, fields)
  if (errors.length) return fail(null, `${labelOf(field)}：${errors[0]}`)
  let deferred = false
  for (const rule of config?.rules || []) {
    // 未加载字段与明确空值区分：留给权威提交检查，不伪造一个“已通过”的结论。
    if (!Object.hasOwn(record, fieldCode) || !Object.hasOwn(record, rule.targetFieldCode)) {
      deferred = true
      continue
    }
    const left = record[fieldCode]
    const right = record[rule.targetFieldCode]
    const target = fields.find(item => codeOf(item) === rule.targetFieldCode)
    if (isBlank(left) || isBlank(right)) continue
    let comparison
    try {
      comparison = compareCrossFieldValues(left, right, typeOf(field), typeOf(target))
    } catch (error) {
      return fail(rule, `${labelOf(field)}与${labelOf(target)}无法比较：${error.message}`)
    }
    const passed = { EQ: comparison === 0, NE: comparison !== 0, GT: comparison > 0, GE: comparison >= 0, LT: comparison < 0, LE: comparison <= 0 }[rule.operator]
    if (!passed) {
      const operator = CROSS_FIELD_OPERATORS.find(item => item.value === rule.operator)
      return fail(rule, rule.message?.trim() || `${labelOf(field)}必须${operator.label}${labelOf(target)}`)
    }
  }
  return { error: null, deferred }
}
