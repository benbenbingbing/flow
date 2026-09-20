import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { useFormCustomValidation } from '../../composables/useFormCustomValidation.js'
import { useSubFormCustomValidation } from '../../composables/useSubFormCustomValidation.js'
import { registerProjectValidators } from '../../project/validators/index.js'
import { registerCustomValidator } from '../../contracts/validator-registry.js'

registerProjectValidators()
const config = { version: 1, rules: [{ name: 'amount', version: 1, params: { maxAmount: 10 }, triggers: ['CHANGE', 'BLUR'] }] }
const fields = [{ fieldCode: 'amount', fieldType: 'DECIMAL', validationRules: { customValidators: config } }]
const record = ref({ amount: 20 })
const context = ref({ entityCode: 'expense', initializationKey: 'create:1' })
const form = ref({ id: 'f1', runtimeReleaseId: 'r1', fields })
const scope = effectScope()
const validation = scope.run(() => useFormCustomValidation({ getForm: () => form.value, getRecord: () => record.value, getMode: () => 'edit', getContext: () => context.value }))
const settle = async () => { await nextTick(); await new Promise(resolve => setTimeout(resolve, 0)) }
try {
  assert.equal(validation.firstError.value, '')
  assert.equal((await validation.validate()).valid, false)
  context.value = { ...context.value, actionLoadingKey: 'saving' }
  form.value = { ...form.value }
  await settle()
  assert.match(validation.firstError.value, /不能超过 10/)
  record.value.amount = 5
  await settle()
  assert.equal(validation.firstError.value, '')
  assert.equal((await validation.validate()).valid, true)
  const change = validation.touchChanged(record.value, { amount: 30 })
  record.value = { amount: 30 }
  await change
  assert.match(validation.firstError.value, /不能超过 10/)
  context.value = { entityCode: 'expense', initializationKey: 'create:2' }
  await settle()
  assert.equal(validation.firstError.value, '', '切换新建记录后清空交互状态')
} finally { scope.stop() }

const childScope = effectScope()
const rows = ref([{ id: 'r1', amount: 20 }, { id: 'r2', amount: 5 }])
const child = childScope.run(() => useSubFormCustomValidation({
  enabled: () => true, getRows: () => rows.value, getFields: () => fields,
  getForm: () => ({ id: 'child' }), getContext: () => ({ entityCode: 'expense', mode: 'edit' }), getReadonly: () => false
}))
try {
  assert.equal((await child.validate()).valid, false)
  assert.match(child.errorFor(0, fields[0]), /不能超过 10/)
  assert.equal(child.errorFor(1, fields[0]), '')
  rows.value.splice(0, 1)
  await settle()
  assert.equal(child.errorFor(0, fields[0]), '')
  assert.equal((await child.validate()).valid, true)
} finally { childScope.stop() }
// 同时覆盖普通表单和旧版子表的深度 watch；值回填不能重放上一次失焦规则。
let blurCalls = 0
registerCustomValidator('blurWatch', { validate(value) { blurCalls++; return value <= 10 || '超过上限' } })
const blurFields = [{ fieldCode: 'amount', fieldType: 'DECIMAL', validationRules: { customValidators: {
  version: 1, rules: [{ name: 'blurWatch', version: 1, params: {}, triggers: ['BLUR'] }]
} } }]
for (const isChild of [false, true]) {
  const currentScope = effectScope()
  const currentRecord = ref({ amount: 20 })
  const validation = currentScope.run(() => isChild ? useSubFormCustomValidation({
    enabled: () => true, getRows: () => [currentRecord.value], getFields: () => blurFields,
    getForm: () => ({ id: 'child-blur' }), getContext: () => ({ entityCode: 'expense', mode: 'edit' }), getReadonly: () => false
  }) : useFormCustomValidation({
    getForm: () => ({ id: 'parent-blur', fields: blurFields }), getRecord: () => currentRecord.value,
    getMode: () => 'edit', getContext: () => ({ entityCode: 'expense' })
  }))
  const blur = () => isChild ? validation.check({ index: 0, field: blurFields[0] }, 'BLUR') : validation.runtime.onFieldBlur('amount')
  const error = () => isChild ? validation.errorFor(0, blurFields[0]) : validation.runtime.errorFor('amount')
  try {
    await blur()
    const previousCalls = blurCalls
    currentRecord.value.amount = 5
    await settle()
    assert.equal(blurCalls, previousCalls, `${isChild ? '子表' : '主表'}输入期间不执行 BLUR`)
    assert.equal(error(), '超过上限')
    await blur()
    assert.equal(error(), '')
    currentRecord.value.amount = 30
    await settle()
    assert.equal(blurCalls, previousCalls + 1)
    assert.equal(error(), '')
    assert.equal((await validation.validate()).valid, false)
  } finally { currentScope.stop() }
}
console.log('custom validation composable: initial state, submit, rerender, correction, identity, independent child rows and strict BLUR watchers passed')
