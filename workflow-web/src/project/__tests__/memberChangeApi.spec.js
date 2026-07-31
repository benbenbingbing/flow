import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../api/memberChange.js', import.meta.url),
  'utf8'
)

test('member context reads project member through the scoped detail API', () => {
  assert.match(
    source,
    /entityDataApi\.getDetail\(\s*['"]project_member['"]\s*,\s*memberId\s*\)/
  )
  assert.doesNotMatch(
    source,
    /entityDataApi\.getById\(memberId\)/
  )
})
