import { test, expect } from '@playwright/test'
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
const field = { id: 'amount', fieldId: 'amount', fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', componentType: 'number', isReadonly: 0 }
const form = (overrides = {}) => ({ id: 'form-test', formKey: 'test', formName: '测试表单', configHash: 'hash-1', fields: [field], nodes: [], ...overrides })
const action = (key, label) => ({ key, label, type: 'built-in', placement: 'FOOTER', visible: true, enabled: true })
const actions = [action('save', '保存'), action('reset', '重置')]
const mapping = amount => ({ effects: [{ type: 'FIELD_MAPPING', data: { form: { amount } }, mappings: [{ targetPath: 'form.amount', overwrite: 'ALWAYS' }] }] })
async function setup(page, options = {}) {
  const state = { reads: [], writes: [], errors: [], unexpected: [] }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const endpoint = url.pathname.slice(4), body = route.request().postDataJSON()
    state.reads.push({ endpoint, body })
    let data
    if (endpoint === '/entity-form-resolve/new-data/test') data = options.config ? await options.config() : form()
    else if (endpoint.includes('/detail/') && endpoint.endsWith('/load')) data = options.detail ? await options.detail(endpoint) : { id: endpoint.match(/detail\/([^/]+)\//)[1], data: { amount: 7 } }
    else if (endpoint === '/ui-runtime/form-actions/resolve') data = options.actions ? await options.actions(body) : actions
    else if (endpoint === '/ui-runtime/events/FORM_OPEN/execute') data = options.event ? await options.event(body) : { effects: [] }
    else if (endpoint === '/ui-runtime/events/FORM_CLOSE/execute') data = options.closeEvent ? await options.closeEvent(body) : { effects: [] }
    else if (['/ui-runtime/events/FORM_RESET/execute', '/ui-runtime/events/FIELD_CHANGE/execute'].includes(endpoint)) data = { effects: [] }
    else if (endpoint === '/ui-runtime/extensions/execute') data = options.extension ? await options.extension(body) : {}
    else if (endpoint.includes('/dict/')) data = []
    else if (endpoint === '/entity/code/test') data = { id: 'entity-test', entityCode: 'test', storageMode: 'DYNAMIC', fields: [field] }
    else if (endpoint === '/entity-lists/test/tests/schema') data = { id: 'list-test', listKey: 'tests', configHash: 'list-hash', fields: [{ fieldCode: 'name', fieldName: '名称', showInList: true }], toolbarButtons: [{ key: 'create', type: 'built-in', label: '新增', enabled: true }], rowActionButtons: [{ key: 'edit', type: 'built-in', label: '编辑', enabled: true }] }
    else if (endpoint === '/entity-lists/test/tests/query') data = { records: [{ id: 'a', name: '记录 A', actionCapabilities: { edit: { visible: true, enabled: true } } }], total: 1, toolbarCapabilities: { create: { visible: true, enabled: true } } }
    else if (endpoint === '/entity-versions/records/test/capabilities') data = { runtimeEnabled: false }
    else if (endpoint.startsWith('/entity-status/list/')) data = []
    else if (endpoint === '/entity-data' || endpoint.startsWith('/entity-data/entity/test/')) { state.writes.push({ endpoint, body, params: Object.fromEntries(url.searchParams) }); data = { id: 'saved' } }
    else { state.unexpected.push(endpoint); return route.fulfill({ status: 500, json: { code: 500, message: `未声明接口 ${endpoint}` } }) }
    if (data?.failure) return route.fulfill({ status: 500, json: { code: 500, message: data.failure } })
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto(`/tests/e2e/form-opening-fixture.html${options.workspace ? '?workspace=1' : options.list ? '?list=1' : ''}`)
  await page.waitForFunction(() => window.openingTest)
  return state
}
const dialog = page => page.locator('.entity-data-form-dialog:visible')
const amount = page => dialog(page).getByRole('spinbutton').first()
const start = (page, mode = 'create', id = 'a', options = {}) => page.evaluate(([mode, id, options]) => { void openingTest.open(mode, id, options) }, [mode, id, options])
const close = async page => { await dialog(page).getByRole('button', { name: 'Close this dialog', exact: true }).click(); await expect(dialog(page)).toHaveCount(0) }
const closeEvents = state => state.reads.filter(item => item.endpoint.includes('/FORM_CLOSE/')).map(item => item.body)
const clean = state => { expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([]) }
for (const mode of ['create', 'edit']) test(`${mode}：配置未返回即显示骨架，关闭后不继续读取详情或执行事件`, async ({ page }) => {
  const gate = deferred(), entered = deferred(), state = await setup(page, { config: async () => { entered.resolve(); await gate.promise; return form() } })
  await start(page, mode); await entered.promise; await expect(dialog(page).locator('.el-skeleton')).toBeVisible(); expect(state.reads).toHaveLength(1)
  await close(page)
  const response = page.waitForResponse(response => response.url().includes('/entity-form-resolve/')); gate.resolve(); await response
  await page.evaluate(() => new Promise(requestAnimationFrame)); expect(state.reads).toHaveLength(1); clean(state)
})
test('编辑旧详情迟到不能覆盖新记录，也不能继续执行旧记录打开事件', async ({ page }) => {
  const gate = deferred(), entered = deferred(), state = await setup(page, { detail: async endpoint => {
    const id = endpoint.includes('/a/') ? 'a' : 'b'; if (id === 'a') { entered.resolve(); await gate.promise }; return { id, data: { amount: id === 'a' ? 7 : 20 } }
  } })
  await start(page, 'edit', 'a'); await entered.promise; await start(page, 'edit', 'b'); await expect(amount(page)).toHaveValue('20.00')
  const response = page.waitForResponse(response => response.url().includes('/detail/a/')); gate.resolve(); await response
  await expect(amount(page)).toHaveValue('20.00')
  const events = state.reads.filter(item => item.endpoint.includes('/FORM_OPEN/')); expect(events).toHaveLength(1); expect(events[0].body.recordId).toBe('b')
  expect(state.reads.filter(item => item.endpoint.endsWith('/progress'))).toHaveLength(0); clean(state)
})
test('新增必要初始化按顺序完成，按钮慢响应不阻塞填写，重置恢复完整初值', async ({ page }) => {
  const init = deferred(), entered = deferred(), buttons = deferred(), actionsEntered = deferred()
  const state = await setup(page, {
    config: () => form({ dataSourceBindings: { FORM_INIT: { extensionId: 'init', inputMapping: { entry: 'context.entryCode', initialAmount: 'context.initialData.amount' } }, AFTER_LOAD: { extensionId: 'after' } } }), event: () => mapping(5),
    extension: async body => { if (body.bindingCode === 'FORM_INIT') { entered.resolve(); await init.promise; return { amount: 10 } }; return { amount: 12 } },
    actions: async () => { actionsEntered.resolve(); await buttons.promise; return actions }
  })
  await start(page, 'create', '', { initialData: { amount: 55 }, context: { entryCode: 'entry-1' } }); await entered.promise
  expect(state.reads.find(item => item.body?.bindingCode === 'FORM_INIT').body.input).toEqual({ entry: 'entry-1', initialAmount: 55 })
  await expect(dialog(page).locator('.el-skeleton')).toBeVisible(); expect(state.reads.filter(item => item.endpoint.includes('form-actions'))).toHaveLength(0)
  init.resolve(); await actionsEntered.promise; await expect(amount(page)).toHaveValue('55.00'); await expect(amount(page)).toBeEnabled()
  await expect(dialog(page).getByRole('status')).toContainText('正在加载操作'); await amount(page).fill('77')
  buttons.resolve(); await dialog(page).getByRole('button', { name: '重置', exact: true }).click(); await expect(amount(page)).toHaveValue('55.00')
  expect(state.reads.filter(item => item.endpoint.includes('/FORM_OPEN/'))).toHaveLength(1)
  expect(state.reads.filter(item => item.endpoint.includes('/extensions/')).map(item => item.body.bindingCode)).toEqual(['FORM_INIT', 'AFTER_LOAD'])
  await close(page); await start(page, 'create'); await expect(amount(page)).toHaveValue('12.00')
  expect(state.reads.filter(item => item.endpoint.includes('/extensions/') && item.body.bindingCode === 'FORM_INIT')).toHaveLength(2); clean(state)
})
test('编辑不执行新增初始化，AFTER_LOAD 完成后才可填写', async ({ page }) => {
  const gate = deferred(), entered = deferred(), state = await setup(page, {
    config: () => form({ dataSourceBindings: { FORM_INIT: { extensionId: 'init' }, AFTER_LOAD: { extensionId: 'after' } } }),
    extension: async () => { entered.resolve(); await gate.promise; return { amount: 22 } }
  })
  await start(page, 'edit'); await entered.promise; await expect(dialog(page).locator('.el-skeleton')).toBeVisible()
  gate.resolve(); await expect(amount(page)).toHaveValue('22.00')
  expect(state.reads.filter(item => item.endpoint.includes('/extensions/')).map(item => item.body.bindingCode)).toEqual(['AFTER_LOAD']); clean(state)
})
test('关闭时正在初始化，不再执行后续绑定；新记录不受旧结果影响', async ({ page }) => {
  const gate = deferred(), entered = deferred(); let first = true
  const state = await setup(page, {
    config: () => form({ dataSourceBindings: { FORM_INIT: { extensionId: 'init' }, AFTER_LOAD: { extensionId: 'after' } } }),
    extension: async () => { if (first) { first = false; entered.resolve(); await gate.promise; return { amount: 999 } }; return { amount: 23 } }
  })
  await start(page); await entered.promise; await close(page); await start(page, 'edit', 'b'); await expect(amount(page)).toHaveValue('23.00')
  const response = page.waitForResponse(response => response.url().endsWith('/extensions/execute')); gate.resolve(); await response
  await expect(amount(page)).toHaveValue('23.00')
  expect(state.reads.filter(item => item.endpoint.includes('/extensions/')).map(item => item.body.bindingCode)).toEqual(['FORM_INIT', 'AFTER_LOAD']); clean(state)
})
test('按钮失败独立重试，已填写内容及打开事件次数不变', async ({ page }) => {
  let calls = 0; const state = await setup(page, { actions: () => ++calls === 1 ? { failure: '按钮服务暂时不可用' } : actions })
  await start(page, 'edit'); await expect(amount(page)).toHaveValue('7.00'); await expect(dialog(page).getByRole('button', { name: '重试操作加载', exact: true })).toBeVisible()
  await amount(page).fill('88'); await dialog(page).getByRole('button', { name: '重试操作加载', exact: true }).click()
  await expect(dialog(page).getByRole('button', { name: '保存', exact: true })).toBeVisible(); await expect(amount(page)).toHaveValue('88.00')
  expect(state.reads.filter(item => item.endpoint.includes('/detail/'))).toHaveLength(1); expect(state.reads.filter(item => item.endpoint.includes('/FORM_OPEN/'))).toHaveLength(1); clean(state)
})
test('用户关闭后丢弃打开事件的迟到刷新效果，事件自身关闭仍执行后续刷新', async ({ page }) => {
  const gate = deferred(), entered = deferred(); let calls = 0
  const state = await setup(page, { event: async () => {
    if (++calls === 1) { entered.resolve(); await gate.promise }
    return { effects: [{ type: 'CLOSE_FORM' }, { type: 'REFRESH_PARENT' }] }
  } })
  await start(page); await entered.promise
  await dialog(page).getByRole('button', { name: 'Close this dialog', exact: true }).click()
  const response = page.waitForResponse(response => response.url().includes('/FORM_OPEN/')); gate.resolve(); await response
  await page.evaluate(() => new Promise(requestAnimationFrame)); expect(await page.evaluate(() => openingTest.refreshed.value)).toBe(0)
  await start(page); await expect.poll(() => page.evaluate(() => openingTest.refreshed.value)).toBe(1)
  await expect(dialog(page)).toHaveCount(0); expect(state.reads.filter(item => item.endpoint.includes('form-actions'))).toHaveLength(0); clean(state)
})
test('旧打开事件和按钮迟到，不回填字段、不暴露旧操作', async ({ page }) => {
  const oldEvent = deferred(), eventEntered = deferred(), oldActions = deferred(), actionsEntered = deferred()
  const state = await setup(page, {
    event: async body => { if (body.recordId === 'a') { eventEntered.resolve(); await oldEvent.promise; return mapping(999) }; return {} },
    actions: async body => { if (body.recordId === 'b') { actionsEntered.resolve(); await oldActions.promise; return [action('save', '旧操作')] }; return actions }
  })
  await start(page, 'edit', 'a'); await eventEntered.promise; await start(page, 'edit', 'b'); await actionsEntered.promise
  await start(page, 'edit', 'c'); await expect(dialog(page).getByRole('button', { name: '保存', exact: true })).toBeVisible()
  oldEvent.resolve(); oldActions.resolve(); await expect(amount(page)).toHaveValue('7.00'); await expect(dialog(page).getByRole('button', { name: '旧操作' })).toHaveCount(0)
  expect(state.reads.filter(item => item.endpoint.includes('form-actions') && item.body.recordId === 'a')).toHaveLength(0); clean(state)
})
test('打开事件失败阻止提交，不自动重放；关闭后可重新打开', async ({ page }) => {
  let calls = 0; const state = await setup(page, { event: () => ++calls === 1 ? { failure: '打开事件执行失败' } : {} })
  await start(page); await expect(dialog(page).getByRole('alert')).toContainText('表单打开事件加载失败'); await expect(amount(page)).toHaveCount(0)
  expect(state.reads.filter(item => item.endpoint.includes('/FORM_OPEN/'))).toHaveLength(1); expect(state.reads.filter(item => item.endpoint.includes('form-actions'))).toHaveLength(0)
  await close(page); await start(page); await expect(amount(page)).toBeVisible(); clean(state)
})
for (const mode of ['create', 'edit']) test(`${mode} 保存保留配置摘要、字段值和提交入口`, async ({ page }) => {
  const state = await setup(page); await start(page, mode); await expect(amount(page)).toBeVisible(); await amount(page).fill('66')
  await dialog(page).getByRole('button', { name: '保存', exact: true }).click(); await expect(dialog(page)).toHaveCount(0)
  expect(state.writes).toHaveLength(1); const write = state.writes[0]
  expect(mode === 'create' ? write.endpoint === '/entity-data' : write.endpoint.includes('/a')).toBe(true)
  expect(write.body.formId).toBe('form-test'); expect(write.body.formConfigHash).toBe('hash-1'); expect(write.body.data.amount).toBe(66); expect(write.body.startProcess).toBe(false); clean(state)
})
for (const mode of ['create', 'edit']) test(`${mode} 保存并发起仍使用原提交接口并传递流程标记`, async ({ page }) => {
  const state = await setup(page, { actions: () => [action('saveAndStart', '保存并发起')] })
  await start(page, mode); await expect(amount(page)).toBeVisible(); await amount(page).fill('68')
  await dialog(page).getByRole('button', { name: '保存并发起', exact: true }).click(); await expect(dialog(page)).toHaveCount(0)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0].body.startProcess).toBe(true); expect(state.writes[0].body.data.amount).toBe(68)
  expect(state.writes[0].body.formConfigHash).toBe('hash-1'); clean(state)
})
test('工作区隐藏再显示保留会话，初始化完成作为未保存基线', async ({ page }) => {
  const state = await setup(page, {
    workspace: true,
    config: () => form({ hasFormCloseBinding: true, dataSourceBindings: { FORM_INIT: { extensionId: 'init' } } }),
    extension: () => ({ amount: 33 })
  })
  await start(page); await expect(amount(page)).toHaveValue('33.00'); expect(await page.evaluate(() => openingTest.confirmDiscard())).toBe(true)
  await amount(page).fill('44'); await page.evaluate(() => { openingTest.workspace.active.value = false }); await expect(dialog(page)).toHaveCount(0)
  await page.evaluate(() => { openingTest.workspace.active.value = true }); await expect(amount(page)).toHaveValue('44.00')
  await dialog(page).getByRole('button', { name: 'Close this dialog', exact: true }).click(); await expect(page.getByText('确认放弃修改？', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '继续填写', exact: true }).click(); await expect(amount(page)).toHaveValue('44.00')
  expect(closeEvents(state)).toHaveLength(0)
  await dialog(page).getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.getByRole('button', { name: '放弃修改', exact: true }).click()
  await expect(dialog(page)).toHaveCount(0)
  await expect.poll(() => closeEvents(state).map(body => body.input.reason)).toEqual(['dismiss'])
  expect(state.reads.filter(item => item.endpoint.includes('/FORM_OPEN/'))).toHaveLength(1); clean(state)
})

test('关闭事件区分保存和取消，带上配置摘要且失败不阻止关闭', async ({ page }) => {
  const state = await setup(page, {
    config: () => form({ hasFormCloseBinding: true }),
    actions: () => [...actions, action('close', '取消')],
    closeEvent: body => body.input.reason === 'cancel' ? { failure: '通知接口不可用' } : {}
  })
  await start(page)
  await dialog(page).getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog(page)).toHaveCount(0)
  await expect.poll(() => closeEvents(state).length).toBe(1)
  const [saved] = closeEvents(state)
  expect(saved).toMatchObject({ configId: 'form-test', configHash: 'hash-1', input: { mode: 'create', reason: 'saved', savedRecordId: 'saved' } })
  expect(saved.recordId).toBeUndefined()

  await start(page, 'edit', 'b')
  const failedNotification = page.waitForResponse(response => response.url().includes('/FORM_CLOSE/') && response.status() === 500)
  await dialog(page).getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog(page)).toHaveCount(0)
  await failedNotification
  expect(closeEvents(state)).toHaveLength(2)
  expect(closeEvents(state)[1]).toMatchObject({ recordId: 'b', input: { mode: 'edit', reason: 'cancel' } })
  await expect(page.getByText('通知接口不可用', { exact: true })).toHaveCount(0)
  clean(state)
})

