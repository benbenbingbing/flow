import { test, expect } from '@playwright/test'
const field = { id: 'amount-field', fieldCode: 'amount', fieldName: '金额', fieldLabel: '金额', fieldType: 'DECIMAL', componentType: 'number', showInList: true, isQuery: true, sortOrder: 0 }
const entity = { id: 'entity-test', entityCode: 'test', entityName: '测试实体', storageMode: 'DYNAMIC', lifecycleMode: 'WORKFLOW', status: 'DRAFT', fields: [field] }
const form = { id: 'form-test', formName: '测试表单', formKey: 'main', entityId: 'entity-test', entityCode: 'test', isDefault: true, revision: 3, fields: [field], viewConfig: '{}', rendererMode: 'DEFAULT', configHash: 'form-release' }
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
  await page.screenshot({ path: '../../.codex-artifacts/frontend-refactor/pc-list-settings.png', fullPage: true, animations: 'disabled' })
})

test('单选字典字段可保存并发布多选查询，选中任一值即可匹配', async ({ page }) => {
  const state = await mock(page)
  const priority = {
    id: 'priority-field', fieldCode: 'priority', fieldName: '优先级', fieldType: 'SELECT',
    dictType: 'priority', componentType: 'select', showInList: true, isQuery: true
  }
  const status = { id: 'status-field', fieldCode: 'status', fieldName: '状态', fieldType: 'STRING', isSystem: true }
  const draft = {
    ...config,
    fields: [priority, status].map((item, index) => ({
      ...item, id: `column-${item.fieldCode}`, fieldId: item.id, revision: 1,
      showInList: true, isQuery: true, queryType: 'EQ', orderKey: (index + 1) * 1000000
    }))
  }
  draft.fields[0].queryConfig = JSON.stringify({ defaultValue: 'HIGH' })
  let published = structuredClone(draft)
  const queries = []
  const records = [['HIGH', '高优先级记录'], ['URGENT', '紧急记录'], ['LOW', '低优先级记录']]
    .map(([priorityCode, name]) => ({ id: name, name, status: 'READY', data: { priority: priorityCode } }))

  // 草稿与发布快照各自保存，运行页只读取已发布版本。
  await page.route('**/api/**', async route => {
    const request = route.request()
    const endpoint = new URL(request.url()).pathname.slice(4)
    let data
    if (['/entity/entity-test', '/entity/code/test'].includes(endpoint)) data = { ...entity, fields: [priority, status] }
    else if (endpoint === '/system/dict/item/tree/code/priority') {
      data = [['HIGH', '高'], ['URGENT', '紧急'], ['LOW', '低']]
        .map(([itemCode, itemLabel]) => ({ itemCode, itemLabel, status: '0' }))
    } else if (endpoint === '/entity-status/list/test') data = [{ statusCode: 'READY', statusName: '待处理' }]
    else if (endpoint === '/entity-list-config/list-test') data = draft
    else if (endpoint === '/entity-list-config/list-test/diff') data = { changed: draft.revision !== published.revision }
    else if (endpoint === '/entity-list-config/list-test/fields/column-priority/patch') {
      const { field: savedField } = request.postDataJSON()
      draft.fields[0] = { ...draft.fields[0], ...savedField, revision: 2 }
      draft.revision++
      data = draft.fields[0]
    } else if (endpoint === '/entity-list-config/list-test/publish-preview') {
      data = { changed: true, canPublish: true, blockers: [], draftHash: 'priority-multi', activeReleaseId: 'list-release' }
    } else if (endpoint === '/entity-list-config/list-test/publish') {
      published = structuredClone(draft)
      data = published
    } else if (endpoint === '/entity-lists/test/main/schema') data = published
    else if (endpoint === '/entity-lists/test/main/query') {
      const { filters } = request.postDataJSON()
      queries.push(filters)
      const matches = filters.priority_op === 'IN'
        ? records.filter(row => filters.priority.includes(row.data.priority))
        : records
      data = { records: matches, total: matches.length }
    } else return route.fallback()
    return route.fulfill({ json: { code: 200, data } })
  })

  const openSettings = () => page.getByRole('row', { name: /^优先级 priority/ }).getByRole('button', { name: '设置', exact: true }).click()
  const advanced = page.getByRole('dialog', { name: '字段高级配置：优先级' })
  const option = name => page.getByRole('option', { name, exact: true }).filter({ visible: true })
  const select = (scope, label) => scope.locator('.el-select__wrapper').filter({
    has: page.getByRole('combobox', { name: label, exact: true })
  })

  await page.goto('/tests/e2e/designer-fixture.html?view=list')
  await openSettings()
  await select(advanced, '查询组件').click()
  await option('下拉多选').click()
  await expect(advanced.getByText('包含于', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '保存当前列' }).click()
  await expect(advanced).toBeHidden()
  expect(draft.fields[0].queryType).toBe('IN')
  expect(JSON.parse(draft.fields[0].queryConfig).componentType).toBe('select_multiple')

  await page.reload()
  await openSettings()
  await expect(advanced.getByText('下拉多选', { exact: true })).toBeVisible()
  await expect(advanced.getByText('包含于', { exact: true })).toBeVisible()
  await advanced.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '发布生效' }).click()
  await page.getByRole('button', { name: '确认发布' }).click()
  await expect(page.getByText('发布成功，已全局生效')).toBeVisible()

  await page.getByRole('button', { name: '预览', exact: true }).click()
  const preview = page.getByRole('dialog', { name: '列表预览' })
  await select(preview.locator('.search-card'), '优先级').click()
  await option('高').click()
  await preview.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toEqual({ priority: ['HIGH'], priority_op: 'IN' })
  await select(preview.locator('.search-card'), '优先级').locator('.el-tag__close').click()
  await preview.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toEqual({})
  await preview.getByRole('button', { name: '关闭', exact: true }).click()

  await page.goto('/tests/e2e/designer-fixture.html?view=data')
  await expect.poll(() => queries.at(-1)).toEqual({ priority: ['HIGH'], priority_op: 'IN' })
  const runtimeSearch = page.locator('.search-card')
  const runtimePriority = select(runtimeSearch, '优先级')
  await runtimePriority.click()
  await option('紧急').click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toEqual({ priority: ['HIGH', 'URGENT'], priority_op: 'IN' })
  await expect(page.locator('.el-table__body tbody tr')).toHaveCount(2)

  await select(runtimeSearch, '状态').click()
  await option('待处理').click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toEqual({ priority: ['HIGH', 'URGENT'], priority_op: 'IN', status: 'READY', status_op: 'EQ' })
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toEqual({ priority: ['HIGH'], priority_op: 'IN' })
  await expect(page.locator('.el-table__body tbody tr')).toHaveCount(1)
  expect(state.errors).toEqual([])
  expect(state.unexpected).toEqual([])
})

