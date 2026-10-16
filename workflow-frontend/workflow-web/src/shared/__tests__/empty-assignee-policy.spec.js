import assert from 'node:assert/strict'
import { normalizeEmptyAssigneeStrategy, validateEmptyAssigneeStrategy, buildAssigneeConfig } from '../process-config/index.js'

const complete = {
  fallbackUser: 'alice', fallbackGroup: 'finance', responsibilityOwner: 'ops',
  responsibilityOwnerType: 'GROUP', maxRetries: 4, initialDelaySeconds: 5, backoffMultiplier: 1.5
}
let checks = 0
function check(value, expected, allowInherit = true) {
  assert.equal(validateEmptyAssigneeStrategy(value, allowInherit), expected)
  checks++
}

for (const policy of ['INHERIT', 'BLOCK_PUBLISH', 'CREATE_INCIDENT', 'FALLBACK_USER', 'FALLBACK_GROUP', 'WAIT_AND_RETRY']) {
  check({ ...complete, policy }, '')
  const snapshot = normalizeEmptyAssigneeStrategy({ ...complete, policy })
  assert.deepEqual(normalizeEmptyAssigneeStrategy(JSON.parse(JSON.stringify(snapshot))), snapshot)
  const node = buildAssigneeConfig({ emptyAssigneeStrategy: snapshot })
  assert.deepEqual(node.emptyAssigneeStrategy, snapshot)
  checks += 2
}
check({ policy: 'INHERIT' }, '请选择有效的空办理人策略', false)
check({ policy: 'BOGUS' }, '请选择有效的空办理人策略')
check({}, '')
check({}, '', false)
check({ policy: 'BLOCK_PUBLISH' }, '')
check({ policy: 'FALLBACK_USER' }, '请选择兜底用户')
check({ policy: 'FALLBACK_GROUP' }, '请选择兜底用户组')
for (const policy of ['CREATE_INCIDENT', 'FALLBACK_USER', 'FALLBACK_GROUP', 'WAIT_AND_RETRY']) {
  check({ ...complete, policy, responsibilityOwner: '  ' }, '请选择责任人或值班组')
}
for (const [field, invalid, message] of [
  ['maxRetries', [0, 21, 1.5, NaN], '最大重试次数必须为 1–20 的整数'],
  ['initialDelaySeconds', [0, 4, 86401, 5.5, NaN], '首次等待必须为 5–86400 秒的整数'],
  ['backoffMultiplier', [0, 0.5, 11, NaN, Infinity], '退避倍率必须在 1–10 之间']
]) {
  for (const value of invalid) check({ ...complete, policy: 'WAIT_AND_RETRY', [field]: value }, message)
}
check({ ...complete, policy: 'WAIT_AND_RETRY', maxRetries: 20, initialDelaySeconds: 86400, backoffMultiplier: 10 }, '')
assert.equal(normalizeEmptyAssigneeStrategy({ responsibilityOwner: 'legacy-ops' }).responsibilityOwnerType, '')
assert.equal(normalizeEmptyAssigneeStrategy({ ...complete, responsibilityOwnerType: 'USER' }).responsibilityOwnerType, 'USER')
console.log(`Empty assignee policy: ${checks + 2} checks passed`)
