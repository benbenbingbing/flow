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
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Management from '/src/views/entity/components/EntityRelationManagement.vue'
import Picker from '/src/components/form-designer/FormRelationPicker.vue'
import Preview from '/src/components/form-designer/RelationContentDesignPreview.vue'
import Runtime from '/src/components/FormPreviewLinkage.vue'
import PageSettings from '/src/components/related-content/RelatedContentPageSettings.vue'
import FormButtonExecutionSettings from '/src/components/FormButtonExecutionSettings.vue'
import { registerListRowAction } from '/src/extensions/core/registries/listActionRegistry.js'
import { registerCustomFormComponent } from '/src/extensions/core/registries/customComponentRegistry.js'
import HostFormDialog from '/src/views/entity/components/EntityDataFormDialog.vue'
import ViewFormFields from '/src/views/entity/components/approval/EntityApprovalBasicInfo.vue'
import DesignNode from '/src/components/FormNodeDesignItem.vue'
import { formRelatedContentsAt } from '/src/shared/form-related-content.js'
import ButtonConfig from '/src/components/ListButtonConfigPanel.vue'
import DataTable from '/src/views/entity/components/EntityDataTable.vue'
import RelatedPanel from '/src/components/related-content/RelatedContentPanel.vue'
import Designer from '/src/views/EntityFormDesignByEntity.vue'
import { registerApplicationExtensions } from '/src/extensions/register.js'
import { buildFormNodePayload } from '@flow/workflow-core/form-node-property-schema'
import { resolveLocalFormActions } from '@flow/workflow-core/form-actions'
import { buildRelationEditor } from '/src/shared/relation-content.js'
import { normalizeListActionForSave } from '/src/shared/list-config-design.js'
import request from '/src/utils/request.js'
// 与真实应用共用安装入口，输入字段、子表单及业务扩展必须经过统一清单注册。
registerApplicationExtensions()
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
// 目标表单自身带有返回主表的关联入口，覆盖根级、字段和 Tab 三种挂载路径。
const nestedTargetNodes=[...nodes,
 {id:'target-tabs',nodeKey:'target-tabs',nodeType:'TAB_SET',props:{label:'目标页签'}},
 {id:'target-tab',nodeKey:'target-tab',parentId:'target-tabs',nodeType:'TAB',props:{label:'目标基本信息'}}]
const nestedTargetRelations=[['OWNER','','查看最终改造名次'],['FORM_NODE','name-node','字段上的返回主表'],['FORM_NODE','target-tab','页签中的返回主表']].map(([anchorType,anchorKey,title],index)=>({
 compositionKey:'back-to-owner-'+index,anchorType,anchorKey,config:{enabled:true,name:title,
 target:{contentType:'FORM',contentId:'owner',entityId:'all'},relation:{type:'ENTITY_RELATION',relationCode:'ownedDetails',direction:'REVERSE'},
 actions:['VIEW'],presentation:{position:'DRAWER',title,loadMode:'ON_DEMAND'}}}))
