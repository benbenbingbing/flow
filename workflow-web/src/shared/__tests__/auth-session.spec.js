import assert from 'node:assert/strict'
import {
  createSingleFlight,
  isAccessTokenExpired,
  isTerminalAuthError,
  shouldRefreshAccessToken
} from '../auth-session.js'

const now = Date.parse('2026-08-12T03:30:00.000Z')

assert.equal(
  shouldRefreshAccessToken(
    'token',
    '2026-08-12T03:30:59.000Z',
    now
  ),
  true,
  '不足 60 秒时应在业务请求前续签'
)
assert.equal(
  shouldRefreshAccessToken(
    'token',
    '2026-08-12T03:31:01.000Z',
    now
  ),
  false,
  '剩余时间超过 60 秒时应直接发送业务请求'
)
assert.equal(
  shouldRefreshAccessToken('', '', now),
  false,
  '没有 Access Token 时不应由业务请求触发刷新'
)
assert.equal(
  shouldRefreshAccessToken('token', 'invalid', now),
  true,
  '过期时间不可解析时应保守续签'
)
assert.equal(
  isAccessTokenExpired(
    '2026-08-12T03:30:00.000Z',
    now
  ),
  true
)
assert.equal(
  isAccessTokenExpired(
    '2026-08-12T03:30:01.000Z',
    now
  ),
  false
)

assert.equal(
  isTerminalAuthError('AUTH_REFRESH_IDLE_EXPIRED'),
  true
)
assert.equal(
  isTerminalAuthError('AUTH_ACCESS_EXPIRED'),
  false,
  'Access Token 过期本身仍可续签'
)
assert.equal(
  isTerminalAuthError(undefined),
  false,
  '网络错误没有稳定错误码，不得清除登录状态'
)

let executions = 0
let release
const pending = new Promise(resolve => {
  release = resolve
})
const singleFlight = createSingleFlight(async () => {
  executions += 1
  await pending
  return 'refreshed'
})

const concurrent = Array.from(
  { length: 5 },
  () => singleFlight()
)
assert.equal(executions, 0)
await Promise.resolve()
assert.equal(
  executions,
  1,
  '五个并发请求只能触发一次刷新'
)
release()
assert.deepEqual(
  await Promise.all(concurrent),
  Array(5).fill('refreshed')
)
assert.equal(
  await singleFlight(),
  'refreshed',
  '上一次刷新结束后应允许创建新的刷新调用'
)
assert.equal(executions, 2)

console.log('auth session tests passed')
