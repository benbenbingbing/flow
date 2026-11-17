import test from 'node:test'
import assert from 'node:assert/strict'
import { timingAllowsMode, timingAllowsFailurePolicy, timingDefaults } from '../flow-action-timing-policy.js'

test('前置校验和执行前处理禁止提交后执行及非回滚策略', () => {
  const timing = { blocking: true, defaultExecutionMode: 'IN_TRANSACTION', defaultFailurePolicy: 'ROLLBACK' }
  assert.equal(timingAllowsMode(timing, 'IN_TRANSACTION'), true)
  assert.equal(timingAllowsMode(timing, 'AFTER_COMMIT'), false)
  assert.equal(timingAllowsFailurePolicy(timing, 'ROLLBACK'), true)
  for (const policy of ['CONTINUE', 'RETRY', 'IGNORE', 'CUSTOM']) {
    assert.equal(timingAllowsFailurePolicy(timing, policy), false)
    assert.deepEqual(timingDefaults(timing, policy), { executionMode: 'IN_TRANSACTION', failurePolicy: 'ROLLBACK' })
  }
})

test('后处理允许通知及自定义策略，已有引擎和扩展时机保留配置能力', () => {
  for (const timing of [undefined, {}, { blocking: false }]) {
    assert.equal(timingAllowsMode(timing, 'AFTER_COMMIT'), true)
    assert.equal(timingAllowsFailurePolicy(timing, 'CUSTOM'), true)
  }
  assert.deepEqual(timingDefaults({ defaultExecutionMode: 'AFTER_COMMIT', defaultFailurePolicy: 'RETRY' }, 'CUSTOM'),
    { executionMode: 'AFTER_COMMIT', failurePolicy: 'CUSTOM' })
})
