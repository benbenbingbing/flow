/**
 * 节点 Vue 契约：registerFormNodeComponent(name, Component, metadata)。
 * 用于跨字段摘要、特殊节点展示等；仅改一个输入框时使用 form-field.js。
 * node.componentName/componentVersion 选择实现，nodeTypes/supportedBindings 限制匹配。
 *
 * 注意 modelValue 是整个业务字段对象；修改节点绑定值也要保留其他字段：
 * emit('update:modelValue', { ...props.modelValue, [props.node.fieldCode]: value })。
 * config 是 props.componentProps（兼容回退节点 props），宿主已按 snapshotVersion
 * 调用可选 migrateConfig。自定义节点不会自动收到字段组件的 options/disabled；
 * 实现可编辑节点时要自行落实发布字段规则，默认模板仅作只读摘要。
 */
export const formNodeProps = {
  node: { type: Object, required: true },
  modelValue: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false },
  mode: { type: String, default: 'view' },
  context: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null }
}

/** update:modelValue(nextFields)；当前自定义节点分支没有收集节点 validate()。 */
export const formNodeEmits = ['update:modelValue']