for (const mode of ['create', 'edit']) test(`真实列表 ${mode}：加载配置时已打开，关闭立即释放入口，下一次重新解析发布表单`, async ({ page }) => {
  const first = deferred(), entered = deferred(); let reads = 0
  const state = await setup(page, { list: true, config: async () => { reads++; if (reads === 2) { entered.resolve(); await first.promise }; return form() } })
  await expect(page.getByText('记录 A', { exact: true })).toBeVisible()
  const button = mode === 'create' ? page.getByRole('button', { name: '新增数据', exact: true }) : page.getByRole('button', { name: '编辑', exact: true })
  await button.click(); await entered.promise
  await expect(dialog(page).locator('.el-skeleton')).toBeVisible()
  await close(page); await expect(button).toBeEnabled()
  await button.click(); await expect(amount(page)).toBeVisible()
  const response = page.waitForResponse(response => response.url().includes('/new-data/')); first.resolve(); await response
  await expect(amount(page)).toBeVisible(); expect(reads).toBe(3)
  const body = state.reads.find(item => item.endpoint.includes('/FORM_OPEN/')).body
  expect(body.listKey).toBe('tests'); expect(body.configHash).toBe('hash-1')
  if (mode === 'edit') expect(state.reads.filter(item => item.endpoint.includes('/detail/'))).toHaveLength(1)
  clean(state)
})

