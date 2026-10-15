import assert from 'node:assert/strict'
import test from 'node:test'
import { buildPublishPreview } from '../ui-config-publish-preview.js'

const itemsOf = preview => preview.groups.flatMap(group => group.items)

test('移动使用服务端按真实快照计算的业务位置，不展示稀疏排序键', () => {
  const operation = detail({ section: 'nodes', path: '/orderKey', beforeValue: 1000000, afterValue: 3000000,
    beforeDisplayValue: '第 1 项', afterDisplayValue: '第 3 项' })
  const item = itemsOf(buildPublishPreview({ changeDetails: [operation] }, 'FORM'))[0]
  assert.equal(item.changeType, 'MOVED')
  assert.equal(item.details[0].before, '第 1 项')
  assert.equal(item.details[0].after, '第 3 项')
  assert.equal(operation.beforeValue, 1000000, '展示转换不得改写接口原始补丁')
})

function detail(overrides = {}) {
  return {
    section: 'fields',
    itemId: 'field-name',
    itemLabel: '需求名称',
    previousItemLabel: '需求名称',
    changeType: 'UPDATED',
    path: '/width',
    beforeValue: 120,
    afterValue: 180,
    riskLevel: 'SAFE',
    reason: '仅影响展示或稳定布局',
    ...overrides
  }
}

test('多个属性合并为一个业务配置项，摘要计数与可见明细一致', () => {
  const result = buildPublishPreview({
    firstPublish: false,
    changeDetails: [
      detail(),
      detail({ path: '/align', beforeValue: 'left', afterValue: 'center' }),
      detail({ itemId: 'field-budget', itemLabel: '预算金额' })
    ]
  }, 'LIST')

  assert.equal(result.total, 2)
  assert.equal(result.counts.MODIFIED, 2)
  assert.equal(itemsOf(result).length, 2)
  const name = itemsOf(result).find(item => item.label === '需求名称')
  assert.equal(name.details.length, 2)
  assert.equal(name.reviewRequired, false)
  assert.equal(result.reviewItems.length, 0)
  assert.match(result.groups[0].label, /[\u4e00-\u9fff]/)
  assert.ok(name.details.every(row => /[\u4e00-\u9fff]/.test(row.label)))
  assert.match(String(name.details.find(row => /宽/.test(row.label)).before), /120/)
  assert.match(String(name.details.find(row => /宽/.test(row.label)).after), /180/)
})

test('同名不同 ID、跨分区相同 ID 均保留独立配置项', () => {
  const result = buildPublishPreview({
    changeDetails: [
      detail({ itemId: 'same-id', itemLabel: '名称' }),
      detail({ itemId: 'another-id', itemLabel: '名称' }),
      detail({ section: 'toolbarActions', itemId: 'same-id', itemLabel: '名称', path: '/buttonLabel', beforeValue: '查看', afterValue: '详情' })
    ]
  }, 'LIST')

  assert.equal(result.total, 3)
  assert.equal(new Set(itemsOf(result).map(item => item.key)).size, 3)
  assert.equal(result.counts.MODIFIED, 3)
})

test('改名保留发布前名称，避免复核人无法定位原配置', () => {
  const result = buildPublishPreview({ changeDetails: [detail({
    itemLabel: '申请金额',
    previousItemLabel: '预算金额',
    path: '/fieldName',
    beforeValue: '预算金额',
    afterValue: '申请金额'
  })] }, 'LIST')

  const item = itemsOf(result)[0]
  assert.equal(item.label, '申请金额')
  assert.equal(item.previousLabel, '预算金额')
  assert.equal(item.details[0].before, '预算金额')
  assert.equal(item.details[0].after, '申请金额')
})

