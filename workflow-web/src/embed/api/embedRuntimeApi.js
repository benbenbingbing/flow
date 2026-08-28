import { createEmbedRequest } from './embedRequest.js'
import { isSecureHandshakeNonce } from '../security/secureNonce.js'

export const FLOW_EMBED_PROTOCOL_VERSION = 'flow-embed/1'
export const FLOW_EMBED_PROTOCOL_HEADER_VALUE = '1'

const PROTOCOL_OPTIONS = Object.freeze({
  headers: Object.freeze({
    'X-Flow-Embed-Protocol': FLOW_EMBED_PROTOCOL_HEADER_VALUE
  })
})

function normalizeSegment(value, label) {
  const normalized = String(value || '').trim()
  if (!normalized || normalized.length > 256 || /[/?#\\]/.test(normalized)) {
    throw new TypeError(`${label} 无效`)
  }
  return encodeURIComponent(normalized)
}

function normalizeLaunchId(value) {
  const launchId = String(value || '').trim()
  if (!/^lch_[A-Za-z0-9_-]{16,60}$/.test(launchId)) {
    throw new TypeError('Embed launchId 无效')
  }
  return launchId
}

function normalizeFormMode(value) {
  const mode = String(value || '').trim().toUpperCase()
  if (!['CREATE', 'VIEW'].includes(mode)) {
    throw new TypeError('Embed form mode 无效')
  }
  return mode
}

function optionalSignalOptions(signal) {
  return signal ? { ...PROTOCOL_OPTIONS, signal } : PROTOCOL_OPTIONS
}

function createRecordOptions(idempotencyKey, signal) {
  const key = String(idempotencyKey || '')
  if (!/^[\x21-\x7E]{1,128}$/.test(key)) {
    throw new TypeError('Embed Idempotency-Key 无效')
  }
  return {
    headers: Object.freeze({
      ...PROTOCOL_OPTIONS.headers,
      'Idempotency-Key': key
    }),
    ...(signal ? { signal } : {})
  }
}

function normalizeCreateBody(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)
    || !input.data || typeof input.data !== 'object' || Array.isArray(input.data)
    || Object.keys(input).some(key => !['data', 'clientMutationId'].includes(key))) {
    throw new TypeError('Embed 创建请求无效')
  }
  // 只复制协议允许的两个字段，调用方即使污染对象原型也不能夹带目标坐标。
  return Object.freeze({
    data: Object.freeze({ ...input.data }),
    ...(Object.prototype.hasOwnProperty.call(input, 'clientMutationId')
      ? { clientMutationId: input.clientMutationId }
      : {})
  })
}

function normalizeCreateEvaluationBody(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)
    || !input.data || typeof input.data !== 'object' || Array.isArray(input.data)
    || Object.keys(input).some(key => key !== 'data')) {
    throw new TypeError('Embed CREATE 表单重算请求无效')
  }
  return Object.freeze({ data: Object.freeze({ ...input.data }) })
}

function normalizeLaunchCode(value) {
  const code = String(value || '').trim()
  if (!/^[A-Za-z0-9_-]{43,86}$/.test(code)) {
    throw new TypeError('Embed launch code 无效')
  }
  return code
}

function normalizeNonce(value, label) {
  const nonce = String(value || '').trim()
  if (!isSecureHandshakeNonce(nonce)) {
    throw new TypeError(`${label} 无效`)
  }
  return nonce
}

function normalizeChannelId(value) {
  const channelId = String(value || '').trim()
  if (!/^[A-Za-z0-9._:-]{16,128}$/.test(channelId)) {
    throw new TypeError('Embed channelId 无效')
  }
  return channelId
}

function normalizeParentOrigin(value) {
  const raw = String(value || '').trim()
  let parsed
  try {
    parsed = new URL(raw)
  } catch {
    throw new TypeError('Embed parentOrigin 无效')
  }
  if (parsed.protocol !== 'https:' || parsed.origin === 'null' || parsed.origin !== raw
    || parsed.username || parsed.password) {
    throw new TypeError('Embed parentOrigin 无效')
  }
  return parsed.origin
}

