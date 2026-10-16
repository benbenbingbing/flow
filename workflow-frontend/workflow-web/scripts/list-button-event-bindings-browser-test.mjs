import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 真实事件弹窗配合内存 API，验证公共绑定不会占用具体按钮；不写入业务数据。
const harness = `
import { createApp, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import Dialog from '/src/components/ui-config/EventBindingDialog.vue'
import { listButtonEventOptions } from '/src/components/ui-config/listButtonEventTargets.js'
import { uiEventBindingApi, uiExtensionApi } from '/src/api/uiConfig.js'
const copy = value => JSON.parse(JSON.stringify(value))
const events = ['LIST_LOAD','ROW_BUTTON_CLICK','TOOLBAR_BUTTON_CLICK']
const button = (key,label) => ({key,label,type:'custom',customMode:'event'})
const binding = (eventCode, targetType='OWNER', targetKey='') => ({id:eventCode+targetKey,revision:1,ownerType:'LIST',ownerId:'list1',eventCode,targetType,targetKey,inheritanceMode:'INHERIT',enabled:true,steps:[]})
const state = reactive({
 changes:0,writes:[],
 toolbar:[button('shared','批量审批'),button('export','导出业务数据')],
 row:[button('shared','审批'),button('archive','归档')],
 rows:[...events.map(event=>binding(event)),binding('ROW_BUTTON_CLICK','BUTTON','shared')]
})
uiEventBindingApi.catalog=async()=>({events})
uiEventBindingApi.list=async()=>copy(state.rows)
uiEventBindingApi.create=async payload=>{
 if(state.rows.some(r=>r.targetType===payload.targetType&&r.targetKey===payload.targetKey&&r.eventCode===payload.eventCode))throw Error('duplicate binding')
 const row={...copy(payload),id:'new'+state.writes.length,revision:1};state.rows.push(row);state.writes.push(copy(row));return copy(row)
}
uiEventBindingApi.update=async(id,payload)=>{
 const index=state.rows.findIndex(r=>r.id===id)
 if(state.rows[index].revision!==payload.expectedRevision)throw Error('revision conflict')
 const row={...copy(payload),id,revision:payload.expectedRevision+1};state.rows[index]=row;state.writes.push(copy(row));return copy(row)
}
uiEventBindingApi.remove=async(id,revision)=>{
 const row=state.rows.find(r=>r.id===id);if(row.revision!==revision)throw Error('revision conflict');state.rows=state.rows.filter(r=>r.id!==id)
}
uiExtensionApi.availableInterfaces=async()=>[{extensionId:'test-interface',displayName:'验收接口',interfaceContextType:'LIST',interfaceKind:'READ',enabled:true}]
const dialog=ref()
const app=createApp({setup(){return()=>h(Dialog,{ref:dialog,ownerType:'LIST',ownerId:'list1',ownerLabel:'列表',buttonOptions:listButtonEventOptions(state.toolbar,state.row),onChanged:()=>state.changes++})}})
app.use(createPinia()).use(ElementPlus,{locale:zhCn}).mount('#app')
window.eventTest={state,dialog,clearMessages:()=>ElMessage.closeAll(),settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,350));await nextTick()}}
dialog.value.openOwner()
`
const fixture = mkdtempSync(path.resolve('.list-button-event-fixture-'))
writeFileSync(path.join(fixture,'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'), harness)
const profile=mkdtempSync(path.join(tmpdir(),'list-button-event-chrome-'))
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
 server=await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{include:['axios'],entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3398,strictPort:true}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9398',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9398/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(Error(JSON.stringify(data.error))):p.resolve(data.result)}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Page.enable');await send('Emulation.setDeviceMetricsOverride',{width:1280,height:900,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3398/${path.basename(fixture)}/index.html`})
 for(let i=0;i<200;i++){if(await evaluate('Boolean(window.eventTest)'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.eventTest)'),true,errors.join('\n'));await evaluate('eventTest.settle()')

 const lastDialog=`([...document.querySelectorAll('.el-dialog')].filter(${visible})).at(-1)`
 const tableText=()=>evaluate(`document.querySelector('.event-binding-editor .el-table').textContent`)
 const createDisabled=()=>evaluate(`([...document.querySelectorAll('.binding-toolbar button')].find(e=>e.textContent.trim()==='新增绑定')).disabled`)
 async function selectEvent(code) {
  await openEventOptions()
  const items=await options();assert.equal(items.find(i=>i.text.includes(code)).disabled,false,code+' 应允许其他按钮继续新增')
  await evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).find(e=>e.textContent.includes(${JSON.stringify(code)})).click()`)
  await evaluate('eventTest.settle()')
 }
 async function openTargets(){
  await evaluate(`${lastDialog}.querySelectorAll('.el-select__wrapper')[1].click()`)
  await evaluate('eventTest.settle()')
 }
 async function closeOptions(){
  await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'})
  await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape'})
  await evaluate('eventTest.settle()')
 }
 async function editBinding(label,action='编辑') {
  await evaluate(`(()=>{const row=[...document.querySelectorAll('.event-binding-editor .el-table__body tr')].find(e=>e.textContent.includes(${JSON.stringify(label)}));if(!row)throw Error('missing binding');[...row.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(action)}).click()})()`)
  await evaluate('eventTest.settle()')
 }
 assert.match(await tableText(),/所有操作列按钮（公共默认）/)
 assert.match(await tableText(),/操作列按钮：审批 \(shared\)/)
 assert.equal(await createDisabled(),false,'公共事件全部配置后仍可新增按钮绑定')
 // 已有公共链与“审批”按钮，仍能新建“归档”，且已占用目标独立置灰。
 await clickText('新增绑定');await selectEvent('ROW_BUTTON_CLICK');await openTargets()
 let items=await options()
 assert.equal(items.find(i=>i.text.includes('(shared)')).disabled,true)
 assert.equal(items.find(i=>i.text.includes('(archive)')).disabled,false)
 assert.equal(items.find(i=>i.text.includes('公共默认')).disabled,true)
 assert.equal(items.some(i=>i.text.includes('批量审批')),false,'行事件不得选择工具栏按钮')
 await closeOptions()
 await clickText('增加步骤')
 // 接口选择器通过提示文本定位，避免执行阶段选择器变化影响测试。
 await evaluate(`([...${lastDialog}.querySelectorAll('.el-select')]).find(e=>e.textContent.includes('留空表示只做字段映射')).querySelector('.el-select__wrapper').click()`)
 await evaluate('eventTest.settle()')
 await evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).find(e=>e.textContent.includes('验收接口')).click()`)
 await evaluate('eventTest.settle()')
 await clickText('保存绑定')
 let writes=await evaluate('JSON.parse(JSON.stringify(eventTest.state.writes))')
 assert.equal(writes[0].targetType,'BUTTON',JSON.stringify(writes));assert.equal(writes[0].targetKey,'archive')
 assert.equal(writes[0].steps[0].extensionId,'test-interface')
 assert.match(await tableText(),/操作列按钮：归档 \(archive\)/)
 // 行、工具栏允许同编码；创建完成后继续创建第二个工具栏按钮。
 await clickText('新增绑定');await selectEvent('TOOLBAR_BUTTON_CLICK');await clickText('保存绑定')
 await clickText('新增绑定');await clickText('保存绑定')
 writes=await evaluate('JSON.parse(JSON.stringify(eventTest.state.writes))')
 assert.deepEqual(writes.slice(1).map(r=>[r.eventCode,r.targetType,r.targetKey]),[
  ['TOOLBAR_BUTTON_CLICK','BUTTON','shared'],['TOOLBAR_BUTTON_CLICK','BUTTON','export']
 ])
 assert.equal(await createDisabled(),true,'所有目标配置后禁止重复新建')
 assert.equal(await evaluate('eventTest.state.changes'),3,'独立按钮保存应通知列表刷新发布差异')
 assert.equal(await evaluate('eventTest.state.rows.filter(r=>r.targetType==="OWNER").length'),3,'公共绑定必须原样保留')
 // 编辑固定身份并提交原 revision；删除后重新释放当前按钮名额。
 await editBinding('操作列按钮：归档 (archive)')
 assert.equal(await evaluate(`${lastDialog}.querySelectorAll('.el-select__wrapper')[0].classList.contains('is-disabled')`),true)
 assert.equal(await evaluate(`${lastDialog}.querySelectorAll('.el-select__wrapper')[1].classList.contains('is-disabled')`),true)
 await clickText('保存绑定')
 assert.equal(await evaluate('eventTest.state.writes.at(-1).expectedRevision'),1)
 assert.equal(await evaluate('eventTest.state.writes.at(-1).targetKey'),'archive')
 await editBinding('操作列按钮：归档 (archive)','删除');await clickText('确定')
 assert.equal(await createDisabled(),false)
 await clickText('新增绑定');await clickText('保存绑定')
 assert.equal(await evaluate('eventTest.state.writes.at(-1).targetKey'),'archive')
 // 饱和后新增第三个按钮应即时开放同一事件，不能只按事件编码判断。
 await evaluate(`eventTest.state.row.push({key:'reopen',label:'重新打开',type:'custom',customMode:'event'});eventTest.settle()`)
 assert.equal(await createDisabled(),false)
 await clickText('新增绑定');await clickText('保存绑定')
 assert.equal(await evaluate('eventTest.state.writes.at(-1).targetKey'),'reopen')
 // 不存在于按钮目录的历史绑定仍可准确编辑，不能把它转换成 OWNER。
 await evaluate(`eventTest.state.row=eventTest.state.row.filter(b=>b.key!=='archive');eventTest.settle()`)
 await editBinding('按钮：archive');await clickText('保存绑定')
 assert.equal(await evaluate('eventTest.state.writes.at(-1).targetType'),'BUTTON')
 assert.equal(await evaluate('eventTest.state.writes.at(-1).targetKey'),'archive')
 // 直接从单按钮入口打开时仍只管理该目标，不能串入公共链或其他按钮。
 await evaluate(`eventTest.dialog.value.openButton({key:'shared',label:'审批'});eventTest.settle()`)
 assert.doesNotMatch(await tableText(),/公共默认|归档|导出业务数据/)
 assert.equal(await createDisabled(),true,'该编码两种点击事件均已绑定')
 await evaluate(`eventTest.dialog.value.openOwner();eventTest.settle()`)
 await evaluate('eventTest.clearMessages();eventTest.settle()')
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-list-button-event-bindings.png',Buffer.from(screenshot.data,'base64'))
 // 公共默认是显式可选目标；空编码必须按 OWNER 保存，不能继承上一次的按钮编码。
 await editBinding('所有操作列按钮（公共默认）','删除');await clickText('确定')
 await evaluate(`eventTest.state.row.push({key:'notify',label:'通知',type:'custom',customMode:'event'});eventTest.settle()`)
 await clickText('新增绑定');await openTargets()
 await evaluate('eventTest.clearMessages();eventTest.settle()')
 const targetScreenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-list-button-event-targets.png',Buffer.from(targetScreenshot.data,'base64'))
 await evaluate(`([...document.querySelectorAll('.el-select-dropdown__item')].filter(${visible})).find(e=>e.textContent.includes('公共默认')).click()`)
 await evaluate('eventTest.settle()');await clickText('保存绑定')
 assert.equal(await evaluate('eventTest.state.writes.at(-1).targetType'),'OWNER')
 assert.equal(await evaluate('eventTest.state.writes.at(-1).targetKey'),'')
 assert.deepEqual(errors,[])
 console.log('list button event browser acceptance passed: multiple row/toolbar targets, public defaults, duplicate prevention, immutable editing identity, delete/recreate, dynamic buttons, orphan bindings, exact-button entry')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
