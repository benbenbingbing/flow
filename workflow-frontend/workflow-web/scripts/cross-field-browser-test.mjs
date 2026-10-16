import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 使用真实编辑器与标准渲染组件在独立 Chrome 中验收，不调用业务 API。
const harness = `
import { createApp, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Preview from '/src/components/FormPreviewLinkage.vue'
import Editor from '/src/components/form-designer/FormCrossFieldRuleEditor.vue'
const rule = { version:1, rules:[{id:'range',operator:'GE',targetFieldCode:'start',message:'结束不得早于开始'}] }
const fields = [{id:'s',fieldCode:'start',fieldLabel:'开始',fieldType:'INTEGER',componentType:'number'}, {id:'e',fieldCode:'end',fieldLabel:'结束',fieldType:'INTEGER',componentType:'number',validationRules:{min:0,crossField:rule}}]
// 测试表单不提供业务 ID，真实输入变化不会触发后端 FIELD_CHANGE 事件请求。
const state = reactive({record:{start:10,end:5}, readonly:false, nodeTree:true, form:{formName:'跨字段校验验收',layoutType:'vertical', fields, nodes:[
 {id:'tabs',nodeType:'TAB_SET',propsDocument:{label:'时间范围'}},
 {id:'tab-a',parentId:'tabs',nodeType:'TAB_PANE',propsDocument:{label:'开始'}},
 {id:'tab-b',parentId:'tabs',nodeType:'TAB_PANE',propsDocument:{label:'结束'}},
 {id:'s',parentId:'tab-a',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'start',propsDocument:{fieldType:'INTEGER',componentType:'number',label:'开始'}},
 {id:'e',parentId:'tab-b',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'end',propsDocument:{fieldType:'INTEGER',componentType:'number',label:'结束'},rulesDocument:{validation:{min:0,crossField:rule}}}
]}})
const preview = ref()
const app = createApp({ setup() {return () => h('main',{style:'padding:32px;max-width:900px'},[
 h('h2','跨字段比较'), h('section',{id:'editor'},[h(Editor,{field:state.form.fields[1],fields:state.form.fields,modelValue:state.form.fields[1].validationRules.crossField,'onUpdate:modelValue':value=>{state.form.fields[1].validationRules.crossField=value;state.form.nodes[4].rulesDocument.validation.crossField=value}})]),
 h('section',{id:'runtime',style:'margin-top:32px'},[h(Preview,{ref:preview,form:{...state.form,nodes:state.nodeTree?state.form.nodes:[]},modelValue:state.record,mode:'edit',readonly:state.readonly,'onUpdate:modelValue':value=>state.record=value})])
])}})
app.use(createPinia()).use(ElementPlus).mount('#app')
window.crossFieldTest={state, getError(){return preview.value.getValidationError()},async validate(){return preview.value.validate()},async serverError(){return preview.value.applyServerValidationError({errorCode:'FORM_CROSS_FIELD_VALIDATION_FAILED',currentData:{fieldErrors:[{fieldCode:'end',ruleId:'range',targetFieldCode:'start',message:'服务器最终值不符合要求'}]}})}, async settle(){await nextTick();await nextTick();await new Promise(r=>setTimeout(r,450))}}
`
const fixture = mkdtempSync(path.resolve('.cross-field-fixture-'))
writeFileSync(path.join(fixture,'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'cross-field-chrome-'))
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
  server = await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')]},server:{host:'127.0.0.1',port:3398,strictPort:true}})
  await server.listen()
  browser = spawn(chromePath,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9398',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'})
  let targets
  for(let i=0;i<100;i++){try{targets=await (await fetch('http://127.0.0.1:9398/json/list')).json();break}catch{await sleep(100)}}
  assert.ok(targets?.length,'Chrome failed to start')
  ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
  ws.addEventListener('message',event=>{const data=JSON.parse(event.data); if(data.id&&pending.has(data.id)){const p=pending.get(data.id);pending.delete(data.id);data.error?p.reject(new Error(JSON.stringify(data.error))):p.resolve(data.result)} if(data.method==='Runtime.exceptionThrown')browserErrors.push(data.params.exceptionDetails.exception?.description||data.params.exceptionDetails.text)})
  await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
  await send('Runtime.enable');await send('Page.enable')
  await send('Page.navigate',{url:`http://127.0.0.1:3398/${path.basename(fixture)}/index.html`})
  for(let i=0;i<200;i++){if(await evaluate('Boolean(window.crossFieldTest)'))break;await sleep(100)}
  assert.equal(await evaluate('Boolean(window.crossFieldTest)'),true,browserErrors.join('\n'))
  await evaluate('crossFieldTest.settle()')
  assert.equal(await evaluate(`document.querySelectorAll('#runtime .el-form-item__error').length`),0,'初次加载不提示')
  assert.equal(await evaluate('crossFieldTest.validate()'),false)
  await evaluate('crossFieldTest.settle()')
  assert.equal(await evaluate(`document.querySelector('#runtime .el-tabs__item.is-active')?.textContent`),'结束','提交后定位错误所在页签')
  assert.match(await evaluate(`document.querySelector('#runtime .el-form-item__error')?.textContent`),/结束不得早于开始/)
  // 单字段 min=0 校验会在 blur 时通过，但不能覆盖尚未修正的跨字段提示。
  for (let attempt = 0; attempt < 2; attempt++) {
    await evaluate(`(()=>{const input=[...document.querySelectorAll('#runtime input')].at(-1);input.focus();input.blur()})()`)
    await evaluate('crossFieldTest.settle()')
    assert.match(await evaluate(`document.querySelector('#runtime .el-form-item__error')?.textContent`),/结束不得早于开始/,'反复失焦仍保留跨字段提示')
    assert.equal(await evaluate(`Boolean([...document.querySelectorAll('#runtime input')].at(-1).closest('.el-form-item').classList.contains('is-error'))`),true,'失焦后保留字段错误样式')
    assert.equal(await evaluate('crossFieldTest.validate()'),false,'重复提交仍然拦截')
  }
  // 通过真实输入事件修正所属字段，不直接调用校验器。
  await evaluate(`(()=>{const input=[...document.querySelectorAll('#runtime input')].at(-1);input.value='10';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.blur()})()`)
  await evaluate('crossFieldTest.settle()')
  assert.equal(await evaluate('crossFieldTest.state.record.end'),10)
  assert.equal(await evaluate(`document.querySelectorAll('#runtime .el-form-item__error').length`),0,'相等通过并清除错误')
  await evaluate('crossFieldTest.state.record.start=11;crossFieldTest.settle()')
  assert.match(await evaluate(`document.querySelector('#runtime .el-form-item__error')?.textContent`),/结束不得早于开始/,'引用字段程序更新也刷新错误')
  await evaluate('crossFieldTest.state.readonly=true;crossFieldTest.settle()')
  assert.equal(await evaluate('crossFieldTest.validate()'),true,'整表只读跳过')
  await evaluate('crossFieldTest.state.readonly=false;crossFieldTest.state.form.nodes[4].propsDocument.hidden=true;crossFieldTest.settle()')
  assert.equal(await evaluate('crossFieldTest.validate()'),true,'所属字段隐藏跳过')
  await evaluate('crossFieldTest.state.form.nodes[4].propsDocument.hidden=false;crossFieldTest.state.record.end=null;crossFieldTest.settle()')
  assert.equal(await evaluate('crossFieldTest.validate()'),true,'空值不增加必填限制')
  await evaluate('crossFieldTest.state.record.end=20;crossFieldTest.settle()')
  assert.equal(await evaluate('crossFieldTest.serverError()'),true)
  await evaluate('crossFieldTest.settle()')
  assert.match(await evaluate(`document.querySelector('#runtime .el-form-item__error')?.textContent`),/服务器最终值/)
  await evaluate('crossFieldTest.state.record.end=21;crossFieldTest.settle()')
  assert.equal(await evaluate(`document.querySelectorAll('#runtime .el-form-item__error').length`),0)
  // 无节点的历史平铺表单使用另一条模板分支，也必须保留相同的错误生命周期。
  await evaluate('crossFieldTest.state.nodeTree=false;crossFieldTest.state.record.end=5;crossFieldTest.settle()')
  assert.equal(await evaluate('crossFieldTest.validate()'),false)
  await evaluate(`(()=>{const input=[...document.querySelectorAll('#runtime input')].at(-1);input.focus();input.blur()})()`)
  await evaluate('crossFieldTest.settle()')
  assert.match(await evaluate(`document.querySelector('#runtime .el-form-item__error')?.textContent`),/结束不得早于开始/,'平铺表单失焦后保留提示')
  await evaluate('crossFieldTest.state.record.end=11;crossFieldTest.settle()')
  assert.equal(await evaluate(`document.querySelectorAll('#runtime .el-form-item__error').length`),0,'平铺表单修正为相等后清除提示')
  assert.equal(await evaluate('crossFieldTest.validate()'),true)
  await evaluate('crossFieldTest.state.nodeTree=true;crossFieldTest.settle()')
  // 编辑器不包含填写关系、启用开关或模式选择；删除保存为显式空规则。
  const editorText=await evaluate(`document.querySelector('#editor').textContent`)
  assert.ok(!editorText.includes('至少填写一项')&&!editorText.includes('适用模式'))
  await evaluate(`document.querySelector('#editor [aria-label="删除跨字段规则 1"]').click();crossFieldTest.settle()`)
  assert.deepEqual(await evaluate('JSON.parse(JSON.stringify(crossFieldTest.state.form.fields[1].validationRules.crossField))'),{version:1,rules:[]})
  await evaluate(`([...document.querySelectorAll('#editor button')].find(button=>button.textContent.includes('添加规则'))).click();crossFieldTest.settle()`)
  assert.equal(await evaluate('crossFieldTest.state.form.fields[1].validationRules.crossField.rules.length'),1)
  assert.match(await evaluate('crossFieldTest.state.form.fields[1].validationRules.crossField.rules[0].id'),/^cf_[A-Za-z0-9_-]+$/)
  assert.equal(await evaluate('crossFieldTest.state.form.fields[1].validationRules.crossField.rules[0].operator'),'GE')
  assert.deepEqual(browserErrors,[])
  console.log('cross-field browser acceptance passed: editor, native input, repeated blur/submit, flat form, hidden tab reveal, reference update, readonly, hidden, empty values, server errors, rule removal')
} finally {
  ws?.close();browser?.kill();await server?.close()
  await sleep(200)
  rmSync(fixture,{recursive:true,force:true})
  rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:200})
}
