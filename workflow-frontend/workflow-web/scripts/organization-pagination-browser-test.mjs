import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 使用真实 Vue 页面和 Element Plus，以有界接口夹具覆盖懒加载、搜索定位和上级选择。
const fixture = mkdtempSync(path.resolve('.organization-fixture-'))
const harness = `
import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import Page from '/src/views/system/Organization.vue'
import request from '/src/utils/request'
import { useUserStore } from '/src/stores/user'
const group = {
  id: 'group', parentId: '0', orgName: '测试集团', orgCode: 'GROUP', type: 'org',
  path: '/0/group/', displayPath: '测试集团', status: '0', hasChildren: true
}
const other = {
  ...group, id: 'other', orgName: '另一集团', orgCode: 'OTHER',
  path: '/0/other/', displayPath: '另一集团', hasChildren: false
}
const departments = Array.from({ length: 55 }, (_, index) => {
  const suffix = String(index).padStart(2, '0')
  return {
    id: 'dept' + suffix, parentId: 'group', orgName: '部门' + suffix,
    orgCode: 'D' + index, type: 'dept', status: index === 0 ? '1' : '0',
    hasChildren: index === 54, path: '/0/group/dept' + suffix + '/',
    displayPath: '测试集团 / 部门' + suffix
  }
})
const nested = {
  ...departments[54], id: 'nested', parentId: 'dept54', orgName: '深层部门', orgCode: 'NESTED',
  path: '/0/group/dept54/nested/', displayPath: '测试集团 / 部门54 / 深层部门', hasChildren: false
}
const state = {
  rows: [group, other, ...departments, nested], calls: [], writes: [],
  failNextPage: false, delayNextPage: 0
}
request.get = async (url, config = {}) => {
  const params = config.params || {}
  state.calls.push({ url, ...params })
  if (url === '/system/org/business-level-options' || url === '/system/position/enabled') return []
  if (url === '/system/identity/extensions/schema') return []
  if (url.startsWith('/system/identity/extensions/')) return { extensions: {} }
  if (url === '/system/org/page') {
    if (state.failNextPage) {
      state.failNextPage = false
      throw Error('模拟加载失败')
    }
    let rows = state.rows.filter(row => {
      if (!params.keyword) return row.parentId === (params.parentId || '0')
      const scope = state.rows.find(item => item.id === params.parentId)
      const inScope = !params.parentId || params.parentId === '0' || row.path.startsWith(scope.path)
      return inScope && (row.orgName.includes(params.keyword) || row.orgCode.includes(params.keyword))
    })
    if (params.type) rows = rows.filter(row => row.type === params.type)
    if (params.excludeId) {
      const excluded = state.rows.find(row => row.id === params.excludeId)
      rows = rows.filter(row => !row.path.startsWith(excluded.path))
    }
    const start = ((params.pageNum || 1) - 1) * params.pageSize
    const page = {
      total: rows.length,
      records: rows.slice(start, start + params.pageSize).map(row => ({ ...row }))
    }
    if (state.delayNextPage) {
      const delay = state.delayNextPage
      state.delayNextPage = 0
      await new Promise(resolve => setTimeout(resolve, delay))
    }
    return page
  }
  const id = decodeURIComponent(url.split('/')[3])
  const row = state.rows.find(item => item.id === id)
  if (!row) throw Error('找不到 ' + url)
  if (url.endsWith('/ancestors')) {
    return row.path.split('/').filter(id => id && id !== '0')
      .map(id => ({ ...state.rows.find(item => item.id === id) }))
  }
  return { ...row }
}
request.post = async (url, payload) => {
  state.writes.push({ url, payload })
  const id = decodeURIComponent(url.split('/')[3])
  if (url.endsWith('/delete')) state.rows = state.rows.filter(row => row.id !== id)
  else if (url.endsWith('/update')) Object.assign(state.rows.find(row => row.id === id), payload)
  return {}
}
const pinia = createPinia()
const app = createApp(Page).use(pinia).use(ElementPlus, { locale: zhCn })
useUserStore(pinia).setPermissions(['system:organization:view', 'system:organization:manage', 'system:position:assign'])
app.mount('#app')
window.organizationTest = {
  state,
  settle: async () => {
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 60))
    await nextTick()
  }
}
`
writeFileSync(path.join(fixture, 'index.html'), '<html lang="zh-CN"><meta charset="utf-8"><body style="margin:20px"><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), server: { host: '127.0.0.1', port: 0 } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/${path.basename(fixture)}/index.html`)
  await page.waitForFunction(() => !!window.organizationTest)
  const settle = () => page.evaluate(() => organizationTest.settle())
  const table = page.locator('.organization-content .el-table__body')
  const names = table.locator('.org-name')
  const sidebar = page.locator('.organization-sidebar')
  const breadcrumb = page.locator('.organization-breadcrumb')
  const search = page.locator('.list-toolbar .el-input input')
  const dialog = page.locator('.el-dialog').filter({ hasText: '编辑组织部门' })
  const picker = page.locator('.el-dialog').filter({ hasText: '选择上级组织部门' })
  const waitNames = count => page.waitForFunction(count => document.querySelectorAll('.organization-content .org-name').length === count, count)
  await waitNames(2)
  await page.waitForFunction(() => document.querySelector('.organization-sidebar .el-tree')?.innerText.includes('测试集团'))
  assert.equal(await page.evaluate(() => organizationTest.state.calls.some(c => c.url.endsWith('/tree') || c.url.endsWith('/enabled'))), false)
  assert.equal(await page.evaluate(() => organizationTest.state.calls.some(c => c.parentId === 'group')), false, '首屏不能预读集团下级')

  await names.getByText('测试集团', { exact: true }).click()
  await waitNames(20)
  assert.equal(await names.first().innerText(), '部门00')
  assert.equal((await table.innerText()).includes('禁用'), true)
  await page.locator('.organization-pagination .el-pager li').filter({ hasText: /^3$/ }).click()
  await waitNames(15)
  assert.equal(await names.first().innerText(), '部门40')

  await search.fill('深层')
  await page.locator('.list-toolbar button').getByText('搜索', { exact: true }).click()
  await waitNames(1)
  assert.equal(await names.first().innerText(), '深层部门')
  assert.equal((await table.innerText()).includes('测试集团 / 部门54 / 深层部门'), true)
  await table.getByText('定位到树', { exact: true }).click()
  await waitNames(0)
  await page.waitForFunction(() => document.querySelector('.organization-sidebar .is-current')?.innerText.includes('深层部门'))
  assert.equal((await breadcrumb.innerText()).includes('部门54'), true)
  assert.equal(await search.inputValue(), '')
  // 目标在第 2 批同级数据中，加载更多不能重复插入定位时补齐的部门。
  await sidebar.getByText('加载更多…', { exact: true }).click()
  await settle()
  assert.equal(await sidebar.getByText('部门54', { exact: true }).count(), 1)
  await breadcrumb.getByText('测试集团', { exact: true }).click()
  await waitNames(20)

  await table.locator('tr').first().getByText('编辑', { exact: true }).click()
  await dialog.waitFor({ state: 'visible' })
  assert.equal(await dialog.locator('.parent-picker-field input').inputValue(), '测试集团')
  await dialog.getByText('选择上级', { exact: true }).click()
  await picker.waitFor({ state: 'visible' })
  await picker.locator('.el-input input').fill('部门00')
  await picker.getByText('搜索', { exact: true }).click()
  await picker.getByText('暂无匹配组织部门', { exact: true }).waitFor()
  await picker.locator('input').fill('部门54')
  await picker.getByText('搜索', { exact: true }).click()
  await picker.locator('.search-result').filter({ hasText: '部门54' }).click()
  await picker.waitFor({ state: 'hidden' })
  assert.equal(await dialog.locator('.parent-picker-field input').inputValue(), '测试集团 / 部门54')
  await dialog.getByText('取消', { exact: true }).click()

  await table.locator('tr').first().getByText('职务任命', { exact: true }).click()
  const assignment = page.locator('.el-dialog').filter({ hasText: '新增任职' })
  await assignment.waitFor({ state: 'visible' })
  await page.waitForFunction(() => organizationTest.state.calls.some(call => call.url === '/system/position/enabled'))
  assert.equal(await page.evaluate(() => organizationTest.state.calls.some(call => call.url.endsWith('/tree'))), false,
    '锁定组织节点的任职弹窗不能重新加载全量组织树')
  await assignment.getByText('取消', { exact: true }).click()

  // 过期分页响应晚于新搜索返回时，表格仍应保持最新搜索结果。
  await page.evaluate(() => { organizationTest.state.delayNextPage = 500 })
  await page.locator('.organization-pagination .el-pager li').filter({ hasText: /^2$/ }).click()
  await search.fill('深层')
  await page.locator('.list-toolbar button').getByText('搜索', { exact: true }).click()
  await waitNames(1)
  await page.waitForTimeout(600)
  assert.equal(await names.first().innerText(), '深层部门')
  await page.evaluate(() => { organizationTest.state.failNextPage = true })
  await page.locator('.list-toolbar button').getByText('搜索', { exact: true }).click()
  await page.getByText('模拟加载失败', { exact: true }).waitFor()
  await page.locator('.organization-content').getByRole('button', { name: '重新加载', exact: true }).click()
  await waitNames(1)

  await breadcrumb.getByText('测试集团', { exact: true }).click()
  await waitNames(20)
  // 构造末页单条，再通过真实删除确认流验证自动退页。
  await page.evaluate(() => {
    const keep = new Set(['group', 'other', ...Array.from({ length: 21 }, (_, i) => 'dept' + String(i).padStart(2, '0'))])
    organizationTest.state.rows = organizationTest.state.rows.filter(r => keep.has(r.id))
  })
  await page.locator('.list-toolbar button').getByText('搜索', { exact: true }).click()
  await settle()
  await page.locator('.organization-pagination .el-pager li').filter({ hasText: /^2$/ }).click()
  await waitNames(1)
  await table.getByText('删除', { exact: true }).click()
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  await waitNames(20)
  assert.equal(await page.locator('.organization-pagination .el-pager .is-active').innerText(), '1')
  assert.equal(await page.evaluate(() => organizationTest.state.calls.some(c => Number(c.pageSize) > 100)), false)
  assert.deepEqual(errors, [])
  await page.locator('.el-message-box').waitFor({ state: 'hidden' })
  await page.waitForFunction(() => document.querySelector('.organization-sidebar .is-current')?.innerText.includes('测试集团'))
  await page.locator('.organization-content .el-table__body-wrapper .el-scrollbar__wrap').evaluate(node => { node.scrollLeft = 0 })
  await page.waitForTimeout(300)
  await page.screenshot({ path: '/tmp/flow-organization-pagination.png', fullPage: true })
  console.log('组织分页浏览器回归通过，覆盖按需加载、分页、搜索定位、上级选择、过期响应、失败重试和末页删除。')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
