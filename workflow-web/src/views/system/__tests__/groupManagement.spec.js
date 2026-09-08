import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import { createGroupApi } from '../../../api/system/groupApi.js'
import {
  buildGroupPayload,
  hasGroupMemberChanges,
  normalizeGroupMemberIds,
  prepareGroupMemberChange,
  runGroupStatusChange,
  submitGroupForm
} from '../../../shared/group-management.js'

function fakeTransport() {
  const calls = []
  return {
    calls,
    get(url, ...args) {
      const call = { method: 'get', url, args }
      calls.push(call)
      return Promise.resolve(call)
    },
    post(url, ...args) {
      const call = { method: 'post', url, args }
      calls.push(call)
      return Promise.resolve(call)
    }
  }
}

test('用户组 API 的全部端点使用正确方法、编码 ID 并以 JSON 提交状态', async () => {
  const transport = fakeTransport()
  const api = createGroupApi(transport)
  const payload = { groupName: '审批组' }

  await api.getGroupList()
  await api.getEnabledGroups()
  await api.getGroupById('组/ 1?')
  await api.createGroup(payload)
  await api.updateGroup('组/ 1?', payload)
  await api.deleteGroup('组/ 1?')
  await api.updateGroupStatus('组/ 1?', 1)
  await api.saveGroupUsers('组/ 1?', [' user-1 ', 'user-1', '', 2])
  await api.getUsers()

  const encodedPath = '/system/group/%E7%BB%84%2F%201%3F'
  assert.deepEqual(transport.calls, [
    { method: 'get', url: '/system/group/list', args: [] },
    { method: 'get', url: '/system/group/enabled', args: [] },
    { method: 'get', url: encodedPath, args: [] },
    { method: 'post', url: '/system/group', args: [payload] },
    { method: 'post', url: `${encodedPath}/update`, args: [payload] },
    { method: 'post', url: `${encodedPath}/delete`, args: [] },
    { method: 'post', url: `${encodedPath}/status`, args: [{ status: '1' }] },
    { method: 'post', url: `${encodedPath}/users`, args: [['user-1', '2']] },
    { method: 'get', url: '/system/group/users', args: [] }
  ])
})

test('用户组 API 拒绝空 ID，避免请求落到错误路由', () => {
  const api = createGroupApi(fakeTransport())
  assert.throws(() => api.getGroupById('  '), /用户组 ID 不能为空/)
})

test('用户组 API 拒绝错误请求体、非法状态和非数组成员且不发送请求', () => {
  const transport = fakeTransport()
  const api = createGroupApi(transport)

  assert.throws(() => api.createGroup(''), /用户组请求体必须是对象/)
  assert.throws(() => api.updateGroup('group-1', []), /用户组请求体必须是对象/)
  assert.throws(() => api.updateGroupStatus('group-1', '2'), /用户组状态只能为0/)
  assert.throws(
    () => api.saveGroupUsers('group-1'),
    /用户组成员必须是数组/
  )
  assert.equal(transport.calls.length, 0)
})

test('显式空数组可以清空成员，成员参数缺失不能误清空', async () => {
  const transport = fakeTransport()
  const api = createGroupApi(transport)

  await api.saveGroupUsers('group-1', [])

  assert.deepEqual(transport.calls, [{
    method: 'post',
    url: '/system/group/group-1/users',
    args: [[]]
  }])
})

test('新增只调用 createGroup 并提交不含 id 的白名单 payload', async () => {
  const createCalls = []
  const updateCalls = []
  const formData = {
    id: '',
    groupName: '财务审批',
    groupCode: 'finance_approval',
    description: '财务审批成员',
    sort: 3,
    status: '0',
    userIds: ['user-1'],
    deleted: 1,
    createTime: 'should-not-submit'
  }

  await submitGroupForm(formData, {
    createGroup: (...args) => {
      createCalls.push(args)
      return Promise.resolve()
    },
    updateGroup: (...args) => {
      updateCalls.push(args)
      return Promise.resolve()
    }
  })

  assert.deepEqual(createCalls, [[{
    groupName: '财务审批',
    groupCode: 'finance_approval',
    description: '财务审批成员',
    sort: 3,
    status: '0'
  }]])
  assert.deepEqual(updateCalls, [])
  assert.equal(Object.hasOwn(buildGroupPayload(formData), 'id'), false)
})

test('更新明确调用 updateGroup(id, payload)', async () => {
  const createCalls = []
  const updateCalls = []
  await submitGroupForm({
    id: ' group-1 ',
    groupName: '研发审批',
    groupCode: 'rd_approval',
    description: '',
    sort: 0,
    status: '1'
  }, {
    createGroup: (...args) => createCalls.push(args),
    updateGroup: (...args) => updateCalls.push(args)
  })

  assert.deepEqual(createCalls, [])
  assert.deepEqual(updateCalls, [[
    'group-1',
    {
      groupName: '研发审批',
      groupCode: 'rd_approval',
      description: '',
      sort: 0,
      status: '1'
    }
  ]])
})

test('用户组文本字段提交前去除首尾空白', () => {
  assert.deepEqual(buildGroupPayload({
    groupName: ' 财务审批 ',
    groupCode: ' finance_approval ',
    description: ' 审批成员 ',
    sort: 2,
    status: '0'
  }), {
    groupName: '财务审批',
    groupCode: 'finance_approval',
    description: '审批成员',
    sort: 2,
    status: '0'
  })
})

test('成员 ID 会规范化去重，集合变化不依赖人数或顺序', () => {
  assert.deepEqual(
    normalizeGroupMemberIds([' user-2 ', 'user-1', 'user-2', null, '', 3]),
    ['user-2', 'user-1', '3']
  )
  assert.equal(hasGroupMemberChanges(['u1', 'u2'], ['u2', 'u1']), false)
  assert.equal(hasGroupMemberChanges(['u1', 'u2'], ['u1', 'u3']), true)
})

