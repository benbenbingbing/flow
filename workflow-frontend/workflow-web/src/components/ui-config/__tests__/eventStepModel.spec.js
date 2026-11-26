import test from 'node:test'
import assert from 'node:assert/strict'
import { createEventStepEditor, serializeEventStep, parseEventDocument } from '../eventStepModel.js'
import { eventConditionPathGroups } from '../eventConditionPaths.js'
import { formLifecycleRows, updateFormLifecycleBindings } from '../formLifecycleEventModel.js'

test('表单事件编辑保留加载和提交绑定的执行条件、映射与预校验标记', () => {
  const otherBinding = { extensionId: 'load-api' }
  const form = { revision: 3, dataSourceBindingsDocument: JSON.stringify({ AFTER_LOAD: otherBinding }) }
  const { normalizeStep } = createEventStepEditor()
  const step = normalizeStep({ name: '提交校验', extensionId: 'check-api',
    condition: { path: 'input.mode', equals: 'approve' }, inputMapping: { amount: 'data.amount' },
    outputMapping: { checked: { literal: false } }, sideEffectFree: true, clientPrevalidate: true }, 0)

  const bindings = updateFormLifecycleBindings(form, 'BEFORE_SUBMIT', [step], false)
  assert.deepEqual(bindings.AFTER_LOAD, otherBinding)
  assert.deepEqual(bindings.BEFORE_SUBMIT, { name: '提交校验', extensionId: 'check-api', enabled: false,
    condition: { path: 'input.mode', equals: 'approve' }, inputMapping: { amount: 'data.amount' },
    outputMapping: { checked: { literal: false } }, sideEffectFree: true, clientPrevalidate: true })
  const updated = { revision: 4, dataSourceBindingsDocument: bindings }
  const row = formLifecycleRows(updated).find(row => row.eventCode === 'BEFORE_SUBMIT')
  assert.equal(row.enabled, false)
  assert.equal(row.revision, 4)
  assert.deepEqual(updateFormLifecycleBindings(updated, 'BEFORE_SUBMIT', [], false), { AFTER_LOAD: otherBinding })
})

test('步骤编辑往返保持条件、接口兼容标记与输出映射策略', () => {
  const { normalizeStep } = createEventStepEditor()
  const step = normalizeStep({ stepCode: 'lookup', strategy: 'REPLACE', extensionId: 'api-1', serviceId: 'legacy', operationCode: 'old-query', legacyListQuery: true, condition: { path: 'form.ready', equals: false }, inputMapping: { query: 'form.keyword' }, outputMapping: [{ sourcePath: 'data.value', targetPath: 'form.amount', clearOnEmpty: false, overwrite: 'CONFIRM' }] }, 0)
  assert.equal(step.legacyServiceId, 'legacy')
  const saved = serializeEventStep(step, 2)
  assert.equal(saved.order, 30)
  assert.deepEqual(saved.condition, { path: 'form.ready', equals: false })
  assert.deepEqual(saved.inputMapping, { query: 'form.keyword' })
  assert.equal(saved.outputMapping[0].clearOnEmpty, false)
  assert.equal(saved.outputMapping[0].overwrite, 'CONFIRM')
  assert.equal(saved.legacyListQuery, true)
  assert.equal('serviceId' in saved, false); assert.equal('legacyServiceId' in saved, false)
  assert.equal('rowKey' in saved.outputMapping[0], false)
})
test('布尔条件、字面量零值、空行和新旧 mapping 形状保持语义', () => {
  const { normalizeStep } = createEventStepEditor()
  for (const operator of ['truthy', 'exists']) {
    const step = normalizeStep({ condition: { path: 'form.ready', [operator]: false }, outputMapping: [{ targetPath: 'form.amount', literal: 0 }, { sourcePath: 'data.x' }] }, 0)
    const saved = serializeEventStep(step, 0)
    assert.deepEqual(saved.condition, { path: 'form.ready', [operator]: false })
    assert.equal(saved.outputMapping.length, 1); assert.equal(saved.outputMapping[0].literal, 0)
  }
  assert.equal(normalizeStep({ outputMapping: { 'form.name': 'data.name' } }, 0).outputRows[0].sourcePath, 'data.name')
})
test('编辑行 ID 在会话内唯一，解析错误沿用指定兜底', () => {
  const { normalizeStep } = createEventStepEditor()
  const steps = Array.from({ length: 4 }, (_, i) => normalizeStep({ inputMapping: { p: 'form.name' } }, i))
  assert.equal(new Set(steps.flatMap(step => [step.rowKey, step.inputRows[0].rowKey])).size, 8)
  assert.deepEqual(parseEventDocument('{', []), [])
  assert.deepEqual(parseEventDocument('[{"strategy":"AFTER"}]', []), [{ strategy: 'AFTER' }])
})

