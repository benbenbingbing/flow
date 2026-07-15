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
import { LinkageEngine } from '@/utils/linkageEngine.js'
import {
  getNodeTypeDescription,
  getNodeTypeTag,
  getNodeTypeText,
  buildAssigneeConfig
} from '@/shared/process-config'
import {
  ENTITY_FIELD_TYPES,
  getEntityFieldTypeLabel,
  getEntityFieldTypeTag
} from '@/shared/entity-design'

const DemoComponent = { name: 'DemoComponent' }
const DemoForm = { name: 'DemoForm' }
const DemoButton = { name: 'DemoButton' }

registerCustomListComponent('functionalList', DemoComponent, {
  label: '功能列表',
  configSchema: [{ key: 'cardSize', label: '卡片尺寸', type: 'select' }]
})
assert.equal(hasCustomListComponent('functionalList'), true)
assert.equal(getCustomListComponent('functionalList'), DemoComponent)
assert.ok(getRegisteredCustomListNames().includes('functionalList'))
assert.equal(getCustomListDescriptor('functionalList').label, '功能列表')

registerCustomFormComponent('functionalForm', DemoForm, {
  label: '功能表单',
  supportedModes: ['create', 'edit', 'approve', 'view']
})
assert.equal(hasCustomFormComponent('functionalForm'), true)
assert.equal(getCustomFormComponent('functionalForm'), DemoForm)
assert.ok(getRegisteredCustomFormNames().includes('functionalForm'))
assert.deepEqual(getCustomFormDescriptor('functionalForm').supportedModes, ['create', 'edit', 'approve', 'view'])

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
assert.equal(getNodeTypeTag('bpmn:StartEvent'), 'success')
assert.equal(getNodeTypeTag('bpmn:ExclusiveGateway'), 'warning')
assert.equal(buildAssigneeConfig({ assigneeType: 'user', assignee: 'zhangsan', candidateUsers: 'lisi' }).assigneeValue, 'zhangsan')
assert.equal(buildAssigneeConfig({ assigneeType: 'group', candidateGroups: 'finance' }).assigneeValue, 'finance')
assert.equal(buildAssigneeConfig({ assigneeType: 'expression', candidateUsers: '${starter}' }).assigneeValue, '${starter}')

assert.ok(ENTITY_FIELD_TYPES.length >= 20)
assert.equal(getEntityFieldTypeLabel('STRING'), '文本')
assert.equal(getEntityFieldTypeLabel('UNKNOWN_TYPE'), 'UNKNOWN_TYPE')
assert.equal(getEntityFieldTypeTag('REFERENCE'), 'primary')

const apiExpectations = {
  'src/api/auth.js': ['login', 'getCurrentUser', 'logout', 'getPermissions'],
  'src/api/process.js': ['getList', 'getPublishedList', 'getById', 'create', 'update', 'delete', 'publish', 'getProcessProgress'],
  'src/api/entity.js': ['getList', 'getByCode', 'create', 'update', 'delete', 'publish', 'getListWithConfig', 'getDetail', 'save', 'exportData'],
  'src/api/entityListConfig.js': ['getByEntityId', 'getById', 'getExtensionOptions', 'save', 'delete'],
  'src/api/processTask.js': ['getTodoList', 'getDoneList', 'getStatistics', 'completeTask', 'withdrawProcess', 'terminateProcess'],
  'src/api/system/menu.ts': ['getMenuTree', 'getSidebarMenuTree', 'createMenu', 'updateMenu', 'deleteMenu', 'updateSort'],
  'src/api/system/user.ts': ['getUserList', 'createUser', 'updateUser', 'deleteUser', 'resetPassword'],
  'src/api/system/role.ts': ['getRoleList', 'createRole', 'updateRole', 'deleteRole', 'saveRoleMenus'],
  'src/api/system/group.ts': ['getGroupList', 'createGroup', 'updateGroup', 'deleteGroup', 'saveGroupUsers'],
  'src/api/system/dict.ts': ['getDictList', 'createDict', 'updateDict', 'deleteDict']
}

for (const [file, names] of Object.entries(apiExpectations)) {
  const source = readFileSync(file, 'utf8')
  for (const name of names) {
    assert.ok(source.includes(name), `${file} 缺少功能 API: ${name}`)
  }
}

const pageFeatureExpectations = {
  'src/views/ProcessList.vue': ['handleCreate', 'handleEdit', 'handleDelete', 'handlePublish', 'handleDisable', 'handleDesign', 'handleViewVersions', 'handleDeleteVersion'],
  'src/views/EntityList.vue': ['handleCreate', 'handleDelete', 'handlePublish', 'handleRepublish', 'handleDesign', 'handleListConfig', 'handleForm', 'handleBindProcess', 'handleStatusConfig'],
  'src/views/EntityListConfigDesign.vue': ['handleSave', 'handlePreviewSearch', 'handlePreviewReset', 'parseOptions'],
  'src/views/entity/EntityDataList.vue': ['handleSearch', 'handleReset', 'handleCreate', 'handleEdit', 'handleDelete', 'handleExport'],
  'src/views/entity/components/EntityDataFormDialog.vue': ['openCreate', 'openEdit', 'handleSubmit', 'resetForm'],
  'src/views/system/Menu.vue': ['handleAddTopLevel', 'handleAddChild', 'handleEdit', 'handleDelete', 'handleStatusChange', 'handleVisibleChange', 'handleSortChange'],
  'src/views/system/User.vue': ['handleAdd', 'handleEdit', 'handleDelete', 'handleResetPassword'],
  'src/views/system/Role.vue': ['handleAdd', 'handleEdit', 'handleDelete', 'handleAssignMenu', 'handleSaveMenus'],
  'src/views/system/Dict.vue': ['handleAddDict', 'handleEditDict', 'handleDeleteDict', 'handleAddItem', 'handleEditItem', 'handleDeleteItem']
}

for (const [file, names] of Object.entries(pageFeatureExpectations)) {
  const source = readFileSync(file, 'utf8')
  for (const name of names) {
    assert.ok(source.includes(name), `${file} 缺少页面功能入口: ${name}`)
  }
}

console.log('functional tests passed')
