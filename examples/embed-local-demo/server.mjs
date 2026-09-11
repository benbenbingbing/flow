import { spawnSync } from 'node:child_process'
import {
  chmodSync,
  existsSync,
  mkdirSync,
  readFileSync,
  statSync
} from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import {
  createPrivateKey,
  randomUUID,
  sign as signBytes
} from 'node:crypto'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const demoDirectory = path.dirname(fileURLToPath(import.meta.url))
const repositoryRoot = path.resolve(demoDirectory, '../..')
const MAX_HOST_REQUEST_BYTES = 16 * 1024
const MAX_FLOW_RESPONSE_BYTES = 2 * 1024 * 1024
const DEFAULT_REQUEST_TIMEOUT_MS = 15_000
const SAFE_RECORD_ID = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/
const SAFE_VIEW_KEY = /^[A-Za-z][A-Za-z0-9._-]{0,99}$/
const FORM_PRESENTATIONS = new Set(['seamless', 'dialog'])
const SDK_FILENAMES = new Set(['index.js', 'FlowEmbedWidget.js', 'protocol.js'])
const EMBED_CONTROL_PROXY_PREFIXES = [
  '/embed/v1/launches/',
  '/api/embed/v1/'
]
const EMBED_DELEGATED_DENIED_PREFIXES = [
  '/api/auth/',
  '/api/embed-management/',
  '/api/open/',
  '/api/integration-applications/'
]
const PASSTHROUGH_RESPONSE_HEADERS = new Set([
  'cache-control',
  'content-length',
  'content-security-policy',
  'content-type',
  'idempotent-replay',
  'location',
  'permissions-policy',
  'pragma',
  'referrer-policy',
  'retry-after',
  'x-business-trace-key',
  'x-content-type-options',
  'x-request-id',
  'x-trace-id'
])
const PASSTHROUGH_REQUEST_HEADERS = new Set([
  'accept',
  'accept-language',
  'authorization',
  'content-length',
  'content-type',
  'idempotency-key',
  'user-agent',
  'x-business-trace-key',
  'x-flow-embed-protocol',
  'x-request-id',
  'x-trace-id'
])

export class DemoConfigurationError extends Error {
  constructor(message) {
    super(message)
    this.name = 'DemoConfigurationError'
  }
}

export class FlowRemoteError extends Error {
  constructor(message, { status = 502, errorCode = 'FLOW_DEMO_UPSTREAM_ERROR', traceId } = {}) {
    super(message)
    this.name = 'FlowRemoteError'
    this.status = status
    this.errorCode = errorCode
    this.traceId = traceId
  }
}

function requiredText(value, label) {
  const normalized = String(value || '').trim()
  if (!normalized) throw new DemoConfigurationError(`${label} 未配置`)
  return normalized
}

function exactHttpsOrigin(value, label) {
  let parsed
  try {
    parsed = new URL(requiredText(value, label))
  } catch (cause) {
    if (cause instanceof DemoConfigurationError) throw cause
    throw new DemoConfigurationError(`${label} 必须是完整 HTTPS Origin`)
  }
  if (parsed.protocol !== 'https:'
    || parsed.username || parsed.password
    || parsed.pathname !== '/' || parsed.search || parsed.hash
    || !['localhost', '127.0.0.1'].includes(parsed.hostname)) {
    throw new DemoConfigurationError(`${label} 必须是 localhost/127.0.0.1 的完整 HTTPS Origin`)
  }
  return parsed.origin
}

function flowBaseUrl(value) {
  let parsed
  try {
    parsed = new URL(requiredText(value, 'FLOW_DEMO_FLOW_BASE_URL'))
  } catch (cause) {
    if (cause instanceof DemoConfigurationError) throw cause
    throw new DemoConfigurationError('FLOW_DEMO_FLOW_BASE_URL 格式无效')
  }
  if (!['http:', 'https:'].includes(parsed.protocol)
    || parsed.username || parsed.password
    || parsed.pathname !== '/' || parsed.search || parsed.hash) {
    throw new DemoConfigurationError('FLOW_DEMO_FLOW_BASE_URL 必须是无路径的 HTTP(S) Origin')
  }
  return parsed.origin
}

function resolveConfiguredPath(value, fallback) {
  const configured = String(value || '').trim()
  return path.resolve(repositoryRoot, configured || fallback)
}

function parseAllowedEntryModes(value) {
  const values = String(value || 'CREATE,VIEW')
    .split(',')
    .map(item => item.trim().toUpperCase())
    .filter(Boolean)
  const unique = [...new Set(values)]
  if (!unique.length || unique.some(item => !['LIST', 'CREATE', 'VIEW'].includes(item))) {
    throw new DemoConfigurationError('FLOW_DEMO_ALLOWED_ENTRY_MODES 只能包含 LIST、CREATE、VIEW')
  }
  return Object.freeze(unique)
}

function normalizeViewKey(value, label) {
  const normalized = requiredText(value, label)
  if (!SAFE_VIEW_KEY.test(normalized)) {
    throw new DemoConfigurationError(`${label} 格式无效`)
  }
  return normalized
}

function createDemoTarget(key, label, surfaceType, viewKey, allowedEntryModes) {
  return Object.freeze({
    key,
    label,
    surfaceType,
    viewKey: normalizeViewKey(viewKey, `${label} View Key`),
    allowedEntryModes: Object.freeze([...allowedEntryModes])
  })
}

