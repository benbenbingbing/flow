import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 挂载真实菜单页，模拟分页端点，验证树定位及写操作不会重新查询完整目录。
const fixture = mkdtempSync(path.resolve('.menu-fixture-'))
const harness = `
import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import Page from '/src/views/system/Menu.vue'
import request from '/src/utils/request'

const root = {
  id: 'system', parentId: '0', menuName: '系统管理', menuType: 'M',
  sort: 0, status: '0', visible: '0', icon: 'Setting'
}
const other = { ...root, id: 'other', menuName: '其他目录' }
const children = Array.from({ length: 55 }, (_, index) => ({
  id: 'page' + String(index).padStart(2, '0'), parentId: 'system',
  menuName: '页面' + String(index).padStart(2, '0'), menuType: 'C', sort: 0,
  path: '/pages/' + index, component: 'system/Test', status: index === 0 ? '1' : '0',
  visible: index === 0 ? '1' : '0', perm: 'pages:view:' + index
}))
const nested = {
  ...children[0], id: 'nested', parentId: 'page54', menuName: '深层菜单',
  status: '0', path: '/deep/entry', perm: 'deep:entry:view'
}
const button = { ...nested, id: 'button', parentId: 'nested', menuName: '新增按钮', menuType: 'F', path: '' }
const state = {
  rows: [root, other, ...children, nested, button], calls: [], writes: [],
  failNextPage: false, failNextSort: false, delayNextPage: 0
}
function ancestors(row) {
  const result = [row]
  while (result[0].parentId && result[0].parentId !== '0') {
    result.unshift(state.rows.find(item => item.id === result[0].parentId))
  }
  return result
}
function decorate(row, params = {}) {
  const chain = ancestors(row)
  return {
    ...row,
    displayPath: chain.map(item => item.menuName).join(' / '),
    hasChildren: state.rows.some(item => item.parentId === row.id
      && (!params.parentCandidates || item.menuType !== 'F') && item.id !== params.excludeId),
    selectionDisabled: chain.some(item => item.id === params.excludeId)
  }
}
request.get = async (url, config = {}) => {
  const params = config.params || {}
  state.calls.push({ url, ...params })
  if (url === '/system/menu/page') {
    if (state.failNextPage) {
      state.failNextPage = false
      throw Error('模拟分页失败')
    }
    let rows = state.rows.filter(row => params.keyword
      ? [row.menuName, row.path, row.perm].some(value => value?.includes(params.keyword))
      : row.parentId === (params.parentId || '0'))
    if (params.type) rows = rows.filter(row => row.menuType === params.type)
    if (params.parentCandidates) rows = rows.filter(row => row.menuType !== 'F')
    if (params.excludeId) rows = rows.filter(row => row.id !== params.excludeId)
    rows.sort((first, second) => first.sort - second.sort || first.id.localeCompare(second.id))
    const start = ((params.pageNum || 1) - 1) * params.pageSize
    const page = { total: rows.length, records: rows.slice(start, start + params.pageSize).map(row => decorate(row, params)) }
    if (state.delayNextPage) {
      const delay = state.delayNextPage
      state.delayNextPage = 0
      await new Promise(resolve => setTimeout(resolve, delay))
    }
    return page
  }
  const id = decodeURIComponent(url.split('/')[3])
  const row = state.rows.find(item => item.id === id)
  if (!row) throw Error('未模拟端点 ' + url)
  if (url.endsWith('/ancestors')) return ancestors(row).map(item => decorate(item))
  return { ...row }
}
request.post = async (url, payload) => {
  state.writes.push({ url, payload })
  const parsed = new URL(url, 'http://fixture')
  const id = decodeURIComponent(parsed.pathname.split('/')[3] || '')
  const row = state.rows.find(item => item.id === id)
  if (parsed.pathname.endsWith('/sort')) {
    if (state.failNextSort) {
      state.failNextSort = false
      throw Error('模拟排序失败')
    }
    row.sort = payload.sort
  } else if (parsed.pathname.endsWith('/status')) row.status = parsed.searchParams.get('status')
  else if (parsed.pathname.endsWith('/visible')) row.visible = parsed.searchParams.get('visible')
  else if (parsed.pathname.endsWith('/delete')) {
    state.rows = state.rows.filter(item => !ancestors(item).some(parent => parent.id === id))
  } else if (parsed.pathname.endsWith('/update')) Object.assign(row, payload)
  else if (url === '/system/menu') state.rows.push({ ...payload, id: 'created' })
  else throw Error('未模拟写入 ' + url)
  return {}
}
createApp(Page).use(createPinia()).use(ElementPlus, { locale: zhCn }).mount('#app')
window.menuTest = {
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
  await page.waitForFunction(() => !!window.menuTest)
  const settle = () => page.evaluate(() => menuTest.settle())
  const table = page.locator('.menu-content .el-table__body')
  const names = table.locator('.menu-name')
  const sidebar = page.locator('.menu-sidebar')
  const breadcrumb = page.locator('.menu-breadcrumb')
  const search = page.locator('.list-toolbar .el-input input')
  const searchButton = page.locator('.list-toolbar').getByRole('button', { name: '搜索', exact: true })
  const dialog = page.locator('.el-dialog').filter({ hasText: '编辑菜单' })
  const picker = page.locator('.el-dialog').filter({ has: page.locator('.el-dialog__title').filter({ hasText: /^选择上级菜单$/ }) })
  const waitNames = count => page.waitForFunction(count => document.querySelectorAll('.menu-content .menu-name').length === count, count)
  const switchCell = index => table.locator('tr').first().locator('.el-switch').nth(index)
  await waitNames(2)
  await page.waitForFunction(() => document.querySelector('.menu-sidebar .el-tree')?.innerText.includes('系统管理'))
  assert.equal(await page.evaluate(() => menuTest.state.calls.some(call => call.parentId === 'system')), false)
  await names.getByText('系统管理', { exact: true }).click()
  await waitNames(20)
  assert.equal(await names.first().innerText(), '页面00')
  assert.equal(await switchCell(0).locator('input').isChecked(), false)

  // 状态确认取消不写入，确认后才更新本行及侧栏刷新通知。
  await switchCell(0).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(await switchCell(0).locator('input').isChecked(), false)
  assert.equal(await page.evaluate(() => menuTest.state.writes.length), 0)
  await switchCell(0).click()
  await page.getByRole('button', { name: '确认启用', exact: true }).click()
  await page.waitForFunction(() => menuTest.state.rows.find(row => row.id === 'page00').status === '0')
  await settle()
  assert.equal(await switchCell(0).locator('input').isChecked(), true)
  await switchCell(1).click()
  await page.getByRole('button', { name: '确认显示', exact: true }).click()
  await page.waitForFunction(() => menuTest.state.rows.find(row => row.id === 'page00').visible === '0')

  await page.locator('.pagination-wrapper .el-pager li').filter({ hasText: /^3$/ }).click()
  await waitNames(15)
  assert.equal(await names.first().innerText(), '页面40')
  await search.fill('deep:entry:view')
  await searchButton.click()
  await waitNames(2)
  await table.locator('tr').filter({ hasText: '深层菜单' }).filter({ has: page.locator('.menu-name').getByText('深层菜单', { exact: true }) }).getByText('定位到树', { exact: true }).click()
  await waitNames(1)
  await page.waitForFunction(() => document.querySelector('.menu-sidebar .is-current')?.innerText.includes('深层菜单'))
  assert.equal(await names.first().innerText(), '新增按钮')
  assert.equal((await breadcrumb.innerText()).includes('页面54'), true)
  assert.equal(await table.getByText('新增', { exact: true }).count(), 0, '按钮不能新增下级')
  await sidebar.getByText('加载更多…', { exact: true }).click()
  await settle()
  assert.equal(await sidebar.getByText('页面54', { exact: true }).count(), 1)
  await breadcrumb.getByText('系统管理', { exact: true }).click()
  await waitNames(20)

  await search.fill('页面54')
  await searchButton.click()
  await waitNames(1)
  await table.getByText('编辑', { exact: true }).click()
  await dialog.waitFor({ state: 'visible' })
  assert.equal(await dialog.locator('.parent-picker-field input').inputValue(), '系统管理')
  await dialog.getByText('选择上级', { exact: true }).click()
  await picker.waitFor({ state: 'visible' })
  await picker.locator('.el-input input').fill('深层')
  await picker.getByRole('button', { name: '搜索', exact: true }).click()
  await picker.locator('.search-result').waitFor()
  assert.equal(await picker.locator('.search-result').isDisabled(), true, '搜索不能选择下级作为上级')
  await picker.locator('input').fill('页面54')
  await picker.getByRole('button', { name: '搜索', exact: true }).click()
  await picker.getByText('暂无匹配上级菜单', { exact: true }).waitFor()
  await picker.locator('input').fill('其他目录')
  await picker.getByRole('button', { name: '搜索', exact: true }).click()
  await picker.locator('.search-result').click()
  await picker.waitFor({ state: 'hidden' })
  assert.equal(await dialog.locator('.parent-picker-field input').inputValue(), '其他目录')
  await dialog.getByRole('button', { name: '保存菜单', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  const editWrite = await page.evaluate(() => menuTest.state.writes.find(write => write.url.endsWith('/page54/update')))
  assert.equal(editWrite.payload.parentId, 'other')
  assert.equal(Object.hasOwn(editWrite.payload, 'displayPath'), false)
  assert.equal(Object.hasOwn(editWrite.payload, 'hasChildren'), false)

  // 单行排序请求只包含 sort，失败时控件恢复旧值，成功后列表按新顺序刷新。
  await breadcrumb.getByText('系统管理', { exact: true }).click()
  await waitNames(20)
  const sortInput = table.locator('tr').first().locator('.el-input-number input')
  await page.evaluate(() => { menuTest.state.failNextSort = true })
  await sortInput.fill('8')
  await sortInput.press('Tab')
  await page.getByText('模拟排序失败', { exact: true }).waitFor()
  await settle()
  assert.equal(await sortInput.inputValue(), '0')
  await sortInput.fill('8')
  await sortInput.press('Tab')
  await page.waitForFunction(() => document.querySelector('.menu-content .menu-name')?.textContent === '页面01')
  const sortWrite = await page.evaluate(() => menuTest.state.writes.filter(write => write.url.endsWith('/sort')).at(-1))
  assert.deepEqual(sortWrite.payload, { sort: 8 })

  await page.evaluate(() => { menuTest.state.delayNextPage = 500 })
  await page.locator('.pagination-wrapper .el-pager li').filter({ hasText: /^2$/ }).click()
  await search.fill('/deep/entry')
  await searchButton.click()
  await waitNames(1)
  await page.waitForTimeout(600)
  assert.equal(await names.first().innerText(), '深层菜单')
  await page.evaluate(() => { menuTest.state.failNextPage = true })
  await searchButton.click()
  await page.getByText('模拟分页失败', { exact: true }).waitFor()
  await page.locator('.menu-content').getByRole('button', { name: '重新加载', exact: true }).click()
  await waitNames(1)

  // 末页最后一条删除后自动回退，保留当前父级和每页大小。
  await breadcrumb.getByText('系统管理', { exact: true }).click()
  await waitNames(20)
  await page.evaluate(() => {
    const keep = new Set(['system', 'other', ...Array.from({ length: 21 }, (_, index) => 'page' + String(index).padStart(2, '0'))])
    menuTest.state.rows = menuTest.state.rows.filter(row => keep.has(row.id))
    menuTest.state.rows.forEach(row => { row.sort = 0 })
  })
  await searchButton.click()
  await settle()
  await page.locator('.pagination-wrapper .el-pager li').filter({ hasText: /^2$/ }).click()
  await waitNames(1)
  await table.getByText('删除', { exact: true }).click()
  const confirmation = page.locator('.el-message-box')
  await confirmation.locator('input').fill('页面20')
  await confirmation.getByRole('button', { name: '确认删除', exact: true }).click()
  await waitNames(20)
  assert.equal(await page.locator('.pagination-wrapper .el-pager .is-active').innerText(), '1')
  assert.equal(await page.evaluate(() => menuTest.state.calls.some(call => /\/(tree|subtree|children)$/.test(call.url))), false)
  assert.equal(await page.evaluate(() => menuTest.state.calls.some(call => call.pageSize > 100)), false)
  await confirmation.waitFor({ state: 'hidden' })
  await page.waitForFunction(() => document.querySelector('.menu-sidebar .is-current')?.innerText.includes('系统管理'))
  await page.waitForTimeout(300)
  await page.screenshot({ path: '/tmp/flow-menu-pagination.png', fullPage: true })
  await names.getByText('页面01', { exact: true }).click()
  await waitNames(0)
  await search.fill('系统管理')
  await searchButton.click()
  await waitNames(1)
  await table.getByText('删除', { exact: true }).click()
  await confirmation.locator('input').fill('系统管理')
  await confirmation.getByRole('button', { name: '确认删除', exact: true }).click()
  await waitNames(1)
  assert.equal(await names.first().innerText(), '其他目录')
  assert.equal((await breadcrumb.innerText()).includes('系统管理'), false)
  assert.deepEqual(errors, [])
  console.log('菜单分页浏览器回归通过，覆盖按需加载、翻页、搜索定位、上级限制、状态与显示确认、排序回退、过期响应和末页删除。')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
