import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'
import { mkdtempSync, writeFileSync, rmSync, existsSync } from 'node:fs'
import path from 'node:path'

// 使用真实 Vue/Element Plus 组件，替换取数边界，验证编辑、只读、加载失败及目标切换。
const fixture = mkdtempSync(path.resolve('.identity-extension-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import { createApp, ref, nextTick, h } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElForm } from 'element-plus'
import 'element-plus/dist/index.css'
import Extension from '/src/views/system/components/IdentityExtensionForm.vue'
import request from '/src/utils/request.js'
const state = { fail:false, release:null }
const field = (name,label) => ({ name,label,control:'TEXT',required:false,maxLength:64,options:[] })
const definitions = [
 {code:'profile',title:'研发资料',maintenance:'LOCAL',writable:true,fields:[field('skill','技能方向')]},
 {code:'directory',title:'来源资料',maintenance:'SOURCE',writable:false,fields:[field('employee','工号')]},
 {code:'extra',title:'附加资料',maintenance:'LOCAL',writable:true,fields:[field('note','备注')]}
]
const detail = id => ({extensions: id === 'new' ? {} : {profile:{rowVersion:3,data:{skill:id}},directory:{rowVersion:2,data:{employee:'EMP-1'}}}})
request.get = async url => {
 if(state.fail) throw Error('offline')
 if(url.endsWith('/schema')) return definitions
 const id = url.split('/').at(-1)
 if(id === 'slow') return new Promise(resolve => state.release = () => resolve(detail(id)))
 return detail(id)
}
const target = ref('existing'), form = ref(), readonly = ref(false)
createApp({ setup:() => () => h(ElForm, {labelWidth:'100px'}, () => h(Extension, {ref:form,targetType:'USER',targetId:target.value,readonly:readonly.value})) }).use(createPinia()).use(ElementPlus).mount('#app')
window.identityTest = {state,form,target,readonly,settle:nextTick}
`)
let server, browser
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')] }, server: { host:'127.0.0.1', port:3426, strictPort:true } })
  await server.listen()
  const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  browser = await chromium.launch({ headless:true, ...(existsSync(chrome) ? { executablePath:chrome } : {}) })
  const page = await browser.newPage()
  page.setDefaultTimeout(10000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('http://127.0.0.1:3426/' + path.basename(fixture) + '/index.html')
  const profile = page.locator('.extension-card').filter({hasText:'研发资料'}).locator('input')
  await profile.waitFor()
  assert.equal(await profile.inputValue(), 'existing')
  assert.equal(await page.locator('.extension-card').filter({hasText:'来源资料'}).locator('input').isDisabled(), true)
  await profile.fill('Go')
  assert.deepEqual(await page.evaluate(() => identityTest.form.value.getChanges()), {profile:{expectedVersion:3,data:{skill:'Go'}}})
  const extra = page.locator('.extension-card').filter({hasText:'附加资料'})
  await extra.getByRole('button', {name:'添加资料'}).click()
  await extra.locator('input').fill('交付团队')
  assert.equal((await page.evaluate(() => identityTest.form.value.getChanges())).extra.expectedVersion, 0)
  await page.evaluate(() => identityTest.readonly.value = true)
  assert.equal(await profile.isDisabled(), true)
  assert.deepEqual(await page.evaluate(() => identityTest.form.value.getChanges()), {})
  await page.evaluate(() => { identityTest.readonly.value = false; identityTest.target.value = 'slow' })
  await page.waitForFunction(() => Boolean(identityTest.state.release))
  await page.evaluate(() => identityTest.target.value = 'fresh')
  await page.waitForFunction(() => document.querySelector('.extension-card input')?.value === 'fresh')
  await page.evaluate(async () => { identityTest.state.release(); await identityTest.settle() })
  assert.equal(await profile.inputValue(), 'fresh')
  await page.evaluate(() => identityTest.target.value = 'new')
  await page.waitForFunction(() => !document.querySelector('.extension-card input'))
  assert.deepEqual(await page.evaluate(() => identityTest.form.value.getChanges()), {})
  await page.evaluate(async () => { identityTest.state.fail = true; await identityTest.form.value.reload() })
  assert.equal(await page.locator('.el-alert--error').count(), 1)
  assert.equal(await page.evaluate(() => { try {identityTest.form.value.getChanges();return false} catch {return true} }), true)
  assert.deepEqual(errors, [])
  console.log('Identity extension browser tests passed: edit/create, source/read-only, target race, fail-closed load')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, {recursive:true,force:true})
}
