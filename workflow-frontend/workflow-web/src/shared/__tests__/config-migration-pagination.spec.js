import assert from 'node:assert/strict'
import { migrationValidationIssues } from '../config-migration-display.js'
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

// 真实报告可能同时包含正文问题、结构风险、编码映射缺失与冲突，每一项都应保留操作建议。
const report = { items: [{ itemId: 'blocked', comparisonStatus: 'CONFLICT', risks: [
  { level: 'BLOCKING', message: '未声明固定用户', path: '$[expense].paramsJson/owner', suggestion: '声明 /owner 为 USER' },
  { level: 'BLOCKING', message: '字段类型变更', fieldCode: 'owner' }
], missingDependencies: [{ type: 'USER', key: 'alice', targetKey: 'alice-prod', reason: '用户不存在' }] }] }
const issues = migrationValidationIssues(report, 'blocked')
assert.equal(issues.length, 4)
assert.equal(issues[0].path, '$[expense].paramsJson/owner')
assert.equal(issues[0].suggestion, '声明 /owner 为 USER')
assert.equal(issues[1].path, 'owner')
assert.ok(issues.every(issue => issue.suggestion?.length > 0), '所有阻断项都应有处理建议')
assert.match(issues[3].message, /USER\/alice-prod/)
assert.deepEqual(migrationValidationIssues(report, 'another-item'), [])
assert.deepEqual(migrationValidationIssues(null, 'blocked'), [])
