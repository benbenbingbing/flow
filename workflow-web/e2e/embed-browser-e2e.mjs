import assert from 'node:assert/strict'
import { execFileSync, spawn } from 'node:child_process'
import {
  existsSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync
} from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sdkRoot = path.join(webRoot, 'packages/flow-embed-sdk/src')
const embedAssetsRoot = path.join(webRoot, 'dist/embed-assets')
const embedSourceRoot = path.join(webRoot, 'src/embed')
const timeoutMs = 30_000
const protocol = 'flow-embed/1'
const protocolHeader = '1'
const entityCode = 'work_order'
const formId = 'form_native_work_order'
const listKey = 'list_native_work_orders'
const stableViewKey = 'native-work-order-form'
const recordId = 'record-native-001'

const launches = Object.freeze({
  directEditorR1: Object.freeze({
    key: 'directEditorR1',
    launchId: 'lch_native_editor_r1_000001',
    launchCode: 'A'.repeat(43),
    channelId: 'native:editor:r1:000001',
    actorKey: 'editor',
    actorName: '原生编辑用户',
    surfaceType: 'FORM',
    entryMode: 'CREATE',
    viewKey: stableViewKey
  }),
  directEditorR2: Object.freeze({
    key: 'directEditorR2',
    launchId: 'lch_native_editor_r2_000002',
    launchCode: 'B'.repeat(43),
    channelId: 'native:editor:r2:000002',
    actorKey: 'editor',
    actorName: '原生编辑用户',
    surfaceType: 'FORM',
    entryMode: 'CREATE',
    viewKey: stableViewKey
  }),
  listEditor: Object.freeze({
    key: 'listEditor',
    launchId: 'lch_native_list_editor_00003',
    launchCode: 'C'.repeat(43),
    channelId: 'native:list:editor:00003',
    actorKey: 'editor',
    actorName: '原生编辑用户',
    surfaceType: 'LIST',
    entryMode: 'LIST',
    viewKey: 'native-work-order-list'
  }),
  directRestricted: Object.freeze({
    key: 'directRestricted',
    launchId: 'lch_native_restricted_000004',
    launchCode: 'D'.repeat(43),
    channelId: 'native:restricted:000004',
    actorKey: 'restricted',
    actorName: '受限映射用户',
    surfaceType: 'FORM',
    entryMode: 'CREATE',
    viewKey: stableViewKey
  })
})

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
  throw new Error(
    `等待超时：${description}${lastError ? `；${lastError.message}` : ''}`
  )
}

function findChrome() {
  const configured = String(
    process.env.FLOW_EMBED_E2E_CHROME_PATH || ''
  ).trim()
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
    throw new Error(
      '无法生成浏览器 E2E 临时 HTTPS 证书，请确认 openssl 可用',
      { cause }
    )
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
      if (body.length > 2 * 1024 * 1024) {
        request.destroy(new Error('fixture body too large'))
      }
    })
    request.on('end', () => resolve(body))
    request.on('error', reject)
  })
}

