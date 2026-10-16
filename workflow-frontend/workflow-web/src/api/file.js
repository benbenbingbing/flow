import request from '@/utils/request'
import { createFileApi } from '@flow/workflow-api/file'

export const { fileApi } = createFileApi(request)
