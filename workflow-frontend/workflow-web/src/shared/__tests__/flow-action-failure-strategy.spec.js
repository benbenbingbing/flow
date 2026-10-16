import assert from 'node:assert/strict'
import { failurePolicyOptions, strategyProblem, strategyDefaults, strategyFormSchema, parseStrategyConfig } from '../flow-action-failure-strategy.js'

assert.deepEqual(failurePolicyOptions('IN_TRANSACTION').map(v => v.value), ['ROLLBACK', 'CONTINUE', 'CUSTOM'])
assert.deepEqual(failurePolicyOptions('AFTER_COMMIT').map(v => v.value), ['RETRY', 'IGNORE', 'CUSTOM'])
const strategy = {
  supportedExecutionModes: ['AFTER_COMMIT'], possibleDispositions: ['RETRY', 'MANUAL'],
  configSchema: [{ key: 'delay', type: 'number', defaultValue: 60 },
    { key: 'end', type: 'select', defaultValue: 'MANUAL', options: ['MANUAL', 'IGNORE'] }]
}
assert.ok(strategyProblem(strategy, 'IN_TRANSACTION', true))
assert.ok(strategyProblem(strategy, 'AFTER_COMMIT', false))
assert.equal(strategyProblem(strategy, 'AFTER_COMMIT', true), '')
assert.ok(strategyProblem(null, 'AFTER_COMMIT', true))
assert.deepEqual(strategyDefaults(strategy), { delay: 60, end: 'MANUAL' })
assert.deepEqual(strategyFormSchema(strategy)[1].options[0], { label: 'MANUAL', value: 'MANUAL' })
assert.deepEqual(parseStrategyConfig('{"delay":0}'), { delay: 0 })
for (const invalid of ['null', '[]', '{', '"text"']) assert.throws(() => parseStrategyConfig(invalid))
console.log('流程动作自定义失败策略配置测试通过')
