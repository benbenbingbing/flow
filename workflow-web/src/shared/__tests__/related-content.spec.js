import assert from 'node:assert/strict'
import {
  buildRelatedContentPayload,
  createEmptyRelatedContent,
  describeRelatedContent,
  describeRelatedContentRelation,
  normalizeRelatedContent,
  recommendRelatedContentRelation,
  relatedContentPositionOptions,
  updateRelatedContentAnchor,
  validateRelatedContent
} from '../related-content.js'

const sourceEntity = {
  id: 'requirement-entity',
  entityCode: 'requirement',
  entityName: '需求'
}
const targetEntity = {
  id: 'project-entity',
  entityCode: 'project',
  entityName: '项目'
}

const empty = createEmptyRelatedContent({ ownerType: 'FORM', sourceEntity })
assert.equal(empty.anchorType, 'OWNER')
assert.match(empty.compositionKey, /^related_[a-z0-9_]+$/)
assert.equal(empty.config.source.entityName, '需求')
assert.equal(empty.config.presentation.loadMode, 'ON_DEMAND')
assert.deepEqual(empty.config.actions, ['VIEW'])
assert.equal(empty.config.actionSettings.select.mode, 'SINGLE')
assert.equal(empty.config.actionSettings.select.result, 'FILL_FIELDS')

assert.equal(
  relatedContentPositionOptions('FORM').some(item => item.value === 'ROW_EXPAND'),
  false
)
assert.equal(
  relatedContentPositionOptions('LIST').some(item => item.value === 'ROW_EXPAND'),
  true
)
assert.equal(
  relatedContentPositionOptions('LIST').some(item => item.value === 'TAB'),
  false,
  '列表设计不能展示没有真实运行时承载的 Tab 位置'
)

assert.deepEqual(recommendRelatedContentRelation({
  sourceEntity,
  targetEntity: sourceEntity
}), { type: 'SAME_RECORD' })

assert.deepEqual(recommendRelatedContentRelation({
  sourceEntity,
  targetEntity,
  relations: [{
    relationCode: 'requirement_project',
    relationName: '所属项目',
    childEntityId: 'project-entity'
  }],
  sourceFields: [{
    fieldCode: 'projectId',
    fieldName: '所属项目',
    refEntityId: 'project-entity'
  }]
}), {
  type: 'ENTITY_RELATION',
  relationCode: 'requirement_project',
  relationName: '所属项目'
}, '已有实体关系优先于引用字段')

assert.deepEqual(recommendRelatedContentRelation({
  sourceEntity,
  targetEntity,
  sourceFields: [{
    fieldCode: 'projectId',
    fieldName: '所属项目',
    refEntityId: 'project-entity'
  }]
}), {
  type: 'REFERENCE_FIELD',
  sourceField: 'projectId',
  sourceFieldName: '所属项目'
})

const reverse = recommendRelatedContentRelation({
  sourceEntity: targetEntity,
  targetEntity: sourceEntity,
  targetFields: [{
    fieldCode: 'projectId',
    fieldName: '所属项目',
    refEntityId: 'project-entity'
  }]
})
assert.equal(reverse.type, 'REVERSE_REFERENCE')
assert.equal(reverse.targetField, 'projectId')

const configured = normalizeRelatedContent({
  id: 'composition-1',
  revision: 3,
  ownerRevision: 8,
  anchorType: 'OWNER',
  config: {
    name: '所属项目详情',
    source: sourceEntity,
    target: {
      entityId: targetEntity.id,
      entityCode: targetEntity.entityCode,
      entityName: targetEntity.entityName,
      contentType: 'FORM',
      contentId: 'project-form',
      contentName: '项目详情表单'
    },
    presentation: { position: 'DRAWER', loadMode: 'ON_DEMAND' },
    relation: {
      type: 'REFERENCE_FIELD',
      sourceField: 'projectId',
      sourceFieldName: '所属项目'
    },
    actions: ['VIEW']
  }
}, { ownerType: 'FORM', sourceEntity })

assert.equal(validateRelatedContent(configured, 'FORM').valid, true)
assert.equal(
  describeRelatedContent(configured),
  '在需求中，以抽屉方式显示“项目详情表单”。'
)
assert.equal(
  describeRelatedContentRelation(configured),
  '当前记录的“所属项目”用于定位目标数据。'
)

