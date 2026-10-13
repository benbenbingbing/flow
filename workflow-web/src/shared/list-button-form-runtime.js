import { getFormRuntimeRelease } from '@/api/entityForm'
import { normalizeRuntimeFormRelease } from '@flow/workflow-core/form-runtime/release'
export { normalizeRuntimeFormRelease } from '@flow/workflow-core/form-runtime/release'

export function hasExplicitListButtonForm(button) {
  return Boolean(button?.targetFormId)
}

/** 每次打开按按钮业务来源读取当前配置，服务端核验按钮目标及当前权限。 */
export async function loadExplicitListButtonForm(button, runtimeContext = {}) {
  if (!hasExplicitListButtonForm(button)) return null
  const formId = String(button.targetFormId)
  const release = await getFormRuntimeRelease(formId, {
    ...runtimeContext,
    ...(button.runtimeContext || {})
  })
  return normalizeRuntimeFormRelease(release, formId)
}
