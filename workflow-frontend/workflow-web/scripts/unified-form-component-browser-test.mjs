import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实设计器和节点树运行时；全部接口走内存适配器，不读取或改写业务配置。
const summary = JSON.parse(readFileSync('../extensions/manifests/biz-project/fields/ProjectAcceptanceSummaryNode.v1.extension.json', 'utf8'))
const password = JSON.parse(readFileSync('../extensions/manifests/platform/fields/password.v1.extension.json', 'utf8'))
const harness = `
import {createApp,h,reactive,nextTick} from 'vue'
import {createPinia} from 'pinia'
import {createRouter,createMemoryHistory,RouterView} from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Designer from '/src/views/EntityFormDesignByEntity.vue'
import Runtime from '/src/components/FormPreviewLinkage.vue'
import TextField from '/src/extensions/builtin/fields/components/TextField.vue'
import MobileField from '/@fs/${path.resolve('../packages/workflow-mobile-ui/src/fields/MobileFieldRenderer.vue')}'
import 'vant/lib/index.css'
import Summary from '@flow/biz-project/pc/fields/ProjectAcceptanceSummaryField'
import {registerFormFieldComponent} from '/src/extensions/core/registries/formFieldRegistry.js'
import request from '/src/utils/request.js'
import {installExtensionHost} from '/src/adapters/extensionHost.js'
const summary=${JSON.stringify(summary)}
const password=${JSON.stringify(password)}
registerFormFieldComponent('input',TextField,{origin:'PLATFORM',label:'文本输入',supportedFieldTypes:['STRING','TEXT']})
registerFormFieldComponent(password.name,TextField,{...password.metadata,origin:'PLATFORM',label:password.label,version:password.version})
registerFormFieldComponent(summary.name,Summary,{...summary.metadata,origin:'PROJECT',label:summary.label,version:summary.version})
const fields=['name','target','locked','hidden'].map((fieldCode,index)=>({id:fieldCode,fieldCode,fieldName:fieldCode,fieldType:'STRING',componentType:'input',isReadonly:fieldCode==='locked'?1:0,isHidden:fieldCode==='hidden'?1:0}))
const designerNode={id:'design-name',formId:'component-design',nodeType:'FIELD',nodeKey:'name',bindingType:'ENTITY_FIELD',bindingRef:'name',revision:1,orderKey:1000000,propsDocument:JSON.stringify({fieldId:'name',fieldCode:'name',fieldName:'客户名称',fieldType:'STRING',componentType:'input',label:'客户名称'})}
const designerForm={id:'component-design',entityId:'component-entity',formName:'统一组件配置验收',formKey:'component_design',revision:1,status:1,layoutType:'vertical',viewConfig:'{}'}
const state=reactive({data:{name:'初始值',target:'待回填',locked:'只读原值',hidden:'隐藏原值'},row:{name:'子表原值',target:'子表目标',locked:'子表只读'},readonly:false,seen:[],patches:[],events:[],api:[],saves:[],designerNodes:[designerNode]})
Object.assign(state,{passwordForm:null,passwordData:{name:''},passwordReadonly:false,mobilePassword:''})
const Probe={props:['modelValue','formData','node','field','config','disabled','readonly','context'],emits:['update:modelValue','change','patch-form'],setup(props,{emit}){
 const scalar=()=>{emit('update:modelValue','组件新值');emit('change','组件新值')}
 const patch=()=>emit('patch-form',{target:'跨字段新值',locked:'禁止修改',hidden:'禁止修改',unknown:'禁止新增',rootOnly:'禁止跨作用域'})
 return()=>h('div',{class:'probe'},[h('pre',{class:'probe-state'},JSON.stringify({value:props.modelValue,target:props.formData.target,node:props.node?.nodeKey,config:props.config,disabled:props.disabled,readonly:props.readonly})),h('button',{type:'button',class:'scalar',onClick:scalar},'修改当前字段'),h('button',{type:'button',class:'patch',onClick:patch},'回填多个字段')])
}}
registerFormFieldComponent('unified_probe',Probe,{origin:'PROJECT',label:'统一协议探针',nodeTypes:['FIELD'],supportedBindings:['ENTITY_FIELD']})
const node=(key,extra={})=>({id:key,nodeKey:key,nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:key,props:{fieldId:key,fieldCode:key,fieldType:'STRING',label:key,componentType:key==='name'?'unified_probe':'input',componentProps:key==='name'?{caption:'统一参数'}:{},...extra}})
const form={id:'runtime-form',formName:'统一组件运行时',fields,nodes:[node('name'),node('target'),node('locked',{readonly:true}),node('hidden',{hidden:true})]}
const rowForm={...form,id:'row-form',nodes:[node('rootOnly'),{id:'rows',nodeKey:'rows',nodeType:'REPEATER',bindingType:'RELATION',bindingRef:'rows',props:{componentType:'sub_form'}},...form.nodes.map(n=>({...n,parentId:'rows'}))]}
request.defaults.adapter=async config=>{
 const url=config.url,body=typeof config.data==='string'?JSON.parse(config.data):config.data
 state.api.push(url)
 let data=[]
 if(url==='/entity-form/component-design')data=designerForm
 else if(url==='/entity/component-entity')data={id:'component-entity',entityCode:'COMPONENT',entityName:'组件验收实体',fields}
 else if(url==='/entity-form/component-design/fields')data=[]
 else if(url==='/entity-forms/component-design/nodes')data=state.designerNodes
 else if(url==='/entity-forms/component-design/diff')data={changed:false,changedItems:[],changedSections:[],canDiscardDraft:false}
 else if(url==='/entity-form/component-design/patch')data={...designerForm,...body,revision:++designerForm.revision}
 else if(url==='/entity-forms/component-design/nodes/update'){state.saves.push(body);state.designerNodes=body;data=body;designerForm.revision++}
 else if(url.includes('/ui-runtime/events/')){state.events.push(body);data={success:true,results:[],patch:{}}}
 else if(url.endsWith('/catalog'))data={events:[]}
 else if(!url.includes('/ui-extensions')&&!url.includes('/ui-view-compositions')&&!url.includes('/ui-event-bindings')&&!url.includes('/entity-permission-options')&&!url.endsWith('/relations/available'))throw Error('Unexpected memory API: '+url)
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/designer/:id',component:Designer}]})
await router.push('/designer/component-design?entityId=component-entity');await router.isReady()
const app=createApp({setup(){return()=>h('main',{style:'width:1100px;margin:20px auto'},[
 h('section',{id:'designer',style:'height:650px;border:1px solid #ddd'},[h(RouterView)]),
 h('h2','统一协议运行时'),h('section',{id:'runtime'},[h(Runtime,{form,modelValue:state.data,'onUpdate:modelValue':v=>state.data=v,readonly:state.readonly,mode:'create',showHeader:false})]),
 h('h2','子表当前行'),h('section',{id:'row'},[h(Runtime,{form:rowForm,nodeRootParentId:'rows',modelValue:state.row,'onUpdate:modelValue':v=>state.row=v,readonly:state.readonly,mode:'create',showHeader:false})]),
 state.passwordForm&&h('section',{id:'password-runtime'},[h(Runtime,{form:state.passwordForm,modelValue:state.passwordData,'onUpdate:modelValue':v=>state.passwordData=v,readonly:state.passwordReadonly,mode:state.passwordReadonly?'view':'edit',showHeader:false})]),
 state.passwordForm&&h('section',{id:'mobile-password'},[h(MobileField,{field:{fieldCode:'name',fieldName:'密码',fieldType:'STRING',componentType:'password'},modelValue:state.mobilePassword,'onUpdate:modelValue':v=>state.mobilePassword=v,disabled:state.passwordReadonly})])
])}})
installExtensionHost(app)
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.componentTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,220));await nextTick()}}
`
const fixture=mkdtempSync(path.resolve('.unified-component-fixture-'))
writeFileSync(path.join(fixture,'index.html'),'<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'),harness)
const profile=mkdtempSync(path.join(tmpdir(),'unified-component-chrome-'))
let browser,ws,server,nextId=0
const pending=new Map(),errors=[],consoleErrors=[]
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms))
async function send(method,params={}){
 const id=++nextId
 const result=new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout: '+method))},15000);pending.set(id,{resolve:r=>{clearTimeout(timer);resolve(r)},reject:e=>{clearTimeout(timer);reject(e)}})})
 ws.send(JSON.stringify({id,method,params}));return result
}
async function evaluate(expression){const result=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||JSON.stringify(result.exceptionDetails));return result.result.value}
async function click(selector){await evaluate(`document.querySelector(${JSON.stringify(selector)}).click();componentTest.settle()`)}
const visible='e=>e.getClientRects().length>0&&!e.closest(\'[aria-hidden="true"]\')'
async function clickText(label,selector='button'){await evaluate(`(()=>{const el=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(${visible}).find(e=>e.textContent.trim()===${JSON.stringify(label)});if(!el)throw Error('Missing control '+${JSON.stringify(label)});el.click()})();componentTest.settle()`)}
try{
 server=await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')],include:['axios','async-validator']},server:{host:'127.0.0.1',port:3406,strictPort:true,hmr:false}});await server.listen()
 browser=spawn(process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9406',`--user-data-dir=${profile}`,'about:blank'],{stdio:['ignore','ignore','pipe']})
 browser.stderr.on('data',chunk=>{const message=String(chunk);if(/DevTools|Singleton|fatal/i.test(message))console.log('Chrome:',message.trim())})
 browser.on('error',error=>console.error('Chrome spawn failed',error));browser.on('exit',(code,signal)=>console.log('Chrome exit',code,signal))
 let targets
 for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9406/json/list')).json();break}catch{await sleep(100)}}
 assert.ok(targets?.length,'Chrome failed to start')
 ws=new WebSocket(targets.find(target=>target.type==='page').webSocketDebuggerUrl)
 ws.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const task=pending.get(data.id);pending.delete(data.id);data.error?task.reject(Error(JSON.stringify(data.error))):task.resolve(data.result)}if(data.method==='Runtime.exceptionThrown')errors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text);if(data.method==='Runtime.consoleAPICalled'&&data.params.type==='error')consoleErrors.push(data.params.args.map(arg=>arg.description||arg.value).join(' '))})
 await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
 await send('Runtime.enable');await send('Page.enable');await send('Emulation.setFocusEmulationEnabled',{enabled:true});await send('Emulation.setDeviceMetricsOverride',{width:1500,height:1250,deviceScaleFactor:1,mobile:false})
 await send('Page.navigate',{url:`http://127.0.0.1:3406/${path.basename(fixture)}/index.html`})
 for(let i=0;i<500;i++){if(await evaluate('Boolean(window.componentTest)&&Boolean(document.querySelector(\'[data-node-id="design-name"]\'))'))break;await sleep(100)}
 assert.equal(await evaluate('Boolean(window.componentTest)'),true,[...errors,...consoleErrors,await evaluate('JSON.stringify({url:location.href,state:document.readyState,text:document.body.innerText})')].join('\n'))
 await evaluate('componentTest.settle()')
 // 真正打开设计器节点属性，只允许一个选择入口，原 NODE 组件也在同一目录中。
 await evaluate('document.querySelector(\'[data-node-id="design-name"]\').dispatchEvent(new MouseEvent("dblclick",{bubbles:true}));componentTest.settle()')
 assert.equal(await evaluate(`[...document.querySelectorAll('.property-form .el-form-item__label')].filter(${visible}).filter(e=>e.textContent.trim()==='组件类型').length`),1)
 await evaluate(`(()=>{const item=[...document.querySelectorAll('.property-form .el-form-item')].find(e=>e.querySelector('.el-form-item__label')?.textContent.trim()==='组件类型');item.querySelector('.el-select__wrapper').click()})();componentTest.settle()`)
 await clickText('项目·验收摘要','.el-select-dropdown__item')
 assert.equal(await evaluate('document.querySelector(".property-form").textContent.includes("节点扩展")'),false)
 assert.equal(await evaluate('document.querySelector(".property-form").textContent.includes("复用与扩展")'),false)
 assert.equal(await evaluate(`document.querySelector('.property-form .config-schema-editor').getClientRects().length>0`),true)
 assert.equal(await evaluate(`document.querySelector('.property-form .config-schema-editor').textContent.includes('摘要标题')`),true)
 assert.equal(await evaluate(`(()=>{const section=document.querySelector('.property-form .config-schema-editor').closest('.settings-section');return section?.textContent.includes('组件类型')})()`),true)
 await evaluate(`(()=>{const input=document.querySelector('.property-form .config-schema-editor input');input.value='统一后的摘要';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))})();componentTest.settle()`)
 const image=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-unified-component-designer.png',Buffer.from(image.data,'base64'))
 await click('.el-drawer__close-btn')
 await clickText('保存全部')
 const saves=await evaluate('JSON.parse(JSON.stringify(componentTest.state.saves))')
 assert.ok(saves.length,'真实设计器保存应写入内存节点接口')
 const saved=saves.at(-1)[0],props=JSON.parse(saved.propsDocument)
 assert.equal(props.componentType,summary.name);assert.equal(props.componentProps.title,'统一后的摘要')
 assert.equal(Object.hasOwn(saved,'componentName'),false);assert.equal(Object.hasOwn(props,'componentExtensionType'),false)
 console.log('designer single-selector/schema/save checks passed')
 // 从真实组件下拉选择密码输入，再用保存的节点重建运行时，覆盖序列化与回显。
 await evaluate('document.querySelector(\'[data-node-id="design-name"]\').dispatchEvent(new MouseEvent("dblclick",{bubbles:true}));componentTest.settle()')
 await evaluate(`(()=>{const item=[...document.querySelectorAll('.property-form .el-form-item')].find(e=>e.querySelector('.el-form-item__label')?.textContent.trim()==='组件类型');item.querySelector('.el-select__wrapper').click()})();componentTest.settle()`)
 await clickText('密码输入','.el-select-dropdown__item')
 await click('.el-drawer__close-btn');await clickText('保存全部')
 const passwordNodes=await evaluate('JSON.parse(JSON.stringify(componentTest.state.saves.at(-1)))')
 assert.equal(JSON.parse(passwordNodes[0].propsDocument).componentType,'password')
 await evaluate(`componentTest.state.passwordForm={id:'password-form',fields:[{id:'name',fieldCode:'name',fieldType:'STRING',fieldLength:24}],nodes:${JSON.stringify(passwordNodes)}};componentTest.settle()`)
 assert.equal(await evaluate('document.querySelector("#password-runtime input").type'),'password')
 assert.equal(await evaluate('document.querySelector("#mobile-password input").type'),'password')
 await evaluate(`(()=>{for(const selector of ['#password-runtime input','#mobile-password input']){const input=document.querySelector(selector);input.value='Example-123';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))}})();componentTest.settle()`)
 assert.equal(await evaluate('componentTest.state.passwordData.name'),'Example-123')
 assert.equal(await evaluate('componentTest.state.mobilePassword'),'Example-123')
 await click('#password-runtime .el-input__password')
 assert.equal(await evaluate('document.querySelector("#password-runtime input").type'),'text')
 await click('#password-runtime .el-input__password')
 assert.equal(await evaluate('document.querySelector("#password-runtime input").type'),'password')
 await evaluate('componentTest.state.passwordReadonly=true;componentTest.settle()')
 assert.equal(await evaluate('document.querySelector("#password-runtime input").disabled'),true)
 assert.equal(await evaluate('document.querySelector("#password-runtime input").type'),'password')
 assert.equal(await evaluate('document.querySelector("#mobile-password .readonly-value").textContent'),'••••••••')
 assert.equal(await evaluate('componentTest.state.mobilePassword'),'Example-123','只读掩码不能覆盖原始值')
 console.log('password designer/save/runtime checks passed: PC/mobile input, reveal toggle, readonly masking')
 // 运行时同时传递字段值和整表数据；组件主动发起越权补丁也必须由宿主过滤。
 const probe=await evaluate('JSON.parse(document.querySelector("#runtime .probe-state").textContent)')
 assert.equal(probe.value,'初始值');assert.equal(probe.target,'待回填');assert.equal(probe.node,'name');assert.equal(probe.config.caption,'统一参数')
 await click('#runtime .scalar');assert.equal(await evaluate('componentTest.state.data.name'),'组件新值')
 await click('#runtime .patch')
 assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(componentTest.state.data))'),{name:'组件新值',target:'跨字段新值',locked:'只读原值',hidden:'隐藏原值'})
 await click('#row .patch')
 assert.equal(await evaluate('componentTest.state.row.target'),'跨字段新值')
 assert.equal(await evaluate('Object.hasOwn(componentTest.state.row,"rootOnly")'),false,'子表当前行不能回填根表字段')
 assert.equal(await evaluate('componentTest.state.data.name'),'组件新值')
 // 恶意控件没有主动禁用按钮，平台仍需阻止 scalar 和 patch 两种写入口。
 await evaluate('componentTest.state.readonly=true;componentTest.settle()')
 const before=await evaluate('JSON.stringify([componentTest.state.data,componentTest.state.row])')
 await click('#runtime .scalar');await click('#runtime .patch');await click('#row .scalar');await click('#row .patch')
 assert.equal(await evaluate('JSON.stringify([componentTest.state.data,componentTest.state.row])'),before)
 assert.equal(await evaluate('JSON.parse(document.querySelector("#runtime .probe-state").textContent).disabled'),true)
 assert.deepEqual(errors,[])
 const runtimeImage=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-unified-component-runtime.png',Buffer.from(runtimeImage.data,'base64'))
 console.log('unified component browser acceptance passed: real designer single selector/schema/save, scalar and formData props, multi-field patches, readonly/hidden/unknown target protection, child-row scope')
}catch(error){console.error('browser errors',errors,consoleErrors);if(ws?.readyState===WebSocket.OPEN){try{const shot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-unified-component-failure.png',Buffer.from(shot.data,'base64'));console.error(await evaluate('JSON.stringify({url:location.href,state:document.readyState,text:document.body.innerText})'))}catch{}}throw error}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
