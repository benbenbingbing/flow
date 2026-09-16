import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 在隔离页面中使用真实 Vue/Element Plus 编辑器；API 仅替换为内存草稿，不触碰业务配置。
const harness = `
import { createApp, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import Panel from '/src/components/form-designer/FormNodeEventBindings.vue'
import SettingsSection from '/src/components/SettingsSection.vue'
import { uiEventBindingApi, uiExtensionApi } from '/src/api/uiConfig.js'
import { registerFormFieldComponent } from '/src/components/form-fields/index.js'
const copy = value => JSON.parse(JSON.stringify(value))
const events = ['FIELD_CHANGE','ENTITY_SELECTED','FIELD_BUTTON_CLICK','SUBFORM_LOAD','SUBFORM_SAVE']
const field = {id:'customer-id',revision:1,nodeType:'FIELD',fieldId:'customer-id',fieldCode:'customer',fieldName:'单选用户',fieldType:'REFERENCE',componentType:'reference',refEntityType:'USER'}
const fields = [field,{id:'name-id',fieldId:'name-id',fieldCode:'name',fieldName:'数据名称',fieldType:'STRING'}]
const state = reactive({field, formId:'form1', changes:0, rows:[], writes:[], reads:0})
uiEventBindingApi.catalog=async()=>({events})
uiEventBindingApi.list=async(owner,id)=>{state.reads++;const rows=copy(state.rows.filter(r=>r.ownerId===id));await new Promise(r=>setTimeout(r,id==='slow'?350:10));return rows}
uiEventBindingApi.create=async payload=>{if(state.rows.some(r=>r.ownerId===payload.ownerId&&r.targetKey===payload.targetKey&&r.eventCode===payload.eventCode))throw Error('duplicate binding');const row={...copy(payload),id:'binding'+state.writes.length,revision:1};state.rows.push(row);state.writes.push(copy(row));return copy(row)}
uiEventBindingApi.update=async(id,payload)=>{const index=state.rows.findIndex(r=>r.id===id);if(state.rows[index].revision!==payload.expectedRevision)throw Error('revision conflict');const row={...copy(payload),id,revision:payload.expectedRevision+1};state.rows[index]=row;state.writes.push(copy(row));return copy(row)}
uiEventBindingApi.remove=async(id,revision)=>{const row=state.rows.find(r=>r.id===id);if(row.revision!==revision)throw Error('revision conflict');state.rows=state.rows.filter(r=>r.id!==id)}
uiExtensionApi.availableInterfaces=async()=>[{id:'extra-interface',extensionId:'extra-interface',extensionType:'INTERFACE',displayName:'扩展接口',interfaceContextType:'FORM',interfaceKind:'READ',enabled:true}]
registerFormFieldComponent('test_score',{render:()=>h('div')},{capabilities:{supportedEvents:['FIELD_BUTTON_CLICK']}})
const app=createApp({setup(){return()=>h('main',{id:'panel',style:'width:900px;margin:30px auto'},[
 h(SettingsSection,{title:'事件与回填',defaultExpanded:true},{default:()=>h(Panel,{formId:state.formId,field:state.field,formFields:fields,fieldOptions:fields.map(f=>({label:f.fieldName,value:f.fieldCode})),onChanged:()=>state.changes++})})
])}})
app.config.warnHandler=message=>console.warn(message)
app.use(createPinia()).use(ElementPlus,{locale:zhCn}).mount('#app')
window.eventTest={state,field,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,400));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.field-event-fixture-'))
writeFileSync(path.join(fixture,'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'), harness)
const profile=mkdtempSync(path.join(tmpdir(),'field-event-chrome-'))
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
 await evaluate('eventTest.settle()')
}
async function options(){return evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).map(e=>({text:e.textContent.trim(),disabled:e.classList.contains('is-disabled')}))`)}
async function openEventOptions(){await evaluate(`([...document.querySelectorAll('.el-dialog')].filter(${visible})).at(-1).querySelector('.el-select__wrapper').click()`);await evaluate('eventTest.settle()')}
try{
 server=await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3397,strictPort:true}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9397',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9397/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(Error(JSON.stringify(data.error))):p.resolve(data.result)}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Page.enable');await send('Emulation.setDeviceMetricsOverride',{width:1280,height:900,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3397/${path.basename(fixture)}/index.html`})
 for(let i=0;i<200;i++){if(await evaluate('Boolean(window.eventTest)'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.eventTest)'),true,errors.join('\n'));await evaluate('eventTest.settle()')
 assert.equal(await evaluate('eventTest.state.reads'),1,'初次挂载只加载一次列表')
 // 单击新增直接进入编辑框，所有字段事件可见但仅两个可选。
 await clickText('新增绑定');await openEventOptions()
 let items=await options();assert.equal(items.length,5);assert.equal(items.filter(i=>!i.disabled).length,2)
 assert.match(items.find(i=>i.text.includes('FIELD_BUTTON_CLICK')).text,/没有独立/)
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'});await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape'});await clickText('取消')
 // 快捷回填创建 ENTITY_SELECTED，并自动关闭、刷新内嵌列表。
 await clickText('配置快捷回填');await clickText('添加映射')
 await evaluate(`document.querySelector('.selection-mapping-editor .el-select__wrapper').click()`);await evaluate('eventTest.settle()');await clickText('数据名称 (name)','.el-select-dropdown__item')
 await evaluate(`document.querySelectorAll('.selection-mapping-editor .el-select__wrapper')[1].click()`);await evaluate('eventTest.settle()');await clickText('数据名称 (name)','.el-select-dropdown__item')
 await clickText('保存回填配置')
 assert.equal(await evaluate('eventTest.state.rows.length'),1)
 assert.equal(await evaluate('eventTest.state.rows[0].eventCode'),'ENTITY_SELECTED')
 assert.equal(await evaluate(`([...document.querySelectorAll('.el-dialog')].filter(${visible})).length`),0)
 assert.match(await evaluate(`document.querySelector('#panel .el-table').textContent`),/选择实体后/)
 assert.equal(await evaluate('eventTest.state.changes'),1)
 // 通用新增禁用已有选择事件，但继续允许值变化。
 await clickText('新增绑定');await openEventOptions();items=await options()
 assert.equal(items.filter(i=>!i.disabled).length,1);assert.match(items.find(i=>i.text.includes('ENTITY_SELECTED')).text,/已配置/)
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'});await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape'});await clickText('保存绑定')
 assert.equal(await evaluate('eventTest.state.rows.length'),2)
 assert.equal(await evaluate(`([...document.querySelectorAll('#panel button')].find(e=>e.textContent.trim()==='新增绑定')).disabled`),true)
 // 快捷配置再次保存保持接口步骤和顺序，使用同一行 revision 更新。
 await evaluate(`eventTest.state.rows[0].steps.push({name:'接口补充信息',strategy:'AFTER',extensionId:'extra-interface',order:20,outputMapping:[]})`)
 await clickText('配置快捷回填');await clickText('保存回填配置')
 assert.equal(await evaluate('eventTest.state.rows.length'),2)
 assert.deepEqual(await evaluate('eventTest.state.rows[0].steps.map(s=>s.name)'),['选择后回填','接口补充信息'])
 assert.match(await evaluate(`document.querySelector('#panel .el-table').textContent`),/接口补充信息/)
 // 真实编辑保留原步骤，删除使用当前 revision。
 await clickText('编辑');await clickText('保存绑定')
 assert.equal(await evaluate('eventTest.state.rows[0].steps[1].extensionId'),'extra-interface')
 await clickText('删除');await clickText('确定')
 assert.equal(await evaluate('eventTest.state.rows.length'),1)
 // 普通文本只能值变化，快捷回填禁用；切换字段也关闭编辑框，防止串字段保存。
 await evaluate(`eventTest.state.field={id:'text-id',fieldCode:'text',nodeType:'FIELD',fieldType:'STRING',componentType:'input'};eventTest.settle()`)
 assert.equal(await evaluate(`([...document.querySelectorAll('#panel button')].find(e=>e.textContent.trim()==='配置快捷回填')).disabled`),true)
 await clickText('新增绑定');await openEventOptions();items=await options();assert.equal(items.filter(i=>!i.disabled).length,1)
 await evaluate(`eventTest.state.field={id:'score-id',fieldCode:'score',nodeType:'FIELD',fieldType:'INTEGER',componentType:'test_score'};eventTest.settle()`)
 assert.equal(await evaluate(`([...document.querySelectorAll('.el-dialog')].filter(${visible})).length`),0)
 await clickText('新增绑定');await openEventOptions();items=await options();assert.equal(items.find(i=>i.text.includes('FIELD_BUTTON_CLICK')).disabled,false)
 await evaluate(`eventTest.state.field={id:'node_unsaved',fieldCode:'newField',nodeType:'FIELD',fieldType:'STRING'};eventTest.settle()`)
 assert.equal(await evaluate(`([...document.querySelectorAll('#panel button')].find(e=>e.textContent.trim()==='新增绑定')).disabled`),true)
 assert.match(await evaluate(`document.querySelector('#panel').textContent`),/请先保存当前字段/)
 // 上一个表单的请求晚于新表单返回，不应覆盖当前列表。
 await evaluate(`eventTest.state.field=eventTest.field;eventTest.state.rows.push({ownerId:'slow',targetType:'FIELD',targetKey:'customer',id:'stale',eventCode:'SUBFORM_LOAD',steps:[]});eventTest.state.formId='slow';new Promise(r=>setTimeout(r,30))`)
 await evaluate(`eventTest.state.formId='fast';eventTest.settle()`)
 assert.ok(!(await evaluate(`document.querySelector('#panel .el-table').textContent`)).includes('加载子表'))
 await evaluate(`eventTest.state.formId='form1';eventTest.settle()`)
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-field-event-panel.png',Buffer.from(screenshot.data,'base64'))
 assert.deepEqual(errors,[])
 console.log('field event browser acceptance passed: inline list, disabled event reasons, quick-fill/create/edit/delete, duplicate prevention, ordering, field switching, stale responses, unsaved guard')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
