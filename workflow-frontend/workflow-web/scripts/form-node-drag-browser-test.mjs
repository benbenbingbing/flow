import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createServer } from 'vite'

// 挂载真实设计页，用内存接口及 localStorage 模拟草稿、刷新和发布，避免读写业务数据库。
const harness = `
import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Designer from '/src/views/EntityFormDesignByEntity.vue'
import request from '/src/utils/request.js'
const makeNode=(id,nodeType,parentId,orderKey,label)=>({id,nodeKey:id,nodeType,parentId,orderKey,revision:1,formId:'drag-form',bindingType:nodeType==='FIELD'?'ENTITY_FIELD':'NONE',bindingRef:nodeType==='FIELD'?id:'',propsDocument:JSON.stringify({label,fieldCode:id,fieldType:'VARCHAR',componentType:'input',gridSpan:24}),rulesDocument:'{}',dataSourceBindingsDocument:'{}'})
const initialNodes=[makeNode('tabs','TAB_SET','',1000000,'Tab 集合'),makeNode('tab-a','TAB','tabs',1000000,'基本信息'),makeNode('tab-b','TAB','tabs',2000000,'更多信息'),makeNode('inside','FIELD','tab-a',1000000,'原有字段'),makeNode('moving','FIELD','',2000000,'待移动字段'),makeNode('section','SECTION','',3000000,'区块'),makeNode('grid','GRID','',4000000,'栅格')]
const state=JSON.parse(localStorage.getItem('form-drag-test')||'null')||{form:{id:'drag-form',entityId:'entity-test',formName:'拖拽验收',formKey:'TEST_drag',layoutType:'grid',revision:1,status:1,activeReleaseId:'published',viewConfig:'{}'},nodes:initialNodes,published:initialNodes.map(n=>({...n})),orders:[],saves:[]}
const fingerprint=nodes=>JSON.stringify(nodes.map(({id,parentId,orderKey})=>({id,parentId:parentId||'',orderKey})).sort((a,b)=>a.id.localeCompare(b.id)))
const changed=()=>fingerprint(state.nodes)!==fingerprint(state.published)
const save=()=>localStorage.setItem('form-drag-test',JSON.stringify(state))
request.defaults.adapter=async config=>{
 let data=[]
 const body=config.data?JSON.parse(config.data):null
 if(config.url==='/entity/entity-test')data={id:'entity-test',entityCode:'TEST',entityName:'拖拽验收实体',fields:['inside','moving'].map(id=>({id,fieldCode:id,fieldName:id==='moving'?'待移动字段':'原有字段',fieldType:'VARCHAR'}))}
 else if(config.url==='/entity-form/drag-form')data=state.form
 else if(config.url==='/entity-forms/drag-form/nodes')data=state.nodes
 else if(config.url==='/entity-forms/drag-form/diff')data={changed:changed(),discardableChanged:changed(),changedSections:changed()?['nodes']:[],changedItems:changed()?[{changeType:'MOVED',id:'moving',label:'待移动字段'}]:[]}
 else if(config.url?.endsWith('/order')){
  const id=config.url.split('/').at(-2),node=state.nodes.find(n=>n.id===id)
  if(body.expectedRevision!==node.revision)throw Error('节点 revision 不匹配')
  if(body.parentId&&!state.nodes.some(n=>n.id===body.parentId))throw Error('目标父容器尚未保存')
  state.orders.push({id,...body})
  node.parentId=body.parentId||''
  const previous=state.nodes.find(n=>n.id===body.previousNodeId),next=state.nodes.find(n=>n.id===body.nextNodeId)
  node.orderKey=next?Math.floor(((previous?.orderKey||0)+next.orderKey)/2):(previous?.orderKey||0)+1000000
  node.revision++;state.form.revision++;data=node;save()
 }
 else if(config.url==='/entity-form/drag-form/patch'){
  if(body.expectedRevision!==state.form.revision)throw Error('表单 revision 不匹配')
  state.form={...state.form,...body,revision:state.form.revision+1,viewConfig:JSON.stringify(body.viewConfig)};data=state.form;save()
 }
 else if(config.url==='/entity-forms/drag-form/nodes/update'){
  if(config.params.expectedRevision!==state.form.revision)throw Error('保存全部 revision 不匹配')
  state.saves.push(body);state.nodes=body;state.form.revision++;data=state.nodes;save()
 }
 else if(config.url==='/entity-forms/drag-form/publish-preview')data={changed:changed(),canPublish:changed(),draftHash:fingerprint(state.nodes),activeReleaseId:'published',riskLevel:'LOW',changedItems:[{id:'moving'}],blockers:[]}
 else if(config.url==='/entity-forms/drag-form/publish'){
  state.published=state.nodes.map(n=>({...n}));data={id:'published'};save()
 }
 else if(config.url?.endsWith('/catalog'))data={events:[]}
 return {data:{code:200,data:JSON.parse(JSON.stringify(data))},status:200,statusText:'OK',headers:{},config}
}
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/entity-form/design/:id',component:Designer}]})
await router.push('/entity-form/design/drag-form?entityId=entity-test')
await router.isReady()
createApp({render:()=>h(RouterView)}).use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.dragTest={state,changed}
`;

