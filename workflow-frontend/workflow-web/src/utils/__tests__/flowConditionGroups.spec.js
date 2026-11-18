import assert from 'node:assert/strict'
import {
  buildFlowConditionExpression,
  collectFlowConditionProperties,
  createFlowConditionConfig,
  createFlowConditionGroup,
  evaluateFlowConditionExpression,
  evaluateFlowConditionGroup,
  isFlowConditionGroupComplete,
  parseFlowConditionConfig,
  parseFlowConditionExpression,
  serializeFlowConditionConfig
} from '@flow/workflow-core/utils/flowConditionGroups'

const fieldType = field => field === 'amount' ? 'number' : 'string'

assert.equal(buildFlowConditionExpression(createFlowConditionGroup('AND', [
  { type: 'CONDITION', property: 'amount', operator: '>=', value: '100' },
  { type: 'CONDITION', property: 'tags', operator: 'selectionAny', value: 'A 1' }
]), fieldType), '')

for (const [operator, codes, selected, expected] of [
  ['selectionEquals', '001', '001', true],
  ['selectionEquals', '001', '1', false],
  ['selectionAny', 'A1,B2', ['A10'], false],
  ['selectionAny', 'A1,B2', ['B2'], true],
  ['selectionAll', 'A1,B2', ['A1'], false],
  ['selectionAll', 'A1,B2', ['B2', 'A1'], true],
  ['selectionAll', 'A1,B2', 'A1,B2', false]
]) {
  const rule = createFlowConditionGroup('AND', [{ type: 'CONDITION', property: 'tags', operator, value: codes }])
  const expression = buildFlowConditionExpression(rule)
  assert.ok(expression.includes('_wfSelectionCondition.matches'))
  assert.deepEqual(parseFlowConditionExpression(expression), rule)
  assert.equal(evaluateFlowConditionExpression(expression, { tags: selected }), expected)
}

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
assert.deepEqual(parseFlowConditionConfig(createFlowConditionConfig(grouped)), grouped)
assert.equal(isFlowConditionGroupComplete(grouped), true)
assert.equal(
  isFlowConditionGroupComplete(createFlowConditionGroup('AND', [])),
  false
)
assert.deepEqual(
  collectFlowConditionProperties(grouped).sort(),
  ['amount', 'approved']
)

const linkageGroup = createFlowConditionGroup('AND', [
  { type: 'CONDITION', property: 'status', operator: '==', value: 'OPEN' },
  createFlowConditionGroup('OR', [
    { type: 'CONDITION', property: 'priority', operator: '==', value: 'HIGH' },
    { type: 'CONDITION', property: 'owner', operator: 'notEmpty', value: '' }
  ])
])
assert.equal(
  evaluateFlowConditionGroup(
    linkageGroup,
    { status: 'OPEN', priority: 'LOW', owner: 'u1' }
  ),
  true
)
assert.equal(
  evaluateFlowConditionGroup(
    linkageGroup,
    { status: 'OPEN', priority: 'LOW', owner: '' }
  ),
  false
)
assert.equal(
  evaluateFlowConditionGroup(
    createFlowConditionGroup('AND', []),
    {}
  ),
  false
)

const emptyExpression = buildFlowConditionExpression(
  createFlowConditionGroup('OR', [
    { type: 'CONDITION', property: 'remark', operator: 'empty', value: '' },
    { type: 'CONDITION', property: 'owner', operator: 'notEmpty', value: '' }
  ])
)
assert.equal(emptyExpression, '${empty(remark) || notEmpty(owner)}')
assert.equal(
  evaluateFlowConditionExpression(emptyExpression, { remark: '', owner: '' }),
  true
)
assert.equal(
  evaluateFlowConditionExpression('${empty(remark)}', { remark: '   ' }),
  true
)
assert.equal(
  evaluateFlowConditionExpression('${empty(metadata)}', { metadata: {} }),
  true
)

const legacyEmpty = parseFlowConditionExpression(
  "!${remark} || ${remark} == ''"
)
assert.equal(legacyEmpty.children[0].operator, 'empty')
assert.equal(
  evaluateFlowConditionExpression(
    "${owner} && ${owner} != ''",
    { owner: 'u1' }
  ),
  true
)

const strictComparison = parseFlowConditionExpression(
  "${status} === 'OPEN'"
)
assert.equal(strictComparison.children[0].operator, '==')
assert.equal(
  parseFlowConditionExpression("${status} == 'OPEN' && custom(status)"),
  null
)

console.log('flow condition group tests passed')
