import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import {
  FORM_NODE_ALLOWED_CHILD_TYPES,
  canContainFormNode,
  canPlaceFormNodeAtRoot,
  isFormNodeContainer
} from '../shared/form-node-hierarchy.js'

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

const formNodeDesignItem = readFileSync(path.join(root, 'src/components/FormNodeDesignItem.vue'), 'utf8')
const formNodeHierarchy = readFileSync(path.join(root, 'src/shared/form-node-hierarchy.js'), 'utf8')
const formPreviewLinkage = readFileSync(path.join(root, 'src/components/FormPreviewLinkage.vue'), 'utf8')
const formNodeRenderer = readFileSync(path.join(root, 'src/components/FormNodeRenderer.vue'), 'utf8')
const formNodeRuntimeItem = readFileSync(path.join(root, 'src/components/FormNodeRuntimeItem.vue'), 'utf8')
const formTreeRuntime = `${formNodeRenderer}\n${formNodeRuntimeItem}`

assert.match(
  formDesigner,
  /<el-drawer[\s\S]{0,4000}(?:property|attribute|node)[\s\S]{0,4000}>/i,
  '表单设计器应使用默认关闭的右侧节点属性抽屉，而非固定属性栏'
)
assert.match(
  formDesigner,
  /(?:selectField|selectNode)[\s\S]{0,1200}(?:drawer|property)[\w.]*\s*=\s*true/i,
  '点击表单节点后应打开属性抽屉'
)
assert.match(
  formDesigner,
  /previewForm[\s\S]{0,2600}nodes\s*:/,
  '草稿预览应传入当前节点树，而非只传递扁平字段'
)
assert.equal(
  /node-order|rev\.\{\{\s*node\.revision|<el-tag[^>]*>\s*\{\{\s*node\.nodeType/.test(formNodeDesignItem),
  false,
  '设计画布不应展示节点序号、nodeType 或 revision 等技术元信息'
)
assert.match(
  formNodeDesignItem,
  /(?:SECTION|GRID|TAB_SET|COLLAPSE)[\s\S]{0,1000}(?:children|child)/,
  '设计画布应按容器节点递归渲染，而非把所有节点作为同类卡片'
)
assert.match(
  formPreviewLinkage,
  /hasNodeTree[\s\S]{0,1400}FormNodeRenderer|FormNodeRenderer[\s\S]{0,1400}hasNodeTree/,
  '表单预览应优先使用节点树运行时渲染器'
)
;['SECTION', 'GRID', 'TAB_SET', 'COLLAPSE'].forEach((containerType) => {
  assert.ok(formNodeRuntimeItem.includes(containerType), `运行时缺少容器节点渲染: ${containerType}`)
})
assert.match(
  formTreeRuntime,
  /gridSpan[\s\S]{0,320}span|span[\s\S]{0,320}gridSpan/,
  '节点运行时应兼容 gridSpan 与历史 span 的栅格宽度读取'
)
assert.match(
  formDesigner,
  /(?:nodePropertySchema|propertySchema|editableFields|nodeTypeSchema)|(?:(?:isContainerNode|selectedNodeType)[\s\S]{0,2400}(?:canEditNodeLabel|canConfigureNodeExtension|selectedNodeLockMessage))/,
  '表单设计器应按节点类型 Schema 控制可编辑属性'
)
assert.match(
  formDesigner,
  /(?:bindingType|bindingRef)[\s\S]{0,1200}(?:readonly|disabled|locked)|(?:readonly|disabled|locked)[\s\S]{0,1200}(?:bindingType|bindingRef)/i,
  '表单设计器应把已绑定数据语义展示为不可编辑'
)
;[
  'availableTabSetNodes',
  'availableParentNodes',
  'handleParentChange',
  'resolveDefaultParentId',
  'collectDescendantNodeIds',
  'getSubtreeHeight',
  'FORM_NODE_MAX_DEPTH',
  '父容器',
  '所属 Tab 集合',
  '请选择所属 Tab 集合后再保存 Tab 页',
  '请先创建 Tab 集合，再添加 Tab 页'
].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `表单节点缺少受限父容器移动能力: ${marker}`)
})
;[
  'FORM_NODE_ALLOWED_CHILD_TYPES',
  'TAB_SET',
  "Object.freeze(['TAB'])",
  'canContainFormNode',
  'canPlaceFormNodeAtRoot'
].forEach((marker) => {
  assert.ok(formNodeHierarchy.includes(marker), `前端层级规则缺少约束: ${marker}`)
})
const standardFormNodeChildren = [
  'SECTION',
  'GRID',
  'TAB_SET',
  'COLLAPSE',
  'TEXT',
  'FIELD',
  'SUB_FORM',
  'REPEATER',
  'ACTION_SLOT'
]
;['SECTION', 'GRID', 'TAB', 'COLLAPSE', 'SUB_FORM', 'REPEATER'].forEach((parentType) => {
  assert.deepEqual(
    FORM_NODE_ALLOWED_CHILD_TYPES[parentType],
    standardFormNodeChildren,
    `${parentType} 的前端子节点矩阵应与后端普通容器一致`
  )
  standardFormNodeChildren.forEach((childType) => {
    assert.equal(
      canContainFormNode(parentType, childType),
      true,
      `${parentType} 应允许直接包含 ${childType}`
    )
  })
  assert.equal(
    canContainFormNode(parentType, 'TAB'),
    false,
    `${parentType} 不应直接包含 TAB`
  )
})
assert.deepEqual(
  FORM_NODE_ALLOWED_CHILD_TYPES.TAB_SET,
  ['TAB'],
  'TAB_SET 的直接子节点只能是 TAB'
)
assert.equal(canContainFormNode('TAB_SET', 'TAB'), true, 'TAB_SET 应允许直接包含 TAB')
standardFormNodeChildren.forEach((childType) => {
  assert.equal(
    canContainFormNode('TAB_SET', childType),
    false,
    `TAB_SET 不应直接包含 ${childType}`
  )
})
;['TEXT', 'FIELD', 'ACTION_SLOT'].forEach((nodeType) => {
  assert.equal(isFormNodeContainer(nodeType), false, `${nodeType} 不应作为父容器`)
})
;[
  'SECTION',
  'GRID',
  'TAB_SET',
  'COLLAPSE',
  'TEXT',
  'FIELD',
  'SUB_FORM',
  'REPEATER',
  'ACTION_SLOT'
].forEach((nodeType) => {
  assert.equal(canPlaceFormNodeAtRoot(nodeType), true, `${nodeType} 应允许放在根节点`)
})
assert.equal(canPlaceFormNodeAtRoot('TAB'), false, 'TAB 不应允许放在根节点')
;[
  'Tab 集合',
  'Tab 页',
  'design-container-caption',
  'tab-node-toolbar',
  'nested-field-children',
  'design-orphan-tab',
  'design-action-slot',
  'findContainingTabId'
].forEach((marker) => {
  assert.ok(formNodeDesignItem.includes(marker), `设计画布缺少清晰递归层级表达: ${marker}`)
})
assert.ok(
  formDesigner.includes('applyLocalSiblingOrder'),
  '未保存节点同级移动应同步更新稀疏排序键'
)
;['tabPosition', 'defaultExpanded', 'accordion'].forEach((marker) => {
  assert.ok(formNodeRuntimeItem.includes(marker), `容器运行时缺少属性支持: ${marker}`)
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
