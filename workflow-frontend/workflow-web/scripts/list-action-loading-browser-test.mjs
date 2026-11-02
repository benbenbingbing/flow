import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 真实配置面板、表格和实体列表，仅替换 API；延迟由测试主动释放，不访问业务数据库。
const harness = `
import { createApp, h, reactive, ref, defineComponent } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '/src/components/ListButtonConfigPanel.vue'
import Table from '/src/views/entity/components/EntityDataTable.vue'
import List from '/src/views/entity/EntityDataList.vue'
import Drawer from '/src/views/entity/components/EntityRecordVersionDrawer.vue'
import { entityVersionApi } from '/src/api/entityVersion.js'
import { normalizeListActionForSave } from '/src/shared/list-config-design.js'
import { registerListRowAction, registerListToolbarAction } from '/src/extensions/core/registries/listActionRegistry.js'
import { registerListButtonComponent } from '/src/extensions/core/registries/listButtonComponentRegistry.js'
import { entityApi, entityDataApi } from '/src/api/entity.js'
import { entityListRuntimeApi } from '/src/api/entityListRuntime.js'
import { historyImportApi } from '/src/api/historyImport.js'
import request from '/src/utils/request.js'
const buttons = [
 {key:'view',type:'built-in',label:'查看',mappedFieldCode:'name'},
 {key:'edit',type:'built-in',label:'编辑',icon:'Edit'},
 {key:'approve',type:'built-in',label:'审批'},
 {key:'restartProcess',type:'built-in',label:'重新发起'},
 {key:'handler',type:'custom',customMode:'handler',customHandler:'slow',label:'慢函数'},
 {key:'event',type:'custom',customMode:'event',label:'业务接口'},
 {key:'component',type:'custom',customMode:'component',customHandler:'slow-component',label:'组件动作'},
 {key:'form',type:'custom',customMode:'open-form',label:'打开表单'}
].map(button=>({...button,enabled:true}))
const capabilities=Object.fromEntries([...buttons.map(b=>b.key),'delete'].map(key=>[key,{visible:true,enabled:true}]))
const rows=['a','b'].map(id=>({id,name:'记录'+id,actionCapabilities:capabilities}))
const fields=[{fieldCode:'name',fieldName:'名称',showInList:true}]
const state=reactive({buttons,events:[],deletes:0,queries:0,details:0,saved:null,actualRows:[{...rows[0],name:'实际记录'}]})
const gates=new Map()
const wait=key=>new Promise((resolve,reject)=>gates.set(key,{resolve,reject}))
async function execute(type,row,button){state.events.push({type,id:row?.id,key:button?.key});await wait('action')}
registerListRowAction('slow',ctx=>execute('handler',ctx.row,ctx.config))
registerListToolbarAction('slow-toolbar',ctx=>execute('toolbar',undefined,ctx.config))
registerListButtonComponent('slow-component',defineComponent({props:['context','disabled','loading'],setup:p=>()=>h('button',{disabled:p.disabled,onClick:()=>p.context.runAction(()=>execute('component',rows[0],{key:'component'}))},'组件动作')}))
const versionDrawer=ref()
const handlers={view:(r,b)=>execute('view',r,b),edit:(r,b)=>execute('edit',r,b),approve:(r,b)=>execute('approve',r,b),'event-action':p=>execute('event',p.row,p.button),versions:r=>versionDrawer.value.open(r)}
entityVersionApi.recordVersions=async()=>{await wait('versions');return []}
entityApi.getByCode=async()=>({id:'entity-loading',entityCode:'loading',entityName:'加载测试',storageMode:'DYNAMIC',fields:[]})
entityListRuntimeApi.getSchema=async()=>({id:'list-loading',listKey:'loading',fields,toolbarButtons:[],rowActionButtons:[{key:'view',type:'built-in',label:'查看'},{key:'delete',type:'built-in',label:'删除'}]})
entityListRuntimeApi.query=async()=>{state.queries++;if(state.queries>1)await wait('query');return {records:state.actualRows,total:state.actualRows.length}}
entityDataApi.getDetail=async()=>{state.details++;await wait('detail');return state.actualRows[0]}
entityDataApi.delete=async()=>{state.deletes++;await wait('delete')}
historyImportApi.archives=async()=>[]
request.defaults.adapter=async config=>{
 if(config.url.startsWith('/entity-status/list/'))return {data:{code:200,data:[]},status:200,statusText:'OK',headers:{},config}
 throw Error('Unexpected API '+config.url)
}
const app=createApp({setup:()=>()=>h('main',[
 h('section',{id:'config'},[h(Panel,{modelValue:state.buttons,type:'row',mappingFields:fields,entityFields:[],onSave:b=>state.saved=normalizeListActionForSave(b,'ROW')})]),
 h('section',{id:'runtime'},[h(Table,{dataList:rows,loading:false,total:2,pageNum:1,pageSize:10,listFields:fields,
 toolbarButtons:[{key:'toolbar',type:'custom',customMode:'handler',customHandler:'slow-toolbar',label:'工具栏动作',icon:'Edit'}],toolbarCapabilities:{},rowActionButtons:state.buttons,showSelectionColumn:false,useListConfig:true,
 entityCode:'loading',entityDefinition:{},entityStatusMap:{},refEntityNameMap:{},refresh:async()=>{},showPagination:false,showVersionAction:true,actionHandlers:handlers})]),
 h(Drawer,{ref:versionDrawer,entityCode:'loading',runtimeEnabled:true}),
 h('section',{id:'actual'},[h(List,{entityCode:'loading',listKey:'loading',embedded:true,showSearch:false,showPagination:false,
 showToolbar:false,showRowActions:true,allowDefaultFormResolve:false,defaultForm:{fields:[],nodes:[]}})])
])})
app.use(createPinia()).use(createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]})).use(ElementPlus).mount('#app')
window.loadingTest={state,release:(key,fail=false)=>{const gate=gates.get(key);if(!gate)throw Error('Missing gate '+key);gates.delete(key);fail?gate.reject(Error('模拟网络失败')):gate.resolve()}}
`
const fixture = mkdtempSync(path.resolve('.list-action-loading-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios', 'async-validator'], entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3431, strictPort: true, hmr: false, forwardConsole: false } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1400 } })
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:3431/${path.basename(fixture)}/index.html`)
  await page.waitForFunction(() => window.loadingTest)
  const runtime = page.locator('#runtime'), row = runtime.locator('.el-table__body tr').first(), actual = page.locator('#actual')
  await actual.getByRole('button', { name: '删除', exact: true }).waitFor()
  const release = (key, fail = false) => page.evaluate(([key, fail]) => loadingTest.release(key, fail), [key, fail])
  const waitEvents = count => page.waitForFunction(count => loadingTest.state.events.length === count, count)
  const waitIdle = () => page.waitForFunction(() => !document.querySelector('#runtime .is-loading'))
  const geometry = () => runtime.locator('.el-table__body tr:first-child button, .el-table__body tr:first-child .list-cell-action, .el-table__body tr:first-child .custom-button-host, .table-toolbar button').evaluateAll(elements => {
    const origin = document.querySelector('#runtime').getBoundingClientRect()
    return elements.map(element => {
      const rect = element.getBoundingClientRect()
      return [rect.width, rect.height, rect.left - origin.left, rect.top - origin.top]
    })
  })
  async function assertStable(before, message) {
    const after = await geometry()
    assert.equal(after.length, before.length)
    before.forEach((rect, i) => rect.forEach((value, j) => {
      assert.ok(Math.abs(value - after[i][j]) < 0.25, message + ': 按钮尺寸和位置保持一致')
    }))
  }

  // 映射入口和操作列共享状态；其他行不转圈，也不能竞争共用弹窗。
  const beforeView = await geometry()
  await row.getByRole('button', { name: '查看：记录a', exact: true }).click(); await waitEvents(1)
  assert.equal(await row.getByRole('button', { name: '查看', exact: true }).evaluate(e => e.classList.contains('is-loading')), true)
  assert.equal(await row.locator('.list-cell-action').getAttribute('aria-busy'), 'true')
  assert.equal(await row.locator('.list-cell-action__content').evaluate(e => getComputedStyle(e).visibility), 'hidden')
  assert.equal(await row.getByRole('button', { name: '查看', exact: true }).locator(':scope > span').evaluate(e => getComputedStyle(e).visibility), 'hidden')
  await assertStable(beforeView, '查看加载中')
  await page.screenshot({ path: '/private/tmp/flow-list-loading-stable.png', fullPage: true })
  assert.equal(await runtime.locator('.el-table__body tr').nth(1).locator('.is-loading').count(), 0)
  await row.getByRole('button', { name: '查看', exact: true }).evaluate(e => e.click())
  await runtime.locator('.el-table__body tr').nth(1).getByRole('button', { name: '编辑', exact: true }).evaluate(e => e.click())
  assert.equal(await page.evaluate(() => loadingTest.state.events.length), 1)
  await release('action'); await waitIdle()
  await assertStable(beforeView, '查看加载结束')

  // 版本入口在抽屉首屏出现后释放，未完成的数据请求由抽屉内部 loading 接管。
  await row.getByRole('button', { name: '版本', exact: true }).click()
  const versionDrawer = page.locator('.record-version-drawer:visible')
  await versionDrawer.waitFor({ state: 'visible' })
  await waitIdle()
  assert.equal(await versionDrawer.locator('.version-body .el-loading-mask').isVisible(), true)
  await release('versions')
  await versionDrawer.locator('.el-drawer__close-btn').click()
  await versionDrawer.waitFor({ state: 'hidden' })
  let count = 1
  for (const label of ['编辑', '审批', '重新发起', '慢函数', '业务接口', '打开表单']) {
    const button = row.getByRole('button', { name: label, exact: true })
    const before = await geometry()
    await button.click(); await waitEvents(++count)
    assert.equal(await button.evaluate(e => e.classList.contains('is-loading')), true, label)
    assert.equal(await button.locator(':scope > span').evaluate(e => getComputedStyle(e).visibility), 'hidden', label + '加载时隐藏文字和原图标')
    await assertStable(before, label + '加载中')
    await release('action', label === '慢函数'); await waitIdle()
    assert.equal(await button.isEnabled(), true, label + '结束后可重试')
    assert.equal(await button.locator(':scope > span').evaluate(e => getComputedStyle(e).visibility), 'visible')
    await assertStable(before, label + '加载结束')
  }
  const beforeComponent = await geometry()
  await row.getByRole('button', { name: '组件动作', exact: true }).click(); await waitEvents(++count)
  assert.equal(await row.locator('.custom-button-host > .is-loading').count(), 1)
  assert.equal(await row.locator('.custom-button-host > button').evaluate(e => getComputedStyle(e).visibility), 'hidden')
  await assertStable(beforeComponent, '组件按钮加载中')
  await release('action'); await waitIdle()
  await assertStable(beforeComponent, '组件按钮加载结束')
  const toolbar = runtime.getByRole('button', { name: '工具栏动作', exact: true })
  const beforeToolbar = await geometry()
  await toolbar.click(); await waitEvents(++count)
  assert.equal(await toolbar.locator(':scope > span').evaluate(e => getComputedStyle(e).visibility), 'hidden')
  await assertStable(beforeToolbar, '工具栏按钮加载中')
  await release('action'); await waitIdle()
  await assertStable(beforeToolbar, '工具栏按钮加载结束')

  // 默认开关开启，保存 false 后只隐藏展示，继续防重。
  const configRow = page.locator('#config .button-config-table .el-table__body tr').nth(4)
  await configRow.getByRole('button', { name: '更多', exact: true }).click()
  const settings = page.getByRole('dialog', { name: '按钮更多设置', exact: true }), toggle = settings.getByRole('switch').first()
  assert.equal(await toggle.getAttribute('aria-checked'), 'true')
  await settings.locator('.el-switch').first().click(); await settings.getByRole('button', { name: '完成', exact: true }).click()
  await configRow.getByRole('button', { name: '保存', exact: true }).click()
  assert.equal(await page.evaluate(() => loadingTest.state.saved.actionParams.showLoading), false)
  const custom = row.getByRole('button', { name: '慢函数', exact: true })
  await custom.click(); await waitEvents(++count)
  assert.equal(await row.locator('.is-loading').count(), 0)
  assert.equal(await custom.isDisabled(), true)
  await custom.evaluate(e => e.click())
  assert.equal(await page.evaluate(() => loadingTest.state.events.length), count)
  await release('action')
  await page.waitForFunction(() => ![...document.querySelectorAll('#runtime button')].find(e => e.textContent.trim() === '慢函数').disabled)

  // 真实父子分发必须等到详情准备完成，不能在发出事件时提前清除。
  const view = actual.getByRole('button', { name: '查看', exact: true })
  await view.click(); await page.waitForFunction(() => loadingTest.state.details === 1)
  assert.equal(await view.evaluate(e => e.classList.contains('is-loading')), true)
  await release('detail'); await page.locator('.entity-approval-dialog').waitFor({ state: 'visible' })
  await page.waitForFunction(() => ![...document.querySelectorAll('#actual button')].find(e => e.textContent.trim() === '查看').classList.contains('is-loading'))
  await page.locator('.entity-approval-dialog').getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })

  // 删除取消不发请求，确认按钮与入口一起加载，并覆盖列表刷新和错误恢复。
  const remove = actual.getByRole('button', { name: '删除', exact: true })
  await remove.click(); await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(await page.evaluate(() => loadingTest.state.deletes), 0)
  await remove.click()
  const confirm = page.locator('.el-message-box')
  await confirm.getByRole('button', { name: '确认删除', exact: true }).click()
  await page.waitForFunction(() => loadingTest.state.deletes === 1)
  assert.equal(await remove.evaluate(e => e.classList.contains('is-loading')), true)
  assert.equal(await confirm.getByRole('button', { name: '确认删除', exact: true }).evaluate(e => e.classList.contains('is-loading')), true)
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(await confirm.isVisible(), true)
  await release('delete'); await page.waitForFunction(() => loadingTest.state.queries === 2)
  assert.equal(await remove.evaluate(e => e.classList.contains('is-loading')), true, '刷新期间继续加载')
  await release('query'); await confirm.waitFor({ state: 'hidden' })
  await page.waitForFunction(() => ![...document.querySelectorAll('#actual button')].find(e => e.textContent.trim() === '删除').disabled)
  await remove.click(); await page.locator('.el-message-box').getByRole('button', { name: '确认删除', exact: true }).click()
  await page.waitForFunction(() => loadingTest.state.deletes === 2); await release('delete', true)
  await page.locator('.el-message-box').waitFor({ state: 'hidden' })
  await page.waitForFunction(() => ![...document.querySelectorAll('#actual button')].find(e => e.textContent.trim() === '删除').disabled)
  assert.deepEqual(errors, [])
  console.log('列表 loading 浏览器验证通过：原位替换文字、图标及尺寸位置稳定、内置/自定义/组件/工具栏、映射单元格、防重、关闭设置、失败恢复、真实弹窗和删除流程。')
} finally { await browser?.close(); await server?.close(); rmSync(fixture, { recursive: true, force: true }) }
