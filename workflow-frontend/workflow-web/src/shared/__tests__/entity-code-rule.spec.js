import assert from 'node:assert/strict'
import { generatorSchemaFields, generatorConfigDefaults } from '../entity-code-rule.js'

const schema = { type: 'object', required: ['prefix'], properties: {
  prefix: { type: 'string', title: '编号前缀', default: 'XM' },
  enabled: { type: 'boolean', default: true },
  width: { type: 'integer', minimum: 1, maximum: 10, default: 4 },
  type: { type: 'string', enum: ['A', 'B'] },
  options: { type: 'object', default: { year: true } }
} }
const fields = generatorSchemaFields(schema)
assert.equal(fields[0].required, true)
assert.equal(fields[0].label, '编号前缀')
assert.equal(fields[1].type, 'boolean')
assert.equal(fields[2].type, 'number')
assert.equal(fields[3].type, 'select')
assert.equal(fields[4].type, 'json')
const config = generatorConfigDefaults(schema, { enabled: false, width: 0 })
assert.equal(config.prefix, 'XM')
assert.equal(config.enabled, false)
assert.equal(config.width, 0)
config.options.year = false
assert.equal(schema.properties.options.default.year, true)
assert.deepEqual(generatorSchemaFields(), [])
console.log('entity-code-rule tests passed')
