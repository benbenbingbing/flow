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

const CONTEXT_FIELD_TYPES = new Set(['', 'string', 'integer', 'number', 'boolean', 'object', 'array', 'null'])
const CONTEXT_BINDING_SOURCE_TYPES = new Set(['string', 'integer', 'number', 'boolean'])
const MAX_CONTEXT_FIELDS = 32

/** 新行只描述现有 Schema 字段；id 由调用方提供，不进入保存后的 JSON。 */
export function createContextField(name = '', id = '') {
  return { id, originalName: null, name, type: 'string', required: false, title: '', description: '', rawSchema: {} }
}

/** 绑定使用字段稳定 id 跟踪来源，避免字段改名后按字符串误关联到另一个字段。 */
export function createContextBinding(id = '') {
  return {
    id,
    originalIndex: null,
    sourceFieldId: '',
    source: '',
    target: '',
    usage: 'FIXED_FILTER',
    rawBinding: {}
  }
}

function jsonCopy(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value))
}

function contextObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function contextControls(editor) {
  return JSON.stringify({
    additionalProperties: editor.additionalProperties,
    fields: editor.fields.map(({ id, name, type, required, title, description }) => ({ id, name, type, required, title, description })),
    bindings: editor.bindings.map(({ sourceFieldId, source, target, usage }) => ({ sourceFieldId, source, target, usage }))
  })
}

/** 判断可视化上下文是否偏离载入基线，供覆盖式 JSON 重载前做防丢失确认。 */
export function hasContextEditorChanges(editor) {
  if (!editor) return false
  return contextControls(editor) !== editor.baseline
}

/**
 * 从现有上下文 JSON 建立可视化视图；保留原节点及未填写状态，不补默认值或丢弃嵌套 Schema。
 * 不合法的历史节点也保留在原文中，未编辑时仍交由服务端给出准确的违规位置。
 */
export function contextToEditor(contextSchema = {}, contextBindings = []) {
  const schema = contextObject(contextSchema) ? contextSchema : {}
  const properties = contextObject(schema.properties) ? schema.properties : {}
  const required = Array.isArray(schema.required) ? schema.required : []
  const fields = Object.entries(properties).map(([name, raw], index) => ({
    ...createContextField(name, `context-field-${index}`),
    originalName: name,
    type: typeof raw?.type === 'string' ? raw.type : '',
    required: required.includes(name),
    title: typeof raw?.title === 'string' ? raw.title : '',
    description: typeof raw?.description === 'string' ? raw.description : '',
    rawSchema: jsonCopy(raw)
  }))
  const fieldByOriginalName = new Map(fields.map(field => [field.originalName, field]))
  const editor = {
    rawSchema: jsonCopy(contextSchema),
    rawBindings: jsonCopy(contextBindings),
    additionalProperties: schema.additionalProperties === false ? 'deny'
      : schema.additionalProperties === true ? 'allow' : 'unset',
    fields,
    bindings: (Array.isArray(contextBindings) ? contextBindings : []).map((raw, index) => {
      const source = typeof raw?.source === 'string' ? raw.source : ''
      return {
        ...createContextBinding(`context-binding-${index}`),
        originalIndex: index,
        sourceFieldId: fieldByOriginalName.get(source)?.id || '',
        source,
        target: typeof raw?.target === 'string' ? raw.target : '',
        usage: typeof raw?.usage === 'string' ? raw.usage : '',
        rawBinding: jsonCopy(raw)
      }
    })
  }
  editor.baseline = contextControls(editor)
  return editor
}

/**
 * 将可视化改动合并回最新高级 JSON；双方同时改动受控字段时拒绝静默覆盖。
 * 只更新顶层字段、必传及绑定，未知元数据从最新原文继承；字段改名同步引用。
 */
