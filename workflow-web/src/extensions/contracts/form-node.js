/**
 * 节点 Vue 契约：使用 NODE 类型 JSON 清单。
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
  // 当前表单节点配置及绑定信息，修改值不能改写节点身份。
  node: { type: Object, required: true },
  // 宿主提供的当前模型；通过 update:modelValue 返回新值，数据形状见本文件说明。
  modelValue: { type: Object, default: () => ({}) },
  // 宿主整表或节点只读状态，不得在组件内部绕过。
  readonly: { type: Boolean, default: false },
  // 当前运行模式，具体取值见本文件协议。
  mode: { type: String, default: 'view' },
  // 宿主运行上下文；可选能力须检查存在性，不自行构造可信身份。
  context: { type: Object, default: () => ({}) },
  // 当前扩展的实例参数；与 JSON 清单的 configSchema 定义分开。
  config: { type: Object, default: () => ({}) },
  // 调用已发布接口的受控运行对象；预览时可能为空。
  dataSourceRuntime: { type: Object, default: null }
}

/** update:modelValue(nextFields)；当前自定义节点分支没有收集节点 validate()。 */
export const formNodeEmits = ['update:modelValue']
