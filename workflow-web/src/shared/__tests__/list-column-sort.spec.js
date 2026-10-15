import test from 'node:test'
import assert from 'node:assert/strict'
import { isListColumnSortable, resolveListColumnSort } from '../list-column-sort.js'

const field = (fieldCode, overrides = {}) => ({
  fieldCode, fieldId: `field_${fieldCode}`, dataSourceType: 'ENTITY_FIELD',
  columnConfig: '{"sortable":true}', ...overrides
})

test('排序仅向显式启用的实体列开放，兼容对象配置和历史字段', () => {
  assert.equal(isListColumnSortable(field('amount')), true)
  assert.equal(isListColumnSortable(field('name', { columnConfig: { sortable: true }, dataSourceType: undefined })), true)
  for (const columnConfig of [undefined, '', '{bad', '{}', '{"sortable":false}', '{"sortable":"true"}', { sortable: 1 }]) {
    assert.equal(isListColumnSortable(field('amount', { columnConfig })), false)
  }
  for (const overrides of [
    { fieldId: 'virtual_total' }, { showInList: false }, { dataSourceType: 'STATIC' },
    { dataSourceType: 'FIELD_TEMPLATE' }, { interfaceExtensionId: 'interface_1' }, { fieldCode: '' }
  ]) assert.equal(isListColumnSortable(field('amount', overrides)), false)
  assert.equal(isListColumnSortable(null), false)
})

test('表格属性路径映射为配置字段，升降序与取消排序保持服务端契约', () => {
  const fields = [field('amount'), field('create_time'), field('disabled', { columnConfig: '{}' }), field('virtual', { fieldId: 'virtual_x' })]
  assert.deepEqual(resolveListColumnSort(fields, { prop: 'data.amount', order: 'ascending' }), { sortField: 'amount', sortDirection: 'asc' })
  assert.deepEqual(resolveListColumnSort(fields, { prop: 'amount', order: 'descending' }), { sortField: 'amount', sortDirection: 'desc' })
  assert.deepEqual(resolveListColumnSort(fields, { prop: 'create_time', order: 'descending' }), { sortField: 'create_time', sortDirection: 'desc' })
  for (const payload of [
    { prop: 'amount', order: null }, { prop: 'amount', order: 'asc' },
    { prop: 'data.disabled', order: 'ascending' }, { prop: 'data.virtual', order: 'ascending' },
    { prop: 'data.missing', order: 'ascending' }, { prop: 'amount desc;select', order: 'ascending' }, {}
  ]) assert.equal(resolveListColumnSort(fields, payload), null)
  assert.equal(resolveListColumnSort(undefined), null)
})