const formRelease={id:'req-release',configId:'req-form',formId:'req-form',protocolVersion:1,contentHash:'req-release',configHash:'req-graph',snapshotDocument:JSON.stringify({form,legacyFields:formFields,nodes})}
const listFields=[{id:'name-col',fieldCode:'reqName',fieldName:'需求名称',fieldLabel:'需求名称',fieldType:'STRING',showInList:true}]
const list={id:'req-list',entityId:'req',listName:'需求明细列表',listKey:'requirements',activeReleaseId:'list-release',publishedVersion:1,fields:listFields,viewConfig:{inputParameterSchema:{type:'object',properties:{sourceName:{type:'string'}}}}}
const listRelease={id:'list-release',version:1,status:'ACTIVE',snapshotDocument:JSON.stringify({list})}
const state=reactive({compositions:[],editorNodes:[],aggregateRuntime:false,aggregateData:{},published:false,sourceRecordId:'all-1',sourceData:{name:'未保存的验收名称'},parameterTest:false,calls:[],relationWrites:[],unexpected:[],catalogFailure:false,listButtonTest:false,buttons:[],savedButton:null,selectedRows:[],tableRows:[{id:'all-1',name:'验收一',actionCapabilities:{requirements:{visible:true,enabled:true}}},{id:'all-2',name:'验收二',actionCapabilities:{requirements:{visible:true,enabled:true}}}]})
state.designerForm={id:'owner',entityId:'all',formName:'主从编辑验收',formKey:'ALL_main',status:1,revision:1,layoutType:'grid',viewConfig:'{}'}
registerListRowAction('form-test-handler', context => {state.handlerCalls=(state.handlerCalls||0)+1;state.handlerRecord=context.row;return {message:'函数完成'}})
registerCustomFormComponent('FormTestComponent',{props:['modelValue'],emits:['update:modelValue'],render(){return h('div',{id:'form-test-component'},[h('span','表单按钮组件内容'),h('button',{id:'component-update',onClick:()=>this.$emit('update:modelValue',{...this.modelValue,name:'组件回填名称'})},'修改组件草稿')])}})
state.designerNodes=[]
state.designerSaves=[]
const buttonCompositions=()=>state.compositions.map(item=>({...item,anchorType:'LIST_ACTION',config:{...item.config,presentation:{...item.config.presentation,position:'DRAWER'}}}))
request.defaults.adapter=async config=>{
 const url=config.url,method=config.method,body=typeof config.data==='string'?JSON.parse(config.data):config.data||{}
 state.calls.push({url,method,body,params:config.params})
 let data
 if(['/entity/all/relations','/entity/all/relations/available'].includes(url)&&method==='get'){if(state.relationFailure)throw Error('模拟实体关系加载失败');data=relations}
 else if(url==='/entity/all/relations'&&method==='post'){state.relationWrites.push(body);data={...body,id:'new-relation',relationCode:body.relationCode||'rel_generated',dataKey:body.dataKey||'rel_generated'};relations.push(data)}
 else if(url==='/entity/options')data={records:[target],total:1}
 else if(url==='/entity/options/resolve')data=[target]
 else if(url==='/entity/all')data={...source,fields:[{id:'source-name',fieldCode:'name',fieldName:'验收名称',fieldType:'STRING'}]}
 else if(url==='/entity-form/owner'&&method==='get')data=state.designerForm
 else if(url==='/entity-form/owner/fields'||url==='/system/menu/entity-permission-options'||url==='/ui-extensions')data=[]
 else if(url==='/entity-forms/owner/nodes')data=state.designerNodes
 else if(url==='/entity-forms/owner/diff')data={changed:!!state.publicationEnabled&&!state.designerPublished,discardableChanged:!!state.publicationEnabled&&!state.designerPublished,canDiscardDraft:!!state.publicationEnabled&&!state.designerPublished,draftHash:'draft',activeHash:'active',changedItems:[],changedSections:['关联内容']}
 else if(url==='/entity-forms/owner/publish-preview')data={changed:true,canPublish:true,riskLevel:'SAFE',draftHash:'draft',activeReleaseId:state.designerForm.activeReleaseId,changedItems:[]}
 else if(url==='/entity-forms/owner/publish'){state.publishedCompositions=JSON.parse(JSON.stringify(state.compositions));state.designerPublished=true;state.designerForm.activeReleaseId='owner-release';data={id:'owner-release',contentHash:'published-owner'}}
 else if(url==='/entity-form/owner/patch'){
   assertRevision(body.expectedRevision);state.designerForm={...state.designerForm,...body,revision:state.designerForm.revision+1};data=state.designerForm
 }
 else if(url==='/entity-forms/owner/nodes/update'){
   assertRevision(config.params.expectedRevision);state.designerNodes=body;state.designerSaves.push(body);state.designerForm.revision++;data=body
 }
 else if(url==='/entity/req'||url==='/entity/code/ZDWREQ')data=target
 else if(url==='/entity-form/entity/req'){if(state.catalogFailure)throw Error('模拟目录加载失败');data=[form,{id:'draft',formName:'未发布表单',status:1}]}
 else if(url==='/entity-list-config/entity/req')data=[list]
 else if(url==='/entity-form/req-form/fields')data=formFields
 else if(url==='/entity-forms/req-form/runtime')data=state.inlineSaveTest
   ?{...formRelease,snapshotDocument:JSON.stringify({form:{...form,isReadonly:!!state.inlineFormReadonly,viewConfig:{...form.viewConfig,inputParameterBindings:[{usage:'INITIALIZE',parameter:'sourceName',targetField:'reqName'}]}},legacyFields:formFields.map(f=>({...f,isRequired:1})),nodes:nodes.map(n=>({...n,props:{...n.props,isRequired:1}}))})}
   :state.nestedTargetTest
   ?{...formRelease,snapshotDocument:JSON.stringify({form,legacyFields:formFields,nodes:nestedTargetNodes,viewCompositions:nestedTargetRelations})}
   :state.parameterTest?{...formRelease,snapshotDocument:JSON.stringify({form:{...form,dataSourceBindingsDocument:{AFTER_LOAD:[{extensionId:'parameter-reader',bindingCode:'parameter-reader'}]}},legacyFields:formFields,nodes})}:formRelease
 else if(url==='/entity-list-config/req-list')data=list
 else if(url==='/entity-list-config/req-list/releases')data=[listRelease]
 else if(url==='/ui-view-compositions/FORM/owner'&&method==='get')data=state.compositions.map(item=>({...item,ownerRevision:state.designerTest?state.designerForm.revision:100}))
 else if(url==='/ui-view-compositions/FORM/owner'&&method==='post'){if(state.designerTest)assertRevision(body.expectedOwnerRevision);data={...body,id:'c'+(state.compositions.length+1),revision:1,ownerRevision:state.designerTest?++state.designerForm.revision:state.compositions.length+2};state.compositions.push(data)}
 else if(url==='/ui-view-compositions/FORM/owner/validate')data={normalizedConfig:body.config}
 else if(url.startsWith('/ui-view-compositions/FORM/owner/c') && url.endsWith('/update')){
   if(state.failRelatedUpdate)throw Error('模拟关联属性保存失败')
   if(body.expectedOwnerRevision!==(state.designerTest?state.designerForm.revision:100))throw Error('未刷新宿主修订号')
   const index=state.compositions.findIndex(item=>url.endsWith('/'+item.id+'/update'))
   data={...body,id:state.compositions[index].id,revision:state.compositions[index].revision+1,ownerRevision:state.designerTest?++state.designerForm.revision:101};state.compositions[index]=data
 }
 else if(url==='/ui-runtime/view-compositions/resolve'){
   const item=state.compositions.find(i=>i.compositionKey===body.compositionKey),t=item.config.target
   const missing=body.recordId==='missing'&&!state.childRecords?.[body.recordId]
   data={compositionKey:item.compositionKey,targetContentType:t.contentType,targetContentId:t.contentId,targetContentKey:t.contentKey,targetEntityCode:'ZDWREQ',targetEntityId:'req',targetContentHash:t.contentType==='FORM'?'req-graph':'list-graph',targetRecordId:missing?null:'child-'+body.recordId,matchNone:missing,actions:['VIEW'],compositionContext:{ownerType:body.ownerType,ownerId:body.ownerId,recordId:body.recordId,compositionKey:body.compositionKey,path:[],configHash:body.configHash}}
 }
 else if(url==='/ui-runtime/view-compositions/actions/capabilities')data={actionCapabilities:state.inlineSaveTest
   ?{EDIT:{available:!state.inlineViewOnly},CREATE:{available:!state.inlineViewOnly}}:{}}
 // 独立提交与服务端 EntityDataActionService 一致，从顶层 compositionContext 核验来源；延迟/失败用于验证重复点击与回包隔离。
 else if(state.nativeHostTest&&url==='/entity-data/entity/ALL/detail/all-3/load')data={id:'all-3',data:{name:'主表名称',childId:'child-all-3'}}
 else if(state.nativeHostTest&&url==='/entity-data/entity/ALL/detail/all-3/update'){state.mainSavedData=body.data;data={id:'all-3',data:body.data}}
 else if(state.nativeHostTest&&url==='/ui-runtime/form-actions/resolve')data=state.formButtonsTest&&body.formId==='owner'?[{...state.testButton,ownerFormId:'owner',runtimeKey:'owner:quick',type:'custom',key:'quick',label:'运行测试按钮',visible:true,enabled:true,placement:'FOOTER'}]:body.formId==='owner'?resolveLocalFormActions(hostForm(),body):[]
 else if(state.nativeHostTest&&url.startsWith('/ui-runtime/events/'))data={success:true,results:[],patch:{}}
 else if(state.inlineSaveTest&&(url==='/entity-data'||url.endsWith('/update'))){
   if(!body.compositionContext||body.formConfigHash!=='req-graph'||body.formId!=='req-form')throw Error('独立保存未携带可信发布上下文')
   const recordId=body.compositionContext.recordId
   if(state.inlineSaveDelay)await new Promise(resolve=>setTimeout(resolve,state.inlineSaveDelay))
   if(state.inlineSaveFailure)throw Error('模拟子表单提交失败')
   state.childRecords[recordId]={...body.data};data={id:'child-'+recordId,data:body.data}
 }
 else if(url.startsWith('/entity-data/entity/ZDWREQ/detail/')){
   const recordId=url.split('/detail/')[1].split('/')[0].replace(/^child-/,'')
   data={id:'child-'+recordId,data:state.childRecords?.[recordId]||{reqName:'关联需求 '+recordId,zdw_all_id:recordId,acceptance_id:recordId}}
 }
 else if(url==='/entity-lists/ZDWREQ/requirements/schema')data={...list,entityCode:'ZDWREQ',releaseId:'list-release',scene:'EMBEDDED',toolbarConfig:[],rowActionConfig:[]}
 else if(url==='/entity-lists/ZDWREQ/requirements/query'){
   if(!state.parameterTest&&!state.formButtonsTest&&body.compositionContext?.recordId!==state.sourceRecordId)throw Error('列表未携带当前记录的关联来源')
   data={records:[{id:'row-'+state.sourceRecordId,data:{reqName:'明细需求 '+state.sourceRecordId},reqName:'明细需求 '+state.sourceRecordId}],total:1}
 }
 else if(url==='/ui-runtime/extensions/execute')data={data:{reqName:body.input.params.sourceName}}
 else if(url==='/entity-form-resolve/new-data/ZDWREQ')data=null
 else if(url.includes('/entity-versions/records/'))data={enabled:false}
 else if(url==='/ui-runtime/events/FIELD_CHANGE/execute')data={success:true,results:[],patch:{}}
 else if(url.endsWith('/catalog'))data={events:[]}
 else if(url.includes('/entity-status/')||url.includes('/ui-event-bindings')||url.includes('/ui-extensions/')||url.includes('/entity-form/entity/req/fields')||url.includes('/entity-form/entity/all/fields'))data=[]
 else {state.unexpected.push(url);data=[]}
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
function assertRevision(value){if(value!==state.designerForm.revision)throw Error('设计器保存使用了过期修订号')}
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
const hostDialog=ref(null)
const hostFields=[{id:'main-name',fieldCode:'name',fieldName:'主表名称',fieldType:'STRING',componentType:'input'}]
// 编辑弹窗会提升根 Tab 并只渲染子节点；关联内容锚定空 Tab 时也必须挂载。
const hostTabNodes=[{id:'host-tabs',nodeType:'TAB_SET',props:{}},{id:'host-basic',parentId:'host-tabs',nodeType:'TAB',props:{label:'基本信息'}},{id:'host-child',nodeKey:'host-child-key',parentId:'host-tabs',nodeType:'TAB',props:{label:'子表单'}}]
const hostForm=()=>({id:'owner',viewConfig:{actionBar:{customButtons:state.formButtonsTest?[{...state.testButton,key:'quick',label:'运行测试按钮',perm:'entity:all:view',modes:['edit']}]:[]}},formName:'主表',fields:hostFields,nodes:state.spacingPlacement?state.designerNodes.map(n=>({...n,props:JSON.parse(n.propsDocument||'{}')})):state.immediateTabsTest?hostTabNodes:[],configHash:'owner-release',viewCompositions:[state.compositions[0]]})
// 节点显隐使用实际递归渲染器，不用 CSS 或替身组件模拟整块隐藏。
const visibilityRenderer=ref(null)
const visibilityNodes=['SECTION','GRID','COLLAPSE','TEXT','ACTION_SLOT','TAB_SET'].flatMap((nodeType,index)=>{
 const id='visibility-'+index, props={label:'区域'+index,text:'条件说明',modeAccess:{edit:{visible:false}},linkageRules:{visibilityRule:"status == 'ACTIVE'"}}
 const result=[{id,nodeKey:id,nodeType,props}]
 if(['SECTION','GRID','COLLAPSE','TAB_SET'].includes(nodeType)){
  const parentId=nodeType==='TAB_SET'?id+'-tab':id
  if(nodeType==='TAB_SET')result.push({id:parentId,parentId:id,nodeType:'TAB',props:{label:'条件页签'}})
  result.push({id:id+'-field',parentId,nodeKey:'value'+index,nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'value'+index,props:{fieldCode:'value'+index,label:'内部字段'+index,componentType:'input',fieldType:'STRING',required:true}})
 }
 return result
})
const app=createApp({setup(){return()=>h('main',{style:'width:1080px;margin:20px auto'},[
 state.borderTest?h('section',{id:'border-settings'},[h(PageSettings,{editor:state.compositions[0]})]):null,
 state.executionSettingsTest?h('section',{id:'execution-settings'},[h(FormButtonExecutionSettings,{button:state.executionDraft,relatedContents:state.compositions,fields:hostFields})]):null,
 state.visibilityTest?h('section',{id:'node-visibility-runtime'},[h(Runtime,{ref:visibilityRenderer,form:{id:'visibility-form',nodes:visibilityNodes,fields:[]},modelValue:state.visibilityData,'onUpdate:modelValue':value=>state.visibilityData=value,mode:state.visibilityMode,showHeader:false})]):null,
 state.viewHostTest?h('section',{id:'view-host'},[h(ViewFormFields,{entityData:{name:'主表名称'},approvalNormalForm:hostForm(),formReadonly:true,mode:'view',nodeRootParentId:'host-child',context:{record:{id:'all-3'}},entityCode:'ALL',entityDefinition:source,entityFields:hostFields})]):null,
 state.nativeHostTest?h(HostFormDialog,{ref:hostDialog,entityCode:'ALL',entityDefinition:{...source,fields:hostFields},entityFields:hostFields,defaultForm:hostForm()}):null,
 state.designerTest?h('section',{id:'real-designer'},[h(RouterView,{key:state.designerRemount||0})]):null,
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
 state.published?h('section',{id:'runtime'},[h('h3','发布运行时'),h(Runtime,{form:{id:'owner',formName:'验收',fields:[],nodes:[],configHash:'owner-release',viewCompositions:state.compositions},modelValue:state.sourceData,context:{record:{id:state.sourceRecordId}},readonly:true,mode:'view',showHeader:false})]):null,
 state.standaloneTargetTest?h('section',{id:'standalone-target'},[h(Runtime,{form:{...form,fields:formFields,nodes:nestedTargetNodes,configHash:'req-release',viewCompositions:nestedTargetRelations},modelValue:{reqName:'独立目标表单'},context:{record:{id:'req-standalone'}},readonly:true,mode:'view',showHeader:false})]):null,
 state.listButtonTest?h('section',{id:'button-config'},[h('h3','关联内容使用标准列表按钮'),h(ButtonConfig,{type:'row',modelValue:state.buttons,relatedContents:buttonCompositions(),onSave:button=>{state.savedButton=normalizeListActionForSave(button,'ROW')}})]):null,
 state.listButtonTest?h('section',{id:'button-runtime'},[h(DataTable,{dataList:state.tableRows,loading:false,total:2,pageNum:1,pageSize:10,listFields:[{fieldCode:'name',fieldLabel:'验收名称',fieldName:'验收名称'}],useListConfig:false,toolbarButtons:state.buttons.map(b=>({...b,label:'打开需求'})),toolbarCapabilities:{requirements:{visible:true,enabled:true}},rowActionButtons:state.buttons,showSelectionColumn:true,selectedRows:state.selectedRows,'onUpdate:selectedRows':rows=>state.selectedRows=rows,entityCode:'ALL',entityDefinition:source,entityStatusMap:{},refEntityNameMap:{},refresh:()=>{},buttonCompositions:buttonCompositions(),listOwnerId:'list-owner',listConfigHash:'list-graph'})]):null
])}})
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/entity-form/design/:id',component:Designer}]})
await router.push('/entity-form/design/owner?entityId=all')
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.relationTest={visibilityRenderer,state,picker,relatedPanel,hostDialog,hostForm,relations,placementItems,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,400));await nextTick()}}
`

const fixture = mkdtempSync(path.resolve('.relation-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'flow-relation-chrome-'))
let browser, ws, server, nextId = 0
const pending = new Map(), errors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const compositionEditorOnly = process.argv.includes('--composition-editor')
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
// 用浏览器原生双击验证事件链，不能只调用暴露的方法绕过侧栏、递归容器和 DOM 冒泡。
async function doubleClick(expression) {
  const point = await evaluate(`(()=>{const node=${expression};if(!node)throw Error('找不到双击标题');node.scrollIntoView({block:'center'});const r=node.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`)
  for (const clickCount of [1, 2]) {
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', buttons: 1, clickCount, ...point })
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', buttons: 0, clickCount, ...point })
  }
  await evaluate('relationTest.settle()')
}
async function until(expression) {
  for (let i = 0; i < 80; i++) { if (await evaluate(expression)) return; await sleep(100) }
  throw Error('等待失败: ' + expression + '\n' + await evaluate('document.body.innerText'))
}
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios', 'async-validator', 'dompurify'] }, server: { host: '127.0.0.1', port: 3397, strictPort: true, forwardConsole: false } })
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


  // 节点整体显隐同时覆盖运行时和真实设计器的保存回填。
  if (process.argv.includes('--node-visibility')) {
    await evaluate("relationTest.state.visibilityData={status:'DRAFT',value0:'保留的输入'};relationTest.state.visibilityMode='create';relationTest.state.visibilityTest=true;relationTest.settle()")
    assert.equal(await evaluate("document.querySelectorAll('#node-visibility-runtime .form-node-root-row > .el-col').length"),0)
    await evaluate("relationTest.state.visibilityData.status='ACTIVE';relationTest.settle()")
    await until("document.querySelectorAll('#node-visibility-runtime .form-node-root-row > .el-col').length===6")
    assert.equal(await evaluate('relationTest.visibilityRenderer.value.validate()'),false,'显示节点内必填字段应校验')
    await evaluate("relationTest.state.visibilityMode='edit';relationTest.settle()")
    assert.equal(await evaluate("document.querySelectorAll('#node-visibility-runtime .form-node-root-row > .el-col').length"),0,'模式隐藏整块且不留空列')
    assert.equal(await evaluate('relationTest.visibilityRenderer.value.validate()'),true,'隐藏节点内字段不应阻挡前端校验')
    assert.equal(await evaluate('relationTest.state.visibilityData.value0'),'保留的输入')
    await evaluate("relationTest.state.visibilityMode='view';relationTest.settle()")
    await until("document.querySelectorAll('#node-visibility-runtime .form-node-root-row > .el-col').length===6")
    assert.equal(await evaluate("document.querySelector('#node-visibility-runtime input').value"),'保留的输入')
    await evaluate("relationTest.state.visibilityData.status='DRAFT';relationTest.settle()")
    assert.equal(await evaluate("document.querySelectorAll('#node-visibility-runtime .form-node-root-row > .el-col').length"),0,'条件变化时隐藏整个节点')
    await evaluate(`relationTest.state.visibilityTest=false;relationTest.state.designerNodes=${JSON.stringify([
      {id:'visible-section',nodeKey:'visible-section',nodeType:'SECTION',propsDocument:JSON.stringify({label:'整体显示区域',linkageRules:{visibilityRule:"reqName == '原条件'"}})}
    ])};relationTest.state.designerTest=true;relationTest.settle()`)
    const section = "document.querySelector('#real-designer .form-node-design-item[data-node-type=\"SECTION\"]')"
    await until(`${section} && !document.querySelector('#real-designer .el-loading-mask')`)
    await doubleClick(section)
    const drawer = "[...document.querySelectorAll('.node-property-drawer')].find(e=>e.getClientRects().length)"
    await until(`${drawer}?.innerText.includes('运行模式显示')`)
    const modes = `[...${drawer}.querySelectorAll('.settings-section')].find(e=>e.querySelector('.settings-section__heading strong')?.textContent==='运行模式显示')`
    assert.equal(await evaluate(`${modes}.classList.contains('is-disabled')`),false)
    await evaluate(`${modes}.querySelector('.settings-section__header').click();relationTest.settle()`)
    assert.equal(await evaluate(`${modes}.querySelectorAll('.el-checkbox').length`),4,'布局节点仅配置显示，不附带字段编辑权限')
    await evaluate(`[...${modes}.querySelectorAll('.mode-access-row')].find(e=>e.textContent.includes('编辑')).querySelector('.el-checkbox').click();relationTest.settle()`)
    assert.equal(await evaluate(`${drawer}.querySelector('[aria-label="启用条件显示"]').getAttribute('aria-checked')`),'true')
    const conditionInput = `${drawer}.querySelector('.condition-value input')`
    await until(conditionInput)
    await evaluate(`${conditionInput}.value='新条件';${conditionInput}.dispatchEvent(new Event('input',{bubbles:true}));${conditionInput}.dispatchEvent(new Event('change',{bubbles:true}));relationTest.settle()`)
    await evaluate(`${drawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
    await click('保存全部',"document.getElementById('real-designer')")
    await until('relationTest.state.designerSaves.length===1')
    const saved = await evaluate('JSON.parse(relationTest.state.designerNodes[0].propsDocument)')
    assert.equal(saved.modeAccess.edit.visible,false)
    assert.match(JSON.stringify(saved.linkageRules),/新条件/)
    await evaluate('relationTest.state.designerRemount=1;relationTest.settle()')
    await until(`${section} && !document.querySelector('#real-designer .el-loading-mask')`)
    await doubleClick(section)
    await until(`${drawer}?.innerText.includes('运行模式显示')`)
    assert.equal(await evaluate(`[...${modes}.querySelectorAll('.mode-access-row')].find(e=>e.textContent.includes('编辑')).querySelector('input').checked`),false)
    assert.equal(await evaluate(`${conditionInput}.value`),'新条件')
    assert.deepEqual(errors, [])
    console.log('node visibility browser acceptance passed: all container kinds, mode/condition changes, preserved values, hidden validation and designer save/reload')
  } else if (process.argv.includes('--subform-spacing-placement')) {
    await evaluate(`relationTest.state.compositions=[{id:'c1',revision:1,compositionKey:'child',anchorType:'FORM_NODE',anchorKey:'child-tab',config:{enabled:true,name:'关联子表单',target:{contentType:'FORM',contentId:'req-form',entityId:'req'},relation:{type:'ENTITY_RELATION',relationCode:'reqRelation'},presentation:{position:'TAB',loadMode:'IMMEDIATE',showBorder:false,showTitle:false}}}];relationTest.state.designerNodes=[{id:'tabs',nodeKey:'tabs',nodeType:'TAB_SET',propsDocument:'{}'},{id:'child-tab',nodeKey:'child-tab',parentId:'tabs',nodeType:'TAB',propsDocument:'{"label":"子表单"}'},{id:'following',nodeKey:'following',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'reqName',propsDocument:'{"fieldCode":"reqName","label":"后续字段","componentType":"input","span":24}'}];relationTest.state.designerTest=true;relationTest.settle()`)
    await until("[...document.querySelectorAll('#real-designer .design-container-empty')].some(e=>e.textContent.includes('拖拽节点到子表单下方'))")
    // 从真实末尾投放组件发出 Sortable 完成事件，覆盖设计器父容器、锚点和保存逻辑。
    await evaluate(`(()=>{const zone=[...document.querySelectorAll('#real-designer .design-container-empty')].find(e=>e.textContent.includes('拖拽节点到子表单下方')).parentElement;let vm=zone.__vueParentComponent;while(vm&&!vm.props.canDrop)vm=vm.parent;const element=document.querySelector('#real-designer .form-node-design-item[data-node-id=following]');let source=element.__vueParentComponent;while(source&&!source.props.node)source=source.parent;vm.emit('drop',{node:source.props.node,newParentId:'child-tab',newIndex:0})})();relationTest.settle()`)
    await until("document.querySelector('#real-designer .design-tab-panel .form-node-design-entry:has(> .form-node-design-item[data-node-id=following]) > .relation-design-preview')")
    assert.equal(await evaluate("document.querySelector('#real-designer .form-node-design-item[data-node-id=following]').contains(document.querySelector('#real-designer .relation-design-preview'))"),false,'主表字段选框不能包含子表单预览')
    assert.equal(await evaluate("(()=>{const f=document.querySelector('#real-designer .form-node-design-item[data-node-id=following]');return f.previousElementSibling?.classList.contains('relation-design-preview')})()"),true,'子表单与主表字段必须为独立的同级显示区域')
    await click('保存全部',"document.getElementById('real-designer')")
    await until("relationTest.state.designerNodes.find(n=>n.id==='following')?.parentId==='child-tab' && relationTest.state.compositions[0].config.presentation.anchorSide==='BEFORE'")
    assert.equal(await evaluate('relationTest.state.compositions[0].anchorKey'),'following')
    await evaluate('relationTest.state.designerRemount=1;relationTest.settle()')
    await until("document.querySelector('#real-designer .design-tab-panel .form-node-design-entry:has(> .form-node-design-item[data-node-id=following]) > .relation-design-preview')")
    await evaluate('relationTest.state.designerTest=false;relationTest.state.spacingPlacement=true;relationTest.state.nativeHostTest=true;relationTest.settle()')
    await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
    await until("document.querySelector('.entity-data-form-dialog .related-content-runtime__card input')")
    assert.equal(await evaluate("(()=>{const c=document.querySelector('.entity-data-form-dialog .related-content-runtime__card');const input=[...document.querySelectorAll('.entity-data-form-dialog input')].find(e=>!e.closest('.related-content-runtime'));return !!(c.compareDocumentPosition(input)&Node.DOCUMENT_POSITION_FOLLOWING)})()"),true,'运行时子表单必须位于新字段之前')
    await evaluate('relationTest.state.spacingPlacement=false;relationTest.state.nativeHostTest=false;relationTest.settle()')
    await evaluate("relationTest.state.designerTest=false;relationTest.state.compositions[0].anchorType='OWNER';relationTest.state.compositions[0].anchorKey='';delete relationTest.state.compositions[0].config.presentation.anchorSide;relationTest.state.compositions[0].config.presentation.position='INLINE';relationTest.state.nativeHostTest=true;relationTest.settle()")
    await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
    await until("document.querySelector('.entity-data-form-dialog .related-content-runtime__card input')")
    const spacing = await evaluate("(()=>{const card=document.querySelector('.entity-data-form-dialog .related-content-runtime__card');return {header:!!card.querySelector('.el-card__header'),margin:getComputedStyle(card).marginTop,padding:getComputedStyle(card.querySelector('.el-card__body')).paddingTop}})()")
    assert.deepEqual(spacing,{header:false,margin:'0px',padding:'0px'})
    assert.deepEqual(errors, [])
    await evaluate('relationTest.state.nativeHostTest=false;relationTest.state.designerTest=true;relationTest.state.designerRemount=2;relationTest.settle()')
    await until("[...document.querySelectorAll('#real-designer .design-container-empty')].some(e=>e.textContent.includes('拖拽节点到子表单下方'))")
    await evaluate(`(()=>{const zone=[...document.querySelectorAll('#real-designer .design-container-empty')].find(e=>e.textContent.includes('拖拽节点到子表单下方')).parentElement;let vm=zone.__vueParentComponent;while(vm&&!vm.props.canDrop)vm=vm.parent;let source=document.querySelector('#real-designer .form-node-design-item[data-node-id=following]').__vueParentComponent;while(source&&!source.props.node)source=source.parent;vm.emit('drop',{node:source.props.node,newParentId:'',newIndex:0})})();relationTest.settle()`)
    await click('保存全部',"document.getElementById('real-designer')")
    await until("!relationTest.state.designerNodes.find(n=>n.id==='following')?.parentId && relationTest.state.compositions[0].config.presentation.anchorSide==='BEFORE'")
    assert.deepEqual(errors, [])
    console.log('subform spacing and placement passed: no empty header/padding, append within Tab and root, save and reload')
  } else if (process.argv.includes('--subform-border')) {
    await evaluate(`relationTest.state.compositions=[{id:'c1',compositionKey:'child',anchorType:'OWNER',config:{enabled:true,name:'关联子表单',target:{contentType:'FORM',contentId:'req-form',entityId:'req'},relation:{type:'ENTITY_RELATION',relationCode:'reqRelation'},presentation:{position:'INLINE',loadMode:'IMMEDIATE'}}}];relationTest.state.borderTest=true;relationTest.state.nativeHostTest=true;relationTest.settle()`)
    const borders = () => evaluate("(()=>{const c=document.querySelector('.entity-data-form-dialog .related-content-runtime__card');return [getComputedStyle(c).borderTopWidth,getComputedStyle(c.querySelector('.el-card__header')).borderBottomWidth]})()")
    for (const visible of [true,false,true]) {
      if (visible !== true || await evaluate('relationTest.state.compositions[0].config.presentation.showBorder===false')) {
        await evaluate("document.querySelector('#border-settings .el-switch').click();relationTest.settle()")
        assert.equal(await evaluate('relationTest.state.compositions[0].config.presentation.showBorder'),visible)
      }
      // 保存回读后重新挂载宿主，验证运行时而非仅检查属性草稿。
      await evaluate('relationTest.state.compositions=JSON.parse(JSON.stringify(relationTest.state.compositions));relationTest.state.nativeHostTest=false;relationTest.settle()')
      await evaluate('relationTest.state.nativeHostTest=true;relationTest.settle()')
      await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
      await until("document.querySelector('.entity-data-form-dialog .related-content-runtime__card')")
      assert.deepEqual(await borders(),visible?['1px','1px']:['0px','0px'])
    }
    assert.deepEqual(errors, [])
    console.log('subform border passed: default, settings switch, JSON reload, outer/header borders and re-enable')
  } else if (process.argv.includes('--form-buttons')) {
    await evaluate(`relationTest.state.compositions=[{id:'c1',compositionKey:'child',anchorType:'OWNER',config:{enabled:true,name:'关联子表单',target:{contentType:'FORM',contentId:'req-form',entityId:'req'},relation:{type:'ENTITY_RELATION',relationCode:'reqRelation'},presentation:{position:'DRAWER',loadMode:'ON_DEMAND'}}}];relationTest.state.executionDraft={customMode:'event',execution:{}};relationTest.state.executionSettingsTest=true;relationTest.settle()`)
    await evaluate("document.querySelector('#execution-settings .el-select__wrapper').click();relationTest.settle()")
    const choices = await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].filter(e=>e.getClientRects().length).map(e=>e.textContent)")
    for (const label of ['函数','组件','打开列表','打开表单','打开关联内容','业务接口 / 事件链']) assert.ok(choices.includes(label),label)
    await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent==='打开关联内容').click();relationTest.settle()")
    await evaluate("document.querySelectorAll('#execution-settings .el-select__wrapper')[1].click();relationTest.settle()")
    await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent==='关联子表单').click();relationTest.settle()")
    assert.equal(await evaluate('relationTest.state.executionDraft.execution.compositionKey'),'child')
    await evaluate('relationTest.state.executionSettingsTest=false;relationTest.state.formButtonsTest=true;relationTest.settle()')
    const modes = [
      ['handler',{name:'form-test-handler'}], ['event',{}], ['component',{name:'FormTestComponent'}],
      ['open-related-content',{compositionKey:'child'}], ['open-list',{entityCode:'ZDWREQ',listKey:'requirements',presentation:'DIALOG'}],
      ...['create','edit','view'].map(formMode=>['open-form',{entityCode:'ZDWREQ',formId:'req-form',formMode,recordIdField:'childId'}])
    ]
    for (const [customMode,execution] of modes) {
      await evaluate('relationTest.state.nativeHostTest=false;relationTest.settle()')
      await evaluate(`relationTest.state.testButton=${JSON.stringify({customMode,execution})};relationTest.state.nativeHostTest=true;relationTest.settle()`)
      await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
      await click('运行测试按钮',"document.querySelector('.entity-data-form-dialog')")
      if (customMode==='handler') {
        await until('relationTest.state.handlerCalls===1')
        assert.equal(await evaluate('relationTest.state.handlerRecord.name'),'主表名称')
      } else if (customMode==='event') await until("relationTest.state.calls.some(c=>c.url==='/ui-runtime/events/FORM_BUTTON_CLICK/execute'&&c.body.targetKey==='quick')")
      else if (customMode==='component') {
        await until("document.querySelector('#form-test-component')?.getClientRects().length")
        await evaluate("document.querySelector('#component-update').click();relationTest.settle()")
        assert.equal(await evaluate("[...document.querySelectorAll('.entity-data-form-dialog input')].some(e=>e.value==='组件回填名称')"),false,'组件草稿不应提前写入主表')
        await click('应用到当前表单')
        await until("[...document.querySelectorAll('.entity-data-form-dialog input')].some(e=>e.value==='组件回填名称')")
      }
      else if (customMode==='open-list') await until("relationTest.state.calls.some(c=>c.url==='/entity-lists/ZDWREQ/requirements/query')")
      else if (customMode==='open-related-content') await until("[...document.querySelectorAll('.el-drawer input')].some(e=>e.getClientRects().length&&e.value==='关联需求 all-3')")
      else if (execution.formMode==='create') await until("[...document.querySelectorAll('.entity-data-form-dialog')].some(e=>e.innerText.includes('新增数据 - 需求详情表单'))")
      else await until("[...document.querySelectorAll('.el-dialog input')].some(e=>e.getClientRects().length&&e.value==='关联需求 all-3')")
    }
    assert.deepEqual(errors, [])
    console.log('form custom buttons passed: six execution choices, event compatibility, handler context, component, related child, list and create/edit/view form')
  } else if (process.argv.includes('--overlay-positions')) {
    // 从 Tab 改成独立打开时锚点成为 OWNER；只有 Tab 的表单仍须挂载根级入口。
    for (const position of ['DIALOG', 'DRAWER', 'PAGE']) {
      await evaluate(`relationTest.state.nativeHostTest=false;relationTest.settle()`)
      await evaluate(`(()=>{
        const s=relationTest.state;s.immediateTabsTest=true;s.nativeHostTest=true;
        s.compositions=[{id:'c1',compositionKey:'child',anchorType:'OWNER',anchorKey:'',config:{enabled:true,name:'关联子表单',target:{contentType:'FORM',contentId:'req-form',contentKey:'details',entityId:'req'},relation:{type:'ENTITY_RELATION',relationCode:'reqRelation'},presentation:{position:'${position}',loadMode:'IMMEDIATE',showTitle:false}}}];
      })();relationTest.settle()`)
      await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
      const label = position === 'PAGE' ? '打开关联子表单' : '查看关联子表单'
      assert.equal(await evaluate(`document.querySelector('.entity-data-form-dialog').innerText.includes('${label}')`),true,`${position} 根级打开入口不能被提升后的 Tab 丢弃`)
      await click(label,"document.querySelector('.entity-data-form-dialog')")
      const overlay = position === 'DRAWER' ? '.el-drawer' : '.el-dialog'
      await until(`[...document.querySelectorAll('${overlay} .linkage-form-preview input')].some(e=>e.getClientRects().length && e.value==='关联需求 all-3')`)
      if (position === 'PAGE') assert.equal(await evaluate("Boolean(document.querySelector('.el-dialog.is-fullscreen'))"),true)
    }
    assert.deepEqual(errors, [])
    console.log('related form overlay positions passed: dialog, drawer and fullscreen in Tab-only host')
  } else if (process.argv.includes('--immediate-tabs')) {
    await evaluate(`(()=>{
      const s=relationTest.state;s.immediateTabsTest=true;s.nativeHostTest=true;
      s.compositions=[{id:'c1',compositionKey:'child',anchorType:'FORM_NODE',anchorKey:'host-child',config:{enabled:true,name:'关联子表单',target:{contentType:'FORM',contentId:'req-form',contentKey:'details',entityId:'req'},relation:{type:'ENTITY_RELATION',relationCode:'reqRelation'},presentation:{position:'INLINE',loadMode:'IMMEDIATE',showTitle:false}}}];
    })();relationTest.settle()`)
    await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
    await evaluate("[...document.querySelectorAll('.entity-data-form-dialog [role=tab]')].find(e=>e.textContent==='子表单').click();relationTest.settle()")
    await until("[...document.querySelectorAll('.entity-data-form-dialog .related-content-runtime input')].some(e=>e.getClientRects().length && e.value==='关联需求 all-3')")
    await evaluate("relationTest.state.nativeHostTest=false;relationTest.state.viewHostTest=true;relationTest.settle()")
    await until("[...document.querySelectorAll('#view-host .related-content-runtime input')].some(e=>e.getClientRects().length && e.value==='关联需求 all-3')")
    assert.deepEqual(errors, [])
    console.log('immediate related form in edit/view Tab passed')
  } else {

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
  await evaluate('relationTest.state.nestedTargetTest=true;relationTest.state.published=true;relationTest.settle()')
  await until("[...document.querySelectorAll('#runtime input')].some(input=>input.value==='关联需求 all-1')")
  await until("document.getElementById('runtime').innerText.includes('明细需求 all-1')")
  assert.equal(await evaluate("document.querySelectorAll('#runtime .related-content-runtime').length"),2,'嵌入目标不能继续实例化自己的关联区域')
  for(const title of ['查看最终改造名次','字段上的返回主表','页签中的返回主表']) {
    assert.equal(await evaluate(`document.getElementById('runtime').innerText.includes(${JSON.stringify(title)})`),false,'嵌入目标不保留返回主表的入口')
  }
  assert.equal(await evaluate("relationTest.state.calls.some(c=>c.url==='/ui-runtime/view-compositions/resolve'&&c.body.compositionKey.startsWith('back-to-owner-'))"),false,'禁止发出嵌套关联查询')
  await evaluate('relationTest.state.standaloneTargetTest=true;relationTest.settle()')
  await until("document.getElementById('standalone-target')?.innerText.includes('页签中的返回主表')")
  for(const title of ['查看最终改造名次','字段上的返回主表','页签中的返回主表']) {
    assert.equal(await evaluate(`document.getElementById('standalone-target').innerText.includes(${JSON.stringify(title)})`),true,'独立打开目标表单保留自己的发布关联配置')
  }
  await evaluate('relationTest.state.standaloneTargetTest=false;relationTest.state.nestedTargetTest=false;relationTest.settle()')
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
  assert.equal(rowRequest.configHash, 'list-graph')
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
  // 摘要卡片移除后直接核对匹配规则和当前选中的目标页面，避免仅凭展示标题通过校验。
  const relationMatch = `${dialog}?.querySelector('.relation-match-rule')`
  const targetContentSelection = `${dialog}?.querySelector('.target-content-select .el-select__selected-item.el-select__placeholder')`
  await until(`${relationMatch}?.textContent.trim()==='ZDWREQ.acceptance_id = 当前记录.id' && !${dialog}.querySelector('.el-loading-mask')`)
  assert.equal(await evaluate(`${targetContentSelection}?.textContent.trim()`), '需求详情表单 (details)')
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.el-steps .el-step').length`), 2)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.relation-methods, .el-segmented').length`), 0)
  assert.equal(await evaluate(`${dialog}.querySelector('.inherited-target').textContent.includes('需求管理')`), true)
  assert.equal(await evaluate(`${dialog}.innerText.includes('第 1 步，共 2 步')`), true)
  await click('下一步', dialog)
  assert.equal(await evaluate(`${dialog}.innerText.includes('第 2 步，共 2 步')`), true)
  assert.equal(await evaluate(`[...${dialog}.querySelectorAll('.step-heading')].find(e=>e.getClientRects().length).textContent.includes('允许做什么')`), true)
  assert.equal(await evaluate(`[...${dialog}.querySelectorAll('.action-option')].some(e=>e.textContent.includes('随当前表单一起保存'))`), false, '新展示不能提供无效的聚合保存勾选项')
  await click('上一步', dialog)
  const displaySettings = `${dialog}.querySelector('.form-content-display-settings')`
  assert.equal(await evaluate(`${displaySettings}.querySelector('input.el-input__inner').disabled`),false,'普通关联必须能够修改显示属性')
  await evaluate(`(()=>{const input=${displaySettings}.querySelector('input.el-input__inner');input.value='关联展示标题';input.dispatchEvent(new Event('input',{bubbles:true}));${displaySettings}.querySelector('.el-switch').click()})()`)
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
  assert.equal(inherited.config.presentation.title,'关联展示标题')
  assert.equal(inherited.config.presentation.showTitle,false)
  assert.equal(inherited.expectedOwnerRevision,100)
  await evaluate('relationTest.relatedPanel.value.open(relationTest.state.compositions[1]);relationTest.settle()')
  await until(`${relationMatch}?.textContent.trim()==='ZDWREQ.zdw_all_id = 当前记录.id' && ${targetContentSelection}?.textContent.trim()==='需求明细列表 (requirements)'`)
  await click('取消',dialog)

  // 无编辑/新增权限的关联表单保持只读，不再显示原来的刷新/编辑/新增按钮。
  await evaluate("relationTest.state.compositions[0].config.presentation.position='INLINE';relationTest.state.published=true;relationTest.settle()")
  await until("document.querySelector('#runtime .related-content-runtime input')?.value==='关联需求 all-2'")
  assert.equal(await evaluate("document.querySelector('#runtime .related-content-runtime:has(.linkage-form-preview) .related-content-runtime__header > span')===null"),true)
  assert.equal(await evaluate("document.querySelector('#runtime .related-content-runtime:has(.linkage-form-preview) .related-content-runtime__header button')"),null)
  await evaluate('relationTest.state.published=false;relationTest.settle()')

  // 普通关联在当前区域独立保存；组成子表单后续回归仍只更新主表草稿。
  const inlineForm = "document.querySelector('#runtime .related-content-runtime:has(.linkage-form-preview)')"
  const inlineInput = `${inlineForm}.querySelector('input')`
  const inlineWrites = "relationTest.state.calls.filter(c=>c.method==='post'&&(c.url==='/entity-data'||c.url.endsWith('/update'))&&c.body.compositionContext?.recordId)"
  async function fillInline(value) {
    await evaluate(`(()=>{const input=${inlineInput};input.value=${JSON.stringify(value)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.blur()})()`)
    await evaluate('relationTest.settle()')
  }
  await evaluate("relationTest.state.inlineSaveTest=true;relationTest.state.childRecords={};relationTest.state.published=true;relationTest.settle()")
  await until(`${inlineInput}?.value==='关联需求 all-2' && !${inlineInput}.disabled`)
  assert.deepEqual(await evaluate(`[...${inlineForm}.querySelectorAll('.related-content-runtime__header button')].map(b=>b.textContent.trim())`),['保存子表单'])
  const originalSource = await evaluate('JSON.stringify(relationTest.state.sourceData)')
  await fillInline('')
  await click('保存子表单',inlineForm)
  assert.equal(await evaluate(`${inlineWrites}.length`),0,'必填校验失败不得提交')
  assert.ok(await evaluate(`${inlineForm}.querySelector('.el-form-item__error')`),'必填错误定位在当前区域')
  await fillInline('当前页面修改的需求')
  await evaluate('relationTest.state.inlineSaveFailure=true')
  await click('保存子表单',inlineForm)
  assert.equal(await evaluate(`${inlineInput}.value`),'当前页面修改的需求','提交失败保留输入')
  assert.equal(await evaluate('JSON.stringify(relationTest.state.sourceData)'),originalSource,'关联草稿不反向写入主表')
  assert.equal(await evaluate("[...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length).length"),0,'编辑和保存不打开第二个表单弹窗')
  await evaluate('relationTest.state.inlineSaveFailure=false')
  await click('保存子表单',inlineForm)
  await until(`relationTest.state.childRecords['all-2']?.reqName==='当前页面修改的需求' && !${inlineForm}.querySelector('.el-loading-mask')`)
  const updateCall = await evaluate(`JSON.parse(JSON.stringify(${inlineWrites}.at(-1)))`)
  assert.equal(updateCall.url,'/entity-data/entity/ZDWREQ/detail/child-all-2/update')
  assert.equal(updateCall.body.compositionContext.recordId,'all-2')
  assert.equal(updateCall.body.formConfigHash,'req-graph')
  assert.equal(updateCall.body.startProcess,false)

  // 新页面通过统一参数初始化；用户修改后保存创建，再保存更新同一条记录。
  await evaluate("relationTest.state.compositions[0].config.parameterMappings=[{parameter:'sourceName',sourceType:'FIELD',sourceField:'name'}];relationTest.state.sourceRecordId='missing';relationTest.settle()")
  await until(`${inlineInput}?.value===relationTest.state.sourceData.name && !${inlineInput}.disabled`)
  await fillInline('第一次保存创建的需求')
  await click('保存子表单',inlineForm)
  await until(`${inlineInput}?.value==='第一次保存创建的需求' && !${inlineForm}.querySelector('.el-loading-mask')`)
  const createCall = await evaluate(`JSON.parse(JSON.stringify(${inlineWrites}.at(-1)))`)
  assert.equal(createCall.url,'/entity-data')
  assert.equal(createCall.body.data.reqName,'第一次保存创建的需求','参数预填允许用户修改，保存不重新覆盖输入')
  assert.equal(createCall.body.compositionContext.recordId,'missing')
  await fillInline('新需求再次修改')
  await click('保存子表单',inlineForm)
  assert.equal(await evaluate(`${inlineWrites}.at(-1).url`),'/entity-data/entity/ZDWREQ/detail/child-missing/update')
  assert.equal(await evaluate(`${inlineWrites}.filter(c=>c.url==='/entity-data').length`),1)

  // 主表映射参数变化不能覆盖正在编辑的子表单；保存成功后才同步最新上下文。
  await evaluate("relationTest.state.compositions[0].config.parameterMappings=[{parameter:'sourceName',sourceType:'FIELD',sourceField:'name'}];relationTest.settle()")
  await fillInline('等待保存的草稿')
  await evaluate("relationTest.state.sourceData.name='主表新参数';relationTest.settle()")
  assert.equal(await evaluate(`${inlineInput}.value`),'等待保存的草稿')
  await click('保存子表单',inlineForm)

  // 重复点击只提交一次；切换来源后旧保存回包不能覆盖新记录。
  await fillInline('旧记录的延迟提交')
  await evaluate('relationTest.state.inlineSaveDelay=1400')
  const writesBefore = await evaluate(`${inlineWrites}.length`)
  await evaluate(`(()=>{const button=${inlineForm}.querySelector('.related-content-runtime__header button');button.click();button.click()})()`)
  await until(`${inlineWrites}.length===${writesBefore+1}`)
  assert.equal(await evaluate(`${inlineForm}.querySelector('[inert]')!==null`),true,'提交时锁定当前区域输入')
  await evaluate("relationTest.state.sourceRecordId='all-3';relationTest.settle()")
  await until(`${inlineInput}?.value==='关联需求 all-3' && !${inlineForm}.querySelector('.el-loading-mask')`)
  assert.equal(await evaluate(`${inlineWrites}.length`),writesBefore+1)
  assert.equal(await evaluate(`${inlineInput}.value`),'关联需求 all-3','旧提交回包不影响新来源')
  await evaluate('relationTest.state.inlineSaveDelay=0')

  // 编辑权限与发布表单只读规则都必须限制当前区域和保存按钮。
  for (const readonlyFlag of ['inlineViewOnly','inlineFormReadonly']) {
    await evaluate(`relationTest.state.published=false;relationTest.state.${readonlyFlag}=true;relationTest.settle()`)
    await evaluate('relationTest.state.published=true;relationTest.settle()')
    await until(`${inlineInput}?.disabled===true`)
    assert.equal(await evaluate(`${inlineForm}.querySelector('.related-content-runtime__header button')`),null)
    await evaluate(`relationTest.state.published=false;relationTest.state.${readonlyFlag}=false;relationTest.settle()`)
  }

  // 真实主表弹窗保护独立草稿：取消关闭保留输入，主表保存不会把子草稿标为已保存。
  await evaluate('relationTest.state.nativeHostTest=true;relationTest.settle()')
  await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
  const nativeHost="document.querySelector('.entity-data-form-dialog')"
  const nativeChildInput=`${nativeHost}?.querySelector('.related-content-runtime input')`
  await until(`${nativeChildInput}?.value==='关联需求 all-3'`)
  await evaluate(`(()=>{const input=${nativeChildInput};input.value='主表弹窗中的子草稿';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))})()`)
  await evaluate('relationTest.settle()')
  await evaluate(`${nativeHost}.querySelector('.el-dialog__headerbtn').click()`)
  await until("document.querySelector('.el-message-box')?.innerText.includes('子表单有未保存的修改')")
  await click('继续填写',"document.querySelector('.el-message-box')")
  assert.equal(await evaluate(`${nativeChildInput}.value`),'主表弹窗中的子草稿')
  const childWritesBeforeMainSave=await evaluate(`${inlineWrites}.length`)
  await click('保存修改',nativeHost)
  await until("relationTest.state.mainSavedData?.name==='主表名称'")
  assert.equal(await evaluate(`${inlineWrites}.length`),childWritesBeforeMainSave,'保存主表不提交普通关联子草稿')
  assert.ok(await evaluate(`${nativeHost}.getClientRects().length`),'保留弹窗以继续保存子表单')
  assert.equal(await evaluate(`${nativeChildInput}.value`),'主表弹窗中的子草稿')
  await click('保存子表单',`${nativeHost}.querySelector('.related-content-runtime')`)
  await until("relationTest.state.childRecords['all-3']?.reqName==='主表弹窗中的子草稿'")
  const inlineScreenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true})
  writeFileSync('/tmp/flow-inline-related-form-save.png',Buffer.from(inlineScreenshot.data,'base64'))
  await evaluate(`${nativeHost}.querySelector('.el-dialog__headerbtn').click();relationTest.settle()`)
  await until(`!${nativeHost}?.getClientRects().length`)
  await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
  await until(`${nativeChildInput}?.value==='主表弹窗中的子草稿'`)
  await evaluate(`(()=>{const input=${nativeChildInput};input.value='明确丢弃的草稿';input.dispatchEvent(new Event('input',{bubbles:true}))})()`)
  await evaluate(`${nativeHost}.querySelector('.el-dialog__headerbtn').click();relationTest.settle()`)
  await until("document.querySelector('.el-message-box')?.innerText.includes('子表单有未保存的修改')")
  await click('放弃修改',"document.querySelector('.el-message-box')")
  await until(`!${nativeHost}?.getClientRects().length`)
  await evaluate("relationTest.hostDialog.value.openEdit({id:'all-3'},{form:relationTest.hostForm()});relationTest.settle()")
  await until(`${nativeChildInput}?.value==='主表弹窗中的子草稿'`)
  await evaluate(`${nativeHost}.querySelector('.el-dialog__headerbtn').click();relationTest.settle()`)
  await until(`!${nativeHost}?.getClientRects().length`)
  await evaluate('relationTest.state.nativeHostTest=false;relationTest.settle()')
  await evaluate(`relationTest.state.inlineSaveTest=false;relationTest.state.childRecords={};relationTest.state.sourceRecordId='all-2';relationTest.state.sourceData=JSON.parse(${JSON.stringify(originalSource)});relationTest.state.compositions[0].config.parameterMappings=[];relationTest.state.calls=[];relationTest.settle()`)

  // 失效的关系不可退回默认值或继续保存。
  for (const invalid of ['disabled','missing','failure']) {
    await evaluate(`relationTest.relations[0].enabled=${invalid!=='disabled'};relationTest.relations[0].deleted=${invalid==='missing'?1:0};relationTest.state.relationFailure=${invalid==='failure'};relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()`)
    const expectedError = { disabled: '此实体关系已停用', missing: '此实体关系已不存在', failure: '模拟实体关系加载失败' }[invalid]
    await until(`${dialog}?.querySelector('.relation-binding-error')?.textContent.includes(${JSON.stringify(expectedError)})`)
    assert.equal(await evaluate(`[...${dialog}.querySelectorAll('button')].find(b=>b.textContent.trim()==='保存显示配置').disabled`),true)
    await click('取消',dialog)
  }
  await evaluate('relationTest.relations[0].enabled=true;relationTest.relations[0].deleted=0;relationTest.state.relationFailure=false')

  // 关联内容只配置查看、新增和编辑；再次保存后继续保留实体关系绑定和编辑权限。
  await evaluate("relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()")
  await until(`${relationMatch}?.textContent.trim()==='ZDWREQ.acceptance_id = 当前记录.id' && ${targetContentSelection}?.textContent.trim()==='需求详情表单 (details)'`)
  assert.deepEqual(await evaluate(`[...${dialog}.querySelectorAll('.action-options .el-checkbox__label')].map(item=>item.textContent.trim())`), ['仅查看', '新增记录', '编辑记录'])
  await click('保存显示配置',dialog)
  await until('relationTest.state.compositions[0].revision===4')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.specialHandling.mode'),'NONE')
  assert.deepEqual(await evaluate('Array.from(relationTest.state.compositions[0].config.actions)'),['EDIT'])

  // 关系数量发生变化后重新选择页面，不能保留不兼容的单条表单。
  await evaluate("relationTest.relations[0].relationType='ONE_TO_MANY';relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()")
  await until(`${relationMatch}?.textContent.trim()==='ZDWREQ.acceptance_id = 当前记录.id' && ${targetContentSelection}?.textContent.trim()==='选择已发布列表'`)
  assert.equal(await evaluate(`${targetContentSelection}.classList.contains('is-transparent')`),true,'关系变成一对多后必须清空原来的单条表单选择')
  await click('保存显示配置',dialog)
  assert.equal(await evaluate('relationTest.state.compositions[0].revision'),4)
  await evaluate(`${dialog}.querySelector('.target-content-select .el-select__wrapper').click()`)
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.includes('需求明细列表'))")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('需求明细列表')).click()")
  await click('保存显示配置',dialog)
  await until('relationTest.state.compositions[0].revision===5')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.target.contentType'),'LIST')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.target.contentId'),'req-list')
  assert.equal(await evaluate('relationTest.state.compositions[0].config.relation.relationCode'),'reqRelation')
  assert.deepEqual(await evaluate('Array.from(relationTest.state.compositions[0].config.actions)'),['EDIT'], '列表也支持独立编辑，切换页面后应保留兼容动作')

  // 新建业务关联必须选择目录中的关系；扩展页面另有入口，不能再次输入字段匹配条件。
  await click('新增关联内容',"document.querySelector('.related-content-panel')")
  await until(`${dialog}?.querySelector('.relation-binding-select .el-select__placeholder')?.textContent.trim()==='选择已定义的实体关系'`)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.el-steps .el-step').length`),2)
  assert.equal(await evaluate(`[...${dialog}.querySelectorAll('button')].find(b=>b.textContent.trim()==='保存显示配置').disabled`),true)
  await evaluate(`${dialog}.querySelector('.relation-binding-select .el-select__wrapper').click()`)
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.includes('所属需求'))")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('所属需求')).click()")
  await until(`${relationMatch}?.textContent.trim()==='当前记录.acceptance_id = ZDWREQ.id'`)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.relation-methods').length`),0)
  await evaluate(`${dialog}.querySelector('.target-content-select .el-select__wrapper').click()`)
  await until("[...document.querySelectorAll('.el-select-dropdown__item')].some(e=>e.getClientRects().length&&e.textContent.includes('需求详情表单'))")
  await evaluate("[...document.querySelectorAll('.el-select-dropdown__item')].find(e=>e.getClientRects().length&&e.textContent.includes('需求详情表单')).click()")
  await click('保存显示配置',dialog)
  await until("relationTest.state.compositions.some(c=>c.config.relation.direction==='REVERSE')")
  assert.equal(await evaluate("relationTest.state.compositions.find(c=>c.config.relation.direction==='REVERSE').config.target.contentType"),'FORM')
  await click('扩展页面',"document.querySelector('.related-content-panel')")
  await until(`${dialog}?.innerText.includes('扩展页面')`)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.el-steps .el-step').length`),4)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.relation-binding-select, .relation-match-rule, .inherited-target').length`),0,'扩展页面不应继承业务实体关系')
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.relation-methods button').length`),1)
  await click('取消',dialog)

  // 关联操作不再显示主从跳转或新增初值；左侧真实主从编辑入口保持可用。
  await evaluate('relationTest.relatedPanel.value.open(relationTest.state.compositions[0]);relationTest.settle()')
  await until(`${relationMatch}?.textContent.trim()==='ZDWREQ.acceptance_id = 当前记录.id' && ${targetContentSelection}?.textContent.trim()==='需求明细列表 (requirements)'`)
  await click('下一步', dialog)
  assert.equal(await evaluate(`/需要随主表单一起保存|新增记录设置|新增初值|目标内容独立保存|最终权限如何确定/.test(${dialog}.innerText)`),false)
  await click('取消', dialog)
  await evaluate("document.querySelector('.related-content-panel .el-drawer__close-btn').click();relationTest.settle()")
  await until("!document.querySelector('.related-content-panel')?.getClientRects().length")
  // 组成关系直接生成主从编辑节点，不调用实体字段创建接口，也不创建独立保存的展示配置。
  await click('添加主从编辑', "document.getElementById('picker')")
  await until("document.body.innerText.includes('选择子记录使用的表单')")
  assert.equal(await evaluate("document.body.innerText.includes('随主表单统一提交')"),true)
  await click('添加到表单')
  await until('relationTest.state.editorNodes.length===1')
  const aggregate=await evaluate('JSON.parse(JSON.stringify(relationTest.state.editorNodes[0]))')
  assert.equal(aggregate.bindingType,'RELATION')
  assert.equal(aggregate.bindingRef,'ownedDetails')
  assert.equal(aggregate.nodeType,'REPEATER')
  assert.equal(aggregate.childFormId,'req-form')
  assert.equal(aggregate.childFormReleaseId,undefined)
  assert.equal(aggregate.childFormReleaseVersion,undefined)
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
  await until("document.querySelector('#placement-canvas .form-node-design-entry:has(> [data-node-id=\"text-node\"]) > .relation-design-preview input')")
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
    ['nested-key','.form-node-design-entry:has(> [data-node-id="nested"])','INLINE'],
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
  if (!compositionEditorOnly) {
  // 前面的权限用例关闭了第二行能力；参数用例须恢复它，避免按钮被正确禁用后误判为传参失败。
  await evaluate("relationTest.state.tableRows[1].actionCapabilities.requirements={visible:true,enabled:true};relationTest.state.published=false;relationTest.state.buttons=[{key:'requirements',type:'custom',customMode:'open-list',label:'参数查询',targetEntityCode:'ZDWREQ',targetListKey:'requirements',parameterMappings:[{parameter:'sourceName',sourceType:'FIELD',sourceField:'name'}]}];relationTest.state.selectedRows=[];relationTest.state.listButtonTest=true;relationTest.settle()")
  const queriesBefore=await evaluate("relationTest.state.calls.filter(c=>c.url.endsWith('/requirements/query')).length")
  await click('打开需求',"document.getElementById('button-runtime')")
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url.endsWith('/requirements/query')).length"),queriesBefore)
  await evaluate('relationTest.state.selectedRows=[...relationTest.state.tableRows];relationTest.settle()')
  await click('打开需求',"document.getElementById('button-runtime')")
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url.endsWith('/requirements/query')).length"),queriesBefore)
  await evaluate('relationTest.state.selectedRows=[relationTest.state.tableRows[1]];relationTest.settle()')
  await click('打开需求',"document.getElementById('button-runtime')")
  await until("relationTest.state.calls.some(c=>c.url.endsWith('/requirements/query')&&c.body.context?.parameters?.sourceName==='验收二')")
  }

  // 真实设计页必须接通导航、节点属性及保存重载，而不仅是独立面板能触发事件。
  await evaluate("document.querySelector('.el-drawer__close-btn')?.click();relationTest.state.published=false;relationTest.state.listButtonTest=false;relationTest.state.parameterTest=false;relationTest.relations.find(r=>r.relationCode==='ownedDetails').relationType='ONE_TO_ONE';relationTest.state.designerTest=true;relationTest.settle()")
  await until("document.querySelector('#real-designer .form-relation-picker .relation-item') && !document.querySelector('#real-designer .el-loading-mask')")

  // 已添加的普通关联双击直接打开属性抽屉，不打开关系向导；保存重开须刷新宿主修订号并保留其他配置。
  const ordinaryTitle = "[...document.querySelectorAll('#real-designer .relation-item .relation-property-title')].find(e=>e.textContent==='所属需求')"
  const beforeProperties = await evaluate('JSON.parse(JSON.stringify(relationTest.state.compositions[0]))')
  await doubleClick(ordinaryTitle)
  const propertyDrawer = "[...document.querySelectorAll('.node-property-drawer')].find(e=>e.getClientRects().length)"
  await until(`${propertyDrawer}?.querySelector('.form-content-display-settings input')`)
  assert.equal(await evaluate(`[...${propertyDrawer}.querySelectorAll('.node-settings-tabs .el-tabs__item')].some(e=>e.textContent==='数据与扩展')`),false,'普通关联移除空的扩展页签')
  assert.equal(await evaluate(`${propertyDrawer}.querySelectorAll('.special-choice').length`),0,'普通关联不得渲染扩展组件配置')
  assert.equal(await evaluate("[...document.querySelectorAll('.el-dialog.related-content-config-dialog,.form-settings-drawer')].some(e=>e.getClientRects().length)"),false,'双击不得跳入关系展示向导或表单设置')
  assert.equal(await evaluate(`${propertyDrawer}.querySelector('input').disabled`),false)
  await evaluate(`(async()=>{const input=${propertyDrawer}.querySelector('input');input.value='双击打开的关联表单';input.dispatchEvent(new Event('input',{bubbles:true}));const toggle=${propertyDrawer}.querySelector('.el-switch');toggle.click();await relationTest.settle();toggle.click();await relationTest.settle()})()`)
  await click('保存当前属性', propertyDrawer)
  await until('relationTest.state.compositions[0].revision > '+beforeProperties.revision)
  await until("document.querySelector('#real-designer .relation-design-preview header strong')?.textContent==='双击打开的关联表单'")
  const propertySaved = await evaluate('JSON.parse(JSON.stringify(relationTest.state.compositions[0]))')
  assert.equal(propertySaved.config.presentation.showTitle,true)
  assert.equal(propertySaved.expectedOwnerRevision,1,'双击属性必须读取宿主当前修订号，不能使用侧栏旧值')
  assert.deepEqual(propertySaved.config.actions,beforeProperties.config.actions)
  assert.deepEqual(propertySaved.config.relation,beforeProperties.config.relation)
  assert.deepEqual(propertySaved.config.parameterMappings,beforeProperties.config.parameterMappings)
  assert.equal(propertySaved.config.presentation.position,beforeProperties.config.presentation.position)
  assert.equal(propertySaved.revision,beforeProperties.revision+1)
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  await doubleClick("document.querySelector('#real-designer .relation-design-preview header .relation-property-title')")
  await until(`${propertyDrawer}?.querySelector('input')?.value==='双击打开的关联表单'`)
  assert.equal(await evaluate(`${propertyDrawer}.querySelector('.el-switch').classList.contains('is-checked')`),true)
  const propertyScreenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync('/tmp/flow-related-content-property-drawer.png', Buffer.from(propertyScreenshot.data, 'base64'))
  await evaluate(`(()=>{const input=${propertyDrawer}.querySelector('input');input.value='关闭后保留草稿';input.dispatchEvent(new Event('input',{bubbles:true}))})()`)
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  assert.equal(await evaluate('relationTest.state.compositions[0].revision'),propertySaved.revision)
  assert.equal(await evaluate("document.querySelector('#real-designer .relation-design-preview header strong').textContent"),'关闭后保留草稿')

  await click('已添加 · 配置', "[...document.querySelectorAll('#real-designer .relation-item')].find(e=>e.innerText.includes('所属需求'))")
  await until(`${propertyDrawer}?.querySelector('.el-tabs__item.is-active')?.textContent==='子页面'`)
  assert.equal(await evaluate(`${propertyDrawer}.querySelectorAll('.node-settings-tabs .el-tabs__item').length`),4)
  await evaluate(`[...${propertyDrawer}.querySelectorAll('.el-tabs__item')].find(e=>e.textContent==='基础与布局').click();relationTest.settle()`)
  await until(`${propertyDrawer}.querySelector('input')?.value==='关闭后保留草稿'`)
  await evaluate(`(()=>{const input=${propertyDrawer}.querySelector('input');input.value='双击打开的关联表单';input.dispatchEvent(new Event('input',{bubbles:true}));${propertyDrawer}.querySelector('.el-drawer__close-btn').click()})()`)

  // 同一属性入口在 Tab 内递归转发，且不会被外层 Tab 集合的双击事件抢走。
  await evaluate(`relationTest.state.designerNodes=${JSON.stringify([
    {id:'property-tabs',nodeKey:'property-tabs',nodeType:'TAB_SET',propsDocument:'{}'},
    {id:'property-tab',nodeKey:'property-tab',parentId:'property-tabs',nodeType:'TAB',propsDocument:'{"label":"关系属性页"}'}
  ])};relationTest.state.compositions[0].anchorType='FORM_NODE';relationTest.state.compositions[0].anchorKey='property-tab';relationTest.state.compositions[0].config.presentation.position='TAB';relationTest.state.designerRemount=1;relationTest.settle()`)
  await until("document.querySelector('#real-designer .design-tab-panel .relation-design-preview header .relation-property-title')")
  await doubleClick("document.querySelector('#real-designer .design-tab-panel .relation-design-preview header .relation-property-title')")
  await until(`${propertyDrawer}?.querySelector('input')?.value==='双击打开的关联表单'`)
  assert.equal(await evaluate(`${propertyDrawer}.querySelector('.node-property-heading-title').textContent`),'双击打开的关联表单属性','嵌套关联标题应选择关系而非 Tab 节点')
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  await evaluate("relationTest.state.designerNodes=[];relationTest.state.compositions[0].anchorType='OWNER';relationTest.state.compositions[0].anchorKey='';relationTest.state.compositions[0].config.presentation.position='INLINE';relationTest.state.designerRemount=2;relationTest.settle()")
  await until("document.querySelector('#real-designer .relation-design-preview header .relation-property-title')")
  await click('设置',"document.getElementById('real-designer')")
  await evaluate("[...document.querySelectorAll('.form-settings-tabs .el-tabs__item')].find(e=>e.textContent.includes('关联内容')).click();relationTest.settle()")
  await until("document.querySelector('.form-settings-drawer .content-card')")
  await click('编辑',"document.querySelector('.form-settings-drawer .content-card')")
  await until(`${propertyDrawer}?.querySelector('.el-tabs__item.is-active')?.textContent==='子页面'`)
  assert.equal(await evaluate(`/需要随主表单一起保存|新增记录设置|新增初值|目标内容独立保存|最终权限如何确定/.test(${propertyDrawer}.innerText)`),false)
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();document.querySelector('.form-settings-drawer .el-drawer__close-btn')?.click();relationTest.settle()`)
  await until("document.querySelector('#real-designer .relation-item')")
  await click('添加主从编辑',"document.getElementById('real-designer')")
  await until("document.body.innerText.includes('选择子记录使用的表单')")
  await click('添加到表单')
  const nodeDrawer = "[...document.querySelectorAll('.el-drawer')].find(e=>e.getClientRects().length&&e.querySelector('.node-settings-tabs'))"
  await until(`${nodeDrawer}?.innerText.includes('组成明细')`)
  assert.equal(await evaluate(`[...${nodeDrawer}.querySelectorAll('.node-settings-tabs .el-tabs__item')].some(e=>e.textContent==='数据与扩展')`),true,'真实组成节点仍保留自身的字段数据配置')
  await evaluate(`[...${nodeDrawer}.querySelectorAll('.el-tabs__item')].find(e=>e.textContent.includes('子页面')).click();relationTest.settle()`)
  const childSettings = `[...${nodeDrawer}.querySelectorAll('.settings-section')].find(e=>e.querySelector('.settings-section__heading strong')?.textContent==='子表单配置')`
  assert.equal(await evaluate(`${childSettings}.classList.contains('is-disabled')`),false,'组成节点的子页面属性必须激活')
  await evaluate(`${childSettings}.querySelector('.settings-section__header').click();relationTest.settle()`)
  await until(`${nodeDrawer}.innerText.includes('一对一')`)
  await evaluate(`[...${nodeDrawer}.querySelectorAll('.el-tabs__item')].find(e=>e.textContent==='基础与布局').click();relationTest.settle()`)
  const relationNameInput = `${nodeDrawer}.querySelector('.form-content-display-settings input.el-input__inner')`
  assert.equal(await evaluate(`${relationNameInput}.disabled`),false)
  await evaluate(`${relationNameInput}.value='随主表保存的子表单';${relationNameInput}.dispatchEvent(new Event('input',{bubbles:true}));${nodeDrawer}.querySelector('.form-content-display-settings .el-switch').click();${nodeDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  await click('保存全部',"document.getElementById('real-designer')")
  await until('relationTest.state.designerSaves.length===1')
  const persisted=await evaluate('JSON.parse(JSON.stringify(relationTest.state.designerNodes[0]))')
  const persistedProps=JSON.parse(persisted.propsDocument)
  assert.equal(persisted.nodeType,'SUB_FORM')
  assert.equal(persisted.bindingType,'RELATION')
  assert.equal(persisted.bindingRef,'ownedDetails')
  assert.equal(persistedProps.childFormId,'req-form')
  assert.equal(persistedProps.childFormReleaseVersion,undefined)
  assert.equal(persistedProps.componentProps.subFormConfig.childFormReleaseVersion,undefined)
  assert.equal(persistedProps.componentProps.subFormConfig.relationType,'ONE_TO_ONE')
  assert.equal(persistedProps.componentProps.subFormConfig.showTitle,false)
  await evaluate('relationTest.state.designerRemount=3;relationTest.settle()')
  await until("document.querySelector('#real-designer .form-node-design-item[data-node-type=\"SUB_FORM\"]') && !document.querySelector('#real-designer .el-loading-mask')")
  await doubleClick("[...document.querySelectorAll('#real-designer .relation-item .relation-property-title')].find(e=>e.textContent==='组成明细')")
  await until(`${nodeDrawer}?.innerText.includes('随主表保存的子表单')`)
  await evaluate(`[...${nodeDrawer}.querySelectorAll('.el-tabs__item')].find(e=>e.textContent.includes('子页面')).click();relationTest.settle()`)
  assert.equal(await evaluate(`${childSettings}.classList.contains('is-disabled')`),false,'保存重开仍须识别关系节点')
  await evaluate(`${childSettings}.querySelector('.settings-section__header').click();relationTest.settle()`)
  assert.equal(await evaluate(`${nodeDrawer}.innerText.includes('一对一')`),true)
  await evaluate(`[...${nodeDrawer}.querySelectorAll('.el-tabs__item')].find(e=>e.textContent==='基础与布局').click();relationTest.settle()`)
  assert.equal(await evaluate(`${nodeDrawer}.querySelector('.form-content-display-settings .el-switch').classList.contains('is-checked')`),false)

  // 新增关联列表只进入本地草稿，列表专属设置与外层高度分别保存。
  await evaluate(`${nodeDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  await evaluate("[...document.querySelectorAll('#real-designer button')].find(e=>e.textContent.trim()==='显示全部关系')?.click();relationTest.settle()")
  const listRelation = "[...document.querySelectorAll('#real-designer .relation-item')].find(e=>e.querySelector('strong')?.textContent==='需求明细')"
  await click('选择列表',listRelation)
  await click('添加到表单')
  await until(`${propertyDrawer}?.innerText.includes('子列表配置')`)
  assert.equal(await evaluate('relationTest.state.compositions.length'),1,'新增关系应留在本地草稿')
  async function setPropertyNumber(label, value) {
    await evaluate(`(()=>{const row=[...${propertyDrawer}.querySelectorAll('.el-form-item')].find(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent.trim()===${JSON.stringify(label)});const input=row.querySelector('input');input.value=${JSON.stringify(String(value))};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.dispatchEvent(new FocusEvent('blur',{bubbles:true}))})();relationTest.settle()`)
  }
  for (const label of ['显示查询','显示分页','显示工具栏','显示操作列']) {
    await evaluate(`(()=>{const row=[...${propertyDrawer}.querySelectorAll('.el-form-item')].find(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent.trim()===${JSON.stringify(label)});row.querySelector('.el-switch').click()})();relationTest.settle()`)
  }
  await setPropertyNumber('每页条数',37)
  await setPropertyNumber('列表最大高度',240)
  await evaluate(`[...${propertyDrawer}.querySelectorAll('.el-tabs__item')].find(e=>e.textContent==='基础与布局').click();relationTest.settle()`)
  assert.equal(await evaluate(`${propertyDrawer}.innerText.includes('每页条数')`),false,'列表专属设置不能出现在基础与布局')
  await setPropertyNumber('外层高度',600)
  await setPropertyNumber('区域宽度',75)
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  await click('保存全部',"document.getElementById('real-designer')")
  await until('relationTest.state.compositions.length===2')
  const childList=await evaluate("JSON.parse(JSON.stringify(relationTest.state.compositions.find(c=>c.config.target.contentType==='LIST')))")
  assert.deepEqual(childList.config.presentation.listOptions,{showSearch:false,showPagination:false,showToolbar:false,showRowActions:false,pageSize:37,maxHeight:240})
  assert.deepEqual(childList.config.presentation.layout,{height:600,width:75})

  // 更新失败保留整份属性草稿；修复后保存全部能继续完成，不提示全部成功。
  await click('已添加 · 配置',listRelation)
  await until(`${propertyDrawer}?.querySelector('.el-tabs__item.is-active')?.textContent==='子页面'`)
  await setPropertyNumber('每页条数',38)
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.state.failRelatedUpdate=true;relationTest.settle()`)
  await click('保存全部',"document.getElementById('real-designer')")
  await until("document.body.innerText.includes('模拟关联属性保存失败')")
  assert.equal(await evaluate("relationTest.state.compositions.find(c=>c.config.target.contentType==='LIST').config.presentation.listOptions.pageSize"),37)
  await click('已添加 · 配置',listRelation)
  await until(`${propertyDrawer}?.querySelector('.el-tabs__item.is-active')?.textContent==='子页面'`)
  assert.equal(await evaluate(`[...${propertyDrawer}.querySelectorAll('.el-form-item')].find(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent.trim()==='每页条数').querySelector('input').value`),'38')
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.state.failRelatedUpdate=false;relationTest.settle()`)
  await click('保存全部',"document.getElementById('real-designer')")
  await until("relationTest.state.compositions.find(c=>c.config.target.contentType==='LIST').config.presentation.listOptions.pageSize===38")
  await evaluate('relationTest.state.designerRemount=4;relationTest.settle()')
  await until("document.querySelector('#real-designer .relation-item') && !document.querySelector('#real-designer .el-loading-mask')")
  await click('已添加 · 配置',listRelation)
  await until(`${propertyDrawer}?.querySelector('.el-tabs__item.is-active')?.textContent==='子页面'`)
  assert.equal(await evaluate(`[...${propertyDrawer}.querySelectorAll('.el-form-item')].find(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent.trim()==='每页条数').querySelector('input').value`),'38')

  // 发布会先保存当前未保存属性，再预检并发布；发布运行时消费相同子列表选项。
  await setPropertyNumber('每页条数',39)
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.state.publicationEnabled=true;relationTest.settle()`)
  await evaluate("[...document.querySelectorAll('#real-designer .header-right button')].find(e=>e.textContent.replace(/\\s/g,'')==='发布').click();relationTest.settle()")
  await until("[...document.querySelectorAll('.el-dialog')].some(e=>e.getClientRects().length&&e.innerText.includes('发布说明'))")
  assert.equal(await evaluate("relationTest.state.compositions.find(c=>c.config.target.contentType==='LIST').config.presentation.listOptions.pageSize"),39)
  await click('确认发布',"[...document.querySelectorAll('.el-dialog')].find(e=>e.getClientRects().length&&e.innerText.includes('发布说明'))")
  await until('relationTest.state.designerPublished===true')
  await evaluate("relationTest.state.published=true;relationTest.state.sourceRecordId='all-1';relationTest.settle()")
  await until("relationTest.state.calls.some(c=>c.url.endsWith('/requirements/query')&&c.body.pageSize===39)")
  const listRuntime="[...document.querySelectorAll('#runtime .related-content-runtime')].find(e=>e.innerText.includes('需求明细'))"
  assert.equal(await evaluate(`${listRuntime}.querySelector('.el-pagination')===null`),true)
  assert.equal(await evaluate(`${listRuntime}.style.height`),'600px')
  assert.equal(await evaluate(`${listRuntime}.style.width`),'75%')
  assert.equal(await evaluate(`${listRuntime}.querySelector('.entity-data-list')?.__vueParentComponent.props.maxHeight`),240)
  const optionsScreenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false})
  writeFileSync('/tmp/flow-related-content-unified-properties.png',Buffer.from(optionsScreenshot.data,'base64'))

  // 已同步表单也能撤销尚未落库的属性修改/删除，不能误调用服务端草稿回退。
  await click('已添加 · 配置',listRelation)
  await until(`${propertyDrawer}?.querySelector('.el-tabs__item.is-active')?.textContent==='子页面'`)
  await setPropertyNumber('每页条数',40)
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  await click('撤销',"document.getElementById('real-designer')")
  await click('确认撤销')
  await until("document.body.innerText.includes('已撤销当前页面未保存的修改') && !document.querySelector('#real-designer .el-loading-mask')")
  await click('已添加 · 配置',listRelation)
  await until(`${propertyDrawer}?.querySelector('.el-tabs__item.is-active')?.textContent==='子页面'`)
  assert.equal(await evaluate(`[...${propertyDrawer}.querySelectorAll('.el-form-item')].find(e=>e.getClientRects().length&&e.querySelector('.el-form-item__label')?.textContent.trim()==='每页条数').querySelector('input').value`),'39')
  await evaluate(`${propertyDrawer}.querySelector('.el-drawer__close-btn').click();relationTest.settle()`)
  const childListPreview="[...document.querySelectorAll('#real-designer .relation-design-preview')].find(e=>e.innerText.includes('需求明细'))"
  await click('移除',childListPreview)
  await until("document.querySelector('.el-message-box__btns .el-button--primary')")
  await evaluate("document.querySelector('.el-message-box__btns .el-button--primary').click();relationTest.settle()")
  await until(`${childListPreview}===undefined`)
  assert.equal(await evaluate('relationTest.state.compositions.length'),2,'删除只变更本地草稿')
  await click('撤销',"document.getElementById('real-designer')")
  await click('确认撤销')
  await until(`${childListPreview} && !document.querySelector('#real-designer .el-loading-mask')`)
  assert.equal(await evaluate("relationTest.state.calls.filter(c=>c.url.includes('/discard')||c.method==='delete').length"),0,'纯本地撤销不触碰服务端发布基线')

  assert.deepEqual(errors, [])
  assert.deepEqual(await evaluate('Array.from(relationTest.state.unexpected)'), [], '出现未覆盖的 API 路径')
  console.log(compositionEditorOnly
    ? 'composition editor browser acceptance passed: actionable navigation, absent composition guidance, current publication without versions, aggregate child drafts, active child settings, one-to-one stable bindings, save and designer reload'
    : 'entity relation browser acceptance passed: inline related form create/update, validation, save failure retention, permission/readonly, duplicate submission and stale response isolation, independent child drafts protected across host save/close/discard; relation definition, form/list runtime, permissions and parameters; nested target relation isolation at owner/field/Tab anchors without changing standalone forms; unified five-tab properties, shared local drafts across sidebar/canvas/settings, nested Tab selection, composition nodes, save failure and retry, refresh retention, child list options, separate outer/list heights, automatic save before publication and local change/deletion undo')
  }
} finally {
  if(errors.length) console.error(errors)
  ws?.close(); browser?.kill(); await server?.close(); await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
