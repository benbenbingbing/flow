import assert from 'node:assert/strict'
import { compileTemplate, parse } from '@vue/compiler-sfc'
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
    dataSourceBindingsDocument: JSON.stringify({
      FORM_INIT: {
        serviceId: 'form-source-1',
        operationCode: 'initializeForm'
      }
    }),
    viewConfig: '{"actionBar":{"customButtons":[{"key":"submit","placement":"FOOTER"},{"key":"inline_review","placement":"ACTION_SLOT","slotKey":"record_actions"}]}}'
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
  }, {
    id: 'node-actions',
    nodeKey: 'record_actions',
    nodeType: 'ACTION_SLOT',
    propsDocument: JSON.stringify({
      label: '记录操作',
      gridSpan: 8
    })
  }],
  eventBindings: [{
    eventCode: 'FORM_SUBMIT',
    targetType: 'OWNER',
    stepsDocument: JSON.stringify([{
      strategy: 'BEFORE',
      serviceId: 'service-1',
      operationCode: 'validateForm',
      executableSnapshot: JSON.stringify({
        configDocument: { apiToken: 'server-secret' }
      }),
      definitionHash: 'pinned-hash'
    }])
  }]
})

assert.equal(
  formSnapshot.form.dataSourceBindings.FORM_INIT.serviceId,
  'form-source-1'
)
assert.equal(formSnapshot.nodes[0].props.label, '项目')
assert.equal(formSnapshot.nodes[0].rules.validation.required, true)
assert.equal(
  formSnapshot.nodes[0].dataSourceBindings.FIELD_OPTIONS.serviceId,
  'source-1'
)
assert.equal(formSnapshot.nodes[0]._saving, undefined)
assert.equal(formSnapshot.eventBindings[0].steps.length, 1)
assert.equal(
  formSnapshot.eventBindings[0].steps[0].executableSnapshot,
  undefined,
  '服务端钉版可执行文档不得进入前端等价代码制品'
)

const formArtifact = buildRuntimeCodeArtifact({
  configType: 'FORM',
  configLabel: '项目审批表单',
  snapshot: formSnapshot
})
assert.match(formArtifact.code, /<template>/)
assert.match(formArtifact.code, /<script setup>/)
assert.match(formArtifact.code, /FormNodeRenderer/)
assert.match(formArtifact.code, /#\[`action-\$\{slotKey\}`\]/)
assert.match(formArtifact.code, /:actions="slotActions\(slotKey\)"/)
assert.match(formArtifact.code, /:actions="footerActions"/)
assert.doesNotMatch(formArtifact.code, /:actions="formActions"/)
assert.match(formArtifact.code, /String\(action\.placement \|\| ''\).*ACTION_SLOT/s)
assert.match(formArtifact.code, /FORM_BUTTON_CLICK/)
const parsedFormArtifact = parse(formArtifact.code, {
  filename: 'GeneratedFormRuntime.vue'
})
assert.deepEqual(parsedFormArtifact.errors, [])
const compiledFormTemplate = compileTemplate({
  id: 'generated-form-runtime',
  filename: 'GeneratedFormRuntime.vue',
  source: parsedFormArtifact.descriptor.template.content
})
assert.deepEqual(compiledFormTemplate.errors, [])
assert.match(
  formArtifact.code,
  /const targetKey = action\.key \|\| action\.buttonKey/
)
assert.match(
  formArtifact.code,
  /const loadingKey = action\.runtimeKey \|\| targetKey/
)
assert.match(formArtifact.code, /function createFormActionRequestId\(\)/)
assert.match(formArtifact.code, /const requestId = createFormActionRequestId\(\)/)
assert.match(formArtifact.code, /\n\s+requestId,/)
assert.match(formArtifact.code, /targetKey: String\(targetKey\)/)
assert.match(
  formArtifact.code,
  /const recordId = computed\(\(\) => String\(props\.recordId \|\| formData\.id/
)
assert.match(
  formArtifact.code,
  /const mode = computed\(\(\) => props\.mode \|\| \(recordId\.value \? 'edit' : 'create'\)\)/
)
assert.match(formArtifact.code, /recordId: recordId\.value \|\| undefined/)
assert.match(formArtifact.code, /taskId: props\.taskId \|\| undefined/)
assert.match(formArtifact.code, /releaseId: props\.releaseId \|\| formDefinition\.runtimeReleaseId/)
assert.match(formArtifact.code, /releaseVersion: props\.releaseVersion/)
assert.match(formArtifact.code, /releaseResolutionToken:/)
assert.match(formArtifact.code, /props\.releaseResolutionToken/)
assert.match(formArtifact.code, /input: \{\s+mode: mode\.value,/)
assert.equal(formArtifact.code.includes('button: action'), false)
assert.doesNotMatch(
  formArtifact.code,
  /input: \{\s+mode: mode\.value,\s+recordId:/
)
assert.match(formArtifact.code, /context: \{\}/)
assert.equal(formArtifact.code.includes('context: runtimeContext.value'), false)
assert.match(formArtifact.code, /if \(action\.type === 'built-in'\) return/)
assert.doesNotMatch(formArtifact.code, /targetKey: String\(actionKey\)/)
assert.doesNotMatch(formArtifact.code, /as const/)
assert.doesNotMatch(formArtifact.json, /server-secret|configDocument/)
assert.ok(formArtifact.logicItems.some(item =>
  item.category === '规则' && item.name === '项目'
))
assert.ok(formArtifact.logicItems.some(item =>
  item.category === '数据源' && item.name === '初始化与数据处理'
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
assert.match(listArtifact.code, /selectedIds: selectedRows\.value\.map/)
assert.match(listArtifact.code, /releaseResolutionToken:/)
assert.doesNotMatch(listArtifact.code, /input: \{ button, row, selectedRows:/)
assert.match(
  listArtifact.code,
  /type="selection"\s+width="50"\s+fixed="left"/
)
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
