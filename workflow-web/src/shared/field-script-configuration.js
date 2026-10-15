import { fieldScriptEventSupported, readFieldScripts, validateFieldScript } from '@flow/workflow-core/browser/field-event-scripts'
import { resolveFieldComponentDescriptor } from '../extensions/core/registries/formFieldRegistry.js'

/** 使用真实渲染描述，不能用 fieldType 猜测自定义组件/引用组件的事件能力。 */
export function getFieldScriptEventCatalog(field) {
  const descriptor = resolveFieldComponentDescriptor(field)
  if (!descriptor) return { events: [], legacy: false, missing: true }
  if (descriptor.capabilities.scriptEvents !== undefined) return { events: descriptor.capabilities.scriptEvents, legacy: false }
  // 尚未迁移的扩展仅展示历史标准入口；任意旧事件仍在编辑器单独保留供核对。
  return { legacy: true, events: [
    { name: 'onInput', label: '输入中' }, { name: 'onChange', label: '值变化' },
    { name: 'onFocus', label: '聚焦' }, { name: 'onBlur', label: '失焦' }
  ].filter(item => fieldScriptEventSupported(field, item.name)) }
}

/** 发布前核对绑定与精确组件版本；错误保留字段位置，草稿仍可保存。 */
export function validateFieldScriptConfiguration(fields) {
  const errors = []
  for (const field of fields || []) {
    const catalog = getFieldScriptEventCatalog(field)
    for (const [name, binding] of Object.entries(readFieldScripts(field))) {
      try {
        if (catalog.missing) throw new Error('组件或指定版本未安装')
        if (!catalog.events.some(item => item.name === name)) throw new Error('当前组件未声明此事件')
        validateFieldScript(binding, field, name)
      } catch (error) { errors.push({ field, eventName: name, message: `${field.fieldLabel || field.fieldName || field.fieldCode || field.id} / ${name}：${error.message}` }) }
    }
  }
  return errors
}
