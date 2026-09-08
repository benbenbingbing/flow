import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  createEmbedManagementApi,
  sanitizeProvider,
  unwrapEmbedManagementResponse
} from '../../../../api/system/embedManagement.js'
import {
  EMBED_PERMISSIONS,
  assertPublicJwks,
  buildExpectedVersionPayload,
  contextEditorToConfig,
  contextToEditor,
  createContextBinding,
  createContextField,
  describeEmbedManagementError,
  draftToEditor,
  editorToDraft,
  extractViolations,
  hasContextEditorChanges,
  hasEmbedPermission,
  normalizeExactOrigin,
  normalizeExactOrigins,
  normalizeRevokeReason,
  providerToEditor
} from '../embedManagementModel.js'
import {
  buildContextTargetFields,
  contextTargetIssue,
  resolveContextFormRelease,
  validateContextBindingTargets
} from '../embedContextTargetModel.js'

const currentDirectory = path.dirname(fileURLToPath(import.meta.url))
const webRoot = path.resolve(currentDirectory, '../../../..')

assert.deepEqual(
  unwrapEmbedManagementResponse({ data: { code: 200, data: { id: 'ev_1' } } }),
  { id: 'ev_1' },
  '应解包 Axios 形态的统一响应'
)
assert.deepEqual(
  unwrapEmbedManagementResponse({ code: '200', data: ['one'] }),
  ['one'],
  '应解包字符串成功码'
)

const calls = []
const transport = Object.fromEntries(
  ['get', 'post', 'put', 'patch', 'delete'].map(method => [
    method,
    (...args) => {
      calls.push({ method, args })
      if (args[0].includes('identity-providers')) {
        return Promise.resolve({
          data: {
            code: 200,
            data: {
              id: 'eidp_1',
              name: 'OIDC',
              jwks: { keys: [{ kid: 'public', kty: 'RSA', n: 'n', e: 'AQAB' }] }
            }
          }
        })
      }
      return Promise.resolve({ data: { code: 200, data: { version: 8 } } })
    }
  ])
)
const api = createEmbedManagementApi(transport)
await api.views.updateDraft('ev/unsafe', {
  expectedVersion: 7,
  draft: { target: { entityCode: 'order' } }
})
assert.equal(calls[0].method, 'patch')
assert.equal(
  calls[0].args[0],
  '/embed-management/v1/views/ev%2Funsafe/draft',
  '路径参数必须编码'
)
assert.equal(calls[0].args[1].expectedVersion, 7, 'CAS 版本必须原样提交')
assert.equal(calls[0].args[2].silentError, true, '页面应统一展示稳定错误')

await api.views.validation('ev/check')
const validationCall = calls.at(-1)
assert.equal(validationCall.method, 'get')
assert.equal(
  validationCall.args[0],
  '/embed-management/v1/views/ev%2Fcheck/validation',
  '向导校验必须复用编码后的 View 路径'
)

await api.options.applications({
  keyword: '门户', status: 'ACTIVE', pageNum: 1, pageSize: 100
})
const applicationOptionsCall = calls.at(-1)
assert.equal(
  applicationOptionsCall.args[0],
  '/embed-management/v1/options/applications'
)
assert.equal(applicationOptionsCall.args[1].params.keyword, '门户')
assert.equal(applicationOptionsCall.args[1].params.status, 'ACTIVE')
await api.options.identityProviders({
  keyword: '统一登录', status: 'ACTIVE', pageNum: 1, pageSize: 100
})
const providerOptionsCall = calls.at(-1)
assert.equal(
  providerOptionsCall.args[0],
  '/embed-management/v1/options/identity-providers'
)
assert.equal(providerOptionsCall.args[1].params.keyword, '统一登录')
assert.equal(providerOptionsCall.args[1].params.status, 'ACTIVE')

const provider = await api.providers.get('eidp_1')
assert.equal(provider.jwks, undefined, 'Provider 验签材料不应进入页面状态')
assert.equal(providerToEditor(provider).jwksText, '', '编辑 Provider 不得回填 JWKS')
assert.equal(
  sanitizeProvider({ id: 'p', jwks: { keys: [] } }).jwks,
  undefined
)
await api.operations.revokeSession('es/unsafe', { reason: 'SECURITY_INCIDENT' })
const revokeCall = calls.at(-1)
assert.equal(
  revokeCall.args[0],
  '/embed-management/v1/sessions/es%2Funsafe/revoke'
)
assert.equal(revokeCall.args[1].reason, 'SECURITY_INCIDENT')

assert.deepEqual(
  buildExpectedVersionPayload(12, { reason: '测试' }),
  { expectedVersion: 12, reason: '测试' }
)
assert.throws(() => buildExpectedVersionPayload(undefined), /expectedVersion/)
assert.match(
  describeEmbedManagementError({ status: 409, currentData: { currentVersion: 9 } }),
  /当前版本 9/
)
assert.equal(
  describeEmbedManagementError({ status: 413, message: '草稿过大' }),
  '草稿过大'
)
assert.deepEqual(
  extractViolations({
    currentData: { violations: [{ path: 'target', code: 'INVALID', message: '错误' }] }
  }),
  [{ path: 'target', code: 'INVALID', message: '错误' }]
)
assert.equal(normalizeRevokeReason(''), 'ADMINISTRATIVE_REVOKE')
assert.equal(normalizeRevokeReason('SECURITY_INCIDENT:2026-08'), 'SECURITY_INCIDENT:2026-08')
assert.throws(() => normalizeRevokeReason('中文原因'), /1 到 128/)

