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

;['/home', '/process', '/entity', '/system/menu', '/system/user', '/system/role', '/system/group', '/system/org', '/system/dict', '/system/config-migration'].forEach((routePath) => {
  const routePattern = new RegExp(`path:\\s*'${routePath.replaceAll('/', '\\/')}'[\\s\\S]{0,500}meta:\\s*\\{\\s*title:\\s*'[^']+'`)
  assert.match(routerSource, routePattern, `核心页面缺少标题: ${routePath}`)
})

const documentationRoutes = {
  '/manual/entity': ['EntityManual.vue', '实体配置手册'],
  '/system/dev-guide': ['DevGuide.vue', '列表字段扩展'],
  '/system/custom-list-guide': ['CustomListGuide.vue', '自定义列表组件'],
  '/system/custom-form-guide': ['CustomFormGuide.vue', '自定义表单组件']
}
for (const [routePath, markers] of Object.entries(documentationRoutes)) {
  const routeBlockPattern = new RegExp(`path:\\s*'${routePath.replaceAll('/', '\\/')}'[\\s\\S]{0,350}`)
  const routeBlock = routerSource.match(routeBlockPattern)?.[0] || ''
  markers.forEach((marker) => {
    assert.ok(routeBlock.includes(marker), `手册或扩展指南入口缺少配置: ${routePath} -> ${marker}`)
  })
}

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
    '/entity-list/:entityCode/:listKey',
    '/process/design/:id?',
    '/process/form/:nodeId',
    '/process/progress/:instanceId'
  ].sort()
)

const dynamicRuntimeFiles = [
  'src/components/EntityListLauncher.vue',
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
assert.match(entityDataList, /selectionScene[\s\S]*toolbarButtons[\s\S]*return \[\]/s, '选择型列表应隐藏业务工具栏动作')
const listButtonConfig = readFileSync(path.join(root, 'src/components/ListButtonConfigPanel.vue'), 'utf8')
;['open-list', 'targetEntityCode', 'targetListKey', 'relationKey'].forEach((marker) => {
  assert.ok(listButtonConfig.includes(marker), `列表按钮缺少打开列表配置: ${marker}`)
})
const entitySelector = readFileSync(path.join(root, 'src/components/EntitySelector.vue'), 'utf8')
;['FORM_PICKER', 'runtimeEntityCode', 'listKey'].forEach((marker) => {
  assert.ok(entitySelector.includes(marker), `实体选择器缺少统一列表能力: ${marker}`)
})
;['viewConfig', 'customListRuntime', 'defaultValue'].forEach((marker) => {
  assert.ok(entityDataList.includes(marker), `动态实体列表缺少配置能力: ${marker}`)
})

const listDesigner = readFileSync(path.join(root, 'src/views/EntityListConfigDesign.vue'), 'utf8')
;['addVirtualField', 'getExtensionOptions', 'ConfigSchemaEditor', 'renderConfig', 'queryConfig', 'columnConfig'].forEach((marker) => {
  assert.ok(listDesigner.includes(marker), `列表设计器缺少动态配置能力: ${marker}`)
})
;['dataScopeMode', 'allowedSceneValues', 'selectionMode', 'fixedFilterConfig', 'contextBindingConfig'].forEach((marker) => {
  assert.ok(listDesigner.includes(marker), `列表设计器缺少统一运行时配置: ${marker}`)
})
;['getScenes', 'toggleScene', 'saveListAction', 'removeListAction', '当前项独立保存'].forEach((marker) => {
  assert.ok(listDesigner.includes(marker), `列表设计器缺少单项增量保存能力: ${marker}`)
})
assert.equal(listDesigner.includes('@click="handleSave"'), false, '列表设计器不应继续暴露整包保存入口')
;["'save'", "'remove'", "@click=\"$emit('save', row)\"", "@click=\"$emit('remove', row)\""].forEach((marker) => {
  assert.ok(listButtonConfig.includes(marker), `列表按钮缺少单项操作能力: ${marker}`)
})

const formDesigner = readFileSync(path.join(root, 'src/views/EntityFormDesignByEntity.vue'), 'utf8')
;['getFormFieldComponentOptions', 'selectedComponentConfig', 'validationRules', 'extensionConfig', 'modeOptions'].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `表单设计器缺少动态项目能力: ${marker}`)
})
;[
  'childFormReleaseId',
  'childFormReleaseVersion',
  'handleChildFormReleaseChange',
  'ensureChildFormReleaseBinding',
  'getFormReleases'
].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `子表单设计器缺少固定发布版本能力: ${marker}`)
})

