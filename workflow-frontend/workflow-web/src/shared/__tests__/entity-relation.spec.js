import assert from 'node:assert/strict'
import {
  ENTITY_RELATION_CODE_PATTERN,
  createEntityRelationDraft,
  normalizeEntityRelation,
  isRelationFieldCompatible,
  relationReferenceFields,
  relationContentType,
  sortEntityRelations,
  toEntityRelationSavePayload
} from '../entity-relation.js'

assert.equal(ENTITY_RELATION_CODE_PATTERN.test('orderItems_2'), true)
assert.equal(ENTITY_RELATION_CODE_PATTERN.test('2_orderItems'), false)
assert.equal(ENTITY_RELATION_CODE_PATTERN.test('订单明细'), false)

const draft = createEntityRelationDraft(20)
assert.equal(draft.relationType, 'ONE_TO_ONE')
assert.equal(draft.ownershipType, 'ASSOCIATION')
assert.equal(draft.cascadeDelete, false)
assert.equal(draft.sortOrder, 20)

const normalized = normalizeEntityRelation({
  id: 7,
  childEntityId: 8,
  required: 1,
  enabled: 0,
  cascadeDelete: false
})
assert.equal(normalized.id, '7')
assert.equal(normalized.childEntityId, '8')
assert.equal(normalized.required, true)
assert.equal(normalized.enabled, false)
assert.equal(normalized.cascadeDelete, false)

const payload = toEntityRelationSavePayload({
  relationCode: ' orderItems ',
  relationName: ' 订单明细 ',
  dataKey: ' items ',
  childEntityId: 8,
  childRefFieldCode: ' orderId ',
  ownershipType: 'ASSOCIATION',
  cascadeDelete: true,
  parentFieldId: 'legacy-field-id',
  parentFieldCode: 'items'
})
assert.equal(payload.relationCode, 'orderItems')
assert.equal(payload.relationName, '订单明细')
assert.equal(payload.childEntityId, '8')
assert.equal(payload.childRefFieldCode, 'orderId')
assert.equal(payload.cascadeDelete, false)
assert.equal(payload.parentFieldId, 'legacy-field-id')
assert.equal(payload.parentFieldCode, 'items')

assert.deepEqual(relationReferenceFields([
  { fieldCode: 'plainId', fieldType: 'STRING', fieldLength: 128 },
  { fieldCode: 'wrongType', fieldType: 'LONG', dbType: 'VARCHAR(200)' },
  { fieldCode: 'wrongTarget', fieldType: 'REFERENCE', refEntityId: 'other' },
  { fieldCode: 'zdw_all_id', fieldType: 'REFERENCE', refEntityId: 'parent' },
  { fieldCode: 'multi', fieldType: 'MULTI_REFERENCE', refEntityId: 'parent' }
], 'parent').map(field => field.fieldCode), ['zdw_all_id', 'plainId'])
for (const fieldType of ['STRING', 'SELECT', 'RADIO', 'REFERENCE']) {
  for (const fieldLength of [undefined, 64, 200, 4096]) {
    assert.equal(isRelationFieldCompatible({ fieldCode: 'parentId', fieldType,
      refEntityId: fieldType === 'REFERENCE' ? 'parent' : null, fieldLength }, 'parent'), true)
  }
}
for (const fieldType of ['INTEGER', 'LONG', 'DECIMAL', 'DATE', 'BOOLEAN', 'TEXT', 'RICH_TEXT',
  'USER', 'DEPT', 'MULTI_SELECT', 'MULTI_REFERENCE', 'SUB_FORM', 'SUB_LIST', 'FILE']) {
  assert.equal(isRelationFieldCompatible({ fieldCode: 'parentId', fieldType, dbType: 'VARCHAR(200)' }, 'parent'), false)
}
for (const overrides of [{ fieldLength: 63 }, { fieldLength: 4097 }, { fieldLength: 0 },
  { refEntityId: 'other' }, { refEntityType: 'USER' }, { valueStorage: 'MULTI_TABLE' },
  { fieldCode: 'id' }, { dbColumnName: 'id' }]) {
  assert.equal(isRelationFieldCompatible({ fieldCode: 'parentId', fieldType: 'STRING', ...overrides }, 'parent'), false)
}
assert.equal(relationContentType({ relationType: 'ONE_TO_ONE' }), 'FORM')
assert.equal(relationContentType({ relationType: 'ONE_TO_MANY' }), 'LIST')
assert.equal(toEntityRelationSavePayload(createEntityRelationDraft()).relationCode, '')
assert.equal(toEntityRelationSavePayload(createEntityRelationDraft()).dataKey, '')
// 历史缺省仍按旧版组成关系和一对多解释，不能因为新建默认值改变历史行为。
assert.equal(normalizeEntityRelation({}).ownershipType, 'COMPOSITION')
assert.equal(normalizeEntityRelation({}).relationType, 'ONE_TO_MANY')

assert.deepEqual(
  sortEntityRelations([
    { relationName: '乙', sortOrder: 20 },
    { relationName: '甲', sortOrder: 10 }
  ]).map(item => item.relationName),
  ['甲', '乙']
)

console.log('entity-relation tests passed')