/**
 * 默认同时开放仓库内的 LIST 与 FORM 示例；若继续传旧单 View 环境变量，则保持原启动命令语义。
 * 浏览器只能选择这里生成的 target key，不能用请求参数覆盖真实 View Key。
 */
function buildDemoTargets(env) {
  const legacyViewKey = String(env.FLOW_DEMO_VIEW_KEY || '').trim()
  const legacyEntryModes = String(env.FLOW_DEMO_ALLOWED_ENTRY_MODES || '').trim()
  if (legacyViewKey || legacyEntryModes) {
    const modes = parseAllowedEntryModes(legacyEntryModes || 'CREATE,VIEW')
    const listOnly = modes.length === 1 && modes[0] === 'LIST'
    return Object.freeze([
      createDemoTarget(
        'default',
        listOnly ? '需求列表' : '需求表单',
        listOnly ? 'LIST' : 'FORM',
        legacyViewKey || (listOnly ? 'req-list' : 'zdwreq-form-demo'),
        modes
      )
    ])
  }

  return Object.freeze([
    createDemoTarget(
      'list',
      '需求列表',
      'LIST',
      env.FLOW_DEMO_LIST_VIEW_KEY || 'req-list',
      ['LIST']
    ),
    createDemoTarget(
      'form',
      '需求表单',
      'FORM',
      env.FLOW_DEMO_FORM_VIEW_KEY || 'zdwreq-form-demo',
      ['CREATE', 'VIEW']
    )
  ])
}

/**
 * 读取不含秘密值的示例配置。实际 Client Secret 和人员断言私钥由启动阶段单独加载，
 * 不进入浏览器配置，也不会被打印到日志。
 */
export function buildConfig(env = process.env) {
  const hostOrigin = exactHttpsOrigin(
    env.FLOW_DEMO_HOST_ORIGIN || 'https://localhost:3443',
    'FLOW_DEMO_HOST_ORIGIN'
  )
  const embedOrigin = exactHttpsOrigin(
    env.FLOW_DEMO_EMBED_ORIGIN || 'https://localhost:8443',
    'FLOW_DEMO_EMBED_ORIGIN'
  )
  if (hostOrigin === embedOrigin) {
    throw new DemoConfigurationError('宿主 Origin 与 Embed Origin 必须隔离')
  }
  const hostUrl = new URL(hostOrigin)
  const embedUrl = new URL(embedOrigin)
  const bindAddress = String(env.FLOW_DEMO_BIND_ADDRESS || '127.0.0.1').trim()
  if (!['127.0.0.1', '::1'].includes(bindAddress)) {
    throw new DemoConfigurationError('本地示例只允许绑定 127.0.0.1 或 ::1')
  }

  return Object.freeze({
    repositoryRoot,
    demoDirectory,
    bindAddress,
    hostOrigin,
    embedOrigin,
    hostPort: Number(hostUrl.port || 443),
    embedPort: Number(embedUrl.port || 443),
    hostHeader: hostUrl.host,
    embedHeader: embedUrl.host,
    flowBaseUrl: flowBaseUrl(env.FLOW_DEMO_FLOW_BASE_URL || 'http://127.0.0.1:8080'),
    flowCaFile: String(env.FLOW_DEMO_FLOW_CA_FILE || '').trim()
      ? resolveConfiguredPath(env.FLOW_DEMO_FLOW_CA_FILE, '')
      : '',
    credentialsFile: resolveConfiguredPath(
      env.FLOW_DEMO_OAUTH_CREDENTIALS_FILE,
      '.codex-artifacts/embed-demo/oauth-credentials.json'
    ),
    assertionPrivateKeyFile: resolveConfiguredPath(
      env.FLOW_DEMO_ASSERTION_PRIVATE_KEY_FILE,
      '.codex-artifacts/embed-demo/assertion-private.pem'
    ),
    assertionIssuer: String(
      env.FLOW_DEMO_ASSERTION_ISSUER || 'https://id.embed-demo.local'
    ).trim(),
    assertionAudience: String(
      env.FLOW_DEMO_ASSERTION_AUDIENCE || 'flow-embed-launch'
    ).trim(),
    assertionSubject: String(
      env.FLOW_DEMO_ASSERTION_SUBJECT || 'demo-lisi-001'
    ).trim(),
    assertionKeyId: String(
      env.FLOW_DEMO_ASSERTION_KEY_ID || 'embed-demo-rs256-20260828'
    ).trim(),
    targets: buildDemoTargets(env),
    tlsCertificateFile: resolveConfiguredPath(
      env.FLOW_DEMO_TLS_CERT_FILE,
      'examples/embed-local-demo/.runtime/localhost-cert.pem'
    ),
    tlsPrivateKeyFile: resolveConfiguredPath(
      env.FLOW_DEMO_TLS_KEY_FILE,
      'examples/embed-local-demo/.runtime/localhost-key.pem'
    ),
    embedAssetsRoot: path.resolve(repositoryRoot, 'workflow-web/dist/embed-assets'),
    sdkRoot: path.resolve(repositoryRoot, 'workflow-web/packages/flow-embed-sdk/src'),
    publicRoot: path.resolve(demoDirectory, 'public'),
    clientId: String(env.FLOW_DEMO_CLIENT_ID || '').trim(),
    clientSecret: String(env.FLOW_DEMO_CLIENT_SECRET || '').trim()
  })
}

