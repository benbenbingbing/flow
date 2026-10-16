import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as vue from 'vue'
import { useCascaderFieldRuntime } from '../../packages/workflow-core/src/composables/useCascaderFieldRuntime.js'

const source = await readFile(new URL('../../packages/workflow-mobile-ui/src/fields/MobileCascaderField.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
const flush = async () => { await vue.nextTick(); await new Promise(resolve => setImmediate(resolve)); await vue.nextTick() }
const lazyOptions = {
  '[]': [{ value: 'a', label: '甲省', leaf: false }, { value: 'b', label: '乙省', leaf: false }],
  '["a"]': [{ value: 'city', label: '甲市', leaf: true }],
  '["b"]': [{ value: 'city', label: '乙市', leaf: true }]
}

/** 执行实际 SFC setup 和共享 runtime；自定义渲染器只替换 DOM，选择/请求/回显逻辑均保持真实。 */
function mountField({ value = [], disabled = false, sourceType = 'INTERFACE', loadMode = 'CHILDREN', options = [], loadOptions } = {}) {
  const requests = [], emitted = []
  const props = vue.reactive({
    field: {
      id: 'address', fieldCode: 'address', fieldLabel: '地区', componentType: 'cascader',
      componentProps: { cascaderSource: sourceType, cascaderLoadMode: loadMode, cascaderOptions: options },
      dataSourceBindings: { FIELD_OPTIONS: { extensionId: 'address-options' } }
    },
    modelValue: value, disabled, context: { form: { id: 'form', configHash: 'hash' }, record: { id: 'record' } },
    dataSourceRuntime: { loadOptions: async (_field, context) => {
      requests.push(context.input.cascader)
      return loadOptions ? loadOptions(context.input.cascader) : lazyOptions[JSON.stringify(context.input.cascader.path)] || []
    } }
  })
  let state, exposed
  const values = {
    ...vue, useCascaderFieldRuntime, formFieldProps: {}, resolveMobileLabelPosition: () => 'left',
    defineProps: () => props,
    defineEmits: () => (event, value) => { emitted.push([event, value]); if (event === 'update:modelValue') props.modelValue = value },
    defineExpose: api => { exposed = api }
  }
  const setup = new Function(...Object.keys(values), `${script}\nreturn { displayLabel, valuePath, pickerOptions, pickerOpen, draftPath, draftKey, loading, error, selectionError, openPicker, closePicker, selectBranch, finishSelection, clearSelection, retryCurrent }`)
  const node = () => ({ children: [] })
  const renderer = vue.createRenderer({
    createElement: node, createText: node, createComment: node,
    setText() {}, setElementText() {}, patchProp() {}, parentNode: n => n.parent,
    nextSibling: () => null, remove() {}, insert(n, parent) { n.parent = parent; parent.children.push(n) }
  })
  const app = renderer.createApp({ setup() { state = setup(...Object.values(values)); return () => vue.h('div') } })
  app.mount(node())
  return { state, props, requests, emitted, validate: () => exposed.validate(), unmount: () => app.unmount() }
}
const selection = option => ({ selectedOptions: [option] })

test('移动只读级联按已保存路径逐级加载标签，不触发字段修改', async t => {
  const field = mountField({ value: ['b', 'city'], disabled: true }); t.after(field.unmount)
  await flush()
  assert.equal(field.state.displayLabel.value, '乙省 / 乙市')
  assert.deepEqual(field.requests.map(request => request.path), [[], ['b']])
  assert.deepEqual(field.requests[1], { loadMode: 'CHILDREN', parentValue: 'b', path: ['b'], level: 1 })
  await field.state.openPicker()
  assert.equal(field.state.pickerOpen.value, false)
  field.state.clearSelection()
  assert.deepEqual(field.props.modelValue, ['b', 'city'])
  assert.deepEqual(field.emitted, [])
})

test('移动静态树支持相同末级编码不同路径，并仅提交完整路径', async t => {
  const field = mountField({ sourceType: 'STATIC', value: ['b', 1], options: [
    { value: 'a', label: '甲', children: [{ value: 1, label: '甲一' }] },
    { value: 'b', label: '乙', children: [{ value: 1, label: '乙一' }] }
  ] }); t.after(field.unmount)
  await flush()
  assert.equal(field.state.displayLabel.value, '乙 / 乙一')
  await field.state.openPicker()
  assert.equal(field.state.draftKey.value, '["b",1]')
  const leaf = field.state.pickerOptions.value[0].children[0]
  assert.notEqual(leaf.value, field.state.pickerOptions.value[1].children[0].value)
  await field.state.selectBranch(selection(leaf)); field.state.finishSelection(selection(leaf))
  assert.deepEqual(field.props.modelValue, ['a', 1])
  assert.equal(field.state.pickerOpen.value, false)
  assert.equal(field.emitted.filter(([name]) => name === 'update:modelValue').length, 1)
  assert.equal(field.requests.length, 0)
})

test('移动全量接口仅加载一次，空父分支或禁用节点不会保存', async t => {
  const field = mountField({ loadMode: 'ALL', loadOptions: async () => [{ value: 'p', label: '父', children: [{ value: 'x', label: '禁用', disabled: true }, { value: 'y', label: '可用' }] }] }); t.after(field.unmount)
  await flush(); await field.state.openPicker()
  const parent = field.state.pickerOptions.value[0]
  await field.state.selectBranch(selection(parent)); field.state.finishSelection(selection(parent))
  assert.deepEqual(field.props.modelValue, [])
  await field.state.selectBranch(selection(parent.children[0])); field.state.finishSelection(selection(parent.children[0]))
  assert.deepEqual(field.props.modelValue, [])
  const leaf = parent.children[1]
  await field.state.selectBranch(selection(leaf)); field.state.finishSelection(selection(leaf))
  assert.deepEqual(field.props.modelValue, ['p', 'y'])
  assert.equal(field.requests.length, 1)
  assert.equal(field.requests[0].loadMode, 'ALL')
})

test('移动懒加载失败保留原值和父分支，重试后才能完成新选择', async t => {
  let fail = true
  const field = mountField({ value: ['b', 'city'], loadOptions: async ({ path }) => {
    if (path[0] === 'a' && fail) throw new Error('暂时离线')
    return lazyOptions[JSON.stringify(path)]
  } }); t.after(field.unmount)
  await flush(); await field.state.openPicker()
  let parent = field.state.pickerOptions.value[0]
  assert.deepEqual(parent.children, [])
  await field.state.selectBranch(selection(parent)); field.state.finishSelection(selection(parent))
  assert.match(field.state.error.value, /离线/)
  assert.deepEqual(field.props.modelValue, ['b', 'city'])
  assert.deepEqual(field.state.draftPath.value, ['a'])
  fail = false; await field.state.retryCurrent()
  parent = field.state.pickerOptions.value[0]
  const leaf = parent.children[0]
  await field.state.selectBranch(selection(leaf)); field.state.finishSelection(selection(leaf))
  assert.deepEqual(field.props.modelValue, ['a', 'city'])
  assert.equal(field.emitted.filter(([name]) => name === 'update:modelValue').length, 1)
})

test('移动非末级接口空响应不能伪装成可选叶子，取消和清空行为分离', async t => {
  const field = mountField({ value: ['b', 'city'], loadOptions: async ({ path }) => path[0] === 'a' ? [] : lazyOptions[JSON.stringify(path)] }); t.after(field.unmount)
  await flush(); await field.state.openPicker()
  const parent = field.state.pickerOptions.value[0]
  await field.state.selectBranch(selection(parent)); field.state.finishSelection(selection(parent))
  assert.match(field.state.error.value, /非末级/)
  assert.deepEqual(field.props.modelValue, ['b', 'city'])
  field.state.closePicker(); assert.deepEqual(field.props.modelValue, ['b', 'city'])
  field.state.clearSelection(); assert.deepEqual(field.props.modelValue, [])
})

test('移动切换父分支和取消时，旧请求不覆盖选择草稿或提交字段', async t => {
  let resolveA, resolveB
  const field = mountField({ loadOptions: async ({ path }) => {
    if (!path.length) return lazyOptions['[]']
    return new Promise(resolve => { if (path[0] === 'a') resolveA = resolve; else resolveB = resolve })
  } }); t.after(field.unmount)
  await flush(); await field.state.openPicker()
  const a = field.state.pickerOptions.value[0], b = field.state.pickerOptions.value[1]
  const pendingA = field.state.selectBranch(selection(a)); await flush()
  const pendingB = field.state.selectBranch(selection(b)); await flush()
  assert.equal(field.state.loading.value, true)
  field.state.finishSelection(selection(a)); assert.deepEqual(field.props.modelValue, [])
  resolveB(lazyOptions['["b"]']); await pendingB
  resolveA(lazyOptions['["a"]']); await pendingA
  assert.deepEqual(field.state.draftPath.value, ['b'])
  field.state.closePicker()
  const leafA = field.state.pickerOptions.value[0].children[0]
  field.state.finishSelection(selection(leafA))
  assert.deepEqual(field.props.modelValue, [])
  assert.equal(field.emitted.filter(([name]) => name === 'update:modelValue').length, 0)
})

test('移动从失败分支切到已知叶子后可以正常确认，不被旧错误锁住', async t => {
  const field = mountField({ loadOptions: async ({ path }) => {
    if (!path.length) return [{ value: 'broken', label: '分支', leaf: false }, { value: 'leaf', label: '直接末级', leaf: true }]
    throw new Error('分支离线')
  } }); t.after(field.unmount)
  await flush(); await field.state.openPicker()
  await field.state.selectBranch(selection(field.state.pickerOptions.value[0]))
  assert.match(field.state.error.value, /离线/)
  const leaf = field.state.pickerOptions.value[1]
  await field.state.selectBranch(selection(leaf)); field.state.finishSelection(selection(leaf))
  assert.deepEqual(field.props.modelValue, ['leaf'])
  assert.equal(field.state.error.value, '')
})

test('移动加载期间依赖变化关闭旧弹层，迟到数据不混入新的选项', async t => {
  let resolveOld
  const field = mountField({ loadOptions: async ({ path }) => {
    if (!path.length) return lazyOptions['[]']
    return new Promise(resolve => { resolveOld = resolve })
  } }); t.after(field.unmount)
  await flush(); await field.state.openPicker()
  const request = field.state.selectBranch(selection(field.state.pickerOptions.value[0])); await flush()
  field.props.field.componentProps = { cascaderSource: 'STATIC', cascaderOptions: [{ value: 'new', label: '新选项' }] }
  await flush()
  assert.equal(field.state.pickerOpen.value, false)
  resolveOld(lazyOptions['["a"]']); await request
  assert.deepEqual(field.state.pickerOptions.value.map(option => option.path), [['new']])
  assert.deepEqual(field.props.modelValue, [])
})

test('移动弹层重试会刷新缺失路径的缓存，接口补齐后能恢复标签', async t => {
  let fixed = false
  const field = mountField({ value: ['later'], loadOptions: async () => fixed ? [{ value: 'later', label: '已恢复', leaf: true }] : [] }); t.after(field.unmount)
  await flush(); await field.state.openPicker()
  assert.match(field.state.error.value, /不可用/)
  fixed = true; await field.state.retryCurrent(); await flush()
  assert.equal(field.state.displayLabel.value, '已恢复')
  assert.deepEqual(field.props.modelValue, ['later'])
  assert.equal(field.state.error.value, '')
})
