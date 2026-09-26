import { test, expect } from '@playwright/test'
const field = { id: 'amount-field', fieldCode: 'amount', fieldName: '金额', fieldLabel: '金额', fieldType: 'DECIMAL', componentType: 'number', showInList: true, isQuery: true, sortOrder: 0 }
const entity = { id: 'entity-test', entityCode: 'test', entityName: '测试实体', storageMode: 'DYNAMIC', lifecycleMode: 'WORKFLOW', status: 'DRAFT', fields: [field] }
const form = { id: 'form-test', formName: '测试表单', formKey: 'main', entityId: 'entity-test', entityCode: 'test', revision: 3, fields: [field], viewConfig: '{}', rendererMode: 'DEFAULT', runtimeReleaseId: 'form-release', runtimeReleaseVersion: 1 }
const column = { ...field, id: 'column-amount', fieldId: field.id, revision: 1, orderKey: 1000000 }
const config = { id: 'list-test', listName: '测试列表', listKey: 'main', entityId: 'entity-test', entityCode: 'test', revision: 3, viewConfig: '{}', fields: [column], releaseId: 'list-release', publishedVersion: 1 }
async function mock(page, { failScope = false } = {}) {
  const errors = [], unexpected = [], writes = []
  let revision = 3, scopeFails = failScope
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const endpoint = url.pathname.slice(4), method = request.method(), body = method === 'POST' ? request.postDataJSON() : null
    let data
    if (method === 'POST') writes.push({ endpoint, body })
    if (endpoint === '/entity') data = { records: [entity], total: 1 }
    else if (['/entity/entity-test', '/entity/code/test', '/entity/process/process-test'].includes(endpoint)) data = entity
    else if (endpoint === '/entity-form/entity/entity-test/fields') data = [field]
    else if (endpoint === '/entity-lists/test/main/schema') data = config
    else if (endpoint === '/entity-lists/test/main/query') data = { records: [{ id: 'record-test', name: '测试记录', amount: 7 }], total: 1 }
    else if (endpoint === '/entity-versions/records/test/capabilities') data = { runtimeEnabled: false, historyReadable: false }
    else if (endpoint === '/entity-selector/USER/batch') data = [{ id: 'fixture', code: 'fixture', name: '测试用户' }]
    else if (endpoint === '/entity/options') data = { records: [entity], total: 1 }
    else if (endpoint === '/entity/options/resolve') data = [entity]
    else if (endpoint === '/entity-list-config/list-test' && method === 'POST') data = { ...config, revision: ++revision }
    else if (endpoint === '/entity-list-config/list-test') data = { ...config, revision }
    else if (endpoint === '/entity-list-scopes/test/lists/main/bindings/update') {
      if (scopeFails) { scopeFails = false; return route.fulfill({ status: 500, json: { code: 500, message: '规则保存测试失败' } }) }
      data = {}
    }
    else if (endpoint === '/entity-list-scopes/test') data = { policies: [], bindings: [], listDefaults: { main: { unboundPolicy: 'DENY_ALL', enforcementMode: 'ENFORCE' } } }
    else if (endpoint.endsWith('/diff')) data = { changed: false, changedSections: [] }
    else if (endpoint === '/entity-list-config/entity/entity-test') data = [config]
    else if (endpoint === '/entity-form/entity/entity-test') data = [form]
    else if (['/entity-form/form-test', '/entity-form-resolve/new-data/test'].includes(endpoint)) data = form
    else if (endpoint === '/entity-form/form-test/fields') data = [field]
    else if (endpoint === '/entity-forms/form-test/nodes') data = [{ id: 'amount-node', nodeKey: 'amount', nodeType: 'FIELD', formId: form.id, bindingType: 'ENTITY_FIELD', bindingRef: 'amount', revision: 1, orderKey: 1000000, propsDocument: JSON.stringify(field) }]
    else if (endpoint === '/entity-version-diff/pending/entity-test') data = { isFirstPublish: true, currentVersion: 0, nextVersion: 1, changeSummary: '首次发布', addedFields: [field], pendingDdls: [], schemaOperation: { riskLevel: 'LOW', status: 'DDL_PENDING' } }
    else if (endpoint === '/ui-runtime/form-actions/resolve') data = []
    else if (endpoint === '/ui-runtime/view-compositions/resolve') data = []
    else if (endpoint === '/ui-extensions/catalog') data = []
    else if (['/entity-list-config/extension-options', '/ui-component-templates', '/ui-extensions/available-interfaces', '/ui-extensions', '/ui-event-bindings', '/task-sla-policies/published', '/work-calendars'].includes(endpoint)) data = []
    else if (['/ui-view-compositions/FORM/form-test', '/ui-view-compositions/LIST/list-test', '/entity/entity-test/relations', '/entity/entity-test/relations/available', '/entity-status/test'].includes(endpoint)) data = []
    else if (['/system/dict/list', '/ui-event-bindings/catalog', '/extension-catalog/options', '/system/menu/entity-permission-options', '/entity-status/list/test', '/process-design/position-options', '/process-design/organization-business-levels', '/system/group/enabled', '/system/role/enabled', '/system/org/enabled', '/process/published'].includes(endpoint)) data = []
    else { unexpected.push(`${method} ${endpoint}`); console.warn('未声明测试接口', method, endpoint); return route.fulfill({ status: 500, json: { code: 500, message: `未声明测试接口 ${endpoint}` } }) }
    return route.fulfill({ json: { code: 200, data } })
  })
  return { errors, unexpected, writes }
}
test('列表设计：元数据成功但规则失败时停止，重试使用新 revision', async ({ page }) => {
  const state = await mock(page, { failScope: true })
  await page.goto('/tests/e2e/designer-fixture.html?view=list')
  await page.getByRole('tab', { name: '列表设置', exact: true }).click()
  await page.locator('label.el-checkbox').filter({ hasText: /^边框$/ }).click()
  await expect(page.getByRole('checkbox', { name: '边框', exact: true })).toBeChecked()
  await page.getByRole('button', { name: '保存全部', exact: true }).click()
  await expect(page.getByText(/列表设置已保存.*数据规则/)).toBeVisible()
  expect(state.writes.filter(x => x.endpoint === '/entity-list-config/list-test')[0].body.expectedRevision).toBe(3)
  expect(state.writes.some(x => x.endpoint.includes('/fields'))).toBe(false)
  await page.getByRole('button', { name: '保存列表设置', exact: true }).click()
  await expect(page.getByText(/列表设置已保存。数据规则绑定已立即生效/)).toBeVisible()
  expect(state.writes.filter(x => x.endpoint === '/entity-list-config/list-test')[1].body.expectedRevision).toBe(4)
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
  await page.screenshot({ path: '../.codex-artifacts/frontend-refactor/pc-list-settings.png', fullPage: true, animations: 'disabled' })
})
for (const mode of ['entity', 'form', 'entities', 'data', 'related', 'events', 'node']) test(`拆分后真实页面可加载与交互：${mode}`, async ({ page }) => {
  const state = await mock(page)
  await page.goto(`/tests/e2e/designer-fixture.html?view=${mode}`)
  if (mode === 'entity') {
    await page.getByRole('tab', { name: '数据权限', exact: true }).click()
    await expect(page.getByText('规则名称', { exact: true }).first()).toBeVisible()
  } else if (mode === 'form') {
    await expect(page.getByText('金额', { exact: true }).first()).toBeVisible()
  } else if (mode === 'entities') {
    await page.getByRole('button', { name: '发布', exact: true }).first().click()
    await expect(page.getByText('发布预览 - 首次发布', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    expect(state.writes).toEqual([])
  } else if (mode === 'data') {
    await expect(page.getByText('金额', { exact: true }).first()).toBeVisible()
  } else if (mode === 'related') {
    await page.getByRole('button', { name: '打开关联内容' }).click()
    await page.getByPlaceholder('例如：所属项目详情').fill('测试草稿')
    await expect(page.getByPlaceholder('例如：所属项目详情')).toHaveValue('测试草稿')
  } else if (mode === 'events') {
    await expect(page.getByText('触发事件', { exact: true }).first()).toBeVisible()
  } else {
    await expect(page.getByTestId('extension-value')).toHaveText('before')
    await page.getByRole('button', { name: '测试扩展更新', exact: true }).click()
    await expect(page.getByTestId('extension-value')).toHaveText('after')
    await page.getByRole('button', { name: '撤销', exact: true }).click()
    await expect(page.getByTestId('extension-value')).toHaveText('before')
    await page.getByRole('button', { name: '重做', exact: true }).click()
    await expect(page.getByTestId('extension-value')).toHaveText('after')
    await page.getByRole('tab', { name: '高级', exact: true }).click()
    await page.getByRole('button', { name: /任务 SLA/ }).click()
    const sla = page.locator('.settings-section').filter({ has: page.locator('strong').filter({ hasText: /^任务 SLA$/ }) })
    await sla.locator('.el-switch').click()
    await expect(sla.getByRole('combobox', { name: /SLA 策略/ })).toBeVisible()
    await page.getByRole('tab', { name: '常用', exact: true }).click()
    await page.getByRole('tab', { name: '高级', exact: true }).click()
    await expect(sla.getByRole('switch')).toBeChecked()
    await sla.locator('.el-switch').click()
    await expect(sla.getByRole('combobox', { name: /SLA 策略/ })).toBeHidden()
  }
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  expect(state.errors).toEqual([]); expect(state.unexpected).toEqual([])
  await page.screenshot({ path: `../.codex-artifacts/frontend-refactor/pc-designer-${mode}.png`, fullPage: true, animations: 'disabled' })
})
