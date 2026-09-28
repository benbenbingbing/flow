import request from '@/utils/request'
import { createTaskHandoverApi } from './system/taskHandoverApi.js'

export const taskHandoverApi = createTaskHandoverApi(request)