assert.equal(
  normalizeExactOrigin('https://EXAMPLE.com:443/'),
  'https://example.com'
)
assert.equal(
  normalizeExactOrigin('https://bücher.example:8443'),
  'https://xn--bcher-kva.example:8443'
)
assert.equal(normalizeExactOrigin('https://Example.COM./'), 'https://example.com')
assert.deepEqual(
  normalizeExactOrigins(`
    https://portal.example.com/
    https://PORTAL.example.com:443
  `),
  ['https://portal.example.com'],
  'Origin 应按后端规则规范化后精确去重'
)
for (const invalid of [
  'http://portal.example.com',
  'https://portal.example.com/path',
  'https://portal.example.com?tenant=1',
  'https://portal.example.com/./',
  'https://portal.example.com:',
  'https://*.example.com',
  'https://user:pass@portal.example.com'
]) {
  assert.throws(() => normalizeExactOrigin(invalid), /HTTPS Origin/)
}

assert.doesNotThrow(() => assertPublicJwks({
  keys: [{ kid: 'rsa-1', kty: 'RSA', n: 'public', e: 'AQAB' }]
}))
assert.throws(() => assertPublicJwks({
  keys: [{ kid: 'rsa-1', kty: 'RSA', n: 'public', e: 'AQAB', d: 'private' }]
}), /私钥参数 d/)

const sourceDraft = {
  target: { entityCode: 'old', listKey: 'old_list' },
  entryModes: ['LIST', 'EDIT'],
  releasePolicy: { strategy: 'FOLLOW_ACTIVE' },
  capabilities: ['LIST_QUERY', 'RECORD_UPDATE', 'ACTION_EXECUTE'],
  fieldPolicy: { visible: ['id'], queryable: [], writable: [], returnable: [] },
  actionPolicy: { allowed: [] },
  contextSchema: { type: 'object', additionalProperties: false },
  contextBindings: [],
  ui: { density: 'compact' },
  extensionField: { preserved: true }
}
const draftEditor = draftToEditor(sourceDraft, 'LIST')
assert.deepEqual(draftEditor.entryModes, ['LIST'])
assert.deepEqual(draftEditor.capabilities, ['LIST_QUERY', 'ACTION_EXECUTE'])
draftEditor.entityCode = 'work_order'
const serializedDraft = editorToDraft(draftEditor, 'LIST')
assert.equal(serializedDraft.target.entityCode, 'work_order')
assert.deepEqual(serializedDraft.fieldPolicy, {
  mode: 'FLOW_PUBLISHED',
  returnable: []
})
assert.deepEqual(serializedDraft.entryModes, ['LIST'])
assert.deepEqual(serializedDraft.capabilities, ['LIST_QUERY', 'ACTION_EXECUTE'])
assert.deepEqual(serializedDraft.actionPolicy, { allowed: [] })
assert.equal('releasePolicy' in serializedDraft, false)
assert.deepEqual(
  serializedDraft.extensionField,
  { preserved: true },
  '结构化字段不应丢失高级 JSON 中的扩展配置'
)

const contextConfig = {
  contextSchema: {
    type: 'object', description: '宿主授权上下文', additionalProperties: false,
    properties: {
      supplierId: { type: 'string', minLength: 1, maxLength: 64, title: '供应商', default: 'S-1', examples: ['S-2'] },
      metadata: { type: 'object', properties: { flags: { type: 'array', items: { type: 'boolean' } } }, 'x-owner': 'preserve' },
      optional: { enum: [0, false, null, ''], default: null }
    },
    required: ['supplierId'],
    'x-schema-owner': { preserved: true }
  },
  contextBindings: [{ source: 'supplierId', target: 'supplier_id', usage: 'FIXED_FILTER', 'x-binding': { preserved: true } }]
}
assert.deepEqual(contextEditorToConfig(contextToEditor(contextConfig.contextSchema, contextConfig.contextBindings)), contextConfig,
  '无改动往返应保留所有嵌套 Schema、原生值类型与扩展属性')
