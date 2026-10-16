import test from 'node:test'
import assert from 'node:assert/strict'
import { createFormDataSourceRuntime } from '../dist/shared/form-runtime/dataSourceRuntime.js'

test('历史发布的数据源绑定完整传递服务和操作，新接口不携带旧路由', async () => {
  const requests = []
  const form = { id: 'form-1', dataSourceBindingsDocument: JSON.stringify({
    FORM_INIT: { serviceId: 'old-service', operationCode: 'initialize' },
    AFTER_LOAD: { extensionId: 'new-interface', serviceId: 'old-service', operationCode: 'ignored-method' }
  }) }
  const record = { deptId: '', userId: 'selected-user' }
  const runtime = createFormDataSourceRuntime({ getForm: () => form, getRecord: () => record,
    executeDataSource: async request => { requests.push(request); return {} } })
  // 通过 owner 执行会先规范化一次，再经过 execute；兼容坐标必须跨两次规范化保留。
  await runtime.executeOwnerUsage(form, 'FORM_INIT')
  await runtime.executeOwnerUsage(form, 'AFTER_LOAD')
  assert.equal(requests[0].extensionId, 'old-service')
  assert.equal(requests[0].legacyOperationCode, 'initialize')
  assert.deepEqual(requests[0].input.formData, record)
  assert.equal(requests[1].extensionId, 'new-interface')
  assert.equal('legacyOperationCode' in requests[1], false)
  assert.equal('operationCode' in requests[1], false)
})
