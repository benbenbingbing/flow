import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

// 挂载真实导入页和 BPMN Viewer；仅 API 使用内存模拟，验证用户操作及失败恢复。
const harness = `
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '/src/views/system/HistoryImport.vue'
import { useUserStore } from '/src/stores/user.js'
import { historyImportApi as api } from '/src/api/historyImport.js'
const copy=x=>JSON.parse(JSON.stringify(x))
const fields=[{code:'name',name:'名称',type:'STRING',required:true}]
const xml='<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" targetNamespace="test"><process id="p"><userTask id="review" name="经理审批"/></process><bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="p"><bpmndi:BPMNShape id="shape" bpmnElement="review"><dc:Bounds x="100" y="100" width="140" height="80"/></bpmndi:BPMNShape></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></definitions>'
const process={id:'v1',name:'重绘审批图',version:1,nodes:{review:'经理审批'},xml}
const plan={name:'历史报销迁入',sourceSystem:'legacy',entities:[{key:'main',entityCode:'expense',readonly:true,references:{}},{key:'items',entityCode:'item',readonly:true,references:{parent:'main'}}],processVersionId:'v1',nodeMappings:{经理:'review'},actionMappings:{通过:'APPROVED'}}
const batch={id:'b1',name:plan.name,plan,state:'READY',revision:3,validationHash:'h1',entitiesUploaded:true,historyUploaded:true,sourceNodes:['经理'],sourceActions:['通过'],counts:{entities:3,instances:1,events:2,groups:2},groupOrder:['shared','business'],groups:[{key:'business',state:'PENDING'},{key:'shared',state:'PENDING'}]}
const state={batch,calls:[],fail:true}
api.catalog=async()=>({entities:[{code:'expense',name:'报销',fields,relations:[{childEntity:'item',childField:'parent'}]},{code:'item',name:'报销明细',fields:[...fields,{code:'parent',name:'所属报销',type:'STRING',required:true}],relations:[]}],processes:[process]})
api.list=async()=>[copy(state.batch)]
api.get=async()=>copy(state.batch)
api.save=async(id,revision,plan)=>{state.batch.plan=copy(plan);state.batch.revision++;return copy(state.batch)}
api.create=async plan=>copy({...state.batch,id:'new',name:plan.name,plan,state:'DRAFT'})
api.template=async()=>new Blob(['fixture'])
api.execute=async(id,hash,key,retry)=>{
 state.calls.push({key,retry});const group=state.batch.groups.find(g=>g.key===key)
 if(key==='business'&&state.fail){state.fail=false;group.state='FAILED';group.error='模拟字段约束失败';state.batch.state='PARTIAL_SUCCESS';throw Error(group.error)}
 group.state='COMMITTED';group.error=null;group.result={created:key==='shared'?1:2,reused:0,archiveIds:key==='business'?['a1']:[]}
 state.batch.state=state.batch.groups.every(g=>g.state==='COMMITTED')?'SUCCEEDED':'RUNNING';return copy(state.batch)
}
api.archive=async()=>({id:'a1',sourceSystem:'legacy',instance:{name:'历史报销',sourceId:'P1',result:'APPROVED',initiator:'张三'},template:process,data:{name:'历史表单数据'},fields,related:[{entityCode:'item',recordId:'i1',name:'报销明细',fields,data:{name:'差旅明细'}}],events:[{id:'E1',nodeId:'review',sourceNode:'经理',actor:'李四',action:'APPROVED',rawAction:'通过',comment:'首次办理',sequence:1},{id:'E2',nodeId:'review',sourceNode:'经理',actor:'王五',action:'APPROVED',rawAction:'通过',comment:'再次办理',sequence:2}]})
const pinia=createPinia();const app=createApp(Page);app.use(pinia).use(ElementPlus);useUserStore().permissions=['*'];app.mount('#app');window.historyTest=state
`
const fixture = mkdtempSync(path.resolve('.history-import-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios'], entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3417, strictPort: true, hmr: false, forwardConsole: false } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:3417/${path.basename(fixture)}/index.html`)
  await page.getByRole('button', { name: '查看 / 继续', exact: true }).click()
  await page.getByText('所属报销 (parent) 的旧 ID 来自', { exact: true }).waitFor()
  const batchName = page.locator('.el-form-item').filter({ hasText: '批次名称' }).locator('input')
  await batchName.fill('修改后的批次')
  await page.getByText('配置已修改，请重新联合预检后再执行。', { exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '执行 / 继续导入', exact: true }).count(), 0)
  await batchName.fill('历史报销迁入')
  await page.getByRole('button', { name: '执行 / 继续导入', exact: true }).click()
  await page.getByRole('button', { name: '重试并继续', exact: true }).waitFor()
  assert.deepEqual(await page.evaluate(() => window.historyTest.calls.map(call => call.key)), ['shared', 'business'])
  await page.getByRole('button', { name: '重试并继续', exact: true }).click()
  await page.getByText('100%', { exact: true }).waitFor()
  assert.deepEqual(await page.evaluate(() => window.historyTest.calls.at(-1)), { key: 'business', retry: true })
  await page.getByRole('button', { name: '历史 1', exact: true }).click()
  await page.locator('.archive-recorded').waitFor()
  await page.locator('.djs-element[data-element-id="review"]').click()
  await page.getByText('节点：经理审批', { exact: true }).waitFor()
  await page.getByText('首次办理', { exact: true }).waitFor()
  await page.getByText('再次办理', { exact: true }).waitFor()
  assert.equal(await page.locator('.node-tooltip').count(), 0, '历史图不能出现原生运行态未开始提示')
  await page.screenshot({ path: '/tmp/history-import-archive.png', fullPage: true })
  await page.getByRole('tab', { name: '实体数据快照', exact: true }).click()
  await page.getByText('历史表单数据', { exact: true }).waitFor()
  await page.getByRole('button', { name: '报销明细 · i1', exact: true }).click()
  await page.getByText('差旅明细', { exact: true }).waitFor()
  await page.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.getByRole('button', { name: '复制配置新建', exact: true }).click()
  await page.getByRole('button', { name: '创建批次', exact: true }).waitFor()
  assert.deepEqual(errors, [])
  console.log('历史导入浏览器验证通过：关联配置、未保存配置保护、依赖顺序、失败重试、重复节点、BPMN 点击、只读关联快照、配置复制。')
} finally { await browser?.close(); await server?.close(); rmSync(fixture, { recursive: true, force: true }) }
