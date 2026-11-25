import test from 'node:test'
import assert from 'node:assert/strict'
import { mapPageParameters, resolvePageParameters } from '../../../../packages/workflow-core/src/shared/page-parameters.js'
import { createFormActionRuntime } from '../../../../packages/workflow-core/src/shared/form-action-runtime.js'
import { applyRuntimeFieldEffects, buildFieldEventPayload } from '../../../../packages/workflow-core/src/shared/form-runtime/fieldEvents.js'

test('订单列表传入的客户 ID 沿用页面参数契约，表单按钮和字段事件可映射同一参数', () => {
  const parameters = mapPageParameters([{ parameter: 'customerId', sourceType: 'FIELD', sourceField: 'customer_id' }],
    { data: { id: 'order-1', data: { customer_id: '0007' } } })
  const viewConfig = { inputParameterSchema: { properties: { customerId: { type: 'string' } }, required: ['customerId'] } }
  const params = resolvePageParameters(viewConfig, parameters)
  const form = { id: 'form-1', configHash: 'published-hash' }
  const formData = { phone: '' }
  const context = { form, mode: 'create', params, formData, getFormData: () => formData }
  const runtime = createFormActionRuntime({})
  const request = runtime.buildCustomFormActionExecutionPayload({ key: 'lookup', ownerFormId: 'form-1' }, form, context, 'request-1')
  assert.deepEqual(request.input.params, { customerId: '0007' })
  assert.deepEqual(request.context, {})
  const fieldRequest = buildFieldEventPayload({ fieldCode: 'customer_id' }, '0010', context)
  assert.deepEqual(fieldRequest.input.params, { customerId: '0007' })
  assert.equal(fieldRequest.input.value, '0010')
})

const effect = (overwrite = 'IF_EMPTY') => ({
  type: 'FIELD_MAPPING',
  sourceField: 'customer_id',
  sourceValue: '0007',
  data: { form: { phone: '010-1234' } },
  mappings: [{ targetPath: 'form.phone', overwrite, clearOnEmpty: false }]
})

test('查询结果只填空值，无结果或来源变化时保留草稿', async () => {
  const draft = { customer_id: '0007', phone: '人工填写' }
  const handlers = { getRecord: () => draft, setField: (key, value) => { draft[key] = value } }
  await applyRuntimeFieldEffects({ effects: [effect()] }, handlers)
  assert.equal(draft.phone, '人工填写')
  draft.phone = ''
  await applyRuntimeFieldEffects({ data: null, effects: [] }, handlers)
  assert.equal(draft.phone, '')
  await applyRuntimeFieldEffects({ effects: [effect()] }, handlers)
  assert.equal(draft.phone, '010-1234')
  draft.customer_id = '0010'
  await applyRuntimeFieldEffects({ effects: [effect('ALWAYS')] }, handlers)
  assert.equal(draft.phone, '010-1234')
})

test('等待覆盖确认时切换了客户，确认后也不会回填旧查询结果', async () => {
  const draft = { customer_id: '0007', phone: '人工填写' }
  await applyRuntimeFieldEffects({ effects: [effect('CONFIRM')] }, {
    getRecord: () => draft,
    setField: () => assert.fail('旧查询结果不能写回'),
    async confirmOverwrite() {
      draft.customer_id = '0010'
      return true
    }
  })
})
