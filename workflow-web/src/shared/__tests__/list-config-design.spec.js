import assert from 'node:assert/strict'
import {
  calculateListActionOrderKey,
  describeListPublishChanges,
  getListButtonDefaultType,
  listActionFingerprint,
  listMetadataDetailEntries,
  listMetadataFingerprint,
  normalizeListActionForSave,
  resolveListButtonType,
  withListButtonTypeDefault
} from '../list-config-design.js'

const config = {
  listName: '项目列表',
  selectionMode: 'SINGLE',
  queryDataSourceId: 'service-1',
  queryOperationCode: 'queryPage'
}
const viewConfig = {
  search: { defaultVisibleCount: 4, collapsible: true, labelWidth: 100 },
  table: { stripe: true, border: false, showIndex: true, size: 'default' },
  pagination: { pageSize: 20, pageSizes: [10, 20, 50] },
  customComponentProps: {}
}

assert.equal(
  JSON.parse(listMetadataFingerprint(config, viewConfig)).selectionValueField,
  'id'
)
assert.equal(
  JSON.parse(listMetadataFingerprint(config, viewConfig))
    .queryOperationCode,
  'queryPage'
)
assert.equal(
  listMetadataDetailEntries(config, viewConfig)
    .find(item => item.key === 'queryDataSourceId')?.value,
  'service-1'
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
  'templateId', 'templateVersion', 'localOverridesDocument'
])
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
