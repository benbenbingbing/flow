/** 与内置处理器保持稳定编码；复杂字段由各自业务动作维护。 */
export const ENTITY_FIELD_ASSIGNMENT_HANDLER = 'entityFieldAssignmentFlowActionHandler'

/** 保留原始字面值，尤其不能把 LONG/DECIMAL 转成 JavaScript 浮点数。非法配置明确报错。 */
export function parseFieldAssignments(raw) {
  if (!raw) return []
  let parsed
  try { parsed = JSON.parse(raw) }
  catch { throw new Error('字段赋值参数格式不合法，请检查原配置或重新配置。') }
  if (!parsed || Array.isArray(parsed) || Object.keys(parsed).length !== 1 || !Array.isArray(parsed.assignments)) {
    throw new Error('字段赋值参数格式不合法，请检查原配置或重新配置。')
  }
  return parsed.assignments.map(row => {
    if (!row || Array.isArray(row) || typeof row !== 'object'
      || Object.keys(row).some(key => !['fieldCode', 'operation', 'value'].includes(key))) {
      throw new Error('存在无法识别的字段赋值，请检查原配置或重新配置。')
    }
    return { ...row }
  })
}

/** 配置只允许固定字面值或显式清空；缺失、空字符串与 null 不互相转换。 */
export function buildFieldAssignments(rows, fields) {
  if (!rows.length || rows.length > 50) throw new Error('请配置 1 至 50 个字段。')
  const seen = new Set()
  const assignments = rows.map(row => {
    const field = fields.find(field => field.fieldCode === row.fieldCode)
    if (!field) throw new Error(`字段 ${row.fieldCode || '（未选择）'} 不存在或当前不允许赋值。`)
    if (seen.has(row.fieldCode)) throw new Error(`字段「${field.fieldName}」不能重复配置。`)
    seen.add(row.fieldCode)
    if (row.operation === 'CLEAR') {
      if (field.required) throw new Error(`必填字段「${field.fieldName}」不能清空。`)
      if (row.value != null) throw new Error('清空操作不能同时设置固定值。')
      return { fieldCode: row.fieldCode, operation: 'CLEAR' }
    }
    if (row.operation !== 'SET' || row.value == null) throw new Error(`请填写「${field.fieldName}」的固定值。`)
    const value = row.value
    if (field.fieldType === 'BOOLEAN') {
      if (typeof value !== 'boolean') throw new Error('布尔字段必须选择是或否。')
    } else if (field.fieldType === 'INTEGER') {
      if (!Number.isInteger(value) || value < -2147483648 || value > 2147483647) throw new Error('请输入32位整数。')
    } else {
      if (typeof value !== 'string') throw new Error(`字段「${field.fieldName}」的固定值必须为文本。`)
      if (field.required && !value.trim()) throw new Error(`必填字段「${field.fieldName}」不能为空。`)
      if (field.fieldType === 'LONG' && (!/^-?\d+$/.test(value)
        || BigInt(value) < -9223372036854775808n || BigInt(value) > 9223372036854775807n)) throw new Error('请输入64位长整数。')
      if (field.fieldType === 'DECIMAL' && !/^-?\d+(\.\d+)?$/.test(value)) throw new Error('请输入十进制小数。')
      if (['SELECT', 'RADIO'].includes(field.fieldType)
        && !field.options?.some(option => !option.disabled && option.value === value)) throw new Error('请选择当前可用的选项。')
    }
    return { fieldCode: row.fieldCode, operation: 'SET', value }
  })
  return JSON.stringify({ assignments })
}

export function initialAssignmentValue(field) {
  if (field?.fieldType === 'BOOLEAN') return false
  if (field?.fieldType === 'INTEGER') return null
  return ''
}