export function contextEditorToConfig(editor, baseConfig) {
  const sourceSchema = baseConfig
    ? (Object.hasOwn(baseConfig, 'contextSchema') ? baseConfig.contextSchema : {}) : editor.rawSchema
  const sourceBindings = baseConfig
    ? (Object.hasOwn(baseConfig, 'contextBindings') ? baseConfig.contextBindings : []) : editor.rawBindings
  const latest = contextToEditor(sourceSchema, sourceBindings)
  if (contextControls(editor) === editor.baseline) {
    return { contextSchema: jsonCopy(sourceSchema), contextBindings: jsonCopy(sourceBindings) }
  }
  if (editor.baseline && contextControls(latest) !== editor.baseline) {
    throw new Error('高级 JSON 与上下文表单均有修改，请先将高级 JSON 同步到上下文表单后再保存')
  }
  if (!contextObject(sourceSchema) || !Array.isArray(sourceBindings)) {
    throw new Error('请先修正高级 JSON 中的 contextSchema Object 和 contextBindings 数组')
  }
  const schema = jsonCopy(sourceSchema)
  const original = contextToEditor(editor.rawSchema, editor.rawBindings)
  const originalFields = new Map(original.fields.map(field => [field.name, field]))
  const latestProperties = contextObject(schema.properties) ? schema.properties : {}
  const names = new Set()
  const fieldsById = new Map()
  const fieldsByCurrentName = new Map()
  if (editor.fields.length > MAX_CONTEXT_FIELDS) {
    throw new Error(`上下文字段最多允许 ${MAX_CONTEXT_FIELDS} 个`)
  }
  const properties = editor.fields.map(field => {
    const name = String(field.name ?? '')
    if (!name.trim()) throw new Error('上下文字段名称不能为空')
    if (names.has(name)) throw new Error(`上下文字段“${name}”重复`)
    names.add(name)
    if (field.id) fieldsById.set(field.id, field)
    fieldsByCurrentName.set(name, field)
    const type = field.type === 'unset' ? '' : field.type
    if (!CONTEXT_FIELD_TYPES.has(type)) throw new Error(`上下文字段“${name}”的类型不受支持`)
    const previous = originalFields.get(field.originalName)
    const raw = previous && Object.hasOwn(latestProperties, field.originalName)
      ? latestProperties[field.originalName] : field.rawSchema
    if (!contextObject(raw)) throw new Error(`上下文字段“${name}”的 Schema 必须是 Object`)
    const node = jsonCopy(raw)
    for (const key of ['type', 'title', 'description']) {
      const value = key === 'type' ? type : String(field[key] ?? '')
      if (previous && value === previous[key]) continue
      if (value === '') delete node[key]
      else node[key] = value
    }
    return [name, node]
  })
  // Object.fromEntries 使用自有数据属性，__proto__ 等字段名不会触发原型赋值。
  if (editor.fields.length || Object.hasOwn(schema, 'properties')) schema.properties = Object.fromEntries(properties)
  const requiredNames = new Set(editor.fields.filter(field => field.required).map(field => field.name))
  const currentNameByOriginalName = new Map(
    editor.fields
      .filter(field => field.originalName)
      .map(field => [field.originalName, field.name])
  )
  const required = (Array.isArray(schema.required) ? schema.required : [])
    .map(name => currentNameByOriginalName.get(name) ?? name)
    .filter(name => requiredNames.has(name))
  for (const name of requiredNames) if (!required.includes(name)) required.push(name)
  if (required.length || Object.hasOwn(schema, 'required')) schema.required = required
  if (!['unset', 'allow', 'deny'].includes(editor.additionalProperties)) throw new Error('额外上下文字段规则无效')
  if (editor.additionalProperties !== original.additionalProperties) {
    if (editor.additionalProperties === 'unset') delete schema.additionalProperties
    else schema.additionalProperties = editor.additionalProperties === 'allow'
  }
  const usageTargets = new Set()
  const fixedFilterKeys = new Set()
  const bindings = editor.bindings.map(binding => {
    const previous = original.bindings.find(item => item.originalIndex === binding.originalIndex)
    const raw = previous && sourceBindings[binding.originalIndex] !== undefined
      ? sourceBindings[binding.originalIndex] : binding.rawBinding
    if (!contextObject(raw)) throw new Error('上下文绑定必须是 Object')
    // 新版编辑器优先按稳定 id 解析；无 id 的旧调用方仍可按当前名称保存。
    const sourceField = binding.sourceFieldId
      ? fieldsById.get(binding.sourceFieldId)
      : (fieldsByCurrentName.get(binding.source)
        || (previous ? editor.fields.find(field => field.originalName === previous.source) : null))
    const source = sourceField?.name || binding.source
    if (!sourceField || !names.has(source)) {
      throw new Error(`绑定来源“${source}”未定义，请先移除绑定或选择其他字段`)
    }
    const sourceType = sourceField.type === 'unset' ? '' : sourceField.type
    if (!sourceField.required) throw new Error(`绑定来源“${source}”必须设为必传`)
    if (!CONTEXT_BINDING_SOURCE_TYPES.has(sourceType)) {
      throw new Error(`绑定来源“${source}”必须使用 string、integer、number 或 boolean 类型`)
    }
    const target = String(binding.target ?? '')
    const usage = String(binding.usage ?? '')
    if (!target.trim()) throw new Error('上下文映射的 Flow 目标字段不能为空')
    if (!['FIXED_FILTER', 'FORCED_FORM_VALUE'].includes(usage)) {
      throw new Error('上下文映射用途无效')
    }
    const usageTargetKey = `${usage}\0${target}`
    if (usageTargets.has(usageTargetKey)) {
      throw new Error(`同一用途不能重复绑定目标字段“${target}”`)
    }
    usageTargets.add(usageTargetKey)
    if (usage === 'FIXED_FILTER') {
      if (fixedFilterKeys.has(target) || fixedFilterKeys.has(`${target}_op`)) {
        throw new Error(`固定过滤目标“${target}”与其他过滤字段的编码键冲突`)
      }
      fixedFilterKeys.add(target)
      fixedFilterKeys.add(`${target}_op`)
    }
    return { ...jsonCopy(raw), source, target, usage }
  })
  return { contextSchema: schema, contextBindings: bindings }
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
    contextEditor: contextToEditor(source.contextSchema ?? {}, source.contextBindings ?? []),
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
  // 旧调用方只有 advancedJson 时继续原有行为；未改可视化上下文时也保留 JSON 的新改动。
  if (editor.contextEditor) Object.assign(draft, contextEditorToConfig(editor.contextEditor, draft))
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
