import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 真实组件配合有界目录夹具：任何旧全量接口调用立即报错，防止懒加载只停留在渲染层。
const fixture = mkdtempSync(path.resolve('.organization-selection-fixture-'))
const harness = `
import { createApp, h, nextTick, reactive } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import Selector from '/src/components/OrganizationSelector.vue'
import ScopeEditor from '/src/components/PersonScopeRuleEditor.vue'
import Assignment from '/src/views/system/components/PositionAssignmentDialog.vue'
import Position from '/src/views/system/Position.vue'
import User from '/src/views/system/User.vue'
import Role from '/src/views/system/Role.vue'
import { useUserStore } from '/src/stores/user'
import request from '/src/utils/request'
const group = { id:'group', parentId:'0', orgName:'测试集团', orgCode:'GROUP', type:'org', status:'1', path:'/0/group/' }
const other = { ...group, id:'other', orgName:'其他集团', orgCode:'OTHER', status:'0', path:'/0/other/' }
const departments = Array.from({length:55}, (_,i) => {
  const suffix=String(i).padStart(2,'0')
  return {id:'dept'+suffix,parentId:'group',orgName:'部门'+suffix,orgCode:'DEPT'+suffix,type:'dept',status:i===0?'1':'0',path:'/0/group/dept'+suffix+'/'}
})
const nested = {...departments[54],id:'nested',parentId:'dept54',orgName:'深层部门',orgCode:'DEEP',path:'/0/group/dept54/nested/'}
const nodes = [group, other, ...departments, nested]
const decorate = row => ({ ...row, hasChildren:nodes.some(n=>n.parentId===row.id), displayPath:row.path.split('/').filter(id=>id&&id!=='0').map(id=>nodes.find(n=>n.id===id)?.orgName).join(' / ') })
const position = {id:'p1',positionCode:'LEADER',positionName:'部门负责人',applicableUnitType:'DEPT',holderMode:'SINGLE',status:'ENABLED'}
const assignment = {id:'a1',userId:'u1',username:'test',displayName:'测试用户',organizationUnitId:'dept54',organizationUnitName:'部门54',organizationUnitType:'dept',positionCode:'LEADER',positionName:'部门负责人',holderMode:'SINGLE',applicableUnitType:'DEPT'}
const state = reactive({mode:'single',revision:0,value:'',type:'dept',valueKey:'id',multiple:false,calls:[],writes:[],selected:null,failNext:false,delayKeyword:'',open:true,lockedUnit:null,lockedUser:null,scope:[{type:'ORGANIZATION',values:['DEEP'],includeChildren:true}]})
request.get = async (url, config={}) => {
  const params=config.params||{}
  state.calls.push({url,...params})
  if(['/system/org/tree','/system/org/enabled'].includes(url)) throw Error('禁止全量加载组织目录')
  if(url==='/system/org/children') {
    if(state.failNext){state.failNext=false;throw Error('模拟目录失败')}
    return nodes.filter(row=>row.parentId===(params.parentId||'0')&&(!params.type||row.type===params.type)).map(decorate)
  }
  if(url==='/system/org/options/page') {
    if(state.failNext){state.failNext=false;throw Error('模拟目录失败')}
    let rows=nodes.filter(row=>params.keyword ? row.status==='0'&&(!params.type||row.type===params.type)&&(row.orgName.includes(params.keyword)||row.orgCode.includes(params.keyword)) : row.parentId===(params.parentId||'0'))
    const start=((params.pageNum||1)-1)*params.pageSize
    const result={records:rows.slice(start,start+params.pageSize).map(decorate),total:rows.length}
    if(state.delayKeyword===params.keyword) await new Promise(r=>setTimeout(r,400))
    return result
  }
  if(url==='/system/org/options/resolve') {
    const keys=String(params.values||'').split(',')
    return nodes.filter(row=>keys.includes(params.valueKey==='code'?row.orgCode:row.id)).map(decorate)
  }
  if(url.endsWith('/ancestors')) {const id=url.split('/')[3];return nodes.find(n=>n.id===id).path.split('/').filter(id=>id&&id!=='0').map(id=>decorate(nodes.find(n=>n.id===id)))}
  if(url==='/system/position/enabled') return [position]
  if(url==='/system/position/page') return {records:[position],total:1}
  if(url==='/system/position/assignments/page') return {records:[assignment],total:1}
  if(url.endsWith('/position-assignments')) return {assignments:[assignment]}
  if(url==='/entity-selector/USER/batch') return [{id:'u1',name:'测试用户',code:'test'}]
  if(url==='/system/user/page') return {records:[{id:'u1',username:'test',nickname:'测试用户',status:'0',roles:[]}],total:1}
  if(url==='/system/role/list'||url==='/system/user/roles') return [{id:'r1',roleName:'测试角色',roleCode:'ROLE1',status:'0',userCount:1}]
  return []
}
request.put=async(url,payload)=>{state.writes.push({url,payload});return {}}
request.post=async(url,payload)=>{state.writes.push({url,payload});return {}}
const pinia=createPinia()
const app=createApp({setup:()=>()=>{
  const key=state.mode+state.revision
  if(state.mode==='position')return h(Position,{key})
  if(state.mode==='user')return h(User,{key})
  if(state.mode==='role')return h(Role,{key})
  if(state.mode==='assignment')return h(Assignment,{key,modelValue:state.open,'onUpdate:modelValue':v=>state.open=v,lockedUnit:state.lockedUnit,lockedUser:state.lockedUser,lockedPosition:position})
  if(state.mode==='scope')return h(ScopeEditor,{key,modelValue:state.scope,'onUpdate:modelValue':v=>state.scope=v})
  return h(Selector,{key,modelValue:state.value,'onUpdate:modelValue':v=>state.value=v,type:state.type,valueKey:state.valueKey,multiple:state.multiple,onSelected:row=>state.selected=row})
}}).use(pinia).use(ElementPlus,{locale:zhCn})
useUserStore(pinia).setPermissions(['*'])
app.mount('#app')
window.orgSelectionTest={state,nodes,decorate,async mount(mode,patch={}){Object.assign(state,patch,{mode,revision:state.revision+1});await nextTick();await new Promise(r=>setTimeout(r,100));state.calls=[]}}
`
writeFileSync(path.join(fixture, 'index.html'), '<html lang="zh-CN"><meta charset="utf-8"><body style="margin:20px"><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios'] }, server: { host: '127.0.0.1', port: 0, watch: { ignored: ['**/.*fixture*/**'] } } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1500, height: 1100 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/${path.basename(fixture)}/index.html`)
  await page.waitForLoadState('networkidle')
  await page.waitForFunction(() => !!window.orgSelectionTest)
  const mount = (mode, patch = {}) => page.evaluate(([mode, patch]) => orgSelectionTest.mount(mode, patch), [mode, patch])
  const calls = () => page.evaluate(() => orgSelectionTest.state.calls)
  const picker = page.getByRole('dialog', { name: '选择组织或部门', exact: true })
  const search = async (root, keyword) => {
    await root.locator('.management-navigation-tree > .el-input input').fill(keyword)
    await root.getByRole('button', { name: '搜索', exact: true }).click()
  }
  const expandGroup = async root => {
    const node = root.locator('.el-tree-node__content').filter({ hasText: /^测试集团$/ })
    await node.locator('.el-tree-node__expand-icon').click()
    await root.getByText('部门01', { exact: true }).waitFor()
  }

  assert.deepEqual(await calls(), [], '空选择器挂载不能读取组织目录')
  await page.locator('.organization-selector input').click()
  await picker.getByText('测试集团', { exact: true }).waitFor()
  assert.equal((await calls()).some(c => c.parentId === 'group'), false, '首屏不能预读下级')
  await expandGroup(picker)
  await picker.getByText('部门54', { exact: true }).waitFor()
  assert.equal(await picker.getByText('加载更多…', { exact: true }).count(), 0, '树展开不分页')
  assert.equal((await calls()).some(c => c.url === '/system/org/options/page'), false, '树展开不走分页列表')
  assert.equal((await calls()).some(c => c.parentId === 'dept54'), false, '仅加载当前层，不能预读孙级')
  await picker.getByText('部门00', { exact: true }).click()
  assert.equal(await picker.isVisible(), true, '停用节点不能选择')
  await picker.getByText('测试集团', { exact: true }).click()
  assert.equal(await picker.isVisible(), true, '部门选择器的组织祖先不能选择')
  await picker.getByText('部门54', { exact: true }).click()
  await picker.waitFor({ state: 'hidden' })
  assert.equal(await page.evaluate(() => orgSelectionTest.state.value), 'dept54')

  await mount('single', { value: 'DEEP', valueKey: 'code', multiple: true })
  assert.equal(await page.locator('.organization-selector input').inputValue(), '深层部门', '已选编码定向回显')
  await page.locator('.organization-selector input').click()
  await picker.locator('.is-current').filter({ hasText: '深层部门' }).waitFor()
  await search(picker, '部门')
  await picker.locator('.search-result').first().waitFor()
  assert.equal(await picker.locator('.search-result').count(), 20, '搜索结果必须服务端分页')
  await picker.locator('.el-pager li').filter({ hasText: /^2$/ }).click()
  await picker.getByText('部门21', { exact: true }).waitFor()
  await picker.locator('.search-result').filter({ hasText: /^部门21/ }).click()
  await picker.getByRole('button', { name: '确认选择' }).click()
  assert.deepEqual(await page.evaluate(() => orgSelectionTest.state.value), ['DEEP', 'DEPT21'], '多选保持业务编码与既有选择')
  await page.locator('.organization-selector input').click()
  await search(picker, '部门22')
  await picker.locator('.search-result').click()
  await picker.getByRole('button', { name: '取消', exact: true }).click()
  assert.deepEqual(await page.evaluate(() => orgSelectionTest.state.value), ['DEEP', 'DEPT21'], '取消不提交草稿')

  await mount('single', { value: '', valueKey: 'id', multiple: false })
  await page.evaluate(() => { orgSelectionTest.state.failNext = true })
  await page.locator('.organization-selector input').click()
  await picker.getByText('模拟目录失败', { exact: false }).waitFor()
  await picker.getByRole('button', { name: '重新加载', exact: true }).click()
  await picker.getByText('测试集团', { exact: true }).waitFor()
  await page.evaluate(() => { orgSelectionTest.state.delayKeyword = '部门01' })
  await search(picker, '部门01')
  await search(picker, '深层')
  await picker.locator('.search-result').filter({ hasText: '深层部门' }).waitFor()
  await page.waitForTimeout(500)
  assert.equal(await picker.locator('.search-result').count(), 1, '迟到搜索不能覆盖新结果')

  await mount('position')
  assert.equal((await calls()).some(c => c.url.startsWith('/system/org/')), false)
  assert.equal(await page.getByText('适用单位', { exact: true }).count(), 0)
  assert.equal(await page.getByText('人员数量', { exact: true }).count(), 0)
  await page.getByRole('button', { name: '新增职务', exact: true }).click()
  const createPosition = page.getByRole('dialog', { name: '新增职务', exact: true })
  await createPosition.waitFor()
  assert.equal(await createPosition.getByText('人员数量', { exact: true }).count(), 0)
  assert.equal(await createPosition.getByText('适用单位', { exact: true }).count(), 0)
  await createPosition.getByPlaceholder('如：部门负责人').fill('审核员')
  await createPosition.getByPlaceholder('如：UNIT_LEADER').fill('REVIEWER')
  await createPosition.getByRole('button', { name: '保存职务', exact: true }).click()
  await createPosition.waitFor({ state: 'hidden' })
  const created = await page.evaluate(() => orgSelectionTest.state.writes.at(-1))
  assert.equal(created.url, '/system/position')
  assert.equal(created.payload.positionCode, 'REVIEWER')
  assert.equal(Object.hasOwn(created.payload, 'holderMode'), false)
  assert.equal(Object.hasOwn(created.payload, 'applicableUnitType'), false)

  await page.getByRole('tab', { name: '职务人员' }).click()
  const panel = page.locator('.organization-panel')
  await panel.getByText('测试集团', { exact: true }).waitFor()
  await expandGroup(panel)
  await panel.getByText('部门01', { exact: true }).click()
  await page.waitForFunction(() => orgSelectionTest.state.calls.some(c => c.url === '/system/position/assignments/page' && c.organizationUnitId === 'dept01'))
  await page.locator('.assignment-content').getByRole('button', { name: '设置职务人员', exact: true }).click()
  const assignment = page.getByRole('dialog', { name: '设置职务人员', exact: true })
  await assignment.waitFor()
  assert.equal(await assignment.locator('.organization-selector').count(), 0, '锁定组织不应挂载目录选择器')
  const before = (await calls()).filter(c => c.url.startsWith('/system/org/')).length
  await assignment.getByRole('button', { name: '取消', exact: true }).click()
  assert.equal((await calls()).filter(c => c.url.startsWith('/system/org/')).length, before)

  await mount('assignment', { open: true, lockedUnit: { id: 'other', type: 'org', orgName: '其他集团' }, lockedUser: null })
  await assignment.locator('.user-selector .selector-input.multiple').waitFor()
  await assignment.getByRole('button', { name: '保存', exact: true }).click()
  await assignment.waitFor({ state: 'hidden' })
  assert.deepEqual(await page.evaluate(() => orgSelectionTest.state.writes.at(-1).payload), {
    organizationUnitId: 'other', positionCode: 'LEADER', userIds: ['u1'], expectedUserIds: ['u1']
  })

  await mount('assignment', { open: true, lockedUnit: null, lockedUser: { id: 'u2', username: 'test2', deptId: 'dept54' }, writes: [] })
  await assignment.locator('.organization-selector input').waitFor()
  await page.waitForFunction(() => document.querySelector('.el-dialog .organization-selector input')?.value === '部门54')
  assert.equal(await assignment.locator('.organization-selector input').inputValue(), '部门54')
  await assignment.getByRole('button', { name: '设置职务', exact: true }).click()
  await page.waitForFunction(() => orgSelectionTest.state.writes.length > 0)
  assert.deepEqual(await page.evaluate(() => orgSelectionTest.state.writes.at(-1).payload), {
    organizationUnitId: 'dept54', positionCode: 'LEADER', userIds: ['u1', 'u2'], expectedUserIds: ['u1']
  })

  await mount('user')
  assert.equal((await calls()).some(c => c.url.startsWith('/system/org/')), false, '用户页首屏不再预读全目录')
  await page.getByPlaceholder('全部组织/部门').click()
  const filter = page.getByRole('dialog', { name: '选择组织/部门', exact: true })
  await search(filter, '深层')
  await filter.locator('.search-result').click()
  await filter.getByRole('button', { name: '确认选择', exact: true }).click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.waitForFunction(() => orgSelectionTest.state.calls.some(c => c.url === '/system/user/page' && c.deptId === 'nested' && !c.orgId))

  await mount('role')
  assert.equal((await calls()).some(c => c.url.startsWith('/system/org/')), false, '角色页首屏不预读组织')
  await mount('scope')
  await page.waitForFunction(() => document.querySelector('.organization-selector input')?.value === '深层部门')
  assert.equal((await calls()).some(c => c.url === '/system/org/options/page'), false, '流程范围只回显已有编码，不加载目录')
  assert.deepEqual(errors, [])
  console.log('组织层级选择浏览器回归通过：逐层展开、停用祖先、类型限制、搜索分页、ID/编码回显、多选取消、失败重试、竞态、职务导航与保存、用户筛选和流程范围。')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
