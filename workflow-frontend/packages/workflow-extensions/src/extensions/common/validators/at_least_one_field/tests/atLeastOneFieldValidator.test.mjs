import assert from 'node:assert/strict'
import test from 'node:test'
import { AtLeastOneFieldValidator } from '../shared/atLeastOneFieldValidator.js'

const validator = new AtLeastOneFieldValidator()
const fieldCodes = ['phone', 'email']
const message = '请至少填写指定字段中的一项'
const validate = (value, formData = {}, params = { fieldCodes }) => validator.validate(value, { fieldCode: 'phone', formData, params })

test('从同一表单的指定字段取值，绑定字段采用当前传入值', () => {
  assert.equal(validate('', { phone: '旧值', email: '' }), message)
  assert.equal(validate('123', { phone: '', email: '' }), true)
  assert.equal(validate('', { email: 'user@example.com' }), true)
  assert.equal(validate('123', { email: '', alternatePhone: '' }, { fieldCodes: ['email', 'alternatePhone'] }), message)
  assert.equal(validate('', { anotherField: '无关字段' }), message)
})

test('空白文本、空选择和空附件分组算空，0 和 false 是有效值', () => {
  for (const value of [undefined, null, ' \n ', [], {}, { receipts: [null, ' ', []] }, NaN, new Date(NaN)]) {
    assert.equal(validate('', { email: value }), message)
  }
  for (const value of [0, false, [0], { receipts: [{ id: 'attachment-1' }] }, new Date(2026, 0, 1)]) {
    assert.equal(validate('', { email: value }), true)
  }
})

test('拒绝无效字段清单，支持自定义错误并忽略快照原型上的字段', () => {
  for (const invalid of [undefined, 'phone,email', [], ['phone'], ['phone', 'phone'], ['phone', 'bad field'], ['phone', null]]) {
    assert.throws(() => validate('', {}, { fieldCodes: invalid }), /至少两个不同的有效字段编码/)
  }
  assert.equal(validate('', Object.create({ email: '原型属性' })), message)
  assert.equal(validate('', {}, { fieldCodes, message: ' 手机或邮箱至少填写一项 ' }), '手机或邮箱至少填写一项')
})
