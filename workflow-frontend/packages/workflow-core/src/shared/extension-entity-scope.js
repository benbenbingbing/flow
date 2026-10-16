/**
 * 列表字段扩展的实体适用范围。
 *
 * 空、缺省或包含 * 表示全部实体。只用来收窄设计器下拉，
 * 不阻止已经保存的列继续渲染或补数。
 */
export function normalizeSupportedEntityCodes(value) {
  if (value == null || value === '') {
    return []
  }
  const list = Array.isArray(value) ? value : [value]
  const codes = []
  const seen = new Set()
  for (const item of list) {
    const code = String(item || '').trim()
    if (!code) continue
    if (code === '*') {
      return []
    }
    const key = code.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    codes.push(code)
  }
  return codes
}

/**
 * 当前实体是否命中扩展声明的范围。
 * 没有当前实体编码时不过滤，避免列模板等无实体场景把选项藏掉。
 */
export function matchesSupportedEntityCodes(supported, entityCode) {
  const codes = normalizeSupportedEntityCodes(supported)
  if (!codes.length) {
    return true
  }
  const current = String(entityCode || '').trim()
  if (!current) {
    return true
  }
  const currentKey = current.toLowerCase()
  return codes.some(code => code.toLowerCase() === currentKey)
}

/**
 * 按实体过滤下拉选项。当前已选值始终保留，避免旧配置在下拉里消失。
 */
export function filterOptionsByEntity(options, entityCode, currentValue) {
  const current = String(currentValue || '').trim()
  return (options || []).filter(option => {
    const value = String(option?.value || '')
    if (current && value === current) {
      return true
    }
    return matchesSupportedEntityCodes(option?.supportedEntityCodes, entityCode)
  })
}
