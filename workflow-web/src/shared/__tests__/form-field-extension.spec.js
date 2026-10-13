import assert from 'node:assert/strict'
import { applyRuntimeFieldDefaults } from '../../../../packages/workflow-core/src/shared/form-runtime/index.js'
import { resolveRuntimeNodeField } from '../../../../packages/workflow-core/src/shared/form-runtime/nodeProjection.js'
import { projectRuntimeFields, resolveComponentFormPatch, validateRuntimeFields } from '../../../../packages/workflow-core/src/shared/form-runtime/formModel.js'

// 自定义组件只有一个身份入口；投影保留版本、节点及配置，不需要类型标记。
const componentNode = { id: 'customer-node', nodeKey: 'customer', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'customerId', componentVersion: 3, snapshotVersion: 2, props: { fieldCode: 'customerId', componentType: 'project_customer', componentProps: { multiple: false } } }
const projected = resolveRuntimeNodeField(componentNode)
assert.equal(projected.componentType, 'project_customer')
assert.equal(projected.componentVersion, 3)
assert.equal(projected.node, componentNode)
assert.deepEqual(projected.componentProps, { multiple: false })

const summary = resolveRuntimeNodeField({ id: 'summary', nodeKey: 'summary', nodeType: 'FIELD', bindingType: 'NONE', props: { fieldCode: 'summary', componentType: 'project_summary', required: true } })
assert.equal(summary.isDisplayOnly, true)
assert.equal(resolveRuntimeNodeField({ ...summary.node, props: { fieldCode: 'summary', componentType: 'project_summary' } }, [{ fieldCode: 'summary', isRequired: 1 }]).isRequired, 0, '无绑定组件不继承同名实体字段规则')
assert.deepEqual(applyRuntimeFieldDefaults({}, { nodes: [{ nodeType: 'FIELD', bindingType: 'NONE', props: { fieldCode: 'summary', defaultValue: 'fake' } }] }), {})
assert.deepEqual(await validateRuntimeFields([summary], {}, { mode: 'edit' }), {})

// 嵌套行只能修改本行声明的可编辑字段，不扩散到父表，也不能绕过字段状态。
const form = { nodes: [
  { id: 'parent', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'parentValue' } },
  { id: 'rows', nodeType: 'REPEATER', props: { fieldCode: 'rows' } },
  { id: 'child', parentId: 'rows', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'amount' } },
  { id: 'locked', parentId: 'rows', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'locked', readonly: true } }
] }
const rowFields = projectRuntimeFields(form, [], { rootParentId: 'rows' }).fields
const row = { amount: 1, locked: 5 }
const changes = resolveComponentFormPatch(JSON.parse('{"amount":2,"locked":8,"parentValue":9,"__proto__":{}}'), rowFields, row, { form, rootParentId: 'rows', mode: 'edit' })
assert.deepEqual(changes.map(({ code, value }) => [code, value]), [['amount', 2]])
assert.deepEqual(row, { amount: 1, locked: 5 }, '补丁解析不能直接修改外部模型')
assert.deepEqual(resolveComponentFormPatch({ amount: 2 }, rowFields, row, { mode: 'view' }), [])
assert.deepEqual(resolveComponentFormPatch({ amount: 2 }, rowFields, row, { mode: 'edit' }, { disabled: { amount: true } }), [])
assert.deepEqual(resolveComponentFormPatch({ amount: 1 }, rowFields, row, { mode: 'edit' }), [])
console.log('unified form component projection and patch tests passed')
