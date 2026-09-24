/**
 * 判断一对一子表单是否代表真实子记录。仅依据表单字段判断新行，避免
 * 用户点击“添加数据”后未填写内容，却在父聚合保存时写入只有外键的空子行。
 * 已有行即使清空业务字段也保留其 ID，由显式删除操作处理移除语义。
 */
export function hasOneToOneSubFormContent(row, fields = []) {
  if (!row || typeof row !== 'object') return false
  if (row.id != null && String(row.id).trim() !== '') return true

  const fieldKeys = fields.map(field => field?.fieldKey || field?.fieldCode).filter(Boolean)
  const keys = fieldKeys.length > 0 ? fieldKeys : Object.keys(row).filter(key => !key.startsWith('_'))
  return keys.some(key => hasValue(row[key]))
}

function hasValue(value) {
  if (value == null) return false
  if (typeof value === 'string') return value.trim() !== ''
  if (Array.isArray(value)) return value.some(hasValue)
  if (value instanceof Date) return !Number.isNaN(value.getTime())
  if (typeof value === 'object') return Object.values(value).some(hasValue)
  // 0 和 false 是用户可以明确填写的业务值。
  return true
}
