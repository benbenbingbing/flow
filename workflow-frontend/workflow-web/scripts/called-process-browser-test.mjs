import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 使用真实 bpmn-js 命令栈验证转换及撤销，防止内部节点和外围连线丢失。
const harness = `
import Modeler from 'bpmn-js/lib/Modeler'
import { createApp, h, shallowRef } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '/src/components/NodeConfigPanel.vue'
import request from '/src/utils/request.js'
request.get=async url=>{
  if(url==='/process/published') return [{id:'1',processKey:'parent',processName:'当前流程'},{id:'2',processKey:'other_process',processName:'其他实体流程'}]
  if(url==='/entity/process/1') return {id:'entity-a',entityCode:'parent_entity'}
  if(url==='/entity-form/entity/entity-a/fields') return [{fieldCode:'relatedRecordId',fieldName:'关联记录 ID'}]
  return []
}
import flowable from '/src/assets/flowable.json'
import { readCalledProcessConfig, validateCalledProcessConfig, replaceWithCalledProcess } from '/src/components/node-config/calledProcessConfig.js'
import 'bpmn-js/dist/assets/diagram-js.css'
const modeler=new Modeler({container:'#app',moddleExtensions:{flowable}})
const selected=shallowRef(null)
await modeler.createDiagram()
const modeling=modeler.get('modeling'), factory=modeler.get('elementFactory'), registry=modeler.get('elementRegistry')
const root=modeler.get('canvas').getRootElement(), start=registry.get('StartEvent_1')
const sub=modeling.createShape(factory.createShape({type:'bpmn:SubProcess',isExpanded:true}),{x:460,y:260},root)
modeling.updateProperties(sub,{id:'sub',name:'原内嵌子流程'})
const inside=modeling.createShape(factory.createShape({type:'bpmn:UserTask'}),{x:460,y:260},sub)
modeling.updateProperties(inside,{id:'inside'})
const after=modeling.createShape(factory.createShape({type:'bpmn:UserTask'}),{x:800,y:260},root)
modeling.updateProperties(after,{id:'after'})
modeling.connect(start,sub,{type:'bpmn:SequenceFlow'})
modeling.connect(sub,after,{type:'bpmn:SequenceFlow'})
const config={...readCalledProcessConfig(),calledElement:'other_process',entityRecordId:'\${related.id}'}
function select(element){element._modeler=modeler;selected.value=element}
modeler.on('element.click',event=>select(event.element))
function mountPanel(){
  select(registry.get('sub'))
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]})
  createApp({setup:()=>()=>h(Panel,{element:selected.value,processId:'1'})}).use(createPinia()).use(router).use(ElementPlus).mount('#panel')
}
function persisted(){return registry.get('sub').businessObject.extensionElements.values.find(e=>e.$type==='flowable:Properties').values.find(e=>e.name==='callConfig').value}
function state(){const node=registry.get('sub');return {type:node.type,id:node.id,name:node.businessObject.name,inside:!!registry.get('inside'),incoming:node.incoming.map(f=>f.source.id),outgoing:node.outgoing.map(f=>f.target.id),calledElement:node.businessObject.calledElement}}
window.calledTest={state,mountPanel,persisted,select:id=>select(registry.get(id)),convert:()=>{replaceWithCalledProcess(modeler,registry.get('sub'),config);return state()},undo:()=>{modeler.get('commandStack').undo();return state()},redo:()=>{modeler.get('commandStack').redo();return state()},xml:async()=>(await modeler.saveXML({format:true})).xml,readCalledProcessConfig,validateCalledProcessConfig,settle:async()=>{}}
`
const fixture = mkdtempSync(path.resolve('.called-process-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app" style="width:900px;height:800px"></div><div id="panel" style="position:absolute;right:0;top:0;width:480px;height:100vh;background:white"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'flow-called-process-chrome-'))
let browser, ws, server, nextId = 0
const pending = new Map(), errors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function send(method, params = {}) {
  const id = ++nextId
  const promise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return promise
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
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios'], entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3427, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9427', '--user-data-dir=' + profile, 'about:blank'], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9427/json/list')).json(); break } catch { await sleep(100) } }
  assert.ok(targets?.length, 'Chrome 未启动')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) { const request = pending.get(data.id); pending.delete(data.id); data.error ? request.reject(Error(JSON.stringify(data.error))) : request.resolve(data.result) }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: 'http://127.0.0.1:3427/' + path.basename(fixture) + '/index.html' })
  await until('Boolean(window.calledTest)')

  const before=await evaluate('calledTest.state()')
  assert.equal(before.type,'bpmn:SubProcess')
  assert.equal(before.inside,true)
  const converted=await evaluate('calledTest.convert()')
  assert.deepEqual(converted,{...before,type:'bpmn:CallActivity',inside:false,calledElement:'other_process'})
  assert.match(await evaluate('calledTest.xml()'),/entityRecordId/)
  assert.deepEqual(await evaluate('calledTest.undo()'),before)
  assert.deepEqual(await evaluate('calledTest.redo()'),converted)
  await evaluate('calledTest.undo()')
  assert.deepEqual(await evaluate('calledTest.convert()'),converted)
  assert.equal(await evaluate('calledTest.readCalledProcessConfig({calledElement:"legacy"}).entityBinding'),'')
  assert.equal(await evaluate('calledTest.readCalledProcessConfig({},JSON.stringify({calledElement:"legacy"})).entityBinding'),'')
  assert.equal(await evaluate('calledTest.readCalledProcessConfig().entityRecordId'),'')
  assert.equal(await evaluate('calledTest.validateCalledProcessConfig({calledElement:"child",entityBinding:"existing",callActivityType:"bpmn",entityRecordId:"${related.id}"})'),'')
  assert.notEqual(await evaluate('calledTest.validateCalledProcessConfig({calledElement:"child",entityBinding:"existing",callActivityType:"bpmn",entityRecordId:"${bean.run()}"})'),'')
  // 实际点击面板，验证跨实体候选、字段来源、应用和再次选中后的回显。
  await evaluate('calledTest.undo();calledTest.mountPanel()')
  await until("document.body.innerText.includes('子流程来源')")
  await evaluate("[...document.querySelectorAll('.el-radio')].find(e=>e.textContent.includes('调用已有流程')).click()")
  await until("document.body.innerText.includes('记录 ID 来源')")
  await evaluate("document.querySelector('#panel .el-select').click()")
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.textContent.includes('其他实体流程'))")
  assert.equal(await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.textContent.includes('当前流程'))"),false)
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.textContent.includes('其他实体流程')).click()")
  await evaluate("document.querySelectorAll('#panel .el-select')[1].click()")
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.textContent.includes('关联记录 ID'))")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.textContent.includes('关联记录 ID')).click()")
  await evaluate("[...document.querySelectorAll('button')].find(e=>e.textContent.includes('应用到画布')).click()")
  await until("calledTest.state().type==='bpmn:CallActivity'")
  assert.equal(JSON.parse(await evaluate('calledTest.persisted()')).entityRecordId,'${relatedRecordId}')
  assert.equal(JSON.parse(await evaluate('calledTest.persisted()')).entityBinding,'existing')
  await evaluate("calledTest.select('after')")
  await sleep(100)
  await evaluate("calledTest.select('sub')")
  await until("document.querySelector('#panel').innerText.includes('关联记录 ID')")
  const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true})
  writeFileSync('/tmp/flow-called-process-panel.png',Buffer.from(shot.data,'base64'))
  assert.deepEqual(errors,[])
  console.log('PASS: 子流程转换、外围连线、撤销/重做、旧配置兼容、记录来源校验')
} finally {
  ws?.close()
  if (browser && browser.exitCode === null) {
    const exited = new Promise(resolve => browser.once('exit', resolve))
    browser.kill()
    await Promise.race([exited, sleep(3000)])
  }
  await server?.close()
  rmSync(fixture,{recursive:true,force:true}); rmSync(profile,{recursive:true,force:true,maxRetries:5,retryDelay:100})
}
