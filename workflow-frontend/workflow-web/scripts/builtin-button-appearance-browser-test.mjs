import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 挂载真实编辑器和子表组件，验证修改外观后的 DOM、点击行为及原有权限边界。
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const fixture = mkdtempSync(path.join(root, '.builtin-button-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import { createApp, h, reactive } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Button from '/src/components/BuiltInActionButton.vue'
import Editor from '/src/components/BuiltInButtonAppearanceEditor.vue'
import SubForm from '/src/components/SubFormRenderer.vue'
const state = reactive({ config: {}, editorEpoch: 0, disabled: false, loading: false, calls: 0, rows: [{}], readonly: false, layout: 'form', minRows: 0 })
const actions = [{key:'save',label:'保存子表单',icon:'Check',buttonType:'primary',buttonAppearance:'LINK'}]
window.buttonTest = state
createApp({render:()=>h('main',{style:'max-width:920px;margin:24px auto'},[
 h('section',{id:'editor'},[h(Editor,{key:state.editorEpoch,modelValue:state.config,actions,'onUpdate:modelValue':v=>state.config=v})]),
 h('section',{id:'fixed-button'},[h(Button,{actionKey:'save',config:state.config,defaults:actions[0],disabled:state.disabled,loading:state.loading,onClick:()=>state.calls++})]),
 h('section',{id:'subform'},[h(SubForm,{modelValue:state.rows,readonly:state.readonly,config:{layout:state.layout,relationType:'ONE_TO_MANY',repeatable:true,fields:[],maxRows:3,minRows:state.minRows,actionBar:state.config},'onUpdate:modelValue':v=>state.rows=v})])
])}).use(createPinia()).use(ElementPlus).mount('#app')
`)
let server, browser
try {
  server = await createServer({ root, cacheDir: path.join(fixture, 'cache'), configFile: path.join(root, 'vite.config.js'), optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 0 } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1200, height: 1100 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`${server.resolvedUrls.local[0]}${path.basename(fixture)}/index.html`)
  await page.waitForSelector('#fixed-button button')
  assert.match(await page.locator('#fixed-button button').getAttribute('class'), /is-link/)
  await page.locator('#fixed-button button').click()
  assert.equal(await page.evaluate(() => buttonTest.calls), 1)

  // 用户在编辑器中改名后，实际按钮仍调用原来的动作；不能编辑功能或隐藏入口。
  assert.equal(await page.getByPlaceholder('保存子表单', { exact: true }).isVisible(), false, '按钮外观默认折叠')
  await page.locator('#editor .appearance-edit-hint').filter({ hasText: '编辑名称和颜色' }).click()
  await page.getByPlaceholder('保存子表单', { exact: true }).waitFor({ state: 'visible' })
  await page.getByPlaceholder('保存子表单', { exact: true }).fill('保存验收信息')
  await page.locator('#fixed-button button').filter({ hasText: '保存验收信息' }).click()
  assert.equal(await page.evaluate(() => buttonTest.calls), 2)
  assert.equal(await page.locator('#editor input[type=checkbox]').count(), 0)
  // 精简后的外观通过主题和样式选择；重新选择时旧的隐藏覆盖值不应阻止新选择生效。
  await page.evaluate(() => Object.assign(buttonTest.config.buttons.save, { textColor: '#ffffff', backgroundColor: '#123456', borderColor: '#123456', borderRadius: 12 }))
  const themeItem = page.locator('#editor .el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: /^主题颜色$/ }) })
  await themeItem.locator('.el-select').click()
  await page.getByRole('option', { name: '成功', exact: true }).click()
  await page.waitForFunction(() => {
    const save = buttonTest.config.buttons.save
    return save.buttonType === 'success' && !('textColor' in save) && !('backgroundColor' in save) && !('borderColor' in save)
  })
  const appearanceItem = page.locator('#editor .el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: /^按钮样式$/ }) })
  await appearanceItem.locator('.el-select').click()
  await page.getByRole('option', { name: '圆角', exact: true }).click()
  await page.waitForFunction(() => buttonTest.config.buttons.save.buttonAppearance === 'ROUND' && !('borderRadius' in buttonTest.config.buttons.save))
  await page.evaluate(() => { buttonTest.config = JSON.parse(JSON.stringify(buttonTest.config)); buttonTest.editorEpoch++ })
  await page.locator('#editor .appearance-edit-hint').filter({ hasText: '编辑名称和颜色' }).click()
  await page.waitForFunction(() => document.querySelector('#editor input[placeholder="保存子表单"]')?.value === '保存验收信息')
  assert.equal(await page.getByPlaceholder('保存子表单', { exact: true }).isVisible(), true)
  await page.waitForFunction(() => {
    const button = document.querySelector('#fixed-button button')
    return button.classList.contains('el-button--success') && button.classList.contains('is-round')
  })
  const collapseHeader = page.locator('#editor .el-collapse-item__header').first()
  await collapseHeader.click()
  await page.getByPlaceholder('保存子表单', { exact: true }).waitFor({ state: 'hidden' })
  assert.match(await collapseHeader.innerText(), /编辑名称和颜色/)
  await collapseHeader.click()
  await page.getByPlaceholder('保存子表单', { exact: true }).waitFor({ state: 'visible' })
  await page.evaluate(() => {
    buttonTest.config = { position: 'BOTH', align: 'RIGHT', gap: 16, buttons: {
      save: { label: '保存验收信息', buttonAppearance: 'DEFAULT', icon: 'Check', backgroundColor: '#123456', textColor: '#ffffff', borderColor: '#123456', borderRadius: 12, size: 'large', enabled: false, visible: false },
      add: { label: '添加验收项', icon: 'Plus', buttonAppearance: 'PLAIN', buttonType: 'success' },
      remove: { label: '移除验收项', icon: 'Delete', buttonAppearance: 'TEXT' }
    } }
  })
  await page.waitForFunction(() => {
    const style = getComputedStyle(document.querySelector('#fixed-button button'))
    return style.borderRadius === '12px' && style.backgroundColor === 'rgb(18, 52, 86)'
  })
  const style = await page.locator('#fixed-button button').evaluate(el => { const s = getComputedStyle(el); return { bg: s.backgroundColor, radius: s.borderRadius } })
  assert.deepEqual(style, { bg: 'rgb(18, 52, 86)', radius: '12px' })
  for (const appearance of ['TEXT', 'LINK']) {
    await page.evaluate(value => { buttonTest.config.buttons.save.buttonAppearance = value }, appearance)
    await page.locator('#fixed-button button').hover()
    await page.waitForFunction(() => {
      const s = getComputedStyle(document.querySelector('#fixed-button button'))
      return s.backgroundColor === 'rgb(18, 52, 86)' && s.color === 'rgb(255, 255, 255)' && s.borderColor === 'rgb(18, 52, 86)'
    })
  }
  await page.evaluate(() => { buttonTest.config.buttons.save.buttonAppearance = 'DEFAULT' })
  if (process.env.BUTTON_APPEARANCE_SCREENSHOT) await page.screenshot({ path: process.env.BUTTON_APPEARANCE_SCREENSHOT, fullPage: true })
  assert.equal(await page.locator('#fixed-button button').isEnabled(), true, '外观字段不能改变功能开关')
  await page.evaluate(() => { buttonTest.disabled = true })
  await page.waitForFunction(() => document.querySelector('#fixed-button button').disabled)
  await page.locator('#fixed-button button').evaluate(el => el.click())
  assert.equal(await page.evaluate(() => buttonTest.calls), 2)
  await page.evaluate(() => { buttonTest.disabled = false; buttonTest.loading = true })
  await page.waitForFunction(() => document.querySelector('#fixed-button button').classList.contains('is-loading'))
  await page.locator('#fixed-button button').evaluate(el => el.click())
  assert.equal(await page.evaluate(() => buttonTest.calls), 2)

  const add = page.locator('#subform [data-builtin-action=add]')
  await page.waitForFunction(() => document.querySelectorAll('#subform [data-builtin-action=add]').length === 2)
  assert.equal(await page.locator('#subform .sub-form-actions').first().evaluate(el => getComputedStyle(el).justifyContent), 'flex-end')
  await add.last().click()
  await page.waitForFunction(() => buttonTest.rows.length === 2)
  await add.first().click()
  await page.waitForFunction(() => buttonTest.rows.length === 3)
  assert.equal(await add.count(), 0, '达到上限后继续遵循原有增行限制')
  await page.locator('#subform [data-builtin-action=remove]').nth(1).click()
  await page.waitForFunction(() => buttonTest.rows.length === 2)
  assert.equal(await add.count(), 2)
  await page.evaluate(() => { buttonTest.readonly = true })
  await page.waitForFunction(() => !document.querySelector('#subform [data-builtin-action]'))
  await page.evaluate(() => { buttonTest.readonly = false; buttonTest.layout = 'table'; buttonTest.minRows = 2 })
  await page.waitForSelector('#subform .el-table')
  assert.equal(await page.locator('#subform [data-builtin-action=remove]').first().isDisabled(), true)
  await page.evaluate(() => { buttonTest.config.buttons.remove = { icon: '', buttonAppearance: 'CIRCLE' } })
  await page.waitForFunction(() => document.querySelector('#subform [data-builtin-action=remove]')?.textContent.trim() === '删除')
  assert.equal(await page.locator('#subform [data-builtin-action=remove]').first().evaluate(el => el.classList.contains('is-circle')), false)

  // 圆形按钮清空图标后回退有文字的按钮，恢复默认也不会改变计数或子表草稿。
  await page.evaluate(() => { buttonTest.loading = false; buttonTest.config.buttons.save = { label:'保存验收信息',buttonAppearance:'CIRCLE',icon:'' } })
  await page.waitForFunction(() => !document.querySelector('#fixed-button button').classList.contains('is-circle'))
  assert.equal(await page.locator('#fixed-button button').innerText(), '保存验收信息')
  assert.equal(await page.locator('#editor .el-select__selected-item').filter({ hasText: /^无图标$/ }).isVisible(), true)
  await page.getByRole('button', { name: '恢复全部默认外观', exact: true }).click()
  await page.waitForFunction(() => Object.keys(buttonTest.config).length === 0)
  assert.equal(await page.locator('#fixed-button button').innerText(), '保存子表单')
  assert.equal(await page.evaluate(() => buttonTest.rows.length), 2)
  assert.deepEqual(errors, [])
  console.log('built-in button browser acceptance passed: editor, styling, immutable actions, disabled/loading, toolbar placement, add/remove, min/max rows, readonly and default restoration')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
