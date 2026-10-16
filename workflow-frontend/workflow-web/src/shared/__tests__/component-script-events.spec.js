import assert from 'node:assert/strict'
import { effectScope, reactive } from 'vue'
import { readComponentScriptEvents, snapshotScriptEventData } from '@flow/workflow-core/browser/field-script-metadata'
import { readFieldScripts, writeFieldScripts, normalizeFieldScript, runFieldScript, validateFieldScript } from '@flow/workflow-core/browser/field-event-scripts'
import { registerFieldEventHandler, getFieldEventHandlerOptions } from '@flow/workflow-core/extensions/core/registries/fieldEventHandlerRegistry'
import { useFormField } from '@flow/workflow-core/vue/useFormField'

const declarations = ['onClear','onInput','onChange','onFocus','onBlur','onRecognizeSuccess'].map(name => ({ name, label: name }))
assert.equal(readComponentScriptEvents({}), undefined)
assert.deepEqual(readComponentScriptEvents({ scriptEvents: [] }), [])
assert.throws(() => readComponentScriptEvents({ scriptEvents: [declarations[0], declarations[0]] }), /重复/)
assert.deepEqual(readComponentScriptEvents({ scriptEvents: [{ ...declarations[0], variants: ['input'] }] }, 'textarea'), [])
assert.throws(() => snapshotScriptEventData({ event: new Date() }), /JSON/)
assert.throws(() => snapshotScriptEventData({ key: 1 }, { parameters: [{ name: 'key', type: 'string' }] }), /string/)
assert.throws(() => normalizeFieldScript({ mode: 'function', code: '() => {}' }), /处理函数/)

let calls = 0
registerFieldEventHandler('test.trim', async ({ value, setValue }) => { calls++; await Promise.resolve(); setValue(value.trim()) }, { version: 1, supportedFieldTypes: ['STRING'] })
registerFieldEventHandler('test.trim', () => { throw Error('不得选择新版本') }, { version: 2 })
assert.throws(() => registerFieldEventHandler('test.trim', () => {}, { version: 1 }), /重复/)
const binding = { mode: 'function', handler: 'test.trim', version: 1 }
assert.equal(getFieldEventHandlerOptions({ fieldType: 'NUMBER' }, 'onChange').some(item => item.version === 1), false)
assert.throws(() => validateFieldScript({ ...binding, version: 3 }, {}, 'onChange'), /未安装/)
assert.throws(() => validateFieldScript(binding, { fieldType: 'INTEGER' }, 'onChange'), /不适用/)
const field = { fieldCode: 'name', fieldType: 'STRING', componentType: 'input', componentProps: { events: { onChange: 'old' } } }
writeFieldScripts(field, { onChange: binding, onInput: { mode: 'body', code: 'setValue(value)' } })
assert.deepEqual(readFieldScripts(JSON.parse(JSON.stringify(field))).onChange, binding)
writeFieldScripts(field, { onChange: { mode: 'body', code: '' } })
assert.deepEqual(readFieldScripts(field), {})
let result
await runFieldScript(binding, { field, event: { name: 'onChange' }, isCurrent: () => true, value: ' x ', setValue: value => { result = value } })
assert.equal(result, 'x'); assert.equal(calls, 1)

const scopes = []
function setup(events, deferredModel = false) {
  const scope = effectScope(); scopes.push(scope)
  const props = reactive({ field: { ...field, componentProps: { events } }, modelValue: 'before', disabled: false })
  const emitted = []
  const api = scope.run(() => useFormField(props, (name, value) => {
    emitted.push([name,value])
    if (name === 'update:modelValue' && !deferredModel) props.modelValue = value
  }, { eventAdapter: 'text', scriptEvents: declarations }))
  return { props, emitted, api }
}
const trace = []
globalThis.componentScriptTrace = trace
const clear = setup({
 onClear: { mode:'body',code:"await Promise.resolve(); componentScriptTrace.push(['clear', value]); setValue('reset')" },
 onInput: { mode:'body',code:"componentScriptTrace.push(['input', value]); setValue(value + '-input')" },
 onChange: { mode:'body',code:"componentScriptTrace.push(['change', value]); setValue(value + '-change')" },
 onBlur: { mode:'body',code:"componentScriptTrace.push(['blur', value])" }
}, true)
// 按真实 Element Plus 的清空顺序调用；父 props 延迟时仍使用本批次最新值。
clear.api.fieldValue.value = ''
const change = clear.api.handleChange('')
const clearPromise = clear.api.handleClear()
clear.api.handleInput('')
clear.api.handleBlur()
await Promise.all([change,clearPromise])
assert.deepEqual(trace, [['clear',''],['input','reset'],['change','reset-input'],['blur','reset-input-change']])
assert.deepEqual(clear.emitted.filter(([name]) => name === 'change'), [['change','reset-input-change']])
assert.equal(clear.api.fieldValue.value, 'reset-input-change')

const race = setup({ onInput: { mode: 'body', code: "await new Promise(r => setTimeout(r, value === 'old' ? 30 : 1)); setValue(value + '!')" } })
race.api.fieldValue.value = 'old'; const old = race.api.handleInput('old')
await Promise.resolve()
race.api.fieldValue.value = 'new'; await race.api.handleInput('new'); await old
assert.equal(race.props.modelValue, 'new!')
assert.equal(race.emitted.filter(([name]) => name === 'change').length, 0)

const custom = setup({ onRecognizeSuccess: { mode:'body', code: 'setValue(event.data.address)' } })
await custom.api.triggerScriptEvent('onRecognizeSuccess', { data: { address: '上海' } })
assert.equal(custom.props.modelValue, '上海')
assert.equal(custom.emitted.filter(([name]) => name === 'change').length, 0)
custom.props.disabled = true
await custom.api.triggerScriptEvent('onRecognizeSuccess', { data: { address: '禁用回填' } })
assert.equal(custom.props.modelValue, '上海')
custom.props.disabled = false; custom.props.field.isReadonly = 1
await custom.api.handleChange('只读回填')
assert.equal(custom.props.modelValue, '上海')
// 多值字段的父表单将 raw 数组包装为 reactive Proxy，仍属于同一模型回声，不能取消自身脚本。
const multiScope = effectScope(); scopes.push(multiScope)
const multiProps = reactive({ field: { fieldCode:'users', componentType:'reference', componentProps:{events:{onChange:{mode:'body',code:"setValue(['id2'])"}}} }, modelValue:['id1'] })
const multiEmitted = []
const multi = multiScope.run(() => useFormField(multiProps, (name,value) => {
  if (name === 'update:modelValue') multiProps.modelValue = value
  else multiEmitted.push([name,value])
}, {scriptEvents:declarations}))
await multi.handleSelectionChange([{id:'id1',name:'旧用户'}])
assert.deepEqual(multiEmitted,[['change',[{id:'id2'}]]])
for (const scope of scopes) scope.stop()
delete globalThis.componentScriptTrace
console.log('component script events passed: declarations, selected handlers, persistence, exact versions, clear batching, stale writes, custom events, readonly')
