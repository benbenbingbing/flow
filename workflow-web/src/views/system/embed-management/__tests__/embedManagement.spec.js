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
  describeEmbedManagementError,
  draftToEditor,
  editorToDraft,
  extractViolations,
  hasEmbedPermission,
  normalizeExactOrigin,
  normalizeExactOrigins,
  normalizeRevokeReason,
  providerToEditor
} from '../embedManagementModel.js'

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
