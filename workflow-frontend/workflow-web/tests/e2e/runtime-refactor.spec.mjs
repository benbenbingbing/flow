import { test, expect } from '@playwright/test'
const xml = `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Defs" targetNamespace="http://test"><bpmn:process id="process" isExecutable="true"><bpmn:userTask id="review" name="再次审批"/></bpmn:process><bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="process"><bpmndi:BPMNShape id="shape" bpmnElement="review"><dc:Bounds x="100" y="100" width="100" height="80"/></bpmndi:BPMNShape></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`
const form = { id: 'form-test', formKey: 'test', configHash: 'graph-test', fields: [{ id: 'amount-field', fieldId: 'amount-field', fieldCode: 'amount', fieldLabel: '金额', fieldName: '金额', fieldType: 'DECIMAL', componentType: 'number', isReadonly: 0 }], nodes: [] }
const action = { key: 'calculate', label: '计算并回填', type: 'custom', ownerFormId: 'form-test', placement: 'FOOTER', visible: true, enabled: true, confirm: { enabled: true, message: '确认执行这次回填？' } }
const task = { taskId: 'task-test', processInstanceId: 'instance-test', processName: '回退重审流程', code: 'FLOW-123', name: '测试记录' }
const recipients = [
  { id: 'u1', code: 'reviewer', name: '复核人', entityType: 'USER' },
  { id: 'u2', code: 'observer', name: '观察人', entityType: 'USER' }
]
async function fixture(page, { result, done, todo = [], started = [], formActions = [action], taskOps = {}, instanceOps = {}, hasFormCloseBinding = false } = {}) {
  const errors = [], unexpected = [], writes = [], reads = [], closes = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    if (!new URL(route.request().url()).pathname.startsWith('/api/')) return route.continue()
    const request = route.request(), url = new URL(request.url()), endpoint = url.pathname.slice(4), body = request.method() === 'POST' ? request.postDataJSON() : null
    reads.push({ endpoint, taskId: url.searchParams.get('taskId') })
    let data
    if (endpoint === '/ui-runtime/form-actions/resolve') data = formActions
    else if (endpoint === '/tasks/task-test/operations') data = taskOps
    else if (endpoint === '/process-instance/instance-test/operations') data = instanceOps
    else if (endpoint === '/tasks/task-test/return-targets') data = [{ activityInstanceId: 'prior-check', nodeId: 'check', nodeName: '初审' }]
    else if (endpoint === '/tasks/task-test/return') { writes.push(body); data = {} }
    else if (['/process-task/withdraw', '/process-instance/instance-test/terminate', '/tasks/task-test/cc'].includes(endpoint)) { writes.push({ endpoint, body }); data = {} }
    else if (endpoint === '/entity-selector/USER') data = { records: recipients, total: recipients.length }
    else if (endpoint === '/entity-selector/USER/batch') data = recipients
    else if (endpoint.includes('/FORM_BUTTON_CLICK/')) { writes.push(body); data = result || { effects: [{ type: 'FIELD_MAPPING', data: { form: { amount: 100 } }, mappings: [{ targetPath: 'form.amount', overwrite: 'ALWAYS' }] }] } }
    else if (endpoint.includes('/FORM_CLOSE/')) { closes.push(body); data = { effects: [] } }
    else if (endpoint.startsWith('/ui-runtime/events/')) data = { effects: [] }
    else if (endpoint === '/entity-data/entity/test/detail/record-test/load') data = { id: 'record-test', name: '测试记录', data: { amount: 7 } }
    else if (endpoint === '/process-instance/instance-test/form-runtime') data = { processInstanceId: 'instance-test', processName: '回退重审流程', status: 'RUNNING', formConfig: { ...form, hasFormCloseBinding }, entityData: { id: 'record-test', amount: 7 }, tasks: [] }
    else if (endpoint === '/process-instance/instance-test/progress') data = { processInstanceId: 'instance-test', processName: '回退重审流程', status: 'RUNNING', bpmnXml: xml, activeNodes: ['review'], completedNodes: ['review'], tasks: [], nodeHistory: [{ nodeId: 'review', nodeName: '再次审批', startTime: '2026-09-24 10:00:00', duration: 60000, status: 'ACTIVE' }] }
    else if (endpoint.endsWith('/next-approval-preview')) data = { status: 'READY', nextNodes: [], scopeKey: 'scope-test' }
    else if (endpoint.endsWith('/history/instance-test')) data = []
    else if (endpoint === '/process-task/statistics') data = { todoCount: 0, doneCount: 0, processCount: 0 }
    else if (endpoint === '/process-task/done') data = done ? await done(body, url) : { records: [], total: 0 }
    else if (endpoint === '/process-task/todo') data = { records: todo, total: todo.length }
    else if (endpoint === '/process-instance/my-started') data = { records: started, total: started.length }
    else if (endpoint === '/process-cc/my-cc') data = { records: [], total: 0 }
    else { unexpected.push(endpoint); return route.fulfill({ status: 500, json: { code: 500, message: `未声明测试接口 ${endpoint}` } }) }
    await route.fulfill({ json: { code: 200, data } })
  })
  return { errors, unexpected, writes, reads, closes }
}
for (const mode of ['编辑', '审批']) test(`PC ${mode}真实弹窗：取消确认不执行，确认后按 form.amount 正确回填`, async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/tests/e2e/fixture.html')
  await page.getByRole('button', { name: `打开${mode}`, exact: true }).click()
  const amount = page.getByRole('spinbutton').first(), run = page.getByRole('button', { name: '计算并回填', exact: true })
  await expect(amount).toHaveValue('7.00')
  await run.click()
  await expect(page.getByText('确认执行这次回填？', { exact: true })).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(amount).toHaveValue('7.00'); expect(state.writes).toHaveLength(0)
  await run.click(); await page.getByRole('button', { name: '确定', exact: true }).click()
  await expect(amount).toHaveValue('100.00')
  await expect(page.getByText('确认执行这次回填？', { exact: true })).toBeHidden()
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0]).toMatchObject({ configHash: 'graph-test', targetKey: 'calculate' })
  if (mode === '审批') {
    expect(state.reads.filter(item => item.endpoint.endsWith('/form-runtime'))).toEqual([
      { endpoint: '/process-instance/instance-test/form-runtime', taskId: 'task-test' }
    ])
    expect(state.reads.filter(item => item.endpoint.endsWith('/progress'))).toHaveLength(0)
    await page.getByRole('tab', { name: '流程图', exact: true }).click()
    await expect(page.locator('.djs-element[data-element-id="review"]')).toBeVisible()
    await page.getByRole('tab', { name: '审批历史', exact: true }).click()
    await page.getByRole('tab', { name: '基本信息', exact: true }).click()
    await expect(amount).toHaveValue('100.00')
    expect(state.reads.filter(item => item.endpoint.endsWith('/progress'))).toHaveLength(1)
    expect(state.reads.filter(item => item.endpoint.endsWith('/form-runtime'))).toHaveLength(1)
  }
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
  await page.screenshot({ path: `../../.codex-artifacts/frontend-refactor/pc-${mode}-mapping.png`, fullPage: true, animations: 'disabled' })
})
test('PC 进度页面复用查看器：回退节点显示进行中且保留历史耗时', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/tests/e2e/fixture.html?view=progress')
  const node = page.locator('.djs-element[data-element-id="review"]').first()
  await expect(node).toHaveClass(/status-active/)
  await expect(node).not.toHaveClass(/status-completed/)
  await node.hover()
  await expect(page.locator('.node-tooltip')).toContainText('1分钟0秒')
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
  await page.screenshot({ path: '../../.codex-artifacts/frontend-refactor/pc-progress.png', fullPage: true, animations: 'disabled' })
})
test('PC 左下角只显示有权限的操作，退回使用历史活动且关闭当前详情', async ({ page }) => {
  const state = await fixture(page, {
    hasFormCloseBinding: true,
    formActions: [{ key: 'submitApproval', label: '提交审批', type: 'built-in', placement: 'FOOTER', visible: true, enabled: true }],
    taskOps: { approve: true, return: true, transfer: true, addSign: true, addSignTypes: [] },
    instanceOps: { terminate: true }
  })
  await page.goto('/tests/e2e/fixture.html')
  await page.getByRole('button', { name: '打开审批', exact: true }).click()
  await page.locator('.approval-dialog-footer').getByRole('button', { name: '更多操作', exact: true }).click()
  await expect(page.getByRole('menuitem')).toHaveText(['退回', '终止', '转办'])
  await page.getByRole('menuitem', { name: '退回', exact: true }).click()
  await page.getByText('请选择回退节点', { exact: true }).click()
  await page.getByRole('option', { name: '初审', exact: true }).click()
  await page.getByRole('dialog', { name: '退回', exact: true }).getByRole('textbox', { name: /意见/ }).fill('请补金额')
  await page.getByRole('button', { name: '确认退回', exact: true }).click()
  await expect(page.getByTestId('refresh-count')).toHaveText('1')
  expect(state.writes).toEqual([{
    targetActivityInstanceId: 'prior-check', comment: '请补金额',
    formId: 'form-test', formConfigHash: 'graph-test', requestId: expect.any(String)
  }])
  await expect.poll(() => state.closes.length).toBe(1)
  expect(state.closes[0]).toMatchObject({
    configId: 'form-test', configHash: 'graph-test', recordId: 'record-test',
    input: { mode: 'approve', reason: 'event' }, runtimeContext: { processInstanceId: 'instance-test' }
  })
  expect(state.closes[0].runtimeContext.taskId).toBeUndefined()
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
})

