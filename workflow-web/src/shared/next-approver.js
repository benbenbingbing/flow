const PREVIEW_STATUSES = new Set(['READY', 'DEFERRED', 'BLOCKED'])
const SOURCE_TYPES = new Set(['NODE_ASSIGNMENT', 'SCOPE', 'RESOLVER'])
const ASSIGNMENT_MODES = new Set([
  'DIRECT',
  'CANDIDATE',
  'MULTI_INSTANCE'
])
const SCOPE_TYPES = new Set([
  'ALL_USERS',
  'USER',
  'ORGANIZATION',
  'ROLE',
  'GROUP'
])

function text(value) {
  return value == null ? '' : String(value).trim()
}

function bool(value, fallback = false) {
  if (value == null) return fallback
  if (value === true || value === 1) return true
  return typeof value === 'string' && value.trim().toLowerCase() === 'true'
}

function objectValue(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : {}
}

export function normalizeUserKeys(values = []) {
  const source = Array.isArray(values) ? values : [values]
  const seen = new Set()
  return source
    .map(value => text(value))
    .filter(value => {
      if (!value || seen.has(value)) return false
      seen.add(value)
      return true
    })
}

function canonicalRequestValue(value) {
  if (Array.isArray(value)) {
    return value.map(canonicalRequestValue)
  }
  if (!value || typeof value !== 'object') return value
  if (typeof value.toJSON === 'function') {
    return canonicalRequestValue(value.toJSON())
  }
  return Object.fromEntries(
    Object.keys(value)
      .sort()
      .map(key => [key, canonicalRequestValue(value[key])])
  )
}

/**
 * 候选人员请求的稳定签名。搜索、分页或任一审批上下文发生变化时，签名都会
 * 改变，选择器据此拒绝晚到的旧响应。
 */
export function createNextApproverOptionsRequestSignature(value = {}) {
  const source = objectValue(value)
  return JSON.stringify(canonicalRequestValue({
    taskId: source.taskId ?? '',
    targetNodeId: source.targetNodeId ?? '',
    scopeKey: source.scopeKey ?? '',
    action: source.action ?? '',
    actionLabel: source.actionLabel ?? '',
    comment: source.comment ?? '',
    formData: source.formData ?? {},
    keyword: source.keyword ?? '',
    pageNum: source.pageNum ?? 1,
    pageSize: source.pageSize ?? 10
  }))
}

/** 保留显式人员顺序，供顺序或并行多实例保存参与人次序。 */
export function reorderNextApproverValues(values = [], fromIndex, toIndex) {
  const next = Array.isArray(values) ? [...values] : []
  if (
    !Number.isInteger(fromIndex)
    || !Number.isInteger(toIndex)
    || fromIndex < 0
    || toIndex < 0
    || fromIndex >= next.length
    || toIndex >= next.length
    || fromIndex === toIndex
  ) {
    return next
  }
  const [moved] = next.splice(fromIndex, 1)
  next.splice(toIndex, 0, moved)
  return next
}

export function nextApproverUserKey(user) {
  if (typeof user === 'string' || typeof user === 'number') {
    return text(user)
  }
  return text(
    user?.username
    ?? user?.userKey
    ?? user?.code
    ?? user?.userId
    ?? user?.id
  )
}

export function nextApproverUserLabel(user) {
  if (typeof user === 'string' || typeof user === 'number') {
    return text(user)
  }
  return text(
    user?.displayName
    ?? user?.name
    ?? user?.nickname
    ?? user?.username
    ?? user?.userKey
    ?? user?.code
    ?? user?.userId
    ?? user?.id
  )
}

export function normalizeNextApproverUser(user) {
  const source = objectValue(user)
  const userKey = nextApproverUserKey(user)
  return {
    ...source,
    userId: text(source.userId ?? source.id),
    username: text(source.username ?? source.userKey ?? source.code),
    displayName: nextApproverUserLabel(user),
    userKey
  }
}

