import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 使用真实实体设计页、Element Plus 弹窗和请求拦截器；内存接口不会写入业务数据库。
const fixture = mkdtempSync(path.resolve('.entity-dictionary-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"><link rel="icon" href="data:,"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), String.raw`
import { createApp, reactive, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus, { ElMessage } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import EntityDesign from '/src/views/EntityDesign.vue'
import request from '/src/utils/request.js'
import { useUserStore } from '/src/stores/user.js'
const state = reactive({ calls: [], duplicate: false, saveFailure: false, definitionFailure: false,
  dicts: [{ id:'dict-1',dictCode:'demo_choice',dictName:'费用类别',status:'0' },
    { id:'dict-2',dictCode:'retired',dictName:'停用代码表',status:'1' }],
  items:[{id:'item-1',dictId:'dict-1',dictCode:'demo_choice',parentId:'0',itemCode:'1',itemValue:'1',itemLabel:'交通费',sort:10,status:'0',ext1:'保留扩展'}] })
const fields = [
  {id:'published',fieldCode:'choice',fieldName:'已发布选择',fieldType:'SELECT',dictType:'demo_choice',isPublished:true},
  {id:'draft',fieldCode:'draftChoice',fieldName:'未发布选择',fieldType:'RADIO',dictType:'demo_choice',isPublished:false},
  {id:'retired',fieldCode:'retiredChoice',fieldName:'停用绑定',fieldType:'CHECKBOX',dictType:'retired',isPublished:true},
  {id:'missing',fieldCode:'missingChoice',fieldName:'缺失绑定',fieldType:'MULTI_SELECT',dictType:'missing',isPublished:true}]
request.defaults.adapter = async config => {
  const url = config.url, data = config.data ? JSON.parse(config.data) : null
  state.calls.push({url, method:config.method, data})
  let result=[], code=200, message=''
  if(url==='/entity/demo') result={id:'demo',entityCode:'demo',entityName:'代码表绑定验收',status:'PUBLISHED',lifecycleMode:'STANDALONE',storageMode:'DYNAMIC',fields}
  else if(url==='/system/dict/list') result=state.dicts.filter(dict=>dict.status==='0')
  else if(url.startsWith('/system/dict/code/')) {
    if(state.definitionFailure) {code=500;message='代码表暂时无法读取'}
    else result=state.dicts.find(dict=>dict.dictCode===decodeURIComponent(url.split('/').pop()))||null
  }
  else if(url.startsWith('/system/dict/item/tree/')) result=state.items.filter(item=>item.dictId===url.split('/').pop())
  else if(url==='/system/dict/with-items') {
    if(state.duplicate){code=500;message='字典编码已存在：demo_choice'}
    else {result={...data.dict,id:'created'};state.dicts.push(result)}
  }
  else if(url==='/system/dict/item'||/\/system\/dict\/item\/[^/]+\/update$/.test(url)) {
    if(state.saveFailure){code=500;message='保存暂时失败'}
    else {result={...data,id:data.id||'item-'+(state.items.length+1)};const index=state.items.findIndex(item=>item.id===result.id);if(index<0)state.items.push(result);else state.items[index]=result}
  }
  return {data:{code,message,data:JSON.parse(JSON.stringify(result))},status:200,statusText:'OK',headers:{},config}
}
const pinia=createPinia(), store=useUserStore(pinia)
store.setPermissions(['entity:definition:manage','system:dictionary:view','system:dictionary:manage'])
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/entity/:id/design',component:EntityDesign}]})
await router.push('/entity/demo/design')
const app=createApp({render:()=>h(RouterView)}).use(pinia).use(router).use(ElementPlus)
for(const [name,component] of Object.entries(Icons)) app.component(name,component)
app.mount('#app')
window.dictionaryTest={state, readonly:()=>store.setPermissions(['entity:definition:manage','system:dictionary:view']),clearMessages:()=>ElMessage.closeAll()}
`)

let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'),
    optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios'] },
    server: { host: '127.0.0.1', port: 3442, strictPort: true } })
  await server.listen()
  const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  browser = await chromium.launch({ headless: true, ...(existsSync(chrome) ? { executablePath: chrome } : {}) })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
  page.setDefaultTimeout(10000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('http://127.0.0.1:3442/' + path.basename(fixture) + '/index.html')
  const panel = page.locator('.property-panel')
  const drawer = page.locator('.el-drawer:visible')
  const dialog = page.locator('.el-dialog:visible')
  const selectField = name => page.locator('.field-item').filter({ hasText: name }).click()
  const closeDrawer = async () => {
    await drawer.getByRole('button', { name: '关闭', exact: true }).click()
    await drawer.waitFor({ state: 'hidden' })
  }

  await selectField('已发布选择')
  assert.equal(await panel.locator('.picker-trigger').isDisabled(), true)
  assert.equal(await panel.getByRole('button', { name: /新建/ }).count(), 0)
  await panel.getByRole('button', { name: '查看', exact: true }).click()
  await drawer.getByText('查看代码表', { exact: true }).waitFor()
  await drawer.getByRole('cell', { name: '交通费', exact: true }).waitFor()
  assert.equal(await drawer.getByRole('button', { name: '编辑', exact: true }).count(), 0)
  await closeDrawer()

  await panel.getByRole('button', { name: '维护', exact: true }).click()
  await drawer.getByText('维护代码表', { exact: true }).waitFor()
  await drawer.getByRole('button', { name: '编辑', exact: true }).click()
  assert.equal(await dialog.getByLabel('编码', { exact: true }).isDisabled(), true)
  assert.equal(await dialog.getByLabel('存储值', { exact: true }).isDisabled(), true)
  await dialog.getByLabel('名称', { exact: true }).fill('差旅费')
  await dialog.getByText('停用', { exact: true }).click()
  await page.evaluate(() => { dictionaryTest.state.saveFailure = true; dictionaryTest.clearMessages() })
  await dialog.getByRole('button', { name: '保存代码项', exact: true }).click()
  await page.getByText('保存暂时失败', { exact: true }).waitFor()
  assert.equal(await page.locator('.el-message--error').count(), 1)
  assert.equal(await dialog.getByLabel('名称', { exact: true }).inputValue(), '差旅费')
  await page.evaluate(() => { dictionaryTest.state.saveFailure = false; dictionaryTest.clearMessages() })
  await dialog.getByRole('button', { name: '保存代码项', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  await drawer.getByRole('cell', { name: '差旅费', exact: true }).waitFor()
  assert.deepEqual(await page.evaluate(() => ({code:dictionaryTest.state.items[0].itemCode,value:dictionaryTest.state.items[0].itemValue,status:dictionaryTest.state.items[0].status,ext:dictionaryTest.state.items[0].ext1})),
    { code: '1', value: '1', status: '1', ext: '保留扩展' })
  await drawer.getByRole('button', { name: '新增代码项', exact: true }).click()
  await dialog.getByLabel('编码', { exact: true }).fill('2')
  await dialog.getByLabel('名称', { exact: true }).fill('餐费')
  await dialog.getByRole('button', { name: '保存代码项', exact: true }).click()
  await drawer.getByRole('cell', { name: '餐费', exact: true }).waitFor()
  await closeDrawer()

  await selectField('停用绑定')
  assert.equal(await panel.locator('.picker-trigger').isDisabled(), true)
  await panel.getByRole('button', { name: '查看', exact: true }).click()
  await drawer.getByText('此代码表已停用，当前字段仍保留原绑定。', { exact: true }).waitFor()
  await closeDrawer()
  await selectField('缺失绑定')
  await panel.getByRole('button', { name: '查看', exact: true }).click()
  await drawer.getByText('代码表 missing 不存在，请联系管理员检查绑定', { exact: true }).waitFor()
  await closeDrawer()

  await selectField('未发布选择')
  assert.equal(await panel.locator('.picker-trigger').isDisabled(), false, '实体已发布，但新增未发布字段仍可选表')
  await panel.locator('.picker-trigger').click()
  await dialog.getByRole('button', { name: '确认选择', exact: true }).click()
  await panel.getByRole('button', { name: '新建其他', exact: true }).click()
  const codeInput = dialog.getByLabel('代码表编码', { exact: true })
  assert.notEqual(await codeInput.inputValue(), 'demo_choice')
  await codeInput.fill('demo_choice')
  await dialog.getByRole('textbox', { name: '第 1 行编码', exact: true }).fill('1')
  await dialog.getByRole('textbox', { name: '第 1 行名称', exact: true }).fill('交通费')
  await page.evaluate(() => { dictionaryTest.state.duplicate = true; dictionaryTest.clearMessages() })
  await dialog.getByRole('button', { name: '创建并绑定', exact: true }).click()
  await page.getByText('字典编码已存在：demo_choice', { exact: true }).waitFor()
  assert.equal(await page.locator('.el-message--error').count(), 1, '重复编码失败只提示一次')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()

  await page.evaluate(() => dictionaryTest.readonly())
  await selectField('已发布选择')
  assert.equal(await panel.getByRole('button', { name: '维护', exact: true }).count(), 0)
  await panel.getByRole('button', { name: '查看', exact: true }).click()
  await drawer.getByRole('cell', { name: '差旅费', exact: true }).waitFor()
  assert.equal(await drawer.getByRole('button', { name: '编辑', exact: true }).count(), 0)
  assert.equal(await page.evaluate(() => dictionaryTest.state.calls.some(call => call.url.includes('/entity/demo/') && call.method === 'post')), false,
    '代码项维护不自动保存或修改实体字段绑定')
  assert.deepEqual(errors, [])
  await page.evaluate(() => dictionaryTest.clearMessages())
  await page.screenshot({ path: '/tmp/flow-entity-dictionary-acceptance.png', animations: 'disabled' })
  console.log('实体代码表浏览器验收通过：发布锁定、未发布可调整、查看与维护、停用与缺失绑定、失败保留草稿、独立保存、只读权限和单次错误提示。')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
