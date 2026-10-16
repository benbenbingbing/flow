import assert from 'node:assert/strict'
import { parseJsonConfig } from '../jsonConfig.js'

assert.deepEqual(parseJsonConfig(''), {})
assert.deepEqual(parseJsonConfig('{"status":"ACTIVE"}'), { status: 'ACTIVE' })
assert.deepEqual(parseJsonConfig('[1,2]', { expectedType: 'array' }), [1, 2])
assert.deepEqual(
  parseJsonConfig('[{"id":1}]', { expectedType: 'object-or-array' }),
  [{ id: 1 }]
)
assert.deepEqual(
  parseJsonConfig('{"id":1}', { expectedType: 'object-or-array' }),
  { id: 1 }
)
assert.equal(parseJsonConfig('', { emptyValue: null }), null)

assert.throws(
  () => parseJsonConfig('{bad json', { fieldName: '查询参数' }),
  /查询参数不是有效的 JSON/
)
assert.throws(
  () => parseJsonConfig('[]', { fieldName: '字段映射' }),
  /字段映射必须是 JSON 对象/
)
assert.throws(
  () => parseJsonConfig('{}', { fieldName: '静态值', expectedType: 'array' }),
  /静态值必须是 JSON 数组/
)
assert.throws(
  () => parseJsonConfig('"text"', {
    fieldName: '请求体',
    expectedType: 'object-or-array'
  }),
  /请求体必须是 JSON 对象或数组/
)

console.log('jsonConfig tests passed')
