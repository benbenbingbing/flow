import assert from 'node:assert/strict'
import {
  readSelectionReturnMappings,
  selectionMappingSource,
  selectionMappingTarget,
  selectionReturnFieldOptions,
  updateSelectionReturnMapping,
  validateSelectionReturnMappings
} from '../selection-return-mapping-editor.js'
import { applySelectionReturnMappings } from '../../utils/selectionReturnMappings.ts'
import { listMetadataFingerprint } from '../list-config-design.js'

assert.deepEqual(readSelectionReturnMappings(''), [])
assert.deepEqual(readSelectionReturnMappings('  '), [])
assert.throws(() => readSelectionReturnMappings('{'), /无法读取/)
assert.throws(() => readSelectionReturnMappings('{}'), /必须是数组/)
const fields = [
  { fieldCode: 'id', fieldName: 'ID', isSystem: true },
  { fieldCode: 'customer_name', fieldName: '客户名称' },
  { fieldCode: 'phone', fieldName: '联系电话' }
]
const options = selectionReturnFieldOptions(fields)
assert.equal(options.filter(option => option.value === 'id').length, 1)
assert.equal(options.find(option => option.value === 'data.customer_name').label, '客户名称')
assert.ok(selectionReturnFieldOptions(fields, true).some(option => option.value === 'customer_name'))

// 存量别名、点路径和扩展属性必须往返无损；修改来源不能意外改写另一侧。
const legacy = {
  sourcePath: 'data.region.name', sourceField: 'ignored',
  targetPath: 'selectionData.customer.region', targetField: 'ignored',
  extension: { keep: true }
}
assert.equal(selectionMappingSource(legacy), 'data.region.name')
assert.equal(selectionMappingTarget(legacy), 'customer.region')
assert.deepEqual(validateSelectionReturnMappings(JSON.stringify([legacy])), [legacy])
const changedSource = updateSelectionReturnMapping(legacy, 'sourceField', 'data.customer_name')
assert.equal(changedSource.sourcePath, undefined)
assert.equal(changedSource.targetPath, legacy.targetPath)
assert.deepEqual(changedSource.extension, legacy.extension)
const changed = updateSelectionReturnMapping(changedSource, 'targetField', 'customer.name')
assert.equal(changed.targetPath, undefined)
assert.equal(legacy.targetPath, 'selectionData.customer.region')

const saved = validateSelectionReturnMappings(JSON.stringify([
  { sourceField: 'id', targetField: 'customer.id' }, changed
]))
const reloaded = readSelectionReturnMappings(JSON.stringify(saved))
const selectedRows = [
  { id: 'c1', data: { customer_name: '客户甲' } },
  { id: 'c2', data: { customer_name: '客户乙' } }
].map(row => applySelectionReturnMappings(row, reloaded))
assert.deepEqual(selectedRows.map(row => row.selectionData), [
  { customer: { id: 'c1', name: '客户甲' } },
  { customer: { id: 'c2', name: '客户乙' } }
])
assert.equal(selectedRows[0].data.customer_name, '客户甲')
assert.deepEqual(validateSelectionReturnMappings('[]'), [])
const mapping = (targetField, sourceField = 'id') => ({ sourceField, targetField })
assert.throws(() => validateSelectionReturnMappings([mapping('name', '')]), /请选择来源字段/)
assert.throws(() => validateSelectionReturnMappings([mapping('')]), /请填写返回名称/)
assert.throws(() => validateSelectionReturnMappings([mapping('customer..name')]), /空的层级/)
assert.throws(() => validateSelectionReturnMappings([mapping('customer name')]), /空格/)
assert.throws(() => validateSelectionReturnMappings([mapping('x'), mapping('selectionData.x')]), /第 2.*第 1.*重复/)
assert.throws(() => validateSelectionReturnMappings([mapping('x'), mapping('x.name')]), /层级冲突/)
assert.throws(() => validateSelectionReturnMappings([mapping('x.name'), mapping('x')]), /层级冲突/)
assert.doesNotThrow(() => validateSelectionReturnMappings([mapping('x'), mapping('xyz')]))
for (const target of ['__proto__.name', 'customer.constructor.name', 'prototype']) {
  assert.throws(() => validateSelectionReturnMappings([mapping(target)]), /保留名称/)
}
assert.throws(() => validateSelectionReturnMappings([null]), /请选择来源字段/)
assert.notEqual(
  listMetadataFingerprint({ selectionReturnMappingsText: '[]' }, {}),
  listMetadataFingerprint({ selectionReturnMappingsText: '[{"sourceField":"","targetField":""}]' }, {}),
  '未填完的行仍触发草稿差异和离页保护'
)
console.log('selection return mapping editor tests passed')
