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
    relationType: 'ONE_TO_ONE',
    ownershipType: 'ASSOCIATION',
    cascadeDelete: false,
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

/** 与服务端 EntityRelationFieldPolicy 保持一致；类型按建表规则判断，不使用客户端 dbType。 */
export function isRelationFieldCompatible(field, parentEntityId) {
  if (!field?.fieldCode || field.fieldCode.toLowerCase() === 'id'
    || String(field.dbColumnName || '').toLowerCase() === 'id'
    || String(field.valueStorage || '').toUpperCase() === 'MULTI_TABLE'
    || !['STRING', 'SELECT', 'RADIO', 'REFERENCE'].includes(field.fieldType)) return false
  if (field.refEntityType && field.refEntityType !== 'CUSTOM') return false
  if ((field.fieldType === 'REFERENCE' || field.refEntityId)
    && String(field.refEntityId || '') !== String(parentEntityId || '')) return false
  const length = field.fieldLength == null ? 200 : Number(field.fieldLength)
  return Number.isInteger(length) && length >= 64 && length <= 4096
}

/** 普通字段由关系定义声明目标；保留已有引用目标约束，并优先显示正确的实体引用字段。 */
export function relationReferenceFields(fields = [], parentEntityId) {
  return fields.filter(field => isRelationFieldCompatible(field, parentEntityId))
    .sort((left, right) => (Number(right.fieldType === 'REFERENCE') - Number(left.fieldType === 'REFERENCE'))
      || Number(left.sortOrder || 0) - Number(right.sortOrder || 0))
}

/** 一个关联记录使用表单，多条关联记录使用列表；展示入口不再让用户重复选择基数。 */
export function relationContentType(relation) {
  return relation?.direction === 'REVERSE' || relation?.relationType === 'ONE_TO_ONE' ? 'FORM' : 'LIST'
}

/** 正反向共用关系定义；目标随使用方向确定，不允许页面自行改写外键。 */
export function relationTarget(relation = {}) {
  const prefix = relation.direction === 'REVERSE' ? 'parent' : 'child'
  return { entityId: String(relation[`${prefix}EntityId`] || ''), entityCode: relation[`${prefix}EntityCode`] || '', entityName: relation[`${prefix}EntityName`] || relation[`${prefix}EntityCode`] || '' }
}

export function relationUsageKey(relation = {}) {
  return `${relation.parentEntityId || ''}:${relation.relationCode || ''}:${relation.direction || 'FORWARD'}`
}

export function relationMatchLabel(relation = {}) {
  return relation.direction === 'REVERSE'
    ? `当前记录.${relation.childRefFieldCode} = ${relation.parentEntityCode || '所属记录'}.id`
    : `${relation.childEntityCode || '关联记录'}.${relation.childRefFieldCode} = 当前记录.id`
}

export function isCompositionEditorRelation(relation = {}) {
  return relation?.ownershipType === 'COMPOSITION' && relation?.direction !== 'REVERSE'
}
