import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 使用真实设计节点、预览、运行时和设置抽屉；数据仅存内存，不改业务配置。
const harness = `
import { createApp, h, reactive, ref, computed, provide, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElForm } from 'element-plus'
import 'element-plus/dist/index.css'
import Preview from '/src/components/FormPreview.vue'
import Runtime from '/src/components/FormPreviewLinkage.vue'
import DesignItem from '/src/components/FormNodeDesignItem.vue'
import DraggableList from '/src/components/FormNodeDraggableList.vue'
import Settings from '/src/components/form-designer/FormDesignerSettingsDrawer.vue'
import RelatedContentPanel from '/src/components/related-content/RelatedContentPanel.vue'
import { FORM_DESIGNER_CONTEXT_KEY } from '/src/components/form-designer/context.js'
import { resolveFormLabelPosition, resolveFormLabelWidth } from '/src/shared/form-layout.js'
import { resolveFormNodeLayoutSpan } from '/src/shared/form-node-property-schema.js'
import { emptyFormActionBar } from '/src/shared/form-actions.js'
import { uiEventBindingApi, uiExtensionApi } from '/src/api/uiConfig.js'
import { uiCompositionApi } from '/src/api/uiComposition.js'
import request from '/src/utils/request.js'
// 关联内容弹窗也只访问内存适配器，保证验收不会读写当前业务库。
request.defaults.adapter=async config=>({data:{code:200,data:[]},status:200,statusText:'OK',headers:{},config})
let compositions=[{id:'related-1',compositionKey:'req',revision:1,config:{name:'关联需求',target:{contentType:'FORM',contentName:'需求表单'},presentation:{position:'INLINE'}}}]
uiCompositionApi.list=async()=>compositions
uiCompositionApi.remove=async()=>{compositions=[];return {ownerRevision:2}}
uiEventBindingApi.list=async()=>[]
uiEventBindingApi.catalog=async()=>({events:[]})
uiExtensionApi.availableInterfaces=async()=>[]
const nodes=[8,16,24].map((gridSpan,i)=>({id:'n'+i,nodeKey:'f'+i,nodeType:'FIELD',bindingType:'FIELD',bindingRef:'f'+i,props:{fieldCode:'f'+i,label:'字段 '+i,componentType:'input',gridSpan}}))
nodes.push({id:'grid',nodeKey:'grid',nodeType:'GRID',props:{defaultSpan:12,gutter:16}}, ...[8,16].map((gridSpan,i)=>({id:'g'+i,parentId:'grid',nodeKey:'gf'+i,nodeType:'FIELD',bindingType:'FIELD',bindingRef:'gf'+i,props:{fieldCode:'gf'+i,label:'嵌套字段 '+i,componentType:'input',gridSpan}})))
const fields=nodes.filter(n=>n.nodeType==='FIELD').map(n=>({id:n.bindingRef,fieldCode:n.props.fieldCode,fieldName:n.props.label,fieldType:'STRING',componentType:'input',gridSpan:n.props.gridSpan}))
const form=ref({id:'form-layout',formName:'栅格验收',formKey:'grid-test',status:1,isDefault:false,layoutType:'grid',nodes,fields,viewConfig:{labelWidth:150,actionBar:emptyFormActionBar()}})
const viewConfig=computed({get:()=>form.value.viewConfig,set:v=>form.value.viewConfig=v})
const state=reactive({form, drawer:false, activeTab:'basic',relatedCount:0,relatedChanges:0,data:{}, saved:null})
const relatedPanel=ref(null), standalonePanel=ref(null)
const designNodes=computed(()=>form.value.nodes.map(n=>({...n,...n.props,fieldLabel:n.props.label,componentProps:n.props})))
const pos=computed(()=>resolveFormLabelPosition(form.value)),width=computed(()=>resolveFormLabelWidth(form.value))
const noop=()=>{}
const app=createApp({setup(){
 provide(FORM_DESIGNER_CONTEXT_KEY,{form,viewConfig,isEdit:ref(true),isCustomRendererMode:ref(false),isSystemEntity:ref(false),customFormButtonCount:ref(0),entityInfo:ref({}),entityFields:ref(fields),formFields:ref(fields),formDataSourceBindingCount:ref(0),eventFieldOptions:ref([]),formActionPersistenceRevision:ref(0),persistedFormButtonKeys:ref([]),createActionSlotForButton:noop,openFormDataSourceConfig:noop,onEventBindingsChanged:noop})
 return()=>h('main',{style:'width:960px;margin:24px auto'},[
 h('h2','表单布局验收'),h('button',{id:'open',onClick:()=>state.drawer=true},'表单设置'),
 h('section',{id:'design'},[h('h3','设计画布'),h(ElForm,{labelPosition:pos.value,labelWidth:width.value},()=>h(DraggableList,{items:designNodes.value.filter(n=>!n.parentId),canDrop:()=>false,zoneClass:'root-design-drop-zone'},{item:({element:n,index:i})=>h(DesignItem,{node:n,siblingIndex:i,siblingCount:4,layoutType:form.value.layoutType,childrenFor:id=>designNodes.value.filter(n=>n.parentId===id),nodeSpanFor:(n,fallback)=>resolveFormNodeLayoutSpan(n,'grid',fallback),nodeStyleFor:n=>{const width=100*resolveFormNodeLayoutSpan(n,form.value.layoutType)/24+'%';return {width,flex:'0 0 '+width}},legacyNodeType:n=>n.nodeType,nodeLabel:id=>designNodes.value.find(n=>n.id===id)?.fieldLabel||'',canDropNode:()=>false})}))]),
 h('section',{id:'runtime'},[h('h3','发布运行时'),h(Runtime,{form:form.value,modelValue:state.data,showHeader:false})]),
 h('section',{id:'preview'},[h('h3','旧字段预览'),h(Preview,{form:{...form.value,fields:fields.slice(0,3)},showHeader:false})]),
 h(Settings,{modelValue:state.drawer,'onUpdate:modelValue':v=>state.drawer=v,activeTab:state.activeTab,'onUpdate:activeTab':v=>state.activeTab=v,relatedContentCount:state.relatedCount},{'related-content':()=>h(RelatedContentPanel,{ref:relatedPanel,embedded:true,ownerType:'FORM',ownerId:'form-layout',onCountChange:v=>state.relatedCount=v,onChanged:()=>state.relatedChanges++})}),
 h(RelatedContentPanel,{ref:standalonePanel,ownerType:'LIST',ownerId:'list-layout'})])
}})
app.use(createPinia()).use(ElementPlus).mount('#app')
window.layoutTest={state,standalonePanel,openRelated:async()=>{state.activeTab='related-content';state.drawer=true;await nextTick();if(!relatedPanel.value)throw Error('关联内容未随设置抽屉挂载');await relatedPanel.value.open()},settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,350));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.form-layout-fixture-'))
writeFileSync(path.join(fixture,'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'), harness)
const profile=mkdtempSync(path.join(tmpdir(),'form-layout-chrome-'))
let browser,ws,server,nextId=0
const pending=new Map(),errors=[]
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
async function send(method,params={}){
 const id=++nextId
 const result=new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout: '+method))},15000);pending.set(id,{resolve:r=>{clearTimeout(timer);resolve(r)},reject:e=>{clearTimeout(timer);reject(e)}})})
 ws.send(JSON.stringify({id,method,params}));return result
}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||JSON.stringify(r.exceptionDetails));return r.result.value}
try{
 server=await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3398,strictPort:true}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9398',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9398/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(Error(JSON.stringify(data.error))):p.resolve(data.result)}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Page.enable');await send('Emulation.setDeviceMetricsOverride',{width:1280,height:900,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3398/${path.basename(fixture)}/index.html`})
 for(let i=0;i<200;i++){if(await evaluate('Boolean(window.layoutTest)'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.layoutTest)'),true,errors.join('\n'));await evaluate('layoutTest.settle()')

 const visible='e=>e.getClientRects().length>0'
 async function set(expression){await evaluate(expression+';layoutTest.settle()')}
 async function open(){await set('layoutTest.state.drawer=true')}
 async function close(){await set('layoutTest.state.drawer=false')}
 async function select(label){await evaluate(`(()=>{const el=[...document.querySelectorAll('.el-segmented__item')].filter(${visible}).find(e=>e.textContent.trim()===${JSON.stringify(label)});if(!el)throw Error('Missing position '+${JSON.stringify(label)});el.click()})()`);await evaluate('layoutTest.settle()')}
 async function layout(){return evaluate(`['design','runtime','preview'].map(id=>{const root=document.getElementById(id),form=root.querySelector('.el-form'),label=root.querySelector('.el-form-item__label');return {id,classes:form.className,width:label.style.width,justify:getComputedStyle(label).justifyContent}})`)}
 async function columns(){return evaluate(`({runtime:[...document.querySelectorAll('#runtime .form-node-root-row > .el-col')].map(e=>Math.round(24*e.getBoundingClientRect().width/e.parentElement.getBoundingClientRect().width)),design:[...document.querySelectorAll('#design .el-form > div > .form-node-design-item')].map(e=>Math.round(24*e.getBoundingClientRect().width/e.parentElement.getBoundingClientRect().width)),nested:[...document.querySelectorAll('#runtime .node-grid > .el-col')].map(e=>Math.round(24*e.getBoundingClientRect().width/e.parentElement.getBoundingClientRect().width))})`)}
 // 除跨度外还检查实际行位置，避免描边或 gap 把合计 24 格的字段挤到下一行。
 async function sameRow(count=2){
  for(const selector of ['#design .root-design-drop-zone > .form-node-design-item','#runtime .form-node-root-row > .el-col']){
   const tops=await evaluate(`([...document.querySelectorAll(${JSON.stringify(selector)})].slice(0,${count})).map(e=>Math.round(e.getBoundingClientRect().top))`)
   assert.ok(tops.every(top=>top===tops[0]),selector+' 应在同一行: '+tops)
  }
 }
 await sameRow()
 for(const spans of [[12,12,24],[8,8,8]]){
  await set(`layoutTest.state.form.nodes.slice(0,3).forEach((n,i)=>n.props.gridSpan=${JSON.stringify(spans)}[i])`)
  await sameRow(spans[2]===8?3:2)
 }
 await set('layoutTest.state.form.nodes.slice(0,3).forEach((n,i)=>n.props.gridSpan=[8,16,24][i])')
 let spans=await columns();assert.deepEqual(spans.runtime,[8,16,24,24]);assert.deepEqual(spans.design,[8,16,24,24]);assert.deepEqual(spans.nested,[8,16])
 for(const row of await layout()){assert.match(row.classes,/label-right/);assert.equal(row.width,'150px')}
 const before=await evaluate('JSON.stringify(layoutTest.state.form)')
 await open();assert.equal(await evaluate('JSON.stringify(layoutTest.state.form)'),before,'打开设置不能修改历史/当前配置')
 await select('顶部');assert.equal(await evaluate('layoutTest.state.form.viewConfig.labelPosition'),'top')
 assert.equal(await evaluate(`document.querySelector('.form-settings-form .el-input-number input').disabled`),true)
 await close();for(const row of await layout())assert.match(row.classes,/label-top/)
 assert.deepEqual((await columns()).runtime,[8,16,24,24],'多列顶部标签不应改变字段宽度')
 await open();await select('左对齐')
 await evaluate(`(()=>{const input=document.querySelector('.form-settings-form .el-input-number input');input.value='200';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.blur()})()`)
 await evaluate('layoutTest.settle()');assert.equal(await evaluate('layoutTest.state.form.viewConfig.labelWidth'),200)
 await close();for(const row of await layout()){assert.match(row.classes,/label-left/);assert.equal(row.width,'200px')}
 // 模拟保存后的 JSON 重新载入；位置和宽度沿用同一 viewConfig，不丢其他配置。
 await set('layoutTest.state.saved=JSON.stringify(layoutTest.state.form);layoutTest.state.form=JSON.parse(layoutTest.state.saved)')
 for(const row of await layout()){assert.match(row.classes,/label-left/);assert.equal(row.width,'200px')}
 await open();await select('右对齐');await close();for(const row of await layout())assert.match(row.classes,/label-right/)
 for(const [type,span,label] of [['vertical',24,'top'],['horizontal',12,'right']]){
  await set(`layoutTest.state.form.layoutType=${JSON.stringify(type)};delete layoutTest.state.form.viewConfig.labelPosition`)
  spans=await columns();assert.deepEqual(spans.runtime,[span,span,span,24]);assert.deepEqual(spans.design,[span,span,span,24]);assert.deepEqual(spans.nested,[8,16],'显式 GRID 不受历史表单模式影响')
  for(const row of await layout())assert.ok(row.classes.includes('label-'+label))
  const old=await evaluate('JSON.stringify(layoutTest.state.form)');await open();await close();assert.equal(await evaluate('JSON.stringify(layoutTest.state.form)'),old)
 }
 await set(`layoutTest.state.form.layoutType='grid';layoutTest.state.form.viewConfig.labelPosition='left';layoutTest.state.form.viewConfig.labelWidth=150`)
 await send('Emulation.setDeviceMetricsOverride',{width:768,height:900,deviceScaleFactor:1,mobile:false})
 await evaluate(`document.querySelector('main').style.width='700px'`);await evaluate('layoutTest.settle()')
 assert.deepEqual((await columns()).runtime,[8,16,24,24]);await sameRow()
 await send('Emulation.setDeviceMetricsOverride',{width:1280,height:1100,deviceScaleFactor:1,mobile:false})
 await evaluate(`document.querySelector('main').style.width='960px'`);await evaluate('layoutTest.settle()')
 const screenshot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-grid-layout.png',Buffer.from(screenshot.data,'base64'))
 await open();const settingsImage=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-grid-settings.png',Buffer.from(settingsImage.data,'base64'))
 // 一级页签切换与关联面板首次挂载、关闭后重开，都必须保留原有编辑能力。
 const tabLabels=await evaluate(`[...document.querySelectorAll('.form-settings-tabs > .el-tabs__header [role="tab"]')].map(e=>e.textContent.trim().replace(/\\s*\\d+$/,''))`)
 assert.deepEqual(tabLabels,['基本与布局','按钮与操作','初始化数据','表单事件','输入参数','关联内容'])
 for(const name of ['data-source','events','input-parameters','related-content']){
  await evaluate(`document.querySelector('.form-settings-tabs #tab-${name}').click();layoutTest.settle()`)
  assert.equal(await evaluate('layoutTest.state.activeTab'),name)
  assert.equal(await evaluate(`document.querySelector('.form-settings-tabs #pane-${name}').getClientRects().length>0`),true)
 }
 assert.equal(await evaluate("document.querySelector('.related-content-panel.is-embedded').innerText.includes('关联需求')"),true)
 await close();await evaluate('layoutTest.openRelated()');await evaluate('layoutTest.settle()')
 assert.equal(await evaluate(`[...document.querySelectorAll('.el-drawer')].filter(${visible}).length`),1,'表单关联内容不应再打开第二层抽屉')
 const relatedImage=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-related-settings.png',Buffer.from(relatedImage.data,'base64'))
 async function clickButton(label,selector){await evaluate(`(()=>{const el=[...document.querySelectorAll(${JSON.stringify(selector)})].find(e=>e.getClientRects().length&&e.textContent.trim()===${JSON.stringify(label)});if(!el)throw Error('找不到按钮 '+${JSON.stringify(label)});el.click()})();layoutTest.settle()`)}
 await clickButton('编辑','.related-content-panel.is-embedded button')
 assert.equal(await evaluate(`document.querySelector('.related-content-config-dialog').innerText.includes('编辑关联内容')`),true)
 await clickButton('取消','.related-content-config-dialog button')
 await clickButton('删除','.related-content-panel.is-embedded button')
 await clickButton('确认删除','.el-message-box button')
 assert.equal(await evaluate('layoutTest.state.relatedCount'),0)
 assert.equal(await evaluate('layoutTest.state.relatedChanges'),1)
 await close();await evaluate('layoutTest.openRelated()');await evaluate('layoutTest.settle()')
 assert.equal(await evaluate("document.querySelector('.related-content-panel.is-embedded').innerText.includes('还没有关联内容')"),true)
 await close();await evaluate('layoutTest.standalonePanel.value.open()');await evaluate('layoutTest.settle()')
 assert.equal(await evaluate(`document.querySelector('.el-drawer.related-content-panel').innerText.includes('去操作列按钮设置')`),true,'列表继续使用原有独立关联抽屉')
 assert.deepEqual(errors,[])
 console.log('form layout browser acceptance passed: grid and labels, settings tab order, embedded related content edit/delete/reopen, standalone list drawer, JSON reload, narrow viewport')
}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
