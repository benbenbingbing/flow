import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { getFieldLinkageDraftError, useFieldValueLinkage } from '../../composables/useFieldValueLinkage.js'
import { getAttachmentConditionError, patchFieldLinkageRules, readFieldValueLinkage, updateFieldValueLinkage } from '../form-field-linkage.js'
import { LinkageEngine } from '../../utils/linkageEngine.js'
import { createFlowConditionConfig, createFlowConditionGroup } from '../../utils/flowConditionGroups.js'
import { readFieldStateConditions, updateFieldStateCondition } from '../form-field-state-conditions.js'
import { buildFormNodePayload } from '../form-node-property-schema.js'

const condition = createFlowConditionConfig(createFlowConditionGroup('AND', [{ type: 'CONDITION', property: 'amount', operator: '>', value: '0' }]))
// 所有历史存储形态均忽略废弃接口，仍保留可执行的公式与映射，且读取不修改快照。
const storedRules = { valueApi: { url: '/legacy' }, valueFormula: '${amount} * 2' }
for (const legacyField of [
  { ...storedRules },
  { linkageRules: storedRules },
  { componentProps: { linkageRules: storedRules } },
  { componentProps: JSON.stringify({ linkageRules: storedRules }) }
]) {
  const original = JSON.stringify(legacyField)
  assert.deepEqual(LinkageEngine.getFieldLinkageRules(legacyField), { valueFormula: '${amount} * 2' })
  assert.equal(JSON.stringify(legacyField), original)
}
const untouched = {
  visibilityConditionConfig: condition,
  visibilityRule: '${amount > 0}',
  attachmentItemRequiredRules: { version: 1, items: [{ itemKey: 'receipt', requiredConditionConfig: condition }] }
}
const field = {
  id: 'total', nodeType: 'FIELD', fieldId: 'total', fieldCode: 'total', fieldType: 'DECIMAL',
  valueApi: { url: '/legacy-root' },
  componentProps: JSON.stringify({ placeholder: 'total', linkageRules: {
    ...untouched, valueApi: { url: '/legacy' }, calculationFormula: '${amount} * 2', calculationPrecision: 0, calculationEditable: true
  } })
}
const original = JSON.stringify(field)
const selected = ref(field)
const disabled = ref(false)
const scope = effectScope()
const editor = scope.run(() => useFieldValueLinkage(() => selected.value, () => disabled.value))
try {
  assert.equal(JSON.stringify(field), original, '打开内联编辑器不修改数据')
  assert.equal(editor.model.value.sourceType, 'formula')
  assert.equal(editor.model.value.formula, '${amount} * 2')
  editor.model.value.formula = '${amount} * 3'
  editor.persist('value')
  await nextTick()
  let saved = JSON.parse(selected.value.componentProps)
  assert.equal(saved.linkageRules.calculationFormula, '${amount} * 3')
  assert.equal(saved.linkageRules.calculationPrecision, 0)
  assert.equal(saved.linkageRules.calculationEditable, true)
  assert.equal(saved.placeholder, 'total')
  assert.equal(saved.linkageRules.valueApi, undefined, '显式编辑时清除废弃接口规则')
  assert.equal(selected.value.valueApi, undefined, '根属性副本也不能继续透传')
  for (const [key, value] of Object.entries(untouched)) assert.deepEqual(saved.linkageRules[key], value)
  assert.equal(LinkageEngine.processAllLinkages([selected.value], { amount: 4 }).values.total, 12)
  const payload = buildFormNodePayload(selected.value, { componentProps: saved })
  assert.equal(readFieldValueLinkage({ componentProps: payload.props.componentProps }).formula, '${amount} * 3')

  // 选项编辑和基础页的条件编辑不能覆盖旧公式，输入中间态跨节点保留。
  editor.model.value.optionsEnabled = true
  editor.model.value.dependsOn = 'kind'
  editor.model.value.filters = [{ dependValue: 'a', allowedOptions: [0, 'b'] }, { dependValue: 'a', allowedOptions: [] }]
  editor.persist('options')
  const current = selected.value
  const draft = editor.model.value
  assert.match(getFieldLinkageDraftError(current), /依赖值不能重复/)
  const required = readFieldStateConditions(current).required
  required.enabled = true
  required.root = condition.root
  updateFieldStateCondition(current, 'required', required, () => 'number')
  await nextTick()
  assert.equal(editor.model.value, draft, '修改条件状态不打断联动输入')
  selected.value = { fieldCode: 'other', componentProps: '{}' }
  await nextTick()
  assert.equal(editor.model.value.valueEnabled, false)
  selected.value = current
  await nextTick()
  assert.equal(editor.model.value.filters.length, 2, '重复的未完成行不能被存储对象合并丢失')
  editor.model.value.filters[1].dependValue = 'b'
  editor.persist('options')
  assert.equal(getFieldLinkageDraftError(current), '')
  saved = JSON.parse(current.componentProps)
  assert.equal(saved.linkageRules.calculationFormula, '${amount} * 3')
  assert.deepEqual(saved.linkageRules.optionsLinkage.filterRules, { a: [0, 'b'], b: [] })
  assert.deepEqual(LinkageEngine.getLinkedOptions([{ value: 0 }, { value: 'b' }, { value: 'c' }], saved.linkageRules.optionsLinkage, { kind: 'a' }), [{ value: 0 }, { value: 'b' }])

  editor.model.value.valueEnabled = false
  editor.persist('value')
  saved = JSON.parse(current.componentProps)
  assert.equal('calculationFormula' in saved.linkageRules, false)
  assert.ok(saved.linkageRules.optionsLinkage)
  assert.ok(saved.linkageRules.requiredConditionConfig)
  assert.equal(saved.linkageRules.valueApi, undefined)

  // 外部回填会重建输入，禁用组件不会改写节点数据。
  patchFieldLinkageRules(current, ['valueFormula'], { valueFormula: '${amount} + 1' })
  await nextTick()
  assert.equal(editor.model.value.formula, '${amount} + 1')
  disabled.value = true
  const beforeDisabled = JSON.stringify(current)
  editor.model.value.formula = '99'
  editor.persist('value')
  assert.equal(JSON.stringify(current), beforeDisabled)
} finally { scope.stop() }

