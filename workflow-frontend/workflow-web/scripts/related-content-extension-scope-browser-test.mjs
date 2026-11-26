import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

// 真实 ConfigDialog、Element Plus 和 HTTP adapter，确认新入口与历史配置的运行兼容。
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const fixture = mkdtempSync(path.join(root, '.related-extension-scope-fixture-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), `
import { createApp, h, reactive, ref } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ConfigDialog from '/src/components/related-content/RelatedContentConfigDialog.vue'
import request from '/src/utils/request.js'
const source = { id: 'source', entityCode: 'SOURCE', entityName: '来源记录' }
const fields = [{ id: 'name', fieldCode: 'name', fieldName: '名称', fieldType: 'STRING' }]
const form = { id: 'child-form', entityId: 'child', formName: '子页面', formKey: 'child_detail', activeReleaseId: 'form-release' }
const currentRecordForm = {
 id: 'source-detail', entityId: 'source', formName: '当前记录详情', formKey: 'source_detail', activeReleaseId: 'source-detail-release',
 viewConfig: { inputParameterSchema: { type: 'object', properties: { projectId: { type: 'string', title: '项目标识' } } } }
}
const relation = { id: 'rel', parentEntityId: 'source', childEntityId: 'child', childEntityCode: 'CHILD', childEntityName: '目标记录', childRefFieldCode: 'sourceId', relationCode: 'children', relationName: '关联子记录', dataKey: 'children', direction: 'FORWARD', relationType: 'ONE_TO_ONE', ownershipType: 'ASSOCIATION', enabled: true }
function composition(type, key) {
 return { id: key, compositionKey: key, anchorType: 'OWNER', revision: 1, config: {
  name: '子页面', enabled: true, target: type === 'SAME_RECORD'
   ? { entityId: 'source', entityCode: 'SOURCE', contentType: 'FORM', contentId: currentRecordForm.id, contentKey: currentRecordForm.formKey, contentName: currentRecordForm.formName }
   : { entityId: 'child', entityCode: 'CHILD', contentType: 'FORM', contentId: form.id, contentKey: form.formKey, contentName: form.formName },
  relation: { type, relationCode: type === 'ENTITY_RELATION' ? 'children' : '', direction: 'FORWARD' },
  actions: ['VIEW', 'EDIT'], presentation: { position: 'INLINE' },
  specialHandling: { mode: type === 'ENTITY_RELATION' ? 'BOTH' : type === 'INTERFACE_SERVICE' ? 'INTERFACE_SERVICE' : 'NONE',
   interfaceService: { extensionId: type === 'ENTITY_RELATION' || type === 'INTERFACE_SERVICE' ? 'legacy-lookup' : '', inputMappings: [], outputMappings: [] },
   actionServices: type === 'ENTITY_RELATION' ? [{ actionKey: 'EDIT', extensionId: 'legacy-edit', inputMappings: [], outputMappings: [] }] : [],
   customComponent: { name: type === 'ENTITY_RELATION' ? 'ScopeCard' : '', version: 1, artifactDigest: 'a'.repeat(64), props: {} }, failurePolicy: 'ERROR' }
 } }
}
const state = reactive({ current: composition('ENTITY_RELATION', 'ordinary'), embedded: true, tab: 'child-pages', draft: null, calls: [], unexpected: [] })
request.defaults.adapter = async config => {
 const url = config.url
 state.calls.push({ url })
 let data
 if (url === '/entity/source/relations/available') data = [relation]
 else if (url === '/entity-form/entity/source') data = [currentRecordForm, { ...currentRecordForm, id: 'owner', formName: '宿主表单' }]
 else if (url === '/entity-form/entity/child') data = [form]
 else if (/^\\/entity-list-config\\/entity\\/(source|child)$/.test(url)) data = []
 else if (/^\\/entity-form\\/entity\\/(source|child)\\/fields$/.test(url)) data = fields
 else if (url === '/entity-forms/child-form/runtime') data = { formId: form.id, configHash: 'child-published-hash', snapshotDocument: JSON.stringify({ form, legacyFields: fields, nodes: [] }) }
 else if (url === '/entity-forms/source-detail/runtime') data = { formId: currentRecordForm.id, snapshotDocument: JSON.stringify({ form: currentRecordForm, legacyFields: fields, nodes: [] }) }
 else if (url === '/ui-view-compositions/FORM/owner/validate') data = { normalizedConfig: JSON.parse(config.data).config }
 else if (url === '/ui-view-compositions/FORM/owner') data = { id: 'saved', ...JSON.parse(config.data) }
 else { state.unexpected.push(url); throw Error('Unexpected fixture API: ' + url) }
 return { data: { code: 200, data }, status: 200, statusText: 'OK', headers: {}, config }
}
window.scopeTest = { state, open(type) { state.draft = null; state.tab = 'child-pages'; state.calls = []; state.current = composition(type, type.toLowerCase()) } }
const dialogRef = ref(null)
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
createApp({ render: () => h('main', { style: 'max-width:1000px;margin:24px auto' }, [
 h('nav', [h('button', { onClick: () => state.tab = 'child-pages' }, '子页面'), h('button', { onClick: () => state.tab = 'extension' }, '扩展'), h('button', { onClick: () => dialogRef.value.open(null, { sameRecord: true }) }, '添加当前记录页面')]),
 h(ConfigDialog, { ref: dialogRef, onSaved: value => state.saved = value, embedded: state.embedded, modelValue: state.current, settingsTab: state.tab, ownerType: 'FORM', ownerId: 'owner', sourceEntity: source, sourceFields: fields, 'onDraft-change': value => state.draft = value })
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
  const assertExtensionSettingsRemoved = async () => {
    assert.equal(await dialog.getByText('页面扩展', { exact: true }).count(), 0)
    assert.equal(await dialog.getByText('使用扩展接口', { exact: true }).count(), 0)
    assert.equal(await dialog.locator('.special-config-card').count(), 0)
    assert.equal(await page.evaluate(() => scopeTest.state.calls.some(call => call.url === '/ui-extensions/available-interfaces')), false)
  }
  const cleaned = await page.evaluate(() => scopeTest.state.draft.config.specialHandling)
  assert.equal(cleaned.interfaceService.extensionId, '')
  assert.deepEqual(cleaned.actionServices, [])
  assert.equal(cleaned.mode, 'NONE')
  assert.equal(cleaned.customComponent?.name || '', '')
  await assertExtensionSettingsRemoved()

  for (const type of ['SAME_RECORD', 'INTERFACE_SERVICE']) {
    await page.evaluate(value => scopeTest.open(value), type)
    await page.waitForFunction(value => scopeTest.state.draft?.compositionKey === value.toLowerCase(), type)
    const legacyHint = dialog.getByText(/此处已有旧版页面扩展配置/)
    assert.equal(await legacyHint.count(), type === 'INTERFACE_SERVICE' ? 1 : 0)
    if (type === 'SAME_RECORD') {
      assert.ok(await dialog.getByText(/目标内容直接使用当前记录/).isVisible())
    } else {
      assert.equal(await page.evaluate(() => scopeTest.state.draft.config.specialHandling.interfaceService.extensionId), 'legacy-lookup')
    }
    await assertExtensionSettingsRemoved()
    await page.getByRole('button', { name: '扩展', exact: true }).click()
    assert.equal(await dialog.locator('.settings-section:visible').count(), 0)
  }
  // 同一编辑器的列表/独立弹窗入口只剩两步，末步保留确认摘要及保存入口。
  await page.evaluate(() => scopeTest.open('ENTITY_RELATION'))
  await page.waitForFunction(() => scopeTest.state.draft?.compositionKey === 'entity_relation')
  await page.evaluate(() => { scopeTest.state.embedded = false })
  await dialog.getByText('第 1 步，共 2 步', { exact: true }).waitFor({ state: 'visible' })
  assert.equal(await dialog.locator('.el-steps .el-step').count(), 2)
  await assertExtensionSettingsRemoved()
  await dialog.getByRole('button', { name: '下一步', exact: true }).click()
  await dialog.getByText('第 2 步，共 2 步', { exact: true }).waitFor({ state: 'visible' })
  assert.equal(await dialog.getByRole('button', { name: '下一步', exact: true }).count(), 0)
  assert.equal(await dialog.getByText('保存前确认', { exact: true }).isVisible(), true)
  assert.equal(await dialog.getByRole('button', { name: '保存显示配置', exact: true }).isEnabled(), true)
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '添加当前记录页面', exact: true }).click()
  await dialog.getByText('当前记录页面', { exact: true }).waitFor()
  await dialog.locator('.target-content-select').click()
  assert.equal(await page.getByRole('option', { name: /宿主表单/ }).count(), 0)
  await page.getByRole('option', { name: /当前记录详情/ }).click()
  await dialog.getByRole('button', { name: '增加映射', exact: true }).click()
  await dialog.locator('.parameter-row .el-select').nth(1).click()
  await page.getByRole('option', { name: '当前记录 ID', exact: true }).click()
  await dialog.getByRole('button', { name: '下一步', exact: true }).click()
  await dialog.getByText(/目标内容直接使用当前记录/).waitFor()
  await dialog.getByRole('button', { name: '下一步', exact: true }).click()
  await dialog.getByRole('button', { name: '保存关联内容', exact: true }).click()
  await page.waitForFunction(() => scopeTest.state.saved)
  const saved = await page.evaluate(() => scopeTest.state.saved.config)
  assert.equal(saved.relation.type, 'SAME_RECORD')
  assert.equal(saved.target.entityId, 'source')
  assert.equal(saved.target.contentId, 'source-detail')
  assert.equal(saved.specialHandling.mode, 'NONE')
  assert.deepEqual(saved.parameterMappings, [{ parameter: 'projectId', sourceType: 'RECORD_ID', sourceField: '', value: '' }])
  await assertExtensionSettingsRemoved()
  assert.deepEqual(await page.evaluate(() => scopeTest.state.unexpected), [])
  assert.deepEqual(errors, [])
  console.log('related content browser acceptance passed: extension editors removed, ordinary relations and current-record pages supported, legacy bindings preserved')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
