import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import {
  FORM_NODE_ALLOWED_CHILD_TYPES,
  canContainFormNode,
  canPlaceFormNodeAtRoot,
  isFormNodeContainer
} from '../shared/form-node-hierarchy.js'

const root = process.cwd()
const collectFiles = (directory, extension) => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const fullPath = path.join(directory, entry.name)
  return entry.isDirectory()
    ? collectFiles(fullPath, extension)
    : entry.name.endsWith(extension) ? [fullPath] : []
})
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

collectFiles(path.join(root, 'src'), '.vue').forEach((vueFile) => {
  const source = readFileSync(vueFile, 'utf8')
  assert.doesNotMatch(
    source,
    /<el-radio(?:-button)?\b[^>]*\s:?label=/,
    `Element Plus 单选控件应使用 value 传值，避免 3.x 废弃兼容问题: ${path.relative(root, vueFile)}`
  )
})

const routedTopLevelViews = new Set([
  ...viewFiles.filter(file => !file.includes('/')),
  'Layout.vue'
])
const unroutedTopLevelViews = readdirSync(path.join(root, 'src/views'))
  .filter(file => file.endsWith('.vue'))
  .filter(file => !routedTopLevelViews.has(file))
assert.deepEqual(
  unroutedTopLevelViews,
  [],
  `src/views 顶层页面必须有正式路由或被移入组件目录，发现无入口页面: ${unroutedTopLevelViews.join(', ')}`
)

;[
  'src/views/Workbench.vue',
  'src/views/EntityFormManage.vue',
  'src/components/ProcessDetail.vue',
  'src/api/workbench.js'
].forEach((retiredFile) => {
  assert.equal(existsSync(path.join(root, retiredFile)), false, `已下线实现不得恢复: ${retiredFile}`)
})

;['/home', '/process', '/entity', '/system/menu', '/system/user', '/system/role', '/system/group', '/system/org', '/system/dict', '/system/audit-logs', '/system/config-migration'].forEach((routePath) => {
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
    '/process/progress/:instanceId'
  ].sort()
)