// 旧根属性映射也可读取；切换公式后清除原映射，确保运行时使用新配置。
const rootMapping = { fieldCode: 'mapped', valueMapping: { sourceField: 'source', rules: [{ sourceValue: 'a', targetValue: 'b' }] } }
const model = readFieldValueLinkage(rootMapping)
assert.equal(model.sourceField, 'source')
model.sourceType = 'formula'
model.formula = 'amount * 2'
updateFieldValueLinkage(rootMapping, model, 'value')
assert.equal(rootMapping.valueFormula, '${amount} * 2')
assert.equal('valueMapping' in LinkageEngine.getFieldLinkageRules(rootMapping), false)
assert.equal(LinkageEngine.processAllLinkages([rootMapping], { amount: 5, source: 'a' }).values.mapped, 10)

// 附件条件同时维护兼容副本，未完成条件阻止保存且不影响其他联动。
const file = { componentProps: JSON.stringify({ linkageRules: { valueFormula: '1' }, attachmentItemRequiredRules: untouched.attachmentItemRequiredRules }) }
const attachments = [{ itemKey: 'receipt', itemName: '凭证' }]
assert.equal(getAttachmentConditionError(file, attachments), '')
patchFieldLinkageRules(file, ['attachmentItemRequiredRules'], { attachmentItemRequiredRules: { version: 1, items: [{ itemKey: 'receipt', requiredConditionConfig: createFlowConditionConfig(createFlowConditionGroup()) }] } })
assert.match(getAttachmentConditionError(file, attachments), /凭证.*尚未填写完整/)
patchFieldLinkageRules(file, ['attachmentItemRequiredRules'], {})
assert.equal('attachmentItemRequiredRules' in JSON.parse(file.componentProps), false)
assert.equal('attachmentItemRequiredRules' in LinkageEngine.getFieldLinkageRules(file), false)
assert.equal(LinkageEngine.getFieldLinkageRules(file).valueFormula, '1')
console.log('inline field linkage: legacy, runtime, persistence, draft lifecycle and attachment conditions passed')
