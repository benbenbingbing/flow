import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { spawn } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

// 用真实注册入口和 Vue 组件验证 JSON 链路；测试不登录、不调用业务接口。
const root = fileURLToPath(new URL('..', import.meta.url))
const fixture = mkdtempSync(path.join(root, '.extension-browser-'))
const implementationFolder = mkdtempSync(path.join(root, 'src/extensions/examples/.browser-'))
const manifestFolder = mkdtempSync(path.join(root, '../extensions/manifests/common/.browser-'))
const profile = mkdtempSync(path.join(tmpdir(), 'extension-chrome-'))
const manifestPath = path.join(manifestFolder, 'temporary.extension.json')
const relative = filename => path.relative(root, filename).split(path.sep).join('/')
const harness = `
import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { registerApplicationExtensions } from '/src/extensions/register.js'
import { getExtensionCatalog } from '/src/extensions/core/catalog.js'
import { getCustomFormComponent, getCustomListComponent } from '/src/extensions/core/registries/customComponentRegistry.js'
import { resolveFieldComponent } from '/src/extensions/core/registries/formFieldRegistry.js'
import { getCellComponent } from '/src/extensions/core/registries/listCellRegistry.js'
import { getFormNodeComponent } from '/src/extensions/core/registries/formNodeRegistry.js'
import { getListButtonComponent } from '/src/extensions/core/registries/listButtonComponentRegistry.js'
import { getListToolbarAction, getListRowAction } from '/src/extensions/core/registries/listActionRegistry.js'
import { getCustomValidator } from '@flow/workflow-core/extensions/core/registries/validatorRegistry'
import { getEntityActionRuleCondition, resolveEntityPermissionOptions } from '/src/extensions/core/registries/entityActionRuleRegistry.js'
const options = {enableDemo: new URLSearchParams(location.search).has('demo')}
registerApplicationExtensions(options)
registerApplicationExtensions(options)
const field = resolveFieldComponent({componentType:'project_acceptance_score'})
const cell = getCellComponent('ProjectAcceptanceScoreCell')
const added = getCellComponent('BrowserTemporaryCell')
createApp({setup:()=>()=>h('main',[
  h('section',{id:'field'},[h(field,{modelValue:90,field:{fieldCode:'score'},disabled:true})]),
  h('section',{id:'cell'},[h(cell,{value:30,config:{passScore:50}})]),
  added ? h('section',{id:'added'},[h(added)]) : null
])}).use(ElementPlus).mount('#app')
const condition = getEntityActionRuleCondition('PROJECT:CUSTOM_CONDITION')
window.extensionTest = {
  names: getExtensionCatalog().map(item=>item.name),
  types: [...new Set(getExtensionCatalog().map(item=>item.type))],
  label: getExtensionCatalog().find(item=>item.name==='BrowserTemporaryCell')?.label,
  identities: [getCustomFormComponent('ProjectMemberChangeForm'),getCustomListComponent('ProjectAcceptanceBoardList'),getFormNodeComponent('ProjectAcceptanceSummaryNode'),getListButtonComponent('ProjectAcceptanceInspectButton'),condition?.component].every(Boolean),
  actions: typeof getListToolbarAction('projectAcceptanceToolbarAction') === 'function' && typeof getListRowAction('projectAcceptanceRowAction') === 'function' && getListToolbarAction('projectAcceptanceSelectionAction') === getListRowAction('projectAcceptanceSelectionAction'),
  validate: value => getCustomValidator('amount',1).validator.validate(value,{params:{maxAmount:100}}),
  permissions: await resolveEntityPermissionOptions({entityCode:'Project'}),
  boot: Math.random()
}
`
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let browser, ws, server
let nextId = 0
const pending = new Map()
const browserErrors = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

