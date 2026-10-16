import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import { extractSfcFunctions } from '../../../scripts/test-sfc-functions.mjs'

const componentFile = new URL('../UiConfigPublishDialog.vue', import.meta.url)
const ref = value => ({ value })
const computed = getter => ({ get value() { return getter() } })
const emptyDisplay = () => ({ firstPublish: false, total: 0, counts: {}, groups: [], reviewItems: [] })
const validPreview = (overrides = {}) => ({
  changed: true, canPublish: true, blockers: [], activeReleaseId: 'release-1', draftHash: 'hash-1', ...overrides
})

/** 注入可控的异步接口，执行组件真实状态和方法，验证用户切换配置或重试时不会发布旧预检。 */
function createDialog(configType = 'FORM') {
  const previews = [], publishes = [], messages = [], events = []
  const request = (queue, type, id, payload) => new Promise((resolve, reject) => {
    queue.push({ type, id, payload, resolve, reject })
  })
  const props = { modelValue: true, configId: 'config-1', configType }
  const page = extractSfcFunctions(componentFile, [
    'preview', 'previewLoading', 'previewError', 'publishing', 'showReviewOnly', 'form',
    'sessionVersion', 'previewRequest', 'displayPreview', 'visibleGroups', 'countSummary',
    'effectDescription', 'canSubmit', 'footerHint', 'requestPayload', 'initialize', 'loadPreview', 'submit'
  ], {
    ref, computed, reactive: value => value, props,
    buildPublishPreview: preview => preview?.display || emptyDisplay(),
    previewFormPublish: (id, payload) => request(previews, 'FORM', id, payload),
    publishForm: (id, payload) => request(publishes, 'FORM', id, payload),
    entityListConfigApi: {
      previewPublish: (id, payload) => request(previews, 'LIST', id, payload),
      publish: (id, payload) => request(publishes, 'LIST', id, payload)
    },
    ElMessage: Object.fromEntries(['error', 'success', 'warning'].map(type => [type, message => messages.push([type, message])])),
    emit: (...args) => events.push(args)
  })
  return { ...page, props, previews, publishes, messages, events }
}

for (const type of ['FORM', 'LIST']) {
  test(`${type} 使用对应预检与发布接口，检查期间禁止发布，并携带已确认的版本`, async () => {
    const page = createDialog(type)
    const opened = page.initialize()
    assert.equal(page.previewLoading.value, true)
    assert.equal(page.canSubmit.value, false)
    await page.submit()
    assert.equal(page.publishes.length, 0)
    assert.equal(page.previews[0].type, type)
    page.previews[0].resolve(validPreview())
    await opened
    assert.equal(page.canSubmit.value, true)
    page.form.description = '调整预算字段'
    const submitted = page.submit()
    assert.equal(page.canSubmit.value, false, '发布请求未完成时不能重复提交')
    await page.submit()
    assert.equal(page.publishes.length, 1)
    assert.deepEqual(page.publishes[0].payload, {
      description: '调整预算字段', expectedActiveReleaseId: 'release-1', expectedDraftHash: 'hash-1'
    })
    assert.equal(page.publishes[0].type, type)
    page.publishes[0].resolve({ id: 'release-2' })
    await submitted
    assert.deepEqual(page.events, [['published', { id: 'release-2' }], ['update:modelValue', false]])
  })
}

test('预检失败显示接口原因，重试清除错误并保留发布说明', async () => {
  const page = createDialog()
  const opened = page.initialize()
  page.previews[0].reject({ response: { data: { message: '目标表单暂时无法读取' } } })
  await opened
  assert.equal(page.previewError.value, '目标表单暂时无法读取')
  assert.equal(page.preview.value, null)
  assert.equal(page.canSubmit.value, false)
  page.form.description = '用户填写的发布说明'
  const retried = page.loadPreview()
  assert.equal(page.previewError.value, '')
  assert.equal(page.previewLoading.value, true)
  assert.equal(page.previews[1].payload.description, '用户填写的发布说明')
  page.previews[1].resolve(validPreview())
  await retried
  assert.equal(page.canSubmit.value, true)
  assert.equal(page.form.description, '用户填写的发布说明')
})

test('切换配置后，旧请求失败不能覆盖新配置的预检或结束新请求的加载状态', async () => {
  const page = createDialog()
  const older = page.initialize()
  page.props.configType = 'LIST'
  page.props.configId = 'list-2'
  const newer = page.initialize()
  page.previews[0].reject(new Error('旧表单出错'))
  await older
  assert.equal(page.previewLoading.value, true)
  assert.equal(page.previewError.value, '')
  assert.equal(page.previews[1].id, 'list-2')
  page.previews[1].resolve(validPreview({ draftHash: 'new-list-hash' }))
  await newer
  assert.equal(page.preview.value.draftHash, 'new-list-hash')
})

