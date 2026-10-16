import assert from 'node:assert/strict'
import { uploadWithSignatureConfirmation } from '../config-migration-upload.js'

const file = { name: 'test.wfpack' }
const warning = { confirmationRequired: true, checksum: 'file-checksum', message: '签名不一致' }
const imported = { id: 'import-1', signatureStatus: 'MISMATCH_CONFIRMED' }

// 签名正常时不显示额外确认。
let result = await uploadWithSignatureConfirmation(file, 'DEV', async () => ({ id: 'verified' }),
  async () => assert.fail('签名已通过不应弹窗'))
assert.equal(result.id, 'verified')

// 用户取消后不重试，不把预检提示误当作已导入。
let calls = []
result = await uploadWithSignatureConfirmation(file, 'DEV', async (...args) => { calls.push(args); return warning }, async () => false)
assert.equal(result, null)
assert.equal(calls.length, 1)

// 只有确认后才针对原文件携带后端摘要重试一次。
calls = []
result = await uploadWithSignatureConfirmation(file, 'DEV', async (...args) => {
  calls.push(args)
  return calls.length === 1 ? warning : imported
}, async message => { assert.equal(message, warning.message); return true })
assert.equal(result, imported)
assert.deepEqual(calls, [[file, 'DEV'], [file, 'DEV', warning.checksum]])

// 真正的校验错误不能变成可确认的提示，也不能自动再次上传。
await assert.rejects(uploadWithSignatureConfirmation(file, 'DEV', async () => { throw new Error('文件损坏') },
  async () => assert.fail('损坏的文件不能确认放行')), /文件损坏/)
await assert.rejects(uploadWithSignatureConfirmation(file, 'DEV', async () => warning, async () => true), /仍需确认/)
console.log('config migration signature confirmation passed')
