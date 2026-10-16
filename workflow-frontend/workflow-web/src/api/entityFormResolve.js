import request from '@/utils/request'
import { createEntityFormResolveApi } from '@flow/workflow-api/entityFormResolve'

export const { getFormForNewData, getFormForViewData } = createEntityFormResolveApi(request)
