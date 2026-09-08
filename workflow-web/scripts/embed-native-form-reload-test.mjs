import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse, parse } from '@vue/compiler-sfc'
import { ref } from 'vue'
import { createEmbedRuntimeController } from '../src/embed/runtime/embedRuntimeController.js'

const source = await readFile(new URL('../src/embed/runtime/NativeEmbeddedFormPage.vue', import.meta.url), 'utf8')
const descriptor = parse(source).descriptor
const script = descriptor.scriptSetup.content
const moduleState = new Function(`${descriptor.script.content}\nreturn { nativeFormReloadLocks }`)()
const ast = babelParse(script, { sourceType: 'module' })
const names = [
  'loadSequence', 'closeRequested', 'saveSucceeded', 'pendingReload', 'releaseReloadInteraction', 'lockReloadInteraction',
  'loadEntityStatuses', 'loadRuntime', 'reload', 'retryLoad', 'handleClosed'
]
// 直接执行生产方法与真实卸载钩子，避免测试复制一套恰好正确的序号判断。
const selected = ast.program.body.filter(node => names.includes(
  node.type === 'VariableDeclaration' ? node.declarations[0].id.name : node.id?.name
) || (node.type === 'ExpressionStatement' && node.expression.callee?.name === 'onBeforeUnmount'))
assert.equal(selected.length, names.length + 1)
const methodsSource = selected.map(node => script.slice(node.start, node.end)).join('\n')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((nextResolve, nextReject) => { resolve = nextResolve; reject = nextReject })
  return { promise, resolve, reject }
}

/** 注入可控制的确认、原生 API 和 Dialog，观察真实刷新方法是否越过生命周期边界。 */
function nativeForm({ confirm = async () => true, entity, openCreate = async () => {}, bodyChildren = [] } = {}) {
  let unmount
  const calls = []
  const state = {
    ...moduleState,
    globalThis: { document: { body: { children: bodyChildren } } },
    target: { entityCode: 'order', formId: 'form-order', formReleaseId: 'fixed-release', formReleaseVersion: 1 },
    mode: ref('CREATE'),
    loading: ref(false),
    error: ref(''),
    entityDefinition: ref({ id: 'original-entity' }),
    entityFields: ref([]),
    runtimeForm: ref({ id: 'original-form' }),
    entityStatusOptions: ref([]),
    dataFormDialogRef: ref({
      confirmDiscardChanges() { calls.push('confirm'); return confirm() },
      async openCreate(options) { calls.push('open'); return openCreate(options) }
    }),
    approvalDialogRef: ref(null),
    props: { bootstrap: {}, controller: { requestClose() { calls.push('close') } } },
    entityApi: {
      getByCode() {
        calls.push('entity')
        return entity ? entity() : Promise.resolve({ id: 'loaded-entity', fields: [] })
      }
    },
    async getFormRuntimeRelease() { calls.push('release'); return { id: 'loaded-form' } },
    async getEntityStatusList() { calls.push('statuses'); return [] },
    getEffectiveEntityStatusOptions: value => value,
    normalizeRuntimeFormRelease: value => value,
    nextTick: async () => {},
    onBeforeUnmount(callback) { unmount = callback },
    console: { error() {} }
  }
  const methods = new Function(...Object.keys(state), `${methodsSource}\nreturn { reload, retryLoad, handleClosed }`)(
    ...Object.values(state)
  )
  return { ...state, ...methods, calls, unmount: () => unmount() }
}

const confirmation = deferred()
const cancelled = nativeForm({ confirm: () => confirmation.promise })
const cancelledReload = cancelled.reload()
assert.equal(cancelled.reload(), cancelledReload, '并发刷新应共享确认及其完成结果')
assert.deepEqual(cancelled.calls, ['confirm'])
assert.equal(cancelled.loading.value, false, '确认前不得将原表单切换为加载态')
confirmation.resolve(false)
assert.equal(await cancelledReload, false)
assert.equal(cancelled.runtimeForm.value.id, 'original-form')
assert.deepEqual(cancelled.calls, ['confirm'], '取消不能重新读取或重置表单')

for (const lifecycle of ['unmount', 'handleClosed']) {
  const pendingConfirmation = deferred()
  const form = nativeForm({ confirm: () => pendingConfirmation.promise })
  const reload = form.reload()
  form[lifecycle]()
  pendingConfirmation.resolve(true)
  assert.equal(await reload, false)
  assert.equal(form.calls.includes('entity'), false, `${lifecycle} 后的确认续体不能发起请求`)
  assert.equal(form.calls.includes('open'), false)
}

for (const lifecycle of ['unmount', 'handleClosed']) {
  const request = deferred()
  const started = deferred()
  const form = nativeForm({ entity: () => { started.resolve(); return request.promise } })
  const reload = form.reload()
  await started.promise
  assert.equal(form.loading.value, true)
  form[lifecycle]()
  request.resolve({ id: 'late-entity' })
  assert.equal(await reload, false)
  assert.equal(form.calls.includes('open'), false, `${lifecycle} 后的 API 响应不能重新打开 Dialog`)
  assert.equal(form.entityDefinition.value.id, 'original-entity')
}

