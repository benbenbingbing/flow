import assert from 'node:assert/strict'
import {
  EMBED_FIELD_TYPES,
  EmbedSchemaError,
  buildEmbedListQuery,
  normalizeEmbedBootstrap,
  normalizeEmbedExternalSchema,
  projectEmbedPage,
  projectEmbedRecord,
  projectEmbedSelection
} from '../src/embed/projection/normalizeEmbedSchema.js'

assert.deepEqual(EMBED_FIELD_TYPES, [
  'TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME', 'TIME', 'SELECT', 'MULTI_SELECT'
])

const bootstrap = normalizeEmbedBootstrap({
  session: {
    id: 'ems_001',
    expiresAt: '2099-08-27T09:00:00.000Z',
    idleExpiresAt: '2099-08-27T08:35:00.000Z',
    tokenHash: 'must-drop'
  },
  actor: {
    displayName: '张三',
    permissionCodes: ['admin']
  },
  view: {
    key: 'supplier-work-orders',
    name: '供应商工单',
    surfaceType: 'LIST',
    revision: 7,
    entryMode: 'LIST',
    fixedFilters: [{ code: 'supplierId' }]
  },
  capabilities: ['LIST_QUERY', 'SELECTION_RETURN', 'INTERNAL_ADMIN', 'LIST_QUERY'],
  ui: {
    locale: 'zh-CN',
    theme: 'light',
    showSearch: true,
    showPagination: true,
    showToolbar: true,
    pageSize: 20,
    heightMode: 'AUTO'
  },
  limits: {
    maxPageSize: 100,
    maxPayloadBytes: 1048576,
    maxSelectionSize: 2
  },
  provider: { serviceId: 'internal-service' }
})

assert.deepEqual(bootstrap.capabilities, ['LIST_QUERY', 'SELECTION_RETURN'])
assert.deepEqual(bootstrap.actor, { displayName: '张三' })
assert.equal(JSON.stringify(bootstrap).includes('permissionCodes'), false)
assert.equal(JSON.stringify(bootstrap).includes('fixedFilters'), false)
assert.equal(JSON.stringify(bootstrap).includes('provider'), false)

const rawSchema = {
  view: {
    key: 'supplier-work-orders',
    surfaceType: 'LIST',
    revision: 7
  },
  entity: {
    code: 'work_order',
    name: '工单',
    physicalTable: 'biz_work_order'
  },
  list: {
    selection: {
      mode: 'SINGLE',
      valueField: 'code',
      returnableFields: ['code', 'unknown', 'secretField', 'code']
    },
    pagination: { allowTotal: false, maxPageSize: 100 },
    columns: [
      {
        code: 'code',
        label: '工单号',
        type: 'TEXT',
        width: 180,
        sortable: true,
        renderComponent: 'RemoteHtml'
      },
      {
        code: 'status',
        label: '状态',
        type: 'SELECT',
        width: 120,
        options: [{ label: '处理中', value: 'PROCESSING', permissionCode: 'secret' }]
      },
      { code: 'startTime', label: '开始时间', type: 'TIME', width: 100 },
      { code: 'secretField', label: '秘密', type: 'REMOTE_COMPONENT' }
    ],
    filters: [
      { code: 'code', label: '工单号', type: 'TEXT', operator: 'CONTAINS' },
      {
        code: 'status',
        label: '状态',
        type: 'SELECT',
        operator: 'EQ',
        options: [{ label: '处理中', value: 'PROCESSING' }]
      },
      { code: 'startTime', label: '开始时间', type: 'TIME', operator: 'EQ' },
      { code: 'secretField', label: '秘密', type: 'TEXT', operator: 'LIKE' }
    ],
    queryUrl: 'https://evil.example/query'
  },
  form: { script: 'alert(1)' },
  actions: [
    {
      key: 'view',
      label: '查看',
      placement: 'ROW',
      kind: 'NAVIGATION',
      transport: 'LOCAL_FORM',
      recordMode: 'CURRENT',
      selectionMode: 'NONE',
      dataSchema: { internal: true },
      inputSchema: { script: 'evil()' },
      requiresRecordVersion: false,
      idempotencyRequired: false,
      permissionCode: 'entity:read',
      url: 'https://evil.example'
    },
    {
      key: 'arbitrary',
      label: '任意请求',
      placement: 'ROW',
      kind: 'MUTATION',
      transport: 'REMOTE_URL',
      recordMode: 'CURRENT',
      selectionMode: 'NONE'
    },
    {
      key: 'edit',
      label: '编辑',
      placement: 'ROW',
      kind: 'MUTATION',
      transport: 'RECORD_UPDATE',
      recordMode: 'CURRENT',
      selectionMode: 'NONE'
    }
  ]
}

