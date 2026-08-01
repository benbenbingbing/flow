import {
  formActionRuntimeApi,
  uiEventBindingApi
} from '@/api/uiConfig'
import {
  mergeResolvedFormActions,
  resolveLocalFormActions
} from '@/shared/form-actions'

export async function resolveRuntimeFormActions(forms, context) {
  const sourceForms = (Array.isArray(forms) ? forms : [forms]).filter(Boolean)
  const groups = await Promise.all(sourceForms.map(async form => {
    const formId = getFormId(form)
    if (!formId) {
      return resolveLocalFormActions(form, context)
    }
    try {
      const result = await formActionRuntimeApi.resolve({
        formId,
        releaseId: getReleaseId(form) || undefined,
        releaseVersion: getReleaseVersion(form),
        releaseResolutionToken:
          form.releaseResolutionToken || undefined,
        entityCode: context.entityCode,
        listKey: context.listKey || undefined,
        mode: context.mode,
        recordId: context.recordId || undefined,
        taskId: context.taskId || undefined
      })
      return Array.isArray(result)
        ? result
        : resolveLocalFormActions(form, context)
    } catch (error) {
      console.warn('解析表单按钮失败，使用本地约定默认值:', error)
      return resolveLocalFormActions(form, context)
    }
  }))
  return mergeResolvedFormActions(groups)
}

export function executeCustomFormAction(action, forms, context) {
  const sourceForms = (Array.isArray(forms) ? forms : [forms]).filter(Boolean)
  const ownerForm = sourceForms.find(form =>
    String(getFormId(form)) === String(action.ownerFormId)
  ) || sourceForms[0]
  const formId = getFormId(ownerForm)
  if (!formId) {
    throw new Error('自定义按钮缺少所属表单')
  }
  return uiEventBindingApi.execute('FORM_BUTTON_CLICK', {
    configType: 'FORM',
    configId: String(formId),
    releaseId: getReleaseId(ownerForm) || undefined,
    releaseVersion: getReleaseVersion(ownerForm),
    releaseResolutionToken:
      ownerForm?.releaseResolutionToken || undefined,
    entityCode: context.entityCode,
    listKey: context.listKey || undefined,
    targetType: 'BUTTON',
    targetKey: String(action.key),
    recordId: context.recordId || undefined,
    input: {
      mode: context.mode,
      button: action,
      form: context.formData || {},
      recordId: context.recordId || undefined,
      task: context.task || null
    },
    context: {
      formId: String(formId),
      listKey: context.listKey || '',
      mode: context.mode,
      taskId: context.taskId || '',
      processInstanceId: context.processInstanceId || ''
    }
  })
}

export function getFormId(form) {
  return form?.id || form?.formId || form?.entityFormId || ''
}

function getReleaseId(form) {
  return form?.runtimeReleaseId
    || form?.formReleaseId
    || form?.effectiveFormReleaseId
    || ''
}

function getReleaseVersion(form) {
  const value = form?.runtimeReleaseVersion
    ?? form?.formReleaseVersion
  return value == null ? undefined : Number(value)
}