const subFormField = readFileSync(
  path.join(root, 'src/components/form-fields/components/SubFormField.vue'),
  'utf8'
)
;[
  'childFormReleaseId',
  'childFormReleaseVersion',
  'snapshotDocument',
  'getFormRuntimeRelease',
  'resolveSnapshotFields',
  'initializeChildRows',
  'childFormDefinition',
  'initializationKey'
].forEach((marker) => {
  assert.ok(subFormField.includes(marker), `子表单运行时缺少发布快照隔离: ${marker}`)
})
;['getFormFields', 'getFormById'].forEach((draftApi) => {
  assert.equal(
    subFormField.includes(draftApi),
    false,
    `子表单运行时不得读取草稿接口: ${draftApi}`
  )
})

const entityDesigner = readFileSync(path.join(root, 'src/views/EntityDesign.vue'), 'utf8')
;[
  'ActionRuleGroupEditor',
  'filterRoot',
  'permissionRuleFieldOptions',
  'getEnabledGroups',
  'CURRENT_ASSIGNEE',
  'value="GROUP"',
  'value="ORG"',
  'value="RULE"'
].forEach((marker) => {
  assert.ok(entityDesigner.includes(marker), `实体数据权限配置缺少结构化能力: ${marker}`)
})
assert.equal(entityDesigner.includes('value="EXPRESSION"'), false, '数据权限配置不得继续暴露自由表达式')
assert.equal(entityDesigner.includes('value="CUSTOM_SQL"'), false, '数据权限配置不得继续暴露自定义 SQL')

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

const processActionApi = readFileSync(path.join(root, 'src/api/processAction.js'), 'utf8')
;['/process-actions', '/process-action-handlers', '/process-action-executions'].forEach((endpoint) => {
  assert.ok(processActionApi.includes(endpoint), `流程动作客户端缺少规范接口: ${endpoint}`)
})
;['/flow-actions', '/flow-action-handlers', '/flow-action-executions'].forEach((endpoint) => {
  assert.equal(processActionApi.includes(endpoint), false, `流程动作客户端不应继续使用旧接口: ${endpoint}`)
})
assert.equal(existsSync(path.join(root, 'src/api/flowAction.js')), false, '旧 flowAction API 文件应移除')

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
assert.match(processProgress, /userStore\.isSuperAdmin[\s\S]*FlowActionExecutionLog/, '流程进度页应仅为超级管理员展示动作执行记录')
const flowActionExecutionLog = readFileSync(path.join(root, 'src/components/FlowActionExecutionLog.vue'), 'utf8')
;['解析后参数', '执行结果', '触发上下文', '执行过程', 'retryExecution'].forEach((marker) => {
  assert.ok(flowActionExecutionLog.includes(marker), `流程动作执行日志缺少详情或重试能力: ${marker}`)
})

const configMigration = readFileSync(path.join(root, 'src/views/system/ConfigMigration.vue'), 'utf8')
;[
  '待导出',
  '导出记录',
  '导入管理',
  '版本对比',
  'exportPackage',
  'uploadPackage',
  'analyzeImport',
  'publishImport',
  'rollbackImport',
  'saveMappings'
].forEach((marker) => {
  assert.ok(configMigration.includes(marker), `配置迁移页面缺少闭环能力: ${marker}`)
})

const processList = readFileSync(path.join(root, 'src/views/ProcessList.vue'), 'utf8')
const entityList = readFileSync(path.join(root, 'src/views/EntityList.vue'), 'utf8')
;['markForExport', 'migrationTag', 'generateMigrationTag'].forEach((marker) => {
  assert.ok(processList.includes(marker), `流程发布缺少迁移标记能力: ${marker}`)
  assert.ok(entityList.includes(marker), `实体发布缺少迁移标记能力: ${marker}`)
})

const formFieldRegistry = readFileSync(path.join(root, 'src/components/form-fields/index.js'), 'utf8')
;['text', 'textarea', 'number', 'select', 'radio', 'checkbox', 'date', 'switch', 'file', 'reference', 'sub_form'].forEach((type) => {
  assert.ok(formFieldRegistry.includes(type), `表单运行时缺少字段类型线索: ${type}`)
})

const guideExpectations = {
  'src/views/system/DevGuide.vue': ['ListFieldDataProvider', 'FIELD_TEMPLATE', 'registerCellComponent', 'DemoRiskProgressCell', 'test:demo:real'],
  'src/views/system/CustomListGuide.vue': ['registerCustomListComponent', 'runtime', 'canAction', 'DemoProjectCardList', 'toolbarCapabilities'],
  'src/views/system/CustomFormGuide.vue': ['registerFormFieldComponent', 'registerFormNodeComponent', 'registerCustomFormComponent', 'create', 'approve', 'defineExpose', 'DemoProjectForm']
}
for (const [file, markers] of Object.entries(guideExpectations)) {
  const source = readFileSync(path.join(root, file), 'utf8')
  markers.forEach((marker) => {
    assert.ok(source.includes(marker), `${file} 缺少扩展说明: ${marker}`)
  })
}