export function normalizeNextApproverScope(scope = {}) {
  const source = objectValue(scope)
  const rawType = text(source.type ?? source.scopeType).toUpperCase()
  const type = rawType === 'DEPARTMENT' ? 'ORGANIZATION' : rawType
  return {
    // 缺失或未知类型原样保留并交由校验拒绝；只有显式 ALL_USERS
    // 才能扩大为全员范围。
    type,
    values: type === 'ALL_USERS'
      ? []
      : normalizeUserKeys(source.values ?? source.targetIds ?? source.ids),
    includeChildren: type === 'ORGANIZATION'
      && bool(source.includeChildren, false)
  }
}

export function createNextApproverSelectionConfig(value = {}) {
  const source = objectValue(value)
  const nestedSource = objectValue(source.source)
  const visible = bool(source.visible ?? source.show ?? source.display, false)
  const rawScopes = nestedSource.rules
    ?? nestedSource.scopes
    ?? (Array.isArray(source.source) ? source.source : null)
    ?? source.scopes
    ?? source.scopeRules
    ?? []
  const legacyScopeType = text(
    nestedSource.scopeType ?? source.scopeType
  ).toUpperCase()
  const configuredResolverCode = text(
    nestedSource.resolverCode
    ?? nestedSource.interfaceName
    ?? source.resolverCode
    ?? source.interfaceName
  )
  const hasLegacyScope = (Array.isArray(rawScopes) && rawScopes.length > 0)
    || Boolean(legacyScopeType)
  const declaredSourceType = text(
    nestedSource.type
    ?? source.sourceType
    ?? (typeof source.source === 'string' ? source.source : null)
  ).toUpperCase()
  const sourceType = declaredSourceType || (configuredResolverCode
    ? 'RESOLVER'
    : hasLegacyScope ? 'SCOPE' : 'NODE_ASSIGNMENT')
  const legacyFlatScope = legacyScopeType
    ? [{
        type: legacyScopeType,
        values: nestedSource.scopeValues
          ?? nestedSource.values
          ?? source.scopeValues
          ?? source.values,
        includeChildren: nestedSource.includeChildren
          ?? source.includeChildren
      }]
    : []
  const scopes = (Array.isArray(rawScopes) && rawScopes.length
    ? rawScopes
    : legacyFlatScope)
    .map(normalizeNextApproverScope)

  return {
    version: 1,
    visible,
    editable: visible && bool(
      source.editable ?? source.allowModify ?? source.allowEdit,
      false
    ),
    source: sourceType === 'NODE_ASSIGNMENT'
      ? { type: 'NODE_ASSIGNMENT' }
      : sourceType === 'RESOLVER'
      ? {
          type: 'RESOLVER',
          resolverCode: configuredResolverCode,
          extraParams: objectValue(
            nestedSource.extraParams ?? source.extraParams
          )
        }
      : {
          type: sourceType,
          rules: scopes
        }
  }
}

export function validateNextApproverSelectionConfig(value) {
  const config = createNextApproverSelectionConfig(value)
  if (!SOURCE_TYPES.has(config.source.type)) {
    return { valid: false, message: '下一审批人来源类型无效' }
  }
  if (config.source.type === 'SCOPE') {
    for (const [index, rule] of config.source.rules.entries()) {
      if (!SCOPE_TYPES.has(rule.type)) {
        return {
          valid: false,
          message: `第 ${index + 1} 条人员范围类型无效`
        }
      }
    }
  }
  if (!config.visible) return { valid: true, message: '' }
  if (config.source.type === 'NODE_ASSIGNMENT') {
    return { valid: true, message: '' }
  }
  if (config.source.type === 'RESOLVER') {
    return config.source.resolverCode
      ? { valid: true, message: '' }
      : { valid: false, message: '请选择下一审批人人员接口' }
  }
  if (!config.source.rules.length) {
    return { valid: false, message: '请至少添加一个下一审批人可选范围' }
  }
  for (const [index, rule] of config.source.rules.entries()) {
    if (rule.type !== 'ALL_USERS' && rule.values.length === 0) {
      return {
        valid: false,
        message: `第 ${index + 1} 条人员范围请选择具体数据`
      }
    }
  }
  return { valid: true, message: '' }
}

/**
 * 判断多人办理是否可由前序改选补齐参与人，而不要求目标节点预置静态名单。
 *
 * 仅独立且有效的 SCOPE/RESOLVER 来源具备兜底能力；复用本节点审批人、
 * 隐藏或只读配置仍必须依赖目标节点自身的默认人员，避免产生无人任务。
 */
