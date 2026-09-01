import {
  FLOW_EMBED_COMMANDS,
  FLOW_EMBED_EVENTS,
  FLOW_EMBED_PROTOCOL,
  FlowEmbedError,
  MAX_MESSAGE_BYTES,
  createSecureNonce,
  estimateMessageBytes,
  hasDangerousKeys,
  isChannelId,
  isLaunchCode,
  isLaunchId,
  isMessageId,
  isNonce,
  isPlainRecord,
  normalizeEmbedUrl,
  normalizeTargetOrigin,
  validateCommandPayload,
  validateEventPayload
} from './protocol.js'

const READY = 'ready'
const INIT = 'init'
const INIT_ACK = 'init.ack'
const READY_FIELDS = Object.freeze([
  'protocol', 'type', 'launchId', 'channelId', 'childNonce', 'supportedVersions'
])
const INIT_ACK_FIELDS = Object.freeze([
  'protocol', 'type', 'launchId', 'channelId', 'childNonce', 'parentNonce'
])

function hasExactFields(value, fields) {
  const keys = Object.keys(value)
  return keys.length === fields.length && keys.every(key => fields.includes(key))
}

function clampNumber(value, fallback, lower, upper) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.min(Math.max(number, lower), upper) : fallback
}

function normalizeHeight(value = {}) {
  const mode = value?.mode === 'fixed' ? 'fixed' : 'auto'
  const min = clampNumber(value?.min, 320, 0, 10000)
  const max = clampNumber(value?.max, Math.max(2000, min), min, 20000)
  const initial = clampNumber(value?.initial, min, min, max)
  return { mode, min, max, initial }
}

function normalizeLimit(value, fallback, minimum, maximum) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.min(Math.max(Math.floor(number), minimum), maximum)
}

/** 浏览器边界对象：协议验证与 DOM/MessageChannel 生命周期集中于此，业务宿主只消费事件。 */
export class FlowEmbedWidget {
  constructor(options = {}) {
    if (!options.container || typeof options.container.appendChild !== 'function') {
      throw new FlowEmbedError('container 无效', 'FLOW_EMBED_CONTAINER_INVALID')
    }
    if (!isLaunchId(options.launchId)) {
      throw new FlowEmbedError('launchId 无效', 'FLOW_EMBED_LAUNCH_INVALID')
    }
    if (!isChannelId(options.channelId)) {
      throw new FlowEmbedError('channelId 无效', 'FLOW_EMBED_CHANNEL_INVALID')
    }
    if (!isLaunchCode(options.launchCode)) {
      throw new FlowEmbedError('launchCode 无效', 'FLOW_EMBED_CODE_INVALID')
    }
    if (options.protocol !== undefined && options.protocol !== FLOW_EMBED_PROTOCOL) {
      throw new FlowEmbedError('协议版本不受支持', 'FLOW_EMBED_PROTOCOL_UNSUPPORTED')
    }

    this.windowRef = options.windowRef || globalThis.window
    this.documentRef = options.documentRef || globalThis.document
    if (!this.windowRef?.addEventListener || !this.documentRef?.createElement) {
      throw new FlowEmbedError('当前环境不支持 iframe', 'FLOW_EMBED_BROWSER_UNAVAILABLE')
    }

    this.targetOrigin = normalizeTargetOrigin(options.targetOrigin)
    this.launchId = options.launchId
    this.channelId = options.channelId
    this.embedUrl = normalizeEmbedUrl(options.embedUrl, this.targetOrigin, this.launchId)
    this.protocol = FLOW_EMBED_PROTOCOL
    this.container = options.container
    this.height = normalizeHeight(options.height)
    this.maxMessageBytes = normalizeLimit(options.maxMessageBytes, MAX_MESSAGE_BYTES, 1024, MAX_MESSAGE_BYTES)
    this.maxSeenMessageIds = normalizeLimit(options.maxSeenMessageIds, 256, 16, 2048)
    this.handshakeTimeoutMs = normalizeLimit(options.handshakeTimeoutMs, 10000, 0, 120000)
    this.destroyTimeoutMs = normalizeLimit(options.destroyTimeoutMs, 20000, 1000, 120000)
    this.nonceFactory = options.nonceFactory || (() => createSecureNonce(options.cryptoRef))
    this.messageChannelFactory = options.messageChannelFactory || (() => new globalThis.MessageChannel())
    const setTimeoutImpl = options.setTimeoutImpl || globalThis.setTimeout
    const clearTimeoutImpl = options.clearTimeoutImpl || globalThis.clearTimeout
    // Window timer 被作为任意对象的方法调用时，真实浏览器会因接收者错误抛出
    // Illegal invocation；闭包保留函数引用，确保默认与注入实现都以普通函数调用。
    this.setTimeoutImpl = typeof setTimeoutImpl === 'function'
      ? (...args) => setTimeoutImpl(...args)
      : undefined
    this.clearTimeoutImpl = typeof clearTimeoutImpl === 'function'
      ? (...args) => clearTimeoutImpl(...args)
      : undefined
    this.onEventHandler = options.onEvent
    this.onViolationHandler = options.onViolation
    this.listeners = new Map()
    this.seenMessageIds = new Set()
    this.state = 'waiting'
    this.parentNonce = ''
    this.childNonce = ''
    this.port = null
    this.timeoutId = undefined
    this.destroyTimeoutId = undefined
    this.destroyPromise = null
    this.destroyRequestId = ''
    this.resolveDestroy = null
    this.rejectDestroy = null
    this.destroyStartedFrom = ''
    this._launchCode = options.launchCode
    this.handleWindowMessage = this.handleWindowMessage.bind(this)
    this.handlePortMessage = this.handlePortMessage.bind(this)

    this.iframe = this.createIframe(options)
    this.windowRef.addEventListener('message', this.handleWindowMessage)
    this.container.appendChild(this.iframe)
    this.armHandshakeTimeout()
  }

