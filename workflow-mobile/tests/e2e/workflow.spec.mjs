import { test, expect } from '@playwright/test'
import { DEFAULT_MOBILE_THEME } from '@flow/workflow-core/mobile-theme'

const field = (id, parentId, label, extra = {}) => ({ id, parentId, nodeKey: id, nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: id, props: { fieldCode: id, label, fieldType: 'STRING', componentType: 'input', ...extra } })
const simpleForm = () => ({ id: 'form-test', runtimeReleaseId: 'release-pinned', runtimeReleaseVersion: 2, releaseResolutionToken: 'signed-test-only', fields: [], nodes: [field('name', '', '事项名称')] })

/** 所有 API 请求在浏览器内截获；审批写入仅记录到测试数组，绝不请求真实业务服务。 */
async function fixture(page, { theme = () => DEFAULT_MOBILE_THEME, form = simpleForm(), record = { id: 'record-test', name: '测试流程', entityCode: 'test' }, preview, complete, progress = {}, candidates = [], processOperations = { withdraw: false }, operations = { approve: true, reject: true, manualCc: false }, rejected = { canResubmit: false } } = {}) {
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
    else if (endpoint.startsWith('/ui-runtime/events/')) data = { effects: [] }
    else if (endpoint === '/process-instance/instance-test/operations') data = processOperations
    else if (endpoint.endsWith('/next-approver-options')) data = { records: candidates, total: candidates.length }
    else if (endpoint === '/process-rollback/rejected-status/instance-test') data = rejected
    else if (endpoint === '/entity-selector/USER') data = { records: [{ username: 'reviewer', nickname: '测试办理人' }], total: 1 }
    else if (endpoint === '/process-rollback/resubmit/instance-test') { writes.push({ endpoint, body }); data = {} }
    else if (endpoint === '/process-task/withdraw') { writes.push({ endpoint, body }); data = {} }
    else if (endpoint === '/process-task/history/instance-test') data = []
    else if (endpoint === '/tasks/task-test/operations') data = operations
    else if (endpoint === '/ui-runtime/form-actions/resolve') data = [{ key: 'submitApproval', label: '提交审批', type: 'built-in', placement: 'FOOTER', visible: true, enabled: true }]
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
  await expect(page.locator('.detail-content').first()).toBeVisible()
}
async function submit(page) {
  await page.getByRole('button', { name: '提交审批', exact: true }).click()
  const confirm = page.getByRole('button', { name: '确认提交', exact: true })
  await expect(confirm).toBeEnabled(); await confirm.click()
}

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
    await page.getByRole('button', { name: '用户菜单' }).click()
    await expect(page.getByText('修改密码', { exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: '取消', exact: true }).click()
  }
  expect(state.unexpected).toEqual([]); expect(state.errors).toEqual([])
})

test('折叠 Tab 必填校验展开错误分组；保留发布坐标且只提交一次', async ({ page }) => {
  const form = { ...simpleForm(), nodes: [{ id: 'tabs', nodeType: 'TAB_SET' }, { id: 'first', parentId: 'tabs', nodeType: 'TAB', props: { label: '主要信息' } }, { id: 'second', parentId: 'tabs', nodeType: 'TAB', props: { label: '补充信息' } }, field('name', 'first', '事项名称'), field('required', 'second', '补充说明', { required: true })] }
  const state = await fixture(page, { form })
  await detail(page); await expect(page.getByPlaceholder('请输入补充说明')).not.toBeVisible()
  await submit(page)
  await expect(page.getByPlaceholder('请输入补充说明')).toBeVisible()
  await expect(page.getByRole('alert').filter({ hasText: '请输入补充说明' })).toBeVisible()
  expect(state.writes).toHaveLength(0)
  await page.getByPlaceholder('请输入补充说明').fill('已核对')
  await submit(page)
  await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0].body).toMatchObject({ formReleaseId: 'release-pinned', formReleaseVersion: 2, formReleaseResolutionToken: 'signed-test-only', formData: { required: '已核对' } })
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
  await expect(page.locator('.detail-content').first()).toContainText('仍需保留的草稿')
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
  const state = await fixture(page, { processOperations: { withdraw: true } })
  await page.goto('process/instance-test?kind=started')
  await page.getByRole('button', { name: '更多操作', exact: true }).click()
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


test('驳回使用发布的动作选项；转办保持办理人协议', async ({ page }) => {
  const state = await fixture(page, { operations: { approve: true, reject: true, transfer: true } })
  await detail(page)
  await page.getByRole('button', { name: '提交审批', exact: true }).click()
  await page.getByRole('radio', { name: /驳回/ }).click()
  await page.getByRole('button', { name: '确认提交', exact: true }).click()
  await expect(page).toHaveURL(/inbox\/todo/)
  expect(state.writes[0].body).toMatchObject({ action: 'reject', actionLabel: '驳回' })
  await detail(page)
  await page.getByRole('button', { name: '更多操作', exact: true }).click()
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


test('流程进度不把连线当节点；完整流程图可缩放和适应屏幕', async ({ page }) => {
  const bpmnXml = `<?xml version="1.0" encoding="UTF-8"?>
    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="definitions" targetNamespace="http://flow.test">
      <bpmn:process id="process" isExecutable="true"><bpmn:startEvent id="start" name="开始"/><bpmn:userTask id="review" name="部门复核"/><bpmn:sequenceFlow id="Flow_test" sourceRef="start" targetRef="review"/></bpmn:process>
      <bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="process"><bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="100" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="review_di" bpmnElement="review"><dc:Bounds x="200" y="78" width="100" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNEdge id="flow_di" bpmnElement="Flow_test"><di:waypoint x="136" y="118"/><di:waypoint x="200" y="118"/></bpmndi:BPMNEdge></bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
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
