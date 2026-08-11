import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const testDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(testDir, '..')
const repositoryDir = path.resolve(projectDir, '../../..')
const indexSource = readFileSync(
  path.join(projectDir, 'index.js'),
  'utf8'
)
const bootstrapSource = readFileSync(
  path.join(
    repositoryDir,
    'workflow-server/workflow-project/tools/'
      + 'real-project-extension-acceptance.mjs'
  ),
  'utf8'
)

const expectedRegistrations = [
  'registerCustomFormComponent',
  'registerCustomListComponent',
  'registerFormFieldComponent',
  'registerFormNodeComponent',
  'registerCellComponent',
  'registerListButtonComponent',
  'registerListToolbarAction',
  'registerListRowAction',
  'registerFormInitializer',
  'registerEntityActionRuleCondition',
  'registerEntityPermissionOptionProvider'
]

expectedRegistrations.forEach(name => {
  assert.ok(
    indexSource.includes(name),
    `项目扩展入口缺少 ${name}`
  )
})

const expectedComponents = [
  'forms/ProjectExtensionAcceptanceForm.vue',
  'lists/ProjectAcceptanceBoardList.vue',
  'fields/ProjectAcceptanceScoreField.vue',
  'fields/ProjectAcceptanceLevelField.vue',
  'nodes/ProjectAcceptanceSummaryNode.vue',
  'list-cells/ProjectAcceptanceScoreCell.vue',
  'buttons/ProjectAcceptanceInspectButton.vue',
  'rules/ProjectAcceptanceRuleCondition.vue'
]

expectedComponents.forEach(relativePath => {
  const source = readFileSync(
    path.join(projectDir, relativePath),
    'utf8'
  )
  assert.ok(
    source.includes('ProjectExtensionAcceptance')
      || source.includes('project-acceptance')
      || source.includes('acceptance-')
      || source.includes('PROJECT:CUSTOM_CONDITION'),
    `${relativePath} 缺少验收扩展标记`
  )
})

const scoreFieldSource = readFileSync(
  path.join(
    projectDir,
    'fields/ProjectAcceptanceScoreField.vue'
  ),
  'utf8'
)
assert.ok(
  scoreFieldSource.includes("'FIELD_BUTTON_CLICK'"),
  '自定义评分字段缺少 FIELD_BUTTON_CLICK 运行时调用'
)
assert.ok(
  scoreFieldSource.includes('getFormId(form)'),
  '自定义评分字段必须兼容流程任务中的 formId'
)

const levelFieldSource = readFileSync(
  path.join(
    projectDir,
    'fields/ProjectAcceptanceLevelField.vue'
  ),
  'utf8'
)
;['FIELD_OPTIONS', 'FIELD_DEFAULT'].forEach(usage => {
  assert.ok(
    levelFieldSource.includes(`'${usage}'`),
    `复核级别字段缺少 ${usage} 运行时调用`
  )
})
assert.ok(
  levelFieldSource.includes('getFormId(props.context?.form)'),
  '复核级别字段必须兼容流程任务中的 formId'
)

const formFieldRegistrySource = readFileSync(
  path.join(
    repositoryDir,
    'workflow-web/src/components/form-fields/index.js'
  ),
  'utf8'
)
assert.ok(
  formFieldRegistrySource.includes(
    "Symbol.for(\n  'workflow.formFieldExtensionRegistry'"
  ),
  '字段扩展注册表必须在 Vite 热更新期间保持稳定'
)

const expectedFormActionContracts = [
  "key: 'acceptance_form_log'",
  "placement: 'FOOTER'",
  "key: 'acceptance_inline_log'",
  "placement: 'ACTION_SLOT'",
  "nodeKey: 'acceptance_inline_actions'",
  "targetKey: 'acceptance_form_log'",
  "targetKey: 'acceptance_inline_log'",
  "eventCode: 'FIELD_BUTTON_CLICK'",
  "fieldComponentName: 'project_acceptance_level'",
  "componentExtensionType:",
  "relationKey: 'projectCustomRelation'",
  "customComponent: 'PROJECT_CUSTOM_LIST_SCHEMA'",
  "providerSchema.viewConfig?.projectCustomSchema",
  "rows(schemaPage)",
  "item.nodeType === 'ACTION_SLOT'",
  "fullFormBindings[usage]?.serviceId",
  "processActions.length"
]

expectedFormActionContracts.forEach(contract => {
  assert.ok(
    bootstrapSource.includes(contract),
    `验收初始化脚本缺少表单动作契约 ${contract}`
  )
})

console.log('project acceptance extensions structural tests passed')
