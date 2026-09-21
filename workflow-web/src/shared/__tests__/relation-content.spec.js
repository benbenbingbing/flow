import assert from 'node:assert/strict'
import { applyEntityRelationBinding, restoreRelationEditorMetadata, buildRelationEditor, buildRelationContent, isSimpleRelationContent, relationContentOptions } from '../relation-content.js'
import { buildFormNodePayload } from '@flow/workflow-core/form-node-property-schema'
import { buildRelatedContentPayload, validateRelatedContent } from '../related-content.js'

const relation = {
  relationCode: 'reqRelation', relationName: '关联需求', dataKey: 'reqRelation',
  childEntityId: 'req', childEntityCode: 'ZDWREQ', childEntityName: '需求管理',
  childRefFieldCode: 'zdw_all_id', relationType: 'ONE_TO_ONE', enabled: true
}
const sourceEntity = { id: 'all', entityCode: 'ALL', entityName: '全流程验收' }
const options = relationContentOptions([
  { id: 1, formName: '草稿', status: 1 },
  { id: 2, entityId: 'req', formName: '需求详情', formKey: 'details', activeReleaseId: 'r2' },
  { id: 3, entityId: 'wrong', formName: '其他实体', activeReleaseId: 'r3' },
  { id: 4, entityId: 'req', formName: '宿主', activeReleaseId: 'r4' }
], relation, '4')
assert.deepEqual(options.map(option => option.id), ['2'])

const form = buildRelationContent({ relation, content: options[0], sourceEntity })
assert.equal(form.config.target.contentType, 'FORM')
assert.equal(form.config.relation.type, 'ENTITY_RELATION')
assert.equal(form.config.relation.relationCode, 'reqRelation')
assert.equal(form.config.presentation.position, 'INLINE')
assert.equal(form.config.presentation.loadMode, 'IMMEDIATE')
assert.equal(form.anchorType, 'OWNER')
assert.equal(form.anchorKey, '')
assert.deepEqual(form.config.actions, ['VIEW'])
assert.equal(isSimpleRelationContent(form), true)
assert.equal(validateRelatedContent(form, 'FORM').valid, true)

// 保存再打开仍引用同一关系，不要求源实体/目标实体有 reqRelation 字段。
const saved = { ...JSON.parse(JSON.stringify(form)), id: 'c1', revision: 3, ownerRevision: 9 }
saved.config.name = '自定义需求区'
saved.config.presentation.emptyText = '暂未关联需求'
const updated = buildRelationContent({ relation, sourceEntity, content: { id: 'other-form', name: '新版表单' }, existing: saved })
const payload = buildRelatedContentPayload(updated, 'FORM', 'owner-form')
assert.equal(payload.compositionKey, form.compositionKey)
assert.equal(payload.expectedRevision, 3)
assert.equal(payload.expectedOwnerRevision, 9)
assert.equal(payload.config.target.contentId, 'other-form')
assert.equal(payload.config.name, '自定义需求区')
assert.equal(payload.config.presentation.emptyText, '暂未关联需求')
assert.equal(payload.config.target.releaseId, undefined, '更换页面不能沿用旧目标发布身份')

const list = buildRelationContent({ relation: { ...relation, relationType: 'ONE_TO_MANY' }, sourceEntity, content: { id: 'list', key: 'requirements', name: '需求列表' } })
assert.equal(list.config.target.contentType, 'LIST')
assert.equal(list.config.target.contentKey, 'requirements')
assert.equal(validateRelatedContent(list, 'FORM').valid, true)
assert.equal(isSimpleRelationContent({ ...form, config: { ...form.config, actions: ['EDIT'] } }), false)
assert.throws(() => buildRelationContent({ relation: { ...relation, enabled: false }, content: options[0], sourceEntity }), /停用/)
assert.throws(() => buildRelationContent({ relation, content: null, sourceEntity }), /请选择/)
assert.throws(() => buildRelationContent({ relation: { ...relation, childRefFieldCode: '' }, content: options[0], sourceEntity }), /关联定义不完整/)

// 已有高级展示只刷新来自关系的派生身份，不能丢失位置、权限或复用旧映射条件。
const advanced = JSON.parse(JSON.stringify(saved))
advanced.config.presentation.position = 'DRAWER'
advanced.config.actions = ['EDIT']
advanced.config.relation.targetField = 'obsolete_fk'
advanced.config.relation.mappings = [{ sourceField: 'code', targetField: 'reqCode' }]
applyEntityRelationBinding(advanced, { ...relation, relationName: '已更新的实体关系' })
assert.equal(advanced.config.target.contentId, saved.config.target.contentId)
assert.equal(advanced.config.relation.relationName, '已更新的实体关系')
assert.equal(advanced.config.presentation.position, 'DRAWER')
assert.deepEqual(advanced.config.actions, ['EDIT'])
assert.equal(advanced.ownerRevision, saved.ownerRevision)
assert.deepEqual(advanced.config.relation, { type: 'ENTITY_RELATION', relationCode: 'reqRelation', relationName: '已更新的实体关系' })
assert.throws(() => applyEntityRelationBinding(advanced, null), /已不存在/)
assert.throws(() => applyEntityRelationBinding(advanced, { ...relation, enabled: false }), /已停用/)
assert.throws(() => applyEntityRelationBinding(advanced, { ...relation, deleted: 1 }), /已不存在/)
assert.throws(() => applyEntityRelationBinding(advanced, { ...relation, relationCode: 'other' }), /不能替换/)
assert.throws(() => applyEntityRelationBinding(advanced, { ...relation, childRefFieldCode: '' }), /不完整/)

