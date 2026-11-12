import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'
import { mkdtempSync, writeFileSync, rmSync, existsSync } from 'node:fs'
import path from 'node:path'

// 真实组件验证按需加载、文本安全、大整数、权限隐藏和慢请求隔离；不连接业务接口。
const fixture = mkdtempSync(path.resolve('.identity-snapshot-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import { createApp, ref, h } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Viewer from '/src/views/system/components/IdentitySourceSnapshotViewer.vue'
import { useUserStore } from '/src/stores/user.js'
import request from '/src/utils/request.js'
const pinia = createPinia(), store = useUserStore(pinia)
store.permissions = ['system:user:view','system:user:manage','system:organization:view','system:organization:manage']
const target = ref('existing'), type = ref('USER')
const state = { calls:0, fail:false, release:null, raw:'{ "id":9223372036854775807123, "html":"<img src=x onerror=window.injected=1>" }' }
const detail = id => ({ state:id === 'missing' ? 'UNAVAILABLE' : id === 'stale' ? 'STALE' : 'CURRENT', sourceCode:'erp', externalId:id,
 currentSourceVersion:'9223372036854775807',recordedSourceVersion:'9223372036854775807',rawDataJson:id === 'missing' ? null : state.raw })
request.get = async url => {
 state.calls++
 if(state.fail) throw Error('offline')
 const id = url.split('/').at(-1)
 if(id === 'slow') return new Promise(resolve => state.release = () => resolve(detail(id)))
 return detail(id)
}
createApp({setup:() => () => h(Viewer,{targetType:type.value,targetId:target.value})}).use(pinia).use(ElementPlus).mount('#app')
window.snapshotTest = {state,store,target,type}
`)
let server, browser
try {
  server = await createServer({ cacheDir:path.join(fixture, 'cache'), optimizeDeps:{entries:[path.join(fixture, 'index.html')]}, server:{host:'127.0.0.1',port:3427,strictPort:true} })
  await server.listen()
  const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  browser = await chromium.launch({headless:true,...(existsSync(chrome) ? {executablePath:chrome} : {})})
  const page = await browser.newPage()
  page.setDefaultTimeout(10000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('http://127.0.0.1:3427/' + path.basename(fixture) + '/index.html')
  const open = () => page.getByRole('button',{name:'来源原始数据',exact:true}).click()
  await page.getByRole('button',{name:'来源原始数据',exact:true}).waitFor()
  assert.equal(await page.evaluate(() => snapshotTest.state.calls), 0)
  await open()
  await page.locator('.snapshot-json').waitFor()
  assert.equal(await page.locator('.snapshot-json').textContent(), await page.evaluate(() => snapshotTest.state.raw))
  assert.equal(await page.locator('.snapshot-json img').count(), 0)
  assert.equal(await page.evaluate(() => window.injected), undefined)
  assert.equal(await page.getByText('9223372036854775807',{exact:true}).count(), 2)
  await page.getByRole('button',{name:'关闭',exact:true}).click()
  await page.waitForFunction(() => !document.querySelector('.snapshot-json'))
  await page.evaluate(() => { snapshotTest.target.value = 'stale'; snapshotTest.type.value = 'ORGANIZATION' })
  await open()
  await page.locator('.el-alert--warning').waitFor()
  await page.getByRole('button',{name:'关闭',exact:true}).click()
  await page.evaluate(() => snapshotTest.target.value = 'missing')
  await open()
  await page.getByText('尚未留存原始数据，后续同步提供原始记录后可查看。').waitFor()
  assert.equal(await page.locator('.snapshot-json').count(), 0)
  await page.getByRole('button',{name:'关闭',exact:true}).click()
  await page.evaluate(() => { snapshotTest.target.value = 'slow'; snapshotTest.type.value = 'USER' })
  await open()
  await page.waitForFunction(() => Boolean(snapshotTest.state.release))
  await page.evaluate(() => snapshotTest.target.value = 'fresh')
  await page.waitForFunction(() => !document.querySelector('.el-overlay-dialog') || document.querySelector('.el-overlay')?.style.display === 'none')
  await open()
  await page.locator('.snapshot-json').waitFor()
  await page.evaluate(() => snapshotTest.state.release())
  assert.equal(await page.getByText('fresh',{exact:true}).count(), 1)
  assert.equal(await page.getByText('slow',{exact:true}).count(), 0)
  await page.getByRole('button',{name:'关闭',exact:true}).click()
  await page.evaluate(() => snapshotTest.state.fail = true)
  await open()
  await page.getByText('原始数据加载失败，请重试。').waitFor()
  await page.evaluate(() => snapshotTest.state.fail = false)
  await page.getByRole('button',{name:'重新加载',exact:true}).click()
  await page.locator('.snapshot-json').waitFor()
  await page.evaluate(() => snapshotTest.store.permissions = ['system:user:view'])
  await page.waitForFunction(() => !document.querySelector('.source-snapshot'))
  assert.equal(await page.locator('.snapshot-json').count(), 0)
  assert.deepEqual(errors, [])
  console.log('Source snapshot browser tests passed: lazy load, exact text, XSS, version precision, stale/missing, retry, target race, permissions')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture,{recursive:true,force:true})
}
