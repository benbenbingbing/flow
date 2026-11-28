import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 挂载真实字段和节点表单，验证历史发布数组不会被误认为宿主解析的候选。
const fixture = mkdtempSync(path.resolve('.selection-options-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><link rel="icon" href="data:,"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import {createApp,h,reactive} from 'vue'
import {createPinia} from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Runtime from '/src/components/FormPreviewLinkage.vue'
import Field from '/src/components/FormFieldRenderer.vue'
import SubForm from '/src/components/SubFormRenderer.vue'
import Select from '/@fs${path.resolve('../packages/workflow-extensions/src/extensions/platform/fields/select/pc/SelectField.vue').split(path.sep).join('/')}'
import {registerFormFieldComponent} from '/src/extensions/core/registries/formFieldRegistry.js'
import {ENTITY_STATUS_OPTIONS_EXTENSION_ID,withEntityStatusRuntimeForm} from '@flow/workflow-core/entity-status-runtime'
import {createFormDataSourceRuntime} from '@flow/workflow-core/form-runtime/dataSourceRuntime'
import request from '/src/utils/request.js'
registerFormFieldComponent('select',Select,{origin:'PLATFORM',label:'选择'})
const calls=[]
request.defaults.adapter=async config=>{
 calls.push(config.url)
 const data=config.url==='/system/dict/item/tree/code/choices'
  ? [{itemCode:'YES',itemLabel:'代码表通过',status:'0'}] : []
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
const legacy=[{value:'OLD',label:'历史内嵌项'}]
const binding={FIELD_OPTIONS:{extensionId:'dynamic-choices'}}
const configs={dict:{dictType:'choices',options:legacy,optionsJson:JSON.stringify(legacy),componentProps:{options:legacy}},array:{options:legacy},json:{optionsJson:JSON.stringify(legacy)},component:{componentProps:{options:legacy}},dynamic:{dictType:'choices',dataSourceBindings:binding},empty:{dictType:'choices',dataSourceBindings:binding},status:{}}
const models=reactive(Object.fromEntries(Object.keys(configs).map(key=>[key,{[key]:key==='status'?'REVIEW':'OLD'}])))
const forms=Object.fromEntries(Object.entries(configs).map(([key,config])=>{
 const field={id:key,fieldCode:key,fieldName:key,fieldType:'SELECT',componentType:'select',...config}
 const form={id:'form-'+key,fields:[field],nodes:[{id:'node-'+key,nodeKey:key,nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:key,dataSourceBindings:config.dataSourceBindings,props:{fieldCode:key,fieldType:'SELECT',componentType:'select',options:legacy}}]}
 return [key,key==='status'?withEntityStatusRuntimeForm(form,[],[{statusCode:'REVIEW',statusName:'业务复核中'}]):form]
}))
// 新发布的状态选项必须实际经过通用绑定，宿主的旧状态名称不能覆盖接口结果。
const publishedStatus=withEntityStatusRuntimeForm({id:'released-form',fields:[{id:'status-field',fieldCode:'status',isSystem:true}],nodes:[{
 id:'status-node',nodeKey:'status',nodeType:'FIELD',bindingType:'ENTITY_FIELD',bindingRef:'status-field',
 dataSourceBindings:{FIELD_OPTIONS:{extensionId:ENTITY_STATUS_OPTIONS_EXTENSION_ID}},
 props:{fieldCode:'status',fieldName:'状态',fieldType:'STRING',componentType:'select',readonly:true}
}]},[],[{value:'REVIEW',label:'旧状态选项'}])
const statusRequests=[]
const statusModel=reactive({status:'REVIEW'})
const statusRuntime=createFormDataSourceRuntime({getForm:()=>publishedStatus,executeDataSource:async request=>{
 statusRequests.push([request.extensionId,request.bindingCode,request.targetKey])
 return [{value:'REVIEW',label:'数据源复核中'}]
}})
createApp({setup:()=>()=>h('main',[
 ...Object.keys(configs).map(key=>h('section',{id:key},[h(Runtime,{form:forms[key],modelValue:models[key],'onUpdate:modelValue':value=>models[key]=value,mode:'edit',showHeader:false,height:'120px',dataSourceRuntime:{loadOptions:async field=>field.fieldCode==='empty'?[]:[{value:'DYNAMIC',label:'动态候选'}]}})])),
 h('section',{id:'published-status'},[h(Runtime,{form:publishedStatus,modelValue:statusModel,mode:'edit',showHeader:false,dataSourceRuntime:statusRuntime})]),
 h('section',{id:'standalone'},[h(Field,{field:{fieldCode:'choice',componentType:'select',dictType:'choices',options:legacy},modelValue:'YES'})]),
 ...['YES','OLD'].map(code=>h('section',{id:'sub-'+code},[h(SubForm,{readonly:true,config:{layout:'table',fields:[{fieldKey:'choice',fieldName:'子表选择',fieldType:'SELECT',dictType:code==='YES'?'choices':null,options:legacy}]},modelValue:[{choice:code}]})]))
])}).use(createPinia()).use(ElementPlus).mount('#app')
window.selectionTest={models,calls,statusModel,statusRequests}
`)
let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios', 'async-validator'] }, server: { host: '127.0.0.1', port: 3439, strictPort: true } })
  await server.listen()
  const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  browser = await chromium.launch({ headless: true, ...(existsSync(chrome) ? { executablePath: chrome } : {}) })
  const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await page.goto('http://127.0.0.1:3439/' + path.basename(fixture) + '/index.html')
  async function open(key) {
    const select = page.locator('#' + key + ' .el-select')
    await select.click()
    const id = await select.getByRole('combobox').getAttribute('aria-controls')
    return page.locator('#' + id)
  }
  const dictionary = await open('dict')
  await dictionary.getByRole('option', { name: '代码表通过', exact: true }).click()
  assert.equal(await page.evaluate(() => selectionTest.models.dict.dict), 'YES')
  for (const key of ['array', 'json', 'component']) {
    const dropdown = await open(key)
    assert.equal(await dropdown.getByRole('option').count(), 0)
    await page.keyboard.press('Escape')
    assert.equal(await page.evaluate(key => selectionTest.models[key][key], key), 'OLD')
  }
  const status = await open('status')
  await status.getByRole('option', { name: '业务复核中', exact: true }).waitFor()
  await page.keyboard.press('Escape')
  const published = page.locator('#published-status .el-select')
  await published.getByText('数据源复核中', { exact: true }).waitFor()
  assert.equal(await published.getByRole('combobox').isDisabled(), true)
  assert.deepEqual(await page.evaluate(() => selectionTest.statusRequests), [
    ['platform_entity_status_options', 'FIELD_OPTIONS', 'status']
  ])
  assert.equal(await page.evaluate(() => selectionTest.statusModel.status), 'REVIEW')
  const dynamic = await open('dynamic')
  await dynamic.getByRole('option', { name: '动态候选', exact: true }).click()
  assert.equal(await page.evaluate(() => selectionTest.models.dynamic.dynamic), 'DYNAMIC')
  const empty = await open('empty')
  assert.equal(await empty.getByRole('option').count(), 0)
  await page.keyboard.press('Escape')
  const standalone = await open('standalone')
  await standalone.getByRole('option', { name: '代码表通过', exact: true }).waitFor()
  await page.locator('#sub-YES').getByText('代码表通过', { exact: true }).waitFor()
  await page.locator('#sub-OLD').getByText('OLD', { exact: true }).waitFor()
  assert.equal(await page.getByRole('option', { name: '历史内嵌项', exact: true }).count(), 0)
  assert.deepEqual(errors, [])
  console.log('PC 选择字段浏览器测试通过，覆盖代码表、独立字段、新旧系统状态及历史值保留。')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
