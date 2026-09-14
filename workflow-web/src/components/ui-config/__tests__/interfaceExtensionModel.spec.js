import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  defaultInterfaceDebugUsage,
  interfaceImplementationTypeOptions,
  interfacesForEvent,
  interfacesForUsage,
  isInterfaceUsageCompatible,
  mergeInterfaceExecutionPolicy,
  normalizeMutableInterfaceBinding,
  normalizeInterfaceExtension,
  normalizeInterfaceExtensions,
  parseInterfaceEditorJson,
  requiresInterfaceProvider,
  resolveInterfaceExtensionId
} from '../interfaceExtensionModel.js'
import { eventGroupsForScope, eventsForScope } from '../uiEventScope.js'

const catalogItem = {
  extensionId: 'ext-read',
  key: 'project.detail',
  displayName: '项目详情',
  implementationType: 'REGISTERED_PROVIDER',
  kind: 'read',
  contextType: 'form',
  inputSchema: '{"type":"object"}',
  outputSchema: { type: 'object' },
  status: 'ACTIVE'
}
const normalized = normalizeInterfaceExtension(catalogItem)
assert.equal(normalized.extensionType, 'INTERFACE')
assert.equal(normalized.extensionId, 'ext-read')
assert.equal(normalized.extensionKey, 'project.detail')
assert.equal(normalized.interfaceKind, 'READ')
assert.equal(normalized.interfaceContextType, 'FORM')
assert.deepEqual(normalized.inputSchema, { type: 'object' })
assert.deepEqual(normalized.outputSchema, { type: 'object' })

assert.deepEqual(
  normalizeInterfaceExtensions([catalogItem, null, {}]).map(item => item.extensionId),
  ['ext-read']
)
assert.equal(resolveInterfaceExtensionId({ extensionId: 'direct' }, []), 'direct')
assert.equal(resolveInterfaceExtensionId({ interfaceExtensionId: 'column' }, []), 'column')
assert.equal(resolveInterfaceExtensionId({
  serviceId: 'legacy-service',
  operationCode: 'detail'
}, [{
  id: 'migrated-extension',
  legacyServiceId: 'legacy-service',
  providerOperationCode: 'detail'
}]), 'migrated-extension', '历史二段绑定只允许在读取时映射为扩展 ID')

const mutableBinding = normalizeMutableInterfaceBinding({
  serviceId: 'legacy-service',
  sourceCode: 'legacy-source',
  serviceName: '历史服务',
  serviceRevision: 3,
  operationCode: 'detail',
  operationName: '查询详情',
  executableSnapshot: { provider: 'legacy-provider' },
  definitionHash: 'old-hash',
  inputMapping: { id: 'data.id' },
  keepForBinding: true
}, [{
  id: 'migrated-extension',
  legacyServiceId: 'legacy-service',
  providerOperationCode: 'detail'
}])
assert.equal(mutableBinding.extensionId, 'migrated-extension')
assert.deepEqual(mutableBinding.extra, { keepForBinding: true })
assert.deepEqual({
  ...mutableBinding.extra,
  extensionId: mutableBinding.extensionId
}, {
  keepForBinding: true,
  extensionId: 'migrated-extension'
}, '兼容输入再次保存时，接口身份只能写回 extensionId')

