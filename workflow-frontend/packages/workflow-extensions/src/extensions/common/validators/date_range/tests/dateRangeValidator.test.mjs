import assert from 'node:assert/strict'
import test from 'node:test'
import { DateRangeValidator } from '../shared/dateRangeValidator.js'

const validator = new DateRangeValidator()
const validate = (value, params, fieldType = 'DATE') => validator.validate(value, { params, field: { fieldType } })

test('固定边界默认包含当天，允许单边并可排除端点', () => {
  const params = { minDate: '2024-02-29', maxDate: '2024-03-02' }
  assert.equal(validate('2024-02-29', params), true)
  assert.equal(validate('2024-03-02', params), true)
  assert.equal(validate('2024-02-28', params), '日期不能早于 2024-02-29')
  assert.equal(validate('2024-03-03', params), '日期不能晚于 2024-03-02')
  assert.equal(validate('2024-02-29', { minDate: params.minDate, includeMin: false }), '日期必须晚于 2024-02-29')
  assert.equal(validate('2024-03-02', { maxDate: params.maxDate, includeMax: false }), '日期必须早于 2024-03-02')
})

test('日期时间按当地日期判断，保留 PC 和移动端的日期时间格式', () => {
  const params = { maxDate: '2026-05-01' }
  assert.equal(validate('2026-05-01 23:59:59', params, 'DATETIME'), true)
  assert.equal(validate('2026-05-02T00:00:00.000', params, 'DATETIME'), '日期不能晚于 2026-05-01')
  assert.equal(validate(new Date(2026, 4, 1, 23, 59), params, 'DATETIME'), true)
  assert.equal(validate('2026-05-01 24:00:00', params, 'DATETIME'), '请输入有效日期')
  assert.equal(validate('2026-05-01 00:00:00', params), '请输入有效日期')
})

test('相对边界按设备日历跨年计算并可与固定边界混用', t => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date(2026, 0, 1, 23, 30) })
  const params = { minDaysFromToday: -1, maxDaysFromToday: 1 }
  assert.equal(validate('2025-12-31', params), true)
  assert.equal(validate('2026-01-02', params), true)
  assert.equal(validate('2025-12-30', params), '日期不能早于 2025-12-31')
  assert.equal(validate('2026-01-03', params), '日期不能晚于 2026-01-02')
  assert.equal(validate('2026-01-01', { minDaysFromToday: 0, maxDate: '2026-01-02' }), true)
  assert.equal(validate('2025-12-31', { minDaysFromToday: 0 }), '日期不能早于 2026-01-01')
  t.mock.timers.tick(60 * 60 * 1000)
  assert.equal(validate('2026-01-01', { minDaysFromToday: 0 }), '日期不能早于 2026-01-02')
})

test('空值交给必填规则，无效日期不会被自动进位', () => {
  const params = { minDate: '2026-01-01' }
  for (const value of [undefined, null, '', '   ']) assert.equal(validate(value, params), true)
  for (const value of ['2026-02-29', '2026-04-31', '2026-13-01', '2026-02-00', '2026-2-01', new Date(NaN), 0]) {
    assert.equal(validate(value, params), '请输入有效日期')
  }
})

test('上下限配置有误时，即使字段为空也明确报错', () => {
  assert.throws(() => validate('', {}), /至少配置一个日期边界/)
  assert.throws(() => validate('', { minDate: '2026-01-01', minDaysFromToday: 0 }), /只能配置/)
  assert.throws(() => validate('', { maxDate: '2026-02-29' }), /有效日期/)
  assert.throws(() => validate('', { maxDaysFromToday: 1.5 }), /偏移必须为整数/)
  assert.throws(() => validate('', { maxDaysFromToday: Number.MAX_SAFE_INTEGER }), /偏移超出日期范围/)
  assert.throws(() => validate('', { minDate: '2026-02-02', maxDate: '2026-02-01' }), /没有可选日期/)
  assert.throws(() => validate('', { minDate: '2026-02-01', maxDate: '2026-02-02', includeMin: false, includeMax: false }), /没有可选日期/)
  assert.throws(() => validate('', { minDate: '9999-12-31', includeMin: false }), /没有可选日期/)
})
