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
const state=reactive({value:{schemaVersion:2,entityCode:'expense',path:[]},status:{},broken:false})
const option=(code,label,source,target,multiple=false)=>({step:{type:source==='expense'?'REFERENCE_FIELD':'SYSTEM_REFERENCE',code,sourceEntityCode:source,targetEntityCode:target},label,targetEntityCode:target,multiple,terminal:target==='sys_user'})
const catalog=prefix=>!prefix.length?[option('applicant','申请用户','expense','sys_user'),option('applicants','申请用户（多选）','expense','sys_user',true)]:prefix.at(-1).targetEntityCode==='sys_user'?[option('dept_id','所属部门','sys_user','sys_organization')]:[option('create_by','创建人','sys_organization','sys_user')]
window.pathTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,140));await nextTick()},request:async(url,{params}={})=>{
 const prefix=JSON.parse(params.prefix||params.path||'[]')
 if(url.endsWith('/options')){if(state.broken&&prefix.length===1)return [];return catalog(prefix)}
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
async function select(level,label){
 await evaluate(`document.querySelectorAll('.el-select__wrapper')[${level}].click()`);await sleep(100)
 await evaluate(`(()=>{const option=[...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible}).find(e=>e.textContent.trim().startsWith(${JSON.stringify(label)}));if(!option)throw Error('missing option');option.click()})()`)
 await evaluate('pathTest.settle()')
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
 await select(0,'申请用户（多选）')
 assert.equal(await evaluate('pathTest.state.status.valid'),true)
 assert.equal(await evaluate('pathTest.state.status.multiple'),true)
 await select(1,'所属部门');assert.equal(await evaluate('pathTest.state.status.valid'),false)
 await select(2,'创建人')
 assert.deepEqual(await evaluate('pathTest.state.value.path.map(s=>s.code)'),['applicants','dept_id','create_by'])
 assert.equal(await evaluate('pathTest.state.status.multiple'),true)
 assert.match(await evaluate("document.querySelector('.path-summary').textContent"),/申请用户.*所属部门.*创建人/)
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-user-relation-path.png',Buffer.from(screenshot.data,'base64'))
 await select(0,'申请用户');assert.equal(await evaluate('pathTest.state.value.path.length'),1)
 await evaluate("pathTest.state.value={schemaVersion:1,entityCode:'expense',fieldCode:'applicant'};pathTest.settle()")
 assert.equal(await evaluate('pathTest.state.value.schemaVersion'),1)
 await evaluate("pathTest.state.broken=true;pathTest.state.value={schemaVersion:2,entityCode:'expense',path:[{type:'REFERENCE_FIELD',code:'applicant',targetEntityCode:'sys_user'},{type:'SYSTEM_REFERENCE',code:'dept_id',targetEntityCode:'sys_organization'}]};pathTest.settle()")
 assert.equal(await evaluate('pathTest.state.status.valid'),false)
 assert.equal(await evaluate('pathTest.state.value.path.length'),2)
 assert.match(await evaluate("document.querySelector('.el-alert').textContent"),/第 2 级关系失效/)
 assert.deepEqual(errors,[])
 console.log('user relation path browser tests passed: incremental selection, user endpoint expansion, cardinality, upstream reset, V1 restore, invalid path retention')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
