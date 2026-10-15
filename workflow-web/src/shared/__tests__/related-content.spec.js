import assert from 'node:assert/strict'
import {
  RELATED_CONTENT_ACTION_OPTIONS,
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
assert.deepEqual(RELATED_CONTENT_ACTION_OPTIONS.map(option => option.value), ['VIEW', 'CREATE', 'EDIT'])
assert.deepEqual(empty.config.actions, ['VIEW'])
assert.deepEqual(empty.config.actionSettings, { create: { initialMappings: [] } })

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

// 普通引用也可以使用显示属性，保存时不能把独立编辑动作改成随主表提交。
const displayConfigured = normalizeRelatedContent(configured, { ownerType: 'FORM', sourceEntity })
displayConfigured.config.presentation = { ...displayConfigured.config.presentation, title: '项目概览', showTitle: false, showBorder: false }
displayConfigured.config.actions = ['EDIT']
const displayPayload = buildRelatedContentPayload(displayConfigured, 'FORM', 'form-1')
assert.equal(validateRelatedContent(displayConfigured, 'FORM').valid, true)
assert.equal(displayPayload.config.presentation.title, '项目概览')
assert.equal(displayPayload.config.presentation.showTitle, false)
assert.deepEqual(displayPayload.config.actions, ['EDIT'])
const displayReopened = normalizeRelatedContent(displayPayload, { ownerType: 'FORM', sourceEntity })
assert.equal(displayReopened.config.presentation.showTitle, false)
assert.equal(displayReopened.config.presentation.showBorder, false)
displayReopened.config.presentation.showBorder = 'false'
assert.equal(validateRelatedContent(displayReopened, 'FORM').valid, false)
displayReopened.config.presentation.showBorder = true
assert.equal(validateRelatedContent(displayReopened, 'FORM').valid, true)
displayReopened.config.presentation.showTitle = 'false'
assert.equal(validateRelatedContent(displayReopened, 'FORM').valid, false)
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
assert.equal(
  'interfaceService' in payload.config.specialHandling,
  false,
  '普通关系保存时不携带空的接口取数配置'
)
assert.equal('actionServices' in payload.config.specialHandling, false)

configured.config.relation.type = 'INTERFACE_SERVICE'
configured.config.specialHandling.mode = 'INTERFACE_SERVICE'
Object.assign(configured.config.specialHandling.interfaceService, {
  extensionId: 'interface-read-project',
  extensionName: '查询项目'
})
configured.config.specialHandling.actionServices = [{
  actionKey: 'EDIT',
  extensionId: 'interface-update-project',
  extensionName: '更新项目',
  inputMappings: [],
  outputMappings: [],
  failurePolicy: 'ERROR'
}]
const interfacePayload = buildRelatedContentPayload(configured, 'FORM', 'form-1')
assert.equal(interfacePayload.config.specialHandling.interfaceService.extensionId,
  'interface-read-project')
assert.equal(
  'extensionName' in interfacePayload.config.specialHandling.interfaceService,
  false
)
assert.equal(
  'extensionName' in interfacePayload.config.specialHandling.actionServices[0],
  false,
  '动作接口也只能持久化 extensionId 和映射契约'
)

// 同一旧配置改为普通关系后，接口能力必须从模型、校验和保存载荷一起移除；自定义展示继续保留。
const customDisplay = { name: 'project-board', version: 2, artifactDigest: 'a'.repeat(64), props: { compact: true } }
for (const ownerType of ['FORM', 'LIST']) {
  for (const relationType of ['ENTITY_RELATION', 'REFERENCE_FIELD', 'REVERSE_REFERENCE', 'FIELD_MATCH']) {
    const ordinary = structuredClone(interfacePayload)
    ordinary.config.relation = { type: relationType, relationCode: 'projects', sourceField: 'projectId', targetField: 'requirementId' }
    ordinary.config.specialHandling.mode = 'BOTH'
    ordinary.config.specialHandling.customComponent = customDisplay
    ordinary.config.specialHandling.failurePolicy = 'PLACEHOLDER'
    ordinary.config.specialHandling.actionServices[0].actionKey = ''
    const original = structuredClone(ordinary)
    const reopened = normalizeRelatedContent(ordinary, { ownerType })
    assert.equal(reopened.config.specialHandling.mode, 'CUSTOM_COMPONENT')
    assert.equal(reopened.config.specialHandling.interfaceService.extensionId, '')
    assert.deepEqual(reopened.config.specialHandling.actionServices, [])
    assert.equal(validateRelatedContent(ordinary, ownerType).valid, true, '已移除的接口不应继续产生隐藏校验错误')
    const saved = buildRelatedContentPayload(reopened, ownerType, 'owner')
    assert.equal('interfaceService' in saved.config.specialHandling, false)
    assert.equal('actionServices' in saved.config.specialHandling, false)
    assert.equal(saved.config.specialHandling.failurePolicy, 'PLACEHOLDER')
    assert.equal(saved.config.specialHandling.customComponent.name, customDisplay.name)
    assert.equal(saved.config.specialHandling.customComponent.artifactDigest, customDisplay.artifactDigest)
    assert.deepEqual(saved.config.specialHandling.customComponent.props, customDisplay.props)
    assert.deepEqual(normalizeRelatedContent(saved, { ownerType }).config.specialHandling, reopened.config.specialHandling)
    assert.deepEqual(ordinary, original, '归一化不能反向修改来源草稿')
  }
}
const ordinaryServiceOnly = structuredClone(interfacePayload)
ordinaryServiceOnly.config.relation = { type: 'ENTITY_RELATION', relationCode: 'projects' }
assert.equal(normalizeRelatedContent(ordinaryServiceOnly).config.specialHandling.mode, 'NONE')

// 独立扩展页仍可保存并回显取数与动作接口；删除普通关系能力不能误伤共享配置。
for (const relationType of ['SAME_RECORD', 'INTERFACE_SERVICE']) {
  const extensionPage = structuredClone(interfacePayload)
  extensionPage.config.relation = { type: relationType }
  extensionPage.config.specialHandling.interfaceService.inputMappings = [{ source: 'id', target: 'projectId' }]
  extensionPage.config.specialHandling.actionServices[0].outputMappings = [{ source: 'status', target: 'status' }]
  const saved = buildRelatedContentPayload(extensionPage, 'FORM', 'owner')
  const reopened = normalizeRelatedContent(saved)
  assert.equal(validateRelatedContent(reopened, 'FORM').valid, true)
  assert.deepEqual(saved.config.specialHandling, extensionPage.config.specialHandling)
  assert.equal(reopened.config.specialHandling.interfaceService.extensionId, 'interface-read-project')
  assert.equal(reopened.config.specialHandling.actionServices[0].extensionId, 'interface-update-project')
  assert.deepEqual(buildRelatedContentPayload(reopened, 'FORM', 'owner').config.specialHandling, saved.config.specialHandling)
}

const listAnchor = createEmptyRelatedContent({ ownerType: 'LIST', sourceEntity })
assert.equal(listAnchor.anchorType, 'LIST_ACTION')
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
configured.config.specialHandling.interfaceService.extensionId = ''
const serviceValidation = validateRelatedContent(configured, 'FORM')
assert.equal(serviceValidation.valid, false)
assert.ok(serviceValidation.errors.some(error => error.step === 4))

const editable = createEmptyRelatedContent({ ownerType: 'FORM', sourceEntity })
Object.assign(editable.config.target, {
  entityId: targetEntity.id, contentType: 'FORM', contentId: 'project-form'
})
Object.assign(editable.config.relation, { type: 'REFERENCE_FIELD', sourceField: 'projectId' })
editable.config.actions = ['CREATE', 'EDIT']
editable.config.actionSettings.create.initialMappings = [{ source: 'id', target: 'projectId' }]
assert.equal(validateRelatedContent(editable, 'FORM').valid, true)
assert.deepEqual(buildRelatedContentPayload(editable, 'FORM', 'owner').config.actionSettings, {
  create: { initialMappings: [{ source: 'id', target: 'projectId' }] }
})
editable.config.actions = ['UNKNOWN_ACTION']
assert.ok(validateRelatedContent(editable, 'FORM').errors.some(error => error.field === 'actions'))

console.log('related content model tests passed')
