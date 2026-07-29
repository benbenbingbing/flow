import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  registerCustomListComponent,
  getCustomListComponent,
  hasCustomListComponent,
  getRegisteredCustomListNames,
  registerCustomFormComponent,
  getCustomFormComponent,
  hasCustomFormComponent,
  getRegisteredCustomFormNames,
  getCustomListDescriptor,
  getCustomFormDescriptor
} from '@/utils/customComponentRegistry.js'
import {
  getFormNodeComponent,
  getFormNodeComponentOptions,
  getFormNodeDescriptor,
  hasFormNodeComponent,
  registerFormNodeComponent,
  resolveFormNodeDescriptor
} from '@/utils/formNodeRegistry.js'
import {
  registerListToolbarAction,
  getListToolbarAction,
  hasListToolbarAction,
  registerListRowAction,
  getListRowAction,
  hasListRowAction
} from '@/utils/listActionRegistry.js'
import {
  registerListButtonComponent,
  getListButtonComponent,
  hasListButtonComponent
} from '@/utils/listButtonComponentRegistry.js'
import {
  registerFormInitializer,
  getFormInitializer,
  hasFormInitializer,
  getRegisteredFormInitializerNames
} from '@/utils/formInitializerRegistry.js'
import { executeFormInitializer } from '@/utils/formInitializer.js'
import {
  formatLinkageConditionLiteral,
  LinkageEngine,
  normalizeLegacyBooleanComparisons
} from '@/utils/linkageEngine.js'
import {
  getNodeTypeDescription,
  getNodeTypeTag,
  getNodeTypeText,
  buildAssigneeConfig,
  getProcessConditionFieldCode,
  getProcessConditionFieldLabel,
  getProcessConditionFieldType
} from '@/shared/process-config'
import {
  ENTITY_FIELD_TYPES,
  getEntityFieldTypeLabel,
  getEntityFieldTypeTag
} from '@/shared/entity-design'
import {
  applyPermissionTransferChange,
  buildPermissionTreeView,
  flattenPermissionMenuTree,
  sanitizePermissionKeys
} from '@/shared/role-permission-transfer'

const DemoComponent = { name: 'DemoComponent' }
const DemoForm = { name: 'DemoForm' }
const DemoButton = { name: 'DemoButton' }
const DemoListV1 = { name: 'DemoListV1' }
const DemoListV2 = { name: 'DemoListV2' }
const DemoNodeV1 = { name: 'DemoNodeV1' }
const DemoNodeV2 = { name: 'DemoNodeV2' }

const permissionOptions = flattenPermissionMenuTree([
  {
    id: 'system',
    menuName: '系统管理',
    menuType: 'M',
    children: [
      { id: 'user', menuName: '用户管理', menuType: 'C', children: [] },
      {
        id: 'role',
        menuName: '角色管理',
        menuType: 'C',
        children: [
          { id: 'role-edit', menuName: '编辑角色', menuType: 'F', perm: 'system:role:edit' }
        ]
      }
    ]
  }
])
assert.equal(permissionOptions.length, 4)
assert.equal(permissionOptions.find(item => item.id === 'role-edit').fullPath, '系统管理 / 角色管理 / 编辑角色')
assert.deepEqual(permissionOptions.find(item => item.id === 'role').descendantIds, ['role-edit'])
assert.deepEqual(sanitizePermissionKeys(['missing', 'role'], permissionOptions), ['role'])
assert.deepEqual(
  applyPermissionTransferChange(['role-edit'], 'right', ['role-edit'], permissionOptions),
  ['system', 'role', 'role-edit']
)
assert.deepEqual(
  applyPermissionTransferChange(['system'], 'right', ['system'], permissionOptions),
  ['system', 'user', 'role', 'role-edit']
)
assert.deepEqual(
  applyPermissionTransferChange(['system', 'user', 'role', 'role-edit'], 'left', ['role'], permissionOptions),
  ['system', 'user']
)
const availablePermissionTree = buildPermissionTreeView([
  {
    id: 'system',
    menuName: '系统管理',
    menuType: 'M',
    children: [
      { id: 'user', menuName: '用户管理', menuType: 'C', children: [] },
      {
        id: 'role',
        menuName: '角色管理',
        menuType: 'C',
        children: [
          { id: 'role-edit', menuName: '编辑角色', menuType: 'F', perm: 'system:role:edit' }
        ]
      }
    ]
  }
], ['system', 'role', 'role-edit'], 'available')
assert.equal(availablePermissionTree.length, 1)
assert.equal(availablePermissionTree[0].contextOnly, true)
assert.equal(availablePermissionTree[0].children.length, 1)
assert.equal(availablePermissionTree[0].children[0].id, 'user')
assert.equal(availablePermissionTree[0].children[0].transferDisabled, false)

