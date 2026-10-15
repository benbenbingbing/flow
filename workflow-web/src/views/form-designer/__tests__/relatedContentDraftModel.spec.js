import assert from 'node:assert/strict'
import { createRelatedContentDraftState, persistRelatedContentDraft } from '../relatedContentDraftModel.js'
import { createEmptyRelatedContent, normalizeRelatedContent } from '../../../shared/related-content.js'

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

// 打开后不改其他属性也必须落库清理旧接口；有效独立扩展和空编辑占位不能被误标脏。
const legacyAction = item('legacy-action')
legacyAction.config.specialHandling.actionServices = [{ actionKey: 'AUDIT', extensionId: 'old-audit' }]
const legacyData = item('legacy-data')
legacyData.config.specialHandling.mode = 'BOTH'
legacyData.config.specialHandling.interfaceService.extensionId = 'old-reader'
legacyData.config.specialHandling.customComponent.name = 'project-board'
legacyData.configDocument = JSON.stringify(legacyData.config)
delete legacyData.config
const extensionPage = item('extension-page')
extensionPage.config.relation.type = 'SAME_RECORD'
extensionPage.config.specialHandling.mode = 'INTERFACE_SERVICE'
extensionPage.config.specialHandling.actionServices = [{ actionKey: 'AUDIT', extensionId: 'valid-audit' }]
const displayOnly = item('display-only')
delete displayOnly.config.specialHandling
const sourceItems = [legacyAction, legacyData, extensionPage, item('empty-placeholder'), displayOnly]
let cleanedRows = sourceItems.map(source => normalizeRelatedContent(source))
const cleanupState = createRelatedContentDraftState()
cleanupState.reset(cleanedRows, { sourceItems })
assert.deepEqual(cleanedRows.map(row => cleanupState.dirty(row)), [true, true, false, false, false])
const cleanupWrites = []
let rejectCleanup = true, cleanupRevision = 4
const cleanupApi = {
  async validate(type, owner, config) { return { normalizedConfig: config } },
  async update(type, owner, id, payload) {
    if (rejectCleanup) throw new Error('清理保存失败')
    assert.equal('interfaceService' in payload.config.specialHandling, false)
    assert.equal('actionServices' in payload.config.specialHandling, false)
    cleanupWrites.push(id)
    return { ...payload, id, revision: 2, ownerRevision: ++cleanupRevision }
  }
}
const saveCleanup = () => persistRelatedContentDraft({
  state: cleanupState, items: cleanedRows, ownerId: 'owner', api: cleanupApi,
  getOwnerRevision: () => cleanupRevision, onRevision: revision => { cleanupRevision = revision },
  onSaved: saved => { cleanedRows = cleanedRows.map(row => row.id === saved.id ? normalizeRelatedContent(saved) : row) }
})
await assert.rejects(saveCleanup(), /清理保存失败/)
assert.equal(cleanupState.dirty(cleanedRows[0]), true, '失败不能清除待清理标记')
rejectCleanup = false
await saveCleanup()
assert.deepEqual(cleanupWrites, ['legacy-action', 'legacy-data'])
assert.equal(cleanedRows[0].config.specialHandling.mode, 'NONE')
assert.equal(cleanedRows[1].config.specialHandling.mode, 'CUSTOM_COMPONENT')
assert.equal(cleanedRows[1].config.specialHandling.customComponent.name, 'project-board')
assert.equal(cleanupState.hasChanges(cleanedRows), false)
await saveCleanup()
assert.equal(cleanupWrites.length, 2, '清理成功后不重复写入')
cleanupState.reset(cleanedRows)
assert.equal(cleanupState.hasChanges(cleanedRows), false, '服务端清理结果重新加载后保持已保存')
console.log('related content draft model tests passed')
