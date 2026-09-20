import assert from 'node:assert/strict'
import { test } from 'node:test'
import { registerCustomValidator, getCustomValidatorOptions, isCustomValidatorApplicable } from '../../contracts/validator-registry.js'
import { registerProjectValidators } from '../../project/validators/index.js'
import { evaluateCustomValidators, validateCustomValidationConfig } from '../form-custom-validation.js'
import { createCustomValidationController } from '../form-custom-validation-runtime.js'
import { collectCrossFieldRuntimeFields } from '../form-cross-field-runtime.js'
import { normalizeFormFieldValidation } from '../form-node-property-schema.js'

registerProjectValidators()
const binding = (name = 'amount', params = { maxAmount: 1000 }, triggers = ['BLUR'], version = 1) => ({ name, version, params, triggers })
const field = (rules = [binding()]) => ({ id: 'a', fieldCode: 'amount', fieldType: 'DECIMAL', validationRules: { customValidators: { version: 1, rules } } })
const execute = (value, trigger = 'SUBMIT', f = field(), entityCode = 'expense') => evaluateCustomValidators(f, value, { entityCode, formData: { amount: value } }, trigger)

test('entity scope: all, selected, unknown identity, type and exact version', () => {
  const restricted = registerCustomValidator('scoped', { validate: () => true }, { supportedEntityCodes: ['expense', 'purchase'], supportedFieldTypes: ['DECIMAL'] })
  assert.equal(isCustomValidatorApplicable(restricted, 'expense', 'DECIMAL'), true)
  assert.equal(isCustomValidatorApplicable(restricted, 'purchase', 'DECIMAL'), true)
  assert.equal(isCustomValidatorApplicable(restricted, 'other', 'DECIMAL'), false)
  assert.equal(isCustomValidatorApplicable(restricted, '', 'DECIMAL'), false)
  assert.equal(isCustomValidatorApplicable(restricted, 'expense', 'STRING'), false)
  assert.ok(getCustomValidatorOptions('other', 'DECIMAL').some(item => item.name === 'amount'))
  assert.ok(!getCustomValidatorOptions('other', 'DECIMAL').some(item => item.name === 'scoped'))
  assert.throws(() => registerCustomValidator('scoped', { validate() {} }))
  assert.throws(() => registerCustomValidator(undefined, { validate() {} }))
  assert.throws(() => registerCustomValidator('badScope', { validate() {} }, { supportedEntityCodes: [' * ', 'expense'] }))
  assert.match(validateCustomValidationConfig(field([binding('amount', {}, [], 2)]).validationRules.customValidators, field(), 'expense')[0], /未安装/)
})

test('amount handles empties, zero, negative/nonfinite numbers and upper bound', async () => {
  for (const value of [null, undefined, '', 0, '0', 1000, '999.99']) assert.equal((await execute(value)).valid, true, String(value))
  for (const value of [-1, Infinity, NaN, ' ', false, {}, [], 1000.01]) assert.equal((await execute(value)).valid, false, String(value))
  assert.equal((await execute(1100, 'CHANGE')).valid, true)
  assert.equal((await execute(1100, 'BLUR')).valid, false)
  assert.equal((await execute(1100, 'SUBMIT', field([binding('amount', { maxAmount: 1000 }, [])]))).valid, false)
})

test('bad configuration, missing implementations and exceptions fail closed', async () => {
  for (const rules of [[binding('unknown')], [binding(), binding()], [binding('amount', { maxAmount: -1 })], [binding('amount', { extra: 1 })], [binding('amount', {}, ['FOCUS'])]]) {
    assert.equal((await execute(5, 'SUBMIT', field(rules))).valid, false)
  }
  assert.equal((await execute(1, 'SUBMIT', field([binding('scoped', {})]), 'other')).valid, false)
  registerCustomValidator('throws', { validate() { throw new Error('网络故障') } })
  assert.match((await execute(1, 'SUBMIT', field([binding('throws', {})]))).message, /网络故障/)
  registerCustomValidator('missingReturn', { validate() {} })
  assert.equal((await execute(1, 'SUBMIT', field([binding('missingReturn', {})]))).valid, false)
})