const assignedPermissionTree = buildPermissionTreeView([
  {
    id: 'system',
    menuName: '系统管理',
    menuType: 'M',
    children: [
      { id: 'user', menuName: '用户管理', menuType: 'C', children: [] },
      {
        id: 'role',
        menuName: '角色管理',
        menuType: 'C',
        children: [
          { id: 'role-edit', menuName: '编辑角色', menuType: 'F', perm: 'system:role:edit' }
        ]
      }
    ]
  }
], ['role-edit'], 'assigned')
assert.equal(assignedPermissionTree[0].id, 'system')
assert.equal(assignedPermissionTree[0].transferDisabled, true)
assert.equal(assignedPermissionTree[0].children[0].id, 'role')
assert.equal(assignedPermissionTree[0].children[0].contextOnly, true)
assert.equal(assignedPermissionTree[0].children[0].children[0].fullPath, '系统管理 / 角色管理 / 编辑角色')

registerCustomListComponent('functionalList', DemoComponent, {
  label: '功能列表',
  configSchema: [{ key: 'cardSize', label: '卡片尺寸', type: 'select' }]
})
assert.equal(hasCustomListComponent('functionalList'), true)
assert.equal(getCustomListComponent('functionalList'), DemoComponent)
assert.ok(getRegisteredCustomListNames().includes('functionalList'))
assert.equal(getCustomListDescriptor('functionalList').label, '功能列表')

registerCustomListComponent('versionedList', DemoListV1, { version: 1 })
registerCustomListComponent('versionedList', DemoListV2, { version: 2 })
assert.equal(getCustomListComponent('versionedList', 1), DemoListV1)
assert.equal(getCustomListComponent('versionedList', 2), DemoListV2)
assert.equal(getCustomListComponent('versionedList'), DemoListV2)
assert.equal(getCustomListComponent('versionedList', 'invalid'), undefined)
assert.equal(hasCustomListComponent('versionedList', 3), false)

registerCustomFormComponent('functionalForm', DemoForm, {
  label: '功能表单',
  supportedModes: ['create', 'edit', 'approve', 'view']
})
assert.equal(hasCustomFormComponent('functionalForm'), true)
assert.equal(getCustomFormComponent('functionalForm'), DemoForm)
assert.ok(getRegisteredCustomFormNames().includes('functionalForm'))
assert.deepEqual(getCustomFormDescriptor('functionalForm').supportedModes, ['create', 'edit', 'approve', 'view'])

registerCustomFormComponent('versionedForm', DemoForm, { version: 1 })
registerCustomFormComponent('versionedForm', DemoComponent, { version: 2 })
assert.equal(getCustomFormComponent('versionedForm', 1), DemoForm)
assert.equal(getCustomFormComponent('versionedForm', 2), DemoComponent)
assert.equal(getCustomFormComponent('versionedForm'), DemoComponent)
assert.equal(getCustomFormComponent('versionedForm', 'invalid'), undefined)
assert.equal(hasCustomFormComponent('versionedForm', 3), false)

