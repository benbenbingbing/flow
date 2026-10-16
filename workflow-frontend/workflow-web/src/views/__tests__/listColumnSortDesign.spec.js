import test from 'node:test'
import assert from 'node:assert/strict'
import { extractSfcFunctions } from '../../../scripts/test-sfc-functions.mjs'
import { supportsEntityFieldQuery } from '../../shared/list-query-policy.js'
import { isListColumnSortable } from '../../shared/list-column-sort.js'

const ref = value => ({ value })
/** 执行真实编辑器方法，验证独立编辑缓冲中的排序开关确实写入列保存协议。 */
function editor(field, sortable) {
  const saved = []
  const editingField = ref(field)
  const editingColumnConfig = ref({ sortable, minWidth: 180 })
  const scope = {
    editingField, editingColumnConfig,
    editingDataSourceConfig: ref({}), editingRenderConfig: ref({}), editingQueryConfig: ref({}),
    fieldConfigDialogVisible: ref(true), supportsEntityFieldQuery, stringifyConfig: JSON.stringify,
    saveCurrentField: async value => { saved.push(JSON.parse(JSON.stringify(value))); return true }
  }
  return { ...extractSfcFunctions(new URL('../EntityListConfigDesign.vue', import.meta.url), ['saveFieldAdvancedConfig'], scope), saved, scope }
}

test('保存当前列保留排序开关和其他列设置', async () => {
  const page = editor({ fieldId: 'field-1', fieldCode: 'amount', dataSourceType: 'ENTITY_FIELD' }, true)
  await page.saveFieldAdvancedConfig()
  assert.deepEqual(JSON.parse(page.saved[0].columnConfig), { sortable: true, minWidth: 180 })
  assert.equal(page.scope.fieldConfigDialogVisible.value, false)
  page.scope.editingColumnConfig.value.sortable = false
  await page.saveFieldAdvancedConfig()
  assert.equal(JSON.parse(page.saved[1].columnConfig).sortable, false)
})

test('保存计算列清除模板或历史配置残留的排序开关', async () => {
  for (const field of [
    { fieldId: 'virtual_1', dataSourceType: 'ENTITY_FIELD' },
    { fieldId: 'field-1', dataSourceType: 'FIELD_TEMPLATE' },
    { fieldId: 'field-1', dataSourceType: 'INTERFACE', interfaceExtensionId: 'interface-1' }
  ]) {
    const page = editor(field, true)
    await page.saveFieldAdvancedConfig()
    assert.deepEqual(JSON.parse(page.saved[0].columnConfig), { sortable: false, minWidth: 180 })
  }
})

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

/** 控制配置和数据返回次序，复现关闭后重开预览以及失败重试的真实边界。 */
function previewPage() {
  const schemas = [], queries = []
  const scope = {
    isListColumnSortable, entityCode: ref('requirement'), configInfo: ref({ listKey: 'main' }),
    viewConfig: ref({ pagination: { pageSize: 10 } }), previewLoading: ref(false),
    previewError: ref(''), previewDataList: ref([]), previewTotal: ref(0), previewSort: ref(null),
    previewPageNum: ref(1), previewPageSize: ref(10), previewDialogVisible: ref(false),
    previewPublishedFields: ref([]), previewSchemaLoaded: ref(false),
    previewQueryForm: ref({}), previewQueryFields: ref([]), console: { error: () => {} },
    entityListRuntimeApi: {
      getSchema: () => { const call = deferred(); schemas.push(call); return call.promise },
      query: () => { const call = deferred(); queries.push(call); return call.promise }
    }
  }
  const methods = extractSfcFunctions(new URL('../EntityListConfigDesign.vue', import.meta.url), [
    'previewRequestSequence', 'openPreview', 'loadPreviewData', 'isPreviewColumnSortable'
  ], scope)
  return { ...scope, ...methods, schemas, queries }
}

test('重开预览配置失败会结束加载，重试会重新读取配置和数据', async () => {
  const page = previewPage()
  page.previewSchemaLoaded.value = true
  const oldQuery = page.loadPreviewData()
  const reopened = page.openPreview()
  page.schemas[0].reject(new Error('配置加载失败'))
  await reopened
  assert.equal(page.previewLoading.value, false)
  assert.equal(page.previewError.value, '配置加载失败')
  page.queries[0].resolve({ records: [{ id: 'old' }], total: 1 })
  await oldQuery
  assert.deepEqual(page.previewDataList.value, [])
  assert.equal(page.previewLoading.value, false)

  const retry = page.loadPreviewData()
  assert.equal(page.schemas.length, 2)
  const fields = [{ fieldCode: 'amount', columnConfig: { sortable: true }, showInList: true }]
  page.schemas[1].resolve({ fields })
  await Promise.resolve()
  page.queries[1].resolve({ records: [{ id: 'new' }], total: 1 })
  await retry
  assert.deepEqual(page.previewPublishedFields.value, fields)
  assert.deepEqual(page.previewDataList.value, [{ id: 'new' }])
  assert.equal(page.previewError.value, '')
  assert.equal(page.previewLoading.value, false)
})

test('旧预览的配置响应不能覆盖新预览或再次发送旧查询', async () => {
  const page = previewPage()
  const oldOpen = page.openPreview()
  const newOpen = page.openPreview()
  const fields = [{ fieldCode: 'name', columnConfig: { sortable: true } }]
  page.schemas[1].resolve({ fields })
  await Promise.resolve()
  page.queries[0].resolve({ records: [{ id: 'new' }], total: 1 })
  await newOpen
  page.schemas[0].resolve({ fields: [{ fieldCode: 'old' }] })
  await oldOpen
  assert.deepEqual(page.previewPublishedFields.value, fields)
  assert.deepEqual(page.previewDataList.value, [{ id: 'new' }])
  assert.equal(page.queries.length, 1)
  assert.equal(page.previewLoading.value, false)
})

test('草稿重新展示尚未发布的隐藏列时，预览不开启排序', () => {
  const page = previewPage()
  const draft = { fieldCode: 'amount', showInList: true, columnConfig: { sortable: true } }
  page.previewPublishedFields.value = [{ ...draft, showInList: false }]
  assert.equal(page.isPreviewColumnSortable(draft), false)
  page.previewPublishedFields.value = [{ ...draft, columnConfig: { sortable: false } }]
  assert.equal(page.isPreviewColumnSortable(draft), false)
  page.previewPublishedFields.value = [draft]
  assert.equal(page.isPreviewColumnSortable(draft), true)
})
