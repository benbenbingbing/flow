import assert from 'node:assert/strict'
import { createEmbedRuntimeApi } from '../src/embed/api/embedRuntimeApi.js'
import {
  EmbedFormProjectionError,
  buildEmbedCreateEvaluationRequest,
  buildEmbedCreateRequest,
  buildEmbedLookupQuery,
  buildEmbedOptionQuery,
  normalizeEmbedCreateResult,
  normalizeEmbedFormResult,
  normalizeEmbedLookupPage,
  normalizeEmbedRecordResult,
  mergeEmbedCreateDraft,
  projectEmbedFormSaved
} from '../src/embed/projection/normalizeEmbedForm.js'
import { createEmbedRemoteChoices } from '../src/embed/runtime/embedRemoteChoices.js'
import {
  EMBED_RUNTIME_STATES,
  createEmbedRuntimeController
} from '../src/embed/runtime/embedRuntimeController.js'

const TEST_LAUNCH_CODE = 'A'.repeat(43)
const PARENT_NONCE = Buffer.alloc(32, 201).toString('base64url')
const CHILD_NONCE = Buffer.alloc(32, 151).toString('base64url')

const entryConfig = Object.freeze({
  launchId: 'lch_form_0123456789ab',
  expectedParentOrigin: 'https://portal.example.com',
  channelId: 'channel-form-1234',
  protocolVersion: 'flow-embed/1'
})

function bootstrap(mode = 'VIEW', capabilities = ['RECORD_VIEW']) {
  return {
    session: {
      id: 'ems_form_001',
      expiresAt: '2099-08-27T09:00:00.000Z',
      idleExpiresAt: '2099-08-27T08:35:00.000Z'
    },
    actor: { displayName: '张三' },
    view: {
      key: 'supplier-work-order-form',
      name: '供应商工单',
      surfaceType: 'FORM',
      revision: 7,
      entryMode: mode
    },
    capabilities,
    ui: {
      locale: 'zh-CN', theme: 'light', showSearch: false,
      showPagination: false, showToolbar: true, pageSize: 20, heightMode: 'AUTO'
    },
    limits: { maxPageSize: 50, maxPayloadBytes: 1048576, maxSelectionSize: 20 }
  }
}

function formResult(mode = 'VIEW') {
  return {
    mode,
    record: mode === 'CREATE' ? null : {
      id: 'record-001',
      recordVersion: null,
      values: {
        title: '安全工单', status: 'OPEN', assignee: 'user-1', internalSecret: 'drop'
      },
      meta: {
        createdAt: '2026-08-27T08:00:00.000Z',
        updatedAt: '2026-08-27T08:20:00.000Z'
      }
    },
    form: {
      title: '工单表单',
      layout: { type: 'GRID', component: 'InternalGrid' },
      returnableFields: ['title'],
      fields: [
        {
          code: 'title', label: '标题', type: 'TEXT', required: true,
          readOnly: false, hidden: false, defaultValue: '',
          validation: { minLength: 2, maxLength: 100, eval: 'evil()' },
          options: [], optionSource: null, lookupSource: null,
          layout: { span: 24 }, fieldState: { visible: true, writable: true },
          component: 'RemoteHtml', html: '<img onerror=alert(1)>'
        },
        {
          code: 'status', label: '状态', type: 'SELECT', required: false,
          readOnly: false, hidden: false, defaultValue: null, validation: {}, options: null,
          optionSource: {
            mode: 'RUNTIME',
            queryUrl: '/api/embed/v1/runtime/form/fields/status/options/query',
            dependencyPolicy: [
              { code: 'title', source: 'CLIENT_WRITABLE' },
              { code: 'tenantId', source: 'CONTEXT' },
              { code: 'ownerId', source: 'CURRENT_RECORD_READONLY' }
            ]
          },
          lookupSource: null,
          layout: { span: 12 }, fieldState: { visible: true, writable: true }
        },
        {
          code: 'assignee', label: '处理人', type: 'TEXT', required: false,
          readOnly: false, hidden: false, defaultValue: null, validation: {}, options: null,
          optionSource: null, lookupSource: null,
          layout: { span: 12 }, fieldState: { visible: true, writable: true }
        },
        {
          code: 'hiddenValue', label: '隐藏值', type: 'TEXT', required: false,
          readOnly: true, hidden: true, defaultValue: null, validation: {}, options: [],
          optionSource: null, lookupSource: null,
          layout: { span: 24 }, fieldState: { visible: false, writable: false }
        },
        {
          code: 'unsafe', label: '自定义 HTML', type: 'HTML', required: false,
          readOnly: false, hidden: false, defaultValue: null, validation: {}, options: [],
          optionSource: null, lookupSource: null,
          layout: { span: 24 }, fieldState: { visible: true, writable: true },
          script: 'alert(1)'
        }
      ],
      actions: mode === 'VIEW' ? [] : [{
        key: 'save', label: '保存', placement: 'FORM', kind: 'MUTATION',
        transport: mode === 'CREATE' ? 'RECORD_CREATE' : 'RECORD_UPDATE',
        recordMode: mode === 'CREATE' ? 'NONE' : 'CURRENT', selectionMode: 'NONE',
        dataSchema: { internal: true }, inputSchema: null,
        requiresRecordVersion: mode === 'EDIT', idempotencyRequired: true,
        enabled: true, disabledReason: null
      }]
    }
  }
}

