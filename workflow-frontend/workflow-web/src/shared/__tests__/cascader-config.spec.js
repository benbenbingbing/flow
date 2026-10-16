import assert from 'node:assert/strict'
import { sanitizeComponentConfig } from '../component-config.js'

const schema = [{ key: 'cascaderOptions', type: 'json' }]
let tree = [{ value: 'leaf', label: '末级' }]
for (let level = 5; level > 0; level--) tree = [{ value: level, label: `层级${level}`, children: tree }]
const config = sanitizeComponentConfig(schema, { cascaderSource: 'STATIC', cascaderOptions: tree })
let node = config.cascaderOptions[0]
for (let level = 1; level <= 5; level++) {
  assert.equal(node.value, level)
  node = node.children[0]
}
assert.equal(node.value, 'leaf', '编辑组件参数不能按通用配置深度限制截断级联树')
assert.equal(sanitizeComponentConfig(schema, { ...config, cascaderSource: 'INTERFACE' }).cascaderOptions[0].children.length, 1)
const options = Array.from({ length: 501 }, (_, index) => ({ value: index, label: `选项${index}` }))
assert.equal(sanitizeComponentConfig(schema, { cascaderOptions: options }).cascaderOptions.length, 501)
assert.throws(() => sanitizeComponentConfig(schema, { cascaderOptions: [{ value: 'x' }] }), /label/)
const unsafe = JSON.parse('{"cascaderOptions":[{"value":"a","label":"A","__proto__":{"polluted":true}}],"__proto__":{"polluted":true}}')
const cleaned = sanitizeComponentConfig(schema, unsafe)
assert.equal(Object.hasOwn(cleaned, '__proto__'), false)
assert.equal(Object.hasOwn(cleaned.cascaderOptions[0], '__proto__'), false)
assert.equal({}.polluted, undefined)
assert.equal(sanitizeComponentConfig([], { normal: Array(501).fill(1) }).normal.length, 500)
console.log('cascader configuration preservation tests passed')