test('首次发布的普通列表字段与表单字段不形成重复风险提醒', () => {
  for (const configType of ['LIST', 'FORM']) {
    const section = configType === 'LIST' ? 'fields' : 'nodes'
    const result = buildPublishPreview({
      firstPublish: true,
      changeDetails: ['name', 'code'].map((id, index) => detail({
        section,
        itemId: id,
        itemLabel: index ? '需求编号' : '需求名称',
        previousItemLabel: null,
        changeType: 'ADDED',
        path: '/',
        beforeValue: null,
        afterValue: configType === 'LIST'
          ? { id, fieldCode: id, fieldName: index ? '需求编号' : '需求名称', showInList: true, width: 180 }
          : { id, nodeKey: id, nodeType: 'FIELD', fieldCode: id, label: index ? '需求编号' : '需求名称', propsDocument: { required: false } },
        riskLevel: 'REVIEW',
        reason: '稳定配置条目发生新增或删除，发布前需要复核'
      }))
    }, configType)

    assert.equal(result.firstPublish, true)
    assert.equal(result.total, 2)
    assert.equal(result.counts.ADDED, 2)
    assert.equal(result.reviewItems.length, 0, `${configType} 首次启用普通字段无需逐个确认风险`)
    assert.ok(itemsOf(result).every(item => !item.reviewRequired))
  }
})

test('首次发布仍保留必填、校验、权限、数据源和提交行为的检查提示', () => {
  // 新条目的关键行为藏在整个对象中，不能仅凭根路径“新增”就抹去提醒。
  const cases = [
    { section: 'nodes', value: { id: 'required', label: '预算金额', propsDocument: { required: true } } },
    { section: 'nodes', value: { id: 'validation', label: '预算金额', propsDocument: { validationRules: [{ type: 'min', value: 0 }] } } },
    { section: 'toolbarActions', value: { id: 'permission', buttonLabel: '导出', permissionCode: 'budget:export' } },
    { section: 'nodes', value: { id: 'source', label: '客户', propsDocument: { dataSourceId: 'customer-source' } } },
    { section: 'eventBindings', value: { id: 'submit', eventCode: 'beforeSubmit', handlerCode: 'validate-budget' } }
  ]
  for (const { section, value } of cases) {
    const result = buildPublishPreview({
      firstPublish: true,
      changeDetails: [detail({
        section,
        itemId: value.id,
        itemLabel: value.label || value.buttonLabel || '提交前检查',
        changeType: 'ADDED',
        path: '/',
        beforeValue: null,
        afterValue: value,
        riskLevel: 'REVIEW'
      })]
    }, section === 'toolbarActions' ? 'LIST' : 'FORM')

    assert.equal(result.reviewItems.length, 1, `${value.id} 首次发布也必须检查关键行为`)
    assert.equal(itemsOf(result)[0].reviewRequired, true)
    assert.match(itemsOf(result)[0].impact, /[\u4e00-\u9fff]/)
  }
})

test('首次发布兼容持久化列表字段的内部属性和空 JSON 默认配置', () => {
  const result = buildPublishPreview({
    firstPublish: true,
    changeDetails: [detail({
      changeType: 'ADDED',
      path: '/',
      beforeValue: null,
      afterValue: {
        id: 'field-name',
        listConfigId: 'list-demand',
        fieldId: 'entity-field-name',
        fieldCode: 'name',
        fieldName: '需求名称',
        deleted: false,
        dataSourceType: 'ENTITY_FIELD',
        dataSourceConfig: '{}',
        queryType: 'EQ',
        showInList: true,
        isQuery: false,
        width: 180,
        sortOrder: 0
      },
      riskLevel: 'REVIEW',
      reason: '稳定配置条目发生新增或删除，发布前需要复核'
    })]
  }, 'LIST')

  assert.equal(result.total, 1)
  assert.equal(result.counts.ADDED, 1)
  assert.equal(result.reviewItems.length, 0)
  assert.equal(itemsOf(result)[0].reviewRequired, false)
  assert.doesNotMatch(JSON.stringify(itemsOf(result)[0].details), /list-demand|entity-field-name/)
})

test('首次发布检查序列化控件文档内的真实必填规则', () => {
  const result = buildPublishPreview({
    firstPublish: true,
    changeDetails: [detail({
      section: 'nodes',
      changeType: 'ADDED',
      path: '/',
      beforeValue: null,
      afterValue: {
        id: 'field-budget',
        label: '预算金额',
        nodeType: 'FIELD',
        propsDocument: '{"required":true,"width":180}'
      },
      riskLevel: 'REVIEW'
    })]
  }, 'FORM')

  assert.equal(result.reviewItems.length, 1)
  assert.match(result.reviewItems[0].impact, /必填|提交/)
  assert.ok(itemsOf(result)[0].details.some(row => row.after === '必填'))
})

