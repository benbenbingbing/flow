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
  buildFlowLaunchPayload,
  isApprovedEmbedProxyRequest,
  normalizeLaunchIntent,
  normalizeLaunchRequest,
  validateLaunchDocument,
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
  assert.deepEqual(config.targets.map(target => ({
    key: target.key,
    viewKey: target.viewKey,
    surfaceType: target.surfaceType,
    allowedEntryModes: target.allowedEntryModes
  })), [
    { key: 'list', viewKey: 'req-list', surfaceType: 'LIST', allowedEntryModes: ['LIST'] },
    {
      key: 'form',
      viewKey: 'zdwreq-form-demo',
      surfaceType: 'FORM',
      allowedEntryModes: ['CREATE', 'VIEW']
    }
  ])
})

test('旧单 View 环境变量仍保持原启动命令语义', () => {
  const config = buildConfig({
    FLOW_DEMO_VIEW_KEY: 'legacy-list',
    FLOW_DEMO_ALLOWED_ENTRY_MODES: 'LIST'
  })
  assert.deepEqual(config.targets.map(target => ({
    key: target.key,
    viewKey: target.viewKey,
    surfaceType: target.surfaceType,
    allowedEntryModes: target.allowedEntryModes
  })), [
    { key: 'default', viewKey: 'legacy-list', surfaceType: 'LIST', allowedEntryModes: ['LIST'] }
  ])
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
    { mode: 'CREATE', theme: 'light', formPresentation: 'seamless' }
  )
  assert.throws(
    () => normalizeLaunchIntent({ mode: 'CREATE', recordId: null }),
    error => error instanceof FlowRemoteError && error.status === 400
  )
  assert.deepEqual(
    normalizeLaunchIntent({
      mode: 'VIEW',
      recordId: 'record-1001',
      theme: 'dark',
      formPresentation: 'dialog'
    }),
    { mode: 'VIEW', recordId: 'record-1001', theme: 'dark', formPresentation: 'dialog' }
  )
  assert.throws(
    () => normalizeLaunchIntent({ mode: 'VIEW', recordId: '../record' }),
    error => error instanceof FlowRemoteError && error.errorCode === 'FLOW_DEMO_RECORD_INVALID'
  )
})

test('表单展示缺省为 seamless，并严格拒绝枚举外的客户端值', () => {
  assert.equal(
    normalizeLaunchIntent({ mode: 'CREATE', formPresentation: null }).formPresentation,
    'seamless'
  )
  for (const formPresentation of ['', ' seamless ', 'SEAMLESS', 'drawer', 1, {}]) {
    assert.throws(
      () => normalizeLaunchIntent({ mode: 'CREATE', formPresentation }),
      error => error instanceof FlowRemoteError
        && error.errorCode === 'FLOW_DEMO_FORM_PRESENTATION_INVALID'
    )
  }
})

test('Partner Backend 把校验后的展示方式写入 Flow Launch ui', () => {
  const config = buildConfig({})
  const target = config.targets.find(candidate => candidate.key === 'form')
  const intent = normalizeLaunchIntent({
    mode: 'VIEW',
    recordId: 'record-1001',
    theme: 'dark',
    formPresentation: 'dialog'
  })
  const payload = buildFlowLaunchPayload(
    config,
    target,
    intent,
    'signed-user-assertion',
    'channel-12345678'
  )
  assert.deepEqual(payload.ui, {
    locale: 'zh-CN',
    theme: 'dark',
    formPresentation: 'dialog'
  })
  assert.deepEqual(payload.entry, { mode: 'VIEW', recordId: 'record-1001' })
})

test('浏览器不能覆盖 View、人员、Origin 或 Context 坐标', () => {
  const targets = buildConfig({}).targets
  for (const forbidden of ['viewKey', 'subject', 'parentOrigin', 'context', 'channelId']) {
    assert.throws(
      () => normalizeLaunchRequest({
        targetKey: 'form',
        mode: 'CREATE',
        [forbidden]: 'forbidden'
      }, targets),
      error => error instanceof FlowRemoteError
        && error.errorCode === 'FLOW_DEMO_REQUEST_INVALID'
    )
  }
})

