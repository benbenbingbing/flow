import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

// 挂载真实编码规则弹窗，替换 API 返回值，验证模式切换、参数、保存与无副作用预览。
const harness = `
import { createApp, h, reactive, nextTick } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Dialog from '/src/views/entity/components/EntityCodeRuleDialog.vue'
import { codeRuleApi } from '/src/api/codeRule.js'
const state=reactive({key:0,rule:{entityCode:'project',generationMode:'RULE',prefix:'XM',seqLength:6,seqType:'DAY',dateFormat:'yyyyMMdd',currentSeq:42,id:'server-rule'},saved:[],previewFailure:false,delay:false,closed:false})
const schema={type:'object',required:['prefix'],properties:{prefix:{type:'string',title:'编号前缀',default:'XM'}}}
codeRuleApi.getByEntityCode=async()=>({...state.rule})
codeRuleApi.generators=async()=>[{code:'PROJECT_RECORD_ID',displayName:'项目编号',configurationSchema:schema}]
codeRuleApi.preview=async()=>{if(state.previewFailure)throw Error('模拟自定义服务失败');if(state.delay)return new Promise(resolve=>window.previewResolve=resolve);return ''}
codeRuleApi.save=async value=>{state.saved.push(JSON.parse(JSON.stringify(value)));state.rule={...value}}
createApp({setup:()=>()=>h(Dialog,{key:state.key,entity:{entityCode:'project',entityName:'项目'},onClose:()=>{state.closed=true}})}).use(ElementPlus).mount('#app')
window.codeRuleTest={state,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,100));await nextTick()}}
`
const fixture = mkdtempSync(path.resolve('.code-rule-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
const profile = mkdtempSync(path.join(tmpdir(), 'flow-code-rule-chrome-'))
let browser, ws, server, nextId = 0
const pending = new Map(), errors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function send(method, params = {}) {
  const id = ++nextId
  const promise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return promise
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || JSON.stringify(result.exceptionDetails))
  return result.result.value
}
async function click(text, scope = 'document') {
  await evaluate(`(()=>{const node=[...${scope}.querySelectorAll('button')].find(e=>e.getClientRects().length&&e.textContent.trim()===${JSON.stringify(text)});if(!node)throw Error('找不到按钮 '+${JSON.stringify(text)});node.click()})()`)
  await evaluate('codeRuleTest.settle()')
}
async function until(expression) {
  for (let i = 0; i < 100; i++) {
    try { if (await evaluate(expression)) return }
    catch (error) { if (!/navigated|context was destroyed|Cannot find context/.test(error.message)) throw error }
    await sleep(100)
  }
  throw Error('等待失败: ' + expression + '\n' + await evaluate('document.body.innerText'))
}
try {
  server = await createServer({ cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios'], entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 3407, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9407', '--user-data-dir=' + profile, 'about:blank'], { stdio: 'ignore' })
  let targets
  for (let i = 0; i < 100; i++) { try { targets = await (await fetch('http://127.0.0.1:9407/json/list')).json(); break } catch { await sleep(100) } }
  assert.ok(targets?.length, 'Chrome 未启动')
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) { const request = pending.get(data.id); pending.delete(data.id); data.error ? request.reject(Error(JSON.stringify(data.error))) : request.resolve(data.result) }
    if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: 'http://127.0.0.1:3407/' + path.basename(fixture) + '/index.html' })
  await until('Boolean(window.codeRuleTest)')
  await evaluate('codeRuleTest.settle()')

  const radio = async label => {
    await evaluate(`[...document.querySelectorAll('.el-radio-button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}).click()`)
    await evaluate('codeRuleTest.settle()')
  }
  await radio('自定义生成')
  await evaluate("document.querySelector('.el-select__wrapper').click()")
  await until("document.querySelector('.el-select-dropdown__item')")
  await evaluate("document.querySelector('.el-select-dropdown__item').click()")
  await evaluate('codeRuleTest.settle()')
  assert.equal(await evaluate("[...document.querySelectorAll('.el-form-item')].find(e=>e.textContent.includes('编号前缀'))?.querySelector('input')?.value"),'XM')
  await click('刷新')
  assert.equal(await evaluate("document.body.innerText.includes('不提供预览')"), true)
  await evaluate('codeRuleTest.state.previewFailure=true')
  await click('刷新')
  assert.equal(await evaluate("document.body.innerText.includes('模拟自定义服务失败')"), true)
  assert.equal(await evaluate("[...document.querySelectorAll('.el-form-item')].find(e=>e.textContent.includes('编码示例'))?.querySelector('input')?.value"),'')
  await evaluate('codeRuleTest.state.previewFailure=false;codeRuleTest.state.delay=true')
  await click('刷新')
  await until('Boolean(window.previewResolve)')
  await radio('规则生成')
  await evaluate("window.previewResolve('STALE-CODE');codeRuleTest.settle()")
  assert.equal(await evaluate("[...document.querySelectorAll('input')].some(e=>e.value==='STALE-CODE')"),false)
  await radio('自定义生成')
  const screenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true})
  writeFileSync('/tmp/flow-custom-code-dialog.png',Buffer.from(screenshot.data,'base64'))
  await click('保存')
  await until('codeRuleTest.state.saved.length===1')
  const saved=await evaluate('JSON.parse(JSON.stringify(codeRuleTest.state.saved[0]))')
  assert.equal(saved.generationMode,'CUSTOM')
  assert.equal(saved.generatorCode,'PROJECT_RECORD_ID')
  assert.deepEqual(saved.generatorConfig,{prefix:'XM'})
  assert.equal(saved.id,undefined)
  assert.equal(saved.currentSeq,undefined)
  await evaluate("codeRuleTest.state.rule.generatorCode='UNINSTALLED';codeRuleTest.state.key++;codeRuleTest.settle()")
  await until("document.body.innerText.includes('当前生成器不可用')")
  assert.equal(await evaluate("[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='保存').disabled"),true)
  assert.deepEqual(errors,[])
  console.log('Entity custom code dialog browser regression passed')
} finally {
  ws?.close()
  browser?.kill('SIGTERM')
  await server?.close()
  rmSync(fixture,{recursive:true,force:true})
  await sleep(300)
  rmSync(profile,{recursive:true,force:true})
}