assert.doesNotMatch(routerSource, /FormDesign\.vue|\/process\/form\/:nodeId/, '不得保留会假装保存成功的旧流程表单设计入口')
assert.match(
  routerSource,
  /path:\s*'\/:pathMatch\(\.\*\)\*'[\s\S]{0,250}NotFound\.vue/,
  '未知地址应展示明确的 404 页面'
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

const switchFieldSource = readFileSync(
  path.join(root, 'src/components/form-fields/components/SwitchField.vue'),
  'utf8'
)
assert.ok(
  switchFieldSource.includes('type: [String, Number, Boolean]')
    && switchFieldSource.includes(':model-value="switchValue"')
    && switchFieldSource.includes('@update:model-value="handleChange"'),
  '布尔字段必须避免 Vue 将空字符串自动转换为 true，并保持单一更新通道'
)

const settingsSectionSource = readFileSync(path.join(root, 'src/components/SettingsSection.vue'), 'utf8')
;['defaultExpanded', 'collapsible', 'primary', 'settings-section__summary', 'aria-expanded'].forEach((marker) => {
  assert.ok(settingsSectionSource.includes(marker), `共享设置分组缺少折叠或首屏语义: ${marker}`)
})

const entityDataList = readFileSync(path.join(root, 'src/views/entity/EntityDataList.vue'), 'utf8')
const legacyEntityDataRedirect = readFileSync(
  path.join(root, 'src/views/entity/LegacyEntityDataRedirect.vue'),
  'utf8'
)
const entityDataFormDialog = readFileSync(
  path.join(root, 'src/views/entity/components/EntityDataFormDialog.vue'),
  'utf8'
)
const entityApprovalDialog = readFileSync(
  path.join(root, 'src/views/entity/components/approval/EntityApprovalDialog.vue'),
  'utf8'
)
const runtimeFormTabs = readFileSync(
  path.join(root, 'src/shared/form-runtime/runtimeFormTabs.js'),
  'utf8'
)
const entityDataTableSource = readFileSync(
  path.join(root, 'src/views/entity/components/EntityDataTable.vue'),
  'utf8'
)
assert.match(entityDataList, /customListComponent[\s\S]*hasCustomListComponent/, '动态实体列表应支持自定义列表组件')
assert.match(entityDataList, /queryFields[\s\S]*listFields[\s\S]*toolbarButtons[\s\S]*rowActionButtons/s, '动态实体列表应派生查询、表格和按钮配置')
assert.match(entityDataList, /selectionScene[\s\S]*toolbarButtons[\s\S]*return \[\]/s, '选择型列表应隐藏业务工具栏动作')
assert.match(
  entityDataList,
  /const handleCreate = async[\s\S]*await loadDefaultForm\(true\)[\s\S]*await nextTick\(\)[\s\S]*await formDialogRef\.value\?\.openCreate\(\)/,
  '新增实体数据前应重新加载最新发布表单，并等待子组件 props 更新后再打开弹窗'
)
assert.ok(
  entityDataList.includes("getFormForNewData(entityCode.value, { silentError: true })")
    && entityDataList.includes("ElMessage.error(e?.message || '加载最新发布表单失败，请稍后重试')"),
  '最新发布表单加载失败时不得继续打开旧缓存表单'
)
assert.ok(
  entityDataFormDialog.includes('新增数据 - ${props.defaultForm.formName}')
    && entityDataFormDialog.includes('props.defaultForm.formKey'),
  '新增实体数据弹窗应明确展示实际运行时表单名称和标识'
)
assert.ok(
  entityDataFormDialog.includes(':showStartProcess="canStartProcess"')
    && entityDataFormDialog.includes('const canStartProcess = computed(() => !hasProcessInfo.value)')
    && entityDataFormDialog.includes('formData.startProcess = false'),
  '未发起流程的数据在编辑时仍应提供“发起审批”，并重置上一次的勾选状态'
)
assert.ok(
  entityDataFormDialog.includes('canStartProcess.value && runtimeNodeTabs.value.length === 0')
    && entityDataFormDialog.includes(':showStartProcess="canStartProcess && !showBasicTab && idx === 0"'),
  '纯递归 Tab 表单新增时不应生成空白“基本信息”，发起流程开关应由首个自定义 Tab 承载'
)
assert.doesNotMatch(
  entityDataFormDialog,
  /:showStartProcess="!isEdit"/,
  '编辑态不得无条件隐藏“发起审批”'
)
;[
  'resolveRuntimeFormTabLayout',
  'runtimeNodeTabs',
  'liftedRootNodeIds',
  'label="流程图"',
  'label="审批历史"'
].forEach((marker) => {
  assert.ok(
    entityDataFormDialog.includes(marker),
    `实体查看编辑弹窗缺少同级表单与流程页签能力: ${marker}`
  )
})
;[
  'approvalNodeTabs',
  'approvalLiftedRootNodeIds',
  'label="流程图"',
  'label="审批历史"',
  'isApprovalFormTab'
].forEach((marker) => {
  assert.ok(
    entityApprovalDialog.includes(marker),
    `审批弹窗缺少统一表单、流程页签或审批操作能力: ${marker}`
  )
})
;['parentId', 'TAB_SET', 'TAB', 'liftedRootNodeIds'].forEach((marker) => {
  assert.ok(
    runtimeFormTabs.includes(marker),
    `运行时根级 Tab 提升逻辑缺少关键语义: ${marker}`
  )
})
assert.match(
  entityDataTableSource,
  /<el-button[\s\S]{0,180}:type="btn\.buttonType \|\| 'primary'"[\s\S]{0,80}\blink(?:\s|>)/,
  '列表操作列按钮应保持无底色的链接样式'
)
assert.match(
  legacyEntityDataRedirect,
  /getByCode[\s\S]*getByEntityId[\s\S]*EntityListRuntime/,
  '旧版实体数据地址应定位默认列表并跳转到统一运行时'
)
assert.doesNotMatch(routerSource, /EntityDataManage\.vue/, '路由不得继续加载旧版实体数据运行时')
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
;['getScenes', 'toggleScene', 'saveListAction', 'removeListAction', 'saveAll'].forEach((marker) => {
  assert.ok(listDesigner.includes(marker), `列表设计器缺少单项增量保存能力: ${marker}`)
})
assert.equal(listDesigner.includes('@click="handleSave"'), false, '列表设计器不应继续暴露整包保存入口')
assert.ok(
  listDesigner.includes('@click="saveAll"') && listDesigner.includes('保存全部'),
  '列表设计器应提供页面级保存全部，同时保留增量保存接口'
)
;["'save'", "'remove'", "@click=\"$emit('save', row)\"", "@click=\"$emit('remove', row)\""].forEach((marker) => {
  assert.ok(listButtonConfig.includes(marker), `列表按钮缺少单项操作能力: ${marker}`)
})
const listButtonTableSource = listButtonConfig.match(/<el-table\b[\s\S]*?<\/el-table>/)?.[0] || ''
assert.ok(
  listButtonTableSource.includes('openAdvancedSettings(row)') && /更多(?:设置)?/.test(listButtonTableSource),
  '列表按钮首屏应提供当前按钮的“更多设置”入口'
)
assert.ok(listButtonConfig.includes('title="按钮更多设置"'), '列表按钮低频属性应集中到“按钮更多设置”弹窗')
const lowFrequencyButtonFieldPatterns = [
  /label="(?:按钮)?图标"/,
  /label="(?:按钮)?样式"/,
  /label="(?:(?:行按钮)?Link\s*(?:样式)?|链接样式)"/,
  /label="(?:按钮|组件)?模板"/
]
lowFrequencyButtonFieldPatterns.forEach((pattern) => {
  assert.equal(pattern.test(listButtonTableSource), false, `列表按钮低频字段不应继续占用主表格: ${pattern}`)
})
const listButtonDetailsSource = listButtonConfig.slice(listButtonConfig.indexOf('</el-table>') + '</el-table>'.length)
lowFrequencyButtonFieldPatterns.forEach((pattern) => {
  assert.match(listButtonDetailsSource, pattern, `列表按钮“更多设置”应保留低频字段: ${pattern}`)
})

const entitySettingsDesigner = readFileSync(path.join(root, 'src/views/EntityDesign.vue'), 'utf8')
;['title="常用属性"', 'title="数据与约束"', 'title="类型专属配置"'].forEach((marker) => {
  assert.ok(entitySettingsDesigner.includes(marker), `实体字段设置缺少频率分组: ${marker}`)
})
;[
  [entitySettingsDesigner, 'designMode', ['label="数据库列名"', 'label="验证规则"']],
  [listDesigner, 'configMode', ['title="查询实现"', 'title="扩展渲染"', 'label="数据与显示"']]
].forEach(([source, modeVariable, visibleMarkers]) => {
  assert.equal(source.includes(modeVariable), false, `设计器不得再按模式隐藏配置: ${modeVariable}`)
  visibleMarkers.forEach((marker) => {
    assert.ok(source.includes(marker), `设计器缺少直接展示的配置项: ${marker}`)
  })
})
const entityFieldGroupPositions = [
  entitySettingsDesigner.indexOf('title="常用属性"'),
  entitySettingsDesigner.indexOf('title="数据与约束"'),
  entitySettingsDesigner.indexOf('title="类型专属配置"')
]
assert.deepEqual(
  entityFieldGroupPositions,
  [...entityFieldGroupPositions].sort((left, right) => left - right),
  '实体字段设置应先显示常用属性，再显示约束和类型专属配置'
)

const formDesigner = readFileSync(path.join(root, 'src/views/EntityFormDesignByEntity.vue'), 'utf8')
assert.equal(formDesigner.includes('designMode'), false, '表单设计器不得再按基础、高级或开发者模式隐藏配置')
;['扩展管理', 'label="自定义组件"', 'title="数据源"', 'title="校验"', 'title="模式与权限"'].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `表单设计器缺少直接展示的配置项: ${marker}`)
})
;[
  "parseJsonConfig(row.inputMappingText",
  "parseJsonConfig(row.outputMappingText",
  'validateNodeDataSourceMappings(selectedField.value)',
  'validateNodeDataSourceMappings(field)'
].forEach((marker) => {
  assert.ok(
    formDesigner.includes(marker),
    `表单数据源映射保存前必须执行严格 JSON 校验: ${marker}`
  )
})
;['getFormFieldComponentOptions', 'selectedComponentConfig', 'validationRules', 'extensionConfig', 'modeOptions'].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `表单设计器缺少动态项目能力: ${marker}`)
})
assert.ok(
  formDesigner.includes('getDefaultFormFieldComponentType as getDefaultComponentType'),
  '新增实体字段必须使用共享的兼容默认组件策略'
)
const formFieldRegistrySource = readFileSync(path.join(root, 'src/components/form-fields/index.js'), 'utf8')
assert.ok(
  formFieldRegistrySource.includes('getBuiltInFormFieldSupportedTypes'),
  '字段组件注册必须复用共享 supportedFieldTypes 策略'
)
;[
  'childFormReleaseId',
  'childFormReleaseVersion',
  'handleChildFormReleaseChange',
  'ensureChildFormReleaseBinding',
  'getFormReleases'
].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `子表单设计器缺少固定发布版本能力: ${marker}`)
})
;[
  'hasUnsavedLocalChanges',
  '画布或属性仍有未保存修改，请先保存草稿后再发布',
  '重新发布流程后再新增流程数据',
  '历史实例继续使用原版本'
].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `表单发布缺少版本生效提示或本地草稿保护: ${marker}`)
})
const formPropertyGroupPatterns = [
  ['常用属性', /title="常用属性"/],
  ['布局与层级', /title="布局与层级"/],
  ['数据源', /title="数据源"/],
  ['校验', /title="校验"/],
  ['模式与权限', /title="模式与权限"/],
  ['关系与子表', /title="关系与子表"/],
  ['复用与扩展', /title="复用与扩展"/]
]
const formPropertyGroupPositions = formPropertyGroupPatterns.map(([label, pattern]) => {
  const position = pattern.exec(formDesigner)?.index ?? -1
  assert.ok(position >= 0, `表单属性抽屉缺少分组: ${label}`)
  return position
})
assert.deepEqual(
  formPropertyGroupPositions,
  [...formPropertyGroupPositions].sort((left, right) => left - right),
  '表单属性抽屉应按常用、布局、数据源、校验、模式权限、关系子表、复用扩展排序'
)
;[
  'formSettingsExpanded',
  'class="form-summary-meta"',
  '表单设置',
  'canConfigureSelectedNodeDataSource',
  'canConfigureSelectedNodeValidation',
  'canConfigureSelectedNodeModeAccess',
  'canConfigureSelectedNodeRelations'
].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `表单设计器缺少表单级摘要或节点类型动态分组: ${marker}`)
})

