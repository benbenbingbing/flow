import assert from 'node:assert/strict'
import { build, createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 真实组件配合内存只读响应，不修改业务库。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import FormButtons from '/src/components/FormButtonConfigPanel.vue'
import RoundHistory from '/src/views/entity/components/approval/EntityProcessRoundHistory.vue'
import request from '/src/utils/request.js'
const state = reactive({ actionBar: {version:1,builtInOverrides:{},customButtons:[]}, requests: [] })
request.get = async url => {
 state.requests.push(url)
 if (url !== '/process-instance/old/progress') throw Error('Unexpected request: '+url)
 return {processInstanceId:'old',status:'COMPLETED',endType:'WITHDRAWN',cancelledNodes:['review'],
 nodeHistory:[{nodeId:'review',nodeName:'审批',status:'CANCELLED',action:'CANCELLED',comment:'修正金额'},
 {nodeId:'withdraw',nodeName:'流程撤回',status:'WITHDRAWN',action:'WITHDRAWN',assignee:'starter',assigneeName:'发起人',comment:'修正金额'}]}
}
const rounds=[{processInstanceId:'new',generation:2,status:'RUNNING'}, {processInstanceId:'old',generation:1,status:'COMPLETED',endType:'WITHDRAWN'}]
createApp({setup(){return()=>h('main',{style:'padding:24px'},[
 h('h2','重新发起默认关闭'),h(FormButtons,{modelValue:state.actionBar,'onUpdate:modelValue':v=>state.actionBar=v}),
 h('h2','原记录各轮历史'),h(RoundHistory,{processInstanceId:'new',bpmnXml:'',progressData:{rounds},processHistory:[{title:'新一轮审批',description:'运行中',status:'ACTIVE'}],view:'history'})
])}}).use(createPinia()).use(ElementPlus).mount('#app')
window.restartTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,250));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.process-restart-fixture-'))
writeFileSync(path.join(fixture,'index.html'), '<html><head><link rel="icon" href="data:,"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'), harness)
const profile=mkdtempSync(path.join(tmpdir(),'process-restart-chrome-'))
let browser,ws,server,nextId=0
const pending=new Map(),errors=[]
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
async function send(method,params={}){
 const id=++nextId
 const result=new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout: '+method))},15000);pending.set(id,{resolve:r=>{clearTimeout(timer);resolve(r)},reject:e=>{clearTimeout(timer);reject(e)}})})
 ws.send(JSON.stringify({id,method,params}));return result
}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||JSON.stringify(r.exceptionDetails));return r.result.value}
const visible='e=>e.getClientRects().length>0'
async function clickText(text,selector='button'){
 await evaluate(`(()=>{const e=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(${visible}).find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!e)throw Error('Missing control: '+${JSON.stringify(text)});e.click()})()`)
 await evaluate('restartTest.settle()')
}
async function options(){return evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).map(e=>({text:e.textContent.trim(),disabled:e.classList.contains('is-disabled')}))`)}
async function openEventOptions(){await evaluate(`([...document.querySelectorAll('.el-dialog')].filter(${visible})).at(-1).querySelector('.el-select__wrapper').click()`);await evaluate('restartTest.settle()')}
try{
 // 验收固定构建产物，避免开发期依赖预构建触发重载打断状态断言。
 await build({base:'./',logLevel:'error',build:{outDir:path.join(fixture,'preview'),emptyOutDir:true,rollupOptions:{input:path.join(fixture,'index.html')}}})
 server=await createServer({configFile:false,root:path.join(fixture,'preview'),server:{host:'127.0.0.1',port:3399,strictPort:true}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9399',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9399/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(Error(JSON.stringify(data.error))):p.resolve(data.result)}if(data.method==='Log.entryAdded'&&data.params.entry.level==='error')errors.push(data.params.entry.text+' '+data.params.entry.url);if(data.method==='Runtime.consoleAPICalled'&&data.params.type==='error')errors.push(data.params.args.map(a=>a.description||a.value).join(' '));if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Log.enable');await send('Page.enable');await send('Emulation.setDeviceMetricsOverride',{width:1280,height:900,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3399/${path.basename(fixture)}/index.html`})
 for(let i=0;i<500;i++){if(await evaluate('Boolean(window.restartTest)'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.restartTest)'),true,errors.join('\n')+' '+await evaluate('location.href+" "+document.body.innerText.slice(0,500)'));await evaluate('restartTest.settle()')


 await evaluate(`([...document.querySelectorAll('.el-segmented__item')].find(e=>e.textContent.trim()==='编辑')).click()`)
 await evaluate('restartTest.settle()')
 const restartRow=`([...document.querySelectorAll('.el-table__body tr')].find(e=>e.textContent.includes('保存并重新发起')))`
 assert.equal(await evaluate(`${restartRow}.querySelector('[role="switch"]').getAttribute('aria-checked')`),'false')
 await evaluate(`${restartRow}.querySelector('[role="switch"]').click()`);await evaluate('restartTest.settle()')
 assert.equal(await evaluate('restartTest.state.actionBar.builtInOverrides.restartProcess.enabled'),true)
 await evaluate(`${restartRow}.querySelector('[role="switch"]').click()`);await evaluate('restartTest.settle()')
 assert.equal(await evaluate('restartTest.state.actionBar.builtInOverrides.restartProcess.enabled'),false)
 assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(restartTest.state.requests))'),[])
 await evaluate(`document.querySelector('.process-round-selector .el-select__wrapper').click()`);await evaluate('restartTest.settle()')
 await evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('第 1 轮'))).click()`);await evaluate('restartTest.settle()')
 const oldText=await evaluate(`document.querySelector('.process-round-content').textContent`)
 assert.match(oldText,/已取消/);assert.match(oldText,/已撤回/);assert.match(oldText,/修正金额/);assert.doesNotMatch(oldText,/通过|新一轮/)
 assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(restartTest.state.requests))'),['/process-instance/old/progress'])
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-process-restart-history.png',Buffer.from(screenshot.data,'base64'))
 await evaluate(`document.querySelector('.process-round-selector .el-select__wrapper').click()`);await evaluate('restartTest.settle()')
 await evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('第 2 轮'))).click()`);await evaluate('restartTest.settle()')
 assert.match(await evaluate(`document.querySelector('.process-round-content').textContent`),/新一轮审批/)
 assert.deepEqual(errors,[])
 console.log('process restart browser acceptance passed: opt-in config, independent round history, cancelled vs withdrawn, current round preserved')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