  createIframe(options) {
    const iframe = this.documentRef.createElement('iframe')
    iframe.src = this.embedUrl
    iframe.title = String(options.title || 'Flow Embed')
    iframe.referrerPolicy = 'no-referrer'
    iframe.setAttribute?.('sandbox', 'allow-forms allow-scripts allow-same-origin')
    iframe.setAttribute?.('allow', '')
    iframe.style.width = '100%'
    iframe.style.border = '0'
    iframe.style.display = 'block'
    iframe.style.height = `${this.height.initial}px`
    return iframe
  }

  armHandshakeTimeout() {
    if (this.handshakeTimeoutMs <= 0 || typeof this.setTimeoutImpl !== 'function') return
    this.timeoutId = this.setTimeoutImpl(() => {
      if (this.state === 'waiting' || this.state === 'acknowledging') {
        this.fail(new FlowEmbedError('Embed 握手超时', 'FLOW_EMBED_HANDSHAKE_TIMEOUT'))
      }
    }, this.handshakeTimeoutMs)
  }

  clearHandshakeTimeout() {
    if (this.timeoutId !== undefined && typeof this.clearTimeoutImpl === 'function') {
      this.clearTimeoutImpl(this.timeoutId)
    }
    this.timeoutId = undefined
  }

  createNonce() {
    const nonce = String(this.nonceFactory() || '')
    if (!isNonce(nonce)) throw new FlowEmbedError('安全随机 nonce 无效', 'FLOW_EMBED_NONCE_INVALID')
    return nonce
  }

  /** Window 通道仅用于 ready/init；建立 MessagePort 后不再接收 Window 业务消息。 */
  handleWindowMessage(event) {
    if (this.state !== 'waiting') return false
    const data = event?.data
    // 页面、浏览器扩展和开发工具都会共享 window.message。只有明确声称属于当前
    // 握手的 ready 才进入安全校验，避免把无关消息误报为来源攻击。
    const isReadyCandidate = isPlainRecord(data)
      && data.protocol === this.protocol
      && data.type === READY
      && data.launchId === this.launchId
      && data.channelId === this.channelId
    if (!isReadyCandidate) return false
    if (event?.source !== this.iframe?.contentWindow || event?.origin !== this.targetOrigin) {
      return this.violation('ready 来源不受信任', 'FLOW_EMBED_READY_SOURCE_INVALID')
    }
    if (!this.validateBase(data) || !hasExactFields(data, READY_FIELDS)
      || data.type !== READY || !isNonce(data.childNonce)
      || !Array.isArray(data.supportedVersions) || data.supportedVersions.length !== 1
      || data.supportedVersions[0] !== this.protocol) {
      return this.violation('ready 消息或协议版本无效', 'FLOW_EMBED_READY_INVALID')
    }

    let channel
    try {
      channel = this.messageChannelFactory()
    } catch (cause) {
      this.fail(new FlowEmbedError('无法创建 MessageChannel', 'FLOW_EMBED_CHANNEL_UNAVAILABLE', cause))
      return false
    }
    if (!channel?.port1 || !channel?.port2 || typeof channel.port1.postMessage !== 'function') {
      this.fail(new FlowEmbedError('MessageChannel 无效', 'FLOW_EMBED_CHANNEL_UNAVAILABLE'))
      return false
    }

    this.childNonce = data.childNonce
    this.parentNonce = this.createNonce()
    this.port = channel.port1
    this.bindPort(this.port)
    this.state = 'acknowledging'
    const initMessage = {
      protocol: this.protocol,
      type: INIT,
      launchId: this.launchId,
      channelId: this.channelId,
      childNonce: this.childNonce,
      parentNonce: this.parentNonce,
      launchCode: this._launchCode
    }
    try {
      this.iframe.contentWindow.postMessage(initMessage, this.targetOrigin, [channel.port2])
    } catch (cause) {
      this.clearLaunchCode(initMessage)
      this.fail(new FlowEmbedError('无法初始化 Embed', 'FLOW_EMBED_INIT_FAILED', cause))
      return false
    }

    // postMessage 会同步执行结构化克隆；传输结束后立刻清除所有 SDK 内部明文引用。
    this.clearLaunchCode(initMessage)
    return true
  }