for (const schema of [{}, { additionalProperties: true }, { additionalProperties: false }]) {
  assert.deepEqual(contextEditorToConfig(contextToEditor(schema, [])), { contextSchema: schema, contextBindings: [] })
}
const changedContext = contextToEditor(contextConfig.contextSchema, contextConfig.contextBindings)
changedContext.fields[0].name = 'partnerId'
changedContext.fields[0].title = '合作方'
changedContext.fields[0].description = '由第三方后端确认'
changedContext.fields = changedContext.fields.filter(field => field.name !== 'optional')
changedContext.fields.push({ ...createContextField('active', 'new-field'), type: 'boolean', required: true })
changedContext.bindings.push({ ...createContextBinding('new-binding'), source: 'active', target: 'active_flag', usage: 'FORCED_FORM_VALUE', rawBinding: { note: 'keep' } })
changedContext.additionalProperties = 'allow'
const changedConfig = contextEditorToConfig(changedContext)
assert.equal(changedConfig.contextSchema.properties.supplierId, undefined)
assert.equal(changedConfig.contextSchema.properties.partnerId.title, '合作方')
assert.equal(changedConfig.contextSchema.properties.partnerId.description, '由第三方后端确认')
assert.equal(changedConfig.contextSchema.properties.partnerId.default, 'S-1')
assert.deepEqual(changedConfig.contextSchema.properties.partnerId.examples, ['S-2'])
assert.deepEqual(changedConfig.contextSchema.properties.metadata, contextConfig.contextSchema.properties.metadata)
assert.equal(changedConfig.contextSchema.properties.optional, undefined)
assert.equal(changedConfig.contextSchema.properties.active.type, 'boolean')
assert.deepEqual(changedConfig.contextSchema.required, ['partnerId', 'active'])
assert.equal(changedConfig.contextBindings[0].source, 'partnerId', '字段改名应同步原样顶层来源引用')
assert.deepEqual(changedConfig.contextBindings[0]['x-binding'], { preserved: true })
assert.equal(changedConfig.contextBindings[1].note, 'keep')
assert.equal(changedConfig.contextSchema.additionalProperties, true)
assert.deepEqual(contextConfig.contextSchema.required, ['supplierId'], '转换不能修改原始草稿')

const chainedRename = contextToEditor({
  properties: { a: { type: 'string' }, b: { type: 'string' } },
  required: ['a', 'b']
}, [{ source: 'a', target: 'a_id', usage: 'FIXED_FILTER' }])
chainedRename.fields.find(field => field.originalName === 'a').name = 'b'
chainedRename.fields.find(field => field.originalName === 'b').name = 'c'
const chainedRenameConfig = contextEditorToConfig(chainedRename)
assert.equal(chainedRenameConfig.contextBindings[0].source, 'b',
  '多个字段连续改名时，绑定应跟随字段身份，不能继续按新名称级联')
assert.deepEqual(Object.keys(chainedRenameConfig.contextSchema.properties), ['b', 'c'])

const repeatedRename = contextToEditor({
  properties: { a: { type: 'string' } },
  required: ['a']
}, [
  { source: 'a', target: 'a_id', usage: 'FIXED_FILTER' }
])
repeatedRename.fields[0].name = 'b'
repeatedRename.fields[0].name = 'c'
assert.equal(contextEditorToConfig(repeatedRename).contextBindings[0].source, 'c',
  '同一字段多次改名时，绑定应跟随最终名称')

const stableSourceRename = contextToEditor({
  properties: { a: { type: 'string' }, b: { type: 'string' } },
  required: ['a', 'b']
}, [])
const renamedA = stableSourceRename.fields.find(field => field.originalName === 'a')
renamedA.name = 'b'
stableSourceRename.fields.find(field => field.originalName === 'b').name = 'c'
stableSourceRename.bindings.push({
  ...createContextBinding('new-binding-after-rename'),
  sourceFieldId: renamedA.id,
  source: renamedA.name,
  target: 'owner_id',
  usage: 'FIXED_FILTER'
})
assert.equal(contextEditorToConfig(stableSourceRename).contextBindings[0].source, 'b',
  '改名后新增映射必须按字段稳定 id 解析，不能把当前名称再次当成旧名称改写')

const replacedSourceIdentity = contextToEditor({
  properties: { tenantId: { type: 'string' } },
  required: ['tenantId']
}, [{ source: 'tenantId', target: 'tenant_id', usage: 'FIXED_FILTER' }])
replacedSourceIdentity.fields = [{
  ...createContextField('tenantId', 'replacement-tenant'),
  required: true
}]
assert.throws(() => contextEditorToConfig(replacedSourceIdentity), /绑定来源.*未定义/,
  '删除来源字段后，即使新字段复用同名也不能让绑定静默改绑')

const optionalSource = contextToEditor({
  properties: { tenantId: { type: 'string' } },
  required: ['tenantId']
}, [{ source: 'tenantId', target: 'tenant_id', usage: 'FIXED_FILTER' }])
optionalSource.fields[0].required = false
assert.throws(() => contextEditorToConfig(optionalSource), /绑定来源.*必须设为必传/)
const objectSource = contextToEditor({
  properties: { tenant: { type: 'string' } },
  required: ['tenant']
}, [{ source: 'tenant', target: 'tenant_id', usage: 'FIXED_FILTER' }])
objectSource.fields[0].type = 'object'
assert.throws(() => contextEditorToConfig(objectSource), /绑定来源.*必须使用.*string/)