test('normalization and node projection preserve explicit clears and isolate child fields', () => {
  const empty = { version: 1, rules: [] }
  assert.deepEqual(normalizeFormFieldValidation('DECIMAL', { customValidators: empty }).customValidators, empty)
  const form = { fields: [field()], nodes: [
    { id: 'sub', nodeType: 'SUB_FORM' },
    { id: 'a', nodeType: 'FIELD', parentId: 'sub', bindingType: 'ENTITY_FIELD', bindingRef: 'amount', propsDocument: { fieldType: 'DECIMAL' }, rulesDocument: { validation: { customValidators: empty } } }
  ] }
  assert.equal(collectCrossFieldRuntimeFields(form).length, 0)
  assert.deepEqual(collectCrossFieldRuntimeFields(form, [], { rootParentId: 'sub' })[0].validationRules.customValidators, empty)
})

test('async races: changed records cancel submission and old errors cannot overwrite corrections', async () => {
  const requests = []
  registerCustomValidator('asyncRace', { validate(value, context) { return new Promise(resolve => requests.push({ value, context, resolve })) } })
  let record = { amount: 1 }
  let state = { visible: true, editable: true }
  const f = field([binding('asyncRace', {}, ['CHANGE'])])
  const controller = createCustomValidationController({ getFields: () => [f], getRecord: () => record, getContext: () => ({ entityCode: 'expense' }), getState: () => state })
  const first = controller.check('amount', 'CHANGE')
  record = { amount: 2 }
  const second = controller.check('amount', 'CHANGE')
  requests[1].resolve(true)
  assert.equal((await second).valid, true)
  requests[0].resolve('旧错误')
  assert.equal((await first).stale, true)
  assert.deepEqual(controller.getErrors(), {})
  const submission = controller.validate()
  record.amount = 3
  controller.refresh()
  assert.equal((await submission).valid, false)
  assert.equal(requests[2].context.signal.aborted, true)
  requests.at(-1).resolve('当前错误')
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(controller.getErrors().amount.message, '当前错误')
  state = { visible: false, editable: true }
  controller.refresh()
  assert.equal((await controller.validate()).valid, true)
  assert.deepEqual(controller.getErrors(), {})
  controller.dispose()
})

test('unconfigured, hidden and readonly fields skip validation; each form owns params', async () => {
  for (const state of [{ visible: false, editable: true }, { visible: true, editable: false }]) {
    const controller = createCustomValidationController({ getFields: () => [field()], getRecord: () => ({ amount: -1 }), getContext: () => ({}), getState: () => state })
    assert.equal((await controller.validate()).valid, true)
    controller.dispose()
  }
  assert.equal((await execute(15, 'SUBMIT', field([binding('amount', { maxAmount: 10 })]))).valid, false)
  assert.equal((await execute(15, 'SUBMIT', field([binding('amount', { maxAmount: 20 })]))).valid, true)
})

test('a delayed CHANGE without matching rules must preserve the BLUR error', async () => {
  const f = field()
  const record = { amount: 1200 }
  const changes = []
  const controller = createCustomValidationController({ getFields: () => [f], getRecord: () => record,
    getContext: () => ({ entityCode: 'expense' }), getState: () => ({ visible: true, editable: true }),
    onErrorsChange: errors => changes.push(errors.amount?.message || '') })
  try {
    assert.equal((await controller.check('amount', 'BLUR')).valid, false)
    await controller.check('amount', 'CHANGE')
    assert.match(controller.getErrors().amount?.message || '', /不能超过 1000/)
    assert.deepEqual(changes, ['金额不能超过 1000'], '未匹配的 change 不能把错误发布成通过')
    assert.equal((await controller.check('amount', 'BLUR')).valid, false)
    record.amount = 1300
    controller.refresh()
    await new Promise(resolve => setTimeout(resolve, 0))
    assert.match(controller.getErrors().amount?.message || '', /不能超过 1000/, '输入期间保留上次失焦提示，等到下一次失焦再更新')
    record.amount = 500
    assert.equal((await controller.check('amount', 'BLUR')).valid, true)
    assert.deepEqual(controller.getErrors(), {})
  } finally { controller.dispose() }
})

