import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeAttachmentFiles } from '../src/shared/file-attachment.js'

test('业务文件引用保留原名与分组，旧存储地址不作为附件接受', () => {
  assert.deepEqual(normalizeAttachmentFiles({ 合同: [
    { fileId: 'contract-id', originalName: '项目合同.pdf', size: 123 },
    { url: 's3://files/uuid.pdf', originalName: '旧文件.pdf' },
    's3://files/old.pdf', { name: '尚未上传.pdf', size: 1 }
  ] }), [{ fileId: 'contract-id', name: '项目合同.pdf', size: 123, groupName: '合同' }])
  assert.deepEqual(normalizeAttachmentFiles({ fileId: 'single-id', name: '文件.pdf' }), [
    { fileId: 'single-id', name: '文件.pdf', size: undefined, groupName: '' }
  ])
  assert.deepEqual(normalizeAttachmentFiles('s3://files/single.pdf'), [])
})