test('查询方式显示伪 SQL，空值条件可以保存、勾选和重置', async ({ page }) => {
  const state = await mock(page)
  const comment = { ...field, id: 'comment-field', fieldCode: 'comment', fieldName: '备注', fieldLabel: '备注', fieldType: 'STRING', componentType: 'input' }
  const draft = {
    ...config,
    fields: [{
      ...column, ...comment, id: 'column-comment', fieldId: comment.id, queryType: 'LIKE',
      queryConfig: JSON.stringify({ componentType: 'input', defaultValue: '旧默认值' })
    }]
  }
  let published = structuredClone(draft)
  const queries = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const endpoint = new URL(request.url()).pathname.slice(4)
    let data
    if (['/entity/entity-test', '/entity/code/test'].includes(endpoint)) data = { ...entity, fields: [comment] }
    else if (endpoint === '/entity-list-config/list-test') data = draft
    else if (endpoint === '/entity-list-config/list-test/diff') data = { changed: draft.revision !== published.revision }
    else if (endpoint === '/entity-list-config/list-test/fields/column-comment/patch') {
      draft.fields[0] = { ...draft.fields[0], ...request.postDataJSON().field, revision: draft.fields[0].revision + 1 }
      draft.revision++
      data = draft.fields[0]
    } else if (endpoint === '/entity-list-config/list-test/publish-preview') {
      data = { changed: true, canPublish: true, blockers: [], draftHash: 'comment-null', activeReleaseId: 'list-release' }
    } else if (endpoint === '/entity-list-config/list-test/publish') {
      published = structuredClone(draft)
      data = published
    } else if (endpoint === '/entity-lists/test/main/schema') data = published
    else if (endpoint === '/entity-lists/test/main/query') {
      const { filters } = request.postDataJSON()
      queries.push(filters)
      data = { records: [], total: 0 }
    } else return route.fallback()
    return route.fulfill({ json: { code: 200, data } })
  })
  const advanced = page.getByRole('dialog', { name: '字段高级配置：备注' })
  const openSettings = () => page.getByRole('row', { name: /^备注 comment/ }).getByRole('button', { name: '设置', exact: true }).click()
  const openOperators = () => advanced.locator('.el-select__wrapper').filter({
    has: page.getByRole('combobox', { name: /^查询方式/ })
  }).click()
  const optionFor = sql => page.getByRole('option').filter({ has: page.getByText(sql, { exact: true }) })
  const saveColumn = async queryType => {
    await expect(advanced.getByRole('textbox', { name: '默认值' })).toBeDisabled()
    await advanced.getByRole('button', { name: '保存当前列' }).click()
    await expect(advanced).toBeHidden()
    expect(draft.fields[0].queryType).toBe(queryType)
  }

  await page.goto('/tests/e2e/designer-fixture.html?view=list')
  await openSettings()
  await openOperators()
  const examples = [
    '字段 = 值', '字段 <> 值', "字段 LIKE '%关键词%'", "字段 NOT LIKE '%关键词%'",
    '字段 > 值', '字段 >= 值', '字段 < 值', '字段 <= 值', '字段 BETWEEN 起始值 AND 结束值',
    '字段 IN (值1, 值2, …)', '字段 NOT IN (值1, 值2, …)', '字段 IS NULL', '字段 IS NOT NULL'
  ]
  for (const sql of examples) await expect(optionFor(sql)).toBeVisible()
  await optionFor('字段 IS NOT NULL').click()
  await saveColumn('NOT_EMPTY')

  await page.getByRole('button', { name: '预览', exact: true }).click()
  const preview = page.getByRole('dialog', { name: '列表预览' })
  const previewFilter = preview.getByRole('checkbox', { name: '非空', exact: true })
  await expect(previewFilter).not.toBeChecked()
  await expect.poll(() => queries.at(-1)).toEqual({})
  await preview.locator('label.el-checkbox').filter({ hasText: /^非空$/ }).click()
  await expect(previewFilter).toBeChecked()
  await preview.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toEqual({ comment: true, comment_op: 'NOT_EMPTY' })
  await preview.getByRole('button', { name: '重置', exact: true }).click()
  await expect(previewFilter).not.toBeChecked()
  await expect.poll(() => queries.at(-1)).toEqual({})
  await preview.getByRole('button', { name: '关闭', exact: true }).click()

  await openSettings()
  await openOperators()
  await optionFor('字段 IS NULL').click()
  await saveColumn('EMPTY')
  await page.getByRole('button', { name: '发布生效' }).click()
  await page.getByRole('button', { name: '确认发布' }).click()
  await expect(page.getByText('发布成功，已全局生效')).toBeVisible()

  const beforeRuntime = queries.length
  await page.goto('/tests/e2e/designer-fixture.html?view=data')
  await expect.poll(() => queries.length).toBeGreaterThan(beforeRuntime)
  expect(queries.at(-1)).toEqual({})
  const runtimeFilter = page.getByRole('checkbox', { name: '为空', exact: true })
  await expect(runtimeFilter).not.toBeChecked()
  await page.locator('.search-card label.el-checkbox').filter({ hasText: /^为空$/ }).click()
  await expect(runtimeFilter).toBeChecked()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => queries.at(-1)).toEqual({ comment: true, comment_op: 'EMPTY' })
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(runtimeFilter).not.toBeChecked()
  await expect.poll(() => queries.at(-1)).toEqual({})
  expect(state.errors).toEqual([])
  expect(state.unexpected).toEqual([])
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
  await page.screenshot({ path: `../../.codex-artifacts/frontend-refactor/pc-designer-${mode}.png`, fullPage: true, animations: 'disabled' })
})

test('自动回退默认值随流程继承，只有主动修改才写入节点', async ({ page }) => {
  const state = await mock(page)
  await page.goto('/tests/e2e/designer-fixture.html?view=node&returnDefault=auto')
  await page.getByRole('tab', { name: '高级', exact: true }).click()
  const group = page.locator('.settings-section').filter({ has: page.getByText('选择节点回退', { exact: true }) })
  await expect(group.getByText('自动（默认）')).toBeVisible()
  const apply = page.getByRole('button', { name: '应用到画布' })
  await apply.click()
  await expect(page.getByText('已应用到画布，请点击「保存草稿」完成保存')).toBeVisible()
  await expect(page.getByTestId('task-return-config')).toHaveText('继承默认')

  await group.getByRole('button', { name: /选择节点回退/ }).click()
  await group.getByText('自动', { exact: true }).click()
  await page.getByRole('option', { name: '禁用', exact: true }).click()
  await apply.click()
  await expect(page.getByTestId('task-return-config')).toHaveText('{"mode":"OFF","targetNodeIds":[]}')
  expect(state.errors).toEqual([])
  expect(state.unexpected).toEqual([])
})
