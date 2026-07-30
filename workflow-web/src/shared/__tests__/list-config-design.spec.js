import assert from 'node:assert/strict'
import {
  calculateListActionOrderKey,
  describeListPublishChanges,
  listActionFingerprint,
  listMetadataDetailEntries,
  listMetadataFingerprint,
  normalizeListActionForSave
} from '../list-config-design.js'

const config = {
  listName: '项目列表',
  selectionMode: 'SINGLE',
  queryDataSourceId: 'source-1'
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
assert.ok(
  listMetadataDetailEntries(config, viewConfig)
    .some(item => item.key === 'queryDataSourceId' && item.value === 'source-1')
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