// 保存入口与模型往返共用同一份校验，主处理规则不能随 UI 拆分而丢失。
test('按钮主步骤数量、无条件执行和接口范围继续阻断非法保存', async () => {
  const { validateEventStepChain } = await import('../eventStepModel.js')
  const context = { inheritanceMode: 'REPLACE', formButtonExactTarget: true, formButtonEventSelected: true, interfaces: [{ extensionId: 'read' }] }
  const step = { strategy: 'REPLACE', condition: {}, extensionId: 'read', outputMapping: [] }
  assert.equal(validateEventStepChain([step], context), '')
  assert.match(validateEventStepChain([], context), /必须且只能/)
  assert.match(validateEventStepChain([step, step], { ...context, inheritanceMode: 'INHERIT' }), /最多只能/)
  assert.match(validateEventStepChain([{ ...step, condition: { path: 'amount', equals: 1 } }], context), /无条件/)
  assert.match(validateEventStepChain([{ ...step, extensionId: 'write' }], context), /无副作用/)
  assert.match(validateEventStepChain([{ ...step, extensionId: '' }], context), /结果回填/)
})

test('内置 Excel 主动作编辑往返保留独立参数，不生成接口坐标或字段映射', async () => {
  const { isBuiltinExcelStep, builtinExcelSummary, validateEventStepChain } = await import('../eventStepModel.js')
  const { normalizeStep } = createEventStepEditor()
  const original = { stepCode: 'excel_action', strategy: 'REPLACE', actionType: 'ENTITY_EXCEL', actionConfig: { operation: 'EXPORT', schemeRef: { mode: 'EXPLICIT', schemeCode: 'finance' }, dataScope: 'SELECTED', listKey: 'employees', selectionSource: { type: 'LIST_DIALOG' } }, failurePolicy: 'STOP' }
  const edited = normalizeStep(original, 0)
  assert.equal(isBuiltinExcelStep(edited), true)
  const saved = serializeEventStep(edited, 1)
  assert.equal(saved.order, 20)
  assert.deepEqual(saved.actionConfig, original.actionConfig)
  for (const key of ['extensionId', 'serviceId', 'inputMapping', 'outputMapping', 'rowKey']) assert.equal(key in saved, false)
  assert.match(builtinExcelSummary(saved), /导出.*finance.*选中记录/)
  edited.actionConfig.schemeRef.schemeCode = 'another'
  assert.equal(original.actionConfig.schemeRef.schemeCode, 'finance')
  assert.equal(saved.actionConfig.schemeRef.schemeCode, 'finance')
  assert.equal(validateEventStepChain([saved], { inheritanceMode: 'REPLACE', formButtonExactTarget: true, formButtonEventSelected: true, interfaces: [] }), '')
})

test('内置 Excel 动作不能作为前置或缺失必需配置保存，普通无接口步骤仍须回填', async () => {
  const { validateEventStepChain } = await import('../eventStepModel.js')
  const options = { inheritanceMode: 'REPLACE', interfaces: [] }
  const step = { strategy: 'REPLACE', actionType: 'ENTITY_EXCEL', actionConfig: { operation: 'TEMPLATE', schemeRef: { mode: 'DEFAULT' } }, failurePolicy: 'STOP' }
  assert.equal(validateEventStepChain([step], options), '')
  assert.match(validateEventStepChain([{ ...step, strategy: 'BEFORE' }], options), /必须为主处理/)
  assert.match(validateEventStepChain([{ ...step, failurePolicy: 'CONTINUE' }], options), /失败时停止/)
  assert.match(validateEventStepChain([{ ...step, actionConfig: {} }], options), /补全操作和方案/)
  assert.match(validateEventStepChain([{ ...step, actionConfig: { operation: 'EXPORT', schemeRef: { mode: 'EXPLICIT' } } }], options), /补全操作和方案/)
  assert.match(validateEventStepChain([{ strategy: 'AFTER' }], options), /结果回填/)
})