  clearLaunchCode(initMessage) {
    this._launchCode = ''
    if (initMessage) initMessage.launchCode = ''
  }

  handlePortMessage(event) {
    if (this.state === 'destroyed' || this.state === 'failed') return false
    const data = event?.data
    if (this.state === 'acknowledging') return this.handleInitAck(data)
    // destroy 可以在 init.ack 尚在途时发出。该 ack 仍需完整校验，但不能把
    // Widget 从 destroying 恢复成 connected，也不能对宿主再发 connected 事件。
    if (this.state === 'destroying' && this.destroyStartedFrom === 'acknowledging'
      && data?.type === INIT_ACK) {
      return this.handleInitAck(data, { preserveDestroying: true })
    }
    if (this.state !== 'connected' && this.state !== 'destroying') return false
    if (!this.validateAuthenticatedPortMessage(data)) {
      return this.violation('Embed 事件无效', 'FLOW_EMBED_EVENT_INVALID')
    }
    if (this.seenMessageIds.has(data.messageId)) {
      return this.violation('Embed 事件重复', 'FLOW_EMBED_EVENT_REPLAYED')
    }
    if (!validateEventPayload(data.type, data.payload)) {
      return this.violation('Embed 事件类型或 payload 未授权', 'FLOW_EMBED_EVENT_FORBIDDEN')
    }

    this.rememberMessageId(data.messageId)
    if (this.state === 'destroying') {
      return this.handleDestroyResponse(data)
    }
    if (data.type === FLOW_EMBED_EVENTS.RESIZE && this.height.mode === 'auto') {
      const height = Math.min(Math.max(data.payload.height, this.height.min), this.height.max)
      this.iframe.style.height = `${height}px`
    }
    this.emit(data.type, Object.freeze({ ...data, payload: data.payload }))
    return true
  }

  handleInitAck(data, { preserveDestroying = false } = {}) {
    if (!this.validateBase(data) || !hasExactFields(data, INIT_ACK_FIELDS)
      || data.type !== INIT_ACK
      || data.childNonce !== this.childNonce || data.parentNonce !== this.parentNonce) {
      return this.violation('init.ack 无效', 'FLOW_EMBED_INIT_ACK_INVALID')
    }
    this.state = preserveDestroying ? 'destroying' : 'connected'
    this.clearHandshakeTimeout()
    if (preserveDestroying) return true
    this.emit('connected', Object.freeze({
      protocol: this.protocol,
      type: 'connected',
      launchId: this.launchId,
      channelId: this.channelId
    }))
    return true
  }

  validateBase(data) {
    return isPlainRecord(data) && !hasDangerousKeys(data)
      && estimateMessageBytes(data) <= this.maxMessageBytes
      && data.protocol === this.protocol
      && data.launchId === this.launchId
      && data.channelId === this.channelId
      && typeof data.type === 'string'
      && /^[a-z][a-z.-]{1,63}$/.test(data.type)
  }

  validateAuthenticatedPortMessage(data) {
    return this.validateBase(data)
      && isMessageId(data.messageId)
      && (data.requestId === null || data.requestId === undefined || isMessageId(data.requestId))
      && typeof data.timestamp === 'string'
      && !Number.isNaN(Date.parse(data.timestamp))
      && data.childNonce === this.childNonce
      && data.parentNonce === this.parentNonce
  }