const normalized = normalizeEmbedFormResult(
  formResult('CREATE'),
  bootstrap('CREATE', ['RECORD_CREATE'])
)
assert.deepEqual(normalized.fields.map(field => field.code), ['title', 'status', 'assignee'])
assert.equal(normalized.fields.every(field => field.writable), true)
assert.deepEqual(normalized.fields[1].optionSource.dependencies, [
  { code: 'title', source: 'CLIENT_WRITABLE' },
  { code: 'tenantId', source: 'CONTEXT' },
  { code: 'ownerId', source: 'CURRENT_RECORD_READONLY' }
])
assert.equal(normalized.actions[0].key, 'save')
assert.equal(normalized.actions[0].transport, 'RECORD_CREATE')
const serialized = JSON.stringify(normalized)
for (const forbidden of ['RemoteHtml', '<img', 'evil()', 'internalSecret', 'InternalGrid', 'script']) {
  assert.equal(serialized.includes(forbidden), false, `${forbidden} 不得进入基础表单投影`)
}

const viewProjection = normalizeEmbedFormResult(
  formResult('VIEW'),
  bootstrap('VIEW', ['RECORD_VIEW', 'RECORD_UPDATE'])
)
assert.equal(viewProjection.fields.every(field => field.readOnly), true, 'VIEW 必须强制只读')
assert.equal(viewProjection.actions.length, 0)

const recordForm = normalizeEmbedRecordResult({
  record: formResult('VIEW').record,
  fieldStates: {
    title: { visible: true, readOnly: true, required: true },
    status: { visible: true, readOnly: false, required: false },
    assignee: { visible: true, readOnly: false, required: false }
  },
  actions: { save: { visible: true, enabled: true } },
  internalPermission: 'drop'
}, viewProjection)
assert.equal(recordForm.fields.every(field => field.readOnly), true)
assert.equal(recordForm.record.values.internalSecret, undefined)

const optionQuery = buildEmbedOptionQuery(normalized.fields[1], {
  title: 'WO', tenantId: 'browser-must-not-send', context: { tenantId: 'evil' }
}, { mode: 'CREATE', keyword: 'open', pageNum: -1, pageSize: 500 })
assert.deepEqual(optionQuery, {
  mode: 'CREATE', keyword: 'open', dependencies: { title: 'WO' }, pageNum: 1, pageSize: 50
})
assert.throws(
  () => buildEmbedLookupQuery(normalized.fields[2], {}, {}),
  error => error.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
)
assert.throws(
  () => normalizeEmbedLookupPage({
    items: [{ id: 'user-1', label: '张三' }],
    hasMore: false, pageNum: 1, pageSize: 20
  }),
  error => error.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
)

const lookupFieldResult = formResult('CREATE')
lookupFieldResult.form.fields[2] = {
  ...lookupFieldResult.form.fields[2],
  type: 'LOOKUP',
  lookupSource: {
    mode: 'RUNTIME',
    queryUrl: '/api/embed/v1/runtime/form/fields/assignee/lookups/query'
  }
}
assert.throws(
  () => normalizeEmbedFormResult(
    lookupFieldResult,
    bootstrap('CREATE', ['RECORD_CREATE'])
  ),
  error => error.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
)

