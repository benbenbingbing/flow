import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 使用实际 Vue 页面和筛选组件，夹具仅替代传输层；验证即时创建、稳定 ID 保存及加载失败保护。
const fixture = mkdtempSync(path.resolve('.group-tags-fixture-'))
const harness = `
import { createApp, h, reactive } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Filter from '/src/components/GroupTagFilterEditor.vue'
import Group from '/src/views/system/Group.vue'
import { useUserStore } from '/src/stores/user'
import request from '/src/utils/request'
const state=reactive({mode:'filter',tags:{TECH:[]},writes:[],fail:false,deferTag:false,rejectTag:false,createdCount:0,bindings:[{userId:'u1',tagIds:['backend']}],directory:[{id:'backend',groupId:'g1',tagName:'后端'},{id:'security',groupId:'g1',tagName:'安全'}]})
const group={id:'g1',groupCode:'TECH',groupName:'技术评审组',userIds:['u1','u2'],status:'0',sort:0}
request.get=async url=>{
  if(url==='/system/group/list') return [group]
  if(url==='/system/group/g1/tags') {if(state.fail)throw Error('模拟加载失败');return state.directory.map(row=>({...row}))}
  if(url==='/system/group/g1/member-tags')return JSON.parse(JSON.stringify(state.bindings))
  if(url==='/system/group/g1')return {...group,users:[{id:'u1',username:'alice',nickname:'张三'},{id:'u2',username:'bob',nickname:'李四'}]}
  return []
}
request.post=async(url,data)=>{
  state.writes.push({url,data:JSON.parse(JSON.stringify(data))})
  if(url==='/system/group/g1/tags'){
    if(state.rejectTag){state.rejectTag=false;throw Error('模拟创建失败')}
    if(state.deferTag)await new Promise(resolve=>window.groupTagsTest.resolveTag=resolve)
    const saved={id:'created-'+(++state.createdCount),groupId:'g1',tagName:data.tagName}
    state.directory.push(saved)
    return {...saved}
  }
  if(url==='/system/group/g1/members')state.bindings=JSON.parse(JSON.stringify(data.memberTags))
  if(url==='/system/group/tag-preview'){
    if(data.some(row=>row.tagIds.some(id=>!state.directory.some(tag=>tag.id===id))))throw Error('标签失效')
    const alice={username:'alice',nickname:' 张三 '},bob={username:'bob',nickname:' '}
    return data[0].tagIds.includes('security')?[alice]:[alice,bob]
  }
  return null
}
const app=createApp({setup(){return()=>state.mode==='filter'?h(Filter,{groupCodes:['TECH'],groupOptions:[{id:'g1',code:'TECH',label:'技术评审组'}],modelValue:state.tags,'onUpdate:modelValue':value=>state.tags=value}):h(Group)}})
app.use(createPinia());app.use(ElementPlus)
useUserStore().permissions=['system:user:view','system:user:manage']
app.mount('#app')
window.groupTagsTest={state}
`
writeFileSync(path.join(fixture,'index.html'),'<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture,'main.js'),harness)
const server=await createServer({server:{host:'127.0.0.1',port:0}})
let browser
try {
  await server.listen()
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'})
  const page=await browser.newPage({viewport:{width:1100,height:900}})
  const errors=[]
  page.on('pageerror',error=>errors.push(error.message))
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/${path.basename(fixture)}/index.html`)
  await page.waitForFunction(()=>!!window.groupTagsTest)
  await page.waitForFunction(()=>document.querySelector('.scope-preview')?.textContent.includes('2 人'))
  assert.match(await page.locator('.scope-preview').innerText(),/张三（alice）、bob/)
  assert.equal(await page.locator('.tag-hint').count(),0)
  await page.locator('.group-tag-row .el-select').click()
  await page.getByRole('option',{name:'安全',exact:true}).click()
  await page.keyboard.press('Escape')
  await page.waitForFunction(()=>document.querySelector('.scope-preview')?.textContent.includes('1 人'))
  assert.match(await page.locator('.scope-preview').innerText(),/张三（alice）/)
  assert.deepEqual(await page.evaluate(()=>groupTagsTest.state.tags.TECH),['security'])
  // 目录失效后不能自动删除条件或把预览回退成全组。
  await page.evaluate(()=>{groupTagsTest.state.directory=[];groupTagsTest.state.tags={TECH:['security']}})
  await page.waitForFunction(()=>document.querySelector('.scope-preview')?.textContent.includes('范围解析失败'))
  assert.deepEqual(await page.evaluate(()=>groupTagsTest.state.tags.TECH),['security'])
  await page.evaluate(()=>{groupTagsTest.state.directory=[{id:'backend',groupId:'g1',tagName:'后端'},{id:'security',groupId:'g1',tagName:'安全'}];groupTagsTest.state.mode='group'})
  await page.getByRole('button',{name:'分配成员',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'分配组成员与标签'})
  await dialog.locator('.member-tags-editor .el-table').waitFor()
  await page.waitForFunction(()=>!document.querySelector('.member-tags-editor')?.textContent.includes('加载失败'))
  await dialog.locator('.member-tags-editor .el-table .el-select').first().click()
  await page.getByRole('option',{name:'安全',exact:true}).click()
  await page.keyboard.press('Escape')
  const memberSelects=dialog.locator('.member-tags-editor .el-table .el-select')
  const firstMember=memberSelects.nth(0),secondMember=memberSelects.nth(1)
  // 输入新名称即保存目录；未取得稳定 ID 前禁止提前保存成员。
  await page.evaluate(()=>{groupTagsTest.state.deferTag=true})
  await firstMember.locator('input').fill(' 运维 ')
  await page.keyboard.press('Enter')
  await page.waitForFunction(()=>typeof groupTagsTest.resolveTag==='function')
  assert.equal(await dialog.getByRole('button',{name:'保存成员（2）',exact:true}).isDisabled(),true)
  await page.evaluate(()=>{groupTagsTest.state.deferTag=false;groupTagsTest.resolveTag()})
  await firstMember.getByText('运维',{exact:true}).waitFor()
  assert.equal(await page.evaluate(()=>groupTagsTest.state.directory.find(row=>row.id==='created-1').tagName),'运维')
  // 带空白的已有名称复用目录中的 ID，其他成员无需重复创建。
  await secondMember.locator('input').fill(' 运维 ')
  await page.keyboard.press('Enter')
  await secondMember.getByText('运维',{exact:true}).waitFor()
  assert.equal(await page.evaluate(()=>groupTagsTest.state.writes.filter(row=>row.url==='/system/group/g1/tags').length),1)
  // 创建失败不能把新名称留在分配中，也不能丢失此前已选标签。
  await page.evaluate(()=>{groupTagsTest.state.rejectTag=true})
  await firstMember.locator('input').fill('失败标签')
  await page.keyboard.press('Enter')
  await page.waitForFunction(()=>!groupTagsTest.state.rejectTag)
  await page.keyboard.press('Escape')
  assert.equal(await firstMember.locator('.el-tag').filter({hasText:'失败标签'}).count(),0)
  await dialog.getByRole('button',{name:'保存成员（2）',exact:true}).click()
  await page.waitForFunction(()=>groupTagsTest.state.writes.some(row=>row.url==='/system/group/g1/members'))
  const save=await page.evaluate(()=>groupTagsTest.state.writes.find(row=>row.url==='/system/group/g1/members'))
  assert.deepEqual(save.data.userIds,['u1','u2'])
  assert.deepEqual(save.data.memberTags,[{userId:'u1',tagIds:['backend','security','created-1']},{userId:'u2',tagIds:['created-1']}])
  // 批量输入同样先创建目录，再将返回 ID 应用给已勾选成员。
  await page.getByRole('button',{name:'分配成员',exact:true}).click()
  await dialog.locator('.member-tags-editor .el-table').waitFor()
  const batchSelect=dialog.locator('.tag-toolbar .el-select')
  await batchSelect.locator('input').fill('值班')
  await page.keyboard.press('Enter')
  await batchSelect.getByText('值班',{exact:true}).waitFor()
  await page.keyboard.press('Escape')
  await dialog.locator('.el-table__header-wrapper .el-checkbox').click()
  await dialog.getByRole('button',{name:'应用到已勾选 2 人',exact:true}).click()
  await dialog.getByRole('button',{name:'保存成员（2）',exact:true}).click()
  await page.waitForFunction(()=>groupTagsTest.state.writes.filter(row=>row.url==='/system/group/g1/members').length===2)
  const batchSave=await page.evaluate(()=>groupTagsTest.state.writes.filter(row=>row.url==='/system/group/g1/members').at(-1))
  assert.deepEqual(batchSave.data.memberTags,[{userId:'u1',tagIds:['created-2']},{userId:'u2',tagIds:['created-2']}])
  // 同样的成员名单也允许保存标签；加载失败时保存按钮必须禁用。
  await page.evaluate(()=>{groupTagsTest.state.fail=true})
  await page.getByRole('button',{name:'分配成员',exact:true}).click()
  await dialog.getByText('成员标签加载失败，请重新加载后再保存').waitFor()
  assert.equal(await dialog.getByRole('button',{name:'保存成员（2）',exact:true}).isDisabled(),true)
  assert.deepEqual(errors,[])
  console.log('group member tags browser tests passed')
} finally {
  await browser?.close()
  await server.close()
  rmSync(fixture,{recursive:true,force:true})
}