test('实体查询往返保留目标和页面参数路径，表单回填和列表返回字段分别校验', async () => {
  const { validateEventStepChain } = await import('../eventStepModel.js')
  const { normalizeStep } = createEventStepEditor()
  const saved = serializeEventStep(normalizeStep({
    actionType: 'ENTITY_LOOKUP',
    actionConfig: { entityCode: 'customer', idPath: 'input.params.customerId' },
    outputMapping: [{ sourcePath: 'data.data.phone', targetPath: 'form.contact_phone', overwrite: 'IF_EMPTY', clearOnEmpty: false }],
    strategy: 'BEFORE'
  }, 0), 0)
  assert.deepEqual(saved.actionConfig, { entityCode: 'customer', idPath: 'input.params.customerId' })
  assert.equal(saved.outputMapping[0].overwrite, 'IF_EMPTY')
  assert.equal('inputMapping' in saved, false)
  assert.equal('extensionId' in saved, false)
  const form = { ownerType: 'FORM', eventCode: 'FORM_OPEN', targetType: 'OWNER' }
  assert.equal(validateEventStepChain([saved], form), '')
  assert.match(validateEventStepChain([{ ...saved, outputMapping: [] }], form), /回填/)
  assert.match(validateEventStepChain([saved], { ...form, eventCode: 'FORM_SAVE' }), /不支持/)

  const list = { ownerType: 'LIST', eventCode: 'ROW_BUTTON_CLICK', targetType: 'BUTTON' }
  const listStep = serializeEventStep(normalizeStep({
    ...saved, strategy: 'REPLACE', actionConfig: { ...saved.actionConfig, fields: ['phone'] }
  }, 0), 0)
  assert.equal(JSON.parse(JSON.stringify(listStep)).outputMapping, undefined)
  assert.equal(validateEventStepChain([listStep], list), '')
  assert.match(validateEventStepChain([{ ...listStep, strategy: 'BEFORE' }], list), /主处理/)
})

test('打开表单按名称提供表单字段和参数，选中的路径保持原有条件协议', () => {
  const fields = [{ value: 'status', label: '状态' }]
  const parameters = [{ code: 'customerId', name: '客户 ID' }]
  const groups = eventConditionPathGroups('FORM_OPEN', fields, parameters)
  assert.deepEqual(groups.find(group => group.label === '表单字段').options, [{ label: '状态', value: 'input.form.status' }])
  assert.deepEqual(groups.find(group => group.label === '页面参数').options, [{ label: '客户 ID', value: 'input.params.customerId' }])
  const step = createEventStepEditor().normalizeStep({}, 0)
  step.conditionPath = groups[0].options.find(item => item.label === '打开方式').value
  step.conditionValue = 'create'
  assert.deepEqual(serializeEventStep(step, 0).condition, { path: 'input.mode', equals: 'create' })
  step.conditionPath = ''
  assert.deepEqual(serializeEventStep(step, 0).condition, {})
})

test('来源选项随事件变化，列表系统字段和业务字段使用实际行结构', () => {
  const fields = [{ value: 'id', label: 'ID' }, { value: 'status', label: '状态' }, { value: 'customer_id', label: '客户' }]
  const pathsFor = event => eventConditionPathGroups(event, fields, []).flatMap(group => group.options.map(item => item.value))
  assert.deepEqual(pathsFor('ROW_BUTTON_CLICK'), ['input.row.id', 'input.row.status', 'input.row.data.customer_id'])
  assert.deepEqual(pathsFor('TOOLBAR_BUTTON_CLICK'), ['input.selectedRows.0.id', 'input.selectedRows.0.status', 'input.selectedRows.0.data.customer_id'])
  assert.ok(pathsFor('ENTITY_SELECTED').includes('selection.id'))
  assert.ok(!pathsFor('FIELD_CHANGE').includes('selection.id'))
  assert.ok(!pathsFor('LIST_LOAD').some(path => path.startsWith('input.row')))
})
