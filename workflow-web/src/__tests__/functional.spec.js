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
  migrateFormNodeConfig,
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
  createFlowConditionConfig,
  createFlowConditionGroup
} from '@/utils/flowConditionGroups.js'
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

registerFormNodeComponent('migratedNode', DemoNodeV2, {
  version: 1,
  snapshotVersion: 2,
  nodeTypes: ['FIELD'],
  supportedBindings: ['ENTITY_FIELD'],
  migrateConfig({ fromVersion, toVersion, config }) {
    return {
      ...config,
      migratedFrom: fromVersion,
      migratedTo: toVersion
    }
  }
})
const migratedNodeDescriptor = getFormNodeDescriptor('migratedNode', 1)
assert.deepEqual(
  migrateFormNodeConfig(
    {
      snapshotVersion: 1,
      props: {
        fieldCode: 'risk_level',
        componentProps: {
          title: '风险摘要'
        }
      }
    },
    migratedNodeDescriptor
  ),
  {
    title: '风险摘要',
    migratedFrom: 1,
    migratedTo: 2
  }
)
assert.deepEqual(
  migrateFormNodeConfig(
    {
      snapshotVersion: 2,
      props: {
        fieldCode: 'risk_level',
        componentProps: {
          title: '风险摘要'
        }
      }
    },
    migratedNodeDescriptor
  ),
  {
    title: '风险摘要'
  }
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

const visibilityConditionConfig = createFlowConditionConfig(
  createFlowConditionGroup('AND', [
    { type: 'CONDITION', property: 'status', operator: '==', value: 'OPEN' },
    createFlowConditionGroup('OR', [
      { type: 'CONDITION', property: 'amount', operator: '>=', value: '100' },
      { type: 'CONDITION', property: 'vip', operator: '==', value: 'true' }
    ])
  ])
)
const disabledConditionConfig = createFlowConditionConfig(
  createFlowConditionGroup('AND', [
    { type: 'CONDITION', property: 'owner', operator: 'empty', value: '' }
  ])
)
const requiredConditionConfig = createFlowConditionConfig(
  createFlowConditionGroup('OR', [
    { type: 'CONDITION', property: 'urgent', operator: '==', value: 'true' },
    { type: 'CONDITION', property: 'category', operator: '==', value: 'SPECIAL' }
  ])
)
const structuredLinkageField = {
  fieldCode: 'approvalNote',
  visibilityConditionConfig,
  visibilityRule: "${status} == 'CLOSED'",
  disabledConditionConfig,
  requiredConditionConfig
}
const structuredLinkageResult = LinkageEngine.processAllLinkages(
  [structuredLinkageField],
  {
    status: 'OPEN',
    amount: 120,
    vip: false,
    owner: '',
    urgent: false,
    category: 'SPECIAL'
  }
)
assert.equal(structuredLinkageResult.visibility.approvalNote, true)
assert.equal(structuredLinkageResult.disabled.approvalNote, true)
assert.equal(structuredLinkageResult.required.approvalNote, true)
assert.equal(
  LinkageEngine.shouldShowField(
    structuredLinkageField,
    { status: 'OPEN', amount: 120, vip: false }
  ),
  true
)
assert.equal(
  LinkageEngine.getTriggeredLinkages(
    'amount',
    [structuredLinkageField]
  ).length,
  1
)

const attachmentRequiredCondition = createFlowConditionConfig(
  createFlowConditionGroup('AND', [
    { type: 'CONDITION', property: 'stage', operator: '==', value: 'REVIEW' },
    createFlowConditionGroup('OR', [
      { type: 'CONDITION', property: 'urgent', operator: '==', value: 'true' },
      { type: 'CONDITION', property: 'amount', operator: '>=', value: '100' }
    ])
  ])
)
const attachmentLinkageField = {
  fieldCode: 'documents',
  fieldType: 'FILE',
  isRequired: 1,
  componentProps: {
    fileItems: [
      { itemKey: 'afi_contract', itemName: '合同', required: '0' },
      { itemKey: 'afi_license', itemName: '许可证', required: '1' }
    ],
    attachmentItemRequiredRules: {
      version: 1,
      items: [{
        itemKey: 'afi_contract',
        requiredConditionConfig: attachmentRequiredCondition
      }]
    },
    linkageRules: {
      requiredConditionConfig: createFlowConditionConfig(
        createFlowConditionGroup('AND', [
          { type: 'CONDITION', property: 'never', operator: '==', value: 'yes' }
        ])
      )
    }
  }
}
const attachmentLinkageResult = LinkageEngine.processAllLinkages(
  [attachmentLinkageField],
  { stage: 'REVIEW', urgent: false, amount: 120, never: 'no' }
)
assert.equal(attachmentLinkageResult.required.documents, true)
assert.deepEqual(
  attachmentLinkageResult.attachmentItemRequired.documents,
  { afi_contract: true, afi_license: true }
)
assert.equal(
  LinkageEngine.getTriggeredLinkages(
    'amount',
    [attachmentLinkageField]
  ).length,
  1
)

const hiddenAttachmentField = {
  ...attachmentLinkageField,
  isRequired: 0,
  componentProps: {
    ...attachmentLinkageField.componentProps,
    linkageRules: {
      visibilityConditionConfig: createFlowConditionConfig(
        createFlowConditionGroup('AND', [
          { type: 'CONDITION', property: 'showDocuments', operator: '==', value: 'true' }
        ])
      ),
      attachmentItemRequiredRules:
        attachmentLinkageField.componentProps.attachmentItemRequiredRules
    }
  }
}
const hiddenAttachmentResult = LinkageEngine.processAllLinkages(
  [hiddenAttachmentField],
  {
    showDocuments: false,
    stage: 'REVIEW',
    urgent: true,
    amount: 120
  }
)
assert.deepEqual(
  hiddenAttachmentResult.attachmentItemRequired.documents,
  { afi_contract: false, afi_license: true }
)

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

const loginSource = readFileSync('src/views/Login.vue', 'utf8')
assert.ok(
  loginSource.includes('@keyup.enter="focusPassword"')
    && loginSource.includes('@keyup.enter="handleLogin"')
    && loginSource.includes(':disabled="!hasCredentials"'),
  '登录页应在用户名输入后聚焦密码，并仅在完整凭据下允许提交'
)
assert.ok(
  loginSource.includes('|| !hasCredentials.value')
    && loginSource.includes('|| loading.value'),
  '登录处理函数必须拦截凭据不完整和重复提交'
)
assert.doesNotMatch(
  loginSource,
  /class="login-form"\s+@keyup\.enter="handleLogin"/,
  '登录表单不得在用户名输入框回车时提前提交'
)

const apiExpectations = {
  'src/api/auth.js': ['login', 'getCurrentUser', 'logout', 'getPermissions'],
  'src/api/process.js': ['getList', 'getPublishedList', 'getById', 'create', 'update', 'delete', 'publish', 'getProcessProgress'],
  'src/api/entity.js': ['getList', 'getOptions', 'resolveOptions', 'getByCode', 'create', 'update', 'delete', 'publish', 'getListWithConfig', 'getDetail', 'save', 'exportData'],
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

const entityDefinitionPickerSource = readFileSync(
  'src/components/EntityDefinitionPicker.vue',
  'utf8'
)
assert.ok(
  entityDefinitionPickerSource.includes('class="selected-scrollbar"')
    && /\.selected-scrollbar\s*\{[\s\S]*?height:\s*468px;/.test(entityDefinitionPickerSource),
  '实体选择弹窗必须限制已选区域高度，避免覆盖底部确认按钮'
)
assert.ok(
  /\.entity-definition-picker-dialog\s+:deep\(\.el-dialog__footer\)\s*\{[\s\S]*?z-index:\s*1;/.test(
    entityDefinitionPickerSource
  ),
  '实体选择弹窗底部操作区必须保持在内容层上方'
)

const pageFeatureExpectations = {
  'src/views/ProcessList.vue': ['handleCreate', 'handleEdit', 'handleDelete', 'handlePublish', 'handleDisable', 'handleDesign', 'handleViewVersions', 'handleDeleteVersion'],
  'src/views/EntityList.vue': ['handleCreate', 'handleDelete', 'handlePublish', 'handleRepublish', 'handleDesign', 'handleListConfig', 'handleForm', 'handleUpgradeWorkflow', 'handleBindWorkflow', 'handleUnbindWorkflow', 'handleStatusConfig'],
  'src/views/EntityListConfigDesign.vue': ['saveListMetadata', 'saveCurrentField', 'saveListAction', 'toggleScene'],
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

const openIntegrationSource = readFileSync(
  'src/views/system/OpenIntegration.vue',
  'utf8'
)
const integrationApplicationPanelSource = readFileSync(
  'src/views/system/open-integration/IntegrationApplicationPanel.vue',
  'utf8'
)
assert.ok(
  openIntegrationSource.includes('integrationApplicationApi.capabilities')
    && openIntegrationSource.includes(':capabilities="capabilities"'),
  '开放集成页面应先读取服务端能力，再加载应用子资源'
)
assert.ok(
  /v-if="capabilities\.webhookEnabled"[\s\S]*?IntegrationWebhookPanel/.test(
    integrationApplicationPanelSource
  )
    && (
      integrationApplicationPanelSource.match(
        /v-if="capabilities\.httpConnectorEnabled"/g
      ) || []
    ).length === 2,
  'Webhook、Secret 和 Connector 面板必须按服务端能力装载，避免功能关闭时产生 404'
)
for (const unavailableTitle of [
  'Webhook 能力未启用',
  '集成 Secret 能力未启用',
  'HTTP Connector 能力未启用'
]) {
  assert.ok(
    integrationApplicationPanelSource.includes(unavailableTitle),
    `开放集成缺少明确的未启用状态: ${unavailableTitle}`
  )
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

const userSelectorSource = readFileSync(
  'src/components/UserSelector.vue',
  'utf8'
)
assert.ok(
  userSelectorSource.includes('entity-type="USER"')
    && userSelectorSource.includes(':value-key="valueKey"'),
  '人员选择必须复用系统实体选择器并支持 ID/用户名取值'
)

const entitySelectorSource = readFileSync(
  'src/components/EntitySelector.vue',
  'utf8'
)
assert.ok(
  entitySelectorSource.includes('valueKey: {')
    && entitySelectorSource.includes('valueKey: props.valueKey'),
  '实体选择器必须支持按配置值批量回显'
)

const userSelectionPages = [
  'src/views/Home.vue',
  'src/components/NodeConfigPanel.vue',
  'src/views/EntityDesign.vue',
  'src/views/system/Organization.vue',
  'src/views/system/Group.vue'
]
for (const file of userSelectionPages) {
  const source = readFileSync(file, 'utf8')
  assert.ok(
    source.includes('<UserSelector'),
    `${file} 未使用通用人员实体选择器`
  )
  assert.equal(
    source.includes('/system/user/list')
      || source.includes('getUserList(')
      || source.includes('userOptions'),
    false,
    `${file} 不应再加载全部人员作为下拉选项`
  )
}

const homeSource = readFileSync('src/views/Home.vue', 'utf8')
assert.ok(
  homeSource.includes(
    '<el-table-column label="操作" width="126" fixed="right" align="center">'
  ),
  '首页待办操作列应只保留主操作和更多入口所需宽度'
)
assert.ok(
  homeSource.includes('@click="handleApprove(row)"')
    && homeSource.includes('aria-label="更多任务操作"')
    && homeSource.includes('@command="handleTodoMoreCommand($event, row)"'),
  '首页待办应直接展示审批，并将其他操作收进更多菜单'
)
assert.ok(
  /class="todo-more-button"[\s\S]*?type="primary"[\s\S]*?link[\s\S]*?aria-label="更多任务操作"/.test(
    homeSource
  ),
  '首页待办更多按钮应使用无边框图标按钮'
)
for (const action of [
  "command: 'transfer', label: '转办'",
  "command: 'addSign', label: '加签'",
  "command: 'cancelAddSign', label: '撤销加签'",
  "command: 'cc', label: '知会'",
  "command: 'sla',"
]) {
  assert.ok(homeSource.includes(action), `首页待办更多菜单缺少操作：${action}`)
}
for (const formName of ['transferForm', 'addSignForm', 'ccForm']) {
  assert.ok(
    new RegExp(`:title="${formName}\\.processName"[\\s\\S]*?${formName}\\.processName \\|\\| '-'`).test(homeSource)
      && new RegExp(`:title="${formName}\\.code"[\\s\\S]*?${formName}\\.code \\|\\| '-'`).test(homeSource),
    `${formName} 应使用无输入框文本展示流程名称和流程编码`
  )
}
assert.equal(
  (homeSource.match(/width="min\(680px, 92vw\)"/g) || []).length,
  3,
  '转办、加签和知会弹窗应使用统一的加宽响应式尺寸'
)
assert.equal(
  (homeSource.match(/processName: row\.processName \|\| ''/g) || []).length,
  2,
  '加签和知会弹窗应保存当前待办的流程名称'
)
assert.equal(
  (homeSource.match(/code: row\.code \|\| ''/g) || []).length,
  2,
  '加签和知会弹窗应保存当前待办的流程编码'
)
assert.ok(
  homeSource.includes("transferForm.processName = row.processName || ''")
    && homeSource.includes("transferForm.code = row.code || ''"),
  '转办弹窗应保存当前待办的流程名称和流程编码'
)
assert.ok(
  /v-model="transferForm\.transferTo"[\s\S]*?value-key="code"/.test(
    homeSource
  ),
  '任务转办必须继续保存 username'
)
assert.ok(
  /v-model="addSignForm\.userIds"[\s\S]*?multiple[\s\S]*?value-key="code"/.test(
    homeSource
  ),
  '任务加签必须支持多个 username'
)
assert.ok(
  /v-model="ccForm\.userIds"[\s\S]*?multiple[\s\S]*?value-key="code"/.test(
    homeSource
  ),
  '人工知会必须支持多个 username'
)

const configHelpLabelSource = readFileSync(
  'src/components/ConfigHelpLabel.vue',
  'utf8'
)
assert.ok(
  configHelpLabelSource.includes('<el-tooltip')
    && configHelpLabelSource.includes('<QuestionFilled />')
    && configHelpLabelSource.includes('<button')
    && configHelpLabelSource.includes('type="button"')
    && configHelpLabelSource.includes(':aria-label="`查看${label}配置说明`"'),
  '通用配置说明标签应支持鼠标悬停和键盘聚焦'
)

const taskSlaPolicySource = readFileSync(
  'src/views/process/TaskSlaPolicyManagement.vue',
  'utf8'
)
assert.equal(
  (taskSlaPolicySource.match(/<ConfigHelpLabel/g) || []).length,
  8,
  'SLA策略的关键配置属性都应展示问号说明'
)
assert.ok(
  taskSlaPolicySource.includes('<el-form :model="form" label-width="132px">'),
  'SLA策略问号说明不应挤压长标签换行'
)
for (const label of [
  '响应分钟',
  '响应计时',
  '办结分钟',
  '办结计时',
  '允许人工暂停',
  '流程挂起暂停',
  '最长暂停分钟',
  '说明'
]) {
  assert.ok(
    taskSlaPolicySource.includes(`label="${label}"`),
    `SLA策略缺少“${label}”的配置说明`
  )
}
for (const helpText of [
  '留空表示不考核首次响应',
  '周末、节假日和非工作时段也计入',
  '暂停时保存剩余响应及办结时长',
  '流程实例挂起时自动暂停活动任务的 SLA',
  '达到上限后系统自动恢复计时',
  '不参与截止时间、提醒或升级计算'
]) {
  assert.ok(
    taskSlaPolicySource.includes(helpText),
    `SLA策略配置说明缺少关键规则：${helpText}`
  )
}

assert.ok(
  /v-model="form\.leaderId"[\s\S]*?value-key="id"/.test(
    readFileSync('src/views/system/Organization.vue', 'utf8')
  ),
  '组织负责人必须继续保存用户 ID'
)
assert.ok(
  /v-model="selectedUserIds"[\s\S]*?multiple[\s\S]*?value-key="id"/.test(
    readFileSync('src/views/system/Group.vue', 'utf8')
  ),
  '用户组成员必须继续保存多个用户 ID'
)

console.log('functional tests passed')
