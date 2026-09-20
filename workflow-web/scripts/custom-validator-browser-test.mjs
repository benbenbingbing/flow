import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 真实组件验收；独立 Chrome 和模拟接口，不创建业务数据。
const harness = `
import { createApp, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Preview from '/src/components/FormPreviewLinkage.vue'
import Editor from '/src/components/FormCustomValidatorEditor.vue'
import SubRow from '/src/components/form-fields/components/SubFormRowRuntime.vue'
import DataFields from '/src/views/entity/components/EntityDataFormFields.vue'
import { registerProjectValidators } from '/src/project/validators/index.js'
import { AmountValidator } from '/src/project/validators/AmountValidator.js'
import { registerCustomValidator } from '/src/contracts/validator-registry.js'
import { registerCustomFormComponent } from '/src/utils/customComponentRegistry.js'
import request from '/src/utils/request.js'
request.defaults.adapter = async config => {
  // 模拟字段事件接口晚于 blur 返回，覆盖首次失焦的 change/blur 竞态。
  if (String(config.data).includes('blur-form')) await new Promise(resolve => setTimeout(resolve, 120))
  return { data: { code: 200, data: config.url.includes('resolve') ? [{id:'child-entity',entityCode:'child'}] : [] }, status:200, statusText:'OK',headers:{},config }
}
registerProjectValidators()
let blurCalls = 0
const blurValidator = new AmountValidator()
registerCustomValidator('blurAmount', {validate(value, context) { blurCalls++; return blurValidator.validate(value, context) }}, {supportedFieldTypes:['STRING']})
registerCustomValidator('childOnly', {validate: value => value <= 10 || '子表金额不能超过 10'}, {supportedEntityCodes:['child'],supportedFieldTypes:['DECIMAL']})
registerCustomFormComponent('TestCustom', {props:['modelValue'],setup:()=>()=>h('div','自绘表单，无 validate 方法')})
const config = (name='amount',params={maxAmount:1000}) => ({version:1,rules:[{name,version:1,params,triggers:['BLUR','CHANGE']}]})
const amount = {id:'amount-node',fieldCode:'amount',fieldLabel:'金额',fieldType:'DECIMAL',componentType:'number',validationRules:{customValidators:config()}}
const childField = {...amount,validationRules:{customValidators:config('childOnly',{})}}
const blurField = {id:'blur-amount',fieldCode:'amount',fieldLabel:'数据名称',fieldType:'STRING',componentType:'input',validationRules:{customValidators:{version:1,rules:[{name:'blurAmount',version:1,params:{},triggers:['BLUR']}]}}}
const amountNode = field => ({id:'amount-node',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'amount',propsDocument:{fieldCode:'amount',fieldType:'DECIMAL',componentType:'number',label:'金额'},rulesDocument:{validation:field.validationRules}})
const state=reactive({entityCode:'expense',record:{amount:1200},readonly:false,custom:false,nodeTree:true,mode:'edit',form:{formName:'自定义校验',layoutType:'vertical',fields:[amount],nodes:[
{id:'tabs',nodeType:'TAB_SET',propsDocument:{}},{id:'first',parentId:'tabs',nodeType:'TAB_PANE',propsDocument:{label:'基本'}},{id:'second',parentId:'tabs',nodeType:'TAB_PANE',propsDocument:{label:'金额'}},{...amountNode(amount),parentId:'second'}]}, childRecord:{amount:20}, childEntity:'child', childField, childNodes:[amountNode(childField)],
 legacyRecord:{details:[{amount:1200},{amount:5}]}, legacyForm:{fields:[{id:'details',fieldCode:'details',fieldType:'SUB_FORM',componentType:'sub_form',fields:[amount]}]},
 dataRecord:{data:{amount:1200}}, dataForm:{customComponent:'TestCustom',fields:[amount]},
 blurRecord:{amount:''},blurForm:{id:'blur-form',fields:[blurField]}
})
const preview=ref(),child=ref(),legacy=ref(),data=ref(),blurPreview=ref()
createApp({setup:()=>()=>h('main',{style:'padding:24px;max-width:900px'},[
 h('section',{id:'blur-runtime'},[h(Preview,{ref:blurPreview,form:state.blurForm,entityCode:'expense',mode:'edit',modelValue:state.blurRecord,'onUpdate:modelValue':v=>state.blurRecord=v}),h('button',{id:'blur-sink'},'下一项')]),
 h('section',{id:'editor'},[h(Editor,{field:state.form.fields[0],entityCode:state.entityCode,modelValue:state.form.fields[0].validationRules.customValidators,'onUpdate:modelValue':v=>{state.form.fields[0].validationRules.customValidators=v;state.form.nodes[3].rulesDocument.validation.customValidators=v}})]),
 h('section',{id:'runtime'},[h(Preview,{ref:preview,form:{...state.form,nodes:state.nodeTree?state.form.nodes:[],customComponent:state.custom?'TestCustom':undefined},entityCode:state.entityCode,mode:state.mode,readonly:state.readonly,modelValue:state.record,'onUpdate:modelValue':v=>state.record=v})]),
 h('section',{id:'child'},[h(SubRow,{ref:child,form:{},fields:[state.childField],nodes:state.childNodes,row:state.childRecord,mode:'edit',context:{entityCode:state.childEntity},'onUpdate:modelValue':v=>state.childRecord=v})]),
 h('section',{id:'legacy'},[h(Preview,{ref:legacy,form:state.legacyForm,entityCode:'expense',mode:'edit',modelValue:state.legacyRecord,'onUpdate:modelValue':v=>state.legacyRecord=v})]),
 h('section',{id:'data'},[h(DataFields,{ref:data,defaultForm:state.dataForm,entityCode:'expense',entityDefinition:{},entityFields:[amount],isEdit:false,skipDataSourcePrevalidation:true,formData:state.dataRecord,'onUpdate:formData':v=>state.dataRecord=v})])
])}).use(createPinia()).use(ElementPlus).mount('#app')
window.customTest={state,getBlurCalls:()=>blurCalls,validateBlur:()=>blurPreview.value.validate(),validate:()=>preview.value.validate(),validateChild:()=>child.value.validate(),validateLegacy:()=>legacy.value.validate(),validateData:()=>data.value.validate(),async settle(){await nextTick();await new Promise(resolve=>setTimeout(resolve,150));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.custom-validator-fixture-'))
writeFileSync(path.join(fixture,'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'custom-validator-chrome-'))
const chromePath = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
let browser, ws, server
let nextId = 0
const pending = new Map()
const browserErrors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function send(method, params = {}) {
  const id = ++nextId
  const result = new Promise((resolve, reject) => {
    const timer = setTimeout(() => {pending.delete(id);reject(new Error('CDP timeout: '+method))},15000)
    pending.set(id,{resolve:value=>{clearTimeout(timer);resolve(value)},reject:error=>{clearTimeout(timer);reject(error)}})
  })
  ws.send(JSON.stringify({id,method,params}))
  return result
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true})
  if(result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}
try {
  server = await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3399,strictPort:true}})
  await server.listen()
  browser = spawn(chromePath,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9399',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
  let targets
  for(let i=0;i<100;i++){try{targets=await (await fetch('http://127.0.0.1:9399/json/list')).json();break}catch{await sleep(100)}}
  assert.ok(targets?.length,'Chrome failed to start')
  ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
  ws.addEventListener('message',event=>{const data=JSON.parse(event.data); if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(new Error(JSON.stringify(data.error))):p.resolve(data.result)} if(data.method==='Runtime.exceptionThrown')browserErrors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
  await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
  await send('Runtime.enable');await send('Page.enable')
  await send('Page.navigate',{url:`http://127.0.0.1:3399/${path.basename(fixture)}/index.html`})
  for(let i=0;i<200;i++){if(await evaluate('Boolean(window.customTest)'))break;await sleep(100)}
  assert.equal(await evaluate('Boolean(window.customTest)'),true,browserErrors.join('\n'))
  await evaluate('customTest.settle()')
  // 使用真实键盘输入和焦点切换，第一次离开输入框后错误必须持续可见。
  await evaluate("document.querySelector('#blur-runtime input').focus()")
  await send('Input.insertText', { text: 'invalid amount' })
  await evaluate('customTest.settle()')
  assert.equal(await evaluate("document.querySelectorAll('#blur-runtime .custom-validation-error').length"),0,'仅配置 BLUR 时首次输入不提前报错')
  assert.equal(await evaluate('customTest.getBlurCalls()'),0,'首次输入不执行失焦规则')
  await evaluate("document.querySelector('#blur-sink').focus();customTest.settle()")
  for (let attempt = 0; attempt < 3; attempt++) {
    await evaluate('customTest.settle()')
    assert.match(await evaluate("document.querySelector('#blur-runtime .custom-validation-error')?.textContent || ''"),/金额必须是有效数字/,'延迟 change 不能清空首次 blur 错误')
    assert.equal(await evaluate("document.querySelectorAll('#blur-runtime .el-alert').length"),0,'错误只显示在输入框下方')
  }
  await evaluate("document.querySelector('#blur-runtime input').focus();document.querySelector('#blur-sink').focus();customTest.settle()")
  assert.match(await evaluate("document.querySelector('#blur-runtime .custom-validation-error')?.textContent || ''"),/金额必须是有效数字/,'再次失焦保持相同提示')
  let blurCallsBeforeInput = await evaluate('customTest.getBlurCalls()')
  await evaluate("document.querySelector('#blur-runtime input').focus();document.querySelector('#blur-runtime input').select()")
  await send('Input.insertText', { text: '500' })
  await evaluate('customTest.settle()')
  assert.equal(await evaluate('customTest.getBlurCalls()'),blurCallsBeforeInput,'曾经失焦报错后继续输入，不能由数据监听重新校验')
  assert.match(await evaluate("document.querySelector('#blur-runtime .custom-validation-error')?.textContent || ''"),/金额必须是有效数字/,'仍在编辑时保留上次提示')
  await evaluate("document.querySelector('#blur-sink').focus();customTest.settle()")
  assert.equal(await evaluate("document.querySelectorAll('#blur-runtime .custom-validation-error').length"),0,'修正后失焦清除错误')
  blurCallsBeforeInput = await evaluate('customTest.getBlurCalls()')
  await evaluate("document.querySelector('#blur-runtime input').focus();document.querySelector('#blur-runtime input').select()")
  await send('Input.insertText', { text: 'invalid again' })
  await evaluate('customTest.settle()')
  assert.equal(await evaluate('customTest.getBlurCalls()'),blurCallsBeforeInput,'失焦通过后继续输入也不能重新校验')
  assert.equal(await evaluate("document.querySelectorAll('#blur-runtime .custom-validation-error').length"),0,'未失焦时不提前显示新错误')
  assert.equal(await evaluate('customTest.validateBlur()'),false,'未失焦时提交仍须校验当前值')
  blurCallsBeforeInput = await evaluate('customTest.getBlurCalls()')
  await evaluate("document.querySelector('#blur-runtime input').focus();document.querySelector('#blur-runtime input').select()")
  await send('Input.insertText', { text: '600' })
  await evaluate('customTest.settle()')
  assert.equal(await evaluate('customTest.getBlurCalls()'),blurCallsBeforeInput,'提交失败后继续输入不能重放提交校验')
  assert.equal(await evaluate('customTest.validateBlur()'),true,'再次提交时使用修正后的值校验')
  assert.equal(await evaluate("document.querySelectorAll('#runtime .custom-validation-error').length"),0,'初始不闪错误')
  assert.equal(await evaluate('customTest.validate()'),false)
  await evaluate('customTest.settle()')
  assert.equal(await evaluate("document.querySelector('#runtime .el-tabs__item.is-active')?.textContent"),'金额')
  assert.match(await evaluate("document.querySelector('#runtime .custom-validation-error')?.textContent"),/不能超过 1000/)
  assert.equal(await evaluate("document.querySelectorAll('#runtime .el-alert').length"),0,'节点表单不重复显示顶部错误')
  // 输入经过真实字段组件及宿主更新，不能只测试手工调用校验器。
  await evaluate("(()=>{const input=document.querySelector('#runtime input'); input.focus();input.value='900';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.blur()})()")
  await evaluate('customTest.settle()')
  assert.equal(await evaluate('customTest.state.record.amount'),900)
  assert.equal(await evaluate('customTest.validate()'),true)
  assert.equal(await evaluate("document.querySelectorAll('#runtime .custom-validation-error').length"),0)
  await evaluate('customTest.state.record.amount=1500;customTest.settle()')
  assert.equal(await evaluate('customTest.validate()'),false)
  await evaluate('customTest.state.readonly=true;customTest.settle()')
  assert.equal(await evaluate('customTest.validate()'),true)
  await evaluate('customTest.state.readonly=false;customTest.state.form.nodes[3].propsDocument.hidden=true;customTest.settle()')
  assert.equal(await evaluate('customTest.validate()'),true)
  await evaluate('customTest.state.form.nodes[3].propsDocument.hidden=false;customTest.state.nodeTree=false;customTest.settle()')
  assert.equal(await evaluate('customTest.validate()'),false,'历史平铺表单拦截')
  await evaluate('customTest.state.custom=true;customTest.settle()')
  assert.equal(await evaluate('customTest.validate()'),false,'自绘整表单无 validate 也由平台拦截')
  await evaluate('customTest.state.record.amount=1;customTest.state.mode="approve";customTest.settle()')
  assert.equal(await evaluate('customTest.validate()'),true)
  assert.equal(await evaluate('customTest.validateChild()'),false,'子表使用独立实体范围')
  assert.equal(await evaluate("document.querySelectorAll('#child .el-alert').length"),0,'子表不重复显示顶部错误')
  await evaluate('customTest.state.childRecord.amount=5;customTest.settle()')
  assert.equal(await evaluate('customTest.validateChild()'),true)
  await evaluate('customTest.state.childEntity="parent";customTest.settle()')
  assert.equal(await evaluate('customTest.validateChild()'),false,'子表不允许错误实体使用受限规则')
  assert.equal(await evaluate('customTest.validateLegacy()'),false,'fields-only 子表校验冒泡到父表')
  await evaluate('customTest.state.legacyRecord.details[0].amount=1;customTest.settle()')
  assert.equal(await evaluate('customTest.validateLegacy()'),true)
  assert.equal(await evaluate('customTest.validateData()'),false,'新增编辑自绘表单独立宿主也执行校验')
  assert.equal(await evaluate("document.querySelectorAll('#data .el-alert').length"),0,'独立表单宿主不重复显示顶部字段错误')
  await evaluate('customTest.state.dataRecord.data.amount=1;customTest.settle()')
  assert.equal(await evaluate('customTest.validateData()'),true)
  await evaluate("[...document.querySelectorAll('#editor button')].find(b=>b.textContent.includes('删除')).click();customTest.settle()")
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(customTest.state.form.fields[0].validationRules.customValidators))'),{version:1,rules:[]})
  await evaluate("[...document.querySelectorAll('#editor button')].find(b=>b.textContent.includes('添加校验器')).click();customTest.settle()")
  assert.equal(await evaluate('customTest.state.form.fields[0].validationRules.customValidators.rules[0].params.maxAmount'),1000)
  assert.deepEqual(browserErrors,[])
  console.log('custom validator browser acceptance passed: strict BLUR after validation/submit while typing, delayed CHANGE, inline errors only, correction, editor, tab reveal, flat/custom forms, child scope, legacy child submission, create/edit host, approve, readonly, hidden, explicit removal')
} finally {
  ws?.close();browser?.kill();await server?.close()
  await sleep(200)
  rmSync(fixture,{recursive:true,force:true})
  rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})
}
