import { test, expect } from '@playwright/test'
const xml = `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Defs" targetNamespace="http://test"><bpmn:process id="process" isExecutable="true"><bpmn:userTask id="review" name="再次审批"/></bpmn:process><bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="process"><bpmndi:BPMNShape id="shape" bpmnElement="review"><dc:Bounds x="100" y="100" width="100" height="80"/></bpmndi:BPMNShape></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`
const form = { id: 'form-test', formKey: 'test', runtimeReleaseId: 'release-pinned', runtimeReleaseVersion: 2, releaseResolutionToken: 'test-signed-token', fields: [{ id: 'amount-field', fieldId: 'amount-field', fieldCode: 'amount', fieldLabel: '金额', fieldName: '金额', fieldType: 'DECIMAL', componentType: 'number', isReadonly: 0 }], nodes: [] }
const action = { key: 'calculate', label: '计算并回填', type: 'custom', ownerFormId: 'form-test', placement: 'FOOTER', visible: true, enabled: true, confirm: { enabled: true, message: '确认执行这次回填？' } }
async function fixture(page, { result, done } = {}) {
  const errors = [], unexpected = [], writes = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    if (!new URL(route.request().url()).pathname.startsWith('/api/')) return route.continue()
    const request = route.request(), url = new URL(request.url()), endpoint = url.pathname.slice(4), body = request.method() === 'POST' ? request.postDataJSON() : null
    let data
    if (endpoint === '/ui-runtime/form-actions/resolve') data = [action]
    else if (endpoint.includes('/FORM_BUTTON_CLICK/')) { writes.push(body); data = result || { effects: [{ type: 'FIELD_MAPPING', data: { form: { amount: 100 } }, mappings: [{ targetPath: 'form.amount', overwrite: 'ALWAYS' }] }] } }
    else if (endpoint.startsWith('/ui-runtime/events/')) data = { effects: [] }
    else if (endpoint === '/entity-data/entity/test/detail/record-test/load') data = { id: 'record-test', name: '测试记录', data: { amount: 7 } }
    else if (endpoint === '/process-instance/instance-test/progress') data = { processInstanceId: 'instance-test', processName: '回退重审流程', status: 'RUNNING', formConfig: form, entityData: { id: 'record-test', amount: 7 }, bpmnXml: xml, activeNodes: ['review'], completedNodes: ['review'], tasks: [], nodeHistory: [{ nodeId: 'review', nodeName: '再次审批', startTime: '2026-09-24 10:00:00', duration: 60000, status: 'ACTIVE' }] }
    else if (endpoint.endsWith('/next-approval-preview')) data = { status: 'READY', nextNodes: [], scopeKey: 'scope-test' }
    else if (endpoint.endsWith('/history/instance-test')) data = []
    else if (endpoint === '/process-task/statistics') data = { todoCount: 0, doneCount: 0, processCount: 0 }
    else if (endpoint === '/process-task/done') data = done ? await done(body, url) : { records: [], total: 0 }
    else if (['/process-task/todo', '/process-instance/my-started', '/process-cc/my-cc'].includes(endpoint)) data = { records: [], total: 0 }
    else { unexpected.push(endpoint); return route.fulfill({ status: 500, json: { code: 500, message: `未声明测试接口 ${endpoint}` } }) }
    await route.fulfill({ json: { code: 200, data } })
  })
  return { errors, unexpected, writes }
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
  expect(state.writes[0]).toMatchObject({ releaseId: 'release-pinned', releaseVersion: 2, targetKey: 'calculate' })
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
  await page.screenshot({ path: `../.codex-artifacts/frontend-refactor/pc-${mode}-mapping.png`, fullPage: true, animations: 'disabled' })
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
  await page.screenshot({ path: '../.codex-artifacts/frontend-refactor/pc-progress.png', fullPage: true, animations: 'disabled' })
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
