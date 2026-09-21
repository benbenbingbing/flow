import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  CROSS_FIELD_OPERATORS,
  areCrossFieldTypesCompatible,
  buildCrossFieldDependencyIndex,
  compareCrossFieldValues,
  evaluateCrossField,
  validateCrossFieldConfiguration
} from '@flow/workflow-core/form-cross-field-validation'

const fixtures = JSON.parse(readFileSync(new URL('../../../../docs/testing/fixtures/form-cross-field-comparisons.json', import.meta.url)))
for (const item of fixtures) {
  const compare = () => compareCrossFieldValues(item.left, item.right, item.type, item.targetType || item.type)
  if (item.invalid) assert.throws(compare, undefined, JSON.stringify(item))
  else assert.equal(compare(), item.expected, JSON.stringify(item))
}
assert.throws(() => compareCrossFieldValues(9007199254740993, 1, 'LONG'), /安全精度/)
assert.equal(compareCrossFieldValues(new Date(2026, 8, 15, 10), '2026-09-15 10:00:00', 'DATETIME'), 0)
assert.equal(areCrossFieldTypesCompatible('STRING', 'STRING'), false)

const start = { fieldCode: 'startTime', fieldLabel: '开始时间', fieldType: 'DATETIME' }
const rule = { id: 'end_after_start', operator: 'GE', targetFieldCode: 'startTime', message: '结束时间不能早于开始时间' }
const config = { version: 1, rules: [rule] }
const end = { fieldCode: 'endTime', fieldLabel: '结束时间', fieldType: 'DATETIME', validationRules: { crossField: config } }
const fields = [start, end]
const editable = { visible: true, editable: true }
const values = { startTime: '2026-09-15 10:00:00', endTime: '2026-09-15 09:00:00' }

assert.deepEqual(validateCrossFieldConfiguration(config, end, fields), [])
assert.equal(evaluateCrossField(end, values, fields, editable).error.message, rule.message)
assert.equal(evaluateCrossField(end, { ...values, endTime: values.startTime }, fields, editable).error, null)
assert.equal(evaluateCrossField(end, values, fields, { visible: false, editable: true }).error, null)
assert.equal(evaluateCrossField(end, values, fields, { visible: true, editable: false }).error, null)
assert.equal(evaluateCrossField(end, values, fields).error, null, '未提供可信编辑状态时不默认启用')
assert.equal(evaluateCrossField(end, { ...values, startTime: null }, fields, editable).deferred, false)
assert.equal(evaluateCrossField(end, { endTime: values.endTime }, fields, editable).deferred, true)
assert.match(evaluateCrossField(end, { ...values, startTime: 'bad' }, fields, editable).error.message, /无法比较/)
assert.deepEqual([...buildCrossFieldDependencyIndex(fields).get('startTime')], ['endTime'])

for (const operator of CROSS_FIELD_OPERATORS) {
  const field = { ...end, validationRules: { crossField: { version: 1, rules: [{ ...rule, operator: operator.value, message: '' }] } } }
  for (const [time, passed] of [
    ['2026-09-15 09:00:00', ['NE', 'LT', 'LE'].includes(operator.value)],
    [values.startTime, ['EQ', 'GE', 'LE'].includes(operator.value)],
    ['2026-09-15 11:00:00', ['NE', 'GT', 'GE'].includes(operator.value)]
  ]) {
    const result = evaluateCrossField(field, { ...values, endTime: time }, [start, field], editable)
    assert.equal(result.error === null, passed, `${operator.value}: ${time}`)
    if (!passed) assert.match(result.error.message, /结束时间必须.+开始时间/)
  }
}

const invalidConfigs = [
  { ...config, version: 2 },
  { ...config, enabled: false },
  { ...config, rules: [{ ...rule, modes: ['edit'] }] },
  { ...config, rules: [{ ...rule, enabled: false }] },
  { ...config, rules: [{ ...rule, targetFieldCode: 'data.startTime' }] },
  { ...config, rules: [{ ...rule, targetFieldCode: 'endTime' }] },
  { ...config, rules: [{ ...rule, targetFieldCode: 'missing' }] },
  { ...config, rules: [{ ...rule, operator: 'script' }] },
  { ...config, rules: [{ ...rule, message: '长'.repeat(201) }] },
  { ...config, rules: [rule, { ...rule, id: 'second' }] },
  { ...config, rules: [rule, { ...rule, operator: 'LT' }] },
  { ...config, rules: Array.from({ length: 21 }, () => rule) }
]
for (const invalid of invalidConfigs) assert.ok(validateCrossFieldConfiguration(invalid, end, fields).length, JSON.stringify(invalid))
assert.ok(validateCrossFieldConfiguration(config, end, [{ ...start, fieldType: 'DATE' }, end]).length)
assert.deepEqual(validateCrossFieldConfiguration(null, end, fields), [])
assert.deepEqual(validateCrossFieldConfiguration({ version: 1, rules: [] }, end, fields), [])
assert.deepEqual(buildCrossFieldDependencyIndex([{ ...end, validationRules: { crossField: null } }]), new Map())
console.log(`cross-field comparison and rule tests passed (${fixtures.length} shared cases)`)
