import assert from 'node:assert/strict'
import {
  EmbedSessionError,
  createEmbedSession,
  parseEmbedSessionExpiry
} from '../src/embed/session/embedSession.js'

const baseTime = Date.parse('2026-08-27T08:00:00.000Z')
let currentTime = baseTime
const session = createEmbedSession({ now: () => currentTime })
const snapshots = []
session.subscribe(snapshot => snapshots.push(snapshot))

assert.equal(parseEmbedSessionExpiry('2026-08-27T08:30:00.000Z'), baseTime + 30 * 60_000)
assert.equal(parseEmbedSessionExpiry(1787819400), 1787819400 * 1000)
assert.equal(parseEmbedSessionExpiry('invalid'), 0)

const exchangeSnapshot = session.setExchange({
  accessToken: 'ems_token_abcdefghijklmnopqrstuvwxyz',
  tokenType: 'Bearer',
  expiresAt: '2026-08-27T09:00:00.000Z',
  idleExpiresAt: '2026-08-27T08:10:00.000Z'
}, { expectedLaunchId: 'lch_0123456789abcdef' })

assert.equal(exchangeSnapshot.active, true)
assert.equal(exchangeSnapshot.launchId, 'lch_0123456789abcdef')
assert.equal(exchangeSnapshot.expiresAt, baseTime + 60 * 60_000)
assert.equal(exchangeSnapshot.idleExpiresAt, baseTime + 10 * 60_000)
assert.equal(Object.hasOwn(exchangeSnapshot, 'accessToken'), false, '快照不得泄露 token')
assert.equal(session.getAccessToken({ required: true }), 'ems_token_abcdefghijklmnopqrstuvwxyz')

const heartbeatSnapshot = session.applyHeartbeat({
  status: 'ACTIVE',
  idleExpiresAt: '2026-08-27T08:20:00.000Z',
  absoluteExpiresAt: '2026-08-27T10:00:00.000Z'
})
assert.equal(heartbeatSnapshot.idleExpiresAt, baseTime + 20 * 60_000)
assert.equal(
  heartbeatSnapshot.absoluteExpiresAt,
  baseTime + 60 * 60_000,
  '客户端不得通过心跳扩展绝对会话寿命'
)

currentTime = baseTime + 20 * 60_000
assert.throws(
  () => session.getAccessToken({ required: true }),
  error => error instanceof EmbedSessionError && error.errorCode === 'EMBED_SESSION_EXPIRED'
)
assert.equal(session.getSnapshot().active, false)
assert.equal(session.getSnapshot().clearReason, 'expired')
assert.ok(snapshots.length >= 3)

assert.throws(
  () => createEmbedSession({ now: () => baseTime }).setExchange({
    accessToken: 'token with spaces',
    expiresAt: baseTime + 60_000
  }),
  error => error.errorCode === 'EMBED_SESSION_TOKEN_INVALID'
)

assert.throws(
  () => createEmbedSession({ now: () => baseTime }).setExchange({
    accessToken: 'valid_token_abcdefghijklmnop',
    expiresAt: baseTime
  }),
  error => error.errorCode === 'EMBED_SESSION_EXPIRY_INVALID'
)

const shortenedSession = createEmbedSession({ now: () => baseTime })
shortenedSession.setExchange({
  accessToken: 'valid_token_abcdefghijklmnop',
  expiresAt: baseTime + 60_000,
  idleExpiresAt: baseTime + 30_000
})
assert.throws(
  () => shortenedSession.applyHeartbeat({
    status: 'ACTIVE',
    absoluteExpiresAt: baseTime - 1,
    idleExpiresAt: baseTime + 30_000
  }),
  error => error.errorCode === 'EMBED_SESSION_EXPIRED'
)

console.log('embed session tests passed')
