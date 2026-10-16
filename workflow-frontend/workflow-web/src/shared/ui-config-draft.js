const UI_CONFIG_DRAFT_DISCARD_CONFLICT_CODES = new Set([
  'CONFIG_REVISION_CONFLICT',
  'UI_CONFIG_ACTIVE_RELEASE_REQUIRED',
  'UI_CONFIG_RELEASE_STATE_CONFLICT',
  'UI_CONFIG_ACTIVE_RELEASE_CHANGED',
  'UI_CONFIG_DRAFT_CHANGED',
  'UI_CONFIG_NO_DISCARDABLE_DRAFT',
  'UI_CONFIG_DISCARD_BASELINE_DRIFT'
])

/**
 * 区分当前配置自身草稿与继承/引用漂移，避免把外部变化误报为本地未发布修改。
 */
export function resolveUiConfigDraftStatus({
  diffLoadSucceeded,
  diff
} = {}) {
  if (diffLoadSucceeded !== true) {
    return { key: 'UNKNOWN', label: '发布状态未知', type: 'info' }
  }
  if (diff?.discardableChanged === true) {
    return { key: 'LOCAL_DRAFT', label: '草稿有未发布修改', type: 'warning' }
  }
  if (diff?.dependencyChanged === true) {
    return { key: 'DEPENDENCY_DRIFT', label: '存在外部依赖差异', type: 'info' }
  }
  if (diff?.changed === false) {
    return { key: 'IN_SYNC', label: '已与发布版本一致', type: 'success' }
  }
  return { key: 'UNKNOWN', label: '发布状态未知', type: 'info' }
}

/**
 * 只有服务端确认存在本地可撤销草稿、差异加载成功且发布/草稿基线完整时，才允许撤销。
 */
export function canDiscardUiConfigDraft({
  diffLoadSucceeded,
  diff,
  serverCanDiscardDraft,
  activeReleaseId
} = {}) {
  return diffLoadSucceeded === true
    && serverCanDiscardDraft === true
    && diff?.changed === true
    && Boolean(String(activeReleaseId || '').trim())
    && Boolean(String(diff?.draftHash || '').trim())
    && Boolean(String(diff?.activeHash || '').trim())
}

/**
 * 撤销前置条件失效时必须重载整个设计器，不能继续沿用旧 revision 和差异快照。
 */
export function isUiConfigDraftDiscardConflict(error) {
  return error?.status === 409
    || UI_CONFIG_DRAFT_DISCARD_CONFLICT_CODES.has(error?.errorCode)
}

/**
 * 构造撤销草稿的三重并发前置条件，避免覆盖其他管理员刚保存或发布的配置。
 */
export function buildUiConfigDraftDiscardRequest({
  revision,
  diffLoadSucceeded,
  diff,
  serverCanDiscardDraft,
  activeReleaseId
} = {}) {
  if (!String(diff?.draftHash || '').trim()) {
    throw new Error('当前草稿哈希无效，请重新加载后再试')
  }
  if (!canDiscardUiConfigDraft({
    diffLoadSucceeded,
    diff,
    serverCanDiscardDraft,
    activeReleaseId
  })) {
    throw new Error('当前没有可撤销的已保存未发布修改')
  }
  const expectedRevision = Number(revision)
  if (!Number.isInteger(expectedRevision) || expectedRevision < 0) {
    throw new Error('当前草稿 revision 无效，请重新加载后再试')
  }
  return {
    expectedRevision,
    expectedDraftHash: String(diff.draftHash).trim(),
    expectedActiveReleaseId: String(activeReleaseId).trim()
  }
}