const schema = normalizeEmbedExternalSchema(rawSchema)
assert.deepEqual(schema.list.columns.map(column => column.code), ['code', 'status', 'startTime'])
assert.deepEqual(schema.list.filters.map(filter => filter.operator), ['CONTAINS', 'EQ', 'EQ'])
assert.deepEqual(schema.list.selection, {
  mode: 'SINGLE',
  valueField: 'code',
  returnableFields: ['code']
})
assert.equal(schema.list.columns[0].sortable, false, 'V1 必须强制禁用客户端排序')
assert.deepEqual(schema.actions.map(action => action.key), ['view'])
assert.equal(schema.actions[0].dataSchema, null)
assert.equal(schema.actions[0].inputSchema, null)
const serializedSchema = JSON.stringify(schema)
for (const forbidden of [
  'physicalTable',
  'renderComponent',
  'queryUrl',
  'permissionCode',
  'script',
  'REMOTE_URL'
]) {
  assert.equal(serializedSchema.includes(forbidden), false, `${forbidden} 不得进入投影`)
}

const record = projectEmbedRecord({
  id: '2080000000000000001',
  recordVersion: null,
  values: {
    code: 'WO-20260827-001',
    status: 'PROCESSING',
    startTime: '08:30:00',
    supplierSecret: 'must-drop'
  },
  meta: {
    updatedAt: '2026-08-27T08:20:00.000Z',
    createdBy: 'internal-user-id'
  },
  actions: {
    view: { visible: true, enabled: true, reason: null, permissionCode: 'secret' },
    delete: { visible: true, enabled: true }
  },
  internalEntityId: 'secret'
}, schema)

assert.deepEqual(record.values, {
  code: 'WO-20260827-001',
  status: 'PROCESSING',
  startTime: '08:30:00'
})
assert.throws(
  () => projectEmbedRecord({
    id: '2080000000000000001', recordVersion: 3, values: {}
  }, schema),
  error => error instanceof EmbedSchemaError
    && error.errorCode === 'EMBED_RECORD_VERSION_UNSUPPORTED'
)
assert.deepEqual(record.meta, { updatedAt: '2026-08-27T08:20:00.000Z' })
assert.deepEqual(Object.keys(record.actions), ['view'])
assert.equal(JSON.stringify(record).includes('supplierSecret'), false)
assert.equal(JSON.stringify(record).includes('createdBy'), false)
assert.equal(JSON.stringify(record).includes('permissionCode'), false)

const page = projectEmbedPage({
  items: [record, { id: '', recordVersion: null, values: {} }],
  hasMore: true,
  pageNum: 1,
  pageSize: 20,
  total: 987654,
  sql: 'select * from secret'
}, schema)
assert.equal(page.items.length, 1)
assert.equal(page.hasMore, true)
assert.equal(Object.hasOwn(page, 'total'), false, 'allowTotal=false 时必须抑制总量侧信道')

const totalSchema = normalizeEmbedExternalSchema({
  ...rawSchema,
  list: {
    ...rawSchema.list,
    pagination: { allowTotal: true, maxPageSize: 100 }
  }
})
assert.equal(projectEmbedPage({
  items: [record],
  hasMore: false,
  pageNum: 1,
  pageSize: 20,
  total: 1
}, totalSchema).total, 1)

const timeOnlySchema = normalizeEmbedExternalSchema({
  view: { key: 'time-view', surfaceType: 'LIST', revision: 1 },
  entity: { code: 'time_entry', name: '时间记录' },
  list: {
    selection: { mode: 'NONE', valueField: 'id', returnableFields: [] },
    pagination: { allowTotal: false, maxPageSize: 20 },
    columns: [
      { code: 'startTime', label: '开始时间', type: 'TIME' },
      { code: 'legacyText', label: '旧长文本', type: 'TEXTAREA' },
      { code: 'legacyInteger', label: '旧整数', type: 'INTEGER' },
      { code: 'legacyDecimal', label: '旧小数', type: 'DECIMAL' }
    ],
    filters: [{ code: 'startTime', label: '开始时间', type: 'TIME', operator: 'EQ' }]
  },
  actions: []
})
assert.deepEqual(timeOnlySchema.list.columns.map(column => column.type), ['TIME'])
assert.deepEqual(timeOnlySchema.list.filters.map(filter => filter.type), ['TIME'])
assert.deepEqual(projectEmbedRecord({
  id: 'time-1', recordVersion: null, values: { startTime: '09:15:00' }
}, timeOnlySchema).values, { startTime: '09:15:00' })

