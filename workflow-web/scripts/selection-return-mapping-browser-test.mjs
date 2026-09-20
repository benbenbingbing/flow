import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实列表设计器，接口以内存草稿回放；验证保存边界且不写业务数据库。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Designer from '/src/views/EntityListConfigDesign.vue'
import { entityListConfigApi } from '/src/api/entityListConfig.js'
import { entityApi } from '/src/api/entity.js'
import { entityListScopeRuleApi } from '/src/api/entityListScopeRule.js'
import request from '/src/utils/request.js'
const clone = value => JSON.parse(JSON.stringify(value))
const legacy = [
 { sourceField:'id', targetField:'customerId' },
 { sourcePath:'data.customer_name', targetPath:'selectionData.customerName', extra:{keep:true} }
]
const state = reactive({mountId:0,saves:[],config:{
 id:'test-list',entityId:'customer',entityCode:'customer',listKey:'customer_picker',listName:'客户选择列表',revision:1,
 selectionConfig:{selectionMode:'MULTIPLE',valueField:'id',returnMappings:legacy},fields:[]
}})
request.defaults.adapter = async config => ({data:{code:200,data:[]},status:200,statusText:'OK',headers:{},config})
entityListConfigApi.getById = async () => clone(state.config)
entityListConfigApi.getExtensionOptions = async () => []
entityListConfigApi.getDiff = async () => ({changed:true,changedSections:['METADATA']})
entityListConfigApi.patchMetadata = async (id,payload) => {
 state.saves.push(clone(payload)); Object.assign(state.config,clone(payload),{revision:state.config.revision+1}); return clone(state.config)
}
entityApi.getById = async () => ({id:'customer',entityCode:'customer',entityName:'客户',storageMode:'DYNAMIC',lifecycleMode:'NONE',fields:[
 {id:'id',fieldCode:'id',fieldName:'ID',fieldType:'STRING',isSystem:true},
 {id:'name',fieldCode:'customer_name',fieldName:'客户名称',fieldType:'STRING'},
 {id:'phone',fieldCode:'phone',fieldName:'联系电话',fieldType:'STRING'}
]})
entityListScopeRuleApi.getConfiguration = async () => ({policies:[],bindings:[],listDefaults:{}})
entityListScopeRuleApi.replaceListBindings = async () => ({})
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:id',component:Designer}]})
await router.push('/test-list'); await router.isReady()
const app=createApp({setup:()=>()=>h(RouterView,{key:state.mountId})})
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.mappingTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,150));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.selection-return-fixture-'))
const profile = mkdtempSync(path.join(tmpdir(), 'selection-return-chrome-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body style="margin:24px;font-family:Arial,sans-serif"><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser, ws, nextId = 0
const pending = new Map(), errors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function send(method, params = {}) {
  const id = ++nextId
  const result = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject: error => { clearTimeout(timer); reject(error) } })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return result
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}
async function step(expression) { await evaluate(expression); await evaluate('mappingTest.settle()') }
const visible = 'e=>e.getClientRects().length>0'
async function button(text) {
  await step(`([...document.querySelectorAll('button')].filter(${visible})).find(e=>e.textContent.trim()===${JSON.stringify(text)}).click()`)
}
async function settings() {
  await step(`([...document.querySelectorAll('[role="tab"]')]).find(e=>e.textContent.trim()==='列表设置').click()`)
}
async function fillName(index, value) {
  await step(`(()=>{const input=document.querySelector('[aria-label="第 ${index} 条映射的返回名称"]');input.value=${JSON.stringify(value)};input.dispatchEvent(new Event('input',{bubbles:true}))})()`)
}
async function chooseSource(index, path) {
  await step(`document.querySelectorAll('.mapping-row .el-select__wrapper')[${index - 1}].click()`)
  await step(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).find(e=>e.textContent.includes(${JSON.stringify('（' + path + '）')})).click()`)
}
const saveCount = () => evaluate('mappingTest.state.saves.length')
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3401, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9401', `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9401/json/list')).json(); break } catch { await sleep(100) } }
  assert.ok(targets?.length, 'Chrome failed to start')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) { const p = pending.get(data.id); pending.delete(data.id); data.error ? p.reject(Error(JSON.stringify(data.error))) : p.resolve(data.result) }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
    if (data.method === 'Runtime.consoleAPICalled' && data.params.type === 'error') errors.push(data.params.args.map(arg => arg.value || arg.description).join(' '))
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1200, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `http://127.0.0.1:3401/${path.basename(fixture)}/index.html` })
  for (let i = 0; i < 200; i++) { if (await evaluate('Boolean(window.mappingTest)')) break; await sleep(100) }
  assert.equal(await evaluate('Boolean(window.mappingTest)'), true, errors.join('\n'))
  await evaluate('mappingTest.settle()'); await settings()
  assert.deepEqual(await evaluate("[...document.querySelectorAll('.selection-behavior-section .el-radio-button')].map(e=>e.textContent.trim())"), ['不可选择', '可选择'], '普通列表只保留两种选择行为')
  assert.equal(await evaluate("document.querySelectorAll('.mapping-row').length"), 2)
  assert.equal(await evaluate("document.querySelector('[aria-label=\"第 2 条映射的返回名称\"]').value"), 'customerName', '旧 targetPath 自动回显')
  assert.equal(await evaluate("document.querySelector('.unsaved-status-tag')===null"), true, '回显不产生未保存修改')
  await button('添加映射'); await button('保存列表设置')
  assert.equal(await saveCount(), 0, '未填完的行不能保存')
  assert.equal(await evaluate("!!document.querySelector('.unsaved-status-tag')"), true, '未填完的行触发离页保护')
  await chooseSource(3, 'data.phone'); await fillName(3, 'customerName'); await button('保存列表设置')
  assert.equal(await saveCount(), 0, '重名不能发送保存请求')
  assert.match(await evaluate("document.querySelector('.mapping-error').textContent"), /重复/)
  await fillName(3, 'customerPhone'); await button('保存列表设置')
  assert.equal(await saveCount(), 1)
  assert.equal(await evaluate('mappingTest.state.saves[0].selectionConfig.selectionMode'), 'MULTIPLE')
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(mappingTest.state.saves[0].selectionConfig.returnMappings))'), [
    { sourceField: 'id', targetField: 'customerId' },
    { sourcePath: 'data.customer_name', targetPath: 'selectionData.customerName', extra: { keep: true } },
    { sourceField: 'data.phone', targetField: 'customerPhone' }
  ])
  assert.equal(await evaluate("document.querySelector('.unsaved-status-tag')===null"), true)
  await step('mappingTest.state.mountId++'); await settings()
  assert.equal(await evaluate("document.querySelectorAll('.mapping-row').length"), 3, '重进设计器回显已保存配置')
  await step("document.querySelector('.mapping-preview summary').click();document.querySelector('.selection-return-editor').scrollIntoView({block:'center'})")
  assert.equal(await evaluate("getComputedStyle(document.querySelector('.mapping-columns')).display"), 'grid', '桌面使用并排映射表')
  const clip = await evaluate("(()=>{const r=document.querySelector('.selection-behavior-section').getBoundingClientRect();return {x:r.x+scrollX,y:r.y+scrollY,width:r.width,height:r.height,scale:1}})()")
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true, clip })
  writeFileSync(path.join(tmpdir(), 'flow-selection-return-mapping.png'), Buffer.from(screenshot.data, 'base64'))
  await send('Emulation.setDeviceMetricsOverride', { width: 720, height: 1100, deviceScaleFactor: 1, mobile: false })
  await evaluate('mappingTest.settle()')
  assert.equal(await evaluate("(()=>{const e=document.querySelector('.selection-return-editor');return e.scrollWidth<=e.clientWidth+1})()"), true, '窄容器没有横向溢出')
  await step("document.querySelector('[aria-label=\"删除第 2 条映射\"]').click()")
  await button('保存列表设置')
  assert.equal(await evaluate('mappingTest.state.saves.at(-1).selectionConfig.returnMappings.length'), 2)
  await step("document.querySelector('[aria-label=\"删除第 1 条映射\"]').click()")
  await step("document.querySelector('[aria-label=\"删除第 1 条映射\"]').click()")
  await button('保存列表设置')
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(mappingTest.state.saves.at(-1).selectionConfig.returnMappings))'), [], '删空后保存空数组')
  // 只有需要数据的按钮自动开启勾选；移除该要求后用户仍能关闭列表勾选。
  await step("mappingTest.state.config.selectionConfig.selectionMode='NONE';mappingTest.state.config.toolbarConfig=[{key:'archive',type:'custom',customMode:'event',label:'归档',enabled:true,selectionRequirement:'SINGLE'}];mappingTest.state.mountId++")
  await settings()
  assert.equal(await evaluate("document.querySelector('.selection-behavior-section .el-radio-button.is-active').textContent.trim()"), '可选择')
  assert.equal(await evaluate("document.querySelector('.selection-behavior-section .el-radio-button input').disabled"), true)
  await button('保存列表设置')
  assert.equal(await evaluate('mappingTest.state.saves.at(-1).selectionConfig.selectionMode'), 'MULTIPLE')
  await step("mappingTest.state.config.selectionConfig.selectionMode='NONE';mappingTest.state.config.toolbarConfig[0].selectionRequirement='NONE';mappingTest.state.mountId++")
  await settings()
  assert.equal(await evaluate("document.querySelector('.selection-behavior-section .el-radio-button.is-active').textContent.trim()"), '不可选择')
  assert.equal(await evaluate("document.querySelector('.selection-behavior-section .el-radio-button input').disabled"), false)
  assert.deepEqual(errors, [])
  console.log('selection return mapping browser acceptance passed: real designer, legacy reload, add/delete/clear, dirty guard, required/duplicate validation, save payload, responsive layout')
} finally {
  ws?.close(); browser?.kill(); await server?.close(); await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
