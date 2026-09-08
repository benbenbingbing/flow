import {
  FLOW_EMBED_PROTOCOL_VERSION,
  createEmbedRuntimeApi
} from '../api/embedRuntimeApi.js'
import {
  EMBED_BRIDGE_MESSAGE_TYPES,
  createEmbedBridge
} from '../bridge/embedBridge.js'
import { isEmbedSessionFailure } from '../api/embedRequest.js'
import { createEmbedSession } from '../session/embedSession.js'
import { generateSecureHandshakeNonce } from '../security/secureNonce.js'
import { resolveEmbedLocale } from './embedAppearance.js'
import {
  buildEmbedListQuery,
  normalizeEmbedBootstrap,
  normalizeEmbedExternalSchema,
  normalizeEmbedNativeRuntimeTarget,
  projectEmbedPage,
  projectEmbedSelection
} from '../projection/normalizeEmbedSchema.js'

export const EMBED_RUNTIME_STATES = Object.freeze({
  LOADING_ENTRY: 'LOADING_ENTRY',
  WAITING_HANDSHAKE: 'WAITING_HANDSHAKE',
  EXCHANGING: 'EXCHANGING',
  BOOTSTRAPPING: 'BOOTSTRAPPING',
  READY: 'READY',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
  FATAL_ERROR: 'FATAL_ERROR',
  DESTROYING: 'DESTROYING',
  DESTROYED: 'DESTROYED'
})

export const EMBED_RUNTIME_SURFACES = Object.freeze({
  LIST: 'LIST',
  FORM: 'FORM'
})

const TERMINAL_STATES = new Set([
  EMBED_RUNTIME_STATES.SESSION_EXPIRED,
  EMBED_RUNTIME_STATES.FATAL_ERROR,
  EMBED_RUNTIME_STATES.DESTROYING,
  EMBED_RUNTIME_STATES.DESTROYED
])

const IDENTITY_ERROR_CODES = new Set([
  'EXTERNAL_IDENTITY_NOT_MAPPED',
  'FLOW_USER_DISABLED',
  'EMBED_IDENTITY_NOT_BOUND',
  'EMBED_IDENTITY_AMBIGUOUS',
  'EMBED_FLOW_USER_DISABLED',
  'EMBED_BINDING_DISABLED'
])
const ORIGIN_ERROR_CODES = new Set([
  'EMBED_ORIGIN_NOT_ALLOWED',
  'EMBED_ORIGIN_FORBIDDEN',
  'EMBED_ORIGIN_MISMATCH',
  'EMBED_BRIDGE_INIT_SOURCE_INVALID',
  'EMBED_ENTRY_CONFIG_ORIGIN_INVALID'
])
const RESOURCE_ERROR_CODES = new Set([
  'EMBED_VIEW_NOT_GRANTED',
  'EMBED_VIEW_DISABLED',
  'EMBED_RELEASE_REVOKED',
  'EMBED_GRANT_REVOKED',
  'EMBED_RESOURCE_DISABLED',
  'EMBED_VIEW_NOT_FOUND'
])
const LAUNCH_ERROR_CODES = new Set([
  'EMBED_LAUNCH_EXPIRED',
  'EMBED_LAUNCH_CONSUMED',
  'EMBED_LAUNCH_INVALID',
  'EMBED_LAUNCH_CODE_INVALID'
])

const LIST_LOCAL_FORM_ACTIONS = Object.freeze({
  CREATE: Object.freeze({
    key: 'create',
    capability: 'RECORD_CREATE',
    placement: 'TOOLBAR',
    recordMode: 'NONE'
  }),
  VIEW: Object.freeze({
    key: 'view',
    capability: 'RECORD_VIEW',
    placement: 'ROW',
    recordMode: 'CURRENT'
  })
})
const FORM_CAPABILITIES = Object.freeze({
  CREATE: 'RECORD_CREATE',
  VIEW: 'RECORD_VIEW'
})

function navigationState(surfaceType = null, mode = null, recordId = null, canBack = false) {
  return Object.freeze({ surfaceType, mode, recordId, canBack: canBack === true })
}

function clampInteger(value, fallback, min, max) {
  const number = Number(value)
  return Number.isFinite(number)
    ? Math.min(max, Math.max(min, Math.floor(number)))
    : fallback
}

function normalizeError(error, { phase = '', recoverable = false } = {}) {
  const errorCode = String(error?.errorCode || 'EMBED_RUNTIME_FAILED')
  let category = 'UNKNOWN'
  let message = '页面暂时无法加载，请稍后重试'
  let relaunchRequired = false

  if (LAUNCH_ERROR_CODES.has(errorCode) || isEmbedSessionFailure(error)) {
    category = 'SESSION'
    message = '启动凭证已失效，请从宿主系统重新打开'
    relaunchRequired = true
    recoverable = false
  } else if (errorCode === 'EMBED_SESSION_LIMIT_EXCEEDED') {
    category = 'SESSION'
    message = '并发会话已达上限，请关闭其他窗口后重新打开'
    relaunchRequired = true
    recoverable = false
  } else if (IDENTITY_ERROR_CODES.has(errorCode)) {
    category = 'IDENTITY'
    message = '当前账号未绑定或已禁用，请联系管理员'
    recoverable = false
  } else if (ORIGIN_ERROR_CODES.has(errorCode)) {
    category = 'ORIGIN'
    message = '当前页面来源未授权，已拒绝嵌入'
    recoverable = false
  } else if (RESOURCE_ERROR_CODES.has(errorCode)) {
    category = 'RESOURCE'
    message = '该嵌入资源已停用，请联系 Flow 管理员'
    recoverable = false
  } else if (errorCode === 'EMBED_NETWORK_ERROR'
    || errorCode === 'EMBED_REQUEST_TIMEOUT'
    || error?.status >= 500) {
    category = 'TRANSIENT'
    recoverable = true
  } else if (error?.status >= 400 && error?.status < 500) {
    recoverable = false
  } else if (typeof error?.message === 'string' && error.message.trim()) {
    message = error.message.trim().slice(0, 512)
  }

  return Object.freeze({
    errorCode,
    message,
    traceId: error?.traceId ? String(error.traceId).slice(0, 256) : null,
    category,
    phase,
    recoverable: recoverable === true,
    relaunchRequired
  })
}

