import assert from 'node:assert/strict'
import {
  normalizeFieldCollectionValue,
  normalizeFieldDefaultValue
} from '../useFormField.js'

const booleanField = { fieldType: 'BOOLEAN', componentType: 'switch' }

assert.equal(normalizeFieldDefaultValue(booleanField, 'false'), false)
assert.equal(normalizeFieldDefaultValue(booleanField, '0'), false)
assert.equal(normalizeFieldDefaultValue(booleanField, false), false)
assert.equal(normalizeFieldDefaultValue(booleanField, 'true'), true)
assert.equal(normalizeFieldDefaultValue(booleanField, '1'), true)
assert.equal(normalizeFieldDefaultValue(booleanField, 1), true)
assert.equal(
  normalizeFieldDefaultValue({ fieldType: 'STRING', componentType: 'input' }, 'false'),
  'false'
)
assert.deepEqual(normalizeFieldCollectionValue(''), [])
assert.deepEqual(normalizeFieldCollectionValue(null), [])
assert.deepEqual(normalizeFieldCollectionValue('dev'), ['dev'])
assert.deepEqual(normalizeFieldCollectionValue(['dev', 'qa']), ['dev', 'qa'])

console.log('useFormField default value tests passed')
