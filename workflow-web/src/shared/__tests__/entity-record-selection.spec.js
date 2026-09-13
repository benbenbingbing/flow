import assert from 'node:assert/strict'

import {
  normalizeRecordSelection,
  refreshRecordPageSelection,
  reconcileRecordPageSelection,
  recordSelectionIds,
  recordSelectionValues,
  removeRecordSelection
} from '../entity-record-selection.js'

const projectA = { id: 'project-a', name: '项目 A' }
const projectB = { id: 'project-b', name: '项目 B' }
const projectC = { id: 'project-c', name: '项目 C' }

assert.deepEqual(
  recordSelectionIds(normalizeRecordSelection([
    projectA,
    projectA,
    projectB
  ])),
  ['project-a', 'project-b'],
  '同一条业务数据只能进入一次多选结果'
)

const afterSecondPage = reconcileRecordPageSelection(
  [projectA, projectB],
  [projectC],
  [projectC]
)
assert.deepEqual(
  recordSelectionIds(afterSecondPage),
  ['project-a', 'project-b', 'project-c'],
  '翻页选择时必须保留前页记录'
)

const afterReturningFirstPage = reconcileRecordPageSelection(
  afterSecondPage,
  [projectA, projectB],
  [projectB]
)
assert.deepEqual(
  recordSelectionIds(afterReturningFirstPage),
  ['project-b', 'project-c'],
  '返回前页取消记录时不能影响其他页选择'
)

const staleProjectB = {
  ...projectB,
  actionCapabilities: { batchDelete: { visible: true, enabled: true } }
}
const freshProjectB = {
  ...projectB,
  actionCapabilities: { batchDelete: { visible: true, enabled: false } }
}
const refreshedSelection = reconcileRecordPageSelection(
  [staleProjectB, projectC],
  [freshProjectB],
  [freshProjectB]
)
assert.equal(
  refreshedSelection[0],
  freshProjectB,
  '返回当前页后必须用最新记录替换同 ID 的旧选择对象'
)
assert.equal(
  refreshedSelection[0].actionCapabilities.batchDelete.enabled,
  false,
  '选择集按钮必须读取本次查询返回的最新能力'
)

const restoredSelection = refreshRecordPageSelection(
  [staleProjectB, projectC],
  [freshProjectB]
)
assert.equal(
  restoredSelection[0],
  freshProjectB,
  '表格屏蔽 selection-change 自动恢复勾选时也必须主动刷新当前页对象'
)
assert.equal(
  restoredSelection[1],
  projectC,
  '自动恢复当前页选择时必须保留其他页对象'
)

assert.deepEqual(
  recordSelectionIds(removeRecordSelection(afterReturningFirstPage, 'project-c')),
  ['project-b'],
  '已选区可以移除任意页面的记录'
)

assert.deepEqual(
  recordSelectionValues([
    { id: '1', code: 'admin', name: '管理员' },
    { id: '2', code: 'reviewer', name: '审批人' }
  ], 'code'),
  ['admin', 'reviewer'],
  '系统用户选择可以保持原有 username 编码值'
)

console.log('entity record selection tests passed')
