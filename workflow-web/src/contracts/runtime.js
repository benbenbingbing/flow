/**
 * 已有前端能力的薄入口；实现仍以原模块为准，未复制或改写任何运行逻辑。
 * 这里会加载 Vue/宿主依赖，适用于 workflow-web 内的页面；纯 JS 校验使用 validation.js。
 *
 * useFormField(props, emit)：共享字段值/配置/选项/事件逻辑；调用即安装默认值 watch，
 * 空值时可能 emit 默认值。普通页面支持字段脚本，异步完成后发送 change；Embed 页面仍禁止配置 JS。
 * safeParseConfig(value, fallback)：解析 JSON 配置；applySchemaDefaults(schema, config)
 * 为参数补默认值；buildRuntimeFieldRules(field, required, label) 复用已有字段规则。
 */
export { useFormField } from '../components/form-fields/composables/useFormField.js'
export {
  safeParseConfig, applySchemaDefaults, buildRuntimeFieldRules,
  isFieldVisibleForMode, isFieldReadonlyForMode
} from '../shared/config-runtime/index.js'
export { createCustomFormActionSlotContract } from '../shared/form-actions.js'
export { formatListFieldValue } from '../shared/list-runtime/index.js'
