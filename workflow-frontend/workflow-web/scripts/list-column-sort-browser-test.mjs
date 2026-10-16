import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实列表、表格和分页，所有接口在 axios adapter 中回放，不访问业务数据库。
const harness = `
import { createApp, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import List from '/src/views/entity/EntityDataList.vue'
import TemplateEditor from '/src/components/ui-config/ListColumnTemplateEditorDialog.vue'
import { createListColumnTemplateEditor, applyListColumnTemplateSnapshot } from '/src/shared/list-column-template.js'
import request from '/src/utils/request.js'
const copy = value => JSON.parse(JSON.stringify(value))
const field = (fieldCode, fieldName, extra={}) => ({
 fieldCode, fieldName, showInList:true, isQuery:false, width:180,
 dataSourceType:'ENTITY_FIELD', columnConfig:JSON.stringify({sortable:true}), ...extra
})
const rows = [30,10,40,20].map((amount,index)=>({
 id:'record-'+index, name:['Charlie','Alpha','Delta','Bravo'][index], data:{amount,note:'备注'+index}
}))
const state=reactive({
 requests:[], unexpected:[], pageSize:2, savedTemplate:null,
 fields:[
  field('name','名称'),field('amount','金额'),
  field('note','备注',{columnConfig:'{}'}),
  field('computed','虚拟列',{dataSourceType:'FIELD_TEMPLATE',dataSourceConfig:JSON.stringify({template:'常量'})})
 ]
})
request.defaults.adapter=async config=>{
 let data
 if(config.url==='/entity/code/sort_test') data={id:'entity-sort',entityCode:'sort_test',entityName:'排序验收',fields:[
  {fieldCode:'name',fieldType:'STRING'},{fieldCode:'amount',fieldType:'NUMBER'},{fieldCode:'note',fieldType:'STRING'}
 ]}
 else if(config.url==='/entity-lists/sort_test/sort_list/schema') data={
  id:'list-sort',listKey:'sort_list',configHash:'fixture',fields:copy(state.fields),
  toolbarButtons:[],rowActionButtons:[],viewConfig:{table:{showIndex:false},pagination:{pageSize:2,pageSizes:[2,3]}}
 }
 else if(config.url==='/entity-status/list/sort_test') data=[]
 else if(config.url==='/ui-component-templates' && config.method==='post') {
  state.savedTemplate=typeof config.data==='string'?JSON.parse(config.data):copy(config.data)
  data={id:'template-sort'}
 }
 else if(config.url==='/entity-lists/sort_test/sort_list/query') {
  const body=typeof config.data==='string'?JSON.parse(config.data):config.data
  state.requests.push(copy(body))
  // 模拟服务端先在全量结果上排序再分页；不允许把 data.amount 直接作为业务字段传给后端。
  let ordered=[...rows]
  if(body.sortField) {
   if(!['name','amount'].includes(body.sortField)) throw Error('Unexpected sortField: '+body.sortField)
   const value=row=>body.sortField==='amount'?row.data.amount:row.name
   ordered.sort((a,b)=>((value(a)>value(b))-(value(a)<value(b)))*(body.sortDirection==='desc'?-1:1))
  }
  const start=(body.pageNum-1)*body.pageSize
  data={records:ordered.slice(start,start+body.pageSize),total:ordered.length,pageNum:body.pageNum,pageSize:body.pageSize}
 } else {state.unexpected.push(config.url);throw Error('Unexpected API: '+config.url)}
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
const list=ref()
const editor=ref()
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]})
await router.push('/')
await router.isReady()
const app=createApp({setup:()=>()=>h('main',{id:'runtime'},[h(List,{
 ref:list,entityCode:'sort_test',listKey:'sort_list',embedded:true,showSearch:false,showPagination:true,
 showToolbar:false,showRowActions:false,allowDefaultFormResolve:false,pageSize:state.pageSize,maxHeight:0
}),h(TemplateEditor,{ref:editor})])})
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.sortTest={state,list,
 openTemplate(){editor.value.openEdit({id:'template-sort',templateKey:'SORT_TEMPLATE',templateName:'排序模板',
  editor:createListColumnTemplateEditor(state.savedTemplate?.snapshot || {field:{columnConfig:'{}'}})
 })},
 async applyTemplate(){
  applyListColumnTemplateSnapshot(state.fields[2],JSON.stringify(state.savedTemplate.snapshot))
  await list.value.reload({throwOnError:true})
 },
 settle:async()=>{await nextTick();await new Promise(resolve=>setTimeout(resolve,150));await nextTick()}
}
`
const fixture = mkdtempSync(path.resolve('.list-column-sort-fixture-'))
const profile = mkdtempSync(path.join(tmpdir(), 'list-column-sort-chrome-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><link rel="icon" href="data:,"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let browser, ws, server, nextId = 0
const pending = new Map(), errors = [], diagnostics = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

/** 为每个 CDP 命令设超时；浏览器启动或页面脚本故障也必须进入 finally 清理。 */
async function send(method, params = {}) {
  const id = ++nextId
  const result = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, {
      resolve: value => { clearTimeout(timer); resolve(value) },
      reject: error => { clearTimeout(timer); reject(error) }
    })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return result
}

async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}

