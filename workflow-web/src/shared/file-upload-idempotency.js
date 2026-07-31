const uploadKeys = new WeakMap()

function randomUploadKey() {
  if (globalThis.crypto?.randomUUID) {
    return `ui-upload-${globalThis.crypto.randomUUID()}`
  }
  return `ui-upload-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

export function getFileUploadIdempotencyKey(file) {
  if (!file || (typeof file !== 'object' && typeof file !== 'function')) {
    throw new TypeError('上传文件必须是对象')
  }
  let key = uploadKeys.get(file)
  if (!key) {
    key = randomUploadKey()
    uploadKeys.set(file, key)
  }
  return key
}
