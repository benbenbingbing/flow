import assert from 'node:assert/strict'
import { getFileUploadIdempotencyKey } from '../file-upload-idempotency.js'

const first = {}
const second = {}
const firstKey = getFileUploadIdempotencyKey(first)

assert.match(firstKey, /^ui-upload-[!-~]{1,118}$/)
assert.equal(getFileUploadIdempotencyKey(first), firstKey)
assert.notEqual(getFileUploadIdempotencyKey(second), firstKey)
assert.throws(
  () => getFileUploadIdempotencyKey(null),
  /上传文件必须是对象/
)

console.log('file upload idempotency tests passed')
