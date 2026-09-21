const empty = value => value == null || value === '' || (Array.isArray(value) && !value.length)
const pathParts = path => String(path || '').split('.').filter(Boolean)
const safePath = parts => parts.length && !parts.some(part => ['__proto__', 'prototype', 'constructor'].includes(part))
const resolve = (value, path) => pathParts(path).reduce((current, key) => current?.[key], value)

/** 字段事件使用当前已发布表单和未提交数据，不允许宿主改变服务端协议。 */
export function buildFieldEventPayload(field, value, context, selection = null) {
  const form = context.form || {}, id = form.id || form.formId || form.entityFormId
  if (!id) return null
  return {
    configType: 'FORM', configId: String(id), releaseId: form.runtimeReleaseId || form.activeReleaseId || undefined,
    releaseVersion: form.runtimeReleaseVersion || undefined, releaseResolutionToken: form.releaseResolutionToken || context.releaseResolutionToken || undefined,
    entityCode: context.entityCode, targetType: 'FIELD', targetKey: String(field.fieldCode || field.fieldKey || field.id), recordId: context.record?.id,
    selection, input: { value: (context.getFormData && Object.hasOwn(context.getFormData(), field.fieldCode) ? context.getFormData()[field.fieldCode] : value), form: context.getFormData?.() || context.record?.data || context.record || {}, selection: value }, context: { formId: String(id), mode: context.mode || '' }
  }
}

/** 回填策略共享；确认框和记录写入由宿主注入，异步确认后再次检查事件是否仍有效。 */
export async function applyRuntimeFieldEffects(result, { getRecord, setField, confirmOverwrite, isCurrent = () => true }) {
  const set = (path, value) => {
    const parts = pathParts(path); if (!safePath(parts) || !isCurrent()) return
    if (parts.length === 1) return setField(parts[0], value)
    const current = getRecord()[parts[0]], root = current && typeof current === 'object' ? JSON.parse(JSON.stringify(current)) : {}
    let target = root
    for (const key of parts.slice(1, -1)) { if (!target[key] || typeof target[key] !== 'object') target[key] = {}; target = target[key] }
    target[parts.at(-1)] = value; setField(parts[0], root)
  }
  const effects = Array.isArray(result?.effects) ? result.effects : []
  for (const effect of effects) {
    if (effect.type !== 'FIELD_MAPPING') continue
    for (const mapping of effect.mappings || []) {
      const target = String(mapping.targetPath || ''), path = target.replace(/^form\./, '').replace(/^data\./, '')
      if (!safePath(pathParts(path))) continue
      const value = resolve(effect.data, target), current = resolve(getRecord(), path), policy = String(mapping.overwrite || 'ALWAYS').toUpperCase()
      if (mapping.clearOnEmpty === false && empty(value)) continue
      if (policy === 'IF_EMPTY' && !empty(current)) continue
      if (policy === 'CONFIRM' && !empty(current) && current !== value && !(await confirmOverwrite?.(path))) continue
      set(path, value)
    }
  }
  if (!effects.length && result?.data && typeof result.data === 'object') {
    for (const [key, value] of Object.entries(result.data.form || result.data.data || result.data)) set(key, value)
  }
}
