import assert from 'node:assert/strict'
import {
  filterOptionsByEntity,
  matchesSupportedEntityCodes,
  normalizeSupportedEntityCodes
} from '../extension-entity-scope.js'

assert.deepEqual(normalizeSupportedEntityCodes(undefined), [])
assert.deepEqual(normalizeSupportedEntityCodes('*'), [])
assert.deepEqual(normalizeSupportedEntityCodes(['*']), [])
assert.deepEqual(normalizeSupportedEntityCodes('expense'), ['expense'])
assert.deepEqual(
  normalizeSupportedEntityCodes(['expense', 'Expense', ' contract ', '']),
  ['expense', 'contract']
)

assert.equal(matchesSupportedEntityCodes([], 'expense'), true)
assert.equal(matchesSupportedEntityCodes(['*'], 'expense'), true)
assert.equal(matchesSupportedEntityCodes(['expense'], 'EXPENSE'), true)
assert.equal(matchesSupportedEntityCodes(['expense'], 'contract'), false)
assert.equal(matchesSupportedEntityCodes(['expense'], ''), true)

const options = [
  { value: 'ENTITY_FIELD', supportedEntityCodes: [] },
  { value: 'EXPENSE_RISK', supportedEntityCodes: ['expense'] },
  { value: 'CONTRACT_AMOUNT', supportedEntityCodes: ['contract'] }
]

assert.deepEqual(
  filterOptionsByEntity(options, 'expense').map(item => item.value),
  ['ENTITY_FIELD', 'EXPENSE_RISK']
)
assert.deepEqual(
  filterOptionsByEntity(options, 'contract', 'EXPENSE_RISK').map(item => item.value),
  ['ENTITY_FIELD', 'EXPENSE_RISK', 'CONTRACT_AMOUNT']
)

console.log('extension entity scope tests passed')