const entityTargetFields = [
  { fieldCode: 'tenant_id', fieldName: '租户', fieldType: 'STRING', editable: true },
  { fieldCode: 'password_hash', fieldName: '密码摘要', fieldType: 'STRING', editable: true },
  { fieldCode: 'readonly_code', fieldName: '只读编码', fieldType: 'STRING', editable: true },
  { fieldCode: 'masked_value', fieldName: '掩码值', fieldType: 'STRING', editable: true },
  { fieldCode: 'draft_only', fieldName: '仅草稿字段', fieldType: 'STRING', editable: true },
  { fieldCode: 'system_code', fieldName: '系统编码', fieldType: 'STRING', editable: false }
]
const publishedListFields = [
  { fieldCode: 'tenant_id', fieldName: '租户', isQuery: true },
  { fieldCode: 'password_hash', fieldName: '密码摘要', isQuery: true },
  { fieldCode: 'readonly_code', fieldName: '只读编码', isQuery: false },
  { fieldCode: 'masked_value', fieldName: '掩码值', isQuery: true }
]
const publishedFormFields = [
  { fieldCode: 'tenant_id', fieldLabel: '租户', isReadonly: false },
  { fieldCode: 'password_hash', fieldLabel: '密码摘要', isReadonly: false },
  { fieldCode: 'readonly_code', fieldLabel: '只读编码', isReadonly: true },
  { fieldCode: 'masked_value', fieldLabel: '掩码值', fieldType: 'PASSWORD', isReadonly: false },
  { fieldCode: 'system_code', fieldLabel: '系统编码', isReadonly: false }
]
const listTargetFields = buildContextTargetFields(
  'LIST', entityTargetFields, publishedListFields, publishedFormFields
)
const listTargetByCode = new Map(listTargetFields.map(field => [field.fieldCode, field]))
assert.equal(listTargetByCode.get('tenant_id').queryable, true)
assert.equal(listTargetByCode.get('tenant_id').writable, true)
assert.equal(listTargetByCode.get('password_hash').queryable, false,
  '敏感字段即使发布为查询字段也不能用作固定过滤')
assert.equal(listTargetByCode.get('masked_value').queryable, false,
  '表单发布快照标记为敏感类型时同样不能用作固定过滤')
assert.equal(listTargetByCode.get('password_hash').writable, true)
assert.equal(listTargetByCode.get('readonly_code').writable, false)
assert.equal(listTargetByCode.get('draft_only').published, false)
assert.equal(listTargetByCode.get('system_code').writable, false,
  'LIST 强制值还必须尊重当前实体字段 editable')
const formTargetFields = buildContextTargetFields(
  'FORM', entityTargetFields, [], publishedFormFields
)
const formTargetByCode = new Map(formTargetFields.map(field => [field.fieldCode, field]))
assert.equal(formTargetByCode.get('tenant_id').queryable, true)
assert.equal(formTargetByCode.get('system_code').writable, true,
  'FORM 可写性只由发布表单只读状态决定')

const pinnedBaseFormRelease = {
  id: 'form-release-base',
  version: 3,
  snapshotDocument: { legacyFields: [{ fieldCode: 'base_field' }] }
}
const effectiveHotfixRelease = {
  id: 'form-release-hotfix',
  version: 4,
  snapshotDocument: { legacyFields: [{ fieldCode: 'hotfix_field' }] }
}
const hotfixedNewDataForm = {
  id: 'form-1',
  runtimeReleaseId: pinnedBaseFormRelease.id,
  runtimeReleaseVersion: pinnedBaseFormRelease.version,
  effectiveReleaseId: effectiveHotfixRelease.id,
  fields: effectiveHotfixRelease.snapshotDocument.legacyFields
}
assert.equal(
  resolveContextFormRelease(
    hotfixedNewDataForm,
    [effectiveHotfixRelease, pinnedBaseFormRelease]
  ),
  pinnedBaseFormRelease,
  'LIST Embed 必须按最终固定的基础发布坐标校验，不能误用流程热修复字段投影'
)
assert.throws(() => resolveContextFormRelease(
  hotfixedNewDataForm,
  [effectiveHotfixRelease]
), /固定发布快照不可用/)

assert.match(contextTargetIssue(listTargetByCode.get('draft_only'), 'FIXED_FILTER'), /不在当前 ACTIVE/)
assert.doesNotThrow(() => validateContextBindingTargets([
  { target: 'tenant_id', usage: 'FIXED_FILTER' }
], listTargetFields, { FIXED_FILTER: true, FORCED_FORM_VALUE: false }))
assert.throws(() => validateContextBindingTargets([
  { target: 'readonly_code', usage: 'FIXED_FILTER' }
], listTargetFields, true), /不可查询/)
assert.throws(() => validateContextBindingTargets([
  { target: 'readonly_code', usage: 'FORCED_FORM_VALUE' }
], listTargetFields, true), /不可写/)
assert.throws(() => validateContextBindingTargets([
  { target: 'tenant_id', usage: 'FORCED_FORM_VALUE' }
], listTargetFields, { FIXED_FILTER: true, FORCED_FORM_VALUE: false }), /尚未完成 ACTIVE/)

const tooManyContextFields = contextToEditor({}, [])
for (let index = 0; index < 33; index += 1) {
  tooManyContextFields.fields.push(createContextField(`field${index}`, `field-${index}`))
}
assert.throws(() => contextEditorToConfig(tooManyContextFields), /最多允许 32 个/)

