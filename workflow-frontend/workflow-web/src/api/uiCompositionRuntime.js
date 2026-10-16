import request from '@/utils/request'
import { createUiCompositionRuntimeApi } from '@flow/workflow-api/uiCompositionRuntime'

export const { uiCompositionRuntimeApi } = createUiCompositionRuntimeApi(request)
