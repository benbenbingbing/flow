import { createHash, createHmac, timingSafeEqual } from 'node:crypto'
import { createServer } from 'node:http'

const CLIENT_ID = process.env.REFERENCE_CLIENT_ID || 'reference-client'
const CLIENT_SECRET = process.env.REFERENCE_CLIENT_SECRET || 'reference-secret'
const WEBHOOK_SECRET = process.env.REFERENCE_WEBHOOK_SECRET || 'reference-webhook-secret'
const MAX_BODY_BYTES = 1024 * 1024
const MAX_TIMESTAMP_SKEW_SECONDS = 300

const json = (response, status, body, headers = {}) => {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', ...headers })
  response.end(JSON.stringify(body))
}

const readBody = async request => {
  const chunks = []
  let size = 0
  for await (const chunk of request) {
    size += chunk.length
    if (size > MAX_BODY_BYTES) throw new Error('request body too large')
    chunks.push(chunk)
  }
  const raw = Buffer.concat(chunks)
  return { raw, value: raw.length === 0 ? null : JSON.parse(raw.toString('utf8')) }
}

const bearer = request => {
  const value = request.headers.authorization || ''
  return value === 'Bearer reference-access-token'
}

const signature = (eventId, timestamp, body) => `v1=${createHmac('sha256', WEBHOOK_SECRET)
  .update(`${eventId}.${timestamp}.`)
  .update(body)
  .digest('base64')}`

const signatureMatches = (actual, expected) => {
  if (!actual || !expected || actual.length !== expected.length) return false
  return timingSafeEqual(Buffer.from(actual), Buffer.from(expected))
}

const stableId = value => createHash('sha256').update(value).digest('hex').slice(0, 24)

export const createReferenceExternalSystem = ({
  port = 0,
  host = '127.0.0.1'
} = {}) => {
  const requests = new Map()
  const idempotency = new Map()
  const webhookEvents = new Set()
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url || '/', 'http://reference.local')
      if (request.method === 'GET' && url.pathname === '/healthz') {
        return json(response, 200, { status: 'UP' })
      }
      if (request.method === 'GET' && url.pathname === '/test/state') {
        if (!bearer(request)) return json(response, 401, { error: 'unauthorized' })
        return json(response, 200, {
          requestCount: requests.size,
          webhookEventCount: webhookEvents.size,
          requests: [...requests.values()].map(value => ({ id: value.id, status: value.status }))
        })
      }
      if (request.method === 'POST' && url.pathname === '/oauth/token') {
        const authorization = request.headers.authorization || ''
        const expected = `Basic ${Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString('base64')}`
        if (authorization !== expected) return json(response, 401, { error: 'invalid_client' })
        return json(response, 200, {
          access_token: 'reference-access-token', token_type: 'Bearer', expires_in: 900
        })
      }
      if (url.pathname === '/api/requests' && request.method === 'POST') {
        if (!bearer(request)) return json(response, 401, { error: 'unauthorized' })
        const { raw, value } = await readBody(request)
        const key = request.headers['idempotency-key']
        if (!key || key.length > 200) return json(response, 400, { error: 'idempotency_key_required' })
        const fingerprint = createHash('sha256').update(raw).digest('hex')
        const previous = idempotency.get(key)
        if (previous && previous.fingerprint !== fingerprint) {
          return json(response, 409, { error: 'idempotency_conflict' })
        }
        if (previous) return json(response, 200, { ...previous.document, replayed: true })
        const id = `request-${stableId(`${key}:${Date.now()}`)}`
        const document = {
          id, status: 'PENDING', businessReference: value?.businessReference || null,
          variables: value?.variables || {}, createdAt: new Date().toISOString()
        }
        requests.set(id, document)
        idempotency.set(key, { fingerprint, document })
        return json(response, 201, document)
      }
      const requestMatch = url.pathname.match(/^\/api\/requests\/([^/]+)(\/cancel)?$/)
      if (requestMatch && (request.method === 'GET' || request.method === 'POST')) {
        if (!bearer(request)) return json(response, 401, { error: 'unauthorized' })
        const document = requests.get(requestMatch[1])
        if (!document) return json(response, 404, { error: 'not_found' })
        if (request.method === 'POST' && requestMatch[2]) {
          document.status = 'CANCELLED'
          document.cancelledAt = new Date().toISOString()
        }
        return json(response, 200, document)
      }
      if (request.method === 'POST' && url.pathname === '/webhooks/flow') {
        const { raw } = await readBody(request)
        const eventId = request.headers['flow-webhook-id']
        const timestamp = Number(request.headers['flow-webhook-timestamp'])
        const received = Math.floor(Date.now() / 1000)
        const expected = signature(eventId, timestamp, raw)
        if (!eventId || !Number.isInteger(timestamp)
          || Math.abs(received - timestamp) > MAX_TIMESTAMP_SKEW_SECONDS
          || !signatureMatches(request.headers['flow-webhook-signature'], expected)) {
          return json(response, 401, { error: 'invalid_signature' })
        }
        const replayed = webhookEvents.has(eventId)
        webhookEvents.add(eventId)
        return json(response, 200, { accepted: true, replayed })
      }
      return json(response, 404, { error: 'not_found' })
    } catch (error) {
      return json(response, 400, { error: error instanceof Error ? error.message : 'invalid_request' })
    }
  })
  return {
    server,
    listen: () => new Promise(resolve => server.listen(port, host, () => resolve(server.address()))),
    close: () => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
  }
}

