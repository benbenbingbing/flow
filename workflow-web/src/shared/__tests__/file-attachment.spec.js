import assert from 'node:assert/strict'
import {
  attachmentFileTypesToString,
  getMissingRequiredAttachmentItems,
  isAttachmentFileTypeAllowed,
  normalizeAttachmentFileTypes
} from '../file-attachment.js'

assert.deepEqual(
  normalizeAttachmentFileTypes(['PDF', '.docx', 'dwg, .tar.gz', 'pdf']),
  ['.pdf', '.docx', '.dwg', '.tar.gz']
)
assert.equal(
  attachmentFileTypesToString(['PDF', 'dwg']),
  '.pdf,.dwg'
)
assert.equal(
  isAttachmentFileTypeAllowed({ name: 'archive.TAR.GZ' }, ['.tar.gz']),
  true
)
assert.equal(
  isAttachmentFileTypeAllowed({ name: 'image.png' }, ['.jpg', '.jpeg']),
  false
)
assert.equal(
  getMissingRequiredAttachmentItems(
    [{ itemName: '项目章程', required: true }],
    [{ name: 'charter.pdf', status: 'ready' }]
  ).length,
  1
)

const fileItems = [
  { itemName: '项目章程', required: true },
  { itemName: '需求文档', required: false },
  { itemName: '验收报告', required: 1 }
]
assert.deepEqual(
  getMissingRequiredAttachmentItems(fileItems, {
    项目章程: [{ url: '/uploads/charter.pdf' }],
    需求文档: []
  }).map(item => item.itemName),
  ['验收报告']
)
assert.deepEqual(
  getMissingRequiredAttachmentItems(
    [{ itemName: '项目章程', required: true }],
    ['/uploads/legacy.pdf']
  ),
  []
)
assert.deepEqual(
  getMissingRequiredAttachmentItems(
    [
      { itemName: '', required: false },
      { itemName: '', required: true }
    ],
    { 附件项2: [{ url: '/uploads/second.pdf' }] }
  ),
  []
)

console.log('file attachment tests passed')
