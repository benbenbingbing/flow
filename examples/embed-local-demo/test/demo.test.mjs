import assert from 'node:assert/strict'
import { generateKeyPairSync, verify } from 'node:crypto'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import {
  DemoConfigurationError,
  FlowRemoteError,
  buildConfig,
  isApprovedEmbedProxyRequest,
  normalizeLaunchIntent,
  signUserAssertion
} from '../server.mjs'

const testDirectory = path.dirname(fileURLToPath(import.meta.url))
const demoDirectory = path.resolve(testDirectory, '..')

function decodePart(value) {
  return JSON.parse(Buffer.from(value, 'base64url').toString('utf8'))
}

test('默认使用隔离的 3443 宿主与 8443 Embed Origin', () => {
  const config = buildConfig({})
  assert.equal(config.hostOrigin, 'https://localhost:3443')
  assert.equal(config.embedOrigin, 'https://localhost:8443')
  assert.equal(config.flowBaseUrl, 'http://127.0.0.1:8080')
  assert.deepEqual(config.allowedEntryModes, ['CREATE', 'VIEW'])
})

test('拒绝让宿主和 Embed 共用 Origin', () => {
  assert.throws(() => buildConfig({
    FLOW_DEMO_HOST_ORIGIN: 'https://localhost:3443',
    FLOW_DEMO_EMBED_ORIGIN: 'https://localhost:3443'
  }), DemoConfigurationError)
})

test('CREATE 必须完全省略 recordId，VIEW 必须携带安全 recordId', () => {
  assert.deepEqual(
    normalizeLaunchIntent({ mode: 'CREATE', theme: 'light' }),
    { mode: 'CREATE', theme: 'light' }
  )
  assert.throws(
    () => normalizeLaunchIntent({ mode: 'CREATE', recordId: null }),
    error => error instanceof FlowRemoteError && error.status === 400
  )
  assert.deepEqual(
    normalizeLaunchIntent({ mode: 'VIEW', recordId: 'record-1001', theme: 'dark' }),
    { mode: 'VIEW', recordId: 'record-1001', theme: 'dark' }
  )
  assert.throws(
    () => normalizeLaunchIntent({ mode: 'VIEW', recordId: '../record' }),
    error => error instanceof FlowRemoteError && error.errorCode === 'FLOW_DEMO_RECORD_INVALID'
  )
})

test('浏览器不能覆盖 View、人员、Origin 或 Context 坐标', () => {
  for (const forbidden of ['viewKey', 'subject', 'parentOrigin', 'context', 'channelId']) {
    assert.throws(
      () => normalizeLaunchIntent({ mode: 'CREATE', [forbidden]: 'forbidden' }),
      error => error instanceof FlowRemoteError
        && error.errorCode === 'FLOW_DEMO_REQUEST_INVALID'
    )
  }
})

test('RS256 人员断言包含固定身份坐标、60 秒有效期和唯一 jti', () => {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
  const token = signUserAssertion({
    privateKey,
    issuer: 'https://id.embed-demo.local',
    audience: 'flow-embed-launch',
    subject: 'demo-lisi-001',
    keyId: 'embed-demo-test-key',
    nowSeconds: 1_788_000_000,
    jwtId: 'assertion-once-001'
  })
  const [headerPart, claimsPart, signaturePart] = token.split('.')
  const header = decodePart(headerPart)
  const claims = decodePart(claimsPart)
  assert.deepEqual(header, { alg: 'RS256', kid: 'embed-demo-test-key', typ: 'JWT' })
  assert.equal(claims.iss, 'https://id.embed-demo.local')
  assert.equal(claims.sub, 'demo-lisi-001')
  assert.equal(claims.aud, 'flow-embed-launch')
  assert.equal(claims.exp - claims.iat, 60)
  assert.equal(claims.jti, 'assertion-once-001')
  assert.equal(verify(
    'RSA-SHA256',
    Buffer.from(`${headerPart}.${claimsPart}`),
    publicKey,
    Buffer.from(signaturePart, 'base64url')
  ), true)
})