test('成员未变化不确认不请求，同人数换人仍确认，取消被安全消费', async () => {
  let confirmations = 0
  const unchanged = await prepareGroupMemberChange({
    currentUserIds: ['u1', 'u2'],
    selectedUserIds: [' u2 ', 'u1', 'u1'],
    confirmChange: async () => { confirmations += 1 }
  })
  assert.equal(unchanged.shouldSave, false)
  assert.equal(unchanged.reason, 'unchanged')
  assert.deepEqual(
    [unchanged.addedCount, unchanged.removedCount],
    [0, 0]
  )
  assert.equal(confirmations, 0)

  const changed = await prepareGroupMemberChange({
    currentUserIds: ['u1', 'u2'],
    selectedUserIds: ['u1', 'u3'],
    confirmChange: async ({ beforeCount, afterCount, addedCount, removedCount }) => {
      confirmations += 1
      assert.deepEqual(
        [beforeCount, afterCount, addedCount, removedCount],
        [2, 2, 1, 1]
      )
    }
  })
  assert.equal(changed.shouldSave, true)
  assert.deepEqual(changed.userIds, ['u1', 'u3'])
  assert.deepEqual([changed.addedCount, changed.removedCount], [1, 1])
  assert.equal(confirmations, 1)

  await assert.doesNotReject(async () => {
    const cancelled = await prepareGroupMemberChange({
      currentUserIds: ['u1'],
      selectedUserIds: ['u2'],
      confirmChange: async () => { throw new Error('cancel') }
    })
    assert.equal(cancelled.shouldSave, false)
    assert.equal(cancelled.reason, 'cancelled')
  })
})

test('状态切换按行加锁，成功后保留状态并释放锁', async () => {
  const pendingIds = new Set()
  const updates = []
  let releaseConfirmation
  const confirmation = new Promise(resolve => { releaseConfirmation = resolve })
  const row = { id: 'group-1', status: '1' }

  const first = runGroupStatusChange({
    row,
    pendingIds,
    confirmChange: () => confirmation,
    updateStatus: async (...args) => { updates.push(args) }
  })
  assert.equal(pendingIds.has('group-1'), true)

  const duplicate = await runGroupStatusChange({
    row,
    pendingIds,
    confirmChange: async () => {},
    updateStatus: async (...args) => { updates.push(args) }
  })
  assert.equal(duplicate.reason, 'pending')

  releaseConfirmation()
  const result = await first
  assert.equal(result.updated, true)
  assert.equal(row.status, '1')
  assert.deepEqual(updates, [['group-1', '1']])
  assert.equal(pendingIds.has('group-1'), false)
})

test('状态确认取消或接口失败均回滚并释放逐行锁', async () => {
  for (const failureAt of ['confirm', 'update']) {
    const pendingIds = new Set()
    const row = { id: `group-${failureAt}`, status: '0' }
    const result = await runGroupStatusChange({
      row,
      pendingIds,
      confirmChange: async () => {
        if (failureAt === 'confirm') throw new Error('cancel')
      },
      updateStatus: async () => {
        if (failureAt === 'update') throw new Error('request failed')
      }
    })

    assert.equal(result.updated, false)
    assert.equal(result.reason, 'reverted')
    assert.equal(row.status, '1')
    assert.equal(pendingIds.size, 0)
  }
})

test('用户组页面接入已测试的提交、成员和逐行状态控制', () => {
  const source = readFileSync(new URL('../Group.vue', import.meta.url), 'utf8')
  assert.match(source, /submitGroupForm\(formData, \{ createGroup, updateGroup \}\)/)
  assert.match(source, /prepareGroupMemberChange\(\{/)
  assert.match(
    source,
    /新增 \$\{addedCount\}、移除 \$\{removedCount\}（总数 \$\{beforeCount\}→\$\{afterCount\}）/
  )
  assert.match(source, /runGroupStatusChange\(\{/)
  assert.match(source, /:loading="isStatusPending\(row\.id\)"/)
  assert.match(
    source,
    /:disabled="!canManage \|\| isStatusPending\(row\.id\)"/
  )
  assert.doesNotMatch(source, /const api = formData\.id \? updateGroup : createGroup/)
})

test('用户组写操作与 system:user:manage 权限保持一致并在 handler 入口防御', () => {
  const source = readFileSync(new URL('../Group.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{ useUserStore \} from '@\/stores\/user'/)
  assert.match(
    source,
    /const canManage = computed\(\(\) => userStore\.isSuperAdmin[\s\S]*?userStore\.permissions\.includes\('\*'\)[\s\S]*?userStore\.permissions\.includes\('system:user:manage'\)\)/
  )
  assert.match(source, /<el-button v-if="canManage" type="primary" @click="handleAdd">/)
  assert.match(
    source,
    /:disabled="!canManage \|\| isStatusPending\(row\.id\)"/
  )
  assert.match(
    source,
    /<el-table-column v-if="canManage" label="操作" width="240" fixed="right">/
  )

  for (const handler of [
    'handleAdd',
    'handleEdit',
    'handleSubmit',
    'handleDelete',
    'handleStatusChange',
    'handleAssignUsers'
  ]) {
    assert.match(
      source,
      new RegExp(`const ${handler} = [^=]+=> \\{\\s*if \\(!canManage\\.value\\) return`),
      `${handler} 缺少写权限入口防御`
    )
  }
  assert.match(
    source,
    /const handleSaveUsers = async \(\) => \{\s*if \(!canManage\.value \|\| !currentGroupId\.value\) return/
  )
})
