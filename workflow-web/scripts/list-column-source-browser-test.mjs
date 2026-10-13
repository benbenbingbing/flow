import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实列表设计器和模板弹窗；保存接口只修改内存快照，重载验证真实序列化结果。
const harness = `
import { createApp, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import Designer from '/src/views/EntityListConfigDesign.vue'
import TemplateDialog from '/src/components/ui-config/ListColumnTemplateEditorDialog.vue'
import { entityListConfigApi } from '/src/api/entityListConfig.js'
import { entityApi } from '/src/api/entity.js'
import { entityListScopeRuleApi } from '/src/api/entityListScopeRule.js'
import { uiComponentTemplateApi, uiExtensionApi } from '/src/api/uiConfig.js'
import { createListColumnTemplateEditor } from '/src/shared/list-column-template.js'
import request from '/src/utils/request.js'
const copy=value=>JSON.parse(JSON.stringify(value))
const sources=[
 {value:'ENTITY_FIELD',label:'实体字段'},
 {value:'FIELD_TEMPLATE',label:'字段组合',configSchema:[{key:'template',label:'组合模板',type:'textarea',required:true,defaultValue:'默认组合'}]},
 {value:'OBSOLETE_PROVIDER',label:'旧自定义数据源，不应显示'}
]
const interfaces=[{extensionId:'amount-interface',extensionKey:'AMOUNT',displayName:'金额计算',interfaceKind:'READ',interfaceContextType:'LIST',enabled:true,outputSchema:{type:'object'}}]
// 模板接收管理目录的完整选项，必须自行排除无法按记录 ID 返回列值的输出类型。
const templateInterfaces=[...interfaces,...['array','string'].map(type=>({
 ...interfaces[0],extensionId:type+'-interface',extensionKey:type.toUpperCase(),displayName:'不适用于列的'+type+'接口',outputSchema:{type}
}))]
const state=reactive({epoch:0,fieldWrites:[],templateWrites:[],requests:[],config:{
 id:'list-1',entityId:'entity-1',entityCode:'project',listKey:'SOURCE_TEST',listName:'取值方式验收',revision:1,
 fields:[{id:'column-1',revision:1,fieldId:'name-field',fieldCode:'name',fieldName:'名称',fieldType:'STRING',showInList:true,isQuery:false,dataSourceType:'ENTITY_FIELD',dataSourceConfig:'',interfaceExtensionId:null,sortOrder:0,orderKey:1000000}]
}})
// 与当前验收无关的组件目录请求统一走内存适配器，防止误连业务服务。
request.defaults.adapter=async config=>{state.requests.push(config.url);return {status:200,statusText:'OK',headers:{},config,data:{code:200,data:[]}}}
entityListConfigApi.getExtensionOptions=async()=>copy(sources)
entityListConfigApi.getById=async()=>copy(state.config)
entityListConfigApi.getDiff=async()=>({changed:false,changedSections:[]})
entityListConfigApi.patchField=async(id,fieldId,revision,payload)=>{
 const row={...copy(payload),id:fieldId,revision:revision+1};state.fieldWrites.push(copy(row));
 state.config.fields=state.config.fields.map(item=>item.id===fieldId?row:item);state.config.revision++;return copy(row)
}
entityListConfigApi.createField=async(id,payload)=>{
 const row={...copy(payload),id:'created-'+state.fieldWrites.length,revision:1};state.fieldWrites.push(copy(row));state.config.fields.push(row);return copy(row)
}
entityApi.getById=async()=>({id:'entity-1',entityName:'项目',entityCode:'project',fields:[{id:'name-field',fieldCode:'name',fieldName:'名称',fieldType:'STRING'}]})
entityListScopeRuleApi.getConfiguration=async()=>({policies:[],bindings:[],listDefaults:{}})
uiExtensionApi.availableInterfaces=async()=>copy(interfaces)
uiComponentTemplateApi.list=async()=>[]
uiComponentTemplateApi.save=async payload=>{state.templateWrites.push(copy(payload));return copy(payload)}
const template=ref()
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/designer/:id',component:Designer}]})
await router.push('/designer/list-1');await router.isReady()
const app=createApp({setup(){return()=>h('main',{},[
 h(RouterView,{key:state.epoch}),h(TemplateDialog,{ref:template,dataSourceOptions:sources,interfaceOptions:templateInterfaces})
])}})
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
window.sourceTest={state,template,
 settle:async()=>{await nextTick();await new Promise(resolve=>setTimeout(resolve,350));await nextTick()},
 reload(){state.epoch++},clearMessages:()=>ElMessage.closeAll(),
 openSavedTemplate(){const saved=state.templateWrites.at(-1);const editor=createListColumnTemplateEditor(saved.snapshot);template.value.openEdit({id:'template-1',templateKey:saved.templateKey,templateName:saved.templateName,editor:{...editor,baseField:saved.snapshot.field}})}
}
`
const fixture = mkdtempSync(path.resolve('.list-column-source-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'list-column-source-chrome-'))
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
let browser, ws, server, nextId = 0
const pending = new Map(), errors = [], diagnostics = []

/** 每条 CDP 操作设超时，断开浏览器时仍能退出并清理测试资源。 */
async function send(method, params = {}) {
  const id = ++nextId
  const result = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, {
      resolve: value => { clearTimeout(timer); resolve(value) },
      reject: error => { clearTimeout(timer); reject(error) }
    })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return result
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}
async function step(expression) { await evaluate(expression); await evaluate('sourceTest.settle()') }
const visible = 'element=>element.getClientRects().length>0&&getComputedStyle(element).visibility!=="hidden"'
const dialog = `([...document.querySelectorAll('.el-dialog')].filter(${visible})).at(-1)`
const formItem = label => `([...${dialog}.querySelectorAll('.el-form-item')].filter(${visible})).find(item=>item.querySelector('.el-form-item__label')?.textContent.trim()===${JSON.stringify(label)})`
async function clickText(text, selector = 'button') {
  await step(`(()=>{const element=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(${visible}).find(item=>item.textContent.trim()===${JSON.stringify(text)});if(!element)throw Error('Missing control: '+${JSON.stringify(text)});element.click()})()`)
}
async function openOptions(label) {
  await step(`(()=>{const select=${formItem(label)}.querySelector('.el-select');sourceTest.activeDropdownId=select.querySelector('[aria-controls]').getAttribute('aria-controls');select.querySelector('.el-select__wrapper').click()})()`)
}
const activeOptions = `document.getElementById(sourceTest.activeDropdownId).querySelectorAll('.el-select-dropdown__item')`
async function options() {
  return evaluate(`([...${activeOptions}].filter(${visible})).map(item=>({text:item.querySelector('.select-option span')?.textContent.trim()||item.textContent.trim(),disabled:item.classList.contains('is-disabled')}))`)
}
async function choose(label, text) {
  await openOptions(label)
  await step(`(()=>{const option=[...${activeOptions}].filter(${visible}).find(item=>(item.querySelector('.select-option span')?.textContent.trim()||item.textContent.trim())===${JSON.stringify(text)});if(!option)throw Error('Missing option: '+${JSON.stringify(text)});option.click()})()`)
}
async function input(label, value) {
  await step(`(()=>{const input=${formItem(label)}.querySelector('textarea,input');input.value=${JSON.stringify(value)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.blur()})()`)
}
async function openColumn(index = 0) {
  await step(`([...document.querySelectorAll('.field-config-table .el-table__body tr')][${index}].querySelectorAll('button')).forEach(button=>{if(button.textContent.trim()==='设置')button.click()})`)
  await clickText('数据与显示', '[role="tab"]')
}
async function closeOptions() {
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape' })
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape' })
  await evaluate('sourceTest.settle()')
}

/** 实际悬停问号，验证用户能读到说明，而不只检查帮助字典存在。 */
async function help(label, expected) {
  const rect = await evaluate(`(()=>{const button=${formItem(label)}.querySelector('.config-help-label__button');button.scrollIntoView({block:'center'});const rect=button.getBoundingClientRect();return{x:rect.x+rect.width/2,y:rect.y+rect.height/2}})()`)
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...rect })
  await sleep(450)
  const texts = await evaluate(`([...document.querySelectorAll('.el-popper')].filter(${visible})).map(item=>item.textContent.trim()).join(' ')`)
  assert.match(texts, expected, `${label}问号必须说明用途和使用限制`)
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 10, y: 10 })
  await sleep(100)
}

