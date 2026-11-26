export function toListFilterFieldCode(filterKey = '') {
  const key = String(filterKey)
  for (const suffix of ['_start', '_end', '_op']) {
    if (key.endsWith(suffix)) {
      return key.slice(0, -suffix.length)
    }
  }
  return key
}

function hasFilterValue(value) {
  if (Array.isArray(value)) {
    return value.some(item => hasFilterValue(item))
  }
  return value !== '' && value !== null && value !== undefined
}

/** 空值查询由用户勾选启用，不需要输入用于比较的字段值。 */
export function isListNullQueryOperator(operator) {
  return ['EMPTY', 'NOT_EMPTY', 'IS_NULL'].includes(operator)
}

export function buildListRequestFilters(
  queryForm = {},
  queryFields = [],
  fixedFilters = {}
) {
  const fields = queryFields || []
  const fieldsByCode = new Map()
  fields.forEach(field => {
    const code = String(field?.fieldCode || '').trim()
    if (code) fieldsByCode.set(code, field)
  })
  const params = {}

  Object.entries(queryForm || {}).forEach(([key, value]) => {
    const field = fieldsByCode.get(toListFilterFieldCode(key))
    if (!field || isListNullQueryOperator(field.queryType)) return
    if (hasFilterValue(value)) {
      params[key] = value
    }
  })

  fieldsByCode.forEach((field, code) => {
    if (!field.queryType) return
    // 只接受勾选产生的布尔值，旧配置中的文本默认值和残留区间值不应启用此条件。
    if (isListNullQueryOperator(field.queryType)) {
      if (queryForm?.[code] === true) {
        params[code] = true
        params[`${code}_op`] = field.queryType
      }
      return
    }
    const hasConfiguredValue = [code, `${code}_start`, `${code}_end`]
      .some(key => params[key] !== undefined)
    if (hasConfiguredValue) {
      params[`${code}_op`] = field.queryType
    }
  })

  Object.entries(fixedFilters || {}).forEach(([key, value]) => {
    if (hasFilterValue(value)) {
      params[key] = value
    }
  })
  return params
}

/** 选择器与生成的列表页共用默认值，空值条件始终由用户主动勾选。 */
export function listQueryDefaults(fields) {
  return Object.fromEntries(fields.filter(field => !isListNullQueryOperator(field.queryType) && field.defaultValue != null)
    .map(field => [field.fieldCode, field.defaultValue]))
}
