export function applySelectionReturnMappings(
  row: Record<string, any>,
  mappings: any[]
) {
  if (!Array.isArray(mappings) || mappings.length === 0) {
    return row
  }
  const selectionData: Record<string, any> = {}
  mappings.forEach(mapping => {
    const sourcePath = mapping.sourcePath || mapping.sourceField
    const targetPath = mapping.targetPath || mapping.targetField
    if (!sourcePath || !targetPath) return
    setValue(
      selectionData,
      targetPath,
      getValue(row, sourcePath)
    )
  })
  return {
    ...row,
    selectionData
  }
}

function getValue(row: Record<string, any>, path: string) {
  const direct = resolvePath(row, path)
  if (direct !== undefined) return direct
  return resolvePath(row?.data, path.replace(/^data\./, ''))
}

function resolvePath(source: any, path: string) {
  if (!source || !path) return undefined
  return String(path)
    .split('.')
    .reduce((value: any, key: string) => value?.[key], source)
}

function setValue(target: Record<string, any>, path: string, value: any) {
  const parts = String(path).replace(/^selectionData\./, '').split('.')
  let current = target
  parts.forEach((part, index) => {
    if (index === parts.length - 1) {
      current[part] = value
      return
    }
    if (!current[part] || typeof current[part] !== 'object') {
      current[part] = {}
    }
    current = current[part]
  })
}