export function canDeferMultiInstanceAssignmentToNextApprover(value) {
  const config = createNextApproverSelectionConfig(value)
  if (!config.visible || !config.editable) return false
  if (!['SCOPE', 'RESOLVER'].includes(config.source.type)) return false
  return validateNextApproverSelectionConfig(config).valid
}

export function normalizeNextApproverPreview(value = {}) {
  const source = objectValue(value)
  const requestedStatus = text(source.status).toUpperCase()
  const baseStatus = !requestedStatus
    ? 'READY'
    : PREVIEW_STATUSES.has(requestedStatus)
      ? requestedStatus
      : 'BLOCKED'
  const protocolErrors = []
  const rawNodes = source.nextNodes ?? source.nodes ?? []
  const nextNodes = (Array.isArray(rawNodes) ? rawNodes : [])
    .map(node => {
      const item = objectValue(node)
      const sourceType = text(item.sourceType).toUpperCase()
      if (sourceType && !SOURCE_TYPES.has(sourceType)) {
        protocolErrors.push(
          `节点“${text(item.nodeName ?? item.name ?? item.nodeId ?? item.id)}”的人员来源无效`
        )
      } else if (item.visible === true && item.editable === true && !sourceType) {
        protocolErrors.push(
          `节点“${text(item.nodeName ?? item.name ?? item.nodeId ?? item.id)}”缺少人员来源`
        )
      }
      const requestedAssignmentMode = text(item.assignmentMode).toUpperCase()
      const hasInvalidAssignmentMode = requestedAssignmentMode
        && !ASSIGNMENT_MODES.has(requestedAssignmentMode)
      if (hasInvalidAssignmentMode) {
        protocolErrors.push(
          `节点“${text(item.nodeName ?? item.name ?? item.nodeId ?? item.id)}”的分配模式无效`
        )
      }
      // 仅旧响应未提供 assignmentMode 时才允许从 multiple 兼容派生。
      const assignmentMode = requestedAssignmentMode
        ? (ASSIGNMENT_MODES.has(requestedAssignmentMode)
            ? requestedAssignmentMode
            : 'DIRECT')
        : item.multiple === true
          ? 'CANDIDATE'
          : 'DIRECT'
      const assignees = (Array.isArray(item.assignees)
        ? item.assignees
        : [])
        .map(normalizeNextApproverUser)
        .filter(user => user.userKey)
      return {
        ...item,
        nodeId: text(item.nodeId ?? item.id),
        nodeName: text(item.nodeName ?? item.name ?? item.nodeId ?? item.id),
        visible: item.visible === true,
        editable: item.visible === true && item.editable === true,
        assignmentMode,
        multiple: assignmentMode !== 'DIRECT',
        sourceType: SOURCE_TYPES.has(sourceType) ? sourceType : null,
        assignees
      }
    })
    .filter(node => node.nodeId)

  return {
    ...source,
    taskId: text(source.taskId),
    processDefinitionId: text(source.processDefinitionId),
    scopeKey: text(source.scopeKey),
    status: protocolErrors.length ? 'BLOCKED' : baseStatus,
    message: text(source.message)
      || (!requestedStatus || PREVIEW_STATUSES.has(requestedStatus)
        ? protocolErrors.join('；')
        : `无法识别下一审批预览状态：${requestedStatus}`),
    nextNodes
  }
}

export function initialNextApproverUserKeys(node) {
  return normalizeUserKeys(
    (node?.assignees || []).map(nextApproverUserKey)
  )
}

export function createNextApproverDraftMap(preview) {
  const normalized = normalizeNextApproverPreview(preview)
  return Object.fromEntries(normalized.nextNodes.map(node => [
    node.nodeId,
    initialNextApproverUserKeys(node)
  ]))
}

function nextApproverDraftNodeIds(preview) {
  return normalizeNextApproverPreview(preview).nextNodes
    .map(node => node.nodeId)
    .sort()
}

