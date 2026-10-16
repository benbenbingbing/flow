import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
import { getMobileFieldExtension, mobileFieldCapability, mobileFieldType, registerMobileExtension } from '../../packages/workflow-mobile-ui/src/fields/registry.js'
import { runtimeFieldState, validateRuntimeFields } from '@flow/workflow-core/form-runtime/formModel'

/** 执行真实 SFC setup 和暴露的 validate；视觉组件/事件环境替身不改变能力查询和校验分支。 */
function setup(filename, props, bindings = {}, returned = '') {
  const { descriptor } = parse(readFileSync(new URL(`../../packages/workflow-mobile-ui/src/${filename}`, import.meta.url), 'utf8'))
  compileScript(descriptor, { id: 'missing-field-capability' })
  const script = descriptor.scriptSetup.content.replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*;?\n/gm, '')
  let exposed
  const values = {
    ...vue, defineProps: () => props, defineOptions() {}, defineEmits: () => () => {}, defineExpose: value => { exposed = value },
    formFieldProps: {}, getMobileFieldExtension, mobileFieldCapability, mobileFieldType, runtimeFieldState, validateRuntimeFields,
    getCurrentInstance: () => ({ type: {} }), provide() {}, onBeforeUnmount() {}, FIELD_SCRIPT_EVENTS: Symbol('field-events'),
    useFormField: () => ({ fieldValue: vue.ref(props.modelValue), parsedComponentProps: vue.ref({}), staticOptions: vue.ref([]) }),
    ...bindings
  }
  delete values.customRef
  const state = new Function(...Object.keys(values), `${script}\nreturn { ${returned} }`)(...Object.values(values))
  return { state, exposed }
}

test('移动字段独立使用时，缺失扩展和缺少校验能力都阻止编辑提交', async () => {
  const props = vue.reactive({ field: { fieldCode: 'score', componentType: 'not-installed', fieldType: 'INTEGER' }, modelValue: 80, context: {}, services: {}, disabled: false })
  const scope = vue.effectScope()
  const { exposed, state } = scope.run(() => setup('fields/MobileFieldRenderer.vue', props, {}, 'customRef'))
  try {
    assert.equal(await exposed.validate(), false)
    props.field.componentType = 'number'
    assert.equal(await exposed.validate(), true, '内置组件不要求自定义实例')
    registerMobileExtension({ name: 'missing-capability-test', component: {}, editable: true, validate: false })
    props.field.componentType = 'missing-capability-test'
    assert.equal(await exposed.validate(), false)
    registerMobileExtension({ name: 'supported-capability-test', component: {}, editable: true, validate: true })
    props.field.componentType = 'supported-capability-test'
    state.customRef.value = { validate: async () => false }
    assert.equal(await exposed.validate(), false, '已安装组件的内部失败不能被覆盖')
    state.customRef.value = { validate: async () => true }
    assert.equal(await exposed.validate(), true)
    state.customRef.value = null
    props.field.componentType = 'not-installed'; props.disabled = true
    assert.equal(await exposed.validate(), true, '只读字段仍显示缺失提示，但不参与编辑校验')
  } finally { scope.stop() }
})

test('自定义整表单补充字段将缺失组件失败向上返回并定位字段', async () => {
  const field = { fieldCode: 'score', componentType: 'not-installed-helper', fieldType: 'INTEGER' }
  const props = vue.reactive({ field, modelValue: 80, context: {}, services: {}, disabled: false })
  const scope = vue.effectScope(), revealed = []
  const renderer = scope.run(() => setup('fields/MobileFieldRenderer.vue', props))
  const helper = scope.run(() => setup('form/MobileExtensionFormFields.vue', {
    fields: [field], modelValue: { score: 80 }, mode: 'edit', form: {}, linkageState: {}, externalErrors: {},
    context: { revealField: code => revealed.push(code) }
  }, {}, 'setRef, errors'))
  try {
    helper.state.setRef('score', renderer.exposed)
    assert.equal(await helper.exposed.validate(), false)
    assert.ok(helper.state.errors.value.score)
    assert.deepEqual(revealed, ['score'])
  } finally { scope.stop() }
})