async function openApprovalOperation(page, label) {
  await page.getByRole('button', { name: '审批', exact: true }).click()
  await page.locator('.approval-dialog-footer').getByRole('button', { name: '更多操作', exact: true }).click()
  await page.getByRole('menuitem', { name: label, exact: true }).click()
}

for (const { key, label, message, endpoint, body } of [
  { key: 'withdraw', label: '撤回', message: '撤回后本轮流程结束，未完成的待办将取消。确认撤回吗？',
    endpoint: '/process-task/withdraw', body: { processInstanceId: task.processInstanceId, reason: '修正金额' } },
  { key: 'terminate', label: '终止', message: '终止后流程将直接结束，相关待办也会取消，且不能从当前节点继续。确认终止吗？',
    endpoint: '/process-instance/instance-test/terminate', body: { reason: '发起人主动终止' } }
]) test(`PC 首页和审批面板共用${label}提示并提交相同参数`, async ({ page }) => {
  const state = await fixture(page, {
    todo: [task], started: [{ ...task, status: 'RUNNING', canWithdraw: true, canTerminate: true }],
    instanceOps: { [key]: true }, formActions: []
  })
  await page.goto('/tests/e2e/fixture.html?view=home')
  await page.getByRole('tab', { name: '我发起的', exact: true }).click()
  const homeAction = page.getByRole('button', { name: label, exact: true })
  const dialog = page.getByRole('dialog', { name: `${label}流程`, exact: true })

  await homeAction.click()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(state.writes).toHaveLength(0)

  const confirmOperation = async () => {
    await expect(dialog.getByText(message, { exact: true })).toBeVisible()
    const reason = dialog.getByPlaceholder('请输入撤回原因（选填）')
    if (key === 'withdraw') await reason.fill('  修正金额  ')
    else await expect(dialog.getByRole('textbox')).toHaveCount(0)
    await dialog.getByRole('button', { name: `确认${label}`, exact: true }).click()
    await expect(dialog).toBeHidden()
  }
  await homeAction.click()
  await confirmOperation()
  await expect.poll(() => state.writes).toEqual([{ endpoint, body }])

  await page.getByRole('tab', { name: /待办任务/ }).click()
  await openApprovalOperation(page, label)
  await confirmOperation()
  await expect(page.locator('.entity-approval-dialog')).toBeHidden()
  expect(state.writes).toEqual([{ endpoint, body }, { endpoint, body }])
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
})

