import assert from 'node:assert/strict'

import { evaluateExpression, formatCalcResult } from '../calcEngine.js'

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