const formNodeDesignItem = readFileSync(path.join(root, 'src/components/FormNodeDesignItem.vue'), 'utf8')
const formNodeDraggableList = readFileSync(path.join(root, 'src/components/FormNodeDraggableList.vue'), 'utf8')
const formNodeDrag = readFileSync(path.join(root, 'src/shared/form-node-drag.js'), 'utf8')
const formNodeHierarchy = readFileSync(path.join(root, 'src/shared/form-node-hierarchy.js'), 'utf8')
const formPreviewLinkage = readFileSync(path.join(root, 'src/components/FormPreviewLinkage.vue'), 'utf8')
const formNodeRenderer = readFileSync(path.join(root, 'src/components/FormNodeRenderer.vue'), 'utf8')
const formNodeRuntimeItem = readFileSync(path.join(root, 'src/components/FormNodeRuntimeItem.vue'), 'utf8')
const entityDataFormFields = readFileSync(
  path.join(root, 'src/views/entity/components/EntityDataFormFields.vue'),
  'utf8'
)
const formTreeRuntime = `${formNodeRenderer}\n${formNodeRuntimeItem}`

assert.ok(
  entityDataFormFields.includes(':form="runtimeForm"')
    && entityDataFormFields.includes('fields: runtimeFormFields.value'),
  '节点表单运行态必须使用注入字典和动态选项后的字段集合'
)
assert.match(
  formNodeRuntimeItem,
  /const required = computed\(\(\) => Boolean\(/,
  '节点表单必填状态必须规范化为 Boolean'
)

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
  formNodeDesignItem,
  /tab-position="nodeConfig\.tabPosition \|\| 'top'"/,
  '设计画布的 Tab 集合应读取 tabPosition，与预览和运行时保持一致'
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
;[
  'getFormFieldValidationCapabilities',
  'selectedValidationCapabilities.length',
  'selectedValidationCapabilities.range',
  'selectedValidationCapabilities.format'
].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `字段校验属性缺少类型兼容控制: ${marker}`)
})
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
  'collectFormNodeDescendantIds',
  'getSubtreeHeight',
  'FORM_NODE_MAX_DEPTH',
  '父容器',
  '所属 Tab 集合',
  '请选择所属 Tab 集合后再保存 Tab 页',
  '请先创建 Tab 集合，再添加 Tab 页'
].forEach((marker) => {
  assert.ok(
    `${formDesigner}\n${formNodeDrag}`.includes(marker),
    `表单节点缺少受限父容器移动能力: ${marker}`
  )
})
;[
  "import Sortable from 'sortablejs'",
  'new Sortable',
  'data-sortable-ready',
  'form-node-sortable-item',
  'form-node-drag-handle',
  'data-form-node-parent-id',
  'forceFallback: true',
  'fallbackTolerance: 3',
  'resolveDragDirection',
  'onAdd: handleAdd',
  'onUpdate: handleUpdate',
  'handleEnd'
].forEach((marker) => {
  assert.ok(
    `${formNodeDesignItem}\n${formNodeDraggableList}`.includes(marker),
    `表单设计器缺少递归拖拽能力: ${marker}`
  )
})
;[
  'buildFormNodeDropPlan',
  'validateFormNodeDrop',
  'reorderFormNode',
  'expectedRevision',
  'loadFormFields'
].forEach((marker) => {
  assert.ok(formDesigner.includes(marker), `拖拽排序缺少草稿持久化能力: ${marker}`)
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
const subFormRenderer = readFileSync(
  path.join(root, 'src/components/SubFormRenderer.vue'),
  'utf8'
)
assert.ok(
  subFormRenderer.includes("config.showHeaderTitle !== false"),
  '子表单渲染器应支持由外层表单项统一展示标题'
)
assert.ok(
  subFormField.includes('showHeaderTitle: false'),
  '节点树中的子表单不应重复展示内外两层标题'
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
assert.match(
  entityDesigner,
  /const showSystemFields = ref\(true\)/,
  '实体设计器应默认展示系统字段'
)
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
;[
  'title="常用设置"',
  'title="可靠性与失败策略"',
  'title="说明与参数"',
  "import SettingsSection from '@/components/SettingsSection.vue'"
].forEach((marker) => {
  assert.ok(flowActionPanel.includes(marker), `流程动作配置缺少常用优先或低频折叠分组: ${marker}`)
})
const flowActionGroupPositions = [
  flowActionPanel.indexOf('title="常用设置"'),
  flowActionPanel.indexOf('title="可靠性与失败策略"'),
  flowActionPanel.indexOf('title="说明与参数"')
]
assert.deepEqual(
  flowActionGroupPositions,
  [...flowActionGroupPositions].sort((left, right) => left - right),
  '流程动作应先显示常用设置，再显示可靠性和说明参数'
)
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
assert.match(processDesign, /全局(?:流程)?动作[\s\S]*scope-type="PROCESS"/, '流程设计器应提供全局流程动作入口')
;[
  '@click="globalActionVisible = true"',
  '>全局动作',
  '@click="handleSaveXML"',
  '>查看 XML'
].forEach((marker) => {
  assert.ok(processDesign.includes(marker), `流程设计器顶部缺少直接可见的工具操作: ${marker}`)
})
assert.equal(processDesign.includes('handleAdvancedCommand'), false, '流程设计器不应再通过“高级”下拉隐藏全局动作和 XML')
;[
  'class="node-config-panel"',
  'nodeConfigVisible && selectedElement',
  'class="node-config-trigger"',
  'openNodeConfig()'
].forEach((marker) => {
  assert.ok(processDesign.includes(marker), `流程节点配置缺少点击节点打开且保留状态的停靠面板: ${marker}`)
})
assert.equal(processDesign.includes('class="config-panel"'), false, '流程设计器不应继续保留固定节点配置栏')
assert.ok(processDesign.includes("name || '未命名节点'"), '流程节点面板不应使用技术 ID 作为未命名节点标题')
const nodeConfigPanelSource = readFileSync(path.join(root, 'src/components/NodeConfigPanel.vue'), 'utf8')
;[
  'min-height: 0',
  'overflow-y: auto',
  'scrollbar-gutter: stable',
  '.config-tabs :deep(.el-tabs__content)'
].forEach((marker) => {
  assert.ok(nodeConfigPanelSource.includes(marker), `流程节点配置缺少纵向滚动能力: ${marker}`)
})

const processListSource = readFileSync(path.join(root, 'src/views/ProcessList.vue'), 'utf8')
;[
  'title="发布后迁移"',
  ':default-expanded="false"',
  "import SettingsSection from '@/components/SettingsSection.vue'",
  'prop="processName" label="流程名称" min-width="170" show-overflow-tooltip',
  'prop="processKey" label="流程标识" min-width="170" show-overflow-tooltip'
].forEach((marker) => {
  assert.ok(processListSource.includes(marker), `流程发布弹窗缺少低频迁移折叠分组: ${marker}`)
})

const configMigrationSource = readFileSync(path.join(root, 'src/views/system/ConfigMigration.vue'), 'utf8')
const appShell = readFileSync(path.join(root, 'src/App.vue'), 'utf8')
assert.match(
  appShell,
  /\.el-step\.is-simple \.el-step__title[\s\S]{0,140}max-width:\s*none[\s\S]{0,140}white-space:\s*nowrap/,
  '配置迁移的五阶段步骤标题应保持单行，避免桌面端被组件默认宽度压缩'
)

assert.match(
  appShell,
  /@media \(max-width: 1366px\)[\s\S]*\.el-table-fixed-column--right[\s\S]*position: static !important/,
  '常见桌面与平板宽度下不应让固定列覆盖状态、时间等业务列'
)

const userManagement = readFileSync(path.join(root, 'src/views/system/User.vue'), 'utf8')
const roleManagement = readFileSync(path.join(root, 'src/views/system/Role.vue'), 'utf8')
const temporaryPasswordNotice = readFileSync(path.join(root, 'src/components/TemporaryPasswordNotice.vue'), 'utf8')
for (const [name, source] of [['用户管理', userManagement], ['角色管理', roleManagement]]) {
  assert.ok(source.includes(':formatter="formatDateColumn"'), `${name}应格式化创建时间`)
  assert.ok(source.includes('<TemporaryPasswordNotice'), `${name}的新增用户流程应展示临时密码交付说明`)
}
assert.ok(temporaryPasswordNotice.includes('一次性临时密码'), '新增用户流程应解释临时密码交付方式')
assert.ok(roleManagement.includes('<RoleTableActions'), '角色列表应收敛为主操作与更多菜单')
assert.ok(roleManagement.includes('label="操作" width="160"'), '角色列表操作列应适配常见桌面宽度')

const nodeConfigPanel = readFileSync(path.join(root, 'src/components/NodeConfigPanel.vue'), 'utf8')
assert.equal(nodeConfigPanel.includes('<span class="node-id">'), false, '流程节点 ID 不应重复占用属性抽屉首屏')
;['FlowConditionGroupEditor', 'conditionGroupConfig', 'conditionRoot', 'buildFlowConditionExpression'].forEach((marker) => {
  assert.ok(nodeConfigPanel.includes(marker), `流程条件配置缺少条件组能力: ${marker}`)
})
;[
  'title="技术信息"',
  "import SettingsSection from '@/components/SettingsSection.vue'"
].forEach((marker) => {
  assert.ok(nodeConfigPanel.includes(marker), `流程节点设置缺少折叠技术信息: ${marker}`)
})
assert.equal(nodeConfigPanel.includes('node-config-summary'), false, '流程节点配置不应展示占用首屏空间的摘要卡片')
;[
  'class="node-config-panel__meta"',
  'nodeConfigTypeText',
  'nodeConfigTypeDesc',
  'getNodeTypeText',
  'getNodeTypeDescription'
].forEach((marker) => {
  assert.ok(processDesign.includes(marker), `流程节点类型应展示在顶部标题栏: ${marker}`)
})
const nodeTabPatterns = [
  ['常用', /<span>常用<\/span>/],
  ['执行人', /<span>执行人<\/span>/],
  ['表单', /<span>表单<\/span>/],
  ['审批', /<span>审批(?:配置)?<\/span>/],
  ['知会', /<span>知会<\/span>/],
  ['流程动作', /<span>流程动作<\/span>/],
  ['高级', /<span>高级<\/span>/]
]
const nodeTabPositions = nodeTabPatterns.map(([label, pattern]) => {
  const position = pattern.exec(nodeConfigPanel)?.index ?? -1
  assert.ok(position >= 0, `流程节点配置缺少适用页签: ${label}`)
  return position
})
assert.deepEqual(
  nodeTabPositions,
  [...nodeTabPositions].sort((left, right) => left - right),
  '流程节点页签应按常用、执行人、表单、审批、知会、流程动作、高级排序'
)
;['title="办理方式"', 'title="参与人员"', 'title="完成规则"', 'title="技术参数"'].forEach((marker) => {
  assert.ok(nodeConfigPanel.includes(marker), `多人办理配置缺少任务分组: ${marker}`)
})
;['ENTITY_NOT_BOUND_MESSAGE', 'isEntityNotBoundError', 'entityFormsLoadingPromise', 'silentError: true'].forEach((marker) => {
  assert.ok(nodeConfigPanel.includes(marker), `流程未绑定实体时缺少预期状态去重或静默处理: ${marker}`)
})
;[
  "import { processApi } from '@/api/process'",
  'processApi.getPublishedList()',
  "String(process.id || '') !== String(props.processId || '')",
  'allow-create',
  ':loading="subProcessesLoading"'
].forEach((marker) => {
  assert.ok(nodeConfigPanel.includes(marker), `调用活动缺少真实已发布子流程选择能力: ${marker}`)
})
assert.equal(
  nodeConfigPanel.includes("{ key: 'seal_process', name: '盖章流程' }"),
  false,
  '调用活动不应继续展示写死的演示子流程'
)
assert.match(
  nodeConfigPanel,
  /if \(isUserTask\.value \|\| isStartEvent\.value\) \{[\s\S]{0,500}entityFormIds/,
  '默认实体表单只应绑定到开始事件或用户任务'
)
assert.equal(
  nodeConfigPanel.includes("delegateExpression: '${ccNotificationDelegate}'"),
  false,
  '节点知会不能覆盖服务任务或发送任务的主实现'
)

;[
  'title="常用体验"',
  'title="访问范围"',
  'title="选择行为"',
  'title="查询实现"',
  'title="扩展渲染"',
  'title="查询项"',
  'title="列展示"',
  'title="高级列布局"',
  'title="数据与显示"',
  'title="高级模板"'
].forEach((marker) => {
  assert.ok(listDesigner.includes(marker), `列表设置缺少常用优先或高级折叠分组: ${marker}`)
})
const listFieldTableSource = listDesigner.match(
  /<el-table[\s\S]*?class="field-config-table"[\s\S]*?<\/el-table>/
)?.[0] || ''
;['label="查询方式"', 'label="数据源"', 'label="渲染组件"', 'label="宽度"', 'label="对齐"'].forEach((marker) => {
  assert.equal(listFieldTableSource.includes(marker), false, `列表字段低频设置不应继续挤占主表格: ${marker}`)
})
;['field-purpose-controls', 'fieldConfigSummary(row)', 'openFieldConfig(row)'].forEach((marker) => {
  assert.ok(listFieldTableSource.includes(marker), `列表字段主表缺少用途、摘要或单项设置入口: ${marker}`)
})
assert.ok(
  listFieldTableSource.includes('label="当前配置" min-width="320"'),
  '列表字段主表应让当前配置列弹性占满剩余空间'
)
assert.ok(
  listDesigner.includes('.field-config-table {') && listDesigner.includes('width: 100%;'),
  '列表字段主表应铺满配置面板'
)
;['label="查询方式"', 'label="字段数据源"', 'label="渲染组件"', 'label="列宽"', 'label="对齐"'].forEach((marker) => {
  assert.ok(listDesigner.includes(marker), `列表字段设置弹窗缺少迁移后的配置项: ${marker}`)
})
assert.ok(listButtonConfig.includes('title="高级映射"'), '打开列表按钮应折叠可信上下文关系与选择回调')

const configSchemaEditor = readFileSync(path.join(root, 'src/components/ConfigSchemaEditor.vue'), 'utf8')
;[
  'schemaGroups',
  'visibleWhen',
  'priorityValue',
  'orderValue',
  'title="',
  "import SettingsSection from '@/components/SettingsSection.vue'"
].forEach((marker) => {
  assert.ok(configSchemaEditor.includes(marker), `扩展配置 Schema 缺少分组、排序或条件显示能力: ${marker}`)
})
const linkageConfigPanel = readFileSync(path.join(root, 'src/components/LinkageConfigPanel.vue'), 'utf8')
;['label="显示与状态"', 'label="值与计算"', 'label="选项"', '受控 Provider / Connector'].forEach((marker) => {
  assert.ok(linkageConfigPanel.includes(marker), `字段联动缺少合并页签或受控数据源提示: ${marker}`)
})

;['title="基本规则"', 'title="适用对象"', 'title="可见数据范围"', 'isSelectedFieldStructureLocked'].forEach((marker) => {
  assert.ok(entitySettingsDesigner.includes(marker), `实体字段或权限规则缺少常用优先与锁定摘要: ${marker}`)
})

assert.match(
  formDesigner,
  /const selectedNodeHasLockedBinding = computed\(\(\) => \{[\s\S]*if \(!isEditableFieldNode\.value\) return false/,
  '只有字段、子表和明细节点可以显示业务绑定锁定提示'
)

const processProgress = readFileSync(path.join(root, 'src/views/ProcessProgress.vue'), 'utf8')
assert.match(processProgress, /userStore\.isSuperAdmin[\s\S]*FlowActionExecutionLog/, '流程进度页应仅为超级管理员展示动作执行记录')
const flowActionExecutionLog = readFileSync(path.join(root, 'src/components/FlowActionExecutionLog.vue'), 'utf8')
;['解析后参数', '执行结果', '触发上下文', '执行过程', 'retryExecution'].forEach((marker) => {
  assert.ok(flowActionExecutionLog.includes(marker), `流程动作执行日志缺少详情或重试能力: ${marker}`)
})

const configMigration = readFileSync(path.join(root, 'src/views/system/ConfigMigration.vue'), 'utf8')
;[
  '选择与校验',
  '发布包',
  '导入与发布',
  '影响对比',
  '1. 选择配置',
  '5. 发布结果',
  'exportPackage',
  'uploadPackage',
  'analyzeImport',
  'publishImport',
  'rollbackImport',
  'saveMappings'
].forEach((marker) => {
  assert.ok(configMigration.includes(marker), `配置迁移页面缺少闭环能力: ${marker}`)
})

const systemAudit = readFileSync(path.join(root, 'src/views/system/SystemAudit.vue'), 'utf8')
;[
  '系统日志',
  'getSystemAuditLogs',
  'getSystemAuditLogDetail',
  'exportSystemAuditLogs',
  'system:audit:detail',
  'system:audit:export',
  '变更前',
  '变更后',
  'Trace ID'
].forEach((marker) => {
  assert.ok(systemAudit.includes(marker), `系统日志页面缺少查询、详情或导出能力: ${marker}`)
})
assert.ok(!systemAudit.includes('<h2>系统日志</h2>'), '系统日志页面不得重复渲染路由标题')
;['class="search-card"', 'class="table-toolbar"', 'searchExpanded', 'ArrowDown', 'ArrowUp'].forEach((marker) => {
  assert.ok(systemAudit.includes(marker), `系统日志页面缺少标准查询折叠或列表工具栏结构: ${marker}`)
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
  'src/views/system/DevGuide.vue': ['ListFieldDataProvider', 'FIELD_TEMPLATE', 'registerCellComponent', 'DemoRiskProgressCell', 'test:demo:real', 'SettingsSection'],
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
    '节点拖拽',
    '右上角手柄',
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
    '运行时回退',
    '常用配置优先',
    '常用属性、布局与层级、数据源、校验、模式与权限、关系与子表、复用与扩展',
    '访问范围、选择行为、查询实现',
    '显示与状态、值与计算、选项',
    '更多设置'
  ],
  'src/data/user-manual/process.js': [
    '节点配置页签与首屏原则',
    '点击节点打开属性抽屉',
    '关闭抽屉不会取消当前选择',
    '常用、执行人、表单、审批、知会、流程动作、高级',
    '技术信息',
    '按节点适用',
    '发布后迁移默认折叠'
  ],
  'src/views/system/DevGuide.vue': [
    '表单节点拖拽必须走平台递归拖拽容器',
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
    '临时回退旧配置',
    "group: 'common'",
    "group: 'advanced'",
    'visibleWhen',
    '`order`',
    '`priority`',
    'SettingsSection',
    'defaultExpanded=false'
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
    '设计器统一通过节点右上角手柄拖拽',
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

const extensionEntry = readFileSync(path.join(root, 'src/extensions/index.js'), 'utf8')
;[
  'registerApplicationExtensions',
  'getBundledExtensionManifest',
  'validateBundledExtensionManifest'
].forEach((marker) => {
  assert.ok(extensionEntry.includes(marker), `统一扩展入口缺少公共能力: ${marker}`)
})
const extensionManifest = readFileSync(path.join(root, 'src/extensions/manifest.js'), 'utf8')
;[
  'permissions',
  'migrationSupported',
  'deprecatedAt'
].forEach((marker) => {
  assert.ok(extensionManifest.includes(marker), `扩展治理清单缺少字段: ${marker}`)
})
const extensionRegister = readFileSync(path.join(root, 'src/extensions/register.js'), 'utf8')
assert.ok(
  extensionRegister.includes('registerApplicationExtensions')
    && extensionRegister.includes('registerDemoExtensions'),
  '扩展启动入口必须集中控制演示扩展注册'
)
const mainSource = readFileSync(path.join(root, 'src/main.js'), 'utf8')
assert.ok(
  mainSource.includes('registerApplicationExtensions')
    && mainSource.includes('./extensions/register')
    && mainSource.includes('VITE_ENABLE_DEMO_EXTENSIONS'),
  '应用启动必须通过轻量注册入口启动，并由环境开关控制演示扩展'
)

const pagedEntityDataList = readFileSync(path.join(root, 'src/views/entity/EntityDataList.vue'), 'utf8')
;[
  'entityListRuntimeApi.query',
  'runtimeListKey',
  'runtimeScene',
  'total.value = Number(res?.total'
].forEach((marker) => {
  assert.ok(pagedEntityDataList.includes(marker), `实体数据列表缺少服务端分页能力: ${marker}`)
})

const uiConfigPublishDialog = readFileSync(
  path.join(root, 'src/components/UiConfigPublishDialog.vue'),
  'utf8'
)
assert.ok(
  uiConfigPublishDialog.includes('所有通过发布校验的表单变更都可热修复')
    && uiConfigPublishDialog.includes('所有通过发布校验的列表变更都可热修复')
    && uiConfigPublishDialog.includes('REVIEW 仅提示风险，不阻止发布'),
  '列表和表单热修复界面应明确 REVIEW 仅提示且不阻止发布'
)
assert.doesNotMatch(
  uiConfigPublishDialog,
  /仅允许兼容修改/,
  '流程表单热修复界面不得继续声称只允许兼容修改'
)
;[
  'override-form',
  'canOverride',
  'entity:ui-config:hotfix:override',
  '已阻断'
].forEach((marker) => {
  assert.ok(
    !uiConfigPublishDialog.includes(marker),
    `热修复发布界面不得保留风险覆盖阻塞逻辑: ${marker}`
  )
})

const entityFormListSource = readFileSync(
  path.join(root, 'src/views/EntityFormList.vue'),
  'utf8'
)
;[
  [
    entityList,
    ['handleDesign(row)', "'设计'", 'handleRepublish(row)', '重新发布', 'handleListConfig(row)', '列表', 'handleForm(row)', '表单']
  ],
  [
    processListSource,
    ['handleDesign(row)', '设计', 'handleEdit(row)', '编辑', 'handlePublish(row)', '发布', 'handleDisable(row)', '禁用']
  ],
  [
    entityFormListSource,
    ['handleDesign(row)', '设计', 'handlePreview(row)', '预览', 'handleEdit(row)', '编辑', 'handleSetDefault(row)', '默认', 'handleCopy(row)', '复制', 'handleInitConfig(row)', '配置', 'handleDelete(row)', '删除']
  ]
].forEach(([source, markers]) => {
  assert.doesNotMatch(source, /<el-dropdown(?:\s|>)/, '列表操作不得继续收纳到更多下拉')
  markers.forEach((marker) => {
    assert.ok(source.includes(marker), `操作列缺少直接操作或精简文案: ${marker}`)
  })
})

const processManualSource = readFileSync(
  path.join(root, 'src/data/user-manual/process.js'),
  'utf8'
)
assert.ok(
  processManualSource.includes('列表和表单都只使用 SAFE/REVIEW')
    && processManualSource.includes('REVIEW 仅提示风险，不阻止发布'),
  '流程手册应说明列表和表单统一使用 SAFE/REVIEW'
)
assert.doesNotMatch(
  processManualSource,
  /BLOCKED 立即停止并改用 STANDARD/,
  '流程手册不得保留表单 BLOCKED 硬阻断说明'
)

const extensionManagementSource = readFileSync(
  path.join(root, 'src/views/system/ExtensionManagement.vue'),
  'utf8'
)
;[
  "path: '/system/extensions'",
  "requiredPermissions: ['system:extension:list']"
].forEach((marker) => {
  assert.ok(
    routerSource.includes(marker),
    `扩展管理必须作为系统管理路由并受权限控制: ${marker}`
  )
})
;[
  'normalizeRouteType(route.query.type)',
  'searchExpanded',
  'table-toolbar',
  'extensionCatalogApi.manage',
  'personResolverApi.saveConfig',
  'getManagedExtensionManifest',
  'isPlatformBuiltInUiExtension'
].forEach((marker) => {
  assert.ok(
    extensionManagementSource.includes(marker),
    `统一扩展管理页面缺少能力: ${marker}`
  )
})

const extensionPickerSource = readFileSync(
  path.join(root, 'src/components/ExtensionCapabilityPicker.vue'),
  'utf8'
)
;[
  'remote',
  'limit: keyword?.trim() ? 20 : 6',
  'sortRecentFirst',
  'extension_recent_',
  "props.capabilityType.startsWith('UI_')"
].forEach((marker) => {
  assert.ok(
    extensionPickerSource.includes(marker),
    `扩展配置选择器缺少按需加载能力: ${marker}`
  )
})

const realAcceptanceScripts = [
  'scripts/real-acceptance-preflight.mjs',
  'scripts/e2e-real-workflow.mjs',
  'scripts/real-ui-config-release.mjs',
  'scripts/real-workflow-config-closure.mjs',
  'scripts/real-workflow-node-forms.mjs',
  'scripts/real-workflow-closure.mjs',
  'scripts/real-flow-action-timing.mjs',
  'scripts/real-dynamic-extension-demo.mjs',
  'scripts/visual-acceptance-real.mjs'
]
realAcceptanceScripts.forEach((scriptFile) => {
  const source = readFileSync(path.join(root, scriptFile), 'utf8')
  assert.ok(
    source.includes('process.env.TEST_USERNAME')
      && source.includes('process.env.TEST_PASSWORD'),
    `真实验收脚本必须支持通过环境变量注入凭据: ${scriptFile}`
  )
  assert.doesNotMatch(
    source,
    /username:\s*['"]admin['"]\s*,\s*password:\s*['"]admin['"]/,
    `真实验收脚本不得在登录请求中写死 admin/admin: ${scriptFile}`
  )
  assert.doesNotMatch(
    source,
    /flowable:(?:assignee|candidateUsers)=["']admin["']/,
    `真实验收流程的办理人必须跟随注入的测试账号: ${scriptFile}`
  )
  assert.doesNotMatch(
    source,
    /\b(?:const|let|var)\s+process\b/,
    `真实验收脚本不得用 process 命名业务对象，以免遮蔽 Node.js process: ${scriptFile}`
  )
  assert.ok(
    !source.includes('/entity-flow-status/list/'),
    `真实验收脚本不得调用已移除的状态映射旧接口: ${scriptFile}`
  )
  assert.doesNotMatch(
    source,
    /api\(['"]PUT['"],\s*`\/process-entity-status-mappings\/process\/\$\{[^}]+\}`\s*,/,
    `真实验收脚本必须调用 POST /process-entity-status-mappings/process/{id}/update: ${scriptFile}`
  )
  assert.doesNotMatch(
    source,
    /api\(['"]PUT['"],\s*`\/entity\/\$\{[^}]+\}\/workflow-binding`\s*,/,
    `真实验收脚本必须调用 POST /entity/{id}/workflow-binding/update: ${scriptFile}`
  )
  ;['/flow-actions', '/flow-action-handlers', '/flow-action-executions'].forEach((endpoint) => {
    assert.equal(
      source.includes(endpoint),
      false,
      `真实验收脚本不得调用已移除的流程动作旧接口 ${endpoint}: ${scriptFile}`
    )
  })
})

const visualAcceptanceSource = readFileSync(
  path.join(root, 'scripts/visual-acceptance-real.mjs'),
  'utf8'
)
assert.ok(
  visualAcceptanceSource.includes('docs/dynamic-extension-demo/latest.json'),
  '真实视觉验收必须读取当轮动态扩展真实夹具'
)
assert.doesNotMatch(
  visualAcceptanceSource,
  /project_nitiation|\/entity\/list\//,
  '真实视觉验收不得依赖已经删除的历史实体路由'
)
;[
  '`/entity/design/${fixture.entityId}`',
  '`/entity-list-config/${fixture.entityId}`',
  '`/entity-list/${fixture.entityCode}/${listKey}`',
  '`/entity-list-config/design/${fixture.listConfigId}`',
  '`/entity-form/list-by-entity/${fixture.entityId}`',
  '`/entity-form/design/${fixture.formId}?entityId=${fixture.entityId}`',
  '`/process/design/${fixture.processId}`'
].forEach((marker) => {
  assert.ok(
    visualAcceptanceSource.includes(marker),
    `真实视觉验收必须覆盖动态夹具页面: ${marker}`
  )
})

const dynamicExtensionRouteSource = readFileSync(
  path.join(root, 'scripts/real-dynamic-extension-demo.mjs'),
  'utf8'
)
assert.ok(
  dynamicExtensionRouteSource.includes('listRoute: `/entity-list/${entityCode}/${listKey}`'),
  '动态扩展验收证据必须记录带 listKey 的现行实体列表运行地址'
)
assert.doesNotMatch(
  dynamicExtensionRouteSource,
  /listRoute:\s*`\/entity\/list\//,
  '动态扩展验收不得再生成已经废弃的实体列表地址'
)

const packageSource = readFileSync(path.join(root, 'package.json'), 'utf8')
;[
  '"test:acceptance:preflight"',
  '"test:acceptance:real"',
  'test:real-ui-config',
  'test:workflow:real',
  'test:workflow:config-real',
  'test:workflow:node-forms-real',
  'test:workflow:actions-real',
  'test:visual:real'
].forEach((marker) => {
  assert.ok(
    packageSource.includes(marker),
    `真实验收一键入口缺少步骤: ${marker}`
  )
})

const workflowConfigClosureSource = readFileSync(
  path.join(root, 'scripts/real-workflow-config-closure.mjs'),
  'utf8'
)
const workflowNodeFormsSource = readFileSync(
  path.join(root, 'scripts/real-workflow-node-forms.mjs'),
  'utf8'
)
const workflowActionTimingSource = readFileSync(
  path.join(root, 'scripts/real-flow-action-timing.mjs'),
  'utf8'
)
const dynamicExtensionDemoSource = readFileSync(
  path.join(root, 'scripts/real-dynamic-extension-demo.mjs'),
  'utf8'
)
;[
  "extensionType: 'FORM'",
  "extensionType: 'LIST'",
  'customComponentVersion: formExtension.version',
  'customComponentSnapshotVersion: formExtension.snapshotVersion',
  '`/entity-forms/${form.id}/publish`',
  '`/entity-list-config/${listConfig.id}/publish`',
  'viewConfig: {\n      search:'
].forEach((marker) => {
  assert.ok(
    dynamicExtensionDemoSource.includes(marker),
    `动态扩展真实验收必须登记版本并发布表单和列表: ${marker}`
  )
})
;[
  'prepareActionHandlers()',
  'restoreActionHandlers()',
  'handlerConfigBackups',
  'actionDefinitionId: definition.definitionId',
  "assert.deepEqual(restoreErrors, []"
].forEach((marker) => {
  assert.ok(
    workflowActionTimingSource.includes(marker),
    `流程动作真实验收必须按目录可见性准备并恢复测试处理器: ${marker}`
  )
})
;[
  'await publishForm(defaultForm',
  'await publishForm(formA',
  'await publishForm(formC',
  "assert.equal(published.status, 'ACTIVE'"
].forEach((marker) => {
  assert.ok(
    workflowNodeFormsSource.includes(marker),
    `节点表单真实验收必须先发布全部被引用表单: ${marker}`
  )
})
assert.ok(
  workflowNodeFormsSource.indexOf('await publishForm(formC')
    < workflowNodeFormsSource.indexOf('`/process/${workflowProcess.id}/publish`'),
  '节点表单真实验收必须在流程发布前完成全部表单发布'
)
;[
  'reset?.temporaryPassword',
  "'/auth/change-password'",
  'activateApprover('
].forEach((marker) => {
  assert.ok(
    workflowConfigClosureSource.includes(marker),
    `跨用户流程验收必须完成随机临时密码激活闭环: ${marker}`
  )
})
assert.doesNotMatch(
  workflowConfigClosureSource,
  /login\([^,]+,\s*['"]123456['"]\)/,
  '跨用户流程验收不得假设重置密码固定为 123456'
)
assert.doesNotMatch(
  workflowConfigClosureSource,
  /api\(['"]PUT['"],\s*`\/system\/user\/\$\{[^}]+\}\/reset-password`\)/,
  '重置用户密码接口仅支持 POST，真实验收脚本不得使用 PUT'
)
assert.doesNotMatch(
  workflowConfigClosureSource,
  /api\(['"]PUT['"],\s*`\/system\/role\/\$\{[^}]+\}\/menus`\s*,/,
  '保存角色菜单接口仅支持 POST，真实验收脚本不得使用 PUT'
)

console.log('page configuration audit passed')
