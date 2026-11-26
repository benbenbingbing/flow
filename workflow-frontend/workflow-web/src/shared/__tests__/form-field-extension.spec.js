import assert from 'node:assert/strict'
import { applyRuntimeFieldDefaults, filterRuntimeFormSubmissionData } from '../../../../packages/workflow-core/src/shared/form-runtime/index.js'
import { resolveRuntimeNodeField } from '../../../../packages/workflow-core/src/shared/form-runtime/nodeProjection.js'
import { projectRuntimeFields, resolveComponentFormPatch, runtimeFieldState, validateRuntimeFields } from '../../../../packages/workflow-core/src/shared/form-runtime/formModel.js'

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

// 嵌套行可回填本行声明的只读字段。输入控件、隐藏字段和作用域继续各自检查。
const form = { nodes: [
  { id: 'parent', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'parentValue' } },
  { id: 'rows', nodeType: 'REPEATER', props: { fieldCode: 'rows' } },
  { id: 'child', parentId: 'rows', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'amount' } },
  { id: 'locked', parentId: 'rows', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'locked', readonly: true } },
  { id: 'mode-locked', parentId: 'rows', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'modeLocked' }, rules: { extension: { modes: { edit: { editable: false } } } } },
  { id: 'hidden', parentId: 'rows', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'hidden', hidden: true } },
  { id: 'disabled', parentId: 'rows', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', props: { fieldCode: 'disabled', disabled: true } }
] }
const rowFields = projectRuntimeFields(form, [], { rootParentId: 'rows' }).fields
const row = { amount: 1, locked: 5 }
const options = { form, rootParentId: 'rows', mode: 'edit', record: row }
const patch = JSON.parse('{"amount":2,"locked":8,"modeLocked":0,"parentValue":9,"hidden":10,"disabled":11,"__proto__":{}}')
const changes = resolveComponentFormPatch(patch, rowFields, row, options)
assert.deepEqual(changes.map(({ code, value }) => [code, value]), [['amount', 2], ['locked', 8], ['modeLocked', 0]])
assert.deepEqual(row, { amount: 1, locked: 5 }, '补丁解析不能直接修改外部模型')
assert.equal(runtimeFieldState(rowFields.find(field => field.fieldCode === 'locked'), options).editable, false)
assert.equal(runtimeFieldState(rowFields.find(field => field.fieldCode === 'modeLocked'), options).editable, false)
assert.deepEqual(resolveComponentFormPatch(patch, rowFields, row, { ...options, mode: 'view' }), [])
assert.deepEqual(resolveComponentFormPatch(patch, rowFields, row, { ...options, form: { ...form, isReadonly: 1 } }), [])
assert.deepEqual(resolveComponentFormPatch({ locked: 8 }, rowFields, row, options, { disabled: { locked: true } }), [])
assert.deepEqual(resolveComponentFormPatch({ amount: 1 }, rowFields, row, options), [])
const submittedRow = { ...row, ...Object.fromEntries(changes.map(({ code, value }) => [code, value])) }
assert.deepEqual(filterRuntimeFormSubmissionData({ rows: [submittedRow] }, form, [{ fieldCode: 'rows', fieldType: 'REPEATER' }]), {
  rows: [{ amount: 2, locked: 8, modeLocked: 0 }]
})
console.log('unified form component projection and patch tests passed')
