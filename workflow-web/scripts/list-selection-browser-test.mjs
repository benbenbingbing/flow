import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 真实组件在内存配置上验收，不访问业务数据库。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '/src/components/ListButtonConfigPanel.vue'
import Table from '/src/views/entity/components/EntityDataTable.vue'
import { normalizeListActionForSave } from '/src/shared/list-config-design.js'
import { isSelectionToolbarButton } from '/src/shared/list-selection.js'
import { registerListToolbarAction } from '/src/utils/listActionRegistry.js'
import { registerListButtonComponent } from '/src/utils/listButtonComponentRegistry.js'
import request from '/src/utils/request.js'
request.defaults.adapter = async config => ({data:{code:200,data:[]},status:200,statusText:'OK',headers:{},config})
const keys=['free','one','many','component']
const state=reactive({
 buttons:keys.map((key,i)=>({id:key,key,label:['自由操作','单条操作','批量操作','组件操作'][i],type:'custom',
  customMode:['handler','handler','event','component'][i],customHandler:'inspect',enabled:true,
  selectionRequirement:['NONE','SINGLE','AT_LEAST_ONE','SINGLE'][i]})),
 rows:['a','b'].map(id=>({id,name:'记录 '+id,actionCapabilities:Object.fromEntries(keys.map(k=>[k,{visible:true,enabled:true}]))})),
 selected:[], events:[], caps:{}, saved:null
})
registerListToolbarAction('inspect',ctx=>state.events.push({key:ctx.config.key,count:ctx.selectedRows.length}))
// 组件故意忽略 disabled，验证平台捕获阶段依然阻断不满足要求的点击。
registerListButtonComponent('inspect',{setup:()=>()=>h('button',{id:'component-action',onClick:()=>state.events.push({key:'component',count:state.selected.length})},'组件操作')})
function save(button){
 const payload=JSON.parse(JSON.stringify(normalizeListActionForSave(button,'TOOLBAR')));state.saved=payload
 state.buttons=state.buttons.map(b=>b.id===button.id?{id:b.id,key:payload.buttonKey,label:payload.buttonLabel,type:payload.buttonType,
  customMode:payload.customMode,customHandler:payload.handlerCode,enabled:payload.enabled,...payload.actionParams}:b)
}
const app=createApp({setup:()=>()=>h('main',{},[
 h('h2',{},'自定义工具栏按钮'),
 h(Panel,{type:'toolbar',modelValue:state.buttons,onSave:save,'onUpdate:modelValue':v=>state.buttons=v}),
 h('h2',{},'列表运行效果'),
 h('section',{id:'runtime'},[h(Table,{
  dataList:state.rows,loading:false,total:2,pageNum:1,pageSize:10,listFields:[{fieldCode:'name',fieldName:'名称'}],
  toolbarButtons:state.buttons,toolbarCapabilities:state.caps,rowActionButtons:[],
  showSelectionColumn:state.buttons.some(isSelectionToolbarButton),selectionMode:'MULTIPLE',
  selectedRows:state.selected,'onUpdate:selectedRows':v=>state.selected=v,
  useListConfig:true,entityCode:'project',entityDefinition:{},entityStatusMap:{},refEntityNameMap:{},refresh:()=>{},showPagination:false,
  onEventAction:p=>state.events.push({key:p.button.key,count:p.selectedRows.length})
 })])
])})
app.use(createPinia()).use(ElementPlus).mount('#app')
window.selectionTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,150));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.list-selection-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'list-selection-chrome-'))
let browser, ws, server, nextId = 0
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
const visible = 'e=>e.getClientRects().length>0'
const dialog = `([...document.querySelectorAll('.el-dialog')].filter(${visible})).at(-1)`
async function step(code) { await evaluate(code); await evaluate('selectionTest.settle()') }
async function button(text, root = 'document') {
  await step(`([...${root}.querySelectorAll('button')].filter(${visible})).find(e=>e.textContent.trim()===${JSON.stringify(text)}).click()`)
}
async function openMore(index = 0) {
  await button('更多', `document.querySelectorAll('.button-config-table .el-table__body tr')[${index}]`)
}
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3403, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9403', `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9403/json/list')).json(); break } catch { await sleep(100) } }
  assert.ok(targets?.length, 'Chrome failed to start')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) { const p = pending.get(data.id); pending.delete(data.id); data.error ? p.reject(Error(JSON.stringify(data.error))) : p.resolve(data.result) }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `http://127.0.0.1:3403/${path.basename(fixture)}/index.html` })
  for (let i = 0; i < 200; i++) { if (await evaluate('Boolean(window.selectionTest)')) break; await sleep(100) }
  assert.equal(await evaluate('Boolean(window.selectionTest)'), true, errors.join('\n'))
  await evaluate('selectionTest.settle()')

  assert.equal(await evaluate("[...document.querySelectorAll('.button-config-table th')].some(e=>e.textContent.includes('选择要求'))"),false,'表格不增加选择要求列')
  await openMore()
  const chooseRequirement=async label=>step(`([...${dialog}.querySelectorAll('.el-radio-button')]).find(e=>e.textContent.trim()===${JSON.stringify(label)}).click()`)
  await chooseRequirement('恰好一条'); await button('完成'); await button('保存', "document.querySelector('.button-config-table .el-table__body tr')")
  assert.equal(await evaluate('selectionTest.state.saved.actionParams.selectionRequirement'),'SINGLE')
  await openMore()
  assert.equal(await evaluate(`${dialog}.querySelector('.el-radio-button.is-active').textContent.trim()`),'恰好一条','保存重载显示原要求')
  await chooseRequirement('无需选择'); await button('完成'); await button('保存', "document.querySelector('.button-config-table .el-table__body tr')")
  const toolbarButton=label=>`([...document.querySelectorAll('#runtime .table-toolbar button')]).find(e=>e.textContent.trim()===${JSON.stringify(label)})`
  const disabled=label=>evaluate(`${toolbarButton(label)}.disabled`)
  const toggle=async index=>step(`document.querySelectorAll('#runtime .el-table__body tr')[${index}].querySelector('.el-checkbox__input').click()`)
  assert.equal(await disabled('自由操作'),false)
  assert.equal(await disabled('单条操作'),true)
  assert.equal(await disabled('批量操作'),true)
  await step("document.querySelector('#component-action').click()")
  assert.equal(await evaluate('selectionTest.state.events.length'),0,'空选择阻断自定义组件')
  await toggle(0)
  assert.equal(await evaluate('selectionTest.state.selected.length'),1)
  assert.equal(await disabled('单条操作'),false)
  assert.equal(await disabled('批量操作'),false)
  await button('单条操作', "document.querySelector('#runtime')")
  await button('批量操作', "document.querySelector('#runtime')")
  await step("document.querySelector('#component-action').click()")
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(selectionTest.state.events))'),[{key:'one',count:1},{key:'many',count:1},{key:'component',count:1}])
  await toggle(1)
  assert.equal(await evaluate('selectionTest.state.selected.length'),2)
  assert.equal(await disabled('自由操作'),false)
  assert.equal(await disabled('单条操作'),true)
  assert.equal(await disabled('批量操作'),false)
  assert.match(await evaluate(`${toolbarButton('单条操作')}.title`),/恰好一条/)
  await step("document.querySelector('#component-action').click()")
  assert.equal(await evaluate('selectionTest.state.events.length'),3,'多选阻断要求一条的自定义组件')
  await button('自由操作', "document.querySelector('#runtime')")
  await button('批量操作', "document.querySelector('#runtime')")
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(selectionTest.state.events.slice(-2)))'),[{key:'free',count:2},{key:'many',count:2}])
  await step("selectionTest.state.rows[1].actionCapabilities.many={visible:true,enabled:false,reason:'当前记录不能操作'}")
  assert.equal(await disabled('批量操作'),true,'任意记录不满足适用条件时禁用')
  await step("selectionTest.state.rows[1].actionCapabilities.many={visible:false,enabled:false}")
  assert.equal(await evaluate(`${toolbarButton('批量操作')}===undefined`),true,'隐藏条件按全部选中行判断')
  await toggle(1)
  await step("selectionTest.state.caps.one={visible:true,enabled:false,reason:'列表级限制'}")
  assert.equal(await disabled('单条操作'),true,'行能力允许时仍遵守列表级禁用')
  await openMore(1)
  const screenshot=await send('Page.captureScreenshot',{format:'png'})
  writeFileSync(path.join(tmpdir(),'flow-list-selection-settings.png'),Buffer.from(screenshot.data,'base64'))
  assert.deepEqual(errors,[])
  console.log('list selection browser acceptance passed: More settings, save/reload, zero/one/many, handler/event/component guards, row conditions')
} finally {
  ws?.close(); browser?.kill(); await server?.close(); await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
