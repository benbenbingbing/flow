import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实关系定义、快捷配置、画布和发布运行时；API 使用内存数据，避免修改业务实体。
const harness = `
import { createApp, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Management from '/src/views/entity/components/EntityRelationManagement.vue'
import Picker from '/src/components/form-designer/FormRelationPicker.vue'
import Preview from '/src/components/form-designer/RelationContentDesignPreview.vue'
import Runtime from '/src/components/FormPreviewLinkage.vue'
import DesignNode from '/src/components/FormNodeDesignItem.vue'
import { formRelatedContentsAt } from '/src/shared/form-related-content.js'
import ButtonConfig from '/src/components/ListButtonConfigPanel.vue'
import DataTable from '/src/views/entity/components/EntityDataTable.vue'
import RelatedPanel from '/src/components/related-content/RelatedContentPanel.vue'
import { buildFormNodePayload } from '/src/shared/form-node-property-schema.js'
import { buildRelationEditor } from '/src/shared/relation-content.js'
import { normalizeListActionForSave } from '/src/shared/list-config-design.js'
import request from '/src/utils/request.js'
const source={id:'all',entityCode:'ALL',entityName:'全流程验收'}
const fields=[{id:'name',fieldCode:'reqName',fieldName:'需求名称',fieldType:'STRING',componentType:'input'},
 {id:'ref',fieldCode:'zdw_all_id',fieldName:'所属验收',fieldType:'REFERENCE',refEntityId:'all'},
 {id:'bad',fieldCode:'wrongRef',fieldName:'其他引用',fieldType:'REFERENCE',refEntityId:'other'},
 {id:'plain',fieldCode:'acceptance_id',fieldName:'验收ID（普通文本）',fieldType:'STRING',fieldLength:128},
 {id:'number',fieldCode:'numericId',fieldName:'数字ID',fieldType:'LONG',dbType:'VARCHAR(200)'},
 {id:'short',fieldCode:'shortId',fieldName:'长度不足的ID',fieldType:'STRING',fieldLength:32}]
const target={id:'req',entityCode:'ZDWREQ',entityName:'需求管理',status:'PUBLISHED',storageMode:'DYNAMIC',fields}
const relations=[{id:'r1',relationName:'关联需求',relationCode:'reqRelation',dataKey:'reqRelation',childEntityId:'req',childEntityName:'需求管理',childEntityCode:'ZDWREQ',childRefFieldCode:'acceptance_id',relationType:'ONE_TO_ONE',ownershipType:'ASSOCIATION',enabled:true,cascadeDelete:false},
 {id:'r2',relationName:'需求明细',relationCode:'reqItems',dataKey:'reqItems',childEntityId:'req',childEntityName:'需求管理',childEntityCode:'ZDWREQ',childRefFieldCode:'zdw_all_id',relationType:'ONE_TO_MANY',ownershipType:'ASSOCIATION',enabled:true}]
relations.forEach(relation=>{relation.parentEntityId='all';relation.direction='FORWARD'})
relations.push({...relations[1],id:'owned',relationCode:'ownedDetails',dataKey:'ownedDetails',relationName:'组成明细',ownershipType:'COMPOSITION'},
 {...relations[0],id:'reverse',relationCode:'reqRelation',relationName:'所属需求',direction:'REVERSE',parentEntityId:'req',parentEntityCode:'ZDWREQ',parentEntityName:'需求管理',childEntityId:'all',relationType:'ONE_TO_MANY'})
const form={id:'req-form',entityId:'req',formName:'需求详情表单',formKey:'details',status:1,activeReleaseId:'req-release',layoutType:'vertical',viewConfig:{inputParameterSchema:{type:'object',properties:{sourceName:{type:'string'}}}}}
const formFields=[{...fields[0],id:'field-name',fieldId:'name',fieldLabel:'需求名称'}]
const nodes=[{id:'name-node',nodeKey:'reqName',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'reqName',props:{fieldCode:'reqName',label:'需求名称',componentType:'input'}}]
const formRelease={id:'req-release',version:1,releaseResolutionToken:'req-release-token',snapshotDocument:JSON.stringify({form,legacyFields:formFields,nodes})}
const listFields=[{id:'name-col',fieldCode:'reqName',fieldName:'需求名称',fieldLabel:'需求名称',fieldType:'STRING',showInList:true}]
const list={id:'req-list',entityId:'req',listName:'需求明细列表',listKey:'requirements',activeReleaseId:'list-release',publishedVersion:1,fields:listFields,viewConfig:{inputParameterSchema:{type:'object',properties:{sourceName:{type:'string'}}}}}
const listRelease={id:'list-release',version:1,status:'ACTIVE',snapshotDocument:JSON.stringify({list})}
const state=reactive({compositions:[],editorNodes:[],aggregateRuntime:false,aggregateData:{},published:false,sourceRecordId:'all-1',sourceData:{name:'未保存的验收名称'},parameterTest:false,calls:[],relationWrites:[],unexpected:[],catalogFailure:false,listButtonTest:false,buttons:[],savedButton:null,selectedRows:[],tableRows:[{id:'all-1',name:'验收一',actionCapabilities:{requirements:{visible:true,enabled:true}}},{id:'all-2',name:'验收二',actionCapabilities:{requirements:{visible:true,enabled:true}}}]})
const buttonCompositions=()=>state.compositions.map(item=>({...item,anchorType:'LIST_ACTION',config:{...item.config,presentation:{...item.config.presentation,position:'DRAWER'}}}))
request.defaults.adapter=async config=>{
 const url=config.url,method=config.method,body=typeof config.data==='string'?JSON.parse(config.data):config.data||{}
 state.calls.push({url,method,body,params:config.params})
 let data
 if(['/entity/all/relations','/entity/all/relations/available'].includes(url)&&method==='get'){if(state.relationFailure)throw Error('模拟实体关系加载失败');data=relations}
 else if(url==='/entity/all/relations'&&method==='post'){state.relationWrites.push(body);data={...body,id:'new-relation',relationCode:body.relationCode||'rel_generated',dataKey:body.dataKey||'rel_generated'};relations.push(data)}
 else if(url==='/entity/options')data={records:[target],total:1}
 else if(url==='/entity/options/resolve')data=[target]
 else if(url==='/entity/req'||url==='/entity/code/ZDWREQ')data=target
 else if(url==='/entity-form/entity/req'){if(state.catalogFailure)throw Error('模拟目录加载失败');data=[form,{id:'draft',formName:'未发布表单',status:1}]}
 else if(url==='/entity-list-config/entity/req')data=[list]
 else if(url==='/entity-form/req-form/fields')data=formFields
 else if(url==='/entity-forms/req-form/runtime-release')data=state.parameterTest?{...formRelease,snapshotDocument:JSON.stringify({form:{...form,dataSourceBindingsDocument:{AFTER_LOAD:[{extensionId:'parameter-reader',bindingCode:'parameter-reader'}]}},legacyFields:formFields,nodes})}:formRelease
 else if(url==='/entity-list-config/req-list')data=list
 else if(url==='/entity-list-config/req-list/releases')data=[listRelease]
 else if(url==='/ui-view-compositions/FORM/owner'&&method==='get')data=state.compositions.map(item=>({...item,ownerRevision:100}))
 else if(url==='/ui-view-compositions/FORM/owner'&&method==='post'){data={...body,id:'c'+(state.compositions.length+1),revision:1,ownerRevision:state.compositions.length+2};state.compositions.push(data)}
 else if(url==='/ui-view-compositions/FORM/owner/validate')data={normalizedConfig:body.config}
 else if(url.startsWith('/ui-view-compositions/FORM/owner/c') && url.endsWith('/update')){
   if(body.expectedOwnerRevision!==100)throw Error('未刷新宿主修订号')
   const index=state.compositions.findIndex(item=>url.endsWith('/'+item.id+'/update'))
   data={...body,id:state.compositions[index].id,revision:state.compositions[index].revision+1,ownerRevision:101};state.compositions[index]=data
 }
 else if(url==='/ui-runtime/view-compositions/resolve'){
   const item=state.compositions.find(i=>i.compositionKey===body.compositionKey),t=item.config.target
   data={compositionKey:item.compositionKey,targetContentType:t.contentType,targetContentId:t.contentId,targetContentKey:t.contentKey,targetEntityCode:'ZDWREQ',targetEntityId:'req',targetReleaseId:t.contentType==='FORM'?'req-release':'list-release',targetReleaseVersion:1,targetRecordId:body.recordId==='missing'?null:'child-'+body.recordId,matchNone:body.recordId==='missing',actions:['VIEW'],traversalContextToken:'traversal',listContextToken:'list-'+body.recordId,actionContextToken:'actions'}
 }
 else if(url==='/ui-runtime/view-compositions/actions/capabilities')data={actionCapabilities:{}}
 else if(url.startsWith('/entity-data/entity/ZDWREQ/detail/'))data={id:'child-'+state.sourceRecordId,data:{reqName:'关联需求 '+state.sourceRecordId,zdw_all_id:state.sourceRecordId,acceptance_id:state.sourceRecordId}}
 else if(url==='/entity-lists/ZDWREQ/requirements/schema')data={...list,entityCode:'ZDWREQ',releaseId:'list-release',scene:'EMBEDDED',toolbarConfig:[],rowActionConfig:[]}
 else if(url==='/entity-lists/ZDWREQ/requirements/query'){
   if(!state.parameterTest&&body.viewCompositionContextToken!=='list-'+state.sourceRecordId)throw Error('列表未携带当前记录的关联凭证')
   data={records:[{id:'row-'+state.sourceRecordId,data:{reqName:'明细需求 '+state.sourceRecordId},reqName:'明细需求 '+state.sourceRecordId}],total:1}
 }
 else if(url==='/ui-runtime/extensions/execute')data={data:{reqName:body.input.params.sourceName}}
 else if(url==='/entity-form-resolve/new-data/ZDWREQ')data=null
 else if(url.includes('/entity-versions/records/'))data={enabled:false}
 else if(url==='/ui-runtime/events/FIELD_CHANGE/execute')data={success:true,results:[],patch:{}}
 else if(url.includes('/entity-status/')||url.includes('/ui-event-bindings')||url.includes('/ui-extensions/')||url.includes('/entity-form/entity/req/fields'))data=[]
 else {state.unexpected.push(url);data=[]}
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
function aggregateForm(){
 const fields=state.editorNodes.map(node=>({...node,componentProps:JSON.stringify({subFormConfig:{relationCode:node.bindingRef,dataKey:node.fieldCode,relationType:node.relationType,childEntityId:node.childEntityId,refEntityId:node.childEntityId,childRefFieldCode:node.childRefFieldCode,layout:'form'}})}))
 return {id:'aggregate-owner',formName:'验收主从表单',fields,nodes:fields.map((node,index)=>buildFormNodePayload({...node,id:'agg-'+index,nodeKey:node.fieldCode},{componentProps:JSON.parse(node.componentProps)}))}
}
// 节点位置回归使用真实递归设计组件和预览渲染器，不将关联卡片统一追加到页面末尾。
const placementNodes=[
 {id:'text-node',nodeKey:'text-key',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'text',fieldCode:'text',fieldName:'我是文本',fieldType:'STRING',componentType:'input'},
 {id:'section',nodeKey:'section-key',nodeType:'SECTION',componentProps:'{}'},
 {id:'nested',nodeKey:'nested-key',parentId:'section',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'nested',fieldCode:'nested',fieldName:'区块内字段',fieldType:'STRING',componentType:'input'},
 {id:'tabs',nodeKey:'tabs-key',nodeType:'TAB_SET',componentProps:'{}'},
 {id:'tab',nodeKey:'tab-key',parentId:'tabs',nodeType:'TAB',componentProps:'{}'}
]
const placementChildren=id=>placementNodes.filter(node=>(node.parentId||'')===(id||''))
const placementItems=()=>state.placementTest?state.compositions.filter(item=>item.config.relation.direction==='REVERSE'):[]
const placementForm=()=>({id:'owner',fields:placementNodes.filter(node=>node.nodeType==='FIELD'),nodes:placementNodes.map((node,index)=>buildFormNodePayload({...node,sortOrder:index},{componentProps:{label:node.fieldName||node.nodeKey}})),viewCompositions:placementItems()})
const picker=ref(null)
const relatedPanel=ref(null)
const app=createApp({setup(){return()=>h('main',{style:'width:1080px;margin:20px auto'},[
 h(RelatedPanel,{ref:relatedPanel,ownerType:'FORM',ownerId:'owner',sourceEntity:source,sourceFields:[{fieldCode:'name',fieldName:'验收名称'}],anchorOptions:placementNodes.map(node=>({value:node.id,label:(node.fieldName||node.nodeKey)+'（节点之后）',nodeType:node.nodeType}))}),
 state.aggregateRuntime?h('section',{id:'aggregate-runtime'},[h('h3','主从编辑运行时'),h(Runtime,{form:aggregateForm(),modelValue:state.aggregateData,'onUpdate:modelValue':value=>state.aggregateData=value,context:{record:{id:'all-1'}},mode:'edit',readonly:false,showHeader:false})]):null,
 state.placementTest?h('section',{id:'placement-canvas',key:'canvas-'+(state.placementRemount||0)},[
 ...placementChildren('').map((node,index)=>h(DesignNode,{node,siblingIndex:index,siblingCount:3,relatedContents:placementItems(),childrenFor:placementChildren,nodeSpanFor:()=>24,nodeStyleFor:()=>({width:'100%'}),legacyNodeType:n=>n.nodeType,nodeLabel:id=>placementNodes.find(n=>n.id===id)?.fieldName||id,canDropNode:()=>true,onEditRelatedContent:item=>{state.placementEdited=item.id;relatedPanel.value.open(item)},onRemoveRelatedContent:item=>{state.placementRemoved=item.id}})),
 ...formRelatedContentsAt(placementItems(),null,{preview:true}).map(item=>h(Preview,{key:item.id,composition:item}))]):null,
 state.placementTest?h('section',{id:'placement-preview',key:'preview-'+(state.placementRemount||0)},[h(Runtime,{form:placementForm(),designPreview:true,mode:state.placementMode||'create',showHeader:false})]):null,
 h('h2','实体关系：定义到展示'),h('section',{id:'definition'},[h(Management,{entityId:'all',canManage:true})]),
 h('div',{style:'display:grid;grid-template-columns:260px 1fr;gap:20px'},[
 h('aside',{id:'picker'},[h(Picker,{ref:picker,sourceEntity:source,ownerId:'owner',compositions:state.compositions,nodes:state.editorNodes,onAddEditor:selection=>state.editorNodes.push(buildRelationEditor(selection)),onEdit:item=>relatedPanel.value.open(item)})]),
 h('section',{id:'design'},[h('h3','设计画布'),...state.compositions.map(item=>h(Preview,{key:item.id,composition:item}))])]),
 state.published?h('section',{id:'runtime'},[h('h3','发布运行时'),h(Runtime,{form:{id:'owner',formName:'验收',fields:[],nodes:[],runtimeReleaseId:'owner-release',runtimeReleaseVersion:1,viewCompositions:state.compositions},modelValue:state.sourceData,context:{record:{id:state.sourceRecordId}},readonly:true,mode:'view',showHeader:false})]):null,
 state.listButtonTest?h('section',{id:'button-config'},[h('h3','关联内容使用标准列表按钮'),h(ButtonConfig,{type:'row',modelValue:state.buttons,relatedContents:buttonCompositions(),onSave:button=>{state.savedButton=normalizeListActionForSave(button,'ROW')}})]):null,
 state.listButtonTest?h('section',{id:'button-runtime'},[h(DataTable,{dataList:state.tableRows,loading:false,total:2,pageNum:1,pageSize:10,listFields:[{fieldCode:'name',fieldLabel:'验收名称',fieldName:'验收名称'}],useListConfig:false,toolbarButtons:state.buttons.map(b=>({...b,label:'打开需求'})),toolbarCapabilities:{requirements:{visible:true,enabled:true}},rowActionButtons:state.buttons,showSelectionColumn:true,selectedRows:state.selectedRows,'onUpdate:selectedRows':rows=>state.selectedRows=rows,entityCode:'ALL',entityDefinition:source,entityStatusMap:{},refEntityNameMap:{},refresh:()=>{},buttonCompositions:buttonCompositions(),listOwnerId:'list-owner',listReleaseId:'list-release',listReleaseVersion:1,listReleaseResolutionToken:'pinned-list-token'})]):null
])}})
app.use(createPinia()).use(createRouter({history:createMemoryHistory(),routes:[]})).use(ElementPlus).mount('#app')
window.relationTest={state,picker,relatedPanel,relations,placementItems,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,400));await nextTick()}}
`

