

export function fieldOptions(fields = []) {
  return (Array.isArray(fields) ? fields : [])
    .filter(field => field?.uiConfigurable !== false && field?.fieldCode)
    .map(field => ({
      value: field.fieldCode,
      label: field.fieldName || field.fieldLabel || field.fieldCode,
      raw: field
    }))
}

/**
 * 映射下拉只展示宿主或目标内容真实暴露的字段。服务端仍会在发布和执行时
 * 按精确快照复核；这里提前收窄，是为了避免出现“配置能保存、运行却不可用”。
 */
export function contentScopedFieldOptions(entityOptions, contentFields, contentType, editable) {
  const rows = Array.isArray(contentFields) ? contentFields : []
  if (!rows.length) return entityOptions
  const normalizedType = String(contentType || '').toUpperCase()
  const allowed = new Set(rows.filter(field => {
    if (normalizedType === 'LIST') {
      if (field.showInList === false || Number(field.showInList) === 0) return false
      const sourceType = String(field.dataSourceType || 'ENTITY_FIELD').toUpperCase()
      return ['ENTITY_FIELD', 'REFERENCE', ''].includes(sourceType)
    }
    if (field.isHidden === true || Number(field.isHidden) === 1) return false
    if (editable && (field.isReadonly === true || Number(field.isReadonly) === 1)) {
      return false
    }
    return true
  }).map(field => String(
    field.fieldCode || field.bindingRef || field.field?.fieldCode || ''
  )).filter(Boolean))
  return entityOptions.filter(option => allowed.has(String(option.value)))
}

export function schemaFieldOptions(schema) {
  const document = typeof schema === 'string' ? safeParse(schema) : (schema || {})
  return Object.entries(document?.properties || {}).map(([key, value]) => ({
    value: key,
    label: value?.title || value?.description || key
  }))
}

export function safeParse(value) {
  try {
    return value ? JSON.parse(value) : {}
  } catch {
    return {}
  }
}

export function isPublishedAsset(item = {}) {
  if (item.activeReleaseId || Number(item.publishedVersion || 0) > 0) return true
  // status=1 在历史表单/列表中仅表示启用，并不代表已有可钉定的发布快照。
  // 只有明确的发布信息才允许被关联内容引用，避免保存时才被服务端拒绝。
  return ['PUBLISHED', 'ACTIVE'].includes(String(item.status || '').toUpperCase())
}

export function normalizeRows(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.records)) return response.records
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.list)) return response.list
  return []
}

export function interfaceScopeLabel(item) {
  return {
    GLOBAL: '全部页面可用',
    ENTITY: '指定实体可用',
    FORM: '指定表单可用',
    LIST: '指定列表可用'
  }[item.scopeType] || '受控范围'
}