const RELATED_CONTENT_RUNTIME_ACTIONS = new Set([
  'VIEW',
  'SELECT',
  'CREATE',
  'EDIT',
  'LINK',
  'UNLINK',
  'SAVE_WITH_FORM'
])

export function normalizeRelatedContentRuntimeActions(actions) {
  if (!Array.isArray(actions)) return []
  return [...new Set(actions
    .map(action => String(action || '').trim().toUpperCase())
    .filter(action => RELATED_CONTENT_RUNTIME_ACTIONS.has(action)))]
}

/**
 * 构造关联内容解析请求。历史钉定表单的发布令牌只作为服务端校验凭证透传；
 * 缺失时保持 undefined，让服务端继续执行当前激活版本的 fail-closed 校验。
 */
export function buildRelatedContentResolveInput(input = {}) {
  const releaseResolutionToken = String(
    input.releaseResolutionToken || ''
  ).trim()
  return {
    ownerType: String(input.ownerType || '').toUpperCase(),
    ownerId: String(input.ownerId ?? ''),
    releaseId: input.releaseId,
    releaseVersion: input.releaseVersion,
    compositionKey: input.compositionKey,
    recordId: input.sourceRecordId || undefined,
    rowContextToken: input.rowContextToken || undefined,
    traversalContextToken: input.traversalContextToken || undefined,
    releaseResolutionToken: releaseResolutionToken || undefined
  }
}

/**
 * 将目标列表按钮映射为“关联内容”的标准动作。
 *
 * 未声明语义的自定义事件、删除、审批和导出都返回空值，避免配置了“新增”
 * 却顺带开放整套目标列表工具栏。
 */
export function relatedContentButtonAction(button, placement) {
  const normalizedPlacement = String(placement || '').toUpperCase()
  const key = String(button?.key || '').trim()
  const type = String(button?.type || 'built-in').toLowerCase()
  const customMode = String(button?.customMode || '').toLowerCase()

  if (type === 'built-in') {
    if (normalizedPlacement === 'TOOLBAR' && key === 'create') return 'CREATE'
    if (normalizedPlacement === 'ROW' && key === 'view') return 'VIEW'
    if (normalizedPlacement === 'ROW' && key === 'edit') return 'EDIT'
    return ''
  }

  // 已固定目标表单的打开动作具有明确的查看/编辑/新增语义；任意事件处理器
  // 没有可证明的动作边界，因此在关联内容中默认不开放。
  if (type === 'custom' && customMode === 'open-form') {
    if (normalizedPlacement === 'TOOLBAR') return 'CREATE'
    if (normalizedPlacement === 'ROW') {
      return String(button?.targetFormMode || '').toUpperCase() === 'EDIT'
        ? 'EDIT'
        : 'VIEW'
    }
  }
  return ''
}

export function filterRelatedContentButtons(buttons, actions, placement) {
  const allowed = new Set(normalizeRelatedContentRuntimeActions(actions))
  return (Array.isArray(buttons) ? buttons : []).filter(button => {
    const action = relatedContentButtonAction(button, placement)
    return Boolean(action) && allowed.has(action)
  })
}

/**
 * 校验服务端解析结果中不可缺少的安全凭证。
 *
 * LIST 没有签名上下文时绝不能继续挂载普通列表，否则请求会退化为目标列表的
 * 常规查询路径并丢失关联固定条件。
 */
export function assertRelatedContentResolveContract(value) {
  if (!value || typeof value !== 'object') {
    throw new Error('关联内容解析结果为空')
  }
  const contentType = String(value.targetContentType || '').toUpperCase()
  if (!['FORM', 'LIST'].includes(contentType)) {
    throw new Error('关联内容目标类型无效')
  }
  if (contentType === 'LIST'
    && !String(value.listContextToken || '').trim()) {
    throw new Error('关联列表缺少可信查询凭证，已停止加载')
  }
  if (value.matchNone !== true
    && !String(value.traversalContextToken || '').trim()) {
    throw new Error('关联内容缺少安全导航凭证，已停止加载')
  }
  return value
}

/** 创建一个只允许最后一次异步请求提交结果的轻量门闩。 */
export function createLatestRequestGate() {
  let revision = 0
  return {
    begin() {
      revision += 1
      return revision
    },
    invalidate() {
      revision += 1
    },
    isCurrent(requestRevision) {
      return requestRevision === revision
    }
  }
}