function assertPrivatePermissions(filename, label) {
  const mode = statSync(filename).mode & 0o777
  if ((mode & 0o077) !== 0) {
    throw new DemoConfigurationError(`${label} 权限过宽，请先执行 chmod 600`)
  }
}

function readPrivateFile(filename, label) {
  if (!existsSync(filename)) {
    throw new DemoConfigurationError(`${label} 不存在：${filename}`)
  }
  assertPrivatePermissions(filename, label)
  return readFileSync(filename, 'utf8')
}

/** 加载只在第三方后端内存中使用的 OAuth 凭据和人员断言私钥。 */
export function loadSecrets(config) {
  let clientId = config.clientId
  let clientSecret = config.clientSecret
  if (!clientId || !clientSecret) {
    const text = readPrivateFile(config.credentialsFile, 'OAuth 凭据文件')
    let document
    try {
      document = JSON.parse(text)
    } catch {
      throw new DemoConfigurationError('OAuth 凭据文件不是合法 JSON')
    }
    clientId = clientId || String(document?.clientId || '').trim()
    clientSecret = clientSecret || String(document?.clientSecret || '').trim()
  }
  if (!clientId || !clientSecret) {
    throw new DemoConfigurationError('OAuth Client ID/Secret 未配置完整')
  }

  const assertionPrivateKey = readPrivateFile(
    config.assertionPrivateKeyFile,
    '人员断言私钥'
  )
  try {
    createPrivateKey(assertionPrivateKey)
  } catch {
    throw new DemoConfigurationError('人员断言私钥不是可用的 PEM 私钥')
  }
  return Object.freeze({ clientId, clientSecret, assertionPrivateKey })
}

function base64urlJson(value) {
  return Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
}

/** 使用 RS256 签发一次性、短生命周期且带唯一 jti 的外部人员断言。 */
export function signUserAssertion({
  privateKey,
  issuer,
  audience,
  subject,
  keyId,
  nowSeconds = Math.floor(Date.now() / 1000),
  jwtId = randomUUID()
}) {
  for (const [label, value] of Object.entries({ issuer, audience, subject, keyId, jwtId })) {
    if (!String(value || '').trim()) throw new DemoConfigurationError(`JWT ${label} 未配置`)
  }
  const encodedHeader = base64urlJson({ alg: 'RS256', kid: keyId, typ: 'JWT' })
  const encodedClaims = base64urlJson({
    iss: issuer,
    sub: subject,
    aud: audience,
    iat: nowSeconds,
    exp: nowSeconds + 60,
    jti: jwtId
  })
  const signingInput = `${encodedHeader}.${encodedClaims}`
  const signature = signBytes('RSA-SHA256', Buffer.from(signingInput), privateKey)
    .toString('base64url')
  return `${signingInput}.${signature}`
}

/**
 * 宿主浏览器只能选择管理员预先允许的入口意图；View、人员、Origin 和 Context 均由
 * 第三方后端固定，不能通过浏览器请求覆盖。
 */
export function normalizeLaunchIntent(value, allowedEntryModes = ['CREATE', 'VIEW']) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new FlowRemoteError('启动参数必须是 JSON 对象', {
      status: 400,
      errorCode: 'FLOW_DEMO_REQUEST_INVALID'
    })
  }
  const allowedKeys = new Set(['mode', 'recordId', 'theme', 'formPresentation'])
  if (Object.keys(value).some(key => !allowedKeys.has(key))) {
    throw new FlowRemoteError('启动参数包含未开放字段', {
      status: 400,
      errorCode: 'FLOW_DEMO_REQUEST_INVALID'
    })
  }
  const mode = String(value.mode || '').trim().toUpperCase()
  if (!allowedEntryModes.includes(mode)) {
    throw new FlowRemoteError('入口模式未被本示例授权', {
      status: 400,
      errorCode: 'FLOW_DEMO_ENTRY_NOT_ALLOWED'
    })
  }
  const recordIdPresent = Object.prototype.hasOwnProperty.call(value, 'recordId')
  const recordId = String(value.recordId || '').trim()
  if (mode === 'VIEW' && (!recordIdPresent || !SAFE_RECORD_ID.test(recordId))) {
    throw new FlowRemoteError('VIEW 模式必须提供合法 recordId', {
      status: 400,
      errorCode: 'FLOW_DEMO_RECORD_INVALID'
    })
  }
  if (mode !== 'VIEW' && recordIdPresent) {
    throw new FlowRemoteError('LIST/CREATE 模式必须省略 recordId', {
      status: 400,
      errorCode: 'FLOW_DEMO_RECORD_FORBIDDEN'
    })
  }
  const theme = String(value.theme || 'light').trim().toLowerCase()
  if (!['light', 'dark', 'system'].includes(theme)) {
    throw new FlowRemoteError('theme 只支持 light、dark、system', {
      status: 400,
      errorCode: 'FLOW_DEMO_THEME_INVALID'
    })
  }
  // 缺失/null 沿用平台默认值；显式输入必须精确命中公开枚举，避免代理层悄悄改写客户端意图。
  const formPresentation = value.formPresentation == null ? 'seamless' : value.formPresentation
  if (typeof formPresentation !== 'string' || !FORM_PRESENTATIONS.has(formPresentation)) {
    throw new FlowRemoteError('formPresentation 只支持 seamless、dialog', {
      status: 400,
      errorCode: 'FLOW_DEMO_FORM_PRESENTATION_INVALID'
    })
  }
  return Object.freeze({
    mode,
    ...(mode === 'VIEW' ? { recordId } : {}),
    theme,
    formPresentation
  })
}

