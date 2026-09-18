import { createEmptyRelatedContent, normalizeRelatedContent } from './related-content.js'
import { relationContentType } from './entity-relation.js'

/** 只列已发布目标，避免把启用状态误当作发布状态，也不允许表单直接嵌入自身。 */
export function relationContentOptions(rows, relation, ownerFormId = '') {
  const type = relationContentType(relation)
  return (Array.isArray(rows) ? rows : []).filter(row => {
    const published = row.activeReleaseId || Number(row.publishedVersion) > 0
      || ['PUBLISHED', 'ACTIVE'].includes(String(row.status || '').toUpperCase())
    return published && (type !== 'FORM' || String(row.id) !== String(ownerFormId))
      && (!row.entityId || String(row.entityId) === String(relation.childEntityId))
  }).map(row => ({
    id: String(row.id),
    name: (type === 'FORM' ? row.formName : row.configName || row.listName) || row.name || String(row.id),
    key: (type === 'FORM' ? row.formKey : row.listKey || row.configKey) || '',
    releaseId: row.activeReleaseId || ''
  }))
}

/** 普通关系使用单一嵌入展示；高级位置、字段映射和写入行为交给原关联内容编辑器维护。 */
export function isSimpleRelationContent(item) {
  return item?.anchorType === 'OWNER'
    && item?.config?.presentation?.position === 'INLINE'
    && item?.config?.relation?.type === 'ENTITY_RELATION'
    && item?.config?.specialHandling?.mode === 'NONE'
    && item?.config?.actions?.length === 1
    && item.config.actions[0] === 'VIEW'
}

/**
 * 重新读取实体关系后同步展示配置中的派生信息；关系编码始终是唯一的关联依据。
 * 关系失效时禁止降级为字段匹配。目标或基数变化时清除旧页面，要求重新选择，
 * 其余展示位置、操作权限和修订信息保留，避免编辑展示时重置已有配置。
 */
export function applyEntityRelationBinding(item, relation) {
  if (!relation || relation.deleted === 1 || relation.deleted === true) {
    throw new Error('此实体关系已不存在，请到实体设计中检查')
  }
  if (relation.enabled === false || relation.enabled === 0) throw new Error('此实体关系已停用，请到实体设计中检查')
  if (!relation.relationCode || !relation.childEntityId || !relation.childRefFieldCode) {
    throw new Error('实体关系定义不完整，请到实体设计中检查')
  }
  if (item.config.relation.type !== 'ENTITY_RELATION'
    || item.config.relation.relationCode !== relation.relationCode) {
    throw new Error('展示配置不能替换已绑定的实体关系')
  }
  const target = item.config.target
  const contentType = relationContentType(relation)
  const sameTarget = String(target.entityId) === String(relation.childEntityId)
    && target.contentType === contentType
  item.config.target = {
    ...(sameTarget ? target : { contentId: '', contentKey: '', contentName: '' }),
    entityId: String(relation.childEntityId),
    entityCode: relation.childEntityCode || '',
    entityName: relation.childEntityName || relation.childEntityCode || '',
    contentType
  }
  // 不复制外键、映射和筛选条件；服务端根据编码从实体发布版本解析真实规则。
  item.config.relation = {
    type: 'ENTITY_RELATION',
    relationCode: relation.relationCode,
    relationName: relation.relationName || relation.relationCode
  }
  return item
}

/**
 * 从权威关系构造展示配置，不创建实体字段，也不把 dataKey 当作外键。
 * 简单更新保留组合身份和修订号，发布时仍由服务端冻结目标表单/列表版本。
 */
export function buildRelationContent({ relation, content, sourceEntity, existing, orderKey }) {
  if (!relation?.relationCode || !relation.childEntityId || !relation.childRefFieldCode) {
    throw new Error('关联定义不完整，请返回实体设计检查关联字段')
  }
  if (relation.enabled === false || relation.enabled === 0) throw new Error('此关系已停用')
  if (!content?.id) throw new Error('请选择已发布的表单或列表')
  const item = existing
    ? normalizeRelatedContent(existing, { ownerType: 'FORM', sourceEntity })
    : createEmptyRelatedContent({ ownerType: 'FORM', sourceEntity })
  item.anchorType = 'OWNER'
  item.anchorKey = ''
  if (!existing) item.orderKey = orderKey || 1000000
  item.config.name = existing?.config?.name || relation.relationName || relation.childEntityName || content.name
  item.config.target = {
    entityId: String(relation.childEntityId),
    entityCode: relation.childEntityCode || '',
    entityName: relation.childEntityName || '',
    contentType: relationContentType(relation),
    contentId: String(content.id),
    contentKey: content.key || '',
    contentName: content.name || ''
  }
  item.config.relation = {
    type: 'ENTITY_RELATION',
    relationCode: relation.relationCode,
    relationName: relation.relationName || ''
  }
  if (!existing) item.config.presentation = { position: 'INLINE', loadMode: 'IMMEDIATE' }
  item.config.actions = ['VIEW']
  return item
}
