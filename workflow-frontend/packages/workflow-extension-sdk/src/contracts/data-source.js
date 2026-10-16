/**
 * 自定义表单、字段和节点都可接收宿主提供的 dataSourceRuntime prop。
 * 它是已有受控接口调用协议，不是可在浏览器注册后端 Provider 的新入口。
 * 字段/节点 owner 必须是宿主传入的配置对象，接口和输入输出映射需先在设计器发布。
 *
 * @typedef {Object} FormDataSourceContext
 * @property {Object} [record] 当前业务字段对象，如 props.modelValue（整表单场景）。
 * @property {Object} [input] 本次输入，如 { fieldCode, value }。
 * @property {Object} [params] 子表单参数。
 * @property {Object} [parent] 父表单上下文。
 * @property {Object} [row] 子表单行上下文。
 * @property {Object} [form] 嵌套场景的实际表单，避免错用根表单身份。
 *
 * @typedef {Object} FormDataSourceRuntime
 * @property {function(Object, FormDataSourceContext=): Promise<*>} execute
 *   执行一个已配置绑定；绑定含 extensionId，返回按 outputMapping 映射后的结果。
 * @property {function(Object, string, FormDataSourceContext=): Promise<Array<*>>} executeOwnerUsage
 *   按 owner、usage 顺序执行全部绑定并返回结果数组；无绑定返回 []，异常直接传播。
 *   方法本身不会把返回值写入自定义组件 modelValue，业务需显式 emit 回填。
 * @property {function(Object): Promise<*>} initialize 宿主初始化流程，扩展通常不重复调用。
 * @property {function(Object, FormDataSourceContext=): Promise<Array<*>>} loadOptions 读取字段选项数组。
 * @property {function(Object, FormDataSourceContext=): Promise<Array<*>>} loadSubformRows 读取子表单行数组。
 * @property {function(Object): Promise<*>} prevalidateBeforeSubmit
 *   仅执行 sideEffectFree=true 且 clientPrevalidate=true 的 BEFORE_SUBMIT 绑定。
 * @property {function(Object): Promise<*>} beforeSubmit 上述浏览器预校验方法的兼容入口。
 * @property {function(Object|Function): FormDataSourceRuntime} withContext
 *   创建继承父级上下文的运行时；函数形式在每次调用时读取最新状态。
 *
 * 示例（放在组件方法中，先检查 dataSourceRuntime 是否存在）：
 * const results = await props.dataSourceRuntime.executeOwnerUsage(
 *   props.node, 'FIELD_COMPUTE', { record: props.modelValue, input: { value: 10 } }
 * )
 * 按接口实际 outputSchema/outputMapping 解读 results，不能假定总是 results[0].data.value。
 */
export const FORM_DATA_SOURCE_USAGES = Object.freeze({
  FORM_INIT: 'FORM_INIT',
  AFTER_LOAD: 'AFTER_LOAD',
  BEFORE_SUBMIT: 'BEFORE_SUBMIT',
  FIELD_OPTIONS: 'FIELD_OPTIONS',
  FIELD_DEFAULT: 'FIELD_DEFAULT',
  FIELD_COMPUTE: 'FIELD_COMPUTE',
  SUBFORM_ROWS: 'SUBFORM_ROWS'
})
