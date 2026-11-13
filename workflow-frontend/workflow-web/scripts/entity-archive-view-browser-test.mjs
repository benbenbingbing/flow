import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 挂载真实查看弹窗及表格，只替换 API；验证普通查看权限、流程图、历史轮次及普通记录切换。
const harness = `
import { createApp, h, ref } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Dialog from '/src/views/entity/components/approval/EntityApprovalDialog.vue'
import Table from '/src/views/entity/components/EntityDataTable.vue'
import Rounds from '/src/views/entity/components/approval/EntityProcessRoundHistory.vue'
import { useUserStore } from '/src/stores/user.js'
import { entityDataApi } from '/src/api/entity.js'
import { historyImportApi as api } from '/src/api/historyImport.js'
import request from '/src/utils/request.js'
const xml='<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" targetNamespace="test"><process id="p"><userTask id="review" name="经理审批"/></process><bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="p"><bpmndi:BPMNShape id="shape" bpmnElement="review"><dc:Bounds x="100" y="100" width="140" height="80"/></bpmndi:BPMNShape></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></definitions>'
const rows={imported:{id:'imported',name:'历史业务数据',processStatus:'NOT_STARTED',data:{name:'历史业务数据'}},normal:{id:'normal',name:'普通业务数据',processStatus:'NOT_STARTED',data:{name:'普通业务数据'}}}
const state={listCalls:[],detailCalls:[],runtimeCalls:[],unexpected:[],requests:[],entityCalls:[]}
const pending=new Map()
const wait=key=>new Promise(resolve=>pending.set(key,resolve))
let delayOptional=false,delayProgress=false,delayEntity='',failProgress=false
rows.error={id:'error',name:'错误记录'}
const related={id:'children',compositionKey:'children',anchorType:'OWNER',config:{enabled:true,name:'关联子列表',target:{contentType:'LIST'},relation:{type:'ENTITY_RELATION'},presentation:{position:'INLINE',loadMode:'IMMEDIATE'}}}
const form=(id,runtimeContext={})=>({id,configHash:'published-'+id,fields:[{id:'name',fieldCode:'name',fieldName:'名称',componentType:'input',fieldType:'STRING'}],runtimeContext,viewCompositions:[related]})
const nativeForm=form('node-form',{processInstanceId:'native1',nodeId:'review',recordId:'native',entityCode:'expense'})
rows.native={id:'native',name:'原生运行记录',processInstanceId:'native1',processStatus:'RUNNING',data:{name:'原生运行记录'}}
request.defaults.adapter=async config=>{
 const body=typeof config.data==='string'?JSON.parse(config.data):config.data||{}
 let data
 state.requests.push(config.url)
 if(config.url==='/process-instance/native1/form-runtime')data={processInstanceId:'native1',status:'RUNNING',entityData:rows.native,formConfig:nativeForm}
 else if(config.url==='/process-instance/native1/progress'){if(failProgress){failProgress=false;throw Error('模拟进度查询失败')}if(delayProgress)await wait('progress');data={processInstanceId:'native1',status:'RUNNING',bpmnXml:xml,nodeHistory:[{nodeId:'review',nodeName:'经理审批',status:'ACTIVE'}]}}
 else if(config.url==='/ui-runtime/form-actions/resolve'){state.runtimeCalls.push({kind:'actions',body});if(delayOptional)await wait('actions');data=[]}
 else if(config.url==='/ui-runtime/view-compositions/resolve'){state.runtimeCalls.push({kind:'composition',body});data={targetContentType:'LIST',matchNone:true}}
 else if(config.url==='/ui-runtime/view-compositions/actions/capabilities')data={actionCapabilities:{}}
 else {state.unexpected.push(config.url);throw Error('Unexpected fixture API: '+config.url)}
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
entityDataApi.getDetail=async(code,id)=>{state.entityCalls.push(id);if(id===delayEntity)await wait('entity');return rows[id]}
api.archives=async(code,id)=>{state.listCalls.push({code,id});if(delayOptional)await wait('archives');if(id==='error')throw Error('模拟错误');return id==='imported'?[{id:'a1',name:'旧报销流程',sourceId:'P1',result:'APPROVED'},{id:'a2',name:'旧报销第二轮',sourceId:'P2',result:'REJECTED'}]:[]}
api.archive=async id=>{state.detailCalls.push(id);return {id,sourceSystem:'legacy',instance:{name:id==='a1'?'旧报销流程':'旧报销第二轮',sourceId:id==='a1'?'P1':'P2',result:id==='a1'?'APPROVED':'REJECTED',initiator:'张三'},template:{name:'审批图',version:1,xml,nodes:{review:'经理审批'}},events:[{id:'E1',nodeId:'review',sourceNode:'经理',actor:'李四',action:'APPROVED',rawAction:'通过',comment:id==='a1'?'首次办理':'第二轮办理',sequence:1},{id:'E2',nodeId:'review',sourceNode:'经理',actor:'王五',action:'APPROVED',rawAction:'通过',comment:'再次办理',sequence:2}]}}
const root={setup(){const dialog=ref(),native=ref(false);window.archiveViewTest={state,failProgressOnce:()=>{failProgress=true},setDelay:(optional,progress,entity='')=>{delayOptional=optional;delayProgress=progress;delayEntity=entity},release:key=>{pending.get(key)?.();pending.delete(key)},openWithForm:()=>dialog.value.openView(rows.normal,{loadForm:()=>wait('form')}),open:id=>dialog.value.openView(rows[id]||{id,name:'错误记录'}),openNative:formId=>{state.runtimeCalls=[];return dialog.value.openView(rows.native,formId?{form:form(formId,{listKey:'expenses',buttonId:'view'})}:{})},showNative:()=>native.value=true};return()=>h('div',[native.value?h('div',{id:'native-history'},[h(Rounds,{view:'history',processInstanceId:'native1',bpmnXml:xml,progressData:{rounds:[{processInstanceId:'native1',generation:1,status:'COMPLETED',endType:'COMPLETED'}]},processHistory:[{title:'原生审批记录',status:'COMPLETED'}],archiveRounds:[{id:'a1',name:'旧报销流程',sourceId:'P1',result:'APPROVED'}]})]):null,h(Table,{entityCode:'expense',entityDefinition:{},dataList:[rows.normal],listFields:[],rowActionButtons:[],toolbarButtons:[]}),h(Dialog,{ref:dialog,entityCode:'expense',entityDefinition:{},entityFields:[]})])}}
const app=createApp(root);const pinia=createPinia();app.use(pinia).use(ElementPlus).use(createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]}));const store=useUserStore(pinia);store.permissions=['entity:expense:view'];app.mount('#app')
`
const fixture = mkdtempSync(path.resolve('.archive-view-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios'], entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3423, strictPort: true, hmr: false, forwardConsole: false } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.setDefaultTimeout(15000)
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:3423/${path.basename(fixture)}/index.html`)
  await page.waitForFunction(() => window.archiveViewTest)
  assert.equal(await page.getByRole('button', { name: '迁入历史', exact: true }).count(), 0)
  assert.equal(await page.locator('.el-table__header').getByText('操作', { exact: true }).count(), 0)
  assert.deepEqual(await page.evaluate(() => archiveViewTest.state.listCalls), [], '列表不逐行探测档案')
  assert.equal(await page.evaluate(() => archiveViewTest.open('imported')), true)
  await page.getByText('历史业务数据', { exact: true }).waitFor()
  await page.getByRole('tab', { name: '流程图', exact: true }).click()
  await page.locator('.archive-recorded').waitFor()
  await page.locator('.djs-element[data-element-id="review"]').click()
  await page.getByText('节点：经理审批', { exact: true }).waitFor()
  await page.getByText('首次办理', { exact: true }).waitFor()
  await page.getByText('再次办理', { exact: true }).waitFor()
  assert.equal(await page.locator('.node-tooltip').count(), 0)
  await page.getByRole('tab', { name: '审批历史', exact: true }).click()
  const history = page.getByRole('tabpanel', { name: '审批历史', exact: true })
  await history.getByText('首次办理', { exact: true }).waitFor()
  assert.equal(await history.locator('.archive-diagram').count(), 0, '审批历史不重复创建流程图')
  await history.locator('.process-round-selector .el-select__wrapper').click()
  await page.getByRole('option', { name: /旧报销第二轮/ }).click()
  await history.getByText('第二轮办理', { exact: true }).waitFor()
  await page.locator('.el-select-dropdown:visible').waitFor({ state: 'hidden' })
  await page.screenshot({ path: '/private/tmp/entity-archive-view.png', fullPage: true })
  await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })
  await page.evaluate(() => archiveViewTest.open('normal'))
  await page.locator('.entity-approval-dialog').waitFor({ state: 'visible' })
  assert.equal(await page.getByRole('tab', { name: '流程图', exact: true }).count(), 0)
  assert.equal(await page.getByRole('tab', { name: '审批历史', exact: true }).count(), 0)
  assert.equal(await page.getByText('首次办理', { exact: true }).count(), 0, '切换普通记录后清理旧档案')
  assert.deepEqual(await page.evaluate(() => archiveViewTest.state.listCalls), [{ code: 'expense', id: 'imported' }, { code: 'expense', id: 'normal' }])
  await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })
  await page.evaluate(() => archiveViewTest.open('error'))
  await page.getByText('流程历史加载失败，请重新打开查看', { exact: true }).waitFor()
  await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })
  // 查看按钮指定的表单是实体入口；即使与流程表单 ID 相同，也不能只因记录有关联流程就拼接流程授权。
  for (const formId of ['node-form', 'other-form', null]) {
    const entityReads = await page.evaluate(() => archiveViewTest.state.entityCalls.length)
    assert.equal(await page.evaluate(id => archiveViewTest.openNative(id), formId), true)
    await page.getByText('未找到关联数据', { exact: true }).waitFor()
    assert.equal(await page.evaluate(() => archiveViewTest.state.entityCalls.length), entityReads + (formId ? 1 : 0), '指定表单仅执行一次详情事件，流程表单不重复取实体详情')
    const calls = await page.evaluate(() => archiveViewTest.state.runtimeCalls)
    assert(calls.some(call => call.kind === 'composition'))
    assert(calls.some(call => call.kind === 'actions'))
    for (const call of calls) {
      const context = call.body.runtimeContext
      assert.equal(context.recordId, 'native')
      assert.equal(context.taskId, undefined, '查看不能声明待办办理权限')
      if (formId) {
        assert.equal(context.processInstanceId, undefined, '按钮指定表单不能混入图示流程实例')
        assert.equal(context.nodeId, undefined)
        assert.equal(context.buttonId, 'view')
      } else {
        assert.equal(context.processInstanceId, 'native1', '流程节点表单保留服务端返回的完整上下文')
        assert.equal(context.nodeId, 'review')
      }
    }
    assert.equal(await page.getByRole('tab', { name: '流程图', exact: true }).count(), 1)
    await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
    await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })
  }
  // 首屏不能等候按钮、档案或进度；进度失败和旧会话响应也不能污染当前表单。
  await page.evaluate(() => { archiveViewTest.setDelay(true, true); window.opening = archiveViewTest.openNative(null) })
  await page.waitForFunction(() => [...document.querySelectorAll('input')].some(el => el.value === '原生运行记录') || document.body.innerText.includes('原生运行记录'))
  assert.equal(await page.evaluate(() => archiveViewTest.state.requests.filter(url => url.endsWith('/progress')).length), 0)
  assert.equal(await page.evaluate(() => window.opening), true, '后台档案和按钮不阻塞首屏')
  await page.getByRole('tab', { name: '流程图', exact: true }).click()
  await page.waitForFunction(() => archiveViewTest.state.requests.some(url => url.endsWith('/progress')))
  await page.getByRole('tab', { name: '基本信息', exact: true }).click()
  assert.equal(await page.evaluate(() => archiveViewTest.state.requests.filter(url => url.endsWith('/progress')).length), 1)
  await page.evaluate(() => archiveViewTest.setDelay(false, false))
  await page.evaluate(() => archiveViewTest.open('normal'))
  await page.evaluate(() => { archiveViewTest.release('progress');archiveViewTest.release('archives');archiveViewTest.release('actions') })
  await page.getByText('普通业务数据', { exact: true }).waitFor()
  assert.equal(await page.getByRole('tab', { name: '流程图', exact: true }).count(), 0)
  await page.evaluate(() => { archiveViewTest.setDelay(false,false,'imported');window.stale=archiveViewTest.open('imported') })
  await page.locator('[aria-label="正在加载数据"]').waitFor()
  await page.evaluate(() => archiveViewTest.open('normal'))
  await page.evaluate(() => archiveViewTest.release('entity'))
  assert.equal(await page.evaluate(() => window.stale), false)
  await page.getByText('普通业务数据', { exact: true }).waitFor()
  assert.equal(await page.getByText('历史业务数据', { exact: true }).count(), 0)
  await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })
  await page.evaluate(() => { window.opening=archiveViewTest.openWithForm() })
  await page.locator('[aria-label="正在加载数据"]').waitFor()
  await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })
  await page.evaluate(() => archiveViewTest.release('form'))
  assert.equal(await page.evaluate(() => window.opening), false, '关闭后表单配置响应不得继续加载实体')
  await page.evaluate(() => { archiveViewTest.failProgressOnce();return archiveViewTest.openNative(null) })
  const formReads = await page.evaluate(() => archiveViewTest.state.requests.filter(url => url.endsWith('/form-runtime')).length)
  await page.getByRole('tab', { name: '流程图', exact: true }).click()
  await page.getByText('流程进度加载失败，请重试', { exact: true }).waitFor()
  await page.getByRole('button', { name: '重试流程历史', exact: true }).click()
  await page.locator('.djs-element[data-element-id="review"]').waitFor()
  await page.getByRole('tab', { name: '基本信息', exact: true }).click()
  assert.equal(await page.evaluate(() => archiveViewTest.state.requests.filter(url => url.endsWith('/form-runtime')).length), formReads, '进度重试不重载表单或重放详情事件')
  await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.locator('.entity-approval-dialog').waitFor({ state: 'hidden' })
  await page.evaluate(() => archiveViewTest.showNative())
  const native = page.locator('#native-history')
  await native.getByText('原生审批记录', { exact: true }).waitFor()
  await native.locator('.process-round-selector .el-select__wrapper').click()
  await page.getByRole('option', { name: /旧报销流程/ }).click()
  await native.getByText('首次办理', { exact: true }).waitFor()
  await native.locator('.process-round-selector .el-select__wrapper').click()
  await page.getByRole('option', { name: '第 1 轮 · 正常结束', exact: true }).click()
  await native.getByText('原生审批记录', { exact: true }).waitFor()
  assert.equal(await native.locator('.archive-panel').count(), 0)
  assert.deepEqual(errors, [])
  assert.deepEqual(await page.evaluate(() => archiveViewTest.state.unexpected), [])
  console.log('实体查看浏览器验证通过：按钮指定表单与流程节点表单上下文、关联子页面请求、无独立按钮/操作列、流程图与重复审批、原生/迁入轮次切换、普通记录清理、查询失败提示、首屏不等待档案/按钮/进度、切换与关闭丢弃旧响应。')
} catch(error) { console.error(error); await browser?.contexts()[0]?.pages()[0]?.screenshot({path:'/private/tmp/flow-view-browser-failure.png'}).catch(()=>{}); throw error
} finally { await browser?.close(); await server?.close(); rmSync(fixture, { recursive: true, force: true }) }
