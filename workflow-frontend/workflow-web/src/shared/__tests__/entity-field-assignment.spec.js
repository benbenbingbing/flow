import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildFieldAssignments, parseFieldAssignments } from '../entity-field-assignment.js'

const fields = [
  { fieldCode: 'flag', fieldName: '标志', fieldType: 'BOOLEAN' },
  { fieldCode: 'serial', fieldName: '长整数', fieldType: 'LONG' },
  { fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL' },
  { fieldCode: 'note', fieldName: '说明', fieldType: 'STRING', required: true },
  { fieldCode: 'choice', fieldName: '选择', fieldType: 'SELECT', options: [{ value: 'A' }, { value: 'B', disabled: true }] }
]
test('精确保留长整数、小数、布尔值、字面量和显式清空，编辑后往返不变', () => {
  const rows = [
    { fieldCode: 'flag', operation: 'SET', value: false },
    { fieldCode: 'serial', operation: 'SET', value: '9223372036854775807' },
    { fieldCode: 'amount', operation: 'SET', value: '1234567890123456.78' },
    { fieldCode: 'note', operation: 'SET', value: '${literal}' },
    { fieldCode: 'choice', operation: 'CLEAR' }
  ]
  const raw = buildFieldAssignments(rows, fields)
  assert.deepEqual(parseFieldAssignments(raw), rows)
  assert.equal(buildFieldAssignments(parseFieldAssignments(raw), fields), raw)
})
test('拒绝重复字段、必填清空、未知字段、非法数值及停用选项', () => {
  for (const rows of [[],
    [{ fieldCode: 'note', operation: 'CLEAR' }],
    [{ fieldCode: 'flag', operation: 'SET', value: 'false' }],
    [{ fieldCode: 'serial', operation: 'SET', value: '9223372036854775808' }],
    [{ fieldCode: 'amount', operation: 'SET', value: 1.2 }],
    [{ fieldCode: 'amount', operation: 'SET', value: '1e3' }],
    [{ fieldCode: 'unknown', operation: 'CLEAR' }],
    [{ fieldCode: 'note', operation: 'SET', value: '' }],
    [{ fieldCode: 'choice', operation: 'SET', value: 'B' }],
    [{ fieldCode: 'flag', operation: 'CLEAR', value: false }],
    [{ fieldCode: 'flag', operation: 'CLEAR' }, { fieldCode: 'flag', operation: 'CLEAR' }]
  ]) assert.throws(() => buildFieldAssignments(rows, fields))
})
test('非法原配置明确报错，不能静默变为空配置或丢弃未知参数', () => {
  for (const raw of ['{', 'null', '[]', '{}', '{"assignments":[],"entityCode":"other"}', '{"assignments":[null]}',
    '{"assignments":[{"fieldCode":"note","operation":"CLEAR","unknown":1}]}']) assert.throws(() => parseFieldAssignments(raw))
})
