import assert from 'node:assert/strict'
import test from 'node:test'
import { createTaskHandoverApi } from '../../../api/system/taskHandoverApi.js'
import {
  buildHandoverPayload, isEligibleTarget, useTaskHandover
} from '../task-handover/useTaskHandover.js'
import { createHandoverUserDataSource } from '../task-handover/handoverUserDataSource.js'

const normalUser = { id: 'target', status: '0', deleted: 0 }
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('交接 API 使用独立的人员、分页待办和批量交接接口', async () => {
  const calls = []
  const api = createTaskHandoverApi({
    get: (...args) => calls.push(['get', ...args]),
    post: (...args) => calls.push(['post', ...args])
  })
  const query = { sourceUserId: 'disabled', pageNum: 2, pageSize: 20 }
  const payload = { sourceUserId: 'disabled', targetUserId: 'target', taskIds: ['t1'], all: false, reason: '离职交接' }
  await api.users({ keyword: '离职员工', targetOnly: false })
  await api.tasks(query)
  await api.transfer(payload)
  assert.deepEqual(calls, [
    ['get', '/task-handover/users', { params: { keyword: '离职员工', targetOnly: false } }],
    ['get', '/task-handover/tasks', { params: query }],
    ['post', '/task-handover/transfer', payload]
  ])
})

test('接收人只允许正常未删除用户，拒绝同人和未知状态', () => {
  assert.equal(isEligibleTarget(normalUser, 'source'), true)
  assert.equal(isEligibleTarget({ ...normalUser, status: 0, deleted: false }, 'source'), true)
  for (const invalid of [
    { ...normalUser, status: '1' }, { ...normalUser, status: null },
    { ...normalUser, deleted: true }, { ...normalUser, deleted: '1' },
    { ...normalUser, id: 'source' }, null
  ]) assert.equal(isEligibleTarget(invalid, 'source'), false)
})

test('选中交接不允许空选择，全部交接必须显式声明且不携带局部任务范围', () => {
  const input = { sourceUserId: 'disabled-or-deleted-source', targetUser: normalUser, taskIds: ['t1', 't1', ' t2 '], reason: ' 离职交接 ' }
  assert.deepEqual(buildHandoverPayload(input), {
    sourceUserId: input.sourceUserId, targetUserId: 'target', taskIds: ['t1', 't2'], all: false, reason: '离职交接'
  })
  assert.throws(() => buildHandoverPayload({ ...input, taskIds: [] }), /至少选择/)
  assert.throws(() => buildHandoverPayload({ ...input, taskIds: [], all: 'true' }), /至少选择/)
  assert.deepEqual(buildHandoverPayload({ ...input, all: true }).taskIds, [])
  assert.equal(buildHandoverPayload({ ...input, all: true }).all, true)
  assert.throws(() => buildHandoverPayload({ ...input, reason: ' ' }), /交接原因/)
  assert.throws(() => buildHandoverPayload({ ...input, reason: '长'.repeat(501) }), /500/)
  assert.throws(() => buildHandoverPayload({ ...input, targetUser: { ...normalUser, id: input.sourceUserId } }), /不同/)
})

test('跨页勾选和取消只影响当前页，切换来源立即清空任务和选择', async () => {
  const state = useTaskHandover({ tasks: async ({ pageNum }) => ({ records: [{ taskId: `p${pageNum}` }], total: 30 }) })
  await state.changeSource('source')
  state.selectCurrentPage(state.tasks.value)
  await state.changePage(2)
  state.selectCurrentPage(state.tasks.value)
  assert.deepEqual(state.selectedTaskIds.value, ['p1', 'p2'])
  await state.changePage(1)
  state.selectCurrentPage([])
  assert.deepEqual(state.selectedTaskIds.value, ['p2'])
  await state.changeSource('other')
  assert.deepEqual(state.selectedTaskIds.value, [])
  assert.equal(state.pageNum.value, 1)
  await state.changeSource('')
  assert.deepEqual(state.tasks.value, [])
  assert.equal(state.total.value, 0)
})

