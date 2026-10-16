import assert from 'node:assert/strict'
import {
  createFixedFilterRow,
  fixedFilterFieldOptions,
  fixedFilterValueType,
  readFixedFilterRows,
  writeFixedFilterRows
} from '../list-fixed-filters.js'

const row = (field, operator, value, rest = {}) => ({ ...createFixedFilterRow(), field, operator, value, ...rest })
const roundTrip = config => writeFixedFilterRows(readFixedFilterRows(config))
const historical = {
  status: 'RUNNING',
  total: 0, total_op: 'EQ',
  enabled: false,
  priority: 'HIGH, LOW', priority_op: 'IN',
  amount_start: 0, amount_end: 100,
  count_start: 5,
  deadline_end: '2026-10-01',
  owner: true, owner_op: 'IS_NULL',
  special: { literal: 'keep-me' },
  unusual: null, unusual_op: 'CUSTOM',
  nested: 'value', nested_start: 1, nested_end: 2
}
assert.deepEqual(roundTrip(historical), historical, '历史配置原样回写，保留隐式 EQ、false、0、单侧边界和特殊条件')
assert.deepEqual(roundTrip(JSON.stringify(historical)), historical)
assert.deepEqual(roundTrip({}), {})
assert.deepEqual(readFixedFilterRows(null), [])
assert.throws(() => readFixedFilterRows('{'), /JSON/)
assert.throws(() => readFixedFilterRows('[]'), /对象/)
assert.throws(() => readFixedFilterRows([]), /对象/)

const loaded = readFixedFilterRows(historical)
loaded[0].value = 'APPROVED'
assert.deepEqual(writeFixedFilterRows(loaded), { ...historical, status: 'APPROVED', status_op: 'EQ' })
assert.equal(historical.status, 'RUNNING', '修改草稿不得改写原始对象')
assert.equal(loaded.find(item => item.field === 'special').readOnly, true)
assert.equal(loaded.find(item => item.field === 'nested').readOnly, true)
assert.equal(loaded.find(item => item.field === 'amount').operator, 'BETWEEN')
assert.equal(loaded.find(item => item.field === 'count').operator, 'GE')
assert.equal(loaded.find(item => item.field === 'deadline').operator, 'LE')
assert.equal('special' in writeFixedFilterRows(loaded.filter(item => item.field !== 'special')), false)

assert.deepEqual(writeFixedFilterRows([
  row('status', 'IN', ['RUNNING', 'APPROVED']), row('amount', 'BETWEEN', '', { start: 0, end: 100 }),
  row('enabled', 'EQ', false), row('owner', 'IS_NULL', ''), row('count', 'GE', 0), row('deadline', 'LE', '2026-10-01')
]), {
  status: ['RUNNING', 'APPROVED'], status_op: 'IN', amount_start: 0, amount_end: 100, amount_op: 'BETWEEN',
  enabled: false, enabled_op: 'EQ', owner: true, owner_op: 'IS_NULL', count_start: 0, deadline_end: '2026-10-01'
})
assert.deepEqual(writeFixedFilterRows([row('count', 'GE', 0)], { systemEntity: true }), { count: 0, count_op: 'GE' })
assert.deepEqual(writeFixedFilterRows([row('code', 'NOT_IN', ['a', 'b'])]), { code: ['a', 'b'], code_op: 'NOT_IN' })
assert.throws(() => writeFixedFilterRows([row('code', 'NOT_IN', ['a'])], { systemEntity: true }), /比较方式/)
for (const op of ['EQ', 'NE', 'LIKE', 'GT', 'LT']) {
  assert.deepEqual(writeFixedFilterRows([row('name', op, 'abc')]), { name: 'abc', name_op: op })
}
assert.throws(() => writeFixedFilterRows([createFixedFilterRow()]), /第 1 行.*字段/)
assert.throws(() => writeFixedFilterRows([row('name', 'EQ', 'a'), row('name', 'NE', 'b')]), /第 2 行.*只能配置一次/)
assert.throws(() => writeFixedFilterRows([row('name', 'EQ', ' ')]), /条件值/)
assert.throws(() => writeFixedFilterRows([row('status', 'IN', [])]), /至少/)
assert.throws(() => writeFixedFilterRows([row('status', 'IN', ['a', ''])]), /条件值/)
assert.throws(() => writeFixedFilterRows([row('amount', 'BETWEEN', '', { start: 0, end: '' })]), /条件值/)
assert.throws(() => writeFixedFilterRows([row('amount', 'BETWEEN', '', { start: 10, end: 0 })]), /起始值/)
assert.throws(() => writeFixedFilterRows([row('name_op', 'EQ', 'a')]), /编码/)
assert.throws(() => writeFixedFilterRows([row('name', 'UNKNOWN', 'a')]), /比较方式/)
assert.throws(() => writeFixedFilterRows([row('amount', 'IN', ['abc'])], { fields: [{ fieldCode: 'amount', fieldType: 'NUMBER' }] }), /有效数值/)
assert.throws(() => writeFixedFilterRows([row('date', 'BETWEEN', '', { start: '2026-10-01', end: '2026-09-01' })], { fields: [{ fieldCode: 'date', fieldType: 'DATE' }] }), /起始值/)
assert.deepEqual(roundTrip(JSON.parse('{"__proto__":{"kept":true}}')), JSON.parse('{"__proto__":{"kept":true}}'))
assert.equal({}.kept, undefined)
assert.equal(fixedFilterValueType({ fieldType: 'DECIMAL' }), 'number')
assert.equal(fixedFilterValueType({ fieldType: 'BOOLEAN' }), 'boolean')
assert.equal(fixedFilterValueType({ fieldType: 'DATETIME' }), 'datetime')
assert.deepEqual(fixedFilterFieldOptions({ optionsJson: '[{"label":"运行中","value":"RUNNING"}]' }), [{ label: '运行中', value: 'RUNNING' }])
assert.deepEqual(fixedFilterFieldOptions({ options: ['a', 'b'] }), [{ label: 'a', value: 'a' }, { label: 'b', value: 'b' }])
const systemRows = readFixedFilterRows({ count: 1, count_op: 'GE' }, { systemEntity: true })
assert.equal(systemRows[0].readOnly, false)
systemRows[0].value = 2
assert.deepEqual(writeFixedFilterRows(systemRows, { systemEntity: true }), { count: 2, count_op: 'GE' })
const legacyNumeric = readFixedFilterRows({ amount: '001' })
legacyNumeric[0].operator = 'GT'
assert.deepEqual(writeFixedFilterRows(legacyNumeric, { fields: [{ fieldCode: 'amount', fieldType: 'NUMBER' }] }), { amount: '001', amount_op: 'GT' })
console.log('list fixed filter conversion tests passed')
