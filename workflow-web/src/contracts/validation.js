/**
 * 新增的业务校验契约，尚未接入现有表单、设计器或发布配置。
 * JS 文件可以继承 CustomValidator，也可导出 { validate(value, context) }，
 * 或通过 defineCustomValidator(fn) 实现。页面显式调用 validateCustomValue。
 * 此模块不注册全局规则、不发请求、不修改 value/context、不弹消息。
 *
 * @typedef {Object} ValidationContext
 * @property {string} [fieldCode] 当前字段编码。
 * @property {Object} [field] 当前字段配置。
 * @property {Object} [formData] 本次校验的整表单字段快照，跨字段比较使用它。
 * @property {Object} [record] 可选记录信封（如包含 id），不自动从 formData 构造。
 * @property {Object} [form] 表单配置。
 * @property {string} [entityCode] 实体编码。
 * @property {'create'|'edit'|'approve'|'view'} [mode] 当前场景。
 * @property {'CHANGE'|'BLUR'|'SUBMIT'} [trigger] 调用方决定的触发时机。
 * @property {Object} [params] 业务规则参数，由调用方明确提供。
 * @property {AbortSignal} [signal] 可选取消信号；异步实现自行传给请求工具。
 *
 * @typedef {{valid: boolean, message: string}} ValidationResult
 * @typedef {boolean|string|{valid: boolean, message?: string}} ValidationOutcome
 * @callback Validate
 * @param {*} value 待校验值；0/false/空串/null 不会被契约自动忽略。
 * @param {ValidationContext} context 调用方上下文，实现只能读取，不应修改。
 * @returns {ValidationOutcome|Promise<ValidationOutcome>}
 *   true 表示通过，false 使用默认失败提示，非空字符串表示失败原因；也可返回结构化结果。
 *   空值、只读跳过策略由具体业务实现决定。业务不通过用返回值；网络或程序错误抛异常。
 */
export class CustomValidator {
  /**
   * 校验一个业务值。子类必须覆写此方法；可直接声明 async validate(value, context)。
   * @param {*} value 待校验值。
   * @param {ValidationContext} context 本次调用上下文。
   * @returns {ValidationOutcome|Promise<ValidationOutcome>}
   * @throws {Error} 未实现时立即报错，防止遗漏实现被当作校验通过。
   */
  validate(value, context) {
    throw new Error('自定义校验必须实现 validate(value, context)')
  }
}

/**
 * 将普通 JS 函数包装为相同契约，适合只有一条业务判断的规则。
 * @param {Validate} validate 业务校验函数。
 * @returns {{validate: Validate}} 可直接调用或交给 validateCustomValue 的规则对象。
 * @throws {TypeError} 参数不是函数。
 */
export function defineCustomValidator(validate) {
  if (typeof validate !== 'function') {
    throw new TypeError('自定义校验必须提供 validate 函数')
  }
  return { validate }
}

/**
 * 执行同步/异步规则并统一结果，保留类实例的 this 以支持实例参数与依赖。
 * 所有异常继续向调用方传播；不要在 catch 中把接口故障变成通过。
 * 并发输入时，由页面负责取消过期请求或只显示最新一次结果。
 * @param {{validate: Validate}} validator 规则实例或普通契约对象。
 * @param {*} value 待校验值，不做隐式转换。
 * @param {ValidationContext} [context={}] 当前调用上下文。
 * @returns {Promise<ValidationResult>} 始终显式读取 result.valid，不能判断对象真值。
 * @throws {TypeError} 未提供方法或返回 undefined/null/数字/空字符串/非法对象。
 */
export async function validateCustomValue(validator, value, context = {}) {
  if (!validator || typeof validator.validate !== 'function') {
    throw new TypeError('校验器必须实现 validate(value, context)')
  }
  const outcome = await validator.validate(value, context)
  if (typeof outcome === 'boolean') {
    return { valid: outcome, message: outcome ? '' : '校验未通过' }
  }
  if (typeof outcome === 'string' && outcome.trim()) {
    return { valid: false, message: outcome }
  }
  if (outcome && typeof outcome === 'object' && !Array.isArray(outcome)
      && typeof outcome.valid === 'boolean'
      && (outcome.message === undefined || typeof outcome.message === 'string')) {
    return {
      valid: outcome.valid,
      message: outcome.valid ? '' : (outcome.message?.trim() || '校验未通过')
    }
  }
  // 常见失误是 async 方法忘记 return；禁止把 undefined 隐式视为成功。
  throw new TypeError('validate 必须返回 boolean、非空错误字符串或 { valid, message? }')
}

/**
 * 供采用 Element Plus el-form 的新页面显式接入，免写 callback/Promise 转换。
 * 用法：{ validator: createElementPlusValidator(rule, () => context), trigger: 'blur' }。
 * getContext 每次调用读取最新状态；trigger 并非 Element Plus 自动注入，需自行填写。
 * @param {{validate: Validate}} validator 自定义规则。
 * @param {function(): ValidationContext} [getContext] 获取本次调用上下文的同步函数。
 * @returns {function(Object, *): Promise<void>} Element Plus validator(rule, value)。
 * @throws {Error} 校验失败以 rejection 交由表单展示；执行异常也原样传播。
 */
export function createElementPlusValidator(validator, getContext = () => ({})) {
  return async (_rule, value) => {
    const result = await validateCustomValue(validator, value, getContext())
    if (!result.valid) throw new Error(result.message)
  }
}
