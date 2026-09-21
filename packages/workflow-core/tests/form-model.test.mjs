import test from 'node:test'
import assert from 'node:assert/strict'
import { projectRuntimeFields, validateRuntimeFields } from '../dist/shared/form-runtime/formModel.js'
import { buildRuntimeFieldRules } from '../dist/shared/config-runtime/index.js'
import { applyRuntimeFieldEffects } from '../dist/shared/form-runtime/fieldEvents.js'
import { formatReadonlyValue } from '../dist/views/entity/components/approval/entityApprovalDisplay.js'
const node = (id, type, parentId = '', props = {}) => ({ id, nodeKey: id, nodeType: type, parentId, props })

test('嵌套分组保留节点身份，子表字段与父表记录隔离', () => {
  const form = { nodes: [node('tabs', 'TAB_SET'), node('base', 'TAB', 'tabs'), node('amount', 'FIELD', 'base', { fieldCode: 'amount', componentType: 'number', required: true }), node('rows', 'REPEATER', 'base', { fieldCode: 'rows' }), node('child', 'FIELD', 'rows', { fieldCode: 'child', componentType: 'input' })] }
  const root = projectRuntimeFields(form, [])
  assert.deepEqual(root.fields.map(field => field.fieldCode), ['amount', 'rows'])
  const child = root.fields.find(field => field.fieldCode === 'rows')
  assert.equal(child.runtimeRootParentId, 'rows'); assert.deepEqual(child.runtimeNodes.map(item => item.id), ['child'])
  const nested = projectRuntimeFields({ nodes: child.runtimeNodes }, [], { rootParentId: child.runtimeRootParentId })
  assert.deepEqual(nested.fields.map(field => field.fieldCode), ['child'])
})

test('折叠不影响校验，隐藏和只读字段不参与必填校验', async () => {
  const fields = [{ fieldCode: 'visible', fieldName: '必填项', isRequired: 1, componentType: 'input' }, { fieldCode: 'hidden', isRequired: 1, isHidden: 1 }, { fieldCode: 'readonly', isRequired: 1, isReadonly: 1 }]
  const errors = await validateRuntimeFields(fields, {}, { form: { fields }, mode: 'approve' })
  assert.deepEqual(Object.keys(errors), ['visible']); assert.ok(buildRuntimeFieldRules(fields[0], true, '必填项').length)
  assert.deepEqual(await validateRuntimeFields(fields, { visible: '已填写' }, { form: { fields }, mode: 'approve' }), {})
})

test('只读 0 和 false 保持真实值，不变成占位符', () => { assert.equal(formatReadonlyValue(0), '0'); assert.equal(formatReadonlyValue(false), '否') })

test('回填遵循覆盖确认，旧异步结果与不安全路径不会落地', async () => {
  let record = { amount: 2 }, current = true
  await applyRuntimeFieldEffects({ effects: [{ type: 'FIELD_MAPPING', data: { form: { amount: 3 } }, mappings: [{ targetPath: 'form.amount', overwrite: 'CONFIRM' }] }] }, { getRecord: () => record, setField: (key, value) => { record[key] = value }, confirmOverwrite: async () => { current = false; return true }, isCurrent: () => current })
  assert.equal(record.amount, 2)
  await applyRuntimeFieldEffects({ data: JSON.parse('{"__proto__.polluted":true,"amount":0}') }, { getRecord: () => record, setField: (key, value) => { record[key] = value } })
  assert.equal(record.amount, 0); assert.equal({}.polluted, undefined)
})
