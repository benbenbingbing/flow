import assert from 'node:assert/strict'
import {
  applyConfigMigrationPageResult,
  createConfigMigrationPage,
  paginateConfigMigrationRows,
  shouldReloadConfigMigrationPage,
  updateConfigMigrationClientPage
} from '../config-migration-pagination.js'

const initial = createConfigMigrationPage()
assert.deepEqual(initial, { pageNum: 1, pageSize: 20, total: 0 })

const pageResult = applyConfigMigrationPageResult(
  { pageNum: 2, pageSize: 20, total: 0 },
  { records: ['row-21'], total: 21, pageNum: 2, pageSize: 20 }
)
assert.deepEqual(pageResult.records, ['row-21'])
assert.deepEqual(pageResult.page, { pageNum: 2, pageSize: 20, total: 21 })

const aliasedPage = applyConfigMigrationPageResult(
  initial,
  { list: ['row-1'], total: 1, pageNum: 99, pageSize: 500 }
)
assert.deepEqual(aliasedPage.records, ['row-1'])
assert.deepEqual(
  aliasedPage.page,
  { pageNum: 1, pageSize: 100, total: 1 },
  '页码和每页条数必须收敛到有效范围'
)

const rows = Array.from({ length: 25 }, (_, index) => index + 1)
assert.deepEqual(
  paginateConfigMigrationRows(rows, { pageNum: 2, pageSize: 10 }),
  [11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
)
assert.deepEqual(
  updateConfigMigrationClientPage({ pageNum: 4, pageSize: 10 }, rows.length),
  { pageNum: 3, pageSize: 10, total: 25 },
  '对比条目减少后应回退到最后一个有效页'
)

const contractedPage = applyConfigMigrationPageResult(
  { pageNum: 2, pageSize: 20 },
  { records: [], total: 20, pageNum: 2, pageSize: 20 }
)
assert.equal(
  shouldReloadConfigMigrationPage({ pageNum: 2 }, contractedPage),
  true,
  '服务端数据收缩造成越界空页时应使用修正后的页码重查'
)
assert.equal(
  shouldReloadConfigMigrationPage(
    { pageNum: 1 },
    applyConfigMigrationPageResult(
      { pageNum: 1, pageSize: 20 },
      { records: [], total: 0, pageNum: 1, pageSize: 20 }
    )
  ),
  false,
  '真正的空数据集不应重复请求'
)

console.log('config migration pagination tests passed')
