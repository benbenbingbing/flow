import request from '@/utils/request'
import { createEntityExcelApi } from '@flow/workflow-api/entityExcel'
export const entityExcelApi = createEntityExcelApi(request)
