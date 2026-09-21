/**
 * 单字段 Vue 契约。使用 FIELD 类型 JSON 清单注册；
 * 配置字段 componentType 为清单 name，查找忽略大小写。
 * 节点树设计器的 FIELD 扩展还使用 node.componentName 和
 * node.props.componentExtensionType='FIELD'，不能误标为 NODE；优先在设计器选组件。
 *
 * modelValue 仅为当前字段值；field 是完整字段配置。没有独立 config prop，
 * 自定义参数读取 field.componentProps（可能是 JSON 字符串）。
 * 可从 contracts/runtime.js 导入 useFormField 复用值同步、配置解析、选项、只读、
 * 默认值和标准事件，见 templates/CustomFieldTemplate.vue。
 * 宿主传入的 disabled 已包含联动和权限结果，组件必须遵守它。
 *
 * 复杂字段可额外 defineExpose({ validate })，无参返回 boolean/Promise<boolean>。
 * 自定义校验类需声明 VALIDATOR 清单 并在设计器字段中绑定才会自动执行。
 * 组件应 emit change/blur；宿主统一调用规则并在字段下方显示错误。
 */
export const formFieldProps = {
  // 宿主字段配置快照，包含字段编码和 componentProps；不要原地修改。
  field: { type: Object, required: true },
  // 宿主提供的当前模型；通过 update:modelValue 返回新值，数据形状见本文件说明。
  modelValue: { type: [String, Number, Array, Date, Object, Boolean], default: '' },
  // 宿主合并联动和权限后的禁用结果，组件必须遵守。
  disabled: { type: Boolean, default: false },
  // 宿主提供的候选项覆盖；null 表示使用字段自身配置。
  options: { type: Array, default: null },
  // 宿主运行上下文；可选能力须检查存在性，不自行构造可信身份。
  context: { type: Object, default: () => ({}) },
  // 调用已发布接口的受控运行对象；预览时可能为空。
  dataSourceRuntime: { type: Object, default: null },
  // 附件分类的动态必填状态，按分类键读取。
  attachmentItemRequiredState: { type: Object, default: () => ({}) }
}

/**
 * update:modelValue(value) 同步字段值；change(value) 驱动宿主的事件、联动和预检。
 * 一次交互各发送一次；blur(value)/focus(value) 使用当前值。
 * 只发送 update:modelValue 会漏掉宿主 change 链路。
 */
export const formFieldEmits = ['update:modelValue', 'change', 'blur', 'focus']
