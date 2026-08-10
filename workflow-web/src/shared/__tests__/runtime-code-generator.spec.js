import assert from 'node:assert/strict'
import {
  buildFormDraftRuntimeSnapshot,
  buildListDraftRuntimeSnapshot,
  buildRuntimeCodeArtifact,
  selectRuntimeRelease
} from '../runtime-code-generator.js'

const formSnapshot = buildFormDraftRuntimeSnapshot({
  form: {
    id: 'form-1',
    formName: '项目审批表单',
    initConfig: '{"loadMode":"DETAIL"}',
    viewConfig: '{"actionBar":{"customButtons":[{"key":"submit"}]}}'
  },
  nodes: [{
    id: 'node-1',
    nodeKey: 'project_id',
    propsDocument: JSON.stringify({
      label: '项目',
      componentProps: {
        refConfig: { targetEntityCode: 'project' }
      }
    }),
    rulesDocument: JSON.stringify({
      validation: { required: true }
    }),
    dataSourceBindingsDocument: JSON.stringify({
      FIELD_OPTIONS: {
        serviceId: 'source-1',
        operationCode: 'queryOptions'
      }
    }),
    _saving: true
  }],
  eventBindings: [{
    eventCode: 'FORM_SUBMIT',
    targetType: 'OWNER',
    stepsDocument: JSON.stringify([{
      strategy: 'BEFORE',
      serviceId: 'service-1',
      operationCode: 'validateForm'
    }])
  }]
})

assert.equal(formSnapshot.form.initConfig.loadMode, 'DETAIL')
assert.equal(formSnapshot.nodes[0].props.label, '项目')
assert.equal(formSnapshot.nodes[0].rules.validation.required, true)
assert.equal(
  formSnapshot.nodes[0].dataSourceBindings.FIELD_OPTIONS.serviceId,
  'source-1'
)
assert.equal(formSnapshot.nodes[0]._saving, undefined)
assert.equal(formSnapshot.eventBindings[0].steps.length, 1)

const formArtifact = buildRuntimeCodeArtifact({
  configType: 'FORM',
  configLabel: '项目审批表单',
  snapshot: formSnapshot
})
assert.match(formArtifact.code, /<template>/)
assert.match(formArtifact.code, /<script setup>/)
assert.match(formArtifact.code, /FormNodeRenderer/)
assert.match(formArtifact.code, /FORM_BUTTON_CLICK/)
assert.doesNotMatch(formArtifact.code, /as const/)
assert.ok(formArtifact.logicItems.some(item =>
  item.category === '规则' && item.name === '项目'
))
assert.ok(formArtifact.logicItems.some(item =>
  item.category === '数据源'
))
assert.ok(formArtifact.logicItems.some(item =>
  item.category === '关系'
))
assert.ok(formArtifact.logicItems.some(item =>
  item.category === '事件' && item.summary.includes('1 个步骤')
))

const listSnapshot = buildListDraftRuntimeSnapshot({
  list: {
    id: 'list-1',
    listName: '项目列表',
    fixedFilterConfig: '{"status":{"operator":"EQ","value":"ACTIVE"}}',
    contextBindingConfig: '{"projectId":"context.projectId"}',
    selectionMode: 'MULTIPLE',
    selectionValueField: 'id',
    selectionReturnMappingsText:
      '[{"sourceField":"id","targetField":"projectId"}]',
    accessPermissionCode: 'project:view',
    dataScopeMode: 'INHERIT'
  },
  viewConfig: {
    pagination: { pageSize: 20 }
  },
  fields: [{
    fieldCode: 'project_name',
    fieldName: '项目名称',
    showInList: true,
    isQuery: true,
    queryType: 'LIKE',
    dataSourceType: 'ENTITY_FIELD',
    queryConfig: '{"componentType":"input"}'
  }],
  toolbarActions: [{
    buttonKey: 'create',
    buttonLabel: '新增项目',
    permissionCode: 'project:create',
    enabled: true
  }],
  rowActions: [{
    buttonKey: 'view',
    buttonLabel: '查看',
    enabled: true,
    availabilityRuleDocument: '{"expression":"row.status === \\"ACTIVE\\""}'
  }],
  scenes: [{ sceneCode: 'PAGE' }, { sceneCode: 'EMBEDDED' }],
  eventBindings: [{
    eventCode: 'LIST_LOAD',
    steps: [{ strategy: 'REPLACE', serviceId: 'service-2' }]
  }]
})

assert.equal(listSnapshot.list.fixedFilterConfig.status.value, 'ACTIVE')
assert.equal(listSnapshot.list.contextBindingConfig.projectId, 'context.projectId')
assert.equal(listSnapshot.list.selectionConfig.selectionMode, 'MULTIPLE')
assert.equal(listSnapshot.list.selectionMode, undefined)
assert.deepEqual(listSnapshot.list.allowedScenes, ['PAGE', 'EMBEDDED'])

const listArtifact = buildRuntimeCodeArtifact({
  configType: 'LIST',
  configLabel: '项目列表',
  snapshot: listSnapshot
})
assert.match(listArtifact.code, /<template>/)
assert.match(listArtifact.code, /EntityDataSearchForm/)
assert.match(listArtifact.code, /entityListRuntimeApi\.query/)
assert.match(listArtifact.code, /ROW_BUTTON_CLICK/)
assert.doesNotMatch(listArtifact.code, /as const/)
assert.ok(listArtifact.logicItems.some(item =>
  item.category === '查询' && item.name === '固定查询条件'
))
assert.ok(listArtifact.logicItems.some(item =>
  item.category === '查询字段' && item.name === '项目名称'
))
assert.ok(listArtifact.logicItems.some(item =>
  item.category === '动作' && item.name.includes('新增项目')
))
assert.ok(listArtifact.logicItems.some(item =>
  item.category === '事件' && item.name === 'LIST_LOAD'
))

const releases = [
  { id: 'release-1', version: 1, status: 'SUPERSEDED' },
  { id: 'release-2', version: 2, status: 'ACTIVE' },
  { id: 'release-3', version: 3, status: 'INACTIVE' }
]
assert.equal(selectRuntimeRelease(releases)?.id, 'release-2')
assert.equal(selectRuntimeRelease(releases, 'release-3')?.id, 'release-3')

console.log('runtime code generator tests passed')