const selectionBoundarySchema = normalizeEmbedExternalSchema({
  view: { key: 'selection-boundary', surfaceType: 'LIST', revision: 1 },
  entity: { code: 'selection_record', name: '选择记录' },
  list: {
    selection: {
      mode: 'MULTIPLE',
      valueField: 'id',
      returnableFields: ['description', 'tags']
    },
    pagination: { allowTotal: false, maxPageSize: 100 },
    columns: [
      { code: 'description', label: '说明', type: 'TEXT' },
      { code: 'tags', label: '标签', type: 'MULTI_SELECT' }
    ],
    filters: []
  },
  actions: []
})
const maximumRecordId = `A${'b'.repeat(127)}`
const boundarySelection = projectEmbedSelection([{
  id: maximumRecordId,
  recordVersion: null,
  values: {
    description: 'x'.repeat(2049),
    tags: Array.from({ length: 101 }, (_, index) => `tag-${index}`)
  }
}], selectionBoundarySchema)
assert.equal(boundarySelection[0].id, maximumRecordId)
assert.equal(boundarySelection[0].values.description.length, 2048)
assert.equal(boundarySelection[0].values.tags.length, 100)
assert.equal(projectEmbedRecord({
  id: 'A'.repeat(129), recordVersion: null, values: {}
}, selectionBoundarySchema), null, '超长 RecordId 不得通过截断改变身份')
assert.equal(projectEmbedRecord({
  id: 'record/1', recordVersion: null, values: {}
}, selectionBoundarySchema), null, 'RecordId 必须满足 OpenAPI pattern')
assert.equal(projectEmbedRecord({
  id: true, recordVersion: null, values: {}
}, selectionBoundarySchema), null, '非字符串/数字 RecordId 不得转换后放行')

const query = buildEmbedListQuery({
  code: 'WO-2026',
  status: 'PROCESSING',
  fixedFilters: { supplierId: 'other' },
  context: { supplierId: 'other' },
  sort: 'secret desc'
}, schema, { pageNum: -1, pageSize: 1000 })
assert.deepEqual(query, {
  pageNum: 1,
  pageSize: 100,
  filters: [
    { field: 'code', value: 'WO-2026' },
    { field: 'status', value: 'PROCESSING' }
  ]
})
assert.equal(JSON.stringify(query).includes('fixedFilters'), false)
assert.equal(JSON.stringify(query).includes('context'), false)
assert.equal(JSON.stringify(query).includes('sort'), false)

const operatorSchema = normalizeEmbedExternalSchema({
  ...rawSchema,
  list: {
    ...rawSchema.list,
    filters: [
      { code: 'exactCode', label: '精确编码', type: 'TEXT', operator: 'EQ' },
      { code: 'title', label: '标题', type: 'TEXT', operator: 'CONTAINS' },
      { code: 'amountGt', label: '金额大于', type: 'NUMBER', operator: 'GT' },
      { code: 'amountGte', label: '金额不小于', type: 'NUMBER', operator: 'GTE' },
      { code: 'amountLt', label: '金额小于', type: 'NUMBER', operator: 'LT' },
      { code: 'amountLte', label: '金额不大于', type: 'NUMBER', operator: 'LTE' },
      {
        code: 'statuses',
        label: '状态',
        type: 'MULTI_SELECT',
        operator: 'IN',
        options: [
          { label: '处理中', value: 'PROCESSING' },
          { label: '已完成', value: 'DONE' }
        ]
      },
      { code: 'period', label: '日期范围', type: 'DATE', operator: 'BETWEEN' }
    ]
  }
})
const operatorQuery = buildEmbedListQuery({
  exactCode: 'WO-100',
  title: 'pump',
  amountGt: '10',
  amountGte: '11',
  amountLt: '20',
  amountLte: '21',
  statuses: ['PROCESSING', 'DONE'],
  period: ['2026-01-01', '2026-12-31'],
  title_op: 'EQ',
  period_start: '1900-01-01',
  period_end: '2999-12-31'
}, operatorSchema)
assert.deepEqual(operatorQuery.filters, [
  { field: 'exactCode', value: 'WO-100' },
  { field: 'title', value: 'pump' },
  { field: 'amountGt', value: 10 },
  { field: 'amountGte', value: 11 },
  { field: 'amountLt', value: 20 },
  { field: 'amountLte', value: 21 },
  { field: 'statuses', values: ['PROCESSING', 'DONE'] },
  { field: 'period', range: { start: '2026-01-01', end: '2026-12-31' } }
])
assert.equal(JSON.stringify(operatorQuery).includes('title_op'), false)
assert.equal(JSON.stringify(operatorQuery).includes('period_start'), false)
assert.equal(JSON.stringify(operatorQuery).includes('period_end'), false)

assert.deepEqual(projectEmbedSelection([record, record, record], schema, 2), [
  { id: record.id, values: { code: 'WO-20260827-001' } },
  { id: record.id, values: { code: 'WO-20260827-001' } }
])

assert.throws(
  () => normalizeEmbedExternalSchema({
    ...rawSchema,
    list: {
      ...rawSchema.list,
      columns: [{ code: 'unsafe', label: '不安全', type: 'HTML' }]
    }
  }),
  error => error instanceof EmbedSchemaError
    && error.errorCode === 'EMBED_SCHEMA_COLUMNS_EMPTY'
)

assert.throws(
  () => normalizeEmbedExternalSchema({
    ...rawSchema,
    view: { ...rawSchema.view, revision: 0 }
  }),
  error => error instanceof EmbedSchemaError
    && error.errorCode === 'EMBED_SCHEMA_VIEW_INVALID'
)

console.log('embed projection tests passed')