const fixture = mkdtempSync(path.resolve('.form-node-drag-fixture-'))
const profile = mkdtempSync(path.join(tmpdir(), 'form-node-drag-chrome-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><style>html,body,#app{height:100%;margin:0}</style></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser, ws, nextId = 0
const pending = new Map()
const errors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

async function send(method, params = {}) {
  const id = ++nextId
  const promise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: result => { clearTimeout(timer); resolve(result) }, reject: error => { clearTimeout(timer); reject(error) } })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return promise
}

async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}

async function waitFor(expression, message) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await evaluate(expression)) return
    await sleep(100)
  }
  assert.fail(message + ': ' + errors.join('\n'))
}

async function clickButton(label, scope = 'document') {
  await evaluate(`(() => { const button=[...${scope}.querySelectorAll('button')].find(e=>e.getClientRects().length&&e.textContent.replace(/\\s+/g,'')===${JSON.stringify(label)});if(!button)throw Error('Missing button: '+${JSON.stringify(label)});button.click() })()`)
}

async function dragNode(nodeId, parentId, index = 'last') {
  const source = `.form-node-design-item[data-node-id="${nodeId}"] > .node-actions .form-node-drag-handle`
  const target = `.form-node-drop-zone[data-form-node-parent-id="${parentId}"]:not(.design-tab-order)`
  const rects = await evaluate(`(() => {
    const handle=document.querySelector(${JSON.stringify(source)}),zone=document.querySelector(${JSON.stringify(target)});
    zone.scrollIntoView({block:'center'});const a=handle.getBoundingClientRect(),b=zone.getBoundingClientRect();
    return {from:{x:a.x+a.width/2,y:a.y+a.height/2},to:{x:b.x+b.width/2,y:${index === 'first' ? 'b.y+8' : 'b.bottom-12'}}}
  })()`)
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...rects.from })
  await sleep(100)
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', ...rects.from, button: 'left', clickCount: 1 })
  for (let step = 1; step <= 20; step++) {
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: rects.from.x + (rects.to.x - rects.from.x) * step / 20, y: rects.from.y + (rects.to.y - rects.from.y) * step / 20, button: 'left', buttons: 1 })
    await sleep(25)
  }
  // 跨容器时来源高度和目标位置会改变，松手前重新定位目标，避免落到移动前的空白位置。
  const destination = await evaluate(`(() => {const zone=document.querySelector(${JSON.stringify(target)}),empty=zone.querySelector(':scope > .design-container-empty'),b=zone.getBoundingClientRect(),e=empty?.getBoundingClientRect();return {x:b.x+b.width/2,y:${index === 'first' ? 'b.y+8' : 'e?e.y+e.height/2:b.bottom-12'}}})()`)
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...destination, button: 'left', buttons: 1 })
  await sleep(250)
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', ...destination, button: 'left', buttons: 0, clickCount: 1 })
  await sleep(400)
}