function validateEntryConfig(config) {
  if (!config || typeof config !== 'object'
    || !config.launchId || !config.channelId || !config.expectedParentOrigin
    || config.protocolVersion !== FLOW_EMBED_PROTOCOL_VERSION) {
    throw new TypeError('Embed entryConfig 无效')
  }
  return config
}

/**
 * 协调握手、兑换、Bootstrap、Schema、首屏查询和会话心跳。
 * 控制器不依赖 Vue/router/store，可用假 API 和假 Bridge 做确定性单元测试。
 */
export function createEmbedRuntimeController({
  entryConfig,
  api = createEmbedRuntimeApi(),
  session = createEmbedSession(),
  bridgeFactory = createEmbedBridge,
  sdkVersion = '1.0.0',
  documentRef = globalThis.document,
  setTimeoutImpl = globalThis.setTimeout,
  clearTimeoutImpl = globalThis.clearTimeout,
  now = () => new Date(),
  idempotencyKeyFactory = () => `emb_${generateSecureHandshakeNonce()}`,
  clientMutationIdFactory = () => `cm_${generateSecureHandshakeNonce()}`,
  onFocus,
  onDelegatedSessionReady,
  onDelegatedSessionReset,
  onRuntimeIdentityReady,
  onRefreshNativeList,
  onRefreshNativeForm
} = {}) {
  const entry = validateEntryConfig(entryConfig)
  if (!api || typeof api.exchange !== 'function' || typeof api.getBootstrap !== 'function'
    || typeof api.getSchema !== 'function' || typeof api.queryList !== 'function'
    || typeof api.heartbeat !== 'function' || typeof api.logout !== 'function') {
    throw new TypeError('Embed runtime API 无效')
  }
  if (!session || typeof session.setExchange !== 'function'
    || typeof session.getAccessToken !== 'function') {
    throw new TypeError('Embed session 无效')
  }
  if (typeof bridgeFactory !== 'function') throw new TypeError('Embed bridgeFactory 无效')

  let current = {
    state: EMBED_RUNTIME_STATES.LOADING_ENTRY,
    phase: 'entry',
    bootstrap: null,
    schema: null,
    page: null,
    form: null,
    nativeListTarget: null,
    nativeFormTarget: null,
    navigation: navigationState(),
    selectedRecordIds: Object.freeze([]),
    queryValues: Object.freeze({}),
    listLoading: false,
    listError: null,
    formLoading: false,
    formError: null,
    formSubmitting: false,
    formSubmitError: null,
    formSubmitResult: null,
    error: null,
    theme: 'light',
    locale: 'zh-CN'
  }
  const listeners = new Set()
  let bridge = null
  let heartbeatTimer
  let heartbeatSeconds = 60
  let querySequence = 0
  let lastListRequest = null
  let retryAction = null
  let startCalled = false
  let createAttempt = null
  let exchangePromise = null
  let exchangeAttempted = false
  let exchangeFailure = null
  let sessionIssued = false
  let sessionReleaseConfirmed = false
  let destroyPromise = null

  function snapshot() {
    return Object.freeze({ ...current })
  }

  function update(patch) {
    current = { ...current, ...patch }
    const value = snapshot()
    for (const listener of listeners) {
      try {
        listener(value)
      } catch {
        // 视图订阅异常不能中断安全状态机。
      }
    }
    return value
  }

  function subscribe(listener) {
    if (typeof listener !== 'function') throw new TypeError('Embed listener 必须是函数')
    listeners.add(listener)
    listener(snapshot())
    return () => listeners.delete(listener)
  }

  function send(type, payload, options) {
    if (bridge?.getState?.() !== 'connected') return false
    try {
      bridge.send(type, payload, options)
      return true
    } catch {
      return false
    }
  }

  function clearHeartbeat() {
    if (heartbeatTimer !== undefined && typeof clearTimeoutImpl === 'function') {
      clearTimeoutImpl(heartbeatTimer)
    }
    heartbeatTimer = undefined
  }

  function scheduleHeartbeat(seconds = heartbeatSeconds) {
    clearHeartbeat()
    if (TERMINAL_STATES.has(current.state) || typeof setTimeoutImpl !== 'function') return
    heartbeatSeconds = clampInteger(seconds, heartbeatSeconds, 15, 300)
    heartbeatTimer = setTimeoutImpl(runHeartbeat, heartbeatSeconds * 1000)
  }

  async function runHeartbeat() {
    heartbeatTimer = undefined
    if (TERMINAL_STATES.has(current.state)) return
    if (documentRef?.visibilityState === 'hidden') {
      scheduleHeartbeat(Math.max(heartbeatSeconds, 60))
      return
    }
    try {
      const heartbeat = await api.heartbeat({
        visible: true,
        clientTime: now().toISOString()
      })
      if (TERMINAL_STATES.has(current.state)) return
      session.applyHeartbeat(heartbeat)
      scheduleHeartbeat(heartbeat?.nextHeartbeatAfterSeconds)
    } catch (error) {
      if (isEmbedSessionFailure(error) || session.isExpired?.()) {
        expire(error)
        return
      }
      // 短暂网络错误不清空有效会话；下一个受控周期重试。
      scheduleHeartbeat(Math.min(heartbeatSeconds * 2, 300))
    }
  }

  function ensureMatchingProjection(bootstrap, schema) {
    if (bootstrap.view.key !== schema.view.key
      || bootstrap.view.surfaceType !== schema.view.surfaceType) {
      const error = new Error('Bootstrap 与 External Schema 不匹配')
      error.errorCode = 'EMBED_SCHEMA_VIEW_MISMATCH'
      throw error
    }
    if (bootstrap.view.surfaceType !== 'LIST'
      || bootstrap.view.entryMode !== 'LIST'
      || !bootstrap.capabilities.includes('LIST_QUERY')) {
      const error = new Error('当前 View 未开放列表查询')
      error.errorCode = 'EMBED_CAPABILITY_FORBIDDEN'
      throw error
    }
  }

  function ensureNativeListTarget(bootstrap) {
    if (bootstrap.view.surfaceType !== EMBED_RUNTIME_SURFACES.LIST
      || bootstrap.view.entryMode !== 'LIST'
      || !bootstrap.capabilities.includes('LIST_QUERY')
      || !bootstrap.target
      || bootstrap.target.mode !== 'LIST') {
      const error = new Error('当前 View 未开放 Flow 原生列表')
      error.errorCode = 'EMBED_CAPABILITY_FORBIDDEN'
      throw error
    }
  }

  function ensureFormProjection(bootstrap) {
    const mode = bootstrap.view.entryMode
    const capability = FORM_CAPABILITIES[mode]
    if (bootstrap.view.surfaceType !== EMBED_RUNTIME_SURFACES.FORM || !capability
      || !bootstrap.capabilities.includes(capability)
      || !bootstrap.target
      || bootstrap.target.mode !== mode) {
      const error = new Error('当前 View 未开放表单读取')
      error.errorCode = 'EMBED_CAPABILITY_FORBIDDEN'
      throw error
    }
    if (mode === 'VIEW' && !bootstrap.target.recordId) {
      const error = new Error('当前 View 缺少记录坐标')
      error.errorCode = 'EMBED_BOOTSTRAP_TARGET_INVALID'
      throw error
    }
  }

  async function executeListQuery({ queryValues, pageNum, pageSize, initial = false }) {
    const sequence = ++querySequence
    lastListRequest = {
      queryValues: Object.freeze({ ...(queryValues || {}) }),
      pageNum,
      pageSize
    }
    if (!initial) update({ listLoading: true, listError: null })
    try {
      const query = buildEmbedListQuery(queryValues, current.schema, { pageNum, pageSize })
      const rawPage = await api.queryList(query)
      if (sequence !== querySequence || TERMINAL_STATES.has(current.state)) return null
      const page = projectEmbedPage(rawPage, current.schema)
      update({
        page,
        queryValues: Object.freeze({ ...(queryValues || {}) }),
        listLoading: false,
        listError: null
      })
      return page
    } catch (error) {
      if (sequence !== querySequence || TERMINAL_STATES.has(current.state)) return null
      if (isEmbedSessionFailure(error) || session.isExpired?.()) {
        expire(error)
        return null
      }
      const normalized = normalizeError(error, { phase: 'query', recoverable: true })
      if (initial) throw Object.assign(error, { normalizedEmbedError: normalized })
      if (!normalized.recoverable) {
        fail(error, 'query')
        return null
      }
      update({ listLoading: false, listError: normalized })
      return null
    }
  }

  async function loadRuntime() {
    retryAction = loadRuntime
    try {
      update({
        state: EMBED_RUNTIME_STATES.BOOTSTRAPPING,
        phase: 'bootstrap',
        error: null
      })
      const rawBootstrap = await api.getBootstrap()
      if (TERMINAL_STATES.has(current.state)) return
      const bootstrap = normalizeEmbedBootstrap(rawBootstrap)
      const locale = resolveEmbedLocale(bootstrap.ui.locale)
      // 原生组件可能读取 userStore（例如超级管理员审计页签）；必须在公开 READY
      // 状态、挂载 Dialog 之前注入服务端映射身份，且仅写隔离 Pinia 内存。
      onRuntimeIdentityReady?.(bootstrap.actor)
      session.applyHeartbeat({
        status: 'ACTIVE',
        idleExpiresAt: bootstrap.session.idleExpiresAt,
        absoluteExpiresAt: bootstrap.session.expiresAt
      })
      update({
        bootstrap,
        theme: bootstrap.ui.theme,
        locale,
        phase: 'schema'
      })

      if (bootstrap.view.surfaceType === 'LIST') {
        ensureNativeListTarget(bootstrap)
        // LIST 与 FORM 一样只交付 Session 固定的原生坐标。列、查询控件、
        // 渲染器、数据源、按钮和弹窗全部由 EntityDataList 按标准 URL 读取。
        update({
          schema: null,
          page: null,
          nativeListTarget: bootstrap.target,
          navigation: navigationState(EMBED_RUNTIME_SURFACES.LIST, 'LIST'),
          phase: 'native-list',
          listLoading: false,
          listError: null
        })
      } else {
        ensureFormProjection(bootstrap)
        // 直接 FORM 入口只建立原生 Flow 坐标，不再读取/重建 Embed 投影表单。
        // NativeEmbeddedFormPage 会按 bootstrap.target 调用标准 runtime-release URL
        // 并挂载与管理端相同的 Dialog/registry。
        update({
          navigation: navigationState(
            EMBED_RUNTIME_SURFACES.FORM,
            bootstrap.view.entryMode,
            bootstrap.target.recordId || null
          ),
          form: null,
          nativeListTarget: null,
          nativeFormTarget: bootstrap.target,
          phase: 'native-form',
          formLoading: false,
          formError: null
        })
      }
      if (TERMINAL_STATES.has(current.state)) return

      retryAction = null
      update({
        state: EMBED_RUNTIME_STATES.READY,
        phase: 'ready',
        listLoading: false,
        formLoading: false,
        error: null
      })
      scheduleHeartbeat(heartbeatSeconds)
      send(EMBED_BRIDGE_MESSAGE_TYPES.INITIALIZED, {
        viewKey: bootstrap.view.key,
        surfaceType: bootstrap.view.surfaceType,
        capabilities: bootstrap.capabilities
      })
    } catch (error) {
      if (TERMINAL_STATES.has(current.state)) return
      if (isEmbedSessionFailure(error) || session.isExpired?.()) {
        expire(error)
        return
      }
      const normalized = error?.normalizedEmbedError
        || normalizeError(error, { phase: current.phase })
      if (normalized.recoverable) {
        update({ state: EMBED_RUNTIME_STATES.FATAL_ERROR, error: normalized })
        send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, normalized)
      } else {
        fail(error, current.phase)
      }
    }
  }

  async function handleInit(init) {
    if (current.state !== EMBED_RUNTIME_STATES.WAITING_HANDSHAKE) return
    update({ state: EMBED_RUNTIME_STATES.EXCHANGING, phase: 'exchange', error: null })
    exchangeAttempted = true
    const pendingExchange = (async () => {
      try {
        const exchange = await api.exchange(entry.launchId, {
          launchCode: init.launchCode,
          channelId: entry.channelId,
          parentOrigin: entry.expectedParentOrigin,
          parentNonce: init.parentNonce,
          childNonce: init.childNonce,
          sdkVersion
        })
        if (exchange?.protocolVersion !== FLOW_EMBED_PROTOCOL_VERSION
          || String(exchange?.tokenType || '').toLowerCase() !== 'bearer') {
          const error = new Error('Embed Exchange 协议不兼容')
          error.errorCode = 'EMBED_EXCHANGE_PROTOCOL_INVALID'
          throw error
        }
        session.setExchange(exchange, { expectedLaunchId: entry.launchId })
        sessionIssued = true
        onDelegatedSessionReady?.(session)
        heartbeatSeconds = clampInteger(exchange.heartbeatAfterSeconds, 60, 15, 300)
        return exchange
      } catch (error) {
        // 即使 destroy 在 Exchange 结束后才到达，也要保留“请求已发出但
        // 未拿到 Token”的二义性，禁止后续误发完成 ACK。
        exchangeFailure = error
        throw error
      }
    })()
    exchangePromise = pendingExchange
    try {
      await pendingExchange
      // destroy 在 Exchange 在途时会等待这个 Promise 拿到 Token 后注销；
      // Exchange 续体不得再进入 Bootstrap，否则会“销毁后复活”。
      if (TERMINAL_STATES.has(current.state)) return
      await loadRuntime()
    } catch (error) {
      if (TERMINAL_STATES.has(current.state)) return
      // Exchange 结果丢失后不能安全重试一次性 Launch，必须由宿主重新签发。
      session.clear('exchange_failed')
      onDelegatedSessionReset?.()
      const normalized = normalizeError(error, { phase: 'exchange' })
      if (normalized.category === 'TRANSIENT') {
        expire(Object.assign(error, { errorCode: 'EMBED_LAUNCH_EXPIRED' }))
      } else if (normalized.category === 'SESSION') {
        expire(error)
      } else {
        fail(error, 'exchange')
      }
    } finally {
      if (exchangePromise === pendingExchange) exchangePromise = null
    }
  }

  function handleBridgeMessage(message) {
    if (message.type === EMBED_BRIDGE_MESSAGE_TYPES.REFRESH) {
      refreshCurrent().then(() => {
        const refreshError = current.error || (
          current.navigation.surfaceType === EMBED_RUNTIME_SURFACES.FORM
            ? current.formError : current.listError
        )
        if (refreshError) {
          send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, refreshError, {
            requestId: message.requestId
          })
          return
        }
        if (current.state !== EMBED_RUNTIME_STATES.READY) return
        send(EMBED_BRIDGE_MESSAGE_TYPES.ACK, { command: message.type }, {
          requestId: message.requestId
        })
      }).catch(error => fail(error, 'query'))
      return
    }
    if (message.type === EMBED_BRIDGE_MESSAGE_TYPES.SET_THEME) {
      update({ theme: String(message.payload.theme).toLowerCase() })
      send(EMBED_BRIDGE_MESSAGE_TYPES.ACK, { command: message.type }, {
        requestId: message.requestId
      })
      return
    }
    if (message.type === EMBED_BRIDGE_MESSAGE_TYPES.SET_LOCALE) {
      try {
        // 原生业务界面目前只有中文资源；拒绝未支持的语言，避免假成功。
        update({ locale: resolveEmbedLocale(message.payload.locale) })
        send(EMBED_BRIDGE_MESSAGE_TYPES.ACK, { command: message.type }, {
          requestId: message.requestId
        })
      } catch (error) {
        send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, normalizeError(error, {
          phase: 'locale', recoverable: false
        }), { requestId: message.requestId })
      }
      return
    }
    if (message.type === EMBED_BRIDGE_MESSAGE_TYPES.FOCUS) {
      try {
        onFocus?.()
        send(EMBED_BRIDGE_MESSAGE_TYPES.ACK, { command: message.type }, {
          requestId: message.requestId
        })
      } catch (error) {
        send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, normalizeError(error, {
          phase: 'focus',
          recoverable: true
        }), { requestId: message.requestId })
      }
      return
    }
    if (message.type === EMBED_BRIDGE_MESSAGE_TYPES.DESTROY) {
      // destroy ACK 是服务端 slot 已释放的完成语义，不是“已收到命令”。
      // 保留 Bridge 到 ACK 投递完成，由宿主 SDK 在收到关联 ACK 后拆 iframe。
      destroy({
        requireLogoutConfirmation: true,
        preserveBridge: true
      }).then(() => {
        send(EMBED_BRIDGE_MESSAGE_TYPES.ACK, { command: message.type }, {
          requestId: message.requestId
        })
      }).catch(error => {
        send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, normalizeError(error, {
          phase: 'logout', recoverable: true
        }), { requestId: message.requestId })
      })
    }
  }

  function start() {
    if (startCalled) throw new Error('Embed runtime 已启动')
    startCalled = true
    update({ state: EMBED_RUNTIME_STATES.WAITING_HANDSHAKE, phase: 'handshake' })
    try {
      bridge = bridgeFactory({
        expectedParentOrigin: entry.expectedParentOrigin,
        launchId: entry.launchId,
        channelId: entry.channelId,
        protocol: entry.protocolVersion,
        onInit: handleInit,
        onMessage: handleBridgeMessage,
        onError(error) {
          if (!TERMINAL_STATES.has(current.state)) fail(error, 'handshake')
        }
      })
      return bridge.start()
    } catch (error) {
      fail(error, 'handshake')
      return ''
    }
  }

  function operationNotAllowed(message = 'Embed 列表导航动作不可用') {
    const error = new Error(message)
    error.errorCode = 'EMBED_OPERATION_NOT_ALLOWED'
    return error
  }

  /**
   * 只接受 V1 固定的 create/view LOCAL_FORM 描述符。组件不能传目标表单或 Release，
   * 即使状态被调用方污染，也必须在 Controller 边界重新与发布投影求交。
   */
  function requireListNavigationAction(mode) {
    const expected = LIST_LOCAL_FORM_ACTIONS[mode]
    if (!expected
      || current.state !== EMBED_RUNTIME_STATES.READY
      || current.listLoading
      || current.bootstrap?.view?.surfaceType !== EMBED_RUNTIME_SURFACES.LIST
      || current.bootstrap?.view?.entryMode !== 'LIST'
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.LIST
      || !current.bootstrap.capabilities.includes(expected.capability)) {
      throw operationNotAllowed()
    }
    const action = current.schema?.actions?.find(candidate => candidate.key === expected.key)
    if (!action
      || action.placement !== expected.placement
      || action.kind !== 'NAVIGATION'
      || action.transport !== 'LOCAL_FORM'
      || action.recordMode !== expected.recordMode
      || action.selectionMode !== 'NONE'
      || action.enabled !== true
      || action.requiresRecordVersion === true
      || action.idempotencyRequired === true) {
      throw operationNotAllowed(action?.disabledReason || undefined)
    }
    if (typeof api.getNativeFormTarget !== 'function') {
      throw new TypeError('Embed 原生表单坐标 API 无效')
    }
    return action
  }

  async function openListForm(mode, recordId = null) {
    createAttempt = null
    const sequence = ++querySequence
    update({
      navigation: navigationState(
        EMBED_RUNTIME_SURFACES.FORM,
        mode,
        recordId,
        true
      ),
      form: null,
      nativeFormTarget: null,
      formLoading: true,
      formError: null,
      formSubmitting: false,
      formSubmitError: null,
      formSubmitResult: null,
      phase: 'native-form-target'
    })
    try {
      // 浏览器只提交模式和已投影行 ID；实体、表单、Release 与解析令牌均由
      // 服务端从当前 Session 固定的目标资源快照恢复。
      const raw = await api.getNativeFormTarget({ mode, recordId })
      if (sequence !== querySequence || TERMINAL_STATES.has(current.state)) return null
      const target = normalizeEmbedNativeRuntimeTarget(
        raw?.target || raw,
        { surfaceType: 'FORM', entryMode: mode }
      )
      if (target.entityCode !== current.schema?.entity?.code
        || (mode === 'VIEW' && target.recordId !== String(recordId || ''))
        || (mode === 'CREATE' && target.recordId)) {
        const error = new Error('原生表单目标与列表导航不匹配')
        error.errorCode = 'EMBED_BOOTSTRAP_TARGET_INVALID'
        throw error
      }
      update({
        nativeFormTarget: target,
        formLoading: false,
        formError: null,
        phase: 'native-form'
      })
      return target
    } catch (error) {
      if (sequence !== querySequence || TERMINAL_STATES.has(current.state)) return null
      if (isEmbedSessionFailure(error) || session.isExpired?.()) {
        expire(error)
        return null
      }
      const normalized = normalizeError(error, {
        phase: 'native-form-target',
        recoverable: true
      })
      update({ formLoading: false, formError: normalized, phase: 'ready' })
      return null
    }
  }

  /** 打开发布列表工具栏投影出的 CREATE 表单，不接受任何目标坐标。 */
  function openListCreate() {
    requireListNavigationAction('CREATE')
    return openListForm('CREATE')
  }

  /**
   * 打开当前页已投影且逐行动作仍为可见可用的记录。外部传入的任意 ID 只有命中
   * current.page.items 后才会成为请求坐标。
   */
  function openListView(recordId) {
    const action = requireListNavigationAction('VIEW')
    const id = String(recordId || '')
    const record = current.page?.items?.find(candidate => candidate.id === id)
    const capability = record?.actions?.[action.key]
    if (!record || capability?.visible !== true || capability?.enabled !== true) {
      throw operationNotAllowed(capability?.reason || undefined)
    }
    return openListForm('VIEW', record.id)
  }

  /** 返回同一 Session 的列表子状态；列表页、查询值和选择快照均不重新请求或重建。 */
  function backToList() {
    if (current.state !== EMBED_RUNTIME_STATES.READY
      || current.bootstrap?.view?.surfaceType !== EMBED_RUNTIME_SURFACES.LIST
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.FORM
      || current.navigation.canBack !== true
      || current.formSubmitting) return false
    querySequence += 1
    createAttempt = null
    update({
      navigation: navigationState(EMBED_RUNTIME_SURFACES.LIST, 'LIST'),
      form: null,
      nativeFormTarget: null,
      formLoading: false,
      formError: null,
      formSubmitting: false,
      formSubmitError: null,
      formSubmitResult: null,
      phase: 'ready'
    })
    return true
  }

  async function refreshList(options = null) {
    if (current.state !== EMBED_RUNTIME_STATES.READY
      || current.bootstrap?.view?.surfaceType !== EMBED_RUNTIME_SURFACES.LIST
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.LIST) return null
    if (current.nativeListTarget) {
      try {
        await onRefreshNativeList?.()
        update({ listError: null, phase: 'ready' })
        return true
      } catch (error) {
        if (isEmbedSessionFailure(error) || session.isExpired?.()) {
          expire(error)
          return null
        }
        update({
          listError: normalizeError(error, { phase: 'native-list', recoverable: true }),
          phase: 'ready'
        })
        return null
      }
    }
    const source = options || lastListRequest || {}
    const queryValues = source.queryValues ?? current.queryValues
    const pageNum = source.pageNum ?? current.page?.pageNum ?? 1
    const pageSize = source.pageSize
      ?? current.page?.pageSize
      ?? current.bootstrap?.ui?.pageSize
      ?? 20
    return executeListQuery({ queryValues, pageNum, pageSize })
  }

  async function refreshForm() {
    if (current.state !== EMBED_RUNTIME_STATES.READY
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.FORM) return null
    if (current.nativeFormTarget) {
      try {
        update({ formLoading: true, formError: null })
        const result = await onRefreshNativeForm?.()
        if (TERMINAL_STATES.has(current.state)) return null
        // 用户取消丢弃输入也属于“没有刷新”，不能向宿主发送成功 ACK。
        if (result === false) {
          throw Object.assign(new Error('刷新已取消，当前表单内容已保留'), {
            errorCode: 'EMBED_OPERATION_NOT_ALLOWED'
          })
        }
        update({ formLoading: false, formError: null, phase: 'ready' })
        return true
      } catch (error) {
        if (TERMINAL_STATES.has(current.state)) return null
        if (isEmbedSessionFailure(error) || session.isExpired?.()) {
          expire(error)
          return null
        }
        update({
          formLoading: false,
          formError: normalizeError(error, {
            phase: 'native-form',
            recoverable: error?.errorCode !== 'EMBED_OPERATION_NOT_ALLOWED'
          }),
          phase: 'ready'
        })
        return null
      }
    }
    if (current.navigation.canBack) {
      return openListForm(
        current.navigation.mode,
        current.navigation.recordId
      )
    }
    return null
  }

  function refreshCurrent() {
    return current.navigation.surfaceType === EMBED_RUNTIME_SURFACES.FORM
      ? refreshForm()
      : refreshList()
  }

  function createOpaqueOperationId(factory, pattern, label) {
    if (typeof factory !== 'function') throw new TypeError(`${label} factory 无效`)
    const value = String(factory() || '')
    if (!pattern.test(value)) throw new TypeError(`${label} 无效`)
    return value
  }

  function createSubmitError(error) {
    const errorCode = String(error?.errorCode || '')
    const retryable = [
      'EMBED_REQUEST_IN_PROGRESS',
      'EMBED_NETWORK_ERROR',
      'EMBED_REQUEST_TIMEOUT',
      'EMBED_RUNTIME_UNAVAILABLE'
    ].includes(errorCode) || Number(error?.status) >= 500
    const normalized = normalizeError(error, { phase: 'create', recoverable: retryable })
    const publicMessage = {
      EMBED_REQUEST_IN_PROGRESS: '记录正在创建，请稍后使用同一内容重试',
      EMBED_IDEMPOTENCY_KEY_REUSED: '本次保存标识已用于其他内容，请修改数据后重试'
    }[errorCode]
    return Object.freeze({
      ...normalized,
      ...(publicMessage ? { message: publicMessage } : {}),
      recoverable: retryable
    })
  }

  /**
   * 原生 CREATE Dialog 的单一提交传输钩子。字段/按钮/校验全部由 Flow 原生
   * 组件完成；这里只有 Session 目标恢复、幂等创建和受控 form.saved 收据投影。
   */
  async function submitNativeRecord(values, actionKey = 'save') {
    if (current.state !== EMBED_RUNTIME_STATES.READY
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.FORM
      || current.navigation.mode !== 'CREATE'
      || current.nativeFormTarget?.mode !== 'CREATE'
      || typeof api.createRecord !== 'function'
      || !current.bootstrap.capabilities.includes('RECORD_CREATE')) {
      throw operationNotAllowed('当前 View 未开放记录创建')
    }
    const key = ['save', 'saveAndStart'].includes(actionKey) ? actionKey : ''
    if (!key || !values || typeof values !== 'object' || Array.isArray(values)) {
      throw operationNotAllowed('原生表单提交参数无效')
    }
    const data = Object.freeze({ ...values })
    const fingerprint = JSON.stringify({ data, actionKey: key })
    if (createAttempt?.fingerprint === fingerprint) {
      if (createAttempt.promise) return createAttempt.promise
      if (createAttempt.status === 'succeeded') return createAttempt.result
    } else if (createAttempt?.promise) {
      const error = new Error('已有记录创建请求正在处理')
      error.errorCode = 'EMBED_REQUEST_IN_PROGRESS'
      throw error
    }
    if (!createAttempt || createAttempt.fingerprint !== fingerprint) {
      createAttempt = {
        fingerprint,
        idempotencyKey: createOpaqueOperationId(
          idempotencyKeyFactory, /^[\x21-\x7E]{1,128}$/, 'Embed Idempotency-Key'
        ),
        clientMutationId: createOpaqueOperationId(
          clientMutationIdFactory, /^[\x20-\x7E]{1,128}$/, 'Embed clientMutationId'
        ),
        status: 'pending',
        promise: null,
        result: null
      }
    }

    const attempt = createAttempt
    update({ formSubmitting: true, formSubmitError: null })
    attempt.status = 'processing'
    attempt.promise = (async () => {
      try {
        const result = await api.createRecord({
          data,
          clientMutationId: attempt.clientMutationId,
          actionKey: key
        }, {
          idempotencyKey: attempt.idempotencyKey
        })
        attempt.status = 'succeeded'
        attempt.result = result
        update({
          formSubmitting: false,
          formSubmitError: null,
          formSubmitResult: result
        })
        // Bridge 本身再次执行精确 Schema 校验；服务端未返回合规 receipt 时
        // 宁可不发事件，也不能由浏览器伪造成功收据。
        send(EMBED_BRIDGE_MESSAGE_TYPES.FORM_SAVED, {
          receiptId: result?.receiptId,
          record: result?.record,
          clientMutationId:
            result?.clientMutationId || attempt.clientMutationId
        })
        return result
      } catch (error) {
        attempt.status = 'failed'
        if (isEmbedSessionFailure(error) || session.isExpired?.()) {
          expire(error)
        } else {
          update({
            formSubmitting: false,
            formSubmitError: createSubmitError(error),
            formSubmitResult: null
          })
        }
        throw error
      } finally {
        if (createAttempt === attempt) attempt.promise = null
      }
    })()
    return attempt.promise
  }

  function emitSelection(records) {
    if (current.state !== EMBED_RUNTIME_STATES.READY
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.LIST
      || !current.bootstrap.capabilities.includes('SELECTION_RETURN')) return false
    if (current.nativeListTarget) {
      const seen = new Set()
      const selection = []
      for (const record of Array.isArray(records) ? records : []) {
        const id = String(record?.id || '')
        if (!/^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(id) || seen.has(id)) continue
        seen.add(id)
        // 原生列表行包含完整业务数据；跨 origin 事件只返回 ID。
        // 若未来需要返回其他值，必须由服务端签发独立投影，不能直传行对象。
        selection.push(Object.freeze({ id, values: Object.freeze({}) }))
        if (selection.length >= current.bootstrap.limits.maxSelectionSize) break
      }
      update({
        selectedRecordIds: Object.freeze(selection.map(record => record.id))
      })
      return send(EMBED_BRIDGE_MESSAGE_TYPES.SELECTION_CHANGED, { selection })
    }
    const requestedIds = new Set(
      (Array.isArray(records) ? records : []).map(record => String(record?.id || ''))
    )
    const selected = (current.page?.items || [])
      .filter(record => requestedIds.has(record.id))
      .slice(0, current.bootstrap.limits.maxSelectionSize)
    update({
      selectedRecordIds: Object.freeze(selected.map(record => record.id))
    })
    return send(EMBED_BRIDGE_MESSAGE_TYPES.SELECTION_CHANGED, {
      selection: projectEmbedSelection(
        selected,
        current.schema,
        current.bootstrap.limits.maxSelectionSize
      )
    })
  }

  function emitResize(height) {
    if (current.state !== EMBED_RUNTIME_STATES.READY) return false
    return send(EMBED_BRIDGE_MESSAGE_TYPES.RESIZE, {
      height: clampInteger(height, 0, 0, 100000)
    })
  }

  /** Published Form 的 close 在直接 FORM 模式下交给宿主决定销毁/隐藏容器。 */
  function requestClose(reason = 'published-form-close') {
    return send(EMBED_BRIDGE_MESSAGE_TYPES.CLOSE_REQUESTED, {
      reason: String(reason || 'published-form-close').slice(0, 128)
    })
  }

  function expire(error) {
    if (current.state === EMBED_RUNTIME_STATES.DESTROYING
      || current.state === EMBED_RUNTIME_STATES.DESTROYED) return
    clearHeartbeat()
    querySequence += 1
    createAttempt = null
    session.clear('expired')
    onDelegatedSessionReset?.()
    const normalized = normalizeError(error, { phase: current.phase })
    update({
      state: EMBED_RUNTIME_STATES.SESSION_EXPIRED,
      error: { ...normalized, relaunchRequired: true, recoverable: false },
      nativeListTarget: null,
      nativeFormTarget: null,
      listLoading: false,
      formLoading: false,
      formSubmitting: false
    })
    send(EMBED_BRIDGE_MESSAGE_TYPES.SESSION_EXPIRED, {
      reason: normalized.errorCode,
      relaunchRequired: true
    })
  }

  function fail(error, phase = current.phase) {
    if (current.state === EMBED_RUNTIME_STATES.DESTROYING
      || current.state === EMBED_RUNTIME_STATES.DESTROYED) return
    clearHeartbeat()
    querySequence += 1
    const normalized = normalizeError(error, { phase })
    if (normalized.category === 'SESSION') {
      expire(error)
      return
    }
    update({
      state: EMBED_RUNTIME_STATES.FATAL_ERROR,
      error: normalized,
      nativeListTarget: null,
      nativeFormTarget: null,
      listLoading: false,
      formLoading: false,
      formSubmitting: false
    })
    send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, normalized)
  }

  async function retry() {
    if (current.state !== EMBED_RUNTIME_STATES.FATAL_ERROR
      || current.error?.recoverable !== true || typeof retryAction !== 'function') {
      return false
    }
    await retryAction()
    return current.state === EMBED_RUNTIME_STATES.READY
  }

  function releaseUnconfirmed(error) {
    const failure = new Error('Embed Session 释放结果无法确认')
    failure.errorCode = 'EMBED_SESSION_RELEASE_UNCONFIRMED'
    failure.cause = error
    return failure
  }

  /**
   * 终止 Runtime；宿主 destroy 命令使用严格确认模式，只有 DELETE Session
   * 成功（或本次从未签发 Session）才解析 Promise。组件卸载仍是 best effort，
   * 服务端 idle/absolute timeout 和 reaper 作为最终兜底。
   */
  function destroy({
    logout = true,
    requireLogoutConfirmation = false,
    preserveBridge = false
  } = {}) {
    if (destroyPromise) return destroyPromise
    if (current.state === EMBED_RUNTIME_STATES.DESTROYED) return Promise.resolve()

    const pendingExchange = exchangePromise
    clearHeartbeat()
    querySequence += 1
    createAttempt = null
    update({
      state: EMBED_RUNTIME_STATES.DESTROYING,
      phase: 'logout',
      nativeListTarget: null,
      nativeFormTarget: null,
      listLoading: false,
      formLoading: false,
      formSubmitting: false
    })

    destroyPromise = (async () => {
      try {
        if (pendingExchange) await pendingExchange
      } catch (error) {
        // Exchange 请求已发出却未拿到 Token 时结果具有二义性：服务端
        // 可能已提交 Session。严格模式必须 fail closed，不得发送 destroy ACK。
        if (requireLogoutConfirmation && exchangeAttempted && !sessionIssued) {
          throw releaseUnconfirmed(error)
        }
      }

      if (requireLogoutConfirmation && exchangeAttempted && !sessionIssued) {
        throw releaseUnconfirmed(exchangeFailure)
      }

      const accessToken = session.getAccessToken({ required: false })
      if (logout && accessToken) {
        try {
          await api.logout()
          sessionReleaseConfirmed = true
        } catch (error) {
          if (requireLogoutConfirmation) throw releaseUnconfirmed(error)
        }
      } else if (requireLogoutConfirmation && sessionIssued && !sessionReleaseConfirmed) {
        throw releaseUnconfirmed()
      }

      session.clear('destroyed')
      onDelegatedSessionReset?.()
      update({
        state: EMBED_RUNTIME_STATES.DESTROYED,
        phase: 'destroyed',
        listLoading: false,
        formLoading: false,
        formSubmitting: false
      })
      if (!preserveBridge) bridge?.destroy?.()
      listeners.clear()
    })()

    // 严格模式失败后保留 Token/Bridge，允许重复 destroy 命令再次尝试。
    // SDK 端超时会强制拆除 iframe 并 reject，宿主不得继续新 Launch。
    if (requireLogoutConfirmation) {
      destroyPromise = destroyPromise.catch(error => {
        destroyPromise = null
        throw error
      })
    }
    return destroyPromise
  }

  return Object.freeze({
    backToList,
    destroy,
    emitResize,
    emitSelection,
    getSnapshot: snapshot,
    requestClose,
    submitNativeRecord,
    openListCreate,
    openListView,
    refreshForm,
    refreshList,
    retry,
    start,
    subscribe
  })
}
