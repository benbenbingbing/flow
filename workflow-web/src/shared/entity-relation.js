export const ENTITY_RELATION_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,99}$/

export function createEntityRelationDraft(sortOrder = 0) {
  return {
    id: '',
    relationCode: '',
    relationName: '',
    dataKey: '',
    childEntityId: '',
    childEntityCode: '',
    childEntityName: '',
    childRefFieldCode: '',
    relationType: 'ONE_TO_MANY',
    ownershipType: 'COMPOSITION',
    cascadeDelete: true,
    required: false,
    sortOrder: Number(sortOrder) || 0,
    enabled: true,
    parentFieldId: null,
    parentFieldCode: null
  }
}

export function normalizeEntityRelation(raw = {}) {
  return {
    ...createEntityRelationDraft(raw.sortOrder),
    ...raw,
    id: raw.id == null ? '' : String(raw.id),
    childEntityId: raw.childEntityId == null
      ? ''
      : String(raw.childEntityId),
    cascadeDelete: raw.cascadeDelete !== false
      && raw.cascadeDelete !== 0
      && raw.cascadeDelete !== '0',
    required: raw.required === true
      || raw.required === 1
      || raw.required === '1',
    enabled: raw.enabled !== false
      && raw.enabled !== 0
      && raw.enabled !== '0',
    ownershipType: raw.ownershipType || 'COMPOSITION',
    relationType: raw.relationType || 'ONE_TO_MANY'
  }
}

export function toEntityRelationSavePayload(draft = {}) {
  const ownershipType = draft.ownershipType || 'COMPOSITION'
  return {
    relationCode: String(draft.relationCode || '').trim(),
    relationName: String(draft.relationName || '').trim(),
    dataKey: String(draft.dataKey || '').trim(),
    childEntityId: String(draft.childEntityId || '').trim(),
    childRefFieldCode: String(draft.childRefFieldCode || '').trim(),
    relationType: draft.relationType || 'ONE_TO_MANY',
    ownershipType,
    cascadeDelete: ownershipType === 'COMPOSITION'
      && draft.cascadeDelete !== false,
    required: draft.required === true,
    sortOrder: Number(draft.sortOrder) || 0,
    enabled: draft.enabled !== false,
    // 旧版关系的数据键可能与承载字段同名。编辑时必须原样带回，
    // 后端才能区分合法的迁移绑定与新建的字段命名冲突。
    parentFieldId: draft.parentFieldId || null,
    parentFieldCode: draft.parentFieldCode || null
  }
}

export function sortEntityRelations(relations = []) {
  return [...relations]
    .map(normalizeEntityRelation)
    .sort((left, right) => {
      const order = Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
      if (order !== 0) return order
      return String(left.relationName || left.relationCode)
        .localeCompare(String(right.relationName || right.relationCode), 'zh-CN')
    })
}