registerFormNodeComponent('versionedNode', DemoNodeV1, {
  version: 1,
  nodeTypes: ['FIELD'],
  supportedBindings: ['ENTITY_FIELD']
})
registerFormNodeComponent('versionedNode', DemoNodeV2, {
  version: 2,
  nodeTypes: ['FIELD'],
  supportedBindings: ['ENTITY_FIELD']
})
assert.equal(getFormNodeComponent('versionedNode', 1), DemoNodeV1)
assert.equal(getFormNodeComponent('versionedNode', 2), DemoNodeV2)
assert.equal(getFormNodeComponent('versionedNode'), DemoNodeV2)
assert.equal(getFormNodeComponent('versionedNode', 'invalid'), undefined)
assert.equal(hasFormNodeComponent('versionedNode', 3), false)
assert.equal(
  resolveFormNodeDescriptor({
    nodeType: 'FIELD',
    bindingType: 'ENTITY_FIELD',
    componentName: 'versionedNode',
    componentVersion: 1
  })?.component,
  DemoNodeV1
)
assert.equal(
  resolveFormNodeDescriptor({
    nodeType: 'FIELD',
    bindingType: 'ENTITY_FIELD',
    componentName: 'versionedNode'
  })?.component,
  DemoNodeV2
)
assert.equal(
  resolveFormNodeDescriptor({
    nodeType: 'FIELD',
    bindingType: 'ENTITY_FIELD',
    componentName: 'versionedNode',
    componentVersion: 3
  }),
  null
)
assert.equal(getFormNodeDescriptor('versionedNode', 1)?.version, 1)
assert.deepEqual(
  getFormNodeComponentOptions()
    .filter(item => item.name === 'versionedNode')
    .map(item => item.version),
  [2]
)

let toolbarCalled = false
const toolbarHandler = (context) => {
  toolbarCalled = context.entityCode === 'project'
}
registerListToolbarAction('exportDemo', toolbarHandler)
assert.equal(hasListToolbarAction('exportDemo'), true)
getListToolbarAction('exportDemo')({ entityCode: 'project' })
assert.equal(toolbarCalled, true)

let rowCalled = false
const rowHandler = (context) => {
  rowCalled = context.row.id === 'row-1'
}
registerListRowAction('rowDemo', rowHandler)
assert.equal(hasListRowAction('rowDemo'), true)
getListRowAction('rowDemo')({ row: { id: 'row-1' } })
assert.equal(rowCalled, true)

registerListButtonComponent('buttonDemo', DemoButton)
assert.equal(hasListButtonComponent('buttonDemo'), true)
assert.equal(getListButtonComponent('buttonDemo'), DemoButton)

const initializer = async (config, context) => ({ owner: context.userId, source: config.source })
registerFormInitializer('ownerInitializer', initializer)
assert.equal(hasFormInitializer('ownerInitializer'), true)
assert.deepEqual(await getFormInitializer('ownerInitializer')({ source: '功能测试' }, { userId: 'u1' }), { owner: 'u1', source: '功能测试' })
assert.ok(getRegisteredFormInitializerNames().includes('ownerInitializer'))
assert.deepEqual(await executeFormInitializer({}), {})
assert.deepEqual(await executeFormInitializer('{}'), {})

assert.deepEqual(
  LinkageEngine.getFieldLinkageRules({
    visibilityRule: "${status} == 'OPEN'",
    componentProps: JSON.stringify({ linkageRules: { disabledRule: "${locked} == '1'" } })
  }),
  { visibilityRule: "${status} == 'OPEN'", disabledRule: "${locked} == '1'" }
)
assert.equal(LinkageEngine.evaluateCondition("${amount} > 100 && ${status} == 'OPEN'", { amount: 120, status: 'OPEN' }), true)
assert.equal(LinkageEngine.evaluateCondition("${amount} > 100", { amount: 80 }), false)
assert.equal(LinkageEngine.evaluateCondition('', { amount: 80 }), true)
assert.equal(LinkageEngine.evaluateCondition("${urgent} == 'true'", { urgent: true }), true)
assert.equal(LinkageEngine.evaluateCondition("${urgent} == 'true'", { urgent: false }), false)
assert.equal(
  formatLinkageConditionLiteral({ fieldType: 'BOOLEAN', componentType: 'switch' }, 'true'),
  'true'
)
assert.equal(formatLinkageConditionLiteral({ fieldType: 'STRING' }, 'true'), '"true"')
assert.equal(normalizeLegacyBooleanComparisons("true == 'true'"), 'true == true')
const processConditionField = {
  fieldCode: 'urgent',
  fieldName: '是否加急',
  fieldType: 'BOOLEAN'
}
assert.equal(getProcessConditionFieldCode(processConditionField), 'urgent')
assert.equal(getProcessConditionFieldLabel(processConditionField), '是否加急')
assert.equal(getProcessConditionFieldType(processConditionField), 'boolean')
assert.equal(getProcessConditionFieldType({ fieldType: 'DECIMAL' }), 'number')

