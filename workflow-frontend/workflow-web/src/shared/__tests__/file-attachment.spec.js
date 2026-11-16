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
} from '@flow/workflow-core/file-attachment'

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
    项目章程: [{ fileId: 'charter' }],
    需求文档: []
  }).map(item => item.itemName),
  ['验收报告']
)
assert.deepEqual(
  getMissingRequiredAttachmentItems(
    [{ itemName: '项目章程', required: true }],
    [{ fileId: 'saved-file' }]
  ),
  []
)
assert.deepEqual(
  getMissingRequiredAttachmentItems(
    [
      { itemName: '', required: false },
      { itemName: '', required: true }
    ],
    { 附件项2: [{ fileId: 'second' }] }
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
  合同: [{ fileId: 'contract' }],
  其他资料: [{ fileId: 'other' }]
}
assert.deepEqual(
  getAttachmentItemValue(renamedItem, 0, legacyGroupedValue),
  [{ fileId: 'contract' }]
)
assert.deepEqual(
  setAttachmentItemValue(
    legacyGroupedValue,
    renamedItem,
    0,
    [{ fileId: 'final-contract' }]
  ),
  {
    合同终稿: [{ fileId: 'final-contract' }],
    其他资料: [{ fileId: 'other' }]
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
      合同: [{ fileId: 'contract' }],
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
    { 发布时名称: [{ fileId: 'old' }] },
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
    [{ fileId: 'new' }]
  ),
  { 实体当前名称: [{ fileId: 'new' }] }
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
    { 实体当前名称: [{ fileId: 'current' }] }
  ),
  [{ fileId: 'current' }]
)

console.log('file attachment tests passed')
