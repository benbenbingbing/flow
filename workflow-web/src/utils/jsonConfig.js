export function parseJsonConfig(text, options = {}) {
  const {
    fieldName = '配置',
    emptyValue = {},
    expectedType = 'object'
  } = options

  if (text == null || String(text).trim() === '') {
    return emptyValue
  }

  let parsed
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error(`${fieldName}不是有效的 JSON`)
  }

  if (expectedType === 'object'
      && (parsed == null || Array.isArray(parsed) || typeof parsed !== 'object')) {
    throw new Error(`${fieldName}必须是 JSON 对象`)
  }
  if (expectedType === 'array' && !Array.isArray(parsed)) {
    throw new Error(`${fieldName}必须是 JSON 数组`)
  }

  return parsed
}
