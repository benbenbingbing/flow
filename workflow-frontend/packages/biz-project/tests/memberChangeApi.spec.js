import assert from 'node:assert/strict'
import test from 'node:test'
import { createMemberChangeApi } from '../src/api/memberChange.js'

test('成员上下文通过宿主的实体范围接口查询，排除自身投入并保留有效角色', async () => {
  const calls = []
  const member = { id: 'm1', status: 'ACTIVE', data: { project_id: 'p1', user_id: 'u1', allocation_percentage: 40, account_required_flag: true, environment_access_required_flag: true } }
  const api = createMemberChangeApi({
    async getDetail(...args) { calls.push(['detail', ...args]); return member },
    async getList(entity, query) {
      calls.push(['list', entity, query])
      return entity === 'project_role_assignment'
        ? { data: { records: [{ status: 'ACTIVE' }, { status: 'SUSPENDED' }] } }
        : { records: [member, { id: 'm2', status: 'ACTIVE', data: { allocation_percentage: 30 } }, { id: 'm3', status: 'LEFT', data: { allocation_percentage: 90 } }] }
    }
  })
  const result = await api.loadMemberChangeContext({ projectId: 'p1', memberId: 'm1', requestedAllocation: 80 })
  assert.deepEqual(calls, [
    ['detail', 'project_member', 'm1'],
    ['list', 'project_role_assignment', { project_id: 'p1', member_id: 'm1', pageNum: 1, pageSize: 200 }],
    ['list', 'project_member', { user_id: 'u1', pageNum: 1, pageSize: 200 }]
  ])
  assert.equal(result.activeRoleCount, 1)
  assert.equal(result.memberProjectMismatch, false)
  assert.equal(result.accountRequired, true)
  assert.equal(result.environmentAccessRequired, true)
  assert.deepEqual(result.allocation, { current: 30, requested: 80, total: 110, available: 70, exceeded: true })
})

test('加入操作只查询目标人员的跨项目投入，不依赖通用表单的业务服务', async () => {
  const api = createMemberChangeApi({
    async getDetail() { assert.fail('JOIN 不应查询既有成员详情') },
    async getList(entity, query) {
      assert.equal(entity, 'project_member')
      assert.equal(query.user_id, 'join-user')
      return [{ id: 'm2', status: 'ACTIVE', data: { allocation_percentage: 20 } }]
    }
  })
  const result = await api.loadMemberChangeContext({ projectId: 'p1', targetUserId: 'join-user', requestedAllocation: 50 })
  assert.equal(result.member, null)
  assert.equal(result.activeRoleCount, 0)
  assert.equal(result.allocation.total, 70)
})

test('目标成员所属项目不匹配时返回阻断校验的标记', async () => {
  const api = createMemberChangeApi({
    async getDetail() { return { data: { project_id: 'other-project' } } },
    async getList() { return [] }
  })
  assert.equal((await api.loadMemberChangeContext({ projectId: 'p1', memberId: 'm1' })).memberProjectMismatch, true)
})

test('权限或网络错误保持向上传递，不能将失败查询伪装成空上下文', async () => {
  const failure = new Error('无权读取项目成员')
  const api = createMemberChangeApi({ async getDetail() { throw failure }, async getList() { return [] } })
  await assert.rejects(api.loadMemberChangeContext({ projectId: 'p1', memberId: 'm1' }), error => error === failure)
  assert.throws(() => createMemberChangeApi({}), /entityDataApi.getDetail\/getList/)
})