/** 从后端固定目标白名单解析浏览器意图，绝不接受浏览器直接提交 View Key。 */
export function normalizeLaunchRequest(value, targets) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new FlowRemoteError('启动参数必须是 JSON 对象', {
      status: 400,
      errorCode: 'FLOW_DEMO_REQUEST_INVALID'
    })
  }
  const allowedKeys = new Set(['targetKey', 'mode', 'recordId', 'theme', 'formPresentation'])
  if (Object.keys(value).some(key => !allowedKeys.has(key))) {
    throw new FlowRemoteError('启动参数包含未开放字段', {
      status: 400,
      errorCode: 'FLOW_DEMO_REQUEST_INVALID'
    })
  }

  const configuredTargets = Array.isArray(targets) ? targets : []
  const requestedTargetKey = String(value.targetKey || '').trim()
  const target = requestedTargetKey
    ? configuredTargets.find(candidate => candidate.key === requestedTargetKey)
    : configuredTargets.length === 1 ? configuredTargets[0] : undefined
  if (!target) {
    throw new FlowRemoteError('目标页面未被本示例授权', {
      status: 400,
      errorCode: 'FLOW_DEMO_TARGET_NOT_ALLOWED'
    })
  }

  const { targetKey: ignoredTargetKey, ...rawIntent } = value
  void ignoredTargetKey
  return Object.freeze({
    target,
    intent: normalizeLaunchIntent(rawIntent, target.allowedEntryModes)
  })
}

function parseJsonBuffer(buffer, label) {
  try {
    return JSON.parse(buffer.toString('utf8'))
  } catch {
    throw new FlowRemoteError(`${label} 返回了非 JSON 响应`, {
      errorCode: 'FLOW_DEMO_UPSTREAM_RESPONSE_INVALID'
    })
  }
}

/** 调用 Flow 的小型 HTTP client：限制超时/响应体，不跟随重定向，也不关闭 TLS 校验。 */
function requestBuffer(url, {
  method,
  headers,
  body,
  timeoutMs = DEFAULT_REQUEST_TIMEOUT_MS,
  ca
}) {
  const transport = url.protocol === 'https:' ? https : http
  return new Promise((resolve, reject) => {
    const request = transport.request(url, {
      method,
      headers,
      ...(ca ? { ca } : {})
    }, response => {
      const chunks = []
      let total = 0
      response.on('data', chunk => {
        total += chunk.length
        if (total > MAX_FLOW_RESPONSE_BYTES) {
          response.destroy(new FlowRemoteError('Flow 响应超过本地示例限制', {
            errorCode: 'FLOW_DEMO_UPSTREAM_RESPONSE_TOO_LARGE'
          }))
          return
        }
        chunks.push(chunk)
      })
      response.on('end', () => resolve({
        status: response.statusCode || 502,
        headers: response.headers,
        body: Buffer.concat(chunks)
      }))
      response.on('error', error => reject(
        error instanceof FlowRemoteError
          ? error
          : new FlowRemoteError('读取 Flow 响应失败', {
              errorCode: 'FLOW_DEMO_UPSTREAM_RESPONSE_FAILED'
            })
      ))
    })
    request.setTimeout(timeoutMs, () => request.destroy(new FlowRemoteError(
      '连接 Flow 超时',
      { status: 504, errorCode: 'FLOW_DEMO_UPSTREAM_TIMEOUT' }
    )))
    request.on('error', error => reject(
      error instanceof FlowRemoteError
        ? error
        : new FlowRemoteError('无法连接 Flow', {
            errorCode: 'FLOW_DEMO_UPSTREAM_UNAVAILABLE'
          })
    ))
    if (body) request.write(body)
    request.end()
  })
}

function loadOptionalCa(config) {
  if (!config.flowCaFile) return undefined
  if (!existsSync(config.flowCaFile)) {
    throw new DemoConfigurationError(`Flow CA 文件不存在：${config.flowCaFile}`)
  }
  return readFileSync(config.flowCaFile)
}