test('普通列编辑器保存的复制与关闭脱敏默认值不产生首次发布提醒', () => {
  const result = buildPublishPreview({
    firstPublish: true,
    changeDetails: [detail({
      changeType: 'ADDED', path: '/', beforeValue: null, riskLevel: 'REVIEW',
      afterValue: {
        id: 'field-name', fieldName: '需求名称', showInList: true, isQuery: false,
        dataSourceType: 'ENTITY_FIELD',
        columnConfig: JSON.stringify({ fixed: '', minWidth: 100, showOverflowTooltip: true, quickCopy: false }),
        renderConfig: JSON.stringify({ maskingEnabled: false, maskPrefixLength: 3, maskSuffixLength: 4, emptyText: '-', labelMap: {}, statusMap: {} })
      }
    })]
  }, 'LIST')

  assert.equal(result.total, 1)
  assert.equal(result.reviewItems.length, 0)
  const rows = itemsOf(result)[0].details
  assert.ok(rows.some(row => /复制/.test(row.label) && /复制/.test(row.impact)))
  assert.ok(rows.some(row => /保留/.test(row.label) && /未启用|暂不/.test(row.impact)))
  assert.ok(rows.every(row => !row.label.includes('其他配置')))
})

test('启用或关闭脱敏以及改变保留位数仍提示检查敏感内容范围', () => {
  for (const operation of [
    { path: '/renderConfig/maskingEnabled', beforeValue: false, afterValue: true },
    { path: '/renderConfig/maskingEnabled', beforeValue: true, afterValue: false },
    { path: '/renderConfig/maskPrefixLength', beforeValue: 3, afterValue: 9 }
  ]) {
    const result = buildPublishPreview({ changeDetails: [detail(operation)] }, 'LIST')
    assert.equal(result.reviewItems.length, 1)
    assert.match(result.reviewItems[0].impact, /脱敏|敏感/)
    assert.match(result.reviewItems[0].impact, /检查/)
  }

  const initial = buildPublishPreview({ firstPublish: true, changeDetails: [detail({
    changeType: 'ADDED', path: '/', beforeValue: null,
    afterValue: { fieldName: '邮箱', renderConfig: { maskingEnabled: true, maskPrefixLength: 3, maskSuffixLength: 4 } }
  })] }, 'LIST')
  assert.equal(initial.reviewItems.length, 1)
  assert.ok(itemsOf(initial)[0].details.filter(row => /脱敏/.test(row.label)).every(row => row.reviewRequired))
})

test('显示映射中的业务键名不会被解释成必填规则或排列顺序', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ itemId: 'status-label', path: '/renderConfig/labelMap/required', beforeValue: '待处理', afterValue: '已完成', riskLevel: 'REVIEW' }),
    detail({ itemId: 'status-style', path: '/renderConfig/statusMap/orderKey', beforeValue: 'warning', afterValue: 'success', riskLevel: 'REVIEW' })
  ] }, 'LIST')

  const items = itemsOf(result)
  assert.equal(result.reviewItems.length, 0)
  assert.equal(result.counts.MODIFIED, 2)
  assert.equal(result.counts.MOVED, 0)
  const labelChange = items.find(item => item.details[0].before === '待处理').details[0]
  assert.equal(labelChange.after, '已完成')
  assert.match(labelChange.label, /映射.*required/)
  assert.doesNotMatch(labelChange.label, /必填/)
  assert.match(labelChange.impact, /显示|样式/)
})

