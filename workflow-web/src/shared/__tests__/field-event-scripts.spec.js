import assert from 'node:assert/strict'
import { effectScope, reactive, nextTick } from 'vue'
import { compileFieldScript, fieldScriptEnvironmentError, readFieldScripts, writeFieldScripts, runFieldScript } from '../field-event-scripts.js'
import { useFormField } from '../../extensions/builtin/fields/composables/useFormField.js'

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const draft = { componentProps: JSON.stringify({ events: { onChange: 'old', onBlur: 'blur' } }), eventOnChange: '' }
assert.deepEqual(readFieldScripts(draft), { onBlur: 'blur' })
assert.deepEqual(readFieldScripts({ componentProps: '{' }), {})
const persisted = { componentProps: '{"placeholder":"保留","events":{"onChange":"old"}}', eventOnChange: 'old' }
writeFieldScripts(persisted, { onChange: '', onFocus: 'new' })
assert.deepEqual(readFieldScripts(persisted), { onFocus: 'new' })
assert.deepEqual(readFieldScripts({ componentProps: persisted.componentProps }), { onFocus: 'new' })
writeFieldScripts(persisted, {})
assert.deepEqual(readFieldScripts(persisted), {})
assert.deepEqual(JSON.parse(persisted.componentProps), { placeholder: '保留' })
assert.throws(() => compileFieldScript('if ('), SyntaxError)
assert.doesNotThrow(() => compileFieldScript('await Promise.resolve(); return value'))
assert.equal(fieldScriptEnvironmentError({ pathname: '/entity-form/1' }), '')
assert.match(fieldScriptEnvironmentError({ pathname: '/embed/v1/launches/launch1' }), /嵌入页面/)

const form = { target: '', source: 'read' }
const writes = []
let current = true
const context = {
  value: 'id1', field: { fieldCode: 'user' }, selection: { name: '张三' }, event: { name: 'onChange' },
  isCurrent: () => current, setValue: value => writes.push(value),
  getFieldValue: key => form[key], setFieldValue: (key, value) => { form[key] = value }
}
await runFieldScript("await Promise.resolve(); setValue(value); setFieldValue('target', selection.name + getFieldValue('source'))", context)
assert.deepEqual(writes, ['id1'])
assert.equal(form.target, '张三read')
await assert.rejects(runFieldScript("throw new Error('sync')", context), /sync/)
await assert.rejects(runFieldScript("await Promise.reject(new Error('async'))", context), /async/)
await assert.rejects(runFieldScript("await new Promise(r => setTimeout(r, 35)); setValue('late')", context, { timeout: 5 }), /超过/)
await sleep(45)
assert.deepEqual(writes, ['id1'], '超时脚本恢复后不能继续通过 helper 写值')
current = false
await runFieldScript("setValue('stale')", context)
assert.deepEqual(writes, ['id1'])

const scope = effectScope()
const props = reactive({ field: { fieldCode: 'text', fieldName: '文本', componentType: 'input' }, modelValue: '', disabled: false })
const emitted = []
const api = scope.run(() => useFormField(props, (name, value) => {
  if (name === 'update:modelValue') props.modelValue = value
  else emitted.push([name, value])
}))
props.field.eventOnChange = "await Promise.resolve(); setValue(value.trim().toUpperCase())"
await api.handleChange(' a ')
assert.equal(props.modelValue, 'A')
assert.deepEqual(emitted, [['change', 'A']], '异步脚本赋值后再发 change')

props.field.eventOnInput = "setValue(value.toUpperCase())"
emitted.length = 0
await api.handleInput('b')
assert.equal(props.modelValue, 'B')
assert.deepEqual(emitted, [], 'input 不发送 change，避免提交时重复事件链')
props.field.eventOnFocus = "setValue('focus')"
await api.handleFocus()
props.field.eventOnBlur = "setValue('blur')"
await api.handleBlur()
assert.deepEqual(emitted, [['focus', 'focus'], ['blur', 'blur']])
props.field.eventOnSelect = "setValue('custom')"
await api.customEventListeners.value.select()
assert.equal(props.modelValue, 'custom')

props.field.eventOnChange = "await new Promise(r => setTimeout(r, value === 'old' ? 30 : 1)); setValue(value + '!')"
emitted.length = 0
const old = api.handleChange('old')
await api.handleChange('new')
await old
assert.equal(props.modelValue, 'new!')
assert.deepEqual(emitted, [['change', 'new!']], '旧异步结果及旧事件链均被丢弃')

const errors = []
const originalError = console.error
console.error = (...args) => errors.push(args)
try {
  for (const code of ["throw Error('failure')", "await Promise.reject(Error('failure'))", 'if (', 'throw null']) {
    props.field.eventOnChange = code
    await api.handleChange('continue')
    assert.deepEqual(emitted.at(-1), ['change', 'continue'])
  }
} finally { console.error = originalError }
assert.equal(errors.length, 4)
assert.match(errors[0][0], /文本.*onChange.*failure/)

props.field = { fieldCode: 'user', componentType: 'reference', eventOnChange: "setValue(value); field.fieldCode = 'changed'" }
props.modelValue = 'id1'
await nextTick()
await api.handleSelectionChange({ id: 'id1', name: '张三' })
assert.equal(props.modelValue, 'id1', '实体模型仍保存 ID')
assert.equal(props.field.fieldCode, 'user', 'field 是快照，不能直接改配置')
assert.deepEqual(emitted.at(-1), ['change', { id: 'id1', name: '张三' }])
props.modelValue = null
await api.handleSelectionChange(null)
assert.deepEqual(emitted.at(-1), ['change', null])
props.field.eventOnChange = "setValue('id2')"
props.modelValue = 'id1'
await api.handleSelectionChange({ id: 'id1', name: '旧用户' })
assert.deepEqual(emitted.at(-1), ['change', { id: 'id2' }], '脚本改选 ID 后不能回填旧记录')
props.field.eventOnChange = "setValue(null)"
await api.handleSelectionChange({ id: 'id2' })
assert.deepEqual(emitted.at(-1), ['change', null])
scope.stop()
console.log('field script tests passed: syntax, async, timeout, stale writes, errors, input/change, focus/blur, custom event, entity ID/selection')
