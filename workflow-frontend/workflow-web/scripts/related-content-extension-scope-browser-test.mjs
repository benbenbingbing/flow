import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 真实 ConfigDialog + Element Plus + HTTP adapter，覆盖页签入口、历史草稿清理和独立扩展页配置。
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const fixture = mkdtempSync(path.join(root, '.related-extension-scope-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import { createApp, h, reactive } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ConfigDialog from '/src/components/related-content/RelatedContentConfigDialog.vue'
import { registerCustomFormComponent } from '/src/extensions/core/registries/customComponentRegistry.js'
import request from '/src/utils/request.js'
const source = { id: 'source', entityCode: 'SOURCE', entityName: '来源记录' }
const target = { id: 'child', entityCode: 'CHILD', entityName: '目标记录', status: 'PUBLISHED' }
const fields = [{ id: 'name', fieldCode: 'name', fieldName: '名称', fieldType: 'STRING' }]
const form = { id: 'child-form', entityId: 'child', formName: '子页面', formKey: 'child_detail', activeReleaseId: 'form-release' }
const relation = { id: 'rel', parentEntityId: 'source', childEntityId: 'child', childEntityCode: 'CHILD', childEntityName: '目标记录', childRefFieldCode: 'sourceId', relationCode: 'children', relationName: '关联子记录', dataKey: 'children', direction: 'FORWARD', relationType: 'ONE_TO_ONE', ownershipType: 'ASSOCIATION', enabled: true }
const readInterface = { extensionId: 'lookup', extensionKey: 'LOOKUP', displayName: '查找子记录接口', interfaceKind: 'READ', interfaceContextType: 'QUERY', enabled: true, inputSchema: { type: 'object', properties: { sourceId: { type: 'string' } } }, outputSchema: { type: 'object', properties: { recordId: { type: 'string' } } } }
const actionInterface = { extensionId: 'calculate', extensionKey: 'CALCULATE', displayName: '计算验收结果', interfaceKind: 'READ', interfaceContextType: 'ACTION', enabled: true, inputSchema: {}, outputSchema: {} }
registerCustomFormComponent('ScopeCard', { render: () => h('div', '专业展示') }, { label: '关联展示卡片', version: 1, usageContexts: ['RELATED_CONTENT'], artifactDigest: 'a'.repeat(64) })
function composition(type, key) {
 return { id: key, compositionKey: key, anchorType: 'OWNER', revision: 1, config: {
  name: '子页面', enabled: true, target: { entityId: type === 'SAME_RECORD' ? 'source' : 'child', entityCode: type === 'SAME_RECORD' ? 'SOURCE' : 'CHILD', contentType: 'FORM', contentId: 'child-form', contentKey: 'child_detail', contentName: '子页面' },
  relation: { type, relationCode: type === 'ENTITY_RELATION' ? 'children' : '', direction: 'FORWARD' },
  actions: ['VIEW', 'EDIT'], presentation: { position: 'INLINE' },
  specialHandling: { mode: type === 'ENTITY_RELATION' ? 'BOTH' : type === 'INTERFACE_SERVICE' ? 'INTERFACE_SERVICE' : 'NONE',
   interfaceService: { extensionId: type === 'ENTITY_RELATION' ? 'legacy-lookup' : '', inputMappings: [], outputMappings: [] },
   actionServices: type === 'ENTITY_RELATION' ? [{ actionKey: 'EDIT', extensionId: 'legacy-edit', inputMappings: [], outputMappings: [] }] : [],
   customComponent: { name: type === 'ENTITY_RELATION' ? 'ScopeCard' : '', version: 1, artifactDigest: 'a'.repeat(64), props: {} }, failurePolicy: 'ERROR' }
 } }
}
const state = reactive({ current: composition('ENTITY_RELATION', 'ordinary'), embedded: true, tab: 'child-pages', draft: null, calls: [], unexpected: [] })
request.defaults.adapter = async config => {
 const url = config.url
 state.calls.push({ url, params: config.params || {} })
 let data
 if (url === '/entity/source/relations/available') data = [relation]
 else if (url === '/entity/options/resolve') data = [String(config.params?.ids || '') === 'source' ? source : target]
 else if (url === '/entity/options') data = { records: [source, target], total: 2 }
 else if (/^\\/entity-form\\/entity\\/(source|child)$/.test(url)) data = [form]
 else if (/^\\/entity-list-config\\/entity\\/(source|child)$/.test(url)) data = []
 else if (/^\\/entity-form\\/entity\\/(source|child)\\/fields$/.test(url)) data = fields
 else if (url === '/entity-forms/child-form/runtime') data = { formId: form.id, configHash: 'child-published-hash', snapshotDocument: JSON.stringify({ form, legacyFields: fields, nodes: [] }) }
 else if (url === '/ui-extensions/available-interfaces') data = config.params.bindingCode === 'RELATED_CONTENT_RESOLVE' ? [readInterface] : [actionInterface]
 else { state.unexpected.push(url); throw Error('Unexpected fixture API: ' + url) }
 return { data: { code: 200, data }, status: 200, statusText: 'OK', headers: {}, config }
}
window.scopeTest = { state, open(type) { state.draft = null; state.tab = 'child-pages'; state.calls = []; state.current = composition(type, type.toLowerCase()) } }
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
createApp({ render: () => h('main', { style: 'max-width:1000px;margin:24px auto' }, [
 h('nav', [h('button', { onClick: () => state.tab = 'child-pages' }, '子页面'), h('button', { onClick: () => state.tab = 'extension' }, '扩展')]),
 h(ConfigDialog, { embedded: state.embedded, modelValue: state.current, settingsTab: state.tab, ownerType: 'FORM', ownerId: 'owner', sourceEntity: source, sourceFields: fields, 'onDraft-change': value => state.draft = value })
]) }).use(createPinia()).use(router).use(ElementPlus).mount('#app')
`)

let server, browser
try {
  server = await createServer({ root, configFile: path.join(root, 'vite.config.js'), cacheDir: path.join(fixture, 'cache'), optimizeDeps: { entries: [path.join(fixture, 'index.html')], include: ['axios', 'async-validator', 'dompurify'] }, server: { host: '127.0.0.1', port: 0, watch: { ignored: ['**/.relation-fixture-*/**', '**/.builtin-button-fixture-*/**'] } } })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1200, height: 1100 } })
  page.setDefaultTimeout(10000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`${server.resolvedUrls.local[0]}${path.basename(fixture)}/index.html`)
  await page.waitForFunction(() => window.scopeTest?.state.draft?.compositionKey === 'ordinary')
  const dialog = page.locator('.related-content-config-dialog')
  const checkbox = name => dialog.locator('.el-checkbox').filter({ hasText: new RegExp('^' + name + '$') })
  const setChecked = async (label, checked = true) => { if (await label.locator('input').isChecked() !== checked) await label.click() }
  const visibleSectionTitles = () => dialog.locator('.settings-section:visible > .settings-section__header .settings-section__heading > strong').allTextContents()
  const assertNoActionInterfaces = async () => {
    assert.equal(await dialog.getByRole('button', { name: '增加操作接口', exact: true }).isVisible(), false)
    assert.equal(await checkbox('使用数据或动作扩展接口').isVisible(), false)
    assert.equal(await dialog.locator('.action-service-section:visible').count(), 0)
  }
  assert.equal((await visibleSectionTitles()).includes('关联操作扩展'), false)
  await assertNoActionInterfaces()
  const cleaned = await page.evaluate(() => scopeTest.state.draft.config.specialHandling)
  assert.equal(cleaned.interfaceService.extensionId, '')
  assert.deepEqual(cleaned.actionServices, [])
  assert.equal(cleaned.mode, 'NONE')
  assert.equal(cleaned.customComponent?.name || '', '')
  assert.equal(await page.evaluate(() => scopeTest.state.calls.some(call => call.url === '/ui-extensions/available-interfaces')), false, '普通关系不能请求接口目录')

  await page.getByRole('button', { name: '扩展', exact: true }).click()
  assert.deepEqual(await visibleSectionTitles(), [])
  await assertNoActionInterfaces()
  const custom = checkbox('使用自定义组件')
  assert.equal(await custom.count(), 0, '普通关系完全移除展示覆盖，不保留隐藏的组件选择器')
  assert.equal(await dialog.locator('.special-config-card').count(), 0)
  assert.equal(await page.evaluate(() => scopeTest.state.calls.some(call => call.url === '/ui-extensions/available-interfaces')), false)

  const capabilityErrors = []
  for (const type of ['SAME_RECORD', 'INTERFACE_SERVICE']) {
    await page.evaluate(value => scopeTest.open(value), type)
    await page.waitForFunction(value => scopeTest.state.draft?.compositionKey === value.toLowerCase(), type)
    assert.equal((await visibleSectionTitles()).includes('关联操作扩展'), false)
    await assertNoActionInterfaces()
    assert.deepEqual(await page.evaluate(() => scopeTest.state.calls.filter(call => call.url === '/ui-extensions/available-interfaces').map(call => call.params.bindingCode).sort()), ['RELATED_CONTENT_ACTION', 'RELATED_CONTENT_RESOLVE'])
    await page.getByRole('button', { name: '扩展', exact: true }).click()
    assert.deepEqual(await visibleSectionTitles(), ['页面扩展'])
    // 独立扩展仍允许使用关联契约组件，不能误删共用注册能力。
    await setChecked(custom)
    const customSelect = dialog.locator('.special-config-card').filter({ hasText: '固定精确版本' }).locator('.el-select').first()
    await customSelect.click()
    await page.getByRole('option', { name: /关联展示卡片/ }).click()
    await page.waitForFunction(() => scopeTest.state.draft.config.specialHandling.customComponent.name === 'ScopeCard')
    const service = checkbox('使用数据或动作扩展接口')
    assert.equal(await service.isVisible(), true)
    if (type === 'SAME_RECORD') await setChecked(service)
    else assert.equal(await service.locator('input').isDisabled(), true)
    const useData = checkbox('使用接口查找目标数据')
    const dataSelect = dialog.locator('.special-config-card').filter({ has: page.locator('.data-service-toggle') }).locator(':scope > .el-form .el-select').first()
    if (type === 'SAME_RECORD') {
      await setChecked(useData)
      await page.evaluate(() => new Promise(requestAnimationFrame))
      if (!await dataSelect.count()) capabilityErrors.push('当前记录扩展页首次启用数据接口后未显示选择器')
    }
    else assert.equal(await useData.locator('input').isChecked(), true)
    if (await dataSelect.count()) {
      await dataSelect.click()
      await page.getByRole('option', { name: /查找子记录接口/ }).click()
      await page.waitForFunction(() => scopeTest.state.draft.config.specialHandling.interfaceService.extensionId === 'lookup')
    }
    await dialog.getByRole('button', { name: '增加操作接口', exact: true }).click()
    await dialog.locator('.action-service-card .el-select').first().click()
    await page.getByRole('option', { name: /计算验收结果/ }).click()
    await page.waitForFunction(() => scopeTest.state.draft.config.specialHandling.actionServices[0]?.extensionId === 'calculate')
    const action = await page.evaluate(() => scopeTest.state.draft.config.specialHandling.actionServices[0])
    assert.equal(action.actionKey, 'CALCULATE')
    assert.equal(action.failurePolicy, 'ERROR')
    if (type === 'SAME_RECORD') {
      // 关闭数据接口清空旧绑定；再次开启应能立即选择，同时保留已配置动作接口。
      await setChecked(useData, false)
      await page.waitForFunction(() => scopeTest.state.draft.config.specialHandling.interfaceService.extensionId === '')
      assert.equal(await dataSelect.count(), 0)
      await setChecked(useData)
      await dataSelect.waitFor({ state: 'visible' })
      await dataSelect.click()
      await page.getByRole('option', { name: /查找子记录接口/ }).click()
      await page.waitForFunction(() => scopeTest.state.draft.config.specialHandling.interfaceService.extensionId === 'lookup')
      assert.equal(await page.evaluate(() => scopeTest.state.draft.config.specialHandling.actionServices[0].extensionId), 'calculate')
    }
    // 页签切换只改变显示，不丢失独立扩展页已编辑的数据接口和动作绑定。
    await page.getByRole('button', { name: '子页面', exact: true }).click()
    await assertNoActionInterfaces()
    await page.getByRole('button', { name: '扩展', exact: true }).click()
    assert.equal(await dialog.locator('.action-service-card:visible').count(), 1)
    assert.equal(await page.evaluate(() => scopeTest.state.draft.config.specialHandling.actionServices[0].extensionId), 'calculate')
    assert.equal(await page.evaluate(() => scopeTest.state.draft.config.specialHandling.interfaceService.extensionId), 'lookup')
    assert.equal(await page.evaluate(() => scopeTest.state.draft.config.specialHandling.customComponent.name), 'ScopeCard')
  }
  // 同一编辑器的列表/独立弹窗入口只剩两步，末步保留确认摘要及保存入口。
  await page.evaluate(() => scopeTest.open('ENTITY_RELATION'))
  await page.waitForFunction(() => scopeTest.state.draft?.compositionKey === 'entity_relation')
  await page.evaluate(() => { scopeTest.state.embedded = false })
  await dialog.getByText('第 1 步，共 2 步', { exact: true }).waitFor({ state: 'visible' })
  assert.equal(await dialog.locator('.el-steps .el-step').count(), 2)
  assert.equal(await custom.count(), 0)
  await dialog.getByRole('button', { name: '下一步', exact: true }).click()
  await dialog.getByText('第 2 步，共 2 步', { exact: true }).waitFor({ state: 'visible' })
  assert.equal(await dialog.getByRole('button', { name: '下一步', exact: true }).count(), 0)
  assert.equal(await dialog.getByText('保存前确认', { exact: true }).isVisible(), true)
  assert.equal(await dialog.getByRole('button', { name: '保存显示配置', exact: true }).isEnabled(), true)
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  assert.deepEqual(await page.evaluate(() => scopeTest.state.unexpected), [])
  assert.deepEqual(errors, [])
  assert.deepEqual(capabilityErrors, [], '独立扩展页数据接口必须可编辑')
  console.log('related content extension scope browser acceptance passed: ordinary relation display overrides removed and legacy settings cleared; two-step dialog; SAME_RECORD and INTERFACE_SERVICE component/data/action editors preserved')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