test('整项新增和移除保留显示映射内的全部数据，不过滤 id 或转换布尔同名键', () => {
  const map = { id: '身份标识', required: '必需状态', fieldCode: '业务字段', blank: '', none: null, width: 180, enabled: false }
  for (const changeType of ['ADDED', 'REMOVED']) {
    const value = { fieldName: '需求状态', renderConfig: JSON.stringify({ labelMap: map, statusMap: { id: { required: false, width: 8 } } }) }
    const result = buildPublishPreview({ firstPublish: changeType === 'ADDED', changeDetails: [detail({
      changeType, path: '/', beforeValue: changeType === 'REMOVED' ? value : null,
      afterValue: changeType === 'ADDED' ? value : null
    })] }, 'LIST')
    const row = itemsOf(result)[0].details.find(item => item.label === '显示文本映射')
    const displayed = changeType === 'ADDED' ? row.after : row.before
    assert.deepEqual(JSON.parse(displayed), map)
    const status = itemsOf(result)[0].details.find(item => item.label === '状态样式映射')
    assert.deepEqual(JSON.parse(changeType === 'ADDED' ? status.after : status.before), { id: { required: false, width: 8 } })
  }
})

test('未知层级的 width 和 orderKey 不被当成安全样式或移动', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ itemId: 'unknown-width', path: '/futureRuntimePolicy/width', beforeValue: 120, afterValue: 180 }),
    detail({ itemId: 'unknown-order', path: '/futureRuntimePolicy/orderKey', beforeValue: 'old-policy', afterValue: 'new-policy' })
  ] }, 'LIST')

  assert.equal(result.reviewItems.length, 2)
  assert.equal(result.counts.MOVED, 0)
  assert.equal(result.counts.MODIFIED, 2)
  assert.ok(itemsOf(result).every(item => item.details[0].label.includes('其他配置')))
  assert.equal(itemsOf(result)[0].details[0].before, '120')
})

test('移除列表列使用旧名称并明确业务数据保留', () => {
  const result = buildPublishPreview({ changeDetails: [detail({
    itemLabel: '预算金额',
    previousItemLabel: '预算金额',
    changeType: 'REMOVED',
    path: '/',
    beforeValue: { id: 'field-budget', fieldCode: 'budget', fieldName: '预算金额' },
    afterValue: null,
    riskLevel: 'REVIEW'
  })] }, 'LIST')

  const item = itemsOf(result)[0]
  assert.equal(item.label, '预算金额')
  assert.equal(result.counts.REMOVED, 1)
  assert.match(item.impact, /数据.*保留|不.*删除.*数据/)
  assert.match(item.changeLabel, /移除/)
})

test('查询开关与常见枚举展示中文值而非内部编码', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ path: '/isQuery', beforeValue: false, afterValue: true, riskLevel: 'REVIEW' }),
    detail({ path: '/queryType', beforeValue: 'EQ', afterValue: 'LIKE', riskLevel: 'REVIEW' })
  ] }, 'LIST')

  const rows = itemsOf(result)[0].details
  assert.equal(rows.length, 2)
  for (const row of rows) {
    assert.match(String(row.before), /[\u4e00-\u9fff]/)
    assert.match(String(row.after), /[\u4e00-\u9fff]/)
    assert.doesNotMatch(String(row.before), /^(false|EQ)$/)
    assert.doesNotMatch(String(row.after), /^(true|LIKE)$/)
  }
})

test('纯排序计为移动，混合内容修改仍计为修改', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ itemId: 'pure-order', path: '/orderKey', beforeValue: 1000000, afterValue: 3000000 }),
    detail({ itemId: 'width-and-order', path: '/orderKey', beforeValue: 3000000, afterValue: 1000000 }),
    detail({ itemId: 'width-and-order', path: '/width', beforeValue: 120, afterValue: 180 })
  ] }, 'LIST')

  assert.equal(result.total, 2)
  assert.equal(result.counts.MOVED, 1)
  assert.equal(result.counts.MODIFIED, 1)
  assert.equal(itemsOf(result).filter(item => item.changeType === 'MOVED').length, 1)
})

test('一个字段只有部分属性需检查时，风险按业务项聚合且普通属性不被误标', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ section: 'nodes', path: '/propsDocument/required', beforeValue: false, afterValue: true, riskLevel: 'REVIEW' }),
    detail({ section: 'nodes', path: '/label', beforeValue: '需求名称', afterValue: '需求标题', riskLevel: 'SAFE' })
  ] }, 'FORM')

  assert.equal(result.total, 1)
  assert.equal(result.reviewItems.length, 1)
  assert.equal(itemsOf(result)[0].reviewRequired, true)
  assert.equal(itemsOf(result)[0].details.filter(row => row.reviewRequired).length, 1)
  assert.match(result.reviewItems[0].impact, /必填|提交/)
})

