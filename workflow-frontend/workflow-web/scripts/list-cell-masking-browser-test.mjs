import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实表格与配置编辑器，接口和剪贴板仅在内存中回放，不写业务数据库。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Table from '/src/views/entity/components/EntityDataTable.vue'
import ConfigSchemaEditor from '/src/components/ConfigSchemaEditor.vue'
import DefaultText from '/src/extensions/builtin/list-cells/DefaultText.vue'
import DateFormatter from '/src/extensions/builtin/list-cells/DateFormatter.vue'
import StatusBadge from '/src/extensions/builtin/list-cells/StatusBadge.vue'
import { registerCellComponent } from '/src/extensions/core/registries/listCellRegistry.js'
import { listCellMaskingSchema } from '/src/shared/list-cell-masking.js'
import { createListColumnTemplateEditor, buildListColumnTemplateSnapshot, applyListColumnTemplateSnapshot } from '/src/shared/list-column-template.js'
const config = (extra={}) => ({ maskingEnabled:true, maskPrefixLength:3, maskSuffixLength:4, ...extra })
const field = (fieldCode, fieldName, extra={}) => ({
 fieldCode, fieldName, showInList:true, width:160, dataSourceType:'ENTITY_FIELD',
 columnConfig:JSON.stringify({quickCopy:true}), renderConfig:config(), ...extra
})
const state=reactive({
 clipboard:'', extensionCalls:0,
 settings:{emptyText:'暂无',maskingEnabled:false,maskPrefixLength:3,maskSuffixLength:4},
 fields:[
  field('phone','自动匹配',{renderConfig:JSON.stringify(config())}),
  field('text','文本组件',{renderComponent:'DefaultText'}),
  field('date','日期组件',{renderComponent:'DateFormatter',renderConfig:config({pattern:'yyyy-MM-dd',maskSuffixLength:3})}),
  field('status','状态组件',{renderComponent:'StatusBadge',renderConfig:config({maskPrefixLength:1,maskSuffixLength:1})}),
  field('extension','扩展组件',{renderComponent:'RowReader'}),
  field('empty','空值',{renderConfig:config({emptyText:'暂无'})}),
  field('short','短值'),
  field('unicode','中文与表情',{renderConfig:config({maskPrefixLength:1,maskSuffixLength:1})}),
  field('long','溢出提示',{width:140}),
  field('zero','零值',{width:90,renderConfig:config({maskPrefixLength:0,maskSuffixLength:0})}),
  field('flag','布尔值',{width:90,renderConfig:config({maskPrefixLength:0,maskSuffixLength:0})})
 ],
 rows:[{id:'record-1',phone:'13812345678',text:'13712345678',date:'2026-10-12T12:00:00',status:'PENDING',
  extension:'13987654321',empty:null,short:'1234567',unicode:'张😀小明',long:'SENSITIVE-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789-END',zero:0,flag:false,
  actionCapabilities:{view:{visible:true,enabled:true}}}]
})
const originalRows=JSON.stringify(state.rows)
Object.defineProperty(navigator,'clipboard',{value:{writeText:async text=>{state.clipboard=text}},configurable:true})
registerCellComponent('DefaultText',DefaultText)
registerCellComponent('DateFormatter',DateFormatter)
registerCellComponent('StatusBadge',StatusBadge)
// 故意忽略 value 并重读原始 row，确保脱敏不是只修改传给扩展的 value。
registerCellComponent('RowReader',{props:['row','field'],setup(props){state.extensionCalls++;return()=>h('span',{
 class:'row-reader',title:props.row[props.field.fieldCode]
},props.row[props.field.fieldCode])}})
const schema=[{key:'emptyText',label:'空值文本',type:'string',default:'-',group:'common'},...listCellMaskingSchema]
const app=createApp({setup(){return()=>h('main',{},[
 h('section',{id:'settings',style:'width:640px'},[h('h2',{},'单元格显示'),h(ConfigSchemaEditor,{
  modelValue:state.settings,schema,grouped:false,'onUpdate:modelValue':value=>state.settings=value
 })]),
 h('section',{id:'runtime'},[h(Table,{
  dataList:state.rows,loading:false,total:1,pageNum:1,pageSize:10,listFields:state.fields,
  toolbarButtons:[],toolbarCapabilities:{},rowActionButtons:[{id:'view',key:'view',label:'查看',type:'built-in',enabled:true,mappedFieldCode:'phone',hideWhenMapped:true}],
  showSelectionColumn:false,useListConfig:true,entityCode:'project',entityDefinition:{},entityStatusMap:{PENDING:'待审核中'},
  refEntityNameMap:{},refresh:()=>{},showPagination:false,viewConfig:{table:{showIndex:false}}
 })])
])}})
app.use(createPinia()).use(ElementPlus).mount('#app')
window.maskTest={state,originalRows,
 settle:async()=>{await nextTick();await new Promise(resolve=>setTimeout(resolve,200));await nextTick()},
 toggle(enabled){state.fields=state.fields.map(item=>({...item,renderConfig:{
  ...(typeof item.renderConfig==='string'?JSON.parse(item.renderConfig):item.renderConfig),maskingEnabled:enabled
 }}))},
 roundTrip(){
  const editor=createListColumnTemplateEditor({field:{renderConfig:JSON.stringify(state.settings)}})
  const snapshot=buildListColumnTemplateSnapshot(editor)
  const reloaded=createListColumnTemplateEditor(JSON.parse(JSON.stringify(snapshot)))
  const applied=applyListColumnTemplateSnapshot({fieldCode:'phone',fieldName:'手机号'},JSON.stringify(snapshot))
  return {snapshot,reloaded:reloaded.renderConfig,applied:JSON.parse(applied.renderConfig)}
 }
}
`
const fixture = mkdtempSync(path.resolve('.list-cell-masking-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'list-cell-masking-chrome-'))
let browser, ws, server, nextId = 0
const pending = new Map(), errors = []
const diagnostics = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

/** 发出 CDP 命令并限制等待时间，让浏览器故障能结束测试并清理临时目录。 */
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

async function step(expression) {
  await evaluate(expression)
  await evaluate('maskTest.settle()')
}

const cells = `document.querySelectorAll('#runtime .el-table__body tr:first-child td')`
const cell = index => `${cells}[${index}]`
const text = index => evaluate(`${cell(index)}.querySelector('.list-quick-copy-cell__content').textContent.trim()`)
const settingsItem = label => `([...document.querySelectorAll('#settings .el-form-item')]).find(item=>item.querySelector('.el-form-item__label').textContent.trim()===${JSON.stringify(label)})`

/** 模拟用户实际输入数字，让 schema 类型转换和双向绑定一起参与验证。 */
async function setNumber(label, value) {
  await step(`(()=>{const input=${settingsItem(label)}.querySelector('input');input.value=${JSON.stringify(String(value))};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.blur()})()`)
}

try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios'] }, server: { host: '127.0.0.1', port: 3403, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9403', `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })
  let targets
  for (let index = 0; index < 100; index++) {
    try { targets = await (await fetch('http://127.0.0.1:9403/json/list')).json(); break } catch { await sleep(100) }
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
    if (data.method === 'Log.entryAdded' && data.params.entry.level === 'error') diagnostics.push(data.params.entry.text + ' ' + (data.params.entry.url || ''))
    if (data.method === 'Runtime.consoleAPICalled' && data.params.type === 'error') diagnostics.push(data.params.args.map(arg => arg.value || arg.description).join(' '))
  })
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', reject, { once: true })
  })
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Log.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1900, height: 1050, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `http://127.0.0.1:3403/${path.basename(fixture)}/index.html` })
  for (let index = 0; index < 500; index++) {
    if (await evaluate('Boolean(window.maskTest)')) break
    await sleep(100)
  }
  assert.equal(await evaluate('Boolean(window.maskTest)'), true, [...errors, ...diagnostics, await evaluate('document.body.innerText'), await evaluate('location.href')].join('\n'))
  await evaluate('maskTest.settle()')

  assert.equal(await text(0), '138****5678', '自动匹配且未指定 renderComponent 的列也必须脱敏')
  assert.equal(await text(1), '137****5678', '显式 DefaultText 与自动匹配使用相同规则')
  assert.equal(await text(2), '202****-12', '日期组件先按 pattern 格式化，再统一脱敏')
  assert.equal(await text(3), '待**中', '状态标签先解析业务文案，再按字符脱敏')
  assert.equal(await evaluate(`${cell(3)}.querySelector('.el-tag')===null`), true, '脱敏统一显示纯文本')
  assert.equal(await text(4), '139****4321')
  assert.equal(await evaluate('maskTest.state.extensionCalls'), 0, '开启脱敏不能执行会重读原值的扩展')
  assert.equal(await text(5), '暂无', '空值仍显示配置的占位文本')
  assert.equal(await text(6), '*******', '保留长度重叠时全部隐藏')
  assert.equal(await text(7), '张**明', '表情按 Unicode 字符处理')
  assert.equal(await text(9), '*', '数值 0 不应被当成空值')
  assert.equal(await text(10), '*****', 'false 不应被当成空值')
  const longMasked = 'SEN' + '*'.repeat('SENSITIVE-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789-END'.length - 7) + '-END'
  assert.equal(await text(8), longMasked)
  assert.equal(await evaluate(`${cell(0)}.querySelector('.list-cell-action').getAttribute('aria-label')`), '查看：138****5678')
  assert.equal(await evaluate("document.querySelector('#runtime').outerHTML.includes('13812345678')"), false, '文本、title 和 ARIA 属性不包含手机号原文')
  assert.equal(await evaluate("document.querySelector('#runtime').outerHTML.includes('13987654321')"), false, '扩展不能通过 title 暴露原值')

  for (const [index, expected] of [[0, '138****5678'], [4, '139****4321'], [5, '暂无']]) {
    await step(`${cell(index)}.querySelector('.list-quick-copy-cell__button').click()`)
    assert.equal(await evaluate('maskTest.state.clipboard'), expected, '复制内容与脱敏展示一致')
  }

  // 必须真正打开溢出提示；只检查静态 DOM 无法发现 tooltip 回读原值的回归。
  const hover = await evaluate(`(()=>{const rect=${cell(8)}.querySelector('.cell').getBoundingClientRect();return{x:rect.x+20,y:rect.y+rect.height/2}})()`)
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: hover.x, y: hover.y })
  await sleep(600)
  const tooltip = await evaluate(`([...document.querySelectorAll('.el-popper')]).filter(item=>item.getClientRects().length&&getComputedStyle(item).visibility!=='hidden').map(item=>item.textContent.trim())`)
  assert.ok(tooltip.includes(longMasked), '溢出提示必须显示脱敏后的完整文案: ' + JSON.stringify(tooltip))
  assert.ok(!tooltip.some(value => value.includes('SENSITIVE-ABCDEFGHIJKLMNOPQRSTUVWXYZ')), '溢出提示不能暴露原值')
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 1890, y: 1000 })

  assert.equal(await evaluate("document.querySelectorAll('#settings .settings-section').length"), 0, '单元格显示参数平铺，不再套常用配置分组')
  assert.equal(await evaluate("document.querySelectorAll('#settings .el-input-number').length"), 0, '关闭脱敏时隐藏位数配置')
  await step("document.querySelector('#settings .el-switch').click()")
  assert.equal(await evaluate('maskTest.state.settings.maskingEnabled'), true)
  assert.equal(await evaluate("document.querySelectorAll('#settings .el-input-number').length"), 2)
  const numberLabels = await evaluate(`([...document.querySelectorAll('#settings .el-form-item')]).filter(item=>item.querySelector('.el-input-number')).map(item=>item.querySelector('.el-form-item__label').textContent.trim())`)
  await setNumber(numberLabels[0], 2)
  await setNumber(numberLabels[1], 3)
  assert.equal(await evaluate('maskTest.state.settings.maskPrefixLength'), 2)
  assert.equal(await evaluate('maskTest.state.settings.maskSuffixLength'), 3)
  const saved = await evaluate('maskTest.roundTrip()')
  for (const result of [JSON.parse(saved.snapshot.field.renderConfig), saved.reloaded, saved.applied]) {
    assert.equal(result.maskingEnabled, true)
    assert.equal(result.maskPrefixLength, 2)
    assert.equal(result.maskSuffixLength, 3)
    assert.equal(result.emptyText, '暂无')
  }
  await step('maskTest.state.fields[0].renderConfig=JSON.stringify(maskTest.roundTrip().applied)')
  assert.equal(await text(0), '13******678', '模板配置保存、重载并应用后实际生效')

  await step('maskTest.toggle(false)')
  assert.equal(await text(0), '13812345678')
  assert.equal(await text(1), '13712345678')
  assert.equal(await text(2), '2026-10-12', '关闭脱敏恢复 DateFormatter 配置')
  assert.equal(await text(3), '待审核中')
  assert.equal(await evaluate(`${cell(3)}.querySelector('.el-tag')!==null`), true, '关闭脱敏恢复状态组件')
  assert.equal(await text(4), '13987654321')
  assert.ok(await evaluate('maskTest.state.extensionCalls>0'), '关闭脱敏恢复扩展渲染')
  assert.equal(await evaluate('JSON.stringify(maskTest.state.rows)===maskTest.originalRows'), true, '原始数据始终保持不变')
  assert.deepEqual(errors, [])
  console.log('list cell masking browser acceptance passed: automatic/default/date/status/custom renderers, empty/short/Unicode/zero/boolean values, clipboard/ARIA/overflow tooltip, flat schema, template round-trip, disable, unchanged row')
} finally {
  ws?.close()
  browser?.kill()
  await server?.close()
  await sleep(200)
  rmSync(fixture, { recursive: true, force: true })
  rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
}
