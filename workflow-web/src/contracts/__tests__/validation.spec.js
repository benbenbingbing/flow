import assert from 'node:assert/strict'
import test from 'node:test'
import {
  CustomValidator, defineCustomValidator, validateCustomValue, createElementPlusValidator
} from '../validation.js'
import { AmountValidator, requiredValidator } from '../examples/AmountValidator.js'

test('校验结果使用显式 valid，业务错误文本不会被当作 truthy 成功', async () => {
  assert.deepEqual(await validateCustomValue(defineCustomValidator(() => true), 0), {
    valid: true, message: ''
  })
  assert.deepEqual(await validateCustomValue(defineCustomValidator(() => false), 0), {
    valid: false, message: '校验未通过'
  })
  assert.deepEqual(await validateCustomValue(defineCustomValidator(() => '额度不足'), 20), {
    valid: false, message: '额度不足'
  })
  assert.deepEqual(await validateCustomValue({ validate: () => ({ valid: false }) }, 0), {
    valid: false, message: '校验未通过'
  })
  assert.deepEqual(await validateCustomValue({ validate: () => ({ valid: true, message: '旧错误' }) }, 0), {
    valid: true, message: ''
  })
})

test('类实例保留 this，异步跨字段规则收到当前上下文和原值', async () => {
  class BudgetValidator extends CustomValidator {
    constructor(limit) {
      super()
      this.limit = limit
    }

    /** 用只读上下文验证跨字段总额，Promise 完成前调用方必须等待。 */
    async validate(value, context) {
      await Promise.resolve()
      return value + context.formData.spent <= this.limit || '超出总预算'
    }
  }
  const context = Object.freeze({ formData: Object.freeze({ spent: 80 }) })
  const validator = new BudgetValidator(100)
  assert.equal((await validateCustomValue(validator, 20, context)).valid, true)
  assert.deepEqual(await validateCustomValue(validator, 21, context), {
    valid: false, message: '超出总预算'
  })
})

test('空值与 false/0 不被框架静默跳过或转换', async () => {
  for (const value of [undefined, null, '', false, 0]) {
    let called = false
    const context = { trigger: 'BLUR' }
    await validateCustomValue(defineCustomValidator((actual, received) => {
      called = true
      assert.equal(actual, value)
      assert.equal(received, context)
      return true
    }), value, context)
    assert.equal(called, true)
  }
})

test('忘记实现或漏写 return 会失败，不能悄悄放行提交', async () => {
  assert.throws(() => new CustomValidator().validate(1, {}), /必须实现/)
  await assert.rejects(validateCustomValue({}, 1), TypeError)
  assert.throws(() => defineCustomValidator(null), TypeError)
  const invalidResults = [undefined, null, '', '  ', 1, 0, [], {}, { valid: 'false' }, { valid: false, message: 1 }]
  for (const result of invalidResults) {
    await assert.rejects(validateCustomValue({ validate: async () => result }, 1), TypeError)
  }
})

test('接口拒绝、程序异常和取消异常原样传播', async () => {
  const failure = new Error('接口不可用')
  await assert.rejects(validateCustomValue({ validate() { throw failure } }, 1), error => error === failure)
  await assert.rejects(validateCustomValue({ async validate() { throw failure } }, 1), error => error === failure)
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(validateCustomValue({
    validate(_value, context) {
      context.signal.throwIfAborted()
      return true
    }
  }, 1, { signal: controller.signal }), { name: 'AbortError' })
})

test('并行调用不在契约内部共享结果或上下文', async () => {
  let releaseFirst
  const firstPending = new Promise(resolve => { releaseFirst = resolve })
  const validator = defineCustomValidator(async (_value, context) => {
    if (context.params.first) await firstPending
    return context.params.first ? '第一条失败' : true
  })
  const first = validateCustomValue(validator, 1, { params: { first: true } })
  assert.equal((await validateCustomValue(validator, 2, { params: { first: false } })).valid, true)
  releaseFirst()
  assert.deepEqual(await first, { valid: false, message: '第一条失败' })
})

test('Element Plus 适配器每次读取最新上下文，并拒绝业务失败', async () => {
  let limit = 10
  const validate = createElementPlusValidator(new AmountValidator(), () => ({ params: { maxAmount: limit } }))
  await assert.rejects(validate({}, 11), /金额不能超过 10/)
  limit = 20
  assert.equal(await validate({}, 11), undefined)
  const failure = new Error('加载额度失败')
  const failed = createElementPlusValidator({ async validate() { throw failure } })
  await assert.rejects(failed({}, 11), error => error === failure)
})

test('金额和必填示例组合处理边界值，不用数字隐式转换接受布尔或数组', async () => {
  const amount = new AmountValidator()
  for (const value of [0, '0', 100, '100']) {
    assert.equal((await validateCustomValue(amount, value, { params: { maxAmount: 100 } })).valid, true)
  }
  for (const value of [-1, 101, NaN, Infinity, false, [], {}, ' ']) {
    assert.equal((await validateCustomValue(amount, value, { params: { maxAmount: 100 } })).valid, false)
  }
  for (const value of [null, undefined, '']) {
    assert.equal((await validateCustomValue(amount, value)).valid, true)
    assert.equal((await validateCustomValue(requiredValidator, value)).valid, false)
  }
  assert.equal((await validateCustomValue(requiredValidator, false)).valid, true)
  assert.equal((await validateCustomValue(requiredValidator, 0)).valid, true)
  await assert.rejects(validateCustomValue(amount, 1, { params: { maxAmount: '100' } }), TypeError)
})