test('未知路径保留复核提醒及技术定位信息，不伪装成安全展示调整', () => {
  const path = '/futureRuntimePolicy/executionMode'
  const result = buildPublishPreview({ changeDetails: [detail({
    path,
    beforeValue: 'LEGACY',
    afterValue: 'NEW',
    riskLevel: 'REVIEW',
    reason: '未知配置路径需要复核，但不阻止发布'
  })] }, 'LIST')

  const item = itemsOf(result)[0]
  assert.equal(result.reviewItems.length, 1)
  assert.equal(item.reviewRequired, true)
  assert.match(JSON.stringify(item.technicalDetails), /futureRuntimePolicy/)
  assert.match(item.impact, /检查|复核|确认|核对/)
})

test('兼容旧预检接口：保留业务名称、普通变更以及风险列表独有的变更', () => {
  const result = buildPublishPreview({
    changed: true,
    changedItems: [
      { section: 'fields', id: 'column-a', label: '需求名称', changeType: 'UPDATED', changedFields: ['width'] },
      { section: 'fields', id: 'column-b', label: '预算金额', changeType: 'REMOVED', changedFields: [] }
    ],
    riskItems: [
      { section: 'fields', itemId: 'column-b', path: '/', riskLevel: 'REVIEW', reason: '移除字段需检查相关展示' },
      { section: 'eventBindings', itemId: 'event-extra', path: '/handlerCode', riskLevel: 'REVIEW', reason: '提交前处理逻辑发生变化' }
    ]
  }, 'LIST')

  const items = itemsOf(result)
  assert.equal(result.total, 3)
  assert.equal(items.length, 3)
  assert.ok(items.some(item => item.label === '需求名称'))
  assert.ok(items.some(item => item.label === '预算金额'))
  assert.ok(result.reviewItems.length >= 2)
  assert.match(JSON.stringify(result), /提交前处理逻辑发生变化/)
})

test('新接口使用 changeDetails 统一计数，不混入旧差异重复项', () => {
  const result = buildPublishPreview({
    changeDetails: [detail()],
    changedItems: [
      { section: 'fields', id: 'field-name', label: '需求名称', changeType: 'UPDATED' },
      { section: 'legacyFields', id: 'old-field-name', label: '需求名称', changeType: 'UPDATED' }
    ]
  }, 'LIST')

  assert.equal(result.total, 1)
  assert.equal(itemsOf(result).length, 1)
})

test('无变化的预检返回空分组与零计数', () => {
  const result = buildPublishPreview({ firstPublish: false, changed: false, changeDetails: [] }, 'FORM')

  assert.equal(result.firstPublish, false)
  assert.equal(result.total, 0)
  assert.deepEqual(result.groups, [])
  assert.deepEqual(result.reviewItems, [])
  assert.deepEqual(result.counts, { ADDED: 0, MODIFIED: 0, REMOVED: 0, MOVED: 0 })
})

test('工具栏按钮空默认值归一化后只展示真实的启用状态变更', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ section: 'toolbarActions', itemId: 'export', itemLabel: '导出', path: '/customMode', beforeValue: null, afterValue: '', riskLevel: 'REVIEW' }),
    detail({ section: 'toolbarActions', itemId: 'export', itemLabel: '导出', path: '/icon', beforeValue: null, afterValue: '' }),
    detail({ section: 'toolbarActions', itemId: 'export', itemLabel: '导出', path: '/customHandler', beforeValue: null, afterValue: '', riskLevel: 'REVIEW' }),
    detail({ section: 'toolbarActions', itemId: 'export', itemLabel: '导出', path: '/perm', beforeValue: null, afterValue: '', riskLevel: 'REVIEW' }),
    detail({ section: 'toolbarActions', itemId: 'export', itemLabel: '导出', path: '/actionParams', beforeValue: null, afterValue: {}, riskLevel: 'REVIEW' }),
    detail({ section: 'toolbarActions', itemId: 'export', itemLabel: '导出', path: '/enabled', beforeValue: true, afterValue: false, riskLevel: 'REVIEW' })
  ] }, 'LIST')

  assert.equal(result.total, 1)
  assert.equal(result.counts.MODIFIED, 1)
  const item = itemsOf(result)[0]
  assert.equal(item.label, '导出')
  assert.equal(item.details.length, 1)
  assert.match(item.details[0].label, /启用/)
  assert.notEqual(item.details[0].before, item.details[0].after)
})