const usageItems = [
  catalogItem,
  {
    extensionId: 'ext-write',
    extensionKey: 'project.save',
    interfaceKind: 'WRITE',
    interfaceContextType: 'FORM',
    status: 'ACTIVE'
  },
  {
    extensionId: 'ext-list',
    extensionKey: 'project.list',
    interfaceKind: 'READ',
    interfaceContextType: 'LIST',
    status: 'ACTIVE'
  },
  {
    extensionId: 'ext-disabled',
    extensionKey: 'project.disabled',
    interfaceKind: 'READ',
    interfaceContextType: 'FORM',
    status: 'DISABLED'
  }
]
assert.deepEqual(
  interfacesForUsage(usageItems, 'FIELD_OPTIONS').map(item => item.extensionId),
  ['ext-read']
)
assert.deepEqual(
  interfacesForUsage(usageItems, 'LIST_QUERY').map(item => item.extensionId),
  ['ext-list']
)
assert.equal(isInterfaceUsageCompatible(usageItems[1], 'FORM_SAVE'), true)
assert.equal(isInterfaceUsageCompatible(usageItems[1], 'FIELD_OPTIONS'), false)
assert.deepEqual(
  interfacesForEvent(usageItems, 'FORM_BUTTON_CLICK').map(item => item.extensionId),
  ['ext-read', 'ext-list', 'ext-disabled'],
  '表单自定义按钮只允许无副作用的 READ 接口'
)
assert.equal(defaultInterfaceDebugUsage(catalogItem), 'DETAIL_LOAD')
assert.equal(defaultInterfaceDebugUsage(usageItems[1]), 'DATA_UPDATE')
assert.equal(defaultInterfaceDebugUsage(usageItems[2]), 'LIST_LOAD')

assert.deepEqual(
  interfaceImplementationTypeOptions.map(option => option.value),
  [
    'DICTIONARY',
    'STATIC_OPTIONS',
    'REGISTERED_PROVIDER',
    'RUNTIME_CONTEXT',
    'STRUCTURED_COMPUTE'
  ]
)
assert.equal(requiresInterfaceProvider('REGISTERED_PROVIDER'), true)
assert.equal(requiresInterfaceProvider('DICTIONARY'), false)
assert.deepEqual(parseInterfaceEditorJson('', '配置'), {})
assert.throws(() => parseInterfaceEditorJson('{', '输入 Schema'), /不是合法 JSON/)
assert.deepEqual(mergeInterfaceExecutionPolicy({
  failurePolicy: 'EMPTY',
  retryBudget: 2
}, {
  timeoutMs: 4200,
  cacheSeconds: 30
}), {
  failurePolicy: 'EMPTY',
  retryBudget: 2,
  timeoutMs: 4200,
  cacheSeconds: 30
}, '编辑超时和缓存不能丢失 EMPTY 或未知的后续策略')
assert.equal(mergeInterfaceExecutionPolicy({
  failurePolicy: 'NULL'
}, {
  timeoutMs: 3000,
  cacheSeconds: 0
}).failurePolicy, 'NULL')

const eventBindingEditorSource = readFileSync(
  new URL('../EventBindingEditor.vue', import.meta.url),
  'utf8'
)
assert.match(eventBindingEditorSource, /v-model="step\.extensionId"/)
assert.match(eventBindingEditorSource, /help-key="uiEvent\.extensionInterface"/)
assert.doesNotMatch(eventBindingEditorSource, /v-model="step\.(serviceId|operationCode)"/)
assert.match(eventBindingEditorSource, /最终链必须且只能包含一个主处理/)
assert.doesNotMatch(
  eventBindingEditorSource,
  /FORM_BUTTON_CLICK:\s*'执行该表单按钮原有的内置动作'/
)

const interfaceEditorSource = readFileSync(
  new URL('../InterfaceExtensionEditorDialog.vue', import.meta.url),
  'utf8'
)
assert.match(interfaceEditorSource, /extensionType:\s*'INTERFACE'/)
assert.match(interfaceEditorSource, /providerOperationCode/)
assert.doesNotMatch(interfaceEditorSource, /\boperations\b/)

assert.deepEqual(
  eventGroupsForScope('FORM', 'OWNER').map(group => group.label),
  ['表单生命周期', '表单数据', '字段默认事件', '子表单默认事件', '表单按钮']
)
assert.equal(eventsForScope('FORM').includes('FORM_SAVE'), true)
assert.equal(eventsForScope('FORM').includes('LIST_LOAD'), false)
assert.deepEqual(eventsForScope('LIST', 'BUTTON'), [
  'TOOLBAR_BUTTON_CLICK',
  'ROW_BUTTON_CLICK'
])

console.log('interface extension model tests passed')
