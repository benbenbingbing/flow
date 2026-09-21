import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { ref } from 'vue'

import {
  normalizeRecordSelection,
  refreshRecordPageSelection,
  reconcileRecordPageSelection,
  recordSelectionIds,
  recordSelectionValues,
  removeRecordSelection
} from '@flow/workflow-core/entity-record-selection'

const projectA = { id: 'project-a', name: '项目 A' }
const projectB = { id: 'project-b', name: '项目 B' }
const projectC = { id: 'project-c', name: '项目 C' }

assert.deepEqual(
  recordSelectionIds(normalizeRecordSelection([
    projectA,
    projectA,
    projectB
  ])),
  ['project-a', 'project-b'],
  '同一条业务数据只能进入一次多选结果'
)

const afterSecondPage = reconcileRecordPageSelection(
  [projectA, projectB],
  [projectC],
  [projectC]
)
assert.deepEqual(
  recordSelectionIds(afterSecondPage),
  ['project-a', 'project-b', 'project-c'],
  '翻页选择时必须保留前页记录'
)

const afterReturningFirstPage = reconcileRecordPageSelection(
  afterSecondPage,
  [projectA, projectB],
  [projectB]
)
assert.deepEqual(
  recordSelectionIds(afterReturningFirstPage),
  ['project-b', 'project-c'],
  '返回前页取消记录时不能影响其他页选择'
)

const staleProjectB = {
  ...projectB,
  actionCapabilities: { batchDelete: { visible: true, enabled: true } }
}
const freshProjectB = {
  ...projectB,
  actionCapabilities: { batchDelete: { visible: true, enabled: false } }
}
const refreshedSelection = reconcileRecordPageSelection(
  [staleProjectB, projectC],
  [freshProjectB],
  [freshProjectB]
)
assert.equal(
  refreshedSelection[0],
  freshProjectB,
  '返回当前页后必须用最新记录替换同 ID 的旧选择对象'
)
assert.equal(
  refreshedSelection[0].actionCapabilities.batchDelete.enabled,
  false,
  '选择集按钮必须读取本次查询返回的最新能力'
)

const restoredSelection = refreshRecordPageSelection(
  [staleProjectB, projectC],
  [freshProjectB]
)
assert.equal(
  restoredSelection[0],
  freshProjectB,
  '表格屏蔽 selection-change 自动恢复勾选时也必须主动刷新当前页对象'
)
assert.equal(
  restoredSelection[1],
  projectC,
  '自动恢复当前页选择时必须保留其他页对象'
)

assert.deepEqual(
  recordSelectionIds(removeRecordSelection(afterReturningFirstPage, 'project-c')),
  ['project-b'],
  '已选区可以移除任意页面的记录'
)

assert.deepEqual(
  recordSelectionValues([
    { id: '1', code: 'admin', name: '管理员' },
    { id: '2', code: 'reviewer', name: '审批人' }
  ], 'code'),
  ['admin', 'reviewer'],
  '系统用户选择可以保持原有 username 编码值'
)

// 直接执行选择器方法并替换请求，验证旧配置不能改变查询入口，分页和回显仍正常。
const selectorSource = readFileSync(new URL('../../components/EntitySelector.vue', import.meta.url), 'utf8')
function selectorMethod(name, dependencies) {
  const start = selectorSource.indexOf(`async function ${name}(`)
  const end = selectorSource.indexOf('\n}\n', start) + 2
  assert.ok(start >= 0 && end > start, `未找到选择器方法 ${name}`)
  return new Function(...Object.keys(dependencies), `${selectorSource.slice(start, end)}\nreturn ${name}`)(...Object.values(dependencies))
}
for (const entityType of ['CUSTOM', 'USER', 'DEPT', 'ROLE', 'GROUP']) {
  const calls = []
  const tableData = ref([])
  const loading = ref(false)
  const total = ref(0)
  let restored = 0
  const dependencies = {
    props: { entityType, apiUrl: 'https://legacy.example.invalid/query' },
    effectiveEntityCode: ref('project'), effectiveRefEntityId: ref(''),
    loading, tableData, total, pageNum: ref(2), pageSize: ref(20), searchKeyword: ref('项目 & A'),
    request: { get: async url => { calls.push(url); return { records: [projectA], total: 35 } } },
    ElMessage: { warning: assert.fail, error: assert.fail },
    restoreCurrentPageSelection: async () => { restored += 1 }
  }
  await selectorMethod('loadData', dependencies)()
  assert.equal(calls.length, 1)
  const query = new URL(calls[0], 'http://localhost')
  assert.equal(query.pathname, `/entity-selector/${entityType}`)
  assert.equal(query.searchParams.get('pageNum'), '2')
  assert.equal(query.searchParams.get('pageSize'), '20')
  assert.equal(query.searchParams.get('keyword'), '项目 & A')
  assert.equal(query.searchParams.get('entityCode'), entityType === 'CUSTOM' ? 'project' : null)
  assert.deepEqual(tableData.value, [projectA])
  assert.equal(total.value, 35)
  assert.equal(loading.value, false)
  assert.equal(restored, 1)
  if (entityType === 'CUSTOM') {
    dependencies.effectiveEntityCode.value = ''
    dependencies.effectiveRefEntityId.value = 'entity-123'
    await selectorMethod('loadData', dependencies)()
    assert.equal(new URL(calls[1], 'http://localhost').searchParams.get('refEntityId'), 'entity-123')
    dependencies.effectiveRefEntityId.value = ''
    let warned = false
    dependencies.ElMessage.warning = () => { warned = true }
    await selectorMethod('loadData', dependencies)()
    assert.equal(warned, true)
    assert.equal(calls.length, 2, '缺少目标实体时不能发起无范围查询')
  }
}

// 已发布选择列表仍由列表运行时加载；打开时保留已选值，不再额外查询默认列表。
let selectedLoads = 0
const dialogVisible = ref(false)
const selectedRows = ref([])
await selectorMethod('openSelector', {
  props: { disabled: false }, dialogVisible, selectedRows,
  selectedList: ref([projectA]), normalizeRecordSelection,
  useUnifiedList: ref(true), loadSelectedData: async () => { selectedLoads += 1 },
  loadData: assert.fail, pageNum: ref(2), searchKeyword: ref('')
})()
assert.equal(dialogVisible.value, true)
assert.equal(selectedLoads, 1)
assert.deepEqual(selectedRows.value, [projectA])

console.log('entity record selection tests passed')
