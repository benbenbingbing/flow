import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 使用真实节点面板和 BPMN 模型验证选项加载、条件写入与重开回显，不连接业务数据库。
const fixture = mkdtempSync(path.resolve('.sequence-options-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"><link rel="icon" href="data:,"></head><body><div id="canvas" style="height:300px"></div><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import Modeler from 'bpmn-js/lib/Modeler'
import { createApp, h, shallowRef } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '/src/components/NodeConfigPanel.vue'
import request from '/src/utils/request.js'
import flowable from '/src/assets/flowable.json'
const calls=[]
request.get=async url=>{
  calls.push(url)
  if(url==='/entity/process/process') return {id:'entity',entityCode:'test_entity'}
  if(url==='/entity-form/entity/entity/fields') return [
    {fieldCode:'choice',fieldName:'我是单选择',fieldType:'SELECT',dictType:'choices',options:[],optionsJson:null},
    {fieldCode:'radio',fieldName:'单选框',fieldType:'RADIO',dictType:'choices'},
    {fieldCode:'legacy',fieldName:'旧内嵌选择',fieldType:'SELECT',optionsJson:'[{"label":"历史选项","value":"LEGACY"}]'}]
  if(url==='/system/dict/item/tree/code/choices') return [
    {itemCode:'YES',itemLabel:'通过',status:'0',children:[{itemCode:'CHILD',itemLabel:'子选项',status:'0'}]},
    {itemCode:'NO',itemLabel:'停用选项',status:'1'}]
  return []
}
request.post=async()=>true
const modeler=new Modeler({container:'#canvas',moddleExtensions:{flowable}})
await modeler.createDiagram()
const modeling=modeler.get('modeling'),factory=modeler.get('elementFactory'),registry=modeler.get('elementRegistry')
const root=modeler.get('canvas').getRootElement(),start=registry.get('StartEvent_1')
const task=modeling.createShape(factory.createShape({type:'bpmn:ServiceTask'}),{x:400,y:180},root)
const flow=modeling.connect(start,task,{type:'bpmn:SequenceFlow'})
const selected=shallowRef(null)
function select(element){element._modeler=modeler;selected.value=element}
select(flow)
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}}]})
createApp({setup:()=>()=>h(Panel,{element:selected.value,processId:'process'})}).use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.sequenceOptionsTest={calls,reopen:()=>{select(task);setTimeout(()=>select(flow),0)},
  expression:()=>flow.businessObject.conditionExpression?.body,
  config:()=>flow.businessObject.extensionElements?.values.find(v=>v.$type==='flowable:Properties')?.values.find(v=>v.name==='conditionGroupConfig')?.value}
`)

let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'),
    optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios'] },
    server: { host: '127.0.0.1', port: 3438, strictPort: true } })
  await server.listen()
  const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  browser = await chromium.launch({ headless: true, ...(existsSync(chrome) ? { executablePath: chrome } : {}) })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1300 } })
  page.setDefaultTimeout(10000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await page.goto('http://127.0.0.1:3438/' + path.basename(fixture) + '/index.html')
  await page.locator('.el-radio-button').filter({ hasText: /^表达式$/ }).click()
  await page.getByRole('button', { name: '添加条件', exact: true }).click()
  const row = page.locator('.condition-row').first()
  async function choose(selector, label) {
    await row.locator(selector).click()
    const id = await row.locator(selector).getByRole('combobox').getAttribute('aria-controls')
    await page.locator('#' + id).getByRole('option', { name: label, exact: true }).click()
  }
  await choose('.condition-property', '我是单选择')
  await row.locator('.condition-value').click()
  const valueId = await row.locator('.condition-value').getByRole('combobox').getAttribute('aria-controls')
  const values = page.locator('#' + valueId)
  await values.getByRole('option', { name: '通过', exact: true }).waitFor()
  assert.equal(await values.getByRole('option', { name: '子选项', exact: true }).count(), 1)
  assert.equal(await values.getByRole('option', { name: '停用选项', exact: true }).getAttribute('aria-disabled'), 'true')
  await values.getByRole('option', { name: '子选项', exact: true }).click()
  await page.getByRole('button', { name: '应用到画布', exact: true }).click()
  await page.waitForFunction(() => sequenceOptionsTest.expression() === "\u0024{choice == 'CHILD'}")
  const saved = JSON.parse(await page.evaluate(() => sequenceOptionsTest.config()))
  assert.equal(saved.root.children[0].value, 'CHILD')
  assert.equal(saved.root.children[0].property, 'choice')
  assert.equal(await page.evaluate(() => sequenceOptionsTest.calls.filter(url => url.includes('/system/dict/item/tree/code/')).length), 1)
  await page.evaluate(() => sequenceOptionsTest.reopen())
  await row.locator('.condition-value').getByText('子选项', { exact: true }).waitFor()
  await choose('.condition-property', '旧内嵌选择')
  await choose('.condition-value', '历史选项')
  await page.getByRole('button', { name: '应用到画布', exact: true }).click()
  await page.waitForFunction(() => sequenceOptionsTest.expression() === "\u0024{legacy == 'LEGACY'}")
  assert.deepEqual(errors, [])
  console.log('顺序流条件浏览器测试通过：系统代码表、子选项、停用项、代码值保存、重开回显和旧内嵌选项。')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
