import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'
import { mkdtempSync, writeFileSync, rmSync, existsSync } from 'node:fs'
import path from 'node:path'

// 真实动作面板、字段编辑器与请求适配器完成创建、保存和重开；HTTP 响应在浏览器拦截，不连接业务库。
const fixture = mkdtempSync(path.resolve('.field-assignment-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import { createApp, ref, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '/src/components/FlowActionConfigPanel.vue'
import { processActionApi } from '/src/api/processAction.js'
import { extensionCatalogApi } from '/src/api/system/extension.js'
const handler = { definitionId:'assignment-definition', beanName:'entityFieldAssignmentFlowActionHandler', displayName:'实体字段赋值',
 supportedTriggerTimings:['TRANSITION_TAKEN'], supportedExecutionModes:['IN_TRANSACTION'],recommendedExecutionMode:'IN_TRANSACTION' }
const fields = [
 {fieldCode:'note',fieldName:'说明',fieldType:'STRING'},
 {fieldCode:'flag',fieldName:'生效标志',fieldType:'BOOLEAN'},
 {fieldCode:'serial',fieldName:'业务长整数',fieldType:'LONG'},
 {fieldCode:'result',fieldName:'评审结果',fieldType:'SELECT',options:[{value:'YES',label:'通过'},{value:'NO',label:'停用',disabled:true}]}]
const state = { actions:[], saved:[], fail:false, fieldMode:'normal' }
const processId=ref('process'), elementId=ref('flow')
processActionApi.findDraftActionsByBinding = async () => state.actions
processActionApi.timingOptions = async () => [{value:'TRANSITION_TAKEN',label:'经过连线时',defaultExecutionMode:'IN_TRANSACTION',defaultFailurePolicy:'ROLLBACK'}]
processActionApi.listHandlers = async () => [handler]
processActionApi.failureStrategies = async () => []
processActionApi.saveAction = async action => { state.saved.push(structuredClone(action)); state.actions=[{...action,id:'saved'}] }
extensionCatalogApi.options = async () => [{id:handler.definitionId,key:handler.beanName,sourceName:handler.beanName,...handler}]
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]})
createApp({setup:()=>()=>h(Panel,{key:elementId.value,processId:processId.value,scopeType:'SEQUENCE_FLOW',elementId:elementId.value,elementName:'审批通过',bpmnType:'bpmn:SequenceFlow'})})
 .use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.assignmentTest={state,processId,elementId,fields}
