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

export function buildListRequestFilters(
  queryForm = {},
  queryFields = [],
  fixedFilters = {}
) {
  const fields = queryFields || []
  const allowedFieldCodes = new Set(
    fields
      .map(field => String(field?.fieldCode || '').trim())
      .filter(Boolean)
  )
  const params = {}

  Object.entries(queryForm || {}).forEach(([key, value]) => {
    if (!allowedFieldCodes.has(toListFilterFieldCode(key))) return
    if (hasFilterValue(value)) {
      params[key] = value
    }
  })

  fields.forEach(field => {
    const code = String(field?.fieldCode || '').trim()
    if (!code || !field?.queryType) return
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

/** 选择器与列表页共用查询字段的默认值，范围条件由实际输入生成。 */
export function listQueryDefaults(fields) {
  return Object.fromEntries(fields.filter(field => field.defaultValue != null)
    .map(field => [field.fieldCode, field.defaultValue]))
}
