/**
 * 历史证据：仅适用于审查基线 c7318b5f 的干净工作区，不适用于整改后的当前源码。
 * 从当时的 Vue 方法复现五组缺陷；断言通过表示旧缺陷存在，不表示当前实现正确。
 * 现行验证：npm run test:packages；npm run test:runtime-regressions --workspace workflow-web；
 * npm run test:e2e:refactor --workspace workflow-web；npm run test:e2e --workspace workflow-mobile。
 * 如需重现原始审查，在基线的独立 checkout 中放入本脚本后执行；不要在当前工作区回退源码。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire, stripTypeScriptTypes } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'
const root = fileURLToPath(new URL('../', import.meta.url)).replace(/\/$/, '')
const require = createRequire(`${root}/package.json`)
const { parse, babelParse } = require('@vue/compiler-sfc')
const { normalizeCustomButton } = await import(pathToFileURL(`${root}/packages/workflow-core/src/shared/form-actions.js`))
const { applyRuntimeFieldEffects } = await import(pathToFileURL(`${root}/packages/workflow-core/src/shared/form-runtime/fieldEvents.js`))

// 提取组件中实际执行的方法，并以可控依赖替换浏览器和网络，不复制被审查的业务算法。
function loadMethods(file, names, scope, entry) {
  const { descriptor } = parse(readFileSync(`${root}/${file}`, 'utf8'))
  const script = descriptor.scriptSetup || descriptor.script
  const ast = babelParse(script.content, { sourceType: 'module', plugins: script.lang === 'ts' ? ['typescript'] : [] })
  const code = names.map(name => {
    const declaration = ast.program.body.find(node => node.type === 'FunctionDeclaration' && node.id.name === name || node.type === 'VariableDeclaration' && node.declarations.some(item => item.id.name === name))
    assert.ok(declaration, `${file}: ${name} must exist`)
    return script.content.slice(declaration.start, declaration.end)
  }).join('\n')
  return Function(...Object.keys(scope), `${script.lang === 'ts' ? stripTypeScriptTypes(code) : code}\nreturn ${entry};`)(...Object.values(scope))
}
const ref = value => ({ value })
const dialogs = ['workflow-web/src/views/entity/components/EntityDataFormDialog.vue', 'workflow-web/src/views/entity/components/approval/EntityApprovalDialog.vue']
const effect = { effects: [{ type: 'FIELD_MAPPING', data: { form: { amount: 100 } }, mappings: [{ targetPath: 'form.amount', overwrite: 'ALWAYS' }] }] }
const output = []
for (const file of dialogs) {
  const record = { amount: 7 }
  const run = loadMethods(file, ['applyFormEventResult', 'resolvePath', 'setPath', 'emptyValue'], { formData: { data: record }, entityData: ref(record) }, 'applyFormEventResult')
  await run(effect)
  output.push(record.amount)
}
const coreRecord = { amount: 7 }
await applyRuntimeFieldEffects(effect, { getRecord: () => coreRecord, setField: (key, value) => coreRecord[key] = value })
assert.deepEqual(output, [100, undefined])
assert.equal(coreRecord.amount, 100)
console.log('FIELD_MAPPING: 编辑弹窗=100；审批弹窗=undefined（覆盖原值 7）；共享实现=100')

let confirms = 0, executions = 0
const action = normalizeCustomButton({ key: 'action', confirm: { enabled: true, message: '请确认' } })
const runAction = loadMethods('workflow-mobile/src/pages/ProcessDetail.vue', ['handleAction'], {
  pending: ref(''), submitting: ref(false), showConfirmDialog: async () => { confirms++ },
  actionRuntime: { acquireFormActionExecution: () => () => {}, executeCustomFormAction: async () => { executions++; return {} }, resolveRuntimeFormActions: async () => [] },
  form: ref({ id: '1' }), actionContext: () => ({}), record: ref({}), applyRuntimeFieldEffects,
  showSuccessToast: () => {}, invalidateInboxes: () => {}, formActions: ref([]), showFailToast: message => { throw new Error(message) }
}, 'handleAction')
await runAction(action)
assert.equal(confirms, 0)
assert.equal(executions, 1)
console.log('移动端自定义按钮：confirm.enabled=true；确认次数=0；执行次数=1')

const progress = { completedNodes: ['approve'], activeNodes: ['approve'], executedSequenceFlows: [] }
const canvas = {}
const viewer = { get: key => key === 'canvas' ? canvas : { getAll: () => [{ id: 'approve', type: 'bpmn:UserTask' }] } }
const statuses = []
for (const file of ['workflow-web/src/views/ProcessProgress.vue', 'workflow-web/src/components/VueBpmnViewer.vue']) {
  const highlight = loadMethods(file, ['highlightProcess'], {
    currentViewer: viewer, viewer: ref(viewer), progressData: ref(progress), props: { progressData: progress },
    toRaw: value => value, COLORS: {}, setNodeStyle: (_canvas, _element, _colors, status) => statuses.push(status),
    console: { log() {}, warn() {}, error(error) { throw error } }
  }, 'highlightProcess')
  highlight()
}
assert.deepEqual(statuses, ['completed', 'active'])
console.log('节点同时出现在 completedNodes/activeNodes：流程进度页=completed；公共查看器=active')

// 用迟到响应模拟用户连续两次筛选；不依赖真实服务，也不修改任何业务记录。
const requests = [], doneList = ref([])
const loadDone = loadMethods('workflow-web/src/views/Home.vue', ['loadDoneList'], {
  loading: ref(false), tabErrors: {}, loadedTabs: {}, doneList, doneTotal: ref(0), buildQueryParams: () => ({}),
  getDoneList: () => new Promise(resolve => requests.push(resolve))
}, 'loadDoneList')
const oldRequest = loadDone(), latestRequest = loadDone()
requests[1]({ records: [{ id: 'latest' }], total: 1 }); await latestRequest
assert.equal(doneList.value[0].id, 'latest')
requests[0]({ records: [{ id: 'old' }], total: 1 }); await oldRequest
assert.equal(doneList.value[0].id, 'old')
console.log('首页已办列表：第二次请求先返回 latest，第一次迟到后最终页面数据=old')

let scopeCalls = 0, baselineUpdates = 0
const messages = [], configInfo = ref({ revision: 1, listKey: 'list1' })
const saveMetadata = loadMethods('workflow-web/src/views/EntityListConfigDesign.vue', ['saveListMetadata'], {
  writeFixedFilterRows: () => ({}), fixedFilterRows: ref([]), isSystemEntity: ref(false), entityFields: ref([]),
  entityListConfigApi: { patchMetadata: async () => ({ revision: 2 }) }, configId: 'list1', configInfo,
  toolbarRequiresSelection: ref(false), normalizeListSelectionMode: () => 'SINGLE', validateSelectionReturnMappings: () => [],
  viewConfig: ref({}), entityCode: ref('ENTITY'), saveScopeBindings: async () => { scopeCalls++; return false },
  rememberMetadataBaseline: () => { baselineUpdates++ }, loadDiff: async () => {},
  ElMessage: { success: message => messages.push(message) }, handleRevisionConflict: error => { throw error }
}, 'saveListMetadata')
const saved = await saveMetadata()
assert.equal(scopeCalls, 1)
assert.equal(saved, true)
assert.equal(baselineUpdates, 1)
assert.match(messages[0], /数据规则绑定已立即生效/)
console.log('列表设置：数据规则绑定保存返回 false；父流程仍返回 true，并提示“数据规则绑定已立即生效”')
console.log('累计 5 组现有问题复现完成；以上不属于修复后的回归通过结果。')
