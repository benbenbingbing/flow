import assert from 'node:assert/strict'
import {
  assertRelatedContentResolveContract,
  buildRelatedContentResolveInput,
  createLatestRequestGate,
  filterRelatedContentButtons,
  normalizeRelatedContentRuntimeActions
} from '../related-content-runtime.js'

assert.deepEqual(
  normalizeRelatedContentRuntimeActions(['view', 'EDIT', 'EDIT', 'DELETE']),
  ['VIEW', 'EDIT']
)

const toolbar = [
  { key: 'create', type: 'built-in' },
  { key: 'exportSelected', type: 'built-in' },
  { key: 'exportAll', type: 'built-in' },
  { key: 'batchDelete', type: 'built-in' },
  { key: 'custom-event', type: 'custom', customMode: 'event' }
]
assert.deepEqual(
  filterRelatedContentButtons(toolbar, ['CREATE'], 'TOOLBAR').map(item => item.key),
  ['create'],
  '新增权限不能顺带开放导出、批量删除或任意事件'
)

const rowActions = [
  { key: 'view', type: 'built-in' },
  { key: 'edit', type: 'built-in' },
  { key: 'approve', type: 'built-in' },
  { key: 'delete', type: 'built-in' },
  {
    key: 'open-edit-form',
    type: 'custom',
    customMode: 'open-form',
    targetFormMode: 'EDIT'
  }
]
assert.deepEqual(
  filterRelatedContentButtons(rowActions, ['VIEW'], 'ROW').map(item => item.key),
  ['view']
)
assert.deepEqual(
  filterRelatedContentButtons(rowActions, ['EDIT'], 'ROW').map(item => item.key),
  ['edit', 'open-edit-form']
)

assert.throws(
  () => assertRelatedContentResolveContract({ targetContentType: 'LIST' }),
  /缺少可信查询凭证/
)
assert.doesNotThrow(() => assertRelatedContentResolveContract({
  targetContentType: 'LIST',
  listContextToken: 'signed-list-context',
  traversalContextToken: 'signed-traversal-context'
}))
assert.throws(
  () => assertRelatedContentResolveContract({
    targetContentType: 'FORM'
  }),
  /缺少安全导航凭证/
)

const pinnedResolveInput = buildRelatedContentResolveInput({
  ownerType: 'form',
  ownerId: 'owner-form',
  releaseId: 'owner-release-5',
  releaseVersion: 5,
  compositionKey: 'related-project',
  sourceRecordId: 'source-record',
  releaseResolutionToken: ' signed-owner-release '
})
assert.equal(pinnedResolveInput.ownerType, 'FORM')
assert.equal(
  pinnedResolveInput.releaseResolutionToken,
  'signed-owner-release',
  '历史钉定表单必须把服务端签发的发布令牌带入关联内容解析请求'
)

const activeResolveInput = buildRelatedContentResolveInput({
  ownerType: 'FORM',
  ownerId: 'owner-form',
  releaseId: 'active-release',
  releaseVersion: 6,
  compositionKey: 'related-project',
  sourceRecordId: 'source-record'
})
assert.equal(activeResolveInput.releaseResolutionToken, undefined)
assert.equal(
  JSON.stringify(activeResolveInput).includes('releaseResolutionToken'),
  false,
  '未签发令牌时不能伪造空凭证或改变当前激活版本解析语义'
)

const gate = createLatestRequestGate()
const staleRequest = gate.begin()
const currentRequest = gate.begin()
assert.equal(gate.isCurrent(staleRequest), false)
assert.equal(gate.isCurrent(currentRequest), true)
gate.invalidate()
assert.equal(gate.isCurrent(currentRequest), false)

console.log('related content runtime tests passed')
