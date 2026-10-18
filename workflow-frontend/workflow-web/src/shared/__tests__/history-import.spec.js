import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextImportGroup, archiveNodeEvents, referenceTargets } from '../history-import.js'

test('执行顺序来自冻结依赖而非表格顺序，失败组保留供重试', () => {
  const batch = { groupOrder: ['shared', 'business'], groups: [{ key: 'business', state: 'PENDING' }, { key: 'shared', state: 'PENDING' }] }
  assert.equal(nextImportGroup(batch).key, 'shared')
  batch.groups[1].state = 'COMMITTED'
  batch.groups[0].state = 'FAILED'
  assert.equal(nextImportGroup(batch).key, 'business')
  batch.groups[0].state = 'COMMITTED'
  assert.equal(nextImportGroup(batch), undefined)
})
test('同节点重复审批不合并，仅时间线的记录仍可在总列表查看', () => {
  const events = [{ id: 'E2', nodeId: 'review', sequence: 2 }, { id: 'E1', nodeId: 'review', sequence: 1 }, { id: 'E3', nodeId: 'TIMELINE_ONLY', sequence: 3 }]
  assert.deepEqual(archiveNodeEvents(events, 'review').map(e => e.id), ['E1', 'E2'])
  assert.equal(archiveNodeEvents(events, '').length, 3)
  assert.equal(events[0].id, 'E2', '排序不能改变归档原数组')
})
test('组成关系中的普通外键也能配置来源实体映射', () => {
  const main = { key: 'main', entityCode: 'expense' }, child = { key: 'items', entityCode: 'item' }
  const schemas = [{ code: 'expense', relations: [{ childEntity: 'item', childField: 'parent' }] }]
  assert.deepEqual(referenceTargets(child, { code: 'parent' }, { entities: [main, child] }, schemas), [main])
})
