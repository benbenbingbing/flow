import assert from 'node:assert/strict'
import { execFileSync, spawn } from 'node:child_process'
import {
  existsSync,
  mkdtempSync,
  readFileSync,
  rmSync
} from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sdkRoot = path.join(webRoot, 'packages/flow-embed-sdk/src')
const embedAssetsRoot = path.join(webRoot, 'dist/embed-assets')
const launchId = 'lch_browser_0123456789ab'
const channelId = 'browser:channel_0123456789'
const launchCode = 'B'.repeat(43)
const accessToken = 'embed_browser_access_token_0123456789'
const timeoutMs = 20_000

function delay(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}

async function waitFor(check, description, milliseconds = timeoutMs) {
  const deadline = Date.now() + milliseconds
  let lastError
  while (Date.now() < deadline) {
    try {
      const value = await check()
      if (value) return value
    } catch (error) {
      lastError = error
    }
    await delay(75)
  }
  throw new Error(`等待超时：${description}${lastError ? `；${lastError.message}` : ''}`)
}

function findChrome() {
  const configured = String(process.env.FLOW_EMBED_E2E_CHROME_PATH || '').trim()
  const candidates = [
    configured,
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser'
  ].filter(Boolean)
  return candidates.find(candidate => existsSync(candidate)) || ''
}

function requireChrome() {
  const chromePath = findChrome()
  if (chromePath) return chromePath
  const message = [
    'Embed 浏览器 E2E 需要 Chrome/Chromium。',
    '可通过 FLOW_EMBED_E2E_CHROME_PATH 指定二进制；',
    '本地确需跳过时显式设置 FLOW_EMBED_E2E_ALLOW_MISSING_BROWSER=1（CI 禁止跳过）。'
  ].join('')
  if (process.env.FLOW_EMBED_E2E_ALLOW_MISSING_BROWSER === '1'
    && process.env.CI !== 'true'
    && process.env.FLOW_EMBED_E2E_REQUIRE_BROWSER !== '1') {
    console.warn(`SKIP: ${message}`)
    process.exit(0)
  }
  throw new Error(message)
}

function generateCertificate(directory) {
  const keyPath = path.join(directory, 'fixture-key.pem')
  const certificatePath = path.join(directory, 'fixture-cert.pem')
  try {
    execFileSync('openssl', [
      'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-sha256', '-days', '1',
      '-keyout', keyPath,
      '-out', certificatePath,
      '-subj', '/CN=127.0.0.1',
      '-addext', 'subjectAltName=IP:127.0.0.1,DNS:localhost'
    ], { stdio: 'ignore' })
  } catch (cause) {
    throw new Error('无法生成浏览器 E2E 临时 HTTPS 证书，请确认 openssl 可用', { cause })
  }
  return {
    key: readFileSync(keyPath),
    cert: readFileSync(certificatePath)
  }
}

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      server.off('error', reject)
      resolve(server.address().port)
    })
  })
}

function closeServer(server) {
  return new Promise(resolve => {
    if (!server?.listening) return resolve()
    server.close(() => resolve())
    server.closeAllConnections?.()
  })
}

function readRequestBody(request) {
  return new Promise((resolve, reject) => {
    let body = ''
    request.setEncoding('utf8')
    request.on('data', chunk => {
      body += chunk
      if (body.length > 2 * 1024 * 1024) request.destroy(new Error('fixture body too large'))
    })
    request.on('end', () => resolve(body))
    request.on('error', reject)
  })
}

function send(response, status, body, headers = {}) {
  response.writeHead(status, {
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    ...headers
  })
  response.end(body)
}

function sendJson(response, status, value) {
  send(response, status, JSON.stringify(value), {
    'Content-Type': 'application/json;charset=UTF-8'
  })
}

function serveFile(response, filename, contentType) {
  send(response, 200, readFileSync(filename), { 'Content-Type': contentType })
}

