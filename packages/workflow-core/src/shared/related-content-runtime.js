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

/** 来源路径只是定位信息；服务端从根入口重新鉴权并逐层推导关联范围。 */
export function buildRelatedContentResolveInput(input = {}) {
  return {
    ownerType: String(input.ownerType || '').toUpperCase(),
    ownerId: String(input.ownerId ?? ''),
    compositionKey: input.compositionKey,
    recordId: input.sourceRecordId || undefined,
    configHash: input.configHash || undefined,
    runtimeContext: input.runtimeContext || undefined,
    compositionContext: input.compositionContext || undefined
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

/** 关联页面必须保留来源路径，否则不能退化为目标实体的普通查询。 */
export function assertRelatedContentResolveContract(value) {
  if (!value || typeof value !== 'object') throw new Error('关联内容解析结果为空')
  const contentType = String(value.targetContentType || '').toUpperCase()
  if (!['FORM', 'LIST'].includes(contentType)) throw new Error('关联内容目标类型无效')
  if (value.matchNone !== true && (!value.compositionContext || typeof value.compositionContext !== 'object')) {
    throw new Error('关联内容缺少来源路径，已停止加载')
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
