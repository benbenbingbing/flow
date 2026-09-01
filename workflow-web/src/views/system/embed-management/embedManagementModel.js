export const EMBED_PERMISSIONS = Object.freeze({
  view: 'system:embed:view',
  manage: 'system:embed:manage',
  identityManage: 'system:embed:identity-manage',
  sessionRevoke: 'system:embed:session-revoke'
})

export const EMBED_CAPABILITIES = Object.freeze([
  'LIST_QUERY',
  'SELECTION_RETURN',
  'RECORD_VIEW',
  'RECORD_CREATE',
  'ACTION_EXECUTE'
])

export const EMBED_V1_BLOCKED_CAPABILITIES = Object.freeze([
  'RECORD_UPDATE',
  'PROCESS_START',
  'RECORD_DELETE',
  'BATCH_DELETE',
  'EXPORT',
  'FILE_UPLOAD',
  'FILE_DOWNLOAD'
])

const PRIVATE_JWK_FIELDS = new Set([
  'd', 'p', 'q', 'dp', 'dq', 'qi', 'oth', 'k'
])
const V1_CAPABILITY_SET = new Set(EMBED_CAPABILITIES)

function capabilityAllowedForSurface(capability, surfaceType) {
  return V1_CAPABILITY_SET.has(capability)
}

function v1EntryModes(surfaceType) {
  return new Set(surfaceType === 'LIST'
    ? ['LIST', 'CREATE', 'VIEW']
    : ['CREATE', 'VIEW'])
}

/**
 * 统一管理台权限判断；服务端仍会对每个接口执行最终鉴权。
 */
export function hasEmbedPermission(
  permissions,
  permission,
  isSuperAdmin = false
) {
  const values = Array.isArray(permissions) ? permissions : []
  return Boolean(
    isSuperAdmin
    || values.includes('*')
    || values.includes(permission)
  )
}

export function parseJsonObject(text, label = 'JSON') {
  let value
  try {
    value = JSON.parse(String(text || '').trim() || '{}')
  } catch (error) {
    throw new Error(`${label}语法错误：${error.message}`)
  }
  if (!value || Array.isArray(value) || typeof value !== 'object') {
    throw new Error(`${label}必须是 JSON Object`)
  }
  return value
}

export function parseLineValues(text) {
  return [...new Set(
    String(text || '')
      .split(/[\n,]/)
      .map(value => value.trim())
      .filter(Boolean)
  )]
}

export function toLineValues(values) {
  return Array.isArray(values) ? values.join('\n') : ''
}

/**
 * 与后端 ExactOriginPolicy 对齐：只接受无路径、Query、Fragment、凭据或通配符的 HTTPS Origin。
 */
