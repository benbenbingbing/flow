import { getFormRuntimeRelease } from '@/api/entityForm'
import { safeParseConfig } from '@/shared/config-runtime'

const formReleaseCache = new Map()

export function hasExplicitListButtonForm(button) {
  return Boolean(button?.targetFormId)
}

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

export async function loadExplicitListButtonForm(button) {
  if (!hasExplicitListButtonForm(button)) return null
  const formId = String(button.targetFormId)
  const releaseId = String(button.targetFormReleaseId || '')
  const releaseVersion = Number(button.targetFormReleaseVersion)
  if (!releaseId || !Number.isInteger(releaseVersion) || releaseVersion <= 0) {
    throw new Error('按钮指定的表单未固定发布版本，请重新预检并发布列表')
  }
  const releaseResolutionToken =
    button.targetFormReleaseResolutionToken || null
  if (!releaseResolutionToken) {
    throw new Error('列表按钮的表单发布授权已失效，请刷新列表后重试')
  }
  const cacheKey = `${formId}:${releaseId}:${releaseVersion}:${releaseResolutionToken}`
  if (!formReleaseCache.has(cacheKey)) {
    formReleaseCache.set(
      cacheKey,
      getFormRuntimeRelease(
        formId,
        releaseId,
        releaseVersion,
        releaseResolutionToken
      )
        .then(release => normalizeRuntimeFormRelease(
          release,
          formId,
          release.releaseResolutionToken || releaseResolutionToken
        ))
        .catch(error => {
          formReleaseCache.delete(cacheKey)
          throw error
        })
    )
  }
  return formReleaseCache.get(cacheKey)
}