const duplicateTarget = contextToEditor({
  properties: { tenantId: { type: 'string' } },
  required: ['tenantId']
}, [])
const tenantFieldId = duplicateTarget.fields[0].id
for (const id of ['binding-1', 'binding-2']) {
  duplicateTarget.bindings.push({
    ...createContextBinding(id),
    sourceFieldId: tenantFieldId,
    source: 'tenantId',
    target: 'owner_id',
    usage: 'FIXED_FILTER'
  })
}
assert.throws(() => contextEditorToConfig(duplicateTarget), /同一用途不能重复绑定目标字段/)

const collidingFilterKeys = contextToEditor({
  properties: {
    tenantId: { type: 'string' },
    tenantOperator: { type: 'string' }
  },
  required: ['tenantId', 'tenantOperator']
}, [])
for (const [index, target] of ['owner', 'owner_op'].entries()) {
  collidingFilterKeys.bindings.push({
    ...createContextBinding(`collision-${index}`),
    sourceFieldId: collidingFilterKeys.fields[index].id,
    source: collidingFilterKeys.fields[index].name,
    target,
    usage: 'FIXED_FILTER'
  })
}
assert.throws(() => contextEditorToConfig(collidingFilterKeys), /编码键冲突/)

const contextDirtyState = contextToEditor({}, [])
assert.equal(hasContextEditorChanges(contextDirtyState), false)
contextDirtyState.fields.push(createContextField('tenantId', 'tenant-field'))
assert.equal(hasContextEditorChanges(contextDirtyState), true,
  '覆盖式 JSON 重载必须识别可视化上下文的未保存修改')

for (const type of ['string', 'integer', 'number', 'boolean', 'object', 'array', 'null', '', 'unset']) {
  const editor = contextToEditor({}, [])
  editor.fields.push({ ...createContextField('value'), type })
  const field = contextEditorToConfig(editor).contextSchema.properties.value
  assert.equal(field.type, ['', 'unset'].includes(type) ? undefined : type)
}
for (const mode of ['allow', 'deny', 'unset']) {
  const editor = contextToEditor({ additionalProperties: mode === 'deny' }, [])
  editor.additionalProperties = mode
  const schema = contextEditorToConfig(editor).contextSchema
  assert.equal(schema.additionalProperties, mode === 'unset' ? undefined : mode === 'allow')
}
const clearedLabels = contextToEditor({ properties: { field: { type: 'string', title: '标题', description: '说明' } } }, [])
Object.assign(clearedLabels.fields[0], { type: '', title: '', description: '' })
assert.deepEqual(contextEditorToConfig(clearedLabels).contextSchema.properties.field, {})
const removedRequired = contextToEditor(contextConfig.contextSchema, [])
removedRequired.fields[0].required = false
assert.deepEqual(contextEditorToConfig(removedRequired).contextSchema.required, [])
const removedBinding = contextToEditor(contextConfig.contextSchema, contextConfig.contextBindings)
removedBinding.fields = removedBinding.fields.filter(field => field.name !== 'supplierId')
assert.throws(() => contextEditorToConfig(removedBinding), /绑定来源.*未定义/)
removedBinding.bindings = []
assert.equal(contextEditorToConfig(removedBinding).contextSchema.properties.supplierId, undefined)
const duplicateField = contextToEditor(contextConfig.contextSchema, [])
duplicateField.fields.push(createContextField('supplierId'))
assert.throws(() => contextEditorToConfig(duplicateField), /字段.*重复/)

const hostileSchema = JSON.parse('{"properties":{"__proto__":{"type":"string","default":"safe"},"constructor":{"type":"boolean"}},"required":["__proto__"]}')
const hostile = contextToEditor(hostileSchema, [{ source: '__proto__', target: 'supplier_id', usage: 'FIXED_FILTER' }])
hostile.fields[0].description = '普通上下文字段'
const hostileResult = contextEditorToConfig(hostile)
assert.equal(Object.getPrototypeOf(hostileResult.contextSchema.properties), Object.prototype)
assert.equal(Object.hasOwn(hostileResult.contextSchema.properties, '__proto__'), true)
assert.equal(hostileResult.contextSchema.properties.__proto__.description, '普通上下文字段')
assert.equal({}.description, undefined, '字段序列化不得修改 Object.prototype')
assert.equal(hostileResult.contextBindings[0].source, '__proto__')

const visualEditor = draftToEditor({ ...sourceDraft, ...contextConfig })
const advanced = JSON.parse(visualEditor.advancedJson)
advanced.ui.extra = 'preserve'
advanced.contextSchema.properties.supplierId.examples = ['S-latest']
advanced.contextBindings[0]['x-binding'].latest = true
visualEditor.advancedJson = JSON.stringify(advanced)
visualEditor.contextEditor.fields[0].title = '结构化标题'
const mergedContextDraft = editorToDraft(visualEditor)
assert.equal(mergedContextDraft.contextSchema.properties.supplierId.title, '结构化标题')
assert.deepEqual(mergedContextDraft.contextSchema.properties.supplierId.examples, ['S-latest'])
assert.equal(mergedContextDraft.contextBindings[0]['x-binding'].latest, true)
assert.equal(mergedContextDraft.ui.extra, 'preserve')