function embedEntryHtml(parentOrigin) {
  const entry = Buffer.from(JSON.stringify({
    launchId,
    expectedParentOrigin: parentOrigin,
    channelId,
    protocolVersion: 'flow-embed/1'
  })).toString('base64url')
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="flow-embed-entry" content="${entry}">
  <title>Flow Embed Browser E2E</title>
  <link rel="stylesheet" href="/embed-assets/embed-main.css">
  <script type="module" src="/embed-assets/embed-main.js"></script>
</head>
<body><div id="app"></div></body>
</html>`
}

function createBootstrap() {
  return {
    session: {
      id: 'ems_browser_001',
      expiresAt: '2099-08-28T09:00:00.000Z',
      idleExpiresAt: '2099-08-28T08:35:00.000Z'
    },
    actor: { displayName: '浏览器验收用户' },
    view: {
      key: 'browser-work-orders',
      name: '供应商工单',
      surfaceType: 'LIST',
      revision: 1,
      entryMode: 'LIST'
    },
    capabilities: ['LIST_QUERY', 'SELECTION_RETURN', 'RECORD_VIEW', 'RECORD_CREATE'],
    ui: {
      locale: 'zh-CN',
      theme: 'light',
      showSearch: true,
      showPagination: true,
      showToolbar: true,
      pageSize: 20,
      heightMode: 'AUTO'
    },
    limits: {
      maxPageSize: 100,
      maxPayloadBytes: 1_048_576,
      maxSelectionSize: 10
    }
  }
}

function createListSchema() {
  return {
    view: { key: 'browser-work-orders', surfaceType: 'LIST', revision: 1 },
    entity: { code: 'work_order', name: '工单' },
    list: {
      selection: { mode: 'SINGLE', valueField: 'id', returnableFields: ['code'] },
      pagination: { allowTotal: false, maxPageSize: 100 },
      columns: [
        { code: 'code', label: '工单号', type: 'TEXT', width: 180, sortable: false },
        { code: 'status', label: '状态', type: 'SELECT', width: 120, sortable: false,
          options: [{ label: '处理中', value: 'PROCESSING' }] }
      ],
      filters: [{ code: 'code', label: '工单号', type: 'TEXT', operator: 'CONTAINS' }]
    },
    form: null,
    actions: [
      {
        key: 'create', label: '新建', placement: 'TOOLBAR', kind: 'NAVIGATION',
        transport: 'LOCAL_FORM', recordMode: 'NONE', selectionMode: 'NONE',
        requiresRecordVersion: false, idempotencyRequired: false, enabled: true,
        disabledReason: null
      },
      {
        key: 'view', label: '查看', placement: 'ROW', kind: 'NAVIGATION',
        transport: 'LOCAL_FORM', recordMode: 'CURRENT', selectionMode: 'NONE',
        requiresRecordVersion: false, idempotencyRequired: false, enabled: true,
        disabledReason: null
      }
    ]
  }
}

function record() {
  return {
    id: 'record-browser-1',
    recordVersion: null,
    values: {
      code: 'WO-BROWSER-001',
      status: 'PROCESSING',
      internalSecret: 'must-not-cross-browser-boundary'
    },
    meta: { updatedAt: '2026-08-28T08:20:00.000Z' },
    actions: { view: { visible: true, enabled: true, reason: null } }
  }
}

function formResult(mode, values = {}) {
  const viewRecord = mode === 'VIEW'
    ? {
        id: 'record-browser-1',
        recordVersion: null,
        values: {
          title: '浏览器查看工单',
          status: 'PROCESSING',
          internalSecret: 'must-not-cross-browser-boundary'
        },
        meta: {
          createdAt: '2026-08-28T08:00:00.000Z',
          updatedAt: '2026-08-28T08:20:00.000Z'
        }
      }
    : null
  return {
    mode,
    record: viewRecord,
    form: {
      title: '工单表单',
      layout: { type: 'GRID' },
      returnableFields: ['title'],
      fields: [
        {
          code: 'title', label: '标题', type: 'TEXT', required: true,
          readOnly: mode === 'VIEW', hidden: false,
          defaultValue: values.title ?? '', validation: { minLength: 2, maxLength: 100 },
          options: [], optionSource: null, lookupSource: null,
          layout: { span: 24 },
          fieldState: { visible: true, writable: mode === 'CREATE' }
        },
        {
          code: 'status', label: '状态', type: 'SELECT', required: false,
          readOnly: mode === 'VIEW', hidden: false,
          defaultValue: values.status ?? 'PROCESSING', validation: {},
          options: [{ label: '处理中', value: 'PROCESSING' }],
          optionSource: null, lookupSource: null,
          layout: { span: 12 },
          fieldState: { visible: true, writable: mode === 'CREATE' }
        }
      ],
      actions: mode === 'CREATE'
        ? [{
            key: 'save', label: '保存', placement: 'FORM', kind: 'MUTATION',
            transport: 'RECORD_CREATE', recordMode: 'NONE', selectionMode: 'NONE',
            requiresRecordVersion: false, idempotencyRequired: true,
            enabled: true, disabledReason: null
          }]
        : []
    }
  }
}

function parentMainSource(embedOrigin, hostileOrigin) {
  return `import { mount } from '/sdk/index.js'
let oneTimeCode = ${JSON.stringify(launchCode)}
const events = []
const violations = []
const widget = mount({
  container: document.querySelector('#embed-root'),
  embedUrl: ${JSON.stringify(`${embedOrigin}/embed/v1/launches/${launchId}`)},
  targetOrigin: ${JSON.stringify(embedOrigin)},
  launchId: ${JSON.stringify(launchId)},
  launchCode: oneTimeCode,
  channelId: ${JSON.stringify(channelId)},
  handshakeTimeoutMs: 15000,
  onEvent(event) { events.push(JSON.parse(JSON.stringify(event))) },
  onViolation(error) { violations.push(error.errorCode) }
})
oneTimeCode = ''
function ready(childNonce) {
  return {
    protocol: 'flow-embed/1', type: 'ready',
    launchId: ${JSON.stringify(launchId)}, channelId: ${JSON.stringify(channelId)},
    childNonce, supportedVersions: ['flow-embed/1']
  }
}
window.__flowHarness = {
  snapshot() {
    return {
      state: widget.getState(),
      iframeUrl: widget.iframe?.src || '',
      iframeCount: document.querySelectorAll('#embed-root iframe').length,
      launchCodeInWidget: widget._launchCode,
      events: JSON.parse(JSON.stringify(events)),
      violations: [...violations],
      localStorage: { ...localStorage },
      sessionStorage: { ...sessionStorage },
      cookie: document.cookie
    }
  },
  injectForgedReady() {
    const validNonce = 'Q'.repeat(43)
    window.dispatchEvent(new MessageEvent('message', {
      data: ready(validNonce), origin: ${JSON.stringify(embedOrigin)}, source: window
    }))
    window.dispatchEvent(new MessageEvent('message', {
      data: ready(validNonce), origin: ${JSON.stringify(hostileOrigin)},
      source: widget.iframe.contentWindow
    }))
    window.dispatchEvent(new MessageEvent('message', {
      data: ready('short'), origin: ${JSON.stringify(embedOrigin)},
      source: widget.iframe.contentWindow
    }))
    return [...violations]
  },
  refresh() { return widget.refresh() },
  destroy() { widget.destroy(); return this.snapshot() }
}`
}

async function main() {
  const chromePath = requireChrome()
  for (const filename of ['embed-main.js', 'embed-main.css']) {
    assert.ok(existsSync(path.join(embedAssetsRoot, filename)),
      `缺少 ${filename}，请先执行 npm run build:embed`)
  }
  assert.doesNotMatch(
    readFileSync(path.join(embedAssetsRoot, 'embed-main.js'), 'utf8'),
    /\bprocess\.env\b/,
    'Embed 浏览器 bundle 不得保留 Node process.env'
  )

  const temporaryDirectory = mkdtempSync(path.join(os.tmpdir(), 'flow-embed-browser-e2e-'))
  let certificate
  const state = {
    assetReleased: false,
    heldAssetResponses: [],
    requestLog: [],
    exchangeBodies: [],
    createBodies: [],
    listQueryCount: 0,
    expireNextListQuery: false,
    parentOrigin: '',
    hostileOrigin: '',
    embedOrigin: ''
  }
  let chrome
  let cdp
  let embedServer
  let parentServer
  let hostileServer

  try {
    certificate = generateCertificate(temporaryDirectory)
    embedServer = https.createServer(certificate, async (request, response) => {
      try {
        const url = new URL(request.url, 'https://fixture.invalid')
        state.requestLog.push({
          method: request.method,
          url: request.url,
          headers: { ...request.headers }
        })
        if (request.method === 'GET' && url.pathname === `/embed/v1/launches/${launchId}`
          && !url.search && !url.hash) {
          const csp = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; object-src 'none'; "
            + `base-uri 'none'; form-action 'none'; frame-ancestors ${state.parentOrigin}`
          send(response, 200, embedEntryHtml(state.parentOrigin), {
            'Content-Type': 'text/html;charset=UTF-8',
            'Content-Security-Policy': csp,
            'Referrer-Policy': 'no-referrer',
            'Permissions-Policy': 'camera=(), microphone=(), geolocation=()'
          })
          return
        }
        if (request.method === 'GET' && url.pathname === '/embed-assets/embed-main.js') {
          if (!state.assetReleased) {
            state.heldAssetResponses.push(response)
            return
          }
          serveFile(response, path.join(embedAssetsRoot, 'embed-main.js'), 'text/javascript;charset=UTF-8')
          return
        }
        if (request.method === 'GET' && url.pathname === '/embed-assets/embed-main.css') {
          serveFile(response, path.join(embedAssetsRoot, 'embed-main.css'), 'text/css;charset=UTF-8')
          return
        }
        if (request.method === 'POST' && url.pathname === `/api/embed/v1/launches/${launchId}/exchange`) {
          const body = JSON.parse(await readRequestBody(request))
          state.exchangeBodies.push(body)
          sendJson(response, 200, {
            accessToken,
            tokenType: 'Bearer',
            launchId,
            expiresAt: '2099-08-28T09:00:00.000Z',
            idleExpiresAt: '2099-08-28T08:35:00.000Z',
            heartbeatAfterSeconds: 60,
            protocolVersion: 'flow-embed/1'
          })
          return
        }
        if (request.headers.authorization !== `Bearer ${accessToken}`) {
          sendJson(response, 401, {
            errorCode: 'EMBED_SESSION_MISSING',
            message: 'fixture session missing',
            traceId: 'trace-browser-auth'
          })
          return
        }
        if (request.method === 'GET' && url.pathname === '/api/embed/v1/runtime/bootstrap') {
          sendJson(response, 200, createBootstrap())
          return
        }
        if (request.method === 'GET' && url.pathname === '/api/embed/v1/runtime/schema') {
          sendJson(response, 200, createListSchema())
          return
        }
        if (request.method === 'POST' && url.pathname === '/api/embed/v1/runtime/list/query') {
          await readRequestBody(request)
          state.listQueryCount += 1
          if (state.expireNextListQuery) {
            state.expireNextListQuery = false
            sendJson(response, 401, {
              errorCode: 'EMBED_SESSION_EXPIRED',
              message: 'fixture session expired',
              traceId: 'trace-browser-expired'
            })
            return
          }
          sendJson(response, 200, {
            items: [record()], hasMore: false, pageNum: 1, pageSize: 20,
            total: 999999, sql: 'must-not-cross-browser-boundary'
          })
          return
        }
        if (request.method === 'GET' && url.pathname === '/api/embed/v1/runtime/form') {
          const mode = url.searchParams.get('mode')
          sendJson(response, 200, formResult(mode))
          return
        }
        if (request.method === 'GET'
          && url.pathname === '/api/embed/v1/runtime/records/record-browser-1') {
          sendJson(response, 200, {
            record: formResult('VIEW').record,
            fieldStates: {
              title: { visible: true, readOnly: true, required: true },
              status: { visible: true, readOnly: true, required: false }
            },
            actions: {},
            internalPermission: 'must-not-cross-browser-boundary'
          })
          return
        }
        if (request.method === 'POST'
          && url.pathname === '/api/embed/v1/runtime/form/evaluations') {
          const body = JSON.parse(await readRequestBody(request))
          sendJson(response, 200, formResult('CREATE', body.data))
          return
        }
        if (request.method === 'POST' && url.pathname === '/api/embed/v1/runtime/records') {
          const body = JSON.parse(await readRequestBody(request))
          state.createBodies.push({
            body,
            idempotencyKey: request.headers['idempotency-key']
          })
          sendJson(response, 201, {
            receiptId: 'eor_browser_0123456789abcdef',
            record: {
              id: 'record-browser-created',
              recordVersion: null,
              values: {
                title: body.data.title,
                status: body.data.status
              },
              meta: { createdAt: null, updatedAt: null }
            },
            effects: [],
            clientMutationId: body.clientMutationId
          })
          return
        }
        if (request.method === 'POST' && url.pathname === '/api/embed/v1/session/heartbeat') {
          await readRequestBody(request)
          sendJson(response, 200, {
            status: 'ACTIVE',
            idleExpiresAt: '2099-08-28T08:40:00.000Z',
            absoluteExpiresAt: '2099-08-28T09:00:00.000Z',
            nextHeartbeatAfterSeconds: 60
          })
          return
        }
        if (request.method === 'DELETE' && url.pathname === '/api/embed/v1/session') {
          send(response, 204, '')
          return
        }
        sendJson(response, 404, { errorCode: 'FIXTURE_ROUTE_NOT_FOUND', message: url.pathname })
      } catch (error) {
        if (!response.headersSent) sendJson(response, 500, {
          errorCode: 'FIXTURE_FAILURE', message: error.message
        })
        else response.destroy(error)
      }
    })
    const embedPort = await listen(embedServer)
    state.embedOrigin = `https://127.0.0.1:${embedPort}`

    parentServer = https.createServer(certificate, (request, response) => {
      const url = new URL(request.url, 'https://fixture.invalid')
      if (url.pathname === '/') {
        send(response, 200, `<!doctype html><html lang="zh-CN"><head>
          <meta charset="UTF-8"><title>Flow Embed Host E2E</title></head>
          <body><main id="embed-root"></main><script type="module" src="/parent-main.js"></script></body>
          </html>`, {
          'Content-Type': 'text/html;charset=UTF-8',
          'Content-Security-Policy': `default-src 'self'; script-src 'self'; frame-src ${state.embedOrigin}; object-src 'none'; base-uri 'none'`
        })
        return
      }
      if (url.pathname === '/parent-main.js') {
        send(response, 200, parentMainSource(state.embedOrigin, state.hostileOrigin), {
          'Content-Type': 'text/javascript;charset=UTF-8'
        })
        return
      }
      if (url.pathname.startsWith('/sdk/')) {
        const filename = path.basename(url.pathname)
        if (!['index.js', 'FlowEmbedWidget.js', 'protocol.js'].includes(filename)) {
          send(response, 404, 'not found')
          return
        }
        serveFile(response, path.join(sdkRoot, filename), 'text/javascript;charset=UTF-8')
        return
      }
      send(response, 404, 'not found')
    })
    const parentPort = await listen(parentServer)
    state.parentOrigin = `https://127.0.0.1:${parentPort}`

    hostileServer = https.createServer(certificate, (request, response) => {
      const url = new URL(request.url, 'https://fixture.invalid')
      if (url.pathname !== '/') {
        send(response, 404, 'not found')
        return
      }
      send(response, 200, `<!doctype html><html><head><meta charset="UTF-8"></head>
        <body><iframe id="blocked" src="${state.embedOrigin}/embed/v1/launches/${launchId}"></iframe></body></html>`, {
        'Content-Type': 'text/html;charset=UTF-8',
        'Content-Security-Policy': `default-src 'self'; frame-src ${state.embedOrigin}`
      })
    })
    const hostilePort = await listen(hostileServer)
    state.hostileOrigin = `https://127.0.0.1:${hostilePort}`

    // parent-main.js 在两个服务器监听后才会请求，因此其中能获得最终 hostileOrigin。
    assert.notEqual(state.parentOrigin, state.embedOrigin)
    assert.notEqual(state.hostileOrigin, state.parentOrigin)

    const debugProbe = http.createServer()
    const debugPort = await listen(debugProbe)
    await closeServer(debugProbe)
    const profileDirectory = path.join(temporaryDirectory, 'chrome-profile')
    const chromeOutput = []
    chrome = spawn(chromePath, [
      '--headless=new',
      '--disable-background-networking',
      '--disable-component-update',
      '--disable-default-apps',
      '--disable-dev-shm-usage',
      '--disable-features=HttpsUpgrades',
      '--disable-gpu',
      '--ignore-certificate-errors',
      '--no-default-browser-check',
      '--no-first-run',
      '--no-sandbox',
      '--remote-allow-origins=*',
      `--remote-debugging-port=${debugPort}`,
      `--user-data-dir=${profileDirectory}`,
      'about:blank'
    ], { stdio: ['ignore', 'pipe', 'pipe'] })
    for (const stream of [chrome.stdout, chrome.stderr]) {
      stream.on('data', chunk => {
        if (chromeOutput.join('').length < 16_000) chromeOutput.push(String(chunk))
      })
    }

    const targets = await waitFor(async () => {
      try {
        const response = await fetch(`http://127.0.0.1:${debugPort}/json/list`)
        const value = await response.json()
        return value.some(item => item.type === 'page' && item.url === 'about:blank')
          ? value : null
      } catch {
        if (chrome.exitCode !== null) {
          throw new Error(`Chrome 提前退出 (${chrome.exitCode})：${chromeOutput.join('').slice(-2000)}`)
        }
        return null
      }
    }, 'Chrome DevTools target')
    const target = targets.find(item => item.type === 'page' && item.url === 'about:blank')
    cdp = new CdpClient(target.webSocketDebuggerUrl)
    await cdp.connect()

    const networkRequests = new Map()
    const networkResponses = []
    const loadingFailures = []
    const consoleMessages = []
    const logEntries = []
    const exceptions = []
    cdp.on('Network.requestWillBeSent', event => {
      networkRequests.set(event.requestId, {
        url: event.request.url,
        headers: event.request.headers || {}
      })
    })
    cdp.on('Network.responseReceived', event => {
      networkResponses.push({
        url: event.response.url,
        status: event.response.status,
        headers: event.response.headers || {}
      })
    })
    cdp.on('Network.loadingFailed', event => {
      loadingFailures.push({ ...event, url: networkRequests.get(event.requestId)?.url || '' })
    })
    cdp.on('Runtime.consoleAPICalled', event => {
      consoleMessages.push((event.args || []).map(argument => (
        argument.value ?? argument.description ?? ''
      )).join(' '))
    })
    cdp.on('Runtime.exceptionThrown', event => {
      exceptions.push(event.exceptionDetails?.exception?.description
        || event.exceptionDetails?.text || 'unknown exception')
    })
    cdp.on('Log.entryAdded', event => {
      logEntries.push(event.entry || {})
    })
    await Promise.all([
      cdp.send('Network.enable'),
      cdp.send('Page.enable'),
      cdp.send('Runtime.enable'),
      cdp.send('Log.enable')
    ])
    await cdp.send('Page.navigate', { url: `${state.parentOrigin}/` })

    try {
      await waitFor(() => cdp.evaluate(`Boolean(window.__flowHarness)`), '宿主 SDK 初始化')
    } catch (error) {
      const pageState = await cdp.evaluate(`({
        href: location.href,
        readyState: document.readyState,
        body: document.body?.innerText || ''
      })`).catch(() => ({}))
      throw new Error(`${error.message}；page=${JSON.stringify(pageState)}；`
        + `exceptions=${JSON.stringify(exceptions)}；console=${JSON.stringify(consoleMessages)}`)
    }
    await waitFor(() => state.heldAssetResponses.length > 0, 'iframe Embed 资源进入等待态')
    const beforeHandshake = await cdp.evaluate(`window.__flowHarness.snapshot()`)
    assert.equal(beforeHandshake.state, 'waiting')
    assert.equal(beforeHandshake.iframeUrl,
      `${state.embedOrigin}/embed/v1/launches/${launchId}`)
    assert.equal(new URL(beforeHandshake.iframeUrl).search, '')
    assert.equal(new URL(beforeHandshake.iframeUrl).hash, '')

    const forgedViolations = await cdp.evaluate(`window.__flowHarness.injectForgedReady()`)
    assert.deepEqual(forgedViolations, [
      'FLOW_EMBED_READY_SOURCE_INVALID',
      'FLOW_EMBED_READY_SOURCE_INVALID',
      'FLOW_EMBED_READY_INVALID'
    ])
    assert.equal(await cdp.evaluate(`window.__flowHarness.snapshot().state`), 'waiting')

    state.assetReleased = true
    for (const response of state.heldAssetResponses.splice(0)) {
      serveFile(response, path.join(embedAssetsRoot, 'embed-main.js'), 'text/javascript;charset=UTF-8')
    }

    try {
      await waitFor(async () => {
        const snapshot = await cdp.evaluate(`window.__flowHarness.snapshot()`)
        return snapshot.state === 'connected'
          && snapshot.events.some(event => event.type === 'initialized')
      }, '真实 MessageChannel 握手与列表初始化')
    } catch (error) {
      const parentSnapshot = await cdp.evaluate(`window.__flowHarness.snapshot()`).catch(() => ({}))
      const iframeText = await cdp.frameEvaluate(state.embedOrigin,
        `document.body?.innerText || ''`).catch(value => String(value?.message || value))
      throw new Error(`${error.message}；parent=${JSON.stringify(parentSnapshot)}；`
        + `iframe=${JSON.stringify(iframeText)}；requests=${JSON.stringify(state.requestLog.map(item => item.url))}；`
        + `exceptions=${JSON.stringify(exceptions)}；console=${JSON.stringify(consoleMessages)}`)
    }
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `document.querySelector('#embed-list-title')?.textContent === '供应商工单'`), '列表渲染')
    const listText = await cdp.frameEvaluate(state.embedOrigin,
      `document.querySelector('.embed-list')?.innerText || ''`)
    assert.match(listText, /WO-BROWSER-001/)
    assert.equal(listText.includes('must-not-cross-browser-boundary'), false)

    assert.equal(state.exchangeBodies.length, 1)
    const exchange = state.exchangeBodies[0]
    assert.equal(exchange.launchCode, launchCode)
    assert.equal(exchange.parentOrigin, state.parentOrigin)
    assert.equal(exchange.channelId, channelId)
    assert.equal(Buffer.from(exchange.parentNonce, 'base64url').length, 32)
    assert.equal(Buffer.from(exchange.childNonce, 'base64url').length, 32)
    assert.notEqual(exchange.parentNonce, exchange.childNonce)

    const afterHandshake = await cdp.evaluate(`window.__flowHarness.snapshot()`)
    assert.equal(afterHandshake.launchCodeInWidget, '')
    assert.deepEqual(afterHandshake.localStorage, {})
    assert.deepEqual(afterHandshake.sessionStorage, {})
    assert.equal(afterHandshake.cookie, '')
    const iframeStorage = await cdp.frameEvaluate(state.embedOrigin, `({
      localStorage: { ...localStorage }, sessionStorage: { ...sessionStorage }, cookie: document.cookie,
      href: location.href, historyLength: history.length
    })`)
    assert.deepEqual(iframeStorage.localStorage, {})
    assert.deepEqual(iframeStorage.sessionStorage, {})
    assert.equal(iframeStorage.cookie, '')
    assert.equal(iframeStorage.href, `${state.embedOrigin}/embed/v1/launches/${launchId}`)

    const requestMetadata = [...networkRequests.values()]
    for (const request of requestMetadata) {
      assert.equal(request.url.includes(launchCode), false, `launchCode 泄漏到 URL：${request.url}`)
      assert.equal(JSON.stringify(request.headers).includes(launchCode), false,
        `launchCode 泄漏到请求头：${request.url}`)
    }
    assert.equal(JSON.stringify(state.requestLog).includes(launchCode), false,
      'fixture 通用请求日志不得记录 launchCode')
    assert.equal(consoleMessages.join('\n').includes(launchCode), false,
      '浏览器 console 不得记录 launchCode')

    const entryResponse = networkResponses.find(item =>
      item.url === `${state.embedOrigin}/embed/v1/launches/${launchId}`)
    assert.ok(entryResponse, '应捕获 Embed Entry 浏览器响应')
    const cspHeader = Object.entries(entryResponse.headers)
      .find(([key]) => key.toLowerCase() === 'content-security-policy')?.[1]
    assert.match(String(cspHeader), new RegExp(`frame-ancestors ${escapeRegExp(state.parentOrigin)}(?:;|$)`))
    assert.equal(String(cspHeader).includes("frame-ancestors *"), false)

    await cdp.frameEvaluate(state.embedOrigin,
      `document.querySelector('input[aria-label^="选择记录"]')?.click()`)
    const selectionEvent = await waitFor(async () => {
      const events = await cdp.evaluate(`window.__flowHarness.snapshot().events`)
      return events.find(event => event.type === 'selection.changed') || null
    }, 'selection.changed 回传')
    assert.deepEqual(selectionEvent.payload, {
      selection: [{ id: 'record-browser-1', values: { code: 'WO-BROWSER-001' } }]
    })
    assert.equal(JSON.stringify(selectionEvent).includes('internalSecret'), false)
    assert.equal(JSON.stringify(selectionEvent).includes('status'), false)

    await cdp.frameEvaluate(state.embedOrigin,
      `[...document.querySelectorAll('button')].find(button => button.textContent.trim() === '查看')?.click()`)
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `document.querySelector('.embed-form__mode')?.textContent.trim() === '查看记录'`), 'LIST → VIEW')
    assert.equal(await cdp.frameEvaluate(state.embedOrigin,
      `document.querySelector('#embed-form-title')?.textContent.trim()`), '工单表单')
    await cdp.frameEvaluate(state.embedOrigin,
      `[...document.querySelectorAll('button')].find(button => button.textContent.trim() === '返回列表')?.click()`)
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `Boolean(document.querySelector('#embed-list-title'))`), 'VIEW → BACK')
    assert.equal(state.listQueryCount, 1, '返回列表必须保留列表快照且不重复查询')

    await cdp.frameEvaluate(state.embedOrigin,
      `[...document.querySelectorAll('button')].find(button => button.textContent.trim() === '新建')?.click()`)
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `document.querySelector('.embed-form__mode')?.textContent.trim() === '新建记录'`), 'LIST → CREATE')
    await cdp.frameEvaluate(state.embedOrigin,
      `[...document.querySelectorAll('button')].find(button => button.textContent.trim() === '返回列表')?.click()`)
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `Boolean(document.querySelector('#embed-list-title'))`), 'CREATE → BACK')
    assert.equal(state.listQueryCount, 1, 'CREATE 返回也必须保留列表快照')

    await cdp.frameEvaluate(state.embedOrigin,
      `[...document.querySelectorAll('button')].find(button => button.textContent.trim() === '新建')?.click()`)
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `Boolean(document.querySelector('#embed-form-title'))`), '重新打开 CREATE')
    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      const input = document.querySelector('.embed-form__field input')
      input.value = '浏览器创建工单'
      input.dispatchEvent(new Event('input', { bubbles: true }))
      return input.value
    })()`)
    await cdp.frameEvaluate(state.embedOrigin,
      `[...document.querySelectorAll('button')].find(button => button.textContent.trim() === '保存')?.click()`)
    const savedEvent = await waitFor(async () => {
      const events = await cdp.evaluate(`window.__flowHarness.snapshot().events`)
      return events.find(event => event.type === 'form.saved') || null
    }, 'form.saved 回传')
    assert.deepEqual(savedEvent.payload.record, {
      id: 'record-browser-created',
      values: { title: '浏览器创建工单' }
    })
    assert.match(savedEvent.payload.receiptId, /^eor_[A-Za-z0-9_-]{16,64}$/)
    assert.equal(Object.keys(savedEvent.payload).sort().join(','),
      'clientMutationId,receiptId,record')
    assert.equal(JSON.stringify(savedEvent).includes('status'), false)
    assert.equal(JSON.stringify(savedEvent).includes('internalSecret'), false)
    assert.equal(state.createBodies.length, 1)
    assert.deepEqual(Object.keys(state.createBodies[0].body).sort(), ['clientMutationId', 'data'])
    assert.deepEqual(Object.keys(state.createBodies[0].body.data).sort(), ['status', 'title'])
    assert.match(state.createBodies[0].idempotencyKey, /^[\x21-\x7E]{1,128}$/)

    await cdp.frameEvaluate(state.embedOrigin,
      `[...document.querySelectorAll('button')].find(button => button.textContent.trim() === '返回列表')?.click()`)
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `Boolean(document.querySelector('#embed-list-title'))`), '保存后返回列表')
    state.expireNextListQuery = true
    await cdp.evaluate(`window.__flowHarness.refresh()`)
    const expiredEvent = await waitFor(async () => {
      const events = await cdp.evaluate(`window.__flowHarness.snapshot().events`)
      return events.find(event => event.type === 'session.expired') || null
    }, 'session.expired 回传')
    assert.deepEqual(expiredEvent.payload, {
      reason: 'EMBED_SESSION_EXPIRED', relaunchRequired: true
    })
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin,
      `document.querySelector('.embed-shell')?.dataset.state === 'SESSION_EXPIRED'`), 'iframe 会话过期终态')

    const eventsBeforeDestroy = (await cdp.evaluate(
      `window.__flowHarness.snapshot().events.length`))
    const destroyed = await cdp.evaluate(`window.__flowHarness.destroy()`)
    assert.equal(destroyed.state, 'destroyed')
    assert.equal(destroyed.iframeCount, 0)
    assert.equal(destroyed.iframeUrl, '')
    assert.equal(destroyed.launchCodeInWidget, '')
    await cdp.evaluate(`window.dispatchEvent(new MessageEvent('message', {
      data: { protocol: 'flow-embed/1', type: 'ready' },
      origin: ${JSON.stringify(state.embedOrigin)}, source: window
    }))`)
    assert.equal(await cdp.evaluate(`window.__flowHarness.snapshot().events.length`), eventsBeforeDestroy)

    const failureMark = loadingFailures.length
    await cdp.send('Page.navigate', { url: `${state.hostileOrigin}/` })
    await waitFor(async () => {
      const result = await cdp.evaluate(`location.origin === ${JSON.stringify(state.hostileOrigin)}`)
      return result === true
    }, '未授权宿主页面导航')
    const cspEvidence = await waitFor(() => {
      const failure = loadingFailures.slice(failureMark).find(candidate =>
        candidate.url === `${state.embedOrigin}/embed/v1/launches/${launchId}`
          && candidate.errorText === 'net::ERR_BLOCKED_BY_RESPONSE')
      const securityLog = logEntries.find(entry => entry.source === 'security'
        && String(entry.text).includes(`frame-ancestors ${state.parentOrigin}`)
        && String(entry.text).includes('request has been blocked'))
      return failure && securityLog ? { failure, securityLog } : null
    }, '错误 origin 被 frame-ancestors CSP 拒绝')
    // Chrome 151 把 frame-ancestors 报为 blockedReason=other，安全日志才携带精确
    // CSP 指令；两份独立证据同时存在，避免把普通网络错误误判为 Origin 拒绝。
    assert.ok(['csp', 'other'].includes(String(cspEvidence.failure.blockedReason).toLowerCase()))
    const { frameTree } = await cdp.send('Page.getFrameTree')
    const blockedFrame = findFrame(frameTree, frame =>
      frame.unreachableUrl === `${state.embedOrigin}/embed/v1/launches/${launchId}`)
    assert.equal(blockedFrame?.url, 'chrome-error://chromewebdata/')
    assert.equal(JSON.stringify(logEntries).includes(launchCode), false,
      '浏览器安全/网络日志不得记录 launchCode')

    assert.deepEqual(exceptions, [], `浏览器存在未捕获异常：${exceptions.join('\n')}`)
    console.log('embed cross-origin browser e2e passed')
  } finally {
    if (cdp) cdp.close()
    for (const response of state.heldAssetResponses.splice(0)) {
      if (!response.headersSent) send(response, 503, 'fixture shutting down')
      else response.destroy()
    }
    if (chrome && chrome.exitCode === null) {
      chrome.kill('SIGTERM')
      await Promise.race([
        new Promise(resolve => chrome.once('exit', resolve)),
        delay(2_000)
      ])
      if (chrome.exitCode === null) chrome.kill('SIGKILL')
    }
    await Promise.all([
      closeServer(embedServer),
      closeServer(parentServer),
      closeServer(hostileServer)
    ])
    rmSync(temporaryDirectory, { recursive: true, force: true })
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

class CdpClient {
  constructor(webSocketUrl) {
    this.webSocketUrl = webSocketUrl
    this.nextId = 1
    this.pending = new Map()
    this.handlers = new Map()
    this.defaultContexts = new Map()
  }

  async connect() {
    this.socket = new WebSocket(this.webSocketUrl)
    this.socket.addEventListener('message', event => {
      const message = JSON.parse(event.data)
      if (message.id && this.pending.has(message.id)) {
        const pending = this.pending.get(message.id)
        this.pending.delete(message.id)
        if (message.error) pending.reject(new Error(
          `${message.error.message}: ${message.error.data || ''}`
        ))
        else pending.resolve(message.result || {})
        return
      }
      if (message.method === 'Runtime.executionContextCreated') {
        const context = message.params?.context
        if (context?.auxData?.isDefault && context.auxData.frameId) {
          this.defaultContexts.set(context.auxData.frameId, context.id)
        }
      } else if (message.method === 'Runtime.executionContextDestroyed') {
        for (const [frameId, contextId] of this.defaultContexts) {
          if (contextId === message.params?.executionContextId) this.defaultContexts.delete(frameId)
        }
      } else if (message.method === 'Runtime.executionContextsCleared') {
        this.defaultContexts.clear()
      }
      for (const handler of this.handlers.get(message.method) || []) {
        try { handler(message.params || {}) } catch { /* 采集器不能中断协议处理。 */ }
      }
    })
    await new Promise((resolve, reject) => {
      this.socket.addEventListener('open', resolve, { once: true })
      this.socket.addEventListener('error', reject, { once: true })
    })
  }

  on(method, handler) {
    const handlers = this.handlers.get(method) || []
    handlers.push(handler)
    this.handlers.set(method, handlers)
  }

  send(method, params = {}) {
    const id = this.nextId++
    const promise = new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id)
        reject(new Error(`CDP 命令超时：${method}`))
      }, 10_000)
      this.pending.set(id, {
        resolve(value) { clearTimeout(timer); resolve(value) },
        reject(error) { clearTimeout(timer); reject(error) }
      })
    })
    this.socket.send(JSON.stringify({ id, method, params }))
    return promise
  }

  async evaluate(expression, contextId) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      ...(contextId ? { contextId } : {}),
      awaitPromise: true,
      returnByValue: true,
      userGesture: true
    })
    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.exception?.description
        || result.exceptionDetails.text || '浏览器表达式执行失败')
    }
    return result.result?.value
  }

  async frameEvaluate(origin, expression) {
    const contextId = await waitFor(async () => {
      const { frameTree } = await this.send('Page.getFrameTree')
      const frame = findFrame(frameTree, candidate => candidate.url.startsWith(origin))
      return frame ? this.defaultContexts.get(frame.id) : null
    }, `${origin} 默认执行上下文`)
    return this.evaluate(expression, contextId)
  }

  close() {
    for (const pending of this.pending.values()) pending.reject(new Error('CDP 已关闭'))
    this.pending.clear()
    this.socket?.close()
  }
}

function findFrame(tree, predicate) {
  if (!tree) return null
  if (predicate(tree.frame)) return tree.frame
  for (const child of tree.childFrames || []) {
    const match = findFrame(child, predicate)
    if (match) return match
  }
  return null
}

main().catch(error => {
  console.error(error.stack || error.message || error)
  process.exitCode = 1
})
