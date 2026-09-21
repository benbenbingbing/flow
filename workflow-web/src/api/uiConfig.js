import request from '@/utils/request'
import { createUiConfigApi } from '@flow/workflow-api/uiConfig'

export const { uiConfigDraftApi, uiExtensionApi, uiExtensionRuntimeApi, uiEventBindingApi, formActionRuntimeApi, uiComponentTemplateApi } = createUiConfigApi(request)