const advancedOnlyEditor = draftToEditor({ ...sourceDraft, ...contextConfig })
const advancedOnly = JSON.parse(advancedOnlyEditor.advancedJson)
advancedOnly.contextSchema.properties.supplierId.type = 'integer'
advancedOnly.contextBindings[0].target = 'other_id'
advancedOnlyEditor.advancedJson = JSON.stringify(advancedOnly)
assert.deepEqual(editorToDraft(advancedOnlyEditor).contextSchema, advancedOnly.contextSchema,
  '只编辑高级 JSON 时不应被未修改的可视化状态覆盖')
assert.deepEqual(editorToDraft(advancedOnlyEditor).contextBindings, advancedOnly.contextBindings)
advancedOnlyEditor.contextEditor.fields[0].title = '同时修改'
assert.throws(() => editorToDraft(advancedOnlyEditor), /高级 JSON 与上下文表单均有修改/)
delete advancedOnlyEditor.contextEditor
assert.deepEqual(editorToDraft(advancedOnlyEditor).contextBindings, advancedOnly.contextBindings,
  '旧调用方不携带 contextEditor 时仍兼容高级 JSON 保存')

const followFormEditor = draftToEditor({
  target: { entityCode: 'ZDWREQ', defaultFormId: 'form-1' },
  entryModes: ['CREATE', 'VIEW'],
  releasePolicy: { strategy: 'FOLLOW_ACTIVE' },
  capabilities: ['RECORD_CREATE', 'RECORD_VIEW', 'ACTION_EXECUTE'],
  fieldPolicy: { mode: 'FLOW_PUBLISHED', returnable: ['code'] },
  actionPolicy: { allowed: ['save'] },
  contextSchema: {},
  contextBindings: [],
  ui: {}
}, 'FORM')
assert.equal(followFormEditor.fieldPolicyMode, 'FLOW_PUBLISHED')
assert.deepEqual(
  followFormEditor.capabilities,
  ['RECORD_CREATE', 'RECORD_VIEW', 'ACTION_EXECUTE']
)
followFormEditor.visibleFieldsText = 'must_not_leak_into_follow_mode'
const followFormDraft = editorToDraft(followFormEditor, 'FORM')
assert.deepEqual(followFormDraft.fieldPolicy, {
  mode: 'FLOW_PUBLISHED',
  returnable: ['code']
})
assert.deepEqual(
  followFormDraft.capabilities,
  ['RECORD_CREATE', 'RECORD_VIEW', 'ACTION_EXECUTE']
)
assert.equal('releasePolicy' in followFormDraft, false)

const legacyExplicitFormEditor = draftToEditor({
  target: { entityCode: 'ZDWREQ', defaultFormId: 'form-1' },
  fieldPolicy: {
    mode: 'EXPLICIT',
    visible: ['req_desc'],
    writable: ['req_desc'],
    returnable: []
  }
}, 'FORM')
assert.equal(legacyExplicitFormEditor.fieldPolicyMode, 'FLOW_PUBLISHED')
assert.deepEqual(
  editorToDraft(legacyExplicitFormEditor, 'FORM').fieldPolicy,
  { mode: 'FLOW_PUBLISHED', returnable: [] },
  '旧 FORM 显式字段草稿再次保存时必须迁移为 Flow 发布表单引用'
)

assert.equal(
  hasEmbedPermission([], EMBED_PERMISSIONS.manage),
  false,
  '无权限时操作默认隐藏'
)
assert.equal(
  hasEmbedPermission([EMBED_PERMISSIONS.manage], EMBED_PERMISSIONS.manage),
  true
)

const managementSource = fs.readFileSync(
  path.join(webRoot, 'views/system/EmbedManagement.vue'),
  'utf8'
)
const viewSource = fs.readFileSync(
  path.join(webRoot, 'views/system/embed-management/EmbedViewWorkspace.vue'),
  'utf8'
)
const draftSource = fs.readFileSync(
  path.join(webRoot, 'views/system/embed-management/EmbedViewDraftPanel.vue'),
  'utf8'
)
const setupGuideSource = fs.readFileSync(
  path.join(webRoot, 'views/system/embed-management/EmbedSetupGuide.vue'),
  'utf8'
)
const grantSource = fs.readFileSync(
  path.join(webRoot, 'views/system/embed-management/EmbedGrantPanel.vue'),
  'utf8'
)
const runtimeSource = fs.readFileSync(
  path.join(webRoot, 'views/system/embed-management/EmbedProviderPanel.vue'),
  'utf8'
)
const bindingSource = fs.readFileSync(
  path.join(webRoot, 'views/system/embed-management/EmbedBindingPanel.vue'),
  'utf8'
)
const operationsSource = fs.readFileSync(
  path.join(webRoot, 'views/system/embed-management/EmbedOperationsWorkspace.vue'),
  'utf8'
)
const routerSource = fs.readFileSync(path.join(webRoot, 'router/index.js'), 'utf8')