  rememberMessageId(messageId) {
    this.seenMessageIds.add(messageId)
    if (this.seenMessageIds.size > this.maxSeenMessageIds) {
      this.seenMessageIds.delete(this.seenMessageIds.values().next().value)
    }
  }

  bindPort(port) {
    if (typeof port.addEventListener === 'function') {
      port.addEventListener('message', this.handlePortMessage)
      port.start?.()
    } else {
      port.onmessage = this.handlePortMessage
    }
  }

  unbindPort(port) {
    if (!port) return
    if (typeof port.removeEventListener === 'function') port.removeEventListener('message', this.handlePortMessage)
    else if (port.onmessage === this.handlePortMessage) port.onmessage = null
  }

  /** 发送协议白名单命令；返回 requestId，宿主可用它关联后续 ack/error 事件。 */
  sendCommand(type, payload = {}) {
    if (this.state !== 'connected' || !this.port) {
      throw new FlowEmbedError('Embed 尚未连接', 'FLOW_EMBED_NOT_CONNECTED')
    }
    return this.postCommand(type, payload)
  }

  /** 已建立 MessagePort 后的底层发送原语；destroy 可在 init.ack 在途时使用。 */
  postCommand(type, payload = {}) {
    if (!this.port) {
      throw new FlowEmbedError('Embed 安全通道不可用', 'FLOW_EMBED_NOT_CONNECTED')
    }
    if (!validateCommandPayload(type, payload)) {
      throw new FlowEmbedError('Embed 命令或 payload 未授权', 'FLOW_EMBED_COMMAND_FORBIDDEN')
    }
    const messageId = `msg_${this.createNonce()}`
    const requestId = `req_${this.createNonce()}`
    const message = {
      protocol: this.protocol,
      type,
      launchId: this.launchId,
      channelId: this.channelId,
      messageId,
      requestId,
      timestamp: new Date().toISOString(),
      childNonce: this.childNonce,
      parentNonce: this.parentNonce,
      payload
    }
    if (estimateMessageBytes(message) > this.maxMessageBytes || hasDangerousKeys(message)) {
      throw new FlowEmbedError('Embed 命令过大或包含危险字段', 'FLOW_EMBED_COMMAND_INVALID')
    }
    this.port.postMessage(message)
    return requestId
  }

  refresh() { return this.sendCommand(FLOW_EMBED_COMMANDS.REFRESH) }
  setTheme(theme) { return this.sendCommand(FLOW_EMBED_COMMANDS.SET_THEME, { theme }) }
  setLocale(locale) { return this.sendCommand(FLOW_EMBED_COMMANDS.SET_LOCALE, { locale }) }
  focus() { return this.sendCommand(FLOW_EMBED_COMMANDS.FOCUS) }

  on(type, handler) {
    if (typeof handler !== 'function') throw new FlowEmbedError('事件处理器无效', 'FLOW_EMBED_HANDLER_INVALID')
    const key = String(type || '')
    const handlers = this.listeners.get(key) || new Set()
    handlers.add(handler)
    this.listeners.set(key, handlers)
    return () => this.off(key, handler)
  }

  off(type, handler) {
    const handlers = this.listeners.get(String(type || ''))
    handlers?.delete(handler)
    if (handlers?.size === 0) this.listeners.delete(String(type || ''))
  }

  emit(type, event) {
    try { this.onEventHandler?.(event) } catch { /* 宿主回调不能破坏安全通道。 */ }
    for (const key of [type, '*']) {
      for (const handler of this.listeners.get(key) || []) {
        try { handler(event) } catch { /* 单个订阅者不能影响其他订阅者。 */ }
      }
    }
  }

  violation(message, errorCode) {
    const error = new FlowEmbedError(message, errorCode)
    try { this.onViolationHandler?.(error) } catch { /* 违规回调只用于诊断。 */ }
    return false
  }

  fail(error) {
    if (this.state === 'failed' || this.state === 'destroyed') return
    if (this.state === 'destroying') {
      this.failDestroy(error instanceof Error ? error : new FlowEmbedError(
        'Flow Embed 销毁通道失败',
        'FLOW_EMBED_DESTROY_FAILED'
      ))
      return
    }
    this.state = 'failed'
    this.cleanup(true)
    this.emit('error', Object.freeze({
      protocol: this.protocol,
      type: 'error',
      launchId: this.launchId,
      channelId: this.channelId,
      payload: Object.freeze({
        errorCode: error?.errorCode || 'FLOW_EMBED_FAILED',
        message: error?.message || 'Embed 初始化失败',
        traceId: null,
        recoverable: false
      })
    }))
  }

