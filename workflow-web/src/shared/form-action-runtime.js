import { createFormActionRuntime } from '@flow/workflow-core/form-action-runtime'
import { formActionRuntimeApi, uiEventBindingApi } from '@/api/uiConfig'
import { createBusinessTraceKey, isEmbedDelegatedRequestEnabled } from '@/shared/request'

export const { resolveRuntimeFormActions, resolveSafeRuntimeActionFallback, acquireFormActionExecution, runFormAction, createFormActionRequestId, buildCustomFormActionExecutionPayload, executeCustomFormAction, getFormId } = createFormActionRuntime({ formActionRuntimeApi, uiEventBindingApi, createBusinessTraceKey, isEmbedDelegatedRequestEnabled })
