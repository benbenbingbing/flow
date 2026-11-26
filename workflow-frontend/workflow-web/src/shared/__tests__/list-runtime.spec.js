import assert from 'node:assert/strict'
import { listQueryDefaults } from '@flow/workflow-core/list-query-filters'

import {
  buildListRequestFilters,
  resolveListQueryDefaultValue,
  toListFilterFieldCode
} from '../list-runtime/index.js'

assert.equal(toListFilterFieldCode('expectedDate_start'), 'expectedDate')
assert.equal(toListFilterFieldCode('expectedDate_end'), 'expectedDate')
assert.equal(toListFilterFieldCode('expectedDate_op'), 'expectedDate')
assert.equal(toListFilterFieldCode('expected_delivery_date'), 'expected_delivery_date')

assert.deepEqual(
  buildListRequestFilters(
    {
      expectedDate: '2026-08-20',
      name: '需求',
      name_op: 'EQ'
    },
    [{
      fieldCode: 'name',
      queryType: 'LIKE'
    }]
  ),
  {
    name: '需求',
    name_op: 'LIKE'
  },
  '切换列表后不得把上一张列表的查询字段带入当前请求'
)

assert.deepEqual(
  buildListRequestFilters(
    {
      expected_delivery_date_start: '2026-08-01',
      expected_delivery_date_end: '2026-08-31'
    },
    [{
      fieldCode: 'expected_delivery_date',
      queryType: 'BETWEEN'
    }],
    {
      status: 'PROCESSING'
    }
  ),
  {
    expected_delivery_date_start: '2026-08-01',
    expected_delivery_date_end: '2026-08-31',
    expected_delivery_date_op: 'BETWEEN',
    status: 'PROCESSING'
  },
  '当前列表的范围查询和受信固定条件应继续提交'
)

assert.deepEqual(
  buildListRequestFilters(
    {
      status: ['DRAFT', 'PENDING']
    },
    [{
      fieldCode: 'status',
      queryType: 'IN'
    }]
  ),
  {
    status: ['DRAFT', 'PENDING'],
    status_op: 'IN'
  },
  '多选查询应保留数组值并提交 IN 操作符'
)

assert.deepEqual(
  buildListRequestFilters(
    {
      status: []
    },
    [{
      fieldCode: 'status',
      queryType: 'IN'
    }]
  ),
  {},
  '清空多选查询后不应提交空集合条件'
)

assert.deepEqual(resolveListQueryDefaultValue({ componentType: 'select_multiple', defaultValue: 'HIGH' }, 'IN'), ['HIGH'])
assert.deepEqual(resolveListQueryDefaultValue({ componentType: 'select_multiple', defaultValue: 'HIGH, URGENT' }, 'IN'), ['HIGH', 'URGENT'])
assert.deepEqual(resolveListQueryDefaultValue({ componentType: 'select_multiple', defaultValue: ['LOW'] }, 'NOT_IN'), ['LOW'])
assert.equal(resolveListQueryDefaultValue({ componentType: 'select', defaultValue: 'HIGH' }, 'EQ'), 'HIGH')

for (const queryType of ['EMPTY', 'NOT_EMPTY', 'IS_NULL']) {
  const field = { fieldCode: 'comment', queryType, defaultValue: true }
  const fields = [field, { fieldCode: 'active', queryType: 'EQ', defaultValue: false }]
  assert.deepEqual(listQueryDefaults(fields), { active: false })
  assert.equal(resolveListQueryDefaultValue(field, queryType), undefined)
  assert.deepEqual(
    buildListRequestFilters({ comment: true, comment_start: '旧值', active: false }, fields),
    { comment: true, comment_op: queryType, active: false, active_op: 'EQ' }
  )
  for (const unchecked of [false, undefined, '旧值', 'true']) {
    assert.deepEqual(buildListRequestFilters({
      comment: unchecked, comment_end: '旧值', comment_op: queryType
    }, fields), {})
  }
}

for (const queryType of ['NOT_LIKE', 'GE', 'LE']) {
  assert.deepEqual(buildListRequestFilters({ amount: '2.00' }, [{ fieldCode: 'amount', queryType }]),
    { amount: '2.00', amount_op: queryType })
}

console.log('list-runtime tests passed')
