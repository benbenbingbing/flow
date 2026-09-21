import { defineCustomValidator } from '../../contracts/validation.js'

// 示例与应用注册复用同一实现，避免两个金额校验产生不同结果。
export { AmountValidator } from '../../common/validators/AmountValidator.js'

/** 普通 JS 函数实现：只检查必填，不把 0/false 误判为空；同样对外暴露 validate。 */
export const requiredValidator = defineCustomValidator((value, context) => {
  const empty = value === null || value === undefined || value === ''
    || (typeof value === 'string' && !value.trim())
    || (Array.isArray(value) && value.length === 0)
  return empty ? `${context.field?.fieldName || '当前字段'}不能为空` : true
})