try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3399, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9399', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' })
  let targets
  for (let attempt = 0; attempt < 100; attempt++) {
    try { targets = await (await fetch('http://127.0.0.1:9399/json/list')).json(); break } catch { await sleep(100) }
  }
  assert.ok(targets?.length, 'Chrome failed to start')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const handler = pending.get(message.id); pending.delete(message.id)
      message.error ? handler.reject(Error(JSON.stringify(message.error))) : handler.resolve(message.result)
    }
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text)
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1400, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `http://127.0.0.1:3399/${path.basename(fixture)}/index.html` })
  await waitFor(`Boolean(window.dragTest)&&document.querySelectorAll('[data-sortable-ready="true"]').length>=6&&!document.querySelector('.el-loading-mask')`, 'Designer did not initialize')

  await dragNode('moving', 'tab-a')
  assert.equal(await evaluate(`dragTest.state.nodes.find(n=>n.id==='moving').parentId`), 'tab-a', '跨容器拖拽必须持久化父容器，不能只移动 DOM')
  assert.equal(await evaluate(`dragTest.state.orders.at(-1).parentId`), 'tab-a')
  await waitFor(`document.querySelectorAll('.form-node-design-item[data-node-id="moving"]').length===1&&document.querySelector('.form-node-design-item[data-node-id="moving"]').closest('.form-node-drop-zone').dataset.formNodeParentId==='tab-a'`, 'Moved node DOM did not reconcile')
  assert.equal(await evaluate('dragTest.changed()'), true, '移动父容器必须成为待发布修改')

  // 属性抽屉与真实草稿必须一致，防止画布看似嵌套但父容器仍然是根节点。
  await evaluate(`document.querySelector('.form-node-design-item[data-node-id="moving"]').dispatchEvent(new MouseEvent('dblclick',{bubbles:true}))`)
  await waitFor(`Boolean([...document.querySelectorAll('.el-drawer')].find(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent))`, 'Node properties did not open')
  await evaluate(`(() => {const header=document.querySelector('.node-layout-settings .settings-section__header');if(header?.getAttribute('aria-expanded')==='false')header.click()})()`)
  await waitFor(`Boolean([...document.querySelectorAll('.el-form-item')].find(e=>e.querySelector('.el-form-item__label')?.textContent.includes('父容器'))?.querySelector('.el-select__placeholder')?.textContent.includes('基本信息'))`, 'Parent container did not reflect the move')
  const parentLabel = await evaluate(`[...document.querySelectorAll('.el-form-item')].find(e=>e.querySelector('.el-form-item__label')?.textContent.includes('父容器'))?.querySelector('.el-select__placeholder')?.textContent`)
  assert.match(parentLabel || '', /基本信息/)
  await evaluate(`document.querySelector('.el-drawer__close-btn').click()`)
  await clickButton('保存全部')
  await waitFor('dragTest.state.saves.length===1', 'Save all did not persist nodes')
  assert.equal(await evaluate(`dragTest.state.saves[0].find(n=>n.id==='moving').parentId`), 'tab-a')
  await evaluate('window.dragTest=null')
  await send('Page.reload')
  await waitFor(`Boolean(window.dragTest)&&document.querySelector('.tab-node-children[data-form-node-parent-id="tab-a"] .form-node-design-item[data-node-id="moving"]')&&!document.querySelector('.el-loading-mask')`, 'Refresh lost parent container')
  await clickButton('发布')
  const publishDialog = `[...document.querySelectorAll('.el-dialog')].find(e=>e.getClientRects().length&&e.textContent.includes('表单发布'))`
  await waitFor(`Boolean(${publishDialog})`, 'Moved node did not open publish dialog')
  await waitFor(`Boolean(${publishDialog}?.querySelector('.el-dialog__footer .el-button--primary:not([disabled])'))`, 'Publish preview did not recognize the move')
  await clickButton('发布', publishDialog)
  await waitFor('!dragTest.changed()', 'Published configuration did not include parent move')
  await waitFor(`!(${publishDialog})`, 'Publish dialog did not close')

  // 覆盖嵌套容器之间、移回根节点和同级排序，确保所有目标实例共享同一次拖拽。
  await dragNode('moving', 'section')
  assert.equal(await evaluate(`dragTest.state.nodes.find(n=>n.id==='moving').parentId`), 'section')
  await dragNode('moving', 'grid')
  assert.equal(await evaluate(`dragTest.state.nodes.find(n=>n.id==='moving').parentId`), 'grid')
  await dragNode('moving', '', 'first')
  assert.equal(await evaluate(`dragTest.state.nodes.find(n=>n.id==='moving').parentId`), '')
  assert.equal(await evaluate(`dragTest.state.orders.at(-1).parentId`), null, '移回根节点必须显式清空父容器')
  await dragNode('moving', 'tab-a')
  await dragNode('moving', 'tab-a', 'first')
  assert.equal(await evaluate(`dragTest.state.orders.at(-1).nextNodeId`), 'inside')
  assert.equal(await evaluate(`document.querySelectorAll('.form-node-design-item[data-node-id="moving"]').length`), 1)

  // 新建子页面尚未存在于服务器时，移动必须保留在本地，保存全部再按父子顺序一起提交。
  await dragNode('moving', '', 'first')
  await clickButton('节点')
  await waitFor(`Boolean([...document.querySelectorAll('.el-dropdown-menu__item')].find(e=>e.textContent.trim()==='Tab 页'))`, 'Node menu did not open')
  await evaluate(`[...document.querySelectorAll('.el-dropdown-menu__item')].find(e=>e.textContent.trim()==='Tab 页').click()`)
  await waitFor(`Boolean(document.querySelector('.design-tab-order-item[data-node-id^="node_tab_"]'))`, 'New tab did not appear')
  const newTabId = await evaluate(`document.querySelector('.design-tab-order-item[data-node-id^="node_tab_"]').dataset.nodeId`)
  await waitFor(`Boolean(document.querySelector('.node-property-drawer .el-drawer__close-btn'))`, 'New tab properties did not open')
  await evaluate(`document.querySelector('.node-property-drawer .el-drawer__close-btn').click()`)
  await waitFor(`!document.querySelector('.node-property-drawer')?.getClientRects().length`, 'New tab properties did not close')
  await evaluate(`document.querySelector('.design-tab-order-item[data-node-id="${newTabId}"] .design-tab-order-label').click()`)
  const orderCount = await evaluate('dragTest.state.orders.length')
  await dragNode('moving', newTabId)
  assert.equal(await evaluate('dragTest.state.orders.length'), orderCount, '未保存的子页面不能调用服务器节点移动接口')
  assert.equal(await evaluate(`document.querySelector('.form-node-design-item[data-node-id="moving"]').closest('.form-node-drop-zone').dataset.formNodeParentId`), newTabId)
  await clickButton('保存全部')
  await waitFor('dragTest.state.saves.length===2', 'New tab hierarchy was not saved')
  assert.equal(await evaluate(`dragTest.state.saves[1].find(n=>n.id==='moving').parentId`), newTabId)
  await evaluate('window.dragTest=null')
  await send('Page.reload')
  await waitFor(`Boolean(window.dragTest)&&document.querySelector('.design-tab-order-item[data-node-id="${newTabId}"]')&&!document.querySelector('.el-loading-mask')`, 'Refresh lost new tab')
  await evaluate(`document.querySelector('.design-tab-order-item[data-node-id="${newTabId}"] .design-tab-order-label').click()`)
  await waitFor(`Boolean(document.querySelector('.tab-node-children[data-form-node-parent-id="${newTabId}"] .form-node-design-item[data-node-id="moving"]'))`, 'Refresh lost field under new tab')
  assert.deepEqual(errors, [])
  const screenshot = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync('/tmp/flow-form-node-drag.png', Buffer.from(screenshot.data, 'base64'))
  console.log('form node drag browser acceptance passed: parent property, save/reload, publish, nested transfers, root reset, sibling reorder and unsaved new tab')
} catch (error) {
  if (ws?.readyState === WebSocket.OPEN) {
    console.error(await evaluate(`JSON.stringify({orders:window.dragTest?.state.orders,parentLabels:[...document.querySelectorAll('.el-form-item')].filter(e=>e.querySelector('.el-form-item__label')?.textContent.includes('父容器')).map(e=>e.querySelector('.el-select__placeholder')?.textContent),nodes:[...document.querySelectorAll('.form-node-design-item[data-node-id="moving"]')].map(e=>({parent:e.closest('.form-node-drop-zone').dataset.formNodeParentId}))})`))
    const screenshot = await send('Page.captureScreenshot', { format: 'png' })
    writeFileSync('/tmp/flow-form-node-drag-failure.png', Buffer.from(screenshot.data, 'base64'))
  }
  throw error
} finally {
  ws?.close(); browser?.kill(); await server?.close()
  await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