assert.throws(
  () => normalizeEmbedFormResult({
    ...formResult('VIEW'),
    record: { ...formResult('VIEW').record, recordVersion: 4 }
  }, bootstrap('VIEW')),
  error => error.errorCode === 'EMBED_RECORD_VERSION_UNSUPPORTED'
)

assert.throws(
  () => normalizeEmbedFormResult({
    ...formResult(),
    form: {
      ...formResult().form,
      fields: formResult().form.fields.map(field => field.code === 'status'
        ? { ...field, optionSource: { ...field.optionSource, queryUrl: 'https://evil.example' } }
        : field)
    }
  }, bootstrap()),
  error => error instanceof EmbedFormProjectionError
    && error.errorCode === 'EMBED_FORM_SOURCE_INVALID'
)

const normalizedCreate = normalizeEmbedFormResult(
  formResult('CREATE'),
  bootstrap('CREATE', ['RECORD_CREATE'])
)
const createRequest = buildEmbedCreateRequest(normalizedCreate, {
  title: '新工单', status: 'OPEN', assignee: null
}, 'cm_0123456789abcdef')
assert.deepEqual(createRequest, {
  data: { title: '新工单', status: 'OPEN', assignee: null },
  clientMutationId: 'cm_0123456789abcdef'
})
assert.equal(normalizedCreate.actions[0].transport, 'RECORD_CREATE')
assert.throws(
  () => buildEmbedCreateRequest(normalizedCreate, {
    title: '新工单', entityCode: 'browser-must-not-coordinate'
  }),
  error => error.errorCode === 'EMBED_CREATE_REQUEST_INVALID'
)

const evaluationRequest = buildEmbedCreateEvaluationRequest(normalizedCreate, {
  title: '', status: 'OPEN'
})
assert.deepEqual(evaluationRequest, { data: { title: '', status: 'OPEN' } })
assert.throws(
  () => buildEmbedCreateEvaluationRequest(normalizedCreate, {
    title: '新工单', entityCode: 'browser-must-not-coordinate'
  }),
  error => error.errorCode === 'EMBED_CREATE_EVALUATION_INVALID'
)

const linkedRaw = formResult('CREATE')
linkedRaw.form.fields = [
  { ...linkedRaw.form.fields[0], label: '联动后的标题' },
  linkedRaw.form.fields[2],
  {
    code: 'reason', label: '原因', type: 'TEXT', required: false,
    readOnly: false, hidden: false, defaultValue: 'DEFAULT', validation: {}, options: [],
    optionSource: null, lookupSource: null,
    layout: { span: 24 }, fieldState: { visible: true, writable: true }
  }
]
const linkedProjection = normalizeEmbedFormResult(
  linkedRaw,
  bootstrap('CREATE', ['RECORD_CREATE'])
)
assert.deepEqual(mergeEmbedCreateDraft(
  linkedProjection,
  normalizedCreate,
  { title: '用户输入', status: 'OPEN', assignee: 'user-9' }
), {
  title: '用户输入', assignee: 'user-9', reason: 'DEFAULT'
})

const createdProjection = normalizeEmbedCreateResult({
  receiptId: 'eor_0123456789abcdef',
  record: {
    id: 'record-created', recordVersion: null,
    values: { title: '新工单', status: 'OPEN' },
    meta: { createdAt: null, updatedAt: null }
  },
  effects: [],
  clientMutationId: 'cm_0123456789abcdef'
}, normalizedCreate, 'cm_0123456789abcdef')
assert.deepEqual(createdProjection.record.values, { title: '新工单', status: 'OPEN' })
assert.deepEqual(projectEmbedFormSaved(createdProjection, normalizedCreate), {
  receiptId: 'eor_0123456789abcdef',
  record: { id: 'record-created', values: { title: '新工单' } },
  clientMutationId: 'cm_0123456789abcdef'
})
assert.throws(
  () => normalizeEmbedCreateResult({
    ...createdProjection,
    record: { ...createdProjection.record, values: { title: '新工单', systemField: 'leak' } }
  }, normalizedCreate, 'cm_0123456789abcdef'),
  error => error.errorCode === 'EMBED_CREATE_RESPONSE_INVALID'
)
assert.throws(
  () => normalizeEmbedFormResult(
    formResult('EDIT'),
    bootstrap('EDIT', ['RECORD_UPDATE'])
  ),
  error => error.errorCode === 'EMBED_FORM_MODE_MISMATCH'
)