/** CDP 请求带超时，浏览器未就绪不能让测试无限等待。 */
function send(method, params = {}) {
  const id = ++nextId
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 15000)
    pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value) }, reject: error => { clearTimeout(timer); reject(error) } })
    ws.send(JSON.stringify({ id, method, params }))
  })
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}
async function until(predicate, label) {
  const deadline = Date.now() + 45000
  while (Date.now() < deadline) {
    if (await predicate()) return
    await sleep(100)
  }
  throw new Error(`${label}: ${browserErrors.join('\n')}`)
}
async function ready(previousBoot) {
  await until(async () => {
    try { return await evaluate(`Boolean(window.extensionTest && window.extensionTest.boot !== ${JSON.stringify(previousBoot || null)})`) }
    catch (error) {
      // 自动刷新中执行上下文会短暂消失；其他错误保留便于定位。
      if (/context.*(destroyed|found)|navigat/i.test(error.message)) return false
      throw error
    }
  }, '扩展页面启动/刷新超时')
}
try {
  server = await createServer({ root, cacheDir: path.join(fixture, 'cache'), optimizeDeps: { include: ['axios'], entries: [path.join(fixture, 'index.html')] }, server: { host: '127.0.0.1', port: 0 } })
  await server.listen()
  const address = server.httpServer.address()
  const url = `http://127.0.0.1:${address.port}/${path.basename(fixture)}/index.html`
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' })
  let debugPort, spawnError
  browser.on('error', error => { spawnError = error })
  await until(() => {
    if (spawnError) throw spawnError
    try { debugPort = readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0]; return true } catch { return false }
  }, 'Chrome 启动失败；可设置 CHROME_PATH')
  const targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json()
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl)
  ws.addEventListener('message', event => {
    const data = JSON.parse(event.data)
    if (data.id && pending.has(data.id)) {
      const request = pending.get(data.id); pending.delete(data.id)
      data.error ? request.reject(new Error(JSON.stringify(data.error))) : request.resolve(data.result)
    }
    if (data.method === 'Runtime.exceptionThrown') browserErrors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text)
  })
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }) })
  await send('Runtime.enable'); await send('Page.enable')
  await send('Page.navigate', { url }); await ready()
  assert.equal(await evaluate('extensionTest.types.length'), 10)
  assert.equal(await evaluate('extensionTest.identities && extensionTest.actions'), true)
  assert.equal(await evaluate('extensionTest.names.includes("DemoProjectForm")'), false)
  assert.equal(await evaluate('extensionTest.permissions.length'), 1, '重复初始化不重复追加权限候选')
  assert.equal(await evaluate('extensionTest.validate(99)'), true)
  assert.equal(await evaluate('extensionTest.validate(101)'), '金额不能超过 100')
  assert.match(await evaluate('document.querySelector("#field").textContent'), /优秀/)
  assert.match(await evaluate('document.querySelector("#cell").textContent'), /待改进/)

  await send('Page.navigate', { url: `${url}?demo=1` }); await ready()
  await until(() => evaluate('window.extensionTest?.names.includes("DemoProjectForm")'), 'Demo 开关未生效')
  // 新增实现和 JSON，完全不更改注册入口；校验 add/change/unlink 三类真实文件监听。
  const implementationPath = path.join(implementationFolder, 'TemporaryCell.vue')
  writeFileSync(implementationPath, '<template><span>JSON 自动发现组件</span></template>')
  const manifest = { schemaVersion: 1, type: 'LIST_CELL', name: 'BrowserTemporaryCell', label: '临时组件', version: 1, implementation: { path: relative(implementationPath), export: 'default', kind: 'COMPONENT' } }
  let boot = await evaluate('extensionTest.boot')
  writeFileSync(manifestPath, JSON.stringify(manifest)); await ready(boot)
  assert.equal(await evaluate('document.querySelector("#added").textContent'), 'JSON 自动发现组件')
  boot = await evaluate('extensionTest.boot')
  manifest.label = '已更新组件'
  writeFileSync(manifestPath, JSON.stringify(manifest)); await ready(boot)
  assert.equal(await evaluate('extensionTest.label'), '已更新组件')
  assert.equal(await evaluate('extensionTest.permissions.length'), 1)
  boot = await evaluate('extensionTest.boot')
  rmSync(manifestPath); await ready(boot)
  assert.equal(await evaluate('extensionTest.names.includes("BrowserTemporaryCell")'), false)
  assert.equal(await evaluate('document.querySelector("#added") === null'), true)
  assert.deepEqual(browserErrors, [])
  console.info('JSON 扩展浏览器验收通过：十类注册、真实字段/单元格、动作/校验/权限、Demo、重复初始化、清单新增/修改/删除自动刷新。')
} finally {
  ws?.close(); browser?.kill(); await server?.close()
  await sleep(200)
  for (const directory of [fixture, implementationFolder, manifestFolder, profile]) rmSync(directory, { recursive: true, force: true, maxRetries: 4, retryDelay: 200 })
}
