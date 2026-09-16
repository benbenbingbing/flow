import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 使用真实字段渲染器、选择器和脚本编辑器；接口用内存替身，不修改业务数据。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import Renderer from '/src/components/FormFieldRendererLinkage.vue'
import Editor from '/src/components/EventConfigPanel.vue'
import FormPreview from '/src/components/FormPreviewLinkage.vue'
import { uiEventBindingApi } from '/src/api/uiConfig.js'
import request from '/src/utils/request.js'
const state = reactive({record:{text:'', user:null, target:''}, trace:[], visible:false, codes:{onChange:'if ('}, saved:null, useAccessor:false})
window.scriptTrace=state.trace
const text={fieldCode:'text',fieldName:'测试文本',componentType:'input',componentProps:{events:{
 onInput:"window.scriptTrace.push(['input',value])",
 onChange:"await new Promise(r=>setTimeout(r,20)); setValue(value.trim().toUpperCase()); setFieldValue('target',getFieldValue('text')); window.scriptTrace.push(['script',value])",
 onFocus:"window.scriptTrace.push(['focus',value])",onBlur:"window.scriptTrace.push(['blur',value])"
}}}
const user={fieldCode:'user',fieldName:'单选用户',fieldType:'REFERENCE',componentType:'reference',refEntityType:'USER',componentProps:{events:{onChange:"setFieldValue('target', selection ? selection.name : '已清空'); window.scriptTrace.push(['user',value,selection])"}}}
state.text=text;state.user=user
state.nodeRecord={nodeText:''}
const nodeField={id:'nf1',fieldCode:'nodeText',fieldName:'节点文本',fieldType:'STRING',componentType:'input',componentProps:{events:{onChange:"await Promise.resolve();setValue(value.toUpperCase());setFieldValue('nodeTarget','节点回填')"}}}
const nodeForm={id:'nodeForm',fields:[nodeField,{id:'nf2',fieldCode:'nodeTarget',fieldType:'STRING'}],nodes:[{id:'node1',nodeType:'FIELD',nodeKey:'nodeText',bindingType:'FIELD',bindingRef:'nf1',props:{fieldCode:'nodeText',componentType:'input'}}]}
const row={id:'u1',name:'测试用户',code:'user1',entityType:'USER'}
request.get=async url=>{if(!url.startsWith('/entity-selector/'))throw Error('unexpected API: '+url);return url.includes('/batch')?[row]:{records:[row],total:1}}
uiEventBindingApi.execute=async(code,payload)=>{state.trace.push(['chain',code,JSON.parse(JSON.stringify(payload))]);return {effects:[]}}
const app=createApp({setup(){return()=>h('main',{style:'width:880px;margin:30px auto'},[
 h('h2','前端脚本事件验收'),
 ...['text','user'].map(key=>h('div',{id:key,style:'margin:20px 0'},[h(Renderer,{field:state[key],modelValue:state.record[key],'onUpdate:modelValue':v=>state.record[key]=v,context:{form:{id:'f1'},...(state.useAccessor?{getFormData:()=>state.record}:{record:state.record})}})])),
 h('div',{id:'node-preview'},[h(FormPreview,{form:nodeForm,modelValue:state.nodeRecord,'onUpdate:modelValue':v=>state.nodeRecord=v,mode:'create'})]),
 h('p',{id:'target'},state.record.target),h('button',{id:'outside'},'其他区域'),
 h(Editor,{visible:state.visible,'onUpdate:visible':v=>state.visible=v,modelValue:state.codes,field:state.text,onSave:codes=>state.saved=codes})
])}})
app.config.warnHandler=message=>console.warn(message)
app.use(createPinia()).use(ElementPlus,{locale:zhCn}).mount('#app')
window.scriptTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,130));await nextTick()}}
`
const fixture=mkdtempSync(path.resolve('.field-script-fixture-'))
writeFileSync(path.join(fixture,'index.html'),'<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'),harness)
const profile=mkdtempSync(path.join(tmpdir(),'field-script-chrome-'))
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
 await evaluate('scriptTest.settle()')
}
async function inputText(text){await evaluate(`(()=>{const e=document.querySelector('#text input');e.focus();e.value=${JSON.stringify(text)};e.dispatchEvent(new Event('input',{bubbles:true}))})()`);await evaluate('scriptTest.settle()')}
async function commitText(){await evaluate(`(()=>{const e=document.querySelector('#text input');e.dispatchEvent(new Event('change',{bubbles:true}));e.blur()})()`);await evaluate('scriptTest.settle()')}
try{
 server=await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3398,strictPort:true}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9398',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9398/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(Error(JSON.stringify(data.error))):p.resolve(data.result)}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Page.enable');await send('Emulation.setFocusEmulationEnabled',{enabled:true});await send('Emulation.setDeviceMetricsOverride',{width:1280,height:950,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3398/${path.basename(fixture)}/index.html`})
 for(let i=0;i<200;i++){if(await evaluate('Boolean(window.scriptTest)'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.scriptTest)'),true,errors.join('\n'))
 await inputText(' abc ')
 assert.equal(await evaluate(`scriptTest.state.trace.filter(t=>t[0]==='chain').length`),0,'input 不调用事件链')
 await commitText()
 const trace=await evaluate('JSON.parse(JSON.stringify(scriptTest.state.trace))')
 assert.equal(trace.filter(t=>t[0]==='script').length,1)
 assert.equal(trace.filter(t=>t[0]==='chain').length,1)
 assert.ok(trace.some(t=>t[0]==='focus')&&trace.some(t=>t[0]==='blur'), JSON.stringify(trace))
 assert.ok(trace.findIndex(t=>t[0]==='script')<trace.findIndex(t=>t[0]==='chain'))
 assert.equal(trace.find(t=>t[0]==='chain')[2].input.value,'ABC')
 assert.equal(await evaluate('scriptTest.state.record.target'),'ABC')
 // 点击真实实体选择器并选择记录，模型仍是 ID，脚本收到完整 selection。
 await evaluate(`document.querySelector('#user .selector-input').click()`);await evaluate('scriptTest.settle()');await clickText('选择')
 assert.equal(await evaluate('scriptTest.state.record.user'),'u1')
 assert.equal(await evaluate('scriptTest.state.record.target'),'测试用户')
 assert.equal(await evaluate(`scriptTest.state.trace.find(t=>t[0]==='user')[1]`),'u1')
 assert.equal(await evaluate(`scriptTest.state.trace.filter(t=>t[0]==='chain'&&t[1]==='ENTITY_SELECTED').length`),1)
 await evaluate(`document.querySelector('#user .clear-icon').click()`);await evaluate('scriptTest.settle()')
 assert.equal(await evaluate('scriptTest.state.record.user'),null)
 assert.equal(await evaluate('scriptTest.state.record.target'),'已清空')
 // 脚本异步失败有具体提示，并继续原事件链。
 await evaluate(`scriptTest.state.useAccessor=true;scriptTest.state.text.componentProps.events.onChange="await Promise.reject(Error('故意失败'))";scriptTest.state.trace.length=0`)
 await inputText('error');await commitText()
 assert.match(await evaluate(`document.body.textContent`),/测试文本.*onChange.*故意失败/)
 assert.equal(await evaluate(`scriptTest.state.trace.filter(t=>t[0]==='chain').length`),1)
 // 真实节点树表单：赋值通过表单模型同步，尚未初始化的目标字段也能按元数据回填。
 await evaluate(`(()=>{const e=document.querySelector('#node-preview input');e.value='node';e.dispatchEvent(new Event('input',{bubbles:true}))})()`);await evaluate('scriptTest.settle()')
 await evaluate(`document.querySelector('#node-preview input').dispatchEvent(new Event('change',{bubbles:true}))`);await evaluate('scriptTest.settle()')
 assert.equal(await evaluate('scriptTest.state.nodeRecord.nodeText'),'NODE')
 assert.equal(await evaluate('scriptTest.state.nodeRecord.nodeTarget'),'节点回填')
 assert.equal(await evaluate(`scriptTest.state.trace.find(t=>t[0]==='chain'&&t[2].configId==='nodeForm')[2].input.form.nodeTarget`),'节点回填')
 // 编辑器拦截语法错误，修正后保存。
 await evaluate('scriptTest.state.visible=true;scriptTest.settle()');await clickText('保存')
 assert.equal(await evaluate('scriptTest.state.saved'),null)
 assert.match(await evaluate('document.body.textContent'),/onChange 脚本语法错误/)
 await evaluate(`scriptTest.state.codes={onChange:"await Promise.resolve(); setValue(value)"};scriptTest.settle()`);await clickText('保存')
 assert.equal(await evaluate('scriptTest.state.saved.onChange'),'await Promise.resolve(); setValue(value)')
 assert.equal(await evaluate('scriptTest.state.visible'),false)
 // 再次打开能读回保存值；不支持的事件显示原因、不可编辑。
 await evaluate(`scriptTest.state.codes=scriptTest.state.saved;scriptTest.state.text.componentType='reference';scriptTest.state.visible=true;scriptTest.settle()`)
 await clickText('onInput','.el-tabs__item')
 assert.match(await evaluate('document.body.textContent'),/当前组件未接入此事件/)
 assert.equal(await evaluate(`([...document.querySelectorAll('.cm-content')].filter(${visible}))[0].getAttribute('contenteditable')`),'false')
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-field-script-editor.png',Buffer.from(screenshot.data,'base64'))
 await clickText('取消')
 // Embed 路径在进入动态编译前被拦截，普通事件链仍然可以执行。
 await evaluate(`scriptTest.state.text.componentType='input';scriptTest.state.text.componentProps.events={onChange:"window.embedScriptRan=true"};history.replaceState({},'', '/embed/v1/launches/test1');scriptTest.settle()`)
 await inputText('embed');await commitText()
 assert.equal(await evaluate('Boolean(window.embedScriptRan)'),false)
 assert.match(await evaluate('document.body.textContent'),/嵌入页面不支持前端脚本事件/)
 assert.deepEqual(errors,[])
 console.log('field script browser acceptance passed: real text/entity, ID/selection, helpers, async chain ordering, one change per commit, errors, editor syntax/save/reopen, disabled events, Embed guard')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
