import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const routerSource = readFileSync(path.join(root, 'src/router/index.js'), 'utf8')
const viewFiles = [...routerSource.matchAll(/import\('@\/views\/([^']+\.vue)'\)/g)].map((match) => match[1])

assert.ok(viewFiles.length >= 20, '应发现主要页面路由')
viewFiles.forEach((viewFile) => {
  const fullPath = path.join(root, 'src/views', viewFile)
  assert.equal(existsSync(fullPath), true, `路由组件不存在: ${viewFile}`)
  const source = readFileSync(fullPath, 'utf8')
  assert.match(source, /<template>[\s\S]*<\/template>/, `页面缺少 template: ${viewFile}`)
  assert.match(source, /<script[\s\S]*>[\s\S]*<\/script>/, `页面缺少 script: ${viewFile}`)
})

;['/home', '/process', '/entity', '/system/menu', '/system/user', '/system/role', '/system/group', '/system/org', '/system/dict'].forEach((routePath) => {
  const routePattern = new RegExp(`path:\\s*'${routePath.replaceAll('/', '\\/')}'[\\s\\S]{0,500}meta:\\s*\\{\\s*title:\\s*'[^']+'`)
  assert.match(routerSource, routePattern, `核心页面缺少标题: ${routePath}`)
})

const dynamicRoutePaths = [...routerSource.matchAll(/path:\s*'([^']*:[^']+)'/g)]
  .map((match) => match[1])
  .filter((routePath) => !routePath.includes('pathMatch'))
assert.deepEqual(
  dynamicRoutePaths.sort(),
  [
    '/entity-form/design/:id',
    '/entity-form/list-by-entity/:entityId',
    '/entity-list-config/:entityId',
    '/entity-list-config/design/:id',
    '/entity/data/:code',
    '/entity/design/:id',
    '/entity/list/:entityCode',
    '/process/design/:id?',
    '/process/form/:nodeId',
    '/process/progress/:instanceId'
  ].sort()
)

const dynamicRuntimeFiles = [
  'src/views/entity/EntityDataList.vue',
  'src/views/entity/components/EntityDataSearchForm.vue',
  'src/views/entity/components/EntityDataTable.vue',
  'src/views/entity/components/EntityDataFormDialog.vue',
  'src/components/FormFieldRenderer.vue',
  'src/components/ListCellRenderer.vue',
  'src/components/ConfigSchemaEditor.vue',
  'src/shared/config-runtime/index.js',
  'src/shared/form-runtime/index.js',
  'src/shared/list-runtime/index.js'
]

dynamicRuntimeFiles.forEach((file) => {
  assert.equal(existsSync(path.join(root, file)), true, `动态配置运行时文件不存在: ${file}`)
})

const entityDataList = readFileSync(path.join(root, 'src/views/entity/EntityDataList.vue'), 'utf8')
assert.match(entityDataList, /customListComponent[\s\S]*hasCustomListComponent/, '动态实体列表应支持自定义列表组件')
assert.match(entityDataList, /queryFields[\s\S]*listFields[\s\S]*toolbarButtons[\s\S]*rowActionButtons/s, '动态实体列表应派生查询、表格和按钮配置')
;['viewConfig', 'customListRuntime', 'defaultValue'].forEach((marker) => {
  assert.ok(entityDataList.includes(marker), `动态实体列表缺少配置能力: ${marker}`)
})

const listDesigner = readFileSync(path.join(root, 'src/views/EntityListConfigDesign.vue'), 'utf8')
;['addVirtualField', 'getExtensionOptions', 'ConfigSchemaEditor', 'renderConfig', 'queryConfig', 'columnConfig'].forEach((marker) => {
  assert.ok(listDesigner.includes(marker), `列表设计器缺少动态配置能力: ${marker}`)
})

const formDesigner = readFileSync(path.join(root, 'src/views/EntityFormDesignByEntity.vue'), 'utf8')
;['getFormFieldComponentOptions', 'selectedComponentConfig', 'validationRules', 'extensionConfig', 'modeOptions'].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `表单设计器缺少动态项目能力: ${marker}`)
})

