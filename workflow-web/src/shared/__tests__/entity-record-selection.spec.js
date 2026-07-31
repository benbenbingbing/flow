import assert from 'node:assert/strict'

import {
  normalizeRecordSelection,
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
