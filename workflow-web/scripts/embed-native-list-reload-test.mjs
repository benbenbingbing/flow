import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse, parse } from '@vue/compiler-sfc'
import { ref } from 'vue'
import { normalizeEntityVersionCapabilities } from '../src/shared/entity-version-capabilities.js'

/** 执行组件的真实加载方法，以可控 API/挂载时机验证异步结果，不依赖 DOM 或字符串断言。 */
async function componentMethods(path, names, dependencies) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const script = parse(source).descriptor.scriptSetup.content
  const ast = babelParse(script, { sourceType: 'module', plugins: ['typescript'] })
  const selected = ast.program.body.filter(node => names.includes(
    node.type === 'VariableDeclaration' ? node.declarations[0].id.name : node.id?.name
  ))
  assert.equal(selected.length, names.length)
  const code = selected.map(node => {
    const annotations = []
    const visit = value => {
      if (!value || typeof value !== 'object') return
      if (value.type === 'TSTypeAnnotation') {
        annotations.push(value)
        return
      }
      for (const child of Object.values(value)) {
        if (Array.isArray(child)) child.forEach(visit)
        else visit(child)
      }
    }
    visit(node)
    let text = script.slice(node.start, node.end)
    for (const annotation of annotations.sort((a, b) => b.start - a.start)) {
      text = text.slice(0, annotation.start - node.start)
        + text.slice(annotation.end - node.start)
    }
    return text
  }).join('\n')
  return new Function(...Object.keys(dependencies), `${code}\nreturn { ${names.join(',')} }`)(
    ...Object.values(dependencies)
  )
}

function remoteError(message = '列表暂时不可用') {
  return Object.assign(new Error(message), {
    errorCode: 'EMBED_RUNTIME_UNAVAILABLE',
    traceId: 'trace-list-reload',
    status: 503
  })
}

async function entityList(overrides = {}) {
  const state = {
    entityCode: ref('order'),
    runtimeListKey: ref('main'),
    runtimeScene: ref('PAGE'),
    viewCompositionTraversalToken: ref(''),
    loading: ref(false),
    tableLoading: ref(false),
    loadError: ref(''),
    dataError: ref(''),
    entityDefinition: ref({ id: 'entity-order' }),
    entityFields: ref([]),
    listConfig: ref({ releaseId: 'release-fixed', publishedVersion: 1 }),
    listConfigFields: ref([]),
    dataList: ref([]),
    refEntityNameMap: ref({}),
    dictOptionMap: ref({}),
    total: ref(0),
    pageNum: ref(1),
    pageSize: ref(10),
    loadedDefaultForm: ref(null),
    versionCapabilities: ref(normalizeEntityVersionCapabilities()),
    isSystemEntity: ref(false),
    canViewVersions: ref(false),
    toolbarButtons: ref([]),
    rowActionButtons: ref([]),
    queryFields: ref([]),
    queryForm: {},
    props: { embedded: true, releaseId: 'release-fixed', releaseVersion: 1 },
    console: { error() {}, warn() {} },
    clearQueryForm() {},
    safeParseConfig: value => value || {},
    buildRequestFilters: () => ({}),
    loadDefaultForm: async () => {},
    loadEntityStatusMap: async () => {},
    loadRefEntityNames: async () => {},
    entityApi: { getByCode: async () => ({ id: 'entity-order', fields: [] }) },
    entityVersionApi: { recordCapabilities: async () => ({}) },
    normalizeEntityVersionCapabilities,
    entityListRuntimeApi: {
      getSchema: async () => ({ releaseId: 'release-fixed', publishedVersion: 1 }),
      query: async () => ({ list: [{ id: 'record-1' }], total: 1 })
    },
    ...overrides
  }
  const methods = await componentMethods('../src/views/entity/EntityDataList.vue', [
    'versionCapabilitiesGeneration', 'resetVersionCapabilities',
    'loadVersionCapabilities', 'entityLoadPromise', 'loadEntityDefinition',
    'prepareEntityDefinition', 'loadListConfig', 'loadDataList', 'reload'
  ], state)
  return { ...state, ...methods }
}

let capabilityRequests = 0
const noVersionPermission = await entityList({
  entityVersionApi: {
    recordCapabilities: async () => {
      capabilityRequests += 1
      return { runtimeEnabled: true, manualCaptureEnabled: true }
    }
  }
})
await noVersionPermission.loadVersionCapabilities(
  'order',
  noVersionPermission.resetVersionCapabilities()
)
assert.equal(capabilityRequests, 0, '缺少版本查看权限时不得请求实体版本能力')
assert.equal(noVersionPermission.versionCapabilities.value.runtimeEnabled, false)

const versionPermission = ref(true)
const systemEntity = await entityList({
  canViewVersions: versionPermission,
  isSystemEntity: ref(true),
  entityVersionApi: {
    recordCapabilities: async () => {
      capabilityRequests += 1
      return { runtimeEnabled: true, manualCaptureEnabled: true }
    }
  }
})
await systemEntity.loadVersionCapabilities(
  'order',
  systemEntity.resetVersionCapabilities()
)
assert.equal(capabilityRequests, 0, 'SYSTEM 实体不得请求业务数据版本能力')

