import assert from 'node:assert/strict'
import { normalizeCustomButton, validateFormActionConfiguration } from '@flow/workflow-core/form-actions'
import { createFormActionRuntime } from '@flow/workflow-core/form-action-runtime'

const executions = {
  handler: { name: 'registered-action' }, component: { name: 'registered-component' },
  'open-form': { entityCode: 'ITEM', formId: 'child', formMode: 'edit', recordIdField: 'childId' },
  'open-list': { entityCode: 'ITEM', listKey: 'children' },
  'open-related-content': { compositionKey: 'child' }
}
for (const [customMode, execution] of Object.entries(executions)) {
  const button = normalizeCustomButton({ key: 'open_child', perm: 'entity:item:view', customMode, execution })
  assert.deepEqual(normalizeCustomButton(JSON.parse(JSON.stringify(button))).execution, execution)
  assert.equal(validateFormActionConfiguration({ actionBar: { customButtons: [button] } }).valid, true, customMode)
  assert.equal(validateFormActionConfiguration({ actionBar: { customButtons: [{ ...button, execution: {} }] } }).valid, false)
}
assert.equal(normalizeCustomButton({}).customMode, 'event')
const button = normalizeCustomButton({ key: 'open_child', perm: 'entity:item:view' })
assert.equal(validateFormActionConfiguration({ actionBar: { customButtons: [button] } }).valid, false, '旧按钮仍须事件链')

const action = { ...button, ownerFormId: 'owner', customMode: 'open-related-content', execution: executions['open-related-content'] }
let result = [{ ...action, execution: { compositionKey: 'published-child' } }], calls = 0, events = 0
const runtime = createFormActionRuntime({ formActionRuntimeApi: { resolve: async () => result }, uiEventBindingApi: { execute: async () => { events++; return 'event' } }, createBusinessTraceKey: () => 'test' })
const form = { id: 'owner' }
const context = { mode: 'edit', entityCode: 'ITEM', executeClientAction: async current => { calls++; return current.execution.compositionKey } }
assert.equal(await runtime.executeCustomFormAction(action, form, context), 'published-child', '仅使用重新鉴权返回的目标')
result = [{ ...action, enabled: false, reason: '无权执行' }]
await assert.rejects(runtime.executeCustomFormAction(action, form, context), /无权执行/)
assert.equal(calls, 1)
result = [action]
await runtime.executeCustomFormAction(action, form, { ...context, isCurrent: () => false })
assert.equal(calls, 1, '来源关闭后不打开旧记录')
assert.equal(await runtime.executeCustomFormAction({ ...action, customMode: 'event' }, form, context), 'event')
assert.equal(events, 1)
console.log('form-button-execution: persistence, configuration, authoritative targets, permissions and legacy events passed')