const algorithmOptionsSource = runtimeSource.match(
  /const algorithmOptions = \[([\s\S]*?)\]\nconst supportedAlgorithmSet/
)
assert.ok(algorithmOptionsSource, 'Provider 页面必须声明封闭的算法选项')
const displayedAlgorithms = [...algorithmOptionsSource[1].matchAll(/'([^']+)'/g)]
  .map(match => match[1])
assert.deepEqual(displayedAlgorithms, [
  'RS256', 'RS384', 'RS512',
  'PS256', 'PS384', 'PS512',
  'ES256', 'ES384', 'ES512'
])
assert.doesNotMatch(algorithmOptionsSource[1], /EdDSA|OKP/)
assert.match(
  runtimeSource,
  /providerForm\.algorithms = normalizeSupportedAlgorithms\(providerForm\.algorithms\)/,
  '编辑历史 Provider 时必须移除不再支持的算法'
)
assert.match(
  runtimeSource,
  /algorithms: providerForm\.type === 'SIGNED_JWT'[\s\S]*?\? normalizeSupportedAlgorithms\(providerForm\.algorithms\)/,
  'Provider payload 必须通过同一算法白名单序列化'
)

assert.match(managementSource, /v-if="canView"/)
assert.match(managementSource, /v-if="canManageIdentity"/)
assert.match(viewSource, /v-if="canManage"/)
assert.match(operationsSource, /v-if="canRevoke"/)
assert.match(operationsSource, /revokeViewSessions/)
assert.match(operationsSource, /revokeApplicationSessions/)
for (const [source, helpKey] of [
  [viewSource, 'embed.view.key'],
  [draftSource, 'embed.view.defaultFormId'],
  [runtimeSource, 'embed.provider.type'],
  [bindingSource, 'embed.binding.externalSubject'],
  [operationsSource, 'embed.operations.queryScope']
]) {
  assert.match(source, new RegExp(`help-key="${helpKey.replaceAll('.', '\\.')}"`))
}
assert.match(draftSource, /每次新 Launch 使用目标资源最新 ACTIVE 版本/)
assert.match(draftSource, /EntityDefinitionPicker/)
assert.match(draftSource, /:show-code="false"/)
for (const label of ['实体', '列表', '目标表单']) {
  assert.match(draftSource, new RegExp(`ConfigHelpLabel[^>]+label="${label}"`))
}
assert.doesNotMatch(
  draftSource,
  /ConfigHelpLabel[^>]+label="(?:Entity Code|List Key|Form ID)"/,
  '目标选择器只能显示业务名称标签，不能把内部 ID/Key 当作产品标签'
)
assert.match(draftSource, /:label="item\.listName \|\| '未命名列表'"/)
assert.match(draftSource, /:label="item\.formName \|\| '未命名表单'"/)
assert.doesNotMatch(draftSource, /label="(?:Visible|Queryable|Writable)"/)
assert.match(draftSource, /Flow 原生\{\{ isList \? '列表' : '表单' \}\}/)
assert.match(draftSource, /宿主上下文/)
assert.match(draftSource, /editor\.contextEditor\.fields/)
assert.match(draftSource, /editor\.contextEditor\.bindings/)
assert.match(draftSource, /v-model="row\.sourceFieldId"/)
assert.match(draftSource, /row-key="id"/)
assert.match(draftSource, /从 JSON 重新载入上下文/)
assert.match(draftSource, /hasContextEditorChanges\(editor\.value\.contextEditor\)/)
assert.match(draftSource, /ElMessageBox\.confirm/)
assert.match(
  draftSource,
  /Array\.isArray\(knownEntity\?\.fields\)[\s\S]*entityApi\.getById\(entityId\)/,
  '实体选择器返回轻量选项时必须继续读取完整字段定义'
)
assert.doesNotMatch(draftSource, /\['ACTION_EXECUTE'\]/)
for (const alertTag of draftSource.matchAll(/<el-alert[\s\S]*?\/>/g)) {
  assert.ok(
    (alertTag[0].match(/\btype=/g) || []).length <= 1,
    'el-alert 不能重复声明 type 属性'
  )
}
assert.doesNotMatch(draftSource, /FOLLOW_ACTIVE|PINNED|Release ID|发布 Embed View|保存草稿/)
assert.doesNotMatch(grantSource, /revisionMode|pinnedRevision|FOLLOW_ACTIVE|PINNED/)
assert.doesNotMatch(viewSource, /Release 历史|publishedRevision|EmbedReleasePanel/)
assert.match(viewSource, /EmbedSetupGuide/)
assert.match(viewSource, /v-for="application in visibleApplicationOptions"/)
assert.match(viewSource, /applicationSearchOptions\.value = incoming/)
assert.match(viewSource, /return \[selected, \.\.\.applicationSearchOptions\.value\]/)
assert.match(setupGuideSource, /<el-steps/)
for (const step of ['目标页面', '应用授权', '测试用户映射', '接入检查']) {
  assert.match(setupGuideSource, new RegExp(step))
}
assert.match(setupGuideSource, /可开始联调/)
assert.match(setupGuideSource, /selectedApplication\.value\.embedLaunchReady/)
assert.match(setupGuideSource, /有效凭据或 embed\.launch Scope/)
assert.match(setupGuideSource, /embedManagementApi\.views\.validation\(props\.view\.id\)/)
assert.match(setupGuideSource, /validation\.value\?\.valid === false/)
assert.match(setupGuideSource, /validation\.value\.viewStatus !== 'ACTIVE'/)
assert.match(setupGuideSource, /item\.flowUserReady === true/)
assert.match(setupGuideSource, /step\.key === 'target' && canManage/)
assert.match(
  setupGuideSource,
  /step\.key === 'grant' && canManage && matchingGrant\?\.status !== 'REVOKED'/,
  '无管理权限或 Grant 已撤销时，向导不能展示可编辑入口'
)
assert.match(viewSource, /:can-manage="canManage"/)
assert.match(grantSource, /function openEdit\(row\) \{\s*if \(!canManage\.value \|\| row\?\.status === 'REVOKED'\) return/)
assert.match(grantSource, /async function configure[\s\S]*?if \(!canManage\.value\) return[\s\S]*?if \(existing\.status === 'REVOKED'\) return/)
assert.match(grantSource, /embedManagementApi\.options\.applications/)
assert.match(grantSource, /embedManagementApi\.options\.identityProviders/)
assert.match(grantSource, /placeholder="按应用名称搜索"/)
assert.match(grantSource, /:remote-method="loadActiveApplicationOptions"/)
assert.match(grantSource, /:remote-method="loadActiveProviderOptions"/)
assert.match(
  grantSource,
  /function loadActiveApplicationOptions[\s\S]*?options\.applications\(\{[\s\S]*?status: 'ACTIVE'/,
  'Grant 创建态应用搜索必须在服务端过滤 ACTIVE'
)
assert.match(
  grantSource,
  /function loadActiveProviderOptions[\s\S]*?options\.identityProviders\(\{[\s\S]*?status: 'ACTIVE'/,
  'Grant 创建态 Provider 搜索必须在服务端过滤 ACTIVE'
)
assert.match(grantSource, /applicationSearchOptions\.value = rows/)
assert.match(grantSource, /providerSearchOptions\.value = rows/)
assert.match(
  grantSource,
  /function loadApplicationOptionById[\s\S]*?status: undefined[\s\S]*?\.find\(item => item\.id === applicationId\)/,
  'Grant 历史应用必须按 ID 无状态补拉并精确合并'
)
assert.match(
  grantSource,
  /function loadProviderOptionById[\s\S]*?status: undefined[\s\S]*?\.find\(item => item\.id === providerId\)/,
  'Grant 历史 Provider 必须按 ID 无状态补拉并精确合并'
)
assert.doesNotMatch(grantSource, /allow-create/)
assert.match(bindingSource, /<UserSelector/)
assert.match(bindingSource, /placeholder="按姓名或账号选择用户"/)
assert.match(bindingSource, /:remote-method="loadActiveApplicationOptions"/)
assert.match(bindingSource, /:remote-method="loadActiveProviderOptions"/)
assert.match(
  bindingSource,
  /function loadActiveApplicationOptions[\s\S]*?options\.applications\(\{[\s\S]*?status: 'ACTIVE'/,
  'Binding 创建态应用搜索必须在服务端过滤 ACTIVE'
)
assert.match(
  bindingSource,
  /function loadActiveProviderOptions[\s\S]*?options\.identityProviders\(\{[\s\S]*?status: 'ACTIVE'/,
  'Binding 创建态 Provider 搜索必须在服务端过滤 ACTIVE'
)
assert.match(bindingSource, /activeApplicationSearchOptions\.value = rows/)
assert.match(bindingSource, /activeProviderSearchOptions\.value = rows/)
assert.match(
  bindingSource,
  /function loadApplicationOptionById[\s\S]*?status: undefined[\s\S]*?\.find\(item => item\.id === applicationId\)/,
  'Binding 历史应用必须按 ID 无状态补拉并精确合并'
)
assert.match(
  bindingSource,
  /function loadProviderOptionById[\s\S]*?status: undefined[\s\S]*?\.find\(item => item\.id === providerId\)/,
  'Binding 历史 Provider 必须按 ID 无状态补拉并精确合并'
)
assert.doesNotMatch(bindingSource, /allow-create/)
assert.doesNotMatch(operationsSource, /二选一/)
assert.match(operationsSource, /Application ID（至少填一项）/)
assert.match(operationsSource, /View ID（至少填一项）/)
assert.match(routerSource, /path: '\/system\/embed-management'/)
assert.doesNotMatch(routerSource, /system:embed:publish/)
for (const permission of Object.values(EMBED_PERMISSIONS)) {
  assert.match(routerSource, new RegExp(permission.replaceAll(':', '\\:')))
}
for (const source of [runtimeSource, bindingSource, operationsSource]) {
  assert.doesNotMatch(source, /localStorage\.(setItem|getItem)/)
  assert.doesNotMatch(source, /console\.(log|info|debug|warn|error)/)
}

console.log('embed management contract tests passed')