const payload = buildRelatedContentPayload(configured, 'FORM', 'form-1')
assert.equal('ownerType' in payload, false)
assert.equal('ownerId' in payload, false)
assert.equal(payload.expectedRevision, 3)
assert.equal(payload.expectedOwnerRevision, 8)
assert.equal(payload.config.target.contentId, 'project-form')

const listAnchor = createEmptyRelatedContent({ ownerType: 'LIST', sourceEntity })
assert.equal(listAnchor.anchorType, 'ROW_ACTION')
assert.equal(listAnchor.anchorKey, listAnchor.compositionKey)
listAnchor.config.presentation.position = 'ROW_EXPAND'
updateRelatedContentAnchor(listAnchor, 'LIST')
assert.equal(listAnchor.anchorType, 'ROW_EXPAND')
assert.equal(listAnchor.anchorKey, 'ROW')

const formAnchor = createEmptyRelatedContent({ ownerType: 'FORM', sourceEntity })
formAnchor.config.presentation.position = 'INLINE'
formAnchor.anchorKey = 'existing-form-node'
updateRelatedContentAnchor(formAnchor, 'FORM')
assert.equal(formAnchor.anchorType, 'FORM_NODE')
formAnchor.config.presentation.position = 'DRAWER'
updateRelatedContentAnchor(formAnchor, 'FORM')
assert.equal(formAnchor.anchorType, 'OWNER')
assert.equal(formAnchor.anchorKey, '')

const formTab = createEmptyRelatedContent({ ownerType: 'FORM', sourceEntity })
Object.assign(formTab.config.target, {
  entityId: targetEntity.id,
  contentType: 'LIST',
  contentId: 'project-list'
})
Object.assign(formTab.config.relation, {
  type: 'REFERENCE_FIELD',
  sourceField: 'projectId'
})
formTab.config.presentation.position = 'TAB'
updateRelatedContentAnchor(formTab, 'FORM')
assert.equal(formTab.anchorType, 'FORM_NODE')
assert.ok(validateRelatedContent(formTab, 'FORM').errors.some(error =>
  error.field === 'anchorKey'))
formTab.anchorKey = 'tab-project'
assert.equal(validateRelatedContent(formTab, 'FORM').valid, true)

const invalid = createEmptyRelatedContent({ ownerType: 'FORM', sourceEntity })
const invalidResult = validateRelatedContent(invalid, 'FORM')
assert.equal(invalidResult.valid, false)
assert.equal(invalidResult.firstStep, 1)
assert.ok(invalidResult.errors.some(error => error.field === 'target.entityId'))

configured.config.relation.type = 'INTERFACE_SERVICE'
configured.config.specialHandling.mode = 'INTERFACE_SERVICE'
configured.config.specialHandling.interfaceService.serviceId = ''
configured.config.specialHandling.interfaceService.operationCode = ''
const serviceValidation = validateRelatedContent(configured, 'FORM')
assert.equal(serviceValidation.valid, false)
assert.ok(serviceValidation.errors.some(error => error.step === 4))

const selectable = createEmptyRelatedContent({ ownerType: 'FORM', sourceEntity })
Object.assign(selectable.config.target, {
  entityId: targetEntity.id,
  contentType: 'LIST',
  contentId: 'project-list'
})
Object.assign(selectable.config.relation, {
  type: 'REFERENCE_FIELD',
  sourceField: 'projectId'
})
selectable.config.actions = ['SELECT']
let selectionValidation = validateRelatedContent(selectable, 'FORM')
assert.equal(selectionValidation.valid, false)
assert.ok(selectionValidation.errors.some(error =>
  error.field === 'actionSettings.select.mappings'))
selectable.config.actionSettings.select.mappings = [{
  source: 'managerId',
  target: 'projectManagerId'
}]
selectionValidation = validateRelatedContent(selectable, 'FORM')
assert.equal(selectionValidation.valid, true)

selectable.config.actions = ['SAVE_WITH_FORM', 'EDIT']
selectable.config.target.contentType = 'FORM'
Object.assign(selectable.config.relation, {
  type: 'ENTITY_RELATION',
  relationCode: 'project_requirements'
})
const mixedSaveBoundary = validateRelatedContent(selectable, 'FORM')
assert.ok(mixedSaveBoundary.errors.some(error =>
  error.message.includes('子表单或重复器')))

selectable.config.actions = ['CREATE']
selectable.config.actionSettings.create.associateAfterCreate = true
const unsafeCreateAndLink = validateRelatedContent(selectable, 'FORM')
assert.ok(unsafeCreateAndLink.errors.some(error =>
  error.message.includes('同一事务')))

console.log('related content model tests passed')
