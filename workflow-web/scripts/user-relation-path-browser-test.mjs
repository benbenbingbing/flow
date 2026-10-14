import assert from 'node:assert/strict'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 使用真实关系选择器和 Element Plus 控件；内存目录不访问业务服务或修改记录。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Selector from '/src/components/EntityUserRelationPathSelector.vue'
const state=reactive({value:{schemaVersion:2,entityCode:'expense',path:[]},status:{},broken:false,failPrefix:null,delayPrefix:null,requests:[]})
const option=(code,label,source,target,multiple=false)=>({step:{type:source==='expense'?'REFERENCE_FIELD':'SYSTEM_REFERENCE',code,sourceEntityCode:source,targetEntityCode:target},label,targetEntityCode:target,multiple,terminal:target==='sys_user'})
const catalog=prefix=>!prefix.length?[option('applicant','申请用户','expense','sys_user'),option('applicants','申请用户（多选）','expense','sys_user',true),option('department','业务部门','expense','sys_organization'),{...option('retired','已停用关系','expense','sys_user'),disabledReason:'关系已停用'}]:prefix.at(-1).targetEntityCode==='sys_user'?[option('dept_id','所属部门','sys_user','sys_organization')]:[option('create_by','创建人','sys_organization','sys_user')]
window.pathTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,140));await nextTick()},request:async(url,{params}={})=>{
 const prefix=JSON.parse(params.prefix||params.path||'[]')
 if(url.endsWith('/options')){
  const codes=prefix.map(s=>s.code).join('/')
  state.requests.push(codes)
  if(state.failPrefix===codes)throw Error('模拟关系加载失败')
  if(state.delayPrefix===codes){state.delayPrefix=null;await new Promise(resolve=>window.pathTest.release=resolve)}
  if(state.broken&&prefix.length===1)return [];return catalog(prefix)
 }
 return {terminal:true,multiple:prefix.some(s=>s.code==='applicants')}
}}
createApp({setup:()=>()=>h('main',{style:'max-width:560px;margin:36px auto'},[
 h('h2','流程用户关系路径'),h(Selector,{modelValue:state.value,processId:'process-1',entityCode:'expense',
 'onUpdate:modelValue':value=>state.value=value,onState:value=>state.status=value})
])}).use(ElementPlus).mount('#app')
`
const fixture=mkdtempSync(path.resolve('.user-path-fixture-'))
writeFileSync(path.join(fixture,'index.html'),'<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'),harness)
writeFileSync(path.join(fixture,'request.js'), 'export default { get: (...args) => window.pathTest.request(...args) }')
const profile=mkdtempSync(path.join(tmpdir(),'user-path-chrome-'))
let browser,ws,server,nextId=0
const pending=new Map(),errors=[]
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
async function send(method,params={}){
 const id=++nextId
 const result=new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout: '+method))},15000);pending.set(id,{resolve:r=>{clearTimeout(timer);resolve(r)},reject:e=>{clearTimeout(timer);reject(e)}})})
 ws.send(JSON.stringify({id,method,params}));return result
}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||JSON.stringify(r.exceptionDetails));return r.result.value}
const visible="e=>e.getClientRects().length>0&&!e.closest('[aria-hidden=\"true\"]')"
async function openPicker(){
 await evaluate("document.querySelector('.path-input').click()")
 await sleep(450)
 assert.equal(await evaluate("document.querySelector('.path-input').getAttribute('aria-expanded')"),'true')
}
async function nodeAction(label,expand=false,occurrence=0){
 await evaluate(`(()=>{const label=[...document.querySelectorAll('.relation-label')].filter(${visible}).filter(e=>e.textContent.trim()===${JSON.stringify(label)}).at(${occurrence});if(!label)throw Error('missing tree node: '+${JSON.stringify(label)});const target=${expand}?label.closest('[role="treeitem"]').querySelector('.el-tree-node__expand-icon'):label;target.click()})()`)
 await evaluate('pathTest.settle()')
}
async function closePicker(){
 await evaluate("document.querySelector('.path-tree-panel').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))")
 await sleep(200)
 assert.equal(await evaluate("document.querySelector('.path-input').getAttribute('aria-expanded')"),'false')
}

try{
 server=await createServer({configFile:false,plugins:[vue()],resolve:{dedupe:['vue'],alias:[{find:'@/utils/request',replacement:path.join(fixture,'request.js')}]},cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3398,strictPort:true,fs:{allow:[path.resolve('..')]}}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9398',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9398/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(Error(JSON.stringify(data.error))):p.resolve(data.result)}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Page.enable');await send('Emulation.setFocusEmulationEnabled',{enabled:true});await send('Emulation.setDeviceMetricsOverride',{width:1280,height:950,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3398/${path.basename(fixture)}/index.html`})
 for(let i=0;i<200;i++){if(await evaluate('Boolean(window.pathTest)'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.pathTest)'),true,errors.join('\n'))
 await evaluate('pathTest.settle()')
 assert.deepEqual(await evaluate('pathTest.state.requests.slice()'),[''],'initial load only fetches root relations')
 assert.equal(await evaluate("document.querySelectorAll('.path-input').length"),1)
 assert.equal(await evaluate("document.querySelectorAll('.path-level, .el-select').length"),0)
 await openPicker()
 await nodeAction('已停用关系')
 assert.equal(await evaluate('pathTest.state.value.path.length'),0)
 await nodeAction('申请用户（多选）',true)
 assert.deepEqual(await evaluate('pathTest.state.requests.slice()'),['','applicants'],'only the expanded branch is loaded')
 await nodeAction('所属部门')
 assert.equal(await evaluate('pathTest.state.value.path.length'),0,'browsing does not commit an incomplete path')
 await nodeAction('创建人')
 assert.deepEqual(await evaluate('pathTest.state.value.path.map(s=>s.code)'),['applicants','dept_id','create_by'])
 assert.equal(await evaluate('pathTest.state.status.valid'),true)
 assert.equal(await evaluate('pathTest.state.status.multiple'),true)
 assert.equal(await evaluate("document.querySelector('.path-input').getAttribute('aria-expanded')"),'false')
 assert.match(await evaluate("document.querySelector('.path-value').textContent"),/申请用户.*所属部门.*创建人/)
 await sleep(250)
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-user-relation-path.png',Buffer.from(screenshot.data,'base64'))

 // 回显已选分支；浏览其他分支或关闭弹层不能覆盖既有选择。
 await openPicker()
 assert.equal(await evaluate("document.querySelector('.selected-icon').closest('.relation-node').querySelector('.relation-label').textContent"),'创建人')
 assert.equal(await evaluate("(()=>{const label=[...document.querySelectorAll('.relation-label')].find(e=>e.textContent==='所属部门');const rect=label.getBoundingClientRect();return document.elementFromPoint(rect.x+5,rect.y+rect.height/2)?.textContent.includes('所属部门')})()"),true,'restoring the tree must keep the intermediate relation visible')
 const treeScreenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-user-relation-path-tree.png',Buffer.from(treeScreenshot.data,'base64'))
 await nodeAction('创建人')
 assert.equal(await evaluate('pathTest.state.status.valid&&!pathTest.state.status.loading'),true,'reselecting the same endpoint must remain valid')
 await openPicker()
 await nodeAction('申请用户',true)
 await closePicker()
 assert.deepEqual(await evaluate('pathTest.state.value.path.map(s=>s.code)'),['applicants','dept_id','create_by'])
 assert.equal(await evaluate('pathTest.state.status.valid'),true)
 await openPicker()
 await nodeAction('申请用户')
 assert.equal(await evaluate('pathTest.state.value.path.length'),1)
 assert.equal(await evaluate('pathTest.state.status.multiple'),false)

 // 旧配置只回显不升级；用户节点仍能展开，失败后可以在原分支重试。
 await evaluate("pathTest.state.value={schemaVersion:1,entityCode:'expense',fieldCode:'applicant'};pathTest.settle()")
 assert.equal(await evaluate('pathTest.state.value.schemaVersion'),1)
 assert.equal(await evaluate("document.querySelector('.path-value').textContent"),'申请用户')
 await openPicker()
 await evaluate("pathTest.state.failPrefix='applicant'")
 await nodeAction('申请用户',true)
 assert.match(await evaluate("document.querySelector('.path-tree-panel .el-alert').textContent"),/加载失败/)
 assert.equal(await evaluate('pathTest.state.status.valid'),true)
 await evaluate('pathTest.state.failPrefix=null')
 await nodeAction('申请用户',true)
 await nodeAction('所属部门')
 await nodeAction('创建人')
 assert.equal(await evaluate('pathTest.state.value.schemaVersion'),2)
 assert.equal(await evaluate('pathTest.state.value.path.length'),3)

 // 同名循环关系可在不同深度继续展开，达到上限后不再加载下级。
 await openPicker()
 await nodeAction('业务部门')
 for(let depth=2;depth<8;depth++) await nodeAction(depth%2===0?'创建人':'所属部门',true,-1)
 assert.equal(await evaluate("[...document.querySelectorAll('.relation-label')].filter(e=>e.textContent==='创建人').at(-1).closest('[role=treeitem]').querySelector('.el-tree-node__expand-icon').classList.contains('is-leaf')"),true)
 await nodeAction('创建人',false,-1)
 assert.equal(await evaluate('pathTest.state.value.path.length'),8)
 assert.equal(await evaluate('pathTest.state.status.valid'),true)
 assert.equal(await evaluate('pathTest.state.requests.some(prefix=>prefix.split("/").length>=8)'),false)
 await evaluate("document.querySelector('.path-clear').click();pathTest.settle()")
 assert.equal(await evaluate('pathTest.state.value.path.length'),0)
 assert.equal(await evaluate('pathTest.state.status.valid'),false)

 // 方向键只展开，Enter 才选定；关闭后焦点回到唯一选择入口。
 await openPicker()
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowRight',code:'ArrowRight'})
 await evaluate('pathTest.settle()')
 assert.equal(await evaluate('pathTest.state.value.path.length'),0)
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Enter',code:'Enter'})
 await evaluate('pathTest.settle()')
 assert.deepEqual(await evaluate('pathTest.state.value.path.map(s=>s.code)'),['applicant'])
 assert.equal(await evaluate("document.activeElement.classList.contains('path-input')"),true)

 // 已失效配置保持原坐标，提示修复并仍允许重新选择。
 await evaluate("pathTest.state.broken=true;pathTest.state.value={schemaVersion:2,entityCode:'expense',path:[{type:'REFERENCE_FIELD',code:'applicant',targetEntityCode:'sys_user'},{type:'SYSTEM_REFERENCE',code:'dept_id',targetEntityCode:'sys_organization'}]};pathTest.settle()")
 assert.equal(await evaluate('pathTest.state.status.valid'),false)
 assert.equal(await evaluate('pathTest.state.value.path.length'),2)
 assert.match(await evaluate("document.querySelector('.path-error').textContent"),/关系“dept_id”已失效/)
 await openPicker()
 await nodeAction('申请用户（多选）')
 assert.equal(await evaluate('pathTest.state.status.valid'),true)

 // 节点切换后到达的旧展开响应不能污染新配置或新树。
 await evaluate("pathTest.state.broken=false;pathTest.state.delayPrefix='applicants'")
 await openPicker()
 await nodeAction('申请用户（多选）',true)
 await evaluate("pathTest.state.value={schemaVersion:1,entityCode:'expense',fieldCode:'applicant'};pathTest.settle()")
 await evaluate('pathTest.release();pathTest.settle()')
 assert.equal(await evaluate('pathTest.state.value.fieldCode'),'applicant')
 assert.equal(await evaluate('pathTest.state.status.valid'),true)
 assert.equal(await evaluate("document.querySelector('.path-value').textContent"),'申请用户')
 assert.equal(await evaluate("[...document.querySelectorAll('.relation-label')].filter(e=>e.textContent==='所属部门').length"),0)
 assert.deepEqual(errors,[])
 console.log('user relation path browser tests passed: lazy tree, selection/cancel, path display, cardinality, V1 restore, retry, depth limit, clear, invalid path retention, stale response isolation')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
