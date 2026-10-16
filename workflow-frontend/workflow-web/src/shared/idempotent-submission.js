function canonicalize(value) {
  if (value instanceof Date) return value.toISOString()
  if (Array.isArray(value)) return value.map(canonicalize)
  if (value && typeof value === 'object') {
    return Object.keys(value).sort().reduce((result, key) => {
      result[key] = canonicalize(value[key])
      return result
    }, {})
  }
  return value
}

export function canonicalRequestFingerprint(payload) {
  return JSON.stringify(canonicalize(payload))
}

function defaultKeyFactory() {
  return globalThis.crypto?.randomUUID?.()
    || `idempotent-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/**
 * 为同一 canonical 请求稳定复用幂等键。网络响应丢失后再次提交不会换键；
 * 请求内容改变或调用 clear（例如重新预检）时才进入新的幂等请求周期。
 */
export function createIdempotentSubmissionKeyTracker(
  keyFactory = defaultKeyFactory
) {
  let fingerprint = ''
  let key = ''
  return {
    keyFor(payload) {
      const nextFingerprint = canonicalRequestFingerprint(payload)
      if (!key || nextFingerprint !== fingerprint) {
        fingerprint = nextFingerprint
        key = keyFactory()
      }
      return key
    },
    clear() {
      fingerprint = ''
      key = ''
    },
    peek() {
      return key
    }
  }
}
