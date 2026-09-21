import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { useFormCrossFieldValidation } from '@flow/workflow-core/vue/useFormCrossFieldValidation'

// 保存按钮切换 loading 会让父组件重建 context；内容未变时必须保留就地错误。
const config = { version: 1, rules: [{
  id: 'range', operator: 'GE', targetFieldCode: 'processStartTime', message: '结束时间不能早于开始时间'
}] }
const form = ref({ id: 'form-1', runtimeReleaseId: 'release-1', fields: [
  { fieldCode: 'processStartTime', fieldType: 'DATETIME' },
  { fieldCode: 'processEndTime', fieldType: 'DATETIME', validationRules: { crossField: config } }
] })
const record = ref({ processStartTime: '2026-09-23 00:00:00', processEndTime: '2026-09-13 00:00:00' })
const context = ref({ record: { id: null }, initializationKey: 'create:1' })
const scope = effectScope()
const validation = scope.run(() => useFormCrossFieldValidation({
  getForm: () => form.value, getRecord: () => record.value, getMode: () => 'create', getContext: () => context.value
}))
try {
  assert.equal(validation.firstError.value, '', '首次加载不显示错误')
  for (const action of ['save', 'saveAndStart']) {
    context.value = { ...context.value, actionLoadingKey: action }
    assert.equal((await validation.validate()).valid, false)
    context.value = { ...context.value, actionLoadingKey: '' }
    form.value = { ...form.value }
    await nextTick()
    assert.equal(validation.firstError.value, config.rules[0].message, `${action} 结束后应保留错误`)
  }

  record.value.processEndTime = record.value.processStartTime
  await nextTick()
  assert.equal(validation.firstError.value, '', '修正为相等后自动清除错误')
  assert.equal((await validation.validate()).valid, true)

  const serverError = { fieldCode: 'processEndTime', ruleId: 'range', targetFieldCode: 'processStartTime', message: '提交处理后的日期不符合要求' }
  validation.applyServerErrors([serverError])
  context.value = { ...context.value }
  await nextTick()
  assert.equal(validation.firstError.value, serverError.message, '上下文重建也不能清空服务端终检错误')

  record.value.processEndTime = '2026-09-13 00:00:00'
  for (const switchIdentity of [
    () => { context.value = { ...context.value, initializationKey: 'create:2' } },
    () => { context.value = { ...context.value, record: { id: 'record-2' } } },
    () => { form.value = { ...form.value, runtimeReleaseId: 'release-2' } },
    () => { form.value = { ...form.value, id: 'form-2' } }
  ]) {
    assert.equal((await validation.validate()).valid, false)
    switchIdentity()
    await nextTick()
    assert.equal(validation.firstError.value, '', '真正切换表单或记录后重置交互状态')
  }
} finally {
  scope.stop()
}
console.log('cross-field composable: submit feedback, rerender, correction, server errors and identity reset passed')
