import assert from 'node:assert/strict'

import {
  buildListRequestFilters,
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

console.log('list-runtime tests passed')
