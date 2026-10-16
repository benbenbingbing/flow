import assert from 'node:assert/strict'
import { createFlowConditionConfig, createFlowConditionGroup } from '@flow/workflow-core/utils/flowConditionGroups'
import { buildFormNodePayload, extractFormNodeComponentConfig } from '@flow/workflow-core/form-node-property-schema'
import { isFormNodeVisible, createFormNodeVisibility } from '@flow/workflow-core/form-runtime/nodeProjection'
import { resolveRuntimeFormTabLayout } from '@flow/workflow-core/form-runtime/runtimeFormTabs'
import { resolveCrossFieldRuntimeState } from '@flow/workflow-core/form-cross-field-runtime'

// 同一属性经过设计器序列化、服务端文档形态和回填后，显隐条件必须保持一致。
for (const nodeType of ['SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE', 'TEXT', 'ACTION_SLOT', 'SUB_FORM', 'REPEATER']) {
  const config = { modeAccess: { edit: { visible: false } }, linkageRules: { visibilityRule: "status == 'ACTIVE'" } }
  const payload = buildFormNodePayload({ id: 'group', nodeType, nodeKey: 'group' }, { componentProps: config })
  const node = { ...payload, props: undefined, propsDocument: JSON.stringify(payload.props) }
  const reloaded = extractFormNodeComponentConfig(nodeType, payload.props)
  assert.deepEqual(reloaded.modeAccess, config.modeAccess)
  assert.deepEqual(reloaded.linkageRules, config.linkageRules)
  assert.equal(isFormNodeVisible(node, { mode: 'edit', data: { status: 'ACTIVE' } }), false, nodeType)
  assert.equal(isFormNodeVisible(node, { mode: 'create', data: { status: 'DRAFT' } }), false, nodeType)
  for (const mode of ['create', 'view', 'approve']) assert.equal(isFormNodeVisible(node, { mode, data: { status: 'ACTIVE' } }), true, nodeType)
  assert.equal(isFormNodeVisible({ nodeType, props: {} }), true, '旧节点缺省保持显示')
}
const nodes = [
  { id: 'tabs', nodeType: 'TAB_SET', props: { modeAccess: { view: { visible: false } } } },
  { id: 'one', parentId: 'tabs', nodeType: 'TAB', nodeKey: 'one', props: { linkageRules: { visibilityRule: "status == 'ACTIVE'" } } },
  { id: 'two', parentId: 'tabs', nodeType: 'TAB', props: {} },
  { id: 'field', parentId: 'one', nodeType: 'FIELD', bindingRef: 'name', props: {} }
]
assert.deepEqual(resolveRuntimeFormTabLayout({ nodes }, { mode: 'edit', data: { status: 'DRAFT' } }).tabs.map(n => n.id), ['two'])
const hidden = resolveRuntimeFormTabLayout({ nodes }, { mode: 'view', data: { status: 'ACTIVE' } })
assert.deepEqual(hidden.tabs, [])
assert.deepEqual(hidden.liftedRootNodeIds, ['tabs'])
assert.equal(createFormNodeVisibility(nodes, { mode: 'edit', data: { status: 'DRAFT' } })(nodes[3]), false)
assert.equal(resolveCrossFieldRuntimeState({ id: 'field', fieldCode: 'name' }, { form: { nodes }, mode: 'edit', record: { status: 'DRAFT' } }).visible, false)
assert.equal(resolveCrossFieldRuntimeState({ id: 'field', fieldCode: 'name' }, { form: { nodes }, mode: 'edit', record: { status: 'ACTIVE' } }).visible, true)
assert.equal(isFormNodeVisible({ nodeType: 'TAB', props: { modeAccess: { edit: 'HIDDEN' } } }, { mode: 'edit' }), false)

const condition = createFlowConditionConfig(createFlowConditionGroup('AND', [{ type: 'CONDITION', property: 'status', operator: '==', value: 'ACTIVE' }]))
const conditionalNode = { nodeType: 'SECTION', props: { linkageRules: { visibilityConditionConfig: condition } } }
assert.equal(isFormNodeVisible(conditionalNode, { data: { status: 'ACTIVE' } }), true)
assert.equal(isFormNodeVisible(conditionalNode, { data: { status: 'DRAFT' } }), false)
console.log('form-node-visibility: roundtrip, all modes, structured conditions, parent inheritance and validation passed')
