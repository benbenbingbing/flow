import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 使用真实按钮面板、表格与单元格组件；保存接口以内存回放验证，不写业务数据库。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '/src/components/ListButtonConfigPanel.vue'
import Table from '/src/views/entity/components/EntityDataTable.vue'
import { normalizeListActionForSave } from '/src/shared/list-config-design.js'
import { registerListRowAction } from '/src/utils/listActionRegistry.js'
const state = reactive({
 buttons:[{id:'view',key:'view',label:'查看',type:'built-in',enabled:true},{id:'edit',key:'edit',label:'编辑',type:'built-in',enabled:true}],
 fields:[
  {fieldCode:'name',fieldName:'名称',showInList:true,columnConfig:'{"quickCopy":true}'},
  {fieldCode:'secret',fieldName:'隐藏字段',showInList:false},
  {fieldCode:'virtual_total',fieldName:'虚拟合计',showInList:false,dataSourceType:'FIELD_TEMPLATE'},
  {fieldCode:'status',fieldName:'状态',showInList:true,isSystem:true}
 ],
 rows:[
  {id:'a',name:'项目 A',status:'DRAFT',actionCapabilities:{view:{visible:true,enabled:true}}},
  {id:'b',name:'项目 B',status:'DRAFT',actionCapabilities:{view:{visible:true,enabled:false,reason:'当前状态不允许查看'}}},
  {id:'c',name:'项目 C',status:'DRAFT',actionCapabilities:{view:{visible:false,enabled:false}}}
 ],
 events:[],saved:null,allow:true,clipboard:''
})
Object.defineProperty(navigator,'clipboard',{value:{writeText:async text=>{state.clipboard=text}},configurable:true})
const record=(type,...args)=>state.events.push({type,row:args[0]?.id,button:args[1]?.key})
registerListRowAction('inspect',ctx=>record('handler',ctx.row,ctx.config))
function save(button){
 const payload=JSON.parse(JSON.stringify(normalizeListActionForSave(button,'ROW')));state.saved=payload
 state.buttons=state.buttons.map(b=>b.id===button.id?{id:b.id,key:payload.buttonKey,label:payload.buttonLabel,type:payload.buttonType,
  customMode:payload.customMode,customHandler:payload.handlerCode,enabled:payload.enabled,...payload.actionParams}:b)
}
const app=createApp({setup(){return()=>h('main',{},[
 h('h2',{},'按钮更多设置与功能映射'),
 h(Panel,{type:'row',modelValue:state.buttons,mappingFields:state.fields,entityFields:state.fields,onSave:save,'onUpdate:modelValue':v=>state.buttons=v}),
 h('h2',{},'列表运行效果'),
 h('section',{id:'runtime'},[h(Table,{
  dataList:state.rows,loading:false,total:3,pageNum:1,pageSize:10,listFields:state.fields.filter(f=>f.showInList),
  toolbarButtons:[],toolbarCapabilities:{},rowActionButtons:state.allow?state.buttons.filter(b=>b.enabled!==false):[],
  showSelectionColumn:false,useListConfig:true,entityCode:'project',entityDefinition:{},entityStatusMap:{DRAFT:'草稿'},
  refEntityNameMap:{},refresh:()=>{},showPagination:false,
  onView:(...args)=>record('view',...args),onEdit:(...args)=>record('edit',...args),
  onDelete:(...args)=>record('delete',...args),onApprove:(...args)=>record('approve',...args),
  onEventAction:p=>record('event',p.row,p.button)
 })])
])}})
app.use(createPinia()).use(ElementPlus).mount('#app')
window.cellTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,200));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.list-cell-action-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'list-cell-action-chrome-'))
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
async function step(code) { await evaluate(code); await evaluate('cellTest.settle()') }
async function button(text, root = 'document') {
  await step(`([...${root}.querySelectorAll('button')].filter(${visible})).find(e=>e.textContent.trim()===${JSON.stringify(text)}).click()`)
}
async function openMore(index = 0) {
  await button('更多', `document.querySelectorAll('.button-config-table .el-table__body tr')[${index}]`)
}
async function openMapping() {
  await step(`([...${dialog}.querySelectorAll('.el-form-item')]).find(e=>e.textContent.includes('功能映射')).querySelector('.el-select__wrapper').click()`)
}
async function chooseField(code) {
  await openMapping()
  await step(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).find(e=>e.textContent.includes(${JSON.stringify('(' + code + ')')})).click()`)
}
const runtimeRows = `document.querySelectorAll('#runtime .el-table__body tr')`
const firstCell = `document.querySelector('#runtime .list-cell-action')`
const eventCount = () => evaluate('cellTest.state.events.length')
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3399, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9399', `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9399/json/list')).json(); break } catch { await sleep(100) } }
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
  await send('Page.navigate', { url: `http://127.0.0.1:3399/${path.basename(fixture)}/index.html` })
  for (let i = 0; i < 200; i++) { if (await evaluate('Boolean(window.cellTest)')) break; await sleep(100) }
  assert.equal(await evaluate('Boolean(window.cellTest)'), true, errors.join('\n'))
  await evaluate('cellTest.settle()')
  await openMore(); await openMapping()
  const options = await evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).map(e=>e.textContent.trim())`)
  for (const code of ['name', 'secret', 'virtual_total', 'status']) assert.ok(options.some(option => option.includes('(' + code + ')')), '全部字段可选择: ' + code)
  await step(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).find(e=>e.textContent.includes('(name)')).click()`)
  await step(`${dialog}.querySelector('.el-switch').click()`)
  assert.equal(await evaluate('cellTest.state.buttons[0].mappedFieldCode'), 'name', await evaluate('JSON.stringify(cellTest.state.buttons)'))
  await button('完成'); await button('保存', "document.querySelector('.button-config-table .el-table__body tr')")
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(cellTest.state.saved.actionParams))'), { mappedFieldCode: 'name', hideWhenMapped: true })
  assert.equal(await evaluate("document.querySelectorAll('#runtime .list-cell-action').length"), 2)
  assert.equal(await evaluate(`getComputedStyle(${firstCell}).color`), 'rgb(64, 158, 255)', '可用单元格文字为蓝色')
  assert.equal(await evaluate(`getComputedStyle(${firstCell}.querySelector('span')).color`), 'rgb(64, 158, 255)')
  assert.equal(await evaluate("[...document.querySelectorAll('#runtime th')].some(e=>e.textContent.trim()==='操作')"), false, '全部按钮移入单元格后隐藏空操作列')
  await step(`${firstCell}.click()`)
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(cellTest.state.events.at(-1)))'), { type: 'view', row: 'a', button: 'view' })
  await step(`${firstCell}.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}))`)
  await step(`${firstCell}.dispatchEvent(new KeyboardEvent('keydown',{key:' ',bubbles:true,cancelable:true}))`)
  assert.equal(await eventCount(), 3, '鼠标、Enter 与空格每次仅执行一次')
  assert.equal(await evaluate(`${runtimeRows}[1].querySelector('.list-cell-action').title`), '当前状态不允许查看')
  await step(`${runtimeRows}[1].querySelector('.list-cell-action').click()`)
  assert.equal(await eventCount(), 3, '禁用动作不可点击')
  assert.equal(await evaluate(`${runtimeRows}[2].querySelector('.list-cell-action')===null`), true, '不可见动作回退普通文本')
  await step("document.querySelector('#runtime .list-quick-copy-cell__button').click()")
  assert.equal(await eventCount(), 3, '快捷复制不触发映射动作')
  assert.equal(await evaluate('cellTest.state.clipboard'), '项目 A')

  // 查看按钮已经占用名称，编辑按钮的同一字段选项不可重复选择。
  await openMore(1); await openMapping()
  assert.equal(await evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).find(e=>e.textContent.includes('(name)')).classList.contains('is-disabled')`), true)
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape' })
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape' })
  await evaluate('cellTest.settle()'); await button('完成')

  await step('cellTest.state.buttons[0].hideWhenMapped=false')
  assert.equal(await evaluate(`${runtimeRows}[0].textContent.includes('查看')`), true, '允许同时保留按钮')
  await button('查看', `${runtimeRows}[0]`)
  assert.equal(await eventCount(), 4)
  await step('cellTest.state.buttons[0].hideWhenMapped=true;cellTest.state.allow=false')
  assert.equal(await evaluate("document.querySelectorAll('#runtime .list-cell-action').length"), 0, '无按钮权限不显示映射入口')
  await step('cellTest.state.allow=true;cellTest.state.buttons[0].enabled=false')
  assert.equal(await evaluate("document.querySelectorAll('#runtime .list-cell-action').length"), 0, '停用按钮同时关闭单元格入口')
  await step('cellTest.state.buttons[0].enabled=true')

  await openMore(); await chooseField('secret'); await button('完成'); await button('保存', "document.querySelector('.button-config-table .el-table__body tr')")
  assert.equal(await evaluate('cellTest.state.saved.actionParams.mappedFieldCode'), 'secret')
  assert.equal(await evaluate(`${runtimeRows}[0].textContent.includes('查看')`), true, '隐藏字段映射保存成功，原按钮保留')
  await step("cellTest.state.buttons[0].mappedFieldCode=''")
  await button('保存', "document.querySelector('.button-config-table .el-table__body tr')")
  assert.equal(await evaluate('cellTest.state.saved.actionParams.mappedFieldCode===undefined'), true, '清空映射后保存不残留旧字段')

  // 自定义动作与内置动作都复用原分发器；自定义单元格/状态标签的字体也应变蓝。
  await step("cellTest.state.buttons=[{id:'inspect',key:'inspect',label:'核验',type:'custom',customMode:'handler',customHandler:'inspect',mappedFieldCode:'status',hideWhenMapped:true}];cellTest.state.rows[0].actionCapabilities.inspect={visible:true,enabled:true}")
  assert.equal(await evaluate(`getComputedStyle(${firstCell}.querySelector('.el-tag')).color`), 'rgb(64, 158, 255)')
  await step(`${firstCell}.click()`)
  assert.equal(await evaluate('cellTest.state.events.at(-1).type'), 'handler')
  for (const mode of ['event', 'open-form']) {
    await step(`cellTest.state.buttons[0].customMode=${JSON.stringify(mode)};cellTest.state.buttons[0].targetFormMode='EDIT'`)
    await step(`${firstCell}.click()`)
    assert.equal(await evaluate('cellTest.state.events.at(-1).type'), mode === 'event' ? 'event' : 'edit')
  }
  await step("cellTest.state.buttons[0].customMode='component'")
  assert.equal(await evaluate("document.querySelectorAll('#runtime .list-cell-action').length"), 0)
  await openMore()
  assert.equal(await evaluate(`([...${dialog}.querySelectorAll('.el-form-item')]).find(e=>e.textContent.includes('功能映射')).querySelector('.el-select__wrapper').classList.contains('is-disabled')`), true)
  await button('完成')
  await step("cellTest.state.buttons=[{id:'view',key:'view',label:'查看',type:'built-in',enabled:true,mappedFieldCode:'name',hideWhenMapped:true}]")
  await openMore()
  assert.equal(await evaluate(`([...${dialog}.querySelectorAll('.field-help')]).every(e=>e.getBoundingClientRect().right<=${dialog}.getBoundingClientRect().right)`), true, '更多设置说明应在弹窗内换行')
  let screenshot = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(path.join(tmpdir(), 'flow-cell-action-settings.png'), Buffer.from(screenshot.data, 'base64'))
  await button('完成')
  screenshot = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(path.join(tmpdir(), 'flow-cell-action-runtime.png'), Buffer.from(screenshot.data, 'base64'))
  assert.deepEqual(errors, [])
  console.log('list cell action browser acceptance passed: all fields, save/reload/clear, blue text, keyboard, copy isolation, permissions, hidden buttons, conflicts, builtin/custom dispatch')
} finally {
  ws?.close(); browser?.kill(); await server?.close(); await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
