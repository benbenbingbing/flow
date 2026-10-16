import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as vue from 'vue'
import { useCascaderFieldRuntime } from '../../../../packages/workflow-core/src/composables/useCascaderFieldRuntime.js'
import { useFormField } from '../../../../packages/workflow-core/src/extensions/builtin/fields/composables/useFormField.js'
import { cascaderPath } from '../../../../packages/workflow-core/src/shared/cascader-runtime.js'

const source = await readFile(new URL('../../extensions/builtin/fields/components/CascaderField.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
const flush = async () => { await vue.nextTick(); await new Promise(resolve => setImmediate(resolve)); await vue.nextTick() }
const tree = [{ value: 'a', label: '甲', children: [{ value: 1, label: '甲一' }] }]

/** 测试实际组件脚本及共享composable，仅替换DOM渲染，保留请求/事件/异步回显行为。 */
function mount({ value = [], disabled = false, sourceType = 'INTERFACE', mode = 'CHILDREN', loader } = {}) {
  const record = vue.reactive({ id: 'record', region: value, tenant: 'A' })
  const requests = [], events = []
  const props = vue.reactive({
    field: { id: 'region', fieldCode: 'region', componentType: 'cascader',
      componentProps: { cascaderSource: sourceType, cascaderLoadMode: mode, cascaderOptions: tree },
      dataSourceBindings: { FIELD_OPTIONS: { extensionId: 'region-api', inputMapping: { custom: 'context.businessKey' } } } },
    modelValue: value, disabled, options: null,
    context: { form: { id: 'form', configHash: 'hash' }, recordId: 'record', getFormData: () => record, businessKey: 'custom-A' },
    dataSourceRuntime: { loadOptions: async (_field, context) => {
      requests.push(context.input.cascader)
      return loader ? loader(context.input.cascader) : context.input.cascader.level === 0
        ? [{ value: 'a', label: '甲', leaf: false }] : [{ value: 1, label: '甲一', leaf: true }]
    } }
  })
  const values = { ...vue, useCascaderFieldRuntime, useFormField, cascaderPath, formFieldProps: {},
    defineProps: () => props,
    defineEmits: () => (event, value) => { events.push([event, value]); if (event === 'update:modelValue') { props.modelValue = value; record.region = value } } }
  const setup = new Function(...Object.keys(values), `${script}\nreturn { options, loading, error, revision, displayOptions, selectedPath, savedValueText, savedValueUnavailable, expandBranch, selectPath, retryLoad }`)
  const node = () => ({ children: [] })
  const renderer = vue.createRenderer({ createElement: node, createText: node, createComment: node,
    setText() {}, setElementText() {}, patchProp() {}, parentNode: n => n.parent, nextSibling: () => null,
    remove() {}, insert(n, parent) { n.parent = parent; parent.children.push(n) } })
  let state
  const app = renderer.createApp({ setup() { state = setup(...Object.values(values)); return () => vue.h('div') } })
  app.mount(node())
  return { props, record, requests, events, state, unmount: () => app.unmount() }
}

test('PC readonly saved path loads all labels without emitting changes', async t => {
  const field = mount({ value: ['a', 1], disabled: true }); t.after(field.unmount)
  await flush()
  assert.equal(field.state.savedValueText.value, '甲 / 甲一')
  assert.equal(field.state.savedValueUnavailable.value, false)
  assert.deepEqual(field.requests.map(item => item.path), [[], ['a']])
  field.state.selectPath([])
  assert.deepEqual(field.props.modelValue, ['a', 1])
  assert.deepEqual(field.events, [])
})

test('PC lazy nodes have a disabled placeholder and only leaves emit a complete path once', async t => {
  const field = mount(); t.after(field.unmount)
  await flush()
  assert.equal(field.state.displayOptions.value[0].children[0].disabled, true)
  field.state.selectPath(['a'])
  assert.deepEqual(field.events, [])
  await field.state.expandBranch(['a'])
  field.state.selectPath(['a', 1]); await flush()
  assert.deepEqual(field.props.modelValue, ['a', 1])
  assert.equal(field.events.filter(([type]) => type === 'update:modelValue').length, 1)
  assert.equal(field.events.filter(([type]) => type === 'change').length, 1)
  assert.equal(field.requests.length, 2)
})

test('PC failure and retry preserve saved values and restore labels', async t => {
  let fail = true
  const field = mount({ value: ['a', 1], loader: ({ level }) => {
    if (level === 0) return [{ value: 'a', label: '甲', leaf: false }]
    if (fail) throw new Error('暂时不可用')
    return [{ value: 1, label: '甲一', leaf: true }]
  } }); t.after(field.unmount)
  await flush()
  assert.equal(field.state.error.value, '暂时不可用')
  assert.deepEqual(field.props.modelValue, ['a', 1])
  assert.deepEqual(field.events, [])
  fail = false
  await field.state.retryLoad(); await flush()
  assert.equal(field.state.savedValueText.value, '甲 / 甲一')
  assert.equal(field.state.error.value, '')
})

test('PC configuration, record, mapped context and dependency changes invalidate cache; own selection does not', async t => {
  const field = mount(); t.after(field.unmount)
  await flush()
  const before = field.state.revision.value
  await field.state.expandBranch(['a']); field.state.selectPath(['a', 1]); await flush()
  assert.equal(field.state.revision.value, before)
  const triggers = [() => { field.record.tenant = 'B' }, () => { field.props.context.businessKey = 'custom-B' },
    () => { field.props.context.recordId = 'other' }, () => { field.props.context.form.configHash = 'next' },
    () => { field.props.context.taskId = 'task-2' }]
  for (const update of triggers) {
    const revision = field.state.revision.value, requests = field.requests.length
    update(); await flush()
    assert.equal(field.state.revision.value, revision + 1)
    assert.equal(field.requests.length, requests + 2)
  }
})

test('PC missing saved path remains visible and static configuration never calls the interface', async t => {
  const field = mount({ sourceType: 'STATIC', value: ['a', 'old'] }); t.after(field.unmount)
  await flush()
  assert.equal(field.state.savedValueUnavailable.value, true)
  assert.match(field.state.error.value, /已保存/)
  assert.equal(field.state.savedValueText.value, '甲 / old')
  assert.equal(field.requests.length, 0)
  assert.deepEqual(field.props.modelValue, ['a', 'old'])
  field.state.selectPath([]); await flush()
  assert.deepEqual(field.props.modelValue, [])
})
