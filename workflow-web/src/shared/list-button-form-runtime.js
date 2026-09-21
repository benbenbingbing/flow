import { getFormRuntimeRelease } from '@/api/entityForm'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'

const formReleaseCache = new Map()

export function hasExplicitListButtonForm(button) {
  return Boolean(button?.targetFormId)
}

export { normalizeRuntimeFormRelease } from '@flow/workflow-core/form-runtime/release'
import { normalizeRuntimeFormRelease } from '@flow/workflow-core/form-runtime/release'

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
