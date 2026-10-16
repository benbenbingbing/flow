import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实页面和 Element Plus，通过请求适配器隔离业务数据，覆盖筛选参数及弹窗提交语义。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '/src/App.vue'
import User from '/src/views/system/User.vue'
import Role from '/src/views/system/Role.vue'
import { useUserStore } from '/src/stores/user.js'
import request from '/src/utils/request.js'
const state = reactive({ page: 'user', failures: [], requests: [] })
const roles = Array.from({length: 25}, (_, i) => ({id: 'role-'+i, roleName: '角色'+String(i).padStart(2,'0'), roleCode: 'ROLE_'+i, description: '用于对应业务范围的访问授权', status: i%2 ? '1' : '0', sort: i, userCount: i, createTime: '2026-09-23 10:00:00'}))
const organizations = [
 {id:'org-1',parentId:'0',orgName:'示例集团',orgCode:'GROUP',type:'org'},
 {id:'org-2',parentId:'org-1',orgName:'华东分公司',orgCode:'EAST',type:'org'},
 {id:'dept-1',parentId:'org-2',orgName:'研发部',orgCode:'RD',type:'dept'},
 {id:'dept-2',parentId:'dept-1',orgName:'平台组',orgCode:'PLATFORM',type:'dept'},
 {id:'dept-3',parentId:'unavailable',orgName:'独立可见部门',orgCode:'VISIBLE',type:'dept'}
]
const positions = [{positionCode:'LEADER',positionName:'部门负责人',description:'负责部门业务审批'}, {positionCode:'MEMBER',positionName:'业务专员',description:'办理日常业务'}]
const users = Array.from({length: 24},(_,i)=>({id:'user-'+i,username:i ? 'user'+i : 'admin',nickname:i ? '用户'+i : '系统管理员',email:'user'+i+'@example.com',phone:'13800000000',orgName:'华东分公司',deptName:'研发部',status:'0',roles:[roles[0]],createTime:'2026-09-23 10:00:00'}))
request.defaults.adapter = async config => {
 state.requests.push({url:config.url,params:config.params || {},method:config.method})
 if(state.failures.includes(config.url)) throw new Error('模拟加载失败')
 let data = []
 if(config.url === '/system/user/page') { const {pageNum=1,pageSize=20}=config.params; data={records:users.slice((pageNum-1)*pageSize,pageNum*pageSize),total:users.length,pageNum,pageSize} }
 else if(config.url === '/system/role/list' || config.url === '/system/user/roles') data=roles.map(role=>({...role}))
 else if(config.url === '/system/org/enabled') data=organizations
 else if(config.url === '/system/position/enabled') data=positions
 return {data:{code:200,data},status:200,statusText:'OK',headers:{},config}
}
const pinia=createPinia()
const app=createApp({setup:()=>()=>h(state.page==='user' ? User : Role,{key:state.page})})
app.use(pinia).use(ElementPlus,{locale:zhCn})
const store=useUserStore(pinia)
store.permissions=['*']
app.mount('#app')
window.systemListTest={state,store,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,180));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.system-management-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><style>body{background:#f5f7fa}#app{padding:20px}</style></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'system-management-chrome-'))
let browser, ws, server, nextId = 0
const pending = new Map(), errors = [], logs = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function send(method, params = {}) {
  const id = ++nextId
  const result = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject: error => { clearTimeout(timer); reject(error) } })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return result
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}
const visible = 'e=>e.getClientRects().length>0'
const dialog = `([...document.querySelectorAll('.el-dialog')].filter(${visible})).at(-1)`
async function step(code) { await evaluate(code); await evaluate('systemListTest.settle()') }
async function button(text, root = 'document') {
  await step(`([...${root}.querySelectorAll('button')].filter(${visible})).find(e=>e.textContent.trim()===${JSON.stringify(text)}).click()`)
}
async function input(selector, value, root = 'document') {
  await step(`{ const input=${root}.querySelector(${JSON.stringify(selector)}); input.value=${JSON.stringify(value)}; input.dispatchEvent(new Event('input',{bubbles:true})); }`)
}
async function openPicker(title) { await step(`document.querySelector('button[aria-label="${title}"]').click()`) }
async function pickTree(label) {
  const option = `([...${dialog}.querySelectorAll('.tree-option')].filter(${visible})).find(e=>e.querySelector('.tree-label').textContent.trim()===${JSON.stringify(label)})`
  // 沿真实展开按钮访问深层节点，不依赖组件内部实例或直接修改选中状态。
  for (let depth = 0; depth < 6 && !await evaluate(`Boolean(${option})`); depth++) {
    await step(`([...${dialog}.querySelectorAll('.el-tree-node__expand-icon:not(.is-leaf):not(.expanded)')].filter(${visible})).forEach(e=>e.click())`)
  }
  await step(`${option}.click()`)
}
async function pickRow(label) { await step(`([...${dialog}.querySelectorAll('.el-table__body tr')]).find(e=>e.textContent.includes(${JSON.stringify(label)})).click()`) }
async function query() { await button('查询', "document.querySelector('.search-form')"); return evaluate("JSON.parse(JSON.stringify(systemListTest.state.requests.filter(r=>r.url==='/system/user/page').at(-1).params))") }
async function screenshot(name) { const shot=await send('Page.captureScreenshot',{format:'png'}); writeFileSync(path.join(tmpdir(),name+'.png'),Buffer.from(shot.data,'base64')) }
async function menuItem(label) { await step(`([...document.querySelectorAll('.el-dropdown-menu__item')].filter(${visible})).find(e=>e.textContent.trim()===${JSON.stringify(label)}).click()`) }
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios'] }, server: { host: '127.0.0.1', port: 3417, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9417', `--user-data-dir=${profile}`, 'about:blank'
  ], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9417/json/list')).json(); break } catch { await sleep(100) } }
  assert.ok(targets?.length, 'Chrome failed to start')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) { const p = pending.get(data.id); pending.delete(data.id); data.error ? p.reject(Error(JSON.stringify(data.error))) : p.resolve(data.result) }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
    if (data.method === 'Log.entryAdded') logs.push(data.params.entry.text)
    if (data.method === 'Runtime.consoleAPICalled' && data.params.type === 'error') logs.push(data.params.args.map(arg => arg.value || arg.description).join(' '))
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable'); await send('Page.enable'); await send('Log.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1050, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `http://127.0.0.1:3417/${path.basename(fixture)}/index.html` })
  for (let i = 0; i < 200; i++) { if (await evaluate('Boolean(window.systemListTest)')) break; await sleep(100) }
  assert.equal(await evaluate('Boolean(window.systemListTest)'), true, [...errors, ...logs, await evaluate('document.body.innerText.slice(0, 2000)')].join('\n'))
  await evaluate('systemListTest.settle()')


  assert.equal(await evaluate("document.querySelectorAll('.search-form .el-form-item').length"),3,'默认仅显示三个条件')
  assert.equal(await evaluate("document.querySelectorAll('.table-toolbar button').length"),4,'批量入口和新增按钮始终显示')
  assert.equal(await evaluate("[...document.querySelectorAll('.table-toolbar button')].filter(e=>e.textContent.includes('批量')).every(e=>e.disabled)"),true,'未选择用户时三个批量入口置灰')
  await screenshot('flow-system-user-list')
  await openPicker('选择组织/部门')
  await pickTree('研发部')
  await screenshot('flow-system-organization-picker')
  await button('取消',dialog)
  assert.equal((await query()).deptId,undefined,'取消不应用草稿')
  await openPicker('选择组织/部门')
  await pickTree('研发部')
  await button('确认选择',dialog)
  let params=await query()
  assert.equal(params.deptId,'dept-1'); assert.equal(params.orgId,undefined)
  await openPicker('选择组织/部门')
  await pickTree('华东分公司')
  await button('确认选择',dialog)
  params=await query()
  assert.equal(params.orgId,'org-2'); assert.equal(params.deptId,undefined,'切换组织清除旧部门')
  await openPicker('选择组织/部门')
  await input('input','PLATFORM',dialog)
  await pickTree('平台组')
  await button('确认选择',dialog)
  params=await query()
  assert.equal(params.deptId,'dept-2'); assert.equal(params.orgId,undefined,'深层部门搜索及组织条件互斥')
  await step(`document.querySelector('[aria-label="清空选择组织/部门"]').click()`)
  params=await query()
  assert.equal(params.deptId,undefined); assert.equal(params.orgId,undefined)

  await openPicker('选择角色')
  await step(`${dialog}.querySelector('.btn-next').click()`)
  await pickRow('角色12')
  await input('input','ROLE_24',dialog)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('.el-table__body tr').length`),1,'弹窗搜索覆盖所有分页')
  assert.match(await evaluate(`${dialog}.querySelector('.selected-item').textContent`),/角色12/,'搜索不丢已选草稿')
  await screenshot('flow-system-role-picker')
  await button('确认选择',dialog)
  assert.equal((await query()).roleId,'role-12')
  await openPicker('选择角色')
  await pickRow('角色00')
  await button('取消',dialog)
  assert.equal((await query()).roleId,'role-12','取消替换后保留原角色')
  await button('展开')
  assert.equal(await evaluate("document.querySelectorAll('.search-form .el-form-item').length"),5)
  await openPicker('选择职务')
  await pickRow('部门负责人')
  await button('确认选择',dialog)
  await button('收起（1）')
  params=await query()
  assert.equal(params.positionCode,'LEADER','折叠仍使用职务编码')
  await button('重置',"document.querySelector('.search-form')")
  params=await query()
  for(const key of ['keyword','orgId','deptId','roleId','positionCode','status']) assert.equal(params[key],undefined,'重置全部条件：'+key)
  assert.equal(params.pageNum,1)

  await step("document.querySelectorAll('.user-management .el-table__body tr')[1].querySelector('.el-checkbox__input').click()")
  assert.match(await evaluate("document.querySelector('.table-toolbar').textContent"),/批量分配角色/)
  assert.equal(await evaluate("[...document.querySelectorAll('.table-toolbar button')].filter(e=>e.textContent.includes('批量')).every(e=>!e.disabled)"),true,'选择用户后启用三个批量入口')
  assert.equal(await evaluate("document.querySelector('.user-management .el-table__body tr .el-checkbox').classList.contains('is-disabled')"),true,'管理员保持不可批量操作')
  await button('批量分配角色')
  assert.match(await evaluate(`${dialog}.textContent`),/批量分配角色/)
  await button('取消',dialog)

  // 检查用户原有入口与确认语义；仅取消操作，模拟接口也不执行账号写入。
  await button('批量启用')
  assert.match(await evaluate("document.querySelector('.el-message-box').textContent"),/批量启用用户/)
  await button('取消',"document.querySelector('.el-message-box')")
  await button('批量禁用')
  assert.match(await evaluate("document.querySelector('.el-message-box').textContent"),/批量禁用用户/)
  await button('取消',"document.querySelector('.el-message-box')")
  await button('新增用户',"document.querySelector('.table-toolbar')")
  assert.match(await evaluate(`${dialog}.textContent`),/初始密码/)
  await button('取消',dialog)
  const userRow = "document.querySelectorAll('.user-management .el-table__body tr')[1]"
  await button('查看',userRow)
  assert.match(await evaluate(`${dialog}.textContent`),/查看用户/)
  assert.match(await evaluate(`${dialog}.textContent`),/user1@example.com/)
  assert.match(await evaluate(`${dialog}.textContent`),/华东分公司 \/ 研发部/)
  assert.match(await evaluate(`${dialog}.textContent`),/角色00/)
  assert.equal(await evaluate(`${dialog}.querySelectorAll('input, select, textarea, [role="switch"]').length`),0,'查看用户只展示信息，不提供编辑控件')
  assert.deepEqual(await evaluate(`[...${dialog}.querySelectorAll('.el-dialog__footer button')].map(e=>e.textContent.trim())`),['关闭'],'只读详情不提供保存入口')
  await screenshot('flow-system-user-details')
  await button('关闭',dialog)
  await button('编辑',userRow)
  assert.match(await evaluate(`${dialog}.textContent`),/角色/)
  await button('取消',dialog)
  await step(`${userRow}.querySelector('[aria-label="更多用户操作"]').click()`)
  await menuItem('重置密码')
  assert.equal(await evaluate("document.querySelector('.el-message-box input').type"),'password')
  await button('取消',"document.querySelector('.el-message-box')")
  await step(`${userRow}.querySelector('[aria-label="更多用户操作"]').click()`)
  await menuItem('职务任命')
  assert.match(await evaluate(`${dialog}.textContent`),/任职用户/)
  await button('取消',dialog)
  await step(`${userRow}.querySelector('[aria-label="更多用户操作"]').click()`)
  await menuItem('删除用户')
  assert.match(await evaluate("document.querySelector('.el-message-box').textContent"),/请输入用户名/)
  await button('取消',"document.querySelector('.el-message-box')")
  await step(`${userRow}.querySelector('.el-checkbox__input').click()`)
  assert.equal(await evaluate("document.querySelectorAll('.table-toolbar button').length"),4,'取消勾选后批量入口仍然显示')
  assert.equal(await evaluate("[...document.querySelectorAll('.table-toolbar button')].filter(e=>e.textContent.includes('批量')).every(e=>e.disabled)"),true,'取消勾选后批量入口恢复置灰')

  await step("systemListTest.state.page='role'")
  assert.equal(await evaluate("document.querySelectorAll('.search-form .el-form-item').length"),3)
  assert.equal(await evaluate("document.querySelectorAll('.role-management .el-table__body tr').length"),20)
  await step("document.querySelector('.role-management .pagination .btn-next').click()")
  assert.equal(await evaluate("document.querySelectorAll('.role-management .el-table__body tr').length"),5)
  await input('input[placeholder=请输入角色名称]','角色24')
  assert.equal(await evaluate("document.querySelectorAll('.role-management .el-table__body tr').length"),5,'输入但未查询不改变结果')
  await button('查询',"document.querySelector('.search-form')")
  assert.equal(await evaluate("document.querySelectorAll('.role-management .el-table__body tr').length"),1)
  assert.match(await evaluate("document.querySelector('.role-management .el-table__body tr').textContent"),/角色24/)
  await button('重置',"document.querySelector('.search-form')")
  await input('input[placeholder=请输入角色编码]',' role_2 ')
  await button('查询',"document.querySelector('.search-form')")
  assert.equal(await evaluate("document.querySelectorAll('.role-management .el-table__body tr').length"),6,'编码筛选忽略大小写和首尾空格')
  await step("document.querySelector('.search-form .el-select').click()")
  await step("([...document.querySelectorAll('.el-select-dropdown__item')]).find(e=>e.textContent==='禁用').click()")
  await button('查询',"document.querySelector('.search-form')")
  assert.equal(await evaluate("document.querySelectorAll('.role-management .el-table__body tr').length"),2,'状态与编码联合筛选')
  await input('input[placeholder=请输入角色名称]','不存在')
  await button('查询',"document.querySelector('.search-form')")
  assert.match(await evaluate("document.querySelector('.role-management .el-table').textContent"),/当前条件下没有角色/)
  await button('重置',"document.querySelector('.search-form')")
  await screenshot('flow-system-role-list')
  await button('编辑',"document.querySelector('.role-management .el-table__body tr')")
  assert.match(await evaluate(`${dialog}.textContent`),/角色名称/,'编辑入口仍有效')
  await button('取消',dialog)
  await button('分配',"document.querySelector('.role-management .el-table__body tr')")
  assert.match(await evaluate(`${dialog}.textContent`),/未分配/)
  await button('取消',dialog)
  await button('用户',"document.querySelector('.role-management .el-table__body tr')")
  assert.match(await evaluate(`${dialog}.textContent`),/角色用户/)
  await button('新增用户',dialog)
  assert.match(await evaluate(`${dialog}.textContent`),/所属角色/)
  await button('取消',dialog)
  await button('关闭',"document.querySelector('.role-user-dialog')")
  await button('删除',"document.querySelector('.role-management .el-table__body tr')")
  assert.match(await evaluate("document.querySelector('.el-message-box').textContent"),/删除角色/)
  await button('取消',"document.querySelector('.el-message-box')")
  assert.equal(await evaluate("systemListTest.state.requests.filter(r=>r.method==='post').length"),0,'取消原有操作不会发送写请求')

  await send('Emulation.setDeviceMetricsOverride', {width:390,height:844,deviceScaleFactor:1,mobile:false})
  await step("systemListTest.state.page='user';systemListTest.store.permissions=[]")
  await button('展开')
  assert.equal(await evaluate("document.querySelectorAll('.search-form .el-form-item').length"),4,'无职务权限时不显示职务条件')
  assert.equal(await evaluate('document.documentElement.scrollWidth <= window.innerWidth'),true,'窄屏页面不溢出')
  await screenshot('flow-system-user-mobile')

  await step("systemListTest.state.page='role'")
  await step("systemListTest.state.failures=['/system/org/enabled'];systemListTest.state.page='user'")
  await openPicker('选择组织/部门')
  assert.match(await evaluate(`${dialog}.textContent`),/选项加载失败/)
  await step("systemListTest.state.failures=[]")
  await button('重新加载',dialog)
  await pickTree('独立可见部门')
  await button('确认选择',dialog)
  assert.equal((await query()).deptId,'dept-3','缺少父节点时保留可见部门且重试可恢复')
  assert.deepEqual(errors,[])
  console.log('system management browser acceptance passed: collapsed filters, tree selection, single-select confirm/cancel, pagination, reset, permissions, retry, responsive layout')
} finally {
  ws?.close(); browser?.kill(); await server?.close(); await sleep(200)
  rmSync(fixture, {recursive:true,force:true})
  rmSync(profile, {recursive:true,force:true,maxRetries:3,retryDelay:200})
}
