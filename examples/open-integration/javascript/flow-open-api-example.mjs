const required = name => {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

const baseUrl = required('FLOW_BASE_URL').replace(/\/+$/, '')
const clientId = required('FLOW_CLIENT_ID')
const clientSecret = required('FLOW_CLIENT_SECRET')
const basic = Buffer.from(`${clientId}:${clientSecret}`, 'utf8').toString('base64')

const tokenResponse = await fetch(`${baseUrl}/oauth2/token`, {
  method: 'POST',
  redirect: 'manual',
  signal: AbortSignal.timeout(10_000),
  headers: {
    accept: 'application/json',
    authorization: `Basic ${basic}`,
    'content-type': 'application/x-www-form-urlencoded'
  },
  body: new URLSearchParams({
    grant_type: 'client_credentials',
    scope: 'process.definition.read'
  })
})
if (!tokenResponse.ok) {
  throw new Error(`token endpoint returned HTTP ${tokenResponse.status}`)
}
const tokenDocument = await tokenResponse.json()
if (!tokenDocument.access_token || tokenDocument.token_type !== 'Bearer') {
  throw new Error('token response does not match the V1 contract')
}

const definitionsResponse = await fetch(
  `${baseUrl}/api/open/v1/process-definitions?limit=20`,
  {
    redirect: 'manual',
    signal: AbortSignal.timeout(10_000),
    headers: {
      accept: 'application/json',
      authorization: `Bearer ${tokenDocument.access_token}`
    }
  }
)
if (!definitionsResponse.ok) {
  throw new Error(`list process definitions returned HTTP ${definitionsResponse.status}`)
}
const definitions = await definitionsResponse.json()
if (
  definitions.code !== 200
  || definitions.errorCode !== null
  || !Array.isArray(definitions.data?.items)
) {
  throw new Error('process definition response does not match the V1 contract')
}

console.log(
  `JavaScript contract passed: ${definitions.data.items.length} process definitions, `
    + `traceId=${definitions.traceId || 'missing'}`
)
