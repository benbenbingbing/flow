/** 将生成器的 JSON Schema 转成现有参数编辑器结构，服务端仍负责权威校验。 */
export const generatorSchemaFields = (schema = {}) => Object.entries(schema.properties || {}).map(([key, value]) => ({
  key,
  label: value.title || key,
  description: value.description || '',
  required: (schema.required || []).includes(key),
  defaultValue: value.default,
  type: Array.isArray(value.enum) ? 'select' : ({ integer: 'number', number: 'number', boolean: 'boolean', object: 'json', array: 'json' }[value.type] || 'text'),
  options: (value.enum || []).map(item => ({ label: String(item), value: item })),
  min: value.minimum,
  max: value.maximum
}))

/** 只在缺键时应用默认值，保留业务方显式配置的 false、0 和空字符串。 */
export const generatorConfigDefaults = (schema = {}, value = {}) => {
  const result = { ...value }
  for (const [key, property] of Object.entries(schema.properties || {})) {
    if (!(key in result) && property.default !== undefined) result[key] = structuredClone(property.default)
  }
  return result
}
