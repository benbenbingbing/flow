import request from '@/utils/request'
import { createEntityListRuntimeApi } from '@flow/workflow-api/entityListRuntime'
export const entityListRuntimeApi = createEntityListRuntimeApi(request)