const versionEnabled = await entityList({
  canViewVersions: versionPermission,
  entityVersionApi: {
    recordCapabilities: async entityCode => {
      capabilityRequests += 1
      assert.equal(entityCode, 'order')
      return { runtimeEnabled: true, manualCaptureEnabled: true }
    }
  }
})
await versionEnabled.loadVersionCapabilities(
  'order',
  versionEnabled.resetVersionCapabilities()
)
assert.equal(capabilityRequests, 1)
assert.deepEqual(versionEnabled.versionCapabilities.value, {
  runtimeEnabled: true,
  manualCaptureEnabled: true
})

let resolveStaleCapability
const staleCapabilityPending = new Promise(resolve => {
  resolveStaleCapability = resolve
})
const staleCapability = await entityList({
  canViewVersions: versionPermission,
  entityVersionApi: {
    recordCapabilities: () => staleCapabilityPending
  }
})
const staleGeneration = staleCapability.resetVersionCapabilities()
const staleLoad = staleCapability.loadVersionCapabilities('order', staleGeneration)
staleCapability.resetVersionCapabilities()
resolveStaleCapability({ runtimeEnabled: true, manualCaptureEnabled: true })
await staleLoad
assert.equal(
  staleCapability.versionCapabilities.value.runtimeEnabled,
  false,
  '实体切换后旧能力响应不得重新显示版本入口'
)

const failure = remoteError()
const failedQuery = await entityList({
  entityListRuntimeApi: { query: async () => { throw failure } }
})
await failedQuery.loadDataList()
assert.equal(failedQuery.dataError.value, failure.message, '普通页面仍就地展示错误')
await assert.rejects(failedQuery.reload({ throwOnError: true }), error => error === failure)
assert.equal(failedQuery.tableLoading.value, false)

let schemaAvailable = false
let queryCount = 0
const failedSchema = await entityList({
  entityListRuntimeApi: {
    getSchema: async () => {
      if (!schemaAvailable) throw failure
      return { releaseId: 'release-fixed', publishedVersion: 1 }
    },
    query: async () => {
      queryCount += 1
      return { list: [{ id: 'recovered' }], total: 1 }
    }
  }
})
await failedSchema.loadEntityDefinition()
assert.equal(failedSchema.loadError.value, failure.message)
await assert.rejects(failedSchema.reload({ throwOnError: true }), error => error === failure)
assert.equal(queryCount, 0, '配置失败时不能向未准备好的列表查询')
schemaAvailable = true
await failedSchema.reload({ throwOnError: true })
assert.equal(failedSchema.loadError.value, '')
assert.equal(queryCount, 1)
assert.equal(failedSchema.dataList.value[0].id, 'recovered')

let finishEntity
const entityPending = new Promise(resolve => { finishEntity = resolve })
queryCount = 0
const pendingList = await entityList({
  entityApi: { getByCode: () => entityPending },
  entityListRuntimeApi: {
    getSchema: async () => ({ releaseId: 'release-fixed', publishedVersion: 1 }),
    query: async () => { queryCount += 1; return { list: [] } }
  }
})
const initialLoad = pendingList.loadEntityDefinition()
const pendingReload = pendingList.reload({ throwOnError: true })
await Promise.resolve()
assert.equal(queryCount, 0, '宿主刷新必须等待初始实体与 exact LIST 配置')
finishEntity({ id: 'entity-order' })
await Promise.all([initialLoad, pendingReload])
assert.equal(queryCount, 2)

let formAvailable = false
let reloadFinished = false
let finishReload
const queryPending = new Promise(resolve => { finishReload = resolve })
const nativeState = {
  target: {
    formId: 'form-order', formReleaseId: 'form-fixed',
    formReleaseVersion: 1, formReleaseResolutionToken: 'resolution-fixed'
  },
  listRef: ref(null),
  loading: ref(true),
  error: ref(''),
  defaultForm: ref(null),
  console: { error() {} },
  getFormRuntimeRelease: async () => {
    if (!formAvailable) throw failure
    return { id: 'form-order' }
  },
  normalizeRuntimeFormRelease: value => value,
  nextTick: async () => {
    nativeState.listRef.value = {
      async reload(options) {
        assert.equal(options.throwOnError, true)
        await queryPending
      }
    }
  }
}
const native = await componentMethods('../src/embed/runtime/NativeEmbeddedListPage.vue', [
  'loadSequence', 'hasFixedDefaultForm', 'prepareRuntime', 'reload'
], nativeState)
await native.prepareRuntime()
assert.equal(nativeState.error.value, failure.message)
await assert.rejects(native.reload(), error => error === failure)
formAvailable = true
const nativeReload = native.reload().then(() => { reloadFinished = true })
await Promise.resolve()
await Promise.resolve()
assert.equal(reloadFinished, false, '固定表单恢复后仍必须等待子列表实际加载')
finishReload()
await nativeReload
assert.equal(nativeState.error.value, '')
nativeState.listRef.value.reload = async () => { throw failure }
await assert.rejects(native.reload(), error => error === failure)

console.log('embed native list reload tests passed')
