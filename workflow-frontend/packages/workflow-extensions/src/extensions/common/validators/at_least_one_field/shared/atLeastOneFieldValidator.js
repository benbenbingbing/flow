import { CustomValidator } from '@flow/workflow-core/extensions/contracts/validation'

const FIELD_CODE = /^[A-Za-z][A-Za-z0-9_]{0,99}$/

/** 附件分组等复合值可能保留空容器，检查内容；0 和 false 都是有效填报值。 */
function hasValue(value) {
  if (value == null) return false
  if (typeof value === 'string') return value.trim() !== ''
  if (typeof value === 'number') return Number.isFinite(value)
  if (value instanceof Date) return Number.isFinite(value.getTime())
  if (typeof value === 'object') return Object.values(value).some(hasValue)
  return true
}

export class AtLeastOneFieldValidator extends CustomValidator {
  /**
   * 从当前表单快照读取指定字段，错误显示在绑定规则的字段上。
   * @param {*} value 绑定字段的当前值。该字段列入 fieldCodes 时参与判断。
   * @param {import('@flow/workflow-core/extensions/contracts/validation').ValidationContext} [context={}] formData 为同一表单快照，params.fieldCodes 指定参与判断的字段编码。
   * @returns {true|string} 任一字段有值返回 true，全部为空返回错误提示。
   * @throws {TypeError} fieldCodes 缺失、含重复项或编码格式无效。
   */
  validate(value, context = {}) {
    const { fieldCodes, message } = context.params || {}
    if (!Array.isArray(fieldCodes) || fieldCodes.length < 2
        || [...fieldCodes].some(code => typeof code !== 'string' || !FIELD_CODE.test(code))
        || new Set(fieldCodes).size !== fieldCodes.length) {
      throw new TypeError('请配置至少两个不同的有效字段编码')
    }
    const formData = context.formData || {}
    // 使用当前字段传入的值，其余字段只读取快照自身的属性。
    const filled = fieldCodes.some(code => code === context.fieldCode
      ? hasValue(value)
      : Object.hasOwn(formData, code) && hasValue(formData[code]))
    return filled || message?.trim() || '请至少填写指定字段中的一项'
  }
}
