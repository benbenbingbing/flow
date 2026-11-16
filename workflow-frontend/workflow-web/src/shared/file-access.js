import { ElMessage } from 'element-plus'
import { createBrowserFileAccess } from '@flow/workflow-api/file'
import { notifyRequestError } from '@flow/workflow-api/request'
import { fileApi } from '@/api/file'

/** 复用宿主会话请求文件，Embed 仍由统一请求层附带委托令牌。 */
const access = createBrowserFileAccess(fileApi, {
  notifyError: error => notifyRequestError(error, message => ElMessage.error(message), '读取附件失败')
})
export const previewFile = access.preview
export const downloadFile = access.download
