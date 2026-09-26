const empty = value => value == null || value === '' || (Array.isArray(value) && !value.length)
const pathParts = path => String(path || '').split('.').filter(Boolean)
const safePath = parts => parts.length && !parts.some(part => ['__proto__', 'prototype', 'constructor'].includes(part))
const resolve = (value, path) => pathParts(path).reduce((current, key) => current?.[key], value)

/**
 * 用当前发布表单和未提交记录构造字段事件请求；服务端据发布坐标定位事件绑定，
 * 再用 input.form 执行映射，不能只传当前字段值，否则跨字段规则会读到旧数据。
 * @param {object} field 触发事件的字段，fieldCode/fieldKey 用于定位发布绑定。
 * @param {*} value 当前 UI 值；当表单模型已有该字段时，以模型值作为 input.value。
 * @param {object} context 当前表单、记录及取值函数，提供后续服务端解析需要的发布身份。
 * @param {*} selection 选择器额外选中内容，作为事件输入交给绑定脚本使用。
 * @returns {object|null} 请求信封；没有表单 ID 时不发起事件。
 */
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

/**
 * 将事件输出按覆盖策略写回当前记录。getRecord/setField 由宿主提供，
 * confirmOverwrite 可能异步等待用户确认，isCurrent 用于阻止旧事件覆盖后发的新输入。
 * @param {object} result 服务端事件结果，优先读取显式 FIELD_MAPPING 效果。
 * @param {object} handlers 当前记录读取、字段写入、覆盖确认和请求有效性检查函数。
 * @returns {Promise<void>} 完成所有有效映射后结束；非法路径和已过期事件不会写入。
 */
export async function applyRuntimeFieldEffects(result, { getRecord, setField, confirmOverwrite, isCurrent = () => true }) {
  const set = (path, value) => {
    // 嵌套路径最终会整体写回顶层字段，先拒绝原型链键并检查事件时序。
    const parts = pathParts(path); if (!safePath(parts) || !isCurrent()) return
    if (parts.length === 1) return setField(parts[0], value)
    const current = getRecord()[parts[0]], root = current && typeof current === 'object' ? JSON.parse(JSON.stringify(current)) : {}
    let target = root
    for (const key of parts.slice(1, -1)) { if (!target[key] || typeof target[key] !== 'object') target[key] = {}; target = target[key] }
    target[parts.at(-1)] = value; setField(parts[0], root)
  }
  const effects = Array.isArray(result?.effects) ? result.effects : []
  for (const effect of effects) {
    if (String(effect?.type || '').toUpperCase() !== 'FIELD_MAPPING') continue
    for (const mapping of Array.isArray(effect.mappings) ? effect.mappings : []) {
      if (!isCurrent()) return
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
