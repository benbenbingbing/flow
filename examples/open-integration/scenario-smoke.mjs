const required = name => {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

const baseUrl = required('FLOW_BASE_URL').replace(/\/+$/, '')
const clientId = required('FLOW_CLIENT_ID')
const clientSecret = required('FLOW_CLIENT_SECRET')
const scenarioKey = required('FLOW_SCENARIO_KEY')
const variables = JSON.parse(process.env.FLOW_INPUT_JSON || '{}')
const businessReference = {
  system: process.env.FLOW_BUSINESS_SYSTEM || 'reference-system',
  type: process.env.FLOW_BUSINESS_TYPE || 'reference-request',
  id: process.env.FLOW_BUSINESS_ID || `smoke-${Date.now()}`
}
const basic = Buffer.from(`${clientId}:${clientSecret}`, 'utf8').toString('base64')
const request = async (path, options = {}) => {
  const response = await fetch(`${baseUrl}${path}`, {
    redirect: 'manual',
    signal: AbortSignal.timeout(15_000),
    ...options,
    headers: { accept: 'application/json', ...(options.headers || {}) }
  })
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(`${path} returned HTTP ${response.status}: ${JSON.stringify(body)}`)
  return { response, body }
}

const token = await request('/oauth2/token', {
  method: 'POST',
  headers: {
    authorization: `Basic ${basic}`,
    'content-type': 'application/x-www-form-urlencoded'
  },
  body: new URLSearchParams({
    grant_type: 'client_credentials',
    scope: 'process.instance.start process.instance.read'
  })
})
if (!token.body?.access_token) throw new Error('token response does not contain access_token')
const authorization = { authorization: `Bearer ${token.body.access_token}` }
const idempotencyKey = `scenario-smoke-${Date.now()}`
const start = await request('/api/open/v1/process-instances', {
  method: 'POST',
  headers: { ...authorization, 'content-type': 'application/json', 'Idempotency-Key': idempotencyKey },
  body: JSON.stringify({ scenarioKey, businessReference, variables })
})
if (start.body?.code !== 201 || start.body?.data?.scenarioKey !== scenarioKey) {
  throw new Error(`scenario start response does not match contract: ${JSON.stringify(start.body)}`)
}
const replay = await request('/api/open/v1/process-instances', {
  method: 'POST',
  headers: { ...authorization, 'content-type': 'application/json', 'Idempotency-Key': idempotencyKey },
  body: JSON.stringify({ scenarioKey, businessReference, variables })
})
if (replay.response.headers.get('idempotent-replay') !== 'true') {
  throw new Error('repeated start did not return Idempotent-Replay: true')
}

const processId = start.body.data.processInstanceId
const deadline = Date.now() + Number(process.env.FLOW_MAX_WAIT_SECONDS || 30) * 1000
let latest = start.body.data
while (Date.now() < deadline && latest.status === 'RUNNING') {
  await new Promise(resolve => setTimeout(resolve, 1000))
  const current = await request(`/api/open/v1/process-instances/${encodeURIComponent(processId)}`, {
    headers: authorization
  })
  latest = current.body.data
}
console.log(`scenario contract passed: process=${processId} status=${latest.status} scenario=${latest.scenarioKey}`)
