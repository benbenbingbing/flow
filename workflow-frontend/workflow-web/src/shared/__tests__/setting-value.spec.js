import assert from 'node:assert/strict'
import { serializeSettingInput, settingInputText } from '../setting-value.js'

assert.equal(serializeSettingInput('BOOLEAN', false), 'false')
assert.equal(serializeSettingInput('BOOLEAN', true), 'true')
assert.throws(() => serializeSettingInput('BOOLEAN', 'true'))
assert.equal(serializeSettingInput('NUMBER', '0'), '0')
assert.equal(serializeSettingInput('NUMBER', '-12.5'), '-12.5')
assert.equal(serializeSettingInput('NUMBER', '1e3'), '1000')
for (const input of ['', 'NaN', 'Infinity', '1e9999', 'true', '"12"', '{}', '1 2']) {
  assert.throws(() => serializeSettingInput('NUMBER', input), input)
}
assert.equal(serializeSettingInput('STRING', ''), '""')
assert.equal(serializeSettingInput('STRING', 'true'), '"true"')
assert.equal(serializeSettingInput('STRING', '  中文\n"引号"'), JSON.stringify('  中文\n"引号"'))
assert.equal(settingInputText({ settingValueType: 'STRING', value: '原文' }), '原文')
assert.equal(settingInputText({ settingValueType: 'STRING', sensitive: true, value: null }), '')
assert.equal(settingInputText({ settingValueType: 'STRING', sensitive: true, value: 'must-not-echo' }), '')
assert.equal(serializeSettingInput('JSON', '{"width":240}'), '{"width":240}')
assert.equal(serializeSettingInput('JSON', '[1, false, "中文"]'), '[1,false,"中文"]')
for (const input of ['null', 'true', '12', '"text"', '{', '[] {}']) {
  assert.throws(() => serializeSettingInput('JSON', input), input)
}
assert.throws(() => serializeSettingInput('STRING', '中'.repeat(6000)), /16 KiB/)
assert.throws(() => serializeSettingInput('UNKNOWN', 'true'))
console.log('setting value validation passed (BOOLEAN, NUMBER, STRING, JSON)')
