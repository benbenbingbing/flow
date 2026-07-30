import assert from 'node:assert/strict'

import { configurableEntities } from '../interfaceServiceModel.js'

const entities = [
  { id: 'system', storageMode: 'SYSTEM' },
  { id: 'dynamic', storageMode: 'DYNAMIC' },
  { id: 'legacy' }
]

assert.deepEqual(
  configurableEntities(entities).map(entity => entity.id),
  ['dynamic', 'legacy']
)
assert.equal(entities.length, 3)
assert.deepEqual(configurableEntities(null), [])

console.log('interface service entity selection tests passed')