const opened = deferred()
const finishOpen = deferred()
const delayedOpen = nativeForm({ openCreate: () => { opened.resolve(); return finishOpen.promise } })
const opening = delayedOpen.reload()
await opened.promise
assert.equal(delayedOpen.loading.value, true, 'openCreate 的初始化尚未完成时继续禁止编辑')
finishOpen.resolve()
assert.equal(await opening, true)
assert.equal(delayedOpen.loading.value, false)

const failure = Object.assign(new Error('表单暂时不可用'), {
  errorCode: 'EMBED_RUNTIME_UNAVAILABLE', traceId: 'trace-native-form-reload', status: 503
})
let shouldFail = true
const failed = nativeForm({ entity: async () => { if (shouldFail) throw failure; return { id: 'recovered' } } })
await assert.rejects(failed.reload(), error => error === failure)
assert.equal(failed.error.value, failure.message)
assert.equal(failed.loading.value, false)
shouldFail = false
assert.equal(await failed.reload(), true, '失败后必须清除 pendingReload 以允许真实重试')
assert.equal(failed.error.value, '')

const failedOpen = nativeForm({ openCreate: async () => { throw failure } })
await assert.rejects(failedOpen.reload(), error => error === failure)
assert.equal(failedOpen.loading.value, false)

const missingDialog = nativeForm()
missingDialog.dataFormDialogRef.value = null
await assert.rejects(missingDialog.reload(), error => error.errorCode === 'EMBED_RUNTIME_UNAVAILABLE')
assert.equal(missingDialog.loading.value, false)

const failedView = nativeForm()
failedView.mode.value = 'VIEW'
failedView.dataFormDialogRef.value = null
failedView.approvalDialogRef.value = { async openView() { return false } }
await assert.rejects(failedView.reload(), /记录详情暂时无法加载/)
assert.equal(failedView.loading.value, false)

const dialogRoot = { inert: false }
const openPopper = { inert: false }
const alreadyLocked = { inert: true }
const bodyChildren = [dialogRoot, openPopper, alreadyLocked]
const blockedOpen = deferred()
const initializing = deferred()
const lockedForm = nativeForm({
  bodyChildren,
  openCreate: () => { initializing.resolve(); return blockedOpen.promise }
})
const lockedReload = lockedForm.reload()
await initializing.promise
assert.ok(bodyChildren.every(element => element.inert), '旧表单和 body 中已打开的 Popper 必须同时停止交互')
const businessConfirmation = { inert: false }
bodyChildren.push(businessConfirmation)
assert.equal(businessConfirmation.inert, false, 'FORM_OPEN 新创建的业务确认框不能被加载锁阻塞')
blockedOpen.resolve()
await lockedReload
assert.equal(dialogRoot.inert, false)
assert.equal(openPopper.inert, false)
assert.equal(alreadyLocked.inert, true, '恢复时必须保留节点原有的 inert 状态')
assert.equal(businessConfirmation.inert, false)

const failedRoot = { inert: false }
const lockedFailure = nativeForm({ bodyChildren: [failedRoot], openCreate: async () => { throw failure } })
await assert.rejects(lockedFailure.reload(), error => error === failure)
assert.equal(failedRoot.inert, false, '加载失败也必须解除本次交互锁')

const sharedRoot = { inert: false }
const oldRequest = deferred()
const newRequest = deferred()
const oldStarted = deferred()
const newStarted = deferred()
const oldForm = nativeForm({ bodyChildren: [sharedRoot], entity: () => { oldStarted.resolve(); return oldRequest.promise } })
const newForm = nativeForm({ bodyChildren: [sharedRoot], entity: () => { newStarted.resolve(); return newRequest.promise } })
const oldReload = oldForm.reload()
await oldStarted.promise
const newReload = newForm.reload()
await newStarted.promise
oldForm.unmount()
assert.equal(sharedRoot.inert, true, '旧实例卸载不能解除新实例仍持有的锁')
oldRequest.resolve({ id: 'old' })
assert.equal(await oldReload, false)
assert.equal(sharedRoot.inert, true, '旧异步续体不能再次释放并越过新实例的锁')
newRequest.resolve({ id: 'new' })
await newReload
assert.equal(sharedRoot.inert, false)

const unmountedRoot = { inert: false }
const unmountedRequest = deferred()
const unmountedStarted = deferred()
const unmountedForm = nativeForm({
  bodyChildren: [unmountedRoot],
  entity: () => { unmountedStarted.resolve(); return unmountedRequest.promise }
})
const unmountedReload = unmountedForm.reload()
await unmountedStarted.promise
unmountedForm.unmount()
assert.equal(unmountedRoot.inert, false, '卸载时立即解锁，不等待可能永不返回的 API')
unmountedRequest.resolve({ id: 'stale' })
await unmountedReload
assert.equal(unmountedRoot.inert, false)

