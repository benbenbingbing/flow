import { test, expect } from '@playwright/test'
import { DEFAULT_MOBILE_THEME } from '@flow/workflow-core/mobile-theme'

const field = (id, parentId, label, extra = {}) => ({ id, parentId, nodeKey: id, nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: id, props: { fieldCode: id, label, fieldType: 'STRING', componentType: 'input', ...extra } })
const simpleForm = () => ({ id: 'form-test', runtimeReleaseId: 'release-pinned', runtimeReleaseVersion: 2, releaseResolutionToken: 'signed-test-only', fields: [], nodes: [field('name', '', '事项名称')] })

/** 所有 API 请求在浏览器内截获；审批写入仅记录到测试数组，绝不请求真实业务服务。 */
async function fixture(page, { actions, eventResult, theme = () => DEFAULT_MOBILE_THEME, form = simpleForm(), record = { id: 'record-test', name: '测试流程', entityCode: 'test' }, preview, complete, progress = {}, candidates = [], processOperations = { withdraw: false }, operations = { approve: true, reject: true, manualCc: false }, rejected = { canResubmit: false } } = {}) {
  const writes = [], requests = [], unexpected = [], errors = []
  page.on('pageerror', error => errors.push(error.message))
  const row = { id: 'record-test', taskId: 'task-test', processInstanceId: 'instance-test', dataName: record.name, processName: '测试流程', startUserName: '测试用户', assigneeName: '另一位办理人', entityCode: 'test', entityStatus: 'FINANCE_REVIEW', entityStatusText: '财务复核中', status: 'RUNNING', statusText: '运行中' }
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), endpoint = url.pathname.replace('/api', '')
    const body = request.method() === 'POST' ? request.postDataJSON() : null
    requests.push({ endpoint, body })
    let data
    if (['/auth/refresh', '/auth/login'].includes(endpoint)) data = { token: 'fixture-token', tokenExpiresAt: new Date(Date.now() + 3600000).toISOString(), username: 'fixture' }
    else if (endpoint === '/auth/current') data = { id: 'test-user', username: 'fixture', nickname: '测试用户' }
    else if (endpoint === '/auth/permissions') data = ['*']
    else if (endpoint === '/system/mobile-theme') data = theme()
    else if (endpoint === '/process-task/statistics') data = { todoCount: 1, unreadCcCount: 1 }
    else if (['/process-task/todo', '/process-task/done', '/process-instance/my-started', '/process-cc/my-cc'].includes(endpoint)) data = { records: [row], total: 1 }
    else if (endpoint === '/process-task/detail/task-test') data = { processTask: { taskId: 'task-test', status: 'todo', entityCode: 'test' } }
    else if (endpoint === '/process-instance/instance-test/progress') data = { status: 'RUNNING', processName: '测试流程', entityData: record, formConfig: form, activeNodes: ['review'], ...progress, approvalConfig: { enabled: true, options: [{ value: 'approve', label: '通过' }, { value: 'reject', label: '驳回' }] } }
    else if (endpoint === '/entity/code/test') data = { id: 'entity-test', entityCode: 'test', fields: [] }
    else if (endpoint.startsWith('/ui-runtime/events/')) {
      if (endpoint.includes('/FORM_BUTTON_CLICK/')) { writes.push({ endpoint, body }); data = typeof eventResult === 'function' ? await eventResult(body) : eventResult || { effects: [] } }
      else data = { effects: [] }
    }
    else if (endpoint === '/process-instance/instance-test/operations') data = typeof processOperations === 'function' ? processOperations() : processOperations
    else if (endpoint.endsWith('/next-approver-options')) data = { records: candidates, total: candidates.length }
    else if (endpoint === '/process-rollback/rejected-status/instance-test') data = rejected
    else if (endpoint === '/entity-selector/USER') data = { records: [{ username: 'reviewer', nickname: '测试办理人' }], total: 1 }
    else if (endpoint === '/process-rollback/resubmit/instance-test') { writes.push({ endpoint, body }); data = {} }
    else if (endpoint === '/process-task/withdraw') { writes.push({ endpoint, body }); data = {} }
    else if (endpoint === '/process-task/history/instance-test') data = []
    else if (endpoint === '/tasks/task-test/operations') data = operations
    else if (endpoint === '/ui-runtime/form-actions/resolve') data = actions || [{ key: 'submitApproval', label: '提交审批', type: 'built-in', placement: 'FOOTER', visible: true, enabled: true }]
    else if (endpoint.endsWith('/next-approval-preview')) data = preview ? await preview(body) : { status: 'READY', scopeKey: 'scope-test', nextNodes: [] }
    else if (endpoint === '/process-task/complete') {
      writes.push({ body, trace: request.headers()['x-trace-id'] })
      if (complete) return complete(route, body)
      await new Promise(resolve => setTimeout(resolve, 150)); data = {}
    } else if (endpoint.startsWith('/process-cc/read/')) data = {}
    else { unexpected.push(endpoint); return route.fulfill({ status: 500, json: { code: 500, message: `未声明的测试接口：${endpoint}` } }) }
    await route.fulfill({ status: 200, json: { code: 200, data } })
  })
  return { writes, requests, unexpected, errors }
}

async function detail(page, kind = 'todo') {
  await page.goto(`process/instance-test?kind=${kind}&taskId=task-test`)
  await expect(page.getByRole('tabpanel').first()).toBeVisible()
}
async function submit(page) {
  await page.getByRole('button', { name: '提交审批', exact: true }).click()
  const confirm = page.getByRole('button', { name: '确认提交', exact: true })
  await expect(confirm).toBeEnabled(); await confirm.click()
}