// API adapter 只构造冻结的相对路由，不执行响应 DTO 内的 queryUrl。
const apiCalls = []
const apiAdapter = createEmbedRuntimeApi({
  get(path, options) { apiCalls.push({ method: 'GET', path, options }); return Promise.resolve({}) },
  post(path, body, options) {
    apiCalls.push({ method: 'POST', path, body, options }); return Promise.resolve({})
  },
  delete(path, options) { apiCalls.push({ method: 'DELETE', path, options }); return Promise.resolve() }
})
await apiAdapter.getForm({ mode: 'VIEW' })
await apiAdapter.evaluateCreate(evaluationRequest)
await apiAdapter.getRecord('record-001')
await apiAdapter.queryFormOptions('status', optionQuery)
await apiAdapter.queryFormLookups('assignee', {
  mode: 'VIEW', recordId: 'record-001', keyword: '张',
  filters: {}, pageNum: 1, pageSize: 20
})
await apiAdapter.createRecord(createRequest, { idempotencyKey: 'emb_0123456789abcdef' })
assert.deepEqual(apiCalls.map(call => `${call.method} ${call.path}`), [
  'GET /runtime/form?mode=VIEW',
  'POST /runtime/form/evaluations',
  'GET /runtime/records/record-001',
  'POST /runtime/form/fields/status/options/query',
  'POST /runtime/form/fields/assignee/lookups/query',
  'POST /runtime/records'
])
assert.equal(apiCalls.at(-1).options.headers['Idempotency-Key'], 'emb_0123456789abcdef')
assert.deepEqual(Object.keys(apiCalls.at(-1).body), ['data', 'clientMutationId'])
assert.throws(
  () => apiAdapter.getForm({ mode: 'EDIT' }),
  /form mode 无效/
)
assert.throws(
  () => apiAdapter.evaluateCreate({ ...evaluationRequest, releaseId: 'forbidden' }),
  /重算请求无效/
)
assert.throws(
  () => apiAdapter.createRecord({ ...createRequest, entityCode: 'forbidden' }, {
    idempotencyKey: 'emb_0123456789abcdef'
  }),
  /创建请求无效/
)

// 后发搜索必须胜出；上一请求会被 abort，迟到响应和销毁后响应均不能覆盖状态。
const deferred = []
const remote = createEmbedRemoteChoices({
  query(keyword, { signal }) {
    return new Promise(resolve => deferred.push({ keyword, signal, resolve }))
  }
})
const oldRequest = remote.load('old')
const newRequest = remote.load('new')
assert.equal(deferred[0].signal.aborted, true)
deferred[1].resolve({ items: [{ value: 'new' }], hasMore: false })
await newRequest
deferred[0].resolve({ items: [{ value: 'old' }], hasMore: false })
await oldRequest
assert.equal(remote.getSnapshot().items[0].value, 'new')
const destroyedRequest = remote.load('destroyed')
remote.destroy()
deferred[2].resolve({ items: [{ value: 'must-not-appear' }], hasMore: false })
await destroyedRequest
assert.deepEqual(remote.getSnapshot().items, [])

function createBridgeHarness() {
  let options
  let state = 'idle'
  const sent = []
  return {
    sent,
    factory(nextOptions) {
      options = nextOptions
      return {
        start() { state = 'waiting'; return CHILD_NONCE },
        getState() { return state },
        send(type, payload) { sent.push({ type, payload }) },
        destroy() { state = 'destroyed' }
      }
    },
    async initialize() {
      state = 'connected'
      return options.onInit({
        launchCode: TEST_LAUNCH_CODE,
        parentNonce: PARENT_NONCE,
        childNonce: CHILD_NONCE,
        channelId: entryConfig.channelId
      })
    }
  }
}

