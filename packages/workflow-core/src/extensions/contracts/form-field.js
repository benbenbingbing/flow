/**
 * 统一表单组件契约：内置组件与项目组件都使用 FIELD 清单及 props.componentType。
 * modelValue 是当前绑定字段值；formData 是当前表单或子表行，根表数据通过 context 访问。
 * 组件只能发出值事件，不能原地修改 formData/node/config；跨字段修改使用 patch-form。
 * 宿主统一负责字段状态、标签、联动、事件和校验；组件必须遵守 disabled/readonly。
 * 复杂组件可 expose validate() 返回 boolean/Promise<boolean>，由宿主提交校验调用。
 */
export const formFieldProps = {
  // 当前作用域的完整业务数据；子表组件拿到当前行，不能把它当根表写回。
  formData: { type: Object, default: () => ({}) },
  // 当前节点及已解码参数；无节点的字段列表仍遵循相同组件协议。
  node: { type: Object, default: null },
  config: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false },
  mode: { type: String, default: 'view' },
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
 * patch-form({ fieldCode: value }) 原子更新当前作用域的多个字段；宿主负责对应 change 链。
 * 只发送 update:modelValue 会漏掉宿主 change 链路。
 */
export const formFieldEmits = ['update:modelValue', 'patch-form', 'change', 'blur', 'focus']