test('示例源码不把敏感凭据写入浏览器 storage、URL 或日志', () => {
  const browserSource = readFileSync(path.join(demoDirectory, 'public/app.js'), 'utf8')
  const hostHtml = readFileSync(path.join(demoDirectory, 'public/index.html'), 'utf8')
  const hostStyles = readFileSync(path.join(demoDirectory, 'public/styles.css'), 'utf8')
  const readme = readFileSync(path.join(demoDirectory, 'README.md'), 'utf8')
  const serverSource = readFileSync(path.join(demoDirectory, 'server.mjs'), 'utf8')
  assert.doesNotMatch(browserSource, /localStorage|sessionStorage|document\.cookie/)
  assert.doesNotMatch(browserSource, /console\.(?:log|info|debug|warn|error)/)
  assert.match(hostHtml, /每次新 Launch[\s\S]*最新 ACTIVE/)
  assert.match(hostHtml, /不会根据 Embed 参数重新绘制字段/)
  assert.match(hostHtml, /Flow 原生页面运行时/)
  assert.doesNotMatch(hostHtml, /Flow 原生表单运行时|zdwreq-form-demo/)
  assert.match(browserSource, /runtimeViewKey\.textContent = config\.viewKey/)
  const topbarRule = hostStyles.match(/\.topbar\s*\{[\s\S]*?\}/)?.[0] || ''
  assert.match(topbarRule, /position:\s*relative/)
  assert.doesNotMatch(topbarRule, /position:\s*(?:sticky|fixed)/)
  assert.match(readme, /平台内建控件来自目标最新 ACTIVE 发布版/s)
  assert.doesNotMatch(readme, /releaseSelector|FOLLOW_ACTIVE|PINNED/)
  assert.doesNotMatch(readme, /FORM r\d+/)
  for (const statement of serverSource.matchAll(/console\.(?:log|info|debug|warn|error)\(([^\n]*)/g)) {
    assert.doesNotMatch(statement[1], /clientSecret|accessToken|assertion|launchCode/)
  }
})

test('关闭和重新打开会等待 Session Logout 并阻止并发 Launch', () => {
  const browserSource = readFileSync(path.join(demoDirectory, 'public/app.js'), 'utf8')
  const readme = readFileSync(path.join(demoDirectory, 'README.md'), 'utf8')
  const destroyIndex = browserSource.indexOf('await destroyWidget({ showIdleStatus: false })')
  const launchIndex = browserSource.indexOf("fetch('/partner-api/embed-launch'")

  assert.match(browserSource, /await retiringWidget\.destroy\(\)/)
  assert.match(browserSource, /if \(destroyPromise\) return destroyPromise/)
  assert.match(browserSource, /if \(launchInProgress \|\| destroyPromise\) return/)
  assert.match(browserSource, /async function closeEmbed\(\)[\s\S]*await destroyWidget\(\)/)
  assert.match(
    browserSource,
    /event\.type === 'close\.requested'[\s\S]*void closeEmbed\(\)/
  )
  assert.match(
    browserSource,
    /await destroyWidget\(\{ showIdleStatus: false \}\)[\s\S]*?catch \(error\) \{[\s\S]*?host\.destroy\.failed[\s\S]*?return\s*\}/
  )
  assert.ok(destroyIndex >= 0 && destroyIndex < launchIndex)
  assert.match(readme, /await widget\.destroy\(\)/)
  assert.match(readme, /close\.requested/)
  assert.doesNotMatch(browserSource, /logoutSequence|embed-lifecycle/)
})

test('Embed Origin 只代理控制面或携带 opaque 委托身份的原生 Flow API', () => {
  const request = (headers, method = 'GET') => ({ headers, method })
  assert.equal(isApprovedEmbedProxyRequest(request({}), '/embed/v1/launches/lch_1'), true)
  assert.equal(isApprovedEmbedProxyRequest(request({}), '/api/embed/v1/runtime/bootstrap'), true)
  assert.equal(isApprovedEmbedProxyRequest(request({}), '/api/entity/code/ZDWREQ'), false)
  assert.equal(isApprovedEmbedProxyRequest(request({}), '/uploads/example.png'), true)
  assert.equal(isApprovedEmbedProxyRequest(request({}, 'POST'), '/uploads/example.png'), false)
  assert.equal(isApprovedEmbedProxyRequest(request({
    authorization: `Bearer ${'a'.repeat(43)}`,
    'x-flow-embed-protocol': '1'
  }), '/api/entity/code/ZDWREQ'), true)
  for (const controlPath of [
    '/api/auth/current',
    '/api/embed-management/v1/views',
    '/api/open/v1/processes',
    '/api/integration-applications/app-1/secrets'
  ]) {
    assert.equal(isApprovedEmbedProxyRequest(request({
      authorization: `Bearer ${'a'.repeat(43)}`,
      'x-flow-embed-protocol': '1'
    }), controlPath), false)
  }
  assert.equal(isApprovedEmbedProxyRequest(request({
    authorization: 'Bearer normal jwt with spaces',
    'x-flow-embed-protocol': '1'
  }), '/api/entity/code/ZDWREQ'), false)
  assert.equal(isApprovedEmbedProxyRequest(request({
    authorization: `Bearer ${'a'.repeat(43)}`
  }), '/api/entity/code/ZDWREQ'), false)
  assert.equal(isApprovedEmbedProxyRequest(request({
    authorization: `Bearer ${'a'.repeat(43)}`,
    'x-flow-embed-protocol': '1'
  }), '/oauth2/token'), false)
})