const linkageResult = LinkageEngine.processAllLinkages([
  { fieldCode: 'discount', visibilityRule: "${amount} > 100", disabledRule: "${locked} == true", requiredRule: "${status} == 'OPEN'" },
  { fieldCode: 'total', calculationFormula: '${amount} + ${fee}', calculationPrecision: 2 }
], { amount: 120, fee: 5, locked: true, status: 'OPEN' })
assert.equal(linkageResult.visibility.discount, true)
assert.equal(linkageResult.disabled.discount, true)
assert.equal(linkageResult.required.discount, true)
assert.equal(linkageResult.values.total, 125)

assert.equal(getNodeTypeText('bpmn:UserTask'), '用户任务')
assert.equal(getNodeTypeDescription('bpmn:ServiceTask').title, '服务任务')
assert.equal(getNodeTypeText('bpmn:EventBasedGateway'), '事件网关')
for (const gatewayType of [
  'bpmn:ExclusiveGateway',
  'bpmn:ParallelGateway',
  'bpmn:InclusiveGateway',
  'bpmn:EventBasedGateway'
]) {
  assert.notEqual(getNodeTypeDescription(gatewayType).title, '未知节点')
}
assert.equal(getNodeTypeTag('bpmn:StartEvent'), 'success')
assert.equal(getNodeTypeTag('bpmn:ExclusiveGateway'), 'warning')
assert.equal(buildAssigneeConfig({ assigneeType: 'user', assignee: 'zhangsan', candidateUsers: 'lisi' }).assigneeValue, 'zhangsan')
assert.equal(buildAssigneeConfig({ assigneeType: 'group', candidateGroups: 'finance' }).assigneeValue, 'finance')
assert.equal(buildAssigneeConfig({ assigneeType: 'expression', candidateUsers: '${starter}' }).assigneeValue, '${starter}')

assert.ok(ENTITY_FIELD_TYPES.length >= 20)
assert.equal(getEntityFieldTypeLabel('STRING'), '文本')
assert.equal(getEntityFieldTypeLabel('UNKNOWN_TYPE'), 'UNKNOWN_TYPE')
assert.equal(getEntityFieldTypeTag('STRING'), 'info')
assert.equal(getEntityFieldTypeTag('REFERENCE'), 'primary')

