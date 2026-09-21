import request from '@/utils/request'
import { createEntityApi } from '@flow/workflow-api/entity'

export const { entityApi, entityDataApi } = createEntityApi(request)
