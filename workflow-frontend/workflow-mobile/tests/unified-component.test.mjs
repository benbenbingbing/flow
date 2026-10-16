import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { normalizeRuntimeNodes, isFormNodeVisible } from '../../packages/workflow-core/src/shared/form-runtime/nodeProjection.js'
import { projectRuntimeFields, runtimeFieldState } from '../../packages/workflow-core/src/shared/form-runtime/formModel.js'
import { registerMobileExtension, getMobileFieldExtension } from '../../packages/workflow-mobile-ui/src/fields/registry.js'

const source = await readFile(new URL('../../packages/workflow-mobile-ui/src/form/mobileFormTree.js', import.meta.url), 'utf8')
const { buildMobileFormTree } = new Function('normalizeRuntimeNodes', 'isFormNodeVisible', 'runtimeFieldState', source.replace(/^import .*\n/gm, '').replace(/export function/g, 'function') + '\nreturn { buildMobileFormTree }')(normalizeRuntimeNodes, isFormNodeVisible, runtimeFieldState)

test('移动组件与普通字段共用树节点和绑定值，摘要也不再产生独立扩展分支', () => {
  const form = { nodes: [
    { id: 'amount', nodeKey: 'amount', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'amount', componentVersion: 2, props: { fieldCode: 'amount', componentType: 'test_amount' } },
    { id: 'summary', nodeKey: 'summary', nodeType: 'FIELD', bindingType: 'NONE', props: { fieldCode: 'summary', componentType: 'test_summary' } }
  ] }
  const { fields } = projectRuntimeFields(form)
  const tree = buildMobileFormTree(form, fields, { mode: 'edit', form, record: { amount: 3 } })
  assert.deepEqual(tree.map(item => item.kind), ['field', 'field'])
  assert.deepEqual(tree.map(item => item.field.componentType), ['test_amount', 'test_summary'])
  assert.equal(tree[0].field.componentVersion, 2)
  assert.equal(tree[1].field.isDisplayOnly, true)
})

test('移动注册仅接受统一 FIELD 协议并按版本和支持的绑定匹配', () => {
  assert.throws(() => registerMobileExtension({ type: 'NODE', name: 'removed_node', component: {} }), /注册无效/)
  registerMobileExtension({ name: 'test_bound', version: 2, component: {}, nodeTypes: ['FIELD'], supportedBindings: ['ENTITY_FIELD'], supportedFieldTypes: ['TEXT'], editable: true, validate: true })
  const field = { componentType: 'test_bound', componentVersion: 2, fieldType: 'TEXT', bindingType: 'ENTITY_FIELD' }
  assert.equal(getMobileFieldExtension(field).version, 2)
  assert.equal(getMobileFieldExtension({ ...field, componentVersion: 1 }), undefined)
  assert.equal(getMobileFieldExtension({ ...field, bindingType: 'NONE' }), undefined)
  assert.equal(getMobileFieldExtension({ ...field, nodeType: 'REPEATER' }), undefined)
})
