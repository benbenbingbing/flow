import assert from 'node:assert/strict'
import {
  mergeResolvedFormActions,
  resolveLocalFormActions
} from '../form-actions.js'

const baseForm = {
  id: 'form-1',
  viewConfig: {}
}

assert.deepEqual(
  resolveLocalFormActions(baseForm, {
    mode: 'create',
    workflowReady: false
  }).map(item => item.key),
  ['close', 'reset', 'save']
)

assert.deepEqual(
  resolveLocalFormActions(baseForm, {
    mode: 'create',
    workflowReady: true
  }).map(item => item.key),
  ['close', 'reset', 'save', 'saveAndStart']
)

assert.deepEqual(
  resolveLocalFormActions(baseForm, {
    mode: 'view'
  }).map(item => item.key),
  ['close']
)

const configured = {
  id: 'form-2',
  viewConfig: {
    actionBar: {
      version: 1,
      builtInOverrides: {
        save: {
          enabled: true,
          labelByMode: { edit: '提交修改' }
        }
      },
      customButtons: [{
        key: 'generate_report',
        label: '生成报告',
        modes: ['view'],
        placement: 'FOOTER',
        perm: 'entity:demo:custom:generate_report'
      }]
    }
  }
}

assert.equal(
  resolveLocalFormActions(configured, { mode: 'edit' })
    .find(item => item.key === 'save')?.label,
  '提交修改'
)

assert.deepEqual(
  resolveLocalFormActions(configured, { mode: 'view' })
    .map(item => item.runtimeKey),
  ['close', 'form-2:generate_report']
)

assert.deepEqual(
  mergeResolvedFormActions([
    [{ key: 'close', type: 'built-in', sort: 10 }],
    [
      { key: 'close', type: 'built-in', sort: 10 },
      {
        ownerFormId: 'form-2',
        key: 'notify',
        type: 'custom',
        sort: 50
      }
    ]
  ]).map(item => item.runtimeKey || item.key),
  ['close', 'form-2:notify']
)

console.log('form-actions.spec.js passed')