// 关系从单条改成多条或切换实体后，不能沿用旧的表单与发布版本。
advanced.config.target.releaseId = 'old-form-release'
applyEntityRelationBinding(advanced, { ...relation, relationType: 'ONE_TO_MANY' })
assert.equal(advanced.config.target.contentType, 'LIST')
assert.equal(advanced.config.target.contentId, '')
assert.equal(advanced.config.target.releaseId, undefined)
advanced.config.target.contentId = 'req-list'
applyEntityRelationBinding(advanced, { ...relation, relationType: 'ONE_TO_MANY', childEntityId: 'other' })
assert.equal(advanced.config.target.entityId, 'other')
assert.equal(advanced.config.target.contentId, '')
console.log('relation-content tests passed: cardinality, published targets, direct display, inherited binding, stable edits, invalid relations')

// 反向展示复用所属实体上的定义；同名关系也不能忽略方向混用。
const reverse = { ...relation, parentEntityId: 'all', parentEntityCode: 'ALL', parentEntityName: '验收', direction: 'REVERSE', relationType: 'ONE_TO_MANY' }
const parentContent = buildRelationContent({ relation: reverse, content: { id: 'parent-form' }, sourceEntity: { id: 'req' } })
assert.equal(parentContent.config.target.entityId, 'all')
assert.equal(parentContent.config.target.contentType, 'FORM')
assert.equal(buildRelatedContentPayload(parentContent, 'FORM', 'req-form').config.relation.direction, 'REVERSE')
assert.throws(() => applyEntityRelationBinding(parentContent, { ...reverse, direction: 'FORWARD' }), /不能替换/)
const composition = { ...relation, ownershipType: 'COMPOSITION' }
for (const [type, nodeType] of [['ONE_TO_ONE', 'SUB_FORM'], ['ONE_TO_MANY', 'REPEATER']]) {
  const node = buildRelationEditor({ relation: { ...composition, relationType: type }, content: { id: 'child-form' }, release: { id: 'release', version: 3 } })
  assert.equal(node.nodeType, nodeType)
  assert.equal(node.bindingType, 'RELATION')
  assert.equal(node.bindingRef, relation.relationCode)
  assert.equal(node.fieldCode, relation.dataKey)
  assert.equal(node.childFormReleaseId, 'release')
  assert.equal(node.childRefFieldCode, relation.childRefFieldCode)
  assert.equal(node.fieldId, undefined, '主从组件无需实体展示字段')
}
assert.throws(() => buildRelationEditor({ relation: { ...relation, ownershipType: 'ASSOCIATION' } }), /组成关系/)
assert.throws(() => buildRelationEditor({ relation: { ...composition, direction: 'REVERSE' } }), /组成关系/)
assert.throws(() => buildRelationEditor({ relation: composition, content: { id: 'child-form' }, release: null }), /已发布/)

// 无实体字段的节点保存后重开，基数、外键和发布身份仍保持不变。
for (const relationType of ['ONE_TO_ONE', 'ONE_TO_MANY']) {
  const node = buildRelationEditor({ relation: { ...composition, relationType }, content: { id: 'child-form' }, release: { id: 'release', version: 3 } })
  const subFormConfig = { relationCode: node.bindingRef, dataKey: node.fieldCode, childEntityId: node.childEntityId, relationType, childRefFieldCode: node.childRefFieldCode }
  const payload = buildFormNodePayload(node, { componentProps: { subFormConfig } })
  assert.equal(payload.bindingType, 'RELATION')
  assert.equal(payload.childFormReleaseVersion, 3)
  const reopened = restoreRelationEditorMetadata({ nodeType: payload.nodeType, bindingRef: payload.bindingRef }, payload.props.componentProps.subFormConfig)
  assert.equal(reopened.relationType, relationType)
  assert.equal(reopened.childRefFieldCode, relation.childRefFieldCode)
  assert.equal(reopened.childEntityId, relation.childEntityId)
  assert.equal(reopened.repeatable, relationType === 'ONE_TO_MANY')
}

// 快捷入口与设置面板只改变锚点；设计态和运行时必须按同一节点定位。
const { formRelatedContentsAt } = await import('../form-related-content.js')
const atNode = (id, anchorKey, orderKey = 0, overrides = {}) => ({
  id, anchorType: 'FORM_NODE', anchorKey, orderKey,
  config: { enabled: true, presentation: { position: 'INLINE', loadMode: 'ON_DEMAND' }, ...overrides }
})
const placements = [
  atNode('by-id', 'field-id', 2), atNode('by-key', 'field-key', 1),
  atNode('other', 'other-key'), atNode('disabled', 'field-id', 0, { enabled: false }),
  atNode('popup', 'field-id', 3, { presentation: { position: 'DIALOG' } }),
  { ...atNode('owner', ''), anchorType: 'OWNER' },
  atNode('tab', 'tab-key', 0, { presentation: { position: 'TAB' } })
]
const anchor = { id: 'field-id', nodeKey: 'field-key' }
assert.deepEqual(formRelatedContentsAt(placements, anchor, { preview: true }).map(x => x.id), ['by-key', 'by-id'])
assert.deepEqual(formRelatedContentsAt(placements, anchor).map(x => x.id), ['by-key', 'by-id', 'popup'])
assert.deepEqual(formRelatedContentsAt(placements, null, { preview: true }).map(x => x.id), ['owner'])
assert.deepEqual(formRelatedContentsAt(placements, { nodeKey: 'tab-key' }, { preview: true }).map(x => x.id), ['tab'])
assert.deepEqual(formRelatedContentsAt(placements, { id: 'missing' }, { preview: true }), [])
assert.equal(placements[0].id, 'by-id', '定位时不能修改原配置顺序')
console.log('form related content placement tests passed')
