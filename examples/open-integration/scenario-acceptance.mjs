import { createHmac } from 'node:crypto'

const required = name => {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

const baseUrl = required('FLOW_BASE_URL').replace(/\/+$/, '')
const clientId = required('FLOW_CLIENT_ID')
const clientSecret = required('FLOW_CLIENT_SECRET')
const scenarioKey = required('FLOW_SCENARIO_KEY')
const variables = JSON.parse(process.env.FLOW_INPUT_JSON || '{"requesterId":"reference-user"}')
const scopes = process.env.FLOW_SCOPES
  || 'process.instance.start process.instance.read process.instance.cancel process.task.read'
const referenceBaseUrl = (process.env.REFERENCE_BASE_URL || '').replace(/\/+$/, '')
const referenceClientId = process.env.REFERENCE_CLIENT_ID || 'reference-client'
const referenceClientSecret = process.env.REFERENCE_CLIENT_SECRET || 'reference-secret'
const referenceWebhookSecret = process.env.REFERENCE_WEBHOOK_SECRET || 'reference-webhook-secret'

const assert = (condition, message) => {
  if (!condition) throw new Error(message)
}

const rawRequest = async (base, path, options = {}) => {
  const response = await fetch(`${base}${path}`, {
    redirect: 'manual',
    signal: AbortSignal.timeout(Number(process.env.FLOW_HTTP_TIMEOUT_MS || 15_000)),
    ...options,
    headers: { accept: 'application/json', ...(options.headers || {}) }
  })
  const body = await response.json().catch(() => null)
  return { response, body }
}

const request = async (base, path, options = {}) => {
  const result = await rawRequest(base, path, options)
  assert(result.response.ok, `${path} returned HTTP ${result.response.status}: ${JSON.stringify(result.body)}`)
  return result
}

const token = async (base, id, secret, requestedScopes, tokenPath = '/oauth2/token') => {
  const basic = Buffer.from(`${id}:${secret}`, 'utf8').toString('base64')
  const result = await request(base, tokenPath, {
    method: 'POST',
    headers: {
      authorization: `Basic ${basic}`,
      'content-type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams({ grant_type: 'client_credentials', scope: requestedScopes })
  })
  assert(result.body?.access_token, 'token response does not contain access_token')
  return result.body.access_token
}

const flowToken = await token(baseUrl, clientId, clientSecret, scopes)
const authorization = { authorization: `Bearer ${flowToken}` }
const business = (id, version = 'v1') => ({
  system: process.env.FLOW_BUSINESS_SYSTEM || 'reference-system',
  type: process.env.FLOW_BUSINESS_TYPE || 'reference-request',
  id: process.env.FLOW_BUSINESS_ID || id,
  version
})

const start = async (idempotencyKey, businessId, input = variables) => rawRequest(
  baseUrl,
  '/api/open/v1/process-instances',
  {
    method: 'POST',
    headers: {
      ...authorization,
      'content-type': 'application/json',
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify({ scenarioKey, businessReference: business(businessId), variables: input })
  }
)

const runFlowContract = async () => {
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2)}`
  const idempotencyKey = `scenario-acceptance-${suffix}`
  const first = await start(idempotencyKey, `acceptance-${suffix}`)
  assert(first.response.status === 201, `scenario start failed: ${JSON.stringify(first.body)}`)
  assert(first.body?.data?.scenarioKey === scenarioKey, 'scenario key was not returned')
  assert(Number.isInteger(first.body?.data?.scenarioRevision), 'scenario revision was not returned')

  const replay = await start(idempotencyKey, `acceptance-${suffix}`)
  assert(replay.response.status === 200, 'repeated start did not return the existing instance')
  assert(replay.response.headers.get('idempotent-replay') === 'true', 'Idempotent-Replay header is missing')
  assert(replay.body?.data?.processInstanceId === first.body.data.processInstanceId, 'replay returned another instance')

  const concurrent = await Promise.all(
    Array.from({ length: Number(process.env.FLOW_CONCURRENT_STARTS || 4) }, (_, index) =>
      start(`${idempotencyKey}-concurrent-${index}`, `concurrent-${suffix}-${index}`)))
  assert(concurrent.every(result => result.response.status === 201), 'concurrent starts did not all succeed')

  const processId = first.body.data.processInstanceId
  const queried = await request(baseUrl, `/api/open/v1/process-instances/${encodeURIComponent(processId)}`, {
    headers: authorization
  })
  assert(queried.body?.data?.scenarioKey === scenarioKey, 'query lost scenario metadata')

  const tasks = await request(baseUrl, `/api/open/v1/process-instances/${encodeURIComponent(processId)}/tasks`, {
    headers: authorization
  })
  assert(Array.isArray(tasks.body?.data?.items), 'task query did not return a page')

  const cancelKey = `${idempotencyKey}-cancel`
  const cancelled = await rawRequest(baseUrl, `/api/open/v1/process-instances/${encodeURIComponent(processId)}/cancel`, {
    method: 'POST',
    headers: { ...authorization, 'content-type': 'application/json', 'Idempotency-Key': cancelKey },
    body: JSON.stringify({ reason: 'reference acceptance cancellation' })
  })
  assert([200, 409].includes(cancelled.response.status), `cancel returned unexpected HTTP ${cancelled.response.status}`)
  if (cancelled.response.status === 200) {
    assert(cancelled.body?.data?.status === 'TERMINATED', 'cancel did not terminate the process')
    const cancelReplay = await rawRequest(baseUrl, `/api/open/v1/process-instances/${encodeURIComponent(processId)}/cancel`, {
      method: 'POST',
      headers: { ...authorization, 'content-type': 'application/json', 'Idempotency-Key': cancelKey },
      body: JSON.stringify({ reason: 'reference acceptance cancellation' })
    })
    assert(cancelReplay.response.status === 200, 'repeated cancel did not replay')
    assert(cancelReplay.response.headers.get('idempotent-replay') === 'true', 'cancel replay header is missing')
  }

  const missingIdentity = await start(`${idempotencyKey}-missing-identity`, `missing-identity-${suffix}`, {})
  assert(missingIdentity.response.status === 422, 'missing identity was not rejected')
  assert(['IDENTITY_NOT_RESOLVED', 'VARIABLE_VALIDATION_FAILED'].includes(missingIdentity.body?.errorCode)
    || ['IDENTITY_NOT_RESOLVED', 'VARIABLE_VALIDATION_FAILED'].includes(missingIdentity.body?.data?.errorCode),
  `missing identity returned unexpected error: ${JSON.stringify(missingIdentity.body)}`)

  if (process.env.FLOW_SCENARIO_KEY_V2) {
    const second = await rawRequest(baseUrl, '/api/open/v1/process-instances', {
      method: 'POST',
      headers: {
        ...authorization,
        'content-type': 'application/json',
        'Idempotency-Key': `${idempotencyKey}-revision-v2`
      },
      body: JSON.stringify({
        scenarioKey: process.env.FLOW_SCENARIO_KEY_V2,
        businessReference: business(`revision-v2-${suffix}`, 'v2'),
        variables
      })
    })
    assert(second.response.status === 201, `revision switch start failed: ${JSON.stringify(second.body)}`)
    assert(second.body?.data?.scenarioKey === process.env.FLOW_SCENARIO_KEY_V2,
      'revision switch returned the wrong scenario')
    assert(second.body?.data?.scenarioRevision !== first.body.data.scenarioRevision,
      'revision switch did not pin a different revision')
  }

  if (process.env.FLOW_EXPIRED_ACCESS_TOKEN) {
    const expired = await rawRequest(baseUrl, '/api/open/v1/process-definitions', {
      headers: { authorization: `Bearer ${process.env.FLOW_EXPIRED_ACCESS_TOKEN}` }
    })
    assert(expired.response.status === 401, 'expired access token was accepted')
  }

  console.log(JSON.stringify({
    flow: 'passed',
    processInstanceId: processId,
    scenarioKey,
    scenarioRevision: first.body.data.scenarioRevision,
    concurrentStarts: concurrent.length,
    cancel: cancelled.response.status === 200 ? 'passed' : 'already-finished'
  }))
}

const runReferenceContract = async () => {
  if (!referenceBaseUrl) return
  const referenceToken = await token(
    referenceBaseUrl,
    referenceClientId,
    referenceClientSecret,
    'requests.read requests.write',
    process.env.REFERENCE_TOKEN_PATH || '/oauth/token')
  const headers = { authorization: `Bearer ${referenceToken}`, 'content-type': 'application/json' }
  const key = `reference-acceptance-${Date.now()}`
  const body = JSON.stringify({ businessReference: business(`receiver-${Date.now()}`), variables })
  const created = await request(referenceBaseUrl, '/api/requests', {
    method: 'POST', headers: { ...headers, 'idempotency-key': key }, body
  })
  assert(created.response.status === 201, 'reference system did not create a request')
  const replay = await request(referenceBaseUrl, '/api/requests', {
    method: 'POST', headers: { ...headers, 'idempotency-key': key }, body
  })
  assert(replay.body?.replayed === true, 'reference system did not replay a request')
  const eventId = `reference-event-${Date.now()}`
  const timestamp = Math.floor(Date.now() / 1000)
  const event = Buffer.from(JSON.stringify({ id: eventId, type: 'com.flow.process.completed.v1' }))
  const signature = `v1=${createHmac('sha256', referenceWebhookSecret)
    .update(`${eventId}.${timestamp}.`)
    .update(event)
    .digest('base64')}`
  const webhookHeaders = {
    'content-type': 'application/cloudevents+json',
    'flow-webhook-id': eventId,
    'flow-webhook-timestamp': String(timestamp),
    'flow-webhook-signature': signature
  }
  const accepted = await request(referenceBaseUrl, '/webhooks/flow', {
    method: 'POST', headers: webhookHeaders, body: event
  })
  assert(accepted.body?.accepted === true && accepted.body?.replayed === false, 'reference webhook acceptance failed')
  const duplicate = await request(referenceBaseUrl, '/webhooks/flow', {
    method: 'POST', headers: webhookHeaders, body: event
  })
  assert(duplicate.body?.replayed === true, 'reference webhook deduplication failed')
  console.log(JSON.stringify({ reference: 'passed', webhook: 'signed-and-deduplicated' }))
}

await runFlowContract()
await runReferenceContract()
