import assert from 'node:assert/strict'
import {
  buildFlowConditionExpression,
  createFlowConditionGroup,
  parseFlowConditionConfig,
  parseFlowConditionExpression,
  serializeFlowConditionConfig
} from '../flowConditionGroups.js'

const fieldType = field => field === 'amount' ? 'number' : 'string'

const grouped = createFlowConditionGroup('AND', [
  {
    type: 'GROUP',
    logic: 'OR',
    children: [
      { type: 'CONDITION', property: 'approved', operator: '==', value: 'approve' },
      { type: 'CONDITION', property: 'approved', operator: '==', value: 'delegate' }
    ]
  },
  { type: 'CONDITION', property: 'amount', operator: '>=', value: '100' }
])

assert.equal(
  buildFlowConditionExpression(grouped, fieldType),
  "${(approved == 'approve' || approved == 'delegate') && amount >= 100}"
)

const parsed = parseFlowConditionExpression(
  "${(approved == 'approve' || approved == 'delegate') && amount >= 100}"
)
assert.equal(parsed.logic, 'AND')
assert.equal(parsed.children[0].logic, 'OR')
assert.equal(parsed.children[1].property, 'amount')

const precedence = parseFlowConditionExpression("${approved == 'approve' || amount >= 100 && status != 'CLOSED'}")
assert.equal(precedence.logic, 'OR')
assert.equal(precedence.children[1].logic, 'AND')

const contains = parseFlowConditionExpression("${remark.contains('urgent && important')}")
assert.equal(contains.children[0].operator, 'contains')
assert.equal(contains.children[0].value, 'urgent && important')

const legacyApproved = parseFlowConditionExpression('${approved == true || approved == false}')
assert.equal(legacyApproved.children[0].value, 'approve')
assert.equal(legacyApproved.children[1].value, 'reject')

const serialized = serializeFlowConditionConfig(grouped)
assert.deepEqual(parseFlowConditionConfig(serialized), grouped)

console.log('flow condition group tests passed')