export function normalizeExactOrigin(candidate) {
  const text = String(candidate || '').trim()
  if (!text || text.length > 2048 || text.includes('*') || text.includes('%')) {
    throw new Error('只允许精确 HTTPS Origin')
  }
  if (!text.toLowerCase().startsWith('https://')) {
    throw new Error('只允许精确 HTTPS Origin')
  }
  const remainder = text.slice('https://'.length)
  const componentIndex = remainder.search(/[/?#]/)
  const authority = componentIndex < 0
    ? remainder
    : remainder.slice(0, componentIndex)
  const suffix = componentIndex < 0 ? '' : remainder.slice(componentIndex)
  if (!authority || authority.includes('@') || (suffix && suffix !== '/')) {
    throw new Error('只允许不含路径、Query、Fragment 或通配符的精确 HTTPS Origin')
  }
  validateRawAuthority(authority)
  let parsed
  try {
    parsed = new URL(`https://${authority}`)
  } catch {
    throw new Error('只允许精确 HTTPS Origin')
  }
  if (
    parsed.protocol !== 'https:'
    || !parsed.hostname
    || parsed.username
    || parsed.password
    || parsed.search
    || parsed.hash
  ) {
    throw new Error('只允许不含路径、Query、Fragment 或通配符的精确 HTTPS Origin')
  }
  // URL 会同时完成域名小写、IDN ASCII 转换与默认 443 端口移除。
  const hostname = parsed.hostname.endsWith('.')
    ? parsed.hostname.slice(0, -1)
    : parsed.hostname
  const normalized = `https://${hostname}${parsed.port ? `:${parsed.port}` : ''}`
  if (normalized.length > 255) {
    throw new Error('只允许精确 HTTPS Origin')
  }
  return normalized
}

export function normalizeExactOrigins(text) {
  const candidates = parseLineValues(text)
  if (candidates.length < 1 || candidates.length > 20) {
    throw new Error('请配置 1 到 20 个精确 HTTPS Origin')
  }
  return [...new Set(candidates.map(normalizeExactOrigin))]
}

export function assertPublicJwks(value) {
  if (!value || typeof value !== 'object' || !Array.isArray(value.keys)) {
    throw new Error('JWKS 必须是包含 keys 数组的 JSON Object')
  }
  if (value.keys.length < 1 || value.keys.length > 20) {
    throw new Error('JWKS 必须包含 1 到 20 把公钥')
  }
  const keyIds = new Set()
  for (const key of value.keys) {
    if (!key || typeof key !== 'object' || Array.isArray(key)) {
      throw new Error('JWKS 中的每把公钥必须是 JSON Object')
    }
    for (const field of Object.keys(key)) {
      if (PRIVATE_JWK_FIELDS.has(field)) {
        throw new Error(`JWKS 禁止包含私钥参数 ${field}`)
      }
    }
    if (!key.kid || keyIds.has(key.kid)) {
      throw new Error('每把 JWK 必须使用唯一的 kid')
    }
    keyIds.add(key.kid)
  }
  return value
}

export function providerToEditor(provider = {}) {
  return {
    id: provider.id || '',
    version: provider.version ?? null,
    name: provider.name || '',
    type: provider.type || 'SIGNED_JWT',
    issuer: provider.issuer || '',
    subjectNamespace: provider.subjectNamespace || '',
    audiencesText: toLineValues(provider.audiences),
    algorithms: Array.isArray(provider.algorithms)
      ? [...provider.algorithms]
      : ['RS256'],
    jwksMode: provider.jwksMode || 'STATIC_JWK_SET',
    jwksUrl: provider.jwksUrl || '',
    // 故意不从响应回填 JWKS，避免验签材料在页面状态中长时间留存。
    jwksText: '',
    clockSkewSeconds: provider.clockSkewSeconds ?? 30,
    maxAssertionLifetimeSeconds:
      provider.maxAssertionLifetimeSeconds ?? 120
  }
}

export function draftToEditor(draft = {}, surfaceType = 'LIST') {
  const source = cloneJson(draft)
  const target = source.target || {}
  const fieldPolicy = source.fieldPolicy || {}
  return {
    entityCode: target.entityCode || '',
    listKey: target.listKey || '',
    defaultFormId: target.defaultFormId || target.formId || '',
    entryModes: Array.isArray(source.entryModes)
      ? source.entryModes.filter(mode => v1EntryModes(surfaceType).has(mode))
      : [surfaceType === 'LIST' ? 'LIST' : 'VIEW'],
    capabilities: Array.isArray(source.capabilities)
      ? source.capabilities.filter(capability =>
          capabilityAllowedForSurface(capability, surfaceType))
      : [],
    // LIST/FORM 都直接挂载 Flow 原生页；旧 EXPLICIT 草稿再次保存时
    // 会迁移为发布态资源引用，不再由 Embed 参数改写字段。
    fieldPolicyMode: 'FLOW_PUBLISHED',
    visibleFieldsText: toLineValues(fieldPolicy.visible),
    queryableFieldsText: toLineValues(fieldPolicy.queryable),
    writableFieldsText: toLineValues(fieldPolicy.writable),
    returnableFieldsText: toLineValues(fieldPolicy.returnable),
    advancedJson: JSON.stringify(source, null, 2)
  }
}

/**
 * 高级 JSON 保留未在表单中展示的字段，基础表单则覆盖它所负责的受控路径。
 */
export function editorToDraft(editor, surfaceType = 'LIST') {
  const draft = parseJsonObject(editor.advancedJson, '高级配置 JSON')
  draft.target = {
    ...(draft.target || {}),
    entityCode: String(editor.entityCode || '').trim()
  }
  if (surfaceType === 'LIST') {
    draft.target.listKey = String(editor.listKey || '').trim()
  } else {
    delete draft.target.listKey
  }
  if (surfaceType === 'FORM' && editor.defaultFormId) {
    draft.target.defaultFormId = String(editor.defaultFormId).trim()
    delete draft.target.formId
  } else {
    delete draft.target.defaultFormId
    delete draft.target.formId
  }
  const allowedModes = v1EntryModes(surfaceType)
  draft.entryModes = [...(editor.entryModes || [])]
    .filter(mode => allowedModes.has(mode))
  // Embed 只保存资源稳定坐标；目标发布版本由每次 Launch 在服务端解析。
  delete draft.releasePolicy
  draft.capabilities = [...(editor.capabilities || [])]
    .filter(capability => capabilityAllowedForSurface(capability, surfaceType))
  const returnable = parseLineValues(editor.returnableFieldsText)
  // 显示、查询、布局和可写状态由 Flow 已发布列表/表单及映射
  // 用户权限决定；仅宿主事件回传字段继续显式维护。
  draft.fieldPolicy = {
    mode: 'FLOW_PUBLISHED',
    returnable
  }
  // 原生 LIST/FORM 的按钮、顺序、文案和实时可用状态全部来自
  // Flow 页面与 mapped user；清理旧投影 override，避免继续误导。
  draft.actionPolicy = { allowed: [] }
  draft.contextSchema ||= {}
  draft.contextBindings ||= []
  draft.ui ||= {}
  return draft
}

export function buildExpectedVersionPayload(version, extra = {}) {
  const expectedVersion = Number(version)
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw new Error('缺少有效的 expectedVersion，请刷新后重试')
  }
  return { expectedVersion, ...extra }
}

export function toInstant(value) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    throw new Error('日期时间格式不正确')
  }
  return date.toISOString()
}

