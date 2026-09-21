/**
 * 整表单契约：在 <script setup> 中 defineProps(customFormProps)。
 * 使用 FORM 类型 JSON 清单注册。
 * 小幅修改布局可复用 templates/CustomFormTemplate.vue；完全自绘时自行处理
 * linkageState 的 visibility/disabled/required/options，以及字段权限和错误展示。
 *
 * modelValue 是业务字段对象，不是 { id, data } 记录信封。更新时 emit 一个新对象，
 * 保留未参与当前交互的字段。context.record 可能是记录信封，不能替代 modelValue。
 * 必须 defineExpose({ validate })：validate() => Promise<boolean>，false 阻止提交。
 * 这里的无参组件方法与 validation.js 的 validate(value, context) 是不同层次的协议。
 *
 * @typedef {Object} FormActionSlots
 * @property {number} version 当前为 1。
 * @property {Object<string, Object[]>} slots 按 slotKey 分组的可见 ACTION_SLOT 动作只读副本。
 * @property {function(string): boolean} trigger 传 runtimeKey/key；返回是否受理，不代表执行完成。
 *
 * @typedef {Object} FormContext
 * @property {string} [mode] create/edit/approve/view。
 * @property {string} [entityCode] 实体编码。
 * @property {Object} [entityDefinition] 实体定义。
 * @property {Object} [form] 当前表单配置。
 * @property {Object} [record] 宿主记录；有的场景为 { id, data }，有的场景为字段对象。
 * @property {Object} [params] 子表单输入参数，具体键由 inputParameterSchema 声明。
 * @property {Object} [parent] 子表单父级上下文。
 * @property {Object} [row] 子表单行上下文。
 * @property {Object} [formUniqueErrors] 字段唯一错误映射，兼容旧组件。
 * @property {{errors: Object, onFieldBlur: Function, checkField: Function}} [formUniqueness]
 *   onFieldBlur(fieldOrCode) / checkField(fieldOrCode, 'CHANGE'|'BLUR'|'SUBMIT')。
 *   是否执行仍受发布规则约束。请用可选链访问，预览或独立页面可能未提供。
 * @property {{errors: Object, onFieldBlur: Function, checkField: Function, errorFor: Function}} [formCustomValidation]
 *   已绑定规则由宿主在提交时统一校验；自绘输入调用 onFieldBlur(fieldCode)，
 *   errorFor(fieldCode) 读取错误文本。emit 新的 modelValue 触发 CHANGE。
 * @property {string} [releaseResolutionToken] 宿主发布上下文，只透传，不自行构造。
 */
export const customFormProps = {
  // 宿主选定的表单配置及发布上下文。
  form: { type: Object, required: true },
  // 宿主提供的当前模型；通过 update:modelValue 返回新值，数据形状见本文件说明。
  modelValue: { type: Object, default: () => ({}) },
  // 宿主整表或节点只读状态，不得在组件内部绕过。
  readonly: { type: Boolean, default: false },
  // 宿主提供的字段定义/选项，具体结构见本契约。
  fields: { type: Array, default: () => [] },
  // 宿主计算的显隐、禁用、必填、选项和值联动状态。
  linkageState: { type: Object, default: () => ({}) },
  // 稳定实体编码，不是数据库实体 ID。
  entityCode: { type: String, default: '' },
  // 当前实体定义，独立预览时可能为空。
  entityDefinition: { type: Object, default: null },
  // 当前实体字段元数据，区别于表单已选 fields。
  entityFields: { type: Array, default: () => [] },
  // 当前运行模式，具体取值见本文件协议。
  mode: { type: String, default: 'view' },
  // 值来自 form.viewConfig.customComponentProps，不是整个表单配置。
  config: { type: Object, default: () => ({}) },
  // 宿主运行上下文；可选能力须检查存在性，不自行构造可信身份。
  context: { type: Object, default: () => ({}) },
  // 调用已发布接口的受控运行对象；预览时可能为空。
  dataSourceRuntime: { type: Object, default: null },
  // 只包含当前可见动作的受控插槽，触发仍由宿主验证。
  formActionSlots: { type: Object, default: null }
}

/** update:modelValue(nextFields)；form-action(actionKey) 由宿主重新检查白名单。 */
export const customFormEmits = ['update:modelValue', 'form-action']
