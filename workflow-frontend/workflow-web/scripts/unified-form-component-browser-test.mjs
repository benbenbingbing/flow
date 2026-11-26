import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实设计器和节点树运行时；全部接口走内存适配器，不读取或改写业务配置。
const summary = JSON.parse(readFileSync('../custom/biz-project/manifests/fields/ProjectAcceptanceSummaryNode.v1.extension.json', 'utf8'))
const password = JSON.parse(readFileSync('../packages/workflow-extensions/manifests/platform/fields/password.v1.extension.json', 'utf8'))
const additionalComponents = ['data_picker', 'cascader', 'reference', 'multi_reference'].map(name =>
 JSON.parse(readFileSync(`../packages/workflow-extensions/manifests/platform/fields/${name}.v1.extension.json`, 'utf8')))
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
import MobileSummary from '@flow/biz-project/mobile/fields/ProjectAcceptanceSummaryField'
import {registerFormFieldComponent} from '/src/extensions/core/registries/formFieldRegistry.js'
import request from '/src/utils/request.js'
import {installExtensionHost} from '/src/adapters/extensionHost.js'
const summary=${JSON.stringify(summary)}
const password=${JSON.stringify(password)}
registerFormFieldComponent('input',TextField,{origin:'PLATFORM',label:'文本输入',supportedFieldTypes:['STRING','TEXT']})
registerFormFieldComponent(password.name,TextField,{...password.metadata,origin:'PLATFORM',label:password.label,version:password.version})
registerFormFieldComponent(summary.name,Summary,{...summary.metadata,origin:'PROJECT',label:summary.label,version:summary.version})
for (const item of ${JSON.stringify(additionalComponents)}) {
 registerFormFieldComponent(item.name,TextField,{...item.metadata,origin:'PLATFORM',label:item.label,version:item.version})
}
const fields=['name','target','locked','hidden'].map((fieldCode,index)=>({id:fieldCode,fieldCode,fieldName:fieldCode,fieldType:'STRING',componentType:'input',isReadonly:fieldCode==='locked'?1:0,isHidden:fieldCode==='hidden'?1:0}))
const designerNode={id:'design-name',formId:'component-design',nodeType:'FIELD',nodeKey:'name',bindingType:'ENTITY_FIELD',bindingRef:'name',revision:1,orderKey:1000000,propsDocument:JSON.stringify({fieldId:'name',fieldCode:'name',fieldName:'客户名称',fieldType:'STRING',componentType:'input',label:'客户名称'})}
const targetEntity={id:'catalog',entityCode:'CATALOG',entityName:'目录实体'}
const targetList={id:'catalog-list',entityCode:targetEntity.entityCode,listKey:'catalog_selection',listName:'目录选择'}
const targetViewConfig={
 inputParameterSchema:{properties:{category:{type:'string',title:'目录分类'}}},
 inputParameterBindings:[{usage:'FILTER',parameter:'category',targetField:'category'}]
}
const referenceFields=[
 {id:'single',fieldCode:'single',fieldName:'单选目录',fieldType:'REFERENCE',componentType:'reference'},
 {id:'multiple',fieldCode:'multiple',fieldName:'多选目录',fieldType:'MULTI_REFERENCE',componentType:'multi_reference'}
].map(field=>({...field,refEntityType:'CUSTOM',refEntityId:targetEntity.id}))
const referenceNodes=referenceFields.map((field,index)=>({
 ...designerNode,id:'design-'+field.id,nodeKey:field.fieldCode,bindingRef:field.fieldCode,orderKey:2000000+index*1000000,
 propsDocument:JSON.stringify({...field,fieldId:field.id,label:field.fieldName,componentProps:{
  refConfig:{refEntityType:field.refEntityType,refEntityId:field.refEntityId}
 }})
}))
designerNode.dataSourceBindingsDocument=JSON.stringify({FIELD_COMPUTE:{serviceId:'retired-compute',inputMapping:'{'}})
const oldFieldBinding={extensionId:'existing-load',inputMapping:{id:'data.single'},outputMapping:{single:'data.value'}}
referenceNodes[0].dataSourceBindingsDocument=JSON.stringify({AFTER_LOAD:oldFieldBinding})
const designerForm={id:'component-design',entityId:'component-entity',formName:'统一组件配置验收',formKey:'component_design',revision:1,status:1,layoutType:'vertical',viewConfig:'{}'}
const state=reactive({data:{name:'初始值',target:'待回填',locked:'只读原值',hidden:'隐藏原值'},row:{name:'子表原值',target:'子表目标',locked:'子表只读'},readonly:false,seen:[],patches:[],events:[],api:[],saves:[],designerNodes:[designerNode,...referenceNodes]})
Object.assign(state,{passwordForm:null,passwordData:{name:''},passwordReadonly:false,mobilePassword:''})
const Probe={props:['modelValue','formData','node','field','config','disabled','readonly','context'],emits:['update:modelValue','change','patch-form'],setup(props,{emit}){
 const scalar=()=>{emit('update:modelValue','组件新值');emit('change','组件新值')}
 const patch=()=>emit('patch-form',{target:'跨字段新值',locked:'只读字段回填值',hidden:'禁止修改',unknown:'禁止新增',rootOnly:'禁止跨作用域'})
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
 else if(url==='/entity/component-entity')data={id:'component-entity',entityCode:'COMPONENT',entityName:'组件验收实体',fields:[...fields,...referenceFields]}
 else if(url==='/entity/options/resolve')data=body.ids?.includes(targetEntity.id)?[targetEntity]:[]
 else if(url==='/entity-list-config/entity/catalog')data=[targetList]
 else if(url==='/entity-list-config/catalog-list/releases')data=[{status:'ACTIVE',snapshotDocument:{list:{...targetList,viewConfig:targetViewConfig}}}]
 else if(url==='/entity-form/component-design/fields')data=[]
 else if(url==='/entity-forms/component-design/nodes')data=state.designerNodes
 else if(url==='/entity-forms/component-design/diff')data={changed:false,changedItems:[],changedSections:[],canDiscardDraft:false}
 else if(url==='/entity-form/component-design/patch')data={...designerForm,...body,revision:++designerForm.revision}
 else if(url==='/entity-forms/component-design/nodes/update'){state.saves.push(body);state.designerNodes=body;data=body;designerForm.revision++}
 else if(url.startsWith('/entity-forms/component-design/nodes/')&&url.endsWith('/patch')){
  const id=url.split('/').at(-2)
  const index=state.designerNodes.findIndex(node=>node.id===id)
  const previous=state.designerNodes[index]
  const {props,rules,dataSourceBindings,...patch}=body
  data={...previous,...patch,revision:previous.revision+1,propsDocument:JSON.stringify(props),
   rulesDocument:JSON.stringify(rules||{}),dataSourceBindingsDocument:JSON.stringify(dataSourceBindings||{})}
  state.designerNodes[index]=data;designerForm.revision++
 }
 else if(url==='/ui-extensions/available-interfaces') {
  const usage=config.params?.bindingCode
  if(['FIELD_OPTIONS','FIELD_PICKER_QUERY','FIELD_PICKER_RESOLVE','FIELD_PICKER_SCHEMA'].includes(usage)) {
   data=[{extensionId:usage,extensionKey:usage,displayName:'测试接口'}]
  }
 }
 else if(url==='/ui-extensions/FIELD_PICKER_SCHEMA/preview') {
  const binding=JSON.parse(state.designerNodes[0].dataSourceBindingsDocument).FIELD_PICKER_SCHEMA
  if(binding?.extensionId!=='FIELD_PICKER_SCHEMA') throw Error('预览需要已保存的绑定')
  state.pickerPreview=body
  data={schemaVersion:'test-v1',valueField:'id',labelField:'name',fields:[{code:'id',type:'STRING'},{code:'name',type:'STRING'}],columns:[{field:'name'}]}
 }
 else if(url.includes('/ui-runtime/events/')){state.events.push(body);data={success:true,results:[],patch:{}}}
 else if(url.endsWith('/catalog'))data={events:[]}
 else if(!url.includes('/ui-extensions')&&!url.includes('/ui-view-compositions')&&!url.includes('/ui-event-bindings')&&!url.includes('/entity-permission-options')&&!url.endsWith('/relations/available'))throw Error('Unexpected memory API: '+url)
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/designer/:id',component:Designer}]})
await router.push('/designer/component-design?entityId=component-entity');await router.isReady()
const summaryProps={field:{fieldCode:'name'},config:{title:'当前行验收摘要'},formData:{name:'当前子表行',acceptance_score:0,extension_summary:'表单事件已回填'}}
const app=createApp({setup(){return()=>h('main',{style:'width:1100px;margin:20px auto'},[
 h('section',{id:'designer',style:'height:650px;border:1px solid #ddd'},[h(RouterView)]),
 h('h2','统一协议运行时'),h('section',{id:'runtime'},[h(Runtime,{form,modelValue:state.data,'onUpdate:modelValue':v=>state.data=v,readonly:state.readonly,mode:'create',showHeader:false})]),
 h('h2','子表当前行'),h('section',{id:'row'},[h(Runtime,{form:rowForm,nodeRootParentId:'rows',modelValue:state.row,'onUpdate:modelValue':v=>state.row=v,readonly:state.readonly,mode:'create',showHeader:false})]),
 state.passwordForm&&h('section',{id:'password-runtime'},[h(Runtime,{form:state.passwordForm,modelValue:state.passwordData,'onUpdate:modelValue':v=>state.passwordData=v,readonly:state.passwordReadonly,mode:state.passwordReadonly?'view':'edit',showHeader:false})]),
 state.passwordForm&&h('section',{id:'mobile-password'},[h(MobileField,{field:{fieldCode:'name',fieldName:'密码',fieldType:'STRING',componentType:'password'},modelValue:state.mobilePassword,'onUpdate:modelValue':v=>state.mobilePassword=v,disabled:state.passwordReadonly})]),
 h('section',{id:'pc-summary'},[h(Summary,summaryProps)]),
 h('section',{id:'mobile-summary'},[h(MobileSummary,summaryProps)])
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
const componentConfig = '.component-configuration'
const configFooter = '.el-dialog__footer button'
const openNode = (id = 'design-name') => evaluate(`document.querySelector(${JSON.stringify(`[data-node-id="${id}"]`)}).dispatchEvent(new MouseEvent("dblclick",{bubbles:true}));componentTest.settle()`)
const openConfig = () => clickText('组件配置', '.component-type-row button')
async function closeNode() {
 await click('.node-property-drawer .el-drawer__close-btn')
 for (let attempt = 0; attempt < 30; attempt++) {
  if (!await evaluate(`[...document.querySelectorAll('.node-property-drawer')].some(${visible})`)) return
  await sleep(50)
 }
 assert.fail('节点属性抽屉未关闭')
}
async function confirmConfig() {
 await clickText('确定', configFooter)
 for (let attempt = 0; attempt < 30; attempt++) {
  if (!await evaluate(`Boolean(document.querySelector('${componentConfig}'))`)) return
  await sleep(50)
 }
 assert.fail('组件配置未关闭')
}
async function selectSetting(label, option, root = '.property-form') {
 await evaluate(`(()=>{const labels=[...document.querySelectorAll(${JSON.stringify(root+' .el-form-item__label')})];const item=labels.find(e=>e.textContent.trim()===${JSON.stringify(label)})?.closest('.el-form-item');if(!item)throw Error('Missing setting '+${JSON.stringify(label)});item.querySelector('.el-select__wrapper').click()})();componentTest.settle()`)
 await clickText(option,'.el-select-dropdown__item')
}
function enterValue(selector,value) {
 return evaluate(`(()=>{const input=document.querySelector(${JSON.stringify(selector)});input.value=${JSON.stringify(value)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))})();componentTest.settle()`)
}
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
 await openNode()
 assert.equal(await evaluate(`[...document.querySelectorAll('.property-form .el-form-item__label')].filter(${visible}).filter(e=>e.textContent.trim()==='组件类型').length`),1)
 assert.equal(await evaluate('document.querySelector(".component-type-row button")'),null)
 await selectSetting('组件类型','项目·验收摘要')
 assert.equal(await evaluate('document.querySelector(".property-form").textContent.includes("节点扩展")'),false)
 assert.equal(await evaluate('document.querySelector(".property-form").textContent.includes("复用与扩展")'),false)
 assert.equal(await evaluate('document.querySelector(".property-form .config-schema-editor")'),null)
 await openConfig()
 assert.match(await evaluate(`document.querySelector('${componentConfig}').innerText`), /摘要标题/)
 await enterValue(`${componentConfig} input`, '将取消的摘要')
 await clickText('取消', configFooter)
 await openConfig()
 assert.equal(await evaluate(`document.querySelector('${componentConfig} input').value`), '扩展执行摘要')
 await enterValue(`${componentConfig} input`, '统一后的摘要')
 const image=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-unified-component-designer.png',Buffer.from(image.data,'base64'))
 await confirmConfig()
 await click('.el-drawer__close-btn')
 await clickText('保存全部')
 const saves=await evaluate('JSON.parse(JSON.stringify(componentTest.state.saves))')
 assert.ok(saves.length,'真实设计器保存应写入内存节点接口')
 const saved=saves.at(-1)[0],props=JSON.parse(saved.propsDocument)
 assert.equal(props.componentType,summary.name);assert.equal(props.componentProps.title,'统一后的摘要')
 assert.equal(Object.hasOwn(saved,'componentName'),false);assert.equal(Object.hasOwn(props,'componentExtensionType'),false)
 const savedBindings=saved.dataSourceBindings || JSON.parse(saved.dataSourceBindingsDocument || '{}')
 assert.equal(Object.hasOwn(savedBindings,'FIELD_COMPUTE'),false,'保存应丢弃退役的计算接口')
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

 // 级联与数据选择器独立暂存，原抽屉页签保留各自的通用配置。
 await openNode()
 await selectSetting('组件类型','级联选择')
 await openConfig()
 await selectSetting('数据来源','数据接口',componentConfig)
 await confirmConfig()
 assert.match(await evaluate('document.querySelector(".component-type-row").innerText'),/待配置/)
 await openConfig()
 await selectSetting('级联数据接口','测试接口 (FIELD_OPTIONS)',componentConfig)
 await click(`${componentConfig} details summary`)
 await enterValue(`${componentConfig} input[aria-label="下级节点"]`,'nodes')
 await confirmConfig()
 assert.doesNotMatch(await evaluate('document.querySelector(".component-type-row").innerText'),/待配置/)
 await clickText('保存当前属性','.node-property-actions button')
 const cascade = await evaluate('JSON.parse(JSON.stringify(componentTest.state.designerNodes[0]))')
 assert.equal(JSON.parse(cascade.propsDocument).componentProps.cascaderNodeFields.children,'nodes')
 assert.equal(JSON.parse(cascade.dataSourceBindingsDocument).FIELD_OPTIONS.extensionId,'FIELD_OPTIONS')

 await selectSetting('组件类型','数据选择器')
 await clickText('数据与扩展','[role="tab"]')
 assert.equal(await evaluate('document.querySelector(".node-data-settings .data-picker-settings")'),null)
 await clickText('基础与布局','[role="tab"]')
 await openConfig()
 const pickerContents = await evaluate(`document.querySelector('${componentConfig}').innerText`)
 for (const label of ['面板标题','分页查询接口','返回字段','选择后写入']) assert.ok(pickerContents.includes(label), label)
 await enterValue(`${componentConfig} .config-schema-editor input`, '客户选择面板')
 await selectSetting('分页查询接口','测试接口 (FIELD_PICKER_QUERY)',componentConfig)
 await selectSetting('按标识查询','测试接口 (FIELD_PICKER_RESOLVE)',componentConfig)
 await selectSetting('定义方式','通过字段描述接口获取',componentConfig)
 await selectSetting('字段描述接口','测试接口 (FIELD_PICKER_SCHEMA)',componentConfig)
 await confirmConfig()
 assert.match(await evaluate('document.querySelector(".component-type-row").innerText'),/待配置/)
 await clickText('保存当前属性','.node-property-actions button')
 await openConfig()
 await clickText('获取字段描述', `${componentConfig} button`)
 assert.equal(await evaluate('componentTest.state.pickerPreview.usage'),'FIELD_PICKER_SCHEMA')
 await confirmConfig()
 assert.doesNotMatch(await evaluate('document.querySelector(".component-type-row").innerText'),/待配置/)
 await clickText('保存当前属性','.node-property-actions button')
 const picker = await evaluate('JSON.parse(JSON.stringify(componentTest.state.designerNodes[0]))')
 assert.equal(JSON.parse(picker.propsDocument).componentProps.pickerTitle,'客户选择面板')
 assert.equal(JSON.parse(picker.propsDocument).componentProps.pickerSchema.labelField,'name')
 assert.equal(JSON.parse(picker.dataSourceBindingsDocument).FIELD_PICKER_QUERY.extensionId,'FIELD_PICKER_QUERY')
 await closeNode()
 console.log('component configuration modal checks passed: staged edits, cascade, picker bindings and field description')

 // 单选和多选共用已发布列表的参数契约，取消后保留旧值，确认后通过节点接口保存。
 for (const [id, title] of [['design-single', '实体记录单选'], ['design-multiple', '实体记录多选']]) {
  await openNode(id)
  await clickText('数据与扩展','[role="tab"]')
  assert.doesNotMatch(await evaluate('document.querySelector(".node-data-settings").innerText'),/引用选择配置|列表参数映射/)
  const usageLabels=await evaluate('[...document.querySelectorAll(".data-source-usage-row > span:first-child")].map(e=>e.textContent.trim())')
  assert.deepEqual(usageLabels,id==='design-single'?['加载后处理']:[])
  await clickText('基础与布局','[role="tab"]')
  await openConfig()
  assert.match(await evaluate(`document.querySelector('${componentConfig}').closest('[role="dialog"]').innerText`),new RegExp(title+'配置'))
  assert.match(await evaluate(`document.querySelector('${componentConfig}').innerText`),/目录实体/)
  assert.equal(await evaluate(`document.querySelector('${componentConfig} .el-select input').disabled`),true)
  await selectSetting('选择列表','目录选择 (catalog_selection)',componentConfig)
  await clickText('增加映射',`${componentConfig} button`)
  await click(`${componentConfig} .reference-list-row .el-select:nth-child(2) .el-select__wrapper`)
  await clickText('固定值','.el-select-dropdown__item')
  await enterValue(`${componentConfig} input[aria-label="固定值"]`,'内部')
  await confirmConfig()
  await openConfig()
  await enterValue(`${componentConfig} input[aria-label="固定值"]`,'将取消的分类')
  await clickText('取消',configFooter)
  await clickText('保存当前属性','.node-property-actions button')
  if(id==='design-single'){
   const binding=await evaluate('JSON.parse(componentTest.state.designerNodes.find(node=>node.id==="design-single").dataSourceBindingsDocument).AFTER_LOAD')
   assert.deepEqual(binding,{extensionId:'existing-load',inputMapping:{id:'data.single'},outputMapping:{single:'data.value'}})
  }
  const refConfig=await evaluate(`JSON.parse(componentTest.state.designerNodes.find(node=>node.id===${JSON.stringify(id)}).propsDocument).componentProps.refConfig`)
  assert.equal(refConfig.listKey,'catalog_selection')
  assert.deepEqual(refConfig.parameterMappings,[{parameter:'category',sourceType:'LITERAL',sourceField:'',value:'内部'}])
  await openConfig()
  assert.equal(await evaluate(`document.querySelector('${componentConfig} input[aria-label="固定值"]').value`),'内部')
  await clickText('取消',configFooter)
  await closeNode()
 }
 console.log('entity record configuration modal checks passed: single and multiple, cancel, parameter mapping save and reopen')

 // 三种值联动方式共用一个控件，切换时保留输入，保存时仅保留选中的规则。
 const linkage = '.node-value-linkage'
 const formulaInput = linkage + ' textarea[aria-label="计算公式"]'
 const sourceValueInput = linkage + ' input[placeholder="源字段的值"]'
 const targetValueInput = linkage + ' input[placeholder="当前字段的值"]'
 const chooseLinkageMode = label => clickText(label, linkage + ' .el-radio')
 const selectedLinkageMode = () => evaluate('document.querySelector(".node-value-linkage input[type=radio]:checked").value')
 const savedLinkage = () => evaluate('JSON.parse(componentTest.state.designerNodes[0].propsDocument).componentProps.linkageRules || {}')
 const openLinkage = async () => {
  await openNode()
  await clickText('联动与事件','[role="tab"]')
 }
 await openNode()
 await clickText('数据与扩展','[role="tab"]')
 assert.equal(await evaluate('document.querySelectorAll(".data-source-usage-row").length'),0)
 assert.match(await evaluate('document.querySelector(".node-data-settings").innerText'),/表单事件 → 字段默认事件/)
 await clickText('联动与事件','[role="tab"]')
 assert.equal(await selectedLinkageMode(),'none')
 assert.doesNotMatch(await evaluate('document.querySelector(".node-value-linkage").innerText'),/计算接口|源字段|启用公式计算|启用字段映射/)
 await chooseLinkageMode('字段映射')
 await selectSetting('源字段','target',linkage)
 await clickText('添加映射',linkage + ' button')
 await enterValue(sourceValueInput,'A')
 await enterValue(targetValueInput,'B')
 await chooseLinkageMode('公式计算')
 await enterValue(formulaInput,'1 + 2')
 await chooseLinkageMode('字段映射')
 assert.equal(await evaluate(`document.querySelector(${JSON.stringify(sourceValueInput)}).value`),'A')
 assert.equal(await evaluate(`document.querySelector(${JSON.stringify(targetValueInput)}).value`),'B')
 await clickText('保存当前属性','.node-property-actions button')
 assert.deepEqual((await savedLinkage()).valueMapping,{sourceField:'target',rules:[{sourceValue:'A',targetValue:'B'}]})
 assert.equal((await savedLinkage()).valueFormula,undefined)
 await closeNode()

 await openLinkage()
 assert.equal(await selectedLinkageMode(),'field')
 await chooseLinkageMode('公式计算')
 await enterValue(formulaInput,'1 + 2')
 await chooseLinkageMode('不启用')
 assert.equal(await evaluate(`Boolean(document.querySelector(${JSON.stringify(formulaInput)}))`),false)
 await chooseLinkageMode('公式计算')
 assert.equal(await evaluate(`document.querySelector(${JSON.stringify(formulaInput)}).value`),'1 + 2')
 await clickText('保存当前属性','.node-property-actions button')
 assert.equal((await savedLinkage()).valueFormula,'1 + 2')
 assert.equal((await savedLinkage()).valueMapping,undefined)
 await closeNode()

 await openLinkage()
 assert.equal(await selectedLinkageMode(),'formula')
 await chooseLinkageMode('不启用')
 await clickText('保存当前属性','.node-property-actions button')
 assert.equal((await savedLinkage()).valueMapping,undefined)
 assert.equal((await savedLinkage()).valueFormula,undefined)
 await closeNode()
 await openLinkage()
 assert.equal(await selectedLinkageMode(),'none')
 await closeNode()
 console.log('value linkage checks passed: single mode, draft input, exclusive save, disabled round trip')

 // 固定默认值在弹窗内暂存，配置行的布局应与组件类型一致。
 await openNode()
 await clickText('基础与布局','[role="tab"]')
 await selectSetting('默认值','固定值')
 assert.ok(await evaluate(`(() => {
  const row = document.querySelector('.node-default-value')
  const select = row.querySelector('.el-select').getBoundingClientRect()
  const button = row.querySelector('button').getBoundingClientRect()
  const content = row.closest('.el-form-item__content').getBoundingClientRect()
  return Math.abs(select.left - content.left) < 1 && Math.abs(button.right - content.right) < 1
   && Math.abs(button.left - select.right - 10) < 1 && button.top < select.bottom && select.top < button.bottom
 })()`), '默认值下拉和配置按钮应占满整行')
 const fixedDialog = '.el-dialog:has(.default-fixed-value)'
 const fixedInput = `${fixedDialog} input[aria-label="固定默认值"]`
 const openFixedConfig = () => clickText('配置','.node-default-value button')
 assert.equal(await evaluate('document.querySelector(".property-form [aria-label=固定默认值]")'),null)
 await openFixedConfig()
 await clickText('确定',`${fixedDialog} ${configFooter}`)
 assert.match(await evaluate(`document.querySelector('${fixedDialog} [role="alert"]').textContent`), /请填写固定默认值/)
 await enterValue(fixedInput,'新建客户')
 await clickText('确定',`${fixedDialog} ${configFooter}`)
 await openFixedConfig()
 assert.equal(await evaluate(`document.querySelector('${fixedInput}').value`),'新建客户')
 await enterValue(fixedInput,'将取消的默认值')
 await clickText('取消',`${fixedDialog} ${configFooter}`)
 await clickText('保存当前属性','.node-property-actions button')
 const savedDefault = await evaluate('JSON.parse(componentTest.state.designerNodes[0].propsDocument)')
 assert.equal(savedDefault.defaultValueMode,'fixed')
 assert.equal(savedDefault.defaultValue,'新建客户')
 await closeNode()
 console.log('fixed default configuration checks passed: full-width row, validation, confirm and cancel')

 // 运行时同时传递字段值和整表数据；组件主动发起越权补丁也必须由宿主过滤。
 const probe=await evaluate('JSON.parse(document.querySelector("#runtime .probe-state").textContent)')
 assert.equal(probe.value,'初始值');assert.equal(probe.target,'待回填');assert.equal(probe.node,'name');assert.equal(probe.config.caption,'统一参数')
 await click('#runtime .scalar');assert.equal(await evaluate('componentTest.state.data.name'),'组件新值')
 await click('#runtime .patch')
 assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(componentTest.state.data))'),{name:'组件新值',target:'跨字段新值',locked:'只读字段回填值',hidden:'隐藏原值'})
 assert.equal(await evaluate('[...document.querySelectorAll("#runtime input")].some(input=>input.value==="只读字段回填值" && (input.disabled || input.readOnly))'),true)
 await click('#row .patch')
 assert.equal(await evaluate('componentTest.state.row.target'),'跨字段新值')
 assert.equal(await evaluate('componentTest.state.row.locked'),'只读字段回填值')
 assert.equal(await evaluate('Object.hasOwn(componentTest.state.row,"rootOnly")'),false,'子表当前行不能回填根表字段')
 assert.equal(await evaluate('componentTest.state.data.name'),'组件新值')
 // 恶意控件没有主动禁用按钮，平台仍需阻止 scalar 和 patch 两种写入口。
 await evaluate('componentTest.state.readonly=true;componentTest.settle()')
 const before=await evaluate('JSON.stringify([componentTest.state.data,componentTest.state.row])')
 await click('#runtime .scalar');await click('#runtime .patch');await click('#row .scalar');await click('#row .patch')
 assert.equal(await evaluate('JSON.stringify([componentTest.state.data,componentTest.state.row])'),before)
 assert.equal(await evaluate('JSON.parse(document.querySelector("#runtime .probe-state").textContent).disabled'),true)
 for (const selector of ['#pc-summary', '#mobile-summary']) {
  const summaryText=await evaluate(`document.querySelector('${selector}').innerText`)
  assert.match(summaryText,/当前行验收摘要/)
  assert.match(summaryText,/当前子表行/)
  assert.match(summaryText,/验收评分\s*0/)
  assert.match(summaryText,/表单事件已回填/)
  assert.equal(await evaluate(`document.querySelector('${selector} button')`),null)
 }
 assert.deepEqual(errors,[])
 const runtimeImage=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-unified-component-runtime.png',Buffer.from(runtimeImage.data,'base64'))
 console.log('unified component browser acceptance passed: designer, multi-field backfill into readonly fields, hidden and unknown target protection, child-row scope')
}catch(error){console.error('browser errors',errors,consoleErrors);if(ws?.readyState===WebSocket.OPEN){try{const shot=await send('Page.captureScreenshot',{format:'png'});writeFileSync('/tmp/flow-unified-component-failure.png',Buffer.from(shot.data,'base64'));console.error(await evaluate('JSON.stringify({url:location.href,state:document.readyState,text:document.body.innerText})'))}catch{}}throw error}finally{ws?.close();browser?.kill();await server?.close();await sleep(200);rmSync(fixture,{recursive:true,force:true});rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})}