/** 把真实原生刷新接到控制器，验证每次宿主命令得到对应的 ACK 或错误。 */
async function controllerFor(form) {
  let handlers
  let bridgeState = 'waiting'
  const messages = []
  const waiters = []
  const controller = createEmbedRuntimeController({
    entryConfig: {
      launchId: 'lch_0123456789abcdef', expectedParentOrigin: 'https://portal.example.com',
      channelId: 'channel-12345678', protocolVersion: 'flow-embed/1'
    },
    api: {
      async exchange() {
        return {
          accessToken: 'embed_token_abcdefghijklmnopqrstuvwxyz', tokenType: 'Bearer',
          expiresAt: '2099-08-27T09:00:00.000Z', idleExpiresAt: '2099-08-27T08:35:00.000Z',
          heartbeatAfterSeconds: 60, protocolVersion: 'flow-embed/1'
        }
      },
      async getBootstrap() {
        return {
          session: { id: 'ems_001', expiresAt: '2099-08-27T09:00:00.000Z', idleExpiresAt: '2099-08-27T08:35:00.000Z' },
          actor: { displayName: '测试用户' },
          view: { key: 'order-create', name: '订单', surfaceType: 'FORM', entryMode: 'CREATE' },
          capabilities: ['RECORD_CREATE'],
          target: {
            entityCode: 'order', formId: 'form-order', formReleaseId: 'fixed-release',
            formReleaseVersion: 1, formReleaseResolutionToken: 'resolution_token_0123456789',
            entryMode: 'CREATE', recordId: null, initialData: {}, parameters: {}, context: {}
          },
          ui: { locale: 'zh-CN', theme: 'light', pageSize: 20 },
          limits: { maxPageSize: 100, maxPayloadBytes: 1048576, maxSelectionSize: 100 }
        }
      },
      async getSchema() { throw new Error('原生 FORM 不应读取投影 schema') },
      async queryList() { throw new Error('原生 FORM 不应查询列表') },
      async heartbeat() {},
      async logout() {}
    },
    onRefreshNativeForm: form.reload,
    bridgeFactory(options) {
      handlers = options
      return {
        start() {}, getState: () => bridgeState,
        send(type, payload, options) {
          const message = { type, payload, options }
          messages.push(message)
          for (const waiter of waiters) if (waiter.requestId === options?.requestId) waiter.resolve(message)
        },
        destroy() { bridgeState = 'destroyed' }
      }
    },
    setTimeoutImpl() { return 1 }, clearTimeoutImpl() {}
  })
  controller.start()
  bridgeState = 'connected'
  await handlers.onInit({ launchCode: 'A'.repeat(43), parentNonce: 'parent', childNonce: 'child', channelId: 'channel-12345678' })
  assert.equal(controller.getSnapshot().state, 'READY')
  return {
    controller, messages,
    refresh(requestId) {
      const response = new Promise(resolve => waiters.push({ requestId, resolve }))
      handlers.onMessage({ type: 'refresh', payload: {}, requestId })
      return response
    }
  }
}

const rejectedRuntime = await controllerFor(failedOpen)
const rejectedResponse = await rejectedRuntime.refresh('req-native-rejected')
assert.equal(rejectedResponse.type, 'error')
assert.equal(rejectedResponse.payload.traceId, failure.traceId)
assert.equal(rejectedRuntime.messages.some(message => message.type === 'ack'
  && message.options?.requestId === 'req-native-rejected'), false)
await rejectedRuntime.controller.destroy()

const cancelledRuntime = await controllerFor(nativeForm({ confirm: async () => false }))
const cancelledResponse = await cancelledRuntime.refresh('req-native-cancelled')
assert.equal(cancelledResponse.type, 'error')
assert.equal(cancelledResponse.payload.errorCode, 'EMBED_OPERATION_NOT_ALLOWED')
await cancelledRuntime.controller.destroy()

const successRuntime = await controllerFor(nativeForm())
assert.equal((await successRuntime.refresh('req-native-success')).type, 'ack')
await successRuntime.controller.destroy()

const lateRequest = deferred()
const lateStarted = deferred()
const closingForm = nativeForm({ entity: () => { lateStarted.resolve(); return lateRequest.promise } })
const closingRuntime = await controllerFor(closingForm)
const closingResponse = closingRuntime.refresh('req-native-closed')
await lateStarted.promise
closingForm.handleClosed()
lateRequest.reject(failure)
assert.equal((await closingResponse).type, 'error', '关闭作废的刷新即使收到晚返回错误也不能 ACK 成功')
assert.equal(closingForm.error.value, '', '晚返回错误不应覆盖已关闭表单的局部状态')
await closingRuntime.controller.destroy()

console.log('embed native form reload tests passed')
