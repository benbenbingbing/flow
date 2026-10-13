import assert from 'node:assert/strict'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import path from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 挂载真实编辑器与实体多选组件，所有 API 使用内存数据，避免触碰业务配置。
const fixture = mkdtempSync(path.resolve('.interface-scope-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import { createApp, h, ref } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Editor from '/src/components/ui-config/InterfaceExtensionEditorDialog.vue'
import { entityApi } from '/src/api/entity.js'
import { entityListConfigApi } from '/src/api/entityListConfig.js'
import { uiExtensionApi } from '/src/api/uiConfig.js'
import request from '/src/utils/request.js'
const entities = [
  { id: 'entity-a', entityCode: 'PROJECT', entityName: '项目', status: 'PUBLISHED', lifecycleMode: 'STANDALONE', storageMode: 'DYNAMIC' },
  { id: 'entity-b', entityCode: 'CONTRACT', entityName: '合同', status: 'PUBLISHED', lifecycleMode: 'STANDALONE', storageMode: 'DYNAMIC' }
]
const writes = []
request.defaults.adapter = async config => ({ status: 200, statusText: 'OK', headers: {}, config, data: { code: 200, data: { entityId: 'entity-a' } } })
entityApi.resolveOptions = async ({ ids }) => entities.filter(item => ids.includes(item.id))
entityApi.getOptions = async () => ({ records: entities, total: 2 })
entityListConfigApi.getById = async () => ({ entityId: 'entity-b' })
uiExtensionApi.create = async data => writes.push(JSON.parse(JSON.stringify(data)))
uiExtensionApi.update = async (id, data) => writes.push(JSON.parse(JSON.stringify(data)))
const editor = ref()
createApp({ setup() { return () => h(Editor, { ref: editor, catalog: { providers: [{ code: 'test', name: '测试 Provider' }] } }) } }).use(createPinia()).use(ElementPlus).mount('#app')
window.scopeCheck = { editor, writes }
`)

let server, browser
try {
  server = await createServer({
    cacheDir: path.join(fixture, 'cache'),
    optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios'] },
    server: { host: '127.0.0.1', port: 3409, strictPort: true }
  })
  await server.listen()
  browser = await chromium.launch({
    executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    headless: true
  })
  const page = await browser.newPage({ viewport: { width: 1280, height: 1050 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:3409/${path.basename(fixture)}/index.html`)
  await page.waitForFunction(() => Boolean(window.scopeCheck))
  await page.evaluate(() => window.scopeCheck.editor.value.openCreate())
  const dialog = page.getByRole('dialog', { name: '新增扩展接口' })
  const formItem = (owner, label) => owner.locator('.el-form-item').filter({
    has: page.locator('.el-form-item__label', { hasText: label })
  })
  await formItem(dialog, '接口名称').locator('input').fill('多实体列表查询')
  await formItem(dialog, '接口编码').locator('input').fill('multi.entity.list')
  await formItem(dialog, '实现类型').locator('.el-select').click()
  await page.getByRole('option', { name: '平台静态数据', exact: true }).click()
  await formItem(dialog, '业务上下文').getByText('列表', { exact: true }).click()
  assert.match(await formItem(dialog, '作用范围').innerText(), /所选实体的所有列表/)
  const scopeSelect = formItem(dialog, '作用范围').locator('.el-select')
  await scopeSelect.click()
  const scopeMenu = page.locator(`#${await scopeSelect.locator('input').getAttribute('aria-controls')}`)
  await scopeMenu.getByRole('option').first().waitFor()
  assert.deepEqual(await scopeMenu.getByRole('option').allTextContents(), ['全部实体', '选择实体'])
  await page.getByRole('option', { name: '选择实体', exact: true }).click()
  await formItem(dialog, '作用实体').getByRole('button').click()
  const picker = page.getByRole('dialog', { name: '选择扩展接口作用实体' })
  await picker.locator('.el-table__body tr').nth(0).click()
  await picker.locator('.el-table__body tr').nth(1).click()
  await picker.getByRole('button', { name: /确认选择\s*（2）/ }).click()
  await picker.waitFor({ state: 'hidden' })
  await formItem(dialog, '作用实体').getByText('已选 2 个实体').waitFor()
  if (process.env.SCOPE_SCREENSHOT) {
    await page.screenshot({ path: process.env.SCOPE_SCREENSHOT, fullPage: true })
  }
  await dialog.getByRole('button', { name: '保存扩展接口' }).click()
  await page.waitForFunction(() => window.scopeCheck.writes.length === 1)
  await dialog.waitFor({ state: 'hidden' })
  const saved = await page.evaluate(() => window.scopeCheck.writes[0])
  assert.deepEqual(saved.scopeEntityIds, ['entity-a', 'entity-b'])
  assert.equal(saved.scopeType, 'ENTITY')
  assert.equal('scopeId' in saved, false)

  // 保存后重新打开，验证完整多选回显；切换全部实体必须清除旧的选择载荷。
  await page.evaluate(value => window.scopeCheck.editor.value.openEdit(value), { ...saved, id: 'ext-1', revision: 1 })
  const edit = page.getByRole('dialog', { name: '编辑扩展接口' })
  await edit.getByText('已选 2 个实体').waitFor()
  await formItem(edit, '作用范围').locator('.el-select').click()
  await page.getByRole('option', { name: '全部实体', exact: true }).click()
  assert.equal(await edit.locator('.entity-definition-picker').count(), 0)
  await edit.getByRole('button', { name: '保存扩展接口' }).click()
  await page.waitForFunction(() => window.scopeCheck.writes.length === 2)
  await edit.waitFor({ state: 'hidden' })
  assert.deepEqual(await page.evaluate(() => window.scopeCheck.writes[1].scopeEntityIds), [])

  // 旧表单/列表范围编辑时须选择所属实体，不能保留页面级入口或误扩展成 GLOBAL。
  for (const [scopeType, scopeId, expected] of [
    ['ENTITY', 'entity-a', '项目'], ['FORM', 'old-form', '项目'], ['LIST', 'old-list', '合同']
  ]) {
    const legacy = { ...saved, id: `legacy-${scopeType}`, scopeType, scopeId }
    delete legacy.scopeEntityIds
    await page.evaluate(value => window.scopeCheck.editor.value.openEdit(value), legacy)
    try {
      await edit.getByText('已选 1 个实体').waitFor({ timeout: 5000 })
    } catch (error) {
      console.error(scopeType, await page.locator('body').innerText())
      throw error
    }
    assert.match(await edit.locator('.entity-definition-picker').innerText(), new RegExp(expected))
    await edit.getByRole('button', { name: '取消', exact: true }).click()
    await edit.waitFor({ state: 'hidden' })
  }
  assert.deepEqual(errors, [])
  console.log('interface entity scope browser checks passed')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
