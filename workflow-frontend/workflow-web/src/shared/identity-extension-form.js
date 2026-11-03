/** 从白名单字段构造完整组快照，未添加的组不参与保存。 */
export function extensionSnapshot(schema, data) {
  return Object.fromEntries(schema.fields.map(field => {
    const value = data?.[field.name] ?? null
    return [field.name, value === '' && (field.control === 'NUMBER' || field.control === 'SELECT') ? null : value]
  }))
}

/** 仅提交修改过的 LOCAL 资料，携带原行版本，SOURCE 和只读组永不进入编辑请求。 */
export function buildExtensionChanges(schemas, drafts, originals, versions) {
  const changes = {}
  for (const schema of schemas) {
    if (!schema.writable || schema.maintenance !== 'LOCAL' || !Object.hasOwn(drafts, schema.code)) continue
    const data = extensionSnapshot(schema, drafts[schema.code])
    for (const field of schema.fields) {
      const value = data[field.name]
      if (field.required && (value === null || typeof value === 'string' && !value.trim())) throw new Error(field.label + '不能为空')
      if (typeof value === 'string' && [...value].length > field.maxLength) throw new Error(field.label + '超过允许长度')
      if (field.control === 'NUMBER' && value !== null) {
        // 精确数值由服务端声明为字符串，不先转换为 Number，防止大整数/小数丢失精度。
        const valid = field.numberAsString
          ? typeof value === 'string' && /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value)
          : typeof value === 'number' && Number.isFinite(value)
        if (!valid) throw new Error(field.label + '必须是有效数字')
      }
      if (field.control === 'SELECT' && value !== null && !field.options.includes(value)) throw new Error(field.label + '选项无效')
    }
    if (originals[schema.code] !== JSON.stringify(data)) changes[schema.code] = { expectedVersion: versions[schema.code] ?? 0, data }
  }
  return changes
}
