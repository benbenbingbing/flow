import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
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
  /缺少来源路径/
)
assert.doesNotThrow(() => assertRelatedContentResolveContract({
  targetContentType: 'LIST',
  compositionContext: { ownerType: 'FORM', ownerId: 'form', compositionKey: 'items', recordId: '1' }
}))
assert.throws(
  () => assertRelatedContentResolveContract({
    targetContentType: 'FORM'
  }),
  /缺少来源路径/
)

const pinnedResolveInput = buildRelatedContentResolveInput({
  ownerType: 'form',
  ownerId: 'owner-form',
  releaseId: 'owner-release-5',
  releaseVersion: 5,
  compositionKey: 'related-project',
  sourceRecordId: 'source-record',
  configHash: 'owner-hash'
})
assert.equal(pinnedResolveInput.ownerType, 'FORM')
assert.equal(
  pinnedResolveInput.configHash,
  'owner-hash',
  '关联内容传递当前配置摘要以检查配置变化'
)

const activeResolveInput = buildRelatedContentResolveInput({
  ownerType: 'FORM',
  ownerId: 'owner-form',
  releaseId: 'active-release',
  releaseVersion: 6,
  compositionKey: 'related-project',
  sourceRecordId: 'source-record'
})
assert.equal(activeResolveInput.configHash, undefined)
assert.equal(
  JSON.stringify(activeResolveInput).includes('configHash'),
  false,
  '首次读取不要求配置摘要'
)

const gate = createLatestRequestGate()
const staleRequest = gate.begin()
const currentRequest = gate.begin()
assert.equal(gate.isCurrent(staleRequest), false)
assert.equal(gate.isCurrent(currentRequest), true)
gate.invalidate()
assert.equal(gate.isCurrent(currentRequest), false)

const relatedRuntimeSource = readFileSync(
  new URL('../../components/related-content/RelatedContentRuntime.vue', import.meta.url),
  'utf8'
)
assert.match(
  relatedRuntimeSource,
  /entityApi\.getByCode\(value\.targetEntityCode, traversalRuntimeContext\)/,
  '目标实体定义必须沿服务端核验的关联来源路径读取'
)
assert.match(
  relatedRuntimeSource,
  /getEntityStatusList\(value\.targetEntityCode, traversalRuntimeContext\)/,
  '目标表单状态必须与目标实体共用同一关联来源路径'
)
assert.match(
  relatedRuntimeSource,
  /compositionContext:\s*value\.compositionContext/,
  '目标详情、子表单与原生动作必须继续透传关联来源路径'
)

console.log('related content runtime tests passed')
