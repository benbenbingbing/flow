import assert from 'node:assert/strict'
import {
  attachmentFileTypesToString,
  getAttachmentItemRequiredState,
  getAttachmentItemValue,
  getMissingRequiredAttachmentItems,
  hasAttachmentValue,
  isAttachmentItemRequired,
  isAttachmentFileTypeAllowed,
  normalizeAttachmentFileTypes,
  resolveAttachmentItems,
  setAttachmentItemValue
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

const renamedItem = {
  itemKey: 'afi_contract',
  itemName: '合同终稿',
  nameAliases: '["合同初稿","合同"]',
  required: '0'
}
const legacyGroupedValue = {
  合同: [{ url: '/uploads/contract.pdf' }],
  其他资料: [{ url: '/uploads/other.pdf' }]
}
assert.deepEqual(
  getAttachmentItemValue(renamedItem, 0, legacyGroupedValue),
  [{ url: '/uploads/contract.pdf' }]
)
assert.deepEqual(
  setAttachmentItemValue(
    legacyGroupedValue,
    renamedItem,
    0,
    [{ url: '/uploads/final-contract.pdf' }]
  ),
  {
    合同终稿: [{ url: '/uploads/final-contract.pdf' }],
    其他资料: [{ url: '/uploads/other.pdf' }]
  }
)
assert.equal(isAttachmentItemRequired(renamedItem), false)
assert.equal(isAttachmentItemRequired({ required: '1' }), true)
assert.equal(hasAttachmentValue(1), false)
assert.deepEqual(
  getMissingRequiredAttachmentItems(
    [{ itemKey: 'afi_contract', itemName: '合同终稿', required: true }],
    { 合同终稿: { name: 'fake.pdf', status: 'success' } }
  ).map(item => item.itemKey),
  ['afi_contract']
)

const conditionalItems = [
  renamedItem,
  { itemKey: 'afi_acceptance', itemName: '验收报告', required: false },
  { itemKey: 'afi_license', itemName: '许可证', required: true }
]
const conditionalRules = {
  version: 1,
  items: [
    {
      itemKey: 'afi_contract',
      requiredConditionConfig: { code: 'contractRequired' }
    },
    {
      itemKey: 'afi_acceptance',
      requiredConditionConfig: { code: 'acceptanceRequired' }
    }
  ]
}
const requiredState = getAttachmentItemRequiredState(
  conditionalItems,
  conditionalRules,
  { stage: 'CONTRACT' },
  config => config.code === 'contractRequired'
)
assert.deepEqual(requiredState, {
  afi_contract: true,
  afi_acceptance: false,
  afi_license: true
})
assert.deepEqual(
  getMissingRequiredAttachmentItems(
    conditionalItems,
    {
      合同: [{ url: '/uploads/contract.pdf' }],
      许可证: []
    },
    requiredState
  ).map(item => item.itemKey),
  ['afi_license']
)

assert.deepEqual(
  resolveAttachmentItems({
    fileItems: [{ itemKey: 'afi_live', itemName: '当前实体项' }],
    componentProps: JSON.stringify({
      fileItems: [{
        itemKey: 'afi_snapshot',
        itemName: '发布快照项',
        nameAliases: '["历史名称"]'
      }]
    })
  }),
  [{
    itemKey: 'afi_snapshot',
    itemName: '发布快照项',
    nameAliases: ['历史名称'],
    storageItemName: '发布快照项'
  }]
)

assert.deepEqual(
  resolveAttachmentItems({
    entityFileItems: [{
      itemKey: 'afi_snapshot',
      itemName: '实体当前名称',
      required: true,
      fileTypes: '.docx',
      maxCount: 9
    }],
    componentProps: JSON.stringify({
      fileItems: [{
        itemKey: 'afi_snapshot',
        itemName: '发布时名称',
        required: false,
        fileTypes: '.pdf',
        maxCount: 2
      }]
    })
  }),
  [{
    itemKey: 'afi_snapshot',
    itemName: '发布时名称',
    nameAliases: ['发布时名称'],
    storageItemName: '实体当前名称',
    required: true,
    fileTypes: '.pdf',
    maxCount: 2
  }]
)

assert.deepEqual(
  setAttachmentItemValue(
    { 发布时名称: [{ url: '/uploads/old.pdf' }] },
    resolveAttachmentItems({
      entityFileItems: [{
        itemKey: 'afi_snapshot',
        itemName: '实体当前名称',
        nameAliases: ['更早名称']
      }],
      componentProps: JSON.stringify({
        fileItems: [{
          itemKey: 'afi_snapshot',
          itemName: '发布时名称'
        }]
      })
    })[0],
    0,
    [{ url: '/uploads/new.pdf' }]
  ),
  { 实体当前名称: [{ url: '/uploads/new.pdf' }] }
)

const renamedSnapshotItem = resolveAttachmentItems({
  entityFileItems: [{
    itemKey: 'afi_snapshot',
    itemName: '实体当前名称'
  }],
  componentProps: JSON.stringify({
    fileItems: [{
      itemKey: 'afi_snapshot',
      itemName: '发布时名称'
    }]
  })
})[0]
assert.deepEqual(
  getAttachmentItemValue(
    renamedSnapshotItem,
    0,
    { 实体当前名称: [{ url: '/uploads/current.pdf' }] }
  ),
  [{ url: '/uploads/current.pdf' }]
)

console.log('file attachment tests passed')
