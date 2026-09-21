import { safeParseConfig } from '../config-runtime/index.js'

export function normalizeRuntimeFormRelease(
  release,
  targetFormId,
  releaseResolutionToken = null
) {
  const snapshot = safeParseConfig(release?.snapshotDocument)
  const form = snapshot?.form
  if (!form || typeof form !== 'object') {
    throw new Error('目标表单发布快照缺少表单定义')
  }
  return {
    ...form,
    id: form.id || targetFormId,
    fields: Array.isArray(snapshot.legacyFields)
      ? snapshot.legacyFields
      : [],
    nodes: Array.isArray(snapshot.nodes) ? snapshot.nodes : [],
    // 关联内容与字段、节点同属不可变发布快照；遗漏会导致固定目标表单运行时静默丢失入口。
    viewCompositions: Array.isArray(snapshot.viewCompositions)
      ? snapshot.viewCompositions
      : [],
    runtimeReleaseId: release.id,
    runtimeReleaseVersion: release.version,
    effectiveReleaseId: release.effectiveReleaseId || release.id,
    hotfixApplied: release.hotfixApplied === true,
    releaseResolutionToken:
      releaseResolutionToken
      || release.releaseResolutionToken
      || null
  }
}