async function step(expression) {
  await evaluate(expression)
  await evaluate('sortTest.settle()')
}

const header = label => `([...document.querySelectorAll('#runtime .el-table__header th')]).find(item=>item.textContent.trim()===${JSON.stringify(label)})`
const lastRequest = () => evaluate('JSON.parse(JSON.stringify(sortTest.state.requests.at(-1)))')
const amounts = () => evaluate(`([...document.querySelectorAll('#runtime .el-table__body tr')]).map(row=>Number(row.querySelectorAll('td')[1].textContent.trim()))`)
const arrowCount = label => evaluate(`${header(label)}.querySelectorAll('.sort-caret').length`)
const clickHeader = label => step(`${header(label)}.querySelector('.cell').click()`)
const dialog = `([...document.querySelectorAll('.el-dialog')]).find(item=>item.getClientRects().length)`
const sortSwitch = `([...${dialog}.querySelectorAll('.el-form-item')]).find(item=>item.querySelector('.el-form-item__label')?.textContent.trim()==='是否排序').querySelector('.el-switch')`

async function clickDialogText(text, selector = 'button') {
  await step(`([...${dialog}.querySelectorAll(${JSON.stringify(selector)})]).find(item=>item.textContent.trim()===${JSON.stringify(text)}).click()`)
}

/** 仅通过真实分页按钮切换页码，避免测试绕过 EntityDataTable 的分页事件。 */
async function changePage(number) {
  await step(`([...document.querySelectorAll('#runtime .el-pager .number')]).find(item=>item.textContent.trim()===${JSON.stringify(String(number))}).click()`)
}

function assertSort(request, field, direction, pageNum = 1) {
  assert.equal(request.sortField, field, '业务字段必须从表格 data.xxx 映射回字段编码')
  assert.equal(request.sortDirection, direction)
  assert.equal(request.pageNum, pageNum)
  assert.equal(request.filters.sortField, undefined, '排序属于顶层请求参数，不能混入筛选条件')
}