async function issueMachineToken(config, secrets) {
  const body = new URLSearchParams({
    grant_type: 'client_credentials'
  }).toString()
  const response = await requestBuffer(new URL('/oauth2/token', config.flowBaseUrl), {
    method: 'POST',
    ca: loadOptionalCa(config),
    headers: {
      Accept: 'application/json',
      Authorization: `Basic ${Buffer.from(
        `${secrets.clientId}:${secrets.clientSecret}`,
        'utf8'
      ).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded',
      'Content-Length': Buffer.byteLength(body)
    },
    body
  })
  const document = parseJsonBuffer(response.body, 'OAuth token endpoint')
  if (response.status !== 200
    || !document?.access_token
    || String(document.token_type || '').toLowerCase() !== 'bearer') {
    throw new FlowRemoteError(`Flow OAuth 拒绝了请求（HTTP ${response.status}）`, {
      status: response.status >= 400 && response.status < 500 ? response.status : 502,
      errorCode: document?.error || document?.errorCode || 'FLOW_DEMO_OAUTH_REJECTED'
    })
  }
  return document.access_token
}

export function validateLaunchDocument(response, target) {
  const document = parseJsonBuffer(response.body, 'Embed Launch endpoint')
  const data = document?.data
  if (response.status !== 201 || document?.code !== 201 || !data
    || !/^lch_[A-Za-z0-9_-]{16,60}$/.test(String(data.launchId || ''))
    || !/^[A-Za-z0-9_-]{43,128}$/.test(String(data.launchCode || ''))) {
    throw new FlowRemoteError(
      document?.message || `Flow Launch 被拒绝（HTTP ${response.status}）`,
      {
        status: response.status >= 400 && response.status < 500 ? response.status : 502,
        errorCode: document?.errorCode || 'FLOW_DEMO_LAUNCH_REJECTED',
        traceId: document?.traceId
      }
    )
  }
  if (String(data.view?.key || '') !== target.viewKey
    || String(data.view?.surfaceType || '').toUpperCase() !== target.surfaceType) {
    throw new FlowRemoteError('Flow 返回的嵌入目标与宿主授权目标不一致', {
      errorCode: 'FLOW_DEMO_LAUNCH_TARGET_MISMATCH',
      traceId: document?.traceId
    })
  }
  return data
}

/**
 * 组装发给 Flow 的 Launch 文档。target 与 intent 必须已通过本文件的白名单校验，浏览器原始
 * 输入不能直接传入；展示偏好因此与 View、Origin、人员断言共享同一条可信后端边界。
 */
export function buildFlowLaunchPayload(config, target, intent, assertion, channelId) {
  const entry = intent.mode === 'VIEW'
    ? { mode: intent.mode, recordId: intent.recordId }
    : { mode: intent.mode }
  return {
    viewKey: target.viewKey,
    parentOrigin: config.hostOrigin,
    channelId,
    subject: { type: 'SIGNED_JWT', assertion },
    entry,
    context: {},
    ui: {
      locale: 'zh-CN',
      theme: intent.theme,
      formPresentation: intent.formPresentation
    }
  }
}

/** 第三方后端完整执行 OAuth + 人员断言 + 一次性 Launch，浏览器看不到机器凭据。 */
export async function createEmbedLaunch(config, secrets, rawIntent) {
  const { target, intent } = normalizeLaunchRequest(rawIntent, config.targets)
  const accessToken = await issueMachineToken(config, secrets)
  const assertion = signUserAssertion({
    privateKey: secrets.assertionPrivateKey,
    issuer: config.assertionIssuer,
    audience: config.assertionAudience,
    subject: config.assertionSubject,
    keyId: config.assertionKeyId
  })
  const channelId = randomUUID()
  const body = JSON.stringify(buildFlowLaunchPayload(
    config,
    target,
    intent,
    assertion,
    channelId
  ))
  const response = await requestBuffer(
    new URL('/api/open/v1/embed-launches', config.flowBaseUrl),
    {
      method: 'POST',
      ca: loadOptionalCa(config),
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json;charset=UTF-8',
        'Content-Length': Buffer.byteLength(body),
        'X-Trace-Id': `embed-demo-${randomUUID()}`
      },
      body
    }
  )
  const launch = validateLaunchDocument(response, target)
  let returnedOrigin
  try {
    returnedOrigin = new URL(launch.embedUrl).origin
  } catch {
    throw new FlowRemoteError('Flow 返回了无效 embedUrl', {
      errorCode: 'FLOW_DEMO_EMBED_URL_INVALID'
    })
  }
  if (returnedOrigin !== config.embedOrigin) {
    throw new FlowRemoteError(
      `Flow 返回的 Embed Origin 为 ${returnedOrigin}，预期 ${config.embedOrigin}`,
      { errorCode: 'FLOW_DEMO_EMBED_ORIGIN_MISMATCH' }
    )
  }
  return Object.freeze({
    launchId: launch.launchId,
    embedUrl: launch.embedUrl,
    launchCode: launch.launchCode,
    expiresAt: launch.expiresAt,
    view: launch.view,
    protocolVersion: launch.protocolVersion,
    channelId,
    targetKey: target.key,
    targetOrigin: config.embedOrigin
  })
}

/**
 * 为两个 localhost Origin 生成同一张短期开发证书。私钥只写入已忽略目录，
 * 不尝试导入系统信任库，避免示例擅自改变用户机器的安全状态。
 */
function generateDevelopmentCertificate(config) {
  const hasCertificate = existsSync(config.tlsCertificateFile)
  const hasPrivateKey = existsSync(config.tlsPrivateKeyFile)
  if (hasCertificate !== hasPrivateKey) {
    throw new DemoConfigurationError('TLS 证书和私钥必须同时存在或同时缺失')
  }
  if (hasCertificate) return

  mkdirSync(path.dirname(config.tlsCertificateFile), { recursive: true, mode: 0o700 })
  mkdirSync(path.dirname(config.tlsPrivateKeyFile), { recursive: true, mode: 0o700 })
  const result = spawnSync('openssl', [
    'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-sha256', '-days', '30',
    '-keyout', config.tlsPrivateKeyFile,
    '-out', config.tlsCertificateFile,
    '-subj', '/CN=localhost',
    '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'
  ], { stdio: 'pipe' })
  if (result.status !== 0) {
    throw new DemoConfigurationError('无法生成本地 HTTPS 证书，请确认 openssl 可用')
  }
  chmodSync(config.tlsPrivateKeyFile, 0o600)
  chmodSync(config.tlsCertificateFile, 0o644)
}

function loadTls(config) {
  generateDevelopmentCertificate(config)
  assertPrivatePermissions(config.tlsPrivateKeyFile, '本地 TLS 私钥')
  return {
    cert: readFileSync(config.tlsCertificateFile),
    key: readFileSync(config.tlsPrivateKeyFile)
  }
}

function ensureRuntimeFiles(config) {
  const files = [
    path.join(config.publicRoot, 'index.html'),
    path.join(config.publicRoot, 'app.js'),
    path.join(config.publicRoot, 'styles.css'),
    path.join(config.sdkRoot, 'index.js'),
    path.join(config.sdkRoot, 'FlowEmbedWidget.js'),
    path.join(config.sdkRoot, 'protocol.js')
  ]
  for (const filename of files) {
    if (!existsSync(filename)) throw new DemoConfigurationError(`示例依赖文件不存在：${filename}`)
  }
  for (const filename of ['embed-main.js', 'embed-main.css']) {
    const asset = path.join(config.embedAssetsRoot, filename)
    if (!existsSync(asset)) {
      throw new DemoConfigurationError(
        `Embed 资源不存在：${asset}；请先执行 cd workflow-web && npm run build:embed`
      )
    }
  }
}

function readRequestBody(request, maxBytes = MAX_HOST_REQUEST_BYTES) {
  return new Promise((resolve, reject) => {
    const declared = Number(request.headers['content-length'] || 0)
    if (declared > maxBytes) {
      reject(new FlowRemoteError('请求体过大', {
        status: 413,
        errorCode: 'FLOW_DEMO_PAYLOAD_TOO_LARGE'
      }))
      return
    }
    const chunks = []
    let total = 0
    request.on('data', chunk => {
      total += chunk.length
      if (total > maxBytes) {
        request.destroy(new FlowRemoteError('请求体过大', {
          status: 413,
          errorCode: 'FLOW_DEMO_PAYLOAD_TOO_LARGE'
        }))
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
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

function sendPublicError(response, error) {
  const status = Number(error?.status) >= 400 && Number(error?.status) <= 599
    ? Number(error.status)
    : 500
  sendJson(response, status, {
    code: status,
    message: error?.message || '本地嵌入示例暂不可用',
    errorCode: error?.errorCode || 'FLOW_DEMO_INTERNAL_ERROR',
    data: null,
    traceId: error?.traceId || null
  })
}

function hostSecurityHeaders(config, contentType) {
  return {
    'Content-Type': contentType,
    'Content-Security-Policy': [
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data:",
      "connect-src 'self'",
      `frame-src ${config.embedOrigin}`,
      "object-src 'none'",
      "base-uri 'none'",
      "form-action 'self'",
      "frame-ancestors 'none'"
    ].join('; '),
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
    'Referrer-Policy': 'no-referrer'
  }
}

function serveFile(response, filename, contentType, headers = {}) {
  send(response, 200, readFileSync(filename), {
    'Content-Type': contentType,
    ...headers
  })
}

function hasExpectedHost(request, expected) {
  return String(request.headers.host || '').toLowerCase() === expected.toLowerCase()
}

/**
 * 3443 只暴露第三方宿主页、真实 SDK 源码和同源 Launch BFF；POST 还要求精确 Origin，
 * 浏览器因此无法直接接触 OAuth 凭据或覆盖 Flow 资源坐标。
 */
function createHostHandler(config, secrets) {
  return async (request, response) => {
    if (!hasExpectedHost(request, config.hostHeader)) {
      send(response, 421, 'misdirected request', { 'Content-Type': 'text/plain;charset=UTF-8' })
      return
    }
    const url = new URL(request.url, config.hostOrigin)
    if (request.method === 'GET' && url.pathname === '/') {
      serveFile(
        response,
        path.join(config.publicRoot, 'index.html'),
        'text/html;charset=UTF-8',
        hostSecurityHeaders(config, 'text/html;charset=UTF-8')
      )
      return
    }
    if (request.method === 'GET' && url.pathname === '/app.js') {
      serveFile(
        response,
        path.join(config.publicRoot, 'app.js'),
        'text/javascript;charset=UTF-8',
        hostSecurityHeaders(config, 'text/javascript;charset=UTF-8')
      )
      return
    }
    if (request.method === 'GET' && url.pathname === '/styles.css') {
      serveFile(
        response,
        path.join(config.publicRoot, 'styles.css'),
        'text/css;charset=UTF-8',
        hostSecurityHeaders(config, 'text/css;charset=UTF-8')
      )
      return
    }
    if (request.method === 'GET' && url.pathname.startsWith('/vendor/flow-embed-sdk/')) {
      const filename = path.basename(url.pathname)
      if (!SDK_FILENAMES.has(filename)
        || url.pathname !== `/vendor/flow-embed-sdk/${filename}`) {
        send(response, 404, 'not found', { 'Content-Type': 'text/plain;charset=UTF-8' })
        return
      }
      serveFile(
        response,
        path.join(config.sdkRoot, filename),
        'text/javascript;charset=UTF-8',
        hostSecurityHeaders(config, 'text/javascript;charset=UTF-8')
      )
      return
    }
    if (request.method === 'GET' && url.pathname === '/partner-api/demo-config') {
      const subjectHint = config.assertionSubject.length > 4
        ? `${config.assertionSubject.slice(0, 2)}***${config.assertionSubject.slice(-2)}`
        : '***'
      sendJson(response, 200, {
        hostOrigin: config.hostOrigin,
        embedOrigin: config.embedOrigin,
        targets: config.targets,
        defaultTargetKey: config.targets[0].key,
        subjectHint
      })
      return
    }
    if (request.method === 'POST' && url.pathname === '/partner-api/embed-launch') {
      if (String(request.headers.origin || '') !== config.hostOrigin) {
        sendPublicError(response, new FlowRemoteError('请求 Origin 不受信任', {
          status: 403,
          errorCode: 'FLOW_DEMO_ORIGIN_FORBIDDEN'
        }))
        return
      }
      if (!String(request.headers['content-type'] || '').toLowerCase()
        .startsWith('application/json')) {
        sendPublicError(response, new FlowRemoteError('Content-Type 必须是 application/json', {
          status: 415,
          errorCode: 'FLOW_DEMO_CONTENT_TYPE_INVALID'
        }))
        return
      }
      try {
        const text = await readRequestBody(request)
        let body
        try {
          body = JSON.parse(text)
        } catch {
          throw new FlowRemoteError('请求体不是合法 JSON', {
            status: 400,
            errorCode: 'FLOW_DEMO_REQUEST_INVALID'
          })
        }
        const launch = await createEmbedLaunch(config, secrets, body)
        sendJson(response, 201, launch)
      } catch (error) {
        sendPublicError(response, error)
      }
      return
    }
    send(response, 404, 'not found', { 'Content-Type': 'text/plain;charset=UTF-8' })
  }
}

function filteredHeaders(source, allowed) {
  const result = {}
  for (const [name, value] of Object.entries(source || {})) {
    if (allowed.has(name.toLowerCase()) && value !== undefined) result[name] = value
  }
  return result
}

/**
 * 8443 到 8080 的受限反向代理。请求/响应头均使用白名单，明确丢弃 Cookie、
 * Proxy-Authorization、Set-Cookie 和其他跨边界状态。
 */
function proxyEmbedRequest(config, request, response) {
  const target = new URL(request.url, config.flowBaseUrl)
  const transport = target.protocol === 'https:' ? https : http
  const headers = filteredHeaders(request.headers, PASSTHROUGH_REQUEST_HEADERS)
  headers.host = target.host
  // 浏览器同源 GET 可以不带 Origin，且代理不能转发宿主
  // Origin。因此由隔离 Embed 反代固定注入自身 Origin，Flow 服务端
  // 再与 workflow.embed.publicBaseUrl 做字节级比较。
  headers.origin = config.embedOrigin
  const upstream = transport.request(target, {
    method: request.method,
    headers,
    ...(config.flowCaFile ? { ca: loadOptionalCa(config) } : {})
  }, upstreamResponse => {
    const responseHeaders = filteredHeaders(
      upstreamResponse.headers,
      PASSTHROUGH_RESPONSE_HEADERS
    )
    responseHeaders['cache-control'] ||= 'no-store'
    response.writeHead(upstreamResponse.statusCode || 502, responseHeaders)
    upstreamResponse.pipe(response)
  })
  upstream.setTimeout(DEFAULT_REQUEST_TIMEOUT_MS, () => upstream.destroy(new Error('timeout')))
  upstream.on('error', () => {
    if (response.headersSent) {
      response.destroy()
      return
    }
    sendJson(response, 502, {
      code: 502,
      message: 'Embed 代理无法连接 Flow',
      errorCode: 'FLOW_DEMO_PROXY_UNAVAILABLE',
      data: null,
      traceId: null
    })
  })
  request.on('aborted', () => upstream.destroy())
  request.pipe(upstream)
}

/**
 * 判断独立 Embed Origin 是否可以把请求转发给 Flow。
 *
 * Embed 控制面使用固定前缀；原生表单的数据面则与 Flow 主站共用 `/api/**`，但只有
 * 已携带 opaque Embed Bearer 和协议头的委托请求才能进入代理。真正的端点授权仍由
 * Flow 服务端的声明式 delegated policy 默认拒绝完成，示例代理不再复制一份会随新
 * 组件漂移的逐端点清单，同时也不会转发 Cookie 或普通 Flow 登录会话。
 */
export function isApprovedEmbedProxyRequest(request, pathname) {
  if (EMBED_CONTROL_PROXY_PREFIXES.some(prefix => pathname.startsWith(prefix))) {
    return true
  }
  // Flow 本地主站会把已上传文件暴露在 /uploads/**。原生表单中的图片、
  // 附件预览和下载使用相对 URL，隔离的 8443 Origin 必须保持同一路径；
  // 这里只透传只读静态资源，不开放任意非 API 路由。
  if (pathname.startsWith('/uploads/')) {
    return ['GET', 'HEAD'].includes(String(request.method || 'GET').toUpperCase())
  }
  if (!pathname.startsWith('/api/') || pathname.startsWith('/api/embed/')) {
    return false
  }
  if (EMBED_DELEGATED_DENIED_PREFIXES.some(prefix => pathname.startsWith(prefix))) {
    return false
  }
  const protocol = String(request.headers['x-flow-embed-protocol'] || '')
  const authorization = String(request.headers.authorization || '')
  return protocol === '1'
    && /^Bearer [A-Za-z0-9_-]{43,128}$/.test(authorization)
}

/** 独立 Embed Origin 只开放固定资源、控制面及已认证的原生 Flow 委托请求。 */
function createEmbedHandler(config) {
  return (request, response) => {
    if (!hasExpectedHost(request, config.embedHeader)) {
      send(response, 421, 'misdirected request', { 'Content-Type': 'text/plain;charset=UTF-8' })
      return
    }
    const url = new URL(request.url, config.embedOrigin)
    if (request.method === 'GET' && url.pathname.startsWith('/embed-assets/')) {
      const filename = path.basename(url.pathname)
      if (!['embed-main.js', 'embed-main.css'].includes(filename)
        || url.pathname !== `/embed-assets/${filename}`) {
        send(response, 404, 'not found', { 'Content-Type': 'text/plain;charset=UTF-8' })
        return
      }
      serveFile(
        response,
        path.join(config.embedAssetsRoot, filename),
        filename.endsWith('.js')
          ? 'text/javascript;charset=UTF-8'
          : 'text/css;charset=UTF-8'
      )
      return
    }
    if (isApprovedEmbedProxyRequest(request, url.pathname)) {
      proxyEmbedRequest(config, request, response)
      return
    }
    send(response, 404, 'Embed origin only exposes approved runtime routes', {
      'Content-Type': 'text/plain;charset=UTF-8'
    })
  }
}

function listen(server, port, address) {
  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, address, () => {
      server.off('error', reject)
      resolve()
    })
  })
}

export async function startDemo(config = buildConfig(), secrets = loadSecrets(config)) {
  ensureRuntimeFiles(config)
  const tls = loadTls(config)
  const hostServer = https.createServer(tls, createHostHandler(config, secrets))
  const embedServer = https.createServer(tls, createEmbedHandler(config))
  try {
    await listen(hostServer, config.hostPort, config.bindAddress)
    await listen(embedServer, config.embedPort, config.bindAddress)
  } catch (error) {
    hostServer.close()
    embedServer.close()
    throw error
  }
  return Object.freeze({ hostServer, embedServer })
}

/** 只准备本地 TLS 与前端构建产物，不读取 OAuth 或人员断言秘密。 */
export function setupDemo(config = buildConfig()) {
  ensureRuntimeFiles(config)
  loadTls(config)
  return Object.freeze({
    certificateFile: config.tlsCertificateFile,
    hostOrigin: config.hostOrigin,
    embedOrigin: config.embedOrigin
  })
}

export function checkDemo(config = buildConfig(), secrets = loadSecrets(config)) {
  ensureRuntimeFiles(config)
  loadTls(config)
  signUserAssertion({
    privateKey: secrets.assertionPrivateKey,
    issuer: config.assertionIssuer,
    audience: config.assertionAudience,
    subject: config.assertionSubject,
    keyId: config.assertionKeyId
  })
  return Object.freeze({
    hostOrigin: config.hostOrigin,
    embedOrigin: config.embedOrigin,
    targets: config.targets
  })
}

async function main() {
  const config = buildConfig()
  if (process.argv.includes('--setup')) {
    const prepared = setupDemo(config)
    console.log(
      `本地 HTTPS 已准备：host=${prepared.hostOrigin}，embed=${prepared.embedOrigin}，`
        + '证书仅保存在示例 .runtime 目录，未写入系统 Keychain。'
    )
    return
  }
  const secrets = loadSecrets(config)
  if (process.argv.includes('--check')) {
    const checked = checkDemo(config, secrets)
    console.log(
      `配置检查通过：host=${checked.hostOrigin}，embed=${checked.embedOrigin}，`
        + `targets=${checked.targets.map(target => `${target.key}:${target.viewKey}`
          + `[${target.allowedEntryModes.join('/')}]`).join(',')}`
    )
    return
  }
  const { hostServer, embedServer } = await startDemo(config, secrets)
  console.log(`第三方宿主页：${config.hostOrigin}`)
  console.log(`独立 Embed Origin：${config.embedOrigin}`)
  console.log('机器凭据、Access Token、人员断言和一次性 Launch Code 均不会写入日志。')

  let closing = false
  const close = () => {
    if (closing) return
    closing = true
    hostServer.close()
    embedServer.close()
  }
  process.once('SIGINT', close)
  process.once('SIGTERM', close)
}

const isMain = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url
if (isMain) {
  main().catch(error => {
    console.error(`启动失败：${error.message}`)
    process.exitCode = 1
  })
}
