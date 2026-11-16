import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { ref, computed } from 'vue'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { extractSfcFunctions } from '../../../../../scripts/test-sfc-functions.mjs'

const file = new URL('../BoundDictDrawer.vue', import.meta.url)

/** 执行抽屉真实方法，接口队列用于验证晚到请求隔离与失败后重试。 */
function drawer({ manage = true, permission = true } = {}) {
  const definitions = [], trees = [], writes = [], events = [], errors = [], messages = []
  const queue = (requests, payload) => new Promise((resolve, reject) => requests.push({ payload, resolve, reject }))
  const props = { modelValue: true, dictCode: 'expense_type', manage }
  const userStore = { isSuperAdmin: false, permissions: permission ? ['system:dictionary:manage'] : [] }
  const state = extractSfcFunctions(file, [
    'editable', 'dict', 'items', 'loading', 'loadError', 'saving', 'itemEditorVisible', 'itemForm',
    'loadVersion', 'loadDict', 'openItemEditor', 'saveItem', 'flattenItems', 'closeDrawer'
  ], {
    props, userStore, ref, computed, visible: ref(true),
    emit: (...args) => events.push(args),
    getDictByCode: code => queue(definitions, code),
    getItemTreeByDictId: id => queue(trees, id),
    createDictItem: data => queue(writes, ['create', data]),
    updateDictItem: (id, data) => queue(writes, ['update', id, data]),
    showRequestError: (...args) => errors.push(args),
    ElMessage: { warning: message => messages.push(message), success: message => messages.push(message) }
  })
  return { ...state, props, userStore, definitions, trees, writes, events, errors, messages }
}

test('加载停用代码表及完整代码项，不依赖启用候选列表', async () => {
  const page = drawer()
  const loaded = page.loadDict()
  page.definitions[0].resolve({ id: 'dict-1', dictCode: 'expense_type', status: '1' })
  await Promise.resolve()
  page.trees[0].resolve([{ id: 'item-1', status: '1' }])
  await loaded
  assert.equal(page.dict.value.status, '1')
  assert.equal(page.items.value[0].status, '1')
  assert.equal(page.loading.value, false)
})

test('切换代码表时迟到响应不能覆盖新抽屉', async () => {
  const page = drawer()
  const first = page.loadDict()
  page.props.dictCode = 'new_type'
  const second = page.loadDict()
  page.definitions[1].resolve({ id: 'new', dictCode: 'new_type' })
  await Promise.resolve()
  page.trees[0].resolve([{ id: 'new-item' }])
  await second
  page.definitions[0].reject(new Error('旧请求失败'))
  await first
  assert.equal(page.dict.value.id, 'new')
  assert.equal(page.loadError.value, '')
})

test('代码表不存在显示异常，不创建或自动换绑', async () => {
  const page = drawer()
  const loaded = page.loadDict()
  page.definitions[0].resolve(null)
  await loaded
  assert.match(page.loadError.value, /不存在/)
  assert.equal(page.trees.length, 0)
  assert.equal(page.writes.length, 0)
})

test('查看模式及无权限用户均不能开启编辑或提交', async () => {
  for (const options of [{ manage: false }, { permission: false }]) {
    const page = drawer(options)
    page.dict.value = { id: 'dict-1' }
    page.openItemEditor()
    await page.saveItem()
    assert.equal(page.itemEditorVisible.value, false)
    assert.equal(page.writes.length, 0)
  }
})

test('维护名称排序和停用状态保留原标识、层级及扩展字段，保存失败保留草稿', async () => {
  const page = drawer()
  page.dict.value = { id: 'dict-1', dictCode: 'expense_type' }
  page.openItemEditor({ id: 'item-1', itemCode: ' 1 ', itemValue: ' LEGACY ', itemLabel: '交通费',
    parentId: 'parent', ext1: '扩展信息', sort: 10, status: '0', children: [] })
  page.itemForm.value.itemLabel = '差旅费'
  page.itemForm.value.status = '1'
  page.itemForm.value.sort = 20
  const first = page.saveItem()
  await page.saveItem()
  assert.equal(page.writes.length, 1, '提交期间禁止重复写入')
  const [, id, data] = page.writes[0].payload
  assert.equal(id, 'item-1')
  assert.equal(data.itemCode, ' 1 ')
  assert.equal(data.itemValue, ' LEGACY ')
  assert.equal(data.ext1, '扩展信息')
  assert.equal(data.parentId, 'parent')
  assert.equal(data.status, '1')
  assert.equal(data.sort, 20)
  page.writes[0].reject(new Error('暂时不可用'))
  await first
  assert.equal(page.itemEditorVisible.value, true)
  assert.equal(page.itemForm.value.itemLabel, '差旅费')
  assert.equal(page.errors.length, 1)
  assert.equal(page.saving.value, false)
})

test('新增代码项以编码补充存储值，成功后刷新且不改变字段绑定', async () => {
  const page = drawer()
  page.dict.value = { id: 'dict-1', dictCode: 'expense_type' }
  page.openItemEditor()
  Object.assign(page.itemForm.value, { itemCode: '2', itemLabel: '餐费' })
  const saved = page.saveItem()
  assert.equal(page.writes[0].payload[1].itemValue, '2')
  page.writes[0].resolve({ id: 'item-2' })
  await Promise.resolve()
  page.definitions[0].resolve({ id: 'dict-1', dictCode: 'expense_type' })
  await Promise.resolve()
  page.trees[0].resolve([{ id: 'item-2', itemCode: '2' }])
  await saved
  assert.equal(page.itemEditorVisible.value, false)
  assert.equal(page.items.value[0].id, 'item-2')
  assert.deepEqual(page.events, [['changed']])
  assert.equal(page.props.dictCode, 'expense_type')
})

test('禁止与树中停用子项重复的编码或存储值', async () => {
  const page = drawer()
  page.dict.value = { id: 'dict-1', dictCode: 'expense_type' }
  page.items.value = [{ id: 'parent', children: [{ id: 'old', itemCode: '2', itemValue: 'old_value', status: '1' }] }]
  for (const values of [{ itemCode: '2' }, { itemCode: 'old_value' }]) {
    page.openItemEditor()
    Object.assign(page.itemForm.value, { itemLabel: '重复项', ...values })
    await page.saveItem()
  }
  assert.equal(page.writes.length, 0)
  assert.equal(page.messages.length, 2)
})

test('新增和修改的 Vue 组件均能编译', () => {
  const files = [file, new URL('../QuickDictDialog.vue', import.meta.url),
    new URL('../../../../components/DictDefinitionPicker.vue', import.meta.url),
    new URL('../../../EntityDesign.vue', import.meta.url)]
  for (const target of files) {
    const { descriptor, errors } = parse(readFileSync(target, 'utf8'))
    assert.deepEqual(errors, [])
    compileScript(descriptor, { id: 'bound-dict-test' })
    assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: target.pathname, id: 'bound-dict-test' }).errors, [])
  }
})