test('表单节点已知属性中的 null 与空文本图标不构成业务变更', () => {
  const result = buildPublishPreview({ changeDetails: [detail({
    section: 'nodes', path: '/propsDocument/icon', beforeValue: null, afterValue: ''
  })] }, 'FORM')

  assert.equal(result.total, 0)
  assert.deepEqual(result.groups, [])
  assert.deepEqual(result.reviewItems, [])
  assert.deepEqual(result.counts, { ADDED: 0, MODIFIED: 0, REMOVED: 0, MOVED: 0 })
  assert.equal(result.equivalentOnly, true)
})

test('过滤全等值与无效空值差异后同时移除空配置项、空分组和风险计数', () => {
  const result = buildPublishPreview({ changed: true, changeDetails: [
    detail({ section: 'toolbarActions', itemId: 'button-a', path: '/customMode', beforeValue: null, afterValue: '', riskLevel: 'REVIEW' }),
    detail({ section: 'toolbarActions', itemId: 'button-b', path: '/enabled', beforeValue: true, afterValue: true, riskLevel: 'REVIEW' }),
    detail({ section: 'fields', itemId: 'name', path: '/width', beforeValue: 180, afterValue: 180 }),
    detail({ section: 'list', itemId: 'list', path: '/viewConfig', beforeValue: { table: { border: false } }, afterValue: { table: { border: false } }, riskLevel: 'REVIEW' })
  ] }, 'LIST')

  assert.equal(result.total, 0)
  assert.deepEqual(result.groups, [])
  assert.deepEqual(result.reviewItems, [])
  assert.deepEqual(result.counts, { ADDED: 0, MODIFIED: 0, REMOVED: 0, MOVED: 0 })
})

test('已知 JSON 配置忽略对象键顺序与字符串文档和对象的表示差异', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ section: 'toolbarActions', itemId: 'button', path: '/actionParams', beforeValue: { targetFormId: 'form-a', targetFormMode: 'VIEW' }, afterValue: { targetFormMode: 'VIEW', targetFormId: 'form-a' } }),
    detail({ section: 'nodes', itemId: 'node', path: '/propsDocument', beforeValue: '{"required":false,"componentProps":{"clearable":true,"placeholder":"请输入"}}', afterValue: { componentProps: { placeholder: '请输入', clearable: true }, required: false } }),
    detail({ itemId: 'status', path: '/renderConfig', beforeValue: '{"labelMap":{"open":"处理中","closed":"已关闭"},"emptyText":"-"}', afterValue: '{"emptyText":"-","labelMap":{"closed":"已关闭","open":"处理中"}}' })
  ] }, 'LIST')

  assert.equal(result.total, 0)
  assert.deepEqual(result.groups, [])
  assert.deepEqual(result.reviewItems, [])
})

test('未知字段的 JSON 文本不能按已知文档规则转为对象后过滤', () => {
  const result = buildPublishPreview({ changeDetails: [detail({
    path: '/futureRuntimePolicy/rawValue',
    beforeValue: '{"mode":"STRICT"}',
    afterValue: { mode: 'STRICT' },
    riskLevel: 'REVIEW'
  })] }, 'LIST')

  assert.equal(result.total, 1)
  assert.equal(itemsOf(result)[0].details.length, 1)
  assert.equal(result.reviewItems.length, 1)
})