  cleanup(removeIframe = true) {
    this.clearHandshakeTimeout()
    this.clearDestroyTimeout()
    this.windowRef.removeEventListener?.('message', this.handleWindowMessage)
    this.unbindPort(this.port)
    this.port?.close?.()
    this.port = null
    this.parentNonce = ''
    this.childNonce = ''
    this.clearLaunchCode()
    this.seenMessageIds.clear()
    if (removeIframe && this.iframe) {
      if (typeof this.iframe.remove === 'function') this.iframe.remove()
      else this.iframe.parentNode?.removeChild?.(this.iframe)
    }
  }

  clearDestroyTimeout() {
    if (this.destroyTimeoutId !== undefined && typeof this.clearTimeoutImpl === 'function') {
      this.clearTimeoutImpl(this.destroyTimeoutId)
    }
    this.destroyTimeoutId = undefined
  }

  handleDestroyResponse(data) {
    if (!this.destroyRequestId || data.requestId !== this.destroyRequestId) return true
    if (data.type === FLOW_EMBED_EVENTS.ACK
      && data.payload.command === FLOW_EMBED_COMMANDS.DESTROY) {
      this.emit(data.type, Object.freeze({ ...data, payload: data.payload }))
      this.finishDestroy()
      return true
    }
    if (data.type === FLOW_EMBED_EVENTS.ERROR) {
      this.emit(data.type, Object.freeze({ ...data, payload: data.payload }))
      const error = new FlowEmbedError(
        data.payload.message || 'Flow Embed 会话注销失败',
        data.payload.errorCode || 'FLOW_EMBED_DESTROY_FAILED'
      )
      error.traceId = data.payload.traceId ?? null
      this.failDestroy(error)
      return true
    }
    return true
  }

  finishDestroy() {
    if (this.state === 'destroyed') return
    const resolve = this.resolveDestroy
    this.state = 'destroyed'
    this.cleanup(true)
    this.listeners.clear()
    this.onEventHandler = undefined
    this.onViolationHandler = undefined
    this.iframe = null
    this.destroyRequestId = ''
    this.resolveDestroy = null
    this.rejectDestroy = null
    resolve?.()
  }

  failDestroy(error) {
    if (this.state === 'destroyed') return
    const reject = this.rejectDestroy
    this.state = 'destroyed'
    this.cleanup(true)
    this.listeners.clear()
    this.onEventHandler = undefined
    this.onViolationHandler = undefined
    this.iframe = null
    this.destroyRequestId = ''
    this.resolveDestroy = null
    this.rejectDestroy = null
    reject?.(error)
  }

  /**
   * 请求 iframe 先完成服务端 Session Logout，收到关联 ACK 后再拆除 DOM。
   * 返回值可重入：重复调用复用同一 Promise，不会重复发送 destroy。
   */
  destroy() {
    if (this.destroyPromise) return this.destroyPromise

    // ready/init 尚未发生时 launchCode 从未交给 iframe，不可能已建立 Session。
    if (!['connected', 'acknowledging'].includes(this.state) || !this.port) {
      this.destroyPromise = Promise.resolve()
      this.state = 'destroyed'
      this.cleanup(true)
      this.listeners.clear()
      this.onEventHandler = undefined
      this.onViolationHandler = undefined
      this.iframe = null
      return this.destroyPromise
    }

    this.destroyStartedFrom = this.state
    this.destroyPromise = new Promise((resolve, reject) => {
      this.resolveDestroy = resolve
      this.rejectDestroy = reject
    })
    try {
      this.destroyRequestId = this.postCommand(FLOW_EMBED_COMMANDS.DESTROY)
      this.state = 'destroying'
      this.clearHandshakeTimeout()
      if (typeof this.setTimeoutImpl === 'function') {
        this.destroyTimeoutId = this.setTimeoutImpl(() => {
          this.failDestroy(new FlowEmbedError(
            'Flow Embed 会话注销确认超时',
            'FLOW_EMBED_DESTROY_TIMEOUT'
          ))
        }, this.destroyTimeoutMs)
      }
    } catch (cause) {
      this.failDestroy(new FlowEmbedError(
        '无法发起 Flow Embed 会话注销',
        'FLOW_EMBED_DESTROY_FAILED',
        cause
      ))
    }
    return this.destroyPromise
  }

  getState() { return this.state }
}
