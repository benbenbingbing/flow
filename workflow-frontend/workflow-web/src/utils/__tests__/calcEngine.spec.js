import assert from 'node:assert/strict'

import { evaluateExpression, evaluateLinkageExpression, formatCalcResult, getLinkageExpressionError } from '@flow/workflow-core/utils/calcEngine'

assert.equal(
  evaluateExpression('(${amount} + ${fee}) * ${rate}', {
    amount: 100,
    fee: '20',
    rate: 0.5
  }),
  60
)

assert.equal(
  evaluateExpression('datediff(${startDate}, ${endDate})', {
    startDate: '2026-06-01',
    endDate: '2026-06-06'
  }),
  5
)

const originalWarn = console.warn
console.warn = () => {}
assert.equal(evaluateExpression('Math.max(1, 2)', {}), null)
console.warn = originalWarn
assert.equal(formatCalcResult(12.345, 'number', 1), '12.3')
assert.equal(formatCalcResult('2026-06-06T12:30:00', 'date'), '2026-06-06')

const now = new Date(2024, 1, 28, 23, 30)
assert.equal(evaluateLinkageExpression('TODAY()', {}, now), '2024-02-28')
assert.equal(evaluateLinkageExpression('ADD_DAYS(TODAY(), 2)', {}, now), '2024-03-01')
assert.equal(evaluateLinkageExpression('ADD_DAYS(${field.start}, ${days} * -1)', { start: '2024-03-01', days: 1 }, now), '2024-02-29')
assert.equal(evaluateLinkageExpression('ADD_DAYS("2026-03-07", 2)', {}, now), '2026-03-09')
assert.equal(evaluateLinkageExpression('DATEDIFF(TODAY(), ADD_DAYS(TODAY(), 7))', {}, now), 7)
assert.equal(evaluateLinkageExpression('IF(${qty} >= 10, TODAY(), ADD_DAYS(TODAY(), 3))', { qty: 8 }, now), '2024-03-02')
assert.equal(evaluateLinkageExpression('IF(${urgent}, TODAY(), null)', { urgent: true }, now), '2024-02-28')
assert.equal(evaluateLinkageExpression('IF(${label} == "O\'Brien", 1, 0)', { label: "O'Brien" }, now), 1)
assert.equal(evaluateLinkageExpression('ADD_DAYS(TODAY(), ${days})', { days: '' }, now), null)
assert.equal(evaluateLinkageExpression('ADD_DAYS(TODAY(), ${days} * 2)', { days: '' }, now), null)
assert.equal(evaluateLinkageExpression('ADD_DAYS("2024-02-30", 1)', {}, now), null)
assert.equal(evaluateLinkageExpression('ADD_DAYS(TODAY(), 1.5)', {}, now), null)
assert.match(getLinkageExpressionError('ADD_DAYS(TODAY())'), /2 个参数/)
assert.match(getLinkageExpressionError('constructor()'), /不支持的函数/)