try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios', 'async-validator'] }, server: { host: '127.0.0.1', port: 0 } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })
  let targets
  let startupError
  browser.on('error', error => { startupError = error })
  for (let index = 0; index < 100; index++) {
    if (startupError) throw startupError
    try {
      const port = readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0]
      targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
      break
    } catch { await sleep(100) }
  }
  assert.ok(targets?.length, 'Chrome failed to start')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) {
      const request = pending.get(data.id)
      pending.delete(data.id)
      data.error ? request.reject(Error(JSON.stringify(data.error))) : request.resolve(data.result)
    }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
    if (data.method === 'Log.entryAdded' && data.params.entry.level === 'error') diagnostics.push(data.params.entry.text + ' ' + (data.params.entry.url || ''))
    if (data.method === 'Runtime.consoleAPICalled' && data.params.type === 'error') diagnostics.push(data.params.args.map(arg => arg.value || arg.description).join(' '))
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Log.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `${server.resolvedUrls.local[0]}${path.basename(fixture)}/index.html` })
  for (let index = 0; index < 500; index++) {
    if (await evaluate('Boolean(window.sortTest && document.querySelector("#runtime .el-table__body tr"))')) break
    if (errors.length) break
    await sleep(100)
  }
  assert.equal(await evaluate('Boolean(window.sortTest && document.querySelector("#runtime .el-table__body tr"))'), true, [...errors, ...diagnostics, await evaluate('document.body.innerText')].join('\n'))
  await evaluate('sortTest.settle()')

  assert.equal(await arrowCount('名称'), 2, '启用排序的系统列显示升降序箭头')
  assert.equal(await arrowCount('金额'), 2, '启用排序的业务列显示升降序箭头')
  assert.equal(await arrowCount('备注'), 0, '历史配置默认不显示排序入口')
  assert.equal(await arrowCount('虚拟列'), 0, '虚拟列即使残留 sortable=true 也不能参与服务端排序')
  assert.deepEqual(await amounts(), [30, 10])
  const initialCount = await evaluate('sortTest.state.requests.length')
  await clickHeader('备注')
  assert.equal(await evaluate('sortTest.state.requests.length'), initialCount, '未启用排序的表头点击不查询')

  await changePage(2)
  assert.equal((await lastRequest()).pageNum, 2)
  assert.deepEqual(await amounts(), [40, 20])
  await clickHeader('金额')
  assertSort(await lastRequest(), 'amount', 'asc')
  assert.deepEqual(await amounts(), [10, 20], '排序必须在全部记录上执行并返回第一页')
  assert.equal(await evaluate(`${header('金额')}.classList.contains('ascending')`), true)

  await changePage(2)
  assertSort(await lastRequest(), 'amount', 'asc', 2)
  assert.deepEqual(await amounts(), [30, 40], '翻页继续使用同一排序，全量数据顺序连续')
  await clickHeader('金额')
  assertSort(await lastRequest(), 'amount', 'desc')
  assert.deepEqual(await amounts(), [40, 30])
  assert.equal(await evaluate(`${header('金额')}.classList.contains('descending')`), true)

  await clickHeader('金额')
  const cleared = await lastRequest()
  assert.ok(!cleared.sortField && !cleared.sortDirection, '第三次点击取消排序，使用列表默认顺序')
  assert.deepEqual(await amounts(), [30, 10])
  assert.equal(await evaluate(`${header('金额')}.classList.contains('ascending')||${header('金额')}.classList.contains('descending')`), false)

  // 点击箭头也要产生后端请求，并验证在另一列激活时旧列高亮被清除。
  await step(`${header('金额')}.querySelector('.sort-caret.ascending').click()`)
  assertSort(await lastRequest(), 'amount', 'asc')
  await clickHeader('名称')
  assertSort(await lastRequest(), 'name', 'asc')
  assert.equal(await evaluate(`${header('金额')}.classList.contains('ascending')`), false)
  assert.deepEqual(await amounts(), [10, 20])
  await changePage(2)
  await step('sortTest.state.pageSize=3')
  assertSort(await lastRequest(), 'name', 'asc')
  assert.equal((await lastRequest()).pageSize, 3, '变更每页条数回到第一页并保留排序')
  assert.deepEqual(await amounts(), [10, 20, 30])

  await step('sortTest.list.value.reload({throwOnError:true})')
  assertSort(await lastRequest(), 'name', 'asc')
  assert.equal(await evaluate(`${header('名称')}.classList.contains('ascending')`), true, '刷新后排序指示与请求保持一致')
  await step('sortTest.openTemplate()')
  await clickDialogText('显示与预览', '[role="tab"]')
  assert.equal(await evaluate(`${sortSwitch}.querySelector('[role="switch"]').getAttribute('aria-checked')`), 'false', '模板历史配置默认不启用排序')
  await step(`${sortSwitch}.click()`)
  await clickDialogText('保存模板')
  assert.equal(await evaluate('JSON.parse(sortTest.state.savedTemplate.snapshot.field.columnConfig).sortable'), true, '真实模板保存请求携带排序开关')
  await step('sortTest.openTemplate()')
  await clickDialogText('显示与预览', '[role="tab"]')
  assert.equal(await evaluate(`${sortSwitch}.querySelector('[role="switch"]').getAttribute('aria-checked')`), 'true', '重载模板后排序开关回显')
  await clickDialogText('取消')
  await step('sortTest.applyTemplate()')
  assert.equal(await arrowCount('备注'), 2, '模板配置应用到真实列表后显示排序入口')

  await step('sortTest.openTemplate()')
  await clickDialogText('数据与查询', '[role="tab"]')
  await step(`([...${dialog}.querySelectorAll('.el-form-item')]).find(item=>item.querySelector('.el-form-item__label')?.textContent.trim()==='取值方式').querySelector('.el-select__wrapper').click()`)
  await step(`([...document.querySelectorAll('.el-select-dropdown__item')]).find(item=>item.getClientRects().length&&item.textContent.trim()==='字段组合').click()`)
  await clickDialogText('显示与预览', '[role="tab"]')
  assert.equal(await evaluate(`${sortSwitch}.querySelector('[role="switch"]').getAttribute('aria-checked')`), 'false', '切换为虚拟来源时清空旧排序配置')
  assert.equal(await evaluate(`${sortSwitch}.classList.contains('is-disabled')`), true, '虚拟来源不能打开排序开关')
  await clickDialogText('保存模板')
  assert.equal(await evaluate('JSON.parse(sortTest.state.savedTemplate.snapshot.field.columnConfig).sortable'), false)

  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(sortTest.state.unexpected))'), [])
  assert.deepEqual(errors, [])
  assert.deepEqual(diagnostics, [])
  console.log('list column sorting browser acceptance passed: actual headers/arrows, default disabled, virtual field guard, asc/desc/clear, data-field mapping, full-result pagination, page reset, page-size change, refresh persistence, template toggle/save/reload/apply/source change')
} finally {
  ws?.close()
  browser?.kill()
  await server?.close()
  await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