test('an unrelated CHANGE must not cancel a pending async BLUR', async () => {
  let finish
  let signal
  registerCustomValidator('blurRace', { validate(_value, context) { signal = context.signal; return new Promise(resolve => { finish = resolve }) } })
  const f = field([binding('blurRace', {}, ['BLUR'])])
  const controller = createCustomValidationController({ getFields: () => [f], getRecord: () => ({ amount: 1 }),
    getContext: () => ({}), getState: () => ({ visible: true, editable: true }) })
  try {
    const blur = controller.check('amount', 'BLUR')
    await controller.check('amount', 'CHANGE')
    assert.equal(signal.aborted, false)
    finish('异步校验不通过')
    assert.equal((await blur).valid, false)
    assert.equal(controller.getErrors().amount.message, '异步校验不通过')
  } finally { controller.dispose() }
})

test('record watchers never replay BLUR or SUBMIT while typing, including after prior validation', async () => {
  const calls = []
  registerCustomValidator('strictBlur', { validate(value, context) { calls.push([value, context.trigger]); return value >= 0 || '金额不能为负数' } })
  const f = field([binding('strictBlur', {}, ['BLUR'])])
  const record = { amount: 1, other: '' }
  let state = { visible: true, editable: true }
  const controller = createCustomValidationController({ getFields: () => [f], getRecord: () => record,
    getContext: () => ({}), getState: () => state })
  const refresh = async () => { controller.refresh(); await new Promise(resolve => setTimeout(resolve, 0)) }
  try {
    assert.equal((await controller.check('amount', 'BLUR')).valid, true)
    record.amount = -1
    await refresh()
    await controller.check('amount', 'CHANGE')
    assert.equal(calls.length, 1, '失焦通过后继续输入也不能重跑 BLUR')
    assert.deepEqual(controller.getErrors(), {})
    assert.equal((await controller.check('amount', 'BLUR')).valid, false)
    record.amount = 10
    record.other = '程序回填其他字段'
    await refresh()
    assert.equal(calls.length, 2, '其他字段或当前值变化均不等于失焦')
    assert.equal(controller.getErrors().amount.message, '金额不能为负数')
    assert.equal((await controller.check('amount', 'BLUR')).valid, true)
    assert.deepEqual(controller.getErrors(), {})
    record.amount = -2
    assert.equal((await controller.validate()).valid, false)
    record.amount = 20
    await refresh()
    assert.equal(calls.length, 4, '提交失败后继续输入不能重放 SUBMIT')
    assert.equal(controller.getErrors().amount.message, '金额不能为负数')
    assert.equal((await controller.validate()).valid, true)
    assert.deepEqual(calls.map(item => item[1]), ['BLUR', 'BLUR', 'BLUR', 'SUBMIT', 'SUBMIT'])
    record.amount = -1
    await controller.check('amount', 'BLUR')
    state = { visible: false, editable: true }
    await refresh()
    assert.deepEqual(controller.getErrors(), {}, '隐藏或只读时仍清除不适用错误')
  } finally { controller.dispose() }
})

test('editing during async BLUR cancels the old result without starting another BLUR', async () => {
  const requests = []
  registerCustomValidator('strictAsyncBlur', { validate(value, context) {
    return new Promise(resolve => requests.push({ value, signal: context.signal, resolve }))
  } })
  const f = field([binding('strictAsyncBlur', {}, ['BLUR'])])
  const record = { amount: 1 }
  const controller = createCustomValidationController({ getFields: () => [f], getRecord: () => record,
    getContext: () => ({}), getState: () => ({ visible: true, editable: true }) })
  try {
    const pending = controller.check('amount', 'BLUR')
    record.amount = 2
    controller.refresh()
    assert.equal((await pending).stale, true)
    assert.equal(requests[0].signal.aborted, true)
    assert.equal(requests.length, 1, '取消过期请求后必须等待下一次失焦')
    requests[0].resolve('旧错误')
    const latest = controller.check('amount', 'BLUR')
    requests[1].resolve(true)
    assert.equal((await latest).valid, true)
    assert.deepEqual(controller.getErrors(), {})
  } finally { controller.dispose() }
})

test('BLUR is never implicitly converted to CHANGE for custom or selection controls', async () => {
  for (const componentType of ['input', 'CUSTOM', 'ENTITY_SELECTOR', 'SWITCH']) {
    const f = { ...field(), componentType }
    assert.equal((await execute(1200, 'CHANGE', f)).valid, true, componentType)
    assert.equal((await execute(1200, 'BLUR', f)).valid, false, componentType)
    assert.equal((await execute(1200, 'SUBMIT', f)).valid, false, componentType)
  }
})
