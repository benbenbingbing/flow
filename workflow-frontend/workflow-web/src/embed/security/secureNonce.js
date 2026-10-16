const BASE64URL_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'

/** 将随机字节直接编码为无 padding Base64URL，不依赖 btoa 的字符串转换。 */
export function encodeBase64Url(bytes) {
  if (!(bytes instanceof Uint8Array)) throw new TypeError('随机字节无效')
  let result = ''
  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index]
    const hasSecond = index + 1 < bytes.length
    const hasThird = index + 2 < bytes.length
    const second = hasSecond ? bytes[index + 1] : 0
    const third = hasThird ? bytes[index + 2] : 0
    result += BASE64URL_ALPHABET[first >>> 2]
    result += BASE64URL_ALPHABET[((first & 0x03) << 4) | (second >>> 4)]
    if (hasSecond) result += BASE64URL_ALPHABET[((second & 0x0f) << 2) | (third >>> 6)]
    if (hasThird) result += BASE64URL_ALPHABET[third & 0x3f]
  }
  return result
}

/** 与后端 Base64 URL decoder 边界一致：无 padding、可解码且至少承载 32 字节。 */
export function isSecureHandshakeNonce(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{43,128}$/.test(value)
    || value.length % 4 === 1) return false
  return Math.floor(value.length * 6 / 8) >= 32
}

/** 只使用 Web Crypto CSPRNG 生成 256-bit nonce；任何能力缺失均由调用层 fail closed。 */
export function generateSecureHandshakeNonce(cryptoRef = globalThis.crypto) {
  if (typeof cryptoRef?.getRandomValues !== 'function') {
    throw new TypeError('Web Crypto getRandomValues 不可用')
  }
  const bytes = new Uint8Array(32)
  cryptoRef.getRandomValues(bytes)
  const nonce = encodeBase64Url(bytes)
  if (nonce.length !== 43 || !isSecureHandshakeNonce(nonce)) {
    throw new TypeError('安全 nonce 生成失败')
  }
  return nonce
}