const apiExpectations = {
  'src/api/auth.js': ['login', 'getCurrentUser', 'logout', 'getPermissions'],
  'src/api/process.js': ['getList', 'getPublishedList', 'getById', 'create', 'update', 'delete', 'publish', 'getProcessProgress'],
  'src/api/entity.js': ['getList', 'getAll', 'getByCode', 'create', 'update', 'delete', 'publish', 'getListWithConfig', 'getDetail', 'save', 'exportData'],
  'src/api/entityListConfig.js': [
    'getByEntityId',
    'getById',
    'getExtensionOptions',
    'save',
    'patchMetadata',
    'patchAction',
    'deleteAction',
    'patchScene',
    'deleteScene',
    'publish',
    'delete'
  ],
  'src/api/entityListRuntime.js': ['getSchema', 'query', 'simulate'],
  'src/api/entityListScope.js': ['getConfiguration', 'createPolicy', 'createBinding', 'publish'],
  'src/api/processTask.js': ['getTodoList', 'getDoneList', 'getStatistics', 'completeTask', 'getTaskOperations', 'previewAddSign', 'addSignTask', 'cancelAddSign', 'ccTask', 'getMyCcList', 'markCcRead', 'withdrawProcess', 'terminateProcess'],
  'src/api/system/menu.ts': ['getMenuTree', 'getSidebarMenuTree', 'createMenu', 'updateMenu', 'deleteMenu', 'updateSort'],
  'src/api/system/user.ts': ['getUserList', 'createUser', 'updateUser', 'deleteUser', 'resetPassword'],
  'src/api/system/role.ts': ['getRoleList', 'createRole', 'updateRole', 'deleteRole', 'getRoleUsers', 'saveRoleMenus'],
  'src/api/system/group.ts': ['getGroupList', 'createGroup', 'updateGroup', 'deleteGroup', 'saveGroupUsers'],
  'src/api/system/dict.ts': ['getDictList', 'createDict', 'updateDict', 'deleteDict'],
  'src/api/system/audit.ts': ['getSystemAuditLogs', 'getSystemAuditLogDetail', 'exportSystemAuditLogs'],
  'src/api/system/openIntegration.js': [
    'integrationApplicationApi',
    'integrationWebhookApi',
    'integrationSecretApi',
    'integrationConnectorApi',
    'validate',
    'rotateCredential',
    'replay'
  ]
}

for (const [file, names] of Object.entries(apiExpectations)) {
  const source = readFileSync(file, 'utf8')
  for (const name of names) {
    assert.ok(source.includes(name), `${file} 缺少功能 API: ${name}`)
  }
}

const entityListConfigApiSource = readFileSync(
  'src/api/entityListConfig.js',
  'utf8'
)
for (const route of [
  '/actions/${actionId}/patch',
  '/actions/${actionId}/delete',
  '/scenes/${sceneId}/patch',
  '/scenes/${sceneId}/delete'
]) {
  assert.ok(
    entityListConfigApiSource.includes(route),
    `列表单项接口路径错误: ${route}`
  )
}