`)
let server, browser
try {
  server = await createServer({ cacheDir:path.join(fixture, 'cache'), optimizeDeps:{entries:[path.join(fixture, 'index.html')],include:['axios']},
    server:{host:'127.0.0.1',port:3432,strictPort:true} })
  await server.listen()
  const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  browser = await chromium.launch({headless:true,...(existsSync(chrome) ? {executablePath:chrome} : {})})
  const page = await browser.newPage({viewport:{width:1200,height:1100}})
  page.setDefaultTimeout(10000)
  const errors=[]; page.on('pageerror',error=>errors.push(error.message))
  page.on('console',message=>{ if(message.type()==='warning' && message.text().includes('Invalid prop')) errors.push(message.text()) })
  // 保留真实 assignableFields 和响应解包，避免直接替换 API 方法掩盖候选加载的契约问题。
  await page.route('**/api/process-actions/process/*/assignable-fields', async route => {
    const { state, fields } = await page.evaluate(() => ({ state:assignmentTest.state, fields:assignmentTest.fields }))
    if (state.fail) return route.fulfill({status:500,json:{code:500,message:'加载字段失败'}})
    const data = state.fieldMode === 'empty' ? [] : state.fieldMode === 'invalid' ? {fields} : fields
    await route.fulfill({status:200,json:{code:200,message:'success',data}})
  })
  await page.goto('http://127.0.0.1:3432/' + path.basename(fixture) + '/index.html')
  await page.getByRole('button',{name:'字段赋值',exact:true}).click()
  const dialog=page.getByRole('dialog')
  await dialog.getByRole('button',{name:'添加字段',exact:true}).waitFor()
  const addField=async (name) => {
    await dialog.getByRole('button',{name:'添加字段',exact:true}).click()
    const row=dialog.locator('.assignment-row').last()
    await row.locator('.el-select__wrapper').first().click()
    const options=await row.getByRole('combobox').first().getAttribute('aria-controls')
    await page.locator('#'+options).getByRole('option',{name,exact:true}).click()
    return row
  }
  const note=await addField('说明')
  await note.getByRole('textbox').last().fill('<img src=x onerror=window.injected=1>')
  await addField('生效标志') // 默认 false 必须作为布尔值保存。
  const serial=await addField('业务长整数')
  await serial.getByRole('textbox').last().fill('9223372036854775807')
  const result=await addField('评审结果')
  await result.locator('.el-select__wrapper').nth(1).click()
  const operations=await result.getByRole('combobox').nth(1).getAttribute('aria-controls')
  await page.locator('#'+operations).getByRole('option',{name:'清空',exact:true}).click()
  await page.screenshot({path:'/private/tmp/flow-field-assignment-panel.png',fullPage:true})
  await dialog.getByRole('button',{name:'创建流程动作',exact:true}).click()
  await page.waitForFunction(()=>assignmentTest.state.saved.length===1)
  const saved=await page.evaluate(()=>assignmentTest.state.saved[0])
  assert.equal(saved.executionMode,'IN_TRANSACTION'); assert.equal(saved.failurePolicy,'ROLLBACK')
  assert.equal(saved.actionDefinitionId,'assignment-definition'); assert.equal(saved.elementId,'flow')
  const expected=[
    {fieldCode:'note',operation:'SET',value:'<img src=x onerror=window.injected=1>'},
    {fieldCode:'flag',operation:'SET',value:false},
    {fieldCode:'serial',operation:'SET',value:'9223372036854775807'},
    {fieldCode:'result',operation:'CLEAR'}]
  assert.deepEqual(JSON.parse(saved.paramsJson).assignments,expected)
  assert.equal(await page.evaluate(()=>window.injected),undefined)
  await page.locator('.action-ops').getByRole('button',{name:'编辑',exact:true}).click()
  await dialog.locator('.assignment-row').nth(3).waitFor()
  assert.equal(await dialog.locator('.assignment-row').nth(2).getByRole('textbox').last().inputValue(),'9223372036854775807')
  await dialog.getByRole('button',{name:'保存流程动作',exact:true}).click()
  await page.waitForFunction(()=>assignmentTest.state.saved.length===2)
  assert.deepEqual(JSON.parse(await page.evaluate(()=>assignmentTest.state.saved[1].paramsJson)).assignments,expected)
  // 非法旧配置和加载失败都不能被静默覆盖，必须明确修复或重试。
  await page.evaluate(()=>{ assignmentTest.state.actions[0].paramsJson='{'; assignmentTest.elementId.value='invalid' })
  await page.locator('.action-ops').getByRole('button',{name:'编辑',exact:true}).click()
  await dialog.getByRole('button',{name:'重新配置字段赋值',exact:true}).waitFor()
  await dialog.getByRole('button',{name:'保存流程动作',exact:true}).click()
  assert.equal(await page.evaluate(()=>assignmentTest.state.saved.length),2)
  await dialog.getByRole('button',{name:'取消',exact:true}).click()
  await page.evaluate(()=>assignmentTest.state.fail=true)
  await page.getByRole('button',{name:'字段赋值',exact:true}).click()
  await dialog.getByRole('button',{name:'重新加载字段',exact:true}).waitFor()
  await page.evaluate(()=>assignmentTest.state.fail=false)
  await dialog.getByRole('button',{name:'重新加载字段',exact:true}).click()
  await addField('说明')
  await dialog.getByRole('button',{name:'取消',exact:true}).click()
  // 历史流程没有合规发布字段时，显示原因且不允许继续添加一个必然为空的下拉框。
  await page.evaluate(()=>assignmentTest.state.fieldMode='empty')
  await page.getByRole('button',{name:'字段赋值',exact:true}).click()
  await dialog.getByText('当前实体没有可赋值的已发布普通字段，请检查实体发布版本及字段类型',{exact:true}).waitFor()
  assert.equal(await dialog.getByRole('button',{name:'添加字段',exact:true}).isDisabled(),true)
  assert.equal(await dialog.locator('.assignment-row').count(),0)
  await dialog.getByRole('button',{name:'取消',exact:true}).click()
  // 异常响应必须提示加载失败，不能按对象迭代出无名称的候选项；恢复响应后允许重试。
  await page.evaluate(()=>assignmentTest.state.fieldMode='invalid')
  await page.getByRole('button',{name:'字段赋值',exact:true}).click()
  await dialog.getByRole('button',{name:'重新加载字段',exact:true}).waitFor()
  assert.equal(await dialog.getByRole('button',{name:'添加字段',exact:true}).isDisabled(),true)
  await page.evaluate(()=>assignmentTest.state.fieldMode='normal')
  await dialog.getByRole('button',{name:'重新加载字段',exact:true}).click()
  await addField('说明')
  await dialog.getByRole('button',{name:'取消',exact:true}).click()
  assert.deepEqual(errors,[])
  console.log('字段赋值浏览器测试通过：真实 HTTP 响应解包、字段选择、多字段保存、布尔/长整数/清空、重开回显、非法配置保护、空候选禁用和错误响应重试。')
} finally {
  await browser?.close(); await server?.close(); rmSync(fixture,{recursive:true,force:true})
}