const fixture = mkdtempSync(path.resolve('.relation-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'flow-relation-chrome-'))
let browser, ws, server, nextId = 0
const pending = new Map(), errors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function send(method, params = {}) {
  const id = ++nextId
  const promise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return promise
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}
async function click(text, scope = 'document') {
  await evaluate(`(()=>{const node=[...${scope}.querySelectorAll('button')].find(e=>e.getClientRects().length&&e.textContent.trim()===${JSON.stringify(text)});if(!node)throw Error('找不到按钮 '+${JSON.stringify(text)});node.click()})()`)
  await evaluate('relationTest.settle()')
}
async function until(expression) {
  for (let i = 0; i < 80; i++) { if (await evaluate(expression)) return; await sleep(100) }
  throw Error('等待失败: ' + expression + '\n' + await evaluate('document.body.innerText'))
}
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3397, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9397', '--user-data-dir=' + profile, 'about:blank'], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9397/json/list')).json(); break } catch { await sleep(100) } }
  assert.ok(targets?.length, 'Chrome 未启动')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) { const request = pending.get(data.id); pending.delete(data.id); data.error ? request.reject(Error(JSON.stringify(data.error))) : request.resolve(data.result) }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: 'http://127.0.0.1:3397/' + path.basename(fixture) + '/index.html' })
  await until('Boolean(window.relationTest)')
  await evaluate('relationTest.settle()')

  // 普通 ID 字段可直接建立关系；数字、短字段与错误引用目标不能出现在候选项。
  await click('新增关系', "document.getElementById('definition')")
  await click('请选择要展示数据的实体')
  await until("document.querySelector('.entity-definition-picker-dialog .el-table__row')")
  await evaluate("document.querySelector('.entity-definition-picker-dialog .el-table__row').click()")
  await click('确认选择')
  await until("[...document.querySelectorAll('.el-form-item')].some(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent.trim()==='关联字段')")
  await evaluate("[...document.querySelectorAll('.el-form-item')].find(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent.trim()==='关联字段').querySelector('.el-select__wrapper').click()")
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.includes('acceptance_id'))")
  const fieldOptions=await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].filter(e=>e.getClientRects().length).map(e=>e.textContent)")
  assert.ok(fieldOptions.some(text=>text.includes('zdw_all_id')),'原有正确引用字段仍可选')
  assert.ok(fieldOptions.every(text=>!['wrongRef','numericId','shortId'].some(code=>text.includes(code))))
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('acceptance_id')).click()")
  // 匹配规则随字段说明收进问号提示，实际悬停后仍应显示当前选择的关联字段。
  const referenceHelpPoint = await evaluate(`(()=>{
    const rect = document.querySelector('[aria-label="查看关联字段配置说明"]').getBoundingClientRect()
    return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }
  })()`)
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...referenceHelpPoint })
  await until("[...document.querySelectorAll('[role=tooltip]')].some(e=>e.getClientRects().length&&e.innerText.includes('acceptance_id = 当前记录.id'))")
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 0, y: 0 })
  await click('创建关系')
  await until('relationTest.state.relationWrites.length===1')
  const created = await evaluate('JSON.parse(JSON.stringify(relationTest.state.relationWrites[0]))')
  assert.ok(!created.relationCode, '内部关系编码无需用户填写')
  assert.ok(!created.dataKey, '内部数据键无需用户填写')
  assert.equal(created.childRefFieldCode, 'acceptance_id')
  assert.equal(created.relationType, 'ONE_TO_ONE')

  // 目录错误不能被误判为没有发布页面，也不能提交上次残留的选项。
  await evaluate('relationTest.state.catalogFailure=true')
  await click('选择表单', "document.getElementById('picker')")
  assert.equal(await evaluate("document.body.innerText.includes('模拟目录加载失败')"), true)
  assert.equal(await evaluate("[...document.querySelectorAll('button')].find(e=>e.getClientRects().length&&e.textContent.trim()==='添加到表单').disabled"), true)
  await click('取消')
  await evaluate('relationTest.state.catalogFailure=false')
  await click('选择表单', "document.getElementById('picker')")
  await click('添加到表单')
  await until("document.getElementById('design').innerText.includes('需求名称')")
  await click('选择列表', "document.getElementById('picker')")
  await click('添加到表单')
  await until("document.querySelectorAll('#design .relation-design-preview').length===2 && document.querySelector('#design .el-table__header')")
  const compositions = await evaluate('JSON.parse(JSON.stringify(relationTest.state.compositions))')
  assert.deepEqual(compositions.map(item => item.config.target.contentType), ['FORM', 'LIST'])
  assert.deepEqual(compositions.map(item => item.config.relation.relationCode), ['reqRelation', 'reqItems'])
  assert.ok(compositions.every(item => item.config.presentation.position === 'INLINE'))
  assert.equal(await evaluate("relationTest.state.calls.some(call=>call.url.endsWith('/query'))"), false, '设计预览不得查询目标业务数据')
  await evaluate('relationTest.state.compositions=JSON.parse(JSON.stringify(relationTest.state.compositions));relationTest.settle()')
  await click('已添加 · 配置', "document.getElementById('picker')")
  await click('保存显示配置')
  await until('relationTest.state.compositions[0].revision===2')
  assert.equal(await evaluate('relationTest.state.compositions.length'), 2, '修改目标展示不能重复添加')

  // 模拟发布后的配置重载，实际使用共享表单/列表运行时及可信关联查询参数。
  await evaluate('relationTest.state.published=true;relationTest.settle()')
  await until("[...document.querySelectorAll('#runtime input')].some(input=>input.value==='关联需求 all-1')")
  await until("document.getElementById('runtime').innerText.includes('明细需求 all-1')")
  await evaluate("relationTest.state.sourceRecordId='all-2';relationTest.settle()")
  await until("document.getElementById('runtime').innerText.includes('明细需求 all-2')")
  assert.equal(await evaluate("[...document.querySelectorAll('#runtime input')].some(input=>input.value==='关联需求 all-2')"), true)
  await evaluate("relationTest.state.sourceRecordId='missing';relationTest.settle()")
  await until("document.getElementById('runtime').innerText.includes('未找到关联数据')")
  await evaluate("relationTest.state.sourceRecordId='';relationTest.settle()")
  await until("document.getElementById('runtime').innerText.includes('请先保存当前记录')")
  assert.equal(await evaluate("document.getElementById('runtime').innerText.includes('关联内容加载失败')"), false)
  await evaluate("relationTest.state.sourceRecordId='all-1';relationTest.settle()")
  await until("document.getElementById('runtime').innerText.includes('明细需求 all-1')")
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('/tmp/flow-entity-relation-acceptance.png', Buffer.from(screenshot.data, 'base64'))

  // 新入口在标准按钮中配置并保存；同一关联定义可以由工具栏和操作列复用。
  await evaluate("relationTest.state.published=false;relationTest.state.buttons=[{key:'requirements',type:'custom',customMode:'open-related-content',label:'需求详情',buttonType:'success',link:false,icon:'View',enabled:true}];relationTest.state.listButtonTest=true;relationTest.settle()")
  await until("document.querySelector('#button-config .execution-config__detail .el-select')")
  await evaluate("document.querySelector('#button-config .execution-config__detail .el-select__wrapper').click()")
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.trim()==='关联需求')")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.trim()==='关联需求').click()")
  await click('保存', "document.getElementById('button-config')")
  assert.equal(await evaluate('relationTest.state.savedButton.actionParams.compositionKey'), compositions[0].compositionKey)
  await evaluate("(()=>{const saved=JSON.parse(JSON.stringify(relationTest.state.savedButton));relationTest.state.buttons=[{key:saved.buttonKey,type:saved.buttonType,customMode:saved.customMode,label:saved.buttonLabel,buttonType:saved.styleType,link:saved.linkMode,icon:saved.icon,enabled:saved.enabled,...saved.actionParams}]})()")
  await evaluate('relationTest.settle()')
  assert.equal(await evaluate("[...document.querySelectorAll('#button-runtime .table-toolbar button')].find(b=>b.textContent.trim()==='打开需求').disabled"), true)
  assert.equal(await evaluate("document.getElementById('button-runtime').innerText.includes('查看关联需求')"), false, '不能额外生成独立入口')
  const rowButton = "[...document.querySelectorAll('#button-runtime .el-table__body button')].find(b=>b.textContent.trim()==='需求详情')"
  assert.equal(await evaluate(`${rowButton}.classList.contains('el-button--success') && ${rowButton}.classList.contains('is-link') && !!${rowButton}.querySelector('svg')`), true, '历史 link=false 也应保持操作列文字链接外观')
  await click('需求详情', "document.getElementById('button-runtime')")
  await until("[...document.querySelectorAll('.el-drawer input')].some(e=>e.value==='关联需求 all-1')")
  const rowRequest = await evaluate("JSON.parse(JSON.stringify(relationTest.state.calls.filter(c=>c.url==='/ui-runtime/view-compositions/resolve').at(-1).body))")
  assert.equal(rowRequest.ownerType, 'LIST')
  assert.equal(rowRequest.ownerId, 'list-owner')
  assert.equal(rowRequest.recordId, 'all-1')
  assert.equal(rowRequest.releaseResolutionToken, 'pinned-list-token')
  await evaluate("[...document.querySelectorAll('.el-drawer__close-btn')].find(e=>e.getClientRects().length).click();relationTest.settle()")
  await evaluate('relationTest.state.selectedRows=[...relationTest.state.tableRows];relationTest.settle()')
  assert.equal(await evaluate("[...document.querySelectorAll('#button-runtime .table-toolbar button')].find(b=>b.textContent.trim()==='打开需求').disabled"), true)
  await evaluate("relationTest.state.sourceRecordId='all-2';relationTest.state.selectedRows=[relationTest.state.tableRows[1]];relationTest.settle()")
  await click('打开需求', "document.getElementById('button-runtime')")
  await until("[...document.querySelectorAll('.el-drawer input')].some(e=>e.value==='关联需求 all-2')")
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url==='/ui-runtime/view-compositions/resolve').at(-1).body.recordId"), 'all-2')
  await evaluate("[...document.querySelectorAll('.el-drawer__close-btn')].find(e=>e.getClientRects().length).click();relationTest.settle()")
  await evaluate("relationTest.state.tableRows[1].actionCapabilities.requirements.enabled=false;relationTest.settle()")
  assert.equal(await evaluate("[...document.querySelectorAll('#button-runtime .table-toolbar button')].find(b=>b.textContent.trim()==='打开需求').disabled"), true)
  await evaluate("relationTest.state.tableRows[1].actionCapabilities.requirements.visible=false;relationTest.settle()")
  assert.equal(await evaluate("[...document.querySelectorAll('#button-runtime .table-toolbar button')].some(b=>b.textContent.trim()==='打开需求')"), false)
  const buttonScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('/tmp/flow-list-related-button-acceptance.png', Buffer.from(buttonScreenshot.data, 'base64'))

  // 侧栏高级配置和设置面板共同使用固定实体关系的展示编辑器，不再重新定义数据关联。
  await evaluate("relationTest.state.compositions[0].config.presentation.position='DRAWER';relationTest.state.compositions[0].config.actions=['EDIT'];relationTest.settle()")
  await click('已添加 · 配置', "document.getElementById('picker')")
  const dialog = "document.querySelector('.related-content-config-dialog')"
  await until(`${dialog}?.innerText.includes('继承实体关系：关联需求') && !${dialog}.querySelector('.el-loading-mask')`)
  assert.equal(await evaluate(`${dialog}.innerText.includes('ZDWREQ.acceptance_id = 当前记录.id')`), true)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.el-steps .el-step').length`), 3)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.relation-methods, .el-segmented').length`), 0)
  assert.equal(await evaluate(`${dialog}.querySelector('.inherited-target').textContent.includes('需求管理')`), true)
  assert.equal(await evaluate(`${dialog}.innerText.includes('第 1 步，共 3 步')`), true)
  await click('下一步', dialog)
  assert.equal(await evaluate(`${dialog}.innerText.includes('第 2 步，共 3 步')`), true)
  assert.equal(await evaluate(`[...${dialog}.querySelectorAll('.step-heading')].find(e=>e.getClientRects().length).textContent.includes('允许做什么')`), true)
  await click('上一步', dialog)
  await evaluate(`(()=>{const input=${dialog}.querySelector('input.el-input__inner');input.value='验收需求展示';input.dispatchEvent(new Event('input',{bubbles:true}))})()`)
  const inheritedScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync('/tmp/flow-inherited-relation-settings.png', Buffer.from(inheritedScreenshot.data, 'base64'))
  await click('保存显示配置', dialog)
  await until('relationTest.state.compositions[0].revision===3')
  const inherited=await evaluate('JSON.parse(JSON.stringify(relationTest.state.compositions[0]))')
  assert.equal(inherited.config.name,'验收需求展示')
  assert.equal(inherited.config.relation.relationCode,'reqRelation')
  assert.equal(inherited.config.target.entityId,'req')
  assert.equal(inherited.config.target.contentType,'FORM')
  assert.equal(inherited.config.presentation.position,'DRAWER')
  assert.deepEqual(inherited.config.actions,['EDIT'])
  assert.equal(inherited.expectedOwnerRevision,100)
  await evaluate('relationTest.relatedPanel.value.open(relationTest.state.compositions[1]);relationTest.settle()')
  await until(`${dialog}?.innerText.includes('继承实体关系：需求明细')`)
  assert.equal(await evaluate(`${dialog}.innerText.includes('一对多：选择关联实体的列表')`),true)
  await click('取消',dialog)

  // 失效的关系不可退回默认值或继续保存。
  for (const invalid of ['disabled','missing','failure']) {
    await evaluate(`relationTest.relations[0].enabled=${invalid!=='disabled'};relationTest.relations[0].deleted=${invalid==='missing'?1:0};relationTest.state.relationFailure=${invalid==='failure'};relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()`)
    await until(`${dialog}?.querySelector('.entity-relation-binding .el-alert--error')`)
    assert.equal(await evaluate(`[...${dialog}.querySelectorAll('button')].find(b=>b.textContent.trim()==='保存显示配置').disabled`),true)
    await click('取消',dialog)
  }
  await evaluate('relationTest.relations[0].enabled=true;relationTest.relations[0].deleted=0;relationTest.state.relationFailure=false')

  // 历史接口取数必须由用户明确移除；保存后恢复真正的实体关系引用，操作权限仍保留。
  await evaluate("relationTest.state.compositions[0].config.specialHandling={mode:'INTERFACE_SERVICE',interfaceService:{extensionId:'legacy-reader',inputMappings:[],outputMappings:[]},actionServices:[]};relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()")
  await until(`${dialog}?.innerText.includes('继承实体关系：关联需求')`)
  await click('保存显示配置',dialog)
  await until(`${dialog}.innerText.includes('改为使用实体关系取数')`)
  assert.equal(await evaluate('relationTest.state.compositions[0].revision'),3)
  await click('改为使用实体关系取数',dialog)
  await click('保存显示配置',dialog)
  await until('relationTest.state.compositions[0].revision===4')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.specialHandling.mode'),'NONE')
  assert.deepEqual(await evaluate('Array.from(relationTest.state.compositions[0].config.actions)'),['EDIT'])

  // 关系数量发生变化后重新选择页面，不能保留不兼容的单条表单。
  await evaluate("relationTest.relations[0].relationType='ONE_TO_MANY';relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()")
  await until(`${dialog}?.innerText.includes('一对多：选择关联实体的列表')`)
  assert.equal(await evaluate(`${dialog}.innerText.includes('需求详情表单 (details)')`),false)
  await click('保存显示配置',dialog)
  assert.equal(await evaluate('relationTest.state.compositions[0].revision'),4)
  await evaluate(`[...${dialog}.querySelectorAll('.el-form-item')].find(e=>e.querySelector('.el-form-item__label')?.textContent.includes('显示内容')).querySelector('.el-select__wrapper').click()`)
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.includes('需求明细列表'))")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('需求明细列表')).click()")
  await click('保存显示配置',dialog)
  await until(`${dialog}.innerText.includes('第 2 步，共 3 步')`)
  const editCheckbox = `[...${dialog}.querySelectorAll('.action-card')].find(e=>e.querySelector('.el-checkbox__label').textContent.trim()==='编辑记录').querySelector('input')`
  assert.equal(await evaluate(`${editCheckbox}.disabled`),false,'数量变化后不兼容的历史动作仍须允许取消')
  await evaluate(`${editCheckbox}.click()`)
  await evaluate(`[...${dialog}.querySelectorAll('.action-card')].find(e=>e.querySelector('.el-checkbox__label').textContent.trim()==='仅查看').querySelector('input').click()`)
  await click('保存显示配置',dialog)
  await until('relationTest.state.compositions[0].revision===5')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.target.contentType'),'LIST')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.target.contentId'),'req-list')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.relation.relationCode'),'reqRelation')
  assert.deepEqual(await evaluate('Array.from(relationTest.state.compositions[0].config.actions)'),['VIEW'])

  // 新建业务关联必须选择目录中的关系；扩展页面另有入口，不能再次输入字段匹配条件。
  await click('新增关联内容',"document.querySelector('.related-content-panel')")
  await until(`${dialog}?.innerText.includes('继承实体关系：请选择')`)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.el-steps .el-step').length`),3)
  assert.equal(await evaluate(`[...${dialog}.querySelectorAll('button')].find(b=>b.textContent.trim()==='保存显示配置').disabled`),true)
  await evaluate(`${dialog}.querySelector('.entity-relation-binding .el-select__wrapper').click()`)
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.includes('所属需求'))")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('所属需求')).click()")
  await until(`${dialog}.innerText.includes('当前记录.acceptance_id = ZDWREQ.id')`)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.relation-methods').length`),0)
  await evaluate(`[...${dialog}.querySelectorAll('.el-form-item')].find(e=>e.querySelector('.el-form-item__label')?.textContent.includes('显示内容')).querySelector('.el-select__wrapper').click()`)
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.includes('需求详情表单'))")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('需求详情表单')).click()")
  await click('保存显示配置',dialog)
  await until("relationTest.state.compositions.some(c=>c.config.relation.direction==='REVERSE')")
  assert.equal(await evaluate("relationTest.state.compositions.find(c=>c.config.relation.direction==='REVERSE').config.target.contentType"),'FORM')
  await click('扩展页面',"document.querySelector('.related-content-panel')")
  await until(`${dialog}?.innerText.includes('扩展页面')`)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.el-steps .el-step').length`),4)
  assert.equal(await evaluate(`${dialog}.querySelector('.entity-relation-binding')===null`),true)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.relation-methods button').length`),1)
  await click('取消',dialog)

  // 组成关系直接生成主从编辑节点，不调用实体字段创建接口，也不创建独立保存的展示配置。
  await click('Close this dialog', "document.querySelector('.related-content-panel')").catch(async()=>{
    await evaluate("document.querySelector('.related-content-panel .el-drawer__close-btn')?.click();relationTest.settle()")
  })
  await click('添加主从编辑', "document.getElementById('picker')")
  await until("document.body.innerText.includes('选择子记录使用的表单')")
  assert.equal(await evaluate("document.body.innerText.includes('随主表单统一提交')"),true)
  await click('添加到表单')
  await until('relationTest.state.editorNodes.length===1')
  const aggregate=await evaluate('JSON.parse(JSON.stringify(relationTest.state.editorNodes[0]))')
  assert.equal(aggregate.bindingType,'RELATION')
  assert.equal(aggregate.bindingRef,'ownedDetails')
  assert.equal(aggregate.nodeType,'REPEATER')
  assert.equal(aggregate.childFormReleaseId,'req-release')
  assert.equal(aggregate.fieldId,undefined)
  await evaluate('relationTest.state.aggregateRuntime=true;relationTest.settle()')
  await until("document.querySelector('#aggregate-runtime .sub-form-field')")
  await evaluate("(()=>{const button=[...document.querySelectorAll('#aggregate-runtime button')].find(b=>/添加|新增/.test(b.textContent));if(!button)throw Error('主从编辑缺少新增行');button.click()})();relationTest.settle()")
  await until("document.querySelector('#aggregate-runtime input.el-input__inner')")
  await evaluate("(()=>{const input=document.querySelector('#aggregate-runtime input.el-input__inner');input.value='新增需求明细';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))})();relationTest.settle()")
  await until("relationTest.state.aggregateData.ownedDetails?.[0]?.reqName==='新增需求明细'")
  assert.equal(await evaluate("relationTest.state.calls.some(call=>call.method==='post'&&call.url.startsWith('/entity-data')&&/(create|update|save|delete|submit)([/]|$)/.test(call.url))"),false,'子表编辑只修改主表草稿，不独立提交')
  const aggregateScreenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true})
  writeFileSync('/tmp/flow-unified-aggregate-editor.png',Buffer.from(aggregateScreenshot.data,'base64'))

  // 从设置面板选择“我是文本之后”，无宿主发布版本和记录 ID 也应显示目标布局。
  await evaluate('relationTest.state.published=false;relationTest.state.aggregateRuntime=false;relationTest.state.listButtonTest=false;relationTest.state.placementTest=true;relationTest.settle()')
  await evaluate('relationTest.relatedPanel.value.open(relationTest.placementItems()[0]);relationTest.settle()')
  await until(`${dialog}?.innerText.includes('关系展示设置')`)
  async function selectPlacement(label, optionText) {
    await evaluate(`[...${dialog}.querySelectorAll('.el-form-item')].find(e=>e.querySelector('.el-form-item__label')?.textContent.includes(${JSON.stringify(label)})).querySelector('.el-select__wrapper').click()`)
    const option=`[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes(${JSON.stringify(optionText)}))`
    await until(option)
    await evaluate(`${option}.click();relationTest.settle()`)
  }
  await selectPlacement('显示位置','嵌入当前页面')
  await selectPlacement('放置位置','我是文本')
  await click('保存显示配置',dialog)
  await until("relationTest.placementItems()[0].anchorKey==='text-node'")
  await evaluate("document.querySelector('.related-content-panel .el-drawer__close-btn')?.click();relationTest.settle()")
  await until("document.querySelector('#placement-canvas [data-node-id=\"text-node\"] .relation-design-preview input')")
  await until("document.querySelector('#placement-preview .relation-design-preview input')")
  const initialResolveCalls=await evaluate("relationTest.state.calls.filter(c=>c.url==='/ui-runtime/view-compositions/resolve').length")
  for(const mode of ['create','edit','view']) {
    await evaluate(`relationTest.state.placementMode=${JSON.stringify(mode)};relationTest.state.placementRemount=(relationTest.state.placementRemount||0)+1;relationTest.settle()`)
    await until("document.querySelector('#placement-preview .relation-design-preview input')")
    assert.equal(await evaluate("document.querySelectorAll('#placement-preview .relation-design-preview').length"),1)
    assert.equal(await evaluate("document.querySelector('#placement-preview .form-node-root-row > .el-col').querySelectorAll('.relation-design-preview').length"),1,'应显示在字段所属节点之后，不能追加到表单末尾')
  }
  // 稳定 nodeKey、递归区块、Tab 和表单末尾均使用相同的布局预览，不重复渲染。
  for(const [key,selector,position] of [
    ['nested-key','[data-node-id="nested"]','INLINE'],
    ['tab-key','.design-tab-panel','TAB'],
    ['','','INLINE']
  ]) {
    await evaluate(`(()=>{const item=relationTest.placementItems()[0];item.anchorKey=${JSON.stringify(key)};item.anchorType=${JSON.stringify(key?'FORM_NODE':'OWNER')};item.config.presentation.position=${JSON.stringify(position)};item.config.presentation.loadMode='ON_DEMAND'})();relationTest.settle()`)
    const canvasSelector='#placement-canvas '+selector+' .relation-design-preview'
    await until(`document.querySelector(${JSON.stringify(canvasSelector)})?.querySelector('input')`)
    await until("document.querySelector('#placement-preview .relation-design-preview input')")
    assert.equal(await evaluate("document.querySelectorAll('#placement-canvas .relation-design-preview').length"),1)
    assert.equal(await evaluate("document.querySelectorAll('#placement-preview .relation-design-preview').length"),1)
    if(key==='tab-key') assert.equal(await evaluate("document.querySelectorAll('#placement-preview .node-tab-panel .relation-design-preview').length"),1)
  }
  // 停用立即生效；布局预览不能因为缺少记录偷偷查询业务数据。
  await evaluate('relationTest.placementItems()[0].config.enabled=false;relationTest.settle()')
  assert.equal(await evaluate("document.querySelectorAll('#placement-preview .relation-design-preview,#placement-canvas .relation-design-preview').length"),0)
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url==='/ui-runtime/view-compositions/resolve').length"),initialResolveCalls)
  await evaluate('relationTest.placementItems()[0].config.enabled=true;relationTest.placementItems()[0].anchorKey="text-node";relationTest.placementItems()[0].anchorType="FORM_NODE";relationTest.settle()')
  await until("document.querySelector('#placement-preview .relation-design-preview input')")
  const placementScreenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true})
  writeFileSync('/tmp/flow-related-content-placement.png',Buffer.from(placementScreenshot.data,'base64'))

  // 从关系展示设置保存来源字段映射；只读关联仍可把当前未保存值传给目标数据接口。
  await evaluate('relationTest.state.compositions=[relationTest.placementItems()[0]];relationTest.state.placementTest=false;relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()')
  await until(`${dialog}?.querySelector('.page-parameter-mapping')`)
  await click('增加映射',dialog)
  async function selectParameter(index, label) {
    await evaluate(`${dialog}.querySelectorAll('.parameter-row .el-select__wrapper')[${index}].click()`)
    const option=`[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes(${JSON.stringify(label)}))`
    await until(option); await evaluate(`${option}.click();relationTest.settle()`)
  }
  await selectParameter(2,'验收名称')
  await click('保存显示配置',dialog)
  await until("relationTest.state.compositions[0].config.parameterMappings?.[0]?.sourceField==='name'")
  await evaluate("document.querySelector('.related-content-panel .el-drawer__close-btn')?.click();(()=>{const item=relationTest.state.compositions[0];item.anchorType='OWNER';item.anchorKey='';item.config.presentation={position:'INLINE',loadMode:'IMMEDIATE'};item.config.actions=['VIEW'];relationTest.state.compositions=[item];relationTest.state.parameterTest=true;relationTest.state.published=true})();relationTest.settle()")
  await until("relationTest.state.calls.some(c=>c.url==='/ui-runtime/extensions/execute'&&c.body.input?.params?.sourceName==='未保存的验收名称')")
  await until("document.querySelector('#runtime .related-content-runtime input')?.value==='未保存的验收名称'")
  const lastResolve=await evaluate("JSON.parse(JSON.stringify(relationTest.state.calls.filter(c=>c.url==='/ui-runtime/view-compositions/resolve').at(-1).body))")
  assert.equal(lastResolve.recordId,await evaluate('relationTest.state.sourceRecordId'))
  assert.equal(lastResolve.sourceData,undefined,'传参不能进入可信关系解析')
  await evaluate("relationTest.state.sourceData.name='再次修改但未保存';relationTest.settle()")
  await until("document.querySelector('#runtime .related-content-runtime input')?.value==='再次修改但未保存'")
  const resolveCount = await evaluate("relationTest.state.calls.filter(c=>c.url==='/ui-runtime/view-compositions/resolve').length")
  await evaluate("relationTest.state.sourceData.unmapped='不影响目标';relationTest.settle()")
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url==='/ui-runtime/view-compositions/resolve').length"),resolveCount,'未映射字段变化不能触发关联重复请求')
  const parameterScreenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true})
  writeFileSync('/tmp/flow-page-parameters.png',Buffer.from(parameterScreenshot.data,'base64'))

  // 普通工具栏打开列表：行字段映射必须唯一选中，不能静默取多选第一行。
  await evaluate("relationTest.state.published=false;relationTest.state.buttons=[{key:'requirements',type:'custom',customMode:'open-list',label:'参数查询',targetEntityCode:'ZDWREQ',targetListKey:'requirements',parameterMappings:[{parameter:'sourceName',sourceType:'FIELD',sourceField:'name'}]}];relationTest.state.selectedRows=[];relationTest.state.listButtonTest=true;relationTest.settle()")
  const queriesBefore=await evaluate("relationTest.state.calls.filter(c=>c.url.endsWith('/requirements/query')).length")
  await click('打开需求',"document.getElementById('button-runtime')")
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url.endsWith('/requirements/query')).length"),queriesBefore)
  await evaluate('relationTest.state.selectedRows=[...relationTest.state.tableRows];relationTest.settle()')
  await click('打开需求',"document.getElementById('button-runtime')")
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url.endsWith('/requirements/query')).length"),queriesBefore)
  await evaluate('relationTest.state.selectedRows=[relationTest.state.tableRows[1]];relationTest.settle()')
  await click('打开需求',"document.getElementById('button-runtime')")
  await until("relationTest.state.calls.some(c=>c.url.endsWith('/requirements/query')&&c.body.context?.parameters?.sourceName==='验收二')")

  assert.deepEqual(errors, [])
  assert.deepEqual(await evaluate('Array.from(relationTest.state.unexpected)'), [], '出现未覆盖的 API 路径')
  console.log('entity relation browser acceptance passed: definition/form/list runtime, standard buttons, inherited relation editor, preserved settings/revisions, cardinality, disabled/deleted/failed relations, unified relation creation, reverse usage, composition editors, extension entry, saved anchor placement; page parameter UI save, latest unsaved source values, target interface usage, list button single-row guard and parameter delivery')
} finally {
  ws?.close(); browser?.kill(); await server?.close(); await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