test('目标白名单分别约束 LIST 与 FORM 入口', () => {
  const targets = buildConfig({}).targets
  const listRequest = normalizeLaunchRequest({
    targetKey: 'list',
    mode: 'LIST',
    theme: 'light'
  }, targets)
  assert.equal(listRequest.target.viewKey, 'req-list')
  assert.deepEqual(listRequest.intent, {
    mode: 'LIST',
    theme: 'light',
    formPresentation: 'seamless'
  })

  const formRequest = normalizeLaunchRequest({
    targetKey: 'form',
    mode: 'VIEW',
    recordId: 'record-1001',
    theme: 'dark',
    formPresentation: 'dialog'
  }, targets)
  assert.equal(formRequest.target.viewKey, 'zdwreq-form-demo')
  assert.equal(formRequest.intent.recordId, 'record-1001')
  assert.equal(formRequest.intent.formPresentation, 'dialog')

  for (const request of [
    { targetKey: 'list', mode: 'CREATE' },
    { targetKey: 'form', mode: 'LIST' },
    { targetKey: 'unknown', mode: 'LIST' },
    { targetKey: ' LIST ', mode: 'LIST' }
  ]) {
    assert.throws(
      () => normalizeLaunchRequest(request, targets),
      error => error instanceof FlowRemoteError
        && ['FLOW_DEMO_ENTRY_NOT_ALLOWED', 'FLOW_DEMO_TARGET_NOT_ALLOWED']
          .includes(error.errorCode)
    )
  }
})

test('Flow Launch 回包必须匹配后端选定的 View 与页面类型', () => {
  const target = buildConfig({}).targets[0]
  const response = {
    status: 201,
    body: Buffer.from(JSON.stringify({
      code: 201,
      data: {
        launchId: `lch_${'a'.repeat(16)}`,
        launchCode: 'b'.repeat(43),
        view: { key: target.viewKey, surfaceType: target.surfaceType }
      }
    }))
  }
  assert.equal(validateLaunchDocument(response, target).view.key, 'req-list')
  assert.throws(
    () => validateLaunchDocument(response, { ...target, viewKey: 'other-list' }),
    error => error instanceof FlowRemoteError
      && error.errorCode === 'FLOW_DEMO_LAUNCH_TARGET_MISMATCH'
  )
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
  assert.match(browserSource, /targetKey: target\?\.key/)
  assert.match(browserSource, /formPresentation: presentation/)
  assert.match(hostHtml, /id="form-presentation"[\s\S]*value="seamless" selected[\s\S]*value="dialog"/)
  assert.match(hostHtml, /表单展示（含列表内打开）/)
  assert.doesNotMatch(hostHtml, /id="form-presentation-field"\s+hidden/)
  assert.match(browserSource, /runtimeViewKey\.textContent = event\.payload\?\.viewKey/)
  const topbarRule = hostStyles.match(/\.topbar\s*\{[\s\S]*?\}/)?.[0] || ''
  assert.match(topbarRule, /position:\s*relative/)
  assert.doesNotMatch(topbarRule, /position:\s*(?:sticky|fixed)/)
  assert.match(readme, /平台内建控件来自目标最新 ACTIVE 发布版/s)
  assert.doesNotMatch(readme, /releaseSelector|FOLLOW_ACTIVE|PINNED/)
  assert.doesNotMatch(readme, /FORM r\d+/)
  assert.match(serverSource, /grant_type: 'client_credentials'/)
  assert.doesNotMatch(serverSource, /scope:\s*['"]embed\.launch['"]/)
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
  assert.match(browserSource, /if \(launchInProgress \|\| destroyPromise \|\| logoutUnconfirmed\) return/)
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
    '/api/open/v1/embed-launches',
    '/api/integration-applications/app-1/credentials/rotate'
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
