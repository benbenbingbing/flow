const STANDARD_FIELDS = [
  { value: 'id', label: '记录 ID' },
  { value: 'name', label: '名称' },
  { value: 'code', label: '编码' },
  { value: 'status', label: '状态' }
]

/** 读取旧 JSON 存储，不把解析失败当成空配置，避免保存其他设置时丢失已有映射。 */
export function readSelectionReturnMappings(value) {
  let mappings
  try {
    mappings = typeof value === 'string' ? JSON.parse(value.trim() || '[]') : value ?? []
  } catch {
    throw new Error('已有返回映射无法读取，请检查原配置后重试。')
  }
  if (!Array.isArray(mappings)) throw new Error('已有返回映射必须是数组，请检查原配置后重试。')
  return mappings
}

/** 与运行时保持相同的别名优先级；界面只展示 selectionData 下的返回名称。 */
export function selectionMappingSource(mapping) {
  const value = mapping?.sourcePath || mapping?.sourceField
  return typeof value === 'string' ? value : ''
}

export function selectionMappingTarget(mapping) {
  const value = mapping?.targetPath || mapping?.targetField
  return typeof value === 'string' ? value.replace(/^selectionData\./, '') : ''
}

/** 仅替换用户编辑的一侧，保留旧配置的扩展属性和未编辑路径。 */
export function updateSelectionReturnMapping(mapping, key, value) {
  const updated = { ...mapping, [key]: value }
  delete updated[key === 'sourceField' ? 'sourcePath' : 'targetPath']
  return updated
}

/** 自定义字段使用 data 路径，系统实体直接读取行字段；存量特殊路径由控件额外展示。 */
export function selectionReturnFieldOptions(fields = [], systemEntity = false) {
  const options = new Map(STANDARD_FIELDS.map(field => [field.value, { ...field }]))
  for (const field of fields) {
    if (!field.fieldCode) continue
    const value = systemEntity || field.isSystem === true || field.isSystem === 1
      ? field.fieldCode
      : `data.${field.fieldCode}`
    options.set(value, { value, label: field.fieldName || field.fieldCode })
  }
  return [...options.values()]
}

/**
 * 保存前校验必填项和目标路径。重复名称或父子路径会在运行时互相覆盖，必须先由用户消除冲突。
 * 成功时原样返回映射数组，保留 sourcePath/targetPath 和未知扩展属性的兼容性。
 */
export function validateSelectionReturnMappings(value) {
  const mappings = readSelectionReturnMappings(value)
  const targets = []
  mappings.forEach((mapping, index) => {
    const prefix = `第 ${index + 1} 条返回映射：`
    const source = selectionMappingSource(mapping)
    const target = selectionMappingTarget(mapping)
    if (!source) throw new Error(`${prefix}请选择来源字段`)
    if (!target) throw new Error(`${prefix}请填写返回名称`)
    if ([source, target].some(path => /\s/.test(path) || path.split('.').some(part => !part))) {
      throw new Error(`${prefix}字段路径和返回名称不能包含空格或空的层级`)
    }
    if (target.split('.').some(part => ['__proto__', 'prototype', 'constructor'].includes(part))) {
      throw new Error(`${prefix}返回名称包含保留名称，请更换`)
    }
    const conflict = targets.findIndex(previous => previous === target
      || previous.startsWith(`${target}.`) || target.startsWith(`${previous}.`))
    if (conflict !== -1) {
      throw new Error(`${prefix}返回名称与第 ${conflict + 1} 条重复或存在层级冲突，请使用不同名称`)
    }
    targets.push(target)
  })
  return mappings
}
