import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'
import { mkdtempSync, writeFileSync, rmSync, existsSync } from 'node:fs'
import path from 'node:path'

// 使用真实动作面板和参数编辑器，API 夹具只隔离业务库，验证保存请求与重开回显。
const fixture = mkdtempSync(path.resolve('.action-dependency-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import {createApp,h} from 'vue'
import {createPinia} from 'pinia'
import {createRouter,createMemoryHistory} from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '/src/components/FlowActionConfigPanel.vue'
import {processActionApi} from '/src/api/processAction.js'
import {extensionCatalogApi} from '/src/api/system/extension.js'
const common={processConfigId:'process',scopeType:'NODE',elementId:'task',triggerTiming:'TASK_SUBMITTED',executionMode:'AFTER_COMMIT',failurePolicy:'RETRY',enabled:true,actionDefinitionId:'handler-definition',interfaceName:'handler',status:'DRAFT'}
const state={actions:[{...common,id:'create',actionName:'创建合同',sortOrder:10},
 {...common,id:'notify',actionName:'发送通知',sortOrder:0},
 {...common,id:'archive',actionName:'归档',predecessorActionId:'notify',sortOrder:20},
 {...common,id:'validate',actionName:'检查金额',triggerTiming:'TASK_SUBMIT_VALIDATING',executionMode:'IN_TRANSACTION',failurePolicy:'ROLLBACK',sortOrder:99},
 {...common,id:'sync',actionName:'更新状态',executionMode:'IN_TRANSACTION',failurePolicy:'ROLLBACK'}],saved:[],sorted:[]}
const handler={definitionId:'handler-definition',beanName:'handler',displayName:'业务处理器',retryable:true,supportedExecutionModes:['IN_TRANSACTION','AFTER_COMMIT']}
processActionApi.findDraftActionsByBinding=async()=>state.actions
processActionApi.timingOptions=async()=>[
 {value:'TASK_SUBMIT_VALIDATING',label:'审批办理前业务校验',defaultExecutionMode:'IN_TRANSACTION',defaultFailurePolicy:'ROLLBACK',blocking:true},
 {value:'TASK_SUBMITTED',label:'审批办理执行后',defaultExecutionMode:'IN_TRANSACTION',defaultFailurePolicy:'ROLLBACK'}]
processActionApi.listHandlers=async()=>[handler]
processActionApi.failureStrategies=async()=>[]
processActionApi.updateSortOrder=async ids=>{state.sorted.push(ids);ids.forEach((id,index)=>state.actions.find(a=>a.id===id).sortOrder=index)}
processActionApi.saveAction=async action=>{state.saved.push(structuredClone(action));state.actions=state.actions.map(a=>a.id===action.id?{...a,...action}:a)}
extensionCatalogApi.options=async()=>[{id:'handler-definition',key:'handler',sourceName:'handler',...handler}]
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]})
createApp({setup:()=>()=>h(Panel,{processId:'process',scopeType:'NODE',elementId:'task',elementName:'合同审批',bpmnType:'bpmn:UserTask'})})
 .use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.dependencyTest=state
`)
let server, browser
try {
  server=await createServer({cacheDir:path.join(fixture,'cache'),optimizeDeps:{entries:[path.join(fixture,'index.html')],include:['axios']},server:{host:'127.0.0.1',port:3438,strictPort:true}})
  await server.listen()
  const chrome=process.env.PLAYWRIGHT_EXECUTABLE_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  browser=await chromium.launch({headless:true,...(existsSync(chrome)?{executablePath:chrome}:{})})
  const page=await browser.newPage({viewport:{width:1300,height:1200}})
  page.setDefaultTimeout(10000)
  const errors=[];page.on('pageerror',error=>errors.push(error.message))
  await page.goto('http://127.0.0.1:3438/'+path.basename(fixture)+'/index.html')
  await page.locator('.action-group').nth(2).waitFor()
  assert.equal(await page.locator('.action-group').count(),3)
  const headings=await page.locator('.action-group-heading strong').allTextContents()
  assert.deepEqual(headings,['审批办理前业务校验 · 事务内执行','审批办理执行后 · 事务内执行','审批办理执行后 · 提交后执行'])
  // 使用精确名称定位，避免依赖摘要中出现前置名称时误选另一动作。
  const notify=page.locator('.action-item').filter({has:page.locator('.action-name',{hasText:/^发送通知$/})})
  await notify.getByRole('button',{name:'编辑',exact:true}).click()
  const dialog=page.getByRole('dialog')
  const predecessor=dialog.locator('.el-form-item').filter({has:page.locator('.el-form-item__label',{hasText:/^前置动作$/})})
  await predecessor.locator('.el-select__wrapper').click()
  const options=await predecessor.getByRole('combobox').getAttribute('aria-controls')
  const candidates=page.locator('#'+options)
  assert.deepEqual(await candidates.getByRole('option').allTextContents(),['创建合同'])
  await candidates.getByRole('option',{name:'创建合同',exact:true}).click()
  await dialog.getByRole('button',{name:/说明与参数/}).click()
  await dialog.getByRole('button',{name:'添加参数',exact:true}).click()
  const row=dialog.locator('.action-param-row').last()
  await row.getByRole('textbox').first().fill('contractNo')
  await row.locator('.el-select__wrapper').click()
  const types=await row.getByRole('combobox').getAttribute('aria-controls')
  await page.locator('#'+types).getByRole('option',{name:'前置动作结果',exact:true}).click()
  await row.getByPlaceholder('结果字段，如 contractNo').fill('contractNo')
  await page.screenshot({path:'/private/tmp/flow-action-dependencies.png',fullPage:true})
  await dialog.getByRole('button',{name:'保存流程动作',exact:true}).click()
  await page.waitForFunction(()=>dependencyTest.saved.length===1)
  const saved=await page.evaluate(()=>dependencyTest.saved[0])
  assert.equal(saved.predecessorActionId,'create')
  assert.deepEqual(JSON.parse(saved.paramsJson),{contractNo:{$predecessor:'contractNo'}})
  await notify.getByRole('button',{name:'编辑',exact:true}).click()
  assert.equal(await dialog.getByPlaceholder('结果字段，如 contractNo').inputValue(),'contractNo')
  await dialog.getByRole('button',{name:'保存流程动作',exact:true}).click()
  await page.waitForFunction(()=>dependencyTest.saved.length===2)
  // 上下移动只能提交本组 ID，且依赖不受列表位置影响。
  await notify.getByRole('button',{name:'下移动作',exact:true}).click()
  await page.waitForFunction(()=>dependencyTest.sorted.length===1)
  assert.deepEqual(await page.evaluate(()=>dependencyTest.sorted[0]),['create','notify','archive'])
  assert.equal(await page.evaluate(()=>dependencyTest.actions.find(a=>a.id==='notify').predecessorActionId),'create')
  await page.screenshot({path:'/private/tmp/flow-action-dependency-groups.png',fullPage:true})
  assert.deepEqual(errors,[])
  console.log('动作依赖浏览器验证通过：时机分组、候选过滤、结果参数保存与重开、组内排序和依赖保持。')
} finally {
  await browser?.close();await server?.close();rmSync(fixture,{recursive:true,force:true})
}
