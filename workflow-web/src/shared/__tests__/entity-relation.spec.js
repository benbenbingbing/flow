import assert from 'node:assert/strict'
import {
  ENTITY_RELATION_CODE_PATTERN,
  createEntityRelationDraft,
  normalizeEntityRelation,
  sortEntityRelations,
  toEntityRelationSavePayload
} from '../entity-relation.js'

assert.equal(ENTITY_RELATION_CODE_PATTERN.test('orderItems_2'), true)
assert.equal(ENTITY_RELATION_CODE_PATTERN.test('2_orderItems'), false)
assert.equal(ENTITY_RELATION_CODE_PATTERN.test('订单明细'), false)

const draft = createEntityRelationDraft(20)
assert.equal(draft.relationType, 'ONE_TO_MANY')
assert.equal(draft.ownershipType, 'COMPOSITION')
assert.equal(draft.cascadeDelete, true)
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

assert.deepEqual(
  sortEntityRelations([
    { relationName: '乙', sortOrder: 20 },
    { relationName: '甲', sortOrder: 10 }
  ]).map(item => item.relationName),
  ['甲', '乙']
)

console.log('entity-relation tests passed')
