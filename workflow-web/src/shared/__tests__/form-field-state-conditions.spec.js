import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { useFieldStateConditions } from '../../composables/useFieldStateConditions.js'
import {
  getFieldStateConditionError,
  readFieldStateConditions,
  updateFieldStateCondition
} from '../form-field-state-conditions.js'
import { LinkageEngine } from '../../utils/linkageEngine.js'
import { createFlowConditionConfig, createFlowConditionGroup } from '../../utils/flowConditionGroups.js'
import { buildFormNodePayload } from '../form-node-property-schema.js'

const condition = (property, operator, value) => ({ type: 'CONDITION', property, operator, value })
const root = createFlowConditionGroup('AND', [
  condition('approved', '==', 'true'),
  createFlowConditionGroup('OR', [condition('amount', '>=', '10'), condition('name', 'notEmpty', '')])
])
const fieldType = code => code === 'approved' ? 'boolean' : code === 'amount' ? 'number' : 'string'
const otherRules = {
  valueMapping: { sourceField: 'amount', rules: [{ sourceValue: '1', targetValue: 'a' }] },
  valueApi: { url: '/legacy' },
  optionsLinkage: { dependsOn: 'name', filterRules: { a: ['1'] } },
  calculationFormula: '${amount * 2}', calculationPrecision: 2, calculationEditable: true,
  attachmentItemRequiredRules: { version: 1, items: [{ itemKey: 'file', requiredConditionConfig: createFlowConditionConfig(root) }] }
}
const field = {
  id: 'a', nodeType: 'FIELD', fieldId: 'entity-a', fieldCode: 'a', fieldType: 'STRING',
  componentProps: JSON.stringify({ showWordLimit: false, linkageRules: otherRules })
}

const states = readFieldStateConditions(field)
states.visibility.enabled = true
states.visibility.root = root
updateFieldStateCondition(field, 'visibility', states.visibility, fieldType)
const saved = JSON.parse(field.componentProps)
assert.equal(saved.showWordLimit, false)
for (const [key, value] of Object.entries(otherRules)) assert.deepEqual(saved.linkageRules[key], value)
assert.equal(LinkageEngine.shouldShowField(field, { approved: true, amount: 10 }), true)
assert.equal(LinkageEngine.shouldShowField(field, { approved: false, amount: 10 }), false)
assert.equal(LinkageEngine.shouldShowField(field, { approved: true, amount: 1, name: 'a' }), true)

// 节点载荷沿用现有结构；重新打开发布/草稿投影后条件仍可正确读取。
const payload = buildFormNodePayload(field, { componentProps: saved })
assert.deepEqual(payload.props.componentProps, saved)
assert.deepEqual(readFieldStateConditions({ componentProps: payload.props.componentProps }).visibility.root, root)

states.disabled.enabled = true
states.disabled.root = createFlowConditionGroup('AND', [condition('amount', '>=', '100')])
updateFieldStateCondition(field, 'disabled', states.disabled, fieldType)
states.required.enabled = true
states.required.root = createFlowConditionGroup('AND', [condition('name', 'empty', '')])
updateFieldStateCondition(field, 'required', states.required, fieldType)
assert.equal(LinkageEngine.shouldDisableField(field, { amount: 100 }), true)
assert.equal(LinkageEngine.shouldRequireField(field, { name: '' }), true)
assert.equal(LinkageEngine.shouldRequireField({ ...field, isRequired: 1 }, { name: 'a' }), true)
assert.equal(getFieldStateConditionError(field), '')

states.visibility.enabled = false
updateFieldStateCondition(field, 'visibility', states.visibility, fieldType)
const withoutVisibility = LinkageEngine.getFieldLinkageRules(field)
assert.equal('visibilityRule' in withoutVisibility, false)
assert.equal('visibilityConditionConfig' in withoutVisibility, false)
assert.equal('visibilityRule' in field, false)
assert.equal(LinkageEngine.shouldShowField(field, {}), true)
assert.equal(LinkageEngine.shouldDisableField(field, { amount: 100 }), true)

// 启用后尚未输入、删除条件行、嵌套组留空，都不能静默丢弃后通过保存。
states.visibility.enabled = true
states.visibility.root = createFlowConditionGroup()
updateFieldStateCondition(field, 'visibility', states.visibility, fieldType)
assert.match(getFieldStateConditionError(field), /条件显示.*补全/)
assert.equal('visibilityRule' in LinkageEngine.getFieldLinkageRules(field), false)
assert.equal(readFieldStateConditions(field).visibility.parseWarning, '')
// 旧表达式无法可视化时，不因移动入口或修改另一类条件而被改写。
const legacy = { componentProps: { linkageRules: { ...otherRules, visibilityRule: '${customCheck(a)}' } } }
const legacyStates = readFieldStateConditions(legacy)
assert.ok(legacyStates.visibility.parseWarning)
legacyStates.disabled.enabled = true
legacyStates.disabled.root = root
updateFieldStateCondition(legacy, 'disabled', legacyStates.disabled, fieldType)
assert.equal(LinkageEngine.getFieldLinkageRules(legacy).visibilityRule, '${customCheck(a)}')
assert.equal(getFieldStateConditionError(legacy), '')
legacyStates.visibility.enabled = false
updateFieldStateCondition(legacy, 'visibility', legacyStates.visibility, fieldType)
legacyStates.visibility.enabled = true
updateFieldStateCondition(legacy, 'visibility', legacyStates.visibility, fieldType)
assert.equal(LinkageEngine.getFieldLinkageRules(legacy).visibilityRule, '${customCheck(a)}')

const selected = ref(field)
const second = { id: 'b', fieldCode: 'b', componentProps: '' }
const scope = effectScope()
const editor = scope.run(() => useFieldStateConditions(() => selected.value, fieldType))
try {
  const draftRoot = editor.states.value.visibility.root
  draftRoot.children[0].property = 'name'
  editor.persist('visibility')
  await nextTick()
  assert.equal(editor.states.value.visibility.root, draftRoot, '本地输入不能重建条件组')
  selected.value.componentProps = JSON.stringify({ ...JSON.parse(selected.value.componentProps), placeholder: 'changed' })
  await nextTick()
  assert.equal(editor.states.value.visibility.root, draftRoot, '其他配置变化不能重建条件组')

  const first = selected.value
  selected.value = second
  await nextTick()
  assert.equal(editor.states.value.visibility.enabled, false)
  selected.value = first
  await nextTick()
  assert.equal(editor.states.value.visibility.root.children[0].property, 'name')
  assert.match(getFieldStateConditionError(selected.value), /补全/, '切换节点后保留未完成编辑并继续阻止保存')
  editor.states.value.visibility.root.children[0].value = 'ready'
  editor.persist('visibility')
  await nextTick()
  assert.equal(getFieldStateConditionError(selected.value), '')

  // 服务端回填/冲突刷新也要同步到内联编辑器。
  const external = readFieldStateConditions(first).visibility
  external.enabled = false
  updateFieldStateCondition(first, 'visibility', external, fieldType)
  await nextTick()
  assert.equal(editor.states.value.visibility.enabled, false)
  selected.value = legacy
  await nextTick()
  editor.resetCondition('visibility')
  assert.equal(editor.states.value.visibility.parseWarning, '')
  assert.match(getFieldStateConditionError(selected.value), /条件显示/)
  editor.setEnabled('visibility', false)
  assert.equal(getFieldStateConditionError(selected.value), '')
} finally {
  scope.stop()
}

console.log('field state conditions: merge, runtime, legacy, validation and editor draft lifecycle passed')
