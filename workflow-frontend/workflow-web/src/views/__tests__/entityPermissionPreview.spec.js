import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse, parse } from '@vue/compiler-sfc'

const source = await readFile(new URL('../EntityDesign.vue', import.meta.url), 'utf8')
const script = await readFile(new URL('../entity-design/useEntityPermissions.js', import.meta.url), 'utf8')
const ast = babelParse(script, { sourceType: 'module' })
const names = new Set([
  'permissionSqlPreview', 'permissionSqlPreviewVisible', 'permissionSqlPreviewTitle',
  'permissionPreviewRule', 'simulationUserId', 'permissionPreviewLoading',
  'permissionPreviewError', 'permissionPreviewRequestId',
  'handlePreviewPermissionSql', 'loadPermissionPreview'
])
const workspaceBody = ast.program.body.find(node => node.type === 'ExportNamedDeclaration' && node.declaration?.id?.name === 'useEntityPermissions').declaration.body.body
const code = workspaceBody
  .filter(node => node.type === 'VariableDeclaration'
    && node.declarations.some(item => names.has(item.id.name)))
  .map(node => script.slice(node.start, node.end)).join('\n')

/** 执行页面真实的模拟请求和状态转换，复现换人时请求乱序、失败后仍显示旧 SQL 的问题。 */
function createPreview() {
  const requests = []
  const api = { previewSql: (policyId, userId) => new Promise((resolve, reject) => {
    requests.push({ policyId, userId, resolve, reject })
  }) }
  const page = new Function('ref', 'entityListScopeRuleApi', 'console',
    `${code}\nreturn { ${[...names].filter(name => name !== 'permissionPreviewRequestId').join(', ')} }`
  )(value => ({ value }), api, { error() {} })
  return { ...page, requests }
}

const page = createPreview()
const opening = page.handlePreviewPermissionSql({ policyId: 'personal', ruleName: '本人创建或提交', boundListKeys: [] })
assert.equal(page.permissionSqlPreviewVisible.value, true, '先打开结果面板，允许选择模拟人员')
assert.equal(page.requests[0].policyId, 'personal', '未绑定规则也必须使用当前行规则 ID')
assert.equal(page.requests[0].userId, '', '未选择人员时由服务端使用登录用户')
page.requests[0].resolve({ sql: "create_by = 'admin' OR submitter_id = 'admin'", ruleName: '本人创建或提交' })
await opening

page.simulationUserId.value = 'u1'
const older = page.loadPermissionPreview()
assert.equal(page.permissionSqlPreview.value, null, '换人后立即移除旧 SQL，避免误读')
page.simulationUserId.value = 'u2'
const newer = page.loadPermissionPreview()
assert.deepEqual(page.requests.slice(1).map(({ policyId, userId }) => [policyId, userId]), [
  ['personal', 'u1'], ['personal', 'u2']
])
page.requests[2].resolve({ sql: "create_by = 'u2'", username: '用户二' })
await newer
page.requests[1].resolve({ sql: "create_by = 'u1'", username: '用户一' })
await older
assert.equal(page.permissionSqlPreview.value.username, '用户二', '旧请求返回不能覆盖新用户的模拟结果')
assert.equal(page.permissionPreviewLoading.value, false)

const failed = page.loadPermissionPreview()
page.requests[3].reject(new Error('模拟用户不存在'))
await failed
assert.equal(page.permissionSqlPreview.value, null)
assert.equal(page.permissionPreviewError.value, '模拟用户不存在')
assert.equal(page.permissionPreviewLoading.value, false)

page.simulationUserId.value = ''
const retry = page.loadPermissionPreview()
assert.equal(page.requests[4].userId, '', '清空选择恢复当前登录用户')
page.requests[4].resolve({ sql: '1=0' })
await retry
assert.equal(page.permissionSqlPreview.value.sql, '1=0', '合法的空范围不能回退为全放行')
assert.equal(page.permissionPreviewError.value, '')

const dialog = source.slice(source.indexOf('<!-- 单条规则模拟'), source.indexOf('<QuickDictDialog'))
assert.match(dialog, /v-model="simulationUserId"/)
assert.match(dialog, /@change="loadPermissionPreview"/)
assert.doesNotMatch(source.slice(source.indexOf('<div class="permission-header">'), source.indexOf('<!-- 规则编辑对话框 -->')), /simulationUserId/)
console.log('规则模拟：规则定位、人员切换、请求乱序、失败重试验证通过')