/**
 * 合并一次新的权威预览：路径或节点集合变化时清空旧草稿；同一路径刷新时，
 * 只保留用户已经主动改过的节点，未触碰节点采用后端最新默认值。
 */
export function reconcileNextApproverDraftState(
  previousPreview,
  nextPreview,
  currentDraftMap = {},
  touchedNodeIds = []
) {
  const previous = normalizeNextApproverPreview(previousPreview)
  const next = normalizeNextApproverPreview(nextPreview)
  const nextDefaults = createNextApproverDraftMap(next)
  const sameContext = Boolean(next.scopeKey)
    && previous.scopeKey === next.scopeKey
    && JSON.stringify(nextApproverDraftNodeIds(previous))
      === JSON.stringify(nextApproverDraftNodeIds(next))
  if (!sameContext) {
    return { draftMap: nextDefaults, touchedNodeIds: [] }
  }

  const knownNodeIds = new Set(next.nextNodes.map(node => node.nodeId))
  const touched = normalizeUserKeys(touchedNodeIds)
    .filter(nodeId => knownNodeIds.has(nodeId))
  const touchedSet = new Set(touched)
  const current = objectValue(currentDraftMap)
  const draftMap = Object.fromEntries(next.nextNodes.map(node => {
    const preserve = touchedSet.has(node.nodeId)
      && Object.hasOwn(current, node.nodeId)
    return [
      node.nodeId,
      preserve
        ? normalizeUserKeys(current[node.nodeId])
        : nextDefaults[node.nodeId]
    ]
  }))
  return { draftMap, touchedNodeIds: touched }
}

function sameUserKeys(left, right) {
  const normalizedLeft = normalizeUserKeys(left)
  const normalizedRight = normalizeUserKeys(right)
  return normalizedLeft.length === normalizedRight.length
    && normalizedLeft.every((value, index) => value === normalizedRight[index])
}

function sameSelection(node, left, right) {
  if (node.assignmentMode === 'MULTI_INSTANCE') {
    return sameUserKeys(left, right)
  }
  return sameUserKeys(
    normalizeUserKeys(left).sort(),
    normalizeUserKeys(right).sort()
  )
}

export function buildChangedNextApproverSelections(preview, draftMap = {}) {
  const normalized = normalizeNextApproverPreview(preview)
  if (normalized.status !== 'READY') return []
  return normalized.nextNodes
    .filter(node => node.visible && node.editable)
    .map(node => {
      const initialKeys = initialNextApproverUserKeys(node)
      const userKeys = normalizeUserKeys(draftMap[node.nodeId] ?? initialKeys)
      return {
        nodeId: node.nodeId,
        userKeys
      }
    })
    .filter(selection => {
      const node = normalized.nextNodes.find(item =>
        item.nodeId === selection.nodeId)
      return !sameSelection(
        node,
        initialNextApproverUserKeys(node),
        selection.userKeys
      )
    })
}

export function hasNextApproverPresentation(preview, loading = false) {
  const normalized = normalizeNextApproverPreview(preview)
  const hasVisibleNodes = normalized.nextNodes.some(node => node.visible)
  if (loading) return hasVisibleNodes
  return normalized.status !== 'READY'
    || hasVisibleNodes
}

export function validateNextApproverDraft(preview, draftMap = {}) {
  const normalized = normalizeNextApproverPreview(preview)
  if (normalized.status === 'BLOCKED') {
    return {
      valid: false,
      message: normalized.message || '无法确定下一审批节点'
    }
  }
  if (normalized.status !== 'READY') return { valid: true, message: '' }

  for (const node of normalized.nextNodes) {
    if (!node.visible || !node.editable) continue
    if (!normalized.scopeKey) {
      return {
        valid: false,
        message: `节点“${node.nodeName}”缺少有效的人员选择范围`
      }
    }
    const values = normalizeUserKeys(
      draftMap[node.nodeId] ?? initialNextApproverUserKeys(node)
    )
    if (values.length === 0) {
      return {
        valid: false,
        message: `请选择“${node.nodeName}”审批人`
      }
    }
    if (!node.multiple && values.length > 1) {
      return {
        valid: false,
        message: `节点“${node.nodeName}”只能选择一名审批人`
      }
    }
  }
  return { valid: true, message: '' }
}
