import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as vue from 'vue'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { FIELD_SCRIPT_EVENTS } from '../../../../packages/workflow-core/src/shared/field-script-metadata.js'
import { resolveComponentFormPatch } from '../../../../packages/workflow-core/src/shared/form-runtime/formModel.js'

/** 执行真实 SFC setup；仅替换网络、注册表和 Vue 编译宏，校验协议与写入事件行为。 */
async function setupFile(file, bindings, exposed = '') {
  const source = await readFile(new URL(file, import.meta.url), 'utf8')
  const { descriptor, errors } = parse(source)
  assert.deepEqual(errors, [])
  compileScript(descriptor, { id: 'unified-test' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file, id: 'unified-test' }).errors, [])
  const script = descriptor.scriptSetup.content.replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*;?\n/gm, '')
  const values = { ...vue, FIELD_SCRIPT_EVENTS, ...bindings }
  return new Function(...Object.keys(values), `${script}\nreturn { ${exposed} }`)(...Object.values(values))
}

test('统一组件同时收到单字段值、当前行、节点和解码参数；只读和无绑定组件不能写字段', async () => {
  const props = vue.reactive({ field: { componentType: 'custom', componentProps: '{"precision":2}' }, modelValue: 3, formData: { amount: 3 }, node: { id: 'n' }, context: {}, disabled: false, readonly: false })
  const events = []
  const state = await setupFile('../FormFieldRenderer.vue', {
    formFieldProps: {}, formFieldEmits: [], defineProps: () => props,
    defineEmits: () => (...args) => events.push(args), defineExpose() {}, provideFieldScriptContext() {}, provide() {},
    resolveFieldComponentDescriptor: () => ({ component: { name: 'Custom' }, capabilities: {} }), safeParseConfig: value => JSON.parse(value)
  }, 'componentProps, updateValue, patchForm')
  assert.equal(state.componentProps.value.modelValue, 3)
  assert.equal(state.componentProps.value.formData, props.formData)
  assert.equal(state.componentProps.value.node, props.node)
  assert.deepEqual(state.componentProps.value.config, { precision: 2 })
  state.updateValue(4); state.patchForm({ amount: 5 })
  assert.deepEqual(events, [['update:modelValue', 4], ['patch-form', { amount: 5 }]])
  props.disabled = true; state.updateValue(8); state.patchForm({ amount: 9 })
  assert.equal(events.length, 2)
  props.disabled = false; props.field.isDisplayOnly = true; state.updateValue('invalid')
  assert.equal(events.length, 2)
})

test('跨字段 patch 合并完成后逐字段进入 change 和校验链，事件读取当前行而不是根表', async () => {
  const fields = [{ fieldCode: 'amount' }, { fieldCode: 'name' }, { fieldCode: 'locked', isReadonly: 1 }]
  const row = { amount: 1, name: 'old', locked: 10 }
  const props = vue.reactive({ field: fields[0], formData: row, context: { scriptFields: fields, form: {}, getFormData: () => ({ parent: true }) }, mode: 'edit', disabled: false, readonly: false })
  const events = [], requests = [], validations = []
  const state = await setupFile('../FormFieldRendererLinkage.vue', {
    FormFieldRenderer: {}, formFieldProps: {}, formFieldEmits: [], defineProps: () => props,
    defineEmits: () => (type, value) => { events.push([type, value]); if (type === 'patch-form') props.formData = { ...props.formData, ...value } },
    defineExpose() {}, provideFieldScriptContext() {}, resolveComponentFormPatch,
    CUSTOM_VALIDATION_CONTEXT_KEY: 'custom', FORM_UNIQUE_PRECHECK_CONTEXT_KEY: 'unique',
    inject: key => key === 'custom' ? { errorFor: () => '', checkField: field => validations.push(field.fieldCode) } : null,
    isEntitySelectionEventField: () => false, resolveFormUniqueValidationTrigger: () => 'change',
    buildFieldEventPayload: (field, value, context) => ({ fieldCode: field.fieldCode, value, record: { ...context.getFormData() } }),
    uiEventBindingApi: { execute: async (code, payload) => { requests.push([code, payload]); return {} } },
    applyRuntimeFieldEffects: async () => {}, showRequestError(error) { throw error }, ElMessage: {}, ElMessageBox: {}, getFormId() {}
  }, 'handleFormPatch')
  await state.handleFormPatch({ amount: 2, name: 'new', locked: 20, parent: false })
  assert.deepEqual(props.formData, { amount: 2, name: 'new', locked: 10 })
  assert.deepEqual(requests.map(([, payload]) => payload.fieldCode), ['amount', 'name'])
  assert.ok(requests.every(([, payload]) => payload.record.amount === 2 && payload.record.name === 'new' && !('parent' in payload.record)))
  assert.deepEqual(validations, ['amount', 'name'])
  props.readonly = true; await state.handleFormPatch({ amount: 3 })
  assert.equal(requests.length, 2)
})