async function readJsonBody(request) {
  const body = await readRequestBody(request)
  return body ? JSON.parse(body) : {}
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

function walkSourceFiles(directory) {
  return readdirSync(directory).flatMap(name => {
    const filename = path.join(directory, name)
    if (statSync(filename).isDirectory()) return walkSourceFiles(filename)
    return /\.(?:js|ts|vue)$/.test(filename) ? [filename] : []
  })
}

function entryHtml(spec, parentOrigin) {
  const entry = Buffer.from(JSON.stringify({
    launchId: spec.launchId,
    expectedParentOrigin: parentOrigin,
    channelId: spec.channelId,
    protocolVersion: protocol
  })).toString('base64url')
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="flow-embed-entry" content="${entry}">
  <title>Flow Native Embed E2E</title>
  <link rel="stylesheet" href="/embed-assets/embed-main.css">
  <script src="/csp-probe.js"></script>
  <script type="module" src="/embed-assets/embed-main.js"></script>
</head>
<body><div id="app"></div></body>
</html>`
}

function cspProbeSource() {
  return `window.__flowCspViolations = []
window.addEventListener('securitypolicyviolation', event => {
  window.__flowCspViolations.push({
    blockedURI: event.blockedURI,
    effectiveDirective: event.effectiveDirective,
    violatedDirective: event.violatedDirective
  })
})`
}

function releaseId(version) {
  return `form_release_native_v${version}`
}

function releaseToken(version) {
  return `rrt_native_release_v${version}_0123456789abcdef`
}

function nativeTarget(version, mode, targetRecordId = null) {
  return {
    entityCode,
    form: { formId },
    formRelease: {
      releaseId: releaseId(version),
      version,
      releaseResolutionToken: releaseToken(version)
    },
    mode,
    recordId: mode === 'VIEW' ? targetRecordId : null,
    listKey,
    initialData: mode === 'CREATE'
      ? {
          title: `原生表单默认标题 R${version}`,
          dueDate: '2026-09-18',
          startsAt: '2026-09-18 14:25:07',
          status: 'PROCESSING',
          tags: ['URGENT'],
          enabled: true,
          description: '<p><strong>Flow 原生富文本</strong></p>',
          acceptanceScore: 72
        }
      : {},
    parameters: { source: 'browser-e2e' },
    runtimeContext: { fixture: 'native-runtime' }
  }
}

function capabilities(spec) {
  return spec.surfaceType === 'LIST'
    ? ['LIST_QUERY', 'SELECTION_RETURN', 'RECORD_VIEW', 'RECORD_CREATE', 'ACTION_EXECUTE']
    : [spec.entryMode === 'VIEW' ? 'RECORD_VIEW' : 'RECORD_CREATE', 'ACTION_EXECUTE']
}

function createBootstrap(session) {
  const { spec, pinnedReleaseVersion } = session
  return {
    session: {
      id: `ems_${spec.key}`,
      expiresAt: '2099-08-31T09:00:00.000Z',
      idleExpiresAt: '2099-08-31T08:35:00.000Z'
    },
    actor: { displayName: spec.actorName },
    view: {
      key: spec.viewKey,
      name: spec.surfaceType === 'LIST'
        ? 'Flow 原生嵌入列表'
        : 'Flow 原生需求表单',
      surfaceType: spec.surfaceType,
      entryMode: spec.entryMode
    },
    target: spec.surfaceType === 'FORM'
      ? nativeTarget(
          pinnedReleaseVersion,
          spec.entryMode,
          spec.entryMode === 'VIEW' ? recordId : null
        )
      : null,
    capabilities: capabilities(spec),
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
    view: {
      key: launches.listEditor.viewKey,
      surfaceType: 'LIST',
      revision: 7
    },
    entity: { code: entityCode, name: '原生工单' },
    list: {
      selection: {
        mode: 'SINGLE',
        valueField: 'id',
        returnableFields: ['code']
      },
      pagination: { allowTotal: false, maxPageSize: 100 },
      columns: [
        {
          code: 'code', label: '工单号', type: 'TEXT',
          width: 180, sortable: false
        },
        {
          code: 'status', label: '状态', type: 'SELECT',
          width: 120, sortable: false,
          options: [{ label: '处理中', value: 'PROCESSING' }]
        }
      ],
      filters: [
        {
          code: 'code', label: '工单号', type: 'TEXT',
          operator: 'CONTAINS'
        }
      ]
    },
    form: null,
    actions: [
      {
        key: 'create', label: '新建', placement: 'TOOLBAR',
        kind: 'NAVIGATION', transport: 'LOCAL_FORM',
        recordMode: 'NONE', selectionMode: 'NONE',
        requiresRecordVersion: false, idempotencyRequired: false,
        enabled: true, disabledReason: null
      },
      {
        key: 'view', label: '查看', placement: 'ROW',
        kind: 'NAVIGATION', transport: 'LOCAL_FORM',
        recordMode: 'CURRENT', selectionMode: 'NONE',
        requiresRecordVersion: false, idempotencyRequired: false,
        enabled: true, disabledReason: null
      }
    ]
  }
}

function listRecord() {
  return {
    id: recordId,
    recordVersion: null,
    values: {
      code: 'WO-NATIVE-001',
      status: 'PROCESSING',
      serverOnlySecret: 'must-not-cross-list-boundary'
    },
    meta: { updatedAt: '2026-08-31T08:20:00.000Z' },
    actions: {
      view: { visible: true, enabled: true, reason: null }
    }
  }
}

function modeAccess() {
  return {
    modes: {
      create: { visible: true, editable: true },
      edit: { visible: true, editable: true },
      view: { visible: true, editable: false },
      approve: { visible: true, editable: true }
    }
  }
}

function runtimeFields() {
  const options = {
    status: [
      { label: '待处理', value: 'PENDING' },
      { label: '处理中', value: 'PROCESSING' },
      { label: '已完成', value: 'DONE' }
    ],
    tags: [
      { label: '紧急', value: 'URGENT' },
      { label: '重要', value: 'IMPORTANT' },
      { label: '跟进', value: 'FOLLOW_UP' }
    ]
  }
  const fields = [
    {
      id: 'field-title', fieldCode: 'title', fieldName: '标题', fieldLabel: '标题',
      fieldType: 'STRING', componentType: 'input', defaultValue: '', gridSpan: 12
    },
    {
      id: 'field-due-date', fieldCode: 'dueDate', fieldName: '到期日期',
      fieldLabel: '到期日期', fieldType: 'DATE', componentType: 'date',
      defaultValue: '2026-09-18', gridSpan: 12
    },
    {
      id: 'field-starts-at', fieldCode: 'startsAt', fieldName: '开始时间',
      fieldLabel: '开始时间', fieldType: 'DATETIME', componentType: 'datetime',
      defaultValue: '2026-09-18 14:25:07', gridSpan: 12
    },
    {
      id: 'field-status', fieldCode: 'status', fieldName: '处理状态',
      fieldLabel: '处理状态', fieldType: 'STRING', componentType: 'select',
      defaultValue: 'PROCESSING', optionsJson: JSON.stringify(options.status),
      gridSpan: 12
    },
    {
      id: 'field-tags', fieldCode: 'tags', fieldName: '工单标签',
      fieldLabel: '工单标签', fieldType: 'STRING', componentType: 'select_multiple',
      defaultValue: JSON.stringify(['URGENT']),
      optionsJson: JSON.stringify(options.tags), gridSpan: 12
    },
    {
      id: 'field-enabled', fieldCode: 'enabled', fieldName: '是否启用',
      fieldLabel: '是否启用', fieldType: 'BOOLEAN', componentType: 'switch',
      defaultValue: 'true',
      componentProps: { activeText: '启用', inactiveText: '停用' }, gridSpan: 12
    },
    {
      id: 'field-description', fieldCode: 'description', fieldName: '需求描述',
      fieldLabel: '需求描述', fieldType: 'STRING', componentType: 'rich_text',
      defaultValue: '<p><strong>Flow 原生富文本</strong></p>',
      componentProps: { height: 160 }, gridSpan: 24
    },
    {
      id: 'field-acceptance-score', fieldCode: 'acceptanceScore',
      fieldName: '扩展验收分', fieldLabel: '扩展验收分', fieldType: 'INTEGER',
      componentType: 'project_acceptance_score', defaultValue: '72',
      componentProps: { passScore: 60 }, gridSpan: 24
    }
  ]
  return fields.map((field, index) => ({
    ...field,
    isRequired: 0,
    isReadonly: 0,
    isHidden: 0,
    runtimeReadable: true,
    runtimeWritable: true,
    sortOrder: index + 1,
    extensionConfig: modeAccess()
  }))
}

function runtimeNodes(fields, version) {
  return [
    {
      id: 'native-section', parentId: '', nodeKey: 'nativeSection',
      nodeType: 'SECTION', bindingType: 'NONE', bindingRef: '',
      propsDocument: { label: `Flow 原生组件 R${version}` },
      rulesDocument: {}, orderKey: 1
    },
    {
      id: 'native-grid', parentId: 'native-section', nodeKey: 'nativeGrid',
      nodeType: 'GRID', bindingType: 'NONE', bindingRef: '',
      propsDocument: { defaultSpan: 12, gutter: 16 },
      rulesDocument: {}, orderKey: 1
    },
    ...fields.map((field, index) => ({
      id: `node-${field.fieldCode}`,
      parentId: 'native-grid',
      nodeKey: `${field.fieldCode}Node`,
      nodeType: 'FIELD',
      bindingType: 'ENTITY_FIELD',
      bindingRef: field.fieldCode,
      propsDocument: { gridSpan: field.gridSpan },
      rulesDocument: {},
      orderKey: index + 1
    }))
  ]
}

function runtimeRelease(version) {
  const fields = runtimeFields()
  return {
    id: releaseId(version),
    version,
    effectiveReleaseId: releaseId(version),
    releaseResolutionToken: releaseToken(version),
    snapshotDocument: JSON.stringify({
      form: {
        id: formId,
        formName: `Flow 原生需求表单 R${version}`,
        formKey: 'nativeWorkOrder',
        layoutType: 'grid',
        viewConfig: { labelWidth: 120, labelPosition: 'right' },
        actionBar: {},
        isReadonly: false
      },
      legacyFields: fields,
      nodes: runtimeNodes(fields, version),
      viewCompositions: []
    })
  }
}

function entityDefinition() {
  return {
    id: 'entity_native_work_order',
    entityCode,
    entityName: '原生工单',
    lifecycleMode: 'PLAIN',
    storageMode: 'DYNAMIC',
    fields: runtimeFields()
  }
}

function detailRecord() {
  return {
    id: recordId,
    name: '原生查看记录',
    dataNo: 'WO-NATIVE-001',
    data: {
      title: 'LIST 查看进入原生详情',
      dueDate: '2026-09-20',
      startsAt: '2026-09-20 09:10:11',
      status: 'PROCESSING',
      tags: ['IMPORTANT'],
      enabled: true,
      description: '<p><em>原生查看富文本</em></p>',
      acceptanceScore: 88
    }
  }
}

function runtimeAction(key, label, sort, options = {}) {
  return {
    key,
    label,
    type: 'built-in',
    icon: options.icon || '',
    buttonType: options.buttonType || 'default',
    sort,
    placement: 'FOOTER',
    visible: true,
    enabled: true,
    reason: '',
    runtimeKey: key,
    validateBeforeExecute: key === 'save',
    confirm: options.confirm || { enabled: false }
  }
}

function resolvedActions(session, mode) {
  if (mode === 'view') {
    return [runtimeAction('close', '关闭', 10)]
  }
  if (session.spec.actorKey === 'restricted') {
    return [runtimeAction('close', '取消', 10)]
  }
  return [
    runtimeAction('close', '取消', 10),
    runtimeAction('reset', '重置', 20, { icon: 'RefreshLeft' }),
    runtimeAction('save', '保存', 30, {
      icon: 'Check',
      buttonType: 'primary',
      confirm: { enabled: true, message: '确认由 Flow 原生表单保存吗？' }
    })
  ]
}

function parentMainSource(embedOrigin) {
  const publicLaunches = Object.fromEntries(
    Object.entries(launches).map(([key, spec]) => [key, {
      launchId: spec.launchId,
      launchCode: spec.launchCode,
      channelId: spec.channelId
    }])
  )
  return `import { mount } from '/sdk/index.js'
const launches = ${JSON.stringify(publicLaunches)}
let widget = null
let currentKey = ''
let currentEvents = []
let currentViolations = []

function snapshot() {
  return {
    key: currentKey,
    state: widget?.getState?.() || 'idle',
    iframeUrl: widget?.iframe?.src || '',
    iframeCount: document.querySelectorAll('#embed-root iframe').length,
    launchCodeInWidget: widget?._launchCode || '',
    events: JSON.parse(JSON.stringify(currentEvents)),
    violations: [...currentViolations],
    localStorage: { ...localStorage },
    sessionStorage: { ...sessionStorage },
    cookie: document.cookie
  }
}

window.__flowHarness = {
  mount(key) {
    if (widget && !['destroyed', 'failed'].includes(widget.getState())) {
      throw new Error('请先销毁当前 Embed')
    }
    const input = launches[key]
    if (!input) throw new Error('未知 fixture: ' + key)
    document.querySelector('#embed-root').replaceChildren()
    currentKey = key
    currentEvents = []
    currentViolations = []
    let oneTimeCode = input.launchCode
    widget = mount({
      container: document.querySelector('#embed-root'),
      embedUrl: ${JSON.stringify(embedOrigin)} + '/embed/v1/launches/' + input.launchId,
      targetOrigin: ${JSON.stringify(embedOrigin)},
      launchId: input.launchId,
      launchCode: oneTimeCode,
      channelId: input.channelId,
      handshakeTimeoutMs: 20000,
      destroyTimeoutMs: 20000,
      height: { mode: 'fixed', min: 900, max: 1200, initial: 1000 },
      onEvent(event) { currentEvents.push(JSON.parse(JSON.stringify(event))) },
      onViolation(error) { currentViolations.push(error.errorCode) }
    })
    oneTimeCode = ''
    return snapshot()
  },
  snapshot,
  refresh() { return widget.refresh() },
  async destroy() {
    const currentWidget = widget
    try {
      await currentWidget.destroy()
      const result = { ok: true, widgetState: currentWidget.getState(), ...snapshot() }
      widget = null
      return result
    } catch (error) {
      const result = {
        ok: false,
        errorCode: error?.errorCode || 'FLOW_EMBED_DESTROY_FAILED',
        widgetState: currentWidget?.getState?.() || '',
        ...snapshot()
      }
      widget = null
      return result
    }
  }
}`
}

function sessionForRequest(request, state) {
  const authorization = String(request.headers.authorization || '')
  const token = authorization.startsWith('Bearer ')
    ? authorization.slice('Bearer '.length)
    : ''
  const session = state.sessions.get(token)
  return session?.active ? session : null
}

function stableLaunchUrlShape(url) {
  const parsed = new URL(url)
  return {
    origin: parsed.origin,
    pathname: parsed.pathname.replace(
      /^\/embed\/v1\/launches\/[^/]+$/,
      '/embed/v1/launches/{launchId}'
    ),
    search: parsed.search,
    hash: parsed.hash
  }
}

async function main() {
  const chromePath = requireChrome()
  for (const filename of ['embed-main.js', 'embed-main.css']) {
    assert.ok(
      existsSync(path.join(embedAssetsRoot, filename)),
      `缺少 ${filename}，请先执行 npm run build:embed`
    )
  }
  const bundle = readFileSync(
    path.join(embedAssetsRoot, 'embed-main.js'),
    'utf8'
  )
  assert.doesNotMatch(
    bundle,
    /\bprocess\.env\b/,
    'Embed 浏览器 bundle 不得保留 Node process.env'
  )
  assert.match(
    bundle,
    /acceptance-score-field/,
    '完整原生 registry 的项目扩展必须进入 Embed bundle'
  )
  const embedSources = walkSourceFiles(embedSourceRoot)
    .map(filename => readFileSync(filename, 'utf8'))
    .join('\n')
  assert.doesNotMatch(
    embedSources,
    /project_acceptance_score/,
    'Embed 框架不得逐个登记项目字段扩展'
  )

  const temporaryDirectory = mkdtempSync(
    path.join(os.tmpdir(), 'flow-native-embed-e2e-')
  )
  const state = {
    activeReleaseVersion: 1,
    launches: new Map(Object.values(launches).map(spec => [spec.launchId, {
      spec,
      consumed: false
    }])),
    sessions: new Map(),
    requestLog: [],
    exchangeLog: [],
    releaseRequests: [],
    nativeTargetRequests: [],
    actionResolutions: [],
    logoutAcks: [],
    sessionLimitResponses: [],
    unhandledRequests: [],
    parentOrigin: '',
    embedOrigin: ''
  }
  let embedServer
  let parentServer
  let chrome
  let cdp
  const observedCspViolations = []

  try {
    const certificate = generateCertificate(temporaryDirectory)
    embedServer = https.createServer(certificate, async (request, response) => {
      try {
        const url = new URL(request.url, 'https://fixture.invalid')
        state.requestLog.push({
          method: request.method,
          url: request.url,
          pathname: url.pathname,
          headers: { ...request.headers }
        })

        const entryMatch = url.pathname.match(
          /^\/embed\/v1\/launches\/(lch_[A-Za-z0-9_-]+)$/
        )
        if (request.method === 'GET' && entryMatch && !url.search) {
          const launch = state.launches.get(entryMatch[1])
          if (!launch) {
            send(response, 404, 'launch not found')
            return
          }
          const csp = "default-src 'self'; script-src 'self'; script-src-attr 'none'; "
            + "style-src 'self'; style-src-elem 'self'; style-src-attr 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
            + `base-uri 'none'; form-action 'none'; frame-ancestors ${state.parentOrigin}`
          send(response, 200, entryHtml(launch.spec, state.parentOrigin), {
            'Content-Type': 'text/html;charset=UTF-8',
            'Content-Security-Policy': csp,
            'Referrer-Policy': 'no-referrer',
            'Permissions-Policy': 'camera=(), microphone=(), geolocation=()'
          })
          return
        }
        if (request.method === 'GET' && url.pathname === '/csp-probe.js') {
          send(response, 200, cspProbeSource(), {
            'Content-Type': 'text/javascript;charset=UTF-8'
          })
          return
        }
        if (request.method === 'GET' && url.pathname === '/embed-assets/embed-main.js') {
          serveFile(
            response,
            path.join(embedAssetsRoot, 'embed-main.js'),
            'text/javascript;charset=UTF-8'
          )
          return
        }
        if (request.method === 'GET' && url.pathname === '/embed-assets/embed-main.css') {
          serveFile(
            response,
            path.join(embedAssetsRoot, 'embed-main.css'),
            'text/css;charset=UTF-8'
          )
          return
        }
        if (request.method === 'GET' && url.pathname === '/favicon.ico') {
          send(response, 204, '')
          return
        }

        const exchangeMatch = url.pathname.match(
          /^\/api\/embed\/v1\/launches\/(lch_[A-Za-z0-9_-]+)\/exchange$/
        )
        if (request.method === 'POST' && exchangeMatch) {
          const launch = state.launches.get(exchangeMatch[1])
          const body = await readJsonBody(request)
          if (request.headers['x-flow-embed-protocol'] !== protocolHeader
              || !launch
              || launch.consumed
              || body.launchCode !== launch.spec.launchCode
              || body.channelId !== launch.spec.channelId
              || body.parentOrigin !== state.parentOrigin) {
            sendJson(response, 400, {
              errorCode: 'EMBED_LAUNCH_INVALID',
              message: 'fixture launch invalid',
              traceId: 'trace-native-launch-invalid'
            })
            return
          }
          const actorActive = [...state.sessions.values()].some(session =>
            session.active && session.spec.actorKey === launch.spec.actorKey
          )
          if (actorActive) {
            state.sessionLimitResponses.push(launch.spec.key)
            state.exchangeLog.push({ key: launch.spec.key, status: 429 })
            sendJson(response, 429, {
              errorCode: 'EMBED_SESSION_LIMIT_EXCEEDED',
              message: 'Active Embed session limit has been reached',
              traceId: 'trace-native-session-limit'
            })
            return
          }
          launch.consumed = true
          const token = `opaque_embed_${launch.spec.key}_0123456789`
          const session = {
            token,
            spec: launch.spec,
            active: true,
            pinnedReleaseVersion: state.activeReleaseVersion
          }
          state.sessions.set(token, session)
          state.exchangeLog.push({
            key: launch.spec.key,
            status: 200,
            pinnedReleaseVersion: session.pinnedReleaseVersion
          })
          sendJson(response, 200, {
            accessToken: token,
            tokenType: 'Bearer',
            launchId: launch.spec.launchId,
            expiresAt: '2099-08-31T09:00:00.000Z',
            idleExpiresAt: '2099-08-31T08:35:00.000Z',
            heartbeatAfterSeconds: 60,
            protocolVersion: protocol
          })
          return
        }

        const session = sessionForRequest(request, state)
        if (!session) {
          sendJson(response, 401, {
            errorCode: 'EMBED_SESSION_MISSING',
            message: 'fixture session missing',
            traceId: 'trace-native-auth'
          })
          return
        }
        if (request.headers['x-flow-embed-protocol'] !== protocolHeader) {
          sendJson(response, 400, {
            errorCode: 'EMBED_PROTOCOL_HEADER_MISSING',
            message: 'fixture protocol header missing',
            traceId: 'trace-native-protocol'
          })
          return
        }

        if (request.method === 'GET'
            && url.pathname === '/api/embed/v1/runtime/bootstrap') {
          sendJson(response, 200, createBootstrap(session))
          return
        }
        if (request.method === 'GET'
            && url.pathname === '/api/embed/v1/runtime/schema') {
          sendJson(response, 200, createListSchema())
          return
        }
        if (request.method === 'POST'
            && url.pathname === '/api/embed/v1/runtime/list/query') {
          await readJsonBody(request)
          sendJson(response, 200, {
            items: [listRecord()],
            hasMore: false,
            pageNum: 1,
            pageSize: 20,
            total: 999999,
            sql: 'must-not-cross-list-boundary'
          })
          return
        }
        if (request.method === 'GET'
            && url.pathname === '/api/embed/v1/runtime/native-form-target') {
          const mode = String(url.searchParams.get('mode') || '').toUpperCase()
          const targetRecordId = url.searchParams.get('recordId')
          state.nativeTargetRequests.push({
            actorKey: session.spec.actorKey,
            mode,
            recordId: targetRecordId,
            version: session.pinnedReleaseVersion
          })
          sendJson(response, 200, {
            target: nativeTarget(
              session.pinnedReleaseVersion,
              mode,
              targetRecordId
            )
          })
          return
        }
        if (request.method === 'POST'
            && url.pathname === '/api/embed/v1/runtime/records') {
          const body = await readJsonBody(request)
          sendJson(response, 201, {
            receiptId: 'eor_native_browser_0123456789',
            record: {
              id: 'record-native-created',
              recordVersion: null,
              values: { title: body.data?.title || '' },
              meta: { createdAt: null, updatedAt: null }
            },
            effects: [],
            clientMutationId: body.clientMutationId
          })
          return
        }
        if (request.method === 'POST'
            && url.pathname === '/api/embed/v1/session/heartbeat') {
          await readJsonBody(request)
          sendJson(response, 200, {
            status: 'ACTIVE',
            idleExpiresAt: '2099-08-31T08:40:00.000Z',
            absoluteExpiresAt: '2099-08-31T09:00:00.000Z',
            nextHeartbeatAfterSeconds: 60
          })
          return
        }
        if (request.method === 'DELETE'
            && url.pathname === '/api/embed/v1/session') {
          session.active = false
          state.logoutAcks.push(session.spec.key)
          send(response, 204, '')
          return
        }

        if (request.method === 'GET'
            && url.pathname === `/api/entity/code/${entityCode}`) {
          sendJson(response, 200, entityDefinition())
          return
        }
        if (request.method === 'GET'
            && url.pathname === `/api/entity-status/list/${entityCode}`) {
          sendJson(response, 200, [])
          return
        }
        if (request.method === 'GET'
            && url.pathname === `/api/entity-forms/${formId}/runtime-release`) {
          const version = Number(url.searchParams.get('version'))
          const requestedReleaseId = url.searchParams.get('releaseId')
          const requestedToken = url.searchParams.get('releaseResolutionToken')
          state.releaseRequests.push({
            launchKey: session.spec.key,
            actorKey: session.spec.actorKey,
            version,
            releaseId: requestedReleaseId,
            releaseResolutionToken: requestedToken
          })
          if (requestedReleaseId !== releaseId(version)
              || requestedToken !== releaseToken(version)) {
            sendJson(response, 409, {
              errorCode: 'FORM_RELEASE_COORDINATE_MISMATCH',
              message: 'fixture release coordinate mismatch'
            })
            return
          }
          sendJson(response, 200, runtimeRelease(version))
          return
        }
        if (request.method === 'POST'
            && url.pathname === '/api/ui-runtime/form-actions/resolve') {
          const body = await readJsonBody(request)
          const mode = String(body.mode || '').toLowerCase()
          state.actionResolutions.push({
            actorKey: session.spec.actorKey,
            launchKey: session.spec.key,
            mode,
            recordId: body.recordId || null
          })
          sendJson(response, 200, resolvedActions(session, mode))
          return
        }
        if (request.method === 'POST'
            && /^\/api\/ui-runtime\/events\/[A-Z_]+\/execute$/.test(url.pathname)) {
          await readJsonBody(request)
          sendJson(response, 200, { effects: [] })
          return
        }
        if (request.method === 'POST'
            && url.pathname === `/api/entity-data/entity/${entityCode}/detail/${recordId}/load`) {
          await readJsonBody(request)
          sendJson(response, 200, detailRecord())
          return
        }
        if (request.method === 'POST'
            && url.pathname === `/api/entity-form/${formId}/unique-precheck`) {
          await readJsonBody(request)
          sendJson(response, 200, { available: true, checked: true })
          return
        }

        state.unhandledRequests.push({
          method: request.method,
          url: request.url,
          launchKey: session.spec.key
        })
        sendJson(response, 404, {
          errorCode: 'FIXTURE_ROUTE_NOT_FOUND',
          message: url.pathname
        })
      } catch (error) {
        if (!response.headersSent) {
          sendJson(response, 500, {
            errorCode: 'FIXTURE_FAILURE',
            message: error.message
          })
        } else {
          response.destroy(error)
        }
      }
    })
    const embedPort = await listen(embedServer)
    state.embedOrigin = `https://127.0.0.1:${embedPort}`

    parentServer = https.createServer(certificate, (request, response) => {
      const url = new URL(request.url, 'https://fixture.invalid')
      if (url.pathname === '/') {
        send(response, 200, `<!doctype html><html lang="zh-CN"><head>
          <meta charset="UTF-8"><title>Flow Native Embed Host E2E</title>
          <script src="/csp-probe.js"></script></head>
          <body><main id="embed-root"></main>
          <script type="module" src="/parent-main.js"></script></body></html>`, {
          'Content-Type': 'text/html;charset=UTF-8',
          'Content-Security-Policy': "default-src 'self'; script-src 'self'; script-src-attr 'none'; "
            + "style-src-attr 'unsafe-inline'; "
            + `frame-src ${state.embedOrigin}; object-src 'none'; base-uri 'none'`
        })
        return
      }
      if (url.pathname === '/csp-probe.js') {
        send(response, 200, cspProbeSource(), {
          'Content-Type': 'text/javascript;charset=UTF-8'
        })
        return
      }
      if (url.pathname === '/parent-main.js') {
        send(response, 200, parentMainSource(state.embedOrigin), {
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
        serveFile(
          response,
          path.join(sdkRoot, filename),
          'text/javascript;charset=UTF-8'
        )
        return
      }
      if (url.pathname === '/favicon.ico') {
        send(response, 204, '')
        return
      }
      send(response, 404, 'not found')
    })
    const parentPort = await listen(parentServer)
    state.parentOrigin = `https://127.0.0.1:${parentPort}`
    assert.notEqual(state.parentOrigin, state.embedOrigin)

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
        if (chromeOutput.join('').length < 16_000) {
          chromeOutput.push(String(chunk))
        }
      })
    }

    const targets = await waitFor(async () => {
      try {
        const response = await fetch(`http://127.0.0.1:${debugPort}/json/list`)
        const value = await response.json()
        return value.some(item => item.type === 'page' && item.url === 'about:blank')
          ? value
          : null
      } catch {
        if (chrome.exitCode !== null) {
          throw new Error(
            `Chrome 提前退出 (${chrome.exitCode})：${chromeOutput.join('').slice(-2000)}`
          )
        }
        return null
      }
    }, 'Chrome DevTools target')
    const target = targets.find(item =>
      item.type === 'page' && item.url === 'about:blank'
    )
    cdp = new CdpClient(target.webSocketDebuggerUrl)
    await cdp.connect()

    const networkRequests = new Map()
    const consoleMessages = []
    const logEntries = []
    const exceptions = []
    cdp.on('Network.requestWillBeSent', event => {
      networkRequests.set(event.requestId, {
        url: event.request.url,
        headers: event.request.headers || {}
      })
    })
    cdp.on('Runtime.consoleAPICalled', event => {
      consoleMessages.push((event.args || []).map(argument =>
        argument.value ?? argument.description ?? ''
      ).join(' '))
    })
    cdp.on('Runtime.exceptionThrown', event => {
      exceptions.push(
        event.exceptionDetails?.exception?.description
          || event.exceptionDetails?.text
          || 'unknown exception'
      )
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
    await waitFor(
      () => cdp.evaluate('Boolean(window.__flowHarness)'),
      '宿主 SDK fixture 初始化'
    )

    async function mountFixture(key, description) {
      await cdp.evaluate(`window.__flowHarness.mount(${JSON.stringify(key)})`)
      try {
        return await waitFor(async () => {
          const snapshot = await cdp.evaluate('window.__flowHarness.snapshot()')
          const initialized = snapshot.events.find(event =>
            event.type === 'initialized'
          )
          if (snapshot.events.some(event => event.type === 'error')) {
            throw new Error(JSON.stringify(snapshot.events))
          }
          return snapshot.state === 'connected' && initialized
            ? { snapshot, initialized }
            : null
        }, description)
      } catch (error) {
        const snapshot = await cdp.evaluate(
          'window.__flowHarness.snapshot()'
        ).catch(() => ({}))
        const iframeText = await cdp.frameEvaluate(
          state.embedOrigin,
          'document.body?.innerText || ""'
        ).catch(cause => String(cause?.message || cause))
        throw new Error(
          `${error.message}；snapshot=${JSON.stringify(snapshot)}；`
            + `iframe=${JSON.stringify(iframeText)}；`
            + `unhandled=${JSON.stringify(state.unhandledRequests)}；`
            + `exceptions=${JSON.stringify(exceptions)}；`
            + `console=${JSON.stringify(consoleMessages.slice(-20))}`
        )
      }
    }

    async function captureFrameCsp(key) {
      const violations = await cdp.frameEvaluate(
        state.embedOrigin,
        'window.__flowCspViolations || []'
      )
      observedCspViolations.push(...violations.map(item => ({ key, ...item })))
    }

    async function destroyFixture(key) {
      await captureFrameCsp(key)
      const logoutCountBefore = state.logoutAcks.length
      const result = await cdp.evaluate('window.__flowHarness.destroy()')
      assert.equal(result.ok, true, `${key} destroy 必须收到服务端 ACK`)
      assert.equal(result.widgetState, 'destroyed')
      assert.equal(result.iframeCount, 0)
      assert.equal(result.iframeUrl, '')
      assert.equal(result.launchCodeInWidget, '')
      assert.equal(state.logoutAcks.length, logoutCountBefore + 1)
      assert.equal(state.logoutAcks.at(-1), key)
      const destroyAck = result.events.at(-1)
      assert.equal(destroyAck?.type, 'ack')
      assert.equal(destroyAck?.payload?.command, 'destroy')
      return result
    }

    async function waitForDialog(selector, description) {
      return waitFor(() => cdp.frameEvaluate(state.embedOrigin, `(() => {
        const dialog = document.querySelector(${JSON.stringify(selector)})
        if (!dialog) return null
        const style = getComputedStyle(dialog)
        const rect = dialog.getBoundingClientRect()
        return style.display !== 'none' && rect.width > 0 && rect.height > 0
          ? { title: dialog.querySelector('.el-dialog__title')?.textContent.trim() || '' }
          : null
      })()`), description)
    }

    /**
     * 用 DevTools 输入域发送浏览器可信鼠标事件。Element Plus 的浮层触发器会
     * 区分完整 pointer/mouse 序列；直接执行 element.click() 不能代表真实用户。
     */
    async function trustedFrameClick(elementExpression) {
      const iframeRect = await cdp.evaluate(`(() => {
        const rect = document.querySelector('#embed-root iframe').getBoundingClientRect()
        return { x: rect.x, y: rect.y }
      })()`)
      const point = await cdp.frameEvaluate(state.embedOrigin, `(() => {
        const element = ${elementExpression}
        if (!element) return null
        element.scrollIntoView({ block: 'center', inline: 'center' })
        const rect = element.getBoundingClientRect()
        return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }
      })()`)
      assert.ok(point, `找不到可信点击目标：${elementExpression}`)
      const x = iframeRect.x + point.x
      const y = iframeRect.y + point.y
      await cdp.send('Input.dispatchMouseEvent', {
        type: 'mouseMoved', x, y, button: 'none'
      })
      await cdp.send('Input.dispatchMouseEvent', {
        type: 'mousePressed', x, y, button: 'left', clickCount: 1
      })
      await cdp.send('Input.dispatchMouseEvent', {
        type: 'mouseReleased', x, y, button: 'left', clickCount: 1
      })
    }

    // 直接 FORM 首次会话固定在 R1，并由原生 EntityDataFormDialog 渲染完整组件树。
    const directR1 = await mountFixture(
      'directEditorR1',
      '直接 FORM R1 初始化'
    )
    const directR1Url = directR1.snapshot.iframeUrl
    assert.equal(directR1.initialized.payload.viewKey, stableViewKey)
    assert.equal(directR1.initialized.payload.surfaceType, 'FORM')
    assert.deepEqual(
      directR1.initialized.payload.capabilities,
      ['RECORD_CREATE', 'ACTION_EXECUTE']
    )
    assert.deepEqual(directR1.snapshot.localStorage, {})
    assert.deepEqual(directR1.snapshot.sessionStorage, {})
    assert.equal(directR1.snapshot.cookie, '')
    assert.deepEqual(directR1.snapshot.violations, [])
    const directDialogR1 = await waitForDialog(
      '.entity-form-dialog:not(.entity-approval-dialog)',
      '直接 FORM 原生 EntityDataFormDialog'
    )
    assert.match(directDialogR1.title, /Flow 原生需求表单 R1/)

    const nativeComponentSnapshot = await cdp.frameEvaluate(
      state.embedOrigin,
      `(() => {
        const dialog = document.querySelector('.entity-form-dialog:not(.entity-approval-dialog)')
        const labels = [...dialog.querySelectorAll('.node-field .el-form-item__label')]
          .map(label => label.textContent.replace(/\\s+/g, ' ').trim())
        return {
          dialogClass: dialog.className,
          dateCount: dialog.querySelectorAll('.date-field').length,
          selectCount: dialog.querySelectorAll('.select-field').length,
          switchCount: dialog.querySelectorAll('.switch-field').length,
          richTextCount: dialog.querySelectorAll('.rich-text-field').length,
          canaryCount: dialog.querySelectorAll('.acceptance-score-field').length,
          labels,
          oldProjectionCount: document.querySelectorAll(
            '.embed-form, .trusted-published-form-runtime'
          ).length
        }
      })()`
    )
    assert.match(nativeComponentSnapshot.dialogClass, /el-dialog/)
    assert.equal(nativeComponentSnapshot.dateCount, 2)
    assert.equal(nativeComponentSnapshot.selectCount, 2)
    assert.equal(nativeComponentSnapshot.switchCount, 1)
    assert.equal(nativeComponentSnapshot.richTextCount, 1)
    assert.equal(nativeComponentSnapshot.canaryCount, 1)
    assert.equal(nativeComponentSnapshot.oldProjectionCount, 0)
    for (const label of [
      '标题', '到期日期', '开始时间', '处理状态', '工单标签',
      '是否启用', '需求描述', '扩展验收分'
    ]) {
      assert.ok(
        nativeComponentSnapshot.labels.some(value => value.includes(label)),
        `原生发布表单应展示字段：${label}`
      )
    }

    const editorButtons = await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      `(() => {
        const buttons = [...document.querySelectorAll(
          '.entity-form-dialog:not(.entity-approval-dialog) .el-dialog__footer .form-action-bar button'
        )].map(button => button.textContent.replace(/\\s+/g, ' ').trim())
        return buttons.length ? buttons : null
      })()`
    ), '编辑映射用户原生按钮')
    assert.deepEqual(editorButtons, ['取消', '重置', '保存'])

    // 日期、下拉与确认框必须由 iframe 内的 Element Plus 原生组件 Teleport 到 body。
    await trustedFrameClick(`(() => {
      const field = [...document.querySelectorAll('.node-field')]
        .find(item => item.querySelector('.el-form-item__label')?.textContent.includes('到期日期'))
      return field?.querySelector('.date-field input')
    })()`)
    const datePopup = await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      `(() => {
        const panel = [...document.querySelectorAll('.el-picker-panel')]
          .find(item => item.getBoundingClientRect().height > 0)
        return panel ? {
          inIframeDocument: panel.ownerDocument === document,
          inIframeBody: document.body.contains(panel),
          outsideDialog: !panel.closest('.entity-form-dialog')
        } : null
      })()`
    ), 'iframe 内原生日期选择面板', 8_000).catch(async error => {
      const diagnostics = await cdp.frameEvaluate(state.embedOrigin, `(() => ({
        dateHtml: document.querySelector('.date-field')?.outerHTML || '',
        active: document.activeElement?.outerHTML || '',
        poppers: [...document.querySelectorAll('.el-popper')].map(item => ({
          className: item.className,
          display: getComputedStyle(item).display,
          rect: item.getBoundingClientRect().toJSON()
        }))
      }))()`)
      throw new Error(`${error.message}；${JSON.stringify(diagnostics)}`)
    })
    assert.deepEqual(datePopup, {
      inIframeDocument: true,
      inIframeBody: true,
      outsideDialog: true
    })
    await cdp.send('Input.dispatchKeyEvent', {
      type: 'keyDown', key: 'Escape', code: 'Escape'
    })
    await cdp.send('Input.dispatchKeyEvent', {
      type: 'keyUp', key: 'Escape', code: 'Escape'
    })
    await waitFor(() => cdp.frameEvaluate(state.embedOrigin, `
      ![...document.querySelectorAll('.el-picker-panel')]
        .some(item => item.getBoundingClientRect().height > 0)
    `), '关闭原生日期选择面板')

    await trustedFrameClick(`(() => {
      const field = [...document.querySelectorAll('.node-field')]
        .find(item => item.querySelector('.el-form-item__label')?.textContent.includes('处理状态'))
      return field?.querySelector('.el-select__wrapper')
    })()`)
    const selectPopup = await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      `(() => {
        const popup = [...document.querySelectorAll('.el-select__popper')]
          .find(item => item.getBoundingClientRect().height > 0)
        return popup ? {
          inIframeDocument: popup.ownerDocument === document,
          inIframeBody: document.body.contains(popup),
          outsideDialog: !popup.closest('.entity-form-dialog'),
          options: [...popup.querySelectorAll('.el-select-dropdown__item')]
            .map(item => item.textContent.trim())
        } : null
      })()`
    ), 'iframe 内原生下拉面板', 8_000).catch(async error => {
      const diagnostics = await cdp.frameEvaluate(state.embedOrigin, `(() => ({
        selectHtml: document.querySelector('.select-field')?.outerHTML || '',
        active: document.activeElement?.outerHTML || '',
        poppers: [...document.querySelectorAll('.el-select__popper')].map(item => ({
          className: item.className,
          display: getComputedStyle(item).display,
          rect: item.getBoundingClientRect().toJSON()
        }))
      }))()`)
      throw new Error(`${error.message}；${JSON.stringify(diagnostics)}`)
    })
    assert.equal(selectPopup.inIframeDocument, true)
    assert.equal(selectPopup.inIframeBody, true)
    assert.equal(selectPopup.outsideDialog, true)
    assert.ok(selectPopup.options.includes('草稿'))
    assert.ok(selectPopup.options.includes('处理中'))
    assert.ok(selectPopup.options.includes('已完成'))
    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      const popup = [...document.querySelectorAll('.el-select__popper')]
        .find(item => item.getBoundingClientRect().height > 0)
      ;[...(popup?.querySelectorAll('.el-select-dropdown__item') || [])]
        .find(item => item.textContent.trim() === '已完成')?.click()
    })()`)

    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      const button = [...document.querySelectorAll(
        '.entity-form-dialog:not(.entity-approval-dialog) .el-dialog__footer button'
      )].find(item => item.textContent.trim() === '保存')
      button?.click()
    })()`)
    const confirmPopup = await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      `(() => {
        const box = document.querySelector('.el-message-box')
        return box ? {
          inIframeDocument: box.ownerDocument === document,
          inIframeBody: document.body.contains(box),
          outsideDialog: !box.closest('.entity-form-dialog'),
          text: box.textContent.replace(/\\s+/g, ' ').trim()
        } : null
      })()`
    ), 'iframe 内原生确认弹框')
    assert.equal(confirmPopup.inIframeDocument, true)
    assert.equal(confirmPopup.inIframeBody, true)
    assert.equal(confirmPopup.outsideDialog, true)
    assert.match(confirmPopup.text, /确认由 Flow 原生表单保存吗/)
    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      const box = document.querySelector('.el-message-box')
      ;[...(box?.querySelectorAll('button') || [])]
        .find(item => item.textContent.trim() === '取消')?.click()
    })()`)
    await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      '!document.querySelector(\'.el-message-box\')'
    ), '关闭原生确认弹框')

    // 管理员激活 R2 后，已打开的 R1 Session 仍固定 R1；新 Launch 自动取得 R2。
    const r1ReleaseCount = state.releaseRequests.filter(item =>
      item.launchKey === 'directEditorR1' && item.version === 1
    ).length
    state.activeReleaseVersion = 2
    await cdp.evaluate('window.__flowHarness.refresh()')
    await waitFor(() => state.releaseRequests.filter(item =>
      item.launchKey === 'directEditorR1' && item.version === 1
    ).length > r1ReleaseCount, '已打开会话继续读取固定 R1')
    const refreshedR1Title = await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      `document.querySelector(
        '.entity-form-dialog:not(.entity-approval-dialog) .el-dialog__title'
      )?.textContent.includes('R1')`
    ), 'R1 会话刷新后仍显示 R1')
    assert.equal(refreshedR1Title, true)
    await destroyFixture('directEditorR1')

    // destroy ACK 后，同一映射用户的新 Launch 必须能立刻打开，且跟随新 ACTIVE R2。
    const directR2 = await mountFixture(
      'directEditorR2',
      'destroy ACK 后同用户重新打开 R2'
    )
    const directR2Url = directR2.snapshot.iframeUrl
    assert.equal(directR2.initialized.payload.viewKey, stableViewKey)
    const directDialogR2 = await waitForDialog(
      '.entity-form-dialog:not(.entity-approval-dialog)',
      '新会话跟随 ACTIVE R2'
    )
    assert.match(directDialogR2.title, /Flow 原生需求表单 R2/)
    assert.deepEqual(
      stableLaunchUrlShape(directR1Url),
      stableLaunchUrlShape(directR2Url),
      '最新 ACTIVE 解析不得把 Release/版本写入宿主 Launch URL'
    )
    assert.equal(new URL(directR1Url).search, '')
    assert.equal(new URL(directR1Url).hash, '')
    assert.equal(new URL(directR2Url).search, '')
    assert.equal(new URL(directR2Url).hash, '')
    assert.equal(state.sessionLimitResponses.length, 0)
    assert.deepEqual(
      state.exchangeLog.filter(item =>
        ['directEditorR1', 'directEditorR2'].includes(item.key)
      ).map(item => [item.key, item.status, item.pinnedReleaseVersion]),
      [
        ['directEditorR1', 200, 1],
        ['directEditorR2', 200, 2]
      ]
    )
    await destroyFixture('directEditorR2')

    // LIST 保留投影列表，但 CREATE/VIEW 两条本地导航都进入同一 Flow 原生 Dialog。
    const listMount = await mountFixture('listEditor', 'LIST 初始化')
    assert.equal(listMount.initialized.payload.surfaceType, 'LIST')
    await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      `document.querySelector('#embed-list-title')?.textContent === 'Flow 原生嵌入列表'`
    ), '原有 LIST 渲染')
    const listText = await cdp.frameEvaluate(
      state.embedOrigin,
      `document.querySelector('.embed-list')?.innerText || ''`
    )
    assert.match(listText, /WO-NATIVE-001/)
    assert.equal(listText.includes('must-not-cross-list-boundary'), false)

    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      ;[...document.querySelectorAll('.embed-list button')]
        .find(button => button.textContent.trim() === '新建')?.click()
    })()`)
    const listCreateDialog = await waitForDialog(
      '.entity-form-dialog:not(.entity-approval-dialog)',
      'LIST → CREATE 原生 EntityDataFormDialog'
    )
    assert.match(listCreateDialog.title, /Flow 原生需求表单 R2/)
    assert.equal(await cdp.frameEvaluate(
      state.embedOrigin,
      `document.querySelectorAll(
        '.entity-form-dialog:not(.entity-approval-dialog) .acceptance-score-field'
      ).length`
    ), 1, 'LIST → CREATE 必须复用同一扩展 registry')
    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      ;[...document.querySelectorAll(
        '.entity-form-dialog:not(.entity-approval-dialog) .el-dialog__footer button'
      )].find(button => button.textContent.trim() === '取消')?.click()
    })()`)
    await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      'Boolean(document.querySelector(\'#embed-list-title\'))'
    ), 'LIST → CREATE 取消返回列表')

    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      ;[...document.querySelectorAll('.embed-list button')]
        .find(button => button.textContent.trim() === '查看')?.click()
    })()`)
    await waitForDialog(
      '.entity-form-dialog.entity-approval-dialog',
      'LIST → VIEW 原生 EntityApprovalDialog'
    )
    const viewSnapshot = await cdp.frameEvaluate(state.embedOrigin, `(() => {
      const dialog = document.querySelector('.entity-form-dialog.entity-approval-dialog')
      const titleField = [...(dialog?.querySelectorAll('.node-field') || [])]
        .find(item => item.querySelector('.el-form-item__label')?.textContent.includes('标题'))
      return {
        hasNativeDialog: dialog?.classList.contains('el-dialog') === true,
        text: dialog?.textContent.replace(/\\s+/g, ' ').trim() || '',
        titleValue: titleField?.querySelector('input')?.value || '',
        canaryCount: dialog?.querySelectorAll('.acceptance-score-field').length || 0,
        buttons: [...(dialog?.querySelectorAll(
          '.el-dialog__footer .form-action-bar button'
        ) || [])].map(button => button.textContent.trim())
      }
    })()`)
    assert.equal(viewSnapshot.hasNativeDialog, true)
    assert.equal(viewSnapshot.titleValue, 'LIST 查看进入原生详情')
    assert.equal(viewSnapshot.canaryCount, 1)
    assert.deepEqual(viewSnapshot.buttons, ['关闭'])
    await cdp.frameEvaluate(state.embedOrigin, `(() => {
      const dialog = document.querySelector('.entity-form-dialog.entity-approval-dialog')
      ;[...(dialog?.querySelectorAll('.el-dialog__footer button') || [])]
        .find(button => button.textContent.trim() === '关闭')?.click()
    })()`)
    await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      'Boolean(document.querySelector(\'#embed-list-title\'))'
    ), 'LIST → VIEW 关闭返回列表')
    assert.deepEqual(
      state.nativeTargetRequests
        .filter(item => item.actorKey === 'editor')
        .map(item => [item.mode, item.recordId, item.version]),
      [
        ['CREATE', null, 2],
        ['VIEW', recordId, 2]
      ]
    )
    await destroyFixture('listEditor')

    // 两个 Launch 的 Embed grant 相同，按钮差异只能来自映射 Flow 用户实时解析。
    const restricted = await mountFixture(
      'directRestricted',
      '受限映射用户直接 FORM 初始化'
    )
    assert.deepEqual(
      restricted.initialized.payload.capabilities,
      ['RECORD_CREATE', 'ACTION_EXECUTE']
    )
    await waitForDialog(
      '.entity-form-dialog:not(.entity-approval-dialog)',
      '受限映射用户原生 Dialog'
    )
    const restrictedButtons = await waitFor(() => cdp.frameEvaluate(
      state.embedOrigin,
      `(() => {
        const buttons = [...document.querySelectorAll(
          '.entity-form-dialog:not(.entity-approval-dialog) .el-dialog__footer .form-action-bar button'
        )].map(button => button.textContent.replace(/\\s+/g, ' ').trim())
        return buttons.length ? buttons : null
      })()`
    ), '受限映射用户权限按钮')
    assert.deepEqual(restrictedButtons, ['取消'])
    assert.notDeepEqual(editorButtons, restrictedButtons)
    assert.ok(state.actionResolutions.some(item =>
      item.actorKey === 'editor' && item.mode === 'create'
    ))
    assert.ok(state.actionResolutions.some(item =>
      item.actorKey === 'restricted' && item.mode === 'create'
    ))

    if (process.env.FLOW_EMBED_E2E_SCREENSHOT_PATH) {
      const screenshot = await cdp.send('Page.captureScreenshot', {
        format: 'png',
        captureBeyondViewport: true,
        fromSurface: true
      })
      writeFileSync(
        process.env.FLOW_EMBED_E2E_SCREENSHOT_PATH,
        Buffer.from(screenshot.data, 'base64')
      )
    }
    await destroyFixture('directRestricted')

    const formProjectionRequests = state.requestLog.filter(item =>
      /^\/api\/embed\/v1\/runtime\/form(?:\/|$)/.test(item.pathname)
    )
    assert.deepEqual(
      formProjectionRequests,
      [],
      '单一原生运行时不得调用旧 /api/embed/v1/runtime/form* 投影链'
    )
    const requiredNativePaths = [
      `/api/entity/code/${entityCode}`,
      `/api/entity-forms/${formId}/runtime-release`,
      '/api/ui-runtime/form-actions/resolve',
      `/api/entity-data/entity/${entityCode}/detail/${recordId}/load`
    ]
    for (const requiredPath of requiredNativePaths) {
      assert.ok(
        state.requestLog.some(item => item.pathname === requiredPath),
        `缺少 Flow 原生 API 请求：${requiredPath}`
      )
    }
    const nativeApiRequests = state.requestLog.filter(item =>
      item.pathname.startsWith('/api/entity/')
        || item.pathname.startsWith('/api/entity-forms/')
        || item.pathname.startsWith('/api/entity-data/')
        || item.pathname.startsWith('/api/ui-runtime/')
    )
    assert.ok(nativeApiRequests.length > 0)
    for (const request of nativeApiRequests) {
      assert.match(
        String(request.headers.authorization || ''),
        /^Bearer opaque_embed_/,
        `原生 API 必须携带 opaque Embed token：${request.pathname}`
      )
      assert.equal(
        request.headers['x-flow-embed-protocol'],
        protocolHeader,
        `原生 API 必须携带 Embed 协议头：${request.pathname}`
      )
    }
    assert.deepEqual(
      state.unhandledRequests,
      [],
      `fixture 存在未覆盖请求：${JSON.stringify(state.unhandledRequests)}`
    )
    assert.deepEqual(state.sessionLimitResponses, [])
    assert.deepEqual(
      [...new Set(state.releaseRequests
        .filter(item => item.launchKey === 'directEditorR1')
        .map(item => item.version))],
      [1],
      'R1 已打开会话不得漂移到 R2'
    )
    assert.ok(state.releaseRequests.some(item =>
      item.launchKey === 'directEditorR2' && item.version === 2
    ), '新会话必须跟随 ACTIVE R2')

    const parentCspViolations = await cdp.evaluate(
      'window.__flowCspViolations || []'
    )
    const securityCspLogs = logEntries.filter(entry =>
      entry.source === 'security'
        && /(content security policy|violat(?:ed|ion)|refused to)/i.test(
          String(entry.text || '')
        )
    )
    assert.deepEqual(parentCspViolations, [])
    assert.deepEqual(observedCspViolations, [])
    assert.deepEqual(
      securityCspLogs,
      [],
      `浏览器存在 CSP violation：${JSON.stringify(securityCspLogs)}`
    )
    assert.deepEqual(
      exceptions,
      [],
      `浏览器存在未捕获异常：${exceptions.join('\n')}`
    )
    assert.equal(
      [...networkRequests.values()].some(request =>
        Object.values(launches).some(spec => request.url.includes(spec.launchCode))
      ),
      false,
      '一次性 Launch code 不得进入 URL'
    )

    console.log('native Flow Embed cross-origin browser e2e passed')
  } finally {
    if (cdp) cdp.close()
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
      closeServer(parentServer)
    ])
    rmSync(temporaryDirectory, { recursive: true, force: true })
  }
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
        if (message.error) {
          pending.reject(new Error(
            `${message.error.message}: ${message.error.data || ''}`
          ))
        } else {
          pending.resolve(message.result || {})
        }
        return
      }
      if (message.method === 'Runtime.executionContextCreated') {
        const context = message.params?.context
        if (context?.auxData?.isDefault && context.auxData.frameId) {
          this.defaultContexts.set(context.auxData.frameId, context.id)
        }
      } else if (message.method === 'Runtime.executionContextDestroyed') {
        for (const [frameId, contextId] of this.defaultContexts) {
          if (contextId === message.params?.executionContextId) {
            this.defaultContexts.delete(frameId)
          }
        }
      } else if (message.method === 'Runtime.executionContextsCleared') {
        this.defaultContexts.clear()
      }
      for (const handler of this.handlers.get(message.method) || []) {
        try {
          handler(message.params || {})
        } catch {
          // 采集器异常不能中断 CDP 协议处理。
        }
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
        resolve(value) {
          clearTimeout(timer)
          resolve(value)
        },
        reject(error) {
          clearTimeout(timer)
          reject(error)
        }
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
      throw new Error(
        result.exceptionDetails.exception?.description
          || result.exceptionDetails.text
          || '浏览器表达式执行失败'
      )
    }
    return result.result?.value
  }

  async frameEvaluate(origin, expression) {
    const contextId = await waitFor(async () => {
      const { frameTree } = await this.send('Page.getFrameTree')
      const frame = findFrame(frameTree, candidate =>
        candidate.url.startsWith(origin)
      )
      return frame ? this.defaultContexts.get(frame.id) : null
    }, `${origin} 默认执行上下文`)
    return this.evaluate(expression, contextId)
  }

  close() {
    for (const pending of this.pending.values()) {
      pending.reject(new Error('CDP 已关闭'))
    }
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
