import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildExtensionChanges, extensionSnapshot } from '../identity-extension-form.js'

const schema = { code: 'profile', title: '资料', maintenance: 'LOCAL', writable: true,
  fields: [{ name: 'level', label: '等级', control: 'SELECT', required: true, maxLength: 8, options: ['T1', 'T2'] }] }

test('missing groups stay absent and unchanged rows are not submitted', () => {
  assert.deepEqual(buildExtensionChanges([schema], {}, {}, {}), {})
  const data = { level: 'T1' }
  assert.deepEqual(buildExtensionChanges([schema], { profile: data }, { profile: JSON.stringify(data) }, { profile: 3 }), {})
})
test('new and modified groups carry their own optimistic version', () => {
  assert.deepEqual(buildExtensionChanges([schema], { profile: { level: 'T2', forged: 'ignored' } }, {}, {}),
    { profile: { expectedVersion: 0, data: { level: 'T2' } } })
  assert.equal(buildExtensionChanges([schema], { profile: { level: 'T2' } }, { profile: '{"level":"T1"}' }, { profile: 4 }).profile.expectedVersion, 4)
})
test('source and unauthorized groups cannot become edits', () => {
  for (const definition of [{ ...schema, maintenance: 'SOURCE' }, { ...schema, writable: false }]) {
    assert.deepEqual(buildExtensionChanges([definition], { profile: { level: 'T2' } }, {}, {}), {})
  }
})
test('required and selection validation happens before submission', () => {
  for (const value of [null, ' ', 'unknown']) {
    assert.throws(() => buildExtensionChanges([schema], { profile: { level: value } }, {}, {}))
  }
})
test('snapshots explicitly clear removed fields and discard unknown keys', () => {
  assert.deepEqual(extensionSnapshot(schema, { forged: 'bad' }), { level: null })
})
test('non-finite numbers are rejected while false is a valid required boolean', () => {
  const number = { ...schema, fields: [{ name: 'amount', control: 'NUMBER' }] }
  assert.throws(() => buildExtensionChanges([number], { profile: { amount: Infinity } }, {}, {}))
  const boolean = { ...schema, fields: [{ name: 'enabled', required: true, control: 'BOOLEAN' }] }
  assert.equal(buildExtensionChanges([boolean], { profile: { enabled: false } }, {}, {}).profile.data.enabled, false)
})
test('precise numeric text remains exact and clearing optional selections produces null', () => {
  const exact = { ...schema, fields: [{ name: 'amount', label: '金额', control: 'NUMBER', numberAsString: true }] }
  const value = '9007199254740993.12345678'
  assert.equal(buildExtensionChanges([exact], { profile: { amount: value } }, {}, {}).profile.data.amount, value)
  assert.equal(extensionSnapshot(exact, { amount: '' }).amount, null)
  assert.equal(extensionSnapshot(schema, { level: '' }).level, null)
})
