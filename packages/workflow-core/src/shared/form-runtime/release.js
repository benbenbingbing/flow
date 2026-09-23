import { safeParseConfig } from '../config-runtime/index.js'

/**
 * 将不可变发布快照转换为运行时表单；调用方随后用 fields/nodes 渲染和校验，
 * 用 viewCompositions 渲染关联内容，不能再从可变设计稿补这些配置。
 * @param {object} release 服务端解析的发布记录；基础发布 ID 和有效发布 ID 会随表单继续传给后续请求。
 * @param {string} targetFormId 快照未带 ID 时的固定目标表单 ID，避免运行时丢失请求目标。
 * @param {string|null} releaseResolutionToken 服务端签发的解析令牌，后续精确发布请求会原样透传并由服务端校验。
 * @returns {object} 包含渲染配置及发布坐标的运行时表单。
 */
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
