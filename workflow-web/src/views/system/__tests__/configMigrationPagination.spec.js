import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const testDirectory = path.dirname(fileURLToPath(import.meta.url))
const webRoot = path.resolve(testDirectory, '../../../..')
const viewSource = readFileSync(path.join(webRoot, 'src/views/system/ConfigMigration.vue'), 'utf8')
const styleSource = readFileSync(path.join(webRoot, 'src/views/system/ConfigMigration.scss'), 'utf8')
const apiSource = readFileSync(path.join(webRoot, 'src/api/configMigration.js'), 'utf8')

assert.ok(!viewSource.includes('<h2>配置迁移</h2>'), '页面不应重复展示导航栏已有的配置迁移标题')
assert.ok(!styleSource.includes('border-top'), '配置迁移概览卡不应保留顶部蓝色强调线')
assert.doesNotMatch(
  styleSource,
  /\.config-migration-page\s*\{[^}]*padding:/,
  '配置迁移页面不应在全局内容区留白之外重复增加外层 padding'
)

assert.equal(
  viewSource.match(/<ConfigMigrationPagination/g)?.length,
  4,
  '配置迁移的四个主 Tab 都应提供独立分页控件'
)
;['getAssetPage', 'getExportPackagePage', 'getImportPage', 'getImportOptions', 'getStats'].forEach((method) => {
  assert.ok(apiSource.includes(`${method}(`), `配置迁移 API 缺少 ${method}`)
})
;['assetPage', 'exportPage', 'importPage', 'comparePage'].forEach((state) => {
  assert.ok(viewSource.includes(`v-model="${state}"`), `配置迁移缺少独立分页状态 ${state}`)
})
assert.ok(viewSource.includes('reserve-selection'), '资产跨页批量选择必须保留已勾选项')
assert.ok(
  viewSource.includes('v-for="item in importOptions"'),
  '影响对比的批次候选不能退化为导入主表当前页'
)
assert.ok(
  viewSource.includes(':data="pagedCompareItems"'),
  '影响对比表格应分页展示，同时保留完整比较响应'
)
assert.ok(
  viewSource.includes('@change="handleCompareImportChange"'),
  '切换影响对比批次时必须重置对比分页'
)
assert.match(
  viewSource,
  /const openBatchExport = async \(\) => \{[\s\S]*?targets\.length === 1[\s\S]*?getAsset\(targets\[0\]\.id\)/,
  '单选批量下载必须补取完整资产，保留细粒度表单和列表选项'
)
assert.ok(
  viewSource.includes('requestVersions.exportDetail'),
  '所有导出弹窗入口必须共享详情请求版本，避免迟到响应覆盖导出目标'
)
const mountedHook = viewSource.match(/onMounted\(async \(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''
assert.match(
  mountedHook,
  /loadImportOptions\(\)[\s\S]*loadStats\(\)/,
  '页面初始化必须加载全局统计与完整的影响对比批次候选'
)

console.log('config migration pagination integration tests passed')