test('PC 首页和审批面板共用人工知会弹窗，提交人员编码与说明', async ({ page }) => {
  const state = await fixture(page, { todo: [task], taskOps: { manualCc: true }, formActions: [] })
  await page.goto('/tests/e2e/fixture.html?view=home')
  const cc = page.getByRole('dialog', { name: '人工知会', exact: true })
  const submitCc = async () => {
    await expect(cc.getByText('流程名称', { exact: true })).toBeVisible()
    await expect(cc.getByText(task.processName, { exact: true })).toBeVisible()
    await expect(cc.getByText('流程编码', { exact: true })).toBeVisible()
    await expect(cc.getByText(task.code, { exact: true })).toBeVisible()
    await cc.getByRole('button', { name: '请选择知会人员', exact: true }).click()
    const picker = page.getByRole('dialog', { name: '选择知会人员', exact: true })
    for (const recipient of recipients) {
      await picker.getByRole('cell', { name: recipient.name, exact: true }).click()
    }
    await picker.getByRole('button', { name: '确认选择 (2)', exact: true }).click()
    const comment = cc.getByRole('textbox', { name: '知会说明' })
    await expect(comment).toHaveValue('')
    await comment.fill('请查阅最新结果')
    await cc.getByRole('button', { name: '确认知会', exact: true }).click()
    await expect(cc).toBeHidden()
  }

  await page.getByRole('button', { name: '更多任务操作', exact: true }).click()
  await page.getByRole('menuitem', { name: '知会', exact: true }).click()
  await submitCc()
  await openApprovalOperation(page, '知会')
  await submitCc()
  await expect(page.locator('.entity-approval-dialog')).toBeVisible()
  const write = { endpoint: '/tasks/task-test/cc', body: { userIds: ['reviewer', 'observer'], comment: '请查阅最新结果' } }
  expect(state.writes).toEqual([write, write])
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
})

test('PC 已办列表连续筛选：旧响应迟到不能覆盖最新结果', async ({ page }) => {
  let releaseOld, requestedOld
  const oldRequested = new Promise(resolve => { requestedOld = resolve })
  const state = await fixture(page, { done: async (body, url) => {
    const keyword = body?.keyword || url.searchParams.get('keyword')
    if (keyword === 'old') { requestedOld(); await new Promise(resolve => { releaseOld = resolve }) }
    return { records: keyword ? [{ id: keyword, taskId: keyword, processName: keyword === 'old' ? '旧结果' : '最新结果', dataName: keyword, status: 'COMPLETED' }] : [], total: keyword ? 1 : 0 }
  } })
  await page.goto('/tests/e2e/fixture.html?view=home')
  await page.getByRole('tab', { name: /已办任务/ }).click()
  const search = page.getByPlaceholder('标题、编码、流程或任务')
  await search.fill('old'); await search.press('Enter'); await oldRequested
  await search.fill('new'); await search.press('Enter')
  await expect(page.getByText('最新结果', { exact: true }).first()).toBeVisible()
  const lateResponse = page.waitForResponse(response => response.url().includes('/process-task/done') && new URL(response.url()).searchParams.get('keyword') === 'old')
  releaseOld()
  await lateResponse
  await expect(page.getByText('旧结果', { exact: true })).toHaveCount(0)
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
})