const runtimeCalls = []
const runtimeApi = {
  async exchange() {
    runtimeCalls.push({ operation: 'exchange' })
    return {
      accessToken: 'embed_token_abcdefghijklmnopqrstuvwxyz', tokenType: 'Bearer',
      expiresAt: '2099-08-27T09:00:00.000Z', idleExpiresAt: '2099-08-27T08:35:00.000Z',
      heartbeatAfterSeconds: 60, protocolVersion: 'flow-embed/1'
    }
  },
  async getBootstrap() { runtimeCalls.push({ operation: 'bootstrap' }); return bootstrap() },
  async getSchema() { throw new Error('FORM 入口不得请求列表 Schema') },
  async queryList() { throw new Error('FORM 入口不得请求列表') },
  async getForm(request) {
    runtimeCalls.push({ operation: 'form', request })
    return formResult()
  },
  async getRecord(recordId) {
    runtimeCalls.push({ operation: 'record', recordId })
    return {
      record: formResult().record,
      fieldStates: {
        title: { visible: true, readOnly: false, required: true },
        status: { visible: true, readOnly: false, required: false },
        assignee: { visible: true, readOnly: false, required: false }
      },
      actions: { save: { visible: true, enabled: true, reason: null } }
    }
  },
  async queryFormOptions(fieldCode, request) {
    runtimeCalls.push({ operation: 'options', fieldCode, request })
    return {
      items: [{ label: '打开', value: 'OPEN', disabled: false }],
      hasMore: false, pageNum: 1, pageSize: 50
    }
  },
  async queryFormLookups(fieldCode, request) {
    throw new Error(`V1 不得调用 Lookup API: ${fieldCode} ${JSON.stringify(request)}`)
  },
  async heartbeat() { return { nextHeartbeatAfterSeconds: 60 } },
  async logout() { runtimeCalls.push({ operation: 'logout' }) }
}
const session = {
  token: '',
  setExchange(value) { this.token = value.accessToken },
  getAccessToken() { return this.token },
  applyHeartbeat() {},
  isExpired() { return false },
  clear() { this.token = '' }
}
const bridge = createBridgeHarness()
const controller = createEmbedRuntimeController({
  entryConfig,
  api: runtimeApi,
  session,
  bridgeFactory: bridge.factory,
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
controller.start()
await bridge.initialize()
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.READY)
assert.deepEqual(runtimeCalls.slice(0, 4).map(call => call.operation), [
  'exchange', 'bootstrap', 'form', 'record'
])
assert.deepEqual(runtimeCalls.find(call => call.operation === 'form').request, { mode: 'VIEW' })
assert.equal(runtimeCalls.some(call => call.operation === 'schema'), false)

const optionsPage = await controller.queryFormOptions('status', {
  keyword: '开', formValues: { title: 'WO', tenantId: 'evil' }, pageSize: 500
})
assert.deepEqual(optionsPage.items, [{ label: '打开', value: 'OPEN', disabled: false }])
assert.deepEqual(runtimeCalls.at(-1).request, {
  mode: 'VIEW', recordId: 'record-001', keyword: '开',
  dependencies: { title: 'WO' }, pageNum: 1, pageSize: 50
})
await assert.rejects(
  controller.queryFormLookups('assignee'),
  error => error.status === 403
    && error.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
)
assert.equal(runtimeCalls.some(call => call.operation === 'lookups'), false)

// CREATE 的双击与同正文失败重试必须复用一把 key；成功后只发送投影后的 form.saved。
const createRuntimeCalls = []
const evaluationDeferred = []
let createInvocation = 0
const createRuntimeApi = {
  async exchange() { return runtimeApi.exchange() },
  async getBootstrap() { return bootstrap('CREATE', ['RECORD_CREATE']) },
  async getSchema() { throw new Error('CREATE 不得请求列表 Schema') },
  async queryList() { throw new Error('CREATE 不得请求列表') },
  async getForm() { return formResult('CREATE') },
  async getRecord() { throw new Error('CREATE 初始读取不得请求 record') },
  async queryFormOptions() { return { items: [], hasMore: false, pageNum: 1, pageSize: 20 } },
  async queryFormLookups() { return { items: [], hasMore: false, pageNum: 1, pageSize: 20 } },
  evaluateCreate(request, { signal }) {
    return new Promise(resolve => evaluationDeferred.push({ request, signal, resolve }))
  },
  async createRecord(request, options) {
    createInvocation += 1
    createRuntimeCalls.push({ request, options })
    await Promise.resolve()
    if (createInvocation === 1) {
      throw Object.assign(new Error('temporary'), { errorCode: 'EMBED_NETWORK_ERROR' })
    }
    return {
      receiptId: 'eor_0123456789abcdef',
      record: {
        id: 'record-created', recordVersion: null,
        values: { title: request.data.title, status: request.data.status },
        meta: { createdAt: null, updatedAt: null }
      },
      effects: [],
      clientMutationId: request.clientMutationId
    }
  },
  async heartbeat() { return { nextHeartbeatAfterSeconds: 60 } },
  async logout() {}
}
const createBridge = createBridgeHarness()
const createController = createEmbedRuntimeController({
  entryConfig,
  api: createRuntimeApi,
  session: {
    ...session,
    token: '',
    setExchange(value) { this.token = value.accessToken },
    getAccessToken() { return this.token },
    clear() { this.token = '' }
  },
  bridgeFactory: createBridge.factory,
  idempotencyKeyFactory: () => 'emb_0123456789abcdef',
  clientMutationIdFactory: () => 'cm_0123456789abcdef',
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
createController.start()
await createBridge.initialize()

// 后发联动输入必须胜出：旧请求既被 abort，也不能在迟到后覆盖新投影。
const oldEvaluation = createController.evaluateCreate({ title: '旧值' })
const newEvaluation = createController.evaluateCreate({ title: '新值' })
assert.equal(evaluationDeferred[0].signal.aborted, true)
const newestProjection = formResult('CREATE')
newestProjection.form.fields[0] = {
  ...newestProjection.form.fields[0], label: '最新标题'
}
evaluationDeferred[1].resolve(newestProjection)
await newEvaluation
const staleProjection = formResult('CREATE')
staleProjection.form.fields[0] = {
  ...staleProjection.form.fields[0], label: '过期标题'
}
evaluationDeferred[0].resolve(staleProjection)
await oldEvaluation
assert.equal(
  createController.getSnapshot().form.fields.find(field => field.code === 'title').label,
  '最新标题'
)
assert.deepEqual(evaluationDeferred[1].request, { data: { title: '新值' } })

const createValues = { title: '双击安全', status: 'OPEN', assignee: null }
const firstSubmit = createController.createRecord(createValues)
const secondSubmit = createController.createRecord(createValues)
const conflictingSubmit = createController.createRecord({
  ...createValues,
  title: '并发的另一份正文'
})
const firstResults = await Promise.allSettled([firstSubmit, secondSubmit, conflictingSubmit])
assert.deepEqual(firstResults.map(result => result.status), ['rejected', 'rejected', 'rejected'])
assert.equal(firstResults[2].reason.errorCode, 'EMBED_REQUEST_IN_PROGRESS')
assert.equal(createRuntimeCalls.length, 1, '双击只能发出一个 HTTP 请求')
assert.equal(createController.getSnapshot().formSubmitError.recoverable, true)

const retryResult = await createController.createRecord(createValues)
assert.equal(retryResult.record.id, 'record-created')
assert.equal(createRuntimeCalls.length, 2)
assert.equal(
  createRuntimeCalls[0].options.idempotencyKey,
  createRuntimeCalls[1].options.idempotencyKey,
  '同一次逻辑操作失败重试必须复用 Idempotency-Key'
)
assert.equal(
  createRuntimeCalls[0].request.clientMutationId,
  createRuntimeCalls[1].request.clientMutationId
)
assert.equal(createController.getSnapshot().formSubmitResult.record.id, 'record-created')
assert.equal(createBridge.sent.at(-1).type, 'form.saved')
assert.deepEqual(Object.keys(createBridge.sent.at(-1).payload), [
  'receiptId', 'record', 'clientMutationId'
])
assert.deepEqual(createBridge.sent.at(-1).payload.record.values, {
  title: '双击安全'
})
assert.equal(
  createController.getSnapshot().formSubmitResult.record.values.status,
  'OPEN',
  'HTTP 成功投影仍可供 iframe 展示全部 visible 字段'
)
await createController.createRecord(createValues)
assert.equal(createRuntimeCalls.length, 2, '成功后的重复点击不得再次创建')

// 联动重算遇到失效 Session 时复用统一终态，不把 401 当作普通字段错误留在表单内。
const expiredEvaluationBridge = createBridgeHarness()
const expiredEvaluationController = createEmbedRuntimeController({
  entryConfig,
  api: {
    ...createRuntimeApi,
    async evaluateCreate() {
      throw Object.assign(new Error('expired'), {
        status: 401, errorCode: 'EMBED_SESSION_EXPIRED'
      })
    }
  },
  session: {
    ...session,
    token: '',
    setExchange(value) { this.token = value.accessToken },
    getAccessToken() { return this.token },
    clear() { this.token = '' }
  },
  bridgeFactory: expiredEvaluationBridge.factory,
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
expiredEvaluationController.start()
await expiredEvaluationBridge.initialize()
assert.equal(await expiredEvaluationController.evaluateCreate({ title: '触发失效' }), null)
assert.equal(
  expiredEvaluationController.getSnapshot().state,
  EMBED_RUNTIME_STATES.SESSION_EXPIRED
)
assert.equal(expiredEvaluationBridge.sent.at(-1).type, 'session.expired')

// LIST 子导航只消费已投影的 LOCAL_FORM 动作与当前页行 ID；BACK 不重建 Session/List 状态。
const listNavigationBootstrap = {
  ...bootstrap('VIEW', ['RECORD_VIEW']),
  view: {
    key: 'supplier-work-order-list',
    name: '供应商工单',
    surfaceType: 'LIST',
    revision: 9,
    entryMode: 'LIST'
  },
  capabilities: ['LIST_QUERY', 'SELECTION_RETURN', 'RECORD_VIEW', 'RECORD_CREATE'],
  ui: {
    locale: 'zh-CN', theme: 'light', showSearch: true,
    showPagination: true, showToolbar: true, pageSize: 20, heightMode: 'AUTO'
  }
}
const listNavigationSchema = {
  view: { key: 'supplier-work-order-list', surfaceType: 'LIST', revision: 9 },
  entity: { code: 'work_order', name: '工单' },
  list: {
    selection: { mode: 'SINGLE', valueField: 'id', returnableFields: ['title'] },
    pagination: { allowTotal: false, maxPageSize: 50 },
    columns: [
      { code: 'title', label: '标题', type: 'TEXT' },
      { code: 'status', label: '状态', type: 'TEXT' }
    ],
    filters: [{ code: 'title', label: '标题', type: 'TEXT', operator: 'CONTAINS' }]
  },
  form: null,
  actions: [
    {
      key: 'create', label: '新建', placement: 'TOOLBAR', kind: 'NAVIGATION',
      transport: 'LOCAL_FORM', recordMode: 'NONE', selectionMode: 'NONE',
      requiresRecordVersion: false, idempotencyRequired: false
    },
    {
      key: 'view', label: '查看', placement: 'ROW', kind: 'NAVIGATION',
      transport: 'LOCAL_FORM', recordMode: 'CURRENT', selectionMode: 'NONE',
      requiresRecordVersion: false, idempotencyRequired: false
    }
  ]
}
assert.throws(
  () => normalizeEmbedFormResult(
    formResult('VIEW'),
    listNavigationBootstrap,
    { mode: 'VIEW', recordId: 'record-outside-current-page' }
  ),
  error => error.errorCode === 'EMBED_FORM_RECORD_MISMATCH'
)
const navigationCalls = []
const navigationApi = {
  async exchange() { return runtimeApi.exchange() },
  async getBootstrap() { return listNavigationBootstrap },
  async getSchema() { return listNavigationSchema },
  async queryList(query) {
    navigationCalls.push({ operation: 'list', query })
    return {
      items: [{
        id: 'record-001', recordVersion: null,
        values: { title: '安全工单', status: 'OPEN' },
        meta: { updatedAt: '2026-08-27T08:20:00.000Z' },
        actions: { view: { visible: true, enabled: true, reason: null } }
      }],
      hasMore: query.pageNum === 1,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    }
  },
  async getForm(request) {
    navigationCalls.push({ operation: 'form', request })
    return formResult(request.mode)
  },
  async getRecord(recordId) {
    navigationCalls.push({ operation: 'record', recordId })
    return {
      record: formResult('VIEW').record,
      fieldStates: {
        title: { visible: true, readOnly: true, required: true },
        status: { visible: true, readOnly: true, required: false },
        assignee: { visible: true, readOnly: true, required: false }
      },
      actions: {}
    }
  },
  async queryFormOptions() { return { items: [], hasMore: false, pageNum: 1, pageSize: 20 } },
  async queryFormLookups() { return { items: [], hasMore: false, pageNum: 1, pageSize: 20 } },
  async evaluateCreate(request) {
    navigationCalls.push({ operation: 'evaluation', request })
    return formResult('CREATE')
  },
  async createRecord(request) {
    navigationCalls.push({ operation: 'create', request })
    return {
      receiptId: 'eor_0123456789abcdef',
      record: {
        id: 'record-created', recordVersion: null,
        values: { title: request.data.title, status: request.data.status },
        meta: { createdAt: null, updatedAt: null }
      },
      effects: [],
      clientMutationId: request.clientMutationId
    }
  },
  async heartbeat() { return { nextHeartbeatAfterSeconds: 60 } },
  async logout() {}
}
const navigationBridge = createBridgeHarness()
const navigationController = createEmbedRuntimeController({
  entryConfig,
  api: navigationApi,
  session: {
    ...session,
    token: '',
    setExchange(value) { this.token = value.accessToken },
    getAccessToken() { return this.token },
    clear() { this.token = '' }
  },
  bridgeFactory: navigationBridge.factory,
  idempotencyKeyFactory: () => 'emb_navigation_0123456789',
  clientMutationIdFactory: () => 'cm_navigation_0123456789',
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
navigationController.start()
await navigationBridge.initialize()
await navigationController.refreshList({
  queryValues: { title: '安全' },
  pageNum: 2,
  pageSize: 20
})
const selectedRecord = navigationController.getSnapshot().page.items[0]
assert.equal(navigationController.emitSelection([selectedRecord]), true)
const listSnapshot = navigationController.getSnapshot()
const listQueryCount = navigationCalls.filter(call => call.operation === 'list').length

assert.throws(
  () => navigationController.openListView('record-outside-current-page'),
  error => error.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
)
await navigationController.openListView('record-001')
assert.deepEqual(navigationCalls.at(-2), {
  operation: 'form', request: { mode: 'VIEW', recordId: 'record-001' }
})
assert.deepEqual(navigationCalls.at(-1), { operation: 'record', recordId: 'record-001' })
assert.deepEqual(navigationController.getSnapshot().navigation, {
  surfaceType: 'FORM', mode: 'VIEW', recordId: 'record-001', canBack: true
})
assert.equal(navigationController.getSnapshot().bootstrap.view.surfaceType, 'LIST')
assert.equal(navigationController.backToList(), true)
assert.equal(navigationCalls.filter(call => call.operation === 'list').length, listQueryCount)
assert.equal(navigationController.getSnapshot().page, listSnapshot.page)
assert.deepEqual(navigationController.getSnapshot().queryValues, { title: '安全' })
assert.deepEqual(navigationController.getSnapshot().selectedRecordIds, ['record-001'])

await navigationController.openListCreate()
assert.deepEqual(navigationCalls.at(-1), { operation: 'form', request: { mode: 'CREATE' } })
assert.deepEqual(navigationController.getSnapshot().navigation, {
  surfaceType: 'FORM', mode: 'CREATE', recordId: null, canBack: true
})
const nestedCreate = await navigationController.createRecord({
  title: '列表内新建', status: 'OPEN', assignee: null
})
assert.equal(nestedCreate.record.id, 'record-created')
assert.equal(navigationController.backToList(), true)
assert.equal(navigationCalls.filter(call => call.operation === 'list').length, listQueryCount)
assert.equal(navigationController.getSnapshot().page, listSnapshot.page)
assert.deepEqual(navigationController.getSnapshot().queryValues, { title: '安全' })
assert.deepEqual(navigationController.getSnapshot().selectedRecordIds, ['record-001'])
assert.equal(navigationBridge.sent.some(message => message.type === 'form.saved'), true)

console.log('embed form contract and state tests passed')