export function normalizeRevokeReason(value) {
  const reason = String(value || '').trim() || 'ADMINISTRATIVE_REVOKE'
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(reason)) {
    throw new Error('撤销原因只允许 1 到 128 位英文字母、数字及 . _ : -')
  }
  return reason
}

export function describeEmbedManagementError(error) {
  if (error?.status === 409) {
    const currentVersion = error?.currentData?.currentVersion
    return currentVersion === undefined
      ? '配置已被其他管理员修改，请刷新后重试'
      : `配置已被其他管理员修改（当前版本 ${currentVersion}），请刷新后重试`
  }
  if (error?.status === 413) {
    return error.message || '配置内容过大，请精简后重试'
  }
  if (error?.status === 400) {
    return error.message || '请求参数不合法，请检查输入'
  }
  return error?.message || '请求失败，请稍后重试'
}

export function extractViolations(error) {
  const values = error?.currentData?.violations
  return Array.isArray(values) ? values.map(item => ({
    path: item?.path || '$',
    code: item?.code || item?.reason || 'INVALID',
    message: item?.message || item?.reason || '配置不合法'
  })) : []
}

function assignOrDelete(target, key, value) {
  const normalized = String(value || '').trim()
  if (normalized) target[key] = normalized
  else delete target[key]
}

function validateRawAuthority(authority) {
  if (authority.startsWith('[')) {
    const close = authority.indexOf(']')
    const suffix = close < 0 ? '' : authority.slice(close + 1)
    if (close <= 1 || (suffix && !/^:\d{1,5}$/.test(suffix))) {
      throw new Error('只允许精确 HTTPS Origin')
    }
    return
  }
  const firstColon = authority.indexOf(':')
  const lastColon = authority.lastIndexOf(':')
  if (
    firstColon !== lastColon
    || (lastColon >= 0 && !/^\d{1,5}$/.test(authority.slice(lastColon + 1)))
  ) {
    throw new Error('只允许精确 HTTPS Origin')
  }
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value || {}))
}
