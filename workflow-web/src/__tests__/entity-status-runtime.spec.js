import assert from 'node:assert/strict'

import {
  buildEntityStatusMap,
  getEffectiveEntityStatusOptions,
  resolveEntityStatusLabel,
  withEntityStatusFieldOptions,
  withEntityStatusRuntimeForm
} from '@/shared/entity-status-runtime'
import { formatListFieldValue } from '@/shared/list-runtime'

const statuses = [
  { statusCode: 'DRAFT', statusName: '待填写' },
  { statusCode: 'PENDING', statusName: '处理中' }
]

assert.deepEqual(getEffectiveEntityStatusOptions(statuses), [
  { value: 'DRAFT', label: '待填写' },
  { value: 'PENDING', label: '处理中' }
])

const statusMap = buildEntityStatusMap(statuses)
assert.deepEqual(statusMap, {
  DRAFT: '待填写',
  PENDING: '处理中'
})
assert.equal(resolveEntityStatusLabel('DRAFT', statusMap), '待填写')
assert.equal(resolveEntityStatusLabel('WITHDRAWN', statusMap), '已撤回')
assert.equal(resolveEntityStatusLabel('CUSTOM', statusMap), 'CUSTOM')

const runtimeField = withEntityStatusFieldOptions(
  { fieldCode: 'status', fieldType: 'STRING', componentType: 'input' },
  statuses
)
assert.equal(runtimeField.fieldType, 'SELECT')
assert.equal(runtimeField.componentType, 'select')
assert.deepEqual(runtimeField.options, [
  { value: 'DRAFT', label: '待填写' },
  { value: 'PENDING', label: '处理中' }
])

const multipleQueryField = withEntityStatusFieldOptions(
  {
    fieldCode: 'status',
    fieldType: 'STRING',
    componentType: 'select_multiple'
  },
  statuses,
  { allowMultiple: true }
)
assert.equal(multipleQueryField.fieldType, 'SELECT')
assert.equal(multipleQueryField.componentType, 'select_multiple')
assert.deepEqual(multipleQueryField.options, [
  { value: 'DRAFT', label: '待填写' },
  { value: 'PENDING', label: '处理中' }
])

const runtimeForm = withEntityStatusRuntimeForm(
  {
    fields: [
      { id: 'status-field', fieldCode: 'status', fieldType: 'STRING' }
    ],
    nodes: [
      {
        id: 'status-node',
        nodeType: 'FIELD',
        bindingRef: 'status-field',
        propsDocument: JSON.stringify({
          fieldCode: 'status',
          fieldType: 'STRING',
          componentType: 'input'
        })
      }
    ]
  },
  [],
  statuses
)
assert.equal(runtimeForm.fields[0].componentType, 'select')
assert.equal(JSON.parse(runtimeForm.nodes[0].propsDocument).componentType, 'select')
assert.equal(
  formatListFieldValue(
    { status: 'DRAFT' },
    { fieldCode: 'status', fieldType: 'STRING' },
    {},
    statusMap
  ),
  '待填写'
)

console.log('entity status runtime tests passed')
