import assert from 'node:assert/strict'
import test from 'node:test'
import { buildRuntimeContext, childFormRuntimeContext } from '../src/shared/form-runtime/context.js'
import { buildFieldEventPayload } from '../src/shared/form-runtime/fieldEvents.js'

// 定位信息不构成权限；只传服务端支持的业务来源，避免转发组件或表单敏感对象。
test('task context preserves business locators while excluding client authority claims', () => {
  const context = buildRuntimeContext({ taskId: 'task-1', processInstanceId: 'instance-1', recordId: 'r1', permissions: ['all'], dataScope: '*', userId: 'admin', form: { id: 'form-1', runtimeContext: { nodeId: 'node-1' } } })
  assert.deepEqual(context, { nodeId: 'node-1', taskId: 'task-1', processInstanceId: 'instance-1', recordId: 'r1' })
})
test('nested form preserves root task record and carries child record separately', () => {
  const context = childFormRuntimeContext({ form: { id: 'root' }, taskId: 'task', recordId: 'parent' }, { nodeKey: 'details' })
  assert.deepEqual(context, { taskId: 'task', recordId: 'parent', rootFormId: 'root', rootRecordId: 'parent', rootEntityCode: undefined, componentPath: ['details'] })
  const child = buildRuntimeContext({ form: { runtimeContext: context }, recordId: 'child' })
  assert.equal(child.recordId, 'parent')
  assert.equal(child.childRecordId, 'child')
  assert.deepEqual(child.componentPath, [{ nodeKey: 'details', recordId: 'child' }])
})
test('field event carries config digest and task context without changing user data', () => {
  const payload = buildFieldEventPayload({ fieldCode: 'name' }, 'new', { form: { id: 'form', configHash: 'digest', runtimeContext: { taskId: 'task' } }, record: { id: 'r1', data: { name: 'new' } }, mode: 'approve' })
  assert.equal(payload.configHash, 'digest')
  assert.equal(payload.runtimeContext.taskId, 'task')
  assert.equal(payload.runtimeContext.recordId, 'r1')
  assert.deepEqual(payload.input.form, { name: 'new' })
  assert.equal(Object.keys(payload).some(key => /token|releaseVersion|releaseId/i.test(key)), false)
})

test('unsaved subform row does not send a synthetic UI key as a persisted record', () => {
  const context = buildRuntimeContext({ form: { runtimeContext: { rootFormId: 'root', rootRecordId: 'parent', recordId: 'parent', componentPath: ['details'] } }, row: { isNew: true }, recordId: 'parent:details:0' })
  assert.equal(context.rootRecordId, 'parent')
  assert.equal(context.recordId, 'parent')
  assert.equal(context.childRecordId, undefined)
  assert.deepEqual(context.componentPath, ['details'])
})
