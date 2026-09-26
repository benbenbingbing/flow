import test from 'node:test'
import assert from 'node:assert/strict'
import { createEventStepEditor, serializeEventStep, parseEventDocument } from '../eventStepModel.js'

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
