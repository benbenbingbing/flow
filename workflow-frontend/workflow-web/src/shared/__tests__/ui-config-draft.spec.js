import assert from 'node:assert/strict'
import {
  buildUiConfigDraftDiscardRequest,
  canDiscardUiConfigDraft,
  isUiConfigDraftDiscardConflict,
  resolveUiConfigDraftStatus
} from '../ui-config-draft.js'

const diff = {
  changed: true,
  draftHash: 'draft-hash-7',
  activeHash: 'active-hash-3'
}

assert.deepEqual(resolveUiConfigDraftStatus({
  diffLoadSucceeded: false,
  diff
}), { key: 'UNKNOWN', label: '发布状态未知', type: 'info' })
assert.deepEqual(resolveUiConfigDraftStatus({
  diffLoadSucceeded: true,
  diff: { ...diff, discardableChanged: true, dependencyChanged: true }
}), { key: 'LOCAL_DRAFT', label: '草稿有未发布修改', type: 'warning' })
assert.deepEqual(resolveUiConfigDraftStatus({
  diffLoadSucceeded: true,
  diff: {
    ...diff,
    discardableChanged: false,
    dependencyChanged: true
  }
}), { key: 'DEPENDENCY_DRIFT', label: '存在外部依赖差异', type: 'info' })
assert.deepEqual(resolveUiConfigDraftStatus({
  diffLoadSucceeded: true,
  diff: {
    changed: false,
    discardableChanged: false,
    dependencyChanged: false
  }
}), { key: 'IN_SYNC', label: '已与发布版本一致', type: 'success' })
assert.deepEqual(resolveUiConfigDraftStatus({
  diffLoadSucceeded: true,
  diff: { changed: true }
}), { key: 'UNKNOWN', label: '发布状态未知', type: 'info' })

assert.equal(canDiscardUiConfigDraft({
  diffLoadSucceeded: false,
  diff,
  serverCanDiscardDraft: true,
  activeReleaseId: 'release-3'
}), false)
assert.equal(canDiscardUiConfigDraft({
  diffLoadSucceeded: true,
  diff,
  serverCanDiscardDraft: false,
  activeReleaseId: 'release-3'
}), false, '仅由继承事件或引用版本漂移产生的 diff 不能显示撤销入口')
assert.equal(canDiscardUiConfigDraft({
  diffLoadSucceeded: true,
  diff: { ...diff, changed: false },
  serverCanDiscardDraft: true,
  activeReleaseId: 'release-3'
}), false)
assert.equal(canDiscardUiConfigDraft({
  diffLoadSucceeded: true,
  diff: { ...diff, draftHash: '' },
  serverCanDiscardDraft: true,
  activeReleaseId: 'release-3'
}), false)
assert.equal(canDiscardUiConfigDraft({
  diffLoadSucceeded: true,
  diff: { ...diff, activeHash: '' },
  serverCanDiscardDraft: true,
  activeReleaseId: 'release-3'
}), false)
assert.equal(canDiscardUiConfigDraft({
  diffLoadSucceeded: true,
  diff,
  serverCanDiscardDraft: true,
  activeReleaseId: ''
}), false)
assert.equal(canDiscardUiConfigDraft({
  diffLoadSucceeded: true,
  diff,
  serverCanDiscardDraft: true,
  activeReleaseId: 'release-3'
}), true)

assert.deepEqual(buildUiConfigDraftDiscardRequest({
  revision: 7,
  diffLoadSucceeded: true,
  diff,
  serverCanDiscardDraft: true,
  activeReleaseId: 'release-3'
}), {
  expectedRevision: 7,
  expectedDraftHash: 'draft-hash-7',
  expectedActiveReleaseId: 'release-3'
})
assert.throws(() => buildUiConfigDraftDiscardRequest({
  revision: 7,
  diffLoadSucceeded: true,
  diff,
  serverCanDiscardDraft: false,
  activeReleaseId: 'release-3'
}), /当前没有可撤销/)
assert.throws(() => buildUiConfigDraftDiscardRequest({
  revision: 7,
  diffLoadSucceeded: true,
  diff: { ...diff, draftHash: '' },
  serverCanDiscardDraft: true,
  activeReleaseId: 'release-3'
}), /草稿哈希无效/)

assert.equal(isUiConfigDraftDiscardConflict({ status: 409 }), true)
assert.equal(isUiConfigDraftDiscardConflict({
  status: 400,
  errorCode: 'UI_CONFIG_DRAFT_CHANGED'
}), true)
assert.equal(isUiConfigDraftDiscardConflict({
  status: 400,
  errorCode: 'UI_CONFIG_DISCARD_BASELINE_DRIFT'
}), true)
assert.equal(isUiConfigDraftDiscardConflict({
  status: 400,
  errorCode: 'OTHER_ERROR'
}), false)

console.log('ui config draft tests passed')