const entityDataTable = readFileSync(path.join(root, 'src/views/entity/components/EntityDataTable.vue'), 'utf8')
assert.match(
  entityDataTable,
  /handleSelectionChange[\s\S]*selectedRows\.value\s*=\s*selection/,
  '表格选中数据必须同步到批量操作能力判断'
)

const flowActionPanel = readFileSync(path.join(root, 'src/components/FlowActionConfigPanel.vue'), 'utf8')
;['triggerTiming', 'executionMode', 'failurePolicy', 'maxRetries'].forEach((field) => {
  assert.ok(flowActionPanel.includes(field), `流程动作配置缺少字段: ${field}`)
})
;['TASK_COMPLETING', 'TASK_CREATED', 'TRANSITION_TAKEN', 'PROCESS_COMPLETED', 'PROCESS_WITHDRAWN'].forEach((timing) => {
  assert.ok(flowActionPanel.includes(timing), `流程动作配置缺少常用时机模板: ${timing}`)
})

const flowActionGuide = readFileSync(path.join(root, 'src/views/system/FlowActionGuide.vue'), 'utf8')
;[
  'actionName',
  'triggerTiming',
  'executionMode',
  'failurePolicy',
  'retryConfig.maxRetries',
  'interfaceName',
  'paramsJson',
  'enabled',
  'sortOrder'
].forEach((field) => {
  assert.ok(flowActionGuide.includes(field), `流程动作指南缺少字段说明: ${field}`)
})
;[
  'PROCESS_STARTED',
  'PROCESS_COMPLETED',
  'PROCESS_WITHDRAWN',
  'PROCESS_TERMINATED',
  'NODE_ENTERED',
  'NODE_COMPLETED',
  'TASK_CREATED',
  'TASK_ASSIGNED',
  'TASK_COMPLETING',
  'TRANSITION_TAKEN'
].forEach((timing) => {
  assert.ok(flowActionGuide.includes(timing), `流程动作指南缺少时机说明: ${timing}`)
})
;['tocItems', 'scrollToSection', 'setupSectionObserver', 'id="scope"', 'id="scenes"'].forEach((marker) => {
  assert.ok(flowActionGuide.includes(marker), `流程动作指南缺少目录能力: ${marker}`)
})

const processDesign = readFileSync(path.join(root, 'src/views/ProcessDesign.vue'), 'utf8')
assert.match(processDesign, /全局动作[\s\S]*scope-type="PROCESS"/, '流程设计器应提供全局流程动作入口')

const nodeConfigPanel = readFileSync(path.join(root, 'src/components/NodeConfigPanel.vue'), 'utf8')
;['FlowConditionGroupEditor', 'conditionGroupConfig', 'conditionRoot', 'buildFlowConditionExpression'].forEach((marker) => {
  assert.ok(nodeConfigPanel.includes(marker), `流程条件配置缺少条件组能力: ${marker}`)
})

const processProgress = readFileSync(path.join(root, 'src/views/ProcessProgress.vue'), 'utf8')
assert.match(processProgress, /动作执行记录[\s\S]*retryActionExecution/, '流程进度页应支持查看并重试动作执行记录')

const formFieldRegistry = readFileSync(path.join(root, 'src/components/form-fields/index.js'), 'utf8')
;['text', 'textarea', 'number', 'select', 'radio', 'checkbox', 'date', 'switch', 'file', 'reference', 'sub_form'].forEach((type) => {
  assert.ok(formFieldRegistry.includes(type), `表单运行时缺少字段类型线索: ${type}`)
})

const guideExpectations = {
  'src/views/system/DevGuide.vue': ['ListFieldDataProvider', 'FIELD_TEMPLATE', 'registerCellComponent', 'configSchema'],
  'src/views/system/CustomListGuide.vue': ['registerCustomListComponent', 'runtime', 'canAction', 'configSchema'],
  'src/views/system/CustomFormGuide.vue': ['registerFormFieldComponent', 'registerCustomFormComponent', 'create', 'approve', 'defineExpose']
}
for (const [file, markers] of Object.entries(guideExpectations)) {
  const source = readFileSync(path.join(root, file), 'utf8')
  markers.forEach((marker) => {
    assert.ok(source.includes(marker), `${file} 缺少扩展说明: ${marker}`)
  })
}

console.log('page configuration audit passed')