for (const [platform, file, method] of [
  ['PC', '../../../../custom/biz-project/src/pc/fields/ProjectAcceptanceSummaryField.vue', 'executeNodeDataSource'],
  ['移动', '../../../../custom/biz-project/src/mobile/fields/ProjectAcceptanceSummaryField.vue', 'execute']
]) {
  test(`${platform} 摘要迁移后通过 patch 更新当前作用域，保留零值并丢弃离开原行后的旧结果`, async () => {
    const props = vue.reactive({ field: { fieldCode: 'extension_summary' }, node: null, formData: { name: '当前子表行', acceptance_score: 0 }, disabled: false, readonly: false, mode: 'edit', context: {}, config: {} })
    const events = [], calls = []
    let finish, validation
    props.dataSourceRuntime = { executeOwnerUsage: async (owner, usage, context) => {
      calls.push({ owner, usage, context })
      return [{ data: { value: 0 } }]
    } }
    const state = await setupFile(file, {
      formFieldProps: {}, formFieldEmits: [], defineProps: () => props,
      defineEmits: () => (...args) => events.push(args), defineExpose: value => { validation = value },
      onBeforeUnmount() {}, ElMessage: { success() {}, warning() {} }, useExtensionHost: () => ({ showRequestError(error) { throw error } }),
      console: { info() {}, error() {} }
    }, method)
    await state[method]()
    assert.equal(calls[0].owner, props.field, '无独立节点时使用当前字段定位数据源')
    assert.equal(calls[0].context.record, props.formData)
    assert.deepEqual(events, [['patch-form', { extension_summary: 0 }]])
    props.dataSourceRuntime.executeOwnerUsage = () => new Promise(resolve => { finish = resolve })
    const pending = state[method]()
    assert.equal(validation.validate(), false)
    props.formData = { name: '另一行', acceptance_score: 95 }
    finish([{ data: { value: '旧请求' } }]); await pending
    assert.equal(events.length, 1)
    assert.equal(validation.validate(), true)
  })
}

test('统一 change 链对实际变更字段做 BLUR 预检，使用事件回填后的数据且跳过非 BLUR 配置', async () => {
  const amount = { fieldCode: 'amount' }, other = { fieldCode: 'other' }
  const props = vue.reactive({ field: { fieldCode: 'component' }, formData: { amount: 2, other: 'value' }, context: {}, mode: 'edit' })
  const calls = []
  const state = await setupFile('../FormFieldRendererLinkage.vue', {
    FormFieldRenderer: {}, formFieldProps: {}, formFieldEmits: [], defineProps: () => props,
    defineEmits: () => () => {}, defineExpose() {}, provideFieldScriptContext() {}, resolveComponentFormPatch,
    CUSTOM_VALIDATION_CONTEXT_KEY: 'custom', FORM_UNIQUE_PRECHECK_CONTEXT_KEY: 'unique',
    inject: key => key === 'unique' ? {
      resolveRule: field => ({ precheck: { enabled: true, trigger: field.fieldCode === 'amount' ? 'BLUR' : 'CHANGE' } }),
      check: (field, trigger) => calls.push({ code: field.fieldCode, trigger, record: { ...props.formData } })
    } : null,
    isEntitySelectionEventField: () => false, resolveFormUniqueValidationTrigger: () => 'change',
    buildFieldEventPayload: () => ({}), uiEventBindingApi: { execute: async () => ({}) },
    applyRuntimeFieldEffects: async (_result, handlers) => handlers.setField('amount', 9),
    showRequestError(error) { throw error }, ElMessage: {}, ElMessageBox: {}
  }, 'handleRuntimeChange')
  await state.handleRuntimeChange(2, amount)
  await state.handleRuntimeChange('value', other)
  assert.deepEqual(calls, [{ code: 'amount', trigger: 'BLUR', record: { amount: 9, other: 'value' } }])
})
