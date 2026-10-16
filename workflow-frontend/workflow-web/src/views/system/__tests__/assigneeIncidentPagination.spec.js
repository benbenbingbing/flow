import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ref } from 'vue'
import { extractSfcFunctions } from '../../../../scripts/test-sfc-functions.mjs'

function createPage(list) {
  const state = {
    status: ref('OPEN'), pageNum: ref(1), pageSize: ref(20), total: ref(0),
    incidents: ref([]), loading: ref(false)
  }
  const methods = extractSfcFunctions(new URL('../AssigneeIncidentManagement.vue', import.meta.url), [
    'listRequestVersion', 'unwrap', 'loadList', 'changePage', 'changePageSize', 'changeStatus'
  ], { ...state, assigneeIncidentApi: { list } })
  return { ...state, ...methods }
}

test('翻页传递偏移参数，筛选和页大小变化均回到第一页', async () => {
  const requests = []
  const page = createPage(async params => {
    requests.push(params)
    return { data: { records: [{ id: `page-${params.pageNum}` }], total: 1234 } }
  })
  await page.loadList()
  assert.equal(page.total.value, 1234)
  await page.changePage(3)
  assert.deepEqual(requests.at(-1), { status: 'OPEN', pageNum: 3, pageSize: 20 })
  assert.equal(page.incidents.value[0].id, 'page-3')
  page.status.value = ''
  await page.changeStatus()
  assert.deepEqual(requests.at(-1), { status: undefined, pageNum: 1, pageSize: 20 })
  await page.changePage(2)
  await page.changePageSize(50)
  assert.deepEqual(requests.at(-1), { status: undefined, pageNum: 1, pageSize: 50 })
})

test('末页事件处置完毕后，刷新自动加载最后一个有效页', async () => {
  const requests = []
  const page = createPage(async params => {
    requests.push(params.pageNum)
    return { records: params.pageNum === 2 ? [] : [{ id: 'remaining' }], total: 20 }
  })
  page.pageNum.value = 2
  await page.loadList()
  assert.deepEqual(requests, [2, 1])
  assert.equal(page.pageNum.value, 1)
  assert.equal(page.incidents.value[0].id, 'remaining')
  assert.equal(page.loading.value, false)
})

test('迟到的旧筛选响应不能覆盖新筛选结果或提前结束加载', async () => {
  const pending = []
  const page = createPage(() => new Promise(resolve => pending.push(resolve)))
  const first = page.loadList()
  page.status.value = 'RESOLVED'
  const second = page.changeStatus()
  pending[0]({ records: [{ id: 'old' }], total: 1 })
  await first
  assert.equal(page.loading.value, true)
  assert.deepEqual(page.incidents.value, [])
  pending[1]({ records: [{ id: 'new' }], total: 1 })
  await second
  assert.equal(page.incidents.value[0].id, 'new')
  assert.equal(page.loading.value, false)
})

test('查询失败清空旧页，空结果保留可用的第一页', async () => {
  let failed = true
  const page = createPage(async () => {
    if (failed) throw Error('查询失败')
    return { records: [], total: 0 }
  })
  page.incidents.value = [{ id: 'stale' }]
  page.total.value = 1
  await assert.rejects(page.loadList(), /查询失败/)
  assert.deepEqual(page.incidents.value, [])
  assert.equal(page.total.value, 0)
  assert.equal(page.loading.value, false)
  failed = false
  await page.loadList()
  assert.equal(page.pageNum.value, 1)
})
