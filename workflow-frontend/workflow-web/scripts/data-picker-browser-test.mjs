import assert from 'node:assert/strict'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { chromium } from '@playwright/test'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 运行真实 Element Plus/Vant 面板；内存接口按相同绑定协议提供数据，不访问业务库。
const harness = `
import {createApp,h,reactive,nextTick} from 'vue'
import ElementPlus,{ElForm} from 'element-plus'
import 'element-plus/dist/index.css'
import 'vant/lib/index.css'
import PcField from '/src/extensions/builtin/fields/components/DataPickerField.vue'
import DataPickerSettings from '/src/components/form-designer/DataPickerSettings.vue'
import MobileField from '/@fs/${path.resolve('../packages/workflow-mobile-ui/src/fields/MobileFieldRenderer.vue')}'
import {pickerListSchema} from '@flow/workflow-core/data-picker'
import {entityListRuntimeApi} from '@/api/entityListRuntime'
const schema={schemaVersion:'1',valueField:'code',labelField:'name',fields:[{code:'code',type:'STRING',label:'客户编码'},{code:'name',type:'STRING',label:'客户名称'},{code:'contact',type:'STRING',label:'联系人'}],columns:[{field:'code'},{field:'name'}]}
const state=reactive({pc:{customer:'',contact:''},mobile:{customer:'',contact:''},disabled:false,failure:false,delay:false,scope:'A',calls:[],patches:[]})
const pending=[]
const fields=reactive({})
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
 context:{form:{id:'f',configHash:'hash'},mode:'edit',params:{scope:state.scope},getFormData:()=>state[side]},dataSourceRuntime:runtimes[side],services:{entityListRuntime:entityListRuntimeApi},
 'onUpdate:modelValue':value=>{state[side].customer=value},
 'onPatch-form':patch=>{state.patches.push({side,patch});Object.assign(state[side],patch)}}),h('p',state[side].contact)
 ])))}})
app.use(ElementPlus).mount('#app')
const listFields=[{fieldCode:'name',fieldName:'成员姓名',showInList:true,isQuery:true,queryType:'LIKE'},{fieldCode:'contact',fieldName:'工号',showInList:true},
 {fieldCode:'externalNo',fieldName:'外部编号',showInList:true},{fieldCode:'amount',fieldName:'金额',showInList:true},
 {fieldCode:'shipDay',fieldName:'交付日期',showInList:true},{fieldCode:'enabled',fieldName:'启用',showInList:true}]
const listRecords=[{id:'0001',name:'成员甲',project:'A',data:{contact:'NO1',externalNo:'9223372036854775807',shipDay:'2024-02-29'}},
 {id:'0002',name:'成员乙',project:'B',data:{contact:'NO2',amount:'123456789.123456789',enabled:0}}]
window.pickerTest={state,pending,validate:side=>fieldRefs[side].validate(),settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,350));await nextTick()},release:()=>{while(pending.length)pending.shift()()},
 listSchema:()=>({fields:listFields,configHash:'member-list-release',viewConfig:{
  inputParameterSchema:{properties:{projectId:{type:'string'}},required:['projectId']},
  inputParameterBindings:[{parameter:'projectId',usage:'FILTER',targetField:'project'}]
 }}),
 listQuery:(entityCode,listKey,input)=>{
  state.calls.push({entityCode,listKey,input})
  const scoped=listRecords.filter(row=>row.project===input.context.parameters.projectId
    && (!input.selectionRecordId||row.id===input.selectionRecordId)
    && (!input.filters?.name||row.name.includes(input.filters.name)))
  return {records:scoped,total:scoped.length}
 },
 useList:()=>{
  for(const side of ['pc','mobile']){
   state[side]={customer:'',contact:'',project_id:side==='pc'?'A':'B'}
   Object.assign(fields[side],{dataSourceBindings:{},componentProps:{...fields[side].componentProps,pickerSource:'ENTITY_LIST',
    pickerSchema:pickerListSchema(listFields,[{fieldCode:'contact',fieldType:'STRING'}]),
    pickerList:{entityId:'members',entityCode:'member',listKey:'picker',parameterMappings:[{parameter:'projectId',sourceType:'FIELD',sourceField:'project_id'}]}
   }})
  }
 },
 useTyped:(kind='numeric')=>{
  state.calls.length=0
  for(const side of ['pc','mobile']){
   const [initialValue, fieldType, writeField]=kind==='numeric'
    ? (side==='pc'?['9100000000000000000','LONG','externalNo']:['0.010000000000000001','DECIMAL','amount'])
    : (side==='pc'?['2024-02-01','DATE','shipDay']:[true,'BOOLEAN','enabled'])
   state[side].customer=initialValue
   fields[side].fieldType=fieldType
   Object.assign(fields[side].componentProps,{pickerValueMode:'FIELD',pickerWriteField:writeField,
    pickerSchema:pickerListSchema(listFields,[{fieldCode:'contact',fieldType:'STRING'},{fieldCode:writeField,fieldType}])})
  }
 },
 mountSettings:()=>{
  const field=reactive({fieldType:'TEXT',fieldCode:'customer',componentProps:{pickerSchemaSource:'MANUAL',pickerSchema:schema,
   pickerWriteField:'name',pickerMappings:[{sourcePath:'selection.data.contact',targetPath:'form.contact',overwrite:'ALWAYS',clearOnEmpty:false}]}})
  const host=document.createElement('main')
  host.id='settings';host.style.margin='16px'
  document.body.append(host)
  createApp({setup(){return()=>h(ElForm,{labelWidth:'90px',size:'small'},()=>h(DataPickerSettings,{field,formId:'f',
   fields:[{fieldId:'contact-id',fieldCode:'contact',fieldType:'STRING',fieldLabel:'联系人'},
    {fieldId:'customer-no-id',fieldCode:'customer_no',fieldType:'STRING',fieldLabel:'客户编号'}]}))}}).use(ElementPlus).mount(host)
 }
}
`
const fixture = mkdtempSync(path.resolve('.data-picker-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><meta name="viewport" content="width=device-width,initial-scale=1"><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
writeFileSync(path.join(fixture, 'listApi.js'), "export const entityListRuntimeApi={getSchema:(...args)=>window.pickerTest.listSchema(...args),query:(...args)=>window.pickerTest.listQuery(...args)}")
let server, browser
try {
  server = await createServer({ configFile: false, plugins: [vue()], resolve: { dedupe: ['vue'], alias: [
    { find: '@flow/workflow-core/vue/useDataPickerFieldRuntime', replacement: path.resolve('../packages/workflow-core/src/composables/useDataPickerFieldRuntime.js') },
    { find: '@/api/entityListRuntime', replacement: path.join(fixture, 'listApi.js') },
    { find: '@', replacement: path.resolve('src') }
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
  await page.locator('.van-popup:visible .van-nav-bar__left').click(); await settle()
  await page.evaluate(() => pickerTest.useList()); await settle()
  await page.setViewportSize({ width: 1100, height: 850 })
  const dialog = page.locator('.el-dialog:visible')
  await page.locator('#pc').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  assert.match(await dialog.innerText(), /成员甲/)
  assert.doesNotMatch(await dialog.innerText(), /成员乙/)
  await dialog.getByRole('textbox', { name: '成员姓名' }).fill('甲')
  await dialog.getByRole('button', { name: '搜索', exact: true }).click(); await settle()
  await dialog.getByRole('button', { name: '选择', exact: true }).click(); await settle()
  assert.deepEqual(await state('pc'), { customer: '0001', contact: 'NO1', project_id: 'A' })
  assert.equal(await page.locator('#pc input').first().inputValue(), '成员甲')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.locator('#mobile .van-field').first().click(); await settle()
  const options = page.locator('.van-popup:visible .picker-record')
  assert.equal(await options.count(), 1)
  assert.match(await options.first().innerText(), /成员乙/)
  await options.first().click(); await settle()
  assert.deepEqual(await state('mobile'), { customer: '0002', contact: 'NO2', project_id: 'B' })
  assert.equal(await page.locator('#mobile .van-field input').first().inputValue(), '成员乙')
  const listCalls = await page.evaluate(() => pickerTest.state.calls.filter(call => call.listKey === 'picker'))
  assert.ok(listCalls.some(call => call.input.filters?.name === '甲' && call.input.filters.name_op === 'LIKE'))
  assert.ok(listCalls.some(call => call.input.selectionRecordId === '0002' && call.input.context.parameters.projectId === 'B'))

  await page.evaluate(() => pickerTest.useTyped()); await settle()
  assert.equal(await page.evaluate(() => pickerTest.state.calls.length), 0, '业务值打开时直接回显，无按键解析')
  assert.equal(await page.locator('#pc input').first().inputValue(), '9100000000000000000')
  assert.equal(await page.locator('#mobile .van-field input').first().inputValue(), '0.010000000000000001')
  await page.locator('#mobile').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  await options.first().click(); await settle()
  assert.equal((await state('mobile')).customer, '123456789.123456789')
  assert.equal(await page.locator('#mobile .van-field input').first().inputValue(), '123456789.123456789')
  await page.setViewportSize({ width: 1100, height: 850 })
  await page.locator('#pc').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  await dialog.getByRole('button', { name: '选择', exact: true }).click(); await settle()
  assert.equal((await state('pc')).customer, '9223372036854775807')
  const longInput = page.locator('#pc input').first()
  assert.equal(await longInput.inputValue(), '9223372036854775807')
  await longInput.fill('9223372036854775808'); await settle()
  assert.equal(await page.evaluate(() => pickerTest.validate('pc')), false)
  assert.equal((await state('pc')).customer, '9223372036854775807')
  await longInput.fill('9223372036854775806'); await settle()
  assert.equal(await page.evaluate(() => pickerTest.validate('pc')), true)
  assert.equal((await state('pc')).customer, '9223372036854775806')

  await page.evaluate(() => pickerTest.useTyped('date-boolean')); await settle()
  assert.equal(await page.locator('#pc .el-date-editor input').inputValue(), '2024-02-01')
  await page.locator('#pc').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  await dialog.getByRole('button', { name: '选择', exact: true }).click(); await settle()
  assert.equal(await page.locator('#pc .el-date-editor input').inputValue(), '2024-02-29')
  await page.setViewportSize({ width: 390, height: 844 })
  const toggle = page.locator('#mobile').getByRole('switch')
  assert.equal(await toggle.getAttribute('aria-checked'), 'true')
  await page.locator('#mobile').getByRole('button', { name: '选择', exact: true }).click(); await settle()
  await options.first().click(); await settle()
  assert.equal((await state('mobile')).customer, false)
  assert.equal(await toggle.getAttribute('aria-checked'), 'false')
  await page.screenshot({ path: '/tmp/flow-data-picker-mobile.png', fullPage: true })

  await page.setViewportSize({ width: 1100, height: 900 })
  await page.evaluate(() => pickerTest.mountSettings())
  const settings = page.locator('#settings')
  const source = settings.getByRole('region', { name: '数据来源' })
  const returnedFields = settings.getByRole('region', { name: '返回字段' })
  const appearance = settings.getByRole('region', { name: '弹窗与回显' })
  const write = settings.getByRole('region', { name: '选择后写入' })
  async function checkHelp(section, label, example) {
    const button = section.getByRole('button', { name: `查看${label}配置说明` })
    await button.hover()
    await page.getByRole('tooltip').filter({ hasText: example }).waitFor()
  }
  await checkHelp(source, '分页查询接口', /pageNum.*"records".*"total"/s)
  await checkHelp(source, '按标识查询', /input\.picker\.values.*"C001"/s)
  await source.getByRole('combobox', { name: '按标识查询' }).waitFor()
  assert.match(await returnedFields.innerText(), /字段编码\s+字段名称\s+字段类型/)
  assert.match(await appearance.innerText(), /弹窗展示列/)
  assert.match(await write.innerText(), /写入当前字段/)
  await write.getByRole('button', { name: '添加映射' }).click()
  const mapping = write.locator('.picker-grid-row').last()
  // 下拉面板挂在 body 上，按控件的 aria-controls 查找，避免匹配另一侧仍在收起的同名选项。
  async function chooseOption(select, name) {
    const listId = await select.getByRole('combobox').getAttribute('aria-controls')
    await select.click()
    await page.locator(`[id="${listId}"]`).getByRole('option', { name, exact: true }).click()
  }
  const mappingSelects = mapping.locator('.el-select')
  await chooseOption(mappingSelects.nth(0), '客户编码 (code)')
  await chooseOption(mappingSelects.nth(1), '客户编号 (customer_no)')
  assert.match(await mapping.innerText(), /客户编码 \(code\)\s+→\s+客户编号 \(customer_no\)/)
  await settle()

  for (const width of [1100, 390]) {
    await page.setViewportSize({ width, height: 900 })
    const [fromBox, toBox, policyBox] = await Promise.all([0, 1, 2].map(index => mappingSelects.nth(index).boundingBox()))
    assert.ok(fromBox && toBox && policyBox)
    assert.equal(fromBox.y, toBox.y, `在 ${width}px 宽度下来源和目标同排`)
    assert.equal(policyBox.y > toBox.y, width === 390)
    assert.equal(await settings.evaluate(panel => panel.scrollWidth <= panel.clientWidth), true)
  }
  await chooseOption(returnedFields.locator('.el-select').first(), '通过字段描述接口获取')
  assert.equal(await returnedFields.getByRole('combobox', { name: '字段描述接口' }).count(), 1)
  assert.equal(await source.getByRole('combobox', { name: '字段描述接口' }).count(), 0)
  assert.equal(await appearance.count(), 0)
  await checkHelp(returnedFields, '字段描述接口', /"valueField".*"fields".*"columns"/s)
  await checkHelp(returnedFields, '测试表单数据', /data\.project_id.*保存当前属性.*获取字段描述/s)
  await source.getByText('实体列表', { exact: true }).click()
  assert.match(await source.innerText(), /目标实体/)
  assert.equal(await returnedFields.count(), 0)
  assert.deepEqual(errors, [])
  console.log('data picker browser acceptance passed: PC/mobile interface and entity list runtime, designer groups and mapping layout')
} finally {
  await browser?.close(); await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
