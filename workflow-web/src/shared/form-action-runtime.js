import {
  formActionRuntimeApi,
  uiEventBindingApi
} from '@/api/uiConfig'
import {
  mergeResolvedFormActions,
  resolveLocalFormActions
} from '@/shared/form-actions'
import {
  createBusinessTraceKey,
  isEmbedDelegatedRequestEnabled
} from '@/shared/request'

export async function resolveRuntimeFormActions(forms, context) {
  const sourceForms = (Array.isArray(forms) ? forms : [forms]).filter(Boolean)
  const groups = await Promise.all(sourceForms.map(async form => {
    const formId = getFormId(form)
    if (!formId) {
      return resolveSafeRuntimeActionFallback(form, context)
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
        taskId: context.taskId || undefined,
        viewCompositionTraversalToken:
          context.viewCompositionTraversalToken || undefined
      })
      return Array.isArray(result)
        ? result
        : resolveSafeRuntimeActionFallback(form, context)
    } catch (error) {
      // Embed 必须以映射 Flow 用户的服务端实时权限为准。若解析失败仍回退本地
      // 默认按钮，会把“服务不可用”误显示成“用户有保存权限”，因此委托会话失败关闭。
      if (isEmbedDelegatedRequestEnabled()) throw error
      console.warn('解析表单按钮失败，仅使用本地内置动作:', error)
      return resolveSafeRuntimeActionFallback(form, context)
    }
  }))
  return mergeResolvedFormActions(groups)
}

/**
 * 服务端无法给出权限与可用性结论时，自定义按钮必须失败关闭。无条件的内置
 * 动作沿用既有本地兜底；带显示条件的动作隐藏，只有启用条件的动作禁用，避免
 * 在无法证明条件成立时短暂暴露为可点击状态。业务接口仍负责最终鉴权。
 */
export function resolveSafeRuntimeActionFallback(form, context) {
  return resolveLocalFormActions(form, context)
    .filter(action => action.type !== 'custom')
    .map(action => applyUnavailableRuleFallback(action))
}

function applyUnavailableRuleFallback(action) {
  const rule = action?.availabilityRule
  if (!rule) return action
  // 非 v2 规则理论上会在发布前被拒绝；运行态仍失败关闭，避免脏快照绕过条件。
  if (Number(rule.version) !== 2 || rule.visibleWhen) {
    return {
      ...action,
      visible: false,
      enabled: false,
      reason: ''
    }
  }
  if (!rule.enabledWhen) return action
  return {
    ...action,
    visible: true,
    enabled: false,
    reason: String(rule.disabledMessage || '').trim()
      || '按钮条件暂时无法校验'
  }
}

/**
 * 在确认弹窗出现前原子占用动作锁，防止连续点击生成两个不同 requestId。
 * loadingState 可以是独立 pending ref；返回函数只释放自己持有的锁，避免
 * 未来异步流程误清理后续动作状态。
 */
export function acquireFormActionExecution(action, loadingState) {
  if (!action || action.enabled === false || loadingState?.value) return null
  const loadingKey = String(
    action.runtimeKey || action.key || '__pending_form_action__'
  )
  loadingState.value = loadingKey
  return () => {
    if (loadingState.value === loadingKey) loadingState.value = ''
  }
}

/** 为一次用户点击生成服务端可接受的幂等 nonce。 */
export function createFormActionRequestId() {
  return `form_action_${createBusinessTraceKey()}`.slice(0, 128)
}

/**
 * 构造表单按钮事件请求。requestId 仅位于可信请求顶层，不进入 Provider 的
 * input/context；调用方可在网络重试时复用同一 payload。
 */
export function buildCustomFormActionExecutionPayload(
  action,
  forms,
  context,
  requestId
) {
  const sourceForms = (Array.isArray(forms) ? forms : [forms]).filter(Boolean)
  const ownerForm = sourceForms.find(form =>
    String(getFormId(form)) === String(action.ownerFormId)
  ) || sourceForms[0]
  const formId = getFormId(ownerForm)
  if (!formId) {
    throw new Error('自定义按钮缺少所属表单')
  }
  return {
    configType: 'FORM',
    configId: String(formId),
    releaseId: getReleaseId(ownerForm) || undefined,
    releaseVersion: getReleaseVersion(ownerForm),
    releaseResolutionToken:
      ownerForm?.releaseResolutionToken || undefined,
    viewCompositionTraversalToken:
      context.viewCompositionTraversalToken || undefined,
    entityCode: context.entityCode,
    listKey: context.listKey || undefined,
    targetType: 'BUTTON',
    targetKey: String(action.key),
    requestId,
    recordId: context.recordId || undefined,
    // taskId 只是客户端声明的待办坐标；服务端必须重新校验归属、
    // 记录和固定发布版。不再携带 processInstanceId，避免被映射为可信身份。
    taskId: context.taskId || undefined,
    input: {
      mode: context.mode,
      form: context.formData || {}
    },
    // Provider 身份由服务端强类型上下文注入，不在可映射 context
    // 中重复 form/task/process 坐标。
    context: {}
  }
}

export function executeCustomFormAction(action, forms, context) {
  // 每次用户调用只生成一次；axios 对同一请求配置的传输级重试会保持该值。
  const requestId = createFormActionRequestId()
  return uiEventBindingApi.execute(
    'FORM_BUTTON_CLICK',
    buildCustomFormActionExecutionPayload(
      action,
      forms,
      context,
      requestId
    )
  )
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
