import test from 'node:test'
import assert from 'node:assert/strict'
import { extractSfcFunctions } from '../../../scripts/test-sfc-functions.mjs'
const ref = value => ({ value })
function createPage(scopeSaved) {
  const calls = [], configInfo = ref({ revision: 1, listKey: 'list1' })
  const page = extractSfcFunctions(new URL('../EntityListConfigDesign.vue', import.meta.url), ['saveListMetadata', 'saveAll'], {
    writeFixedFilterRows: () => ({}), fixedFilterRows: ref([]), isSystemEntity: ref(false), entityFields: ref([]),
    entityListConfigApi: { patchMetadata: async (_id, payload) => { calls.push(['metadata', payload.expectedRevision]); return { revision: payload.expectedRevision + 1 } } },
    configId: 'list1', configInfo, toolbarRequiresSelection: ref(false), normalizeListSelectionMode: () => 'SINGLE', validateSelectionReturnMappings: () => [],
    viewConfig: ref({}), entityCode: ref('ENTITY'), saveScopeBindings: async () => { calls.push(['scope']); return scopeSaved },
    rememberMetadataBaseline: () => { calls.push(['baseline', configInfo.value.revision]) }, loadDiff: async () => { calls.push(['diff']) },
    ElMessage: { success: message => calls.push(['success', message]), warning: message => calls.push(['warning', message]) }, handleRevisionConflict: error => { throw error },
    isDirty: ref(true), savingAll: ref(false), metadataDirty: ref(true), dirtyFields: ref([{}]), dirtyActions: ref([]),
    saveCurrentField: async () => { calls.push(['field']); return true }
  })
  return { ...page, calls, configInfo }
}
test('元数据成功但规则取消/失败时返回 false，保留已提交 revision，不报整体成功', async () => {
  const page = createPage(false)
  assert.equal(await page.saveListMetadata(), false)
  assert.equal(page.configInfo.value.revision, 2)
  assert.ok(page.calls.some(([key, value]) => key === 'baseline' && value === 2))
  assert.equal(page.calls.some(([key]) => key === 'success'), false)
  await page.saveListMetadata()
  assert.deepEqual(page.calls.filter(([key]) => key === 'metadata'), [['metadata', 1], ['metadata', 2]])
})
test('整体保存收到部分失败即停止，不继续字段/按钮保存', async () => {
  const page = createPage(false)
  await page.saveAll()
  assert.equal(page.calls.some(([key]) => ['field', 'success'].includes(key)), false)
})
test('元数据与规则均完成才报告整体成功', async () => {
  const page = createPage(true)
  assert.equal(await page.saveListMetadata(), true)
  assert.match(page.calls.find(([key]) => key === 'success')[1], /数据规则绑定已立即生效/)
})
