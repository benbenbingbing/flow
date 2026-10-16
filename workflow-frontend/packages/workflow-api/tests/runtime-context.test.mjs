import test from 'node:test'
import assert from 'node:assert/strict'
import { createFormRuntimeApi } from '../src/formRuntime.js'
import { createUiConfigApi } from '../src/uiConfig.js'
import { createEntityApi } from '../src/entity.js'
import { createEntityListRuntimeApi } from '../src/entityListRuntime.js'
import { createUiCompositionRuntimeApi } from '../src/uiCompositionRuntime.js'

const source = { ownerType: 'FORM', ownerId: 'root', compositionKey: 'children', recordId: 'parent', path: [], configHash: 'root-hash' }
function harness() {
  const calls = []
  const request = { get: (...args) => calls.push({ method: 'get', args }), post: (...args) => calls.push({ method: 'post', args }) }
  return { calls, request }
}
test('form runtime GET serializes business entry without version or credential', () => {
  const { calls, request } = harness()
  const runtimeContext = { taskId: 'task', rootFormId: 'root', rootRecordId: 'parent', componentPath: ['details'] }
  createFormRuntimeApi(request).getFormRuntimeRelease('child', runtimeContext)
  assert.deepEqual(calls[0].args, ['/entity-forms/child/runtime', { params: { runtimeContext: JSON.stringify(runtimeContext) } }])
})
test('field effects and datasource calls keep composition source in authoritative protocol context', () => {
  const { calls, request } = harness()
  const { uiEventBindingApi, uiExtensionRuntimeApi } = createUiConfigApi(request)
  uiEventBindingApi.execute('FIELD_CHANGE', { configType: 'FORM', configId: 'child', configHash: 'hash', compositionContext: source, runtimeContext: { taskId: 'task' }, input: { form: { name: 'draft' } } })
  uiExtensionRuntimeApi.execute({ ownerType: 'FORM', ownerId: 'child', configHash: 'hash', compositionContext: source })
  for (const call of calls) {
    assert.deepEqual(call.args[1].runtimeContext.compositionContext, source)
    assert.equal('compositionContext' in call.args[1], false)
    assert.equal(call.args[1].configHash, 'hash')
  }
  assert.equal(calls[0].args[1].runtimeContext.taskId, 'task')
  assert.deepEqual(calls[0].args[1].input.form, { name: 'draft' })
})
test('data detail and delete preserve composition scope in runtimeContext query', () => {
  const { calls, request } = harness()
  const { entityDataApi } = createEntityApi(request)
  entityDataApi.getDetail('child', 'record', 'items', 'child-form', { configHash: 'list-hash', compositionContext: source }, { configHash: 'form-hash', runtimeContext: { taskId: 'task' } })
  entityDataApi.delete('child', 'record', 'items', { configHash: 'list-hash', compositionContext: source })
  assert.equal(calls[0].args[2].params.formConfigHash, 'form-hash')
  assert.equal(calls[0].args[2].params.runtimeContext.taskId, 'task')
  for (const call of calls) assert.deepEqual(call.args[2].params.runtimeContext.compositionContext, source)
})
test('related lists and extension actions keep the same source path', () => {
  const { calls, request } = harness()
  createEntityListRuntimeApi(request).getSchema('child', 'items', 'EMBEDDED', { compositionContext: source })
  createUiCompositionRuntimeApi(request).uiCompositionRuntimeApi.executeAction({ compositionContext: source, action: 'CUSTOM_VALIDATE', targetRecordIds: ['child-1'], operationId: 'validate-1' })
  assert.deepEqual(calls[0].args[1].params.compositionContext, source)
  assert.deepEqual(calls[1].args[1].compositionContext, source)
  assert.equal(calls[1].args[2].headers['X-Trace-Id'], 'validate-1')
})