test('表单停留超过五分钟后编辑和提交自动续期，保留草稿与原发布版本', async ({ page }) => {
  const start = Date.now()
  const makeToken = expiresAt => `${Buffer.from(JSON.stringify({ expiresAt: Math.floor(expiresAt / 1000), purpose: 'ACTIVE_TASK', taskId: 'task-test', processInstanceId: 'instance-test', nodeId: 'review' })).toString('base64url')}.test-signature`
  const initialToken = makeToken(start + 300_000)
  const form = { ...simpleForm(), releaseResolutionToken: initialToken, nodes: [field('name', '', '事项名称'), field('rich', '', '我是富文本', { componentType: 'rich_text', fieldType: 'RICH_TEXT' })] }
  const state = await fixture(page, { form, record: { id: 'record-test', name: '服务端原名称', rich: '<p>服务端原内容</p>' } })
  await page.clock.setFixedTime(start)
  await detail(page)
  const name = page.getByPlaceholder('请输入事项名称'), rich = page.getByRole('textbox', { name: '我是富文本', exact: true })
  await name.fill('未提交的名称'); await rich.click()
  await expect.poll(() => state.requests.some(item => item.endpoint.includes('/events/FIELD_CHANGE/') && item.body?.targetKey === 'name')).toBe(true)

  // 模拟手机休眠后恢复；不依赖后台定时器继续运行，也不重新加载整份表单草稿。
  await page.clock.setFixedTime(start + 360_000)
  form.releaseResolutionToken = makeToken(start + 660_000)
  await rich.fill('休眠后继续编辑的富文本'); await name.click()
  await expect.poll(() => state.requests.filter(item => item.endpoint.includes('/events/FIELD_CHANGE/') && item.body?.targetKey === 'rich').at(-1)?.body?.releaseResolutionToken).toBe(form.releaseResolutionToken)
  await expect(name).toHaveValue('未提交的名称'); await expect(rich).toHaveText('休眠后继续编辑的富文本')
  expect(state.requests.filter(item => item.endpoint.endsWith('/progress'))).toHaveLength(2)

  // 再次停留至到期，直接提交也必须更新令牌，并且只发送一次审批写请求。
  await page.clock.setFixedTime(start + 720_000)
  form.releaseResolutionToken = makeToken(start + 1_020_000)
  await submit(page); await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0].body).toMatchObject({ formReleaseId: 'release-pinned', formReleaseVersion: 2, formReleaseResolutionToken: form.releaseResolutionToken, formData: { name: '未提交的名称', rich: '<p>休眠后继续编辑的富文本</p>' } })
  expect(state.requests.filter(item => item.endpoint.endsWith('/progress'))).toHaveLength(3)
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('代码表单选、多选加载候选项，回显名称并按编码提交', async ({ page }) => {
  const form = { ...simpleForm(), entityId: 'entity-test', fields: [
    { id: 'single', fieldCode: 'single', fieldType: 'SELECT', componentType: 'select' },
    { id: 'multiple', fieldCode: 'multiple', fieldType: 'MULTI_SELECT', componentType: 'select_multiple' }
  ], nodes: [field('single', '', '我是选择', { fieldType: 'SELECT', componentType: 'select' }), field('multiple', '', '我是多选', { fieldType: 'MULTI_SELECT', componentType: 'select_multiple' })] }
  const record = { id: 'record-test', name: '代码表测试', single: 'first', multiple: ['first'] }
  const state = await fixture(page, { form, record })
  await page.route('**/api/entity-form/entity/entity-test/fields', route => route.fulfill({ json: { code: 200, data: [
    { id: 'single-meta', fieldCode: 'single', dictType: 'single-dict' },
    { id: 'multiple-meta', fieldCode: 'multiple', dictType: 'multiple-dict' }
  ] } }))
  await page.route('**/api/system/dict/item/tree/code/*', route => {
    const prefix = route.request().url().endsWith('multiple-dict') ? '多选' : ''
    return route.fulfill({ json: { code: 200, data: [{ itemCode: 'first', itemLabel: `${prefix}第一个`, status: '0', children: [{ itemCode: 'second', itemLabel: `${prefix}第二个`, status: '0' }] }, { itemCode: 'disabled', itemLabel: '停用项', status: '1' }] } })
  })
  await detail(page)
  const single = page.getByPlaceholder('请选择我是选择'), multiple = page.getByPlaceholder('请选择我是多选')
  await expect(single).toHaveValue('第一个'); await expect(multiple).toHaveValue('多选第一个')
  await single.click()
  const popup = page.locator('.van-popup:visible')
  await expect(popup.locator('.van-picker-column__item')).toHaveText(['第一个', '第二个', '停用项'])
  await expect(popup.locator('.van-picker-column__item--disabled')).toHaveText('停用项')
  await popup.getByText('第二个', { exact: true }).click()
  await popup.getByRole('button', { name: '确认', exact: true }).click()
  await expect(single).toHaveValue('第二个')
  await multiple.click()
  await expect(popup.getByRole('checkbox').first()).toHaveAttribute('aria-checked', 'true')
  await popup.getByRole('checkbox').nth(1).click()
  await expect(popup.getByRole('checkbox').nth(1)).toHaveAttribute('aria-checked', 'true')
  await popup.getByText('停用项', { exact: true }).click()
  await expect(popup.getByRole('checkbox').last()).toHaveAttribute('aria-checked', 'false')
  await popup.getByText('确定', { exact: true }).click()
  await expect(multiple).toHaveValue('多选第一个、多选第二个')
  await multiple.click(); await popup.getByText('多选第一个', { exact: true }).click(); await popup.getByText('取消', { exact: true }).click()
  await expect(multiple).toHaveValue('多选第一个、多选第二个')
  await submit(page); await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes[0].body.formData).toMatchObject({ single: 'second', multiple: ['first', 'second'] })
  await detail(page, 'done')
  await expect(page.locator('.readonly-value')).toHaveText(['第一个', '多选第一个'])
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('流程表单未返回实体 ID 时按实体编码补齐单选、多选代码表配置', async ({ page }) => {
  // 对齐进度接口：只有 formId/formReleaseId，代码表在实体字段元数据中，不在表单字段中。
  const fields = [
    { id: 'form-single', fieldId: 'entity-single', fieldCode: 'single', fieldLabel: '我是选择', fieldType: 'SELECT', componentType: 'select', optionsJson: '[]' },
    { id: 'form-multiple', fieldId: 'entity-multiple', fieldCode: 'multiple', fieldLabel: '我是多选', fieldType: 'MULTI_SELECT', componentType: 'select_multiple', optionsJson: '[]' }
  ]
  const form = { formId: 'form-test', formReleaseId: 'release-pinned', formReleaseVersion: 2, releaseResolutionToken: 'signed-test-only', fields, nodes: fields.map(item => ({
    id: `node-${item.fieldCode}`, parentId: '', nodeKey: item.fieldCode, nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: item.fieldId,
    propsDocument: JSON.stringify({ fieldCode: item.fieldCode, label: item.fieldLabel, fieldType: item.fieldType, componentType: item.componentType })
  })) }
  const state = await fixture(page, { form, record: { id: 'record-test', name: '代码表测试', entityCode: 'ALL_ENTITY', single: 'first', multiple: ['first'] } })
  let metadataRequests = 0, dictionaryRequests = 0
  await page.route('**/api/entity/code/ALL_ENTITY', route => {
    metadataRequests++
    return route.fulfill({ json: { code: 200, data: { id: 'entity-test', fields: fields.map(item => ({ id: item.fieldId, fieldCode: item.fieldCode, dictType: `${item.fieldCode}-dict` })) } } })
  })
  await page.route('**/api/system/dict/item/tree/code/*', route => {
    dictionaryRequests++
    const prefix = route.request().url().endsWith('multiple-dict') ? '多选' : ''
    return route.fulfill({ json: { code: 200, data: [{ itemCode: 'first', itemLabel: `${prefix}第一个`, status: '0' }, { itemCode: 'second', itemLabel: `${prefix}第二个`, status: '0' }] } })
  })
  await detail(page)
  const single = page.getByPlaceholder('请选择我是选择'), multiple = page.getByPlaceholder('请选择我是多选')
  await expect(single).toHaveValue('第一个'); await expect(multiple).toHaveValue('多选第一个')
  expect(metadataRequests).toBe(1); expect(dictionaryRequests).toBe(2)
  await single.click()
  const popup = page.locator('.van-popup:visible')
  await expect(popup.locator('.van-picker-column__item')).toHaveText(['第一个', '第二个'])
  await popup.getByText('第二个', { exact: true }).click(); await popup.getByRole('button', { name: '确认', exact: true }).click()
  await expect(single).toHaveValue('第二个')
  await multiple.click()
  await expect(popup.getByRole('checkbox').first()).toHaveAttribute('aria-checked', 'true')
  await popup.getByText('多选第二个', { exact: true }).click(); await popup.getByText('确定', { exact: true }).click()
  await expect(multiple).toHaveValue('多选第一个、多选第二个')
  await submit(page); await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes[0].body).toMatchObject({ formReleaseId: 'release-pinned', formReleaseVersion: 2, formData: { single: 'second', multiple: ['first', 'second'] } })
  await detail(page, 'done')
  await expect(page.locator('.readonly-value')).toHaveText(['第一个', '多选第一个'])
  expect(metadataRequests).toBe(2); expect(dictionaryRequests).toBe(4)
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('旧内嵌单选、多选选项兼容三种配置来源', async ({ page }) => {
  const options = [{ label: '第一个', value: 0 }, { label: '第二个', value: 2 }]
  const sources = [{ componentProps: JSON.stringify({ options }) }, { optionsJson: JSON.stringify(options) }, { options }]
  const fields = sources.flatMap((source, index) => [
    { id: `single-${index}`, fieldCode: `single-${index}`, fieldName: `单选${index}`, componentType: 'select', ...source },
    { id: `multi-${index}`, fieldCode: `multi-${index}`, fieldName: `多选${index}`, componentType: 'select_multiple', ...source }
  ])
  const record = Object.fromEntries(fields.map(item => [item.fieldCode, item.componentType === 'select' ? 0 : [0]]))
  const state = await fixture(page, { form: { ...simpleForm(), fields, nodes: [] }, record })
  await detail(page)
  for (let index = 0; index < sources.length; index++) {
    const single = page.getByPlaceholder(`请选择单选${index}`), multiple = page.getByPlaceholder(`请选择多选${index}`)
    await expect(single).toHaveValue('第一个'); await expect(multiple).toHaveValue('第一个')
    await single.click()
    const popup = page.locator('.van-popup:visible')
    await expect(popup.locator('.van-picker-column__item')).toHaveText(['第一个', '第二个'])
    await popup.getByRole('button', { name: '取消', exact: true }).click()
    await multiple.click()
    await expect(popup.getByRole('checkbox').first()).toHaveAttribute('aria-checked', 'true')
    await popup.getByText('第二个', { exact: true }).click(); await popup.getByText('确定', { exact: true }).click()
    await expect(multiple).toHaveValue('第一个、第二个')
  }
  await submit(page); await expect(page).toHaveURL(/inbox\/todo/)
  for (let index = 0; index < sources.length; index++) {
    expect(state.writes[0].body.formData[`single-${index}`]).toBe(0)
    expect(state.writes[0].body.formData[`multi-${index}`]).toEqual([0, 2])
  }
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('代码表加载失败可重试；空候选项确认不清除已保存值', async ({ page }) => {
  const form = { ...simpleForm(), fields: [{ id: 'choice', fieldCode: 'choice', fieldName: '需重试的选择', componentType: 'select', dictType: 'retry-dict' }], nodes: [] }
  const state = await fixture(page, { form, record: { id: 'record-test', choice: 'first' } })
  let attempts = 0
  await page.route('**/api/system/dict/item/tree/code/retry-dict', route => {
    attempts++
    return route.fulfill(attempts === 1
      ? { status: 503, json: { code: 503, message: '代码表暂时不可用' } }
      : { json: { code: 200, data: [{ itemCode: 'first', itemLabel: '第一个', status: '0' }] } })
  })
  await detail(page)
  const choice = page.getByPlaceholder('请选择需重试的选择')
  await expect(page.getByRole('alert')).toContainText('代码表暂时不可用')
  await choice.click(); await page.locator('.van-popup:visible').getByRole('button', { name: '确认', exact: true }).click()
  await expect(choice).toHaveValue('first')
  await page.getByRole('alert').getByRole('button', { name: '重试', exact: true }).click()
  await expect(choice).toHaveValue('第一个'); await expect(page.getByRole('alert')).toHaveCount(0)
  expect(attempts).toBe(2)
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('360/390/430 宽度：紧凑列表、用户菜单和详情均无横向溢出', async ({ page }) => {
  const state = await fixture(page, { record: { id: 'record-test', name: '包含较长事项标题的移动端任务：跨部门项目验收与财务复核', entityCode: 'test' } })
  for (const width of [360, 390, 430]) {
    await page.setViewportSize({ width, height: 844 }); await page.goto('inbox/todo')
    await expect(page.locator('.mobile-task-card')).toHaveCount(1)
    expect(await page.locator('.inbox-header').evaluate(node => node.getBoundingClientRect().height)).toBe(56)
    for (const kind of ['todo', 'done', 'started', 'cc']) {
      if (kind !== 'todo') await page.goto(`inbox/${kind}`)
      await expect(page.locator('.mobile-task-card')).toHaveCount(1)
      if (kind === 'todo' || kind === 'done') {
        await expect(page.locator('.card-person')).toHaveText('测测试用户')
        await expect(page.locator('.mobile-task-card')).not.toContainText('另一位办理人')
      }
      if (kind === 'todo') await expect(page.locator('.card-status')).toHaveText('财务复核中')
      if (kind === 'started') await expect(page.locator('.card-status')).toHaveText(['财务复核中', '运行中'])
      // 子组件根节点会继承父级 scope，防止工具栏按钮的固定高度再次裁切任务正文。
      expect(await page.locator('.mobile-task-card').evaluate(node => {
        const outer = node.getBoundingClientRect()
        return [...node.querySelectorAll('h2, .card-context, .card-footer')].every(child => {
          const rect = child.getBoundingClientRect()
          return rect.top >= outer.top && rect.bottom <= outer.bottom && rect.right <= outer.right
        })
      })).toBe(true)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    }
    await page.screenshot({ path: `../.codex-artifacts/frontend-refactor/mobile-inbox-${width}.png`, fullPage: true })
    await page.getByRole('button', { name: '用户菜单' }).click()
    await expect(page.getByText('修改密码', { exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: '取消', exact: true }).click()
  }
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('四个页签滚动列表时，搜索工具栏与时间数量说明保持在顶部且可操作', async ({ page }) => {
  const state = await fixture(page)
  // 足够长的列表才会发生真实滚动，避免单条数据让吸顶检查误通过。
  const rows = Array.from({ length: 12 }, (_, index) => ({ id: `sticky-${index}`, taskId: `task-${index}`, processInstanceId: `instance-${index}`, dataName: `滚动测试事项 ${index + 1}`, processName: '测试流程', startUserName: '测试用户' }))
  await page.route(/\/api\/(process-task\/(todo|done)|process-instance\/my-started|process-cc\/my-cc)(\?|$)/, route => route.fulfill({ json: { code: 200, data: { records: rows, total: rows.length } } }))
  for (const kind of ['todo', 'done', 'started', 'cc']) {
    await page.goto(`inbox/${kind}`)
    await expect(page.locator('.mobile-task-card')).toHaveCount(rows.length)
    const toolbar = page.locator('.inbox-header'), summary = page.locator('.inbox-section'), firstCard = page.locator('.mobile-task-card').first()
    const before = { toolbar: await toolbar.boundingBox(), summary: await summary.boundingBox(), card: await firstCard.boundingBox() }
    await page.evaluate(() => window.scrollTo(0, 360))
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(360)
    expect((await toolbar.boundingBox()).y).toBe(before.toolbar.y)
    expect((await summary.boundingBox()).y).toBe(before.summary.y)
    expect((await firstCard.boundingBox()).y).toBe(before.card.y - 360)
    await expect(summary).toContainText('共 12 条')
    await page.getByRole('button', { name: '筛选', exact: true }).click()
    await expect(page.getByRole('heading', { name: '筛选流程' })).toBeVisible()
    await page.getByRole('button', { name: '确定', exact: true }).click()
    await page.getByRole('button', { name: '用户菜单' }).click()
    await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '取消', exact: true }).click()
  }
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('待办显示到达时间，已办显示办理时间；缺失时不混用其他时间', async ({ page }) => {
  const state = await fixture(page)
  const row = { id: 'time-test', taskId: 'time-task', dataName: '不同时间的任务', createTime: '2026-09-22T10:15:00', endTime: '2026-09-23T17:45:00', startTime: '2026-09-21T07:00:00' }
  await page.route(/\/api\/process-task\/(todo|done)(\?|$)/, route => route.fulfill({ json: { code: 200, data: { records: [row], total: 1 } } }))
  await page.goto('inbox/todo')
  await expect(page.locator('.card-footer time')).toHaveText('09/22 10:15')
  await page.goto('inbox/done')
  await expect(page.locator('.card-footer time')).toHaveText('09/23 17:45')

  row.endTime = null
  await page.reload()
  await expect(page.locator('.card-footer time')).toHaveText('—')
  await expect(page.locator('.inbox-date-group')).toHaveCount(0)
  row.createTime = null
  await page.goto('inbox/todo')
  await expect(page.locator('.card-footer time')).toHaveText('—')
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('四个入口的详情与表单回显状态名称，提交保留编码，缺失时不使用流程状态', async ({ page }) => {
  const record = { id: 'record-test', name: '状态不同的流程', entityCode: 'test', status: 'PENDING', _statusText: '财务复核中', processStatus: 'RUNNING', reference: 'PENDING' }
  const form = { ...simpleForm(), nodes: [field('status', '', '状态', { readonly: true }), field('processStatus', '', '流程状态', { readonly: true }), field('reference', '', '参考编码', { readonly: true })] }
  const state = await fixture(page, { record, form })
  const statusField = page.locator('[data-field-code="status"] .readonly-value')
  for (const kind of ['todo', 'done', 'started', 'cc']) {
    await detail(page, kind)
    await expect(page.locator('.detail-summary .van-tag')).toHaveText('财务复核中')
    await expect(statusField).toHaveText('财务复核中')
    await expect(page.locator('[data-field-code="processStatus"] .readonly-value')).toHaveText('运行中')
    await expect(page.locator('[data-field-code="reference"] .readonly-value')).toHaveText('PENDING')
    await expect(page.locator('.detail-summary')).not.toContainText('运行中')
  }
  await detail(page)
  await submit(page); await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0].body.formData).toMatchObject({ status: 'PENDING', processStatus: 'RUNNING', reference: 'PENDING' })
  record.status = 'FINANCE_REVIEW'
  await detail(page, 'done')
  await expect(statusField).toHaveText('财务复核中')
  record.status = 'PENDING'
  delete record._statusText
  await detail(page, 'done')
  await expect(page.locator('.detail-summary .van-tag')).toHaveText('处理中')
  await expect(statusField).toHaveText('处理中')
  delete record.status
  await detail(page, 'done')
  await expect(page.locator('.detail-summary .van-tag')).toHaveText('—')
  await expect(statusField).toHaveText('—')
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('移动端水平标签统一左对齐，顶部配置与富文本附件布局保持不变', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 844 })
  const form = { ...simpleForm(), layoutType: 'grid', nodes: [
    field('text', '', '文本'), field('notes', '', '多行文本', { componentType: 'textarea' }),
    field('amount', '', '数字', { componentType: 'number', fieldType: 'NUMBER' }),
    field('choice', '', '选择', { componentType: 'select', options: [{ value: 'first', label: '第一个' }] }),
    field('date', '', '日期', { componentType: 'date' }), field('enabled', '', '布尔', { componentType: 'switch' }),
    field('rich', '', '富文本', { componentType: 'rich_text' }),
    field('image', '', '图片', { componentType: 'image' }), field('file', '', '文件', { componentType: 'file' })
  ] }
  const record = { id: 'record-test', text: '文本内容', notes: '这是一段用于验证长文本不会自行改变标签位置的内容。'.repeat(3), amount: 12, choice: 'first', date: '2026-09-23', enabled: true, rich: '<p>富文本内容</p>', image: [], file: [] }
  const state = await fixture(page, { form, record })
  for (const position of ['top', 'left', 'right']) {
    // 发布配置兼容对象和 JSON；同一份配置同时约束可编辑字段及只读回显。
    form.viewConfig = position === 'left' ? { labelPosition: position } : JSON.stringify({ labelPosition: position })
    for (const kind of ['todo', 'done']) {
      await detail(page, kind)
      for (const code of ['text', 'notes', 'amount', 'choice', 'date', 'enabled']) {
        const row = page.locator(`[data-field-code="${code}"]`)
        const label = row.locator(kind === 'todo' ? '.van-field__label' : '.readonly-label')
        const value = row.locator(kind === 'todo' ? '.van-field__value' : '.readonly-value')
        await expect(label).toHaveCSS('text-align', /^(left|start)$/)
        const labelBox = await label.boundingBox(), valueBox = await value.boundingBox()
        expect(labelBox.x).toBe((await row.boundingBox()).x)
        expect(valueBox.width).toBeGreaterThan(40)
        if (position === 'top') expect(valueBox.y).toBeGreaterThanOrEqual(labelBox.y + labelBox.height)
        else expect(valueBox.x).toBeGreaterThanOrEqual(labelBox.x + labelBox.width)
      }
      for (const code of ['rich', 'image', 'file']) {
        const row = page.locator(`[data-field-code="${code}"]`)
        const label = row.locator(kind === 'done' ? '.readonly-label' : code === 'rich' ? '.rich-label' : '.file-label')
        const value = row.locator(kind === 'done' ? code === 'rich' ? '.readonly-rich' : '.readonly-files' : code === 'rich' ? '.rich-toolbar' : '.van-uploader')
        await expect(label).toHaveCSS('text-align', /^(left|start)$/)
        const labelBox = await label.boundingBox(), valueBox = await value.boundingBox()
        expect(valueBox.y).toBeGreaterThanOrEqual(labelBox.y + labelBox.height)
      }
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(360)
    }
  }
  delete form.viewConfig
  form.layoutType = 'vertical'
  await detail(page)
  await expect(page.locator('[data-field-code="text"] .van-field__label--top')).toBeVisible()
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('第一层 Tab 与流程页签并列，嵌套 Tab 保留折叠；跨页签校验保留填写内容和发布坐标', async ({ page }) => {
  const form = { ...simpleForm(), nodes: [{ id: 'tabs', nodeType: 'TAB_SET' }, { id: 'first', parentId: 'tabs', nodeType: 'TAB', props: { label: '主要信息' } }, { id: 'second', parentId: 'tabs', nodeType: 'TAB', props: { label: '补充信息' } }, { id: 'nested', parentId: 'second', nodeType: 'TAB_SET' }, { id: 'notes', parentId: 'nested', nodeType: 'TAB', props: { label: '备注分组' } }, { id: 'checks', parentId: 'nested', nodeType: 'TAB', props: { label: '校验分组' } }, field('name', 'first', '事项名称'), field('note', 'notes', '备注'), field('required', 'checks', '补充说明', { required: true })] }
  const state = await fixture(page, { form })
  await detail(page)
  await expect(page.getByRole('tab')).toHaveText(['主要信息', '补充信息', '流程进度', '审批历史'])
  await expect(page.getByRole('tab', { name: '主要信息', exact: true })).toHaveAttribute('aria-selected', 'true')
  await page.getByPlaceholder('请输入事项名称').fill('跨页签保留的名称')
  await page.getByRole('tab', { name: '补充信息', exact: true }).click()
  await expect(page.locator('.mobile-form-group .van-collapse-item__title')).toHaveText(['备注分组', '校验分组'])
  await expect(page.getByPlaceholder('请输入补充说明')).not.toBeVisible()
  await page.getByRole('tab', { name: '审批历史', exact: true }).click()
  await submit(page)
  await expect(page.getByRole('tab', { name: '补充信息', exact: true })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByPlaceholder('请输入补充说明')).toBeVisible()
  await expect(page.getByRole('alert').filter({ hasText: '请输入补充说明' })).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await page.getByPlaceholder('请输入补充说明').fill('已核对')
  await page.getByRole('tab', { name: '主要信息', exact: true }).click()
  await expect(page.getByPlaceholder('请输入事项名称')).toHaveValue('跨页签保留的名称')
  await submit(page)
  await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0].body).toMatchObject({ formReleaseId: 'release-pinned', formReleaseVersion: 2, formReleaseResolutionToken: 'signed-test-only', formData: { name: '跨页签保留的名称', required: '已核对' } })
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('第一层 Tab 使用发布的顺序和默认项；混排普通字段时仍保留基本信息', async ({ page }) => {
  const form = { ...simpleForm(), nodes: [{ id: 'tabs', nodeType: 'TAB_SET', props: { defaultActiveTabKey: 'tab-a' } }, { id: 'first', nodeKey: 'tab-a', parentId: 'tabs', nodeType: 'TAB', orderKey: 2, props: { label: '默认资料' } }, { id: 'second', parentId: 'tabs', nodeType: 'TAB', orderKey: 1, props: { label: '前置资料' } }, field('name', 'first', '事项名称'), field('extra', 'second', '附加信息')] }
  const state = await fixture(page, { form })
  await detail(page)
  await expect(page.getByRole('tab')).toHaveText(['前置资料', '默认资料', '流程进度', '审批历史'])
  await expect(page.getByRole('tab', { name: '默认资料', exact: true })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByPlaceholder('请输入事项名称')).toBeVisible()
  form.nodes.push(field('base', '', '基本字段'))
  await page.reload()
  await expect(page.getByRole('tab')).toHaveText(['基本信息', '前置资料', '默认资料', '流程进度', '审批历史'])
  await expect(page.getByPlaceholder('请输入基本字段')).toBeVisible()
  await expect(page.getByPlaceholder('请输入事项名称')).not.toBeVisible()
  await page.getByRole('tab', { name: '默认资料', exact: true }).click()
  await expect(page.getByPlaceholder('请输入事项名称')).toBeVisible()
  await expect(page.getByPlaceholder('请输入基本字段')).not.toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('服务端字段错误定位到第一层 Tab 并保留其它页签草稿', async ({ page }) => {
  const form = { ...simpleForm(), nodes: [{ id: 'tabs', nodeType: 'TAB_SET' }, { id: 'first', parentId: 'tabs', nodeType: 'TAB', props: { label: '主要信息' } }, { id: 'second', parentId: 'tabs', nodeType: 'TAB', props: { label: '补充信息' } }, field('name', 'first', '事项名称'), field('required', 'second', '补充说明')] }
  const state = await fixture(page, { form, complete: route => route.fulfill({ status: 400, json: { code: 400, message: '请修正表单', data: { fieldErrors: [{ fieldCode: 'required', message: '补充说明需要复核' }] } } }) })
  await detail(page)
  await page.getByPlaceholder('请输入事项名称').fill('服务端校验前的草稿')
  await submit(page)
  await expect(page.getByRole('tab', { name: '补充信息', exact: true })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('alert').filter({ hasText: '补充说明需要复核' })).toBeVisible()
  await page.getByRole('tab', { name: '主要信息', exact: true }).click()
  await expect(page.getByPlaceholder('请输入事项名称')).toHaveValue('服务端校验前的草稿')
  expect(state.writes).toHaveLength(1)
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('未打开的子表行仍校验并自动进入错误行', async ({ page }) => {
  const form = { ...simpleForm(), nodes: [field('name', '', '事项名称'), { id: 'items', nodeType: 'SUB_FORM', nodeKey: 'items', bindingRef: 'items', props: { label: '明细', fieldCode: 'items' } }, field('note', 'items', '明细说明', { required: true })] }
  const state = await fixture(page, { form, record: { id: 'record-test', name: '子表测试', items: [{ note: '' }, { note: '第二行' }] } })
  await detail(page); await expect(page.getByText('明细 1', { exact: true }).first()).toBeVisible()
  await submit(page)
  const editor = page.locator('.subform-editor:visible')
  await expect(editor.getByPlaceholder('请输入明细说明')).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await editor.getByPlaceholder('请输入明细说明').fill('第一行修复')
  await editor.getByText('返回', { exact: true }).click()
  await submit(page); await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes[0].body.formData.items.map(item => item.note)).toEqual(['第一行修复', '第二行'])
  expect(state.errors).toEqual([])
})

test('任务竞争保留输入且关闭重复办理入口', async ({ page }) => {
  const state = await fixture(page, { complete: route => route.fulfill({ status: 409, json: { code: 409, errorCode: 'TASK_ALREADY_COMPLETED', message: '任务已处理' } }) })
  await detail(page); await page.getByPlaceholder('请输入事项名称').fill('仍需保留的草稿')
  await submit(page)
  await expect(page.getByText(/你填写的内容仍保留/)).toBeVisible()
  await expect(page.getByRole('button', { name: '提交审批', exact: true })).toHaveCount(0)
  await expect(page.locator('.mobile-form-tab-content:visible')).toContainText('仍需保留的草稿')
  expect(state.writes).toHaveLength(1); expect(state.errors).toEqual([])
})

test('查看模式展示普通文字；未知编辑扩展阻止提交', async ({ page }) => {
  const form = { ...simpleForm(), fields: [{ id: 'unknown', fieldCode: 'unknown', fieldName: '特殊字段', componentType: 'uninstalled_business_field', isRequired: true }], nodes: [] }
  const state = await fixture(page, { form })
  await detail(page, 'done'); await expect(page.getByRole('button', { name: '提交审批', exact: true })).toHaveCount(0)
  await detail(page); await submit(page)
  await expect(page.getByRole('alert').filter({ hasText: '此项暂不支持手机办理' })).toBeVisible()
  expect(state.writes).toHaveLength(0); expect(state.errors).toEqual([])
})

test('项目自定义表单使用移动实现并保留计算字段', async ({ page }) => {
  const form = { ...simpleForm(), customComponent: 'DemoProjectForm', fields: [], nodes: [] }
  const state = await fixture(page, { form, record: { id: 'record-test', name: '项目测试', projectName: '项目测试', code: 'TEST-001', ownerName: '测试用户', budget: 0, riskScore: 20 } })
  await detail(page)
  await page.getByPlaceholder('请输入项目名称').fill('移动端项目')
  await submit(page); await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes[0].body.formData).toMatchObject({ projectName: '移动端项目', name: '移动端项目', budget: 0 })
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})


test('切换审批结果静默查询下一审批人，空结果不改变弹层高度', async ({ page }) => {
  let deferPreview = false, resolvePreview
  const emptyPreview = { status: 'READY', scopeKey: 'scope-empty', nextNodes: [] }
  // 手动放行响应，确保真正检查了防抖及请求等待期间，而不是只看到快速返回后的界面。
  const state = await fixture(page, { preview: () => deferPreview ? new Promise(resolve => { resolvePreview = resolve }) : emptyPreview })
  await detail(page)
  await page.getByRole('button', { name: '提交审批', exact: true }).click()
  const confirm = page.getByRole('button', { name: '确认提交', exact: true })
  const popup = page.locator('.approval-popup')
  await expect(confirm).toBeEnabled()
  const initialHeight = (await popup.boundingBox()).height
  deferPreview = true
  for (const label of ['驳回', '通过']) {
    resolvePreview = undefined
    await page.getByRole('radio', { name: new RegExp(label) }).click()
    await expect(confirm).toBeDisabled()
    await expect.poll(() => typeof resolvePreview).toBe('function')
    await expect(popup.getByText('正在确认下一审批节点', { exact: true })).toHaveCount(0)
    await expect(popup.locator('.next-node')).toHaveCount(0)
    expect((await popup.boundingBox()).height).toBe(initialHeight)
    resolvePreview(emptyPreview)
    await expect(confirm).toBeEnabled()
    expect((await popup.boundingBox()).height).toBe(initialHeight)
  }
  resolvePreview = undefined
  await page.getByRole('radio', { name: /驳回/ }).click()
  await expect.poll(() => typeof resolvePreview).toBe('function')
  await expect(popup.locator('.next-node')).toHaveCount(0)
  resolvePreview({ status: 'READY', scopeKey: 'scope-review', nextNodes: [{ nodeId: 'review', nodeName: '补充复核', visible: true, editable: true, sourceType: 'SCOPE', assignmentMode: 'DIRECT', assignees: [] }] })
  await expect(popup.locator('.next-node')).toContainText('补充复核')
  await expect(confirm).toBeEnabled()
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('下一审批人候选受发布范围约束并携带当前表单提交', async ({ page }) => {
  const state = await fixture(page, {
    preview: () => ({ status: 'READY', scopeKey: 'scope-restricted', nextNodes: [{ nodeId: 'next', nodeName: '部门复核', visible: true, editable: true, sourceType: 'SCOPE', assignmentMode: 'DIRECT', assignees: [] }] }),
    candidates: [{ userKey: 'blocked', displayName: '停用用户', disabled: true }, { userKey: 'reviewer', displayName: '指定复核人' }]
  })
  await detail(page)
  await page.getByPlaceholder('请输入事项名称').fill('待复核的表单')
  await page.getByRole('button', { name: '提交审批', exact: true }).click()
  await page.getByText('部门复核', { exact: true }).click()
  await page.getByText('停用用户', { exact: true }).click()
  await expect(page.locator('.picker-selected .van-tag')).toHaveCount(0)
  await page.getByText('指定复核人', { exact: true }).click()
  await page.locator('.picker-shell').getByText('确定', { exact: true }).click()
  await page.getByRole('button', { name: '确认提交', exact: true }).click()
  await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.requests.find(item => item.endpoint.endsWith('/next-approver-options')).body).toMatchObject({ targetNodeId: 'next', scopeKey: 'scope-restricted', formData: { name: '待复核的表单' } })
  expect(state.writes[0].body).toMatchObject({ nextApprovalScopeKey: 'scope-restricted', nextApproverSelections: [{ nodeId: 'next', userKeys: ['reviewer'] }] })
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('我发起的无需任务办理权即可按实例能力撤回', async ({ page }) => {
  const state = await fixture(page, { processOperations: { withdraw: true, terminate: false } })
  await page.goto('process/instance-test?kind=started')
  await page.locator('.van-nav-bar__right').getByRole('button', { name: '更多操作', exact: true }).click()
  await expect(page.locator('.mobile-action-bar')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '撤回流程', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '撤回流程', exact: true }).click()
  await page.getByPlaceholder('请填写操作说明').fill('测试撤回')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page).toHaveURL(/inbox\/started/)
  expect(state.writes).toEqual([{ endpoint: '/process-task/withdraw', body: { processInstanceId: 'instance-test', reason: '测试撤回' } }])
  expect(state.requests.filter(item => item.endpoint === '/process-instance/instance-test/operations')).toHaveLength(2)
  expect(state.requests.some(item => item.endpoint.startsWith('/tasks/'))).toBe(false)
  expect(state.errors).toEqual([])
})


// 撤回与终止独立；已结束实例、未显式授权以及开窗后节点变化都必须拒绝。
for (const scenario of [
  { name: '当前节点关闭撤回', capabilities: { withdraw: false, terminate: true }, status: 'RUNNING' },
  { name: '缺失撤回权限', capabilities: { terminate: true }, status: 'RUNNING' },
  { name: '非布尔撤回权限', capabilities: { withdraw: 'true' }, status: 'RUNNING' },
  { name: '流程已经完成', capabilities: { withdraw: true }, status: 'COMPLETED' }
]) {
  test(`移动端撤回隐藏：${scenario.name}`, async ({ page }) => {
    const state = await fixture(page, { processOperations: scenario.capabilities, progress: { status: scenario.status } })
    await page.goto('process/instance-test?kind=started')
    await expect(page.getByRole('tabpanel').first()).toBeVisible()
    const more = page.getByRole('button', { name: '更多操作', exact: true })
    if (await more.count() && await more.isEnabled()) await more.click()
    await expect(page.getByRole('button', { name: '撤回流程', exact: true })).toHaveCount(0)
    expect(state.writes).toEqual([])
  })
}

test('移动端撤回确认时节点权限变化则阻止写入', async ({ page }) => {
  let allowed = true
  const state = await fixture(page, { processOperations: () => ({ withdraw: allowed, terminate: false }) })
  await page.goto('process/instance-test?kind=started')
  await page.getByRole('button', { name: '更多操作', exact: true }).click()
  await page.getByRole('button', { name: '撤回流程', exact: true }).click()
  allowed = false
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('当前流程已不允许撤回，请刷新后重试', { exact: true })).toBeVisible()
  expect(state.writes).toEqual([])
  expect(state.requests.filter(item => item.endpoint === '/process-instance/instance-test/operations')).toHaveLength(2)
})

test('驳回使用发布的动作选项；转办保持办理人协议', async ({ page }) => {
  const state = await fixture(page, { operations: { approve: true, reject: true, transfer: true } })
  await detail(page)
  await page.getByRole('button', { name: '提交审批', exact: true }).click()
  await page.getByRole('radio', { name: /驳回/ }).click()
  await page.getByRole('button', { name: '确认提交', exact: true }).click()
  await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes[0].body).toMatchObject({ action: 'reject', actionLabel: '驳回' })
  await detail(page)
  await expect(page.locator('.mobile-action-bar').getByRole('button')).toHaveText(['提交审批'])
  await page.locator('.van-nav-bar__right').getByRole('button', { name: '更多操作', exact: true }).click()
  await page.getByRole('button', { name: '转办', exact: true }).click()
  await page.getByText('办理人', { exact: true }).click()
  await page.getByText('测试办理人', { exact: true }).click()
  await page.locator('.picker-shell').getByText('确定', { exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes[1].body).toMatchObject({ action: 'transfer', transferTo: 'reviewer', taskId: 'task-test' })
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('发起人按服务端能力重新提交驳回表单', async ({ page }) => {
  const state = await fixture(page, { record: { id: 'record-test', name: '重新提交', submitterId: 'fixture' }, rejected: { canResubmit: true } })
  await page.goto('process/instance-test?kind=started')
  await page.getByPlaceholder('请输入事项名称').fill('已修正')
  await page.getByRole('button', { name: '重新提交', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page).toHaveURL(/inbox\/started/)
  expect(state.writes[0]).toMatchObject({ endpoint: '/process-rollback/resubmit/instance-test', body: { formData: { name: '已修正' } } })
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})


test('流程进度不把连线当节点；完整流程图支持旋转、拖动、双指缩放和适应屏幕', async ({ page }) => {
  const bpmnXml = `<?xml version="1.0" encoding="UTF-8"?>
    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="definitions" targetNamespace="http://flow.test">
      <bpmn:process id="process" isExecutable="true"><bpmn:startEvent id="start" name="开始"/><bpmn:userTask id="review" name="部门复核"/><bpmn:sequenceFlow id="Flow_test" sourceRef="start" targetRef="review"/></bpmn:process>
      <bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="process"><bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="100" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="review_di" bpmnElement="review"><dc:Bounds x="1000" y="78" width="100" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNEdge id="flow_di" bpmnElement="Flow_test"><di:waypoint x="136" y="118"/><di:waypoint x="1000" y="118"/></bpmndi:BPMNEdge></bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
    </bpmn:definitions>`
  const diagramTheme = { ...DEFAULT_MOBILE_THEME, preset: 'purple', primaryColor: '#722ED1', backgroundColor: '#F8F7FB' }
  const state = await fixture(page, { theme: () => diagramTheme, progress: { bpmnXml, completedNodes: ['start', 'Flow_test'], executedSequenceFlows: ['Flow_test'] } })
  await detail(page, 'done')
  await page.getByRole('tab', { name: '流程进度', exact: true }).click()
  await expect(page.locator('.progress-node')).toHaveCount(2)
  await expect(page.locator('.mobile-progress')).not.toContainText('Flow_test')
  await page.getByRole('button', { name: /流程图/ }).click()
  await expect(page.locator('.diagram-canvas .djs-element[data-element-id="review"]')).toBeVisible()
  await expect(page.locator('.mobile-node-active .djs-visual > rect')).toHaveCSS('stroke', 'rgb(114, 46, 209)')
  const initial = await page.locator('.diagram-scale').innerText()
  await page.getByRole('button', { name: '放大', exact: true }).click()
  await expect(page.locator('.diagram-scale')).not.toHaveText(initial)
  await page.getByRole('button', { name: '适应屏幕', exact: true }).click()
  await expect(page.locator('.diagram-scale')).toHaveText(initial)
  const rotate = page.getByRole('button', { name: '旋转流程图', exact: true })
  await rotate.click()
  await expect(rotate).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(async () => Number.parseInt(await page.locator('.diagram-scale').innerText())).toBeGreaterThan(Number.parseInt(initial))
  const rotatedScale = await page.locator('.diagram-scale').innerText()
  const node = page.locator('.diagram-canvas .djs-element[data-element-id="review"]')
  const beforeDrag = await node.boundingBox()
  expect(beforeDrag.height).toBeGreaterThan(beforeDrag.width)
  // 以屏幕位移检查旋转后的平移方向，不能只检查 CSS 角度或内部矩阵发生变化。
  const area = await page.locator('.diagram-viewport').boundingBox()
  const center = { x: area.x + area.width / 2, y: area.y + area.height / 2 }
  await page.mouse.move(center.x, center.y)
  await page.mouse.down()
  await page.mouse.move(center.x + 24, center.y + 16, { steps: 4 })
  await page.mouse.up()
  const afterDrag = await node.boundingBox()
  expect(afterDrag.x - beforeDrag.x).toBeCloseTo(24, 0)
  expect(afterDrag.y - beforeDrag.y).toBeCloseTo(16, 0)
  const touch = await page.context().newCDPSession(page)
  await touch.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ id: 1, x: center.x - 40, y: center.y }, { id: 2, x: center.x + 40, y: center.y }] })
  await touch.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ id: 1, x: center.x - 70, y: center.y }, { id: 2, x: center.x + 70, y: center.y }] })
  await touch.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  await touch.detach()
  await expect.poll(async () => Number.parseInt(await page.locator('.diagram-scale').innerText())).toBeGreaterThan(Number.parseInt(rotatedScale))
  await page.getByRole('button', { name: '适应屏幕', exact: true }).click()
  await expect(page.locator('.diagram-scale')).toHaveText(rotatedScale)
  await rotate.click()
  await expect(rotate).toHaveAttribute('aria-pressed', 'false')
  await expect(page.locator('.diagram-scale')).toHaveText(initial)
  await page.setViewportSize({ width: 844, height: 390 })
  await expect.poll(async () => Number.parseInt(await page.locator('.diagram-scale').innerText())).toBeGreaterThan(Number.parseInt(initial))
  await expect(rotate).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '../.codex-artifacts/frontend-refactor/mobile-diagram-landscape.png', fullPage: true })
  await page.setViewportSize({ width: 360, height: 844 })
  await expect(rotate).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '../.codex-artifacts/frontend-refactor/mobile-diagram-portrait.png', fullPage: true })
  // 弹层关闭后由 v-show 隐藏，重新挂载画布时不能沿用隐藏阶段的零尺寸。
  for (let attempt = 0; attempt < 3; attempt++) {
    await page.locator('.diagram-page .van-nav-bar__left').click()
    await expect(page.locator('.diagram-page')).toHaveCount(0)
    await expect(page.locator('.van-popup--right')).toBeHidden()
    await page.getByRole('button', { name: '查看完整流程图', exact: true }).click()
    await expect(node).toBeVisible()
    await expect(page.locator('.diagram-page [role="alert"]')).toHaveCount(0)
    await expect(rotate).toBeEnabled()
    await expect.poll(async () => Number.parseInt(await page.locator('.diagram-scale').innerText())).toBeGreaterThan(0)
    await rotate.click()
    await expect(rotate).toHaveAttribute('aria-pressed', 'true')
    await page.getByRole('button', { name: '适应屏幕', exact: true }).click()
  }
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})


test('刷新读取新主题，列表与挂载到 body 的用户菜单同步切换', async ({ page }) => {
  let theme = { ...DEFAULT_MOBILE_THEME }
  const state = await fixture(page, { theme: () => theme })
  await page.goto('inbox/todo')
  await expect(page.locator('.inbox-avatar')).toHaveCSS('color', 'rgb(25, 107, 98)')
  theme = { ...DEFAULT_MOBILE_THEME, preset: 'custom', primaryColor: '#722ED1', backgroundColor: '#F8F7FB', surfaceColor: '#FFF8EE' }
  await page.reload()
  await expect(page.locator('.inbox-avatar')).toHaveCSS('color', 'rgb(114, 46, 209)')
  await expect(page.locator('.inbox-page')).toHaveCSS('background-color', 'rgb(255, 248, 238)')
  await page.getByRole('button', { name: '用户菜单' }).click()
  await expect(page.locator('.van-action-sheet')).toHaveCSS('background-color', 'rgb(255, 248, 238)')
  expect(state.requests.filter(item => item.endpoint === '/system/mobile-theme')).toHaveLength(2)
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('登录前也读取系统主题；极浅主色按钮文字仍可读', async ({ page }) => {
  const theme = { ...DEFAULT_MOBILE_THEME, preset: 'custom', primaryColor: '#FFFF00' }
  const state = await fixture(page, { theme: () => theme })
  await page.route('**/api/auth/refresh', route => route.fulfill({ status: 401, json: { code: 401, message: '请登录' } }))
  await page.goto('login')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toHaveCSS('background-color', 'rgb(255, 255, 0)')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toHaveCSS('color', 'rgb(0, 0, 0)')
  expect(state.errors).toEqual([])
})


const customAction = () => ({ key: 'calculate', label: '计算并回填', type: 'custom', ownerFormId: 'form-test', placement: 'FOOTER', visible: true, enabled: true, primary: true, confirm: { enabled: true, message: '确认执行这次回填？' } })

test('自定义按钮遵守确认协议：取消不执行，确认后一次回填保留发布坐标', async ({ page }) => {
  const state = await fixture(page, { actions: [customAction()], eventResult: { effects: [{ type: 'FIELD_MAPPING', data: { form: { name: '确认后的名称' } }, mappings: [{ targetPath: 'form.name', overwrite: 'ALWAYS' }] }] } })
  await detail(page)
  const button = page.getByRole('button', { name: '计算并回填', exact: true })
  await button.click()
  await expect(page.getByRole('dialog')).toContainText('确认执行这次回填？')
  await expect(page.locator('.mobile-action-bar .van-button--primary')).toBeDisabled()
  expect(state.writes).toHaveLength(0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(button).toBeEnabled(); expect(state.writes).toHaveLength(0)
  await button.click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByPlaceholder('请输入事项名称')).toHaveValue('确认后的名称')
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0].body).toMatchObject({ targetKey: 'calculate', releaseId: 'release-pinned', releaseVersion: 2, taskId: 'task-test' })
  expect(state.writes[0].body.requestId).toMatch(/^form_action_/)
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
})

test('移动按钮消费消息、关闭与刷新效果，返回列表正常使用', async ({ page }) => {
  const action = customAction(); action.confirm.enabled = false
  const state = await fixture(page, { actions: [action], eventResult: { effects: [{ type: 'MESSAGE', message: '回填完成' }, { type: 'REFRESH_PARENT' }, { type: 'CLOSE_FORM' }] } })
  await detail(page)
  await page.getByRole('button', { name: '计算并回填', exact: true }).click()
  await expect(page).toHaveURL(/inbox\/todo/)
  await expect(page.getByText('测试流程', { exact: true }).first()).toBeVisible()
  expect(state.writes).toHaveLength(1)
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
})

test('移动按钮不将未支持的 PC 路由静默重定向为成功', async ({ page }) => {
  const action = customAction(); action.confirm.enabled = false
  const state = await fixture(page, { actions: [action], eventResult: { effects: [{ type: 'OPEN_ROUTE', route: '/entity-list/test/default' }] } })
  await detail(page)
  await page.getByRole('button', { name: '计算并回填', exact: true }).click()
  await expect(page.getByText('该跳转页面暂不支持移动端，请在 PC 端打开', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/process\/instance-test/)
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
})
