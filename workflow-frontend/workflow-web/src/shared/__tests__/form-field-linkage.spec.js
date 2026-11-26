import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { getFieldLinkageDraftError, useFieldValueLinkage } from '../../composables/useFieldValueLinkage.js'
import { getAttachmentConditionError, getFieldValueLinkageError, patchFieldLinkageRules, readFieldValueLinkage, updateFieldValueLinkage } from '../form-field-linkage.js'
import { applyInitialFormulaDefaults, LinkageEngine } from '@flow/workflow-core/utils/linkageEngine'
import { settleRuntimeLinkages } from '@flow/workflow-core/form-runtime/formModel'
import { createFlowConditionConfig, createFlowConditionGroup } from '@flow/workflow-core/utils/flowConditionGroups'
import { readFieldStateConditions, updateFieldStateCondition } from '../form-field-state-conditions.js'
import { buildFormNodePayload } from '@flow/workflow-core/form-node-property-schema'
import { changeFieldDefaultMode, readFieldDefaultFormula, readFieldDefaultMode, writeFieldDefaultFormula } from '../form-field-default.js'

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

// 历史初值可在新入口读取，编辑实时公式之后仍可按独立属性保存和执行。
const legacyFormula = 'IF(${status} == "delayed", ADD_DAYS(${start}, 7), ADD_DAYS(${start}, 3))'
const dateField = {
  id: 'due_date', nodeType: 'FIELD', fieldId: 'due_date', fieldCode: 'due_date', fieldType: 'DATE',
  componentProps: JSON.stringify({ linkageRules: { valueFormula: legacyFormula, valueFormulaMode: 'initial' } })
}
assert.equal(readFieldValueLinkage(dateField).valueEnabled, false)
assert.equal(readFieldDefaultMode(dateField), 'formula')
assert.equal(readFieldDefaultFormula(dateField), legacyFormula)
const dateModel = { ...readFieldValueLinkage(dateField), valueEnabled: true, sourceType: 'formula', formula: 'ADD_DAYS(start, 1)' }
updateFieldValueLinkage(dateField, dateModel, 'value')
const { props: dateProps } = buildFormNodePayload(dateField, { componentProps: JSON.parse(dateField.componentProps) })
assert.equal(dateProps.defaultFormula, legacyFormula)
assert.equal(dateProps.componentProps.linkageRules.valueFormulaMode, undefined)
const reloadedDate = { ...dateProps }
const formulaInput = { status: 'delayed', start: '2026-11-25', due_date: '' }
applyInitialFormulaDefaults([reloadedDate], formulaInput)
assert.equal(formulaInput.due_date, '2026-12-02')
assert.equal(LinkageEngine.processAllLinkages([reloadedDate], formulaInput).values.due_date, '2026-11-26')
changeFieldDefaultMode(reloadedDate, 'formula')
writeFieldDefaultFormula(reloadedDate, 'ADD_DAYS(${start}, 2)')
assert.equal(readFieldValueLinkage(reloadedDate).formula, 'ADD_DAYS(${start}, 1)')
changeFieldDefaultMode(reloadedDate, 'none')
assert.equal(readFieldDefaultFormula(reloadedDate), '')
assert.equal(readFieldValueLinkage(reloadedDate).valueEnabled, true)

// 兜底随节点保存和重载，精确规则优先，空值是否参与由配置决定。
const mappedField = { id: 'mapped', nodeType: 'FIELD', fieldId: 'mapped', fieldCode: 'mapped', fieldType: 'STRING' }
const mappedModel = readFieldValueLinkage(mappedField)
assert.equal(mappedModel.fallback, null)
Object.assign(mappedModel, {
  valueEnabled: true,
  sourceField: 'source',
  mappings: [{ sourceValue: '1', targetValue: 'A' }, { sourceValue: '2', targetValue: 'B' }],
  fallback: { targetValue: 'C', includeEmpty: false }
})
updateFieldValueLinkage(mappedField, mappedModel, 'value')
const mappedPayload = buildFormNodePayload(mappedField, { componentProps: JSON.parse(mappedField.componentProps) })
const reloadedField = { fieldCode: 'mapped', componentProps: JSON.stringify(mappedPayload.props.componentProps) }
const reloadedModel = readFieldValueLinkage(reloadedField)
assert.deepEqual(reloadedModel.fallback, mappedModel.fallback)
const mappedValues = (source) => LinkageEngine.processAllLinkages([reloadedField], { source }).values
for (const [source, target] of [[1, 'A'], ['2', 'B'], ['3', 'C'], ['其他', 'C'], [0, 'C'], [false, 'C']]) {
  assert.deepEqual(mappedValues(source), { mapped: target })
}
for (const empty of [undefined, null, '', '  ', []]) assert.deepEqual(mappedValues(empty), {})
const matched = settleRuntimeLinkages([reloadedField], { source: '1' }).record
assert.equal(settleRuntimeLinkages([reloadedField], { ...matched, source: '3' }).record.mapped, 'C')
assert.equal(settleRuntimeLinkages([reloadedField], { ...matched, source: '' }).record.mapped, 'A')

reloadedModel.fallback.includeEmpty = true
updateFieldValueLinkage(reloadedField, reloadedModel, 'value')
for (const empty of [undefined, null, '', '  ', []]) assert.deepEqual(mappedValues(empty), { mapped: 'C' })
reloadedModel.mappings.push({ sourceValue: '', targetValue: 'E' })
updateFieldValueLinkage(reloadedField, reloadedModel, 'value')
assert.deepEqual(mappedValues(''), { mapped: 'E' })
reloadedModel.fallback = null
updateFieldValueLinkage(reloadedField, reloadedModel, 'value')
assert.equal(Object.hasOwn(JSON.parse(reloadedField.componentProps).linkageRules.valueMapping, 'fallback'), false)
assert.deepEqual(mappedValues('3'), {})

reloadedModel.mappings = []
reloadedModel.fallback = { targetValue: '', includeEmpty: false }
assert.equal(getFieldValueLinkageError(reloadedModel), '')
updateFieldValueLinkage(reloadedField, reloadedModel, 'value')
assert.deepEqual(mappedValues('3'), { mapped: '' }, '可以把未匹配的目标值清空')
reloadedModel.sourceField = ''
assert.match(getFieldValueLinkageError(reloadedModel), /源字段/)

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
