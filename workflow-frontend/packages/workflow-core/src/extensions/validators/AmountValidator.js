import { CustomValidator } from '../contracts/validation.js'

/**
 * 金额校验：单据金额必须为有限数字且不超过调用方传入的额度。
 * 无全局状态、不请求接口；空值交给独立必填规则处理，0 仍参与范围判断。
 */
export class AmountValidator extends CustomValidator {
  /**
   * @param {*} value 当前金额，只接受 number 或非空数字字符串。
   * @param {import('@flow/workflow-core/extensions/contracts/validation').ValidationContext} [context={}] params.maxAmount 为可选额度。
   * @returns {import('@flow/workflow-core/extensions/contracts/validation').ValidationOutcome} true 通过，否则返回错误文本。
   * @throws {TypeError} 额度配置不是非负有限数字，应由调用页面报告配置错误。
   */
  validate(value, context = {}) {
    const maximum = context.params?.maxAmount
    if (maximum !== undefined && (typeof maximum !== 'number' || !Number.isFinite(maximum) || maximum < 0)) {
      throw new TypeError('maxAmount 必须是非负有限数字')
    }
    if (value === null || value === undefined || value === '') return true
    if (!['number', 'string'].includes(typeof value) || (typeof value === 'string' && !value.trim())) {
      return '金额必须是有效数字'
    }
    const amount = Number(value)
    if (!Number.isFinite(amount)) return '金额必须是有效数字'
    if (amount < 0) return '金额不能小于 0'
    if (maximum !== undefined && amount > maximum) return `金额不能超过 ${maximum}`
    return true
  }
}

