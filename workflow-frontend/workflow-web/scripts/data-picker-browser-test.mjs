import assert from 'node:assert/strict'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { chromium } from '@playwright/test'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 运行真实 Element Plus/Vant 面板；内存接口按相同绑定协议提供数据，不访问业务库。
const harness = `
import {createApp,h,reactive,nextTick} from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'vant/lib/index.css'
import PcField from '/src/extensions/builtin/fields/components/DataPickerField.vue'
import MobileField from '/@fs/${path.resolve('../packages/workflow-mobile-ui/src/fields/MobileFieldRenderer.vue')}'
const schema={schemaVersion:'1',valueField:'code',labelField:'name',fields:[{code:'code',type:'STRING',label:'客户编码'},{code:'name',type:'STRING',label:'客户名称'},{code:'contact',type:'STRING',label:'联系人'}],columns:[{field:'code'},{field:'name'}]}
const state=reactive({pc:{customer:'',contact:''},mobile:{customer:'',contact:''},disabled:false,failure:false,delay:false,scope:'A',calls:[],patches:[]})
const pending=[]
const fields={}
const fieldRefs={}
for(const side of ['pc','mobile'])fields[side]={fieldId:'customer-id',fieldCode:'customer',fieldName:'客户',fieldType:'STRING',componentType:'data_picker',componentProps:{pickerSchema:schema,pickerPageSize:1,pickerTitle:side==='pc'?'PC客户':'手机客户',pickerMappings:[{sourcePath:'selection.data.contact',targetPath:'form.contact',overwrite:'ALWAYS',clearOnEmpty:false}]},dataSourceBindings:Object.fromEntries(['SCHEMA','QUERY','RESOLVE'].map(code=>['FIELD_PICKER_'+code,{extensionId:code}]))}
// 从旧选择组件切换过来时可能残留 FIELD_OPTIONS；移动宿主不得重复调用通用选项接口。
fields.mobile.dataSourceBindings.FIELD_OPTIONS={extensionId:'old-options'}
const records=[{code:'001',name:'甲客户',contact:'联系人甲'},{code:'002',name:'乙客户',contact:'联系人乙'}]
function runtime(side){return {executeOwnerUsage:async(_field,usage,context)=>{
 const picker=context.input.picker;state.calls.push({side,usage,picker,scope:state.scope})
 if(usage==='FIELD_PICKER_SCHEMA')return [schema]
 if(usage==='FIELD_PICKER_QUERY'){
  if(state.failure)throw Error('接口暂时不可用')
  const filtered=records.filter(row=>!picker.keyword||row.name.includes(picker.keyword))
  return [{records:filtered.slice((picker.pageNum-1)*picker.pageSize,picker.pageNum*picker.pageSize),total:filtered.length}]
 }
 const result=records.filter(row=>picker.values.includes(row.code)).map(row=>({...row,contact:row.contact+'最新'}))
 if(state.delay)return new Promise(resolve=>pending.push(()=>resolve([result])))
 return [result]
}}}
const runtimes={pc:runtime('pc'),mobile:runtime('mobile')}
const app=createApp({setup(){return()=>h('main',{style:'max-width:900px;margin:20px auto'},['pc','mobile'].map(side=>h('section',{id:side,style:'margin:20px;border:1px solid #ddd;padding:16px'},[
 h('h2',side==='pc'?'电脑端':'移动端'),h(side==='pc'?PcField:MobileField,{ref:value=>{fieldRefs[side]=value},field:fields[side],modelValue:state[side].customer,formData:state[side],mode:'edit',disabled:state.disabled,
 context:{form:{id:'f',configHash:'hash'},mode:'edit',params:{scope:state.scope},getFormData:()=>state[side]},dataSourceRuntime:runtimes[side],
 'onPatch-form':patch=>{state.patches.push({side,patch});Object.assign(state[side],patch)}}),h('p',state[side].contact)
 ])))}})
app.use(ElementPlus).mount('#app')
window.pickerTest={state,pending,validate:side=>fieldRefs[side].validate(),settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,350));await nextTick()},release:()=>{while(pending.length)pending.shift()()}}
`
const fixture = mkdtempSync(path.resolve('.data-picker-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><meta name="viewport" content="width=device-width,initial-scale=1"><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser
try {
  server = await createServer({ configFile: false, plugins: [vue()], resolve: { dedupe: ['vue'], alias: [
    { find: '@flow/workflow-core/vue/useDataPickerFieldRuntime', replacement: path.resolve('../packages/workflow-core/src/composables/useDataPickerFieldRuntime.js') }
  ] }, optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, cacheDir: path.join(fixture, 'cache'), server: { host: '127.0.0.1', port: 3398, strictPort: true, fs: { allow: [path.resolve('..')] } } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1100, height: 850 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:3398/${path.basename(fixture)}/index.html`)
  await page.waitForFunction(() => Boolean(window.pickerTest))
  const settle = () => page.evaluate(() => pickerTest.settle())
  const state = side => page.evaluate(side => ({ ...pickerTest.state[side] }), side)
  await page.locator('#pc').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  assert.match(await page.locator('.el-dialog:visible').innerText(), /客户编码/)
  await page.locator('.el-dialog:visible').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  assert.deepEqual(await state('pc'), { customer: '001', contact: '联系人甲最新' })
  assert.equal(await page.evaluate(() => pickerTest.state.patches.length), 1, '主值与回填只产生一批补丁')
  await page.locator('#pc').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  await page.locator('.el-pagination .btn-next').click(); await settle()
  assert.match(await page.locator('.el-dialog:visible').innerText(), /乙客户/)
  await page.locator('.el-dialog:visible').getByRole('button', { name: '取消', exact: true }).click(); await settle()
  assert.equal((await state('pc')).customer, '001')
  await page.locator('#pc').getByRole('button', { name: '清空', exact: true }).click(); await settle()
  assert.deepEqual(await state('pc'), { customer: null, contact: '联系人甲最新' })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.locator('#mobile .van-field').first().click(); await settle()
  await page.locator('.van-popup:visible').getByRole('button', { name: '下一页', exact: true }).click(); await settle()
  await page.locator('.van-popup:visible .picker-record').click(); await settle()
  assert.deepEqual(await state('mobile'), { customer: '002', contact: '联系人乙最新' })
  assert.equal(await page.evaluate(() => pickerTest.validate('mobile')), true, '移动渲染宿主透传选择器校验')
  assert.equal(await page.locator('#mobile .van-field input').first().inputValue(), '乙客户')
  await page.locator('#mobile .van-field').first().click(); await settle()
  await page.locator('.van-popup:visible input').fill('甲')
  await page.locator('.van-popup:visible').getByRole('button', { name: '搜索', exact: true }).click(); await settle()
  assert.equal(await page.locator('.van-popup:visible .picker-record').count(), 1)
  await page.locator('.van-popup:visible .van-nav-bar__left').click(); await settle()
  assert.equal((await state('mobile')).customer, '002')

  // 只读也能回显；点击不能打开面板。
  await page.evaluate(() => { pickerTest.state.disabled = true }); await settle()
  await page.locator('#mobile .van-field').first().click(); await settle()
  assert.equal(await page.locator('.van-popup:visible').count(), 0)
  assert.equal(await page.locator('#mobile .van-field input').first().inputValue(), '乙客户')
  await page.evaluate(() => { pickerTest.state.disabled = false; pickerTest.state.failure = true }); await settle()
  await page.locator('#mobile .van-field').first().click(); await settle()
  assert.match(await page.locator('.van-popup:visible').innerText(), /接口暂时不可用/)
  assert.equal(await page.locator('.van-popup:visible .picker-record').count(), 0)
  await page.evaluate(() => { pickerTest.state.failure = false });
  await page.locator('.van-popup:visible').getByRole('button', { name: '搜索', exact: true }).click(); await settle()
  await page.evaluate(() => { pickerTest.state.delay = true })
  await page.locator('.van-popup:visible .picker-record').click(); await settle()
  await page.evaluate(() => { pickerTest.state.scope = 'B' }); await settle()
  await page.evaluate(() => pickerTest.release()); await settle()
  assert.equal(await page.locator('.van-popup:visible').count(), 0)
  assert.equal((await state('mobile')).customer, '002', '旧选择确认不写入新参数范围')
  await page.evaluate(() => { pickerTest.state.delay = false }); await settle()
  await page.locator('#mobile .van-field').first().click(); await settle()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, '390px 屏幕无水平溢出')
  assert.deepEqual(errors, [])
  await page.screenshot({ path: '/tmp/flow-data-picker-mobile.png', fullPage: true })
  console.log('data picker browser acceptance passed: PC/mobile dynamic columns, pagination, search, cancel, atomic fill, clear, readonly restore, failure/retry, stale confirm isolation')
} finally {
  await browser?.close(); await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