try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios'] }, server: { host: '127.0.0.1', port: 3405, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9405', `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })
  let targets
  for (let index = 0; index < 100; index++) {
    try { targets = await (await fetch('http://127.0.0.1:9405/json/list')).json(); break } catch { await sleep(100) }
  }
  assert.ok(targets?.length, 'Chrome failed to start')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) {
      const request = pending.get(data.id)
      pending.delete(data.id)
      data.error ? request.reject(Error(JSON.stringify(data.error))) : request.resolve(data.result)
    }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
    if (data.method === 'Runtime.consoleAPICalled' && data.params.type === 'error') diagnostics.push(data.params.args.map(arg => arg.value || arg.description).join(' '))
  })
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', reject, { once: true })
  })
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1500, height: 1300, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `http://127.0.0.1:3405/${path.basename(fixture)}/index.html` })
  for (let index = 0; index < 500; index++) {
    if (await evaluate('Boolean(window.sourceTest)')) break
    await sleep(100)
  }
  assert.equal(await evaluate('Boolean(window.sourceTest)'), true, [...errors, ...diagnostics].join('\n'))
  await evaluate('sourceTest.settle()')
  await openColumn()
  assert.equal(await evaluate(`Boolean(${formItem('扩展接口')})`), false, '实体字段不显示独立扩展接口入口')
  await openOptions('取值方式')
  assert.deepEqual((await options()).map(item => item.text), ['实体字段', '字段组合', '扩展接口'], '后台额外 Provider 不应成为第四种方式')
  await closeOptions()
  await help('取值方式', /三种方式互斥[\s\S]*切换方式会清空/)

  await choose('取值方式', '扩展接口')
  assert.equal(await evaluate(`${formItem('扩展接口')}.classList.contains('is-required')`), true)
  await help('扩展接口', /必须选择[\s\S]*LIST_COLUMN[\s\S]*记录 ID/)
  await clickText('保存当前列')
  assert.equal(await evaluate('sourceTest.state.fieldWrites.length'), 0, '未选接口时不得提交保存')
  assert.match(await evaluate('document.body.innerText'), /请选择扩展接口/)
  await choose('扩展接口', '金额计算 (AMOUNT)')
  await clickText('保存当前列')
  let saved = await evaluate('JSON.parse(JSON.stringify(sourceTest.state.fieldWrites.at(-1)))')
  assert.equal(saved.dataSourceType, 'INTERFACE', JSON.stringify(saved))
  assert.equal(saved.interfaceExtensionId, 'amount-interface')
  assert.deepEqual(JSON.parse(saved.dataSourceConfig || '{}'), {})

  // 重新挂载设计器，用接口中的快照重建编辑态，避免只验证当前组件内存。
  await step('sourceTest.reload()')
  await openColumn()
  assert.match(await evaluate(`${formItem('取值方式')}.textContent`), /扩展接口/)
  assert.match(await evaluate(`${formItem('扩展接口')}.textContent`), /金额计算/)
  await choose('取值方式', '字段组合')
  assert.equal(await evaluate(`Boolean(${formItem('扩展接口')})`), false)
  await help('组合模板', /\$\{字段编码\}[\s\S]*不执行脚本/)
  await input('组合模板', '')
  await clickText('保存当前列')
  assert.equal(await evaluate('sourceTest.state.fieldWrites.length'), 1, '空组合模板不得保存')
  await input('组合模板', '${name} / ${code}')
  await clickText('保存当前列')
  saved = await evaluate('JSON.parse(JSON.stringify(sourceTest.state.fieldWrites.at(-1)))')
  assert.equal(saved.dataSourceType, 'FIELD_TEMPLATE')
  assert.equal(saved.interfaceExtensionId, null, '切换方式后持久化载荷必须清除接口')
  assert.equal(JSON.parse(saved.dataSourceConfig).template, '${name} / ${code}')
  await step('sourceTest.reload()')
  await openColumn()
  assert.equal(await evaluate(`${formItem('组合模板')}.querySelector('textarea').value`), '${name} / ${code}')
  await choose('取值方式', '实体字段')
  await clickText('保存当前列')
  saved = await evaluate('JSON.parse(JSON.stringify(sourceTest.state.fieldWrites.at(-1)))')
  assert.equal(saved.dataSourceType, 'ENTITY_FIELD')
  assert.equal(saved.interfaceExtensionId, null)
  assert.deepEqual(JSON.parse(saved.dataSourceConfig || '{}'), {}, '实体字段不得残留组合参数')

  await clickText('添加虚拟列')
  await openColumn(1)
  await openOptions('取值方式')
  assert.equal((await options()).find(item => item.text === '实体字段').disabled, true)
  await closeOptions()
  await choose('取值方式', '扩展接口')
  await choose('扩展接口', '金额计算 (AMOUNT)')
  await clickText('保存当前列')
  saved = await evaluate('JSON.parse(JSON.stringify(sourceTest.state.fieldWrites.at(-1)))')
  assert.ok(saved.fieldId.startsWith('virtual_'))
  assert.equal(saved.dataSourceType, 'INTERFACE')

  await step('sourceTest.template.value.openCreate()')
  await input('模板编码', 'SOURCE_TEST')
  await input('模板名称', '取值方式验收')
  await clickText('数据与查询', '[role="tab"]')
  await openOptions('取值方式')
  assert.deepEqual((await options()).map(item => item.text), ['实体字段', '字段组合', '扩展接口'])
  await closeOptions()
  await choose('取值方式', '扩展接口')
  await clickText('创建模板')
  assert.equal(await evaluate('sourceTest.state.templateWrites.length'), 0, '模板同样要求选择接口')
  await openOptions('扩展接口')
  assert.deepEqual((await options()).map(item => item.text), ['金额计算 (AMOUNT)'], '模板仅允许输出对象的列表只读接口，排除 array/string 输出')
  await closeOptions()
  await choose('扩展接口', '金额计算 (AMOUNT)')
  await clickText('创建模板')
  saved = await evaluate('JSON.parse(JSON.stringify(sourceTest.state.templateWrites.at(-1).snapshot.field))')
  assert.equal(saved.dataSourceType, 'INTERFACE')
  assert.equal(saved.interfaceExtensionId, 'amount-interface')
  await step('sourceTest.openSavedTemplate()')
  await clickText('数据与查询', '[role="tab"]')
  assert.match(await evaluate(`${formItem('扩展接口')}.textContent`), /金额计算/)
  await choose('取值方式', '字段组合')
  await help('组合模板', /\$\{字段编码\}/)
  await input('组合模板', '${code}-${name}')
  await clickText('保存模板')
  saved = await evaluate('JSON.parse(JSON.stringify(sourceTest.state.templateWrites.at(-1).snapshot.field))')
  assert.equal(saved.dataSourceType, 'FIELD_TEMPLATE')
  assert.equal(saved.interfaceExtensionId, null)
  assert.equal(JSON.parse(saved.dataSourceConfig).template, '${code}-${name}')
  await step('sourceTest.openSavedTemplate()')
  await clickText('数据与查询', '[role="tab"]')
  assert.equal(await evaluate(`${formItem('组合模板')}.querySelector('textarea').value`), '${code}-${name}')
  await choose('取值方式', '实体字段')
  await clickText('保存模板')
  saved = await evaluate('JSON.parse(JSON.stringify(sourceTest.state.templateWrites.at(-1).snapshot.field))')
  assert.deepEqual(JSON.parse(saved.dataSourceConfig || '{}'), {})
  assert.equal(saved.interfaceExtensionId, null)
  assert.deepEqual(errors, [])
  assert.deepEqual(diagnostics, [])
  console.log('list column source browser acceptance passed: real designer/template dialogs, three exclusive sources, conditional required interface, configuration cleanup, virtual fields, tooltip instructions, save/reload')
} finally {
  ws?.close()
  browser?.kill()
  await server?.close()
  await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
