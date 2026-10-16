import assert from 'node:assert/strict'
import './list-cell-action.spec.js'
import './list-selection.spec.js'
import {
  calculateListActionOrderKey,
  describeListPublishChanges,
  ensureListToolbarImportButton,
  getListButtonDefaultType,
  listActionFingerprint,
  listMetadataDetailEntries,
  listMetadataFingerprint,
  listScopeBindingFingerprint,
  LIST_IMPORT_BUTTON_PRESET,
  normalizeListActionForSave,
  resolveListButtonType,
  withListButtonTypeDefault
} from '../list-config-design.js'

// 旧列表的草稿补上可配置入口，保存前保持关闭，不能因升级直接开放导入。
const oldToolbar = [
  { id: 'create-1', key: 'create', type: 'built-in', enabled: true, sort: 3, orderKey: 9000000 },
  { id: 'export-1', key: 'exportAll', type: 'built-in', enabled: false, sort: 8, orderKey: 11000000 }
]
const toolbarWithImport = ensureListToolbarImportButton(oldToolbar)
assert.deepEqual(toolbarWithImport.slice(0, 2), oldToolbar)
assert.equal(oldToolbar.length, 2, '加载设计器不能修改原配置快照')
assert.equal(toolbarWithImport[2].key, 'import')
assert.equal(toolbarWithImport[2].enabled, false)
assert.equal(toolbarWithImport[2].sort, 9)
assert.equal(toolbarWithImport[2].orderKey, 12000000)
assert.equal(normalizeListActionForSave(toolbarWithImport[2], 'TOOLBAR').enabled, false)
assert.equal(LIST_IMPORT_BUTTON_PRESET.enabled, false, '通过菜单添加的导入也必须默认关闭')
assert.equal(ensureListToolbarImportButton([])[0].enabled, false)
assert.equal(ensureListToolbarImportButton(toolbarWithImport).length, 3, '重复加载不能重复补入')

const enabledImport = {
  id: 'import-1', revision: 2, key: 'import', type: 'built-in', enabled: true,
  label: '批量导入员工', sort: 2, perm: 'entity:employee:import', orderKey: 3000000
}
assert.deepEqual(
  ensureListToolbarImportButton([enabledImport, ...oldToolbar]),
  [enabledImport, ...oldToolbar],
  '既有启用状态、按钮身份与用户配置均保留'
)
const savedImport = normalizeListActionForSave({ ...toolbarWithImport[2], enabled: true }, 'TOOLBAR')
assert.equal(savedImport.buttonKey, 'import')
assert.equal(savedImport.buttonType, 'built-in')
assert.equal(savedImport.enabled, true, '设计者勾选启用后能够按既有内置按钮协议保存')
assert.deepEqual(savedImport.actionParams, {}, '默认导入由服务端解析实体方案，无需自定义事件参数')

const config = {
  listName: '项目列表',
  selectionMode: 'SINGLE',
  queryInterfaceExtensionId: 'project-page-interface'
}
const viewConfig = {
  search: { defaultVisibleCount: 4, collapsible: true, labelWidth: 100 },
  table: { stripe: true, border: false, showIndex: true, size: 'default' },
  pagination: { pageSize: 20, pageSizes: [10, 20, 50] },
  customComponentProps: {}
}

assert.equal(
  listScopeBindingFingerprint(['b', 'a', 'a']),
  listScopeBindingFingerprint(['a', 'b'])
)
assert.equal(
  JSON.parse(listMetadataFingerprint(config, viewConfig)).selectionValueField,
  'id'
)
assert.equal(
  JSON.parse(listMetadataFingerprint(config, viewConfig))
    .queryInterfaceExtensionId,
  undefined
)
assert.equal(
  listMetadataDetailEntries(config, viewConfig)
    .find(item => item.key === 'queryInterfaceExtensionId')?.value,
  undefined
)

const action = normalizeListActionForSave({
  id: 'action-1',
  revision: 3,
  key: 'edit',
  type: 'EDIT',
  label: '编辑',
  orderKey: 150,
  targetEntityCode: 'project'
}, 'ROW')
assert.equal(action.expectedRevision, 3)
assert.equal(action.orderKey, 150)
assert.deepEqual(action.actionParams, { targetEntityCode: 'project' })
assert.deepEqual(action.clearFields, [
  'templateId', 'templateVersion', 'localOverridesDocument',
  'availabilityRuleDocument'
])
assert.equal('unavailableBehavior' in action, false)
assert.equal(action.availabilityRule, null)
assert.deepEqual(
  normalizeListActionForSave({
    key: 'archive',
    type: 'custom',
    label: '归档',
    availabilityRule: {
      version: 2,
      visibleWhen: { type: 'STATUS_CODE', operator: 'EQ', value: 'READY' },
      enabledWhen: { type: 'RELATION', relation: 'CURRENT_USER_IS_CREATOR' },
      disabledMessage: '仅创建人可以归档'
    }
  }, 'ROW').availabilityRule,
  {
    version: 2,
    visibleWhen: { type: 'STATUS_CODE', operator: 'EQ', value: 'READY' },
    enabledWhen: { type: 'RELATION', relation: 'CURRENT_USER_IS_CREATOR' },
    disabledMessage: '仅创建人可以归档'
  }
)
assert.equal(
  normalizeListActionForSave({
    key: 'archive',
    availabilityRule: {
      version: 2,
      visibleWhen: null,
      enabledWhen: null,
      disabledMessage: ''
    }
  }, 'ROW').clearFields.includes('availabilityRuleDocument'),
  false
)
assert.equal(getListButtonDefaultType('create'), 'primary')
assert.equal(getListButtonDefaultType('batchDelete'), 'danger')
assert.equal(getListButtonDefaultType('delete'), 'danger')
assert.equal(resolveListButtonType({ key: 'delete', buttonType: 'success' }), 'success')
assert.equal(withListButtonTypeDefault({ key: 'delete' }).buttonType, 'danger')
assert.equal(
  normalizeListActionForSave({
    key: 'create',
    type: 'built-in',
    label: '新增数据'
  }, 'TOOLBAR').styleType,
  'primary'
)
assert.equal(
  normalizeListActionForSave({
    key: 'delete',
    type: 'built-in',
    label: '删除'
  }, 'ROW').styleType,
  'danger'
)
assert.deepEqual(
  normalizeListActionForSave({
    key: 'view',
    type: 'built-in',
    label: '查看',
    targetFormId: 'form-1',
    targetFormReleaseId: 'release-9',
    targetFormReleaseVersion: 9
  }, 'ROW').actionParams,
  {
    targetFormId: 'form-1'
  },
  '表单发布版本只能由列表发布快照生成'
)
assert.deepEqual(
  normalizeListActionForSave({
    key: 'custom_open',
    type: 'custom',
    customMode: 'open-form',
    label: '打开编辑表单',
    targetFormId: 'form-2',
    targetFormMode: 'EDIT'
  }, 'ROW').actionParams,
  {
    targetFormId: 'form-2',
    targetFormMode: 'EDIT'
  }
)
assert.equal(
  listActionFingerprint({ ...action, revision: 3, key: 'edit' }, 'ROW')
    .includes('expectedRevision'),
  false
)

assert.equal(
  calculateListActionOrderKey([{ orderKey: 100 }, {}, { orderKey: 200 }], 1),
  150
)
assert.equal(
  describeListPublishChanges({
    changedItems: [{ changeType: 'ADDED', label: '字段 A' }]
  }),
  '新增：字段 A'
)

console.log('list config design helper tests passed')
