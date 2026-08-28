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
import {
  buildEmbedListQuery,
  normalizeEmbedBootstrap,
  normalizeEmbedExternalSchema,
  projectEmbedPage,
  projectEmbedSelection
} from '../projection/normalizeEmbedSchema.js'
import {
  buildEmbedCreateEvaluationRequest,
  buildEmbedCreateRequest,
  buildEmbedOptionQuery,
  normalizeEmbedCreateResult,
  normalizeEmbedFormResult,
  normalizeEmbedOptionPage,
  normalizeEmbedRecordResult,
  projectEmbedFormSaved,
  requiredFormCapability
} from '../projection/normalizeEmbedForm.js'

export const EMBED_RUNTIME_STATES = Object.freeze({
  LOADING_ENTRY: 'LOADING_ENTRY',
  WAITING_HANDSHAKE: 'WAITING_HANDSHAKE',
  EXCHANGING: 'EXCHANGING',
  BOOTSTRAPPING: 'BOOTSTRAPPING',
  READY: 'READY',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
  FATAL_ERROR: 'FATAL_ERROR',
  DESTROYED: 'DESTROYED'
})

export const EMBED_RUNTIME_SURFACES = Object.freeze({
  LIST: 'LIST',
  FORM: 'FORM'
})

const TERMINAL_STATES = new Set([
  EMBED_RUNTIME_STATES.SESSION_EXPIRED,
  EMBED_RUNTIME_STATES.FATAL_ERROR,
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
  abortControllerFactory = () => new globalThis.AbortController(),
  onFocus
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
  let createEvaluationSequence = 0
  let createEvaluationController = null

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

  /** 任何完整表单读取、导航、提交或终态都会使在途联动投影失效。 */
  function cancelCreateEvaluation() {
    createEvaluationSequence += 1
    createEvaluationController?.abort?.()
    createEvaluationController = null
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
      || bootstrap.view.surfaceType !== schema.view.surfaceType
      || bootstrap.view.revision !== schema.view.revision) {
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

  function ensureFormApi(mode) {
    if (typeof api.getForm !== 'function' || typeof api.getRecord !== 'function'
      || typeof api.queryFormOptions !== 'function') {
      throw new TypeError('Embed form runtime API 无效')
    }
    if (mode === 'CREATE' && typeof api.createRecord !== 'function') {
      throw new TypeError('Embed RECORD_CREATE API 无效')
    }
    if (mode === 'CREATE' && typeof api.evaluateCreate !== 'function') {
      throw new TypeError('Embed CREATE 表单重算 API 无效')
    }
  }

  function ensureFormProjection(bootstrap) {
    const mode = bootstrap.view.entryMode
    const capability = requiredFormCapability(mode)
    if (bootstrap.view.surfaceType !== EMBED_RUNTIME_SURFACES.FORM || !capability
      || !bootstrap.capabilities.includes(capability)) {
      const error = new Error('当前 View 未开放表单读取')
      error.errorCode = 'EMBED_CAPABILITY_FORBIDDEN'
      throw error
    }
    ensureFormApi(mode)
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

  /**
   * FORM 入口坐标来自 Bootstrap；LIST 子导航坐标来自当前 External Schema 动作与已投影行。
   * 两种路径都不接受 URL/宿主传入 formId、entity、release，VIEW 响应还必须与预期行 ID 相等。
   */
  async function executeFormRead({
    initial = false,
    mode = current.navigation.mode,
    recordId = current.navigation.recordId
  } = {}) {
    cancelCreateEvaluation()
    const sequence = ++querySequence
    if (!initial) update({ formLoading: true, formError: null })
    try {
      const request = Object.freeze({
        mode,
        ...(recordId ? { recordId } : {})
      })
      const rawForm = await api.getForm(request)
      if (sequence !== querySequence || TERMINAL_STATES.has(current.state)) return null
      let form = normalizeEmbedFormResult(rawForm, current.bootstrap, {
        mode,
        ...(recordId ? { recordId } : {})
      })
      if (mode !== 'CREATE') {
        if (!form.record?.id) {
          const error = new Error('Embed 表单记录坐标缺失')
          error.errorCode = 'EMBED_FORM_RECORD_INVALID'
          throw error
        }
        form = normalizeEmbedRecordResult(await api.getRecord(form.record.id), form)
      }
      if (sequence !== querySequence || TERMINAL_STATES.has(current.state)) return null
      // 直接 VIEW 入口的 recordId 只可由服务端 Session 恢复；首次表单响应校验后再写入
      // 本地导航状态，后续 options/lookup 查询才能携带同一个已验证坐标。
      const resolvedRecordId = mode === 'VIEW' ? form.record.id : null
      update({
        form,
        navigation: navigationState(
          EMBED_RUNTIME_SURFACES.FORM,
          mode,
          resolvedRecordId,
          current.navigation.canBack
        ),
        formLoading: false,
        formError: null,
        phase: 'ready'
      })
      return form
    } catch (error) {
      if (sequence !== querySequence || TERMINAL_STATES.has(current.state)) return null
      if (isEmbedSessionFailure(error) || session.isExpired?.()) {
        expire(error)
        return null
      }
      const normalized = normalizeError(error, { phase: 'form', recoverable: true })
      if (initial) throw Object.assign(error, { normalizedEmbedError: normalized })
      if (current.navigation.canBack) {
        // 子表单失败不销毁仍然有效的 LIST 会话；保留封闭的返回路径供用户恢复列表。
        update({ formLoading: false, formError: normalized, phase: 'ready' })
        return null
      }
      if (!normalized.recoverable) {
        fail(error, 'form')
        return null
      }
      update({ formLoading: false, formError: normalized })
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
      const bootstrap = normalizeEmbedBootstrap(rawBootstrap)
      session.applyHeartbeat({
        status: 'ACTIVE',
        idleExpiresAt: bootstrap.session.idleExpiresAt,
        absoluteExpiresAt: bootstrap.session.expiresAt
      })
      update({
        bootstrap,
        theme: bootstrap.ui.theme,
        locale: bootstrap.ui.locale,
        phase: 'schema'
      })

      if (bootstrap.view.surfaceType === 'LIST') {
        const schema = normalizeEmbedExternalSchema(await api.getSchema())
        ensureMatchingProjection(bootstrap, schema)
        update({
          schema,
          navigation: navigationState(EMBED_RUNTIME_SURFACES.LIST, 'LIST'),
          phase: 'query',
          listLoading: true
        })
        await executeListQuery({
          queryValues: {},
          pageNum: 1,
          pageSize: Math.min(
            bootstrap.ui.pageSize,
            bootstrap.limits.maxPageSize,
            schema.list.pagination.maxPageSize
          ),
          initial: true
        })
      } else {
        ensureFormProjection(bootstrap)
        update({
          navigation: navigationState(
            EMBED_RUNTIME_SURFACES.FORM,
            bootstrap.view.entryMode
          ),
          phase: 'form',
          formLoading: true
        })
        await executeFormRead({
          initial: true,
          mode: bootstrap.view.entryMode,
          recordId: null
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
      heartbeatSeconds = clampInteger(exchange.heartbeatAfterSeconds, 60, 15, 300)
      await loadRuntime()
    } catch (error) {
      if (TERMINAL_STATES.has(current.state)) return
      // Exchange 结果丢失后不能安全重试一次性 Launch，必须由宿主重新签发。
      session.clear('exchange_failed')
      const normalized = normalizeError(error, { phase: 'exchange' })
      if (normalized.category === 'TRANSIENT') {
        expire(Object.assign(error, { errorCode: 'EMBED_LAUNCH_EXPIRED' }))
      } else if (normalized.category === 'SESSION') {
        expire(error)
      } else {
        fail(error, 'exchange')
      }
    }
  }

  function handleBridgeMessage(message) {
    if (message.type === EMBED_BRIDGE_MESSAGE_TYPES.REFRESH) {
      refreshCurrent().then(() => {
        if (current.state !== EMBED_RUNTIME_STATES.READY) return
        const refreshError = current.navigation.surfaceType === EMBED_RUNTIME_SURFACES.FORM
          ? current.formError : current.listError
        if (refreshError) {
          send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, refreshError, {
            requestId: message.requestId
          })
          return
        }
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
      update({ locale: String(message.payload.locale) })
      send(EMBED_BRIDGE_MESSAGE_TYPES.ACK, { command: message.type }, {
        requestId: message.requestId
      })
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
      send(EMBED_BRIDGE_MESSAGE_TYPES.ACK, { command: message.type }, {
        requestId: message.requestId
      })
      destroy().catch(() => {})
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
    ensureFormApi(mode)
    return action
  }

  async function openListForm(mode, recordId = null) {
    cancelCreateEvaluation()
    createAttempt = null
    update({
      navigation: navigationState(
        EMBED_RUNTIME_SURFACES.FORM,
        mode,
        recordId,
        true
      ),
      form: null,
      formLoading: true,
      formError: null,
      formSubmitting: false,
      formSubmitError: null,
      formSubmitResult: null,
      phase: 'form'
    })
    return executeFormRead({ mode, recordId })
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
    cancelCreateEvaluation()
    querySequence += 1
    createAttempt = null
    update({
      navigation: navigationState(EMBED_RUNTIME_SURFACES.LIST, 'LIST'),
      form: null,
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
    return executeFormRead({
      mode: current.navigation.mode,
      recordId: current.navigation.recordId
    })
  }

  function refreshCurrent() {
    return current.navigation.surfaceType === EMBED_RUNTIME_SURFACES.FORM
      ? refreshForm()
      : refreshList()
  }

  function requireFormField(fieldCode, sourceKey) {
    const field = current.form?.fields?.find(candidate => candidate.code === fieldCode)
    if (!field || !field[sourceKey]) {
      const error = new Error('Embed 字段远程来源未开放')
      error.errorCode = 'EMBED_OPERATION_NOT_ALLOWED'
      throw error
    }
    return field
  }

  async function queryFormOptions(fieldCode, {
    keyword = '', formValues = {}, pageNum = 1, pageSize = 50
  } = {}, { signal } = {}) {
    if (current.state !== EMBED_RUNTIME_STATES.READY) return null
    const field = requireFormField(fieldCode, 'optionSource')
    try {
      const request = buildEmbedOptionQuery(field, formValues, {
        keyword, pageNum, pageSize,
        mode: current.navigation.mode,
        recordId: current.navigation.recordId
      })
      return normalizeEmbedOptionPage(
        await api.queryFormOptions(field.code, request, { signal }),
        field
      )
    } catch (error) {
      if (error?.errorCode === 'EMBED_REQUEST_ABORTED') throw error
      if (isEmbedSessionFailure(error) || session.isExpired?.()) {
        expire(error)
        return null
      }
      error.normalizedEmbedError = normalizeError(error, {
        phase: 'options', recoverable: true
      })
      throw error
    }
  }

  async function queryFormLookups() {
    if (current.state !== EMBED_RUNTIME_STATES.READY) return null
    // 路由为协议稳定性保留，但 V1 Runtime 不消费任何 Lookup 成功响应。
    const error = new Error('Embed V1 未开放 Lookup')
    error.status = 403
    error.errorCode = 'EMBED_OPERATION_NOT_ALLOWED'
    throw error
  }

  function requireCreateAction() {
    if (current.state !== EMBED_RUNTIME_STATES.READY
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.FORM
      || current.navigation.mode !== 'CREATE'
      || current.form?.mode !== 'CREATE'
      || !current.bootstrap.capabilities.includes('RECORD_CREATE')) {
      const error = new Error('当前 View 未开放记录创建')
      error.errorCode = 'EMBED_OPERATION_NOT_ALLOWED'
      throw error
    }
    const action = current.form?.actions?.find(candidate => candidate.key === 'save')
    if (!action || action.transport !== 'RECORD_CREATE' || action.enabled !== true
      || action.requiresRecordVersion === true || action.idempotencyRequired !== true) {
      const error = new Error(action?.disabledReason || '记录创建动作不可用')
      error.errorCode = 'EMBED_OPERATION_NOT_ALLOWED'
      throw error
    }
    return action
  }

  function requireCreateEvaluation() {
    if (current.state !== EMBED_RUNTIME_STATES.READY
      || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.FORM
      || current.navigation.mode !== 'CREATE'
      || current.form?.mode !== 'CREATE'
      || current.formSubmitting
      || current.formSubmitResult
      || !current.bootstrap?.capabilities?.includes('RECORD_CREATE')
      || typeof api.evaluateCreate !== 'function') {
      const error = new Error('当前 View 未开放 CREATE 表单重算')
      error.errorCode = 'EMBED_OPERATION_NOT_ALLOWED'
      throw error
    }
  }

  /**
   * 只读重算当前 CREATE 草稿。新请求会主动取消旧请求，sequence 再阻止无法取消或
   * 已进入响应阶段的旧结果覆盖较新的字段状态。
   */
  async function evaluateCreate(values) {
    requireCreateEvaluation()
    const request = buildEmbedCreateEvaluationRequest(current.form, values)
    const sequence = ++createEvaluationSequence
    createEvaluationController?.abort?.()
    const controller = abortControllerFactory()
    if (!controller || typeof controller.abort !== 'function' || !controller.signal) {
      throw new TypeError('Embed AbortController 无效')
    }
    createEvaluationController = controller
    update({ formError: null })
    try {
      const raw = await api.evaluateCreate(request, { signal: controller.signal })
      if (sequence !== createEvaluationSequence
        || TERMINAL_STATES.has(current.state)
        || current.navigation.surfaceType !== EMBED_RUNTIME_SURFACES.FORM
        || current.navigation.mode !== 'CREATE') return null
      const form = normalizeEmbedFormResult(raw, current.bootstrap, { mode: 'CREATE' })
      if (sequence !== createEvaluationSequence) return null
      update({ form, formError: null })
      return form
    } catch (error) {
      if (sequence !== createEvaluationSequence
        || error?.errorCode === 'EMBED_REQUEST_ABORTED'
        || error?.name === 'AbortError') return null
      if (isEmbedSessionFailure(error) || session.isExpired?.()) {
        expire(error)
        return null
      }
      error.normalizedEmbedError = normalizeError(error, {
        phase: 'form-evaluation', recoverable: true
      })
      update({ formError: error.normalizedEmbedError })
      throw error
    } finally {
      if (sequence === createEvaluationSequence) createEvaluationController = null
    }
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
   * 同一份规范化 data 的双击与失败重试共享一个 Idempotency-Key；只有用户修改数据后
   * 才启动新的逻辑操作。成功结果会替换当前 CREATE 表单的记录投影并通知宿主。
   */
  async function createRecord(values) {
    requireCreateAction()
    cancelCreateEvaluation()
    const draft = buildEmbedCreateRequest(current.form, values)
    const fingerprint = JSON.stringify(draft.data)

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
    const request = buildEmbedCreateRequest(
      current.form, values, attempt.clientMutationId
    )
    update({ formSubmitting: true, formSubmitError: null })
    attempt.status = 'processing'
    attempt.promise = (async () => {
      try {
        const result = normalizeEmbedCreateResult(
          await api.createRecord(request, {
            idempotencyKey: attempt.idempotencyKey
          }),
          current.form,
          attempt.clientMutationId
        )
        attempt.status = 'succeeded'
        attempt.result = result
        update({
          form: Object.freeze({ ...current.form, record: result.record }),
          formSubmitting: false,
          formSubmitError: null,
          formSubmitResult: result
        })
        send(
          EMBED_BRIDGE_MESSAGE_TYPES.FORM_SAVED,
          projectEmbedFormSaved(result, current.form)
        )
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

  function expire(error) {
    if (current.state === EMBED_RUNTIME_STATES.DESTROYED) return
    clearHeartbeat()
    cancelCreateEvaluation()
    querySequence += 1
    createAttempt = null
    session.clear('expired')
    const normalized = normalizeError(error, { phase: current.phase })
    update({
      state: EMBED_RUNTIME_STATES.SESSION_EXPIRED,
      error: { ...normalized, relaunchRequired: true, recoverable: false },
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
    if (current.state === EMBED_RUNTIME_STATES.DESTROYED) return
    clearHeartbeat()
    cancelCreateEvaluation()
    querySequence += 1
    const normalized = normalizeError(error, { phase })
    if (normalized.category === 'SESSION') {
      expire(error)
      return
    }
    update({
      state: EMBED_RUNTIME_STATES.FATAL_ERROR,
      error: normalized,
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

  async function destroy({ logout = true } = {}) {
    if (current.state === EMBED_RUNTIME_STATES.DESTROYED) return
    clearHeartbeat()
    cancelCreateEvaluation()
    querySequence += 1
    createAttempt = null
    update({
      state: EMBED_RUNTIME_STATES.DESTROYED,
      phase: 'destroyed',
      listLoading: false,
      formLoading: false,
      formSubmitting: false
    })
    if (logout && session.getAccessToken({ required: false })) {
      try {
        await api.logout()
      } catch {
        // 页面销毁时 Logout 是尽力而为，服务端 idle/absolute timeout 是最终兜底。
      }
    }
    session.clear('destroyed')
    bridge?.destroy?.()
    listeners.clear()
  }

  return Object.freeze({
    backToList,
    createRecord,
    destroy,
    evaluateCreate,
    emitResize,
    emitSelection,
    getSnapshot: snapshot,
    queryFormLookups,
    queryFormOptions,
    openListCreate,
    openListView,
    refreshForm,
    refreshList,
    retry,
    start,
    subscribe
  })
}
