import assert from 'node:assert/strict'
import test from 'node:test'
import { buildSequenceFlowConditionConfig, buildSequenceFlowConditionExpression } from '../node-config/sequenceFlowCondition.js'

test('业务接口参数保留布尔值和节点列表，连线只编译为固定入口', () => {
  assert.deepEqual(buildSequenceFlowConditionConfig({
    providerCode: ' financeDecision ',
    extraParamsText: '{"nodeIds":["finance","legal"],"requireAll":true}'
  }), { providerCode: 'financeDecision', extraParams: { nodeIds: ['finance', 'legal'], requireAll: true } })
  assert.equal(buildSequenceFlowConditionExpression('Flow_1'), "${sequenceFlowConditionEvaluator.evaluate(execution, 'Flow_1')}")
})

test('缺少接口或参数不是对象时拒绝保存，不能退化为无条件分支', () => {
  assert.throws(() => buildSequenceFlowConditionConfig({ providerCode: '', extraParamsText: '{}' }), /请选择/)
  for (const extraParamsText of ['[1]', 'null', 'true', '"text"', '{broken']) {
    assert.throws(() => buildSequenceFlowConditionConfig({ providerCode: 'decision', extraParamsText }), /JSON/)
  }
  assert.throws(() => buildSequenceFlowConditionExpression("flow') || evil('"), /ID/)
})
