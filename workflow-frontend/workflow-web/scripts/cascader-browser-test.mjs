import assert from 'node:assert/strict'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 使用真实 Element Plus/Vant 字段及共享加载器；内存接口不访问业务服务或修改记录。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'vant/lib/index.css'
import PcField from '/src/extensions/builtin/fields/components/CascaderField.vue'
import MobileField from '/@fs/${path.resolve('../packages/workflow-mobile-ui/src/fields/MobileCascaderField.vue')}'
const state=reactive({values:{pc:[],mobile:[]},requests:[],changes:[],failure:false,delayed:false,dependency:0,disabled:false,version:0,mode:'CHILDREN'})
const pending=[]
const roots=()=>[{value:'a',label:'甲省',leaf:false},{value:'b',label:'乙省',leaf:false}]
const children=parent=>[{value:'same',label:parent==='a'?'甲市':'乙市',leaf:true},{value:'disabled',label:'禁用地区',leaf:true,disabled:true}]
function field(side){return {id:side+state.version,fieldCode:'address',fieldName:side==='pc'?'电脑地区':'手机地区',componentType:'cascader',componentProps:{cascaderSource:'INTERFACE',cascaderLoadMode:state.mode},dataSourceBindings:{FIELD_OPTIONS:{extensionId:'memory-cascader'}}}}
function runtime(side){return {loadOptions:async(_field,context)=>{
 const request=context.input.cascader
 state.requests.push({side,...request,dependency:state.dependency})
 if(state.mode==='ALL')return roots().map(node=>({...node,leaf:false,children:children(node.value)}))
 if(!request.path.length)return roots()
 if(state.failure&&request.parentValue==='a')throw Error('测试接口暂时不可用')
 if(state.delayed&&request.parentValue==='a')return new Promise(resolve=>pending.push(resolve))
 return children(request.parentValue)
}}}
const runtimes={pc:runtime('pc'),mobile:runtime('mobile')}
const settle=async()=>{await nextTick();await new Promise(r=>setTimeout(r,180));await nextTick()}
const app=createApp({setup(){return()=>h('main',{style:'max-width:700px;margin:28px auto'},[
 h('h1','级联接口验收'),
 ...['pc','mobile'].map(side=>h('section',{id:side,style:'padding:24px;border:1px solid #ddd;margin:16px'},[
 h(side==='pc'?PcField:MobileField,{field:field(side),modelValue:state.values[side],disabled:state.disabled,
 context:{form:{id:'form',configHash:'graph'},getFormData:()=>({address:state.values[side],dependency:state.dependency})},dataSourceRuntime:runtimes[side],
 'onUpdate:modelValue':value=>{state.values[side]=value;state.changes.push({side,value})}})
 ])),h('button',{id:'outside'},'其他区域')
])}})
app.use(ElementPlus).mount('#app')
window.cascaderTest={state,settle,reset:async(options={})=>{Object.assign(state,{values:{pc:[],mobile:[]},requests:[],changes:[],failure:false,delayed:false,disabled:false,mode:'CHILDREN',...options});state.version++;await settle()},resolvePending:()=>{while(pending.length)pending.shift()(children('a'))}}
`
const fixture=mkdtempSync(path.resolve('.cascader-fixture-'))
writeFileSync(path.join(fixture,'index.html'),'<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'),harness)
const profile=mkdtempSync(path.join(tmpdir(),'cascader-chrome-'))
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
async function clickText(text,selector='button'){
 await evaluate(`(()=>{const e=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(${visible}).find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!e)throw Error('Missing control: '+${JSON.stringify(text)});e.click()})()`)
 await evaluate('cascaderTest.settle()')
}
async function click(selector){await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);await evaluate('cascaderTest.settle()')}
const value=side=>evaluate(`JSON.parse(JSON.stringify(cascaderTest.state.values.${side}))`)
const openPc=()=>click('#pc .el-cascader')
const openMobile=()=>click('#mobile .van-field')

try{
 server=await createServer({configFile:false,plugins:[vue()],resolve:{dedupe:['vue'],alias:[{find:'@flow/workflow-core/vue/useCascaderFieldRuntime',replacement:path.resolve('../packages/workflow-core/src/composables/useCascaderFieldRuntime.js')},{find:'@flow/workflow-core/cascader-runtime',replacement:path.resolve('../packages/workflow-core/src/shared/cascader-runtime.js')}]},cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3397,strictPort:true,fs:{allow:[path.resolve('..')]}}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9397',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9397/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(Error(JSON.stringify(data.error))):p.resolve(data.result)}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Page.enable');await send('Emulation.setFocusEmulationEnabled',{enabled:true});await send('Emulation.setDeviceMetricsOverride',{width:1280,height:950,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3397/${path.basename(fixture)}/index.html`})
 for(let i=0;i<200;i++){if(await evaluate('Boolean(window.cascaderTest)'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.cascaderTest)'),true,errors.join('\n'))
 await evaluate('cascaderTest.settle()')
 await openPc();await clickText('甲省','.el-cascader-node__label');await clickText('甲市','.el-cascader-node__label')
 assert.deepEqual(await value('pc'),['a','same'],'PC 真实控件逐级选择完整路径')
 await openMobile();await clickText('乙省','.van-cascader__option');await clickText('乙市','.van-cascader__option')
 assert.deepEqual(await value('mobile'),['b','same'],'移动真实控件逐级选择完整路径')
 assert.equal(await evaluate('cascaderTest.state.changes.length'),2)
 assert.equal(await evaluate("cascaderTest.state.requests.some(r=>r.side==='mobile'&&r.parentValue==='b'&&r.level===1)"),true)
 // 相同末级编码位于不同分支，编辑/只读均应回显父路径。
 await evaluate("cascaderTest.reset({values:{pc:['b','same'],mobile:['b','same']},disabled:true})")
 assert.match(await evaluate("document.querySelector('#pc input').value"),/乙省.*乙市/)
 assert.match(await evaluate("document.querySelector('#mobile input').value"),/乙省.*乙市/)
 assert.equal(await evaluate('cascaderTest.state.changes.length'),0)
 // 新分支失败保留已选路径，点击真实重试按钮成功后才提交。
 await evaluate("cascaderTest.reset({values:{pc:['b','same'],mobile:['b','same']},failure:true})")
 await openPc();await clickText('甲省','.el-cascader-node__label')
 assert.deepEqual(await value('pc'),['b','same'])
 assert.match(await evaluate("document.querySelector('#pc').textContent"),/暂时不可用/)
 await evaluate('cascaderTest.state.failure=false');await clickText('重试','#pc button')
 await clickText('甲市','.el-cascader-node__label');assert.deepEqual(await value('pc'),['a','same'])
 await evaluate('cascaderTest.state.failure=true');await openMobile();await clickText('乙省','.van-cascader__tab');await clickText('甲省','.van-cascader__option')
 assert.deepEqual(await value('mobile'),['b','same'])
 assert.match(await evaluate('document.body.textContent'),/暂时不可用/)
 await evaluate('cascaderTest.state.failure=false');await clickText('重试','.van-popup button');await clickText('甲市','.van-cascader__option')
 assert.deepEqual(await value('mobile'),['a','same'])
 // ALL 读完整树；展开节点不再调用接口。
 await evaluate("cascaderTest.reset({mode:'ALL'})")
 await openMobile();await clickText('乙省','.van-cascader__option');await clickText('乙市','.van-cascader__option')
 assert.deepEqual(await value('mobile'),['b','same'])
 assert.equal(await evaluate("cascaderTest.state.requests.filter(r=>r.side==='mobile').length"),1)
 // 依赖变化关闭旧草稿并隔离未完成请求。
 await evaluate('cascaderTest.reset({delayed:true})');await openPc();await clickText('甲省','.el-cascader-node__label');await openMobile();await clickText('甲省','.van-cascader__option')
 assert.deepEqual(await value('mobile'),[])
 await evaluate('cascaderTest.state.dependency++;cascaderTest.settle()')
 await evaluate('cascaderTest.resolvePending();cascaderTest.settle()')
 assert.deepEqual(await value('pc'),[])
 assert.deepEqual(await value('mobile'),[])
 assert.equal(await evaluate(`[...document.querySelectorAll('.van-popup')].filter(${visible}).length`),0)
 assert.deepEqual(errors,[])
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-cascader-acceptance.png',Buffer.from(screenshot.data,'base64'))
 console.log('cascader browser acceptance passed: Element Plus/Vant lazy paths, duplicate leaf codes, readonly restore, failure/retry, ALL mode, stale response isolation')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