const configurationArchitectureExpectations = {
  'src/data/user-manual/entity.js': [
    '稳定节点 ID',
    'expectedRevision',
    '409',
    'SECTION',
    'TAB_SET / TAB',
    'ACTION_SLOT',
    '最大嵌套深度 8 层',
    '/draft',
    '/diff',
    '/publish',
    '/releases',
    '/activate',
    'ENTITY_QUERY',
    'INTEGRATION_CONNECTOR',
    'FORM_INIT',
    'BEFORE_SUBMIT',
    'DataScopePlan',
    'templateVersion + localOverrides',
    '三方合并',
    'legacyProps',
    '配置迁移幂等与兼容',
    '运行时回退'
  ],
  'src/views/system/DevGuide.vue': [
    'expectedRevision',
    '409 Conflict',
    'serverRevision',
    'SECTION / GRID',
    'TAB_SET / TAB',
    '最大嵌套深度为 `8`',
    'UiDataSourceProvider',
    'IntegrationConnector',
    'DataScopePlan',
    'FORM_INIT',
    'BEFORE_SUBMIT',
    '/draft',
    '/diff',
    '/publish',
    '/releases',
    '/activate',
    'templateVersion + localOverrides',
    '三方合并',
    'legacyProps',
    '迁移必须幂等',
    '临时回退旧配置'
  ],
  'src/views/system/CustomListGuide.vue': [
    '稳定 `id`',
    'expectedRevision',
    'HTTP `409`',
    'LIST_QUERY',
    'LIST_COLUMN',
    'INTEGRATION_CONNECTOR',
    'DataScopePlan',
    '/draft',
    '/diff',
    '/publish',
    '/releases',
    '/activate',
    'templateVersion + localOverrides',
    '三方合并',
    '局部视觉或数据变化',
    '重复执行结果幂等',
    '临时回退旧配置'
  ],
  'src/views/system/CustomFormGuide.vue': [
    '稳定 `nodeId`',
    'expectedRevision',
    'HTTP `409`',
    'SECTION、GRID、TAB_SET、TAB、COLLAPSE、TEXT、FIELD、SUB_FORM、REPEATER、ACTION_SLOT',
    '最大深度 8 层',
    'registerFormNodeComponent',
    '节点级扩展',
    'INTEGRATION_CONNECTOR',
    'FORM_INIT',
    'BEFORE_SUBMIT',
    'DataScopePlan',
    '/draft',
    '/diff',
    '/publish',
    '/releases',
    '/activate',
    'templateVersion + localOverrides',
    '三方合并',
    'legacyProps',
    '幂等转换',
    '临时回退旧配置'
  ]
}
for (const [file, markers] of Object.entries(configurationArchitectureExpectations)) {
  const source = readFileSync(path.join(root, file), 'utf8')
  markers.forEach((marker) => {
    assert.ok(source.includes(marker), `${file} 缺少通用配置架构说明: ${marker}`)
  })
}

const demoExpectations = {
  'src/demo/index.js': ['registerDemoExtensions', 'DemoRiskProgressCell', 'DemoProjectCardList', 'DemoProjectForm'],
  'src/demo/list-fields/DemoRiskProgressCell.vue': ['warningAt', 'dangerAt', 'context'],
  'src/demo/lists/DemoProjectCardList.vue': ['runtime.canAction', 'toolbarCapabilities', 'sizeChange', 'pageChange'],
  'src/demo/forms/DemoProjectForm.vue': ['isFieldReadonlyForMode', 'linkageState', 'defineExpose({ validate })'],
  'scripts/real-dynamic-extension-demo.mjs': ['createCustomList', 'progressWithCustomForm', 'completeDemoProcess']
}
for (const [file, markers] of Object.entries(demoExpectations)) {
  const source = readFileSync(path.join(root, file), 'utf8')
  markers.forEach((marker) => {
    assert.ok(source.includes(marker), `${file} 缺少 Demo 验证能力: ${marker}`)
  })
}

const pagedEntityDataList = readFileSync(path.join(root, 'src/views/entity/EntityDataList.vue'), 'utf8')
;[
  'entityListRuntimeApi.query',
  'runtimeListKey',
  'runtimeScene',
  'total.value = Number(res?.total'
].forEach((marker) => {
  assert.ok(pagedEntityDataList.includes(marker), `实体数据列表缺少服务端分页能力: ${marker}`)
})

console.log('page configuration audit passed')