test('多页签仅执行一次表单初始化，相同字典与选项读取合并，切换不覆盖输入', async ({ page }) => {
  const optionsGate = deferred(), optionEntered = deferred()
  const category = { id: 'category', fieldCode: 'category', fieldName: '分类', fieldType: 'STRING', componentType: 'select', dictType: 'kinds', dataSourceBindings: { FIELD_OPTIONS: { extensionId: 'kinds' } } }
  const state = await setup(page, { config: () => form({ fields: [field, category], dataSourceBindings: { FORM_INIT: { extensionId: 'init' } }, nodes: [
    { id: 'amount-node', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'amount', props: field, orderKey: 100 },
    { id: 'tabs', nodeType: 'TAB_SET', parentId: '', orderKey: 200 },
    { id: 'extra', nodeType: 'TAB', parentId: 'tabs', props: { label: '补充信息' } },
    { id: 'kind-node', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'category', parentId: 'extra', props: category }
  ] }), extension: async body => {
    if (body.bindingCode === 'FORM_INIT') return { amount: 31 }
    optionEntered.resolve(); await optionsGate.promise; return [{ label: '类别 A', value: 'A' }]
  } })
  await start(page); await expect(amount(page)).toHaveValue('31.00'); await optionEntered.promise
  await amount(page).fill('42')
  await dialog(page).getByRole('tab', { name: '补充信息', exact: true }).click()
  await dialog(page).getByRole('tab', { name: '基本信息', exact: true }).click()
  await expect(amount(page)).toHaveValue('42.00')
  expect(state.reads.filter(item => item.endpoint.includes('/extensions/') && item.body.bindingCode === 'FORM_INIT')).toHaveLength(1)
  expect(state.reads.filter(item => item.endpoint.includes('/dict/'))).toHaveLength(1)
  const calls = state.reads.filter(item => item.endpoint.includes('/extensions/') && item.body.bindingCode === 'FIELD_OPTIONS')
  expect(calls).toHaveLength(1)
  optionsGate.resolve(); clean(state)
})