test('连续重试乱序返回时保留最近一次结果；关闭弹窗后也不接受预检结果', async () => {
  const page = createDialog()
  const older = page.initialize()
  const newer = page.loadPreview()
  page.previews[1].resolve(validPreview({ draftHash: 'new-hash' }))
  await newer
  page.previews[0].resolve(validPreview({ draftHash: 'old-hash' }))
  await older
  assert.equal(page.preview.value.draftHash, 'new-hash')
  const last = page.loadPreview()
  page.props.modelValue = false
  await page.initialize()
  page.previews[2].resolve(validPreview())
  await last
  assert.equal(page.preview.value, null)
  assert.equal(page.previewLoading.value, false)
  assert.equal(page.canSubmit.value, false)
})

test('409 冲突保留发布说明并重新检查，下一次发布使用新的版本与草稿', async () => {
  const page = createDialog()
  const opened = page.initialize()
  page.previews[0].resolve(validPreview())
  await opened
  page.form.description = '保留我填写的说明'
  const conflicted = page.submit()
  page.publishes[0].reject({ response: { status: 409 } })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(page.previews.length, 2)
  assert.equal(page.preview.value, null)
  assert.equal(page.canSubmit.value, false)
  assert.equal(page.form.description, '保留我填写的说明')
  page.previews[1].resolve(validPreview({ activeReleaseId: 'release-2', draftHash: 'hash-2' }))
  await conflicted
  assert.equal(page.canSubmit.value, true)
  const retried = page.submit()
  assert.deepEqual(page.publishes[1].payload, {
    description: '保留我填写的说明', expectedActiveReleaseId: 'release-2', expectedDraftHash: 'hash-2'
  })
  page.publishes[1].resolve({ id: 'release-3' })
  await retried
  assert.equal(page.messages[0][0], 'warning')
})

test('旧配置的发布响应不会关闭或刷新用户后来打开的配置', async () => {
  const page = createDialog()
  const opened = page.initialize()
  page.previews[0].resolve(validPreview())
  await opened
  const oldSubmit = page.submit()
  page.props.configId = 'config-2'
  const newOpen = page.initialize()
  page.previews[1].resolve(validPreview({ draftHash: 'config-2-hash' }))
  await newOpen
  page.publishes[0].resolve({ id: 'old-release' })
  await oldSubmit
  assert.deepEqual(page.events, [])
  assert.equal(page.preview.value.draftHash, 'config-2-hash')
})

test('无变更与阻断状态不可发布；重点检查筛选保留统一摘要且不显示空组', () => {
  const page = createDialog()
  page.preview.value = validPreview({ changed: false, canPublish: false })
  assert.equal(page.canSubmit.value, false)
  assert.equal(page.footerHint.value, '无需重复发布')
  page.preview.value = validPreview({ blockers: ['引用表单尚未发布'] })
  assert.equal(page.canSubmit.value, false)
  const regular = { key: 'width', reviewRequired: false }
  const review = { key: 'required', reviewRequired: true }
  page.preview.value = validPreview({ display: {
    firstPublish: false, total: 2, counts: { ADDED: 1, MODIFIED: 1 }, reviewItems: [review],
    groups: [{ key: 'columns', items: [regular] }, { key: 'fields', items: [regular, review] }]
  } })
  assert.equal(page.countSummary.value, '新增 1 项、修改 1 项')
  page.showReviewOnly.value = true
  assert.deepEqual(page.visibleGroups.value, [{ key: 'fields', items: [review] }])
  assert.equal(page.countSummary.value, '新增 1 项、修改 1 项', '筛选不改变本次发布总数')
  page.preview.value.display.reviewItems = []
  assert.equal(page.visibleGroups.value.length, 2, '重新检查后已无提醒时，失去筛选开关不能隐藏全部普通变更')
})

test('发布弹窗 SFC 可以编译，确保条件分支与折叠明细模板有效', () => {
  const { descriptor, errors } = parse(readFileSync(componentFile, 'utf8'))
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'ui-config-publish-dialog' })
  const template = compileTemplate({
    source: descriptor.template.content, filename: 'UiConfigPublishDialog.vue', id: 'ui-config-publish-dialog',
    compilerOptions: { bindingMetadata: script.bindings }
  })
  assert.deepEqual(template.errors, [])
})

test('等价差异过滤为空时说明仅保存格式变化，不虚构业务变化或改写服务端发布许可', () => {
  const page = createDialog('LIST')
  page.preview.value = validPreview({ display: { ...emptyDisplay(), equivalentOnly: true } })
  assert.equal(page.visibleGroups.value.length, 0)
  assert.equal(page.countSummary.value, '')
  assert.match(page.footerHint.value, /仅配置保存格式变化/)
  assert.equal(page.canSubmit.value, true, '格式规范化仍可按服务端许可同步草稿')
  page.preview.value.canPublish = false
  assert.equal(page.canSubmit.value, false)
})
