import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实管理页面并替换 API，验证跨页选择与确认交接，不写入真实业务数据。
const harness = `
import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import Page from '/src/views/system/TaskHandoverManagement.vue'
import { taskHandoverApi } from '/src/api/taskHandover.js'
import { useUserStore } from '/src/stores/user.js'
const users=[
  {id:'source',username:'former',nickname:'已离职人员',status:'1',deleted:1},
  {id:'disabled',username:'disabled',nickname:'禁用人员',status:'1',deleted:0},
  {id:'target',username:'active',nickname:'正常接收人',status:'0',deleted:0}
]
const state={calls:[],userQueries:[],taskQueries:[],tasks:Array.from({length:24},(_,i)=>({taskId:'task-'+i,taskName:'部门审批 '+(i+1),processName:'项目立项流程',businessName:'项目申请 '+(i+1),businessCode:'XM-'+(i+1),assigneeName:'已离职人员',assignmentType:i%3===0?'CANDIDATE':i%3===1?'ADD_SIGN':'ASSIGNED',status:i%3===0?'todo':i%3===1?'waiting':'hold',createTime:'2026-09-27T10:30:00'}))}
// 故意保留异常账号，验证接收弹窗也遵守目标状态限制，不能仅依赖服务端筛选。
taskHandoverApi.users=async(params={})=>{state.userQueries.push({...params});const keyword=(params.keyword||'').toLowerCase();return users.filter(user=>(user.nickname+' '+user.username).toLowerCase().includes(keyword))}
taskHandoverApi.tasks=async(params)=>{state.taskQueries.push({...params});const {pageNum,pageSize}=params;return {records:state.tasks.slice((pageNum-1)*pageSize,pageNum*pageSize),total:state.tasks.length}}
taskHandoverApi.transfer=async payload=>{state.calls.push(payload);const before=state.tasks.length;state.tasks=payload.all?[]:state.tasks.filter(t=>!payload.taskIds.includes(t.taskId));return {transferredCount:before-state.tasks.length}}
const pinia=createPinia()
const app=createApp(Page).use(pinia).use(ElementPlus,{locale:zhCn})
const store=useUserStore(pinia)
store.setPermissions(['system:task-handover:view','system:task-handover:transfer'])
app.mount('#app')
window.handoverTest={state,store,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,80));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.handover-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html lang="zh-CN"><head><meta charset="utf-8"></head><body style="margin:24px;background:#f5f7fa"><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'flow-handover-chrome-'))
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
let browser, server, ws, nextId = 0
const pending = new Map(), errors = []
const selectorDialog = "[...document.querySelectorAll('.entity-selector-dialog')].find(e=>e.getClientRects().length)"
const handoverDialog = "[...document.querySelectorAll('.el-dialog')].find(e=>e.getClientRects().length&&e.querySelector('.handover-form'))"

async function send(method, params = {}) {
  const id = ++nextId
  const result = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return result
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}
async function until(expression) {
  for (let i = 0; i < 100; i++) {
    try { if (await evaluate(expression)) return }
    catch (error) { if (!/navigated|context was destroyed|Cannot find context/.test(error.message)) throw error }
    await sleep(100)
  }
  throw Error('等待失败: ' + expression + '\n' + await evaluate('document.body.innerText'))
}
async function click(text, scope = 'document') {
  await evaluate(`(()=>{const node=[...${scope}.querySelectorAll('button')].find(e=>e.getClientRects().length&&e.textContent.trim()===${JSON.stringify(text)});if(!node)throw Error('找不到按钮 '+${JSON.stringify(text)});node.click()})()`)
  await evaluate('handoverTest.settle()')
}
async function openPicker(select) {
  await evaluate(`document.querySelector(${JSON.stringify(select)}).click()`)
  await until(`Boolean(${selectorDialog})`)
  await evaluate('handoverTest.settle()')
}
async function pickerRows() {
  return evaluate(`[...(${selectorDialog}).querySelectorAll('.el-table__body .el-table__row')].map(e=>e.innerText)`)
}
async function searchPicker(keyword) {
  const previous = await evaluate('handoverTest.state.userQueries.length')
  await evaluate(`(()=>{const input=(${selectorDialog}).querySelector('.search-bar input');input.value=${JSON.stringify(keyword)};input.dispatchEvent(new Event('input',{bubbles:true}))})()`)
  await evaluate('handoverTest.settle()')
  await evaluate(`(${selectorDialog}).querySelector('button[aria-label="搜索可选记录"]').click()`)
  await until(`handoverTest.state.userQueries.length>${previous}`)
  await evaluate('handoverTest.settle()')
}
async function selectPerson(label) {
  await evaluate(`(()=>{const row=[...(${selectorDialog}).querySelectorAll('.el-table__body .el-table__row')].find(e=>e.innerText.includes(${JSON.stringify(label)}));if(!row)throw Error('找不到人员 '+${JSON.stringify(label)});const button=[...row.querySelectorAll('button')].find(e=>e.textContent.trim()==='选择');if(!button)throw Error('找不到选择按钮');button.click()})()`)
  await until(`!(${selectorDialog})`)
  await evaluate('handoverTest.settle()')
}
async function cancelPicker() {
  await click('取消', `(${selectorDialog})`)
  await until(`!(${selectorDialog})`)
}
async function choose(select, label) {
  await openPicker(select)
  await selectPerson(label)
}
async function screenshot(file) {
  // 等待 Element Plus 的表格标签/弹窗过渡完成，截图反映静止状态而非动画中间帧。
  await sleep(400)
  const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync(file, Buffer.from(result.data, 'base64'))
}
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios'], entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3419, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9419', '--user-data-dir=' + profile, 'about:blank'], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9419/json/list')).json(); break } catch { await sleep(100) } }
  assert.ok(targets?.length, 'Chrome 未启动')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) { const request = pending.get(data.id); pending.delete(data.id); data.error ? request.reject(Error(JSON.stringify(data.error))) : request.resolve(data.result) }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: 'http://127.0.0.1:3419/' + path.basename(fixture) + '/index.html' })
  await until('Boolean(window.handoverTest)')
  await evaluate('handoverTest.settle()')
  assert.equal(await evaluate("document.body.innerText.includes('请选择待交接人员')"), true)
  await openPicker('.source-picker .selector-input')
  const sourceRows = await pickerRows()
  assert.equal(sourceRows.some(row=>row.includes('已离职人员')), true, '来源弹窗应保留已删除用户')
  assert.equal(sourceRows.some(row=>row.includes('禁用人员')), true, '来源弹窗应保留禁用用户')
  assert.equal(sourceRows.some(row=>row.includes('正常接收人')), true, '来源弹窗也应允许正常用户')
  await cancelPicker()
  assert.equal(await evaluate('handoverTest.state.taskQueries.length'), 0, '取消来源选择不得加载任何人的待办')

  await openPicker('.source-picker .selector-input')
  await searchPicker('former')
  const matchedSourceRows = await pickerRows()
  assert.equal(matchedSourceRows.length, 1)
  assert.equal(matchedSourceRows[0].includes('已离职人员'), true)
  assert.equal(await evaluate("handoverTest.state.userQueries.some(q=>q.keyword==='former'&&q.targetOnly===false)"), true)
  await screenshot('/tmp/flow-task-handover-source-picker.png')
  await selectPerson('已离职人员')
  await until("document.querySelectorAll('.task-handover-management .el-card .el-table__body .el-table__row').length===20")
  await evaluate("document.querySelector('.task-handover-management .el-card .el-table__body .el-checkbox__original').click();handoverTest.settle()")
  const sourceQueryCount = await evaluate('handoverTest.state.taskQueries.length')
  await openPicker('.source-picker .selector-input')
  await searchPicker('disabled')
  assert.equal((await pickerRows())[0].includes('禁用人员'), true)
  await cancelPicker()
  assert.equal(await evaluate("document.querySelector('.source-picker .selector-input').innerText.includes('已离职人员')"), true)
  assert.equal(await evaluate('handoverTest.state.taskQueries.length'), sourceQueryCount, '取消修改来源不得重新查询或清空待办')
  assert.equal(await evaluate("document.body.innerText.includes('已选 1 项')"), true)
  await evaluate("[...document.querySelectorAll('.task-handover-management .el-card .el-pager li.number')].find(e=>e.textContent.trim()==='2').click();handoverTest.settle()")
  await until("document.querySelectorAll('.task-handover-management .el-card .el-table__body .el-table__row').length===4")
  await evaluate("document.querySelector('.task-handover-management .el-card .el-table__body .el-checkbox__original').click();handoverTest.settle()")
  assert.equal(await evaluate("document.body.innerText.includes('已选 2 项')"), true)
  await evaluate("[...document.querySelectorAll('.task-handover-management .el-card .el-pager li.number')].find(e=>e.textContent.trim()==='1').click();handoverTest.settle()")
  await until("document.querySelectorAll('.task-handover-management .el-card .el-table__body .el-table__row').length===20")
  assert.equal(await evaluate("document.querySelector('.task-handover-management .el-card .el-table__body .el-checkbox__original').checked"), true)
  await screenshot('/tmp/flow-task-handover-list.png')

  await click('交接选中待办（2）')
  await openPicker('.handover-form .selector-input')
  const targetRows = await pickerRows()
  assert.equal(targetRows.length, 1, '接收弹窗不得包含来源、禁用或已删除人员')
  assert.equal(targetRows[0].includes('正常接收人'), true)
  await searchPicker('active')
  assert.equal((await pickerRows())[0].includes('正常接收人'), true)
  assert.equal(await evaluate("handoverTest.state.userQueries.some(q=>q.keyword==='active'&&q.targetOnly===true)"), true)
  await screenshot('/tmp/flow-task-handover-target-picker.png')
  await selectPerson('正常接收人')
  assert.equal(await evaluate("document.querySelector('.handover-form .selector-input').innerText.includes('正常接收人')"), true)
  await openPicker('.handover-form .selector-input')
  await searchPicker('没有此人')
  assert.equal((await pickerRows()).length, 0)
  await cancelPicker()
  assert.equal(await evaluate("document.querySelector('.handover-form .selector-input').innerText.includes('正常接收人')"), true, '取消接收人弹窗应保留原选择')
  await evaluate("(()=>{const input=document.querySelector('.handover-form textarea');input.value='离职后的待办交接';input.dispatchEvent(new Event('input',{bubbles:true}))})();handoverTest.settle()")
  await screenshot('/tmp/flow-task-handover-confirm.png')
  await click('确认交接', `(${handoverDialog})`)
  await click('确认交接', "document.querySelector('.el-message-box')")
  await until('handoverTest.state.calls.length===1')
  assert.deepEqual(await evaluate('handoverTest.state.calls[0]'), {
    sourceUserId: 'source', targetUserId: 'target', taskIds: ['task-0', 'task-20'], all: false, reason: '离职后的待办交接'
  })
  assert.equal(await evaluate("document.body.innerText.includes('已选 0 项')"), true)
  assert.equal(await evaluate("document.body.innerText.includes('共 22 项待办')"), true)

  await click('交接全部待办')
  await choose('.handover-form .selector-input', '正常接收人')
  await evaluate("(()=>{const input=document.querySelector('.handover-form textarea');input.value='交接剩余全部待办';input.dispatchEvent(new Event('input',{bubbles:true}))})();handoverTest.settle()")
  await click('确认交接', `(${handoverDialog})`)
  await click('返回修改', "document.querySelector('.el-message-box')")
  assert.equal(await evaluate('handoverTest.state.calls.length'), 1)
  await click('确认交接', `(${handoverDialog})`)
  await click('确认交接', "document.querySelector('.el-message-box')")
  await until('handoverTest.state.calls.length===2')
  assert.equal(await evaluate('handoverTest.state.calls[1].all'), true)
  assert.deepEqual(await evaluate('handoverTest.state.calls[1].taskIds'), [])
  await until("document.body.innerText.includes('该人员当前没有待办')")
  await evaluate("handoverTest.store.setPermissions(['system:task-handover:view']);handoverTest.settle()")
  assert.equal(await evaluate("[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='交接全部待办')"), false)
  assert.deepEqual(errors, [])
  console.log('Task handover browser regression passed; screenshots: /tmp/flow-task-handover-list.png, /tmp/flow-task-handover-confirm.png, /tmp/flow-task-handover-source-picker.png, /tmp/flow-task-handover-target-picker.png')
} finally {
  ws?.close()
  browser?.kill('SIGTERM')
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
  await sleep(300)
  rmSync(profile, { recursive: true, force: true })
}