test('真假切换、数值变更及清空现有配置仍保留在发布预览中', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ itemId: 'query', path: '/isQuery', beforeValue: false, afterValue: true }),
    detail({ itemId: 'width', path: '/width', beforeValue: 0, afterValue: 1 }),
    detail({ section: 'toolbarActions', itemId: 'button', path: '/icon', beforeValue: 'Download', afterValue: '' }),
    detail({ section: 'toolbarActions', itemId: 'action', path: '/actionParams', beforeValue: { targetFormId: 'form-a' }, afterValue: {} })
  ] }, 'LIST')

  assert.equal(result.total, 4)
  assert.equal(result.counts.MODIFIED, 4)
  assert.equal(itemsOf(result).length, 4)
  assert.ok(itemsOf(result).every(item => item.details.length === 1))
})

test('整项新增或移除不能因对象只含内部标识而被空值过滤误删', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ itemId: 'added', itemLabel: '新增字段', changeType: 'ADDED', path: '/', beforeValue: null, afterValue: { id: 'added' } }),
    detail({ itemId: 'removed', itemLabel: '移除字段', changeType: 'REMOVED', path: '/', beforeValue: { id: 'removed' }, afterValue: null })
  ] }, 'LIST')

  assert.equal(result.total, 2)
  assert.equal(result.counts.ADDED, 1)
  assert.equal(result.counts.REMOVED, 1)
  assert.ok(itemsOf(result).every(item => item.details.length >= 1))
})

test('旧接口未提供前后值时仍保留变更，不能把两个缺失值视作相等', () => {
  const result = buildPublishPreview({
    changedItems: [{ section: 'toolbarActions', id: 'export', label: '导出', changeType: 'UPDATED', changedFields: ['icon', 'customMode'] }],
    riskItems: [{ section: 'toolbarActions', itemId: 'export', path: '/customMode', riskLevel: 'REVIEW', reason: '按钮执行方式发生变化' }]
  }, 'LIST')

  assert.equal(result.total, 1)
  assert.equal(result.counts.MODIFIED, 1)
  assert.ok(itemsOf(result)[0].details.length >= 1)
  assert.equal(result.reviewItems.length, 1)
  assert.match(JSON.stringify(result), /按钮执行方式发生变化/)
})

test('显示映射的 null 与空字符串是不同业务值，叶子与整体文档变更都保留', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ itemId: 'mapping-leaf', path: '/renderConfig/labelMap/required', beforeValue: null, afterValue: '' }),
    detail({ itemId: 'mapping-document', path: '/renderConfig', beforeValue: '{"labelMap":{"required":null}}', afterValue: '{"labelMap":{"required":""}}' })
  ] }, 'LIST')

  assert.equal(result.total, 2)
  assert.equal(result.counts.MODIFIED, 2)
  assert.ok(itemsOf(result).every(item => item.details.length === 1))
  const leaf = itemsOf(result).find(item => item.details[0].label.includes('required')).details[0]
  assert.notEqual(leaf.before, leaf.after)
})

test('未知字段的字符串和数字即使显示文本相同也不能被删除', () => {
  const result = buildPublishPreview({ changeDetails: [detail({
    path: '/futureRuntimePolicy/value', beforeValue: '1', afterValue: 1, riskLevel: 'REVIEW'
  })] }, 'LIST')

  assert.equal(result.total, 1)
  assert.equal(result.counts.MODIFIED, 1)
  assert.equal(itemsOf(result)[0].details.length, 1)
  assert.equal(result.reviewItems.length, 1)
  assert.notEqual(itemsOf(result)[0].details[0].before, itemsOf(result)[0].details[0].after)
})

test('表单执行方式及未知配置不沿用列表按钮的空值默认规则', () => {
  const result = buildPublishPreview({ changeDetails: [
    detail({ section: 'nodes', itemId: 'button', path: '/propsDocument/customMode', beforeValue: null, afterValue: '' }),
    detail({ itemId: 'unknown', path: '/futureRuntimePolicy/value', beforeValue: null, afterValue: '' }),
    detail({ itemId: 'unknown-object', path: '/futureRuntimePolicy/value', beforeValue: null, afterValue: {} })
  ] }, 'FORM')
  assert.equal(result.total, 3)
  assert.ok(itemsOf(result).every(item => item.details[0].before !== item.details[0].after))
})
