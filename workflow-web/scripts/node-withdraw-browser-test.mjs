import assert from 'node:assert/strict'
import { build, createServer } from 'vite'
import { chromium } from '@playwright/test'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 真实组件配合内存接口；全部撤回仅记录测试数组，不写业务库。
const harness = `
import { createApp, h, reactive, ref } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Permissions from '/src/components/NodeOperationPermissions.vue'
import Designer from '/src/components/VueBpmnDesigner.vue'
import Home from '/src/views/Home.vue'
import { NEW_NODE_OPERATION_PERMISSIONS, buildAssigneeConfig } from '/src/shared/process-config/index.js'
import request from '/src/utils/request.js'
const state = reactive({config:{...NEW_NODE_OPERATION_PERMISSIONS}, reads:[], writes:[], allowed:true, failed:false, rows:[
 {processInstanceId:'allowed',processName:'允许撤回测试',status:'RUNNING',canWithdraw:true,canTerminate:false},
 {processInstanceId:'denied',processName:'禁止撤回测试',status:'RUNNING',canWithdraw:false,canTerminate:true},
 {processInstanceId:'ended',processName:'已完成测试',status:'COMPLETED',canWithdraw:true},
 {processInstanceId:'missing',processName:'未返回权限测试',status:'RUNNING'},
 {processInstanceId:'text',processName:'非布尔权限测试',status:'RUNNING',canWithdraw:'true'}]})
request.get=async url=>{
 state.reads.push(url)
 if(url==='/process-task/statistics') return {todoCount:0,processCount:state.rows.length}
 if(url==='/process-task/todo') return {records:[],total:0}
 if(url==='/process-instance/my-started') return {records:JSON.parse(JSON.stringify(state.rows)),total:state.rows.length}
 if(url==='/process-instance/allowed/operations') {if(state.failed)throw Error('能力查询失败');return {withdraw:state.allowed}}
 throw Error('Unexpected GET '+url)
}
request.post=async (url,body)=>{
 if(url!=='/process-task/withdraw') throw Error('Unexpected POST '+url)
 state.writes.push({url,body});state.rows[0].status='COMPLETED';state.rows[0].canWithdraw=false;return {}
}
const designer=ref()
const xml='<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" id="D" targetNamespace="test"><bpmn:process id="P" isExecutable="true"/><bpmndi:BPMNDiagram id="DI"><bpmndi:BPMNPlane id="Plane" bpmnElement="P"/></bpmndi:BPMNDiagram></bpmn:definitions>'
createApp({setup(){return()=>h('main',{style:'padding:20px'},[
 h('section',{id:'permissions',style:'width:460px'},[h('h2','操作权限'),h(Permissions,{modelValue:state.config,'onUpdate:modelValue':v=>state.config=v})]),
 h('section',{id:'home'},[h(Home)]),
 h('section',{id:'designer',style:'height:300px'},[h(Designer,{ref:designer,xml})])
])}}).use(createPinia()).use(createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]})).use(ElementPlus).mount('#app')
window.withdrawTest={state, designer, buildAssigneeConfig}
`
const fixture=mkdtempSync(path.resolve('.node-withdraw-fixture-'))
writeFileSync(path.join(fixture,'index.html'), '<html><head><link rel="icon" href="data:,"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'),harness)
let server,browser
try {
 await build({base:'./',logLevel:'error',build:{outDir:path.join(fixture,'preview'),emptyOutDir:true,rollupOptions:{input:path.join(fixture,'index.html')}}})
 server=await createServer({configFile:false,root:path.join(fixture,'preview'),server:{host:'127.0.0.1',port:3409,strictPort:true}})
 await server.listen()
 browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'})
 const page=await browser.newPage({viewport:{width:1280,height:1000}}),errors=[]
 page.on('pageerror',e=>errors.push(e.message))
 await page.goto(`http://127.0.0.1:3409/${path.basename(fixture)}/index.html`)
 await page.waitForFunction(()=>window.withdrawTest?.designer.value?.getModeler()?.get('canvas').getRootElement()?.id==='P')
 const switches=page.locator('#permissions [role="switch"]')
 assert.deepEqual(await switches.evaluateAll(es=>es.map(e=>e.getAttribute('aria-checked'))),['false','false','true','false'])
 const pos=await page.locator('#permissions .el-switch').evaluateAll(es=>es.map(e=>({x:e.getBoundingClientRect().x,y:e.getBoundingClientRect().y})))
 assert.equal(pos[0].y,pos[1].y);assert.equal(pos[2].y,pos[3].y);assert.ok(pos[2].y>pos[0].y)
 await page.getByRole('button',{name:'查看允许终止配置说明'}).hover()
 await page.getByRole('tooltip').filter({hasText:'所有节点均允许才能终止'}).waitFor({state:'visible'})
 await page.locator('#permissions h2').hover()
 await page.getByRole('tooltip').filter({hasText:'所有节点均允许才能终止'}).waitFor({state:'hidden'})
 await page.locator('#permissions').screenshot({path:'/tmp/flow-node-withdraw-permissions.png',animations:'disabled'})
 await page.locator('#permissions .el-switch').filter({has:page.getByRole('switch',{name:'允许撤回',exact:true})}).click()
 await page.locator('#permissions .el-switch').filter({has:page.getByRole('switch',{name:'允许终止',exact:true})}).click()
 const config=await page.evaluate(()=>withdrawTest.buildAssigneeConfig({...withdrawTest.state.config,assigneeType:'user',assignee:'reviewer'}))
 assert.equal(config.allowWithdraw,true);assert.equal(config.allowTerminate,false)
 // 创建时即写入默认值，验证撤销/重做及 XML 重载不会丢失配置。
 const defaults=await page.evaluate(async()=>{
  const m=withdrawTest.designer.value.getModeler(),modeling=m.get('modeling'),factory=m.get('elementFactory')
  const shape=modeling.createShape(factory.createShape({type:'bpmn:UserTask'}),{x:160,y:100},m.get('canvas').getRootElement())
  const read=bo=>JSON.parse(bo.extensionElements.values.find(v=>v.$type==='flowable:Properties').values.find(v=>v.name==='assigneeConfig').value)
  const before=read(shape.businessObject),id=shape.id
  m.get('commandStack').undo();const undone=!m.get('elementRegistry').get(id)
  m.get('commandStack').redo();const after=read(m.get('elementRegistry').get(id).businessObject)
  const {xml}=await m.saveXML();await m.importXML(xml)
  const restored=read(m.get('elementRegistry').get(id).businessObject)
  const old=m.get('moddle').create('bpmn:UserTask')
  const prop=m.get('moddle').create('flowable:Property',{name:'assigneeConfig',value:'{"allowTransfer":true,"allowWithdraw":true,"allowTerminate":false}'})
  old.extensionElements=m.get('moddle').create('bpmn:ExtensionElements',{values:[m.get('moddle').create('flowable:Properties',{values:[prop]})]})
  const copy=modeling.createShape(factory.createShape({type:'bpmn:UserTask',businessObject:old}),{x:320,y:100},m.get('canvas').getRootElement())
  return {before,after,restored,undone,copy:read(copy.businessObject)}
 })
 assert.deepEqual(defaults.before,{allowTransfer:false,allowAddSign:false,allowTerminate:true,allowWithdraw:false})
 assert.deepEqual(defaults.after,defaults.before);assert.deepEqual(defaults.restored,defaults.before);assert.equal(defaults.undone,true)
 assert.deepEqual(defaults.copy,{allowTransfer:true,allowWithdraw:true,allowTerminate:false})
 await page.getByRole('button',{name:'查看我发起的流程',exact:true}).click()
 const withdraw=page.locator('#home').getByRole('button',{name:'撤回',exact:true})
 await withdraw.waitFor({state:'visible'});assert.equal(await withdraw.count(),1)
 await withdraw.click();await page.getByRole('button',{name:'取消',exact:true}).click()
 assert.equal(await page.evaluate(()=>withdrawTest.state.writes.length),0)
 // 确认时重查权限：节点变化与查询失败都不能沿用旧列表权限提交。
 await withdraw.click();await page.evaluate(()=>{withdrawTest.state.allowed=false})
 await page.getByRole('button',{name:'确认撤回',exact:true}).click()
 await page.getByText('撤回权限或流程状态已变化，请刷新列表后重试',{exact:true}).waitFor()
 assert.equal(await page.evaluate(()=>withdrawTest.state.writes.length),0)
 await page.evaluate(()=>{withdrawTest.state.allowed=true;withdrawTest.state.failed=true})
 await withdraw.click();await page.getByRole('button',{name:'确认撤回',exact:true}).click()
 await page.getByText('能力查询失败',{exact:true}).waitFor()
 assert.equal(await page.evaluate(()=>withdrawTest.state.writes.length),0)
 await page.evaluate(()=>{withdrawTest.state.failed=false})
 await withdraw.click();await page.getByPlaceholder('请输入撤回原因（选填）').fill('修正金额')
 await page.getByRole('button',{name:'确认撤回',exact:true}).click()
 await withdraw.waitFor({state:'hidden'})
 assert.deepEqual(await page.evaluate(()=>JSON.parse(JSON.stringify(withdrawTest.state.writes))),[{url:'/process-task/withdraw',body:{processInstanceId:'allowed',reason:'修正金额'}}])
 assert.deepEqual(errors,[])
 console.log('node withdrawal browser checks passed: layout, tooltip, independent switches, BPMN defaults/undo/redo/reload, PC visibility, cancel, stale/error guards and refresh')
} finally {
 await browser?.close();await server?.close();rmSync(fixture,{recursive:true,force:true})
}