const entityListConfigPageSource = readFileSync(
  'src/views/EntityListConfig.vue',
  'utf8'
)
assert.ok(
  /patchMetadata\(formData\.value\.id,[\s\S]*?expectedRevision:\s*formData\.value\.revision/.test(
    entityListConfigPageSource
  ),
  '列表基本信息编辑未携带 expectedRevision'
)

const layoutSource = readFileSync('src/views/Layout.vue', 'utf8')
assert.ok(
  !layoutSource.includes('userStore.permissions.includes(required)'),
  '侧栏菜单不应再用按钮权限集合二次过滤实体菜单'
)

const menuPageSource = readFileSync('src/views/system/Menu.vue', 'utf8')
assert.ok(
  /formData\.resourceType = 'ENTITY_LIST'[\s\S]*?formData\.perm = ''/.test(menuPageSource),
  '实体列表菜单应由角色菜单授权控制，不应重复保存列表访问权限码'
)

const pageFeatureExpectations = {
  'src/views/ProcessList.vue': ['handleCreate', 'handleEdit', 'handleDelete', 'handlePublish', 'handleDisable', 'handleDesign', 'handleViewVersions', 'handleDeleteVersion'],
  'src/views/EntityList.vue': ['handleCreate', 'handleDelete', 'handlePublish', 'handleRepublish', 'handleDesign', 'handleListConfig', 'handleForm', 'handleUpgradeWorkflow', 'handleBindWorkflow', 'handleUnbindWorkflow', 'handleStatusConfig'],
  'src/views/EntityListConfigDesign.vue': ['saveListMetadata', 'saveCurrentField', 'saveListAction', 'toggleScene', 'handlePreviewSearch', 'handlePreviewReset', 'parseOptions'],
  'src/views/entity/EntityDataList.vue': ['handleSearch', 'handleReset', 'handleCreate', 'handleEdit', 'handleDelete', 'handleExport'],
  'src/views/entity/components/EntityDataFormDialog.vue': ['openCreate', 'openEdit', 'handleSubmit', 'resetForm'],
  'src/views/Home.vue': ['loadTaskOperations', 'openAddSignDialog', 'submitAddSign', 'handleCancelAddSign', 'openCcDialog', 'submitCc', 'loadCcList', 'readCc'],
  'src/views/system/Menu.vue': ['handleAddTopLevel', 'handleAddChild', 'handleEdit', 'handleDelete', 'handleStatusChange', 'handleVisibleChange', 'handleSortChange'],
  'src/views/system/User.vue': ['handleAdd', 'handleEdit', 'handleDelete', 'handleResetPassword'],
  'src/views/system/Role.vue': ['handleAdd', 'handleEdit', 'handleDelete', 'handleAssignMenu', 'handleSaveMenus'],
  'src/views/system/Dict.vue': ['handleAddDict', 'handleEditDict', 'handleDeleteDict', 'handleAddItem', 'handleEditItem', 'handleDeleteItem'],
  'src/views/system/OpenIntegration.vue': ['loadApplications', 'createApplication', 'selectedId'],
  'src/views/system/open-integration/IntegrationApplicationPanel.vue': ['saveAccess', 'saveContracts', 'rotateCredential', 'revokeCredential'],
  'src/views/system/open-integration/IntegrationWebhookPanel.vue': ['validateEndpoint', 'rotate', 'replay'],
  'src/views/system/open-integration/IntegrationSecretPanel.vue': ['openRotate', 'revoke', 'destroy'],
  'src/views/system/open-integration/IntegrationConnectorPanel.vue': ['openCreate', 'openEdit', 'save', 'openTest', 'runTest']
}

for (const [file, names] of Object.entries(pageFeatureExpectations)) {
  const source = readFileSync(file, 'utf8')
  for (const name of names) {
    assert.ok(source.includes(name), `${file} 缺少页面功能入口: ${name}`)
  }
}

const designerSource = readFileSync('src/components/VueBpmnDesigner.vue', 'utf8')
for (const configurableType of ['bpmn:CallActivity', 'bpmn:SubProcess']) {
  assert.ok(designerSource.includes(`'${configurableType}'`), `流程设计器无法打开配置面板: ${configurableType}`)
}

const nodeConfigPanelSource = readFileSync('src/components/NodeConfigPanel.vue', 'utf8')
assert.ok(
  /v-if="isManualTask && activeTab === 'basic'"[\s\S]*?title="线下任务"/.test(nodeConfigPanelSource)
    && nodeConfigPanelSource.includes('applyNodeConfiguration'),
  '手动任务缺少常用配置区或统一保存入口'
)
assert.ok(
  /case 'service':[\s\S]*?serviceResultVariable/.test(nodeConfigPanelSource),
  '服务任务保存按钮未持久化结果变量'
)
for (const validationText of [
  '请填写 REST 请求 URL',
  '请至少选择一个发送渠道',
  '接收任务超时时间必须是正整数',
  '请填写业务规则任务的决策表 Key',
  '请选择或填写子流程 Key'
]) {
  assert.ok(
    nodeConfigPanelSource.includes(validationText),
    `自动流程节点缺少保存前校验: ${validationText}`
  )
}
assert.equal(
  nodeConfigPanelSource.includes('<el-option label="multipart/form-data"'),
  false,
  'REST 节点不应提供运行时尚未支持的 multipart/form-data 选项'
)
assert.ok(
  nodeConfigPanelSource.includes('脚本任务已禁用'),
  '历史脚本任务应明确展示禁用状态'
)
assert.equal(
  nodeConfigPanelSource.includes('/script/test'),
  false,
  '前端不应保留服务端脚本测试入口'
)
assert.ok(
  nodeConfigPanelSource.includes('当前运行时仅支持站内信'),
  '发送任务应明确展示真实可用的通知渠道'
)
assert.equal(
  /<el-checkbox label="(?:email|sms)">/.test(nodeConfigPanelSource),
  false,
  '发送任务不应提供尚未注册运行时实现的邮件或短信渠道'
)

console.log('functional tests passed')