test('快速切换人员，旧请求晚到也不能混入另一人的待办', async () => {
  const old = deferred(), current = deferred()
  const state = useTaskHandover({ tasks: ({ sourceUserId }) => sourceUserId === 'old' ? old.promise : current.promise })
  const oldLoad = state.changeSource('old')
  const currentLoad = state.changeSource('new')
  assert.deepEqual(state.tasks.value, [])
  current.resolve({ list: [{ taskId: 'new-task' }], total: 1 })
  await currentLoad
  state.selectCurrentPage(state.tasks.value)
  old.resolve({ records: [{ taskId: 'old-task' }], total: 999 })
  await oldLoad
  assert.deepEqual(state.tasks.value, [{ taskId: 'new-task' }])
  assert.equal(state.total.value, 1)
  assert.deepEqual(state.selectedTaskIds.value, ['new-task'])
  assert.equal(state.loading.value, false)
})

test('清空来源后旧请求失败不能恢复错误状态或待办', async () => {
  const pending = deferred()
  const state = useTaskHandover({ tasks: () => pending.promise })
  const load = state.changeSource('source')
  await state.changeSource('')
  pending.reject(new Error('旧请求失败'))
  await load
  assert.equal(state.error.value, '')
  assert.equal(state.loading.value, false)
  assert.deepEqual(state.tasks.value, [])
})

test('刷新清空选择；查询失败显示错误并允许重新加载', async () => {
  let fail = false
  const state = useTaskHandover({ tasks: async () => {
    if (fail) throw new Error('服务暂不可用')
    return { records: [{ taskId: 't1' }], total: 1 }
  } })
  await state.changeSource('source')
  state.selectCurrentPage(state.tasks.value)
  fail = true
  await state.refresh()
  assert.equal(state.error.value, '服务暂不可用')
  assert.deepEqual(state.tasks.value, [])
  assert.equal(state.selectedCount.value, 0)
  fail = false
  await state.refresh()
  assert.equal(state.error.value, '')
  assert.equal(state.tasks.value.length, 1)
})

test('并发办理导致末页消失时回到有效页', async () => {
  const pages = []
  const state = useTaskHandover({ tasks: async ({ pageNum }) => {
    pages.push(pageNum)
    return { records: pageNum === 1 ? [{ taskId: 'remaining' }] : [], total: 1 }
  } })
  await state.changeSource('source')
  await state.changePage(3)
  assert.deepEqual(pages, [1, 3, 1])
  assert.equal(state.pageNum.value, 1)
  assert.equal(state.loading.value, false)
  assert.equal(state.tasks.value[0].taskId, 'remaining')
})

test('人员弹窗保留异常来源及所选姓名，接收人过滤正常状态并排除来源', async () => {
  const users = [
    { id: 'former', username: 'former', nickname: '离职人员', status: '1', deleted: true },
    { id: 'source', username: 'source', status: '0', deleted: false },
    { ...normalUser, username: 'receiver', nickname: '接收人' }
  ]
  const calls = []
  const api = { users: async params => {
    calls.push(params)
    return users.filter(user => !params.keyword || user.username.includes(params.keyword))
  } }
  const source = createHandoverUserDataSource(api)
  const first = await source.list({ pageNum: 1, pageSize: 1 })
  assert.equal(first.total, 3)
  assert.equal(first.records[0].deleted, true)
  assert.equal(first.records[0].name, '离职人员')
  assert.equal((await source.list({ pageNum: 2, pageSize: 1 })).records[0].id, 'source')
  await source.list({ keyword: ' receiver ' })
  assert.equal((await source.batch(['former']))[0].name, '离职人员', '搜索其他人员后仍回显已删除来源')
  assert.equal(calls.at(-1).keyword, 'receiver')
  const target = createHandoverUserDataSource(api, { targetOnly: true, getSourceUserId: () => 'source' })
  const result = await target.list({})
  assert.equal(calls.at(-1).targetOnly, true)
  assert.deepEqual(result.records.map(user => user.id), ['target'])
  assert.equal((await target.batch(['target']))[0].username, 'receiver')
})
