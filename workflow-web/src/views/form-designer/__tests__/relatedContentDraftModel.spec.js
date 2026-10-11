import assert from 'node:assert/strict'
import { createRelatedContentDraftState, persistRelatedContentDraft } from '../relatedContentDraftModel.js'
import { createEmptyRelatedContent } from '../../../shared/related-content.js'

const item = (key, id = key) => ({ ...createEmptyRelatedContent(), id, compositionKey: key, revision: 1, ownerRevision: 4 })
const state = createRelatedContentDraftState()
let rows = [item('one'), item('two')], ownerRevision = 4
state.reset(rows)
rows[0].config.presentation.title = '保留目标与动作'
rows[1].config.presentation.loadMode = 'IMMEDIATE'
const calls = []
const api = {
  async validate(type, owner, config) { return { normalizedConfig: config } },
  async update(type, owner, id, payload) {
    calls.push([id, payload.expectedOwnerRevision])
    if (id === 'two') throw new Error('并发冲突')
    return { ...payload, id, revision: 2, ownerRevision: ++ownerRevision }
  }
}
const persist = onlyKey => persistRelatedContentDraft({ state, items: rows, ownerId: 'owner', api, onlyKey,
  getOwnerRevision: () => ownerRevision, onRevision: revision => { ownerRevision = revision },
  onSaved: saved => { rows = rows.map(row => row.compositionKey === saved.compositionKey ? saved : row) }
})
await assert.rejects(persist(), /并发冲突/)
assert.deepEqual(calls, [['one', 4], ['two', 5]])
assert.equal(state.dirty(rows[0]), false, '成功项推进基线，不重复保存')
assert.equal(state.dirty(rows[1]), true, '失败项保留脏状态与输入')
assert.equal(rows[1].config.presentation.loadMode, 'IMMEDIATE')
await persist('one')
assert.equal(calls.length, 2, '保存当前不触碰其他失败项')
state.remove(rows[1]); rows = rows.filter(row => row.id !== 'two')
api.remove = async (type, owner, id, revision, expectedOwner) => {
  assert.equal(expectedOwner, 5); assert.equal(revision, 1)
  throw new Error('删除失败')
}
await assert.rejects(persist(), /删除失败/)
assert.equal(state.deleted.size, 1, '删除失败保留标记以便重试')
api.remove = async () => ({ ownerRevision: 6 })
await persist()
assert.equal(ownerRevision, 6)
assert.equal(state.hasChanges(rows), false)
state.remove(item('new', ''))
assert.equal(state.deleted.size, 0, '未保存新增项删除不调用服务端')

// 预检失败必须发生在第一条写入前。
state.reset(rows); rows[0].config.enabled = false
api.validate = async () => { throw new Error('配置不完整') }
await assert.rejects(persist(), /配置不完整/)
assert.equal(calls.length, 2)
console.log('related content draft model tests passed')