const assert = (condition, message) => {
  if (!condition) throw new Error(message)
}

const selfTest = async () => {
  const app = createReferenceExternalSystem()
  const address = await app.listen()
  const baseUrl = `http://127.0.0.1:${address.port}`
  try {
    const tokenResponse = await fetch(`${baseUrl}/oauth/token`, {
      method: 'POST',
      headers: { authorization: `Basic ${Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString('base64')}` }
    })
    assert(tokenResponse.ok, 'token exchange failed')
    const token = (await tokenResponse.json()).access_token
    const body = JSON.stringify({ businessReference: { system: 'reference', id: 'REQ-1' }, variables: { amount: 10 } })
    const headers = { authorization: `Bearer ${token}`, 'content-type': 'application/json', 'idempotency-key': 'self-test-1' }
    const first = await fetch(`${baseUrl}/api/requests`, { method: 'POST', headers, body })
    assert(first.status === 201, 'first request was not created')
    const created = await first.json()
    const replay = await fetch(`${baseUrl}/api/requests`, { method: 'POST', headers, body })
    assert(replay.status === 200 && (await replay.json()).replayed === true, 'idempotent replay failed')
    const cancelled = await fetch(`${baseUrl}/api/requests/${created.id}/cancel`, {
      method: 'POST', headers: { authorization: `Bearer ${token}` }
    })
    assert((await cancelled.json()).status === 'CANCELLED', 'cancel failed')
    const eventId = 'event-self-test-1'
    const timestamp = Math.floor(Date.now() / 1000)
    const event = Buffer.from(JSON.stringify({ id: eventId, type: 'com.flow.process.completed.v1' }))
    const webhookHeaders = {
      'content-type': 'application/cloudevents+json', 'flow-webhook-id': eventId,
      'flow-webhook-timestamp': String(timestamp), 'flow-webhook-signature': signature(eventId, timestamp, event)
    }
    const webhook = await fetch(`${baseUrl}/webhooks/flow`, { method: 'POST', headers: webhookHeaders, body: event })
    assert((await webhook.json()).accepted === true, 'webhook was not accepted')
    const duplicate = await fetch(`${baseUrl}/webhooks/flow`, { method: 'POST', headers: webhookHeaders, body: event })
    assert((await duplicate.json()).replayed === true, 'webhook replay was not deduplicated')
    console.log('reference external system self-test passed: token, start, replay, cancel, webhook signature and deduplication')
  } finally {
    await app.close()
  }
}

if (process.env.REFERENCE_EXTERNAL_SELF_TEST === '1') {
  await selfTest()
} else {
  const app = createReferenceExternalSystem({
    port: Number(process.env.REFERENCE_EXTERNAL_PORT || 9089),
    host: process.env.REFERENCE_EXTERNAL_HOST || '0.0.0.0'
  })
  const address = await app.listen()
  console.log(`reference external system listening on http://127.0.0.1:${address.port}`)
  console.log('endpoints: /oauth/token, /api/requests, /api/requests/:id, /api/requests/:id/cancel, /webhooks/flow')
}
