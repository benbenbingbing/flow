import test from 'node:test'
import assert from 'node:assert/strict'
import { extractSfcFunctions } from '../../../../../scripts/test-sfc-functions.mjs'
import { resolveListColumnSort } from '../../../../shared/list-column-sort.js'

const ref = value => ({ value })
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

/** 执行页面真实查询和表头事件方法，用可控响应验证分页语义和并发时序。 */
function createPage() {
  const calls = []
  const scope = {
    ref, resolveListColumnSort, entityCode: ref('requirement'), runtimeListKey: ref('main'),
    tableLoading: ref(false), dataError: ref(''), missingCustomList: ref(false), missingCustomListMessage: ref(''), dataList: ref([]), total: ref(0),
    pageNum: ref(3), pageSize: ref(10), sortState: ref(null),
    listFields: ref([{ fieldCode: 'amount', fieldId: 'real', columnConfig: '{"sortable":true}' }]),
    listConfig: ref({ viewConfig: {}, configHash: 'hash' }), runtimeScene: ref('PAGE'), props: { context: {} },
    relatedContentRuntimeContext: ref({}), resolvedInputParameters: ref({}),
    resolvePageParameters: () => ({}), buildRequestFilters: () => ({ name: '测试' }),
    loadRefEntityNames: async () => {}, selectionScene: ref(false), selectedRows: ref([]),
    loading: ref(false), loadError: ref(''), resetVersionCapabilities: () => 0,
    entityDefinition: ref({ id: 'old' }), entityFields: ref([]), listConfigFields: ref([]),
    refEntityNameMap: ref({}), dictOptionMap: ref({}), loadedDefaultForm: ref(null), clearQueryForm: () => {},
    entityApi: { getByCode: async () => { throw new Error('新列表尚未发布') } },
    entityListRuntimeApi: { query: (entity, key, payload) => {
      const pending = deferred()
      calls.push({ entity, key, payload, ...pending })
      return pending.promise
    } },
    console: { error: () => {} }
  }
  const methods = extractSfcFunctions(new URL('../../EntityDataList.vue', import.meta.url), [
    'dataRequestSequence', 'loadDataList', 'handleSortChange', 'handleSearch', 'handlePageChange', 'handleSizeChange',
    'prepareEntityDefinition'
  ], scope)
  return { ...scope, ...methods, calls }
}

test('表头排序重置到第一页，筛选和翻页保留排序，取消后不传排序参数', async () => {
  const page = createPage()
  page.handleSortChange({ prop: 'data.amount', order: 'ascending' })
  assert.equal(page.pageNum.value, 1)
  assert.equal(page.calls[0].payload.sortField, 'amount')
  assert.equal(page.calls[0].payload.sortDirection, 'asc')
  assert.deepEqual(page.calls[0].payload.filters, { name: '测试' })
  page.handlePageChange(2)
  assert.equal(page.calls.at(-1).payload.pageNum, 2)
  assert.equal(page.calls.at(-1).payload.sortDirection, 'asc')
  page.handleSearch()
  assert.equal(page.calls.at(-1).payload.pageNum, 1)
  assert.equal(page.calls.at(-1).payload.sortDirection, 'asc')
  page.handleSizeChange(20)
  assert.equal(page.calls.at(-1).payload.pageSize, 20)
  assert.equal(page.calls.at(-1).payload.sortField, 'amount')
  page.handleSortChange({ prop: 'data.amount', order: 'descending' })
  assert.equal(page.calls.at(-1).payload.sortDirection, 'desc')
  const previousCount = page.calls.length
  page.handleSortChange({ prop: 'data.amount', order: 'descending' })
  assert.equal(page.calls.length, previousCount, '恢复表头状态的重复事件不能重复查询')
  page.handleSortChange({ prop: 'data.amount', order: null })
  assert.equal(page.sortState.value, null)
  assert.equal(page.calls.at(-1).payload.pageNum, 1)
  assert.equal('sortField' in page.calls.at(-1).payload, false)
  assert.equal('sortDirection' in page.calls.at(-1).payload, false)
  for (const call of page.calls) call.resolve({ records: [], total: 0 })
  await Promise.all(page.calls.map(call => call.promise))
})

test('旧的排序响应和错误都不能覆盖新排序结果或结束其加载状态', async () => {
  const page = createPage()
  const old = page.loadDataList()
  const latest = page.loadDataList()
  page.calls[0].resolve({ records: [{ id: 'old' }], pageNum: 3, total: 100 })
  await old
  assert.deepEqual(page.dataList.value, [])
  assert.equal(page.tableLoading.value, true)
  page.calls[1].resolve({ records: [{ id: 'latest' }], pageNum: 1, total: 1 })
  await latest
  assert.deepEqual(page.dataList.value, [{ id: 'latest' }])
  assert.equal(page.pageNum.value, 1)
  assert.equal(page.tableLoading.value, false)

  const staleError = page.loadDataList()
  const next = page.loadDataList()
  page.calls[3].resolve({ records: [{ id: 'newest' }], total: 1 })
  await next
  page.calls[2].reject(new Error('旧请求失败'))
  await staleError
  assert.deepEqual(page.dataList.value, [{ id: 'newest' }])
  assert.equal(page.dataError.value, '')
})

test('重新发布后关闭当前列排序，下一次请求自动恢复默认顺序', async () => {
  const page = createPage()
  page.sortState.value = { sortField: 'amount', sortDirection: 'asc' }
  page.listFields.value[0].columnConfig = '{"sortable":false}'
  const request = page.loadDataList()
  assert.equal(page.sortState.value, null)
  assert.equal('sortField' in page.calls[0].payload, false)
  page.calls[0].resolve({ records: [] })
  await request
})

test('切换列表在新配置就绪前清空旧排序并丢弃旧列表响应', async () => {
  const page = createPage()
  page.sortState.value = { sortField: 'amount', sortDirection: 'desc' }
  const oldQuery = page.loadDataList()
  page.runtimeListKey.value = 'other'
  await page.prepareEntityDefinition()
  assert.equal(page.sortState.value, null)
  assert.equal(page.pageNum.value, 1)
  page.calls[0].resolve({ records: [{ id: 'old' }], total: 5 })
  await oldQuery
  assert.deepEqual(page.dataList.value, [])
  assert.equal(page.total.value, 0)
})
