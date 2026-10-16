import request from '@/utils/request'
import { createEntityStatusApi } from '@flow/workflow-api/entityStatus'

export const { getEntityStatusList, getEntityStatusByCategory, saveEntityStatus, saveEntityStatusList, deleteEntityStatus } = createEntityStatusApi(request)