function normalizeSdkVersion(value) {
  const version = String(value || '').trim()
  if (!/^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?$/.test(version)) {
    throw new TypeError('Embed sdkVersion 无效')
  }
  return version
}

function exchangeBody(input = {}) {
  return Object.freeze({
    launchCode: normalizeLaunchCode(input.launchCode),
    channelId: normalizeChannelId(input.channelId),
    parentOrigin: normalizeParentOrigin(input.parentOrigin),
    parentNonce: normalizeNonce(input.parentNonce, 'Embed parentNonce'),
    childNonce: normalizeNonce(input.childNonce, 'Embed childNonce'),
    sdkVersion: normalizeSdkVersion(input.sdkVersion)
  })
}

/**
 * Embed runtime 只暴露经过审查的列表、表单和会话端点，不向组件透传通用 request。
 * 远程字段响应中的 queryUrl 也不会在这里执行，所有请求均重新构造为固定相对路由。
 */
export function createEmbedRuntimeApi(client = createEmbedRequest()) {
  if (!client || typeof client.get !== 'function' || typeof client.post !== 'function'
    || typeof client.delete !== 'function') {
    throw new TypeError('Embed API client 无效')
  }

  return Object.freeze({
    exchange(launchId, input) {
      const launch = normalizeLaunchId(launchId)
      return client.post(
        `/launches/${launch}/exchange`,
        exchangeBody(input),
        { ...PROTOCOL_OPTIONS, auth: false }
      )
    },

    getBootstrap() {
      return client.get('/runtime/bootstrap', PROTOCOL_OPTIONS)
    },

    getSchema() {
      return client.get('/runtime/schema', PROTOCOL_OPTIONS)
    },

    queryList(query) {
      return client.post('/runtime/list/query', query, PROTOCOL_OPTIONS)
    },

    getForm({ mode, recordId } = {}, { signal } = {}) {
      const params = new URLSearchParams({ mode: normalizeFormMode(mode) })
      if (recordId !== undefined && recordId !== null && String(recordId).trim()) {
        params.set('recordId', String(recordId).trim())
      }
      return client.get(`/runtime/form?${params.toString()}`, optionalSignalOptions(signal))
    },

    /** 只读重算当前 Session 固定 CREATE 表单，不接受任何目标坐标。 */
    evaluateCreate(input, { signal } = {}) {
      return client.post(
        '/runtime/form/evaluations',
        normalizeCreateEvaluationBody(input),
        optionalSignalOptions(signal)
      )
    },

    getRecord(recordId, { signal } = {}) {
      const id = normalizeSegment(recordId, 'Embed recordId')
      return client.get(`/runtime/records/${id}`, optionalSignalOptions(signal))
    },

    /** RECORD_CREATE 的目标完全由 Bearer Session 恢复，浏览器只发送 data。 */
    createRecord(input, { idempotencyKey, signal } = {}) {
      return client.post(
        '/runtime/records',
        normalizeCreateBody(input),
        createRecordOptions(idempotencyKey, signal)
      )
    },

    queryFormOptions(fieldCode, query, { signal } = {}) {
      const field = normalizeSegment(fieldCode, 'Embed fieldCode')
      return client.post(
        `/runtime/form/fields/${field}/options/query`,
        query,
        optionalSignalOptions(signal)
      )
    },

    queryFormLookups(fieldCode, query, { signal } = {}) {
      const field = normalizeSegment(fieldCode, 'Embed fieldCode')
      return client.post(
        `/runtime/form/fields/${field}/lookups/query`,
        query,
        optionalSignalOptions(signal)
      )
    },

    getSession() {
      return client.get('/session', PROTOCOL_OPTIONS)
    },

    heartbeat({ visible = true, clientTime = new Date().toISOString() } = {}) {
      return client.post('/session/heartbeat', {
        visible: visible === true,
        clientTime: String(clientTime)
      }, PROTOCOL_OPTIONS)
    },

    logout() {
      return client.delete('/session', { ...PROTOCOL_OPTIONS, keepalive: true })
    }
  })
}
